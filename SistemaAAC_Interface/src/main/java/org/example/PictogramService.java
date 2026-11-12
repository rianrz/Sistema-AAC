package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serviço 100% local para geração de pictogramas de CAA via ComfyUI.
 *
 * Utiliza o modelo toonyou_beta6.safetensors (SD 1.5) com workflow otimizado
 * (15 steps, 512x512, CFG 7.5) para gerar pictogramas claros e reconhecíveis.
 *
 * Inclui dicionário de tradução PT-BR → EN para melhorar a compreensão do modelo.
 *
 * Requer ComfyUI rodando em localhost:8188.
 */
public class PictogramService implements AutoCloseable {

    private final ComfyUIClient comfyClient;

    /** Dicionário de tradução para palavras comuns de CAA (PT-BR → descrição em inglês) */
    private static final Map<String, String> TRADUCOES = new HashMap<>();
    static {
        // Alimentos e bebidas
        TRADUCOES.put("água", "a glass of water");
        TRADUCOES.put("agua", "a glass of water");
        TRADUCOES.put("comer", "eating food, a plate with food");
        TRADUCOES.put("beber", "drinking from a glass");
        TRADUCOES.put("comida", "a plate of food");
        TRADUCOES.put("almoço", "lunch meal on a plate");
        TRADUCOES.put("almoco", "lunch meal on a plate");
        TRADUCOES.put("jantar", "dinner meal on a plate");
        TRADUCOES.put("café", "a cup of coffee");
        TRADUCOES.put("cafe", "a cup of coffee");
        TRADUCOES.put("leite", "a glass of milk");
        TRADUCOES.put("suco", "a glass of juice");
        TRADUCOES.put("pão", "a loaf of bread");
        TRADUCOES.put("pao", "a loaf of bread");
        TRADUCOES.put("arroz", "a bowl of rice");
        TRADUCOES.put("feijão", "a bowl of beans");
        TRADUCOES.put("feijao", "a bowl of beans");
        TRADUCOES.put("fruta", "a colorful fruit, apple");
        TRADUCOES.put("maçã", "a red apple");
        TRADUCOES.put("maca", "a red apple");
        TRADUCOES.put("banana", "a yellow banana");
        TRADUCOES.put("laranja", "an orange fruit");
        TRADUCOES.put("bolo", "a birthday cake");
        TRADUCOES.put("sorvete", "an ice cream cone");
        TRADUCOES.put("biscoito", "a cookie");
        TRADUCOES.put("carne", "a piece of meat");
        TRADUCOES.put("ovo", "a fried egg");
        TRADUCOES.put("queijo", "a piece of cheese");
        TRADUCOES.put("bolacha", "a cracker biscuit");
        TRADUCOES.put("garrafa", "a water bottle");
        TRADUCOES.put("copo", "a drinking glass");
        TRADUCOES.put("prato", "a plate");
        TRADUCOES.put("colher", "a spoon");
        TRADUCOES.put("garfo", "a fork");
        TRADUCOES.put("faca", "a knife for eating");

        // Emoções e estados
        TRADUCOES.put("feliz", "a happy smiling face");
        TRADUCOES.put("triste", "a sad crying face");
        TRADUCOES.put("bravo", "an angry face");
        TRADUCOES.put("com medo", "a scared frightened face");
        TRADUCOES.put("medo", "a scared frightened face");
        TRADUCOES.put("cansado", "a tired sleepy face");
        TRADUCOES.put("doente", "a sick person with thermometer");
        TRADUCOES.put("dor", "a person in pain");
        TRADUCOES.put("fome", "a hungry person, empty stomach");
        TRADUCOES.put("sede", "a thirsty person");
        TRADUCOES.put("sono", "a sleepy person in bed");
        TRADUCOES.put("frio", "a person feeling cold, shivering");
        TRADUCOES.put("calor", "a person feeling hot, sweating");
        TRADUCOES.put("bem", "a person feeling good, thumbs up");
        TRADUCOES.put("mal", "a person feeling sick");

        // Ações
        TRADUCOES.put("brincar", "a child playing with toys");
        TRADUCOES.put("dormir", "a person sleeping in bed");
        TRADUCOES.put("tomar banho", "a person taking a shower");
        TRADUCOES.put("banho", "a person taking a shower");
        TRADUCOES.put("escovar", "brushing teeth with a toothbrush");
        TRADUCOES.put("vestir", "putting on clothes");
        TRADUCOES.put("correr", "a person running");
        TRADUCOES.put("pular", "a person jumping");
        TRADUCOES.put("sentar", "a person sitting on a chair");
        TRADUCOES.put("levantar", "a person standing up");
        TRADUCOES.put("andar", "a person walking");
        TRADUCOES.put("nadar", "a person swimming in water");
        TRADUCOES.put("ler", "a person reading a book");
        TRADUCOES.put("escrever", "a hand writing with a pencil");
        TRADUCOES.put("desenhar", "a hand drawing with crayons");
        TRADUCOES.put("pintar", "a hand painting with a brush");
        TRADUCOES.put("cantar", "a person singing with music notes");
        TRADUCOES.put("dançar", "a person dancing");
        TRADUCOES.put("dancar", "a person dancing");
        TRADUCOES.put("ouvir", "a person listening, ear");
        TRADUCOES.put("olhar", "a person looking, eyes");
        TRADUCOES.put("falar", "a person talking, speech bubble");
        TRADUCOES.put("abraçar", "two people hugging");
        TRADUCOES.put("abracar", "two people hugging");
        TRADUCOES.put("beijar", "a kiss");
        TRADUCOES.put("ajudar", "a person helping another");
        TRADUCOES.put("estudar", "a student studying at a desk");
        TRADUCOES.put("trabalhar", "a person working");
        TRADUCOES.put("comprar", "a person shopping with bags");
        TRADUCOES.put("cozinhar", "a person cooking in kitchen");
        TRADUCOES.put("limpar", "a person cleaning");
        TRADUCOES.put("lavar", "washing hands with soap");
        TRADUCOES.put("abrir", "an open door");
        TRADUCOES.put("fechar", "a closed door");
        TRADUCOES.put("dar", "a hand giving a gift");
        TRADUCOES.put("pegar", "a hand grabbing an object");
        TRADUCOES.put("subir", "a person climbing stairs up");
        TRADUCOES.put("descer", "a person going down stairs");
        TRADUCOES.put("esperar", "a person waiting, clock");
        TRADUCOES.put("ir", "a person walking forward, arrow");
        TRADUCOES.put("voltar", "a person turning back, return arrow");
        TRADUCOES.put("parar", "a stop sign");

        // Lugares
        TRADUCOES.put("casa", "a house");
        TRADUCOES.put("escola", "a school building");
        TRADUCOES.put("parque", "a playground with trees");
        TRADUCOES.put("banheiro", "a bathroom with toilet");
        TRADUCOES.put("cozinha", "a kitchen");
        TRADUCOES.put("quarto", "a bedroom with bed");
        TRADUCOES.put("sala", "a living room");
        TRADUCOES.put("hospital", "a hospital building with cross");
        TRADUCOES.put("loja", "a store shop");
        TRADUCOES.put("praia", "a beach with sand and waves");
        TRADUCOES.put("piscina", "a swimming pool with blue water");
        TRADUCOES.put("jardim", "a garden with flowers");
        TRADUCOES.put("rua", "a street road");
        TRADUCOES.put("igreja", "a church building");
        TRADUCOES.put("mercado", "a supermarket");

        // Pessoas e família
        TRADUCOES.put("mamãe", "a mother, mom");
        TRADUCOES.put("mamae", "a mother, mom");
        TRADUCOES.put("mãe", "a mother, mom");
        TRADUCOES.put("mae", "a mother, mom");
        TRADUCOES.put("papai", "a father, dad");
        TRADUCOES.put("pai", "a father, dad");
        TRADUCOES.put("irmão", "a brother, boy");
        TRADUCOES.put("irmao", "a brother, boy");
        TRADUCOES.put("irmã", "a sister, girl");
        TRADUCOES.put("irma", "a sister, girl");
        TRADUCOES.put("vovó", "a grandmother");
        TRADUCOES.put("vovo", "a grandmother");
        TRADUCOES.put("vovô", "a grandfather");
        TRADUCOES.put("avó", "a grandmother");
        TRADUCOES.put("avo", "a grandmother");
        TRADUCOES.put("avô", "a grandfather");
        TRADUCOES.put("professor", "a teacher at blackboard");
        TRADUCOES.put("professora", "a teacher at blackboard");
        TRADUCOES.put("amigo", "two friends together");
        TRADUCOES.put("bebê", "a baby");
        TRADUCOES.put("bebe", "a baby");
        TRADUCOES.put("criança", "a child");
        TRADUCOES.put("crianca", "a child");
        TRADUCOES.put("médico", "a doctor with stethoscope");
        TRADUCOES.put("medico", "a doctor with stethoscope");

        // Objetos
        TRADUCOES.put("bola", "a colorful ball");
        TRADUCOES.put("brinquedo", "a toy");
        TRADUCOES.put("livro", "an open book");
        TRADUCOES.put("caderno", "a notebook");
        TRADUCOES.put("lápis", "a pencil");
        TRADUCOES.put("lapis", "a pencil");
        TRADUCOES.put("caneta", "a pen");
        TRADUCOES.put("celular", "a smartphone");
        TRADUCOES.put("televisão", "a TV screen");
        TRADUCOES.put("televisao", "a TV screen");
        TRADUCOES.put("carro", "a car");
        TRADUCOES.put("ônibus", "a bus");
        TRADUCOES.put("onibus", "a bus");
        TRADUCOES.put("roupa", "clothes, shirt and pants");
        TRADUCOES.put("sapato", "a pair of shoes");
        TRADUCOES.put("chapéu", "a hat");
        TRADUCOES.put("chapeu", "a hat");
        TRADUCOES.put("cadeira", "a chair");
        TRADUCOES.put("mesa", "a table");
        TRADUCOES.put("cama", "a bed");
        TRADUCOES.put("porta", "a door");
        TRADUCOES.put("janela", "a window");
        TRADUCOES.put("chave", "a key");
        TRADUCOES.put("relógio", "a clock");
        TRADUCOES.put("relogio", "a clock");
        TRADUCOES.put("toalha", "a towel");
        TRADUCOES.put("sabonete", "a bar of soap");
        TRADUCOES.put("escova", "a toothbrush");
        TRADUCOES.put("fralda", "a baby diaper");
        TRADUCOES.put("remédio", "medicine pills");
        TRADUCOES.put("remedio", "medicine pills");
        TRADUCOES.put("óculos", "eyeglasses");
        TRADUCOES.put("oculos", "eyeglasses");
        TRADUCOES.put("guarda-chuva", "an umbrella");
        TRADUCOES.put("mochila", "a backpack");
        TRADUCOES.put("boneca", "a doll");
        TRADUCOES.put("carrinho", "a toy car");

        // Natureza e clima
        TRADUCOES.put("sol", "a bright yellow sun");
        TRADUCOES.put("chuva", "rain falling from cloud");
        TRADUCOES.put("nuvem", "a white cloud");
        TRADUCOES.put("árvore", "a green tree");
        TRADUCOES.put("arvore", "a green tree");
        TRADUCOES.put("flor", "a colorful flower");
        TRADUCOES.put("cachorro", "a friendly dog");
        TRADUCOES.put("gato", "a cute cat");
        TRADUCOES.put("pássaro", "a bird");
        TRADUCOES.put("passaro", "a bird");
        TRADUCOES.put("peixe", "a fish");
        TRADUCOES.put("estrela", "a star");
        TRADUCOES.put("lua", "a crescent moon");
        TRADUCOES.put("mar", "the sea with waves");
        TRADUCOES.put("rio", "a river");
        TRADUCOES.put("montanha", "a mountain");
        TRADUCOES.put("gelo", "an ice cube");
        TRADUCOES.put("neve", "snowflakes falling");
        TRADUCOES.put("vento", "wind blowing");
        TRADUCOES.put("fogo", "a fire flame");
        TRADUCOES.put("terra", "the ground, earth");
        TRADUCOES.put("areia", "sand on beach");

        // Conceitos e comunicação
        TRADUCOES.put("sim", "a green checkmark, yes");
        TRADUCOES.put("não", "a red X mark, no");
        TRADUCOES.put("nao", "a red X mark, no");
        TRADUCOES.put("por favor", "a person saying please, polite");
        TRADUCOES.put("obrigado", "a person saying thank you");
        TRADUCOES.put("obrigada", "a person saying thank you");
        TRADUCOES.put("desculpa", "a person apologizing, sorry");
        TRADUCOES.put("mais", "a plus sign, more");
        TRADUCOES.put("menos", "a minus sign, less");
        TRADUCOES.put("grande", "something big, large");
        TRADUCOES.put("pequeno", "something small, tiny");
        TRADUCOES.put("muito", "a lot, many items");
        TRADUCOES.put("pouco", "a few, small amount");
        TRADUCOES.put("acabou", "empty, finished, done");
        TRADUCOES.put("quero", "a person wanting, pointing");
        TRADUCOES.put("gosto", "a heart, love, like");
        TRADUCOES.put("quente", "something hot, steam rising");
        TRADUCOES.put("gelado", "something cold, ice");

        // Praia específico
        TRADUCOES.put("protetor", "sunscreen bottle, sun protection");
        TRADUCOES.put("protetor solar", "sunscreen lotion bottle");
        TRADUCOES.put("hidratação", "a water bottle, hydration");
        TRADUCOES.put("hidratacao", "a water bottle, hydration");
        TRADUCOES.put("biquíni", "a swimsuit");
        TRADUCOES.put("biquini", "a swimsuit");
        TRADUCOES.put("sunga", "swim trunks");
        TRADUCOES.put("guarda-sol", "a beach umbrella");
        TRADUCOES.put("balde", "a sand bucket and shovel");
        TRADUCOES.put("concha", "a seashell");
        TRADUCOES.put("onda", "an ocean wave");
        TRADUCOES.put("areia", "sand on beach");
        TRADUCOES.put("castelo de areia", "a sandcastle");
        TRADUCOES.put("surfar", "a person surfing on a wave");

        // Cores
        TRADUCOES.put("vermelho", "the color red, red circle");
        TRADUCOES.put("azul", "the color blue, blue circle");
        TRADUCOES.put("verde", "the color green, green circle");
        TRADUCOES.put("amarelo", "the color yellow, yellow circle");
        TRADUCOES.put("branco", "the color white, white circle");
        TRADUCOES.put("preto", "the color black, black circle");
        TRADUCOES.put("rosa", "the color pink, pink circle");

        // Números
        TRADUCOES.put("um", "the number 1");
        TRADUCOES.put("dois", "the number 2");
        TRADUCOES.put("três", "the number 3");
        TRADUCOES.put("tres", "the number 3");

        // Tempo
        TRADUCOES.put("hoje", "a calendar showing today");
        TRADUCOES.put("amanhã", "a calendar showing tomorrow");
        TRADUCOES.put("amanha", "a calendar showing tomorrow");
        TRADUCOES.put("ontem", "a calendar showing yesterday");
        TRADUCOES.put("agora", "a clock showing now");
        TRADUCOES.put("depois", "a clock with arrow, later");
        TRADUCOES.put("manhã", "morning sunrise");
        TRADUCOES.put("manha", "morning sunrise");
        TRADUCOES.put("tarde", "afternoon sun");
        TRADUCOES.put("noite", "night sky with moon and stars");

        // Higiene
        TRADUCOES.put("xixi", "a toilet, bathroom");
        TRADUCOES.put("cocô", "a toilet, bathroom");
        TRADUCOES.put("coco", "a toilet, bathroom");
    }

