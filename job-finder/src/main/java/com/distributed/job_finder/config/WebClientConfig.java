package com.distributed.job_finder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    // This pulls the 5000ms timeout we set in application.yml
    @Value("${app.scraper.webclient.timeout-ms}")
    private int timeoutMs;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // We configure the underlying Netty engine to forcefully drop connections 
        // if an ATS API takes longer than our timeout limit to respond
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}