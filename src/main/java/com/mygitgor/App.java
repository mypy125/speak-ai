package com.mygitgor;

import com.mygitgor.chatbot.ChatBotController;
import com.mygitgor.repository.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class App extends Application {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage stage) throws IOException {
        try {
            logger.info("Запуск приложения SpeakAI...");

            // Создаем необходимые директории
            createApplicationDirectories();

            // Инициализация базы данных
            DatabaseManager.getInstance().initializeDatabase();
            logger.info("База данных инициализирована");

            // Загрузка главного окна - правильный путь для вашей структуры
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mygitgor/view/fxml/main.fxml"));
            Parent root = loader.load();
            logger.info("FXML файл загружен успешно");

            // Настройка контроллера
            ChatBotController controller = loader.getController();
            controller.setStage(stage);
            logger.info("Контроллер инициализирован");

            // Создание сцены
            Scene scene = new Scene(root, 1200, 800);

            // Загрузка CSS - правильный путь
            String cssPath = "/com/mygitgor/view/styles/main.css";
            InputStream cssStream = getClass().getResourceAsStream(cssPath);

            if (cssStream != null) {
                scene.getStylesheets().add(Objects.requireNonNull(
                        getClass().getResource(cssPath)
                ).toExternalForm());
                logger.info("CSS стили загружены: {}", cssPath);
            } else {
                logger.warn("CSS файл не найден: {}", cssPath);
            }

            stage.setTitle("SpeakAI - Разговорный ИИ-бот для изучения английского");

            String iconPath = "/com/mygitgor/view/icons/logo.png";
            InputStream iconStream = getClass().getResourceAsStream(iconPath);

            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
                logger.info("Иконка загружена: {}", iconPath);
            } else {
                logger.warn("Иконка не найдена: {}", iconPath);
            }

            stage.setScene(scene);
            stage.setMinWidth(1000);
            stage.setMinHeight(700);

            // Обработка закрытия окна
            stage.setOnCloseRequest(event -> {
                logger.info("Пользователь закрыл приложение");
                DatabaseManager.getInstance().closeConnection();
            });

            stage.show();
            logger.info("Окно приложения отображено");

            logger.info("Приложение SpeakAI успешно запущено");

        } catch (Exception e) {
            logger.error("Ошибка при запуске приложения", e);
            showErrorDialog("Ошибка запуска SpeakAI",
                    "Не удалось запустить приложение. Подробности:\n\n" +
                            e.getMessage() +
                            "\n\nПроверьте:\n" +
                            "1. Наличие всех ресурсных файлов\n" +
                            "2. Корректность структуры проекта\n" +
                            "3. Наличие необходимых библиотек");

            // Завершаем приложение при критической ошибке
            Platform.exit();
            System.exit(1);
        }
    }

    /**
     * Создание необходимых директорий при запуске
     */
    private void createApplicationDirectories() {
        try {
            // Создаем директории для данных приложения
            String[] directories = {
                    "data",          // База данных
                    "recordings",    // Аудиозаписи
                    "logs",          // Логи
                    "exports"        // Экспортируемые файлы
            };

            for (String dir : directories) {
                java.io.File directory = new java.io.File(dir);
                if (!directory.exists()) {
                    if (directory.mkdirs()) {
                        logger.info("Создана директория: {}", dir);
                    } else {
                        logger.warn("Не удалось создать директорию: {}", dir);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при создании директорий", e);
        }
    }

    private void showErrorDialog(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR
                );
                alert.setTitle(title);
                alert.setHeaderText("Критическая ошибка");
                alert.setContentText(message);
                alert.setResizable(true);
                alert.getDialogPane().setPrefSize(600, 400);
                alert.showAndWait();
            } catch (Exception e) {
                // Если даже диалог не может показаться, пишем в консоль
                System.err.println(title + ": " + message);
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stop() {
        logger.info("Приложение SpeakAI завершает работу");
        // Очистка ресурсов
        DatabaseManager.getInstance().closeConnection();

        // Логируем завершение работы
        logger.info("Работа приложения завершена корректно");
    }

    public static void main(String[] args) {
        logger.info("Запуск метода main()");

        // Проверка Java версии
        String javaVersion = System.getProperty("java.version");
        logger.info("Версия Java: {}", javaVersion);

        // Проверка наличия JavaFX
        try {
            Class.forName("javafx.application.Application");
            logger.info("JavaFX найден");
        } catch (ClassNotFoundException e) {
            logger.error("JavaFX не найден! Убедитесь, что используете JDK с JavaFX");
            System.err.println("ОШИБКА: JavaFX не найден!");
            System.err.println("Установите JDK с поддержкой JavaFX или добавьте JavaFX в classpath");
            System.exit(1);
        }

        // Запуск JavaFX приложения
        launch(args);
    }
}