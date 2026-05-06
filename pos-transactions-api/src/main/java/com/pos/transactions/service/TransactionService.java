package com.pos.transactions.service;

import com.pos.transactions.domain.Transaction;
import com.pos.transactions.domain.TransactionStatus;
import com.pos.transactions.dto.AuthorizeRequest;
import com.pos.transactions.dto.AuthorizeResponse;
import com.pos.transactions.dto.ConfirmRequest;
import com.pos.transactions.dto.VoidRequest;
import com.pos.transactions.exception.InvalidRequestException;
import com.pos.transactions.exception.InvalidTransactionStateException;
import com.pos.transactions.exception.TransactionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TODO.class);
public class TransactionService {

    private final com.pos.transactions.repository.TransactionRepository transactionRepository;
    private final ExternalPaymentService externalPaymentService;

    @Transactional
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        log.info("[AUTHORIZE] Processando autorização: terminalId={}, nsu={}, amount={}",
                request.getTerminalId(), request.getNsu(), request.getAmount());

        return transactionRepository
                .findByTerminalIdAndNsu(request.getTerminalId(), request.getNsu())
                .map(existing -> {
                    log.info("[AUTHORIZE] Idempotência: transação já existe. transactionId={}",
                            existing.getTransactionId());
                    return toAuthorizeResponse(existing);
                })
                .orElseGet(() -> createNewTransaction(request));
    }

    private AuthorizeResponse createNewTransaction(AuthorizeRequest request) {
        String transactionId = generateTransactionId();

        externalPaymentService.authorize(transactionId, request.getTerminalId(),
                request.getNsu(), request.getAmount());

        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .nsu(request.getNsu())
                .terminalId(request.getTerminalId())
                .amount(request.getAmount())
                .status(TransactionStatus.AUTHORIZED)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("[AUTHORIZE] Transação autorizada e persistida. transactionId={}", saved.getTransactionId());
        return toAuthorizeResponse(saved);
    }

    @Transactional
    public void confirm(ConfirmRequest request) {
        log.info("[CONFIRM] Processando confirmação: transactionId={}", request.getTransactionId());

        Transaction transaction = findByTransactionId(request.getTransactionId());

        if (transaction.getStatus() == TransactionStatus.CONFIRMED) {
            log.info("[CONFIRM] Idempotência: transação já confirmada. transactionId={}",
                    request.getTransactionId());
            return;
        }

        if (transaction.getStatus() == TransactionStatus.VOIDED) {
            throw new InvalidTransactionStateException(
                    "Transação já foi desfeita e não pode ser confirmada. transactionId="
                    + request.getTransactionId());
        }

        externalPaymentService.confirm(request.getTransactionId());

        transaction.setStatus(TransactionStatus.CONFIRMED);
        transactionRepository.save(transaction);
        log.info("[CONFIRM] Transação confirmada. transactionId={}", request.getTransactionId());
    }

    @Transactional
    public void voidTransaction(VoidRequest request) {
        Transaction transaction = resolveTransaction(request);

        log.info("[VOID] Processando desfazimento: transactionId={}", transaction.getTransactionId());

        if (transaction.getStatus() == TransactionStatus.VOIDED) {
            log.info("[VOID] Idempotência: transação já desfeita. transactionId={}",
                    transaction.getTransactionId());
            return;
        }

        externalPaymentService.voidTransaction(transaction.getTransactionId());

        transaction.setStatus(TransactionStatus.VOIDED);
        transactionRepository.save(transaction);
        log.info("[VOID] Transação desfeita. transactionId={}", transaction.getTransactionId());
    }

    private Transaction resolveTransaction(VoidRequest request) {
        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
            return findByTransactionId(request.getTransactionId());
        }

        if (request.getNsu() != null && request.getTerminalId() != null) {
            return transactionRepository
                    .findByTerminalIdAndNsu(request.getTerminalId(), request.getNsu())
                    .orElseThrow(() -> new TransactionNotFoundException(
                            "Transação não encontrada para terminalId=" + request.getTerminalId()
                            + " e nsu=" + request.getNsu()));
        }

        throw new InvalidRequestException(
                "Informe transactionId ou o par (terminalId + nsu) para desfazer a transação.");
    }

    private Transaction findByTransactionId(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transação não encontrada: transactionId=" + transactionId));
    }

    private String generateTransactionId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private AuthorizeResponse toAuthorizeResponse(Transaction transaction) {
        return AuthorizeResponse.builder()
                .nsu(transaction.getNsu())
                .amount(transaction.getAmount())
                .terminalId(transaction.getTerminalId())
                .transactionId(transaction.getTransactionId())
                .build();
    }
}
