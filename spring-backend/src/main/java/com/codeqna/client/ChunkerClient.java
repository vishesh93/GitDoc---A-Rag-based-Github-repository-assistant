package com.codeqna.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component

public class ChunkerClient {

    private final WebClient client;

    public ChunkerClient(@Qualifier("chunkerWebClient") WebClient client) {
        this.client = client;
    }
    // ── DTOs that mirror the Python service's schema ──────────
    @Data
    public static class ChunkRequest {
        private String repoUrl;
        private String branch;
    }

    @Data
    public static class CodeChunk {
        private String chunkId;
        private String filePath;
        private String language;
        private String nodeType;      // "function" | "class" | "module"
        private String functionName;
        private int startLine;
        private int endLine;
        private String content;
    }

    @Data
    public static class ChunkResponse {
        private String repoId;
        private List<CodeChunk> chunks;
        private int totalChunks;
    }

    // ── API call ──────────────────────────────────────────────
    public Mono<ChunkResponse> chunkRepo(String repoUrl, String branch) {
        ChunkRequest req = new ChunkRequest();
        req.setRepoUrl(repoUrl);
        req.setBranch(branch);

        log.info("Calling chunker service for repo: {}", repoUrl);

        return client.post()
                .uri("/chunk")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(ChunkResponse.class)
                .doOnSuccess(r -> log.info("Chunker returned {} chunks for repoId={}", r.getTotalChunks(), r.getRepoId()))
                .doOnError(e -> log.error("Chunker service error: {}", e.getMessage()));
    }
}
