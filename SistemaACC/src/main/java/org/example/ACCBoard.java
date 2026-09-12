package org.example;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.util.Scanner;

public class ACCBoard {

    private static final String HOST = "http://localhost:11434/";
    private static final String MODEL = "hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q4_K_M";

    public static void main(String[] args) throws Exception {
        OllamaAPI ollamaAPI = new OllamaAPI(HOST);
        ollamaAPI.setRequestTimeoutSeconds(200);
        Scanner s= new Scanner(System.in);
        String instr = "Você monta pranchas de CAA (pictogramas, pt-BR). Para o PEDIDO, liste ~12 itens concretos e relevantes, "
                + "um por linha, no formato palavra|tipo| (tipo: v/s/a/e/l/p). Só a lista.";
        String pedido = s.nextLine();

        // Monta o prompt exato exigido pela arquitetura Gemma
        String prompt = "<start_of_turn>user\n" + instr + "\n\nPEDIDO: " + pedido + "<end_of_turn>\n<start_of_turn>model\n";

        // Configura a temperatura = 0 e n_predict = 320 como no exemplo Python
        Options options = new OptionsBuilder()
                .setTemperature(0.0f)
                .setNumPredict(320)
                .build();

        // Faz a chamada síncrona enviando as opções de inferência
        OllamaResult result = ollamaAPI.generate(MODEL, prompt, false, options);

        String conteudo = result.getResponse();

        System.out.println("--- Resposta da Prancha AAC ---");
        System.out.println(conteudo);
    }
}
