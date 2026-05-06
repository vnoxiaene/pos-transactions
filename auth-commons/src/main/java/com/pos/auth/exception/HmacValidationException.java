package com.pos.auth.exception;

/**
 * Exception thrown when HMAC signature validation fails.
 *
 * Note: This exception is not actively used in normal flow, as HmacSignatureFilter
 * writes the HTTP 401 response directly. Kept for potential future use and API consistency.
 */
public class HmacValidationException extends RuntimeException {

    public HmacValidationException(String message) {
        super(message);
    }

    public HmacValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
