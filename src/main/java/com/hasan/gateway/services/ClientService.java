package com.hasan.gateway.services;

import com.hasan.gateway.entities.ApiKey;
import com.hasan.gateway.entities.Client;
import com.hasan.gateway.repos.ApiKeyRepo;
import com.hasan.gateway.repos.ClientRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepo clientRepo;
    private final ApiKeyRepo apiKeyRepo;

    public ClientService(ClientRepo clientRepo, ApiKeyRepo apiKeyRepo) {
        this.clientRepo = clientRepo;
        this.apiKeyRepo = apiKeyRepo;
    }

    @Transactional
    public String registerClientAndGenerateKey(String companyName, String email, String tierType) {
        
        // 1. Create and save the new Client
        Client client = new Client();
        client.setCompanyName(companyName);
        client.setEmail(email);
        client.setTierType(tierType);
        clientRepo.save(client);

        // 2. Generate a secure, raw API key
        String rawApiKey = "sk_live_" + UUID.randomUUID().toString().replace("-", "");

        // 3. Hash the key securely
        String hashedKey = hashKey(rawApiKey);

        // 4. Create and configure the ApiKey entity
        ApiKey apiKey = new ApiKey();
        apiKey.setClient(client);
        apiKey.setKeyHash(hashedKey);
        
        // Assign limits based on tier
        if ("PRO".equalsIgnoreCase(tierType)) {
            apiKey.setRequestLimit(10000);
        } else {
            apiKey.setRequestLimit(1000);
        }
        
        apiKeyRepo.save(apiKey);

        // 5. Return the RAW key so the user can copy it. 
        return rawApiKey;
    }

    // Internal Security Method: SHA-256 Hashing
    private String hashKey(String plainTextKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainTextKey.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }
}