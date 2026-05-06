package com.pos.transactions.exception;

public class HmacValidationException extends RuntimeException {
    public HmacValidationException(String message) {
        super(message);
    }
}
