package reverse.server.proxy;

import reverse.server.client.ClientHub;
import reverse.server.config.ProxyMode;
import reverse.server.config.RouteConfig;
import reverse.server.stats.StatsService;

import java.net.Socket;

import static reverse.server.util.Sockets.*;

public class ProxySession implements Runnable {
    private final RouteConfig route;
    private final ClientHub client;
    private final StatsService stats;
    private final Socket codex;

    public ProxySession(RouteConfig route, ClientHub client, StatsService stats, Socket codex) {
        this.route = route;
        this.client = client;
        this.stats = stats;
        this.codex = codex;
    }

    @Override
    public void run() {
        long id = stats.start(route, codex);
        boolean ok = false;
        System.out.println("[S] " + route.getId() + "#" + id + " + " + addr(codex));
        try {
            Socket tunnel = client.openTunnel(route);
            try {
                if (route.getMode() == ProxyMode.TCP) ProxyBridge.tcp(route, stats, codex, tunnel);
                else if (route.getMode() == ProxyMode.HTTP) ProxyBridge.http(route, stats, id, codex, tunnel);
                else ProxyBridge.https(route, stats, id, codex, tunnel);
                ok = true;
            } finally {
                client.release(tunnel);
                close(tunnel);
            }
        } catch (Exception e) {
            stats.error(route);
            System.out.println("[S] " + route.getId() + "#" + id + " error: " + shortError(e));
        } finally {
            close(codex);
            stats.finish(route);
            if (ok) System.out.println("[S] " + route.getId() + "#" + id + " done");
        }
    }
}
