package com.pos.transactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String correlationId;
    private int status;
    private String error;
    private String message;
    private List<String> details;
    private Instant timestamp;
}
