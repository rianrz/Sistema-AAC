package org.example;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

public class AACBoardAppController {

    @FXML
    private TextField campoTema;

    @FXML
    private Button botaoGerar;

    @FXML
    private ProgressIndicator indicadorProgresso;

    @FXML
    private Label rotuloStatus;

    @FXML
    private FlowPane containerCards;

    private final OllamaService ollamaService = new OllamaService();
    private final PictogramService pictogramService = new PictogramService();
    private static final Path PASTA_SAIDA = Path.of("pranchas_geradas");

    @FXML
    private void aoClicarGerar() {
        String tema = campoTema.getText();
        if (tema == null || tema.trim().isEmpty()) {
            rotuloStatus.setText("⚠️ Por favor, digite um tema ou contexto antes de gerar.");
            return;
        }

        botaoGerar.setDisable(true);
        indicadorProgresso.setVisible(true);
        rotuloStatus.setText("⏳ Solicitando itens relevantes ao Ollama...");
        containerCards.getChildren().clear();

        Task<Void> tarefaGeracao = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // 1. Gerar palavras com Ollama
                List<String> palavras = ollamaService.gerarPalavras(tema);
                if (palavras.isEmpty()) {
                    Platform.runLater(() -> rotuloStatus.setText("⚠️ Nenhuma palavra gerada pelo modelo para este tema."));
                    return null;
                }

                Platform.runLater(() -> rotuloStatus.setText("📥 " + palavras.size() + " itens recebidos. Obtendo pictogramas rápidos..."));

                // 2. Para cada palavra, obter o pictograma de forma rápida
                int index = 1;
                for (String palavra : palavras) {
                    final int num = index++;
                    Platform.runLater(() -> rotuloStatus.setText(String.format("🎨 Obtendo pictograma (%d/%d): %s...", num, palavras.size(), palavra)));

                    byte[] imgBytes = pictogramService.obterImagemPictograma(palavra);
                    // Também salva em disco para persistência
                    pictogramService.salvarPictograma(palavra, PASTA_SAIDA);

                    Platform.runLater(() -> {
                        VBox card = criarCardPictograma(palavra, imgBytes);
                        containerCards.getChildren().add(card);
                    });
                }

                Platform.runLater(() -> rotuloStatus.setText("🎉 Prancha pronta! Todos os pictogramas foram gerados com sucesso."));
                return null;
            }
        };

        tarefaGeracao.setOnSucceeded(e -> {
            botaoGerar.setDisable(false);
            indicadorProgresso.setVisible(false);
        });

        tarefaGeracao.setOnFailed(e -> {
            botaoGerar.setDisable(false);
            indicadorProgresso.setVisible(false);
            Throwable err = tarefaGeracao.getException();
            rotuloStatus.setText("❌ Erro durante o processo: " + (err != null ? err.getMessage() : "Desconhecido"));
            if (err != null) {
                err.printStackTrace();
            }
        });

        Thread thread = new Thread(tarefaGeracao);
        thread.setDaemon(true);
        thread.start();
    }

    private VBox criarCardPictograma(String palavra, byte[] imgBytes) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(140, 160);
        card.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12px;" +
                "-fx-border-color: #bdc3c7;" +
                "-fx-border-radius: 12px;" +
                "-fx-border-width: 2px;" +
                "-fx-padding: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 6, 0, 0, 2);"
        );

        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);

        if (imgBytes != null && imgBytes.length > 0) {
            imageView.setImage(new Image(new ByteArrayInputStream(imgBytes)));
        }

        Label label = new Label(palavra.toUpperCase());
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2c3e50;");
        label.setWrapText(true);

        card.getChildren().addAll(imageView, label);

        // Feedback visual ao passar o mouse
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: #f8f9fa;" +
                "-fx-background-radius: 12px;" +
                "-fx-border-color: #3498db;" +
                "-fx-border-radius: 12px;" +
                "-fx-border-width: 2px;" +
                "-fx-padding: 10;" +
                "-fx-cursor: hand;" +
                "-fx-effect: dropshadow(gaussian, rgba(52,152,219,0.3), 8, 0, 0, 3);"
        ));

        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12px;" +
                "-fx-border-color: #bdc3c7;" +
                "-fx-border-radius: 12px;" +
                "-fx-border-width: 2px;" +
                "-fx-padding: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 6, 0, 0, 2);"
        ));

        return card;
    }
}
