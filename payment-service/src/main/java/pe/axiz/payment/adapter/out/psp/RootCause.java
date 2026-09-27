package pe.axiz.payment.adapter.out.psp;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class RootCause {

    private RootCause() {
    }

    static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
