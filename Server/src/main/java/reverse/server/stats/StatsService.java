package reverse.server.stats;

import org.springframework.stereotype.Component;
import reverse.server.config.RouteConfig;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static reverse.server.util.Sockets.addr;

@Component
public class StatsService {
    private final Map<String, RouteStats> routes = new ConcurrentHashMap<>();
    private final AtomicLong streams = new AtomicLong();
    private final AtomicLong activeStreams = new AtomicLong();
    private final AtomicLong finishedStreams = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong bytesUp = new AtomicLong();
    private final AtomicLong bytesDown = new AtomicLong();
    private final AtomicLong clientConnects = new AtomicLong();
    private final AtomicLong pings = new AtomicLong();
    private final AtomicLong pongs = new AtomicLong();
    private final AtomicLong dataSockets = new AtomicLong();
    private final AtomicLong droppedDataSockets = new AtomicLong();
    private volatile boolean clientConnected;
    private volatile String clientRemote = "-";
    private volatile long clientConnectedAt;
    private volatile long lastPongAt;

    public long start(RouteConfig route, String consumer) {
        long id = streams.incrementAndGet();
        activeStreams.incrementAndGet();
        route(route.getId()).request(consumer);
        return id;
    }

    public void finish(RouteConfig route, String consumer) {
        finishedStreams.incrementAndGet();
        activeStreams.updateAndGet(value -> Math.max(0, value - 1));
        route(route.getId()).done(consumer);
    }

    public void error(RouteConfig route, String consumer) {
        errors.incrementAndGet();
        route(route.getId()).error(consumer);
    }

    public void up(RouteConfig route, String consumer, long n) {
        if (n <= 0) return;
        bytesUp.addAndGet(n);
        route(route.getId()).up(consumer, n);
    }

    public void down(RouteConfig route, String consumer, long n) {
        if (n <= 0) return;
        bytesDown.addAndGet(n);
        route(route.getId()).down(consumer, n);
    }

    public RouteStats route(String id) {
        return routes.computeIfAbsent(id, ignored -> new RouteStats());
    }

    public void listenerState(String id, String state) {
        route(id).listenerState(state);
    }

    public void clientConnected(Socket socket) {
        clientConnected = true;
        clientRemote = addr(socket);
        clientConnectedAt = System.currentTimeMillis();
        lastPongAt = clientConnectedAt;
        clientConnects.incrementAndGet();
    }

    public void clientDisconnected() {
        clientConnected = false;
        clientRemote = "-";
    }

    public void ping() {
        pings.incrementAndGet();
    }

    public void pong(long at) {
        lastPongAt = at;
        pongs.incrementAndGet();
    }

    public void dataSocket() {
        dataSockets.incrementAndGet();
    }

    public void droppedDataSocket() {
        droppedDataSockets.incrementAndGet();
    }

    public long streams() {
        return streams.get();
    }

    public long activeStreams() {
        return activeStreams.get();
    }

    public long finishedStreams() {
        return finishedStreams.get();
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

    public long clientConnects() {
        return clientConnects.get();
    }

    public long pings() {
        return pings.get();
    }

    public long pongs() {
        return pongs.get();
    }

    public long dataSockets() {
        return dataSockets.get();
    }

    public long droppedDataSockets() {
        return droppedDataSockets.get();
    }

    public boolean clientConnected() {
        return clientConnected;
    }

    public String clientRemote() {
        return clientRemote;
    }

    public long clientConnectedAt() {
        return clientConnectedAt;
    }

    public long lastPongAt() {
        return lastPongAt;
    }
}
