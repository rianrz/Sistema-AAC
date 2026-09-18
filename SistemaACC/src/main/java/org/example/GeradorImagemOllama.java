package org.example;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.ollama.OllamaImageModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;

public class GeradorImagemOllama {
    public static void main(String[] args) {
        // Configura o modelo de imagem do Ollama através do LangChain4j
        ImageModel imageModel = OllamaImageModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("")
                .timeout(Duration.ofMinutes(5))
                .build();

        Response<Image> response = imageModel.generate("A beautiful cyberpunk city at night");

        System.out.println("URL da imagem gerada: " + response.content().url());
    }
}