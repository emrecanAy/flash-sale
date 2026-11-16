package com.emrecanay.flashsale.shared;

/**
 * A business rule said no. Carries a stable machine-readable {@code code} for API clients
 * and a {@link Kind} that the web layer maps to an HTTP status, so feature modules never
 * have to know about HTTP.
 */
public abstract class DomainException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, UNPROCESSABLE }

    private final Kind kind;
    private final String code;

    protected DomainException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
