package pe.axiz.payment.application.port.in;

import java.math.BigDecimal;

public record ProcessPaymentCommand(
        String merchantId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        ExecutionStyle executionStyle,
        CompositionMode compositionMode,
        IsolationMode isolationMode) {
}
