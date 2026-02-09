package com.mygitgor.speech.tovoice.type;

import com.mygitgor.speech.tovoice.TextToSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.HashMap;

public class GroqTextToSpeechService implements TextToSpeechService {
    private static final Logger logger = LoggerFactory.getLogger(GroqTextToSpeechService.class);

    private final String apiKey;
    private final ExecutorService executorService;
    private volatile boolean closed = false;
    private CompletableFuture<Void> currentSpeechFuture;

    // Константы для конфигурации
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    // Голоса Groq (предположительные, нужно проверить документацию)
    public enum GroqVoice {
        ALLOY("alloy", "Нейтральный голос"),
        ECHO("echo", "Четкий голос"),
        NOVA("nova", "Мягкий голос"),
        SHIMMER("shimmer", "Энергичный голос");

        private final String value;
        private final String description;

        GroqVoice(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }

    // Текущая конфигурация
    private GroqVoice currentVoice = GroqVoice.ALLOY;
    private float currentSpeed = 1.0f;
    private String currentModel = "tts-1"; // Уточнить у Groq

    public GroqTextToSpeechService(String apiKey) {
        logger.info("Инициализация Groq TTS Service...");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API ключ Groq обязателен");
        }

        this.apiKey = apiKey;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Groq-TTS-Thread");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("Groq TTS Service инициализирован");
    }

    public GroqTextToSpeechService() {
        this(getApiKeyFromProperties());
    }

