package reverse.server;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reverse.server.config.RuntimeSettings;
import reverse.server.proxy.ProxyRuntime;
import reverse.server.util.Net;

@SpringBootApplication
public class ReverseServerApplication implements ApplicationRunner {
    private final RuntimeSettings settings;
    private final ProxyRuntime runtime;

    public ReverseServerApplication(RuntimeSettings settings, ProxyRuntime runtime) {
        this.settings = settings;
        this.runtime = runtime;
    }

    public static void main(String[] args) {
        SpringApplication.run(ReverseServerApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        runtime.start();
        System.out.println("Client port: 0.0.0.0:" + settings.controlPort());
        for (String ip : Net.localIps()) {
            System.out.println("Client: java -cp Client Client " + ip + " " + settings.controlPort());
            System.out.println("Admin:  http://" + ip + ":" + settings.adminPort() + "/");
        }
        runtime.routes().forEach(route -> {
            System.out.println("Route " + route.getId() + ": " + route.getBind() + ":" + route.getPublicPort()
                + " -> " + route.getMode().label() + " " + route.target());
            for (String ip : Net.localIps()) {
                if (route.getMode().http()) System.out.println("Codex:  http://" + ip + ":" + route.getPublicPort() + "/stream");
                else System.out.println("TCP:    " + ip + ":" + route.getPublicPort());
            }
        });
    }
}
