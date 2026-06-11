package com.codeqna.model;

import lombok.Data;

@Data
public class IngestResponse {
    private String repoId;
    private String status;   // PROCESSING | DONE | FAILED
    private int totalChunks;
    private String message;

    public static IngestResponse processing(String repoId) {
        IngestResponse r = new IngestResponse();
        r.repoId = repoId;
        r.status = "PROCESSING";
        r.message = "Repo is being ingested asynchronously";
        return r;
    }

    public static IngestResponse done(String repoId, int chunks) {
        IngestResponse r = new IngestResponse();
        r.repoId = repoId;
        r.status = "DONE";
        r.totalChunks = chunks;
        r.message = "Ingestion complete";
        return r;
    }
}