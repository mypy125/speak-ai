package com.mygitgor.ai.strategy.type;

import com.mygitgor.ai.strategy.*;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.model.LearningContext;
import com.mygitgor.model.LearningMode;
import com.mygitgor.model.LearningResponse;
import com.mygitgor.model.LearningTask;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class PronunciationStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(PronunciationStrategy.class);

    private final PronunciationTrainer pronunciationTrainer;
    private final AudioAnalyzer audioAnalyzer;
    private final ExecutorService executor;

    private final Map<String, PronunciationState> sessions = new ConcurrentHashMap<>();

    private static class PronunciationState {
        final List<String> practicedPhonemes = new ArrayList<>();
        final Map<String, Double> phonemeScores = new HashMap<>();
        String currentPhoneme;
        int exercisesCompleted;
    }

    public PronunciationStrategy(PronunciationTrainer trainer, AudioAnalyzer analyzer) {
        this.pronunciationTrainer = trainer;
        this.audioAnalyzer = analyzer;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.PRONUNCIATION;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            PronunciationState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new PronunciationState());

            String phoneme = state.currentPhoneme != null ?
                    state.currentPhoneme : getNextPhoneme(context);

            // Анализ произношения (в реальном приложении здесь будет аудио)
            double score = analyzePronunciation(userInput, phoneme);
            state.phonemeScores.put(phoneme, score);
            state.practicedPhonemes.add(phoneme);

            String feedback = generateFeedback(phoneme, score, context);

            return LearningResponse.builder()
                    .message(feedback)
                    .nextMode(determineNextMode(context, state))
                    .nextTask(generateNextTask(context, state))
                    .progress(calculateProgress(state))
                    .recommendations(generateRecommendations(state))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<String> generateResponse(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            PronunciationState state = sessions.get(context.getUserId());
            String phoneme = state != null ? state.currentPhoneme : "general";
            double score = analyzePronunciation(userInput, phoneme);
            return generateFeedback(phoneme, score, context);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            PronunciationState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("averageScore", state.phonemeScores.values().stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0));
                skills.put("phonemesPracticed", (double) state.practicedPhonemes.size());
            }

            return new LearningProgress.Builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .tasksCompleted(state != null ? state.exercisesCompleted : 0)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String phoneme = getNextPhoneme(context);
            var exercise = pronunciationTrainer.createExercise(phoneme,
                    determineDifficultyLevel(context.getCurrentLevel()));

            return new LearningTask.Builder()
                    .id("pron_" + System.currentTimeMillis())
                    .title("Pronunciation practice /" + phoneme + "/")
                    .description(exercise.getInstructions())
                    .mode(LearningMode.PRONUNCIATION)
                    .difficulty(mapDifficulty(context.getCurrentLevel()))
                    .examples(exercise.getExamples())
                    .metadata(Map.of("phoneme", phoneme, "tips", exercise.getTips()))
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return pronunciationTrainer != null;
    }

    @Override
    public String getStrategyName() {
        return "Pronunciation Training Strategy";
    }

    private double analyzePronunciation(String text, String phoneme) {
        return 60 + Math.random() * 35;
    }

    private String generateFeedback(String phoneme, double score, LearningContext context) {
        if (score > 85) {
            return String.format(
                    "Excellent! The /%s/ sound is pronounced correctly. (Score: %.1f)",
                    phoneme, score
            );
        } else if (score > 70) {
            return String.format(
                    "Good, but there are minor pronunciation issues. (Score: %.1f)",
                    score
            );
        } else {
            return String.format(
                    "You need more practice with the /%s/ sound. (Score: %.1f)",
                    phoneme, score
            );
        }
    }

    private String getNextPhoneme(LearningContext context) {
        String[] phonemes = {"θ", "ð", "r", "æ", "ɪ", "iː", "ʃ", "w"};
        return phonemes[new Random().nextInt(phonemes.length)];
    }

    private String determineDifficultyLevel(double level) {
        if (level < 30) return "beginner";
        if (level < 60) return "intermediate";
        return "advanced";
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, PronunciationState state) {
        double avgScore = state.phonemeScores.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);

        if (avgScore > 80 && state.practicedPhonemes.size() > 5) {
            return LearningMode.CONVERSATION;
        }
        return LearningMode.PRONUNCIATION;
    }

    private LearningTask generateNextTask(LearningContext context, PronunciationState state) {
        String nextPhoneme = getNextPhoneme(context);
        return new LearningTask.Builder()
                .id("task_" + System.currentTimeMillis())
                .title("Practice sound /" + nextPhoneme + "/")
                .description("Repeat words with this sound")
                .mode(LearningMode.PRONUNCIATION)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .build();
    }

    private double calculateProgress(PronunciationState state) {
        if (state == null || state.phonemeScores.isEmpty()) return 0;
        return state.phonemeScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private List<String> generateRecommendations(PronunciationState state) {
        List<String> recs = new ArrayList<>();
        state.phonemeScores.entrySet().stream()
                .filter(e -> e.getValue() < 70)
                .limit(3)
                .forEach(e -> recs.add("Focus on the /" + e.getKey() + "/ sound"));
        return recs;
    }
}
