package com.mygitgor.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.utils.ThreadPoolManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class UniversalAIService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(UniversalAIService.class);

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int RATE_LIMIT_PER_MINUTE = 60;
    private static final int MAX_QUEUE_SIZE = 100;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 500;

    private final String apiKey;
    private final String provider;
    private final String model;
    private final String apiUrl;
    private final double temperature;
    private final int maxTokens;

    private final OkHttpClient client;

    private final AtomicBoolean isAvailable = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final AtomicReference<CompletableFuture<?>> currentRequest = new AtomicReference<>(null);

    private final Semaphore rateLimiter = new Semaphore(RATE_LIMIT_PER_MINUTE);
    private final ScheduledExecutorService rateLimitResetScheduler;

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService requestExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    public UniversalAIService(String apiKey, String provider, String model,
                              String apiUrl, double temperature, int maxTokens) {
        this.apiKey = apiKey;
        this.provider = provider;
        this.model = model;
        this.apiUrl = apiUrl;
        this.temperature = temperature > 0 ? temperature : DEFAULT_TEMPERATURE;
        this.maxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.requestExecutor = threadPoolManager.getBackgroundExecutor();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .addInterceptor(new RetryInterceptor(MAX_RETRIES))
                .addInterceptor(new RateLimitInterceptor())
                .build();

        this.rateLimitResetScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RateLimit-Reset");
            thread.setDaemon(true);
            return thread;
        });

        scheduleRateLimitReset();

        testConnectionAsync();

        logger.info("UniversalAIService инициализирован: провайдер={}, модель={}",
                getProviderName(), model);
    }

    public CompletableFuture<String> analyzeTextAsync(String text) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("UniversalAIService остановлен"));
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                analyzeText(text), requestExecutor);

        currentRequest.set(future);
        return future;
    }

    public CompletableFuture<SpeechAnalysis> analyzePronunciationAsync(String text, String audioPath) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("UniversalAIService остановлен"));
        }

        return CompletableFuture.supplyAsync(() ->
                analyzePronunciation(text, audioPath), requestExecutor);
    }

    public CompletableFuture<String> generateBotResponseAsync(String userMessage, SpeechAnalysis analysis) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("UniversalAIService остановлен"));
        }

        return CompletableFuture.supplyAsync(() ->
                generateBotResponse(userMessage, analysis), requestExecutor);
    }

    public CompletableFuture<String> generateExerciseAsync(String topic, String difficulty) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("UniversalAIService остановлен"));
        }

        return CompletableFuture.supplyAsync(() ->
                generateExercise(topic, difficulty), requestExecutor);
    }

    @Override
    public String analyzeText(String text) {
        if (!isAvailable.get() || isShutdown.get()) {
            return getFallbackAnalysis(text);
        }

        String prompt = createAnalysisPrompt(text);
        return executeWithRetry(prompt, "analyzeText");
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        SpeechAnalysis analysis = new SpeechAnalysis();
        analysis.setText(text);
        analysis.setAudioPath(audioPath);

        if (!isAvailable.get() || isShutdown.get()) {
            generateMockAnalysis(analysis);
            return analysis;
        }

        try {
            String prompt = createPronunciationPrompt(text);
            String response = executeWithRetry(prompt, "analyzePronunciation");
            parseAnalysisFromResponse(response, analysis);
        } catch (Exception e) {
            logger.error("Ошибка при анализе произношения", e);
            generateMockAnalysis(analysis);
        }

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        if (!isAvailable.get() || isShutdown.get()) {
            return getFallbackResponse(userMessage, analysis);
        }

        String prompt = createBotResponsePrompt(userMessage, analysis);
        return executeWithRetry(prompt, "generateBotResponse");
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        if (!isAvailable.get() || isShutdown.get()) {
            return getFallbackExercise(topic, difficulty);
        }

        String prompt = createExercisePrompt(topic, difficulty);
        return executeWithRetry(prompt, "generateExercise");
    }

    @Override
    public boolean isAvailable() {
        return isAvailable.get() && !isShutdown.get();
    }

    private String executeWithRetry(String prompt, String operationName) {
        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_RETRIES) {
            try {
                if (!rateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
                    logger.warn("Rate limit exceeded, waiting...");
                    Thread.sleep(1000);
                    continue;
                }

                String response = callAPI(prompt);
                requestCounter.incrementAndGet();
                return response;

            } catch (IOException e) {
                lastException = e;
                retries++;
                logger.warn("Попытка {} для {} не удалась: {}", retries, operationName, e.getMessage());

                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.error("Все попытки ({}) для {} исчерпаны", MAX_RETRIES, operationName, lastException);
        return getFallbackForOperation(operationName, prompt);
    }

    private String callAPI(String prompt) throws IOException {
        JsonObject request = createRequest(prompt);
        Request httpRequest = buildHttpRequest(request);

        logger.debug("Отправка запроса к {} API, модель: {}", getProviderName(), model);

        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                handleAPIError(response.code(), errorBody);
                throw new IOException(String.format("API error %d: %s",
                        response.code(), response.message()));
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody);
        }
    }

    private String createAnalysisPrompt(String text) {
        return String.format("""
            Ты - AI репетитор английского языка. Проанализируй следующее предложение ученика:
            
            "%s"
            
            В ответе укажи:
            1. Коррекцию грамматических ошибок (если есть)
            2. Предложения по улучшению формулировки
            3. Альтернативные способы выражения той же мысли
            4. Рекомендации по использованию словарного запаса
            
            Форматируй ответ с использованием Markdown.
            """, text);
    }

    private String createPronunciationPrompt(String text) {
        return String.format("""
            Ты - эксперт по фонетике английского языка. 
            Проанализируй произношение следующего текста:
            
            Текст: "%s"
            
            Дай оценку по 100-балльной шкале для:
            - Произношения
            - Беглости речи
            - Грамматики
            - Словарного запаса
            
            Укажи рекомендации для улучшения.
            """, text);
    }

    private String createBotResponsePrompt(String userMessage, SpeechAnalysis analysis) {
        return String.format("""
            Ты - дружелюбный AI репетитор английского языка. 
            Ответь на сообщение ученика и дай обратную связь:
            
            Сообщение ученика: "%s"
            
            %s
            
            Твой ответ должен быть:
            1. Естественным и дружелюбным
            2. Включать коррекцию ошибок
            3. Содержать похвалу за хорошие аспекты
            4. Предлагать полезные советы
            5. Поощрять дальнейшую практику
            
            Отвечай на русском языке, используй Markdown.
            """,
                userMessage,
                analysis != null ?
                        String.format("Результаты анализа:\n%s", analysis.getSummary()) :
                        "Анализ не проводился.");
    }

    private String createExercisePrompt(String topic, String difficulty) {
        return String.format("""
            Сгенерируй упражнение по английскому языку на тему "%s" для уровня "%s".
            
            Включи:
            1. Краткое объяснение темы
            2. 5 практических заданий
            3. Примеры выполнения
            4. Ключевые слова и выражения
            
            Используй Markdown форматирование.
            """, topic, difficulty);
    }

    private JsonObject createRequest(String prompt) {
        JsonObject request = new JsonObject();

        switch (provider) {
            case "anthropic":
                createAnthropicRequest(request, prompt);
                break;
            case "ollama":
                createOllamaRequest(request, prompt);
                break;
            default:
                createOpenAIRequest(request, prompt);
                break;
        }

        return request;
    }

    private void createAnthropicRequest(JsonObject request, String prompt) {
        request.addProperty("model", model);
        request.addProperty("max_tokens", maxTokens);
        request.addProperty("temperature", temperature);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        request.add("messages", messages);
    }

    private void createOllamaRequest(JsonObject request, String prompt) {
        request.addProperty("model", model);
        request.addProperty("prompt", prompt);
        request.addProperty("temperature", temperature);
        request.addProperty("max_tokens", maxTokens);
        request.addProperty("stream", false);
    }

    private void createOpenAIRequest(JsonObject request, String prompt) {
        request.addProperty("model", model);
        request.addProperty("temperature", temperature);
        request.addProperty("max_tokens", maxTokens);

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "Ты - AI репетитор английского языка.");
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        request.add("messages", messages);
    }

    private Request buildHttpRequest(JsonObject requestBody) {
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request.Builder builder = new Request.Builder()
                .url(apiUrl)
                .header("Content-Type", "application/json")
                .header("User-Agent", "SpeakAI/3.0");

        if (provider.equals("anthropic")) {
            builder.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
        } else if (!provider.equals("ollama")) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return builder.post(body).build();
    }

    private String parseResponse(String responseBody) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            return switch (provider) {
                case "anthropic" -> json.getAsJsonArray("content")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
                case "ollama" -> json.get("response").getAsString();
                default -> json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            };
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
            throw new IOException("Неверный формат ответа API", e);
        }
    }

    private void testConnectionAsync() {
        CompletableFuture.runAsync(() -> {
            boolean available = testConnection();
            isAvailable.set(available);

            if (available) {
                logger.info("✅ {} API подключен успешно. Модель: {}", getProviderName(), model);
            } else {
                logger.warn("⚠️ {} API не доступен. Используется демо-режим.", getProviderName());
            }
        }, requestExecutor);
    }

    private boolean testConnection() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            logger.warn("API ключ не настроен. Используется демо-режим.");
            return false;
        }

        try {
            String testPrompt = "Hello";
            JsonObject request = createRequest(testPrompt);
            request.addProperty("max_tokens", 10);

            try (Response response = client.newCall(buildHttpRequest(request)).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            logger.debug("Ошибка при тестировании подключения: {}", e.getMessage());
            return false;
        }
    }

    private void scheduleRateLimitReset() {
        rateLimitResetScheduler.scheduleAtFixedRate(() -> {
            rateLimiter.release(RATE_LIMIT_PER_MINUTE - rateLimiter.availablePermits());
        }, 1, 1, TimeUnit.MINUTES);
    }

    private String getFallbackForOperation(String operation, String prompt) {
        return switch (operation) {
            case "analyzeText" -> getFallbackAnalysis(prompt);
            case "generateBotResponse" -> getFallbackResponse("", null);
            case "generateExercise" -> getFallbackExercise("general", "beginner");
            default -> "Извините, сервис временно недоступен. Пожалуйста, попробуйте позже.";
        };
    }

    private String getFallbackAnalysis(String text) {
        return String.format("""
                ### 📝 Анализ текста (демо-режим)
                
                **Текст:** %s
                
                **Рекомендации:**
                1. ✅ Проверьте грамматические конструкции
                2. 📚 Используйте разнообразный словарный запас
                3. 🗣️ Практикуйте естественные формулировки
                4. ✍️ Работайте над структурой предложений
                
                *Совет: Для получения детального анализа настройте API ключ в конфигурации.*
                """, text);
    }

    private String getFallbackResponse(String userMessage, SpeechAnalysis analysis) {
        return String.format("""
                Привет! 👋
                
                Я получил ваше сообщение: "%s"
                
                Давайте продолжим практиковать английский! Вы можете:
                • 📝 Написать следующее сообщение
                • 🎤 Записать голосовое сообщение
                • 📊 Проанализировать свою речь
                
                *Примечание: Для полноценной работы с ИИ настройте API ключ.*
                """, userMessage);
    }

    private String getFallbackExercise(String topic, String difficulty) {
        return String.format("""
                ## 🎯 Упражнение: %s
                **Уровень:** %s
                
                Практикуйте тему "%s" с помощью:
                
                1. **Составления предложений** - напишите 5 предложений
                2. **Перевода текстов** - переведите небольшой текст
                3. **Диалогов** - составьте диалог на тему
                
                ### Пример:
                ```
                Тема: "Family"
                Предложение: "I have a big family with two brothers and one sister."
                ```
                
                *Совет: Для генерации персонализированных упражнений настройте API ключ.*
                """, topic, difficulty, topic);
    }

    private void parseAnalysisFromResponse(String response, SpeechAnalysis analysis) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains("Произношение:") && line.matches(".*\\d+.*")) {
                    analysis.setPronunciationScore(extractScore(line));
                } else if (line.contains("Беглость:") && line.matches(".*\\d+.*")) {
                    analysis.setFluencyScore(extractScore(line));
                } else if (line.contains("Грамматика:") && line.matches(".*\\d+.*")) {
                    analysis.setGrammarScore(extractScore(line));
                } else if (line.contains("Словарный запас:") && line.matches(".*\\d+.*")) {
                    analysis.setVocabularyScore(extractScore(line));
                } else if (line.contains("рекоменд") || line.contains("совет")) {
                    analysis.addRecommendation(line.trim());
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга анализа", e);
            generateMockAnalysis(analysis);
        }
    }

    private double extractScore(String line) {
        try {
            String scoreStr = line.replaceAll(".*?(\\d+(\\.\\d+)?).*", "$1");
            return Double.parseDouble(scoreStr);
        } catch (Exception e) {
            return 70 + Math.random() * 25;
        }
    }

    private void generateMockAnalysis(SpeechAnalysis analysis) {
        analysis.setPronunciationScore(75 + Math.random() * 20);
        analysis.setFluencyScore(70 + Math.random() * 25);
        analysis.setGrammarScore(80 + Math.random() * 15);
        analysis.setVocabularyScore(85 + Math.random() * 10);

        analysis.addRecommendation("🔊 Практикуйте произношение сложных звуков");
        analysis.addRecommendation("📚 Уделите внимание грамматическим конструкциям");
        analysis.addRecommendation("🎯 Расширяйте словарный запас");
        analysis.addRecommendation("⏱️ Работайте над беглостью речи");
    }

    private void handleAPIError(int code, String errorBody) {
        if (errorBody == null || errorBody.isEmpty()) {
            logger.error("API ошибка {} без тела ответа", code);
            return;
        }

        try {
            JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
            if (errorJson.has("error")) {
                JsonObject error = errorJson.getAsJsonObject("error");
                if (error.has("message")) {
                    logger.error("API Error: {}", error.get("message").getAsString());
                }
                if (error.has("type")) {
                    logger.error("Error Type: {}", error.get("type").getAsString());
                }
            }
        } catch (Exception e) {
            logger.error("Тело ошибки: {}", errorBody.substring(0, Math.min(200, errorBody.length())));
        }
    }

    private String getProviderName() {
        return switch (provider) {
            case "openai" -> "OpenAI";
            case "groq" -> "Groq";
            case "deepseek" -> "DeepSeek";
            case "anthropic" -> "Anthropic Claude";
            case "together" -> "Together AI";
            case "ollama" -> "Ollama";
            default -> "Unknown";
        };
    }

    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        public RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastException = null;

            for (int i = 0; i < maxRetries; i++) {
                try {
                    response = chain.proceed(request);
                    if (response.isSuccessful()) {
                        return response;
                    }
                    response.close();
                } catch (IOException e) {
                    lastException = e;
                }

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (i + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry", e);
                    }
                }
            }

            throw lastException != null ? lastException : new IOException("Max retries exceeded");
        }
    }

    private static class RateLimitInterceptor implements Interceptor {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicLong lastReset = new AtomicLong(System.currentTimeMillis());

        @Override
        public Response intercept(Chain chain) throws IOException {
            long now = System.currentTimeMillis();
            if (now - lastReset.get() > 60000) {
                requestCount.set(0);
                lastReset.set(now);
            }

            if (requestCount.incrementAndGet() > RATE_LIMIT_PER_MINUTE) {
                return chain.proceed(chain.request());
            }

            return chain.proceed(chain.request());
        }
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) {
            return;
        }

        logger.info("Завершение работы UniversalAIService...");

        // Отменяем текущий запрос
        CompletableFuture<?> request = currentRequest.getAndSet(null);
        if (request != null && !request.isDone()) {
            request.cancel(true);
        }

        if (rateLimitResetScheduler != null) {
            rateLimitResetScheduler.shutdown();
            try {
                if (!rateLimitResetScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rateLimitResetScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rateLimitResetScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }

        logger.info("UniversalAIService завершил работу");
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }
    public int getRequestCount() { return requestCounter.get(); }
}