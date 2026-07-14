package com.hasan.gateway.security;

import com.hasan.gateway.repos.ApiKeyRepo;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class ApiAuthenticationFilter implements GlobalFilter, Ordered {

    private final ApiKeyRepo apiKeyRepo;

    public ApiAuthenticationFilter(ApiKeyRepo apiKeyRepo) {
        this.apiKeyRepo = apiKeyRepo;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        // 1. Let registration requests pass through without a key
        if (path.startsWith("/api/v1/clients/register")) {
            return chain.filter(exchange);
        }

        // 2. Extract the API Key from the HTTP Headers
        String rawApiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (rawApiKey == null || rawApiKey.isEmpty()) {
            return rejectRequest(exchange, "Missing X-API-KEY header");
        }

        // 3. Hash the incoming key and check the database
        String hashedIncomingKey = SecurityUtil.hashKey(rawApiKey);
        boolean keyExists = apiKeyRepo.findByKeyHash(hashedIncomingKey).isPresent();

        if (!keyExists) {
            return rejectRequest(exchange, "Invalid API Key");
        }

        // 4. Key is valid! Let them pass to the routing destination.
        return chain.filter(exchange);
    }

    // Standard JSON rejection response for Spring Cloud Gateway (Netty)
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = "{\"error\": \"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }

    // Forces this filter to run BEFORE the Redis rate limiter
    @Override
    public int getOrder() {
        return -100; 
    }
}