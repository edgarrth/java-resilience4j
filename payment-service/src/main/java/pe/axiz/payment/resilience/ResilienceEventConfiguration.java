package pe.axiz.payment.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.List;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceEventConfiguration {

    private static final List<String> INSTANCES = List.of(
            "pspProgrammaticAttempt",
            "pspProgrammaticRequest",
            "pspDeclarative");

    public ResilienceEventConfiguration(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            ResilienceEventJournal journal) {

        for (String instance : INSTANCES) {
            circuitBreakerRegistry.circuitBreaker(instance).getEventPublisher()
                    .onEvent(event -> journal.add("circuit-breaker", instance, event));
            retryRegistry.retry(instance).getEventPublisher()
                    .onEvent(event -> journal.add("retry", instance, event));
            rateLimiterRegistry.rateLimiter(instance).getEventPublisher()
                    .onEvent(event -> journal.add("rate-limiter", instance, event));
            bulkheadRegistry.bulkhead(instance).getEventPublisher()
                    .onEvent(event -> journal.add("semaphore-bulkhead", instance, event));
            threadPoolBulkheadRegistry.bulkhead(instance).getEventPublisher()
                    .onEvent(event -> journal.add("thread-pool-bulkhead", instance, event));
            timeLimiterRegistry.timeLimiter(instance).getEventPublisher()
                    .onEvent(event -> journal.add("time-limiter", instance, event));
        }
    }
}
