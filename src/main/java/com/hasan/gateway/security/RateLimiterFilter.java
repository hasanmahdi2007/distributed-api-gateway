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
        
        // 1. Tell Spring exactly where your Lua script is located
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("token_bucket.lua"));
        this.script.setResultType(Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String rawApiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (rawApiKey == null) {
            return chain.filter(exchange); // Let the AuthFilter handle the rejection
        }

        // 3. Define the Lua variables
        // (We will hardcode limit 20 and rate 5 for now until we link PostgreSQL!)
        String capacity = "20"; 
        String rate = "5"; 
        String now = String.valueOf(Instant.now().getEpochSecond());
        String requested = "1";

        // 4. Create unique Redis keys for this specific API Key
        List<String> keys = List.of("tokens:" + rawApiKey, "timestamp:" + rawApiKey);
        List<String> args = List.of(rate, capacity, now, requested);

        // 5. Fire the script at Redis asynchronously
        return redisTemplate.execute(script, keys, args)
                .next()
                .flatMap(result -> {
                    if (result == 1L) {
                        // Bucket has tokens -> Let them through
                        return chain.filter(exchange); 
                    } else {
                        // Bucket empty -> Smash the request with a 429 Error
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete(); 
                    }
                });
    }

    @Override
    public int getOrder() {
        // Runs at -50, so it happens AFTER AuthFilter (-100)
        return -100; 
    }
}