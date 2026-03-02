package com.mygitgor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;

public class JProWebApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(JProWebApp.class);

    @Override
    public void init() throws Exception {
        logger.info("=== JProWebApp.init() ===");

        logger.info("Java version: {}", System.getProperty("java.version"));
        logger.info("Java vendor: {}", System.getProperty("java.vendor"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        logger.info("User dir: {}", System.getProperty("user.dir"));
        logger.info("Current PID: {}", ProcessHandle.current().pid());

        logger.info("=== JPro Environment Variables ===");
        System.getenv().forEach((key, value) -> {
            if (key.contains("JPRO") || key.contains("jpro") || key.contains("PORT")) {
                logger.info("ENV: {} = {}", key, value);
            }
        });

        logger.info("=== System Properties BEFORE JPro settings ===");
        Properties props = System.getProperties();
        props.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.contains("jpro") || keyStr.contains("javafx") ||
                    keyStr.contains("glass") || keyStr.contains("prism") ||
                    keyStr.contains("monocle") || keyStr.contains("http.port")) {
                logger.info("Property: {} = {}", key, value);
            }
        });

        super.init();
        logger.info("=== JProWebApp.init() completed ===");
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("=== JProWebApp.start() ===");

        try {
            logger.info("Setting JPro properties...");
            System.setProperty("jpro.snapshot.delay", "500");
            System.setProperty("jpro.snapshot.minWidth", "100");
            System.setProperty("jpro.snapshot.debug", "false");
            logger.info("JPro properties set: delay=500, minWidth=100, debug=false");

            logger.info("=== System Properties AFTER JPro settings ===");
            Properties props = System.getProperties();
            props.forEach((key, value) -> {
                String keyStr = key.toString();
                if (keyStr.contains("jpro") || keyStr.contains("javafx") ||
                        keyStr.contains("glass") || keyStr.contains("prism") ||
                        keyStr.contains("monocle") || keyStr.contains("http.port")) {
                    logger.info("Property: {} = {}", key, value);
                }
            });

            String httpPort = System.getProperty("http.port");
            String jproPort = System.getProperty("jpro.port");
            logger.info("HTTP port from properties: {}", httpPort);
            logger.info("JPro port from properties: {}", jproPort);

            logger.info("Waiting 300ms before starting main app...");
            Thread.sleep(300);

            logger.info("Creating main App instance...");
            App mainApp = new App();

            Platform.runLater(() -> {
                try {
                    logger.info("Starting main App in Platform.runLater...");
                    mainApp.start(primaryStage);
                    logger.info("Main App started successfully");
                    logger.info("Stage title: {}", primaryStage.getTitle());
                    logger.info("Stage width: {}, height: {}", primaryStage.getWidth(), primaryStage.getHeight());
                } catch (Exception e) {
                    logger.error("FATAL ERROR in main App start", e);
                }
            });

            Platform.runLater(() -> {
                try {
                    Thread.sleep(500);
                    logger.info("Checking stage dimensions after startup...");
                    logger.info("Stage width: {}, height: {}", primaryStage.getWidth(), primaryStage.getHeight());

                    if (primaryStage.getWidth() == 0) {
                        logger.warn("Stage width is 0, forcing to 1024x768");
                        primaryStage.setWidth(1024);
                        primaryStage.setHeight(768);
                        logger.info("Stage size forced to {}x{}", primaryStage.getWidth(), primaryStage.getHeight());
                    }

                    logger.info("Stage is showing: {}", primaryStage.isShowing());
                    logger.info("Stage is focused: {}", primaryStage.isFocused());

                } catch (InterruptedException e) {
                    logger.warn("Interrupted while checking stage dimensions", e);
                    Thread.currentThread().interrupt();
                }
            });

            logger.info("JProWebApp.start() completed successfully");

        } catch (Exception e) {
            logger.error("FATAL ERROR in JProWebApp.start()", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("=== JProWebApp.stop() ===");
        logger.info("Application is stopping");
        super.stop();
        logger.info("=== JProWebApp.stop() completed ===");
    }
}