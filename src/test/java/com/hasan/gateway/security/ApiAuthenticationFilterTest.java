package com.hasan.gateway.security;

import com.hasan.gateway.repos.ApiKeyRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean; // <-- WE ARE BACK TO THE CLASSIC!
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc 
class ApiAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyRepo apiKeyRepo; 

    @Test
    void whenMissingApiKey_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dummy-data"))
               .andExpect(status().isUnauthorized()); 
    }

    @Test
    void whenInvalidApiKey_thenReturns401() throws Exception {
        when(apiKeyRepo.findByKeyHash(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/dummy-data")
               .header("X-API-KEY", "hacker_key_123"))
               .andExpect(status().isUnauthorized()); 
    }

    @Test
    void whenRegistering_thenPassesBouncer() throws Exception {
        mockMvc.perform(post("/api/v1/clients/register"))
               .andExpect(status().isBadRequest()); 
    }
}