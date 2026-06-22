package reverse.server.stats;

import java.util.concurrent.atomic.AtomicLong;

public class RouteStats {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong active = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong bytesUp = new AtomicLong();
    private final AtomicLong bytesDown = new AtomicLong();
    private volatile long lastRequestAt;
    private volatile long lastErrorAt;
    private volatile String lastRequest = "-";
    private volatile String listenerState = "stopped";

    public void request(String remote) {
        requests.incrementAndGet();
        active.incrementAndGet();
        lastRequestAt = System.currentTimeMillis();
        lastRequest = remote;
    }

    public void done() {
        active.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void error() {
        errors.incrementAndGet();
        lastErrorAt = System.currentTimeMillis();
    }

    public void up(long n) {
        if (n > 0) bytesUp.addAndGet(n);
    }

    public void down(long n) {
        if (n > 0) bytesDown.addAndGet(n);
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

    public long lastRequestAt() {
        return lastRequestAt;
    }

    public long lastErrorAt() {
        return lastErrorAt;
    }

    public String lastRequest() {
        return lastRequest;
    }

    public String listenerState() {
        return listenerState;
    }

    public void listenerState(String listenerState) {
        this.listenerState = listenerState;
    }
}
