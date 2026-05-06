package com.pos.external.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizePaymentRequest {
    private String transactionId;
    private String terminalId;
    private String nsu;
    private BigDecimal amount;
}
