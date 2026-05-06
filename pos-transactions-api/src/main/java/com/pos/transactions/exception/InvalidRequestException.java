package com.pos.transactions.exception;

/**
 * Lançada quando a requisição está semanticamente malformada — por exemplo,
 * quando o corpo do void não contém nem {@code transactionId} nem o par
 * {@code nsu + terminalId}.
 *
 * <p>Mapeada para {@code 400 Bad Request} pelo {@code GlobalExceptionHandler}.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
