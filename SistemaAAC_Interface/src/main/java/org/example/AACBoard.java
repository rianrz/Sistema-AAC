package org.example;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class AACBoard {

    private static final String HOST = "http://localhost:11434/";
    private static final String MODEL = "hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q4_K_M";

    // Caminho do binário compilado do stable-diffusion.cpp e do modelo
    private static final String SD_BIN = "C:\\caminho\\para\\sd.exe"; // No Linux/Mac use "./sd"
    private static final String SD_MODEL = "C:\\caminho\\para\\modelo-imagem.gguf";

    public static void main(String[] args) {
        OllamaAPI ollamaAPI = new OllamaAPI(HOST);
        ollamaAPI.setRequestTimeoutSeconds(200);

        String instr = "Você monta pranchas de CAA (pictogramas, pt-BR). Para o PEDIDO, liste ~12 itens concretos e relevantes, um por linha, no formato palavra|tipo| (tipo: v/s/a/e/l/p). Só a lista.";

        // Inicializa o cliente ComfyUI 接続
        try (ComfyUIClient comfyClient = new ComfyUIClient();
             Scanner scanner = new Scanner(System.in)) {
            System.out.print("Digite o pedido para a prancha: ");
            String pedido = scanner.nextLine();

            String prompt = """
                    <start_of_turn>user
                    %s

                    PEDIDO: %s<end_of_turn>
                    <start_of_turn>model
                    """.formatted(instr, pedido);

            Options options = new OptionsBuilder()
                    .setTemperature(0.0f)
                    .setNumPredict(320)
                    .build();

            OllamaResult result = ollamaAPI.generate(MODEL, prompt, false, options);
            String resposta = result.getResponse();

            System.out.println("\n--- Resposta da Prancha AAC ---");
            System.out.println(resposta);

            System.out.println("\n--- Iniciando Geração de Imagens via ComfyUI ---");
            String[] linhas = resposta.split("\n");

            for (String linha : linhas) {
                if (linha.contains("|")) {
                    // Extrai a primeira parte antes do pipe (a palavra em si)
                    String palavra = linha.split("\\|")[0].trim();
                    if (!palavra.isEmpty()) {
                        gerarImagemComfy(comfyClient, palavra);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Falha na execução: " + e.getMessage());
        }
    }

    private static void gerarImagemComfy(ComfyUIClient client, String palavra) {
        System.out.println("\nGerando pictograma para: " + palavra);
        String outputFilename = palavra.replaceAll("[^a-zA-Z0-9_-]", "_") + ".png";

        // Bloqueia a execução do loop até que a imagem atual termine de gerar
        CountDownLatch latch = new CountDownLatch(1);
        String promptPositivo = "simple pictogram, white background, single object, " + palavra;
        String promptNegativo = "low quality, bad anatomy, worst quality, text, watermark";

        client.startGenerate(promptPositivo, promptNegativo, new ComfyUIClient.GenerationHandler() {
            @Override
            public void onStart() {
                System.out.println("Renderização iniciada no ComfyUI...");
            }

            @Override
            public void onProgress(int value, int max) {
                System.out.println("Progresso: " + value + "/" + max + " passos");
            }

            @Override
            public void onError(IOException ex) {
                System.err.println("Erro durante a geração da imagem: " + ex.getMessage());
                latch.countDown();
            }

            @Override
            public void onFinish(byte[] imageData) {
                try {
                    Files.write(Path.of(outputFilename), imageData);
                    System.out.println("Salvo com sucesso: " + outputFilename);
                } catch (IOException e) {
                    System.err.println("Erro ao salvar imagem no disco: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }
        });
    }
}