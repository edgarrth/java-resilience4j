package pe.axiz.psp.adapter.in.rest;

import java.math.BigDecimal;
import java.time.Instant;
import pe.axiz.psp.application.PspSimulatorService.AuthorizationResult;

public record AuthorizationResponse(
        String authorizationId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String status,
        boolean replayed,
        String message,
        long callNumber,
        String scenario,
        Instant createdAt) {

    public static AuthorizationResponse from(AuthorizationResult result) {
        var authorization = result.authorization();
        return new AuthorizationResponse(
                authorization.authorizationId(),
                authorization.merchantId(),
                authorization.amount(),
                authorization.currency(),
                "AUTHORIZED",
                result.replayed(),
                result.replayed() ? "Respuesta idempotente existente" : "Autorización creada",
                result.callNumber(),
                result.scenario(),
                authorization.createdAt());
    }
}
