package com.hasan.gateway.security;

import com.hasan.gateway.repos.ApiKeyRepo;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class ApiAuthenticationFilter implements WebFilter, Ordered {

    private final ApiKeyRepo apiKeyRepo;
    private final ReactiveStringRedisTemplate redisTemplate;

    public ApiAuthenticationFilter(ApiKeyRepo apiKeyRepo, ReactiveStringRedisTemplate redisTemplate) {
        this.apiKeyRepo = apiKeyRepo;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/api/v1/clients/register")) {
            return chain.filter(exchange);
        }

        // 1. Extract API Key
        String rawApiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (rawApiKey == null || rawApiKey.isEmpty()) {
            return rejectRequest(exchange, "Missing X-API-KEY header");
        }

        String hashedIncomingKey = SecurityUtil.hashKey(rawApiKey);
        String cacheKey = "auth:" + hashedIncomingKey;

        // 2. THE CACHE-ASIDE PATTERN (With Negative Caching)
        return redisTemplate.opsForValue().get(cacheKey)
                .switchIfEmpty(Mono.defer(() -> 
                    
                    // CACHE MISS: Safely query PostgreSQL
                    Mono.fromCallable(() -> apiKeyRepo.findByKeyHash(hashedIncomingKey).isPresent())
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(existsInDb -> {
                            if (existsInDb) {
                                // Found! Save as VALID for 24 hours
                                return redisTemplate.opsForValue()
                                        .set(cacheKey, "valid", Duration.ofHours(24))
                                        .thenReturn("valid");
                            } else {
                                // THE FIX: Negative Caching! Save as INVALID for 5 minutes
                                return redisTemplate.opsForValue()
                                        .set(cacheKey, "invalid", Duration.ofMinutes(5))
                                        .thenReturn("invalid");
                            }
                        })
                ))
                .flatMap(status -> {
                    // Check what Redis/Postgres actually said
                    if ("valid".equals(status)) {
                        return chain.filter(exchange); // Let them pass!
                    } else {
                        return rejectRequest(exchange, "Invalid API Key"); // Blocked by Negative Cache
                    }
                });
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = "{\"error\": \"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }

    @Override
    public int getOrder() {
        return -50; 
    }
}