package com.mygitgor.ai.strategy.type;

import com.mygitgor.ai.AiService;
import com.mygitgor.ai.strategy.*;
import com.mygitgor.ai.strategy.core.*;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class ConversationStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ConversationStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;
    private final Map<String, ConversationState> sessions = new ConcurrentHashMap<>();

    private static class ConversationState {
        final List<String> history = new ArrayList<>();
        String currentTopic;
        int turnCount;
        double fluencyScore;
    }

    public ConversationStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.CONVERSATION;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ConversationState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ConversationState());

            state.history.add("user: " + userInput);
            state.turnCount++;

            String prompt = buildConversationPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            state.history.add("ai: " + aiResponse);

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
            ConversationState state = sessions.get(context.getUserId());
            String prompt = buildConversationPrompt(userInput, state, context);
            return aiService.generateBotResponse(prompt, null);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ConversationState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            skills.put("fluency", calculateFluencyScore(state));
            skills.put("vocabulary", calculateVocabularyScore(state));
            skills.put("grammar", calculateGrammarScore(state));
            skills.put("engagement", calculateEngagementScore(state));

            return new LearningProgress.Builder()
                    .overallProgress(calculateOverallProgress(skills))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.turnCount / 2 : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(getAchievements(state))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ConversationState state = sessions.get(context.getUserId());

            String[] topics = {
                    "Daily routines", "Travel experiences", "Future plans",
                    "Favorite movies", "Cultural differences", "Technology impact",
                    "Environmental issues", "Career aspirations"
            };

            String topic = state != null && state.currentTopic != null
                    ? state.currentTopic
                    : topics[new Random().nextInt(topics.length)];

            return new LearningTask.Builder()
                    .id("conv_" + System.currentTimeMillis())
                    .title("Разговор на тему: " + topic)
                    .description("Обсудите тему '" + topic + "' с AI репетитором")
                    .mode(LearningMode.CONVERSATION)
                    .difficulty(determineDifficulty(context.getCurrentLevel()))
                    .examples(Arrays.asList(
                            "What do you think about...",
                            "In my opinion...",
                            "I'd like to add that..."
                    ))
                    .metadata(Map.of("topic", topic, "suggested_duration", 15))
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Conversation Practice Strategy";
    }

    private String buildConversationPrompt(String userInput, ConversationState state,
                                           LearningContext context) {
        return String.format("""
            Ты - дружелюбный AI репетитор для разговорной практики.
            
            Контекст разговора:
            - Уровень ученика: %.1f
            - Тема: %s
            - История (%d сообщений): %s
            
            Сообщение ученика: %s
            
            Ответь естественно, поддерживая разговор. Задавай вопросы, чтобы 
            стимулировать диалог. Корректируй ошибки мягко, если они есть.
            """,
                context.getCurrentLevel(),
                state.currentTopic != null ? state.currentTopic : "общая",
                state.history.size(),
                String.join(" | ", state.history.subList(
                        Math.max(0, state.history.size() - 4), state.history.size())),
                userInput
        );
    }

    private LearningMode determineNextMode(LearningContext context, ConversationState state) {
        if (state.fluencyScore < 50) {
            return LearningMode.PRONUNCIATION;
        } else if (state.turnCount > 20) {
            return LearningMode.EXERCISE;
        }
        return LearningMode.CONVERSATION;
    }

    private LearningTask generateNextTask(LearningContext context, ConversationState state) {
        return new LearningTask.Builder()
                .id("task_" + System.currentTimeMillis())
                .title("Продолжить разговор")
                .description("Практикуйте спонтанную речь на выбранную тему")
                .mode(LearningMode.CONVERSATION)
                .difficulty(LearningTask.DifficultyLevel.INTERMEDIATE)
                .build();
    }

    private double calculateProgress(ConversationState state) {
        if (state == null) return 0;
        return Math.min(100, (state.turnCount * 5) + (state.fluencyScore * 0.3));
    }

    private List<String> generateRecommendations(ConversationState state) {
        List<String> recs = new ArrayList<>();
        if (state.fluencyScore < 60) {
            recs.add("Практикуйте более длинные ответы");
        }
        if (state.turnCount < 10) {
            recs.add("Старайтесь поддерживать диалог дольше");
        }
        return recs;
    }

    private double calculateFluencyScore(ConversationState state) {
        return state != null ? state.fluencyScore : 0;
    }

    private double calculateVocabularyScore(ConversationState state) {
        return 70 + new Random().nextInt(20);
    }

    private double calculateGrammarScore(ConversationState state) {
        return 65 + new Random().nextInt(25);
    }

    private double calculateEngagementScore(ConversationState state) {
        return state != null ? Math.min(100, state.turnCount * 5) : 0;
    }

    private double calculateOverallProgress(Map<String, Double> skills) {
        return skills.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private List<String> getAchievements(ConversationState state) {
        List<String> achievements = new ArrayList<>();
        if (state != null && state.turnCount > 50) {
            achievements.add("🎯 50+ сообщений в диалоге");
        }
        return achievements;
    }

    private LearningTask.DifficultyLevel determineDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }
}
