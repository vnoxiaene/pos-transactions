package com.pos.transactions.dto;


import java.math.BigDecimal;

public class AuthorizeResponse {
    private String nsu;
    private BigDecimal amount;
    private String terminalId;
    private String transactionId;
}
