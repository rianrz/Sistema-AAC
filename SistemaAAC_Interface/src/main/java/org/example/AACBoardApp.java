package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AACBoardApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("AACBoardApp.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 960, 680);
        stage.setTitle("Gerador de Pranchas CAA - Comunicação Aumentativa e Alternativa");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
