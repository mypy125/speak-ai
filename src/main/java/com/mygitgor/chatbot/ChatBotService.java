package com.mygitgor.chatbot;

import com.mygitgor.ai.AiService;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.model.User;
import com.mygitgor.repository.DAO.ConversationDao;
import com.mygitgor.repository.DAO.UserDao;
import com.mygitgor.speech.*;
import com.mygitgor.speech.STT.SpeechToTextService;
import com.mygitgor.speech.TTS.TextToSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatBotService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotService.class);

    private final AiService aiService;
    private final ConversationDao conversationDao;
    private final AudioAnalyzer audioAnalyzer;
    private final PronunciationTrainer pronunciationTrainer;
    private final RecommendationEngine recommendationEngine;
    private final SpeechToTextService speechToTextService;
    private TextToSpeechService textToSpeechService; // Добавлен TTS сервис
    private User currentUser;

    private volatile boolean closed = false;

    public ChatBotService(AiService aiService, AudioAnalyzer audioAnalyzer,
                          PronunciationTrainer pronunciationTrainer,
                          TextToSpeechService textToSpeechService) { // Добавляем параметр
        this.aiService = aiService;
        this.audioAnalyzer = audioAnalyzer;
        this.pronunciationTrainer = pronunciationTrainer;
        this.textToSpeechService = textToSpeechService; // Используем переданный сервис

        // Остальная инициализация...
        this.recommendationEngine = new RecommendationEngine();

        // Инициализация DAO
        try {
            this.conversationDao = new ConversationDao();
        } catch (Exception e) {
            logger.error("Ошибка инициализации ConversationDao", e);
            throw new RuntimeException("Не удалось инициализировать ChatBotService", e);
        }

        // Инициализация сервиса распознавания речи
        this.speechToTextService = createSpeechToTextService();

        // Временный пользователь для MVP
        this.currentUser = createDefaultUser();

        logger.info("ChatBotService инициализирован с AudioAnalyzer и {}",
                textToSpeechService.getClass().getSimpleName());
    }

    public void setTextToSpeechService(TextToSpeechService newService) {
        if (newService == null) {
            throw new IllegalArgumentException("TTS сервис не может быть null");
        }

        // Закрываем старый сервис, если он существует и это не тот же самый объект
        if (textToSpeechService != null && textToSpeechService != newService) {
            try {
                textToSpeechService.close();
                logger.info("Старый TTS сервис закрыт: {}",
                        textToSpeechService.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Ошибка при закрытии старого TTS сервиса", e);
            }
        }

        // Устанавливаем новый сервис
        textToSpeechService = newService;
        logger.info("TTS сервис обновлен: {}",
                textToSpeechService.getClass().getSimpleName());

        // Проверяем доступность нового сервиса
        if (textToSpeechService.isAvailable()) {
            logger.info("✅ Новый TTS сервис доступен");
        } else {
            logger.warn("⚠️ Новый TTS сервис работает в ограниченном режиме");
        }
    }

    private User createDefaultUser() {
        User user = new User();
        user.setId(1);
        user.setUsername("Demo User");
        user.setEmail("demo@speakai.com");
        user.setLanguageLevel("B1");
        user.setNativeLanguage("Russian");
        user.setCreatedAt(new Date());
        return user;
    }

    public ChatResponse processUserInput(String text, String audioFilePath) {
        return processUserInput(text, audioFilePath, null);
    }

    public ChatResponse processUserInput(String text, String audioFilePath, ChatBotController.ResponseMode responseMode) {
        try {
            logger.info("Обработка пользовательского ввода. Текст: {}, Аудио: {}, Режим: {}",
                    text, audioFilePath, responseMode != null ? responseMode : "TEXT (по умолчанию)");

            String recognizedText = text;
            EnhancedSpeechAnalysis speechAnalysis = null;

            // Если есть аудиофайл - распознаем речь и анализируем
            if (audioFilePath != null && new File(audioFilePath).exists()) {
                logger.info("Обработка аудиофайла: {}", audioFilePath);

                // 1. Распознавание речи
                SpeechToTextService.SpeechRecognitionResult recognition =
                        speechToTextService.transcribe(audioFilePath);

                if (recognition.isConfident() && !recognition.getText().trim().isEmpty()) {
                    recognizedText = recognition.getText();
                    logger.info("Распознанная речь: {}", recognizedText);
                } else if (text.isEmpty()) {
                    // Если нет текста и распознавание не удалось
                    recognizedText = "[Аудиосообщение - текст не распознан]";
                }

                // 2. Анализ аудио с помощью AudioAnalyzer
                speechAnalysis = audioAnalyzer.analyzeAudio(audioFilePath, recognizedText);
                logger.info("Анализ аудио завершен. Оценка: {}",
                        String.format("%.1f", speechAnalysis.getOverallScore()));

            } else if (audioFilePath != null) {
                logger.warn("Аудиофайл не существует: {}", audioFilePath);
            }

            // 3. Если нет текста вообще (только аудио без распознавания)
            if (recognizedText.isEmpty() || recognizedText.equals("[Аудиосообщение - текст не распознан]")) {
                // Используем текст по умолчанию для анализа
                recognizedText = "I recorded an audio message";
            }

            // 4. Анализ текста через ИИ
            String textAnalysis = aiService.analyzeText(recognizedText);

            // 5. Генерация ответа бота
            String botResponse = aiService.generateBotResponse(recognizedText, speechAnalysis);

            // 6. Генерация персонализированных рекомендаций (если есть анализ речи)
            List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations = null;
            RecommendationEngine.WeeklyLearningPlan weeklyPlan = null;

            if (speechAnalysis != null) {
                personalizedRecommendations = recommendationEngine.generateRecommendations(speechAnalysis);
                weeklyPlan = recommendationEngine.generateWeeklyPlan(speechAnalysis);
            }

            // 7. Сохранение в БД
            saveConversation(recognizedText, botResponse, speechAnalysis, audioFilePath);

            // 8. Формирование полного ответа
            String fullResponse = formatResponse(botResponse, textAnalysis,
                    speechAnalysis, personalizedRecommendations, weeklyPlan, responseMode);

            // 9. Если требуется озвучка - запускаем в фоновом режиме
            if (responseMode == ChatBotController.ResponseMode.VOICE && textToSpeechService != null) {
                // Запускаем озвучку в отдельном потоке, чтобы не блокировать ответ
                new Thread(() -> {
                    try {
                        // Небольшая задержка для пользователя
                        Thread.sleep(300);
                        speakResponse(fullResponse);
                    } catch (Exception e) {
                        logger.warn("Не удалось запустить озвучку ответа: {}", e.getMessage());
                    }
                }).start();
            }

            return new ChatResponse(fullResponse, speechAnalysis,
                    personalizedRecommendations, weeklyPlan);

        } catch (Exception e) {
            logger.error("Ошибка при обработке пользовательского ввода", e);
            return new ChatResponse(
                    "Извините, произошла ошибка при обработке вашего сообщения: " + e.getMessage() +
                            "\nПожалуйста, попробуйте еще раз.",
                    null, null, null
            );
        }
    }

    private void speakResponse(String response) {
        try {
            if (textToSpeechService != null && !closed) {
                logger.info("Начало озвучки ответа...");

                // Очищаем текст для озвучки
                String cleanText = cleanTextForSpeech(response);

                if (cleanText != null && !cleanText.trim().isEmpty()) {
                    // Используем асинхронную озвучку и обрабатываем исключения
                    CompletableFuture<Void> speechFuture = textToSpeechService.speakAsync(cleanText);

                    speechFuture.thenRun(() -> {
                        logger.info("✅ Озвучка ответа завершена");
                    }).exceptionally(throwable -> {
                        // Логируем ошибку, но не показываем пользователю
                        logger.warn("Не удалось озвучить ответ: {}", throwable.getMessage());
                        return null;
                    });

                    logger.info("Озвучка ответа запущена асинхронно");
                } else {
                    logger.warn("Текст для озвучки пуст или null");
                }
            } else {
                logger.warn("TTS сервис недоступен или ChatBotService закрыт");
            }
        } catch (Exception e) {
            logger.error("Ошибка при запуске озвучки ответа: {}", e.getMessage(), e);
            // Не бросаем исключение дальше
        }
    }

    private String cleanTextForSpeech(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // Убираем markdown форматирование, эмодзи и лишние символы
        String cleanText = text
                // Убираем заголовки
                .replaceAll("#+\\s*", "")
                // Убираем жирный текст
                .replaceAll("\\*\\*", "")
                // Убираем курсив
                .replaceAll("\\*", "")
                // Убираем код
                .replaceAll("`", "")
                // Убираем ссылки
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                // Убираем HTML теги
                .replaceAll("<[^>]*>", "")
                // Убираем эмодзи и специальные символы
                .replaceAll("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️🏆🎉👍💪📚🔧❤️✨🌟🔥💡🎯📅❌ℹ️]", "")
                // Заменяем множественные переносы на точку
                .replaceAll("\\n{2,}", ". ")
                // Заменяем одиночные переносы на пробел
                .replaceAll("\\n", " ")
                // Убираем лишние пробелы
                .replaceAll("\\s+", " ")
                .trim();

        // Убираем метку "Ответ озвучен" если она есть
        if (cleanText.startsWith("🔊 Ответ озвучен")) {
            int idx = cleanText.indexOf("\n\n");
            if (idx > 0) {
                cleanText = cleanText.substring(idx + 2);
            } else {
                cleanText = cleanText.replace("🔊 Ответ озвучен", "");
            }
        }

        // Ограничиваем длину текста для TTS (чтобы не было слишком долго)
        int maxLength = 2000;
        if (cleanText.length() > maxLength) {
            cleanText = cleanText.substring(0, maxLength) + "... [текст сокращен]";
        }

        return cleanText;
    }

    private SpeechToTextService createSpeechToTextService() {
        try {
            Properties props = new Properties();
            props.load(getClass().getResourceAsStream("/application.properties"));

            String serviceTypeStr = props.getProperty("speech.service.type", "MOCK");
            String apiKey = props.getProperty("speech.api.key", "");
            String defaultLanguage = props.getProperty("speech.default.language", "en");

            SpeechToTextService.ServiceType serviceType;
            try {
                serviceType = SpeechToTextService.ServiceType.valueOf(serviceTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Неизвестный тип сервиса распознавания: {}, используем MOCK", serviceTypeStr);
                serviceType = SpeechToTextService.ServiceType.MOCK;
            }

            logger.info("Создан SpeechToTextService типа: {}, язык по умолчанию: {}",
                    serviceType, defaultLanguage);
            return new SpeechToTextService(serviceType, apiKey, defaultLanguage);

        } catch (Exception e) {
            logger.error("Ошибка при создании SpeechToTextService, используем MOCK", e);
            return new SpeechToTextService(SpeechToTextService.ServiceType.MOCK, "", "en");
        }
    }

    public void switchSpeechLanguage(String languageCode) {
        if (speechToTextService.getServiceType() == SpeechToTextService.ServiceType.VOSK) {
            speechToTextService.switchLanguage(languageCode);
            logger.info("Язык распознавания речи изменен на: {}",
                    speechToTextService.getCurrentLanguageName());
        } else {
            logger.warn("Смена языка поддерживается только для Vosk");
        }
    }

    public List<String> getSupportedLanguages() {
        return speechToTextService.getSupportedLanguages();
    }

    public Map<String, String> getSupportedLanguagesWithNames() {
        return speechToTextService.getSupportedLanguagesWithNames();
    }

    public String getCurrentSpeechLanguage() {
        return speechToTextService.getCurrentLanguage();
    }

    public String getCurrentSpeechLanguageName() {
        return speechToTextService.getCurrentLanguageName();
    }

    private void saveConversation(String userMessage, String botResponse,
                                  SpeechAnalysis analysis, String audioPath) {
        try {
            User defaultUser = ensureDefaultUserExists();

            Conversation conversation = new Conversation();
            conversation.setUser(defaultUser);
            conversation.setUserMessage(userMessage);
            conversation.setBotResponse(botResponse);
            conversation.setAudioPath(audioPath);
            conversation.setTimestamp(new Date());

            if (analysis != null) {
                conversation.setPronunciationScore(analysis.getPronunciationScore());
                conversation.setGrammarScore(analysis.getGrammarScore());
                conversation.setVocabularyScore(analysis.getVocabularyScore());
                conversation.setAnalysisResult(analysis.getSummary());

                if (analysis.getRecommendations() != null && !analysis.getRecommendations().isEmpty()) {
                    conversation.setRecommendations(String.join("; ", analysis.getRecommendations()));
                }
            }

            conversationDao.createConversation(conversation);

            logger.info("Разговор сохранен в БД, ID: {}, для пользователя: {}",
                    conversation.getId(), defaultUser.getUsername());

        } catch (Exception e) {
            logger.error("Ошибка при сохранении разговора в БД", e);
        }
    }

    private User ensureDefaultUserExists() {
        if (currentUser != null) {
            return currentUser;
        }

        try {
            UserDao userDao = new UserDao();

            // Сначала проверяем, есть ли пользователь в базе
            User user = userDao.getUserByEmail("demo@speakai.com");

            if (user == null) {
                // Создаем нового пользователя
                user = createDefaultUser();
                User createdUser = userDao.createUser(user); // Метод возвращает User, а не int
                if (createdUser != null) {
                    user = createdUser;
                    logger.info("Создан новый пользователь с ID: {}", user.getId());
                } else {
                    logger.warn("Не удалось создать пользователя, используется временный объект");
                }
            } else {
                logger.info("Найден существующий пользователь с ID: {}", user.getId());
            }

            currentUser = user;
            return user;

        } catch (Exception e) {
            logger.error("Ошибка при проверке/создании пользователя", e);
            // В случае ошибки возвращаем пользователя без сохранения в БД
            return createDefaultUser();
        }
    }

    private String formatResponse(String botResponse, String textAnalysis,
                                  SpeechAnalysis speechAnalysis,
                                  List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations,
                                  RecommendationEngine.WeeklyLearningPlan weeklyPlan,
                                  ChatBotController.ResponseMode responseMode) {
        StringBuilder response = new StringBuilder();

        // Добавляем метку режима в ответ
        if (responseMode == ChatBotController.ResponseMode.VOICE) {
            response.append("🔊 **Ответ озвучен**\n\n");
        }

        // Ответ бота
        response.append("## 🤖 AI Репетитор:\n\n");
        response.append(botResponse).append("\n\n");

        // Анализ текста
        if (textAnalysis != null && !textAnalysis.trim().isEmpty()) {
            response.append("## 📝 Анализ текста:\n\n");
            response.append(textAnalysis).append("\n\n");
        }

        // Анализ речи
        if (speechAnalysis != null) {
            response.append("## 🎤 Анализ речи:\n\n");

            // Используем EnhancedSpeechAnalysis если доступен
            if (speechAnalysis instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
                response.append("### Общая оценка: **")
                        .append(String.format("%.1f", enhancedAnalysis.getOverallScore()))
                        .append("**/100\n\n");

                response.append("### Детальная оценка:\n");
                response.append("• Произношение: **")
                        .append(String.format("%.1f", enhancedAnalysis.getPronunciationScore()))
                        .append("**/100\n");
                response.append("• Беглость: **")
                        .append(String.format("%.1f", enhancedAnalysis.getFluencyScore()))
                        .append("**/100\n");
                response.append("• Интонация: **")
                        .append(String.format("%.1f", enhancedAnalysis.getIntonationScore()))
                        .append("**/100\n");
                response.append("• Громкость: **")
                        .append(String.format("%.1f", enhancedAnalysis.getVolumeScore()))
                        .append("**/100\n");
                response.append("• Четкость: **")
                        .append(String.format("%.1f", enhancedAnalysis.getClarityScore()))
                        .append("**/100\n");
                response.append("• Уверенность: **")
                        .append(String.format("%.1f", enhancedAnalysis.getConfidenceScore()))
                        .append("**/100\n\n");

                response.append("### Статистика:\n");
                response.append("• Скорость речи: **")
                        .append(String.format("%.1f", enhancedAnalysis.getSpeakingRate()))
                        .append("** слов/мин\n");
                response.append("• Паузы: **")
                        .append(enhancedAnalysis.getPauseCount())
                        .append("** (")
                        .append(String.format("%.1f", enhancedAnalysis.getTotalPauseDuration()))
                        .append(" сек)\n");
                response.append("• Уровень владения: **")
                        .append(enhancedAnalysis.getProficiencyLevel())
                        .append("**\n\n");
            } else {
                response.append(speechAnalysis.getSummary()).append("\n\n");
            }

            // Рекомендации из анализа
            if (!speechAnalysis.getRecommendations().isEmpty()) {
                response.append("## 💡 Общие рекомендации:\n\n");
                for (String rec : speechAnalysis.getRecommendations()) {
                    response.append("• ").append(rec).append("\n");
                }
                response.append("\n");
            }

            // Обнаруженные ошибки (если есть)
            if (speechAnalysis instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
                if (!enhancedAnalysis.getDetectedErrors().isEmpty()) {
                    response.append("## ❌ Обнаруженные ошибки:\n\n");
                    for (String error : enhancedAnalysis.getDetectedErrors()) {
                        response.append("• ").append(error).append("\n");
                    }
                    response.append("\n");
                }
            }
        }

        // Персонализированные рекомендации
        if (personalizedRecommendations != null && !personalizedRecommendations.isEmpty()) {
            response.append("## 🎯 ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ:\n\n");

            for (RecommendationEngine.PersonalizedRecommendation rec : personalizedRecommendations) {
                response.append("### ").append(rec.getTitle()).append("\n");
                response.append("**Приоритет:** ").append(rec.getPriority()).append("\n");
                response.append("**Описание:** ").append(rec.getDescription()).append("\n");
                response.append("**Ожидаемое улучшение:** ")
                        .append(String.format("%.1f", rec.getExpectedImprovement()))
                        .append("%\n");
                response.append("**Упражнения:**\n");
                for (String exercise : rec.getExercises()) {
                    response.append("• ").append(exercise).append("\n");
                }
                response.append("\n");
            }
        }

        // Недельный план обучения
        if (weeklyPlan != null) {
            response.append("## 📅 НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ:\n\n");
            response.append("**Целевой уровень:** ").append(weeklyPlan.getTargetLevel()).append("\n");
            response.append("**Ожидаемое улучшение:** ")
                    .append(String.format("%.1f", weeklyPlan.getExpectedImprovement()))
                    .append(" пунктов\n");
            response.append("**Цель недели:** ").append(weeklyPlan.getWeeklyGoal()).append("\n\n");

            response.append("**Расписание:**\n");
            for (RecommendationEngine.DailySchedule day : weeklyPlan.getSchedule()) {
                response.append("**").append(day.getDay()).append(":**\n");
                response.append("  Фокус: ").append(day.getFocus()).append("\n");
                response.append("  Время: ").append(day.getDurationMinutes()).append(" минут\n");
                response.append("  Упражнения: ");
                if (day.getExercises() != null && !day.getExercises().isEmpty()) {
                    response.append(String.join(", ", day.getExercises()));
                }
                response.append("\n");
                if (day.getTips() != null && !day.getTips().isEmpty()) {
                    response.append("  Совет: ").append(day.getTips().get(0)).append("\n");
                }
                response.append("\n");
            }
        }

        // Генерация упражнения (случайная тема) - если нет персонализированных
        if (personalizedRecommendations == null || personalizedRecommendations.isEmpty()) {
            String[] topics = {"Present Simple", "Past Continuous", "Future Tenses",
                    "Conditionals", "Phrasal Verbs", "Idioms"};
            String randomTopic = topics[(int)(Math.random() * topics.length)];

            response.append("## 🎯 Упражнение для практики:\n\n");
            response.append("**Тема:** ").append(randomTopic).append("\n\n");
            response.append("**Задание:** Составьте 5 предложений на эту тему, используя:\n");
            response.append("1. Разные времена глаголов\n");
            response.append("2. Новые слова из нашего диалога\n");
            response.append("3. Разнообразные грамматические конструкции\n\n");
            response.append("*Совет: Запишите свою речь и проанализируйте произношение!*");
        }

        // Добавляем подсказку о режиме
        if (responseMode == ChatBotController.ResponseMode.VOICE) {
            response.append("\n\n---\n");
            response.append("ℹ️ *Этот ответ был озвучен. Вы можете переключиться на текстовый режим в настройках.*");
        }

        return response.toString();
    }

    // Метод для озвучки любого текста (используется контроллером)
    public CompletableFuture<Void> speakTextAsync(String text) {
        if (textToSpeechService != null && !closed) {
            try {
                String cleanText = cleanTextForSpeech(text);
                if (!cleanText.trim().isEmpty()) {
                    return textToSpeechService.speakAsync(cleanText);
                } else {
                    logger.warn("Пустой текст для озвучки");
                    return CompletableFuture.completedFuture(null);
                }
            } catch (Exception e) {
                logger.error("Ошибка при подготовке текста для озвучки: {}", e.getMessage(), e);
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new RuntimeException("Ошибка подготовки текста: " + e.getMessage(), e));
                return future;
            }
        } else {
            throw new IllegalStateException("TTS сервис недоступен");
        }
    }

    // Синхронная версия для обратной совместимости
    public void speakText(String text) {
        if (textToSpeechService != null && !closed) {
            try {
                String cleanText = cleanTextForSpeech(text);
                if (!cleanText.trim().isEmpty()) {
                    textToSpeechService.speak(cleanText);
                } else {
                    logger.warn("Пустой текст для озвучки");
                }
            } catch (Exception e) {
                logger.error("Ошибка при озвучке текста: {}", e.getMessage(), e);
                // В демо-режиме не бросаем исключение
                if (!(e instanceof IllegalStateException)) {
                    throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
                }
            }
        } else {
            throw new IllegalStateException("TTS сервис недоступен");
        }
    }

    // Метод для остановки текущей озвучки
    public void stopSpeaking() {
        if (textToSpeechService != null) {
            try {
                textToSpeechService.stopSpeaking();
                logger.info("Озвучка остановлена");
            } catch (Exception e) {
                logger.error("Ошибка при остановке озвучки", e);
            }
        }
    }

    public boolean isTTSAvailable() {
        return textToSpeechService != null && textToSpeechService.isAvailable();
    }

    public String getTTSStatus() {
        if (textToSpeechService == null) {
            return "TTS сервис не инициализирован";
        }

        if (textToSpeechService.isAvailable()) {
            return "✅ Системный TTS доступен";
        } else {
            return "⚠️ TTS работает в демо-режиме";
        }
    }

    // Метод для получения персонализированных рекомендаций отдельно
    public List<RecommendationEngine.PersonalizedRecommendation> getPersonalizedRecommendations(
            EnhancedSpeechAnalysis analysis) {
        return recommendationEngine.generateRecommendations(analysis);
    }

    // Метод для получения недельного плана отдельно
    public RecommendationEngine.WeeklyLearningPlan getWeeklyLearningPlan(
            EnhancedSpeechAnalysis analysis) {
        return recommendationEngine.generateWeeklyPlan(analysis);
    }

    public List<Conversation> getConversationHistory() {
        try {
            return conversationDao.getConversationsByUser(currentUser.getId());
        } catch (Exception e) {
            logger.error("Ошибка при получении истории разговоров", e);
            return List.of();
        }
    }

    public void clearHistory() {
        try {
            // Используем новый метод для удаления всех разговоров пользователя
            conversationDao.deleteConversationsByUser(currentUser.getId());
            logger.info("История разговоров очищена для пользователя: {}", currentUser.getUsername());
        } catch (Exception e) {
            logger.error("Ошибка при очистке истории", e);
        }
    }

    public String generateAudioFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());

        File recordingsDir = new File("recordings");
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs();
        }

        return "recordings/audio_" + timestamp + ".wav";
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isAiServiceAvailable() {
        return aiService != null && aiService.isAvailable();
    }

    public AudioAnalyzer getAudioAnalyzer() {
        return audioAnalyzer;
    }

    public PronunciationTrainer getPronunciationTrainer() {
        return pronunciationTrainer;
    }

    public RecommendationEngine getRecommendationEngine() {
        return recommendationEngine;
    }

    public TextToSpeechService getTextToSpeechService() {
        return textToSpeechService;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        logger.info("Закрытие ChatBotService...");
        closed = true;

        try {
            if (speechToTextService != null) {
                try {
                    speechToTextService.close();
                } catch (Exception e) {
                    logger.error("Ошибка при закрытии SpeechToTextService", e);
                }
            }

            if (textToSpeechService != null) {
                try {
                    textToSpeechService.close();
                } catch (Exception e) {
                    logger.error("Ошибка при закрытии DemoTextToSpeechService", e);
                }
            }

            if (pronunciationTrainer != null && pronunciationTrainer instanceof Closeable) {
                try {
                    ((Closeable) pronunciationTrainer).close();
                } catch (Exception e) {
                    logger.error("Ошибка при закрытии PronunciationTrainer", e);
                }
            }

            if (aiService instanceof Closeable) {
                try {
                    ((Closeable) aiService).close();
                } catch (Exception e) {
                    logger.error("Ошибка при закрытии AI сервиса", e);
                }
            }

            logger.info("ChatBotService закрыт");

        } catch (Exception e) {
            logger.error("Ошибка при закрытии ChatBotService", e);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public ChatResponse processUserInputSafe(String text, String audioFilePath) {
        if (closed) {
            throw new IllegalStateException("ChatBotService закрыт");
        }
        return processUserInput(text, audioFilePath);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                logger.warn("ChatBotService не был закрыт явно, вызываем close() в finalize()");
                close();
            }
        } finally {
            super.finalize();
        }
    }

    public SpeechToTextService getSpeechToTextService() {
        return speechToTextService;
    }

    public void testMicrophone(int durationSeconds) {
        if (speechToTextService != null) {
            speechToTextService.testMicrophone(durationSeconds);
        } else {
            logger.warn("SpeechToTextService не инициализирован для теста микрофона");
        }
    }

    public void setMicrophoneSensitivity(double sensitivity) {
        if (speechToTextService != null) {
            speechToTextService.setMicrophoneSensitivity(sensitivity);
        } else {
            logger.warn("SpeechToTextService не инициализирован для установки чувствительности");
        }
    }

    public double getMicrophoneSensitivity() {
        if (speechToTextService != null) {
            return speechToTextService.getMicrophoneSensitivity();
        }
        return 0.5; // Значение по умолчанию
    }

    public String recognizeSpeechInRealTime() {
        SpeechRecorder recorder = null;

        try {
            logger.info("Начало распознавания речи в реальном времени...");

            // Генерируем имя временного файла
            String tempFilePath = generateAudioFileName();
            logger.info("Временный аудиофайл: {}", tempFilePath);

            // Создаем новый рекордер для этой сессии
            recorder = new SpeechRecorder();

            // Начинаем запись
            recorder.startRecording();
            logger.info("Запись начата... Говорите...");

            // Ждем 3 секунды для записи речи
            Thread.sleep(3000);

            // Останавливаем запись
            File audioFile = recorder.stopRecording(tempFilePath);

            if (audioFile != null && audioFile.exists()) {
                long fileSize = audioFile.length();
                logger.info("Запись завершена. Размер файла: {} байт", fileSize);

                // Проверяем, что файл не пустой
                if (fileSize > 44) { // WAV файл должен быть больше заголовка
                    // Распознаем речь - используем getAbsolutePath() у File
                    SpeechToTextService.SpeechRecognitionResult result =
                            speechToTextService.transcribe(audioFile.getAbsolutePath());

                    String recognizedText = result.getText();
                    logger.info("Распознанный текст: {}, уверенность: {:.1f}%",
                            recognizedText, result.getConfidence() * 100);

                    // Удаляем временный файл
                    try {
                        if (audioFile.delete()) {
                            logger.debug("Временный файл удален: {}", tempFilePath);
                        }
                    } catch (Exception e) {
                        logger.warn("Не удалось удалить временный файл: {}", e.getMessage());
                    }

                    return recognizedText;
                } else {
                    logger.warn("Аудиофайл слишком мал или пустой: {} байт", fileSize);
                    return "";
                }
            } else {
                logger.warn("Аудиофайл не был создан");
                return "";
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Распознавание речи прервано", e);
            throw new RuntimeException("Распознавание речи прервано", e);
        } catch (Exception e) {
            logger.error("Ошибка при распознавании речи в реальном времени", e);
            throw new RuntimeException("Ошибка распознавания речи: " + e.getMessage(), e);
        } finally {
            // Закрываем рекордер
            if (recorder != null) {
                try {
                    recorder.close();
                } catch (Exception e) {
                    logger.warn("Ошибка при закрытии рекордера: {}", e.getMessage());
                }
            }
        }
    }

    // Вспомогательный класс для ответа
    public static class ChatResponse {
        private final String fullResponse;
        private final SpeechAnalysis speechAnalysis;
        private final List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations;
        private final RecommendationEngine.WeeklyLearningPlan weeklyPlan;

        public ChatResponse(String fullResponse, SpeechAnalysis speechAnalysis,
                            List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations,
                            RecommendationEngine.WeeklyLearningPlan weeklyPlan) {
            this.fullResponse = fullResponse;
            this.speechAnalysis = speechAnalysis;
            this.personalizedRecommendations = personalizedRecommendations;
            this.weeklyPlan = weeklyPlan;
        }

        public String getFullResponse() {
            return fullResponse;
        }

        public SpeechAnalysis getSpeechAnalysis() {
            return speechAnalysis;
        }

        public List<RecommendationEngine.PersonalizedRecommendation> getPersonalizedRecommendations() {
            return personalizedRecommendations;
        }

        public RecommendationEngine.WeeklyLearningPlan getWeeklyPlan() {
            return weeklyPlan;
        }
    }
}