    private static String getApiKeyFromProperties() {
        try {
            Properties props = new Properties();
            props.load(GroqTextToSpeechService.class.getResourceAsStream("/application.properties"));
            String key = props.getProperty("groq.api.key", "").trim();

            if (key.isEmpty()) {
                logger.warn("API ключ Groq не найден в application.properties");
                // Пробуем системную переменную
                key = System.getenv("GROQ_API_KEY");
                if (key != null && !key.trim().isEmpty()) {
                    logger.info("Используется API ключ из переменной окружения GROQ_API_KEY");
                }
            }

            return key != null ? key : "";
        } catch (Exception e) {
            logger.warn("Не удалось загрузить API ключ из свойств: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Генерация речи через Groq API
     */
    private byte[] generateSpeech(String text, GroqVoice voice, float speed) throws IOException {
        // Ограничиваем длинный текст
        if (text.length() > MAX_TEXT_LENGTH) {
            logger.warn("Текст слишком длинный ({} символов), сокращаем", text.length());
            text = text.substring(0, MAX_TEXT_LENGTH) + "...";
        }

        try {
            return callGroqTTSAPI(text, voice.getValue(), speed);
        } catch (Exception e) {
            logger.error("Ошибка при вызове Groq API", e);
            throw new IOException("Не удалось сгенерировать речь через Groq: " + e.getMessage(), e);
        }
    }

    /**
     * Вызов Groq TTS API
     */
    private byte[] callGroqTTSAPI(String text, String voice, float speed) throws IOException {
        // Проверяем доступность TTS у Groq
        // NOTE: Нужно проверить точный эндпоинт в документации Groq

        String apiUrl = "https://api.groq.com/openai/v1/audio/speech"; // Предположительный URL
        // Или: "https://api.groq.com/v1/audio/speech"

        logger.debug("Groq TTS запрос: голос={}, скорость={}, текст={} символов",
                voice, speed, text.length());

        String requestJson = buildRequestJson(text, voice, speed);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "MyGitgor-App/1.0");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            // Отправляем запрос
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // Получаем ответ
            int responseCode = connection.getResponseCode();
            logger.debug("HTTP Response Code: {}", responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorResponse = readErrorResponse(connection);
                logger.error("Groq API Error {}: {}", responseCode, errorResponse);

                // Проверяем специфические ошибки
                if (responseCode == 404) {
                    throw new IOException("TTS эндпоинт не найден. Возможно, Groq не поддерживает TTS или URL неверный.");
                } else if (responseCode == 403 || responseCode == 401) {
                    throw new IOException("Проблема с аутентификацией Groq API");
                } else {
                    throw new IOException("Groq API Error: " + responseCode + " - " + errorResponse);
                }
            }

            // Читаем аудио данные
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            byte[] audioData = outputStream.toByteArray();
            logger.info("Получено {} байт аудио от Groq API", audioData.length);

            if (audioData.length < 100) {
                throw new IOException("Слишком маленький ответ от Groq API");
            }

            return audioData;

        } catch (IOException e) {
            logger.error("Сетевая ошибка при вызове Groq API", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            return errorResponse.toString();
        } catch (Exception e) {
            return "Не удалось прочитать ошибку: " + e.getMessage();
        }
    }

    private String buildRequestJson(String text, String voice, float speed) {
        // Очищаем текст
        String cleanText = text.replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();

        // Groq, вероятно, использует совместимый с OpenAI формат
        return String.format(
                "{\"model\":\"%s\",\"input\":\"%s\",\"voice\":\"%s\",\"speed\":%.1f,\"response_format\":\"mp3\"}",
                currentModel,
                cleanText,
                voice,
                speed
        );
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        return speakAsync(text, currentVoice, currentSpeed);
    }

    public CompletableFuture<Void> speakAsync(String text, GroqVoice voice, float speed) {
        if (closed) {
            throw new IllegalStateException("Groq TTS Service закрыт");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        currentSpeechFuture = future;

        executorService.submit(() -> {
            try {
                speakInternal(text, voice, speed, future);
            } catch (Exception e) {
                logger.error("Ошибка при озвучке", e);
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    @Override
    public void speak(String text) {
        speak(text, currentVoice, currentSpeed);
    }

    public void speak(String text, GroqVoice voice, float speed) {
        if (closed) {
            throw new IllegalStateException("Groq TTS Service закрыт");
        }

        try {
            speakInternal(text, voice, speed, null);
        } catch (Exception e) {
            logger.error("Ошибка при озвучке", e);
            throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
        }
    }

    private void speakInternal(String text, GroqVoice voice, float speed,
                               CompletableFuture<Void> future) {
        try {
            String cleanText = cleanTextForSpeech(text);
            logger.info("Groq TTS: Озвучка текста ({} символов), голос: {}, скорость: {}",
                    cleanText.length(), voice, speed);

            // Генерация речи
            byte[] audioData = generateSpeech(cleanText, voice, speed);

            // Проигрывание аудио (можно использовать тот же метод, что и в OpenAITextToSpeechService)
            playAudio(audioData);

            if (future != null && !future.isDone()) {
                future.complete(null);
            }

            logger.info("Groq TTS: Озвучка завершена успешно");

        } catch (Exception e) {
            logger.error("Ошибка при озвучке текста", e);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(e);
            }
        } finally {
            currentSpeechFuture = null;
        }
    }

    private void playAudio(byte[] audioData) throws Exception {
        // Используйте тот же метод воспроизведения, что и в OpenAITextToSpeechService
        // Или вынесите его в утилитный класс

        Path tempFile = Files.createTempFile("groq_tts_", ".mp3");
        Files.write(tempFile, audioData);

        try {
            // Используйте JLayer или системный плеер
            // (код аналогичный OpenAITextToSpeechService.playAudio)
            playWithJLayer(tempFile);
        } finally {
            // Удаление файла
            Files.deleteIfExists(tempFile);
        }
    }

    private void playWithJLayer(Path audioFile) throws Exception {
        // Реализация аналогичная OpenAITextToSpeechService
        // Требуется библиотека JLayer
    }

    @Override
    public void stopSpeaking() {
        logger.info("Остановка озвучки Groq TTS...");
        if (currentSpeechFuture != null && !currentSpeechFuture.isDone()) {
            currentSpeechFuture.cancel(true);
            currentSpeechFuture = null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return apiKey != null &&
                    !apiKey.trim().isEmpty() &&
                    !apiKey.equals("your-groq-api-key-here");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверка доступности Groq TTS API
     */
    public boolean checkAvailability() {
        try {
            // Тестовый запрос
            byte[] testAudio = callGroqTTSAPI("test", "alloy", 1.0f);
            return testAudio.length > 100;
        } catch (Exception e) {
            logger.warn("Groq TTS недоступен: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, String> getAvailableVoices() {
        Map<String, String> voices = new HashMap<>();
        for (GroqVoice voice : GroqVoice.values()) {
            voices.put(voice.name(), voice.getDescription());
        }
        return voices;
    }

    public void setVoice(GroqVoice voice) {
        this.currentVoice = voice;
        logger.info("Установлен голос Groq: {}", voice.name());
    }

    public void setSpeed(float speed) {
        if (speed < 0.25f || speed > 4.0f) {
            throw new IllegalArgumentException("Скорость должна быть от 0.25 до 4.0");
        }
        this.currentSpeed = speed;
        logger.info("Установлена скорость озвучки: {}", speed);
    }

    public void setModel(String model) {
        this.currentModel = model;
        logger.info("Установлена модель TTS: {}", model);
    }

    private String cleanTextForSpeech(String text) {
        if (text == null) return "";
        return text
                .replaceAll("#+\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("`", "")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️🏆🎉👍💪📚🔧❤️✨🌟🔥💡🎯📅❌ℹ️]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;
        logger.info("Закрытие Groq TTS Service...");

        stopSpeaking();

        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Groq TTS Service закрыт");
    }
}