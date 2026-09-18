package org.example;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ComfyUIClient implements AutoCloseable {

    private static final String SERVER_ADDRESS = "localhost:8188";
    private static final String CLIENT_ID = "my_client_id";
    private static final String WORKFLOW = "qwen_image_gguf.json";

    private final ObjectNode promptData;
    private WebSocket ws;
    private final HttpClient client;
    private static final ObjectMapper mapper = new ObjectMapper();
    private ComfyHandler comfyHandler;
    private final Map<String, GenerationHandler> handlers = new HashMap<>();
    private final Random rand = new Random();

    public interface ComfyHandler {
        void onOpen();
        void onSid(String sid);
        void onQueueStatus(int remaining);
        void onClose(int statusCode, String reason);
        void onError(Throwable error);
    }

    public interface GenerationHandler {
        void onStart();
        void onFinish(byte[] imageData);
        void onProgress(int value, int max);
        void onError(IOException ex);
    }

    public ComfyUIClient() throws IOException{
        this(null);
    }
    
    public ComfyUIClient(ComfyHandler handler) throws IOException{
        this.comfyHandler = handler;
        client = HttpClient.newHttpClient();
        // プロンプトJSON読み込み
        promptData = loadPromptJson(WORKFLOW);

        // WebSocket接続開始
        startWebSocket();

    }
    public void startGenerate(String positivePrompt, String negativePrompt, GenerationHandler handler) throws UncheckedIOException{
        startGenerate(positivePrompt, negativePrompt, rand.nextInt(9_999_999), handler);
    }
    public void startGenerate(String positivePrompt, String negativePrompt, int seed, GenerationHandler handler) throws UncheckedIOException{
        try {
            var data = createPromptData(promptData, positivePrompt, negativePrompt, seed);
            var id = comfyUIPrompt(data);
            synchronized (handlers) {
                handlers.put(id, handler);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    @Override
    public void close() {
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }
    
    /** WebSocket接続 */
    private void startWebSocket() {
        CountDownLatch wsConnected = new CountDownLatch(1);

        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://" + SERVER_ADDRESS + "/ws?clientId=" + CLIENT_ID),
                        new WSListener())
                .thenAccept(socket -> {
                    ws = socket;
                    wsConnected.countDown();
                })
                .exceptionally(ex -> {
                    System.err.println("❌ Falha ao conectar no WebSocket do ComfyUI: " + ex.getMessage());
                    wsConnected.countDown();
                    return null;
                });

        try {
            if (!wsConnected.await(5, TimeUnit.SECONDS)) {
                System.err.println("⚠️ Conexão com ComfyUI expirou (timeout). O servidor está rodando em localhost:8188?");
            }
        } catch (InterruptedException e) {
            if (comfyHandler != null) comfyHandler.onError(e);
        }
    }

    /** prompt.json 読み込み */
    private ObjectNode loadPromptJson(String path) throws IOException {
        return (ObjectNode) mapper.readTree(Files.readString(Path.of(path), StandardCharsets.UTF_8));
    }
    
    /** プロンプト更新 */
    private ObjectNode createPromptData(ObjectNode n, String positive, String negative, int seed) {
        ObjectNode prompt = n.deepCopy();
        Iterator<String> fields = prompt.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();
            JsonNode node = prompt.get(key);
            if (!(node instanceof ObjectNode obj)) continue;

            String classType = obj.path("class_type").asText();
            ObjectNode inputs = (ObjectNode) obj.path("inputs");

            if ("KSampler".equals(classType) && inputs != null) {
                if (inputs.has("seed")) {
                    inputs.put("seed", seed);
                    System.out.println("🛠️ " + key + " の seed を " + seed + " に設定");
                }

                JsonNode posRef = inputs.get("positive");
                if (posRef != null && posRef.isArray() && posRef.size() > 0) {
                    String refId = posRef.get(0).asText();
                    JsonNode refNode = prompt.get(refId);
                    if (refNode instanceof ObjectNode refObj) {
                        ObjectNode refInputs = (ObjectNode) refObj.path("inputs");
                        refInputs.put("text", positive);
                        System.out.println("📝 Positiveプロンプト: " + positive);
                    }
                }
                JsonNode negRef = inputs.get("negative");
                if (negRef != null && negRef.isArray() && negRef.size() > 0) {
                    String refId = negRef.get(0).asText();
                    JsonNode refNode = prompt.get(refId);
                    if (refNode instanceof ObjectNode refObj) {
                        ObjectNode refInputs = (ObjectNode) refObj.path("inputs");
                        refInputs.put("text", negative);
                        System.out.println("📝 Negativeプロンプト: " + negative);
                    }
                }
                
            }
        }
        return prompt;
    }


    /** ComfyUI: プロンプト送信 */
    private String comfyUIPrompt(ObjectNode prompt) throws IOException{
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("client_id", CLIENT_ID);
            payload.set("prompt", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + SERVER_ADDRESS + "/prompt"))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("📥 サーバー応答: " + response.body());

            JsonNode body = mapper.readTree(response.body());
            String id = body.path("prompt_id").asText(null);
            System.out.println("  ⇨ prompt_id: " + id);
            return id;
        } catch ( InterruptedException e) {
            System.out.println("❌ workflow送信エラー: " + e.getMessage());
            return null;
        }
    }

    /** ComfyUI: 画像取得 */
    private byte[] comfyUIView(String filename, String type, String subfolder) throws IOException {
        String url = String.format("http://%s/view?filename=%s&type=%s&subfolder=%s",
                SERVER_ADDRESS,
                URLEncoder.encode(filename, StandardCharsets.UTF_8),
                URLEncoder.encode(type, StandardCharsets.UTF_8),
                URLEncoder.encode(subfolder, StandardCharsets.UTF_8));

        System.out.println("✅ 画像取得: " + url);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
        try {
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return res.body();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /** WebSocket リスナー */
    private class WSListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        
        @Override
        public void onOpen(WebSocket webSocket) {
            if (comfyHandler != null) comfyHandler.onOpen();
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                handleMessage(buffer.toString());
                buffer.setLength(0);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void handleMessage(String msgStr) {
            try {
                JsonNode msg = mapper.readTree(msgStr);
                String type = msg.path("type").asText();

                switch (type) {
                    case "status" -> {
                        String sid = msg.path("data").path("sid").asText(null);
                        if (sid != null && comfyHandler != null){
                            comfyHandler.onSid(sid);
                        }
                        var remaining = msg.findPath("queue_remaining").asInt(-1);
                        if (remaining >= 0 && comfyHandler != null) {
                            comfyHandler.onQueueStatus(remaining);
                        }
                    }

                    case "progress" -> {
                        JsonNode data = msg.path("data");
                        int value = data.path("value").asInt(0);
                        int max = data.path("max").asInt(1);
                        var id = data.path("prompt_id").asText();

                        GenerationHandler h;
                        synchronized (handlers) {
                            h = handlers.get(id);
                        }
                        if (h != null) h.onProgress(value, max);
                    }

                    case "executed" -> {
                        System.out.println("📥 executed: " + msg);
                        JsonNode data = msg.path("data");
                        var id = data.path("prompt_id").asText();
                        JsonNode images = data.path("output").path("images");
                        if (images.isArray() && images.size() > 0) {
                            ObjectNode img = (ObjectNode) images.get(0);

                            var filename = img.path("filename").asText();
                            var filetype = img.path("type").asText();
                            var subfolder = img.path("subfolder").asText();
                            GenerationHandler h;
                            synchronized (handlers) {
                                h = handlers.get(id);
                            }
                            if (h != null) {
                                try {
                                    byte[] imageData = comfyUIView(filename, filetype, subfolder);
                                    h.onFinish(imageData);
                                } catch (IOException ex) {
                                    h.onError(ex);
                                }
                            }
                        }
                    }

                    case "execution_start" -> {
                        var id = msg.findPath("prompt_id").asText();
                        GenerationHandler h;
                        synchronized (handlers) {
                            h = handlers.get(id);
                        }
                        if (h != null) h.onStart();
                    }
                    case "execution_success" -> {
                        var id = msg.findPath("prompt_id").asText();
                        synchronized (handlers) {
                            handlers.remove(id);
                        }
                    }
                    default -> {} // 無視
                }
            } catch (Exception e) {
                if (comfyHandler != null) comfyHandler.onError(e);
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (comfyHandler != null) comfyHandler.onError(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (comfyHandler != null) comfyHandler.onClose(statusCode, reason);

            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }
}
