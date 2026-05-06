package com.pos.transactions.bdd.steps;

import com.pos.transactions.domain.Transaction;
import com.pos.transactions.domain.TransactionStatus;
import com.pos.transactions.repository.TransactionRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    private ResponseEntity<Map> lastResponse;
    private String lastTransactionId;

    @Before
    public void setUp() {
        transactionRepository.deleteAll();
    }

    @Given("que não existe transação para o terminal {string} com NSU {string}")
    public void queNaoExisteTransacao(String terminalId, String nsu) {
        transactionRepository.findByTerminalIdAndNsu(terminalId, nsu)
                .ifPresent(t -> transactionRepository.delete(t));
    }

    @When("eu autorizo uma transação com terminalId {string}, NSU {string} e valor {double}")
    public void euAutorizoTransacao(String terminalId, String nsu, Double amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("terminalId", terminalId);
        body.put("nsu", nsu);
        body.put("amount", amount);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        lastResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/pos/transactions/authorize",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    @Then("a resposta deve ter status {int}")
    public void aRespostaTerStatus(int status) {
        assertThat(lastResponse.getStatusCode().value()).isEqualTo(status);
    }

    @Then("a resposta deve conter um transactionId")
    public void aRespostaDeveConterTransactionId() {
        assertThat(lastResponse.getBody()).containsKey("transactionId");
        lastTransactionId = (String) lastResponse.getBody().get("transactionId");
        assertThat(lastTransactionId).isNotBlank();
    }

    @Then("a transação deve estar com status AUTHORIZED no banco de dados")
    public void transacaoDeveEstarAuthorized() {
        assertThat(lastTransactionId).isNotNull();
        Transaction tx = transactionRepository.findByTransactionId(lastTransactionId).orElse(null);
        assertThat(tx).isNotNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
    }

    @Given("existe uma transação autorizada com transactionId {string}")
    public void existeTransacaoAutorizada(String transactionId) {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .nsu("BDD-NSU-001")
                .terminalId("T-BDD")
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.AUTHORIZED)
                .version(0L)
                .build();
        transactionRepository.save(tx);
        lastTransactionId = transactionId;
    }

    @When("eu confirmo a transação com transactionId {string}")
    public void euConfirmoTransacao(String transactionId) {
        Map<String, String> body = new HashMap<>();
        body.put("transactionId", transactionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        lastResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/pos/transactions/confirm",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    @Then("a transação deve estar com status CONFIRMED no banco de dados")
    public void transacaoDeveEstarConfirmed() {
        Transaction tx = transactionRepository.findByTransactionId(lastTransactionId).orElse(null);
        assertThat(tx).isNotNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }

    @When("eu desfaço a transação com transactionId {string}")
    public void euDesfacoTransacaoPorId(String transactionId) {
        Map<String, String> body = new HashMap<>();
        body.put("transactionId", transactionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        lastResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/pos/transactions/void",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    @When("eu desfaço a transação com terminalId {string} e NSU {string}")
    public void euDesfacoTransacaoPorNsu(String terminalId, String nsu) {
        Map<String, String> body = new HashMap<>();
        body.put("terminalId", terminalId);
        body.put("nsu", nsu);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        lastResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/pos/transactions/void",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    @Then("a transação deve estar com status VOIDED no banco de dados")
    public void transacaoDeveEstarVoided() {
        Transaction tx = transactionRepository.findByTransactionId(lastTransactionId).orElse(null);
        assertThat(tx).isNotNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.VOIDED);
    }

    @When("eu autorizo novamente a mesma transação com terminalId {string}, NSU {string} e valor {double}")
    public void euAutorizoNovamenteTransacao(String terminalId, String nsu, Double amount) {
        euAutorizoTransacao(terminalId, nsu, amount);
    }

    @Then("a resposta deve conter o mesmo transactionId {string}")
    public void respostaDeveTerMesmoTransactionId(String transactionId) {
        String returnedId = (String) lastResponse.getBody().get("transactionId");
        assertThat(returnedId).isEqualTo(transactionId);
    }
}
