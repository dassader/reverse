import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();
    static volatile boolean reload;
    static final List<Socket> OPEN = Collections.synchronizedList(new ArrayList<>());

    static class Route {
        final String id, host;
        final int port, channels;

        Route(String id, String host, int port, int channels) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.channels = channels;
        }
    }

    public static void main(String[] args) throws Exception {
        String serverHost = pick(args, 0, "SERVER_HOST", "127.0.0.1");
        int controlPort = Integer.parseInt(pick(args, 1, "CONTROL_PORT", "7443"));

        System.out.println("Connecting...");
        while (true) {
            Config cfg = null;
            while (cfg == null) {
                try {
                    cfg = config(serverHost, controlPort);
                } catch (IOException e) {
                    sleep(1000);
                }
            }
            reload = false;
            for (Route r : cfg.routes) {
                for (int i = 1; i <= r.channels; i++) {
                    int n = i;
                    Thread t = new Thread(() -> loop(serverHost, controlPort, r, n), "tunnel-" + r.id + "-" + n);
                    t.setDaemon(true);
                    t.start();
                }
            }
            System.out.println("Connected!");
            while (!reload) {
                sleep(5000);
                if (!ping(serverHost, controlPort, cfg.version)) reload = true;
            }
            closeAll();
            System.out.println("Connecting...");
        }
    }

    static class Config {
        final String version;
        final List<Route> routes;

        Config(String version, List<Route> routes) {
            this.version = version;
            this.routes = routes;
        }
    }

    static Config config(String serverHost, int controlPort) throws IOException {
        try (Socket s = new Socket(serverHost, controlPort)) {
            s.setTcpNoDelay(true);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.println("CONFIG");
            ArrayList<Route> routes = new ArrayList<>();
            String version = "";
            for (String line; (line = in.readLine()) != null; ) {
                if ("END".equals(line)) break;
                if (line.startsWith("VERSION ")) version = line.substring(8).trim();
                String[] p = line.split(" ");
                if (p.length == 5 && "ROUTE".equals(p[0])) routes.add(new Route(p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4])));
            }
            if (routes.isEmpty()) throw new IOException("empty config");
            return new Config(version, routes);
        }
    }

    static boolean ping(String serverHost, int controlPort, String version) {
        try (Socket s = new Socket(serverHost, controlPort)) {
            s.setTcpNoDelay(true);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.println("PING " + version);
            return "OK".equals(in.readLine());
        } catch (IOException e) {
            return false;
        }
    }

    static void loop(String serverHost, int controlPort, Route route, int n) {
        while (true) {
            if (reload) return;
            try (Socket tunnel = new Socket(serverHost, controlPort)) {
                OPEN.add(tunnel);
                tunnel.setTcpNoDelay(true);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(tunnel.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println("TUNNEL " + route.id);
                if (tunnel.getInputStream().read() != 1) throw new IOException("server closed tunnel");
                try (Socket target = new Socket(route.host, route.port)) {
                    OPEN.add(target);
                    target.setTcpNoDelay(true);
                    pipeBoth(tunnel, target);
                }
            } catch (Exception e) {
                sleep(1000);
            } finally {
                OPEN.removeIf(Socket::isClosed);
            }
        }
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

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    static void closeAll() {
        synchronized (OPEN) {
            for (Socket s : OPEN) close(s);
            OPEN.clear();
        }
    }

    static void close(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }
}
