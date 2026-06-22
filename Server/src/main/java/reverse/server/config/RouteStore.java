package reverse.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class RouteStore {
    private final ObjectMapper mapper;
    private final Path file;
    private ServerConfig config;

    public RouteStore(ObjectMapper mapper, RuntimeSettings settings) throws IOException {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.file = settings.configFile();
        this.config = load();
    }

    public synchronized List<RouteConfig> routes() {
        return config.getRoutes().stream()
            .map(RouteStore::copy)
            .sorted(Comparator.comparing(RouteConfig::getId))
            .toList();
    }

    public synchronized Optional<RouteConfig> route(String id) {
        return config.getRoutes().stream()
            .filter(route -> route.getId().equals(id))
            .findFirst()
            .map(RouteStore::copy);
    }

    public synchronized void save(RouteConfig route) throws IOException {
        RouteConfig normalized = copy(route).normalized();
        config.getRoutes().removeIf(existing -> existing.getId().equals(normalized.getId()));
        config.getRoutes().add(normalized);
        write();
    }

    public synchronized void delete(String id) throws IOException {
        config.getRoutes().removeIf(route -> route.getId().equals(id));
        write();
    }

    public synchronized void setEnabled(String id, boolean enabled) throws IOException {
        for (RouteConfig route : config.getRoutes()) {
            if (route.getId().equals(id)) {
                route.setEnabled(enabled);
                write();
                return;
            }
        }
    }

    public Path file() {
        return file;
    }

    private ServerConfig load() throws IOException {
        if (!Files.exists(file)) {
            ServerConfig defaults = ServerConfig.defaults();
            write(defaults);
            return defaults;
        }
        ServerConfig loaded = mapper.readValue(file.toFile(), ServerConfig.class);
        for (RouteConfig route : loaded.getRoutes()) route.normalized();
        return loaded;
    }

    private void write() throws IOException {
        write(config);
    }

    private void write(ServerConfig value) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null && !Files.isDirectory(parent)) Files.createDirectories(parent);
        mapper.writeValue(file.toFile(), value);
    }

    private static RouteConfig copy(RouteConfig route) {
        return new RouteConfig(
            route.getId(),
            route.getBind(),
            route.getPublicPort(),
            route.getTargetHost(),
            route.getTargetPort(),
            route.getMode(),
            route.getTlsHost(),
            route.isEnabled()
        );
    }
}
