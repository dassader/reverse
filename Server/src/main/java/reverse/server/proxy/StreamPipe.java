package reverse.server.proxy;

import reverse.server.config.RouteConfig;
import reverse.server.stats.StatsService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import static reverse.server.util.Sockets.close;

public final class StreamPipe {
    private static final int BUF = 64 * 1024;

    private StreamPipe() {
    }

    public static void both(RouteConfig route, StatsService stats, String consumer, Socket a, Socket b) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Thread left = new Thread(() -> pipe(a, b, done, n -> stats.up(route, consumer, n)), "pipe-up");
        Thread right = new Thread(() -> pipe(b, a, done, n -> stats.down(route, consumer, n)), "pipe-down");
        left.setDaemon(true);
        right.setDaemon(true);
        left.start();
        right.start();
        done.await();
        close(a);
        close(b);
    }

    private static void pipe(Socket from, Socket to, CountDownLatch done, ByteCounter counter) {
        try {
            byte[] buf = new byte[BUF];
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
                counter.add(n);
            }
        } catch (IOException ignored) {
        } finally {
            close(from);
            close(to);
            done.countDown();
        }
    }

    private interface ByteCounter {
        void add(int n);
    }
}
