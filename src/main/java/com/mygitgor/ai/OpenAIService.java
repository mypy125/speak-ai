package com.mygitgor.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mygitgor.model.SpeechAnalysis;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class OpenAIService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);

    // Конфигурируемые параметры
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String apiUrl;

    private final OkHttpClient client;
    private boolean isAvailable;

    // Конструктор с параметрами
    public OpenAIService(String apiKey, String model, double temperature, int maxTokens, String apiUrl) {
        this.apiKey = apiKey;
        this.model = model != null && !model.trim().isEmpty() ? model.trim() : "gpt-3.5-turbo";
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.apiUrl = apiUrl != null && !apiUrl.trim().isEmpty() ? apiUrl.trim() : "https://api.openai.com/v1/chat/completions";

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.isAvailable = testConnection();
    }

    // Конструктор с минимальными параметрами (для обратной совместимости)
    public OpenAIService(String apiKey) {
        this(apiKey, "gpt-3.5-turbo", 0.7, 1500, "https://api.openai.com/v1/chat/completions");
    }

    // Фабричный метод для создания сервиса из конфигурации
    public static OpenAIService fromConfig(Properties config) {
        String apiKey = config.getProperty("openai.api.key", "").trim();
        String model = config.getProperty("openai.api.model", "gpt-3.5-turbo").trim();
        double temperature = Double.parseDouble(config.getProperty("openai.api.temperature", "0.7"));
        int maxTokens = Integer.parseInt(config.getProperty("openai.api.max_tokens", "1500"));
        String apiUrl = config.getProperty("openai.api.url", "https://api.openai.com/v1/chat/completions").trim();

        return new OpenAIService(apiKey, model, temperature, maxTokens, apiUrl);
    }

    private boolean testConnection() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            logger.warn("API ключ не настроен. Сервис будет работать в демо-режиме.");
            return false;
        }

        try {
            // Тестовый запрос с минимальными параметрами
            JsonObject testRequest = createBaseRequest("Hello");
            testRequest.addProperty("max_tokens", 10);

            Request request = createRequest(testRequest);

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Соединение с {} API успешно установлено. Модель: {}", getProviderName(), model);
                    return true;
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.error("Ошибка соединения с API ({}): {}. Ответ: {}",
                            response.code(), response.message(), errorBody);

                    // Проверяем, может это ошибка ключа или доступности модели
                    if (response.code() == 401) {
                        logger.error("Неверный API ключ. Проверьте настройки в application.properties");
                    } else if (response.code() == 404) {
                        logger.error("Модель '{}' не найдена. Проверьте доступность модели.", model);
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при тестировании соединения с API", e);
            return false;
        }
    }

    @Override
    public String analyzeText(String text) {
        String prompt = String.format("""
            Ты - AI репетитор английского языка. Проанализируй следующее предложение ученика и дай подробную обратную связь:
            
            "%s"
            
            В ответе укажи:
            1. Коррекцию грамматических ошибок (если есть)
            2. Предложения по улучшению формулировки
            3. Альтернативные способы выражения той же мысли
            4. Рекомендации по использованию словарного запаса
            
            Форматируй ответ с использованием Markdown для лучшей читаемости.
            
            Если в сообщении есть русские слова, предложи их английские эквиваленты.
            """, text);

        try {
            return callAPI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при анализе текста", e);
            return fallbackTextAnalysis(text);
        }
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        SpeechAnalysis analysis = new SpeechAnalysis();
        analysis.setText(text);
        analysis.setAudioPath(audioPath);

        // Генерируем анализ через AI, если доступен
        if (isAvailable) {
            try {
                String prompt = String.format("""
                    Ты - эксперт по фонетике английского языка. Проанализируй произношение следующего текста:
                    
                    Текст: "%s"
                    
                    Предполагаемые проблемы произношения (если аудио недоступно):
                    1. Сложные звуки для русскоговорящих
                    2. Интонационные паттерны
                    3. Ритм и ударение
                    
                    Дай оценку по 100-балльной шкале для:
                    - Произношения
                    - Беглости речи
                    - Грамматики
                    - Словарного запаса
                    
                    Укажи конкретные рекомендации для улучшения.
                    """, text);

                String aiResponse = callAPI(prompt);

                // Парсим ответ AI
                parseAIResponseToAnalysis(aiResponse, analysis);

            } catch (IOException e) {
                logger.error("Ошибка при анализе произношения через AI", e);
                generateMockAnalysis(analysis); // Fallback на мок-данные
            }
        } else {
            generateMockAnalysis(analysis);
        }

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        String prompt = String.format("""
            Ты - дружелюбный AI репетитор английского языка. Ответь на сообщение ученика и дай обратную связь:
            
            Сообщение ученика: "%s"
            
            %s
            
            Твой ответ должен быть:
            1. Естественным и дружелюбным
            2. Включать коррекцию ошибок (если они есть)
            3. Содержать похвалу за хорошие аспекты
            4. Предлагать полезные советы для улучшения
            5. Поощрять дальнейшую практику
            
            Формат ответа:
            - Используй Markdown для форматирования
            - Отвечай на русском языке
            - Для английских примеров используй код: `пример`
            - Будь конкретным и полезным
            """,
                userMessage,
                analysis != null ?
                        String.format("Результаты анализа речи ученика:\n%s", analysis.getSummary()) :
                        "Анализ речи не проводился.");

        try {
            return callAPI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при генерации ответа бота", e);
            return fallbackBotResponse(userMessage, analysis);
        }
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        String prompt = String.format("""
            Сгенерируй упражнение по английскому языку на тему "%s" для уровня сложности "%s".
            
            Структура упражнения:
            ## Тема: [Название темы]
            
            ### Объяснение:
            [Краткое объяснение темы на русском с английскими примерами]
            
            ### Практические задания (5 заданий):
            1. [Задание 1 с примером]
            2. [Задание 2 с примером]
            3. [Задание 3 с примером]
            4. [Задание 4 с примером]
            5. [Задание 5 с примером]
            
            ### Ключевые слова и выражения:
            - [Английское слово/фраза]: [Перевод и пример использования]
            
            ### Советы для выполнения:
            [Полезные советы для ученика]
            
            Используй Markdown форматирование.
            """, topic, difficulty);

        try {
            return callAPI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при генерации упражнения", e);
            return fallbackExercise(topic, difficulty);
        }
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    // Основной метод вызова API
    private String callAPI(String prompt) throws IOException {
        JsonObject requestBody = createBaseRequest(prompt);

        Request request = createRequest(requestBody);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                logger.error("Ошибка API ({}): {}. Ответ: {}",
                        response.code(), response.message(), errorBody);

                // Пробуем обработать ошибку
                handleAPIError(response.code(), errorBody);

                throw new IOException(String.format("API request failed: %d - %s",
                        response.code(), response.message()));
            }

            String responseBody = response.body().string();
            return parseAPIResponse(responseBody);
        }
    }

    private JsonObject createBaseRequest(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("max_tokens", maxTokens);

        // Добавляем параметры для разных провайдеров
        if (model.contains("claude")) {
            // Anthropic Claude специфичные параметры
            requestBody.addProperty("anthropic_version", "2023-06-01");
        }

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        requestBody.add("messages", messages);

        return requestBody;
    }

    private Request createRequest(JsonObject requestBody) {
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("Content-Type", "application/json")
                .post(body);

        // Добавляем заголовки авторизации в зависимости от провайдера
        if (apiUrl.contains("openai.com")) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        } else if (apiUrl.contains("anthropic.com")) {
            requestBuilder.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
        } else if (apiUrl.contains("groq.com")) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        } else if (apiUrl.contains("together.xyz")) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        } else {
            // По умолчанию OpenAI-совместимый формат
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        return requestBuilder.build();
    }

    private String parseAPIResponse(String responseBody) throws IOException {
        try {
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

            // Обработка разных форматов ответов
            if (jsonResponse.has("choices")) {
                // OpenAI-совместимый формат
                return jsonResponse.get("choices")
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("message")
                        .getAsJsonObject()
                        .get("content")
                        .getAsString();
            } else if (jsonResponse.has("content")) {
                // Anthropic Claude формат
                return jsonResponse.getAsJsonArray("content")
                        .get(0)
                        .getAsJsonObject()
                        .get("text")
                        .getAsString();
            } else if (jsonResponse.has("output")) {
                // Другие форматы
                return jsonResponse.get("output").getAsJsonObject()
                        .get("choices").getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
            } else {
                throw new IOException("Неизвестный формат ответа API");
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа API", e);
            throw new IOException("Ошибка обработки ответа API", e);
        }
    }

    // Вспомогательные методы
    private String getProviderName() {
        if (apiUrl.contains("openai.com")) return "OpenAI";
        if (apiUrl.contains("anthropic.com")) return "Anthropic Claude";
        if (apiUrl.contains("groq.com")) return "Groq";
        if (apiUrl.contains("together.xyz")) return "Together AI";
        if (apiUrl.contains("deepseek.com")) return "DeepSeek";
        if (apiUrl.contains("ollama")) return "Ollama (локальный)";
        return "Неизвестный провайдер";
    }

    private void handleAPIError(int code, String errorBody) {
        try {
            JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
            JsonObject error = errorJson.getAsJsonObject("error");

            if (error != null) {
                String errorMessage = error.has("message") ?
                        error.get("message").getAsString() : "Unknown error";
                logger.error("API Error Message: {}", errorMessage);

                // Логируем тип ошибки
                if (error.has("type")) {
                    logger.error("Error Type: {}", error.get("type").getAsString());
                }
            }
        } catch (Exception e) {
            logger.error("Не удалось распарсить тело ошибки", e);
        }
    }

    private void parseAIResponseToAnalysis(String aiResponse, SpeechAnalysis analysis) {
        try {
            // Простой парсинг ответа AI (можно улучшить)
            if (aiResponse.contains("Произношение:")) {
                // Извлекаем оценки из текста
                String[] lines = aiResponse.split("\n");
                for (String line : lines) {
                    if (line.contains("Произношение:") && line.contains("/100")) {
                        String score = line.replaceAll(".*?(\\d+(\\.\\d+)?).*", "$1");
                        analysis.setPronunciationScore(Double.parseDouble(score));
                    }
                    if (line.contains("Беглость:") && line.contains("/100")) {
                        String score = line.replaceAll(".*?(\\d+(\\.\\d+)?).*", "$1");
                        analysis.setFluencyScore(Double.parseDouble(score));
                    }
                    if (line.contains("Грамматика:") && line.contains("/100")) {
                        String score = line.replaceAll(".*?(\\d+(\\.\\d+)?).*", "$1");
                        analysis.setGrammarScore(Double.parseDouble(score));
                    }
                    if (line.contains("Словарный запас:") && line.contains("/100")) {
                        String score = line.replaceAll(".*?(\\d+(\\.\\d+)?).*", "$1");
                        analysis.setVocabularyScore(Double.parseDouble(score));
                    }
                }
            }

            // Добавляем общие рекомендации из ответа AI
            analysis.addRecommendation("Обратитесь к полному ответу AI выше для детальных рекомендаций");

        } catch (Exception e) {
            logger.error("Ошибка при парсинге ответа AI", e);
            generateMockAnalysis(analysis);
        }
    }

    private void generateMockAnalysis(SpeechAnalysis analysis) {
        // Fallback генерация мок-данных
        analysis.setPronunciationScore(75 + Math.random() * 20);
        analysis.setFluencyScore(70 + Math.random() * 25);
        analysis.setGrammarScore(80 + Math.random() * 15);
        analysis.setVocabularyScore(85 + Math.random() * 10);

        analysis.addRecommendation("Практикуйте произношение сложных звуков");
        analysis.addRecommendation("Уделите внимание использованию правильных времен");
        analysis.addRecommendation("Расширяйте словарный запас по теме");
    }

    private String fallbackTextAnalysis(String text) {
        return String.format("""
            ### Анализ текста (демо-режим)
            
            **Ваш текст:** "%s"
            
            **Рекомендации:**
            1. Обратите внимание на грамматические конструкции
            2. Используйте разнообразный словарный запас
            3. Практикуйте естественные формулировки
            
            **Пример улучшения:**
            Оригинал: %s
            Улучшенный вариант: Try to use more natural expressions in your sentences.
            
            *Примечание: Для получения полноценного анализа настройте API ключ в application.properties*
            """, text, text);
    }

    private String fallbackBotResponse(String userMessage, SpeechAnalysis analysis) {
        return String.format("""
            Привет! 👋 
            
            Я получил ваше сообщение: "%s"
            
            %s
            
            **Совет:** Продолжайте практиковать английский каждый день. 
            Даже короткие ежедневные занятия приносят результаты!
            
            *Работаю в демо-режиме. Для получения персональных рекомендаций настройте API ключ.*
            """,
                userMessage,
                analysis != null ?
                        "Ваша речь была проанализирована. " + analysis.getSummary() :
                        "Ваше сообщение получено.");
    }

    private String fallbackExercise(String topic, String difficulty) {
        return String.format("""
            ## Упражнение: %s
            **Уровень:** %s
            
            ### Объяснение:
            В этом упражнении мы будем практиковать тему "%s".
            
            ### Задания:
            1. Составьте 5 предложений на тему
            2. Переведите предложения с русского на английский
            3. Найдите ошибки в примерах
            4. Составьте диалог на тему
            5. Напишите краткое эссе
            
            ### Ключевые слова:
            - Practice: практика
            - Improve: улучшать
            - Learn: учить
            
            ### Советы:
            - Занимайтесь регулярно
            - Используйте словарь при необходимости
            - Практикуйте произношение вслух
            
            *Для получения персонализированных упражнений настройте API ключ.*
            """, topic, difficulty, topic);
    }

    // Геттеры для информации о конфигурации
    public String getModel() {
        return model;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getProvider() {
        return getProviderName();
    }
}