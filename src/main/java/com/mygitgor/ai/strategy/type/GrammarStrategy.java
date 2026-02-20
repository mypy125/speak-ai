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

public class GrammarStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(GrammarStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, GrammarState> sessions = new ConcurrentHashMap<>();

    private static final double BEGINNER_THRESHOLD = 30.0;
    private static final double INTERMEDIATE_THRESHOLD = 60.0;
    private static final double ADVANCED_THRESHOLD = 85.0;

    private static final double PASSING_SCORE = 70.0;
    private static final double GOOD_SCORE = 80.0;
    private static final double EXCELLENT_SCORE = 90.0;

    private static final int ACHIEVEMENT_TOPICS_5 = 5;
    private static final int ACHIEVEMENT_TOPICS_10 = 10;
    private static final int ACHIEVEMENT_TOPICS_20 = 20;
    private static final int ACHIEVEMENT_EXERCISES_50 = 50;
    private static final int ACHIEVEMENT_EXERCISES_100 = 100;
    private static final int ACHIEVEMENT_EXERCISES_200 = 200;
    private static final double ACHIEVEMENT_AVG_85 = 85.0;
    private static final double ACHIEVEMENT_AVG_90 = 90.0;
    private static final double ACHIEVEMENT_AVG_95 = 95.0;

    private static final Map<String, String> TOPIC_TO_SPEECH = new HashMap<>();
    private static final Map<String, List<GrammarTopic>> grammarTopics = new HashMap<>();

    static {
        // (A1-A2)
        grammarTopics.put("beginner", Arrays.asList(
                new GrammarTopic("Present Simple",
                        "Use for habits, facts, and routines",
                        "Subject + base verb (add -s/-es for he/she/it)",
                        Arrays.asList("I work every day", "She studies English", "They live in London")),

                new GrammarTopic("Past Simple",
                        "Use for completed actions in the past",
                        "Subject + verb-ed (or irregular form)",
                        Arrays.asList("I visited London last year", "She went to the cinema", "They ate dinner")),

                new GrammarTopic("Future Simple (will)",
                        "Use for predictions, promises, and spontaneous decisions",
                        "Subject + will + base verb",
                        Arrays.asList("I will call you tomorrow", "It will rain later", "She will be happy")),

                new GrammarTopic("Articles (a/an/the)",
                        "Use to specify nouns",
                        "a/an for non-specific, the for specific",
                        Arrays.asList("I saw a dog", "The dog was brown", "She is an engineer")),

                new GrammarTopic("Prepositions of Time",
                        "Use to indicate when something happens",
                        "at for specific times, in for months/years, on for days",
                        Arrays.asList("I wake up at 7am", "She was born in 1990", "We meet on Monday")),

                new GrammarTopic("There is/are",
                        "Use to indicate existence",
                        "There is + singular, There are + plural",
                        Arrays.asList("There is a book on the table", "There are many students in class"))
        ));

        // (B1-B2)
        grammarTopics.put("intermediate", Arrays.asList(
                new GrammarTopic("Present Perfect",
                        "Use for past actions with present relevance",
                        "Subject + have/has + past participle",
                        Arrays.asList("I have visited Paris", "She has never seen that movie", "They have finished work")),

                new GrammarTopic("Past Continuous",
                        "Use for actions in progress at a specific past time",
                        "Subject + was/were + verb-ing",
                        Arrays.asList("I was watching TV when you called", "They were eating dinner at 8pm")),

                new GrammarTopic("Conditionals 0,1,2",
                        "Use for real and hypothetical situations",
                        "If + present, present (0) / If + present, will (1) / If + past, would (2)",
                        Arrays.asList("If you heat ice, it melts", "If it rains, we'll stay home", "If I won the lottery, I'd travel")),

                new GrammarTopic("Passive Voice",
                        "Use when the action is more important than the doer",
                        "Subject + be + past participle",
                        Arrays.asList("The cake was baked by Mary", "English is spoken worldwide")),

                new GrammarTopic("Reported Speech",
                        "Use to report what someone said",
                        "Shift tenses back, change pronouns/time expressions",
                        Arrays.asList("She said she was tired", "He told me he would come")),

                new GrammarTopic("Modal Verbs",
                        "Use to express ability, possibility, obligation",
                        "Modal + base verb",
                        Arrays.asList("I can swim", "You must study", "She might come"))
        ));

        // (C1-C2)
        grammarTopics.put("advanced", Arrays.asList(
                new GrammarTopic("Past Perfect",
                        "Use for actions completed before another past action",
                        "Subject + had + past participle",
                        Arrays.asList("I had already eaten when she arrived", "They had finished before we started")),

                new GrammarTopic("Future Perfect",
                        "Use for actions that will be completed by a future time",
                        "Subject + will have + past participle",
                        Arrays.asList("I will have finished by tomorrow", "She will have graduated by June")),

                new GrammarTopic("Conditionals 3",
                        "Use for impossible past situations",
                        "If + past perfect, would have + past participle",
                        Arrays.asList("If I had known, I would have told you", "If she had studied, she would have passed")),

                new GrammarTopic("Mixed Conditionals",
                        "Use for mixed time references in conditionals",
                        "Mix of conditional types",
                        Arrays.asList("If I had studied, I would be a doctor now", "If she wasn't afraid, she would have tried")),

                new GrammarTopic("Inversion",
                        "Use for emphasis or formal style",
                        "Invert subject and auxiliary verb",
                        Arrays.asList("Never have I seen such beauty", "Not only did he come, but he also stayed")),

                new GrammarTopic("Causative Forms",
                        "Use when someone causes something to happen",
                        "Have/get + object + past participle",
                        Arrays.asList("I had my hair cut", "She got her car repaired"))
        ));

        TOPIC_TO_SPEECH.put("Present Simple", "present simple");
        TOPIC_TO_SPEECH.put("Past Simple", "past simple");
        TOPIC_TO_SPEECH.put("Future Simple (will)", "future simple");
        TOPIC_TO_SPEECH.put("Articles (a/an/the)", "articles");
        TOPIC_TO_SPEECH.put("Prepositions of Time", "prepositions of time");
        TOPIC_TO_SPEECH.put("There is/are", "there is and there are");
        TOPIC_TO_SPEECH.put("Present Perfect", "present perfect");
        TOPIC_TO_SPEECH.put("Past Continuous", "past continuous");
        TOPIC_TO_SPEECH.put("Conditionals 0,1,2", "conditionals");
        TOPIC_TO_SPEECH.put("Passive Voice", "passive voice");
        TOPIC_TO_SPEECH.put("Reported Speech", "reported speech");
        TOPIC_TO_SPEECH.put("Modal Verbs", "modal verbs");
        TOPIC_TO_SPEECH.put("Past Perfect", "past perfect");
        TOPIC_TO_SPEECH.put("Future Perfect", "future perfect");
        TOPIC_TO_SPEECH.put("Conditionals 3", "third conditionals");
        TOPIC_TO_SPEECH.put("Mixed Conditionals", "mixed conditionals");
        TOPIC_TO_SPEECH.put("Inversion", "inversion");
        TOPIC_TO_SPEECH.put("Causative Forms", "causative forms");
    }

    private static class GrammarTopic {
        final String name;
        final String description;
        final String formula;
        final List<String> examples;

        GrammarTopic(String name, String description, String formula, List<String> examples) {
            this.name = name;
            this.description = description;
            this.formula = formula;
            this.examples = examples;
        }

        String getDisplayName() {
            return name;
        }
    }

    private static class GrammarState {
        final List<String> completedTopics = new ArrayList<>();
        final Map<String, Double> topicScores = new HashMap<>();
        final Map<String, Integer> topicAttempts = new HashMap<>();
        final List<Double> scoreHistory = new ArrayList<>();
        String currentTopic;
        int exercisesDone;
        int correctAnswers;
        double averageScore;
        double bestScore;
        long totalTimeSpent;
        long sessionStartTime;

        void startExercise() {
            sessionStartTime = System.currentTimeMillis();
        }

        void endExercise(boolean correct) {
            if (sessionStartTime > 0) {
                totalTimeSpent += System.currentTimeMillis() - sessionStartTime;
                sessionStartTime = 0;
            }
            exercisesDone++;
            if (correct) {
                correctAnswers++;
            }
        }

        double getSuccessRate() {
            return exercisesDone > 0 ? (double) correctAnswers / exercisesDone * 100 : 0;
        }

        double getAverageTimePerExercise() {
            return exercisesDone > 0 ? (double) totalTimeSpent / exercisesDone : 0;
        }
    }

    public GrammarStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("GrammarStrategy инициализирована с {} уровнями", grammarTopics.size());
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.GRAMMAR;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            GrammarState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new GrammarState());

            if (state.currentTopic == null) {
                state.currentTopic = getNextTopic(context).name;
            }

            GrammarTopic currentTopic = getTopicByName(state.currentTopic);
            state.startExercise();

            String prompt = buildGrammarPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            double score = evaluateGrammar(userInput, state.currentTopic, context);
            boolean isCorrect = score >= PASSING_SCORE;

            state.endExercise(isCorrect);
            state.topicScores.put(state.currentTopic,
                    (state.topicScores.getOrDefault(state.currentTopic, 0.0) + score) / 2);
            state.topicAttempts.merge(state.currentTopic, 1, Integer::sum);
            state.scoreHistory.add(score);

            if (state.scoreHistory.size() > 20) {
                state.scoreHistory.remove(0);
            }

            if (score > state.bestScore) {
                state.bestScore = score;
            }

            if (score >= GOOD_SCORE && !state.completedTopics.contains(state.currentTopic)) {
                state.completedTopics.add(state.currentTopic);
                state.currentTopic = getNextTopic(context).name;
            }

            state.exercisesDone++;
            updateAverageScore(state);

            // Генерируем текст для отображения
            String displayText = generateDisplayText(aiResponse, score, state, currentTopic);

            // Генерируем текст для TTS
            String ttsText = generateTtsText(aiResponse, score, state, currentTopic);

            return LearningResponse.builder()
                    .message(displayText)
                    .ttsText(ttsText)
                    .nextMode(determineNextMode(context, state))
                    .nextTask(generateNextTaskWithTts(context, state))
                    .progress(calculateProgress(state))
                    .recommendations(generateRecommendations(state))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<String> generateResponse(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.get(context.getUserId());
            GrammarTopic currentTopic = state != null && state.currentTopic != null
                    ? getTopicByName(state.currentTopic)
                    : getTopicByName(getNextTopic(context).name);

            String prompt = buildGrammarPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            // Возвращаем TTS текст
            return generateTtsText(aiResponse,
                    state != null ? state.averageScore : 0,
                    state, currentTopic);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("average_score", state.averageScore);
                skills.put("best_score", state.bestScore);
                skills.put("success_rate", state.getSuccessRate());
                skills.put("topics_completed", (double) state.completedTopics.size());
                skills.put("total_exercises", (double) state.exercisesDone);
                skills.put("avg_time_ms", state.getAverageTimePerExercise());

                state.topicScores.forEach((topic, score) ->
                        skills.put("topic_" + topic, score));

                // Последние 5 оценок для отслеживания тренда
                if (!state.scoreHistory.isEmpty()) {
                    int size = state.scoreHistory.size();
                    skills.put("last_score", state.scoreHistory.get(size - 1));
                    if (size >= 5) {
                        double recentAvg = state.scoreHistory.subList(size - 5, size).stream()
                                .mapToDouble(Double::doubleValue).average().orElse(0);
                        skills.put("recent_trend", recentAvg - state.averageScore);
                    }
                }
            }

            List<String> achievements = getAchievements(state);

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.exercisesDone : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(achievements)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.get(context.getUserId());
            return generateNextTaskWithTts(context, state);
        }, executor);
    }

    private LearningTask generateNextTaskWithTts(LearningContext context, GrammarState state) {
        GrammarTopic topic;
        if (state != null && state.currentTopic != null) {
            topic = getTopicByName(state.currentTopic);
        } else {
            topic = getNextTopic(context);
            if (state != null) {
                state.currentTopic = topic.name;
            }
        }

        List<String> examples = generateExerciseExamples(topic, context.getCurrentLevel());

        // Текст для отображения
        String displayDescription = generateTaskDisplayText(topic, context);

        // Текст для TTS
        String ttsDescription = generateTaskTtsText(topic, context);

        return LearningTask.builder()
                .id("gram_" + System.currentTimeMillis())
                .title("📚 Grammar: " + topic.name)
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.GRAMMAR)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(examples)
                .metadata(Map.of(
                        "topic", topic.name,
                        "formula", topic.formula,
                        "exercises_count", 5,
                        "difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel(),
                        "tips", generateGrammarTips(topic, context.getCurrentLevel())
                ))
                .build();
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Grammar Learning Strategy";
    }

    private GrammarTopic getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<GrammarTopic> topics = grammarTopics.get(level);
        return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
    }

    private GrammarTopic getTopicByName(String name) {
        for (List<GrammarTopic> topics : grammarTopics.values()) {
            for (GrammarTopic topic : topics) {
                if (topic.name.equals(name)) {
                    return topic;
                }
            }
        }
        return grammarTopics.get("beginner").get(0);
    }

    private String determineLevel(double level) {
        if (level < BEGINNER_THRESHOLD) return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private String buildGrammarPrompt(String userInput, GrammarState state,
                                      LearningContext context, GrammarTopic topic) {
        double level = context.getCurrentLevel();
        double topicProgress = (state != null && state.currentTopic != null)
                ? state.topicScores.getOrDefault(state.currentTopic, 0.0)
                : 0.0;

        String levelGuide = getLevelGuide(level);
        String difficulty = determineLevel(level);

        return String.format("""
            You are an expert, patient and highly engaging AI English Grammar Tutor.
            Your mission is to help students truly understand and master English grammar.

            === STUDENT PROFILE ===
            • Proficiency level: %.1f/100 (%s)
            • Current grammar topic: %s
            • Progress on this topic: %.1f%%
            • Exercises completed: %d
            • Average score: %.1f%%

            === CURRENT TOPIC: %s ===
            • Rule: %s
            • Formula: %s
            • Examples: %s

            === TEACHING GUIDELINES ===
            %s

            === STUDENT'S RESPONSE ===
            "%s"

            === YOUR TASK ===
            1. First, warmly acknowledge their effort
            2. Evaluate their answer and explain any mistakes gently
            3. Show the correct form with clear explanation
            4. Give 2-3 more examples of this grammar point
            5. Provide a memorable tip or trick
            6. End with a simple practice question

            Remember: Be encouraging, clear, and always use English only!
            """,
                level,
                difficulty,
                topic.name,
                topicProgress,
                state != null ? state.exercisesDone : 0,
                state != null ? state.averageScore : 0,
                topic.name,
                topic.description,
                topic.formula,
                String.join("; ", topic.examples),
                levelGuide,
                userInput
        );
    }

    private String getLevelGuide(double level) {
        if (level < BEGINNER_THRESHOLD) {
            return """
                • Use VERY simple language and short sentences
                • Explain each concept step by step
                • Provide plenty of examples
                • Use basic vocabulary only
                • Be extremely encouraging and patient""";
        } else if (level < INTERMEDIATE_THRESHOLD) {
            return """
                • Use clear, moderate-level language
                • Explain rules with examples
                • Introduce some terminology
                • Check understanding with questions
                • Encourage elaboration""";
        } else if (level < ADVANCED_THRESHOLD) {
            return """
                • Use natural, sophisticated language
                • Discuss nuances and exceptions
                • Use proper grammatical terminology
                • Provide complex examples
                • Challenge with edge cases""";
        } else {
            return """
                • Use advanced, nuanced explanations
                • Discuss stylistic choices
                • Compare with similar structures
                • Analyze authentic texts
                • Explore subtle differences in meaning""";
        }
    }

    private String generateDisplayText(String aiResponse, double score,
                                       GrammarState state, GrammarTopic topic) {
        StringBuilder display = new StringBuilder();

        display.append("📚 GRAMMAR PRACTICE\n");
        display.append("═══════════════════════════════════════\n\n");

        if (score >= EXCELLENT_SCORE) {
            display.append("🏆 EXCELLENT! ").append(String.format("%.1f/100", score)).append("\n\n");
        } else if (score >= GOOD_SCORE) {
            display.append("🎯 GOOD JOB! ").append(String.format("%.1f/100", score)).append("\n\n");
        } else if (score >= PASSING_SCORE) {
            display.append("👍 GOOD ATTEMPT! ").append(String.format("%.1f/100", score)).append("\n\n");
        } else {
            display.append("📝 LET'S PRACTICE MORE! ").append(String.format("%.1f/100", score)).append("\n\n");
        }

        display.append(aiResponse).append("\n\n");

        display.append("📈 YOUR PROGRESS\n");
        display.append("────────────────\n");
        display.append(String.format("  Topic mastery: %.1f%%\n",
                state.topicScores.getOrDefault(topic.name, 0.0)));
        display.append(String.format("  Overall average: %.1f%%\n", state.averageScore));
        display.append(String.format("  Exercises completed: %d\n", state.exercisesDone));
        display.append(String.format("  Topics mastered: %d\n\n", state.completedTopics.size()));

        display.append("📝 REMEMBER THE FORMULA\n");
        display.append("──────────────────────\n");
        display.append("  ").append(topic.formula).append("\n\n");

        display.append("💡 QUICK TIP\n");
        display.append("────────────\n");
        display.append("  ").append(getGrammarTip(topic)).append("\n");

        return display.toString();
    }


    private String generateTtsText(String aiResponse, double score,
                                   GrammarState state, GrammarTopic topic) {
        StringBuilder tts = new StringBuilder();

        if (score >= EXCELLENT_SCORE) {
            tts.append("Excellent! ");
        } else if (score >= GOOD_SCORE) {
            tts.append("Good job! ");
        } else if (score >= PASSING_SCORE) {
            tts.append("Good attempt! ");
        } else {
            tts.append("Let's practice more. ");
        }

        tts.append(String.format("Your score is %.1f out of 100. ", score));

        // Краткое изложение AI ответа (первые 150 символов)
        String cleanResponse = aiResponse.replaceAll("[\\n\\r]+", " ").trim();
        if (cleanResponse.length() > 150) {
            cleanResponse = cleanResponse.substring(0, 150) + "... ";
        }
        tts.append(cleanResponse).append(" ");

        tts.append(String.format("Your average score is %.1f percent. ", state.averageScore));
        tts.append(String.format("You have completed %d exercises. ", state.exercisesDone));
        tts.append(String.format("You have mastered %d grammar topics. ", state.completedTopics.size()));

        tts.append("Keep practicing to improve your grammar skills.");

        return tts.toString();
    }

    private String generateTaskDisplayText(GrammarTopic topic, LearningContext context) {
        StringBuilder display = new StringBuilder();

        display.append("📖 TOPIC: ").append(topic.name.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("📝 What you'll learn:\n");
        display.append(topic.description).append("\n\n");

        display.append("📐 Grammar Formula:\n");
        display.append("  ").append(topic.formula).append("\n\n");

        display.append("📋 Examples:\n");
        for (int i = 0; i < Math.min(3, topic.examples.size()); i++) {
            display.append("  • ").append(topic.examples.get(i)).append("\n");
        }
        display.append("\n");

        display.append(String.format("📊 Your level: %.1f/100\n", context.getCurrentLevel()));
        display.append("📈 Difficulty: ").append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append("\n\n");

        display.append("Complete 5 exercises to master this topic!\n");

        return display.toString();
    }

    private String generateTaskTtsText(GrammarTopic topic, LearningContext context) {
        StringBuilder tts = new StringBuilder();

        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic.name, topic.name);

        tts.append("New grammar topic: ").append(topicSpeech).append(". ");
        tts.append(topic.description).append(". ");

        tts.append("The formula is: ").append(topic.formula.replaceAll("[+*/]", " plus ")).append(". ");

        tts.append("Examples include: ");
        for (int i = 0; i < Math.min(2, topic.examples.size()); i++) {
            tts.append(topic.examples.get(i));
            if (i < Math.min(2, topic.examples.size()) - 1) {
                tts.append(", and ");
            }
        }
        tts.append(". ");

        tts.append("Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. Difficulty level is ")
                .append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append(". ");

        tts.append("Complete five exercises to master this topic.");

        return tts.toString();
    }

    private String getGrammarTip(GrammarTopic topic) {
        Map<String, String> tips = new HashMap<>();
        tips.put("Present Simple", "Think of routines and facts: every day, always, never");
        tips.put("Past Simple", "Look for time markers: yesterday, last week, in 2010");
        tips.put("Present Perfect", "Life experiences, recent changes, with 'ever/never'");
        tips.put("Conditionals 0,1,2", "If this, then that - the tense shows how real it is!");
        tips.put("Passive Voice", "Focus on WHAT happened, not WHO did it");

        return tips.getOrDefault(topic.name,
                "Practice with examples until it feels natural!");
    }

    private double evaluateGrammar(String text, String topic, LearningContext context) {
        // В реальном приложении здесь будет вызов AI для оценки
        // Сейчас используем упрощенную логику
        double baseScore = 60 + ThreadLocalRandom.current().nextDouble() * 35;

        double levelBonus = context.getCurrentLevel() / 10;
        baseScore = Math.min(100, baseScore + levelBonus);

        return baseScore;
    }

    private void updateAverageScore(GrammarState state) {
        state.averageScore = state.topicScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private double calculateProgress(GrammarState state) {
        if (state == null) return 0;

        double topicProgress = state.topicScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        double completionBonus = Math.min(20, state.completedTopics.size() * 2);

        return Math.min(100, topicProgress + completionBonus);
    }

    private List<String> generateExerciseExamples(GrammarTopic topic, double level) {
        List<String> examples = new ArrayList<>(topic.examples);

        if (level >= INTERMEDIATE_THRESHOLD) {
            examples.add("Try to create your own example");
            examples.add("Can you explain when to use this structure?");
        }

        if (level >= ADVANCED_THRESHOLD) {
            examples.add("What are the exceptions to this rule?");
            examples.add("How does this compare to similar structures?");
        }

        return examples;
    }

    private List<String> generateGrammarTips(GrammarTopic topic, double level) {
        List<String> tips = new ArrayList<>();

        tips.add(getGrammarTip(topic));

        if (level >= INTERMEDIATE_THRESHOLD) {
            tips.add("Notice how this grammar point changes in different contexts");
        }

        if (level >= ADVANCED_THRESHOLD) {
            tips.add("Pay attention to how native speakers use this naturally");
            tips.add("Try using this structure in complex sentences");
        }

        return tips;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, GrammarState state) {
        if (state == null) return LearningMode.GRAMMAR;

        if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_10 &&
                state.averageScore >= GOOD_SCORE) {
            return LearningMode.CONVERSATION;
        } else if (state.averageScore < PASSING_SCORE) {
            return LearningMode.EXERCISE;
        }

        return LearningMode.GRAMMAR;
    }

    private List<String> generateRecommendations(GrammarState state) {
        List<String> recommendations = new ArrayList<>();

        if (state == null) return recommendations;

        state.topicScores.entrySet().stream()
                .filter(e -> e.getValue() < PASSING_SCORE)
                .limit(2)
                .forEach(e -> {
                    GrammarTopic topic = getTopicByName(e.getKey());
                    recommendations.add("Review: " + topic.name + " - " + topic.description);
                });

        if (state.completedTopics.size() < 3) {
            recommendations.add("Master the basics before moving to advanced topics");
        }

        if (state.getSuccessRate() < 60) {
            recommendations.add("Take your time with each exercise - accuracy matters more than speed");
        }

        if (state.averageScore > 80 && state.completedTopics.size() > 5) {
            recommendations.add("Great progress! Try using these grammar points in conversation");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Keep practicing! Try mixing different grammar topics");
        }

        return recommendations;
    }

    private List<String> getAchievements(GrammarState state) {
        List<String> achievements = new ArrayList<>();

        if (state == null) return achievements;

        if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_20) {
            achievements.add("🏆 Grammar Master - " + state.completedTopics.size() + " topics mastered!");
        } else if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_10) {
            achievements.add("🎓 Grammar Expert - " + state.completedTopics.size() + " topics mastered");
        } else if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_5) {
            achievements.add("📚 Grammar Learner - " + state.completedTopics.size() + " topics mastered");
        }

        if (state.exercisesDone >= ACHIEVEMENT_EXERCISES_200) {
            achievements.add("💪 200+ grammar exercises completed!");
        } else if (state.exercisesDone >= ACHIEVEMENT_EXERCISES_100) {
            achievements.add("🎯 100+ grammar exercises completed!");
        } else if (state.exercisesDone >= ACHIEVEMENT_EXERCISES_50) {
            achievements.add("⭐ 50+ grammar exercises completed!");
        }

        if (state.averageScore >= ACHIEVEMENT_AVG_95) {
            achievements.add("🌟 Near-perfect grammar! " + String.format("%.1f%%", state.averageScore));
        } else if (state.averageScore >= ACHIEVEMENT_AVG_90) {
            achievements.add("📊 Excellent grammar! " + String.format("%.1f%%", state.averageScore));
        } else if (state.averageScore >= ACHIEVEMENT_AVG_85) {
            achievements.add("📈 Strong grammar skills! " + String.format("%.1f%%", state.averageScore));
        }

        if (state.bestScore > 95) {
            achievements.add("🔥 Personal best: " + String.format("%.1f%%", state.bestScore));
        }

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        GrammarState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("completedTopics", new ArrayList<>(state.completedTopics));
        stateMap.put("topicScores", new HashMap<>(state.topicScores));
        stateMap.put("topicAttempts", new HashMap<>(state.topicAttempts));
        stateMap.put("scoreHistory", new ArrayList<>(state.scoreHistory));
        stateMap.put("currentTopic", state.currentTopic);
        stateMap.put("exercisesDone", state.exercisesDone);
        stateMap.put("correctAnswers", state.correctAnswers);
        stateMap.put("averageScore", state.averageScore);
        stateMap.put("bestScore", state.bestScore);
        stateMap.put("totalTimeSpent", state.totalTimeSpent);

        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        GrammarState state = new GrammarState();

        @SuppressWarnings("unchecked")
        List<String> completedTopics = (List<String>) stateMap.getOrDefault("completedTopics", Collections.emptyList());
        state.completedTopics.addAll(completedTopics);

        @SuppressWarnings("unchecked")
        Map<String, Double> topicScores = (Map<String, Double>) stateMap.getOrDefault("topicScores", Collections.emptyMap());
        state.topicScores.putAll(topicScores);

        @SuppressWarnings("unchecked")
        Map<String, Integer> topicAttempts = (Map<String, Integer>) stateMap.getOrDefault("topicAttempts", Collections.emptyMap());
        state.topicAttempts.putAll(topicAttempts);

        @SuppressWarnings("unchecked")
        List<Double> scoreHistory = (List<Double>) stateMap.getOrDefault("scoreHistory", Collections.emptyList());
        state.scoreHistory.addAll(scoreHistory);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.exercisesDone = (int) stateMap.getOrDefault("exercisesDone", 0);
        state.correctAnswers = (int) stateMap.getOrDefault("correctAnswers", 0);
        state.averageScore = (double) stateMap.getOrDefault("averageScore", 0.0);
        state.bestScore = (double) stateMap.getOrDefault("bestScore", 0.0);
        state.totalTimeSpent = (long) stateMap.getOrDefault("totalTimeSpent", 0L);

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        GrammarState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("topicsMastered", state.completedTopics.size());
        stats.put("exercisesCompleted", state.exercisesDone);
        stats.put("averageScore", state.averageScore);
        stats.put("bestScore", state.bestScore);
        stats.put("successRate", state.getSuccessRate());
        stats.put("averageTimeMs", state.getAverageTimePerExercise());
        stats.put("currentTopic", state.currentTopic);

        return stats;
    }
}