package org.example;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

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

        // 1. PROMPT: Especialista em Comunicação Aumentativa e Alternativa (CAA)
        String instr = "Você é um especialista em Comunicação Alternativa (CAA). " +
                "Regra OBRIGATÓRIA: Não use números (1., 2.) nem marcadores (-). " +
                "Responda APENAS com a palavra e o tipo separados por um pipe (|). " +
                "Exemplo exato do formato esperado:\n" +
                "água|s|\n" +
                "beber|v|\n" +
                "feliz|a|\n\n" +
                "Gere 12 itens concretos e relevantes para o PEDIDO abaixo. Apenas a lista, sem introdução.";

        PictogramService pictogramService;
        try {
            pictogramService = new PictogramService();
        } catch (IOException e) {
            System.err.println("❌ Não foi possível conectar ao ComfyUI. Certifique-se que está rodando em localhost:8188.");
            System.err.println("   Erro: " + e.getMessage());
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {

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

            System.out.println("\n--- Iniciando Geração Rápida de Pictogramas (Padrão CAA / ARASAAC) ---");
            String[] linhas = resposta.split("\n");

            int geradas = 0;
            for (String linha : linhas) {
                // Ignora linhas vazias ou textos de introdução da IA
                if (linha.trim().isEmpty() || linha.toLowerCase().contains("aqui está") || linha.toLowerCase().contains("claro")) {
                    continue;
                }

                // Filtro inteligente: Remove números, pontos e traços do início
                String linhaLimpa = linha.replaceAll("^[0-9]+[.-]?\\s*", "").replaceFirst("^-\\s*", "").trim();
                String palavra = linhaLimpa;
                if (linhaLimpa.contains("|")) {
                    palavra = linhaLimpa.split("\\|")[0].trim();
                }

                if (!palavra.isEmpty()) {
                    Path salvo = pictogramService.salvarPictograma(palavra, Path.of(PASTA_SAIDA));
                    if (salvo != null) {
                        geradas++;
                    }
                }
            }

            System.out.println("\n🎉 Processo concluído com sucesso!");
            System.out.println("📊 Total de imagens obtidas: " + geradas);
            System.out.println("📁 Verifique a pasta: " + Path.of(PASTA_SAIDA).toAbsolutePath());

        } catch (Exception e) {
            System.err.println("Falha na execução: " + e.getMessage());
        }
    }
}
