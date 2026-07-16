package com.hasan.gateway.controllers;

import com.hasan.gateway.dtos.NewClientResponse;
import com.hasan.gateway.dtos.RegistrationRequest;
import com.hasan.gateway.entities.Client;
import com.hasan.gateway.services.ClientService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    // 1. CREATE: The permanent home of the X-Admin-Key check
    @PostMapping("/register")
    public ResponseEntity<?> registerClient(
            @Valid @RequestBody RegistrationRequest request,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey) {

        if (adminKey == null || !adminKey.equals("super-secret-admin-password-123!")) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Fatal: Only authorized backend servers can register new keys."));
        }

        NewClientResponse response = clientService.registerClientAndGenerateKey(
                request.companyName(), request.email(), request.tierType());
                
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. READ
    @GetMapping("/{id}")
    public ResponseEntity<?> getClient(@PathVariable UUID id) {
        Client clientData = clientService.findById(id);
        return ResponseEntity.ok(clientData);
    }

    // 3. UPDATE
    @PutMapping("/{id}/tier")
    public ResponseEntity<String> updateTier(@PathVariable UUID id, @RequestParam String newTier) {
        clientService.updateTier(id, newTier);
        return ResponseEntity.ok("Client " + id + " successfully upgraded to " + newTier + " tier.");
    }

    // 4. DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        clientService.deleteById(id);
        return ResponseEntity.noContent().build(); 
    }
}