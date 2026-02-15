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

public class GrammarStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(GrammarStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, GrammarState> sessions = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> grammarTopics = new HashMap<>();

    static {
        grammarTopics.put("beginner", Arrays.asList(
                "Present Simple", "Past Simple", "Future Simple",
                "Articles", "Prepositions of time", "There is/are"
        ));
        grammarTopics.put("intermediate", Arrays.asList(
                "Present Perfect", "Past Continuous", "Conditionals 0,1,2",
                "Passive Voice", "Reported Speech", "Modal Verbs"
        ));
        grammarTopics.put("advanced", Arrays.asList(
                "Past Perfect", "Future Perfect", "Conditionals 3",
                "Mixed Conditionals", "Inversion", "Causative Forms"
        ));
    }

    private static class GrammarState {
        final List<String> completedTopics = new ArrayList<>();
        final Map<String, Double> topicScores = new HashMap<>();
        String currentTopic;
        int exercisesDone;
        double averageScore;
    }

    public GrammarStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.GRAMMAR;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new GrammarState());

            if (state.currentTopic == null) {
                state.currentTopic = getNextTopic(context);
            }

            String prompt = buildGrammarPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            double score = evaluateGrammar(userInput, state.currentTopic);
            state.topicScores.put(state.currentTopic, score);

            if (score > 80 && !state.completedTopics.contains(state.currentTopic)) {
                state.completedTopics.add(state.currentTopic);
                state.currentTopic = getNextTopic(context);
            }

            state.exercisesDone++;
            updateAverageScore(state);

            return LearningResponse.builder()
                    .message(aiResponse)
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
            GrammarState state = sessions.get(context.getUserId());
            String prompt = buildGrammarPrompt(userInput, state, context);
            return aiService.generateBotResponse(prompt, null);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.putAll(state.topicScores);
                skills.put("average", state.averageScore);
                skills.put("topics_completed", (double) state.completedTopics.size());
            }

            return new LearningProgress.Builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .tasksCompleted(state != null ? state.exercisesDone : 0)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.get(context.getUserId());
            String topic = state != null ? state.currentTopic : getNextTopic(context);

            return new LearningTask.Builder()
                    .id("gram_" + System.currentTimeMillis())
                    .title("Изучение: " + topic)
                    .description("Практикуйте грамматическую тему '" + topic + "'")
                    .mode(LearningMode.GRAMMAR)
                    .difficulty(mapDifficulty(context.getCurrentLevel()))
                    .examples(getGrammarExamples(topic))
                    .metadata(Map.of("topic", topic, "exercises_count", 5))
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Grammar Learning Strategy";
    }

    private String buildGrammarPrompt(String userInput, GrammarState state,
                                      LearningContext context) {
        return String.format("""
            Ты - AI репетитор по грамматике английского языка.
            
            Текущая тема: %s
            Уровень ученика: %.1f
            Прогресс по теме: %.1f%%
            
            Задание ученика: %s
            
            Объясни грамматическое правило, проверь ответ и дай обратную связь.
            """,
                state != null ? state.currentTopic : "общая",
                context.getCurrentLevel(),
                state != null ? state.topicScores.getOrDefault(state.currentTopic, 0.0) : 0,
                userInput
        );
    }

    private String getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<String> topics = grammarTopics.get(level);
        return topics.get(new Random().nextInt(topics.size()));
    }

    private String determineLevel(double level) {
        if (level < 30) return "beginner";
        if (level < 60) return "intermediate";
        return "advanced";
    }

    private double evaluateGrammar(String text, String topic) {
        return 60 + Math.random() * 35;
    }

    private void updateAverageScore(GrammarState state) {
        state.averageScore = state.topicScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, GrammarState state) {
        if (state.completedTopics.size() > 5) {
            return LearningMode.CONVERSATION;
        }
        return LearningMode.GRAMMAR;
    }

    private LearningTask generateNextTask(LearningContext context, GrammarState state) {
        String nextTopic = getNextTopic(context);
        return new LearningTask.Builder()
                .id("task_" + System.currentTimeMillis())
                .title("Упражнение по теме: " + nextTopic)
                .description("Выполните упражнения для закрепления материала")
                .mode(LearningMode.GRAMMAR)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .build();
    }

    private double calculateProgress(GrammarState state) {
        if (state == null) return 0;
        return state.averageScore;
    }

    private List<String> generateRecommendations(GrammarState state) {
        List<String> recs = new ArrayList<>();
        state.topicScores.entrySet().stream()
                .filter(e -> e.getValue() < 70)
                .limit(2)
                .forEach(e -> recs.add("Повторите тему: " + e.getKey()));
        return recs;
    }

    private List<String> getGrammarExamples(String topic) {
        Map<String, List<String>> examples = new HashMap<>();
        examples.put("Present Simple", Arrays.asList(
                "I work every day",
                "She studies English"
        ));
        examples.put("Past Simple", Arrays.asList(
                "I visited London last year",
                "They went to the cinema"
        ));
        return examples.getOrDefault(topic,
                Arrays.asList("Example 1", "Example 2"));
    }
}
