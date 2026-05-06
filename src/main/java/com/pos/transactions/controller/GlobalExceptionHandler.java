package com.pos.transactions.controller;

import com.pos.transactions.dto.ErrorResponse;
import com.pos.transactions.exception.CircuitBreakerOpenException;
import com.pos.transactions.exception.HmacValidationException;
import com.pos.transactions.exception.InvalidTransactionStateException;
import com.pos.transactions.exception.TransactionNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TransactionNotFoundException ex) {
        log.warn("[ERROR] Transação não encontrada: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, "Transação não encontrada", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTransactionStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidTransactionStateException ex) {
        log.warn("[ERROR] Estado inválido da transação: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildError(HttpStatus.UNPROCESSABLE_ENTITY, "Estado inválido", ex.getMessage()));
    }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreaker(CircuitBreakerOpenException ex) {
        log.error("[ERROR] Circuit breaker aberto: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildError(HttpStatus.SERVICE_UNAVAILABLE, "Serviço temporariamente indisponível", ex.getMessage()));
    }

    @ExceptionHandler(HmacValidationException.class)
    public ResponseEntity<ErrorResponse> handleHmac(HmacValidationException ex) {
        log.warn("[SECURITY] Assinatura HMAC inválida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Assinatura inválida", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .correlationId(MDC.get("correlationId"))
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Requisição inválida")
                        .message("Campos obrigatórios ausentes ou inválidos")
                        .details(details)
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("[ERROR] Erro inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado."));
    }

    private ErrorResponse buildError(HttpStatus status, String error, String message) {
        return ErrorResponse.builder()
                .correlationId(MDC.get("correlationId"))
                .status(status.value())
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
