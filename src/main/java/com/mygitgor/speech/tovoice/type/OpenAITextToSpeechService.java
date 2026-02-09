package com.mygitgor.speech.tovoice.type;

import com.mygitgor.speech.tovoice.TextToSpeechService;
import javazoom.jl.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sound.sampled.*;
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
        // Ограничиваем текст (OpenAI TTS имеет лимит 4096 символов)
        if (text.length() > 2000) {
            text = text.substring(0, 2000) + " [text truncated]";
            logger.warn("Текст сокращен до {} символов", text.length());
        }

        String requestJson = buildRequestJson(text, voice, speed);
        logger.debug("JSON запрос: {}", requestJson);

        try {
            // Используем HttpURLConnection вместо curl для лучшего контроля
            URL url = new URL("https://api.openai.com/v1/audio/speech");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
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
                // Читаем ошибку
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    logger.error("OpenAI API Error {}: {}", responseCode, errorResponse.toString());
                    throw new IOException("OpenAI API Error: " + responseCode + " - " + errorResponse.toString());
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
            logger.info("Получено {} байт аудио от OpenAI API", audioData.length);

            // Проверяем минимальный размер аудио
            if (audioData.length < 1000) {
                String responseText = new String(audioData, StandardCharsets.UTF_8);
                logger.error("Слишком маленький ответ ({} байт). Содержимое: {}",
                        audioData.length, responseText);
                throw new IOException("Неверный ответ от OpenAI API: " + responseText);
            }

            return audioData;

        } catch (Exception e) {
            logger.error("Ошибка при вызове OpenAI API", e);
            throw new IOException("Ошибка OpenAI API: " + e.getMessage(), e);
        }
    }

    private String buildRequestJson(String text, String voice, float speed) {
        // Очищаем текст от специальных символов
        String cleanText = text.replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();

        return String.format(
                "{\"model\":\"tts-1\",\"input\":\"%s\",\"voice\":\"%s\",\"speed\":%.1f,\"response_format\":\"mp3\"}",
                cleanText,
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
        Path tempFile = null;
        Throwable lastError = null; // Изменено на Throwable

        try {
            // Создаем временный файл
            tempFile = Files.createTempFile("openai_tts_", ".mp3");
            Files.write(tempFile, audioData);
            logger.debug("Создан временный файл: {} ({} байт)",
                    tempFile.getFileName(), audioData.length);

            // Метод 1: JLayer (если есть в classpath)
            try {
                playWithJLayer(tempFile);
                return;
            } catch (NoClassDefFoundError | Exception e) {
                lastError = e;
                logger.debug("JLayer недоступен: {}", e.getMessage());
            }

            // Метод 2: Системный плеер
            try {
                playWithSystemPlayer(tempFile);
                return;
            } catch (Exception e) {
                lastError = e;
                logger.debug("Системный плеер не сработал: {}", e.getMessage());
            }

            // Метод 3: Java Audio API (только если установлен MP3 SPI)
            try {
                playWithJavaAudio(tempFile);
                return;
            } catch (Exception e) {
                lastError = e;
                logger.debug("Java Audio API не сработал: {}", e.getMessage());
            }

            // Все методы не сработали
            String errorMessage = "Не удалось воспроизвести аудио. " +
                    "Установите mpg123 или добавьте JLayer библиотеку. " +
                    "Текущая ОС: " + System.getProperty("os.name");

            throw new IOException(errorMessage, lastError);

        } finally {
            // Отложенное удаление файла
            if (tempFile != null) {
                scheduleFileDeletion(tempFile);
            }
        }
    }

    // Метод с JLayer (основной)
    private void playWithJLayer(Path audioFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(audioFile.toFile())) {
            Player player = new Player(fis);

            // Воспроизводим в отдельном потоке
            Thread playbackThread = new Thread(() -> {
                try {
                    player.play();
                } catch (Exception e) {
                    logger.error("Ошибка при воспроизведении через JLayer", e);
                }
            });
            playbackThread.setDaemon(true);
            playbackThread.start();

            // Ждем начала воспроизведения
            Thread.sleep(500);

            // Можно дождаться завершения или вернуться сразу
            // player.close(); // Для остановки

        } catch (NoClassDefFoundError e) {
            throw new NoClassDefFoundError("JLayer библиотека не найдена. " +
                    "Добавьте jlayer-1.0.1.jar в classpath.");
        }
    }

    // Метод с системным плеером (резервный)
    private void playWithSystemPlayer(Path audioFile) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        if (os.contains("linux")) {
            // Пробуем разные плееры для Linux
            if (isCommandAvailable("xdg-open")) {
                pb = new ProcessBuilder("xdg-open", audioFile.toString());
            } else if (isCommandAvailable("mpg123")) {
                pb = new ProcessBuilder("mpg123", "-q", audioFile.toString());
            } else if (isCommandAvailable("mpg321")) {
                pb = new ProcessBuilder("mpg321", "-q", audioFile.toString());
            } else if (isCommandAvailable("ffplay")) {
                pb = new ProcessBuilder("ffplay", "-nodisp", "-autoexit", audioFile.toString());
            } else {
                throw new IOException("Аудиоплеер не найден на Linux");
            }
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("afplay", audioFile.toString());
        } else if (os.contains("win")) {
            String absolutePath = audioFile.toAbsolutePath().toString();
            // Пробуем разные варианты для Windows
            if (isCommandAvailable("wmplayer")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "/MIN", "wmplayer", absolutePath);
            } else {
                // Просто открываем файл
                pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", absolutePath);
            }
        } else {
            pb = new ProcessBuilder("xdg-open", audioFile.toString());
        }

        Process process = pb.start();
        boolean completed = process.waitFor(10, TimeUnit.SECONDS);
        if (!completed) {
            process.destroy();
            logger.debug("Воспроизведение прервано по таймауту");
        }
    }

    // Метод с Java Audio API (только для WAV, резервный)
    private void playWithJavaAudio(Path audioFile) throws Exception {
        // Сначала пробуем как есть (если установлен MP3 SPI)
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile.toFile());
            AudioFormat format = audioStream.getFormat();

            DataLine.Info info = new DataLine.Info(Clip.class, format);
            if (AudioSystem.isLineSupported(info)) {
                Clip audioClip = (Clip) AudioSystem.getLine(info);
                audioClip.open(audioStream);
                audioClip.start();

                // Ждем завершения
                while (audioClip.isRunning()) {
                    Thread.sleep(100);
                }

                audioClip.close();
                audioStream.close();
                return;
            }
        } catch (UnsupportedAudioFileException e) {
            logger.debug("MP3 не поддерживается Java Audio API");
        }

        // Если MP3 не поддерживается, конвертируем
        throw new UnsupportedAudioFileException("MP3 формат не поддерживается. " +
                "Установите MP3 SPI или используйте JLayer.");
    }

    // Вспомогательный метод для проверки доступности команды
    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Отложенное удаление файла
    private void scheduleFileDeletion(Path file) {
        new Thread(() -> {
            try {
                // Ждем 60 секунд на случай длинного воспроизведения
                Thread.sleep(60000);
                Files.deleteIfExists(file);
            } catch (Exception e) {
                logger.warn("Не удалось удалить временный файл {}: {}",
                        file.getFileName(), e.getMessage());
            }
        }).start();
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

    @Override
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