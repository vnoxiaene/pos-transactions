package com.pos.transactions.service;

public interface ExternalPaymentService {
    void authorize(String transactionId, String terminalId, String nsu, java.math.BigDecimal amount);
    void confirm(String transactionId);
    void voidTransaction(String transactionId);
}
