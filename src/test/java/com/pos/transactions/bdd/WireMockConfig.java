package com.pos.transactions.bdd;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@TestConfiguration
public class WireMockConfig {

    static final int WIREMOCK_PORT = 8181;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(
                WireMockConfiguration.options().port(WIREMOCK_PORT));
        stubDefaultResponses(server);
        return server;
    }

    public static void stubDefaultResponses(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/api/payment/authorize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));

        server.stubFor(post(urlEqualTo("/api/payment/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"CONFIRMED\"}")));

        server.stubFor(post(urlEqualTo("/api/payment/void"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"VOIDED\"}")));
    }
}
