package com.codeqna.model;

import lombok.Data;

@Data
public class CodeCitation {
    private String filePath;
    private String functionName;
    private int startLine;
    private int endLine;
    private String codeSnippet;
    private double relevanceScore;
}