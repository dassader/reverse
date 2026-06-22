import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Client {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        String serverHost = pick(args, 0, "SERVER_HOST", "127.0.0.1");
        int tunnelPort = Integer.parseInt(pick(args, 1, "TUNNEL_PORT", "7443"));
        String targetHost = pick(args, 2, "TARGET_HOST", "127.0.0.1");
        int targetPort = Integer.parseInt(pick(args, 3, "TARGET_PORT", "64343"));
        int channels = Integer.parseInt(pick(args, 4, "CHANNELS", "8"));

        System.out.println("Client connects to Server tunnel: " + serverHost + ":" + tunnelPort);
        System.out.println("Client forwards local MCP from:    " + targetHost + ":" + targetPort);
        System.out.println("Channels:                          " + channels);
        for (int i = 0; i < channels; i++) {
            int id = i + 1;
            Thread t = new Thread(() -> loop(serverHost, tunnelPort, targetHost, targetPort), "tunnel-" + id);
            t.start();
        }
        Thread.currentThread().join();
    }

    static void loop(String serverHost, int tunnelPort, String targetHost, int targetPort) {
        while (true) {
            try (Socket tunnel = new Socket(serverHost, tunnelPort)) {
                tunnel.setTcpNoDelay(true);
                tunnel.setKeepAlive(true);
                if (tunnel.getInputStream().read() != 1) throw new IOException("server closed tunnel");
                try (Socket local = new Socket(targetHost, targetPort)) {
                    local.setTcpNoDelay(true);
                    pipeBoth(tunnel, local, targetHost + ":" + targetPort);
                }
            } catch (Exception e) {
                sleep(1000);
            }
        }
    }

    static void pipeBoth(Socket a, Socket b, String host) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        IO.execute(() -> pipeHttpRequest(a, b, done, host));
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

    static void pipeHttpRequest(Socket from, Socket to, CountDownLatch done, String host) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) >= 0) {
                head.write(b);
                byte[] h = head.toByteArray();
                int n = h.length;
                if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n' && h[n - 2] == '\r' && h[n - 1] == '\n') break;
                if (n > 64 * 1024) break;
            }
            byte[] h = rewriteHost(head.toByteArray(), host);
            out.write(h);
            out.flush();
            byte[] buf = new byte[BUF];
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

    static byte[] rewriteHost(byte[] raw, String host) throws IOException {
        String s = new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1);
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
        if (!seen && lines.length > 1) {
            lines[0] = lines[0] + "\r\nHost: " + host;
        }
        return String.join("\r\n", lines).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
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
