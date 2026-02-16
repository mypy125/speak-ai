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

public class VocabularyStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(VocabularyStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, VocabularyState> sessions = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> vocabularyTopics = new HashMap<>();

    static {
        vocabularyTopics.put("beginner", Arrays.asList(
                "Family", "Food", "Animals", "Colors", "Numbers",
                "Daily Routine", "Weather", "Clothes", "House", "School"
        ));
        vocabularyTopics.put("intermediate", Arrays.asList(
                "Work", "Travel", "Health", "Technology", "Environment",
                "Education", "Culture", "Business", "Media", "Sports"
        ));
        vocabularyTopics.put("advanced", Arrays.asList(
                "Psychology", "Philosophy", "Economics", "Politics", "Science",
                "Art", "Literature", "Law", "Medicine", "Engineering"
        ));
    }

    private static class VocabularyState {
        final List<String> learnedWords = new ArrayList<>();
        final Map<String, Integer> wordMastery = new HashMap<>();
        String currentTopic;
        int wordsLearned;
        double averageRetention;
    }

    public VocabularyStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.VOCABULARY;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            VocabularyState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new VocabularyState());

            if (state.currentTopic == null) {
                state.currentTopic = getNextTopic(context);
            }

            String prompt = buildVocabularyPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            analyzeVocabularyUsage(userInput, state);

            updateWordMastery(state);
            calculateRetention(state);

            return LearningResponse.builder()
                    .message(formatVocabularyResponse(aiResponse, state))
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
            VocabularyState state = sessions.get(context.getUserId());
            String prompt = buildVocabularyPrompt(userInput, state, context);
            return aiService.generateBotResponse(prompt, null);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            VocabularyState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("words_learned", (double) state.wordsLearned);
                skills.put("retention_rate", state.averageRetention);
                skills.put("mastery_average", calculateAverageMastery(state));
            }

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .tasksCompleted(state != null ? state.wordsLearned / 5 : 0)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String topic = getNextTopic(context);
            List<String> newWords = generateWordList(topic, context.getCurrentLevel());

            return LearningTask.builder()
                    .id("voc_" + System.currentTimeMillis())
                    .title("Vocabulary learning: " + topic)
                    .description("Learn new words related to \"" + topic + "\"")
                    .mode(LearningMode.VOCABULARY)
                    .difficulty(mapDifficulty(context.getCurrentLevel()))
                    .examples(newWords)
                    .addMetadata("topic", topic)
                    .addMetadata("words", newWords)
                    .addMetadata("count", newWords.size())
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Vocabulary Learning Strategy";
    }

    private String buildVocabularyPrompt(String userInput, VocabularyState state,
                                         LearningContext context) {

        double level = context.getCurrentLevel();
        String topic = (state != null && state.currentTopic != null)
                ? state.currentTopic : "General Vocabulary";
        int wordsLearned = (state != null) ? state.wordsLearned : 0;
        double retention = (state != null) ? state.averageRetention : 0.0;

        return String.format("""
    You are an expert, warm and highly motivating AI English Vocabulary Tutor.
    Your goal is to help students expand their active vocabulary, remember words long-term and use them naturally in real conversations.

    === STRICT RULES (ALWAYS FOLLOW) ===
    - ALWAYS respond ONLY in English. Never switch languages.
    - Perfectly adapt to the student’s level (%.1f / 10.0):
        • 1.0–3.9 → Extremely simple words, short sentences, basic explanations
        • 4.0–6.9 → Friendly, clear and supportive
        • 7.0–10.0 → Rich, natural and idiomatic language with collocations & nuances
    - Always start with genuine praise and encouragement.
    - Never say “wrong” or “bad”. Use: “Nice try!”, “You’re on the right track!”, “Let’s make this even better”.
    - Structure every response exactly in this order:
        1. Warm positive opening (1–2 sentences)
        2. What the student used correctly (highlight good words/phrases)
        3. Gentle improvement suggestions (show better alternatives)
        4. 3–5 powerful new words/collocations related to the topic + natural example sentences
        5. One memorable tip or mnemonic to help retention
        6. Short interactive challenge or question to practice the new vocabulary right away
        7. Motivating closing

    === CURRENT SESSION ===
    - Vocabulary topic: %s
    - Student proficiency level: %.1f / 10.0
    - Words learned this session: %d
    - Average retention: %.1f%%

    Student’s message / answer:
    "%s"

    Now generate a complete, beautifully structured vocabulary feedback response.
    """,
                level,
                topic,
                level,
                wordsLearned,
                retention,
                userInput
        );
    }

    private String formatVocabularyResponse(String aiResponse, VocabularyState state) {
        String topic = (state != null && state.currentTopic != null)
                ? state.currentTopic : "General Vocabulary";

        return String.format("""
        📖 **Vocabulary Mastery Session**

        %s

        **📊 Your Progress**
        • Words learned: **%d**
        • Average retention: **%.1f%%**
        • Current topic: **%s**

        You’re building an amazing vocabulary! Keep it up! 🔥
        """,
                aiResponse,
                state != null ? state.wordsLearned : 0,
                state != null ? state.averageRetention : 0.0,
                topic
        );
    }

    private String getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<String> topics = vocabularyTopics.get(level);
        return topics.get(new Random().nextInt(topics.size()));
    }

    private String determineLevel(double level) {
        if (level < 30) return "beginner";
        if (level < 60) return "intermediate";
        return "advanced";
    }

    private List<String> generateWordList(String topic, double level) {
        // В реальном приложении здесь будет вызов AI
        List<String> words = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            words.add(String.format("word_%d related to %s", i + 1, topic));
        }
        return words;
    }

    private void analyzeVocabularyUsage(String userInput, VocabularyState state) {
        // Простой анализ - в реальном приложении будет сложнее
        String[] words = userInput.toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.length() > 3) {
                if (!state.learnedWords.contains(word)) {
                    state.learnedWords.add(word);
                    state.wordMastery.put(word, 1);
                } else {
                    state.wordMastery.merge(word, 1, Integer::sum);
                }
            }
        }
        state.wordsLearned = state.learnedWords.size();
    }

    private void updateWordMastery(VocabularyState state) {
        state.wordMastery.replaceAll((word, count) -> Math.min(5, count));
    }

    private void calculateRetention(VocabularyState state) {
        if (state.wordMastery.isEmpty()) {
            state.averageRetention = 0;
            return;
        }
        state.averageRetention = state.wordMastery.values().stream()
                .mapToDouble(Integer::doubleValue)
                .average()
                .orElse(0) * 20; // Преобразуем в проценты
    }

    private double calculateAverageMastery(VocabularyState state) {
        if (state.wordMastery.isEmpty()) return 0;
        return state.wordMastery.values().stream()
                .mapToDouble(Integer::doubleValue)
                .average()
                .orElse(0) * 20;
    }

    private double calculateProgress(VocabularyState state) {
        if (state == null) return 0;
        return (state.averageRetention * 0.6) + (Math.min(100, state.wordsLearned) * 0.4);
    }

    private List<String> generateRecommendations(VocabularyState state) {
        List<String> recs = new ArrayList<>();
        if (state.wordsLearned < 50) {
            recs.add("Learn 10 new words every day");
        }
        if (state.averageRetention < 60) {
            recs.add("Review previously learned words regularly");
        }
        return recs;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, VocabularyState state) {
        if (state.wordsLearned > 100 && state.averageRetention > 80) {
            return LearningMode.CONVERSATION;
        }
        return LearningMode.VOCABULARY;
    }

    private LearningTask generateNextTask(LearningContext context, VocabularyState state) {
        String nextTopic = getNextTopic(context);
        return LearningTask.builder()
                .id("task_" + System.currentTimeMillis())
                .title("New topic: " + nextTopic)
                .description("Learn vocabulary related to " + nextTopic)
                .mode(LearningMode.VOCABULARY)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .build();
    }
}
