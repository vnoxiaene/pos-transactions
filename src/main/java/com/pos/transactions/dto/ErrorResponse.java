package com.pos.transactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String correlationId;
    private int status;
    private String error;
    private String message;
    private List<String> details;
    private Instant timestamp;
}
