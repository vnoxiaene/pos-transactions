package com.pos.transactions.repository;

import com.pos.transactions.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionId(String transactionId);

    Optional<Transaction> findByTerminalIdAndNsu(String terminalId, String nsu);

    @Query("SELECT t FROM Transaction t WHERE t.terminalId = :terminalId AND t.nsu = :nsu")
    Optional<Transaction> findByTerminalAndNsu(String terminalId, String nsu);
}
