package com.pos.external.controller;

import com.pos.external.dto.AuthorizePaymentRequest;
import com.pos.external.dto.PaymentResponse;
import com.pos.external.dto.TransactionPaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Mock do processador externo de pagamentos.
 *
 * Em produção, este serviço seria substituído pela integração real com
 * a adquirente / processadora de pagamentos (ex: Cielo, Rede, Stone).
 *
 * Endpoints:
 *   POST /api/payment/authorize  — autoriza a transação
 *   POST /api/payment/confirm    — captura/confirma a transação
 *   POST /api/payment/void       — desfaz a transação
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
public class PaymentMockController {

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorize(@RequestBody AuthorizePaymentRequest request) {
        log.info("[MOCK] authorize — transactionId={}, terminalId={}, nsu={}, amount={}",
                request.getTransactionId(), request.getTerminalId(),
                request.getNsu(), request.getAmount());

        return ResponseEntity.ok(PaymentResponse.of(request.getTransactionId(), "AUTHORIZED"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirm(@RequestBody TransactionPaymentRequest request) {
        log.info("[MOCK] confirm — transactionId={}", request.getTransactionId());

        return ResponseEntity.ok(PaymentResponse.of(request.getTransactionId(), "CONFIRMED"));
    }

    @PostMapping("/void")
    public ResponseEntity<PaymentResponse> voidTransaction(@RequestBody TransactionPaymentRequest request) {
        log.info("[MOCK] void — transactionId={}", request.getTransactionId());

        return ResponseEntity.ok(PaymentResponse.of(request.getTransactionId(), "VOIDED"));
    }
}
