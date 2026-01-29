package com.mygitgor;

import com.mygitgor.chatbot.ChatBotController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class App extends Application {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage stage) throws IOException {
        try {
            // Инициализация базы данных
            DatabaseManager.getInstance().initializeDatabase();

            // Загрузка главного окна
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/speakai/views/main.fxml"));
            Parent root = loader.load();

            // Настройка контроллера
            ChatBotController controller = loader.getController();
            controller.setStage(stage);

            // Создание сцены
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(Objects.requireNonNull(
                    getClass().getResource("/styles/main.css")
            ).toExternalForm());

            // Настройка stage
            stage.setTitle("SpeakAI - Разговорный ИИ-бот");
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/logo.png"))
            ));
            stage.setScene(scene);
            stage.show();

            logger.info("Приложение SpeakAI успешно запущено");

        } catch (Exception e) {
            logger.error("Ошибка при запуске приложения", e);
            showErrorDialog("Ошибка запуска",
                    "Не удалось запустить приложение: " + e.getMessage());
        }
    }

    private void showErrorDialog(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        logger.info("Приложение SpeakAI завершает работу");
        // Очистка ресурсов
        DatabaseManager.getInstance().closeConnection();
    }

    public static void main(String[] args) {
        launch(args);
    }
}