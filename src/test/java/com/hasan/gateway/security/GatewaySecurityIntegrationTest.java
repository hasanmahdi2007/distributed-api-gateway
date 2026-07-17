package com.hasan.gateway.security;

import org.junit.jupiter.api.Assertions;
import com.hasan.gateway.dtos.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.hasan.gateway.dtos.NewClientResponse;
import java.util.UUID;

// This tells Spring to boot up your entire Gateway on a random port for testing
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.gateway.routes[0].id=mock-secure-route",
        "spring.cloud.gateway.routes[0].uri=http://localhost:8081",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/some-secure-route"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GatewaySecurityIntegrationTest {

    // WebTestClient is basically an automated, Java-based version of "curl"
    @Autowired
    private WebTestClient webTestClient;

    /*@Test
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

    @Test
    public void testRegisterAndImmediatelyRetrieveClient_Returns200() {
        // 1. Setup unique data
        String uniqueEmail = "integration-" + UUID.randomUUID() + "@testcompany.com";
        RegistrationRequest request = new RegistrationRequest("Vault Tech", uniqueEmail, "PRO");

        // 2. Fire the POST request to register the client
        NewClientResponse savedResponse = webTestClient.post()
            .uri("/api/v1/clients/register")
            .header("X-Admin-Key", "super-secret-admin-password-123!")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(NewClientResponse.class)
            .returnResult()
            .getResponseBody();

        Assertions.assertNotNull(savedResponse);
        Assertions.assertNotNull(savedResponse.clientId());
        
        // 3. Extract the new UUID AND the new API Key!
        UUID newClientId = savedResponse.clientId();
        String newApiKey = savedResponse.apiKey(); // <-- Use whatever variable name your DTO uses for the plain-text key!

        // 4. Fire the GET request (Acting as the newly registered client)
        webTestClient.get()
            .uri("/api/v1/clients/" + newClientId) // <-- FIX 1: Pass the actual UUID!
            .header("X-API-KEY", newApiKey)        // <-- FIX 2: Provide the API Key to pass the Bouncer!
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(newClientId.toString())
            .jsonPath("$.companyName").isEqualTo("Vault Tech")
            .jsonPath("$.email").isEqualTo(uniqueEmail)
            .jsonPath("$.tierType").isEqualTo("PRO");
    }

    @Test
    public void testFakeApiKey_IsBlockedByBouncer() {
        // Fire a request with a completely fabricated API key
        webTestClient.get()
            .uri("/api/v1/clients/some-random-id")
            .header("X-API-KEY", "sk_live_fake_hacker_key_99999999")
            .exchange()
            
            // The Gateway MUST block this with a 401
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Invalid API Key");
    }*/

    @Test
    public void testRateLimiter_DropsHammerOnSpam() {
        // 1. Register a FREE tier user (Limits: 20 capacity, 5 rate)
        RegistrationRequest request = new RegistrationRequest("Spam Inc", "spam@test.com", "FREE");

        NewClientResponse savedResponse = webTestClient.post()
            .uri("/api/v1/clients/register")
            .header("X-Admin-Key", "super-secret-admin-password-123!")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(NewClientResponse.class)
            .returnResult()
            .getResponseBody();

        String validApiKey = savedResponse.apiKey();
        UUID clientId = savedResponse.clientId();

        // 2. The Free tier bucket holds 20 tokens. 
        // We will fire 20 rapid requests. They should ALL succeed.
        for (int i = 0; i < 20; i++) {
            webTestClient.get()
                .uri("/api/v1/clients/" + clientId)
                .header("X-API-KEY", validApiKey)
                .exchange()
                .expectStatus().isOk(); // All 20 pass through the Gateway
        }

        // 3. THE HAMMER DROP
        // The bucket is now exactly empty. The 21st request MUST be blocked.
        webTestClient.get()
            .uri("/api/v1/clients/" + clientId)
            .header("X-API-KEY", validApiKey)
            .exchange()
            .expectStatus().isEqualTo(429); // 429 TOO MANY REQUESTS
    }
}