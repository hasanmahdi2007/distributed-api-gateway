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
    }*/

    @Test
    public void testRegisterAndImmediatelyRetrieveClient_Returns200() {
        // 1. Setup unique data to prevent database crashes
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
            
            // --- THE ESCAPE HATCH ---
            // We stop checking JSON paths and tell Spring: "Map this JSON back to my Java object and give it to me!"
            .expectBody(NewClientResponse.class)
            .returnResult()
            .getResponseBody();

        // Sanity check: Ensure the response wasn't null
        Assertions.assertNotNull(savedResponse);
        Assertions.assertNotNull(savedResponse.clientId());
        
        // 3. Extract the new UUID from the database!
        UUID newClientId = savedResponse.clientId();

        // 4. Fire the GET request using the extracted UUID
        webTestClient.get()
            .uri("/api/v1/clients/" + "some-secure-route") // Injecting the UUID dynamically
            .exchange()
            
            // 5. Assert the GET request succeeded and matches our initial data
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(newClientId.toString())
            .jsonPath("$.companyName").isEqualTo("Vault Tech")
            .jsonPath("$.email").isEqualTo(uniqueEmail)
            .jsonPath("$.tierType").isEqualTo("PRO");
    }
}