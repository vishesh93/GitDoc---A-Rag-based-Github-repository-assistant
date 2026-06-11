package com.codeqna.controller;

import com.codeqna.model.IngestRequest;
import com.codeqna.model.IngestResponse;
import com.codeqna.model.QueryRequest;
import com.codeqna.model.QueryResponse;
import com.codeqna.service.IngestionService;
import com.codeqna.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CodeQnaController {

    private final IngestionService ingestionService;
    private final QueryService queryService;

    /**
     * POST /api/v1/ingest
     * Triggers async ingestion of a GitHub repo.
     * Returns immediately with a repoId to poll.
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        log.info("Ingest request for repo: {}", request.getRepoUrl());
        String repoId = ingestionService.startIngestion(request.getRepoUrl(), request.getBranch());
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(IngestResponse.processing(repoId));
    }

    /**
     * GET /api/v1/ingest/{repoId}/status
     * Poll ingestion progress: PROCESSING | DONE | FAILED
     */
    @GetMapping("/ingest/{repoId}/status")
    public ResponseEntity<IngestResponse> ingestionStatus(@PathVariable String repoId) {
        IngestResponse status = ingestionService.getStatus(repoId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * POST /api/v1/query
     * Ask a natural language question about an ingested repo.
     * Returns answer + code citations.
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.info("Query for repoId={}: {}", request.getRepoId(), request.getQuestion());

        // Guard: make sure ingestion is done
        IngestResponse status = ingestionService.getStatus(request.getRepoId());
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"DONE".equals(status.getStatus())) {
            QueryResponse pending = new QueryResponse();
            pending.setAnswer("Repo ingestion is not complete yet. Status: " + status.getStatus());
            pending.setCitations(java.util.List.of());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(pending);
        }

        QueryResponse response = queryService.answer(request.getRepoId(), request.getQuestion());
        return ResponseEntity.ok(response);
    }
}
