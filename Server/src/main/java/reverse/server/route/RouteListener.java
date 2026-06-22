package reverse.server.route;

import reverse.server.client.ClientHub;
import reverse.server.config.RouteConfig;
import reverse.server.proxy.ProxySession;
import reverse.server.stats.StatsService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import static reverse.server.util.Sockets.close;

public class RouteListener {
    private final RouteConfig route;
    private final ClientHub client;
    private final StatsService stats;
    private final ExecutorService io;
    private volatile ServerSocket server;
    private volatile boolean running;

    public RouteListener(RouteConfig route, ClientHub client, StatsService stats, ExecutorService io) {
        this.route = route;
        this.client = client;
        this.stats = stats;
        this.io = io;
    }

    public void start() {
        running = true;
        io.execute(this::listen);
    }

    public void stop() {
        running = false;
        close(server);
        stats.listenerState(route.getId(), "stopped");
    }

    private void listen() {
        try (ServerSocket socket = new ServerSocket()) {
            server = socket;
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName(route.getBind()), route.getPublicPort()));
            stats.listenerState(route.getId(), "listening");
            System.out.println("[S] route " + route.getId() + " listening " + route.getBind() + ":" + route.getPublicPort()
                + " -> " + route.getMode().label() + " " + route.target());
            while (running) {
                Socket codex = socket.accept();
                codex.setTcpNoDelay(true);
                io.execute(new ProxySession(route, client, stats, codex));
            }
        } catch (IOException e) {
            if (running) {
                stats.listenerState(route.getId(), "error: " + e.getMessage());
                System.out.println("[S] route " + route.getId() + " failed: " + e.getMessage());
            }
        } finally {
            if (running) running = false;
        }
    }
}
