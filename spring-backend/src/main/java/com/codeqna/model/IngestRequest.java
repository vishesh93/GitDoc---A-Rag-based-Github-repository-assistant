package com.codeqna.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IngestRequest {
    @NotBlank
    @Pattern(regexp = "https://github\\.com/[\\w.-]+/[\\w.-]+(/.*)?",
            message = "Must be a valid GitHub repo URL")
    private String repoUrl;

    private String branch = "main";
}
