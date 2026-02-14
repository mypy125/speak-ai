package com.mygitgor.ai;

import java.util.Properties;

import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AIServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(AIServiceFactory.class);

    private static final String DEFAULT_TEMPERATURE = "0.7";
    private static final String DEFAULT_MAX_TOKENS = "1500";
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int CACHE_SIZE = 10;
    private static final long CACHE_TTL_MINUTES = 5;

    private static final Map<String, AiService> serviceCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();

    private static final AtomicInteger factoryRequests = new AtomicInteger(0);
    private static final AtomicInteger cacheHits = new AtomicInteger(0);
    private static final AtomicInteger cacheMisses = new AtomicInteger(0);
    private static final AtomicLong totalCreationTime = new AtomicLong(0);

    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);

    private static final ThreadPoolManager threadPoolManager = ThreadPoolManager.getInstance();
    private static final ExecutorService backgroundExecutor = threadPoolManager.getBackgroundExecutor();

    private static final Map<String, ProviderConfig> providerConfigs = new ConcurrentHashMap<>();

    static {
        initializeProviderConfigs();
        scheduleCacheCleanup();
        isInitialized.set(true);
        logger.info("AIServiceFactory инициализирован");
    }

    private static class ProviderConfig {
        final String defaultUrl;
        final String defaultModel;
        final String apiKeyPattern;
        final boolean requiresBearerToken;
        final int maxTokens;
        final double defaultTemperature;

        ProviderConfig(String defaultUrl, String defaultModel, String apiKeyPattern,
                       boolean requiresBearerToken, int maxTokens, double defaultTemperature) {
            this.defaultUrl = defaultUrl;
            this.defaultModel = defaultModel;
            this.apiKeyPattern = apiKeyPattern;
            this.requiresBearerToken = requiresBearerToken;
            this.maxTokens = maxTokens;
            this.defaultTemperature = defaultTemperature;
        }
    }

    private static void initializeProviderConfigs() {
        providerConfigs.put("openai", new ProviderConfig(
                "https://api.openai.com/v1/chat/completions",
                "gpt-3.5-turbo",
                "^sk-.*",
                true,
                4096,
                0.7
        ));

        providerConfigs.put("groq", new ProviderConfig(
                "https://api.groq.com/openai/v1/chat/completions",
                "llama-3.1-8b-instant",
                "^gsk_.*",
                true,
                8192,
                0.7
        ));

        providerConfigs.put("deepseek", new ProviderConfig(
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat",
                "^sk-deepseek-.*",
                true,
                4096,
                0.7
        ));

        providerConfigs.put("anthropic", new ProviderConfig(
                "https://api.anthropic.com/v1/messages",
                "claude-3-haiku-20240307",
                "^sk-ant-.*",
                false,
                4096,
                0.7
        ));

        providerConfigs.put("together", new ProviderConfig(
                "https://api.together.xyz/v1/chat/completions",
                "mistralai/Mixtral-8x7B-Instruct-v0.1",
                ".*",
                true,
                4096,
                0.7
        ));

        providerConfigs.put("ollama", new ProviderConfig(
                "http://localhost:11434/api/chat",
                "llama3.2",
                "ollama",
                false,
                2048,
                0.7
        ));
    }

    public static AiService createService(Properties config) {
        if (isShutdown.get()) {
            logger.warn("AIServiceFactory остановлен, создание сервиса невозможно");
            return new MockAiService();
        }

        long startTime = System.currentTimeMillis();
        factoryRequests.incrementAndGet();

        try {
            String apiKey = validateAndGetApiKey(config);

            if (apiKey == null) {
                return createMockService();
            }

            String provider = determineProvider(config, apiKey);

            String cacheKey = buildCacheKey(provider, apiKey, config);
            AiService cachedService = getCachedService(cacheKey);
            if (cachedService != null) {
                cacheHits.incrementAndGet();
                return cachedService;
            }
            cacheMisses.incrementAndGet();

            AiService service = createNewService(config, apiKey, provider);

            cacheService(cacheKey, service);

            recordMetrics(startTime);
            return service;

        } catch (Exception e) {
            logger.error("Ошибка при создании AI сервиса", e);
            return createMockService();
        }
    }

    public static CompletableFuture<AiService> createServiceAsync(Properties config) {
        return CompletableFuture.supplyAsync(() -> createService(config), backgroundExecutor);
    }

    public static AiService createService(String provider, String apiKey) {
        Properties config = new Properties();
        config.setProperty("ai.provider", provider);
        config.setProperty("ai.api.key", apiKey);
        return createService(config);
    }

    public static AiService createService(String provider, String apiKey, String model, String apiUrl) {
        Properties config = new Properties();
        config.setProperty("ai.provider", provider);
        config.setProperty("ai.api.key", apiKey);
        config.setProperty("ai.model", model);
        config.setProperty("ai.custom.url", apiUrl);
        return createService(config);
    }

    private static String validateAndGetApiKey(Properties config) {
        if (config == null) {
            logger.warn("Конфигурация не предоставлена");
            return null;
        }

        String apiKey = config.getProperty("ai.api.key", "").trim();

        if (apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            logger.debug("API ключ не настроен, используется MockAiService");
            return null;
        }

        return apiKey;
    }

    private static String determineProvider(Properties config, String apiKey) {
        String provider = config.getProperty("ai.provider", "auto").toLowerCase();

        if ("auto".equals(provider)) {
            provider = detectProviderByKey(apiKey);
            logger.debug("Автоматически определен провайдер: {}", provider);
        }

        return provider;
    }

    private static String detectProviderByKey(String apiKey) {
        if (apiKey == null) return "groq";

        for (Map.Entry<String, ProviderConfig> entry : providerConfigs.entrySet()) {
            if (apiKey.matches(entry.getValue().apiKeyPattern)) {
                return entry.getKey();
            }
        }

        if (apiKey.startsWith("sk-")) {
            if (apiKey.startsWith("sk-deepseek-")) {
                return "deepseek";
            } else if (apiKey.startsWith("sk-ant-")) {
                return "anthropic";
            } else {
                return "openai";
            }
        } else if (apiKey.startsWith("gsk_")) {
            return "groq";
        } else if (apiKey.contains("AIza")) {
            return "google";
        } else if (apiKey.equals("ollama")) {
            return "ollama";
        }

        return "groq";
    }

    private static AiService createNewService(Properties config, String apiKey, String provider) {
        ProviderConfig providerConfig = providerConfigs.getOrDefault(provider,
                providerConfigs.get("groq"));

        String model = getModel(config, provider, providerConfig);
        String apiUrl = getApiUrl(config, provider, providerConfig);
        double temperature = getTemperature(config, providerConfig);
        int maxTokens = getMaxTokens(config, providerConfig);

        logger.info("Создание {} сервиса: модель={}, url={}, maxTokens={}",
                provider, model, apiUrl, maxTokens);

        return new UniversalAIService(
                apiKey,
                provider,
                model,
                apiUrl,
                temperature,
                maxTokens
        );
    }

    private static String getModel(Properties config, String provider, ProviderConfig providerConfig) {
        String model = config.getProperty("ai.model", "").trim();
        if (model.isEmpty()) {
            model = config.getProperty("ai.model." + provider, providerConfig.defaultModel);
        }
        return model.isEmpty() ? providerConfig.defaultModel : model;
    }

    private static String getApiUrl(Properties config, String provider, ProviderConfig providerConfig) {
        String customUrl = config.getProperty("ai.custom.url", "").trim();
        if (!customUrl.isEmpty()) {
            return customUrl;
        }
        return config.getProperty("ai.url." + provider, providerConfig.defaultUrl);
    }

    private static double getTemperature(Properties config, ProviderConfig providerConfig) {
        try {
            return Double.parseDouble(config.getProperty("ai.temperature",
                    String.valueOf(providerConfig.defaultTemperature)));
        } catch (NumberFormatException e) {
            return providerConfig.defaultTemperature;
        }
    }

    private static int getMaxTokens(Properties config, ProviderConfig providerConfig) {
        try {
            return Integer.parseInt(config.getProperty("ai.max.tokens",
                    String.valueOf(providerConfig.maxTokens)));
        } catch (NumberFormatException e) {
            return providerConfig.maxTokens;
        }
    }

    private static AiService createMockService() {
        logger.debug("Создание MockAiService");
        return new MockAiService();
    }

    private static String buildCacheKey(String provider, String apiKey, Properties config) {
        String model = config.getProperty("ai.model", "");
        String url = config.getProperty("ai.custom.url", "");
        return String.format("%s|%s|%s|%s", provider, maskApiKey(apiKey), model, url);
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) return "***";
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private static AiService getCachedService(String cacheKey) {
        Long timestamp = cacheTimestamp.get(cacheKey);
        if (timestamp == null) {
            return null;
        }

        if (System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(CACHE_TTL_MINUTES)) {
            serviceCache.remove(cacheKey);
            cacheTimestamp.remove(cacheKey);
            return null;
        }

        AiService service = serviceCache.get(cacheKey);
        if (service != null) {
            logger.debug("Найден кэшированный сервис для ключа: {}", cacheKey);
        }
        return service;
    }

    private static void cacheService(String cacheKey, AiService service) {
        if (serviceCache.size() >= CACHE_SIZE) {
            // Удаляем самую старую запись
            serviceCache.entrySet().stream()
                    .min(Map.Entry.comparingByKey())
                    .ifPresent(entry -> {
                        serviceCache.remove(entry.getKey());
                        cacheTimestamp.remove(entry.getKey());
                    });
        }

        serviceCache.put(cacheKey, service);
        cacheTimestamp.put(cacheKey, System.currentTimeMillis());
        logger.debug("Сервис закэширован с ключом: {}", cacheKey);
    }

    private static void scheduleCacheCleanup() {
        threadPoolManager.getScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            cacheTimestamp.entrySet().removeIf(entry ->
                    now - entry.getValue() > TimeUnit.MINUTES.toMillis(CACHE_TTL_MINUTES)
            );
            serviceCache.keySet().retainAll(cacheTimestamp.keySet());
        }, CACHE_TTL_MINUTES, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public static boolean testConnection(String provider, String apiKey) {
        try {
            ProviderConfig config = providerConfigs.getOrDefault(provider,
                    providerConfigs.get("groq"));

            URL url = new URL(config.defaultUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            if (config.requiresBearerToken) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            int responseCode = connection.getResponseCode();
            return responseCode == 200;

        } catch (IOException e) {
            logger.debug("Ошибка при тестировании подключения к {}: {}", provider, e.getMessage());
            return false;
        }
    }

    public static CompletableFuture<Boolean> testConnectionAsync(String provider, String apiKey) {
        return CompletableFuture.supplyAsync(() -> testConnection(provider, apiKey), backgroundExecutor);
    }

    public static String[] getAvailableProviders() {
        return providerConfigs.keySet().toArray(new String[0]);
    }

    public static String getProviderInfo(String provider) {
        ProviderConfig config = providerConfigs.get(provider);
        if (config == null) return "Неизвестный провайдер";

        return String.format("""
                Провайдер: %s
                • URL по умолчанию: %s
                • Модель по умолчанию: %s
                • Максимальное количество токенов: %d
                • Температура по умолчанию: %.1f
                """,
                provider,
                config.defaultUrl,
                config.defaultModel,
                config.maxTokens,
                config.defaultTemperature
        );
    }

    public static String getStats() {
        return String.format("""
                AIServiceFactory Statistics:
                • Всего запросов: %d
                • Попаданий в кэш: %d (%.1f%%)
                • Промахов кэша: %d
                • Среднее время создания: %.1f мс
                • Размер кэша: %d/%d
                """,
                factoryRequests.get(),
                cacheHits.get(),
                factoryRequests.get() > 0 ?
                        (cacheHits.get() * 100.0 / factoryRequests.get()) : 0,
                cacheMisses.get(),
                factoryRequests.get() > 0 ?
                        (double) totalCreationTime.get() / factoryRequests.get() : 0,
                serviceCache.size(),
                CACHE_SIZE
        );
    }

    private static void recordMetrics(long startTime) {
        totalCreationTime.addAndGet(System.currentTimeMillis() - startTime);
    }

    public static void clearCache() {
        serviceCache.clear();
        cacheTimestamp.clear();
        logger.info("Кэш AIServiceFactory очищен");
    }

    public static void resetStats() {
        factoryRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        totalCreationTime.set(0);
        logger.info("Статистика AIServiceFactory сброшена");
    }

    public static void shutdown() {
        if (isShutdown.getAndSet(true)) {
            return;
        }

        logger.info("Завершение работы AIServiceFactory...");

        clearCache();
        resetStats();

        logger.info("AIServiceFactory завершил работу");
    }

    public static boolean isActive() {
        return isInitialized.get() && !isShutdown.get();
    }

    public static String getDefaultUrl(String provider) {
        ProviderConfig config = providerConfigs.get(provider);
        return config != null ? config.defaultUrl : providerConfigs.get("groq").defaultUrl;
    }

    public static String getDefaultModel(String provider) {
        ProviderConfig config = providerConfigs.get(provider);
        return config != null ? config.defaultModel : providerConfigs.get("groq").defaultModel;
    }

    public static int getDefaultMaxTokens(String provider) {
        ProviderConfig config = providerConfigs.get(provider);
        return config != null ? config.maxTokens : providerConfigs.get("groq").maxTokens;
    }
}