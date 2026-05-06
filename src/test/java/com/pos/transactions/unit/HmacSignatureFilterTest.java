package com.pos.transactions.unit;

import com.pos.transactions.config.HmacSignatureFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HmacSignatureFilter - Testes Unitários")
class HmacSignatureFilterTest {

    private HmacSignatureFilter filter;
    private static final String SECRET = "test-secret-key";

    @BeforeEach
    void setUp() {
        filter = new HmacSignatureFilter();
        ReflectionTestUtils.setField(filter, "hmacSecret", SECRET);
        ReflectionTestUtils.setField(filter, "hmacEnabled", true);
    }

    @Test
    @DisplayName("Deve validar assinatura HMAC correta")
    void shouldValidateCorrectSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"nsu\":\"123456\",\"amount\":199.90,\"terminalId\":\"T-1000\"}";
        String signature = generateSignature(timestamp, body);

        assertThat(filter.isSignatureValid(body.getBytes(StandardCharsets.UTF_8), timestamp, signature))
                .isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar assinatura HMAC incorreta")
    void shouldRejectInvalidSignature() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"nsu\":\"123456\"}";

        assertThat(filter.isSignatureValid(body.getBytes(StandardCharsets.UTF_8), timestamp, "invalid-sig"))
                .isFalse();
    }

    @Test
    @DisplayName("Deve aceitar timestamp dentro da janela de 5 minutos")
    void shouldAcceptTimestampWithinWindow() {
        long now = Instant.now().getEpochSecond();
        assertThat(filter.isTimestampValid(String.valueOf(now))).isTrue();
        assertThat(filter.isTimestampValid(String.valueOf(now - 240))).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar timestamp fora da janela de 5 minutos")
    void shouldRejectExpiredTimestamp() {
        long expired = Instant.now().getEpochSecond() - 400;
        assertThat(filter.isTimestampValid(String.valueOf(expired))).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar timestamp não numérico")
    void shouldRejectNonNumericTimestamp() {
        assertThat(filter.isTimestampValid("not-a-number")).isFalse();
    }

    private String generateSignature(String timestamp, String body) throws Exception {
        String payload = timestamp + "." + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
