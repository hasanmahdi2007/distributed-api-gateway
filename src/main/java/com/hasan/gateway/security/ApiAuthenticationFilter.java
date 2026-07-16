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

        // Let register requests pass right through
        if (path.startsWith("/api/v1/clients/register")) {
            return chain.filter(exchange);
        }

        String rawApiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (rawApiKey == null || rawApiKey.isEmpty()) {
            return rejectRequest(exchange, "Missing X-API-KEY header");
        }

        String hashedIncomingKey = SecurityUtil.hashKey(rawApiKey);
        String cacheKey = "auth:" + hashedIncomingKey;

        return redisTemplate.opsForValue().get(cacheKey)
                .switchIfEmpty(Mono.defer(() -> 
                    Mono.fromCallable(() -> apiKeyRepo.findByKeyHash(hashedIncomingKey))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(optionalKey -> {
                            if (optionalKey.isPresent()) {
                                // Get the tier from your database (Adjust this line to match your exact entity structure if needed)
                                String tier = optionalKey.get().getClient().getTierType(); 
                                
                                // Format: "Capacity:Rate"
                                String limits = "PRO".equalsIgnoreCase(tier) ? "100:20" : "20:5";

                                return redisTemplate.opsForValue()
                                        .set(cacheKey, limits, Duration.ofHours(24))
                                        .thenReturn(limits);
                            } else {
                                return redisTemplate.opsForValue()
                                        .set(cacheKey, "invalid", Duration.ofMinutes(5))
                                        .thenReturn("invalid");
                            }
                        })
                ))
                .flatMap(cachedValue -> {
                    if ("invalid".equals(cachedValue)) {
                        return rejectRequest(exchange, "Invalid API Key"); 
                    } else {
                        // SUCCESS! Split the "100:20" string
                        String[] limitParts = cachedValue.split(":");
                        String capacity = limitParts[0];
                        String rate = limitParts[1];

                        // PUT THE LIMITS ON THE CLIPBOARD
                        exchange.getAttributes().put("user_capacity", capacity);
                        exchange.getAttributes().put("user_rate", rate);

                        return chain.filter(exchange); 
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
        return -2; 
    }
}