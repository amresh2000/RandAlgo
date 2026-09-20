package com.penguinsecure.basis.app.config;

/** Rejects an untrusted strategy definition before runtime publication. */
public final class StrategyDefinitionParseException extends Exception {
    public StrategyDefinitionParseException(final String message) {
        super(message);
    }

    public StrategyDefinitionParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
