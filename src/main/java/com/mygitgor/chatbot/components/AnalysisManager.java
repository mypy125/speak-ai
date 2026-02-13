package com.mygitgor.chatbot.components;

import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.service.ChatBotService;
import com.mygitgor.config.AppConstants;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.state.ChatBotState;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AnalysisManager {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisManager.class);

    private static final int ANALYSIS_TIMEOUT_SECONDS = 30;
    private static final int MAX_WEAK_PHONEMES_TO_SHOW = 3;
    private static final int EXERCISE_WINDOW_WIDTH = 820;
    private static final int EXERCISE_WINDOW_HEIGHT = 650;

    private final TextArea analysisArea;
    private final TextArea detailedAnalysisArea;
    private final TextArea recommendationsArea;
    private final Button analyzeButton;
    private final Button pronunciationButton;
    private final ProgressBar analysisProgress;
    private final Label phonemeLabel;
    private final Label statusLabel;

    private final AudioAnalyzer audioAnalyzer;
    private final PronunciationTrainer pronunciationTrainer;
    private final ChatBotService chatBotService;
    private final ChatBotState state;
    private final Stage stage;

    private final AtomicBoolean isAnalyzing = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> currentAnalysis = new AtomicReference<>(null);
    private final AtomicReference<EnhancedSpeechAnalysis> lastAnalysis = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;
    private final ScheduledExecutorService scheduledExecutor;

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

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        initializeUI();
    }

    private void initializeUI() {
        Platform.runLater(() -> {
            if (analyzeButton != null) {
                analyzeButton.setDisable(true);
                analyzeButton.setStyle("-fx-background-color: #95a5a6;");
            }
            if (pronunciationButton != null) {
                pronunciationButton.setDisable(true);
                pronunciationButton.setStyle("-fx-background-color: #95a5a6;");
            }
            if (analysisProgress != null) {
                analysisProgress.setVisible(false);
                analysisProgress.setProgress(0);
            }
            if (detailedAnalysisArea != null) {
                detailedAnalysisArea.setVisible(false);
                detailedAnalysisArea.setEditable(false);
            }
            if (phonemeLabel != null) {
                phonemeLabel.setVisible(false);
                phonemeLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
            }
        });
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

        if (!isAnalyzing.compareAndSet(false, true)) {
            logger.warn("Анализ уже выполняется");
            ErrorHandler.showWarning("Внимание", "Анализ уже выполняется");
            return;
        }

        final String finalAudioFile = audioFile;
        final String finalUserMessage = userMessage != null ? userMessage : "";

        showAnalysisInProgress(true);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                String text = finalUserMessage.isEmpty() ? "[Аудиосообщение]" : finalUserMessage;

                logger.info("Начало анализа аудио: {}", finalAudioFile);
                EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(finalAudioFile, text);

                final EnhancedSpeechAnalysis finalAnalysis = analysis;

                Platform.runLater(() -> {
                    updateAnalysisUI(finalAnalysis);
                    lastAnalysis.set(finalAnalysis);
                    showAnalysisInProgress(false);
                    state.incrementAnalysisCount();

                    logger.info("✅ Анализ аудио завершен. Общий балл: {}/100",
                            String.format("%.1f", analysis.getOverallScore()));
                });

            } catch (Exception e) {
                logger.error("Ошибка анализа аудио", e);
                Platform.runLater(() -> {
                    ErrorHandler.showError("Ошибка анализа",
                            "Не удалось выполнить анализ: " + e.getMessage());
                    showAnalysisInProgress(false);
                });
            } finally {
                isAnalyzing.set(false);
            }
        }, backgroundExecutor);

        currentAnalysis.set(future);

        scheduledExecutor.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                isAnalyzing.set(false);
                Platform.runLater(() -> {
                    showAnalysisInProgress(false);
                    ErrorHandler.showWarning("Таймаут",
                            "Анализ занял слишком много времени");
                });
                logger.warn("Таймаут анализа аудио ({} сек)", ANALYSIS_TIMEOUT_SECONDS);
            }
        }, ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public void processAnalysisResponse(ChatBotService.ChatResponse response) {
        if (response == null) return;

        if (response.getSpeechAnalysis() instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
            Platform.runLater(() -> {
                updateAnalysisUI(enhancedAnalysis);
                updateRecommendations(response);
                state.incrementAnalysisCount();
                lastAnalysis.set(enhancedAnalysis);
                showCompletionNotification(enhancedAnalysis);
            });
        }
    }

    private void updateAnalysisUI(EnhancedSpeechAnalysis analysis) {
        if (analysis == null) return;

        if (analysisArea != null) {
            analysisArea.setText(formatAnalysisSummary(analysis));
            analysisArea.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px;");
        }

        if (detailedAnalysisArea != null) {
            String detailedReport = analysis.getDetailedReport();
            if (detailedReport != null && !detailedReport.isEmpty()) {
                detailedAnalysisArea.setText(formatDetailedReport(detailedReport));
                detailedAnalysisArea.setVisible(true);
            } else {
                detailedAnalysisArea.setVisible(false);
            }
        }

        updatePhonemeLabel(analysis);

        updateButtonsState(true);
        updatePronunciationButtonText(analysis);
    }

    private String formatAnalysisSummary(EnhancedSpeechAnalysis analysis) {
        return String.format("""
            📊 РЕЗУЛЬТАТ АНАЛИЗА РЕЧИ
            =========================
            
            Общая оценка: %.1f/100
            Уровень: %s
            
            Детальные оценки:
            • Произношение: %.1f/100
            • Беглость: %.1f/100
            • Интонация: %.1f/100
            • Громкость: %.1f/100
            • Четкость: %.1f/100
            • Уверенность: %.1f/100
            
            Статистика:
            • Скорость речи: %.1f слов/мин
            • Паузы: %d (%.1f сек)
            """,
                analysis.getOverallScore(),
                analysis.getProficiencyLevel(),
                analysis.getPronunciationScore(),
                analysis.getFluencyScore(),
                analysis.getIntonationScore(),
                analysis.getVolumeScore(),
                analysis.getClarityScore(),
                analysis.getConfidenceScore(),
                analysis.getSpeakingRate(),
                analysis.getPauseCount(),
                analysis.getTotalPauseDuration()
        );
    }

    private String formatDetailedReport(String report) {
        return "📋 ДЕТАЛЬНЫЙ АНАЛИЗ\n" +
                "===================\n\n" + report;
    }

    private void updatePhonemeLabel(EnhancedSpeechAnalysis analysis) {
        if (phonemeLabel == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            analysis.getPhonemeScores().entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(entry -> {
                        String phoneme = "/" + entry.getKey() + "/";
                        float score = entry.getValue();

                        String text = String.format("🔊 Слабая фонема: %s (%.1f/100)", phoneme, score);
                        phonemeLabel.setText(text);
                        phonemeLabel.setVisible(true);
                    });
        } else {
            phonemeLabel.setVisible(false);
        }
    }

    private void updatePronunciationButtonText(EnhancedSpeechAnalysis analysis) {
        if (pronunciationButton == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            long weakPhonemesCount = analysis.getPhonemeScores().values().stream()
                    .filter(score -> score < AppConstants.WEAK_PHONEME_THRESHOLD)
                    .count();

            if (weakPhonemesCount > 0) {
                pronunciationButton.setText("🎯 Тренажер (" + weakPhonemesCount + " проблем)");
            } else {
                pronunciationButton.setText("✅ Тренажер произношения");
            }
        }
    }

    private void updateRecommendations(ChatBotService.ChatResponse response) {
        if (recommendationsArea == null) return;

        StringBuilder recommendationsText = new StringBuilder();

        if (response.getSpeechAnalysis() != null &&
                !response.getSpeechAnalysis().getRecommendations().isEmpty()) {
            recommendationsText.append("📋 ОБЩИЕ РЕКОМЕНДАЦИИ\n");
            recommendationsText.append("====================\n\n");

            response.getSpeechAnalysis().getRecommendations().forEach(rec ->
                    recommendationsText.append("• ").append(rec).append("\n"));
            recommendationsText.append("\n");
        }

        if (response.getPersonalizedRecommendations() != null &&
                !response.getPersonalizedRecommendations().isEmpty()) {

            recommendationsText.append("🎯 ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ\n");
            recommendationsText.append("==================================\n\n");

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
            Platform.runLater(() -> {
                recommendationsArea.setText(recommendationsText.toString());
                recommendationsArea.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px;");
            });
        }
    }

    public void showPronunciationTrainer() {
        String audioFile = state.getCurrentAudioFile();

        if (audioFile == null) {
            ErrorHandler.showWarning("Внимание",
                    "Сначала запишите аудио и проанализируйте его");
            return;
        }

        showAnalysisInProgress(true);

        CompletableFuture.runAsync(() -> {
            try {
                String text = "Аудиосообщение";
                EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(audioFile, text);

                final EnhancedSpeechAnalysis finalAnalysis = analysis;

                Platform.runLater(() -> {
                    showAnalysisInProgress(false);

                    if (finalAnalysis.getPhonemeScores() != null &&
                            !finalAnalysis.getPhonemeScores().isEmpty()) {
                        showPronunciationExercises(finalAnalysis);
                    } else {
                        ErrorHandler.showWarning("Данные не найдены",
                                "Не удалось получить данные о фонемах");
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка при создании тренажера произношения", e);
                Platform.runLater(() -> {
                    showAnalysisInProgress(false);
                    ErrorHandler.showError("Ошибка",
                            "Не удалось создать тренажер: " + e.getMessage());
                });
            }
        }, backgroundExecutor);
    }

    private void showPronunciationExercises(EnhancedSpeechAnalysis analysis) {
        List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                .filter(e -> e.getValue() < AppConstants.TRAINER_PHONEME_THRESHOLD)
                .sorted(Map.Entry.comparingByValue())
                .limit(MAX_WEAK_PHONEMES_TO_SHOW)
                .collect(Collectors.toList());

        if (weakPhonemes.isEmpty()) {
            ErrorHandler.showInfo("Отличное произношение!",
                    "У вас нет проблемных звуков! Все фонемы оценены выше 80 баллов.");
            return;
        }

        String exercisesText = generateExercisesText(weakPhonemes, analysis);
        showExercisesWindow(exercisesText);
    }

    private String generateExercisesText(List<Map.Entry<String, Float>> weakPhonemes,
                                         EnhancedSpeechAnalysis analysis) {
        StringBuilder text = new StringBuilder();
        text.append("🎯 ТРЕНАЖЕР ПРОИЗНОШЕНИЯ\n");
        text.append("=".repeat(60)).append("\n\n");

        for (Map.Entry<String, Float> phonemeEntry : weakPhonemes) {
            String phoneme = phonemeEntry.getKey();
            float score = phonemeEntry.getValue();

            String difficulty = score < 60 ? "beginner" :
                    score < 75 ? "intermediate" : "advanced";

            PronunciationTrainer.PronunciationExercise exercise =
                    pronunciationTrainer.createExercise(phoneme, difficulty);

            text.append("🔊 ЗВУК /").append(phoneme).append("/\n");
            text.append("📊 Оценка: ").append(String.format("%.1f", score)).append("/100\n\n");
            text.append("📝 Инструкции:\n").append(exercise.getInstructions()).append("\n\n");
            text.append("📚 Примеры:\n");
            exercise.getExamples().forEach(ex -> text.append("  • ").append(ex).append("\n"));
            text.append("\n💡 Советы:\n");
            exercise.getTips().forEach(tip -> text.append("  • ").append(tip).append("\n"));
            text.append("\n" + "=".repeat(60)).append("\n\n");
        }

        return text.toString();
    }

    private void showExercisesWindow(String content) {
        TextArea trainingArea = new TextArea(content);
        trainingArea.setEditable(false);
        trainingArea.setWrapText(true);
        trainingArea.setPrefSize(EXERCISE_WINDOW_WIDTH - 20, EXERCISE_WINDOW_HEIGHT - 50);
        trainingArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        ScrollPane scrollPane = new ScrollPane(trainingArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white;");

        Stage trainingStage = new Stage();
        trainingStage.setTitle("🎯 Тренажер произношения");
        if (stage != null) {
            trainingStage.initOwner(stage);
        }

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane,
                EXERCISE_WINDOW_WIDTH, EXERCISE_WINDOW_HEIGHT);
        trainingStage.setScene(scene);
        trainingStage.show();
    }

    private void showAnalysisInProgress(boolean inProgress) {
        Platform.runLater(() -> {
            if (analysisProgress != null) {
                analysisProgress.setVisible(inProgress);
                analysisProgress.setProgress(inProgress ? ProgressBar.INDETERMINATE_PROGRESS : 0);
            }
            if (detailedAnalysisArea != null && inProgress) {
                detailedAnalysisArea.setVisible(true);
                detailedAnalysisArea.setText("⏳ Выполняется анализ...\n\nПожалуйста, подождите.");
            }
            if (statusLabel != null && inProgress) {
                statusLabel.setText("🔍 Анализ речи...");
                statusLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
            }

            updateButtonsState(!inProgress);
        });
    }

    private void updateButtonsState(boolean enabled) {
        if (analyzeButton != null) {
            analyzeButton.setDisable(!enabled);
            analyzeButton.setStyle(enabled ?
                    "-fx-background-color: #3498db; -fx-text-fill: white;" :
                    "-fx-background-color: #95a5a6; -fx-text-fill: white;");
        }
        if (pronunciationButton != null && lastAnalysis.get() != null) {
            pronunciationButton.setDisable(!enabled);
            pronunciationButton.setStyle(enabled ?
                    "-fx-background-color: #27ae60; -fx-text-fill: white;" :
                    "-fx-background-color: #95a5a6; -fx-text-fill: white;");
        }
    }

    private void showCompletionNotification(EnhancedSpeechAnalysis analysis) {
        float overallScore = (float) analysis.getOverallScore();
        String message;
        String emoji;
        String color;

        if (overallScore >= AppConstants.EXCELLENT_SCORE_THRESHOLD) {
            message = "Великолепно! Продвинутый уровень!";
            emoji = "🏆";
            color = "#27ae60";
        } else if (overallScore >= AppConstants.GOOD_SCORE_THRESHOLD) {
            message = "Отличный результат!";
            emoji = "🎉";
            color = "#27ae60";
        } else if (overallScore >= AppConstants.AVERAGE_SCORE_THRESHOLD) {
            message = "Хорошая работа!";
            emoji = "👍";
            color = "#f39c12";
        } else if (overallScore >= AppConstants.FAIR_SCORE_THRESHOLD) {
            message = "Неплохо! Есть прогресс.";
            emoji = "💪";
            color = "#f39c12";
        } else {
            message = "Нужна регулярная практика.";
            emoji = "📚";
            color = "#e74c3c";
        }

        if (statusLabel != null) {
            String finalMessage = String.format("✅ Анализ: %.1f/100 %s %s", overallScore, emoji, message);
            statusLabel.setText(finalMessage);
            statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }

    public void clear() {
        Platform.runLater(() -> {
            if (analysisArea != null) {
                analysisArea.clear();
            }
            if (detailedAnalysisArea != null) {
                detailedAnalysisArea.clear();
                detailedAnalysisArea.setVisible(false);
            }
            if (recommendationsArea != null) {
                recommendationsArea.clear();
            }
            if (phonemeLabel != null) {
                phonemeLabel.setVisible(false);
                phonemeLabel.setText("");
            }

            lastAnalysis.set(null);
            updateButtonsState(false);

            if (pronunciationButton != null) {
                pronunciationButton.setText("🎯 Тренажер произношения");
            }
        });
    }

    public void cancelAnalysis() {
        CompletableFuture<Void> future = currentAnalysis.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            isAnalyzing.set(false);
            Platform.runLater(() -> {
                showAnalysisInProgress(false);
                ErrorHandler.showWarning("Анализ отменен", "Анализ был прерван");
            });
            logger.info("Анализ отменен пользователем");
        }
    }

    public EnhancedSpeechAnalysis getLastAnalysis() {
        return lastAnalysis.get();
    }

    public boolean isAnalyzing() {
        return isAnalyzing.get();
    }
}