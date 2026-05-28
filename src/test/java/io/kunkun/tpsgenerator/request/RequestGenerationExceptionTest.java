package io.kunkun.tpsgenerator.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RequestGenerationException.
 */
class RequestGenerationExceptionTest {

    @Test
    @DisplayName("Should carry the message passed to constructor")
    void shouldCarryMessage() {
        RequestGenerationException ex = new RequestGenerationException("test error");
        assertEquals("test error", ex.getMessage());
    }

    @Test
    @DisplayName("Should carry both message and cause")
    void shouldCarryMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        RequestGenerationException ex = new RequestGenerationException("wrapper", cause);
        assertEquals("wrapper", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("Should be a RuntimeException subtype")
    void shouldBeRuntimeException() {
        RequestGenerationException ex = new RequestGenerationException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("Cause is null when using message-only constructor")
    void causeIsNullWithMessageOnlyConstructor() {
        RequestGenerationException ex = new RequestGenerationException("no cause");
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("Should preserve cause chain across multiple wrappings")
    void shouldPreserveCauseChain() {
        IllegalArgumentException root = new IllegalArgumentException("root");
        RuntimeException middle = new RuntimeException("middle", root);
        RequestGenerationException ex = new RequestGenerationException("top", middle);

        assertSame(middle, ex.getCause());
        assertSame(root, ex.getCause().getCause());
    }
}
