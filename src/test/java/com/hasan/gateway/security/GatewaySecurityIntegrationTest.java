package com.hasan.gateway.security;

import com.hasan.gateway.dtos.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

// This tells Spring to boot up your entire Gateway on a random port for testing
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres",
        "spring.datasource.username=postgres",
        "spring.datasource.password=Hassouna10:)D" 
    }
)
public class GatewaySecurityIntegrationTest {

    // WebTestClient is basically an automated, Java-based version of "curl"
    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testHackerRegistration_WithoutAdminKey_Returns401() {
        // 1. Create the fake bot request
        RegistrationRequest request = new RegistrationRequest("Hacker Inc", "hacker@botnet.com", "FREE");

        // 2. Fire the POST request at the Gateway
        webTestClient.post()
            .uri("/api/v1/clients/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange() // Sends the request
            
            // 3. Assert the results (The Gateway MUST block this)
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Fatal: Only authorized backend servers can register new keys.");
    }

    @Test
    public void testLegitimateRegistration_WithAdminKey_Returns201() {
        // 1. Create the valid frontend request
        RegistrationRequest request = new RegistrationRequest(
            "Test Company", 
            "test@testcompany.com", 
            "FREE"
        );

        // 2. Fire the POST request WITH the Admin Key
        webTestClient.post()
            .uri("/api/v1/clients/register")
            .header("X-Admin-Key", "super-secret-admin-password-123!") // The magic key
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            
            // 3. Assert the results (The Gateway MUST allow this)
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.apiKey").exists() // Proves the server generated a key
            .jsonPath("$.clientId").exists(); // Proves it saved to PostgreSQL
    }

    @Test
    public void testMissingApiKey_Returns401() {
        // Fire a request to a normal route without providing an X-API-KEY header
        webTestClient.get()
            .uri("/api/v1/some-secure-route") // Even if this route doesn't exist yet, the Bouncer catches it first
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Missing X-API-KEY header");
    }
}