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

/**
 * Стратегия развития аудирования
 */
public class ListeningStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ListeningStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, ListeningState> sessions = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> listeningTopics = new HashMap<>();

    static {
        listeningTopics.put("beginner", Arrays.asList(
                "Simple Dialogues", "Daily Conversations", "Basic Instructions",
                "Simple Stories", "Weather Reports"
        ));
        listeningTopics.put("intermediate", Arrays.asList(
                "News Summaries", "Podcast Excerpts", "Movie Dialogues",
                "Interview Segments", "Lectures"
        ));
        listeningTopics.put("advanced", Arrays.asList(
                "Academic Lectures", "Business Meetings", "Debates",
                "Documentaries", "Technical Presentations"
        ));
    }

    private static class ListeningState {
        final List<Double> comprehensionScores = new ArrayList<>();
        String currentTopic;
        int exercisesCompleted;
        double averageComprehension;
        String lastTranscript;
    }

    public ListeningStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.LISTENING;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ListeningState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ListeningState());

            if (state.currentTopic == null) {
                state.currentTopic = getNextTopic(context);
            }

            // Проверяем понимание
            double comprehensionScore = evaluateComprehension(userInput, state.lastTranscript);
            state.comprehensionScores.add(comprehensionScore);
            state.exercisesCompleted++;
            updateAverageComprehension(state);

            String prompt = buildListeningPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            return LearningResponse.builder()
                    .message(formatListeningResponse(aiResponse, state))
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
            ListeningState state = sessions.get(context.getUserId());
            String prompt = buildListeningPrompt(userInput, state, context);
            return aiService.generateBotResponse(prompt, null);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ListeningState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("comprehension", state.averageComprehension);
                skills.put("exercises", (double) state.exercisesCompleted);
            }

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .tasksCompleted(state != null ? state.exercisesCompleted : 0)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String topic = getNextTopic(context);
            String transcript = generateTranscript(topic, context.getCurrentLevel());

            return LearningTask.builder()
                    .id("lis_" + System.currentTimeMillis())
                    .title("Аудирование: " + topic)
                    .description("Прослушайте аудио и ответьте на вопросы")
                    .mode(LearningMode.LISTENING)
                    .difficulty(mapDifficulty(context.getCurrentLevel()))
                    .addMetadata("topic", topic)
                    .addMetadata("transcript", transcript)
                    .addMetadata("speed", getSpeed(context.getCurrentLevel()))
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Listening Practice Strategy";
    }

    private String buildListeningPrompt(String userInput, ListeningState state,
                                        LearningContext context) {
        return String.format("""
                Ты - AI репетитор по аудированию.
                
                Тема: %s
                Уровень ученика: %.1f
                Понимание: %.1f%%
                
                Ответ ученика: %s
                
                Проанализируй понимание услышанного:
                1. Насколько точно ученик понял содержание
                2. Какие детали были упущены
                3. Дай рекомендации по улучшению восприятия
                """,
                state.currentTopic,
                context.getCurrentLevel(),
                state.averageComprehension,
                userInput
        );
    }

    private String formatListeningResponse(String aiResponse, ListeningState state) {
        return String.format("""
                ### 🎧 Тренировка аудирования
                
                %s
                
                **📊 Результаты:**
                • Понимание: %.1f%%
                • Выполнено упражнений: %d
                • Текущая тема: %s
                """,
                aiResponse,
                state.averageComprehension,
                state.exercisesCompleted,
                state.currentTopic
        );
    }

    private String getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<String> topics = listeningTopics.get(level);
        return topics.get(new Random().nextInt(topics.size()));
    }

    private String determineLevel(double level) {
        if (level < 30) return "beginner";
        if (level < 60) return "intermediate";
        return "advanced";
    }

    private String generateTranscript(String topic, double level) {
        // В реальном приложении здесь будет генерация текста через AI
        return String.format("This is a sample transcript for topic: %s at level %.0f", topic, level);
    }

    private String getSpeed(double level) {
        if (level < 30) return "slow";
        if (level < 60) return "normal";
        return "fast";
    }

    private double evaluateComprehension(String userInput, String transcript) {
        if (transcript == null) return 50 + Math.random() * 40;

        // Простая оценка - в реальном приложении будет сложнее
        String[] userWords = userInput.toLowerCase().split("\\s+");
        String[] transcriptWords = transcript.toLowerCase().split("\\s+");

        int matches = 0;
        for (String word : userWords) {
            if (Arrays.asList(transcriptWords).contains(word)) {
                matches++;
            }
        }

        return Math.min(100, (double) matches / transcriptWords.length * 100);
    }

    private void updateAverageComprehension(ListeningState state) {
        if (state.comprehensionScores.isEmpty()) {
            state.averageComprehension = 0;
            return;
        }
        state.averageComprehension = state.comprehensionScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private double calculateProgress(ListeningState state) {
        if (state == null) return 0;
        return (state.averageComprehension * 0.8) + (Math.min(100, state.exercisesCompleted * 10) * 0.2);
    }

    private List<String> generateRecommendations(ListeningState state) {
        List<String> recs = new ArrayList<>();
        if (state.averageComprehension < 60) {
            recs.add("Слушайте более медленные записи");
        }
        if (state.exercisesCompleted < 10) {
            recs.add("Практикуйтесь регулярно, хотя бы 10 минут в день");
        }
        if (state.averageComprehension > 80) {
            recs.add("Попробуйте более быстрый темп речи");
        }
        return recs;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, ListeningState state) {
        if (state.averageComprehension > 85 && state.exercisesCompleted > 20) {
            return LearningMode.CONVERSATION;
        }
        return LearningMode.LISTENING;
    }

    private LearningTask generateNextTask(LearningContext context, ListeningState state) {
        String nextTopic = getNextTopic(context);
        return LearningTask.builder()
                .id("task_" + System.currentTimeMillis())
                .title("Новое упражнение по аудированию")
                .description("Прослушайте запись на тему: " + nextTopic)
                .mode(LearningMode.LISTENING)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .build();
    }
}
