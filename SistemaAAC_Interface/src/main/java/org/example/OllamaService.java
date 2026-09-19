package org.example;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;

public class OllamaService {

    private static final String HOST = "http://localhost:11434/";
    private static final String MODEL = "hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q4_K_M";
    private final OllamaAPI ollamaAPI;

    public OllamaService() {
        this.ollamaAPI = new OllamaAPI(HOST);
        this.ollamaAPI.setRequestTimeoutSeconds(200);
    }

    public List<String> gerarPalavras(String pedido) throws Exception {
        String instr = "Você monta pranchas de CAA (pictogramas, pt-BR). Para o PEDIDO, liste ~12 itens concretos e relevantes, um por linha, no formato palavra|tipo| (tipo: v/s/a/e/l/p). Só a lista.";

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

        // Filtra e retorna apenas as palavras limpas
        List<String> palavras = new ArrayList<>();
        String[] linhas = resposta.split("\n");
        for (String linha : linhas) {
            if (linha.contains("|")) {
                String palavra = linha.split("\\|")[0].trim();
                if (!palavra.isEmpty()) {
                    palavras.add(palavra);
                }
            }
        }

        return palavras;
    }
}