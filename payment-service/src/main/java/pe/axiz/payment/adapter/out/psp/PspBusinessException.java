package pe.axiz.payment.adapter.out.psp;

public class PspBusinessException extends RuntimeException {

    private final int status;

    public PspBusinessException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
