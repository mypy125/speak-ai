package com.mygitgor.chatbot.components;

import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.service.ChatBotService;
import com.mygitgor.config.AppConstants;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.state.ChatBotState;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AnalysisManager {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisManager.class);

    // UI Elements
    private final TextArea analysisArea;
    private final TextArea detailedAnalysisArea;
    private final TextArea recommendationsArea;
    private final Button analyzeButton;
    private final Button pronunciationButton;
    private final ProgressBar analysisProgress;
    private final Label phonemeLabel;
    private final Label statusLabel;

    // Dependencies
    private final AudioAnalyzer audioAnalyzer;
    private final PronunciationTrainer pronunciationTrainer;
    private final ChatBotService chatBotService;
    private final ChatBotState state;
    private final Stage stage;

    public AnalysisManager(
            TextArea analysisArea,
            TextArea detailedAnalysisArea,
            TextArea recommendationsArea,
            Button analyzeButton,
            Button pronunciationButton,
            ProgressBar analysisProgress,
            Label phonemeLabel,
            Label statusLabel,
            AudioAnalyzer audioAnalyzer,
            PronunciationTrainer pronunciationTrainer,
            ChatBotService chatBotService,
            ChatBotState state,
            Stage stage) {

        this.analysisArea = analysisArea;
        this.detailedAnalysisArea = detailedAnalysisArea;
        this.recommendationsArea = recommendationsArea;
        this.analyzeButton = analyzeButton;
        this.pronunciationButton = pronunciationButton;
        this.analysisProgress = analysisProgress;
        this.phonemeLabel = phonemeLabel;
        this.statusLabel = statusLabel;
        this.audioAnalyzer = audioAnalyzer;
        this.pronunciationTrainer = pronunciationTrainer;
        this.chatBotService = chatBotService;
        this.state = state;
        this.stage = stage;

        initializeUI();
    }

    private void initializeUI() {
        if (analyzeButton != null) analyzeButton.setDisable(true);
        if (pronunciationButton != null) pronunciationButton.setDisable(true);
        if (analysisProgress != null) analysisProgress.setVisible(false);
        if (detailedAnalysisArea != null) detailedAnalysisArea.setVisible(false);
        if (phonemeLabel != null) phonemeLabel.setVisible(false);
    }

    public void analyzeCurrentAudio(String userMessage) {
        String audioFile = state.getCurrentAudioFile();

        if (audioFile == null) {
            ErrorHandler.showWarning("Внимание", "Сначала запишите аудио");
            return;
        }

        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        showAnalysisInProgress(true);

        CompletableFuture.runAsync(() -> {
            try {
                String text = userMessage != null && !userMessage.isEmpty()
                        ? userMessage
                        : "[Аудиосообщение]";

                EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(audioFile, text);

                Platform.runLater(() -> {
                    updateAnalysisUI(analysis);
                    showAnalysisInProgress(false);
                    state.incrementAnalysisCount();

                    logger.info("Анализ аудио завершен. Общий балл: {}/100",
                            analysis.getOverallScore());
                });

            } catch (Exception e) {
                logger.error("Ошибка анализа аудио", e);
                Platform.runLater(() -> {
                    ErrorHandler.showError("Ошибка анализа", e.getMessage());
                    showAnalysisInProgress(false);
                });
            }
        });
    }

    public void processAnalysisResponse(ChatBotService.ChatResponse response) {
        if (response.getSpeechAnalysis() instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
            updateAnalysisUI(enhancedAnalysis);
            updateRecommendations(response);
            state.incrementAnalysisCount();

            showCompletionNotification(enhancedAnalysis);
        }
    }

    private void updateAnalysisUI(EnhancedSpeechAnalysis analysis) {
        if (analysisArea != null) {
            analysisArea.setText(analysis.getSummary());
        }

        if (detailedAnalysisArea != null &&
                analysis.getDetailedReport() != null &&
                !analysis.getDetailedReport().isEmpty()) {
            detailedAnalysisArea.setText(analysis.getDetailedReport());
            detailedAnalysisArea.setVisible(true);
        }

        updatePhonemeLabel(analysis);

        if (analyzeButton != null) analyzeButton.setDisable(false);
        if (pronunciationButton != null) {
            pronunciationButton.setDisable(false);
            updatePronunciationButtonText(analysis);
        }
    }

    private void updatePhonemeLabel(EnhancedSpeechAnalysis analysis) {
        if (phonemeLabel == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            String worstPhoneme = analysis.getPhonemeScores().entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(entry -> "/" + entry.getKey() + "/")
                    .orElse("");

            if (!worstPhoneme.isEmpty()) {
                phonemeLabel.setText("Слабая фонема: " + worstPhoneme);
                phonemeLabel.setVisible(true);
            }
        }
    }

    private void updatePronunciationButtonText(EnhancedSpeechAnalysis analysis) {
        if (pronunciationButton == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            long weakPhonemesCount = analysis.getPhonemeScores().values().stream()
                    .filter(score -> score < AppConstants.WEAK_PHONEME_THRESHOLD)
                    .count();

            if (weakPhonemesCount > 0) {
                pronunciationButton.setText("🗣️ Тренажер (" + weakPhonemesCount + " проблем)");
            } else {
                pronunciationButton.setText("🗣️ Тренажер произношения");
            }
        }
    }

    private void updateRecommendations(ChatBotService.ChatResponse response) {
        if (recommendationsArea == null) return;

        StringBuilder recommendationsText = new StringBuilder();

        // Base recommendations
        if (response.getSpeechAnalysis() != null &&
                !response.getSpeechAnalysis().getRecommendations().isEmpty()) {
            recommendationsText.append("📋 РЕКОМЕНДАЦИИ:\n\n");
            for (String rec : response.getSpeechAnalysis().getRecommendations()) {
                recommendationsText.append("• ").append(rec).append("\n");
            }
        }

        // Personalized recommendations
        if (response.getPersonalizedRecommendations() != null &&
                !response.getPersonalizedRecommendations().isEmpty()) {

            recommendationsText.append("\n🎯 ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ:\n\n");

            response.getPersonalizedRecommendations().forEach(rec -> {
                recommendationsText.append("[").append(rec.getPriority()).append("] ")
                        .append(rec.getTitle()).append("\n");
                recommendationsText.append(rec.getDescription()).append("\n");
                recommendationsText.append("Упражнения:\n");
                rec.getExercises().stream().limit(3).forEach(ex ->
                        recommendationsText.append("  • ").append(ex).append("\n"));
                recommendationsText.append("\n");
            });
        }

        if (recommendationsText.length() > 0) {
            recommendationsArea.setText(recommendationsText.toString());
        }
    }

    public void showPronunciationTrainer() {
        String audioFile = state.getCurrentAudioFile();

        if (audioFile == null) {
            ErrorHandler.showWarning("Внимание",
                    "Сначала запишите аудио и проанализируйте его");
            return;
        }

        try {
            String text = "Аудиосообщение"; // Default text
            EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(audioFile, text);

            if (analysis.getPhonemeScores() != null &&
                    !analysis.getPhonemeScores().isEmpty()) {
                showPronunciationExercises(analysis);
            } else {
                ErrorHandler.showWarning("Данные не найдены",
                        "Не удалось получить данные о фонемах");
            }

        } catch (Exception e) {
            logger.error("Ошибка при создании тренажера произношения", e);
            ErrorHandler.showError("Ошибка",
                    "Не удалось создать тренажер: " + e.getMessage());
        }
    }

    private void showPronunciationExercises(EnhancedSpeechAnalysis analysis) {
        List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                .filter(e -> e.getValue() < AppConstants.TRAINER_PHONEME_THRESHOLD)
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .collect(Collectors.toList());

        if (weakPhonemes.isEmpty()) {
            ErrorHandler.showInfo("Отличное произношение!",
                    "У вас нет проблемных звуков! Все фонемы оценены выше 80 баллов.");
            return;
        }

        StringBuilder exercisesText = new StringBuilder();
        exercisesText.append("ТРЕНАЖЕР ПРОИЗНОШЕНИЯ\n");
        exercisesText.append("=".repeat(50)).append("\n\n");

        for (Map.Entry<String, Float> phonemeEntry : weakPhonemes) {
            String phoneme = phonemeEntry.getKey();
            float score = phonemeEntry.getValue();

            String difficulty = score < 60 ? "beginner" :
                    score < 75 ? "intermediate" : "advanced";

            PronunciationTrainer.PronunciationExercise exercise =
                    pronunciationTrainer.createExercise(phoneme, difficulty);

            exercisesText.append("ЗВУК /").append(phoneme).append("/\n");
            exercisesText.append("Оценка: ").append(String.format("%.1f", score))
                    .append("/100\n\n");
            exercisesText.append("📝 Инструкции:\n")
                    .append(exercise.getInstructions()).append("\n\n");
            exercisesText.append("📚 Примеры:\n");
            exercise.getExamples().forEach(ex ->
                    exercisesText.append("• ").append(ex).append("\n"));
            exercisesText.append("\n💡 Советы:\n");
            exercise.getTips().forEach(tip ->
                    exercisesText.append("• ").append(tip).append("\n"));
            exercisesText.append("\n" + "=".repeat(50) + "\n\n");
        }

        showExercisesWindow(exercisesText.toString());
    }

    private void showExercisesWindow(String content) {
        TextArea trainingArea = new TextArea(content);
        trainingArea.setEditable(false);
        trainingArea.setWrapText(true);
        trainingArea.setPrefSize(800, 600);
        trainingArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        ScrollPane scrollPane = new ScrollPane(trainingArea);
        scrollPane.setFitToWidth(true);

        Stage trainingStage = new Stage();
        trainingStage.setTitle("Тренажер произношения");
        if (stage != null) {
            trainingStage.initOwner(stage);
        }

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 820, 650);
        trainingStage.setScene(scene);
        trainingStage.show();
    }

    private void showAnalysisInProgress(boolean inProgress) {
        Platform.runLater(() -> {
            if (analysisProgress != null) {
                analysisProgress.setVisible(inProgress);
            }
            if (detailedAnalysisArea != null && inProgress) {
                detailedAnalysisArea.setVisible(true);
            }
            if (statusLabel != null) {
                statusLabel.setText(inProgress ?
                        "🔍 Анализ речи..." :
                        state.isAiServiceAvailable() ? "✅ ИИ-сервис доступен" : "⚠️ Демо-режим");
            }
        });
    }

    private void showCompletionNotification(EnhancedSpeechAnalysis analysis) {
        float overallScore = (float) analysis.getOverallScore();
        String message;
        String emoji;

        if (overallScore >= AppConstants.EXCELLENT_SCORE_THRESHOLD) {
            message = "Великолепно! Продвинутый уровень!";
            emoji = "🏆";
        } else if (overallScore >= AppConstants.GOOD_SCORE_THRESHOLD) {
            message = "Отличный результат!";
            emoji = "🎉";
        } else if (overallScore >= AppConstants.AVERAGE_SCORE_THRESHOLD) {
            message = "Хорошая работа!";
            emoji = "👍";
        } else if (overallScore >= AppConstants.FAIR_SCORE_THRESHOLD) {
            message = "Неплохо! Есть прогресс.";
            emoji = "💪";
        } else {
            message = "Нужна регулярная практика.";
            emoji = "📚";
        }

        if (statusLabel != null) {
            statusLabel.setText(String.format("✅ Анализ: %.1f/100 %s %s",
                    overallScore, emoji, message));
        }
    }

    public void clear() {
        if (analysisArea != null) analysisArea.clear();
        if (detailedAnalysisArea != null) {
            detailedAnalysisArea.clear();
            detailedAnalysisArea.setVisible(false);
        }
        if (recommendationsArea != null) recommendationsArea.clear();
        if (phonemeLabel != null) {
            phonemeLabel.setVisible(false);
            phonemeLabel.setText("");
        }
        if (analyzeButton != null) analyzeButton.setDisable(true);
        if (pronunciationButton != null) {
            pronunciationButton.setDisable(true);
            pronunciationButton.setText("🗣️ Тренажер произношения");
        }
    }
}
