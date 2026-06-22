import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();
    static final List<Socket> ACTIVE = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        String serverHost = pick(args, 0, "SERVER_HOST", "127.0.0.1");
        int serverPort = Integer.parseInt(pick(args, 1, "SERVER_PORT", "7443"));

        while (true) {
            System.out.println("Connecting...");
            try (Socket control = new Socket(serverHost, serverPort)) {
                control.setTcpNoDelay(true);
                System.out.println("Connected!");
                commands(serverHost, serverPort, control);
            } catch (Exception ignored) {
            }
            closeAll();
            sleep(1000);
        }
    }

    static void commands(String serverHost, int serverPort, Socket control) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8));
        for (String line; (line = in.readLine()) != null; ) {
            if ("P".equals(line)) {
                out.write("P\n");
                out.flush();
            } else if ("S".equals(line)) {
                socket(serverHost, serverPort);
            }
        }
    }

    static void socket(String serverHost, int serverPort) {
        Socket tunnel = null;
        Socket target = null;
        try {
            tunnel = new Socket(serverHost, serverPort);
            tunnel.setTcpNoDelay(true);
            ACTIVE.add(tunnel);

            String line = readLine(tunnel.getInputStream());
            if (line == null || !line.startsWith("S ")) throw new IOException("bad socket command");
            String[] p = line.split(" ", 3);
            if (p.length != 3) throw new IOException("bad socket command");

            target = new Socket(p[1], Integer.parseInt(p[2]));
            target.setTcpNoDelay(true);
            ACTIVE.add(target);
            Socket a = tunnel, b = target;
            IO.execute(() -> {
                try {
                    pipeBoth(a, b);
                } catch (InterruptedException ignored) {
                } finally {
                    ACTIVE.remove(a);
                    ACTIVE.remove(b);
                }
            });
        } catch (Exception ignored) {
            close(tunnel);
            close(target);
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

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') break;
            if (b != '\r') out.write(b);
            if (out.size() > 1024) throw new IOException("line too long");
        }
        if (b < 0 && out.size() == 0) return null;
        return out.toString(StandardCharsets.UTF_8);
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
