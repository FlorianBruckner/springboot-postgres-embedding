package com.dreikraft.ai.embedding.postgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class EmbeddingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingApplication.class, args);
    }
}
