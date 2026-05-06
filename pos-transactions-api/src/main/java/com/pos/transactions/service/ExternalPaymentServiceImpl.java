package com.pos.transactions.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Cliente HTTP do microserviço externo de pagamentos (external-payment-mock).
 *
 * Toda chamada passa pelas camadas de resiliência do Resilience4j:
 *   - Bulkhead       : máximo 10 chamadas concorrentes (maxWaitDuration 100 ms)
 *   - CircuitBreaker : abre com >= 50% de falhas em janela de 10 requisições
 *   - Retry          : até 3 tentativas com backoff exponencial (500 ms → 1 s → 2 s)
 *   - Timeout        : connect + read timeout configurados no HttpClient (padrão: 3 s)
 *
 * O timeout é aplicado diretamente no HttpClient (SimpleClientHttpRequestFactory) para
 * garantir que chamadas síncronas não fiquem penduradas indefinidamente, sem necessidade
 * de CompletableFuture exigida pela anotação @TimeLimiter do Resilience4j.
 */
@Service
public class ExternalPaymentServiceImpl implements ExternalPaymentService {

    private static final String EXTERNAL_API_CB = "externalPaymentApi";

    private final RestClient restClient;

    public ExternalPaymentServiceImpl(
            @Value("${external.payment.base-url:http://localhost:8081}") String baseUrl,
            @Value("${external.payment.timeout-ms:3000}") int timeoutMs,
            RestClient.Builder restClientBuilder) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        log.info("[EXTERNAL-API] Cliente configurado para: {} | timeout: {} ms", baseUrl, timeoutMs);
    }

    @Override
    @CircuitBreaker(name = EXTERNAL_API_CB, fallbackMethod = "authorizeFallback")
    @Retry(name = EXTERNAL_API_CB)
    @Bulkhead(name = EXTERNAL_API_CB)
    public void authorize(String transactionId, String terminalId, String nsu, BigDecimal amount) {
        log.info("[EXTERNAL-API] Autorizando: transactionId={}, terminalId={}, nsu={}, amount={}",
                transactionId, terminalId, nsu, amount);

        restClient.post()
                .uri("/api/payment/authorize")
                .body(new AuthorizePayload(transactionId, terminalId, nsu, amount))
                .retrieve()
                .toBodilessEntity();

        log.info("[EXTERNAL-API] Autorização bem-sucedida: transactionId={}", transactionId);
    }

    @Override
    @CircuitBreaker(name = EXTERNAL_API_CB, fallbackMethod = "confirmFallback")
    @Retry(name = EXTERNAL_API_CB)
    @Bulkhead(name = EXTERNAL_API_CB)
    public void confirm(String transactionId) {
        log.info("[EXTERNAL-API] Confirmando: transactionId={}", transactionId);

        restClient.post()
                .uri("/api/payment/confirm")
                .body(new TransactionPayload(transactionId))
                .retrieve()
                .toBodilessEntity();

        log.info("[EXTERNAL-API] Confirmação bem-sucedida: transactionId={}", transactionId);
    }

    @Override
    @CircuitBreaker(name = EXTERNAL_API_CB, fallbackMethod = "voidFallback")
    @Retry(name = EXTERNAL_API_CB)
    @Bulkhead(name = EXTERNAL_API_CB)
    public void voidTransaction(String transactionId) {
        log.info("[EXTERNAL-API] Desfazendo: transactionId={}", transactionId);

        restClient.post()
                .uri("/api/payment/void")
                .body(new TransactionPayload(transactionId))
                .retrieve()
                .toBodilessEntity();

        log.info("[EXTERNAL-API] Desfazimento bem-sucedido: transactionId={}", transactionId);
    }

    // ---------- Fallbacks ----------

    public void authorizeFallback(String transactionId, String terminalId, String nsu,
                                   BigDecimal amount, Exception ex) {
        log.error("[EXTERNAL-API] Falha ao autorizar. transactionId={}, erro={}", transactionId, ex.getMessage());
        throw new com.pos.transactions.exception.CircuitBreakerOpenException(
                "Serviço externo indisponível. Tente novamente mais tarde.");
    }

    public void confirmFallback(String transactionId, Exception ex) {
        log.error("[EXTERNAL-API] Falha ao confirmar. transactionId={}, erro={}", transactionId, ex.getMessage());
        throw new com.pos.transactions.exception.CircuitBreakerOpenException(
                "Serviço externo indisponível. Tente novamente mais tarde.");
    }

    public void voidFallback(String transactionId, Exception ex) {
        log.error("[EXTERNAL-API] Falha ao desfazer. transactionId={}, erro={}", transactionId, ex.getMessage());
        throw new com.pos.transactions.exception.CircuitBreakerOpenException(
                "Serviço externo indisponível. Tente novamente mais tarde.");
    }

    // ---------- Payload records (uso interno) ----------

    record AuthorizePayload(String transactionId, String terminalId, String nsu, BigDecimal amount) {}
    record TransactionPayload(String transactionId) {}
}
