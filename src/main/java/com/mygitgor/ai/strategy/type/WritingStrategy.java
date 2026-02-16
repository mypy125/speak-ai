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

public class WritingStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(WritingStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, WritingState> sessions = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> writingTopics = new HashMap<>();

    static {
        writingTopics.put("beginner", Arrays.asList(
                "My Day", "My Family", "My Favorite Food", "My Pet", "My Hobby"
        ));
        writingTopics.put("intermediate", Arrays.asList(
                "Travel Experience", "Future Plans", "Movie Review", "Book Review",
                "Opinion on Technology"
        ));
        writingTopics.put("advanced", Arrays.asList(
                "Argumentative Essay", "Research Summary", "Business Proposal",
                "Literary Analysis", "Critical Review"
        ));
    }

    private static class WritingState {
        final List<String> writtenTexts = new ArrayList<>();
        final List<Double> grammarScores = new ArrayList<>();
        final List<Double> styleScores = new ArrayList<>();
        String currentTopic;
        int textsCompleted;
        double averageScore;
    }

    public WritingStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.WRITING;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            WritingState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new WritingState());

            if (state.currentTopic == null) {
                state.currentTopic = getNextTopic(context);
            }

            String prompt = buildWritingPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            // Анализируем текст
            analyzeWriting(userInput, state);

            state.textsCompleted++;
            updateAverageScore(state);

            return LearningResponse.builder()
                    .message(formatWritingResponse(aiResponse, state))
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
            WritingState state = sessions.get(context.getUserId());
            String prompt = buildWritingPrompt(userInput, state, context);
            return aiService.generateBotResponse(prompt, null);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            WritingState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("grammar", calculateAverageGrammar(state));
                skills.put("style", calculateAverageStyle(state));
                skills.put("productivity", (double) state.textsCompleted);
            }

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .tasksCompleted(state != null ? state.textsCompleted : 0)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String topic = getNextTopic(context);

            return LearningTask.builder()
                    .id("wri_" + System.currentTimeMillis())
                    .title("Writing task: " + topic)
                    .description("Write a text about \"" + topic + "\"")
                    .mode(LearningMode.WRITING)
                    .difficulty(mapDifficulty(context.getCurrentLevel()))
                    .examples(getWritingExamples(topic))
                    .addMetadata("topic", topic)
                    .addMetadata("min_words", getMinWords(context.getCurrentLevel()))
                    .addMetadata("max_words", getMaxWords(context.getCurrentLevel()))
                    .build();
        }, executor);
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Writing Practice Strategy";
    }

    private String buildWritingPrompt(String userInput, WritingState state,
                                      LearningContext context) {

        double level = context.getCurrentLevel();
        String topic = (state != null && state.currentTopic != null)
                ? state.currentTopic : "General Writing";
        int textsCompleted = (state != null) ? state.textsCompleted : 0;
        double avgScore = (state != null) ? state.averageScore : 0.0;

        return String.format("""
    You are an expert, warm and highly motivating AI English Writing Tutor.
    Your goal is to help students write clearly, confidently and naturally — from simple paragraphs to advanced essays.

    === STRICT RULES (ALWAYS FOLLOW) ===
    - ALWAYS respond ONLY in English. Never use any other language.
    - Perfectly adapt to the student’s level (%.1f / 10.0):
        • 1.0–3.9 → Extremely simple words, short sentences, basic feedback
        • 4.0–6.9 → Friendly, clear and supportive with moderate detail
        • 7.0–10.0 → Rich vocabulary, advanced style tips, nuanced suggestions
    - Always start with genuine encouragement and praise.
    - Never say “wrong” or “bad”. Use: “Great effort!”, “You’re on the right track!”, “Let’s make this even stronger”.
    - Structure every response exactly in this order:
        1. Warm positive opening (1–2 sentences)
        2. Overall score + one-sentence summary of strengths
        3. Detailed breakdown by 4 criteria:
           • Grammar & Punctuation
           • Vocabulary & Word Choice
           • Style & Structure
           • Logic & Coherence
        4. Rewritten improved version of the student’s text (highlighted improvements)
        5. 3–5 concrete, actionable suggestions with examples
        6. One memorable writing tip or checklist item
        7. Motivating closing + short writing challenge for next practice

    === CURRENT SESSION ===
    - Writing topic: %s
    - Student proficiency level: %.1f / 10.0
    - Texts completed: %d
    - Average score: %.1f

    Student’s text:
    "%s"

    Now generate a complete, beautifully structured writing feedback response.
    """,
                level,
                topic,
                level,
                textsCompleted,
                avgScore,
                userInput
        );
    }

    private String formatWritingResponse(String aiResponse, WritingState state) {
        String topic = (state != null && state.currentTopic != null)
                ? state.currentTopic : "General Writing";

        return String.format("""
        ✍️ **Writing Mastery Session**

        %s

        **📊 Your Progress**
        • Texts completed: **%d**
        • Average score: **%.1f / 10.0**
        • Current topic: **%s**

        Excellent work! Every text you write makes you a better writer 🔥
        """,
                aiResponse,
                state != null ? state.textsCompleted : 0,
                state != null ? state.averageScore : 0.0,
                topic
        );
    }

    private String getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<String> topics = writingTopics.get(level);
        return topics.get(new Random().nextInt(topics.size()));
    }

    private String determineLevel(double level) {
        if (level < 30) return "beginner";
        if (level < 60) return "intermediate";
        return "advanced";
    }

    private void analyzeWriting(String text, WritingState state) {
        // Простой анализ - в реальном приложении будет сложнее
        double grammarScore = 70 + Math.random() * 25;
        double styleScore = 65 + Math.random() * 30;

        state.grammarScores.add(grammarScore);
        state.styleScores.add(styleScore);
        state.writtenTexts.add(text);
    }

    private void updateAverageScore(WritingState state) {
        if (state.grammarScores.isEmpty()) {
            state.averageScore = 0;
            return;
        }
        state.averageScore = state.grammarScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private double calculateAverageGrammar(WritingState state) {
        return state.grammarScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private double calculateAverageStyle(WritingState state) {
        return state.styleScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private double calculateProgress(WritingState state) {
        if (state == null) return 0;
        return (state.averageScore * 0.7) + (Math.min(100, state.textsCompleted * 10) * 0.3);
    }

    private List<String> generateRecommendations(WritingState state) {
        List<String> recs = new ArrayList<>();
        if (calculateAverageGrammar(state) < 70) {
            recs.add("Focus more on grammar accuracy");
        }
        if (calculateAverageStyle(state) < 65) {
            recs.add("Improve text structure and coherence");
        }
        if (state.textsCompleted < 5) {
            recs.add("Write more texts to practice regularly");
        }
        return recs;
    }

    private List<String> getWritingExamples(String topic) {
        return Arrays.asList(
                "Example of a good introduction",
                "Example of a body paragraph",
                "Example of a conclusion"
        );
    }

    private int getMinWords(double level) {
        if (level < 30) return 50;
        if (level < 60) return 150;
        return 300;
    }

    private int getMaxWords(double level) {
        if (level < 30) return 100;
        if (level < 60) return 300;
        return 600;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, WritingState state) {
        if (state.averageScore > 85 && state.textsCompleted > 10) {
            return LearningMode.CONVERSATION;
        }
        return LearningMode.WRITING;
    }

    private LearningTask generateNextTask(LearningContext context, WritingState state) {
        String nextTopic = getNextTopic(context);
        return LearningTask.builder()
                .id("task_" + System.currentTimeMillis())
                .title("New writing topic")
                .description("Write a text about: " + nextTopic)
                .mode(LearningMode.WRITING)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .build();
    }
}
