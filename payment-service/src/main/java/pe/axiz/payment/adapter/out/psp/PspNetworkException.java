package pe.axiz.payment.adapter.out.psp;

public class PspNetworkException extends RuntimeException {

    private final boolean requestMayHaveReachedPsp;

    public PspNetworkException(String message, boolean requestMayHaveReachedPsp, Throwable cause) {
        super(message, cause);
        this.requestMayHaveReachedPsp = requestMayHaveReachedPsp;
    }

    /**
     * {@code false} solo cuando la conexión nunca se estableció. Un reset o un corte a mitad de la
     * respuesta no permite saber si el PSP procesó la autorización.
     */
    public boolean requestMayHaveReachedPsp() {
        return requestMayHaveReachedPsp;
    }
}
