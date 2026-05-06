package com.pos.transactions.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AuthorizeResponse {
    private String nsu;
    private BigDecimal amount;
    private String terminalId;
    private String transactionId;
}
