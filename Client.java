import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Client {
    static final int BUF = 64 * 1024;
    static final ExecutorService IO = Executors.newCachedThreadPool();
    static final AtomicLong STREAM_IDS = new AtomicLong();

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
            Thread t = new Thread(() -> loop(id, serverHost, tunnelPort, targetHost, targetPort), "tunnel-" + id);
            t.start();
        }
        Thread.currentThread().join();
    }

    static void loop(int channelId, String serverHost, int tunnelPort, String targetHost, int targetPort) {
        while (true) {
            try (Socket tunnel = new Socket(serverHost, tunnelPort)) {
                tunnel.setTcpNoDelay(true);
                tunnel.setKeepAlive(true);
                log("[C] tunnel#" + channelId + " ready");
                if (tunnel.getInputStream().read() != 1) throw new IOException("server closed tunnel");
                long streamId = STREAM_IDS.incrementAndGet();
                log("[C] stream#" + streamId + " -> " + targetHost + ":" + targetPort);
                try (Socket local = new Socket(targetHost, targetPort)) {
                    local.setTcpNoDelay(true);
                    pipeBoth(tunnel, local, targetHost + ":" + targetPort, streamId);
                    log("[C] stream#" + streamId + " done");
                }
            } catch (Exception e) {
                log("[C] tunnel#" + channelId + " retry");
                sleep(1000);
            }
        }
    }

    static long[] pipeBoth(Socket a, Socket b, String host, long streamId) throws InterruptedException {
        AtomicLong aToB = new AtomicLong();
        AtomicLong bToA = new AtomicLong();
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(2);
        IO.execute(() -> pipeHttpRequest(a, b, firstDone, allDone, aToB, host, streamId));
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

    static void pipeHttpRequest(Socket from, Socket to, CountDownLatch firstDone, CountDownLatch allDone, AtomicLong bytes, String host, long streamId) {
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
            log("[C] " + requestLine(head.toByteArray()));
            byte[] h = rewriteHost(head.toByteArray(), host);
            out.write(h);
            out.flush();
            bytes.addAndGet(h.length);
            byte[] buf = new byte[BUF];
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

    static String requestLine(byte[] raw) {
        if (raw.length == 0) return "empty request";
        String s = new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1);
        int end = s.indexOf("\r\n");
        return end >= 0 ? s.substring(0, end) : s;
    }

    static String pick(String[] args, int i, String env, String def) {
        return args.length > i ? args[i] : System.getenv().getOrDefault(env, def);
    }

    static synchronized void log(String message) {
        System.out.println(message);
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
