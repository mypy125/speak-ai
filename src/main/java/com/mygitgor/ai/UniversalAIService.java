package com.mygitgor.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mygitgor.model.SpeechAnalysis;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class UniversalAIService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(UniversalAIService.class);

    private final String apiKey;
    private final String provider;
    private final String model;
    private final String apiUrl;
    private final double temperature;
    private final int maxTokens;

    private final OkHttpClient client;
    private boolean isAvailable;

    public UniversalAIService(String apiKey, String provider, String model,
                              String apiUrl, double temperature, int maxTokens) {
        this.apiKey = apiKey;
        this.provider = provider;
        this.model = model;
        this.apiUrl = apiUrl;
        this.temperature = temperature;
        this.maxTokens = maxTokens;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.isAvailable = testConnection();
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

            Response response = client.newCall(buildHttpRequest(request)).execute();

            if (response.isSuccessful()) {
                logger.info("✅ {} API подключен успешно. Модель: {}", getProviderName(), model);
                return true;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "";
                logger.error("❌ Ошибка подключения к {} API: {} - {}",
                        getProviderName(), response.code(), response.message());

                // Логируем детали ошибки
                try {
                    JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
                    if (errorJson.has("error")) {
                        JsonObject error = errorJson.getAsJsonObject("error");
                        if (error.has("message")) {
                            logger.error("Детали ошибки: {}", error.get("message").getAsString());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Тело ошибки: {}", errorBody);
                }
                return false;
            }
        } catch (Exception e) {
            logger.error("Ошибка при тестировании подключения к {} API", getProviderName(), e);
            return false;
        }
    }

    @Override
    public String analyzeText(String text) {
        String prompt = String.format("""
            Ты - AI репетитор английского языка. Проанализируй следующее предложение ученика:
            
            "%s"
            
            В ответе укажи:
            1. Коррекцию грамматических ошибок (если есть)
            2. Предложения по улучшению формулировки
            3. Альтернативные способы выражения той же мысли
            4. Рекомендации по использованию словарного запаса
            
            Форматируй ответ с использованием Markdown.
            """, text);

        try {
            return callAPI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при анализе текста", e);
            return getFallbackAnalysis(text);
        }
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        SpeechAnalysis analysis = new SpeechAnalysis();
        analysis.setText(text);
        analysis.setAudioPath(audioPath);

        if (isAvailable) {
            try {
                String prompt = String.format("""
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

                String aiResponse = callAPI(prompt);
                parseAnalysisFromResponse(aiResponse, analysis);

            } catch (IOException e) {
                logger.error("Ошибка при анализе произношения", e);
                generateMockAnalysis(analysis);
            }
        } else {
            generateMockAnalysis(analysis);
        }

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        String prompt = String.format("""
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

        try {
            return callAPI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при генерации ответа", e);
            return getFallbackResponse(userMessage, analysis);
        }
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        String prompt = String.format("""
            Сгенерируй упражнение по английскому языку на тему "%s" для уровня "%s".
            
            Включи:
            1. Краткое объяснение темы
            2. 5 практических заданий
            3. Примеры выполнения
            4. Ключевые слова и выражения
            
            Используй Markdown форматирование.
            """, topic, difficulty);

        try {
            return callAPI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при генерации упражнения", e);
            return getFallbackExercise(topic, difficulty);
        }
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    private String callAPI(String prompt) throws IOException {
        JsonObject request = createRequest(prompt);
        okhttp3.Request httpRequest = buildHttpRequest(request);

        logger.debug("Отправка запроса к {} API, модель: {}", getProviderName(), model);

        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException(String.format("API error %d: %s",
                        response.code(), errorBody));
            }

            String responseBody = response.body().string();
            return parseResponse(responseBody);
        }
    }

    private JsonObject createRequest(String prompt) {
        JsonObject request = new JsonObject();

        switch (provider) {
            case "anthropic":
                // Claude формат
                request.addProperty("model", model);
                request.addProperty("max_tokens", maxTokens);
                request.addProperty("temperature", temperature);

                JsonArray messages = new JsonArray();
                JsonObject message = new JsonObject();
                message.addProperty("role", "user");
                message.addProperty("content", prompt);
                messages.add(message);

                request.add("messages", messages);
                break;

            case "ollama":
                // Ollama формат
                request.addProperty("model", model);
                request.addProperty("prompt", prompt);
                request.addProperty("temperature", temperature);
                request.addProperty("max_tokens", maxTokens);
                request.addProperty("stream", false);
                break;

            default:
                // OpenAI-совместимый формат
                request.addProperty("model", model);
                request.addProperty("temperature", temperature);
                request.addProperty("max_tokens", maxTokens);

                JsonArray messagesDefault = new JsonArray();
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", "system");
                systemMessage.addProperty("content", "Ты - AI репетитор английского языка.");
                messagesDefault.add(systemMessage);

                JsonObject userMessage = new JsonObject();
                userMessage.addProperty("role", "user");
                userMessage.addProperty("content", prompt);
                messagesDefault.add(userMessage);

                request.add("messages", messagesDefault);
                break;
        }

        return request;
    }

    private okhttp3.Request buildHttpRequest(JsonObject requestBody) {
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request.Builder builder = new Request.Builder()
                .url(apiUrl)
                .header("Content-Type", "application/json");

        // Добавляем заголовки авторизации
        if (provider.equals("anthropic")) {
            builder.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
        } else if (provider.equals("ollama")) {
            // Ollama не требует авторизации
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        builder.header("User-Agent", "SpeakAI/2.0");

        return builder.post(body).build();
    }

    private String parseResponse(String responseBody) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            switch (provider) {
                case "anthropic":
                    // Claude формат
                    return json.getAsJsonArray("content")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();

                case "ollama":
                    // Ollama формат
                    return json.get("response").getAsString();

                default:
                    // OpenAI-совместимый формат
                    return json.get("choices")
                            .getAsJsonArray()
                            .get(0).getAsJsonObject()
                            .get("message").getAsJsonObject()
                            .get("content").getAsString();
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа", e);
            throw new IOException("Неверный формат ответа API");
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

    // Fallback методы
    private String getFallbackAnalysis(String text) {
        return String.format("### Анализ текста (демо-режим)\n\n**Текст:** %s\n\n**Рекомендации:**\n1. Проверьте грамматику\n2. Используйте разнообразный словарный запас\n3. Практикуйте естественные формулировки", text);
    }

    private String getFallbackResponse(String userMessage, SpeechAnalysis analysis) {
        return String.format("Привет! 👋\n\nЯ получил ваше сообщение: \"%s\"\n\nДавайте продолжим практиковать английский!", userMessage);
    }

    private String getFallbackExercise(String topic, String difficulty) {
        return String.format("## Упражнение: %s\n**Уровень:** %s\n\nПрактикуйте тему \"%s\" с помощью:\n1. Составления предложений\n2. Перевода текстов\n3. Диалогов на тему", topic, difficulty, topic);
    }

    private void parseAnalysisFromResponse(String response, SpeechAnalysis analysis) {
        // Простой парсинг ответа AI
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

        analysis.addRecommendation("Практикуйте произношение сложных звуков");
        analysis.addRecommendation("Уделите внимание грамматическим конструкциям");
        analysis.addRecommendation("Расширяйте словарный запас");
    }

    // Геттеры
    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getApiUrl() {
        return apiUrl;
    }
}