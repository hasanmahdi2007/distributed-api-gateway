package com.hasan.gateway.logging;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TelemetryLoggingFilter implements GlobalFilter, Ordered {

    // 1. Inject the Redis tool that communicates with your Docker container
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public TelemetryLoggingFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getPath().value();
        
        String clientIp = request.getRemoteAddress() != null ? 
                          request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
                          
        // Null safety for User-Agent
        String rawUserAgent = request.getHeaders().getFirst("User-Agent");
        String userAgent = rawUserAgent != null ? rawUserAgent : "unknown";
        long requestSizeBytes = request.getHeaders().getContentLength(); 

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Correlation-ID", correlationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // 2. We use Mono.defer() to cleanly execute reactive code AFTER the backend responds
        return chain.filter(mutatedExchange).then(Mono.defer(() -> {
            
            long totalLatencyMs = System.currentTimeMillis() - startTime;
            ServerHttpResponse response = mutatedExchange.getResponse();
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
            long responseSizeBytes = response.getHeaders().getContentLength(); 
            String apiKey = mutatedExchange.getAttributeOrDefault("client_api_key", "unauthenticated");

            // 3. Package all our data into a Map (Redis Streams love key-value dictionaries)
            Map<String, String> telemetryData = new HashMap<>();
            telemetryData.put("correlationId", correlationId);
            telemetryData.put("method", method);
            telemetryData.put("path", path);
            telemetryData.put("status", String.valueOf(statusCode));
            telemetryData.put("latencyMs", String.valueOf(totalLatencyMs));
            telemetryData.put("ip", clientIp);
            telemetryData.put("userAgent", userAgent);
            telemetryData.put("reqBytes", String.valueOf(requestSizeBytes));
            telemetryData.put("resBytes", String.valueOf(responseSizeBytes));
            telemetryData.put("apiKey", apiKey);

            // 4. Fire the Map into the "telemetry:stream" queue using XADD
            return redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in("telemetry:stream").ofMap(telemetryData))
                    .then(); // .then() smoothly closes out the reactive chain
        }));
    }

    @Override
    public int getOrder() {
        return -4;
    }
}