package pe.axiz.payment.adapter.out.psp;

public class PspBusinessException extends RuntimeException {

    public PspBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
