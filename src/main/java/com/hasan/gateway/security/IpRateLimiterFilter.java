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
public class IpRateLimiterFilter implements WebFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public IpRateLimiterFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // Reusing your flawless Lua script!
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("token_bucket.lua"));
        this.script.setResultType(Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                
        // 1. Extract the raw IP address of the incoming request
        String ipAddress = exchange.getRequest().getRemoteAddress() != null ? 
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown-ip";

        // 2. Set the global IP rules (e.g., 50 requests per second max from any single IP)
        String capacity = "50"; 
        String rate = "50"; 
        String now = String.valueOf(Instant.now().getEpochSecond());
        String requested = "1";

        // 3. Create unique Redis tracking keys for this specific IP
        List<String> keys = List.of("ip_tokens:" + ipAddress, "ip_timestamp:" + ipAddress);
        List<String> args = List.of(rate, capacity, now, requested);

        // 4. Fire the script at Redis
        return redisTemplate.execute(script, keys, args)
                .next()
                .flatMap(result -> {
                    if (result == 1L) {
                        // IP is behaving normally -> Pass them inward to the Auth Filter
                        return chain.filter(exchange); 
                    } else {
                        // IP is spamming! Drop the hammer immediately.
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete(); 
                    }
                });
    }

    @Override
    public int getOrder() {
        // Runs at -200! This is the absolute first shield, stopping bots before they hit the Bouncer.
        return -200; 
    }
}