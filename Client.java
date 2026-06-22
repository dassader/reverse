import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();

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
        List<Route> routes = config(serverHost, controlPort);
        for (Route r : routes) {
            for (int i = 1; i <= r.channels; i++) {
                int n = i;
                Thread t = new Thread(() -> loop(serverHost, controlPort, r, n), "tunnel-" + r.id + "-" + n);
                t.start();
            }
        }
        System.out.println("Connected!");
        Thread.currentThread().join();
    }

    static List<Route> config(String serverHost, int controlPort) throws IOException {
        try (Socket s = new Socket(serverHost, controlPort)) {
            s.setTcpNoDelay(true);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.println("CONFIG");
            ArrayList<Route> routes = new ArrayList<>();
            for (String line; (line = in.readLine()) != null; ) {
                if ("END".equals(line)) break;
                String[] p = line.split(" ");
                if (p.length == 5 && "ROUTE".equals(p[0])) routes.add(new Route(p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4])));
            }
            if (routes.isEmpty()) throw new IOException("empty config");
            return routes;
        }
    }

    static void loop(String serverHost, int controlPort, Route route, int n) {
        while (true) {
            try (Socket tunnel = new Socket(serverHost, controlPort)) {
                tunnel.setTcpNoDelay(true);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(tunnel.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println("TUNNEL " + route.id);
                if (tunnel.getInputStream().read() != 1) throw new IOException("server closed tunnel");
                try (Socket target = new Socket(route.host, route.port)) {
                    target.setTcpNoDelay(true);
                    pipeBoth(tunnel, target);
                }
            } catch (Exception e) {
                sleep(1000);
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

    static void close(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }
}