    public PictogramService() throws IOException {
        this.comfyClient = new ComfyUIClient("toonyou_sd15_fast.json");
    }

    /**
     * Traduz uma palavra PT-BR para uma descrição em inglês que o SD entende.
     * Se não encontrar no dicionário, usa a palavra original.
     */
    private String traduzirParaPrompt(String palavra) {
        String chave = palavra.trim().toLowerCase();
        // Tenta com acento
        String traducao = TRADUCOES.get(chave);
        if (traducao != null) return traducao;

        // Tenta sem acento
        String semAcento = Normalizer.normalize(chave, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        traducao = TRADUCOES.get(semAcento);
        if (traducao != null) return traducao;

        // Fallback: usa a palavra em inglês genérico
        System.out.println("⚠️ Palavra '" + palavra + "' não encontrada no dicionário. Usando prompt genérico.");
        return chave;
    }

    /**
     * Gera os bytes da imagem do pictograma para a palavra fornecida via ComfyUI local.
     * Bloqueia até a imagem ser gerada (timeout de 8 minutos).
     */
    public byte[] obterImagemPictograma(String palavra) {
        String palavraTratada = palavra.trim().toLowerCase();
        String descricaoEN = traduzirParaPrompt(palavraTratada);

        String positive = "simple cartoon pictogram of " + descricaoEN
                + ", flat color icon, centered, white background, clean lines, bold outline, "
                + "high contrast, minimalist, single object, cute cartoon style, vector art";
        String negative = "low quality, blurry, complex background, text, words, letters, watermark, "
                + "realistic photo, detailed texture, gradient, multiple objects, "
                + "3d render, shadow, noise, dark background, frame, border";

        System.out.println("🔍 Prompt para '" + palavraTratada + "': " + descricaoEN);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> resultado = new AtomicReference<>(null);
        AtomicReference<Exception> erro = new AtomicReference<>(null);

        try {
            comfyClient.startGenerate(positive, negative, new ComfyUIClient.GenerationHandler() {
                @Override
                public void onStart() {
                    System.out.println("🎨 Gerando pictograma para '" + palavraTratada + "'...");
                }

                @Override
                public void onFinish(byte[] imageData) {
                    System.out.println("✅ Pictograma gerado para '" + palavraTratada + "' (" + imageData.length + " bytes)");
                    resultado.set(imageData);
                    latch.countDown();
                }

                @Override
                public void onProgress(int value, int max) {
                    System.out.println("  ⏳ " + palavraTratada + ": passo " + value + "/" + max);
                }

                @Override
                public void onError(IOException ex) {
                    System.err.println("❌ Erro ao gerar pictograma para '" + palavraTratada + "': " + ex.getMessage());
                    erro.set(ex);
                    latch.countDown();
                }
            });

            // Aguarda até 8 minutos pela geração (CPU 15 steps em 512x512 pode demorar)
            boolean completed = latch.await(8, TimeUnit.MINUTES);
            if (!completed) {
                System.err.println("⏰ Timeout ao gerar pictograma para '" + palavraTratada + "' (8 min)");
                return null;
            }

            if (erro.get() != null) {
                System.err.println("❌ Falha na geração: " + erro.get().getMessage());
                return null;
            }

            return resultado.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ Geração interrompida para '" + palavraTratada + "'");
            return null;
        }
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
            String semAcento = Normalizer.normalize(palavra, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            String nomeArquivo = semAcento.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase() + ".png";
            Path destino = pastaSaida.resolve(nomeArquivo);
            Files.write(destino, dadosImagem);
            System.out.println("💾 Pictograma salvo: " + destino.toAbsolutePath());
            return destino;
        } catch (IOException e) {
            System.err.println("❌ Erro ao salvar imagem no disco: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        if (comfyClient != null) {
            comfyClient.close();
        }
    }
}
