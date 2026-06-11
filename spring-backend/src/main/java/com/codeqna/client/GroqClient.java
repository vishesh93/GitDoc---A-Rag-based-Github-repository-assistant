package com.codeqna.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Groq — OpenAI-compatible API, free tier, insanely fast inference.
 * Supports llama3-8b-8192, llama3-70b-8192, mixtral-8x7b-32768 etc.
 * Sign up at https://console.groq.com — no credit card needed.
 */
@Slf4j
@Component
public class GroqClient {

    private final WebClient client;

    public GroqClient(@Qualifier("groqWebClient") WebClient client) {
        this.client = client;
    }


    @Value("${app.groq.model}")
    private String model;

    @Data
    public static class ChatResponse {
        private List<Choice> choices;

        @Data
        public static class Choice {
            private Message message;

            @Data
            public static class Message {
                private String content;
            }
        }
    }

    public Mono<String> chat(List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.2
        );

        log.info("Calling Groq model: {}", model);

        return client.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(error -> {
                                    log.error("Groq error body: {}", error);
                                    return Mono.error(new RuntimeException(error));
                                })
                )
                .bodyToMono(ChatResponse.class)
                .map(r -> r.getChoices().get(0).getMessage().getContent())
                .doOnError(e -> log.error("Groq chat error", e));
    }}
