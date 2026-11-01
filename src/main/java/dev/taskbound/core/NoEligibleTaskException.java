package dev.taskbound.core;

public final class NoEligibleTaskException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public NoEligibleTaskException(String message) {
        super(message);
    }
}
