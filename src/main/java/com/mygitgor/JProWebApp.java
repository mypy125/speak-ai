package com.mygitgor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JProWebApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(JProWebApp.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Новая сессия JPro. Подготовка окружения...");

        // Устанавливаем системные свойства для JPro
        System.setProperty("jpro.snapshot.delay", "500");
        System.setProperty("jpro.snapshot.minWidth", "100");
        System.setProperty("jpro.snapshot.debug", "false");

        // Добавляем задержку перед инициализацией
        Thread.sleep(300);

        // Запускаем основное приложение
        App mainApp = new App();

        // Оборачиваем запуск в Platform.runLater для гарантии
        Platform.runLater(() -> {
            try {
                mainApp.start(primaryStage);
                logger.info("Приложение успешно запущено");
            } catch (Exception e) {
                logger.error("Ошибка при запуске приложения", e);
            }
        });

        // Дополнительная проверка размеров после старта
        Platform.runLater(() -> {
            try {
                Thread.sleep(500);
                logger.info("Проверка размеров сцены: ширина={}", primaryStage.getWidth());
                if (primaryStage.getWidth() == 0) {
                    primaryStage.setWidth(1024);
                    primaryStage.setHeight(768);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}