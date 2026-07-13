package com.hasan.gateway.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayController {

    private final RestTemplate restTemplate;

    public GatewayController() {
        this.restTemplate = new RestTemplate();
    }

    // The mapping the user actually hits
    @GetMapping("/dummy-backend")
    public ResponseEntity<String> routeToDummyBackend() {
        
        // 1. The Gateway catches the request.
        // 2. The Gateway acts as the middleman and reaches out to the "Backend" (a free public API)
        String backendUrl = "https://jsonplaceholder.typicode.com/posts/1";
        
        System.out.println("Traffic Cop: Forwarding request to the backend...");
        
        // 3. We fetch the data from the backend
        String backendResponse = restTemplate.getForObject(backendUrl, String.class);
        
        // 4. We return the backend's data back to the original client
        return ResponseEntity.ok(backendResponse);
    }
}