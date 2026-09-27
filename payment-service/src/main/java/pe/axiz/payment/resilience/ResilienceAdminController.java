package pe.axiz.payment.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/resilience")
public class ResilienceAdminController {

    private static final List<String> INSTANCES = List.of(
            "pspProgrammaticAttempt",
            "pspProgrammaticRequest",
            "pspDeclarative");

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;
    private final ResilienceEventJournal journal;

    public ResilienceAdminController(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            ResilienceEventJournal journal) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.threadPoolBulkheadRegistry = threadPoolBulkheadRegistry;
        this.journal = journal;
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String instance : INSTANCES) {
            snapshot.put(instance, instanceSnapshot(instance));
        }
        return snapshot;
    }

    @GetMapping("/events")
    public List<ResilienceEvent> events(@RequestParam(defaultValue = "50") int limit) {
        return journal.latest(limit);
    }

    @DeleteMapping("/events")
    public ResponseEntity<Void> clearEvents() {
        journal.clear();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/circuit-breakers/{name}/reset")
    public Map<String, String> reset(@PathVariable String name) {
        CircuitBreaker circuitBreaker = circuitBreaker(name);
        circuitBreaker.reset();
        return Map.of("name", name, "state", circuitBreaker.getState().name());
    }

    @PostMapping("/circuit-breakers/{name}/force-open")
    public Map<String, String> forceOpen(@PathVariable String name) {
        CircuitBreaker circuitBreaker = circuitBreaker(name);
        circuitBreaker.transitionToForcedOpenState();
        return Map.of("name", name, "state", circuitBreaker.getState().name());
    }

    @PostMapping("/circuit-breakers/{name}/metrics-only")
    public Map<String, String> metricsOnly(@PathVariable String name) {
        CircuitBreaker circuitBreaker = circuitBreaker(name);
        circuitBreaker.transitionToMetricsOnlyState();
        return Map.of("name", name, "state", circuitBreaker.getState().name());
    }

    @PostMapping("/circuit-breakers/{name}/disable")
    public Map<String, String> disable(@PathVariable String name) {
        CircuitBreaker circuitBreaker = circuitBreaker(name);
        circuitBreaker.transitionToDisabledState();
        return Map.of("name", name, "state", circuitBreaker.getState().name());
    }

    private Map<String, Object> instanceSnapshot(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        var circuitMetrics = circuitBreaker.getMetrics();
        var retryMetrics = retryRegistry.retry(name).getMetrics();
        var rateMetrics = rateLimiterRegistry.rateLimiter(name).getMetrics();
        var bulkheadMetrics = bulkheadRegistry.bulkhead(name).getMetrics();
        var threadPoolMetrics = threadPoolBulkheadRegistry.bulkhead(name).getMetrics();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("circuitBreaker", Map.of(
                "state", circuitBreaker.getState().name(),
                "failureRate", circuitMetrics.getFailureRate(),
                "slowCallRate", circuitMetrics.getSlowCallRate(),
                "bufferedCalls", circuitMetrics.getNumberOfBufferedCalls(),
                "failedCalls", circuitMetrics.getNumberOfFailedCalls(),
                "slowCalls", circuitMetrics.getNumberOfSlowCalls(),
                "notPermittedCalls", circuitMetrics.getNumberOfNotPermittedCalls()));
        result.put("retry", Map.of(
                "successWithoutRetry", retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                "successWithRetry", retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                "failureWithoutRetry", retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt(),
                "failureWithRetry", retryMetrics.getNumberOfFailedCallsWithRetryAttempt()));
        result.put("rateLimiter", Map.of(
                "availablePermissions", rateMetrics.getAvailablePermissions(),
                "waitingThreads", rateMetrics.getNumberOfWaitingThreads()));
        result.put("semaphoreBulkhead", Map.of(
                "availableConcurrentCalls", bulkheadMetrics.getAvailableConcurrentCalls(),
                "maxAllowedConcurrentCalls", bulkheadMetrics.getMaxAllowedConcurrentCalls()));
        result.put("threadPoolBulkhead", Map.of(
                "queueDepth", threadPoolMetrics.getQueueDepth(),
                "queueCapacity", threadPoolMetrics.getQueueCapacity(),
                "remainingQueueCapacity", threadPoolMetrics.getRemainingQueueCapacity(),
                "threadPoolSize", threadPoolMetrics.getThreadPoolSize(),
                "coreThreadPoolSize", threadPoolMetrics.getCoreThreadPoolSize(),
                "maximumThreadPoolSize", threadPoolMetrics.getMaximumThreadPoolSize()));
        return result;
    }

    private CircuitBreaker circuitBreaker(String name) {
        if (!INSTANCES.contains(name)) {
            throw new IllegalArgumentException("Circuit Breaker no permitido: " + name);
        }
        return circuitBreakerRegistry.circuitBreaker(name);
    }
}
