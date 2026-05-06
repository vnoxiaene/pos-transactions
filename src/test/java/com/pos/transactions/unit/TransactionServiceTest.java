package com.pos.transactions.unit;

import com.pos.transactions.domain.Transaction;
import com.pos.transactions.domain.TransactionStatus;
import com.pos.transactions.dto.AuthorizeRequest;
import com.pos.transactions.dto.AuthorizeResponse;
import com.pos.transactions.dto.ConfirmRequest;
import com.pos.transactions.dto.VoidRequest;
import com.pos.transactions.exception.InvalidTransactionStateException;
import com.pos.transactions.exception.TransactionNotFoundException;
import com.pos.transactions.repository.TransactionRepository;
import com.pos.transactions.service.ExternalPaymentService;
import com.pos.transactions.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService - Testes Unitários")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ExternalPaymentService externalPaymentService;

    @InjectMocks
    private TransactionService transactionService;

    private AuthorizeRequest authorizeRequest;

    @BeforeEach
    void setUp() {
        authorizeRequest = new AuthorizeRequest();
        authorizeRequest.setNsu("123456");
        authorizeRequest.setAmount(new BigDecimal("199.90"));
        authorizeRequest.setTerminalId("T-1000");
    }

    @Test
    @DisplayName("Deve autorizar nova transação com sucesso")
    void shouldAuthorizeNewTransaction() {
        when(transactionRepository.findByTerminalIdAndNsu("T-1000", "123456"))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthorizeResponse response = transactionService.authorize(authorizeRequest);

        assertThat(response.getNsu()).isEqualTo("123456");
        assertThat(response.getTerminalId()).isEqualTo("T-1000");
        assertThat(response.getAmount()).isEqualByComparingTo("199.90");
        assertThat(response.getTransactionId()).isNotBlank();

        verify(externalPaymentService).authorize(anyString(), eq("T-1000"), eq("123456"), any(BigDecimal.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Deve retornar transação existente para requisição idempotente (authorize)")
    void shouldReturnExistingTransactionOnIdempotentAuthorize() {
        Transaction existing = buildTransaction("TXN001", TransactionStatus.AUTHORIZED);
        when(transactionRepository.findByTerminalIdAndNsu("T-1000", "123456"))
                .thenReturn(Optional.of(existing));

        AuthorizeResponse response = transactionService.authorize(authorizeRequest);

        assertThat(response.getTransactionId()).isEqualTo("TXN001");
        verifyNoInteractions(externalPaymentService);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve confirmar transação autorizada com sucesso")
    void shouldConfirmAuthorizedTransaction() {
        Transaction transaction = buildTransaction("TXN001", TransactionStatus.AUTHORIZED);
        when(transactionRepository.findByTransactionId("TXN001"))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenReturn(transaction);

        ConfirmRequest request = new ConfirmRequest();
        request.setTransactionId("TXN001");

        assertThatNoException().isThrownBy(() -> transactionService.confirm(request));

        verify(externalPaymentService).confirm("TXN001");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Deve ser idempotente para confirm já confirmado")
    void shouldBeIdempotentForAlreadyConfirmedTransaction() {
        Transaction transaction = buildTransaction("TXN001", TransactionStatus.CONFIRMED);
        when(transactionRepository.findByTransactionId("TXN001"))
                .thenReturn(Optional.of(transaction));

        ConfirmRequest request = new ConfirmRequest();
        request.setTransactionId("TXN001");

        assertThatNoException().isThrownBy(() -> transactionService.confirm(request));

        verifyNoInteractions(externalPaymentService);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao confirmar transação já desfeita")
    void shouldThrowWhenConfirmingVoidedTransaction() {
        Transaction transaction = buildTransaction("TXN001", TransactionStatus.VOIDED);
        when(transactionRepository.findByTransactionId("TXN001"))
                .thenReturn(Optional.of(transaction));

        ConfirmRequest request = new ConfirmRequest();
        request.setTransactionId("TXN001");

        assertThatThrownBy(() -> transactionService.confirm(request))
                .isInstanceOf(InvalidTransactionStateException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando transação não encontrada no confirm")
    void shouldThrowWhenTransactionNotFoundOnConfirm() {
        when(transactionRepository.findByTransactionId("TXN_INVALID"))
                .thenReturn(Optional.empty());

        ConfirmRequest request = new ConfirmRequest();
        request.setTransactionId("TXN_INVALID");

        assertThatThrownBy(() -> transactionService.confirm(request))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("Deve desfazer transação por transactionId com sucesso")
    void shouldVoidTransactionByTransactionId() {
        Transaction transaction = buildTransaction("TXN001", TransactionStatus.AUTHORIZED);
        when(transactionRepository.findByTransactionId("TXN001"))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenReturn(transaction);

        VoidRequest request = new VoidRequest();
        request.setTransactionId("TXN001");

        assertThatNoException().isThrownBy(() -> transactionService.voidTransaction(request));

        verify(externalPaymentService).voidTransaction("TXN001");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.VOIDED);
    }

    @Test
    @DisplayName("Deve desfazer transação por nsu + terminalId com sucesso")
    void shouldVoidTransactionByNsuAndTerminalId() {
        Transaction transaction = buildTransaction("TXN001", TransactionStatus.AUTHORIZED);
        when(transactionRepository.findByTerminalIdAndNsu("T-1000", "123456"))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenReturn(transaction);

        VoidRequest request = new VoidRequest();
        request.setNsu("123456");
        request.setTerminalId("T-1000");

        assertThatNoException().isThrownBy(() -> transactionService.voidTransaction(request));

        verify(externalPaymentService).voidTransaction("TXN001");
    }

    @Test
    @DisplayName("Deve ser idempotente para void já desfeito")
    void shouldBeIdempotentForAlreadyVoidedTransaction() {
        Transaction transaction = buildTransaction("TXN001", TransactionStatus.VOIDED);
        when(transactionRepository.findByTransactionId("TXN001"))
                .thenReturn(Optional.of(transaction));

        VoidRequest request = new VoidRequest();
        request.setTransactionId("TXN001");

        assertThatNoException().isThrownBy(() -> transactionService.voidTransaction(request));

        verifyNoInteractions(externalPaymentService);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando nem transactionId nem nsu+terminalId fornecidos")
    void shouldThrowWhenNoIdentifierProvided() {
        VoidRequest request = new VoidRequest();

        assertThatThrownBy(() -> transactionService.voidTransaction(request))
                .isInstanceOf(InvalidTransactionStateException.class);
    }

    private Transaction buildTransaction(String transactionId, TransactionStatus status) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .nsu("123456")
                .terminalId("T-1000")
                .amount(new BigDecimal("199.90"))
                .status(status)
                .version(0L)
                .build();
    }
}
