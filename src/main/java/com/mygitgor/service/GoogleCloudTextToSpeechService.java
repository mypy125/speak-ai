package com.mygitgor.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javazoom.jl.player.Player;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class GoogleCloudTextToSpeechService implements ITTSService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudTextToSpeechService.class);

    public enum GoogleVoice {
        EN_US_WAVENET_A("en-US-Wavenet-A", "en-US", "Male", "WaveNet",
                "Английский (US) - Мужской WaveNet"),
        EN_US_WAVENET_B("en-US-Wavenet-B", "en-US", "Female", "WaveNet",
                "Английский (US) - Женский WaveNet (рекомендуется)"),
        EN_US_WAVENET_C("en-US-Wavenet-C", "en-US", "Female", "WaveNet",
                "Английский (US) - Женский WaveNet 2"),
        EN_US_WAVENET_D("en-US-Wavenet-D", "en-US", "Male", "WaveNet",
                "Английский (US) - Мужской WaveNet 2"),
        EN_US_WAVENET_E("en-US-Wavenet-E", "en-US", "Female", "WaveNet",
                "Английский (US) - Женский WaveNet 3"),
        EN_US_WAVENET_F("en-US-Wavenet-F", "en-US", "Female", "WaveNet",
                "Английский (US) - Женский WaveNet 4"),

        RU_RU_WAVENET_A("ru-RU-Wavenet-A", "ru-RU", "Female", "WaveNet",
                "Русский - Женский WaveNet"),
        RU_RU_WAVENET_B("ru-RU-Wavenet-B", "ru-RU", "Male", "WaveNet",
                "Русский - Мужской WaveNet"),
        RU_RU_WAVENET_C("ru-RU-Wavenet-C", "ru-RU", "Female", "WaveNet",
                "Русский - Женский WaveNet 2"),
        RU_RU_WAVENET_D("ru-RU-Wavenet-D", "ru-RU", "Male", "WaveNet",
                "Русский - Мужской WaveNet 2"),

        EN_US_STANDARD_B("en-US-Standard-B", "en-US", "Male", "Standard",
                "Английский (US) - Мужской стандартный"),
        EN_US_STANDARD_C("en-US-Standard-C", "en-US", "Female", "Standard",
                "Английский (US) - Женский стандартный"),
        RU_RU_STANDARD_A("ru-RU-Standard-A", "ru-RU", "Female", "Standard",
                "Русский - Женский стандартный"),
        RU_RU_STANDARD_B("ru-RU-Standard-B", "ru-RU", "Male", "Standard",
                "Русский - Мужской стандартный");

        private final String voiceName;
        private final String languageCode;
        private final String gender;
        private final String modelType;
        private final String description;

        GoogleVoice(String voiceName, String languageCode, String gender,
                    String modelType, String description) {
            this.voiceName = voiceName;
            this.languageCode = languageCode;
            this.gender = gender;
            this.modelType = modelType;
            this.description = description;
        }

        public String getVoiceName() { return voiceName; }
        public String getLanguageCode() { return languageCode; }
        public String getGender() { return gender; }
        public String getModelType() { return modelType; }
        public String getDescription() { return description; }
    }

    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int SAFE_TEXT_LENGTH = 4800;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String TTS_API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize";
    private static final int PLAYBACK_START_DELAY_MS = 200;
    private static final int TEMP_FILE_DELETE_DELAY_MS = 30000;
    private static final int SPEECH_TIMEOUT_SECONDS = 30;

    private GoogleVoice currentVoice = GoogleVoice.EN_US_WAVENET_B;
    private float currentSpeed = 1.0f;
    private float currentPitch = 0.0f;
    private float currentVolumeGainDb = 0.0f;
    private String currentLanguage = "en-US";

    private enum AuthMethod {
        API_KEY,
        SERVICE_ACCOUNT,
        ACCESS_TOKEN,
        NONE
    }

    private AuthMethod authMethod = AuthMethod.NONE;
    private String apiKey;
    private GoogleCredentials credentials;
    private String accessToken;

    private final ExecutorService ttsExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final ThreadPoolManager threadPoolManager;

    private volatile boolean closed = false;
    private volatile boolean serviceAvailable = false;

    private final AtomicBoolean isStopping = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> currentSpeechFuture = new AtomicReference<>(null);

    private final AtomicReference<Player> currentPlayer = new AtomicReference<>(null);
    private final AtomicReference<Thread> playbackThread = new AtomicReference<>(null);
    private final AtomicReference<Process> playbackProcess = new AtomicReference<>(null);
    private final Object playerLock = new Object();

    public GoogleCloudTextToSpeechService(String apiKeyOrCredentials) {
        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.ttsExecutor = threadPoolManager.getTtsExecutor();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        logger.info("Инициализация Google Cloud TTS Service...");

        if (apiKeyOrCredentials == null || apiKeyOrCredentials.trim().isEmpty()) {
            throw new IllegalArgumentException("API ключ или путь к файлу учетных данных обязателен");
        }

        initializeAuthentication(apiKeyOrCredentials);
        checkAvailabilityAsync();

        logger.info("Google Cloud TTS Service инициализирован с методом аутентификации: {}", authMethod);
    }

    public GoogleCloudTextToSpeechService() {
        this(detectCredentialsFromEnvironment());
    }

    private static String detectCredentialsFromEnvironment() {
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
            File file = new File(credentialsPath);
            if (file.exists()) {
                logger.info("Обнаружен файл учетных данных: {}", credentialsPath);
                return credentialsPath;
            }
        }

        File localFile = new File("google-credentials.json");
        if (localFile.exists()) {
            logger.info("Обнаружен локальный файл учетных данных: google-credentials.json");
            return "google-credentials.json";
        }

        try {
            Properties props = new Properties();
            props.load(GoogleCloudTextToSpeechService.class.getResourceAsStream("/application.properties"));
            String apiKey = props.getProperty("google.cloud.api.key", "").trim();
            if (!apiKey.isEmpty() && !apiKey.equals("your-google-cloud-api-key-here")) {
                logger.info("Обнаружен API ключ в application.properties");
                return apiKey;
            }
        } catch (Exception e) {
            logger.debug("Не удалось прочитать application.properties: {}", e.getMessage());
        }

        try {
            Process process = new ProcessBuilder("gcloud", "auth", "print-access-token").start();
            if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
                logger.info("Обнаружен доступ через gcloud CLI");
                return "gcloud";
            }
        } catch (Exception e) {
            logger.debug("gcloud CLI недоступен: {}", e.getMessage());
        }

        return "";
    }

    private void initializeAuthentication(String apiKeyOrCredentials) {
        if (apiKeyOrCredentials.equals("gcloud")) {
            this.authMethod = AuthMethod.ACCESS_TOKEN;
            logger.info("Используется аутентификация через gcloud CLI");

        } else if (apiKeyOrCredentials.endsWith(".json")) {
            try {
                File credentialsFile = new File(apiKeyOrCredentials);
                if (!credentialsFile.exists()) {
                    InputStream is = getClass().getResourceAsStream("/" + apiKeyOrCredentials);
                    if (is != null) {
                        this.credentials = GoogleCredentials.fromStream(is)
                                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                        this.authMethod = AuthMethod.SERVICE_ACCOUNT;
                        logger.info("Загружен файл учетных данных из classpath: {}", apiKeyOrCredentials);
                    } else {
                        throw new FileNotFoundException("Файл учетных данных не найден: " + apiKeyOrCredentials);
                    }
                } else {
                    this.credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsFile))
                            .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                    this.authMethod = AuthMethod.SERVICE_ACCOUNT;
                    logger.info("Загружен файл учетных данных: {}", apiKeyOrCredentials);
                }
            } catch (Exception e) {
                logger.error("Ошибка загрузки файла учетных данных: {}", e.getMessage());
                throw new RuntimeException("Не удалось загрузить учетные данные Google Cloud", e);
            }

        } else if (apiKeyOrCredentials.startsWith("CREDENTIALS_FILE")) {
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
                initializeAuthentication(credentialsPath);
            } else {
                throw new IllegalArgumentException("Переменная окружения GOOGLE_APPLICATION_CREDENTIALS не установлена");
            }

        } else {
            this.apiKey = apiKeyOrCredentials;
            this.authMethod = AuthMethod.API_KEY;
            logger.info("Используется API ключ Google Cloud");
        }
    }

    private String getAccessToken() throws IOException {
        switch (authMethod) {
            case SERVICE_ACCOUNT:
                if (credentials != null) {
                    credentials.refreshIfExpired();
                    return credentials.getAccessToken().getTokenValue();
                }
                break;
            case ACCESS_TOKEN:
                try {
                    Process process = new ProcessBuilder("gcloud", "auth", "print-access-token").start();
                    StringBuilder output = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line.trim());
                        }
                    }
                    if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                        return output.toString();
                    }
                } catch (Exception e) {
                    logger.error("Ошибка получения токена через gcloud: {}", e.getMessage());
                }
                break;
            case API_KEY:
                return null;
            default:
                throw new IOException("Метод аутентификации не настроен");
        }
        return null;
    }

    private void checkAvailabilityAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                logger.debug("Проверка доступности Google Cloud TTS API...");
                byte[] testAudio = callGoogleTTSAPI("test", currentVoice.getVoiceName(),
                        currentSpeed, currentPitch, currentVolumeGainDb);

                serviceAvailable = testAudio.length > 100;

                if (serviceAvailable) {
                    logger.info("✅ Google Cloud TTS API доступен (получено {} байт)", testAudio.length);
                    logger.info("✅ Метод аутентификации: {}", authMethod);
                    if (authMethod == AuthMethod.SERVICE_ACCOUNT) {
                        logger.info("✅ Service Account: {}",
                                ((ServiceAccountCredentials) credentials).getClientEmail());
                    }
                } else {
                    serviceAvailable = false;
                    logger.warn("⚠️ Получен слишком маленький ответ от Google Cloud API: {} байт", testAudio.length);
                }
            } catch (Exception e) {
                serviceAvailable = false;
                logger.error("❌ Google Cloud TTS недоступен: {}", e.getMessage());
                analyzeGoogleError(e);
            }
        }, ttsExecutor);
    }

    private void analyzeGoogleError(Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage.contains("PERMISSION_DENIED")) {
            logger.error("""
                🔴 ОШИБКА ДОСТУПА (Permission Denied):
                
                Убедитесь, что:
                1. Service Account имеет роль 'Cloud Text-to-Speech User'
                2. Text-to-Speech API включен в проекте
                3. Проект активен и не заблокирован
                """);
        } else if (errorMessage.contains("UNAUTHENTICATED") || errorMessage.contains("401")) {
            logger.error("""
                🔐 ОШИБКА АУТЕНТИФИКАЦИИ:
                
                Возможные причины:
                1. Неверный или просроченный API ключ
                2. Неверный файл учетных данных
                3. Service Account не имеет доступа к проекту
                """);
        } else if (errorMessage.contains("RESOURCE_EXHAUSTED") || errorMessage.contains("429")) {
            logger.error("""
                ⚠️ ПРЕВЫШЕНЫ КВОТЫ ИСПОЛЬЗОВАНИЯ:
                
                Решения:
                1. Проверьте квоты в Google Cloud Console
                2. Обновите лимиты использования
                3. Подождите до сброса квот (обычно каждый месяц)
                
                Бесплатный лимит: 1 млн символов в месяц
                """);
        } else if (errorMessage.contains("NOT_FOUND") || errorMessage.contains("404")) {
            logger.error("""
                🔍 API НЕ НАЙДЕН:
                
                Убедитесь, что:
                1. Text-to-Speech API включен в проекте
                2. Используется правильный URL API
                3. Проект существует и доступен
                """);
        }
    }

    private byte[] callGoogleTTSAPI(String text, String voiceName,
                                    float speed, float pitch, float volumeGainDb) throws IOException {
        if (text.length() > SAFE_TEXT_LENGTH) {
            logger.warn("Текст слишком длинный ({} символов), сокращаем до {} символов",
                    text.length(), SAFE_TEXT_LENGTH);
            text = text.substring(0, SAFE_TEXT_LENGTH) + "...";
        }

        String apiUrl = TTS_API_URL;
        if (authMethod == AuthMethod.API_KEY && apiKey != null) {
            apiUrl += "?key=" + apiKey;
        }

        logger.debug("Google TTS запрос: голос={}, скорость={}, текст={} символов, auth={}",
                voiceName, speed, text.length(), authMethod);

        JSONObject audioConfig = new JSONObject();
        audioConfig.put("audioEncoding", "MP3");
        audioConfig.put("speakingRate", speed);
        audioConfig.put("pitch", pitch);
        audioConfig.put("volumeGainDb", volumeGainDb);

        JSONObject voice = new JSONObject();
        voice.put("languageCode", voiceName.substring(0, 5));
        voice.put("name", voiceName);

        JSONObject input = new JSONObject();
        input.put("text", text);

        JSONObject request = new JSONObject();
        request.put("input", input);
        request.put("voice", voice);
        request.put("audioConfig", audioConfig);

        String requestJson = request.toString();

        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            if (authMethod == AuthMethod.SERVICE_ACCOUNT || authMethod == AuthMethod.ACCESS_TOKEN) {
                String token = getAccessToken();
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                } else {
                    throw new IOException("Не удалось получить токен доступа");
                }
            }

            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            logger.debug("HTTP Response Code: {}", responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorResponse = readErrorResponse(connection);
                logger.error("Google Cloud API Error {}: {}", responseCode, errorResponse);
                throw new IOException("Google Cloud API Error: " + responseCode + " - " + errorResponse);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            String audioContent = jsonResponse.getString("audioContent");
            byte[] audioData = java.util.Base64.getDecoder().decode(audioContent);
            logger.info("✅ Получено {} байт аудио от Google Cloud TTS", audioData.length);

            return audioData;

        } catch (IOException e) {
            logger.error("Сетевая ошибка при вызове Google Cloud API", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            return "No error response body";
        }
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            return errorResponse.toString();
        } catch (Exception e) {
            return "Failed to read error: " + e.getMessage();
        }
    }

    public float getCurrentVolumeGainDb() { return currentVolumeGainDb; }

    public void setVolumeGainDb(float volumeGainDb) {
        if (volumeGainDb < -96.0f || volumeGainDb > 16.0f) {
            throw new IllegalArgumentException("Громкость должна быть от -96.0 до 16.0 дБ");
        }
        this.currentVolumeGainDb = volumeGainDb;
        logger.info("Установлена громкость: {} dB", volumeGainDb);
    }

    public void setLanguage(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Код языка не может быть пустым");
        }
        boolean found = false;
        for (GoogleVoice voice : GoogleVoice.values()) {
            if (voice.getLanguageCode().equalsIgnoreCase(languageCode)) {
                found = true;
                break;
            }
        }
        if (!found) {
            logger.warn("Для языка {} нет доступных голосов. Доступные языки: en-US, ru-RU", languageCode);
            return;
        }
        this.currentLanguage = languageCode;
        logger.info("Установлен язык озвучки: {}", languageCode);
        if (!currentVoice.getLanguageCode().equalsIgnoreCase(languageCode)) {
            for (GoogleVoice voice : GoogleVoice.values()) {
                if (voice.getLanguageCode().equalsIgnoreCase(languageCode)) {
                    setVoice(voice);
                    break;
                }
            }
        }
    }

    @Override
    public boolean isAvailable() { return serviceAvailable; }

    public Map<String, String> getAvailableVoices() {
        Map<String, String> voices = new HashMap<>();
        for (GoogleVoice voice : GoogleVoice.values()) {
            String description = String.format("%s (%s, %s, %s)",
                    voice.getDescription(),
                    voice.getLanguageCode(),
                    voice.getGender(),
                    voice.getModelType());
            voices.put(voice.name(), description);
        }
        return voices;
    }

    public GoogleVoice getCurrentVoice() { return currentVoice; }
    public float getCurrentSpeed() { return currentSpeed; }
    public float getCurrentPitch() { return currentPitch; }
    public String getCurrentLanguage() { return currentLanguage; }
    public AuthMethod getAuthMethod() { return authMethod; }
    public boolean isUsingServiceAccount() { return authMethod == AuthMethod.SERVICE_ACCOUNT; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        logger.info("Закрытие Google Cloud TTS Service...");
        stopSpeaking();
        logger.info("Google Cloud TTS Service закрыт");
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Google Cloud TTS Service закрыт"));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        currentSpeechFuture.set(future);

        ScheduledFuture<?> timeout = scheduledExecutor.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Таймаут озвучки"));
                stopSpeaking();
                logger.warn("Таймаут озвучки ({} сек)", SPEECH_TIMEOUT_SECONDS);
            }
        }, SPEECH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture.runAsync(() -> {
            try {
                speakInternal(text, future);
            } catch (Exception e) {
                logger.error("Ошибка при озвучке", e);
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            } finally {
                timeout.cancel(false);
            }
        }, ttsExecutor);

        return future;
    }

    private void speakInternal(String text, CompletableFuture<Void> future) {
        isStopping.set(false);

        try {
            String cleanText = cleanTextForSpeech(text);
            cleanText = trimTextToSafeLength(cleanText);

            logger.info("Google Cloud TTS: Озвучка текста ({} символов, {} байт), голос: {}, скорость: {}, язык: {}",
                    cleanText.length(), cleanText.getBytes(StandardCharsets.UTF_8).length,
                    currentVoice.getDescription(), currentSpeed, currentLanguage);

            if (isStopping.get() || Thread.currentThread().isInterrupted()) {
                handleCancellation(future, "Озвучка отменена перед генерацией");
                return;
            }

            byte[] audioData = callGoogleTTSAPI(cleanText, currentVoice.getVoiceName(),
                    currentSpeed, currentPitch, currentVolumeGainDb);

            if (isStopping.get() || Thread.currentThread().isInterrupted()) {
                handleCancellation(future, "Озвучка отменена после генерации");
                return;
            }

            playAudio(audioData);

            if (!isStopping.get() && future != null && !future.isDone()) {
                future.complete(null);
                logger.info("Google Cloud TTS: Озвучка завершена успешно");
            }

        } catch (CancellationException e) {
            logger.info("⏹️ Озвучка отменена");
            handleCancellation(future, "Озвучка отменена");
        } catch (Exception e) {
            logger.error("Ошибка при озвучке текста", e);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(e);
            }
        } finally {
            currentSpeechFuture.set(null);
        }
    }

    private String trimTextToSafeLength(String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length <= SAFE_TEXT_LENGTH) {
            return text;
        }

        logger.warn("⚠️ Текст слишком длинный ({} байт). Лимит: {} байт. Обрезаем...",
                textBytes.length, SAFE_TEXT_LENGTH);

        String trimmed = new String(textBytes, 0, SAFE_TEXT_LENGTH, StandardCharsets.UTF_8);

        int lastSentence = Math.max(
                Math.max(trimmed.lastIndexOf('.'), trimmed.lastIndexOf('?')),
                trimmed.lastIndexOf('!')
        );

        if (lastSentence > trimmed.length() / 2) {
            return trimmed.substring(0, lastSentence + 1) +
                    "\n\n[Текст сокращен из-за ограничения длины Google TTS]";
        }

        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > trimmed.length() / 2) {
            return trimmed.substring(0, lastSpace) +
                    "... [Текст сокращен из-за ограничения длины Google TTS]";
        }

        return trimmed + "... [Текст сокращен]";
    }

    private void handleCancellation(CompletableFuture<Void> future, String message) {
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new CancellationException(message));
        }
    }

    private void playAudio(byte[] audioData) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("google_tts_", ".mp3");
            Files.write(tempFile, audioData);

            stopCurrentPlayback();

            synchronized (playerLock) {
                FileInputStream fis = new FileInputStream(tempFile.toFile());
                Player player = new Player(fis);
                currentPlayer.set(player);

                Thread thread = new Thread(() -> {
                    try {
                        player.play();
                        logger.debug("Воспроизведение завершено");
                    } catch (javazoom.jl.decoder.JavaLayerException e) {
                        if (isStopping.get()) {
                            logger.debug("Воспроизведение прервано пользователем");
                        } else {
                            logger.error("Ошибка при воспроизведении", e);
                        }
                    } catch (Exception e) {
                        if (!isStopping.get()) {
                            logger.error("Ошибка при воспроизведении", e);
                        }
                    } finally {
                        synchronized (playerLock) {
                            if (Thread.currentThread() == playbackThread.get()) {
                                playbackThread.set(null);
                                currentPlayer.set(null);
                            }
                        }
                        try {
                            fis.close();
                        } catch (IOException e) {
                            logger.debug("Ошибка при закрытии файла: {}", e.getMessage());
                        }
                    }
                });

                thread.setDaemon(true);
                thread.setName("TTS-Playback");
                playbackThread.set(thread);
                thread.start();
            }

            CompletableFuture<Void> delayFuture = new CompletableFuture<>();
            ThreadPoolManager.runWithDelay(() -> delayFuture.complete(null), PLAYBACK_START_DELAY_MS);
            delayFuture.get(1, TimeUnit.SECONDS);

        } catch (NoClassDefFoundError e) {
            logger.warn("JLayer не найден, используем системный плеер");
            playAudioWithSystemPlayer(tempFile);
        } catch (Exception e) {
            logger.error("Ошибка при воспроизведении аудио", e);
        } finally {
            if (tempFile != null) {
                final Path fileToDelete = tempFile;
                scheduledExecutor.schedule(() -> {
                    try {
                        Files.deleteIfExists(fileToDelete);
                    } catch (IOException ex) {
                        logger.debug("Не удалось удалить временный файл: {}", ex.getMessage());
                    }
                }, TEMP_FILE_DELETE_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void playAudioWithSystemPlayer(Path tempFile) {
        if (tempFile == null) return;

        try {
            stopCurrentPlayback();

            synchronized (playerLock) {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;

                if (os.contains("linux")) {
                    pb = new ProcessBuilder("mpg123", "-q", tempFile.toString());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("afplay", tempFile.toString());
                } else if (os.contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", "start", tempFile.toString());
                } else {
                    pb = new ProcessBuilder("play", tempFile.toString());
                }

                Process process = pb.start();
                playbackProcess.set(process);
            }
        } catch (IOException e) {
            logger.error("Ошибка при запуске системного плеера", e);
        }
    }

    private void stopCurrentPlayback() {
        synchronized (playerLock) {
            Player player = currentPlayer.getAndSet(null);
            if (player != null) {
                try {
                    player.close();
                    logger.debug("JLayer Player остановлен");
                } catch (Exception e) {
                    logger.debug("Ошибка при остановке JLayer Player: {}", e.getMessage());
                }
            }

            Thread thread = playbackThread.getAndSet(null);
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
                try {
                    thread.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            Process process = playbackProcess.getAndSet(null);
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
                logger.debug("Системный плеер остановлен");
            }
        }
    }

    public void speak(String text) {
        if (closed) {
            throw new IllegalStateException("Google Cloud TTS Service закрыт");
        }
        try {
            CompletableFuture<Void> future = speakAsync(text);
            future.get(SPEECH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Таймаут озвучки");
            stopSpeaking();
            throw new RuntimeException("Таймаут озвучки", e);
        } catch (Exception e) {
            logger.error("Ошибка при озвучке", e);
            throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
        }
    }

    @Override
    public void stopSpeaking() {
        logger.info("⏹️ Остановка Google Cloud TTS...");

        isStopping.set(true);

        stopCurrentPlayback();

        CompletableFuture<Void> future = currentSpeechFuture.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        logger.info("✅ Google Cloud TTS остановлен");
    }

    public void setVoice(GoogleVoice voice) {
        this.currentVoice = voice;
        this.currentLanguage = voice.getLanguageCode();
        logger.info("Установлен голос Google TTS: {}, язык: {}", voice.getDescription(), voice.getLanguageCode());
    }

    public void setSpeed(float speed) {
        if (speed < 0.25f || speed > 4.0f) {
            throw new IllegalArgumentException("Скорость должна быть от 0.25 до 4.0");
        }
        this.currentSpeed = speed;
        logger.info("Установлена скорость озвучки: {}", speed);
    }

    public void setPitch(float pitch) {
        if (pitch < -20.0f || pitch > 20.0f) {
            throw new IllegalArgumentException("Тон должен быть от -20.0 до 20.0");
        }
        this.currentPitch = pitch;
        logger.info("Установлен тон голоса: {}", pitch);
    }

    public void setVolume(float volumeGainDb) {
        if (volumeGainDb < -96.0f || volumeGainDb > 16.0f) {
            throw new IllegalArgumentException("Громкость должна быть от -96.0 до 16.0 дБ");
        }
        this.currentVolumeGainDb = volumeGainDb;
        logger.info("Установлена громкость: {} dB", volumeGainDb);
    }

    private String cleanTextForSpeech(String text) {
        if (text == null) return "";

        String cleaned = text
                .replaceAll("(?s)📊 CONVERSATION STATS.*?(?=Keep the conversation going|$)", "")
                .replaceAll("(?s)📊 CONVERSATION GOALS.*?(?=⏱️|$)", "")
                .replaceAll("(?s)🗣️ CONVERSATION TOPIC:.*?(?=════|$)", "")

                .replaceAll("(?s)## 🎯 ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ:.*?(?=##|$)", "")
                .replaceAll("(?s)## 📅 НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ:.*?(?=##|$)", "")
                .replaceAll("(?s)## 🎯 Упражнение для практики:.*?(?=##|$)", "")
                .replaceAll("ℹ️ \\*.*?\\*", "")

                .replaceAll("═{3,}", "")
                .replaceAll("─{3,}", "")
                .replaceAll("={3,}", "")
                .replaceAll("-{3,}", "")

                .replaceAll("#+\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("`", "")
                .replaceAll("_", "")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("<[^>]*>", "")

                .replaceAll("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️🏆🎉👍💪📚🔧❤️✨🌟🔥💡🎯📅❌ℹ️]", "")

                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.length() > 4000) {
            int firstSentence = cleaned.indexOf('.');
            if (firstSentence > 200) {
                cleaned = cleaned.substring(0, firstSentence + 1) + " Continuing the conversation.";
            } else {
                cleaned = cleaned.substring(0, 2000) + "...";
            }
        }

        return cleaned;
    }
}