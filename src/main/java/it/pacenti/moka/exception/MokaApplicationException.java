package it.pacenti.moka.exception;

/**
 * Base exception for application-layer errors in Moka.
 */
public class MokaApplicationException extends RuntimeException {

    public MokaApplicationException(String message) {
        super(message);
    }

    public MokaApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}