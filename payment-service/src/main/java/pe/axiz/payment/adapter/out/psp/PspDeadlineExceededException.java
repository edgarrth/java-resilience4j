package pe.axiz.payment.adapter.out.psp;

public class PspDeadlineExceededException extends RuntimeException {

    public PspDeadlineExceededException(String message) {
        super(message);
    }
}
