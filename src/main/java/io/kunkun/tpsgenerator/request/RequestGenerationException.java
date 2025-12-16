package io.kunkun.tpsgenerator.request;

/**
 * Exception thrown when request generation fails.
 */
public class RequestGenerationException extends RuntimeException {

    /**
     * Creates a new RequestGenerationException with a message.
     *
     * @param message the error message
     */
    public RequestGenerationException(String message) {
        super(message);
    }

    /**
     * Creates a new RequestGenerationException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public RequestGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
