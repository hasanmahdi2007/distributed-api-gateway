package com.hasan.gateway.repos;

import com.hasan.gateway.entities.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface ApiKeyRepo extends JpaRepository<ApiKey, UUID> {
    
    // Spring generates the SQL to look up the API Key by its hash
    Optional<ApiKey> findByKeyHash(String keyHash);
}