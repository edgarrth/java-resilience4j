package pe.axiz.payment.resilience;

import java.time.Instant;

public record ResilienceEvent(
        Instant timestamp,
        String component,
        String instance,
        String eventType,
        String detail) {
}
