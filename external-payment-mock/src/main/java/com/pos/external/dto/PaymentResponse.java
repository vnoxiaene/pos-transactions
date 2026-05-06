package com.pos.external.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String transactionId;
    private String status;
    private Instant processedAt;

    public static PaymentResponse of(String transactionId, String status) {
        return new PaymentResponse(transactionId, status, Instant.now());
    }
}
