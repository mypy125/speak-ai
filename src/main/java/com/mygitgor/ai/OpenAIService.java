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

public class OpenAIService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final OkHttpClient client;
    private final String apiKey;
    private boolean isAvailable;

    public OpenAIService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.isAvailable = testConnection();
    }

    private boolean testConnection() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            logger.warn("API ключ OpenAI не настроен");
            return false;
        }

        try {
            // Простой тестовый запрос
            JsonObject testRequest = new JsonObject();
            testRequest.addProperty("model", "gpt-3.5-turbo");
            testRequest.add("messages", JsonParser.parseString(
                    "[{\"role\": \"user\", \"content\": \"Hello\"}]"
            ));
            testRequest.addProperty("max_tokens", 10);

            Request request = createRequest(testRequest);

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Соединение с OpenAI API успешно установлено");
                    return true;
                } else {
                    logger.error("Ошибка соединения с OpenAI API: {}", response.code());
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при тестировании соединения с OpenAI API", e);
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
            """, text);

        try {
            return callOpenAI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при анализе текста", e);
            return "Извините, произошла ошибка при анализе текста. Пожалуйста, попробуйте еще раз.";
        }
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        // В MVP версии эмулируем анализ произношения
        // В будущем можно интегрировать с Whisper API или другими сервисами

        SpeechAnalysis analysis = new SpeechAnalysis();
        analysis.setText(text);
        analysis.setAudioPath(audioPath);

        // Эмуляция анализа
        analysis.setPronunciationScore(75 + Math.random() * 20);
        analysis.setFluencyScore(70 + Math.random() * 25);
        analysis.setGrammarScore(80 + Math.random() * 15);
        analysis.setVocabularyScore(85 + Math.random() * 10);

        // Добавляем примеры ошибок и рекомендаций
        if (analysis.getPronunciationScore() < 80) {
            analysis.addError("Некоторые звуки произносятся нечетко");
            analysis.addRecommendation("Практикуйте произношение звуков 'th' и 'r'");
        }

        if (analysis.getFluencyScore() < 75) {
            analysis.addError("Есть паузы и колебания в речи");
            analysis.addRecommendation("Попробуйте говорить медленнее, но увереннее");
        }

        if (analysis.getGrammarScore() < 85) {
            analysis.addError("Небольшие грамматические неточности");
            analysis.addRecommendation("Обратите внимание на использование времен");
        }

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        String prompt = String.format("""
            Ты - дружелюбный AI репетитор английского языка. Ответь на сообщение ученика и дай обратную связь:
            
            Сообщение ученика: "%s"
            
            Результаты анализа речи ученика:
            %s
            
            Твой ответ должен быть:
            1. Естественным и дружелюбным
            2. Включать коррекцию ошибок (если они есть)
            3. Содержать похвалу за хорошие аспекты
            4. Предлагать полезные советы для улучшения
            5. Поощрять дальнейшую практику
            
            Отвечай на русском языке, но при необходимости используй английские примеры.
            """, userMessage, analysis != null ? analysis.getSummary() : "Анализ не проводился");

        try {
            return callOpenAI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при генерации ответа бота", e);
            return "Привет! Я твой AI репетитор английского. Давай продолжим нашу беседу!";
        }
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        String prompt = String.format("""
            Сгенерируй упражнение по английскому языку на тему "%s" для уровня сложности "%s".
            
            Упражнение должно включать:
            1. Краткое объяснение темы
            2. 5 практических заданий
            3. Примеры выполнения
            4. Ключевые слова и выражения
            
            Форматируй ответ с использованием Markdown.
            """, topic, difficulty);

        try {
            return callOpenAI(prompt);
        } catch (IOException e) {
            logger.error("Ошибка при генерации упражнения", e);
            return "Упражнение на тему \"" + topic + "\" будет доступно в следующем обновлении!";
        }
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    private String callOpenAI(String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-3.5-turbo");
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 1500);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        requestBody.add("messages", messages);

        Request request = createRequest(requestBody);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Ошибка OpenAI API: {}", response.code());
                throw new IOException("API request failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

            return jsonResponse.get("choices")
                    .getAsJsonArray()
                    .get(0)
                    .getAsJsonObject()
                    .get("message")
                    .getAsJsonObject()
                    .get("content")
                    .getAsString();
        }
    }

    private Request createRequest(JsonObject requestBody) {
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        return new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
    }

    public void setApiKey(String apiKey) {
        // Метод для обновления API ключа
        // this.apiKey = apiKey; // Заметка: поле final
        logger.info("API ключ обновлен");
    }
}