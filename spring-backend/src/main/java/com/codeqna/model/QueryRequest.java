package com.codeqna.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryRequest {
    @NotBlank
    private String repoId;

    @NotBlank
    private String question;
}
