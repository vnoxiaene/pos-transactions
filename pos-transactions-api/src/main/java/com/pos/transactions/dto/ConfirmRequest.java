package com.pos.transactions.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmRequest {

    @NotBlank(message = "transactionId é obrigatório")
    private String transactionId;
}
