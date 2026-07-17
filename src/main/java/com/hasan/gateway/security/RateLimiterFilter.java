package com.hasan.gateway.security;

import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class RateLimiterFilter implements WebFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RateLimiterFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.script.setResultType(Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String rawApiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (rawApiKey == null) {
            return chain.filter(exchange); 
        }

        // READ THE CLIPBOARD! (If for some reason it's missing, default to FREE tier: 20 and 5)
        String capacity = exchange.getAttributeOrDefault("user_capacity", "20");
        String rate = exchange.getAttributeOrDefault("user_rate", "5");
        
        String now = String.valueOf(Instant.now().getEpochSecond());
        String requested = "1";

        List<String> keys = List.of("tokens:" + rawApiKey, "timestamp:" + rawApiKey);
        List<String> args = List.of(rate, capacity, now, requested);

        return redisTemplate.execute(script, keys, args)
                .next()
                .flatMap(result -> {
                    if (result == 1L) {
                        return chain.filter(exchange); 
                    } else {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete(); 
                    }
                });
    }

    @Override
    public int getOrder() {
        return -1; 
    }
}