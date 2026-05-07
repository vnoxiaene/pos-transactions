package com.pos.transactions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeResponse {
    private String nsu;
    private BigDecimal amount;
    private String terminalId;
    private String transactionId;
}
