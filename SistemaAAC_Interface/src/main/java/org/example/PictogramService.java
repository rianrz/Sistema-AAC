package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Serviço ultrarrápido para obtenção de pictogramas de CAA (Comunicação Aumentativa e Alternativa).
 *
 * Utiliza o catálogo oficial ARASAAC (padrão internacional em CAA):
 * - Resposta em ~100-300ms por imagem
 * - Não consome recursos de GPU/CPU local
 * - Imagens padronizadas de alta qualidade para pranchas de comunicação
 *
 * Possui fallback automático para Pollinations.ai (IA em nuvem) caso a palavra não exista no ARASAAC.
 */
public class PictogramService {

    private static final String ARASAAC_SEARCH_URL = "https://api.arasaac.org/api/pictograms/pt/bestsearch/";
    private static final String ARASAAC_STATIC_URL = "https://static.arasaac.org/pictograms/%d/%d_500.png";
    private static final String POLLINATIONS_URL = "https://image.pollinations.ai/prompt/%s?width=400&height=400&nologo=true";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PictogramService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Obtém os bytes da imagem do pictograma para a palavra fornecida.
     * Tenta primeiro o ARASAAC; se não encontrar, tenta Pollinations (IA rápida).
     */
    public byte[] obterImagemPictograma(String palavra) {
        String palavraTratada = palavra.trim().toLowerCase();

        // 1. Tentar ARASAAC (Padrão mundial para CAA/Autismo)
        try {
            byte[] imgArasaac = buscarNoArasaac(palavraTratada);
            if (imgArasaac != null && imgArasaac.length > 0) {
                return imgArasaac;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Não foi possível obter do ARASAAC para '" + palavra + "': " + e.getMessage());
        }

        // 2. Fallback: Pollinations.ai (geração rápida via IA na nuvem, sem peso local)
        try {
            System.out.println("🤖 Tentando geração alternativa em nuvem para '" + palavra + "'...");
            byte[] imgAi = gerarViaPollinations(palavraTratada);
            if (imgAi != null && imgAi.length > 0) {
                return imgAi;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Falha também no fallback para '" + palavra + "': " + e.getMessage());
        }

        return null;
    }

    /**
     * Salva o pictograma de uma palavra no diretório de saída especificado.
     * Retorna o Path do arquivo salvo ou null se falhar.
     */
    public Path salvarPictograma(String palavra, Path pastaSaida) {
        byte[] dadosImagem = obterImagemPictograma(palavra);
        if (dadosImagem == null || dadosImagem.length == 0) {
            System.err.println("❌ Falha ao obter imagem para: " + palavra);
            return null;
        }

        try {
            Files.createDirectories(pastaSaida);
            String semAcento = java.text.Normalizer.normalize(palavra, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            String nomeArquivo = semAcento.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase() + ".png";
            Path destino = pastaSaida.resolve(nomeArquivo);
            Files.write(destino, dadosImagem);
            System.out.println("✅ Pictograma salvo: " + destino.toAbsolutePath());
            return destino;
        } catch (IOException e) {
            System.err.println("❌ Erro ao salvar imagem no disco: " + e.getMessage());
            return null;
        }
    }

    private byte[] buscarNoArasaac(String palavra) throws IOException, InterruptedException {
        byte[] res = consultarArasaac(palavra);
        if (res != null) {
            return res;
        }

        // Tenta sem acentos (caso a API ou palavra tenha divergência de grafia)
        String semAcento = java.text.Normalizer.normalize(palavra, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        if (!semAcento.equalsIgnoreCase(palavra)) {
            return consultarArasaac(semAcento);
        }
        return null;
    }

    private byte[] consultarArasaac(String termo) throws IOException, InterruptedException {
        String urlBusca = ARASAAC_SEARCH_URL + URLEncoder.encode(termo, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlBusca))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 || response.body() == null || response.body().trim().isEmpty()) {
            return null;
        }

        JsonNode jsonNode = mapper.readTree(response.body());
        if (!jsonNode.isArray() || jsonNode.size() == 0) {
            return null;
        }

        // Pega o ID do pictograma mais relevante
        int id = jsonNode.get(0).path("_id").asInt(-1);
        if (id <= 0) {
            return null;
        }

        // Baixa a imagem diretamente do static da ARASAAC
        String urlImagem = String.format(ARASAAC_STATIC_URL, id, id);
        HttpRequest reqImg = HttpRequest.newBuilder()
                .uri(URI.create(urlImagem))
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();

        HttpResponse<byte[]> resImg = httpClient.send(reqImg, HttpResponse.BodyHandlers.ofByteArray());
        if (resImg.statusCode() == 200 && resImg.body().length > 100) {
            System.out.println("🏛️ ARASAAC pictograma encontrado para '" + termo + "' (ID: " + id + ")");
            return resImg.body();
        }

        return null;
    }

    private byte[] gerarViaPollinations(String palavra) throws IOException, InterruptedException {
        String prompt = URLEncoder.encode("simple flat pictogram icon of " + palavra + ", white background, high contrast, clean vector style, aac symbol", StandardCharsets.UTF_8);
        String url = String.format(POLLINATIONS_URL, prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200 && response.body().length > 500) {
            System.out.println("✨ Imagem gerada via IA em nuvem para '" + palavra + "'");
            return response.body();
        }

        return null;
    }

    public static void main(String[] args) {
        System.out.println("🧪 Testando PictogramService...");
        PictogramService service = new PictogramService();
        Path pasta = Path.of("pranchas_geradas");

        String[] palavrasTeste = {"água", "comer", "feliz", "escola", "brincar"};
        for (String p : palavrasTeste) {
            long inicio = System.currentTimeMillis();
            Path salvo = service.salvarPictograma(p, pasta);
            long tempo = System.currentTimeMillis() - inicio;
            System.out.println("⏱️ '" + p + "' concluído em " + tempo + " ms -> " + salvo);
        }
    }
}
