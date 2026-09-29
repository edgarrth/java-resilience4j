package pe.axiz.psp.adapter.in.rest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.axiz.psp.application.IdempotencyConflictException;
import pe.axiz.psp.application.SimulatedPspException;

@RestControllerAdvice
public class PspExceptionHandler {

    @ExceptionHandler(SimulatedPspException.class)
    public ResponseEntity<Map<String, Object>> handleSimulated(SimulatedPspException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.status() == 429 && exception.retryAfterSeconds() > 0) {
            headers.set("Retry-After", String.valueOf(exception.retryAfterSeconds()));
        }
        return new ResponseEntity<>(error(exception.status(), "SIMULATED_FAILURE", exception.getMessage()),
                headers, HttpStatus.valueOf(exception.status()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error(409, "IDEMPOTENCY_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        Map<String, Object> body = error(400, "BAD_REQUEST", "La solicitud no cumple las validaciones");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(error(400, "BAD_REQUEST", exception.getMessage()));
    }

    private Map<String, Object> error(int status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status);
        body.put("error", code);
        body.put("message", message);
        return body;
    }
}
