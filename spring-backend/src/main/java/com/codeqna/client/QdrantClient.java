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
 * Qdrant Cloud — free 1GB cluster, no download needed.
 * Create cluster at https://cloud.qdrant.io (free forever tier).
 */
@Slf4j
@Component

public class QdrantClient {

    private final WebClient client;

    public QdrantClient(@Qualifier("qdrantWebClient") WebClient client) {
        this.client = client;
    }
    @Value("${app.qdrant.collection}")
    private String collection;

    public Mono<Void> upsertPoints(List<Map<String, Object>> points) {
        Map<String, Object> body = Map.of("points", points);
        return client.put()
                .uri("/collections/{collection}/points", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Qdrant upsert error: {}", e.getMessage()));
    }

    @Data
    public static class SearchResult {
        private String id;
        private double score;
        private Map<String, Object> payload;
    }

    @Data
    public static class SearchResponse {
        private List<SearchResult> result;
    }

    public Mono<List<SearchResult>> search(List<Double> queryVector, String repoId, int topK) {

        Map<String, Object> body = Map.of(
                "vector", queryVector,
                "limit", topK,
                "filter", Map.of(
                        "must", List.of(
                                Map.of(
                                        "key", "repoId",
                                        "match", Map.of(
                                                "value", repoId
                                        )
                                )
                        )
                ),
                "with_payload", true
        );

        log.info("Searching Qdrant. vectorSize={}, repoId={}, topK={}",
                queryVector.size(), repoId, topK);

        return client.post()
                .uri("/collections/{collection}/points/search", collection)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Qdrant error body: {}", errorBody);
                                    return Mono.error(new RuntimeException(errorBody));
                                })
                )
                .bodyToMono(SearchResponse.class)
                .doOnNext(r -> log.info("Qdrant returned {} results",
                        r.getResult() == null ? 0 : r.getResult().size()))
                .map(SearchResponse::getResult);
    }

    public Mono<Void> ensureCollection(int vectorSize) {
        Map<String, Object> body = Map.of(
                "vectors", Map.of("size", vectorSize, "distance", "Cosine")
        );
        return client.put()
                .uri("/collections/{collection}", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> {
                    log.warn("Collection may already exist: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> createRepoIdIndex() {

        Map<String, Object> body = Map.of(
                "field_name", "repoId",
                "field_schema", "keyword"
        );

        return client.put()
                .uri("/collections/{collection}/index", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v ->
                        log.info("Created repoId payload index"))
                .onErrorResume(e -> {
                    log.warn("repoId index may already exist: {}",
                            e.getMessage());
                    return Mono.empty();
                });
    }
}
