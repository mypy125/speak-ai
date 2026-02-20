package com.mygitgor;

import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class JProWebApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(JProWebApp.class);

    private static volatile boolean isServerConfigured = false;

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Новая сессия JPro. Подготовка окружения...");

        // 1. Выполняем глобальную настройку только один раз для всего сервера
        configureServerGloballyOnce();

        // 2. Запускаем само приложение для текущего пользователя (вкладки)
        App mainApp = new App();
        mainApp.start(primaryStage);

        logger.info("Сессия запущена успешно, title: {}", primaryStage.getTitle());
    }

    private synchronized void configureServerGloballyOnce() {
        if (isServerConfigured) {
            return; // Сервер уже настроен предыдущей сессией
        }

        logger.info("=== ГЛОБАЛЬНАЯ ИНИЦИАЛИЗАЦИЯ СЕРВЕРА ===");
        String workDir = determineWorkDirectory();
        configureForWeb(workDir);
        copyResourcesIfNeeded(workDir);

        isServerConfigured = true;
        logger.info("=== ГЛОБАЛЬНАЯ ИНИЦИАЛИЗАЦИЯ ЗАВЕРШЕНА ===");
    }

    private String determineWorkDirectory() {
        String[] possibleDirs = {
                System.getenv("SPEAKAI_HOME"),
                "/app/data",
                "/opt/render/project/data",
                Paths.get(System.getProperty("user.home"), "speakai-web-data").toString(),
                Paths.get(System.getProperty("user.dir"), "data").toString()
        };

        for (String dir : possibleDirs) {
            if (dir == null) continue;
            File directory = new File(dir);
            if (directory.exists() || directory.mkdirs()) {
                if (directory.canWrite()) {
                    logger.info("Using working directory: {}", dir);
                    return dir;
                }
            }
        }

        String fallbackDir = Paths.get(System.getProperty("java.io.tmpdir"), "speakai").toString();
        new File(fallbackDir).mkdirs();
        logger.info("Using fallback working directory: {}", fallbackDir);
        return fallbackDir;
    }

    private void configureForWeb(String workDir) {
        // ОСТОРОЖНО: user.dir лучше не менять в Runtime, но если очень нужно для SQLite:
        System.setProperty("user.dir", workDir);

        String modelsPath = findModelsPath();
        if (modelsPath != null) {
            System.setProperty("vosk.models.path", modelsPath);
            logger.info("Vosk models path set to: {}", modelsPath);
        }

        configureGoogleCloudTTS();
        configureDatabase(workDir);
        createDirectories(workDir);
    }

    private void configureGoogleCloudTTS() {
        String[] possiblePaths = {
                "/app/google-credentials.json",
                "google-credentials.json",
                Paths.get(System.getProperty("user.dir"), "google-credentials.json").toString()
        };

        for (String path : possiblePaths) {
            File credFile = new File(path);
            if (credFile.exists()) {
                System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", credFile.getAbsolutePath());
                logger.info("✅ Google Cloud credentials found at: {}", path);
                return;
            }
        }
        logger.warn("⚠️ Google Cloud credentials file not found! TTS will use demo mode.");
    }

    private void configureDatabase(String workDir) {
        String dbPath = Paths.get(workDir, "data", "speakai.db").toString();
        System.setProperty("sqlite.db.path", dbPath);
        System.setProperty("sqlite.jdbc.url", "jdbc:sqlite:" + dbPath);
        logger.info("Database path: {}", dbPath);
    }

    private String findModelsPath() {
        String[] possiblePaths = {
                System.getenv("VOSK_MODELS_PATH"),
                "/app/models",
                "models",
                Paths.get(System.getProperty("user.dir"), "models").toString(),
                Paths.get("/app", "models").toString()
        };

        for (String path : possiblePaths) {
            if (path == null) continue;
            File modelsDir = new File(path);
            if (modelsDir.exists() && modelsDir.isDirectory()) {
                File[] modelDirs = modelsDir.listFiles(File::isDirectory);
                if (modelDirs != null && modelDirs.length > 0) {
                    return path;
                }
            }
        }
        return null;
    }

    private void copyResourcesIfNeeded(String workDir) {
        try {
            copyResourceFile("/application.properties", Paths.get(workDir, "config", "application.properties"));
            copyResourceFile("/com/mygitgor/view/styles/main.css", Paths.get(workDir, "styles", "main.css"));
            copyResourceFile("/templates/chat-message-template.html", Paths.get(workDir, "templates", "chat-message-template.html"));
            logger.info("Resource files copied successfully");
        } catch (Exception e) {
            logger.warn("Could not copy some resource files: {}", e.getMessage());
        }
    }

    private void copyResourceFile(String resourcePath, Path targetPath) throws Exception {
        InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
        if (resourceStream != null) {
            Files.createDirectories(targetPath.getParent());
            Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void createDirectories(String basePath) {
        String[] dirs = {"data", "recordings", "logs", "exports", "tmp", "config", "templates", "styles"};
        for (String dir : dirs) {
            new File(basePath, dir).mkdirs();
        }
        try {
            File testFile = new File(basePath, "tmp/test_write.tmp");
            Files.createDirectories(testFile.getParentFile().toPath());
            Files.writeString(testFile.toPath(), "test");
            Files.delete(testFile.toPath());
        } catch (Exception e) {
            logger.error("No write permission in {}: {}", basePath, e.getMessage());
        }
    }
}