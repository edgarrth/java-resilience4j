package pe.axiz.payment.adapter.in.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.axiz.payment.adapter.in.rest.dto.PaymentRequest;
import pe.axiz.payment.adapter.in.rest.dto.PaymentResponse;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.application.port.in.ProcessPaymentCommand;
import pe.axiz.payment.application.port.in.ProcessPaymentUseCase;
import pe.axiz.payment.domain.model.PaymentResult;
import pe.axiz.payment.domain.model.PaymentStatus;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
    }

    @PostMapping("/programmatic")
    public ResponseEntity<PaymentResponse> processProgrammatic(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(defaultValue = "PER_REQUEST") CompositionMode composition,
            @RequestParam(defaultValue = "SEMAPHORE") IsolationMode isolation,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResult result = process(new ProcessPaymentCommand(
                request.merchantId(), request.amount(), request.currency(), idempotencyKey,
                ExecutionStyle.PROGRAMMATIC, composition, isolation));
        return response(result);
    }

    @PostMapping("/declarative")
    public ResponseEntity<PaymentResponse> processDeclarative(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResult result = process(new ProcessPaymentCommand(
                request.merchantId(), request.amount(), request.currency(), idempotencyKey,
                ExecutionStyle.DECLARATIVE, CompositionMode.PER_REQUEST, IsolationMode.SEMAPHORE));
        return response(result);
    }

    private PaymentResult process(ProcessPaymentCommand command) {
        return processPaymentUseCase.process(command);
    }

    private ResponseEntity<PaymentResponse> response(PaymentResult result) {
        return ResponseEntity.status(httpStatus(result.status())).body(PaymentResponse.from(result));
    }

    private static HttpStatus httpStatus(PaymentStatus status) {
        return switch (status) {
            case AUTHORIZED -> HttpStatus.OK;
            // Aceptado pero sin estado final: el cliente consulta reenviando la misma Idempotency-Key.
            case UNKNOWN -> HttpStatus.ACCEPTED;
            case REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case NOT_PROCESSED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
