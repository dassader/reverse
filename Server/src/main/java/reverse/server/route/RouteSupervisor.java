package reverse.server.route;

import org.springframework.stereotype.Component;
import reverse.server.client.ClientHub;
import reverse.server.config.RouteConfig;
import reverse.server.stats.StatsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Component
public class RouteSupervisor {
    private final ExecutorService io;
    private final ClientHub client;
    private final StatsService stats;
    private final Map<String, RouteListener> listeners = new HashMap<>();

    public RouteSupervisor(ExecutorService io, ClientHub client, StatsService stats) {
        this.io = io;
        this.client = client;
        this.stats = stats;
    }

    public synchronized void reload(List<RouteConfig> routes) {
        stop();
        for (RouteConfig route : routes) {
            if (!route.isEnabled()) {
                stats.listenerState(route.getId(), "disabled");
                continue;
            }
            RouteListener listener = new RouteListener(route, client, stats, io);
            listeners.put(route.getId(), listener);
            listener.start();
        }
    }

    public synchronized void stop() {
        for (RouteListener listener : listeners.values()) listener.stop();
        listeners.clear();
    }
}
