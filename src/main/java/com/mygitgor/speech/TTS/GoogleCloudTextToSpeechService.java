package com.mygitgor.speech.TTS;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
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

public class GoogleCloudTextToSpeechService implements TextToSpeechService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudTextToSpeechService.class);

    public enum GoogleVoice {
        // WaveNet voices (высокое качество)
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

        // Русские голоса WaveNet
        RU_RU_WAVENET_A("ru-RU-Wavenet-A", "ru-RU", "Female", "WaveNet",
                "Русский - Женский WaveNet"),
        RU_RU_WAVENET_B("ru-RU-Wavenet-B", "ru-RU", "Male", "WaveNet",
                "Русский - Мужской WaveNet"),
        RU_RU_WAVENET_C("ru-RU-Wavenet-C", "ru-RU", "Female", "WaveNet",
                "Русский - Женский WaveNet 2"),
        RU_RU_WAVENET_D("ru-RU-Wavenet-D", "ru-RU", "Male", "WaveNet",
                "Русский - Мужской WaveNet 2"),

        // Standard voices (низкое качество, дешевле)
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

    // Константы
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String TTS_API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize";

    // Конфигурация
    private GoogleVoice currentVoice = GoogleVoice.EN_US_WAVENET_B;
    private float currentSpeed = 1.0f;
    private float currentPitch = 0.0f;
    private float currentVolumeGainDb = 0.0f;
    private String currentLanguage = "en-US";

    // Аутентификация
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

    private final ExecutorService executorService;
    private volatile boolean closed = false;
    private CompletableFuture<Void> currentSpeechFuture;
    private volatile boolean serviceAvailable = false;

    public GoogleCloudTextToSpeechService(String apiKeyOrCredentials) {
        logger.info("Инициализация Google Cloud TTS Service...");

        if (apiKeyOrCredentials == null || apiKeyOrCredentials.trim().isEmpty()) {
            throw new IllegalArgumentException("API ключ или путь к файлу учетных данных обязателен");
        }

        // Определяем метод аутентификации
        initializeAuthentication(apiKeyOrCredentials);

        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Google-TTS-Thread");
            thread.setDaemon(true);
            return thread;
        });

        // Проверяем доступность асинхронно
        checkAvailabilityAsync();

        logger.info("Google Cloud TTS Service инициализирован с методом аутентификации: {}", authMethod);
    }

    public GoogleCloudTextToSpeechService() {
        this(detectCredentialsFromEnvironment());
    }

    private static String detectCredentialsFromEnvironment() {
        // 1. Проверяем переменную окружения GOOGLE_APPLICATION_CREDENTIALS
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
            File file = new File(credentialsPath);
            if (file.exists()) {
                logger.info("Обнаружен файл учетных данных: {}", credentialsPath);
                return credentialsPath;
            }
        }

        // 2. Проверяем файл в текущей директории
        File localFile = new File("google-credentials.json");
        if (localFile.exists()) {
            logger.info("Обнаружен локальный файл учетных данных: google-credentials.json");
            return "google-credentials.json";
        }

        // 3. Проверяем application.properties
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

        // 4. Проверяем gcloud CLI
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
            // Аутентификация через gcloud CLI
            this.authMethod = AuthMethod.ACCESS_TOKEN;
            logger.info("Используется аутентификация через gcloud CLI");

        } else if (apiKeyOrCredentials.endsWith(".json")) {
            // Файл учетных данных Service Account
            try {
                File credentialsFile = new File(apiKeyOrCredentials);
                if (!credentialsFile.exists()) {
                    // Пробуем загрузить из classpath
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
            // Переменная окружения GOOGLE_APPLICATION_CREDENTIALS
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
                initializeAuthentication(credentialsPath);
            } else {
                throw new IllegalArgumentException("Переменная окружения GOOGLE_APPLICATION_CREDENTIALS не установлена");
            }

        } else {
            // Простой API ключ
            this.apiKey = apiKeyOrCredentials;
            this.authMethod = AuthMethod.API_KEY;
            logger.info("Используется API ключ Google Cloud");
        }
    }

    /**
     * Получение токена доступа
     */
    private String getAccessToken() throws IOException {
        switch (authMethod) {
            case SERVICE_ACCOUNT:
                if (credentials != null) {
                    credentials.refreshIfExpired();
                    return credentials.getAccessToken().getTokenValue();
                }
                break;

            case ACCESS_TOKEN:
                // Получаем токен через gcloud CLI
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
                return null; // API ключ не требует токена

            default:
                throw new IOException("Метод аутентификации не настроен");
        }

        return null;
    }

    private void checkAvailabilityAsync() {
        executorService.submit(() -> {
            try {
                logger.debug("Проверка доступности Google Cloud TTS API...");

                // Простой тестовый запрос
                byte[] testAudio = callGoogleTTSAPI("test", currentVoice.getVoiceName(),
                        currentSpeed, currentPitch, currentVolumeGainDb);

                if (testAudio.length > 100) {
                    serviceAvailable = true;
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

                // Детальный анализ ошибки
                analyzeGoogleError(e);
            }
        });
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
                
                Как исправить:
                1. Перейдите в Google Cloud Console: https://console.cloud.google.com/
                2. Выберите проект: gen-lang-client-0629044890
                3. Включите Text-to-Speech API в разделе 'APIs & Services'
                4. В разделе 'IAM & Admin' добавьте роль для service account
                """);

        } else if (errorMessage.contains("UNAUTHENTICATED") || errorMessage.contains("401")) {
            logger.error("""
                🔐 ОШИБКА АУТЕНТИФИКАЦИИ:
                
                Возможные причины:
                1. Неверный или просроченный API ключ
                2. Неверный файл учетных данных
                3. Service Account не имеет доступа к проекту
                
                Как исправить:
                1. Проверьте корректность файла google-credentials.json
                2. Убедитесь, что Service Account активен
                3. Попробуйте сгенерировать новый ключ
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

    /**
     * Вызов Google Cloud TTS API
     */
    private byte[] callGoogleTTSAPI(String text, String voiceName,
                                    float speed, float pitch, float volumeGainDb) throws IOException {
        // Ограничиваем текст
        if (text.length() > MAX_TEXT_LENGTH) {
            logger.warn("Текст слишком длинный ({} символов), сокращаем", text.length());
            text = text.substring(0, MAX_TEXT_LENGTH) + "...";
        }

        String apiUrl = TTS_API_URL;

        // Добавляем API ключ если используется
        if (authMethod == AuthMethod.API_KEY && apiKey != null) {
            apiUrl += "?key=" + apiKey;
        }

        logger.debug("Google TTS запрос: голос={}, скорость={}, текст={} символов, auth={}",
                voiceName, speed, text.length(), authMethod);

        // Строим JSON запрос
        JSONObject audioConfig = new JSONObject();
        audioConfig.put("audioEncoding", "MP3");
        audioConfig.put("speakingRate", speed);
        audioConfig.put("pitch", pitch);
        audioConfig.put("volumeGainDb", volumeGainDb);

        JSONObject voice = new JSONObject();
        voice.put("languageCode", voiceName.substring(0, 5)); // Например "en-US"
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

            // Добавляем авторизацию если нужно
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
                logger.error("Google Cloud API Error {}: {}", responseCode, errorResponse);

                // Парсим JSON ошибки
                try {
                    JSONObject errorJson = new JSONObject(errorResponse);
                    JSONObject error = errorJson.optJSONObject("error");
                    if (error != null) {
                        String message = error.optString("message", "Unknown error");
                        String status = error.optString("status", "");

                        if (status.equals("PERMISSION_DENIED")) {
                            throw new IOException("Доступ запрещен. Service Account не имеет прав: " + message);
                        } else if (status.equals("UNAUTHENTICATED")) {
                            throw new IOException("Ошибка аутентификации: " + message);
                        } else if (status.equals("RESOURCE_EXHAUSTED")) {
                            throw new IOException("Превышены квоты использования: " + message);
                        } else if (status.equals("NOT_FOUND")) {
                            throw new IOException("API не найден: " + message);
                        } else {
                            throw new IOException("Google Cloud API Error: " + status + " - " + message);
                        }
                    }
                } catch (Exception e) {
                    // Если не JSON, возвращаем как есть
                    throw new IOException("Google Cloud API Error: " + responseCode + " - " + errorResponse);
                }
            }

            // Читаем ответ
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Парсим JSON ответа и извлекаем аудио
            JSONObject jsonResponse = new JSONObject(response.toString());
            String audioContent = jsonResponse.getString("audioContent");

            // Декодируем base64 в байты
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

    /**
     * Получение текущей громкости в децибелах
     * @return текущая громкость в dB
     */
    public float getCurrentVolumeGainDb() {
        return currentVolumeGainDb;
    }

    public void setVolumeGainDb(float volumeGainDb) {
        if (volumeGainDb < -96.0f || volumeGainDb > 16.0f) {
            throw new IllegalArgumentException("Громкость должна быть от -96.0 до 16.0 дБ");
        }
        this.currentVolumeGainDb = volumeGainDb;
        logger.info("Установлена громкость: {} dB", volumeGainDb);
    }

    /**
     * Установка языка озвучки
     * @param languageCode код языка (например, "en-US", "ru-RU")
     */
    public void setLanguage(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Код языка не может быть пустым");
        }

        // Проверяем, есть ли голос для этого языка
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

        // Если текущий голос не соответствует выбранному языку,
        // меняем на первый доступный голос для этого языка
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
    public boolean isAvailable() {
        return serviceAvailable;
    }

    @Override
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

    // Геттеры для текущей конфигурации
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

        logger.info("Google Cloud TTS Service закрыт");
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        if (closed) {
            throw new IllegalStateException("Google Cloud TTS Service закрыт");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        currentSpeechFuture = future;

        executorService.submit(() -> {
            try {
                speakInternal(text, future);
            } catch (Exception e) {
                logger.error("Ошибка при озвучке", e);
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    private void speakInternal(String text, CompletableFuture<Void> future) {
        try {
            String cleanText = cleanTextForSpeech(text);
            logger.info("Google Cloud TTS: Озвучка текста ({} символов), голос: {}, скорость: {}, язык: {}",
                    cleanText.length(), currentVoice.getDescription(), currentSpeed, currentLanguage);

            // Генерация речи
            byte[] audioData = callGoogleTTSAPI(cleanText, currentVoice.getVoiceName(),
                    currentSpeed, currentPitch, currentVolumeGainDb);

            // Воспроизведение
            playAudio(audioData);

            if (future != null && !future.isDone()) {
                future.complete(null);
            }

            logger.info("Google Cloud TTS: Озвучка завершена успешно");

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
        Path tempFile = Files.createTempFile("google_tts_", ".mp3");
        Files.write(tempFile, audioData);

        try {
            // Используем JLayer для воспроизведения MP3
            javazoom.jl.player.Player player = new javazoom.jl.player.Player(
                    new FileInputStream(tempFile.toFile()));

            // Воспроизводим в отдельном потоке
            Thread playbackThread = new Thread(() -> {
                try {
                    player.play();
                } catch (Exception e) {
                    logger.error("Ошибка при воспроизведении", e);
                }
            });
            playbackThread.setDaemon(true);
            playbackThread.start();

            // Ждем начала воспроизведения
            Thread.sleep(500);

        } catch (NoClassDefFoundError e) {
            logger.warn("JLayer не найден, используем системный плеер");

            // Fallback на системный плеер
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
            process.waitFor(30, TimeUnit.SECONDS);

        } finally {
            // Удаляем временный файл
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public void speak(String text) {
        if (closed) {
            throw new IllegalStateException("Google Cloud TTS Service закрыт");
        }

        try {
            speakInternal(text, null);
        } catch (Exception e) {
            logger.error("Ошибка при озвучке", e);
            throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
        }
    }

    @Override
    public void stopSpeaking() {
        logger.info("Остановка Google Cloud TTS...");
        if (currentSpeechFuture != null && !currentSpeechFuture.isDone()) {
            currentSpeechFuture.cancel(true);
            currentSpeechFuture = null;
        }
    }

    // Методы настройки
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
}