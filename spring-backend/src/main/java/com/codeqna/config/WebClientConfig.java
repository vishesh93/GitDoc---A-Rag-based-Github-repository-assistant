package com.codeqna.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("chunkerWebClient")
    public WebClient chunkerWebClient(@Value("${app.chunker.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
    }

    // Groq uses OpenAI-compatible API — just swap the base URL + key
    @Bean("groqWebClient")
    public WebClient groqWebClient(
            @Value("${app.groq.base-url}") String baseUrl,
            @Value("${app.groq.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("cohereWebClient")
    public WebClient cohereWebClient(
            @Value("${app.cohere.base-url}") String baseUrl,
            @Value("${app.cohere.api-key}") String apiKey) {

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .exchangeStrategies(strategies)
                .build();
    }

    // Qdrant Cloud — uses HTTPS + API key header
    @Bean("qdrantWebClient")
    public WebClient qdrantWebClient(
            @Value("${app.qdrant.url}") String url,
            @Value("${app.qdrant.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
