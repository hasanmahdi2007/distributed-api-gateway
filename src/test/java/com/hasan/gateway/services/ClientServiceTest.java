package com.hasan.gateway.services;

import com.hasan.gateway.dtos.NewClientResponse;
import com.hasan.gateway.entities.ApiKey;
import com.hasan.gateway.entities.Client;
import com.hasan.gateway.repos.ApiKeyRepo;
import com.hasan.gateway.repos.ClientRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// This tells Spring we are using Mockito to fake the database
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepo clientRepo;

    @Mock
    private ApiKeyRepo apiKeyRepo;

    @InjectMocks
    private ClientService clientService;

    @Test
    void registerClient_ProTier_Assigns10000LimitAndHashesKey() throws Exception {
        // --- 1. ARRANGE (Set up the test) ---
        String companyName = "Google";
        String email = "admin@google.com";
        String tier = "PRO";

        // --- 2. ACT (Run the actual method) ---
        NewClientResponse response = clientService.registerClientAndGenerateKey(companyName, email, tier);

        // --- 3. ASSERT (Verify the results) ---
        
        // A. Check that the raw key looks correct
        assertNotNull(response.apiKey());
        assertTrue(response.apiKey().startsWith("sk_live_"));

        // B. Check that clientRepo.save() was called exactly once
        verify(clientRepo, times(1)).save(any(Client.class));

        // C. The Detective Work: Catch the ApiKey right before it hits the fake database
        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepo, times(1)).save(apiKeyCaptor.capture());
        
        ApiKey savedApiKey = apiKeyCaptor.getValue();

        // D. Verify the tier limits worked!
        assertEquals(10000, savedApiKey.getRequestLimit());

        // E. Verify the Math: Did the service actually hash it using SHA-256?
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedHashBytes = digest.digest(response.apiKey().getBytes());
        String expectedHashString = Base64.getEncoder().encodeToString(expectedHashBytes);
        
        assertEquals(expectedHashString, savedApiKey.getKeyHash(), "The database hash should match the SHA-256 of the raw key!");
    }

    @Test
    void registerClient_FreeTier_Assigns1000Limit() {
        // --- 1. ACT ---
        clientService.registerClientAndGenerateKey("Startup", "test@startup.com", "FREE");

        // --- 2. ASSERT ---
        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepo, times(1)).save(apiKeyCaptor.capture());
        
        // Verify the fallback/free limit worked!
        assertEquals(1000, apiKeyCaptor.getValue().getRequestLimit());
    }
}