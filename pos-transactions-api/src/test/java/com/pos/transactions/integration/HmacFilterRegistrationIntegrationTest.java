package com.pos.transactions.integration;

import com.pos.transactions.dto.AuthorizeResponse;
import com.pos.transactions.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.hmac.enabled=true",
        "security.hmac.secret=test-secret-key-for-testing-only"
})
class HmacFilterRegistrationIntegrationTest {

    private static final String SECRET = "test-secret-key-for-testing-only";
    private static final String BODY = "{\"nsu\":\"123456\",\"amount\":199.90,\"terminalId\":\"T-1000\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    @DisplayName("Should reject authorize request without HMAC headers")
    void shouldRejectWithoutHeaders() throws Exception {
        mockMvc.perform(post("/v1/pos/transactions/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Should reject authorize request with invalid HMAC signature")
    void shouldRejectInvalidSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/v1/pos/transactions/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Timestamp", timestamp)
                        .header("X-Signature", "wrong-secret-signature")
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Assinatura HMAC inválida"));

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Should accept authorize request with valid HMAC signature")
    void shouldAcceptValidSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(timestamp, BODY);

        when(transactionService.authorize(any())).thenReturn(AuthorizeResponse.builder()
                .nsu("123456")
                .amount(new java.math.BigDecimal("199.90"))
                .terminalId("T-1000")
                .transactionId("TXN-123")
                .build());

        mockMvc.perform(post("/v1/pos/transactions/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Timestamp", timestamp)
                        .header("X-Signature", signature)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-123"));
    }

    private String sign(String timestamp, String body) throws Exception {
        String payload = timestamp + "." + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

