package pe.axiz.payment.adapter.out.psp;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;

/**
 * Estado de una autorización lógica compartido por todos sus intentos físicos.
 *
 * <p>Permite que el fallback distinga entre "el PSP seguro no procesó el pago" y "no sé qué pasó":
 * si al vencer el presupuesto hay un intento en vuelo, o algún intento se cortó después de conectar,
 * el PSP pudo haber autorizado y el resultado es {@code UNKNOWN}.
 *
 * <p>El deadline evita intentos huérfanos: cuando el TimeLimiter ya respondió al cliente, Retry puede
 * seguir programando reintentos en segundo plano. Esos reintentos se cortan antes de salir al PSP.
 */
public final class PspInvocation {

    private static final Logger LOGGER = LoggerFactory.getLogger(PspInvocation.class);
    private static final int IDEMPOTENCY_CONFLICT = 409;

    private final Payment payment;
    private final long deadlineNanos;
    private final AtomicInteger attemptsInFlight = new AtomicInteger();
    private final AtomicBoolean ambiguousAttempt = new AtomicBoolean();

    PspInvocation(Payment payment, Duration timeBudget) {
        this.payment = payment;
        this.deadlineNanos = System.nanoTime() + timeBudget.toNanos();
    }

    PaymentProviderResult attempt(PspHttpClient pspHttpClient) {
        // Se marca en vuelo antes de revisar el deadline: si el fallback lee el contador en medio,
        // lo peor que puede pasar es responder UNKNOWN de más, nunca NOT_PROCESSED de más.
        attemptsInFlight.incrementAndGet();
        try {
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new PspDeadlineExceededException(
                        "Intento descartado: el presupuesto de tiempo del pago " + payment.id() + " ya venció");
            }
            var response = pspHttpClient.authorize(payment);
            return PaymentProviderResult.authorized(response.authorizationId(), response.replayed());
        } catch (PspBusinessException exception) {
            // Un rechazo de negocio es una respuesta válida del PSP: no pasa por Retry ni por el fallback.
            return exception.status() == IDEMPOTENCY_CONFLICT
                    ? PaymentProviderResult.idempotencyConflict(
                            "La Idempotency-Key ya fue usada con otros datos de pago")
                    : PaymentProviderResult.rejected(exception.getMessage());
        } catch (PspNetworkException exception) {
            if (exception.requestMayHaveReachedPsp()) {
                ambiguousAttempt.set(true);
            }
            throw exception;
        } finally {
            attemptsInFlight.decrementAndGet();
        }
    }

    PaymentProviderResult fallback(Throwable throwable) {
        Throwable cause = RootCause.unwrap(throwable);
        String reason = cause.getClass().getSimpleName();
        boolean timedOutWithAttemptInFlight = cause instanceof TimeoutException && attemptsInFlight.get() > 0;
        boolean outcomeUnknown = timedOutWithAttemptInFlight || ambiguousAttempt.get();

        LOGGER.warn("Fallback para pago {} por {} ({}): {}",
                payment.id(), reason, outcomeUnknown ? "resultado desconocido" : "no procesado",
                cause.getMessage());

        return outcomeUnknown
                ? PaymentProviderResult.unknown(
                        "Resultado desconocido: la llamada al PSP pudo completarse después de " + reason
                                + ". Reintente con la misma Idempotency-Key para conocer el estado final")
                : PaymentProviderResult.notProcessed(
                        "El pago no fue procesado. La protección de resiliencia terminó la llamada por " + reason);
    }
}
