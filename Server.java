import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Server {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();
    static final AtomicLong TUNNELS = new AtomicLong();
    static final AtomicLong STREAMS = new AtomicLong();

    static class Route {
        final String id, bind, targetHost, mode, tlsHost;
        final int publicPort, targetPort, channels;
        final BlockingQueue<Tunnel> tunnels = new LinkedBlockingQueue<>();

        Route(String id, String bind, int publicPort, String targetHost, int targetPort, int channels, String mode, String tlsHost) {
            this.id = id;
            this.bind = bind;
            this.publicPort = publicPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.channels = channels;
            this.mode = mode;
            this.tlsHost = tlsHost == null || tlsHost.equals("-") ? targetHost : tlsHost;
        }
    }

    static class Tunnel {
        final long id;
        final Socket socket;

        Tunnel(long id, Socket socket) {
            this.id = id;
            this.socket = socket;
        }
    }

    public static void main(String[] args) throws Exception {
        int controlPort = Integer.parseInt(pick(args, 0, "CONTROL_PORT", "7443"));
        Map<String, Route> routes = routes(args);

        ServerSocket control = new ServerSocket(controlPort);
        IO.execute(() -> acceptControl(control, routes));
        for (Route route : routes.values()) IO.execute(() -> acceptCodex(route));

        printConfig(controlPort, routes.values());
        Thread.currentThread().join();
    }

    static Map<String, Route> routes(String[] args) {
        LinkedHashMap<String, Route> routes = new LinkedHashMap<>();
        if (args.length <= 1) {
            routes.put("mcp", new Route("mcp", "0.0.0.0", 7777, "127.0.0.1", 64343, 8, "http", "-"));
            return routes;
        }
        for (int i = 1; i < args.length; i++) {
            String[] p = args[i].split(",");
            if (p.length < 6) throw new IllegalArgumentException("route: id,bind,publicPort,targetHost,targetPort,channels[,tcp|http|https[,tlsHost]]");
            String id = p[0];
            routes.put(id, new Route(
                id, p[1], Integer.parseInt(p[2]), p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                p.length > 6 ? p[6] : "tcp",
                p.length > 7 ? p[7] : "-"
            ));
        }
        return routes;
    }

    static void acceptControl(ServerSocket server, Map<String, Route> routes) {
        while (true) try {
            Socket s = server.accept();
            s.setTcpNoDelay(true);
            IO.execute(() -> control(s, routes));
        } catch (IOException e) {
            log("[S] control accept error");
        }
    }

    static void control(Socket s, Map<String, Route> routes) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if ("CONFIG".equals(line)) {
                PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                for (Route r : routes.values()) out.println("ROUTE " + r.id + " " + r.targetHost + " " + r.targetPort + " " + r.channels);
                out.println("END");
                log("[S] config -> " + addr(s));
                close(s);
                return;
            }
            if (line != null && line.startsWith("TUNNEL ")) {
                Route route = routes.get(line.substring(7).trim());
                if (route == null) {
                    close(s);
                    return;
                }
                long id = TUNNELS.incrementAndGet();
                route.tunnels.offer(new Tunnel(id, s));
                log("[S] tunnel " + route.id + "#" + id + " + " + addr(s));
                return;
            }
            close(s);
        } catch (IOException e) {
            close(s);
        }
    }

    static void acceptCodex(Route route) {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getByName(route.bind), route.publicPort));
            while (true) {
                Socket codex = server.accept();
                codex.setTcpNoDelay(true);
                IO.execute(() -> serve(route, codex));
            }
        } catch (IOException e) {
            log("[S] route " + route.id + " failed: " + e.getMessage());
        }
    }

    static void serve(Route route, Socket codex) {
        long stream = STREAMS.incrementAndGet();
        log("[S] codex " + route.id + "#" + stream + " + " + addr(codex));
        try (codex) {
            Tunnel tunnel = take(route);
            if (tunnel == null) {
                log("[S] " + route.id + "#" + stream + " no tunnel");
                return;
            }
            log("[S] " + route.id + "#" + stream + " -> tunnel#" + tunnel.id);
            try (tunnel.socket) {
                if ("https".equalsIgnoreCase(route.mode)) https(route, codex, tunnel.socket);
                else if ("http".equalsIgnoreCase(route.mode)) http(route, codex, tunnel.socket);
                else tcp(route, codex, tunnel.socket);
                log("[S] " + route.id + "#" + stream + " done");
            }
        } catch (Exception e) {
            log("[S] " + route.id + "#" + stream + " error");
        }
    }

    static Tunnel take(Route route) throws InterruptedException {
        long end = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < end) {
            Tunnel tunnel = route.tunnels.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (tunnel == null) return null;
            try {
                tunnel.socket.getOutputStream().write(1);
                tunnel.socket.getOutputStream().flush();
                return tunnel;
            } catch (IOException e) {
                log("[S] tunnel " + route.id + "#" + tunnel.id + " stale");
                close(tunnel.socket);
            }
        }
        return null;
    }

    static void tcp(Route route, Socket codex, Socket tunnel) throws Exception {
        pipeBoth(codex, tunnel);
    }

    static void http(Route route, Socket codex, Socket tunnel) throws Exception {
        pipeHttpThen(codex, tunnel, route.targetHost + ":" + route.targetPort);
        pipeBoth(codex, tunnel);
    }

    static void https(Route route, Socket codex, Socket tunnel) throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(tunnel, route.tlsHost, route.targetPort, true);
        SSLParameters p = ssl.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(p);
        ssl.startHandshake();
        pipeHttpThen(codex, ssl, route.tlsHost);
        pipeBoth(codex, ssl);
    }

    static void pipeHttpThen(Socket from, Socket to, String host) throws IOException {
        byte[] head = readHttpHead(from.getInputStream());
        log("[S] " + requestLine(head));
        byte[] rewritten = rewriteHost(head, host);
        to.getOutputStream().write(rewritten);
        to.getOutputStream().flush();
    }

    static byte[] readHttpHead(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            out.write(b);
            byte[] h = out.toByteArray();
            int n = h.length;
            if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n' && h[n - 2] == '\r' && h[n - 1] == '\n') break;
            if (n > 64 * 1024) break;
        }
        return out.toByteArray();
    }

    static byte[] rewriteHost(byte[] raw, String host) {
        String s = new String(raw, StandardCharsets.ISO_8859_1);
        if (!s.startsWith("GET ") && !s.startsWith("POST ") && !s.startsWith("HEAD ") &&
            !s.startsWith("PUT ") && !s.startsWith("DELETE ") && !s.startsWith("PATCH ") &&
            !s.startsWith("OPTIONS ")) return raw;
        String[] lines = s.split("\r\n", -1);
        boolean seen = false;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].regionMatches(true, 0, "Host:", 0, 5)) {
                lines[i] = "Host: " + host;
                seen = true;
            }
        }
        if (!seen && lines.length > 1) lines[0] = lines[0] + "\r\nHost: " + host;
        return String.join("\r\n", lines).getBytes(StandardCharsets.ISO_8859_1);
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

    static void printConfig(int controlPort, Collection<Route> routes) throws SocketException {
        System.out.println("Server control: 0.0.0.0:" + controlPort);
        for (String ip : localIps()) System.out.println("Client: java -cp Client Client " + ip + " " + controlPort);
        for (Route r : routes) {
            System.out.println("Route " + r.id + ": " + r.bind + ":" + r.publicPort + " -> " + r.mode + " " + r.targetHost + ":" + r.targetPort + " x" + r.channels);
            for (String ip : localIps()) System.out.println("Codex:  http://" + ip + ":" + r.publicPort + "/stream");
        }
    }

    static List<String> localIps() throws SocketException {
        ArrayList<String> ips = new ArrayList<>();
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
            Enumeration<InetAddress> addrs = iface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) ips.add(addr.getHostAddress());
            }
        }
        if (ips.isEmpty()) ips.add("127.0.0.1");
        return ips;
    }

    static String pick(String[] args, int i, String env, String def) {
        return args.length > i ? args[i] : System.getenv().getOrDefault(env, def);
    }

    static String requestLine(byte[] raw) {
        if (raw.length == 0) return "empty request";
        String s = new String(raw, StandardCharsets.ISO_8859_1);
        int end = s.indexOf("\r\n");
        return end >= 0 ? s.substring(0, end) : s;
    }

    static String addr(Socket s) {
        SocketAddress a = s.getRemoteSocketAddress();
        return a == null ? "unknown" : a.toString().replaceFirst("^/", "");
    }

    static synchronized void log(String msg) {
        System.out.println(msg);
    }

    static void close(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }
}
