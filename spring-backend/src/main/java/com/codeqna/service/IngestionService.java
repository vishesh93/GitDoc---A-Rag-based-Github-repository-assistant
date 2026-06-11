package com.codeqna.service;

import com.codeqna.client.ChunkerClient;
import com.codeqna.client.ChunkerClient.CodeChunk;
import com.codeqna.client.CohereClient;
import com.codeqna.client.QdrantClient;
import com.codeqna.model.IngestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final ChunkerClient chunkerClient;
    private final CohereClient cohereClient;
    private final QdrantClient qdrantClient;
    private final StringRedisTemplate redis;

    // embed-english-light-v3.0 → 384 dims
    private static final int VECTOR_DIM = 384;
    // Cohere allows up to 96 texts per embed call
    private static final int EMBED_BATCH_SIZE = 50;

    public String startIngestion(String repoUrl, String branch) {
        String repoId = UUID.nameUUIDFromBytes(repoUrl.getBytes()).toString();
        redis.opsForValue().set("ingest:status:" + repoId, "PROCESSING");
        ingestAsync(repoId, repoUrl, branch);
        return repoId;
    }

    @Async
    public void ingestAsync(String repoId, String repoUrl, String branch) {
        try {
            log.info("Starting ingestion for repoId={}", repoId);

            // 1. Clone + AST-chunk via Python microservice
            ChunkerClient.ChunkResponse chunked = chunkerClient
                    .chunkRepo(repoUrl, branch)
                    .block();

            if (chunked == null || chunked.getChunks().isEmpty()) {
                redis.opsForValue().set("ingest:status:" + repoId, "FAILED:no_chunks");
                return;
            }

            List<CodeChunk> chunks = chunked.getChunks();
            log.info("Got {} chunks, embedding with Cohere", chunks.size());

            // 2. Ensure Qdrant Cloud collection exists
            qdrantClient.ensureCollection(VECTOR_DIM).block();
            qdrantClient.createRepoIdIndex().block();

            // 3. Embed in batches and upsert into Qdrant
            for (int i = 0; i < chunks.size(); i += EMBED_BATCH_SIZE) {
                List<CodeChunk> batch = chunks.subList(i, Math.min(i + EMBED_BATCH_SIZE, chunks.size()));
                List<String> texts = batch.stream().map(CodeChunk::getContent).toList();

                List<List<Double>> embeddings = cohereClient.embedBatch(texts).block();

                List<Map<String, Object>> points = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    CodeChunk chunk = batch.get(j);
                    points.add(Map.of(
                            "id", chunk.getChunkId(),
                            "vector", embeddings.get(j),
                            "payload", Map.of(
                                    "repoId", repoId,
                                    "filePath", chunk.getFilePath(),
                                    "language", chunk.getLanguage(),
                                    "nodeType", chunk.getNodeType(),
                                    "functionName", chunk.getFunctionName() != null ? chunk.getFunctionName() : "",
                                    "startLine", chunk.getStartLine(),
                                    "endLine", chunk.getEndLine(),
                                    "content", chunk.getContent()
                            )
                    ));
                }

                qdrantClient.upsertPoints(points).block();
                log.info("Embedded & stored batch {}/{}", (i / EMBED_BATCH_SIZE) + 1,
                        (int) Math.ceil((double) chunks.size() / EMBED_BATCH_SIZE));
            }

            redis.opsForValue().set("ingest:status:" + repoId,
                    "DONE:" + chunks.size(), 24, TimeUnit.HOURS);
            log.info("Ingestion done for repoId={}, {} chunks", repoId, chunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed for repoId={}: {}", repoId, e.getMessage(), e);
            redis.opsForValue().set("ingest:status:" + repoId, "FAILED:" + e.getMessage());
        }
    }

    public IngestResponse getStatus(String repoId) {
        String status = redis.opsForValue().get("ingest:status:" + repoId);
        if (status == null) return null;
        if (status.startsWith("DONE:")) {
            return IngestResponse.done(repoId, Integer.parseInt(status.split(":")[1]));
        } else if (status.startsWith("FAILED:")) {
            IngestResponse r = new IngestResponse();
            r.setRepoId(repoId); r.setStatus("FAILED"); r.setMessage(status.substring(7));
            return r;
        }
        return IngestResponse.processing(repoId);
    }
}
