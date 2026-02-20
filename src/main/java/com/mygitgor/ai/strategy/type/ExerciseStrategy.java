package com.mygitgor.ai.strategy.type;

import com.mygitgor.ai.AiService;
import com.mygitgor.ai.strategy.*;
import com.mygitgor.model.LearningContext;
import com.mygitgor.model.LearningMode;
import com.mygitgor.model.LearningResponse;
import com.mygitgor.model.LearningTask;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

public class ExerciseStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ExerciseStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, ExerciseState> sessions = new ConcurrentHashMap<>();

    private static final String[] EXERCISE_TYPES = {
            "fill_gaps", "multiple_choice", "translation",
            "correction", "matching", "sentence_rearrange",
            "word_formation", "error_detection"
    };

    private static final Map<String, ExerciseTemplate> EXERCISE_TEMPLATES = new HashMap<>();

    private static final double SUCCESS_RATE_WEIGHT = 0.5;
    private static final double COMPLETION_WEIGHT = 0.3;
    private static final double CONSISTENCY_WEIGHT = 0.2;

    private static final int ACHIEVEMENT_EXERCISES_10 = 10;
    private static final int ACHIEVEMENT_EXERCISES_50 = 50;
    private static final int ACHIEVEMENT_EXERCISES_100 = 100;
    private static final int ACHIEVEMENT_EXERCISES_500 = 500;
    private static final double ACHIEVEMENT_SUCCESS_90 = 90.0;
    private static final double ACHIEVEMENT_SUCCESS_95 = 95.0;
    private static final int ACHIEVEMENT_STREAK_5 = 5;
    private static final int ACHIEVEMENT_STREAK_10 = 10;
    private static final int ACHIEVEMENT_STREAK_20 = 20;

    // Карта для преобразования типов упражнений в читаемый текст для TTS
    private static final Map<String, String> EXERCISE_TYPE_TO_SPEECH = new HashMap<>();

    static {
        EXERCISE_TEMPLATES.put("fill_gaps", new ExerciseTemplate(
                "Fill in the gaps",
                "Complete the sentences with the correct words",
                Arrays.asList(
                        "Read the sentence carefully",
                        "Think about the correct grammar form",
                        "Type your answer in the blank space"
                )
        ));

        EXERCISE_TEMPLATES.put("multiple_choice", new ExerciseTemplate(
                "Multiple Choice",
                "Choose the correct option from the given choices",
                Arrays.asList(
                        "Read all options before choosing",
                        "Eliminate obviously wrong answers",
                        "Trust your first instinct"
                )
        ));

        EXERCISE_TEMPLATES.put("translation", new ExerciseTemplate(
                "Translation",
                "Translate the given sentence",
                Arrays.asList(
                        "Think about the meaning, not word-for-word",
                        "Use natural expressions in target language",
                        "Consider the context"
                )
        ));

        EXERCISE_TEMPLATES.put("correction", new ExerciseTemplate(
                "Error Correction",
                "Find and correct the mistakes in the sentence",
                Arrays.asList(
                        "Check grammar, spelling, and word order",
                        "Look for common errors",
                        "Make sure your correction makes sense"
                )
        ));

        EXERCISE_TEMPLATES.put("matching", new ExerciseTemplate(
                "Matching",
                "Match the items from two columns",
                Arrays.asList(
                        "Read all items first",
                        "Look for logical connections",
                        "Double-check your matches"
                )
        ));

        EXERCISE_TEMPLATES.put("sentence_rearrange", new ExerciseTemplate(
                "Sentence Rearrangement",
                "Arrange the words to form a correct sentence",
                Arrays.asList(
                        "Look for the subject first",
                        "Identify the verb",
                        "Arrange remaining words logically"
                )
        ));

        EXERCISE_TEMPLATES.put("word_formation", new ExerciseTemplate(
                "Word Formation",
                "Form the correct word form to complete the sentence",
                Arrays.asList(
                        "Identify the part of speech needed",
                        "Think about prefixes and suffixes",
                        "Check if the word fits grammatically"
                )
        ));

        EXERCISE_TEMPLATES.put("error_detection", new ExerciseTemplate(
                "Error Detection",
                "Identify the error in the sentence without correcting it",
                Arrays.asList(
                        "Read the whole sentence first",
                        "Check each part systematically",
                        "Identify what's wrong, not how to fix it"
                )
        ));

        EXERCISE_TYPE_TO_SPEECH.put("fill_gaps", "fill in the gaps");
        EXERCISE_TYPE_TO_SPEECH.put("multiple_choice", "multiple choice");
        EXERCISE_TYPE_TO_SPEECH.put("translation", "translation");
        EXERCISE_TYPE_TO_SPEECH.put("correction", "error correction");
        EXERCISE_TYPE_TO_SPEECH.put("matching", "matching");
        EXERCISE_TYPE_TO_SPEECH.put("sentence_rearrange", "sentence rearrangement");
        EXERCISE_TYPE_TO_SPEECH.put("word_formation", "word formation");
        EXERCISE_TYPE_TO_SPEECH.put("error_detection", "error detection");
    }

    private static class ExerciseState {
        String currentExercise;
        int completedCount;
        int correctCount;
        double successRate;
        int currentStreak;
        int bestStreak;
        final List<String> history = new ArrayList<>();
        final Map<String, Double> exerciseTypeScores = new ConcurrentHashMap<>();
        final List<Long> responseTimes = new ArrayList<>();

        void addExerciseResult(String exerciseType, boolean correct, long responseTime) {
            completedCount++;
            if (correct) {
                correctCount++;
                currentStreak++;
                if (currentStreak > bestStreak) {
                    bestStreak = currentStreak;
                }
            } else {
                currentStreak = 0;
            }

            successRate = (double) correctCount / completedCount * 100;

            double typeScore = exerciseTypeScores.getOrDefault(exerciseType, 0.0);
            double newScore = (typeScore * (completedCount - 1) + (correct ? 100 : 0)) / completedCount;
            exerciseTypeScores.put(exerciseType, newScore);

            responseTimes.add(responseTime);
            if (responseTimes.size() > 20) {
                responseTimes.remove(0);
            }
        }

        double getAverageResponseTime() {
            if (responseTimes.isEmpty()) return 0;
            return responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }

    private static class ExerciseTemplate {
        final String name;
        final String description;
        final List<String> tips;

        ExerciseTemplate(String name, String description, List<String> tips) {
            this.name = name;
            this.description = description;
            this.tips = tips;
        }
    }

    public ExerciseStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("ExerciseStrategy инициализирована с {} типами упражнений", EXERCISE_TYPES.length);
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.EXERCISE;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            ExerciseState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ExerciseState());

            String exerciseType = state.currentExercise != null ?
                    state.currentExercise : getExerciseType(context);

            boolean isCorrect = evaluateExercise(userInput, exerciseType, context);
            long responseTime = System.currentTimeMillis() - startTime;

            state.addExerciseResult(exerciseType, isCorrect, responseTime);
            state.history.add(String.format("%s: %s -> %s",
                    exerciseType, userInput, isCorrect ? "✓" : "✗"));

            String nextExerciseType = getNextExerciseType(state, context);
            state.currentExercise = nextExerciseType;

            // Генерируем текст для отображения
            String displayText = generateDisplayText(isCorrect, state, context, exerciseType);

            // Генерируем текст для TTS
            String ttsText = generateTtsText(isCorrect, state, exerciseType);

            return LearningResponse.builder()
                    .message(displayText)
                    .ttsText(ttsText)
                    .nextMode(determineNextMode(context, state))
                    .nextTask(generateNextTaskWithTts(context, state))
                    .progress(calculateOverallProgress(state))
                    .recommendations(generateRecommendations(state))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<String> generateResponse(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ExerciseState state = sessions.get(context.getUserId());
            if (state == null) {
                return "Let's start with some exercises! Choose a type to begin.";
            }
            String exerciseType = state.currentExercise != null ?
                    state.currentExercise : getExerciseType(context);
            boolean isCorrect = evaluateExercise(userInput, exerciseType, context);

            // Возвращаем TTS текст
            return generateTtsText(isCorrect, state, exerciseType);
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
                skills.put("best_streak", (double) state.bestStreak);
                skills.put("avg_response_time", state.getAverageResponseTime());

                state.exerciseTypeScores.forEach((type, score) ->
                        skills.put("type_" + type, score));
            }

            List<String> achievements = getAchievements(state);

            return LearningProgress.builder()
                    .overallProgress(calculateOverallProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.completedCount : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(achievements)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ExerciseState state = sessions.get(context.getUserId());
            return generateNextTaskWithTts(context, state);
        }, executor);
    }

    private LearningTask generateNextTaskWithTts(LearningContext context, ExerciseState state) {
        String exerciseType = getNextExerciseType(state, context);
        ExerciseTemplate template = EXERCISE_TEMPLATES.getOrDefault(exerciseType,
                new ExerciseTemplate("Exercise", "Practice exercise",
                        Arrays.asList("Take your time", "Read carefully", "Check your answer")));

        List<String> examples = generateExerciseExamples(exerciseType, context.getCurrentLevel());

        if (state != null) {
            state.currentExercise = exerciseType;
        }

        // Текст для отображения
        String displayDescription = generateTaskDisplayText(template, exerciseType, context);

        // Текст для TTS
        String ttsDescription = generateTaskTtsText(template, exerciseType, context);

        return LearningTask.builder()
                .id("ex_" + System.currentTimeMillis())
                .title("📝 Exercise: " + template.name)
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.EXERCISE)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(examples)
                .metadata(Map.of(
                        "type", exerciseType,
                        "count", 5,
                        "tips", template.tips,
                        "difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel()
                ))
                .build();
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Exercise Practice Strategy";
    }

    private boolean evaluateExercise(String userInput, String exerciseType, LearningContext context) {
        // В реальном приложении здесь будет вызов AI для оценки
        // Сейчас используем упрощенную логику с учетом уровня сложности
        double level = context.getCurrentLevel();
        double successProbability = level / 100.0 * 0.7 + 0.3;
        return ThreadLocalRandom.current().nextDouble() < successProbability;
    }

    /**
     * Генерирует текст для отображения
     */
    private String generateDisplayText(boolean isCorrect, ExerciseState state,
                                       LearningContext context, String exerciseType) {
        ExerciseTemplate template = EXERCISE_TEMPLATES.getOrDefault(exerciseType,
                EXERCISE_TEMPLATES.get("fill_gaps"));

        StringBuilder display = new StringBuilder();

        display.append("📝 EXERCISE RESULT\n");
        display.append("═══════════════════════════════════════\n\n");

        if (isCorrect) {
            display.append("✅ CORRECT! Well done!\n\n");

            if (state.currentStreak >= 5) {
                display.append("🔥 Amazing! You're on a streak of ").append(state.currentStreak).append("!\n\n");
            } else if (state.successRate > 80) {
                display.append("🌟 Excellent progress! You're mastering this!\n\n");
            } else {
                display.append("👍 Great job! Keep it up!\n\n");
            }
        } else {
            display.append("❌ NOT QUITE RIGHT. Let's try again.\n\n");

            if (!template.tips.isEmpty()) {
                display.append("💡 TIP: ").append(template.tips.get(0)).append("\n\n");
            }
        }

        display.append("📊 YOUR STATS\n");
        display.append("─────────────\n");
        display.append(String.format("  Success rate: %.1f%%\n", state.successRate));
        display.append(String.format("  Completed: %d\n", state.completedCount));
        display.append(String.format("  Current streak: %d\n", state.currentStreak));
        display.append(String.format("  Best streak: %d\n\n", state.bestStreak));

        display.append("➡️ NEXT EXERCISE TYPE: ").append(template.name.toUpperCase()).append("\n");

        return display.toString();
    }

    /**
     * Генерирует текст для TTS
     */
    private String generateTtsText(boolean isCorrect, ExerciseState state, String exerciseType) {
        ExerciseTemplate template = EXERCISE_TEMPLATES.getOrDefault(exerciseType,
                EXERCISE_TEMPLATES.get("fill_gaps"));

        StringBuilder tts = new StringBuilder();

        if (isCorrect) {
            tts.append("Correct! Well done. ");
            if (state.currentStreak >= 5) {
                tts.append("You're on a streak of ").append(state.currentStreak).append("! ");
            }
        } else {
            tts.append("Not quite right. Let's try again. ");
        }

        tts.append(String.format("Your success rate is %.1f percent. ", state.successRate));
        tts.append(String.format("You have completed %d exercises. ", state.completedCount));
        tts.append(String.format("Your best streak is %d. ", state.bestStreak));

        tts.append("Next exercise type is ").append(template.name).append(". ");

        if (!isCorrect && !template.tips.isEmpty()) {
            tts.append("Tip: ").append(template.tips.get(0)).append(". ");
        }

        return tts.toString();
    }

    /**
     * Генерирует текст для отображения задачи
     */
    private String generateTaskDisplayText(ExerciseTemplate template, String exerciseType,
                                           LearningContext context) {
        StringBuilder display = new StringBuilder();

        display.append("📝 EXERCISE: ").append(template.name.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("📋 Description:\n");
        display.append(template.description).append("\n\n");

        display.append(String.format("📊 Your level: %.1f/100\n", context.getCurrentLevel()));
        display.append("📈 Difficulty: ").append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append("\n\n");

        display.append("📌 INSTRUCTIONS\n");
        display.append("───────────────\n");
        for (String tip : template.tips) {
            display.append("  • ").append(tip).append("\n");
        }
        display.append("\n");

        display.append("Complete 5 exercises of this type to track your progress.\n");

        return display.toString();
    }

    /**
     * Генерирует текст для TTS задачи
     */
    private String generateTaskTtsText(ExerciseTemplate template, String exerciseType,
                                       LearningContext context) {
        StringBuilder tts = new StringBuilder();

        String exerciseSpeech = EXERCISE_TYPE_TO_SPEECH.getOrDefault(exerciseType, "exercise");

        tts.append("New exercise type: ").append(exerciseSpeech).append(". ");
        tts.append(template.description).append(". ");

        tts.append("Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. Difficulty level is ")
                .append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append(". ");

        tts.append("Instructions: ");
        for (int i = 0; i < Math.min(2, template.tips.size()); i++) {
            tts.append(template.tips.get(i));
            if (i < Math.min(2, template.tips.size()) - 1) {
                tts.append(". ");
            }
        }
        tts.append(". ");

        tts.append("Complete five exercises of this type to track your progress.");

        return tts.toString();
    }

    private String getExerciseType(LearningContext context) {
        double level = context.getCurrentLevel();

        if (level < 30) {
            String[] beginnerTypes = {"fill_gaps", "multiple_choice", "matching"};
            return beginnerTypes[ThreadLocalRandom.current().nextInt(beginnerTypes.length)];
        } else if (level < 60) {
            String[] intermediateTypes = {"fill_gaps", "multiple_choice", "translation",
                    "correction", "matching", "sentence_rearrange"};
            return intermediateTypes[ThreadLocalRandom.current().nextInt(intermediateTypes.length)];
        } else {
            return EXERCISE_TYPES[ThreadLocalRandom.current().nextInt(EXERCISE_TYPES.length)];
        }
    }

    private String getNextExerciseType(ExerciseState state, LearningContext context) {
        if (state == null || state.exerciseTypeScores.isEmpty()) {
            return getExerciseType(context);
        }

        return state.exerciseTypeScores.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(getExerciseType(context));
    }

    private List<String> generateExerciseExamples(String exerciseType, double level) {
        List<String> examples = new ArrayList<>();

        switch (exerciseType) {
            case "fill_gaps":
                if (level < 30) {
                    examples.add("I ___ (to be) a student.");
                    examples.add("She ___ (to work) every day.");
                } else if (level < 60) {
                    examples.add("They ___ (to travel) to Paris last year.");
                    examples.add("By next week, I ___ (to finish) the project.");
                } else {
                    examples.add("Had I known about the traffic, I ___ (to leave) earlier.");
                    examples.add("The documents ___ (to sign) by the manager yesterday.");
                }
                break;

            case "multiple_choice":
                examples.add("Choose the correct option: He ___ to school every day.");
                examples.add("a) go   b) goes   c) going   d) went");
                examples.add("");
                examples.add("Select the right answer: They ___ playing football now.");
                examples.add("a) is   b) am   c) are   d) be");
                break;

            case "translation":
                examples.add("Translate to English: 'Я люблю читать книги'");
                examples.add("Translate to English: 'Она работает в банке'");
                break;

            case "correction":
                examples.add("Find and correct the error: 'He go to school yesterday'");
                examples.add("Find and correct the error: 'She don't like coffee'");
                break;

            default:
                examples.add("Complete the exercise according to the instructions");
                examples.add("Take your time and think carefully");
        }

        return examples;
    }

    private double calculateOverallProgress(ExerciseState state) {
        if (state == null) return 0;

        double successComponent = state.successRate * SUCCESS_RATE_WEIGHT;
        double completionComponent = Math.min(100, state.completedCount) * COMPLETION_WEIGHT;
        double consistencyComponent = (state.bestStreak * 5) * CONSISTENCY_WEIGHT;

        return Math.min(100, successComponent + completionComponent + consistencyComponent);
    }

    private List<String> getAchievements(ExerciseState state) {
        List<String> achievements = new ArrayList<>();

        if (state == null) return achievements;

        if (state.completedCount >= ACHIEVEMENT_EXERCISES_500) {
            achievements.add("🏆 500+ exercises - true master!");
        } else if (state.completedCount >= ACHIEVEMENT_EXERCISES_100) {
            achievements.add("🎯 100+ exercises - excellent progress!");
        } else if (state.completedCount >= ACHIEVEMENT_EXERCISES_50) {
            achievements.add("⭐ 50+ exercises - you're on the right track!");
        } else if (state.completedCount >= ACHIEVEMENT_EXERCISES_10) {
            achievements.add("🌟 10+ exercises - great start!");
        }

        if (state.successRate >= ACHIEVEMENT_SUCCESS_95) {
            achievements.add("💯 Almost perfect! " + String.format("%.1f%% success rate", state.successRate));
        } else if (state.successRate >= ACHIEVEMENT_SUCCESS_90) {
            achievements.add("🎓 Excellent success rate! " + String.format("%.1f%%", state.successRate));
        }

        if (state.bestStreak >= ACHIEVEMENT_STREAK_20) {
            achievements.add("🔥 Incredible streak! " + state.bestStreak + " correct answers in a row!");
        } else if (state.bestStreak >= ACHIEVEMENT_STREAK_10) {
            achievements.add("⚡ Great streak! " + state.bestStreak + " correct answers in a row");
        } else if (state.bestStreak >= ACHIEVEMENT_STREAK_5) {
            achievements.add("✨ Good streak! " + state.bestStreak + " correct answers in a row");
        }

        if (state.exerciseTypeScores.size() >= 5) {
            achievements.add("🔄 Tried 5+ different exercise types");
        }

        return achievements;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, ExerciseState state) {
        if (state == null) return LearningMode.EXERCISE;

        if (state.successRate > 85 && state.completedCount > 20) {
            return LearningMode.CONVERSATION;
        } else if (state.successRate < 50) {
            return LearningMode.GRAMMAR;
        }

        return LearningMode.EXERCISE;
    }

    private List<String> generateRecommendations(ExerciseState state) {
        List<String> recommendations = new ArrayList<>();

        if (state == null) return recommendations;

        if (state.successRate < 60) {
            recommendations.add("Review the theory before doing exercises");
            recommendations.add("Start with easier exercises and gradually increase difficulty");
        }

        if (state.bestStreak < 3) {
            recommendations.add("Try to focus and build a streak of correct answers");
        }

        if (!state.exerciseTypeScores.isEmpty()) {
            state.exerciseTypeScores.entrySet().stream()
                    .filter(e -> e.getValue() < 60)
                    .limit(2)
                    .forEach(e -> {
                        ExerciseTemplate template = EXERCISE_TEMPLATES.get(e.getKey());
                        if (template != null) {
                            recommendations.add("Practice more " + template.name.toLowerCase() + " exercises");
                        }
                    });
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great job! Keep practicing to maintain your skills");
            recommendations.add("Try different exercise types for variety");
        }

        return recommendations;
    }

    public Map<String, Object> getSessionState(String userId) {
        ExerciseState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("currentExercise", state.currentExercise);
        stateMap.put("completedCount", state.completedCount);
        stateMap.put("correctCount", state.correctCount);
        stateMap.put("successRate", state.successRate);
        stateMap.put("currentStreak", state.currentStreak);
        stateMap.put("bestStreak", state.bestStreak);
        stateMap.put("history", new ArrayList<>(state.history));
        stateMap.put("exerciseTypeScores", new HashMap<>(state.exerciseTypeScores));
        stateMap.put("responseTimes", new ArrayList<>(state.responseTimes));

        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        ExerciseState state = new ExerciseState();

        state.currentExercise = (String) stateMap.get("currentExercise");
        state.completedCount = (int) stateMap.getOrDefault("completedCount", 0);
        state.correctCount = (int) stateMap.getOrDefault("correctCount", 0);
        state.successRate = (double) stateMap.getOrDefault("successRate", 0.0);
        state.currentStreak = (int) stateMap.getOrDefault("currentStreak", 0);
        state.bestStreak = (int) stateMap.getOrDefault("bestStreak", 0);

        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) stateMap.getOrDefault("history", Collections.emptyList());
        state.history.addAll(history);

        @SuppressWarnings("unchecked")
        Map<String, Double> typeScores = (Map<String, Double>) stateMap.getOrDefault("exerciseTypeScores", Collections.emptyMap());
        state.exerciseTypeScores.putAll(typeScores);

        @SuppressWarnings("unchecked")
        List<Long> responseTimes = (List<Long>) stateMap.getOrDefault("responseTimes", Collections.emptyList());
        state.responseTimes.addAll(responseTimes);

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        ExerciseState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("completedExercises", state.completedCount);
        stats.put("correctAnswers", state.correctCount);
        stats.put("successRate", state.successRate);
        stats.put("currentStreak", state.currentStreak);
        stats.put("bestStreak", state.bestStreak);
        stats.put("exerciseTypes", new HashMap<>(state.exerciseTypeScores));
        stats.put("averageResponseTime", state.getAverageResponseTime());

        return stats;
    }
}