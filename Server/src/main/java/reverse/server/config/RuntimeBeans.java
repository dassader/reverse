package reverse.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class RuntimeBeans {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService ioExecutor() {
        return Executors.newCachedThreadPool();
    }
}
