import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Server {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();
    static final AtomicLong TUNNEL_IDS = new AtomicLong();
    static final AtomicLong STREAM_IDS = new AtomicLong();

    static class Tunnel {
        final long id;
        final Socket socket;

        Tunnel(long id, Socket socket) {
            this.id = id;
            this.socket = socket;
        }
    }

    public static void main(String[] args) throws Exception {
        String bind = pick(args, 0, "PUBLIC_BIND", "0.0.0.0");
        int publicPort = Integer.parseInt(pick(args, 1, "PUBLIC_PORT", "7777"));
        int tunnelPort = Integer.parseInt(pick(args, 2, "TUNNEL_PORT", "7443"));
        BlockingQueue<Tunnel> tunnels = new LinkedBlockingQueue<>();

        ServerSocket tunnelServer = new ServerSocket(tunnelPort);
        IO.execute(() -> acceptTunnels(tunnelServer, tunnels));

        try (ServerSocket pub = new ServerSocket()) {
            pub.bind(new InetSocketAddress(InetAddress.getByName(bind), publicPort));
            printWhereToConnect(bind, publicPort, tunnelPort);
            while (true) {
                Socket user = pub.accept();
                user.setTcpNoDelay(true);
                IO.execute(() -> serve(user, tunnels));
            }
        }
    }

    static void acceptTunnels(ServerSocket server, BlockingQueue<Tunnel> tunnels) {
        while (true) try {
            Socket s = server.accept();
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            long id = TUNNEL_IDS.incrementAndGet();
            tunnels.offer(new Tunnel(id, s));
            log("[S] tunnel#" + id + " + " + addr(s));
        } catch (IOException e) {
            log("[S] tunnel accept error");
        }
    }

    static void serve(Socket user, BlockingQueue<Tunnel> tunnels) {
        long streamId = STREAM_IDS.incrementAndGet();
        String userAddr = addr(user);
        log("[S] codex#" + streamId + " + " + userAddr);
        try (user) {
            Tunnel tunnel = takeLiveTunnel(tunnels, 30_000);
            if (tunnel == null) {
                log("[S] codex#" + streamId + " no tunnel");
                return;
            }
            log("[S] stream#" + streamId + " -> tunnel#" + tunnel.id);
            try (tunnel.socket) {
                pipeBoth(user, tunnel.socket);
                log("[S] stream#" + streamId + " done");
            }
        } catch (Exception e) {
            log("[S] stream#" + streamId + " error");
        }
    }

    static Tunnel takeLiveTunnel(BlockingQueue<Tunnel> tunnels, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Tunnel tunnel = tunnels.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (tunnel == null) return null;
            try {
                tunnel.socket.getOutputStream().write(1);
                tunnel.socket.getOutputStream().flush();
                return tunnel;
            } catch (IOException e) {
                log("[S] tunnel#" + tunnel.id + " stale");
                close(tunnel.socket);
            }
        }
        return null;
    }

    static long[] pipeBoth(Socket a, Socket b) throws InterruptedException {
        AtomicLong aToB = new AtomicLong();
        AtomicLong bToA = new AtomicLong();
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(2);
        IO.execute(() -> pipe(a, b, firstDone, allDone, aToB));
        IO.execute(() -> pipe(b, a, firstDone, allDone, bToA));
        firstDone.await();
        close(a);
        close(b);
        allDone.await(2, TimeUnit.SECONDS);
        return new long[] {aToB.get(), bToA.get()};
    }

    static void pipe(Socket from, Socket to, CountDownLatch firstDone, CountDownLatch allDone, AtomicLong bytes) {
        try {
            byte[] buf = new byte[BUF];
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            for (int n; (n = in.read(buf)) >= 0; ) {
                out.write(buf, 0, n);
                out.flush();
                bytes.addAndGet(n);
            }
        } catch (IOException ignored) {
        } finally {
            close(from);
            close(to);
            firstDone.countDown();
            allDone.countDown();
        }
    }

    static String pick(String[] args, int i, String env, String def) {
        return args.length > i ? args[i] : System.getenv().getOrDefault(env, def);
    }

    static synchronized void log(String message) {
        System.out.println(message);
    }

    static String addr(Socket socket) {
        SocketAddress address = socket.getRemoteSocketAddress();
        return address == null ? "unknown" : address.toString().replaceFirst("^/", "");
    }

    static void printWhereToConnect(String bind, int publicPort, int tunnelPort) throws SocketException {
        System.out.println("Server listens for Client tunnels on 0.0.0.0:" + tunnelPort);
        System.out.println("Server listens for Codex on " + bind + ":" + publicPort);
        for (String ip : localIps()) {
            System.out.println("Client connects to: java -cp Client Client " + ip + " " + tunnelPort + " 127.0.0.1 64343 8");
            System.out.println("Codex connects to:  http://" + ip + ":" + publicPort + "/stream");
            System.out.println("SSE fallback:       http://" + ip + ":" + publicPort + "/sse");
        }
    }

    static java.util.List<String> localIps() throws SocketException {
        java.util.ArrayList<String> ips = new java.util.ArrayList<>();
        java.util.Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
            java.util.Enumeration<InetAddress> addrs = iface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) ips.add(addr.getHostAddress());
            }
        }
        if (ips.isEmpty()) ips.add("127.0.0.1");
        return ips;
    }

    static void close(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }
}
