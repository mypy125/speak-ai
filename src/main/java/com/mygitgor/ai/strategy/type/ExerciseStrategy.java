package com.mygitgor.ai.strategy.type;

import com.mygitgor.ai.AiService;
import com.mygitgor.ai.strategy.*;
import com.mygitgor.ai.strategy.core.*;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class ExerciseStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ExerciseStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, ExerciseState> sessions = new ConcurrentHashMap<>();

    private static class ExerciseState {
        String currentExercise;
        int completedCount;
        int correctCount;
        double successRate;
        final List<String> history = new ArrayList<>();
    }

    public ExerciseStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.EXERCISE;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ExerciseState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ExerciseState());

            boolean isCorrect = evaluateExercise(userInput, state);

            if (isCorrect) {
                state.correctCount++;
            }
            state.completedCount++;
            state.successRate = (double) state.correctCount / state.completedCount * 100;
            state.history.add(userInput);

            String feedback = generateExerciseFeedback(isCorrect, state, context);

            return LearningResponse.builder()
                    .message(feedback)
                    .nextMode(determineNextMode(context, state))
                    .nextTask(generateNextTask(context, state))
                    .progress(state.successRate)
                    .recommendations(generateRecommendations(state))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<String> generateResponse(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ExerciseState state = sessions.get(context.getUserId());
            boolean isCorrect = evaluateExercise(userInput, state);
            return generateExerciseFeedback(isCorrect, state, context);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ExerciseState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("success_rate", state.successRate);
                skills.put("completed", (double) state.completedCount);
                skills.put("accuracy", (double) state.correctCount /
                        Math.max(1, state.completedCount) * 100);
            }

            return new LearningProgress.Builder()
                    .overallProgress(state != null ? state.successRate : 0)
                    .skillsProgress(skills)
                    .tasksCompleted(state != null ? state.completedCount : 0)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String exerciseType = getExerciseType(context);

            return new LearningTask.Builder()
                    .id("ex_" + System.currentTimeMillis())
                    .title("Упражнение: " + exerciseType)
                    .description("Выполните задание для закрепления материала")
                    .mode(LearningMode.EXERCISE)
                    .difficulty(mapDifficulty(context.getCurrentLevel()))
                    .examples(getExerciseExamples(exerciseType))
                    .metadata(Map.of("type", exerciseType, "count", 5))
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Exercise Practice Strategy";
    }

    private boolean evaluateExercise(String userInput, ExerciseState state) {
        return Math.random() > 0.3;
    }

    private String generateExerciseFeedback(boolean isCorrect, ExerciseState state,
                                            LearningContext context) {
        if (isCorrect) {
            return String.format("✅ Правильно! Прогресс: %.1f%% (Выполнено: %d)",
                    state.successRate, state.completedCount);
        } else {
            return String.format("❌ Попробуйте еще раз. Прогресс: %.1f%%",
                    state.successRate);
        }
    }

    private String getExerciseType(LearningContext context) {
        String[] types = {"fill_gaps", "multiple_choice", "translation",
                "correction", "matching"};
        return types[new Random().nextInt(types.length)];
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, ExerciseState state) {
        if (state.successRate > 85 && state.completedCount > 10) {
            return LearningMode.CONVERSATION;
        }
        return LearningMode.EXERCISE;
    }

    private LearningTask generateNextTask(LearningContext context, ExerciseState state) {
        return new LearningTask.Builder()
                .id("task_" + System.currentTimeMillis())
                .title("Следующее упражнение")
                .description("Продолжайте практиковаться")
                .mode(LearningMode.EXERCISE)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .build();
    }

    private List<String> generateRecommendations(ExerciseState state) {
        List<String> recs = new ArrayList<>();
        if (state.successRate < 60) {
            recs.add("Повторите теорию перед упражнениями");
        }
        return recs;
    }

    private List<String> getExerciseExamples(String type) {
        Map<String, List<String>> examples = new HashMap<>();
        examples.put("fill_gaps", Arrays.asList(
                "I ___ (go) to school every day",
                "She ___ (read) a book now"
        ));
        examples.put("multiple_choice", Arrays.asList(
                "Choose correct: He ___ to London",
                "Select the right option"
        ));
        return examples.getOrDefault(type, Arrays.asList("Example exercise"));
    }
}