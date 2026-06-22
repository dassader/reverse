package reverse.server.config;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {
    private List<RouteConfig> routes = new ArrayList<>();

    public static ServerConfig defaults() {
        ServerConfig config = new ServerConfig();
        config.routes.add(new RouteConfig("mcp", "0.0.0.0", 7777, "127.0.0.1", 64343, ProxyMode.HTTP, "", true));
        return config;
    }

    public List<RouteConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteConfig> routes) {
        this.routes = routes == null ? new ArrayList<>() : routes;
    }
}
