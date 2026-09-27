package pe.axiz.payment.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService paymentVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService resilienceScheduler() {
        return Executors.newScheduledThreadPool(
                4,
                Thread.ofPlatform().name("resilience-scheduler-", 0).factory());
    }
}
