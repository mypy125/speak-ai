package com.mygitgor.service.components;

import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.model.core.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class ResponseFormatter {
    private static final Logger logger = LoggerFactory.getLogger(ResponseFormatter.class);

    public enum ResponseMode {
        TEXT, VOICE
    }

    public String formatResponse(String botResponse,
                                 String textAnalysis,
                                 SpeechAnalysis speechAnalysis,
                                 List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations,
                                 RecommendationEngine.WeeklyLearningPlan weeklyPlan,
                                 ResponseMode responseMode,
                                 User user) {

        StringBuilder response = new StringBuilder();

        if (responseMode == ResponseMode.VOICE) {
            response.append("🔊 **Ответ озвучен**\n\n");
        }

        response.append("## 🤖 AI Репетитор:\n\n");
        response.append(botResponse).append("\n\n");
        appendTextAnalysis(response, textAnalysis);
        appendSpeechAnalysis(response, speechAnalysis);
        appendPersonalizedRecommendations(response, personalizedRecommendations);
        appendWeeklyPlan(response, weeklyPlan);
        appendDefaultExercise(response, personalizedRecommendations);
        appendModeHint(response, responseMode);

        return response.toString();
    }

    private void appendTextAnalysis(StringBuilder response, String textAnalysis) {
        if (textAnalysis != null && !textAnalysis.trim().isEmpty()) {
            response.append("## 📝 Анализ текста:\n\n");
            response.append(textAnalysis).append("\n\n");
        }
    }

    private void appendSpeechAnalysis(StringBuilder response, SpeechAnalysis speechAnalysis) {
        if (speechAnalysis == null) return;

        response.append("## 🎤 Анализ речи:\n\n");

        if (speechAnalysis instanceof EnhancedSpeechAnalysis enhanced) {
            appendEnhancedAnalysis(response, enhanced);
        } else {
            response.append(speechAnalysis.getSummary()).append("\n\n");
        }

        if (!speechAnalysis.getRecommendations().isEmpty()) {
            response.append("### 💡 Рекомендации:\n\n");
            speechAnalysis.getRecommendations().forEach(rec ->
                    response.append("• ").append(rec).append("\n"));
            response.append("\n");
        }
    }

    private void appendEnhancedAnalysis(StringBuilder response, EnhancedSpeechAnalysis analysis) {
        response.append("### Общая оценка: **")
                .append(String.format("%.1f", analysis.getOverallScore()))
                .append("**/100\n\n");

        response.append("### Детальная оценка:\n");
        appendScore(response, "Произношение", analysis.getPronunciationScore());
        appendScore(response, "Беглость", analysis.getFluencyScore());
        appendScore(response, "Интонация", analysis.getIntonationScore());
        appendScore(response, "Громкость", analysis.getVolumeScore());
        appendScore(response, "Четкость", analysis.getClarityScore());
        appendScore(response, "Уверенность", analysis.getConfidenceScore());
        response.append("\n");

        response.append("### Статистика:\n");
        response.append("• Скорость речи: **")
                .append(String.format("%.1f", analysis.getSpeakingRate()))
                .append("** слов/мин\n");
        response.append("• Паузы: **").append(analysis.getPauseCount())
                .append("** (").append(String.format("%.1f", analysis.getTotalPauseDuration()))
                .append(" сек)\n");
        response.append("• Уровень владения: **")
                .append(analysis.getProficiencyLevel()).append("**\n\n");

        if (!analysis.getDetectedErrors().isEmpty()) {
            response.append("### ❌ Обнаруженные ошибки:\n\n");
            analysis.getDetectedErrors().forEach(error ->
                    response.append("• ").append(error).append("\n"));
            response.append("\n");
        }
    }

    private void appendScore(StringBuilder response, String label, double score) {
        response.append("• ").append(label).append(": **")
                .append(String.format("%.1f", score)).append("**/100\n");
    }

    private void appendPersonalizedRecommendations(StringBuilder response,
                                                   List<RecommendationEngine.PersonalizedRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) return;

        response.append("## 🎯 ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ:\n\n");

        recommendations.forEach(rec -> {
            response.append("### ").append(rec.getTitle()).append("\n");
            response.append("**Приоритет:** ").append(rec.getPriority()).append("\n");
            response.append("**Описание:** ").append(rec.getDescription()).append("\n");
            response.append("**Ожидаемое улучшение:** ")
                    .append(String.format("%.1f", rec.getExpectedImprovement()))
                    .append("%\n");
            response.append("**Упражнения:**\n");
            rec.getExercises().forEach(ex -> response.append("• ").append(ex).append("\n"));
            response.append("\n");
        });
    }

    private void appendWeeklyPlan(StringBuilder response,
                                  RecommendationEngine.WeeklyLearningPlan plan) {
        if (plan == null) return;

        response.append("## 📅 НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ:\n\n");
        response.append("**Целевой уровень:** ").append(plan.getTargetLevel()).append("\n");
        response.append("**Ожидаемое улучшение:** ")
                .append(String.format("%.1f", plan.getExpectedImprovement()))
                .append(" пунктов\n");
        response.append("**Цель недели:** ").append(plan.getWeeklyGoal()).append("\n\n");

        response.append("**Расписание:**\n");
        plan.getSchedule().forEach(day -> {
            response.append("**").append(day.getDay()).append(":**\n");
            response.append("  Фокус: ").append(day.getFocus()).append("\n");
            response.append("  Время: ").append(day.getDurationMinutes()).append(" мин\n");

            if (day.getExercises() != null && !day.getExercises().isEmpty()) {
                response.append("  Упражнения: ")
                        .append(String.join(", ", day.getExercises()))
                        .append("\n");
            }

            if (day.getTips() != null && !day.getTips().isEmpty()) {
                response.append("  Совет: ").append(day.getTips().get(0)).append("\n");
            }
            response.append("\n");
        });
    }

    private void appendDefaultExercise(StringBuilder response,
                                       List<RecommendationEngine.PersonalizedRecommendation> recommendations) {
        if (recommendations != null && !recommendations.isEmpty()) return;

        String[] topics = {"Present Simple", "Past Continuous", "Future Tenses",
                "Conditionals", "Phrasal Verbs", "Idioms"};
        String randomTopic = topics[(int)(Math.random() * topics.length)];

        response.append("## 🎯 Упражнение для практики:\n\n");
        response.append("**Тема:** ").append(randomTopic).append("\n\n");
        response.append("**Задание:** Составьте 5 предложений на эту тему, используя:\n");
        response.append("1. Разные времена глаголов\n");
        response.append("2. Новые слова из нашего диалога\n");
        response.append("3. Разнообразные грамматические конструкции\n\n");
        response.append("*Совет: Запишите свою речь и проанализируйте произношение!*\n");
    }

    private void appendModeHint(StringBuilder response, ResponseMode mode) {
        if (mode == ResponseMode.VOICE) {
            response.append("\n---\n");
            response.append("ℹ️ *Этот ответ был озвучен. Вы можете переключиться на текстовый режим в настройках.*");
        }
    }
}
