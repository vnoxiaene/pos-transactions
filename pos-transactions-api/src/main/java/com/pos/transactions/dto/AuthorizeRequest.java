package com.pos.transactions.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class AuthorizeRequest {

    @NotBlank(message = "nsu é obrigatório")
    private String nsu;

    @NotNull(message = "amount é obrigatório")
    @DecimalMin(value = "0.01", message = "amount deve ser maior que zero")
    private BigDecimal amount;

    @NotBlank(message = "terminalId é obrigatório")
    private String terminalId;
}
