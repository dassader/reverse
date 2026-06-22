package reverse.server.stats;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class RouteStats {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong active = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong bytesUp = new AtomicLong();
    private final AtomicLong bytesDown = new AtomicLong();
    private final ConcurrentMap<String, ConsumerStats> consumers = new ConcurrentHashMap<>();
    private volatile long lastRequestAt;
    private volatile long lastErrorAt;
    private volatile String lastRequest = "-";
    private volatile String listenerState = "stopped";

    public void request(String remote) {
        requests.incrementAndGet();
        active.incrementAndGet();
        lastRequestAt = System.currentTimeMillis();
        lastRequest = remote;
        consumer(remote).request();
    }

    public void done(String remote) {
        active.updateAndGet(value -> Math.max(0, value - 1));
        consumer(remote).done();
    }

    public void error(String remote) {
        errors.incrementAndGet();
        lastErrorAt = System.currentTimeMillis();
        consumer(remote).error();
    }

    public void up(String remote, long n) {
        if (n > 0) bytesUp.addAndGet(n);
        consumer(remote).up(n);
    }

    public void down(String remote, long n) {
        if (n > 0) bytesDown.addAndGet(n);
        consumer(remote).down(n);
    }

    public ConsumerStats consumer(String remote) {
        return consumers.computeIfAbsent(remote == null || remote.isBlank() ? "unknown" : remote, ConsumerStats::new);
    }

    public List<ConsumerStats> consumers() {
        return consumers.values().stream()
            .sorted(Comparator.comparingLong(ConsumerStats::lastSeenAt).reversed())
            .toList();
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
