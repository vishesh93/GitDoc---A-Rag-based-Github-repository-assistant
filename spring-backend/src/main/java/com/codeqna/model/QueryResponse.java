package com.codeqna.model;

import lombok.Data;

@Data
public class QueryResponse {
    private String answer;
    private java.util.List<CodeCitation> citations;
    private String model;
    private long latencyMs;
}