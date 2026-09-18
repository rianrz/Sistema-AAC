module org.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires ollama4j;

    opens org.example to javafx.fxml;
    exports org.example;
}
