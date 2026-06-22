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
    static final AtomicLong STREAMS = new AtomicLong();
    static final BlockingQueue<Socket> DATA = new LinkedBlockingQueue<>();
    static final List<Socket> ACTIVE = Collections.synchronizedList(new ArrayList<>());
    static final Object OPEN_LOCK = new Object();
    static volatile Control CONTROL;

    static class Route {
        final String id, bind, targetHost, mode, tlsHost;
        final int publicPort, targetPort;

        Route(String id, String bind, int publicPort, String targetHost, int targetPort, String mode, String tlsHost) {
            this.id = id;
            this.bind = bind;
            this.publicPort = publicPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.mode = mode;
            this.tlsHost = tlsHost == null || tlsHost.equals("-") ? targetHost : tlsHost;
        }
    }

    static class Control {
        final Socket socket;
        final BufferedReader in;
        final BufferedWriter out;
        volatile long lastPong = System.currentTimeMillis();

        Control(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        synchronized void send(String line) throws IOException {
            out.write(line);
            out.write('\n');
            out.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        int controlPort = Integer.parseInt(pick(args, 0, "CONTROL_PORT", "7443"));
        Map<String, Route> routes = routes(args);

        ServerSocket clientPort = new ServerSocket(controlPort);
        IO.execute(() -> acceptClient(clientPort));
        for (Route route : routes.values()) IO.execute(() -> acceptCodex(route));

        printConfig(controlPort, routes.values());
        Thread.currentThread().join();
    }

    static Map<String, Route> routes(String[] args) {
        LinkedHashMap<String, Route> routes = new LinkedHashMap<>();
        if (args.length <= 1) {
            routes.put("mcp", new Route("mcp", "0.0.0.0", 7777, "127.0.0.1", 64343, "http", "-"));
            return routes;
        }
        for (int i = 1; i < args.length; i++) {
            String[] p = args[i].split(",");
            if (p.length < 5) throw new IllegalArgumentException("route: id,bind,publicPort,targetHost,targetPort[,tcp|http|https[,tlsHost]]");
            routes.put(p[0], new Route(
                p[0], p[1], Integer.parseInt(p[2]), p[3], Integer.parseInt(p[4]),
                p.length > 5 ? p[5] : "tcp",
                p.length > 6 ? p[6] : "-"
            ));
        }
        return routes;
    }

    static void acceptClient(ServerSocket server) {
        while (true) try {
            Socket s = server.accept();
            s.setTcpNoDelay(true);
            IO.execute(() -> clientSocket(s));
        } catch (IOException e) {
            log("[S] client accept error");
        }
    }

    static void clientSocket(Socket s) {
        try {
            Control control = CONTROL;
            if (control == null) {
                setControl(s);
                return;
            }
            DATA.offer(s);
        } catch (Exception e) {
            close(s);
        }
    }

    static void setControl(Socket s) throws IOException {
        Control old = CONTROL;
        if (old != null) close(old.socket);
        Control control = new Control(s);
        CONTROL = control;
        log("[S] client connected " + addr(s));
        IO.execute(() -> readControl(control));
        IO.execute(() -> ping(control));
    }

    static void readControl(Control control) {
        try {
            for (String line; (line = control.in.readLine()) != null; ) {
                if ("P".equals(line)) control.lastPong = System.currentTimeMillis();
            }
        } catch (IOException ignored) {
        } finally {
            lost(control);
        }
    }

    static void ping(Control control) {
        while (CONTROL == control) {
            sleep(5000);
            try {
                control.send("P");
                if (System.currentTimeMillis() - control.lastPong > 15000) lost(control);
            } catch (IOException e) {
                lost(control);
            }
        }
    }

    static void lost(Control control) {
        if (CONTROL != control) return;
        CONTROL = null;
        close(control.socket);
        closeAll();
        Socket s;
        while ((s = DATA.poll()) != null) close(s);
        log("[S] client disconnected");
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
        long id = STREAMS.incrementAndGet();
        log("[S] codex " + route.id + "#" + id + " + " + addr(codex));
        try (codex) {
            Control control = CONTROL;
            if (control == null) {
                log("[S] " + route.id + "#" + id + " no client");
                return;
            }

            Socket tunnel = openTunnel(control, route);
            ACTIVE.add(tunnel);
            try (tunnel) {
                if ("https".equalsIgnoreCase(route.mode)) https(route, codex, tunnel);
                else if ("http".equalsIgnoreCase(route.mode)) http(route, codex, tunnel);
                else tcp(codex, tunnel);
                log("[S] " + route.id + "#" + id + " done");
            } finally {
                ACTIVE.remove(tunnel);
            }
        } catch (Exception e) {
            log("[S] " + route.id + "#" + id + " error");
        }
    }

    static Socket openTunnel(Control control, Route route) throws Exception {
        synchronized (OPEN_LOCK) {
            control.send("S " + route.targetHost + " " + route.targetPort);
            Socket tunnel = DATA.poll(30, TimeUnit.SECONDS);
            if (tunnel == null) throw new IOException("no data socket");
            return tunnel;
        }
    }

    static void tcp(Socket codex, Socket tunnel) throws Exception {
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

    static void printConfig(int clientPort, Collection<Route> routes) throws SocketException {
        System.out.println("Client port: 0.0.0.0:" + clientPort);
        for (String ip : localIps()) System.out.println("Client: java -cp Client Client " + ip + " " + clientPort);
        for (Route r : routes) {
            System.out.println("Route " + r.id + ": " + r.bind + ":" + r.publicPort + " -> " + r.mode + " " + r.targetHost + ":" + r.targetPort);
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

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    static void closeAll() {
        synchronized (ACTIVE) {
            for (Socket s : ACTIVE) close(s);
            ACTIVE.clear();
        }
    }

    static void close(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }
}
