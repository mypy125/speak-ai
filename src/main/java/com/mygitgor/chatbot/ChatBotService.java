package com.mygitgor.chatbot;

import com.mygitgor.ai.AiService;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.model.User;
import com.mygitgor.repository.DAO.ConversationDao;
import com.mygitgor.speech.AudioAnalyzer;
import com.mygitgor.speech.SpeechToTextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ChatBotService {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotService.class);

    private final AiService aiService;
    private final ConversationDao conversationDao;
    private final AudioAnalyzer audioAnalyzer;
    private final PronunciationTrainer pronunciationTrainer;
    private final RecommendationEngine recommendationEngine;
    private final SpeechToTextService speechToTextService;
    private User currentUser;

    public ChatBotService(AiService aiService, AudioAnalyzer audioAnalyzer,
                          PronunciationTrainer pronunciationTrainer) {
        this.aiService = aiService;
        this.audioAnalyzer = audioAnalyzer;
        this.pronunciationTrainer = pronunciationTrainer;
        this.recommendationEngine = new RecommendationEngine();
        this.conversationDao = new ConversationDao();

        // Инициализация сервиса распознавания речи
        this.speechToTextService = new SpeechToTextService(
                SpeechToTextService.ServiceType.MOCK,
                "" // В реальном приложении нужно добавить API ключ
        );

        // Временный пользователь для MVP
        this.currentUser = createDefaultUser();
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
        try {
            logger.info("Обработка пользовательского ввода. Текст: {}, Аудио: {}",
                    text, audioFilePath);

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

            // 7. Сохранение в историю
            saveConversation(recognizedText, botResponse, audioFilePath, speechAnalysis);

            // 8. Формирование полного ответа
            String fullResponse = formatResponse(botResponse, textAnalysis,
                    speechAnalysis, personalizedRecommendations, weeklyPlan);

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

    private void saveConversation(String userMessage, String botResponse,
                                  String audioPath, SpeechAnalysis analysis) {
        Conversation conversation = new Conversation(currentUser, userMessage, botResponse);
        conversation.setAudioPath(audioPath);

        if (analysis != null) {
            conversation.setAnalysisResult(analysis.getSummary());

            if (!analysis.getRecommendations().isEmpty()) {
                conversation.setRecommendations(String.join("\n", analysis.getRecommendations()));
            }

            // Используем EnhancedSpeechAnalysis если доступен
            if (analysis instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
                conversation.setPronunciationScore((float) enhancedAnalysis.getPronunciationScore());
                conversation.setGrammarScore((float) enhancedAnalysis.getGrammarScore());
                conversation.setVocabularyScore((float) enhancedAnalysis.getVocabularyScore());
            } else {
                conversation.setPronunciationScore((float) analysis.getPronunciationScore());
                conversation.setGrammarScore((float) analysis.getGrammarScore());
                conversation.setVocabularyScore((float) analysis.getVocabularyScore());
            }
        }

        try {
            conversationDao.createConversation(conversation);
            logger.debug("Разговор сохранен в базу данных. ID: {}", conversation.getId());
        } catch (Exception e) {
            logger.error("Ошибка при сохранении разговора в БД", e);
        }
    }

    private String formatResponse(String botResponse, String textAnalysis,
                                  SpeechAnalysis speechAnalysis,
                                  List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations,
                                  RecommendationEngine.WeeklyLearningPlan weeklyPlan) {
        StringBuilder response = new StringBuilder();

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

        return response.toString();
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
            return conversationDao.getConversationsByUser(currentUser);
        } catch (Exception e) {
            logger.error("Ошибка при получении истории разговоров", e);
            return List.of();
        }
    }

    public void clearHistory() {
        try {
            // Используем новый метод для удаления всех разговоров пользователя
            conversationDao.deleteConversationsByUser(currentUser);
            logger.info("История разговоров очищена для пользователя: {}", currentUser.getUsername());
        } catch (Exception e) {
            logger.error("Ошибка при очистке истории", e);
        }
    }

    public String generateAudioFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());

        // Создаем директорию если не существует
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