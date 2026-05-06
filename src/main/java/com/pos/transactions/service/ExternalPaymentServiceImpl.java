package com.pos.transactions.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class ExternalPaymentServiceImpl implements ExternalPaymentService {

    private static final String EXTERNAL_API_CB = "externalPaymentApi";

    @Override
    @CircuitBreaker(name = EXTERNAL_API_CB, fallbackMethod = "authorizeFallback")
    @Retry(name = EXTERNAL_API_CB)
    @Bulkhead(name = EXTERNAL_API_CB)
    public void authorize(String transactionId, String terminalId, String nsu, BigDecimal amount) {
        log.info("[EXTERNAL-API] Autorizando transação: transactionId={}, terminalId={}, nsu={}, amount={}",
                transactionId, terminalId, nsu, amount);
        simulateExternalCall();
        log.info("[EXTERNAL-API] Autorização bem-sucedida: transactionId={}", transactionId);
    }

    @Override
    @CircuitBreaker(name = EXTERNAL_API_CB, fallbackMethod = "confirmFallback")
    @Retry(name = EXTERNAL_API_CB)
    @Bulkhead(name = EXTERNAL_API_CB)
    public void confirm(String transactionId) {
        log.info("[EXTERNAL-API] Confirmando transação: transactionId={}", transactionId);
        simulateExternalCall();
        log.info("[EXTERNAL-API] Confirmação bem-sucedida: transactionId={}", transactionId);
    }

    @Override
    @CircuitBreaker(name = EXTERNAL_API_CB, fallbackMethod = "voidFallback")
    @Retry(name = EXTERNAL_API_CB)
    @Bulkhead(name = EXTERNAL_API_CB)
    public void voidTransaction(String transactionId) {
        log.info("[EXTERNAL-API] Desfazendo transação: transactionId={}", transactionId);
        simulateExternalCall();
        log.info("[EXTERNAL-API] Desfazimento bem-sucedido: transactionId={}", transactionId);
    }

    private void simulateExternalCall() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void authorizeFallback(String transactionId, String terminalId, String nsu, BigDecimal amount, Exception ex) {
        log.error("[EXTERNAL-API] Circuit breaker aberto para authorize. transactionId={}, erro={}",
                transactionId, ex.getMessage());
        throw new com.pos.transactions.exception.CircuitBreakerOpenException(
                "Serviço externo indisponível. Tente novamente mais tarde.");
    }

    public void confirmFallback(String transactionId, Exception ex) {
        log.error("[EXTERNAL-API] Circuit breaker aberto para confirm. transactionId={}, erro={}",
                transactionId, ex.getMessage());
        throw new com.pos.transactions.exception.CircuitBreakerOpenException(
                "Serviço externo indisponível. Tente novamente mais tarde.");
    }

    public void voidFallback(String transactionId, Exception ex) {
        log.error("[EXTERNAL-API] Circuit breaker aberto para void. transactionId={}, erro={}",
                transactionId, ex.getMessage());
        throw new com.pos.transactions.exception.CircuitBreakerOpenException(
                "Serviço externo indisponível. Tente novamente mais tarde.");
    }
}
