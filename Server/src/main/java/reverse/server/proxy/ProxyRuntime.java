package reverse.server.proxy;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import reverse.server.client.ClientHub;
import reverse.server.config.RouteConfig;
import reverse.server.config.RouteStore;
import reverse.server.route.RouteSupervisor;

import java.io.IOException;
import java.util.List;

@Component
public class ProxyRuntime {
    private final ClientHub client;
    private final RouteStore store;
    private final RouteSupervisor routes;
    private volatile boolean started;

    public ProxyRuntime(ClientHub client, RouteStore store, RouteSupervisor routes) {
        this.client = client;
        this.store = store;
        this.routes = routes;
    }

    public synchronized void start() throws IOException {
        if (started) return;
        client.start();
        routes.reload(store.routes());
        started = true;
    }

    public synchronized void reloadRoutes() {
        routes.reload(store.routes());
    }

    public List<RouteConfig> routes() {
        return store.routes();
    }

    @PreDestroy
    public void stop() {
        routes.stop();
    }
}
