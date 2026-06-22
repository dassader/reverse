import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Server {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        String bind = pick(args, 0, "PUBLIC_BIND", "0.0.0.0");
        int publicPort = Integer.parseInt(pick(args, 1, "PUBLIC_PORT", "7777"));
        int tunnelPort = Integer.parseInt(pick(args, 2, "TUNNEL_PORT", "7443"));
        BlockingQueue<Socket> tunnels = new LinkedBlockingQueue<>();

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

    static void acceptTunnels(ServerSocket server, BlockingQueue<Socket> tunnels) {
        while (true) try {
            Socket s = server.accept();
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            tunnels.offer(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void serve(Socket user, BlockingQueue<Socket> tunnels) {
        try (user) {
            Socket tunnel = takeLiveTunnel(tunnels, 30_000);
            if (tunnel == null) return;
            try (tunnel) {
                pipeBoth(user, tunnel);
            }
        } catch (Exception ignored) {
        }
    }

    static Socket takeLiveTunnel(BlockingQueue<Socket> tunnels, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Socket s = tunnels.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (s == null) return null;
            try {
                s.getOutputStream().write(1);
                s.getOutputStream().flush();
                return s;
            } catch (IOException e) {
                close(s);
            }
        }
        return null;
    }

    static void pipeBoth(Socket a, Socket b) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        IO.execute(() -> pipe(a, b, done));
        IO.execute(() -> pipe(b, a, done));
        done.await();
        close(a);
        close(b);
    }

    static void pipe(Socket from, Socket to, CountDownLatch done) {
        try {
            byte[] buf = new byte[BUF];
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            for (int n; (n = in.read(buf)) >= 0; ) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            close(from);
            close(to);
            done.countDown();
        }
    }

    static String pick(String[] args, int i, String env, String def) {
        return args.length > i ? args[i] : System.getenv().getOrDefault(env, def);
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
