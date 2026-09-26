package org.example;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class AACBoard {

    private static final String HOST = "http://localhost:11434/";
    private static final String MODEL = "hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q4_K_M";
    private static final String PASTA_SAIDA = "pranchas_geradas";

    public static void main(String[] args) {
        OllamaAPI ollamaAPI = new OllamaAPI(HOST);
        ollamaAPI.setRequestTimeoutSeconds(200);

        try {
            Files.createDirectories(Path.of(PASTA_SAIDA));
        } catch (IOException e) {
            System.err.println("Erro ao criar a pasta de saída: " + e.getMessage());
            return;
        }

        // 1. PROMPT MELHORADO: Dando exemplos explícitos para forçar o padrão
        String instr = "Você é um especialista em Comunicação Alternativa (CAA). " +
                "Regra OBRIGATÓRIA: Não use números (1., 2.) nem marcadores (-). " +
                "Responda APENAS com a palavra e o tipo separados por um pipe (|). " +
                "Exemplo exato do formato esperado:\n" +
                "água|s|\n" +
                "beber|v|\n" +
                "feliz|a|\n\n" +
                "Gere 12 itens concretos e relevantes para o PEDIDO abaixo. Apenas a lista, sem introdução.";

        try (ComfyUIClient comfyClient = new ComfyUIClient();
             Scanner scanner = new Scanner(System.in)) {

            System.out.print("Digite o tema/pedido para a prancha: ");
            String pedido = scanner.nextLine();

            System.out.println("✅ Pedido recebido: " + pedido);
            System.out.println("⏳ Enviando para o Ollama... Aguarde.");

            String prompt = String.format("<start_of_turn>user\n" +
                    "%s\n\n" +
                    "PEDIDO: %s<end_of_turn>\n" +
                    "<start_of_turn>model\n", instr, pedido);

            Options options = new OptionsBuilder()
                    .setTemperature(0.0f)
                    .setNumPredict(320)
                    .build();

            OllamaResult result = ollamaAPI.generate(MODEL, prompt, false, options);
            String resposta = result.getResponse();

            System.out.println("\n✅ Lista gerada pelo Ollama:");
            System.out.println(resposta);

            System.out.println("\n--- Iniciando Geração de Imagens via ComfyUI ---");
            String[] linhas = resposta.split("\n");

            for (String linha : linhas) {
                // Ignora linhas vazias ou textos de introdução da IA ("Aqui está a lista:")
                if (linha.trim().isEmpty() || linha.toLowerCase().contains("aqui está") || linha.toLowerCase().contains("claro")) {
                    continue;
                }

                // 2. FILTRO INTELIGENTE: Remove números, pontos e traços do início da frase
                // Exemplo: "1. água|s|" ou "- água" vira apenas "água|s|" ou "água"
                String linhaLimpa = linha.replaceAll("^[0-9]+[.-]?\\s*", "").replaceFirst("^-\\s*", "").trim();
                String palavra = linhaLimpa;
                // Se a IA obedeceu e usou o pipe, cortamos tudo que vem depois dele
                if (linhaLimpa.contains("|")) {
                    palavra = linhaLimpa.split("\\|")[0].trim();
                }

                if (!palavra.isEmpty()) {
                    gerarImagemComfy(comfyClient, palavra);
                }
            }

            System.out.println("\n🎉 Processo concluído! Verifique a pasta: " + Path.of(PASTA_SAIDA).toAbsolutePath());

        } catch (Exception e) {
            System.err.println("Falha na execução: " + e.getMessage());
        }
    }

    private static void gerarImagemComfy(ComfyUIClient client, String palavra) {
        System.out.println("\nGerando pictograma para: " + palavra);
        String nomeArquivo = palavra.replaceAll("[^a-zA-Z0-9_-]", "_") + ".png";
        Path caminhoCompleto = Path.of(PASTA_SAIDA, nomeArquivo);

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
                    Files.write(caminhoCompleto, imageData);
                    System.out.println("✅ Salvo com sucesso em: " + caminhoCompleto);
                } catch (IOException e) {
                    System.err.println("Erro ao salvar imagem no disco: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}