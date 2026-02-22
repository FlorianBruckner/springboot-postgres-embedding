/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.example;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 *
 * @author florianbruckner
 */
public class AbstractOllamaTest {
private static final String BASE_IMAGE = "ollama/ollama:latest";
    private static final String COMMITTED_IMAGE = "ollama-all-minilm:latest";
    protected static OllamaContainer ollama;

    @BeforeAll
    static void setupOllama() throws IOException, InterruptedException {
        boolean imageExists = checkImageExists(COMMITTED_IMAGE);

        if (imageExists) {
            // 1. Fast path: Start the image that already has the model
            ollama = new OllamaContainer(DockerImageName.parse(COMMITTED_IMAGE));
            ollama.start();
        } else {
            // 2. Slow path: Start base, pull model, and commit
            ollama = new OllamaContainer(DockerImageName.parse(BASE_IMAGE));
            ollama.start();

            // Pull the model (this will take a few seconds)
            ollama.execInContainer("ollama", "pull", "all-minilm");

            // Commit the container state to a new local image
            ollama.getDockerClient().commitCmd(ollama.getContainerId())
                    .withRepository("ollama-all-minilm")
                    .withTag("latest")
                    .exec();
            
            System.out.println("✅ Model pulled and image committed for future runs.");
        }
    }

    private static boolean checkImageExists(String imageName) {
        try {
            ollama.getDockerClient().inspectImageCmd(imageName).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }    
}
