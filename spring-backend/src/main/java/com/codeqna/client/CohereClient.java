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
import java.util.stream.Collectors;

/**
 * Cohere — free Trial API key covers both embed + rerank.
 * Sign up at https://dashboard.cohere.com — no credit card needed.
 *
 * embed-english-light-v3.0 → 384-dim vectors, fast, free tier
 * rerank-english-v3.0      → cross-encoder reranking
 */
@Slf4j
@Component

public class CohereClient {

    private final WebClient client;

    public CohereClient(@Qualifier("cohereWebClient") WebClient client) {
        this.client = client;
    }

    @Value("${app.cohere.embedding-model}")
    private String embeddingModel;

    // ── Embed a single text ───────────────────────────────────
    @Data
    public static class EmbeddingContainer {
        private List<List<Double>> floatEmbeddings;

        @com.fasterxml.jackson.annotation.JsonProperty("float")
        public void setFloatEmbeddings(List<List<Double>> floatEmbeddings) {
            this.floatEmbeddings = floatEmbeddings;
        }
    }

    @Data
    public static class EmbedResponse {
        private EmbeddingContainer embeddings;
    }

    public Mono<List<Double>> embed(String text) {
        return embedBatch(List.of(text))
                .map(list -> list.get(0));
    }

    // ── Embed a batch (Cohere supports up to 96 per call) ─────
    public Mono<List<List<Double>>> embedBatch(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "texts", texts,
                "input_type", "search_document",
                "embedding_types", List.of("float")
        );

        return client.post()
                .uri("/embed")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .map(r -> r.getEmbeddings().getFloatEmbeddings())
                .doOnError(e -> log.error("Cohere embed error: {}", e.getMessage()));
    }

    // ── Embed a query (different input_type for retrieval) ────
    public Mono<List<Double>> embedQuery(String query) {
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "texts", List.of(query),
                "input_type", "search_query",
                "embedding_types", List.of("float")
        );

        return client.post()
                .uri("/embed")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .map(r -> r.getEmbeddings()
                        .getFloatEmbeddings()
                        .get(0))
                .doOnError(e -> log.error("Cohere embed query error: {}", e.getMessage()));
    }

    // ── Rerank ────────────────────────────────────────────────
    @Data
    public static class RerankResult {
        private int index;
        private double relevanceScore;
    }

    @Data
    public static class RerankResponse {
        private List<RerankResult> results;
    }

    public Mono<List<RerankResult>> rerank(String query, List<String> documents, int topN) {
        Map<String, Object> body = Map.of(
                "model", "rerank-english-v3.0",
                "query", query,
                "documents", documents,
                "top_n", topN
        );

        return client.post()
                .uri("/rerank")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(RerankResponse.class)
                .map(RerankResponse::getResults)
                .doOnError(e -> log.error("Cohere rerank error: {}", e.getMessage()));
    }
}
