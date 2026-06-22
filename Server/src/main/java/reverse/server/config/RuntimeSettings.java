package reverse.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class RuntimeSettings {
    private final int controlPort;
    private final String adminBind;
    private final int adminPort;
    private final Path configFile;

    public RuntimeSettings(
        @Value("${CONTROL_PORT:${reverse.control-port:7443}}") int controlPort,
        @Value("${server.address:0.0.0.0}") String adminBind,
        @Value("${server.port:8080}") int adminPort,
        @Value("${CONFIG_FILE:${reverse.config-file:server-config.json}}") String configFile
    ) {
        this.controlPort = controlPort;
        this.adminBind = adminBind;
        this.adminPort = adminPort;
        this.configFile = Path.of(configFile);
    }

    public int controlPort() {
        return controlPort;
    }

    public String adminBind() {
        return adminBind;
    }

    public int adminPort() {
        return adminPort;
    }

    public Path configFile() {
        return configFile;
    }
}
