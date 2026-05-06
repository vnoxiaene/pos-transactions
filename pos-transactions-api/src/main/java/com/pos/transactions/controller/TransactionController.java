package com.pos.transactions.controller;

import com.pos.transactions.dto.AuthorizeRequest;
import com.pos.transactions.dto.AuthorizeResponse;
import com.pos.transactions.dto.ConfirmRequest;
import com.pos.transactions.dto.VoidRequest;
import com.pos.transactions.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/pos/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizeResponse> authorize(@Valid @RequestBody AuthorizeRequest request) {
        AuthorizeResponse response = transactionService.authorize(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmRequest request) {
        transactionService.confirm(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/void")
    public ResponseEntity<Void> voidTransaction(@RequestBody VoidRequest request) {
        transactionService.voidTransaction(request);
        return ResponseEntity.noContent().build();
    }
}
