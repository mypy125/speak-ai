package com.mygitgor.speech.tovoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.HashMap;

public class OpenAITextToSpeechService implements TextToSpeechService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAITextToSpeechService.class);

    private final String apiKey;
    private final ExecutorService executorService;
    private volatile boolean closed = false;
    private Clip currentClip;
    private CompletableFuture<Void> currentSpeechFuture;

    // Голоса OpenAI
    public enum OpenAIVoice {
        ALLOY("alloy"),
        ECHO("echo"),
        FABLE("fable"),
        ONYX("onyx"),
        NOVA("nova"),
        SHIMMER("shimmer");

        private final String value;

        OpenAIVoice(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public OpenAITextToSpeechService(String apiKey) {
        logger.info("Инициализация OpenAI TTS Service...");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API ключ OpenAI обязателен");
        }

        this.apiKey = apiKey;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "OpenAI-TTS-Thread");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("OpenAI TTS Service инициализирован");
    }

    public OpenAITextToSpeechService() {
        this(getApiKeyFromProperties());
    }

    private static String getApiKeyFromProperties() {
        try {
            Properties props = new Properties();
            props.load(OpenAITextToSpeechService.class.getResourceAsStream("/application.properties"));
            return props.getProperty("openai.api.key", "");
        } catch (Exception e) {
            logger.warn("Не удалось загрузить API ключ из свойств", e);
            return "";
        }
    }

    /**
     * Генерация речи через OpenAI API
     */
    private byte[] generateSpeech(String text, OpenAIVoice voice, float speed) throws IOException {
        // Разбиваем длинный текст на части
        if (text.length() > 4000) {
            logger.warn("Текст слишком длинный ({} символов), сокращаем", text.length());
            text = text.substring(0, 4000) + "...";
        }

        try {
            // Используем curl или HTTP клиент для вызова OpenAI API
            return callOpenAITTSAPI(text, voice.getValue(), speed);

        } catch (Exception e) {
            logger.error("Ошибка при вызове OpenAI API", e);
            throw new IOException("Не удалось сгенерировать речь: " + e.getMessage(), e);
        }
    }

    /**
     * Вызов OpenAI API через HTTP запрос
     */
    private byte[] callOpenAITTSAPI(String text, String voice, float speed) throws IOException {
        // Используем ProcessBuilder для вызова curl
        String[] command = {
                "curl",
                "-X", "POST",
                "-H", "Content-Type: application/json",
                "-H", "Authorization: Bearer " + apiKey,
                "--data", buildRequestJson(text, voice, speed),
                "https://api.openai.com/v1/audio/speech"
        };

        logger.debug("Вызов OpenAI TTS API...");

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // Читаем ответ
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        try (InputStream inputStream = process.getInputStream()) {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        // Ждем завершения
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Читаем ошибку
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String error = errorReader.readLine();
                    throw new IOException("OpenAI API error: " + error);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запрос прерван", e);
        }

        byte[] audioData = outputStream.toByteArray();
        logger.debug("Получено {} байт аудио от OpenAI API", audioData.length);

        return audioData;
    }

    private String buildRequestJson(String text, String voice, float speed) {
        return String.format(
                "{\"model\":\"tts-1\",\"input\":\"%s\",\"voice\":\"%s\",\"speed\":%.1f,\"response_format\":\"mp3\"}",
                text.replace("\"", "\\\""),
                voice,
                speed
        );
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        return speakAsync(text, OpenAIVoice.ALLOY, 1.0f);
    }

    public CompletableFuture<Void> speakAsync(String text, OpenAIVoice voice, float speed) {
        if (closed) {
            throw new IllegalStateException("OpenAI TTS Service закрыт");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        executorService.submit(() -> {
            try {
                speakInternal(text, voice, speed, future);
            } catch (Exception e) {
                logger.error("Ошибка при озвучке", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public void speak(String text) {
        speak(text, OpenAIVoice.ALLOY, 1.0f);
    }

    public void speak(String text, OpenAIVoice voice, float speed) {
        if (closed) {
            throw new IllegalStateException("OpenAI TTS Service закрыт");
        }

        try {
            speakInternal(text, voice, speed, null);
        } catch (Exception e) {
            logger.error("Ошибка при озвучке", e);
            throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
        }
    }

    private void speakInternal(String text, OpenAIVoice voice, float speed,
                               CompletableFuture<Void> future) {
        try {
            String cleanText = cleanTextForSpeech(text);
            logger.info("OpenAI TTS: Озвучка текста ({} символов), голос: {}, скорость: {}",
                    cleanText.length(), voice, speed);

            // Генерация речи
            byte[] audioData = generateSpeech(cleanText, voice, speed);

            // Проигрывание аудио
            playAudio(audioData);

            if (future != null) {
                future.complete(null);
            }

            logger.info("OpenAI TTS: Озвучка завершена");

        } catch (Exception e) {
            logger.error("Ошибка при озвучке текста", e);
            if (future != null) {
                future.completeExceptionally(e);
            }
            throw new RuntimeException("Ошибка озвучки", e);
        }
    }

    private void playAudio(byte[] audioData) throws Exception {
        // Создаем временный файл
        Path tempFile = Files.createTempFile("openai_tts_", ".mp3");
        Files.write(tempFile, audioData);

        // Проигрываем через системный плеер
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        if (os.contains("linux")) {
            pb = new ProcessBuilder("mpg123", "-q", tempFile.toString());
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("afplay", tempFile.toString());
        } else if (os.contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", "start", "/MIN", tempFile.toString());
        } else {
            // По умолчанию пытаемся использовать mpg123
            pb = new ProcessBuilder("mpg123", "-q", tempFile.toString());
        }

        Process process = pb.start();
        process.waitFor();

        // Удаляем временный файл
        Files.deleteIfExists(tempFile);
    }

    @Override
    public void stopSpeaking() {
        logger.info("Остановка озвучки OpenAI TTS...");
        // Для простоты - просто прерываем текущий процесс
        if (currentSpeechFuture != null && !currentSpeechFuture.isDone()) {
            currentSpeechFuture.cancel(true);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Проверяем доступность API ключа
            return apiKey != null && !apiKey.trim().isEmpty() &&
                    !apiKey.equals("your-openai-api-key-here");
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, String> getAvailableVoices() {
        Map<String, String> voices = new HashMap<>();
        voices.put("ALLOY", "Alloy - нейтральный голос");
        voices.put("ECHO", "Echo - ясный и четкий");
        voices.put("FABLE", "Fable - выразительный, подходит для историй");
        voices.put("ONYX", "Onyx - глубокий и авторитетный");
        voices.put("NOVA", "Nova - мягкий и дружелюбный");
        voices.put("SHIMMER", "Shimmer - яркий и энергичный");
        return voices;
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
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие OpenAI TTS Service...");

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

        logger.info("OpenAI TTS Service закрыт");
    }
}