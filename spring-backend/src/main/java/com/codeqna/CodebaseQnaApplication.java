package com.codeqna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodebaseQnaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodebaseQnaApplication.class, args);
    }
}
