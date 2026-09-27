package pe.axiz.psp.application;

public class SimulatedPspException extends RuntimeException {

    private final int status;
    private final int retryAfterSeconds;

    public SimulatedPspException(int status, int retryAfterSeconds, String message) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status() {
        return status;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
