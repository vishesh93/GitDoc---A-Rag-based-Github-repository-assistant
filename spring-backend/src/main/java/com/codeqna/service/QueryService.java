package com.codeqna.service;

import com.codeqna.client.CohereClient;
import com.codeqna.client.GroqClient;
import com.codeqna.client.QdrantClient;
import com.codeqna.client.QdrantClient.SearchResult;
import com.codeqna.model.CodeCitation;
import com.codeqna.model.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final GroqClient groqClient;
    private final CohereClient cohereClient;
    private final QdrantClient qdrantClient;
    private final StringRedisTemplate redis;

    private static final int RETRIEVAL_TOP_K = 20;
    private static final int RERANK_TOP_N    = 5;

    public QueryResponse answer(String repoId, String question) {
        long start = System.currentTimeMillis();

        // ── 0. Cache check ─────────────────────────────────────
        String cacheKey = "query:" + repoId + ":" + question.hashCode();
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            QueryResponse r = new QueryResponse();
            r.setAnswer(cached); r.setCitations(List.of());
            r.setModel("cached"); r.setLatencyMs(System.currentTimeMillis() - start);
            return r;
        }

        // ── 1. Embed query via Cohere (search_query input_type) ─
        List<Double> queryVector = cohereClient.embedQuery(question).block();

        // ── 2. Semantic search in Qdrant Cloud ─────────────────
        List<SearchResult> candidates = qdrantClient
                .search(queryVector, repoId, RETRIEVAL_TOP_K)
                .block();

        if (candidates == null || candidates.isEmpty()) {
            QueryResponse empty = new QueryResponse();
            empty.setAnswer("No relevant code found. Make sure ingestion completed successfully.");
            empty.setCitations(List.of());
            return empty;
        }

        // ── 3. Rerank with Cohere rerank-english-v3.0 ──────────
        List<String> docs = candidates.stream()
                .map(r -> (String) r.getPayload().get("content"))
                .toList();

        List<CohereClient.RerankResult> reranked = cohereClient
                .rerank(question, docs, RERANK_TOP_N)
                .block();

        List<SearchResult> topChunks = reranked.stream()
                .map(r -> candidates.get(r.getIndex()))
                .toList();

        // ── 4. Build prompt ─────────────────────────────────────
        String context = buildContext(topChunks);
        String systemPrompt = """
                You are an expert code assistant. Answer the user's question about the codebase
                using ONLY the code context provided. Be concise and precise.
                For each claim, mention the file and function name it comes from.
                If the context doesn't contain enough info, say so honestly.
                """;

        String userPrompt = String.format("""
                Context from the codebase:
                %s

                Question: %s
                """, context, question);

        // ── 5. Generate answer via Groq (llama3, blazing fast) ──
        String answer = groqClient.chat(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        )).block();

        // ── 6. Citations + assemble ─────────────────────────────
        List<CodeCitation> citations = buildCitations(topChunks, reranked);

        QueryResponse response = new QueryResponse();
        response.setAnswer(answer);
        response.setCitations(citations);
        response.setModel("groq/llama3-8b + cohere/rerank-v3");
        response.setLatencyMs(System.currentTimeMillis() - start);

        redis.opsForValue().set(cacheKey, answer, 30, TimeUnit.MINUTES);
        return response;
    }

    private String buildContext(List<SearchResult> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> p = chunks.get(i).getPayload();
            sb.append(String.format("""
                    --- Chunk %d ---
                    File: %s | Function: %s (lines %s–%s) | Language: %s

                    %s

                    """, i + 1,
                    p.get("filePath"), p.get("functionName"),
                    p.get("startLine"), p.get("endLine"),
                    p.get("language"), p.get("content")));
        }
        return sb.toString();
    }

    private List<CodeCitation> buildCitations(
            List<SearchResult> chunks,
            List<CohereClient.RerankResult> reranked) {
        List<CodeCitation> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> p = chunks.get(i).getPayload();
            CodeCitation c = new CodeCitation();
            c.setFilePath((String) p.get("filePath"));
            c.setFunctionName((String) p.get("functionName"));
            c.setStartLine((int) p.get("startLine"));
            c.setEndLine((int) p.get("endLine"));
            c.setCodeSnippet((String) p.get("content"));
            c.setRelevanceScore(reranked.get(i).getRelevanceScore());
            citations.add(c);
        }
        return citations;
    }
}
