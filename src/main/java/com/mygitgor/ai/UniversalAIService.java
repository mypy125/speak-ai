package com.mygitgor.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mygitgor.model.LearningContext;
import com.mygitgor.model.LearningMode;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.utils.ThreadPoolManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
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
    private static final int MAX_HISTORY_SIZE = 50;

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
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicReference<CompletableFuture<?>> currentRequest = new AtomicReference<>(null);

    private final Semaphore rateLimiter = new Semaphore(RATE_LIMIT_PER_MINUTE);
    private final ScheduledExecutorService rateLimitResetScheduler;

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService requestExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    private final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LearningMode, PromptTemplate> promptTemplates = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<LearningMode, ModeStats> modeStats = new ConcurrentHashMap<>();

    private static class ModeStats {
        final AtomicInteger requestCount = new AtomicInteger(0);
        final AtomicLong totalTokens = new AtomicLong(0);
        final AtomicLong totalTime = new AtomicLong(0);

        void recordRequest(int tokens, long timeMs) {
            requestCount.incrementAndGet();
            totalTokens.addAndGet(tokens);
            totalTime.addAndGet(timeMs);
        }

        double getAverageTime() {
            int count = requestCount.get();
            return count > 0 ? (double) totalTime.get() / count : 0;
        }
    }

    private static class PromptTemplate {
        final String systemPrompt;
        final String userPromptTemplate;
        final int maxTokens;
        final double temperature;

        PromptTemplate(String systemPrompt, String userPromptTemplate,
                       int maxTokens, double temperature) {
            this.systemPrompt = systemPrompt;
            this.userPromptTemplate = userPromptTemplate;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        String format(String... args) {
            return String.format(userPromptTemplate, (Object[]) args);
        }
    }

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
                .addInterceptor(new MetricsInterceptor())
                .build();

        this.rateLimitResetScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RateLimit-Reset");
            thread.setDaemon(true);
            return thread;
        });

        initializePromptTemplates();
        scheduleRateLimitReset();
        testConnectionAsync();

        logger.info("UniversalAIService инициализирован: провайдер={}, модель={}, режимов={}",
                getProviderName(), model, promptTemplates.size());
    }

    private void initializePromptTemplates() {
        promptTemplates.put(LearningMode.CONVERSATION, new PromptTemplate(
                "Ты - дружелюбный AI репетитор для разговорной практики английского языка. " +
                        "Поддерживай естественный диалог, задавай вопросы, мягко исправляй ошибки. " +
                        "Уровень ученика: %s",
                """
                Контекст разговора:
                - Уровень: %s
                - Предыдущие сообщения: %s
                - Тема: %s
                
                Сообщение ученика: %s
                
                Ответь естественно, поддерживая разговор. Задавай вопросы, чтобы стимулировать диалог.
                Корректируй ошибки мягко, если они есть. Используй Markdown форматирование.
                """,
                800,
                0.8
        ));

        promptTemplates.put(LearningMode.PRONUNCIATION, new PromptTemplate(
                "Ты - эксперт по фонетике английского языка. Помогай ученикам улучшать произношение. " +
                        "Объясняй артикуляцию звуков и давай практические советы. Уровень: %s",
                """
                Тренировка произношения:
                - Целевой звук: %s
                - Текущая оценка: %s
                - Слова для практики: %s
                
                Запись ученика: %s
                
                Проанализируй произношение. Дай оценку по 100-балльной шкале.
                Объясни, как правильно произносить звук, и предложи упражнения.
                """,
                600,
                0.5
        ));

        promptTemplates.put(LearningMode.GRAMMAR, new PromptTemplate(
                "Ты - AI репетитор по грамматике английского языка. Объясняй правила просто и понятно. " +
                        "Давай примеры и проверяй понимание. Уровень: %s",
                """
                Изучение грамматики:
                - Тема: %s
                - Сложность: %s
                - Предыдущие ошибки: %s
                
                Ответ ученика: %s
                
                Проверь правильность, объясни правило, дай примеры.
                Предложи похожее упражнение для закрепления.
                """,
                500,
                0.6
        ));

        promptTemplates.put(LearningMode.EXERCISE, new PromptTemplate(
                "Ты - AI репетитор, создающий упражнения по английскому языку. " +
                        "Генерируй разнообразные задания с учетом уровня ученика. Уровень: %s",
                """
                Генерация упражнения:
                - Тип: %s
                - Сложность: %s
                - Прогресс: %s%%
                
                Ответ ученика: %s
                
                Оцени правильность ответа. Если правильно - похвали и предложи следующее упражнение.
                Если ошибка - объясни и дай похожее задание.
                """,
                400,
                0.7
        ));

        promptTemplates.put(LearningMode.VOCABULARY, new PromptTemplate(
                "Ты - AI репетитор для расширения словарного запаса. " +
                        "Вводи новые слова в контексте и помогай их запоминать. Уровень: %s",
                """
                Работа со словарным запасом:
                - Тема: %s
                - Новые слова: %s
                - Контекст: %s
                
                Использование слов учеником: %s
                
                Проверь правильность использования слов в контексте.
                Дай примеры употребления и предложи составить предложения.
                """,
                500,
                0.7
        ));

        promptTemplates.put(LearningMode.WRITING, new PromptTemplate(
                "Ты - AI репетитор по письменному английскому. Помогай улучшать стиль, " +
                        "структуру и грамматику текстов. Уровень: %s",
                """
                Анализ письменной работы:
                - Тип текста: %s
                - Объем: %d слов
                - Цель: %s
                
                Текст ученика: %s
                
                Проанализируй текст: грамматика, стиль, структура, лексика.
                Дай конкретные рекомендации по улучшению.
                """,
                800,
                0.6
        ));

        promptTemplates.put(LearningMode.LISTENING, new PromptTemplate(
                "Ты - AI репетитор по аудированию. Помогай понимать речь на слух " +
                        "и развивать навыки восприятия. Уровень: %s",
                """
                Тренировка аудирования:
                - Тема: %s
                - Скорость речи: %s
                - Сложность: %s
                
                Распознанный текст ученика: %s
                
                Проверь понимание, укажи на возможные ошибки восприятия.
                Предложи упражнения для улучшения навыков аудирования.
                """,
                600,
                0.5
        ));
    }

    public CompletableFuture<String> processModeRequest(String userInput,
                                                        LearningMode mode,
                                                        LearningContext context) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("UniversalAIService остановлен"));
        }

        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            try {
                PromptTemplate template = promptTemplates.get(mode);
                if (template == null) {
                    logger.warn("Нет шаблона для режима {}, используется общий", mode);
                    return generateBotResponse(userInput, null);
                }

                String prompt = buildModePrompt(userInput, mode, context, template);
                String response = executeWithRetry(prompt, "mode_" + mode.name());

                ModeStats stats = modeStats.computeIfAbsent(mode, k -> new ModeStats());
                stats.recordRequest(estimateTokens(prompt + response),
                        System.currentTimeMillis() - startTime);

                return response;

            } catch (Exception e) {
                errorCount.incrementAndGet();
                logger.error("Ошибка в режиме {}: {}", mode, e.getMessage());
                return getFallbackModeResponse(mode, userInput);
            }
        }, requestExecutor);
    }

    public CompletableFuture<String> analyzeTextAsync(String text) {
        return processModeRequest(text, LearningMode.GRAMMAR, null);
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
        return processModeRequest(userMessage, LearningMode.CONVERSATION, null);
    }

    public CompletableFuture<String> generateExerciseAsync(String topic, String difficulty) {
        return processModeRequest(topic, LearningMode.EXERCISE,
                LearningContext.builder().currentLevel(parseLevel(difficulty)).build());
    }

    @Override
    public String analyzeText(String text) {
        return processModeRequest(text, LearningMode.GRAMMAR, null).join();
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
            String response = processModeRequest(text, LearningMode.PRONUNCIATION,
                    LearningContext.builder().build()).join();
            parseAnalysisFromResponse(response, analysis);
        } catch (Exception e) {
            logger.error("Ошибка при анализе произношения", e);
            generateMockAnalysis(analysis);
        }

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        return processModeRequest(userMessage, LearningMode.CONVERSATION, null).join();
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        return processModeRequest(topic, LearningMode.EXERCISE,
                LearningContext.builder().currentLevel(parseLevel(difficulty)).build()).join();
    }

    @Override
    public boolean isAvailable() {
        return isAvailable.get() && !isShutdown.get();
    }

    private String buildModePrompt(String userInput, LearningMode mode,
                                   LearningContext context, PromptTemplate template) {
        return switch (mode) {
            case CONVERSATION -> buildConversationPrompt(userInput, context, template);
            case PRONUNCIATION -> buildPronunciationPrompt(userInput, context, template);
            case GRAMMAR -> buildGrammarPrompt(userInput, context, template);
            case EXERCISE -> buildExercisePrompt(userInput, context, template);
            case VOCABULARY -> buildVocabularyPrompt(userInput, context, template);
            case WRITING -> buildWritingPrompt(userInput, context, template);
            case LISTENING -> buildListeningPrompt(userInput, context, template);
        };
    }

    private String buildConversationPrompt(String userInput, LearningContext context,
                                           PromptTemplate template) {
        String level = context != null ? formatLevel(context.getCurrentLevel()) : "intermediate";
        String history = context != null ? getConversationHistory(context) : "Новая беседа";
        String topic = context != null && context.getLastAnalysis() != null ?
                context.getLastAnalysis().getSummary() : "Общая тема";

        return template.systemPrompt + "\n\n" +
                template.format(level, history, topic, userInput);
    }

    private String buildPronunciationPrompt(String userInput, LearningContext context,
                                            PromptTemplate template) {
        String phoneme = extractPhoneme(userInput);
        String score = context != null && context.getLastAnalysis() != null ?
                String.format("%.1f", context.getLastAnalysis().getPronunciationScore()) : "0";
        String words = getPracticeWords(phoneme);

        return template.systemPrompt + "\n\n" +
                template.format(phoneme, score, words, userInput);
    }

    private String buildGrammarPrompt(String userInput, LearningContext context,
                                      PromptTemplate template) {
        String topic = detectGrammarTopic(userInput);
        String difficulty = context != null ?
                determineDifficulty(context.getCurrentLevel()) : "intermediate";
        String errors = getPreviousErrors(context);

        return template.systemPrompt + "\n\n" +
                template.format(topic, difficulty, errors, userInput);
    }

    private String buildExercisePrompt(String userInput, LearningContext context,
                                       PromptTemplate template) {
        String type = detectExerciseType(userInput);
        String difficulty = context != null ?
                determineDifficulty(context.getCurrentLevel()) : "intermediate";
        String progress = context != null ?
                String.format("%.1f", context.getCurrentLevel()) : "50";

        return template.systemPrompt + "\n\n" +
                template.format(type, difficulty, progress, userInput);
    }

    private String buildVocabularyPrompt(String userInput, LearningContext context,
                                         PromptTemplate template) {
        String topic = detectVocabularyTopic(userInput);
        String newWords = getNewVocabulary(topic);
        String context_ = getVocabularyContext(topic);

        return template.systemPrompt + "\n\n" +
                template.format(topic, newWords, context_, userInput);
    }

    private String buildWritingPrompt(String userInput, LearningContext context,
                                      PromptTemplate template) {
        String type = detectWritingType(userInput);
        int wordCount = estimateWordCount(userInput);
        String goal = determineWritingGoal(context);

        return template.systemPrompt + "\n\n" +
                template.format(type, String.valueOf(wordCount), goal, userInput);
    }

    private String buildListeningPrompt(String userInput, LearningContext context,
                                        PromptTemplate template) {
        String topic = detectListeningTopic(userInput);
        String speed = determineSpeed(context);
        String difficulty = context != null ?
                determineDifficulty(context.getCurrentLevel()) : "intermediate";

        return template.systemPrompt + "\n\n" +
                template.format(topic, speed, difficulty, userInput);
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
        errorCount.incrementAndGet();
        return getFallbackModeResponse(LearningMode.CONVERSATION, prompt);
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
            logger.error("Ошибка парсинга ответа: {}",
                    responseBody.substring(0, Math.min(200, responseBody.length())));
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

    private String getFallbackModeResponse(LearningMode mode, String input) {
        return switch (mode) {
            case CONVERSATION -> getFallbackConversationResponse(input);
            case PRONUNCIATION -> getFallbackPronunciationResponse(input);
            case GRAMMAR -> getFallbackGrammarResponse(input);
            case EXERCISE -> getFallbackExerciseResponse(input);
            case VOCABULARY -> getFallbackVocabularyResponse(input);
            case WRITING -> getFallbackWritingResponse(input);
            case LISTENING -> getFallbackListeningResponse(input);
        };
    }

    private String getFallbackConversationResponse(String input) {
        return String.format("""
            👋 Привет! (Демо-режим)
            
            Я получил ваше сообщение: "%s"
            
            В демо-режиме я могу предложить:
            • 📝 Практиковать базовые фразы
            • 🎤 Тренировать произношение
            • 📚 Изучать новую лексику
            
            Для полноценного общения настройте API ключ в конфигурации.
            """, input);
    }

    private String getFallbackPronunciationResponse(String input) {
        return """
            🔊 Тренировка произношения (демо-режим)
            
            Практикуйте звуки:
            • /θ/ - think, thought, through
            • /ð/ - this, that, there
            • /r/ - red, right, rain
            • /æ/ - cat, bat, hat
            
            Запишите себя и проанализируйте с помощью AudioAnalyzer!
            """;
    }

    private String getFallbackGrammarResponse(String input) {
        return String.format("""
            📚 Грамматика (демо-режим)
            
            Анализ предложения: "%s"
            
            Рекомендации:
            1. Проверьте согласование времен
            2. Обратите внимание на артикли
            3. Используйте более разнообразные конструкции
            
            Для детального анализа настройте API ключ.
            """, input);
    }

    private String getFallbackExerciseResponse(String input) {
        return """
            🎯 Упражнение (демо-режим)
            
            Задание: Составьте 5 предложений на тему "Daily Routine"
            
            Пример:
            • I wake up at 7 AM every day
            • After breakfast, I go to work
            • I usually have lunch at 1 PM
            
            Запишите свои предложения и проанализируйте их!
            """;
    }

    private String getFallbackVocabularyResponse(String input) {
        return """
            📖 Новые слова (демо-режим)
            
            Тема: "Work and Career"
            
            Новые слова:
            • employment - занятость
            • colleague - коллега
            • deadline - крайний срок
            • promotion - повышение
            
            Составьте предложения с этими словами!
            """;
    }

    private String getFallbackWritingResponse(String input) {
        return String.format("""
            ✍️ Анализ письма (демо-режим)
            
            Текст: "%s"
            
            Рекомендации:
            • Добавьте вводные слова
            • Используйте более сложные предложения
            • Проверьте пунктуацию
            
            Для детального анализа текста настройте API ключ.
            """, input);
    }

    private String getFallbackListeningResponse(String input) {
        return """
            🎧 Аудирование (демо-режим)
            
            Рекомендации для тренировки:
            1. Слушайте подкасты на английском
            2. Смотрите видео с субтитрами
            3. Практикуйте shadowing technique
            
            Записывайте услышанное и сравнивайте с оригиналом!
            """;
    }

    // ========================================
    // Вспомогательные методы
    // ========================================

    private String formatLevel(double level) {
        if (level < 30) return "beginner";
        if (level < 60) return "intermediate";
        if (level < 85) return "advanced";
        return "expert";
    }

    private double parseLevel(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "beginner" -> 20;
            case "intermediate" -> 50;
            case "advanced" -> 80;
            default -> 50;
        };
    }

    private String determineDifficulty(double level) {
        return formatLevel(level);
    }

    private String getConversationHistory(LearningContext context) {
        return "Последние сообщения: ...";
    }

    private String extractPhoneme(String input) {
        String[] phonemes = {"θ", "ð", "r", "æ", "ɪ", "iː", "ʃ", "w"};
        return phonemes[new Random().nextInt(phonemes.length)];
    }

    private String getPracticeWords(String phoneme) {
        Map<String, String> words = Map.of(
                "θ", "think, thought, through",
                "ð", "this, that, there",
                "r", "red, right, rain",
                "æ", "cat, bat, hat"
        );
        return words.getOrDefault(phoneme, "practice words");
    }

    private String detectGrammarTopic(String input) {
        if (input.contains("present") || input.contains("past")) return "tenses";
        if (input.contains("if")) return "conditionals";
        return "general grammar";
    }

    private String getPreviousErrors(LearningContext context) {
        return "Основные ошибки: ...";
    }

    private String detectExerciseType(String input) {
        String[] types = {"fill_gaps", "multiple_choice", "translation"};
        return types[new Random().nextInt(types.length)];
    }

    private String detectVocabularyTopic(String input) {
        String[] topics = {"work", "travel", "food", "family", "technology"};
        return topics[new Random().nextInt(topics.length)];
    }

    private String getNewVocabulary(String topic) {
        return "new words for " + topic;
    }

    private String getVocabularyContext(String topic) {
        return "context for " + topic;
    }

    private String detectWritingType(String input) {
        if (input.length() > 200) return "essay";
        return "short message";
    }

    private int estimateWordCount(String input) {
        return input.split("\\s+").length;
    }

    private String determineWritingGoal(LearningContext context) {
        return "improve writing skills";
    }

    private String detectListeningTopic(String input) {
        String[] topics = {"daily conversation", "news", "lecture"};
        return topics[new Random().nextInt(topics.length)];
    }

    private String determineSpeed(LearningContext context) {
        String[] speeds = {"slow", "normal", "fast"};
        return speeds[new Random().nextInt(speeds.length)];
    }

    private int estimateTokens(String text) {
        return text.length() / 4; // Приблизительная оценка
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
            logger.error("Тело ошибки: {}",
                    errorBody.substring(0, Math.min(200, errorBody.length())));
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

            requestCount.incrementAndGet();
            return chain.proceed(chain.request());
        }
    }

    private static class MetricsInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            long startTime = System.nanoTime();
            Response response = chain.proceed(chain.request());
            long duration = System.nanoTime() - startTime;

            logger.debug("Запрос к {} выполнен за {} мс",
                    chain.request().url(), TimeUnit.NANOSECONDS.toMillis(duration));

            return response;
        }
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) {
            return;
        }

        logger.info("Завершение работы UniversalAIService...");

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

    public String getModeStats() {
        StringBuilder stats = new StringBuilder("Статистика по режимам:\n");

        for (Map.Entry<LearningMode, ModeStats> entry : modeStats.entrySet()) {
            ModeStats ms = entry.getValue();
            stats.append(String.format("• %s: запросов=%d, среднее время=%.1f мс\n",
                    entry.getKey(), ms.requestCount.get(), ms.getAverageTime()));
        }

        stats.append(String.format("\nОбщая статистика:\n• Всего запросов: %d\n• Ошибок: %d\n• Доступен: %s",
                requestCounter.get(), errorCount.get(), isAvailable.get() ? "✅" : "❌"));

        return stats.toString();
    }

    public void clearPromptCache() {
        promptCache.clear();
        logger.info("Кэш промптов очищен");
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }
    public int getRequestCount() { return requestCounter.get(); }
}