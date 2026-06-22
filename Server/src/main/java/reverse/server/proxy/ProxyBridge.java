package reverse.server.proxy;

import reverse.server.config.RouteConfig;
import reverse.server.stats.StatsService;

import javax.net.ssl.SSLSocket;
import java.net.Socket;

import static reverse.server.util.Sockets.close;

public final class ProxyBridge {
    private ProxyBridge() {
    }

    public static void tcp(RouteConfig route, StatsService stats, String consumer, Socket codex, Socket tunnel) throws InterruptedException {
        StreamPipe.both(route, stats, consumer, codex, tunnel);
    }

    public static void http(RouteConfig route, StatsService stats, String consumer, long id, Socket codex, Socket tunnel) throws Exception {
        String request = HttpTools.forwardFirstRequest(route, stats, consumer, codex, tunnel, route.target(), false);
        System.out.println("[S] " + route.getId() + "#" + id + " " + request);
        StreamPipe.both(route, stats, consumer, codex, tunnel);
    }

    public static void https(RouteConfig route, StatsService stats, String consumer, long id, Socket codex, Socket tunnel) throws Exception {
        SSLSocket ssl = Tls.open(tunnel, route);
        try {
            String request = HttpTools.forwardFirstRequest(route, stats, consumer, codex, ssl, route.tlsName(), route.getMode().clean());
            System.out.println("[S] " + route.getId() + "#" + id + " " + request);
            StreamPipe.both(route, stats, consumer, codex, ssl);
        } finally {
            close(ssl);
        }
    }
}
