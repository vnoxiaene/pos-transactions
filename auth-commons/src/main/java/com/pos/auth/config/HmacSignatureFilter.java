package com.pos.auth.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signature validation filter for stateless API authentication.
 *
 * Validates incoming requests contain:
 * - X-Timestamp: Unix epoch seconds
 * - X-Signature: HmacSHA256(timestamp.body) in hex format
 *
 * Timestamp tolerance: ±300 seconds
 */
@Component
@Order(2)
public class HmacSignatureFilter implements Filter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300L;
    private static final String SKIP_PATH_PREFIX = "/actuator";

    @Autowired
    private HmacSignatureProperties properties;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!properties.isEnabled() || httpRequest.getRequestURI().startsWith(SKIP_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String timestamp = httpRequest.getHeader("X-Timestamp");
        String signature = httpRequest.getHeader("X-Signature");

        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            sendUnauthorized(httpResponse, "Headers X-Timestamp e X-Signature são obrigatórios");
            return;
        }

        if (!isTimestampValid(timestamp)) {
            sendUnauthorized(httpResponse, "Timestamp inválido ou expirado");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
        byte[] body = cachedRequest.getCachedBody();

        if (!isSignatureValid(body, timestamp, signature)) {
            sendUnauthorized(httpResponse, "Assinatura HMAC inválida");
            return;
        }

        chain.doFilter(cachedRequest, response);
    }

    /**
     * Validates timestamp is within tolerance window.
     */
    public boolean isTimestampValid(String timestampStr) {
        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - timestamp) <= TIMESTAMP_TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates HMAC-SHA256 signature against payload (timestamp.body).
     */
    public boolean isSignatureValid(byte[] body, String timestamp, String receivedSignature) {
        try {
            String payload = timestamp + "." + new String(body, StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expectedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(expectedHash);
            return expectedSignature.equals(receivedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Não autorizado\",\"message\":\"" + message + "\"}"
        );
    }
}
