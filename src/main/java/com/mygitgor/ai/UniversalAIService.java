package com.mygitgor.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mygitgor.model.EnhancedSpeechAnalysis;
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
import java.util.*;

public class UniversalAIService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(UniversalAIService.class);

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int RATE_LIMIT_PER_MINUTE = 60;
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int MAX_CACHE_SIZE = 200;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 500;
    private static final int MAX_HISTORY_SIZE = 50;
    private static final double TOKEN_ESTIMATION_FACTOR = 3.5;

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
    private final AtomicLong lastHealthCheck = new AtomicLong(System.currentTimeMillis());

    private final Semaphore rateLimiter = new Semaphore(RATE_LIMIT_PER_MINUTE);
    private final ScheduledExecutorService rateLimitResetScheduler;

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService requestExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    private final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTimestamp = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<LearningMode, PromptTemplate> promptTemplates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LearningMode, ModeStats> modeStats = new ConcurrentHashMap<>();

    private static class ModeStats {
        final AtomicInteger requestCount = new AtomicInteger(0);
        final AtomicLong totalTokens = new AtomicLong(0);
        final AtomicLong totalTime = new AtomicLong(0);
        final AtomicInteger totalErrors = new AtomicInteger(0);
        final ConcurrentHashMap<String, AtomicInteger> topicStats = new ConcurrentHashMap<>();

        void recordRequest(int tokens, long timeMs, boolean success, String topic) {
            requestCount.incrementAndGet();
            totalTokens.addAndGet(tokens);
            totalTime.addAndGet(timeMs);
            if (!success) {
                totalErrors.incrementAndGet();
            }
            if (topic != null) {
                topicStats.computeIfAbsent(topic, k -> new AtomicInteger()).incrementAndGet();
            }
        }

        double getAverageTime() {
            int count = requestCount.get();
            return count > 0 ? (double) totalTime.get() / count : 0;
        }

        double getSuccessRate() {
            int total = requestCount.get();
            return total > 0 ? (double) (total - totalErrors.get()) / total * 100 : 100;
        }

        double getAverageTokens() {
            int count = requestCount.get();
            return count > 0 ? (double) totalTokens.get() / count : 0;
        }

        Map<String, Integer> getTopicStats() {
            Map<String, Integer> result = new HashMap<>();
            topicStats.forEach((topic, count) -> result.put(topic, count.get()));
            return result;
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
        scheduleCacheCleanup();
        testConnectionAsync();

        logger.info("UniversalAIService инициализирован: провайдер={}, модель={}, режимов={}",
                getProviderName(), model, promptTemplates.size());
    }

    private void initializePromptTemplates() {
        promptTemplates.put(LearningMode.CONVERSATION, new PromptTemplate(
                "You are a friendly AI English tutor for conversational practice. " +
                        "Maintain natural dialogue, ask questions, gently correct mistakes. " +
                        "Student level: %s",
                """
                Conversation Context:
                - Level: %s
                - Previous messages: %s
                - Topic: %s
                
                Student's message: %s
                
                Respond naturally, support the conversation. Ask questions to encourage dialogue.
                Gently correct mistakes if any. Use Markdown formatting.
                """,
                800,
                0.8
        ));

        promptTemplates.put(LearningMode.PRONUNCIATION, new PromptTemplate(
                "You are a phonetics expert. Help students improve pronunciation. " +
                        "Explain articulation and give practical advice. Level: %s",
                """
                Pronunciation Practice:
                - Target sound: %s
                - Current score: %s
                - Practice words: %s
                
                Student's recording: %s
                
                Analyze pronunciation. Give a score out of 100.
                Explain how to pronounce correctly and suggest exercises.
                """,
                600,
                0.5
        ));

        promptTemplates.put(LearningMode.GRAMMAR, new PromptTemplate(
                "You are an AI grammar tutor. Explain rules clearly and simply. " +
                        "Give examples and check understanding. Level: %s",
                """
                Grammar Study:
                - Topic: %s
                - Difficulty: %s
                - Previous errors: %s
                
                Student's answer: %s
                
                Check correctness, explain the rule, give examples.
                Suggest a similar exercise for practice.
                """,
                500,
                0.6
        ));

        promptTemplates.put(LearningMode.EXERCISE, new PromptTemplate(
                "You are an AI tutor creating English exercises. " +
                        "Generate varied tasks based on student level. Level: %s",
                """
                Exercise Generation:
                - Type: %s
                - Difficulty: %s
                - Progress: %s%%
                
                Student's answer: %s
                
                Evaluate correctness. If correct - praise and suggest next exercise.
                If error - explain and give similar task.
                """,
                400,
                0.7
        ));

        promptTemplates.put(LearningMode.VOCABULARY, new PromptTemplate(
                "You are an AI vocabulary tutor. Introduce new words in context " +
                        "and help remember them. Level: %s",
                """
                Vocabulary Work:
                - Topic: %s
                - New words: %s
                - Context: %s
                
                Student's usage: %s
                
                Check correct word usage in context.
                Give examples and suggest making sentences.
                """,
                500,
                0.7
        ));

        promptTemplates.put(LearningMode.WRITING, new PromptTemplate(
                "You are an AI writing tutor. Help improve style, " +
                        "structure, and grammar of texts. Level: %s",
                """
                Writing Analysis:
                - Text type: %s
                - Length: %d words
                - Goal: %s
                
                Student's text: %s
                
                Analyze the text: grammar, style, structure, vocabulary.
                Give specific recommendations for improvement.
                """,
                800,
                0.6
        ));

        promptTemplates.put(LearningMode.LISTENING, new PromptTemplate(
                "You are an AI listening tutor. Help understand spoken English " +
                        "and develop listening skills. Level: %s",
                """
                Listening Practice:
                - Topic: %s
                - Speech speed: %s
                - Difficulty: %s
                
                Student's recognized text: %s
                
                Check comprehension, identify possible perception errors.
                Suggest exercises to improve listening skills.
                """,
                600,
                0.5
        ));
    }

    private void scheduleRateLimitReset() {
        rateLimitResetScheduler.scheduleAtFixedRate(() -> {
            try {
                int permits = RATE_LIMIT_PER_MINUTE - rateLimiter.availablePermits();
                if (permits > 0) {
                    rateLimiter.release(permits);
                    logger.debug("Rate limit reset: released {} permits", permits);
                }
            } catch (Exception e) {
                logger.error("Error resetting rate limit", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void scheduleCacheCleanup() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long oneHour = 3600000; // 1 час в миллисекундах

                cacheTimestamp.entrySet().removeIf(entry ->
                        now - entry.getValue() > oneHour);
                promptCache.keySet().retainAll(cacheTimestamp.keySet());

                logger.debug("Cache cleaned: {} entries remaining", promptCache.size());
            } catch (Exception e) {
                logger.error("Error cleaning cache", e);
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    public CompletableFuture<String> processModeRequest(String userInput,
                                                        LearningMode mode,
                                                        LearningContext context) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("UniversalAIService is shut down"));
        }

        long startTime = System.currentTimeMillis();
        String topic = context != null ? context.getMode().name() : "general";

        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = generateCacheKey(userInput, mode, context);
                String cachedResponse = getCachedResponse(cacheKey);
                if (cachedResponse != null) {
                    logger.debug("Cache hit for mode: {}", mode);
                    return cachedResponse;
                }

                PromptTemplate template = promptTemplates.get(mode);
                if (template == null) {
                    logger.warn("No template for mode {}, using general", mode);
                    return generateBotResponse(userInput, null);
                }

                String prompt = buildModePrompt(userInput, mode, context, template);
                String response = executeWithRetry(prompt, "mode_" + mode.name());

                cacheResponse(cacheKey, response);

                int tokens = estimateTokens(prompt + response);
                ModeStats stats = modeStats.computeIfAbsent(mode, k -> new ModeStats());
                stats.recordRequest(tokens, System.currentTimeMillis() - startTime, true, topic);

                return response;

            } catch (Exception e) {
                errorCount.incrementAndGet();
                ModeStats stats = modeStats.computeIfAbsent(mode, k -> new ModeStats());
                stats.recordRequest(0, System.currentTimeMillis() - startTime, false, topic);

                logger.error("Error in mode {}: {}", mode, e.getMessage());
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
                    new IllegalStateException("UniversalAIService is shut down"));
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
            logger.error("Error analyzing pronunciation", e);
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
        String history = getConversationHistory(context);
        String topic = getConversationTopic(context);

        return template.systemPrompt.formatted(level) + "\n\n" +
                template.format(level, history, topic, userInput);
    }

    private String buildPronunciationPrompt(String userInput, LearningContext context,
                                            PromptTemplate template) {
        String phoneme = extractPhoneme(userInput);
        String score = getPronunciationScore(context);
        String words = getPracticeWords(phoneme);

        return template.systemPrompt.formatted(getLevel(context)) + "\n\n" +
                template.format(phoneme, score, words, userInput);
    }

    private String buildGrammarPrompt(String userInput, LearningContext context,
                                      PromptTemplate template) {
        String topic = detectGrammarTopic(userInput);
        String difficulty = getDifficulty(context);
        String errors = getPreviousErrors(context);

        return template.systemPrompt.formatted(getLevel(context)) + "\n\n" +
                template.format(topic, difficulty, errors, userInput);
    }

    private String buildExercisePrompt(String userInput, LearningContext context,
                                       PromptTemplate template) {
        String type = detectExerciseType(userInput);
        String difficulty = getDifficulty(context);
        String progress = getProgress(context);

        return template.systemPrompt.formatted(getLevel(context)) + "\n\n" +
                template.format(type, difficulty, progress, userInput);
    }

    private String buildVocabularyPrompt(String userInput, LearningContext context,
                                         PromptTemplate template) {
        String topic = detectVocabularyTopic(userInput);
        String newWords = getNewVocabulary(topic);
        String context_ = getVocabularyContext(topic);

        return template.systemPrompt.formatted(getLevel(context)) + "\n\n" +
                template.format(topic, newWords, context_, userInput);
    }

    private String buildWritingPrompt(String userInput, LearningContext context,
                                      PromptTemplate template) {
        String type = detectWritingType(userInput);
        int wordCount = estimateWordCount(userInput);
        String goal = determineWritingGoal(context);

        return template.systemPrompt.formatted(getLevel(context)) + "\n\n" +
                template.format(type, String.valueOf(wordCount), goal, userInput);
    }

    private String buildListeningPrompt(String userInput, LearningContext context,
                                        PromptTemplate template) {
        String topic = detectListeningTopic(userInput);
        String speed = determineSpeed(context);
        String difficulty = getDifficulty(context);

        return template.systemPrompt.formatted(getLevel(context)) + "\n\n" +
                template.format(topic, speed, difficulty, userInput);
    }

    private String getLevel(LearningContext context) {
        return context != null ? formatLevel(context.getCurrentLevel()) : "intermediate";
    }

    private String getDifficulty(LearningContext context) {
        return context != null ? determineDifficulty(context.getCurrentLevel()) : "intermediate";
    }

    private String getProgress(LearningContext context) {
        return context != null ? String.format("%.1f", context.getCurrentLevel()) : "50";
    }

    private String getConversationHistory(LearningContext context) {
        if (context == null || context.getLastAnalysis() == null) {
            return "New conversation";
        }

        EnhancedSpeechAnalysis analysis = (EnhancedSpeechAnalysis) context.getLastAnalysis();
        return String.format(
                "Last analysis: pronunciation=%.1f, grammar=%.1f, vocabulary=%.1f",
                analysis.getPronunciationScore(),
                analysis.getGrammarScore(),
                analysis.getVocabularyScore()
        );
    }

    private String getConversationTopic(LearningContext context) {
        if (context == null || context.getLastAnalysis() == null) {
            return "General topic";
        }
        return context.getLastAnalysis().getSummary();
    }

    private String getPronunciationScore(LearningContext context) {
        if (context == null || context.getLastAnalysis() == null) {
            return "0";
        }
        return String.format("%.1f", context.getLastAnalysis().getPronunciationScore());
    }

    private String getPreviousErrors(LearningContext context) {
        if (context == null || context.getLastAnalysis() == null) {
            return "No previous errors";
        }

        EnhancedSpeechAnalysis analysis = (EnhancedSpeechAnalysis) context.getLastAnalysis();
        if (analysis.getDetectedErrors().isEmpty()) {
            return "No errors detected";
        }

        return String.join("; ", analysis.getDetectedErrors());
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
                logger.warn("Attempt {} for {} failed: {}", retries, operationName, e.getMessage());

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

        logger.error("All {} attempts for {} exhausted", MAX_RETRIES, operationName, lastException);
        errorCount.incrementAndGet();
        return getFallbackModeResponse(LearningMode.CONVERSATION, prompt);
    }

    private String callAPI(String prompt) throws IOException {
        JsonObject request = createRequest(prompt);
        Request httpRequest = buildHttpRequest(request);

        logger.debug("Sending request to {} API, model: {}", getProviderName(), model);

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
        systemMessage.addProperty("content", "You are an AI English tutor.");
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
            logger.error("Error parsing response: {}",
                    responseBody.substring(0, Math.min(200, responseBody.length())));
            throw new IOException("Invalid API response format", e);
        }
    }

    private void testConnectionAsync() {
        CompletableFuture.runAsync(() -> {
            boolean available = testConnection();
            isAvailable.set(available);

            if (available) {
                logger.info("✅ {} API connected successfully. Model: {}", getProviderName(), model);
            } else {
                logger.warn("⚠️ {} API not available. Using demo mode.", getProviderName());
            }
        }, requestExecutor);
    }

    private boolean testConnection() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            logger.warn("API key not configured. Using demo mode.");
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
            logger.debug("Connection test error: {}", e.getMessage());
            return false;
        }
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
            👋 Hello! (Demo mode)
            
            I received your message: "%s"
            
            In demo mode I can suggest:
            • 📝 Practice basic phrases
            • 🎤 Train pronunciation
            • 📚 Learn new vocabulary
            
            Configure your API key in settings for full functionality.
            """, input);
    }

    private String getFallbackPronunciationResponse(String input) {
        return """
            🔊 Pronunciation practice (demo mode)
            
            Practice these sounds:
            • /θ/ - think, thought, through
            • /ð/ - this, that, there
            • /r/ - red, right, rain
            • /æ/ - cat, bat, hat
            
            Record yourself and analyze with AudioAnalyzer!
            """;
    }

    private String getFallbackGrammarResponse(String input) {
        return String.format("""
            📚 Grammar (demo mode)
            
            Analyzing: "%s"
            
            Recommendations:
            1. Check verb tense agreement
            2. Pay attention to articles
            3. Use more varied structures
            
            Configure API key for detailed analysis.
            """, input);
    }

    private String getFallbackExerciseResponse(String input) {
        return """
            🎯 Exercise (demo mode)
            
            Task: Write 5 sentences about "Daily Routine"
            
            Example:
            • I wake up at 7 AM every day
            • After breakfast, I go to work
            • I usually have lunch at 1 PM
            
            Write your sentences and analyze them!
            """;
    }

    private String getFallbackVocabularyResponse(String input) {
        return """
            📖 New words (demo mode)
            
            Topic: "Work and Career"
            
            New words:
            • employment - занятость
            • colleague - коллега
            • deadline - крайний срок
            • promotion - повышение
            
            Make sentences with these words!
            """;
    }

    private String getFallbackWritingResponse(String input) {
        return String.format("""
            ✍️ Writing analysis (demo mode)
            
            Text: "%s"
            
            Recommendations:
            • Add transition words
            • Use more complex sentences
            • Check punctuation
            
            Configure API key for detailed analysis.
            """, input);
    }

    private String getFallbackListeningResponse(String input) {
        return """
            🎧 Listening (demo mode)
            
            Practice tips:
            1. Listen to English podcasts
            2. Watch videos with subtitles
            3. Practice shadowing technique
            
            Write what you hear and compare with original!
            """;
    }

    private String generateCacheKey(String userInput, LearningMode mode, LearningContext context) {
        String contextStr = context != null ? String.valueOf(context.hashCode()) : "null";
        return mode + ":" + contextStr + ":" + userInput.hashCode();
    }

    private String getCachedResponse(String key) {
        String response = promptCache.get(key);
        if (response != null) {
            cacheTimestamp.put(key, System.currentTimeMillis());
        }
        return response;
    }

    private void cacheResponse(String key, String response) {
        if (promptCache.size() >= MAX_CACHE_SIZE) {
            String oldestKey = cacheTimestamp.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldestKey != null) {
                promptCache.remove(oldestKey);
                cacheTimestamp.remove(oldestKey);
            }
        }
        promptCache.put(key, response);
        cacheTimestamp.put(key, System.currentTimeMillis());
    }

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
        String lower = input.toLowerCase();
        if (lower.contains("present") || lower.contains("past") || lower.contains("future")) {
            return "tenses";
        }
        if (lower.contains("if") || lower.contains("would") || lower.contains("could")) {
            return "conditionals";
        }
        if (lower.contains("the") || lower.contains("a ") || lower.contains("an ")) {
            return "articles";
        }
        return "general grammar";
    }

    private String detectExerciseType(String input) {
        String[] types = {"fill_gaps", "multiple_choice", "translation", "correction", "matching"};
        return types[new Random().nextInt(types.length)];
    }

    private String detectVocabularyTopic(String input) {
        String[] topics = {"work", "travel", "food", "family", "technology", "health", "education"};

        String lower = input.toLowerCase();
        for (String topic : topics) {
            if (lower.contains(topic)) {
                return topic;
            }
        }
        return topics[new Random().nextInt(topics.length)];
    }

    private String getNewVocabulary(String topic) {
        Map<String, String> vocabulary = Map.of(
                "work", "deadline, colleague, promotion, salary, meeting",
                "travel", "destination, accommodation, itinerary, passport, visa",
                "food", "ingredient, recipe, cuisine, appetizer, dessert"
        );
        return vocabulary.getOrDefault(topic, "new words for " + topic);
    }

    private String getVocabularyContext(String topic) {
        Map<String, String> contexts = Map.of(
                "work", "in the office, during meetings, in emails",
                "travel", "at the airport, in hotels, sightseeing",
                "food", "in restaurants, cooking at home, grocery shopping"
        );
        return contexts.getOrDefault(topic, "context for " + topic);
    }

    private String detectWritingType(String input) {
        if (input.length() > 500) return "essay";
        if (input.contains("?") && input.split("[.!?]+").length > 3) return "dialogue";
        return "short message";
    }

    private int estimateWordCount(String input) {
        return input.split("\\s+").length;
    }

    private String determineWritingGoal(LearningContext context) {
        if (context == null) return "improve writing skills";

        double level = context.getCurrentLevel();
        if (level < 30) return "write simple sentences correctly";
        if (level < 60) return "write coherent paragraphs";
        return "write complex texts with good style";
    }

    private String detectListeningTopic(String input) {
        String[] topics = {"daily conversation", "news", "lecture", "interview", "podcast"};
        return topics[new Random().nextInt(topics.length)];
    }

    private String determineSpeed(LearningContext context) {
        if (context == null) return "normal";

        double level = context.getCurrentLevel();
        if (level < 30) return "slow";
        if (level < 60) return "normal";
        return "fast";
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() / TOKEN_ESTIMATION_FACTOR);
    }

    private void parseAnalysisFromResponse(String response, SpeechAnalysis analysis) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains("Pronunciation:") && line.matches(".*\\d+.*")) {
                    analysis.setPronunciationScore(extractScore(line));
                } else if (line.contains("Fluency:") && line.matches(".*\\d+.*")) {
                    analysis.setFluencyScore(extractScore(line));
                } else if (line.contains("Grammar:") && line.matches(".*\\d+.*")) {
                    analysis.setGrammarScore(extractScore(line));
                } else if (line.contains("Vocabulary:") && line.matches(".*\\d+.*")) {
                    analysis.setVocabularyScore(extractScore(line));
                } else if (line.contains("recommend") || line.contains("suggestion")) {
                    analysis.addRecommendation(line.trim());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing analysis", e);
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

        analysis.addRecommendation("🔊 Practice difficult sounds");
        analysis.addRecommendation("📚 Focus on grammar structures");
        analysis.addRecommendation("🎯 Expand your vocabulary");
        analysis.addRecommendation("⏱️ Work on speech fluency");
    }

    private void handleAPIError(int code, String errorBody) {
        if (errorBody == null || errorBody.isEmpty()) {
            logger.error("API error {} with no body", code);
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
            logger.error("Error body: {}",
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

            logger.debug("Request to {} completed in {} ms",
                    chain.request().url(), TimeUnit.NANOSECONDS.toMillis(duration));

            return response;
        }
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) {
            return;
        }

        logger.info("Shutting down UniversalAIService...");

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

        promptCache.clear();
        cacheTimestamp.clear();

        logger.info("UniversalAIService shut down");
    }

    public String getModeStats() {
        StringBuilder stats = new StringBuilder("Statistics by mode:\n");

        for (Map.Entry<LearningMode, ModeStats> entry : modeStats.entrySet()) {
            ModeStats ms = entry.getValue();
            stats.append(String.format("• %s: requests=%d, avg time=%.1fms, success rate=%.1f%%, avg tokens=%.1f\n",
                    entry.getKey(),
                    ms.requestCount.get(),
                    ms.getAverageTime(),
                    ms.getSuccessRate(),
                    ms.getAverageTokens()));
        }

        stats.append(String.format("\nOverall statistics:\n• Total requests: %d\n• Errors: %d\n• Available: %s\n• Cache size: %d",
                requestCounter.get(),
                errorCount.get(),
                isAvailable.get() ? "✅" : "❌",
                promptCache.size()));

        return stats.toString();
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", isAvailable.get());
        status.put("shutdown", isShutdown.get());
        status.put("totalRequests", requestCounter.get());
        status.put("totalErrors", errorCount.get());
        status.put("avgResponseTime", requestCounter.get() > 0 ?
                (double) totalResponseTime.get() / requestCounter.get() : 0);
        status.put("cacheSize", promptCache.size());
        status.put("rateLimiterAvailable", rateLimiter.availablePermits());
        status.put("uptime", System.currentTimeMillis() - lastHealthCheck.get());

        Map<String, Object> modeStatus = new HashMap<>();
        for (Map.Entry<LearningMode, ModeStats> entry : modeStats.entrySet()) {
            ModeStats ms = entry.getValue();
            modeStatus.put(entry.getKey().toString(), Map.of(
                    "requests", ms.requestCount.get(),
                    "avgTime", ms.getAverageTime(),
                    "successRate", ms.getSuccessRate(),
                    "totalTokens", ms.totalTokens.get()
            ));
        }
        status.put("modes", modeStatus);

        return status;
    }

    public void clearPromptCache() {
        promptCache.clear();
        cacheTimestamp.clear();
        logger.info("Prompt cache cleared");
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }
    public int getRequestCount() { return requestCounter.get(); }
    public int getErrorCount() { return errorCount.get(); }
    public double getAverageResponseTime() {
        return requestCounter.get() > 0 ?
                (double) totalResponseTime.get() / requestCounter.get() : 0;
    }
}