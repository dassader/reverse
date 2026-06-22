package reverse.server.stats;

import java.util.concurrent.atomic.AtomicLong;

public class ConsumerStats {
    private final String remote;
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong active = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong bytesUp = new AtomicLong();
    private final AtomicLong bytesDown = new AtomicLong();
    private volatile long lastSeenAt;

    public ConsumerStats(String remote) {
        this.remote = remote;
    }

    public void request() {
        requests.incrementAndGet();
        active.incrementAndGet();
        lastSeenAt = System.currentTimeMillis();
    }

    public void done() {
        active.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void error() {
        errors.incrementAndGet();
        lastSeenAt = System.currentTimeMillis();
    }

    public void up(long n) {
        if (n > 0) bytesUp.addAndGet(n);
    }

    public void down(long n) {
        if (n > 0) bytesDown.addAndGet(n);
    }

    public String remote() {
        return remote;
    }

    public long requests() {
        return requests.get();
    }

    public long active() {
        return active.get();
    }

    public long errors() {
        return errors.get();
    }

    public long bytesUp() {
        return bytesUp.get();
    }

    public long bytesDown() {
        return bytesDown.get();
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }
}
