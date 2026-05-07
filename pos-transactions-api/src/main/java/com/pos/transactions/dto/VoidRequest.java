package com.pos.transactions.dto;

import lombok.Data;

@Data
public class VoidRequest {
    private String transactionId;
    private String nsu;
    private String terminalId;
}
