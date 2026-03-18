package com.mygitgor.ai.learning.strategy;

import com.mygitgor.ai.AiService;
import com.mygitgor.ai.learning.*;
import com.mygitgor.model.LearningContext;
import com.mygitgor.model.LearningMode;
import com.mygitgor.model.LearningResponse;
import com.mygitgor.model.LearningTask;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDate;

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

    private static final Duration SESSION_TIMEOUT = Duration.ofHours(2);

    private static final double EMA_WEIGHT = 0.3;

    private static final Map<String, String>           TOPIC_TO_SPEECH = new HashMap<>();
    private static final Map<String, List<GrammarTopic>> grammarTopics = new HashMap<>();

    static {
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

        TOPIC_TO_SPEECH.put("Present Simple",        "present simple");
        TOPIC_TO_SPEECH.put("Past Simple",           "past simple");
        TOPIC_TO_SPEECH.put("Future Simple (will)",  "future simple");
        TOPIC_TO_SPEECH.put("Articles (a/an/the)",   "articles");
        TOPIC_TO_SPEECH.put("Prepositions of Time",  "prepositions of time");
        TOPIC_TO_SPEECH.put("There is/are",          "there is and there are");
        TOPIC_TO_SPEECH.put("Present Perfect",       "present perfect");
        TOPIC_TO_SPEECH.put("Past Continuous",       "past continuous");
        TOPIC_TO_SPEECH.put("Conditionals 0,1,2",    "conditionals");
        TOPIC_TO_SPEECH.put("Passive Voice",         "passive voice");
        TOPIC_TO_SPEECH.put("Reported Speech",       "reported speech");
        TOPIC_TO_SPEECH.put("Modal Verbs",           "modal verbs");
        TOPIC_TO_SPEECH.put("Past Perfect",          "past perfect");
        TOPIC_TO_SPEECH.put("Future Perfect",        "future perfect");
        TOPIC_TO_SPEECH.put("Conditionals 3",        "third conditionals");
        TOPIC_TO_SPEECH.put("Mixed Conditionals",    "mixed conditionals");
        TOPIC_TO_SPEECH.put("Inversion",             "inversion");
        TOPIC_TO_SPEECH.put("Causative Forms",       "causative forms");
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
    }

    private static class GrammarState {
        final List<String> completedTopics = new ArrayList<>();
        final Map<String, Double> topicScores = new HashMap<>();
        final Map<String, int[]> topicAttempts = new HashMap<>();
        final List<Double> scoreHistory = new ArrayList<>();

        String currentTopic;
        int exercisesDone;
        int correctAnswers;
        double averageScore;
        double bestScore;
        long totalTimeSpent;
        long sessionStartTime;

        Instant lastActivity = Instant.now();

        void startExercise() {
            sessionStartTime = System.currentTimeMillis();
        }

        void endExercise(boolean correct) {
            if (sessionStartTime > 0) {
                totalTimeSpent  += System.currentTimeMillis() - sessionStartTime;
                sessionStartTime = 0;
            }
            exercisesDone++;
            if (correct) correctAnswers++;
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
        this.executor  = ThreadPoolManager.getInstance().getBackgroundExecutor();
        startSessionCleanup();
        logger.info("GrammarStrategy инициализирована с {} уровнями", grammarTopics.size());
    }

    private void startSessionCleanup() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grammar-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            Instant cutoff = Instant.now().minus(SESSION_TIMEOUT);
            int removed = 0;
            for (Map.Entry<String, GrammarState> entry : sessions.entrySet()) {
                if (entry.getValue().lastActivity.isBefore(cutoff)) {
                    sessions.remove(entry.getKey());
                    removed++;
                }
            }
            if (removed > 0) logger.info("Очищено {} устаревших грамматических сессий", removed);
        }, 30, 30, TimeUnit.MINUTES);
    }

    @Override
    public LearningMode getMode() { return LearningMode.GRAMMAR; }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new GrammarState());

            state.lastActivity = Instant.now();

            if (state.currentTopic == null) {
                state.currentTopic = getNextTopic(context, state).name;
            }

            GrammarTopic currentTopic = getTopicByName(state.currentTopic);
            state.startExercise();

            String aiResponse = aiService.generateBotResponse(
                    buildGrammarPrompt(userInput, state, context, currentTopic), null);

            double score = evaluateGrammar(userInput, state.currentTopic, context);
            boolean isCorrect = score >= PASSING_SCORE;

            state.endExercise(isCorrect);

            updateTopicScore(state, state.currentTopic, score);

            state.scoreHistory.add(score);
            if (state.scoreHistory.size() > 20) state.scoreHistory.remove(0);

            if (score > state.bestScore) state.bestScore = score;

            if (!state.completedTopics.contains(state.currentTopic)) {
                int[] attempts = state.topicAttempts.getOrDefault(state.currentTopic, new int[]{0, 0});
                double topicAvg = state.topicScores.getOrDefault(state.currentTopic, 0.0);
                if (attempts[0] >= 3 && topicAvg >= GOOD_SCORE) {
                    state.completedTopics.add(state.currentTopic);
                    state.currentTopic = getNextTopic(context, state).name;
                }
            }

            updateAverageScore(state);

            return LearningResponse.builder()
                    .message(generateDisplayText(aiResponse, score, state, currentTopic))
                    .ttsText(generateTtsText(aiResponse, score, state, currentTopic))
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
            GrammarTopic currentTopic = (state != null && state.currentTopic != null)
                    ? getTopicByName(state.currentTopic)
                    : getNextTopic(context, state);

            String aiResponse = aiService.generateBotResponse(
                    buildGrammarPrompt(userInput, state, context, currentTopic), null);

            return generateTtsText(aiResponse,
                    state != null ? state.averageScore : 0,
                    state, currentTopic);
        }, executor);
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

        String cleanResponse = extractMainGrammarMessage(aiResponse);
        if (!cleanResponse.isEmpty()) {
            tts.append(cleanResponse).append(" ");
        }

        if (state != null && state.currentTopic != null) {
            GrammarTopic nextTopic = getTopicByName(state.currentTopic);
            tts.append("Next topic: ").append(nextTopic.name).append(". ");
        }

        tts.append("Keep practicing to improve your grammar skills.");

        return tts.toString();
    }

    private String extractMainGrammarMessage(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return "";
        }

        String[] statsMarkers = {
                "YOUR PROGRESS",
                "📈 YOUR PROGRESS",
                "REMEMBER THE FORMULA",
                "📝 REMEMBER THE FORMULA",
                "QUICK TIP",
                "💡 QUICK TIP",
                "═══════════════════════════════════════",
                "────────────────"
        };

        String mainMessage = fullResponse;
        for (String marker : statsMarkers) {
            int markerIndex = fullResponse.indexOf(marker);
            if (markerIndex != -1) {
                mainMessage = fullResponse.substring(0, markerIndex).trim();
                break;
            }
        }

        mainMessage = mainMessage.replace("📚 GRAMMAR PRACTICE", "")
                .replace("GRAMMAR PRACTICE", "")
                .trim();

        mainMessage = mainMessage.replaceAll("[\\n\\r]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (mainMessage.length() > 200) {
            mainMessage = mainMessage.substring(0, 200) + "... ";
        }

        return mainMessage;
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            GrammarState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("average_score",      state.averageScore);
                skills.put("best_score",         state.bestScore);
                skills.put("success_rate",       state.getSuccessRate());
                skills.put("topics_completed",   (double) state.completedTopics.size());
                skills.put("total_exercises",    (double) state.exercisesDone);
                skills.put("avg_time_ms",        state.getAverageTimePerExercise());

                state.topicScores.forEach((topic, score) ->
                        skills.put("topic_" + topic, score));

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

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.exercisesDone : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(getAchievements(state))
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
            topic = getNextTopic(context, state);
            if (state != null) state.currentTopic = topic.name;
        }

        return LearningTask.builder()
                .id("gram_" + System.currentTimeMillis())
                .title("📚 Grammar: " + topic.name)
                .description(generateTaskDisplayText(topic, context))
                .ttsDescription(generateTaskTtsText(topic, context))
                .mode(LearningMode.GRAMMAR)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(generateExerciseExamples(topic, context.getCurrentLevel()))
                .metadata(Map.of(
                        "topic",            topic.name,
                        "formula",          topic.formula,
                        "exercises_count",  5,
                        "difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel(),
                        "tips",             generateGrammarTips(topic, context.getCurrentLevel())
                ))
                .build();
    }

    @Override public boolean isSupported()    { return aiService.isAvailable(); }
    @Override public String getStrategyName() { return "Grammar Learning Strategy"; }

    private GrammarTopic getNextTopic(LearningContext context, GrammarState state) {
        String level = determineLevel(context.getCurrentLevel());
        List<GrammarTopic> topics = grammarTopics.get(level);

        if (state == null) {
            return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
        }

        Optional<GrammarTopic> weakTopic = topics.stream()
                .filter(t -> !state.completedTopics.contains(t.name))
                .filter(t -> state.topicScores.containsKey(t.name)
                        && state.topicScores.get(t.name) < PASSING_SCORE)
                .min(Comparator.comparingDouble(t -> state.topicScores.getOrDefault(t.name, 0.0)));
        if (weakTopic.isPresent()) return weakTopic.get();

        List<GrammarTopic> fresh = topics.stream()
                .filter(t -> !state.topicScores.containsKey(t.name))
                .collect(java.util.stream.Collectors.toList());
        if (!fresh.isEmpty()) {
            return fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));
        }

        List<GrammarTopic> inProgress = topics.stream()
                .filter(t -> !state.completedTopics.contains(t.name))
                .collect(java.util.stream.Collectors.toList());
        if (!inProgress.isEmpty()) {
            return inProgress.get(ThreadLocalRandom.current().nextInt(inProgress.size()));
        }

        return topics.stream()
                .min(Comparator.comparingDouble(t -> state.topicScores.getOrDefault(t.name, 0.0)))
                .orElse(topics.get(0));
    }

    private GrammarTopic getTopicByName(String name) {
        for (List<GrammarTopic> topics : grammarTopics.values()) {
            for (GrammarTopic topic : topics) {
                if (topic.name.equals(name)) return topic;
            }
        }
        return grammarTopics.get("beginner").get(0);
    }

    private String determineLevel(double level) {
        if (level < BEGINNER_THRESHOLD)     return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private double evaluateGrammar(String userInput, String topicName, LearningContext context) {
        String prompt = String.format("""
            You are an English grammar evaluator. Evaluate the student's answer strictly.
            
            Grammar topic: %s
            Student level: %.1f/100 (%s)
            Student answer: "%s"
            
            Respond with ONLY a JSON object in this exact format (no other text):
            {"score": <number 0-100>, "correct": <true/false>, "feedback": "<one sentence>"}
            
            Score guide:
            - 90-100: Perfect grammar, correct form
            - 70-89: Minor mistakes, mostly correct
            - 50-69: Partially correct, significant errors
            - 0-49: Incorrect or irrelevant answer
            """,
                topicName,
                context.getCurrentLevel(),
                determineLevel(context.getCurrentLevel()),
                userInput
        );

        try {
            String aiResp = aiService.generateBotResponse(prompt, null);
            String clean = aiResp.replaceAll("```json|```", "").trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"score\"\\s*:\\s*(\\d+(?:\\.\\d+)?)")
                    .matcher(clean);
            if (m.find()) {
                double score = Double.parseDouble(m.group(1));
                return Math.min(100, Math.max(0, score));
            }
        } catch (Exception e) {
            logger.warn("Не удалось разобрать ответ AI для оценки грамматики: {}", e.getMessage());
        }

        return 60.0;
    }

    private void updateTopicScore(GrammarState state, String topicName, double newScore) {
        int[] counters = state.topicAttempts.computeIfAbsent(topicName, k -> new int[]{0, 0});
        counters[0]++;
        if (newScore >= PASSING_SCORE) counters[1]++;

        double currentAvg = state.topicScores.getOrDefault(topicName, 0.0);
        int n = counters[0];
        double updatedAvg = currentAvg + (newScore - currentAvg) / n;
        state.topicScores.put(topicName, updatedAvg);
    }

    private void updateAverageScore(GrammarState state) {
        if (state.topicScores.isEmpty()) {
            state.averageScore = 0;
            return;
        }
        double weightedSum = 0;
        int totalAttempts = 0;
        for (Map.Entry<String, Double> entry : state.topicScores.entrySet()) {
            int attempts = state.topicAttempts.containsKey(entry.getKey())
                    ? state.topicAttempts.get(entry.getKey())[0] : 1;
            weightedSum += entry.getValue() * attempts;
            totalAttempts += attempts;
        }
        state.averageScore = totalAttempts > 0 ? weightedSum / totalAttempts : 0;
    }

    private String buildGrammarPrompt(String userInput, GrammarState state,
                                      LearningContext context, GrammarTopic topic) {
        double level        = context.getCurrentLevel();
        double topicProgress = (state != null && state.currentTopic != null)
                ? state.topicScores.getOrDefault(state.currentTopic, 0.0) : 0.0;

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
                level, determineLevel(level),
                topic.name,
                topicProgress,
                state != null ? state.exercisesDone : 0,
                state != null ? state.averageScore   : 0,
                topic.name, topic.description, topic.formula,
                String.join("; ", topic.examples),
                getLevelGuide(level),
                userInput
        );
    }

    private String getLevelGuide(double level) {
        if (level < BEGINNER_THRESHOLD) return """
                • Use VERY simple language and short sentences
                • Explain each concept step by step
                • Provide plenty of examples
                • Use basic vocabulary only
                • Be extremely encouraging and patient""";
        if (level < INTERMEDIATE_THRESHOLD) return """
                • Use clear, moderate-level language
                • Explain rules with examples
                • Introduce some terminology
                • Check understanding with questions
                • Encourage elaboration""";
        if (level < ADVANCED_THRESHOLD) return """
                • Use natural, sophisticated language
                • Discuss nuances and exceptions
                • Use proper grammatical terminology
                • Provide complex examples
                • Challenge with edge cases""";
        return """
                • Use advanced, nuanced explanations
                • Discuss stylistic choices
                • Compare with similar structures
                • Analyze authentic texts
                • Explore subtle differences in meaning""";
    }

    private String generateDisplayText(String aiResponse, double score,
                                       GrammarState state, GrammarTopic topic) {
        String header = score >= EXCELLENT_SCORE ? "🏆 EXCELLENT! "
                : score >= GOOD_SCORE      ? "🎯 GOOD JOB! "
                : score >= PASSING_SCORE   ? "👍 GOOD ATTEMPT! "
                :                            "📝 LET'S PRACTICE MORE! ";

        return "📚 GRAMMAR PRACTICE\n" +
                "═══════════════════════════════════════\n\n" +
                header + String.format("%.1f/100%n%n", score) +
                aiResponse + "\n\n" +
                "📈 YOUR PROGRESS\n" +
                "────────────────\n" +
                String.format("  Topic mastery: %.1f%%%n",
                        state.topicScores.getOrDefault(topic.name, 0.0)) +
                String.format("  Overall average: %.1f%%%n", state.averageScore) +
                String.format("  Exercises completed: %d%n", state.exercisesDone) +
                String.format("  Topics mastered: %d%n%n", state.completedTopics.size()) +
                "📝 REMEMBER THE FORMULA\n" +
                "──────────────────────\n" +
                "  " + topic.formula + "\n\n" +
                "💡 QUICK TIP\n" +
                "────────────\n" +
                "  " + getGrammarTip(topic) + "\n";
    }

    private String generateTaskDisplayText(GrammarTopic topic, LearningContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 TOPIC: ").append(topic.name.toUpperCase()).append("\n");
        sb.append("═══════════════════════════════════════\n\n");
        sb.append("📝 What you'll learn:\n").append(topic.description).append("\n\n");
        sb.append("📐 Grammar Formula:\n  ").append(topic.formula).append("\n\n");
        sb.append("📋 Examples:\n");
        for (int i = 0; i < Math.min(3, topic.examples.size()); i++) {
            sb.append("  • ").append(topic.examples.get(i)).append("\n");
        }
        sb.append(String.format("%n📊 Your level: %.1f/100%n", context.getCurrentLevel()));
        sb.append("📈 Difficulty: ").append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append("\n\n");
        sb.append("Complete 5 exercises to master this topic!\n");
        return sb.toString();
    }

    private String generateTaskTtsText(GrammarTopic topic, LearningContext context) {
        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic.name, topic.name);
        StringBuilder tts = new StringBuilder();
        tts.append("New grammar topic: ").append(topicSpeech).append(". ");
        tts.append(topic.description).append(". ");
        tts.append("The formula is: ").append(topic.formula.replaceAll("[+*/]", " plus ")).append(". ");
        tts.append("Examples include: ");
        for (int i = 0; i < Math.min(2, topic.examples.size()); i++) {
            tts.append(topic.examples.get(i));
            if (i < Math.min(2, topic.examples.size()) - 1) tts.append(", and ");
        }
        tts.append(". Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. Difficulty level is ")
                .append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append(". ");
        tts.append("Complete five exercises to master this topic.");
        return tts.toString();
    }

    private String getGrammarTip(GrammarTopic topic) {
        Map<String, String> tips = new HashMap<>();
        tips.put("Present Simple",       "Think of routines and facts: every day, always, never");
        tips.put("Past Simple",          "Look for time markers: yesterday, last week, in 2010");
        tips.put("Present Perfect",      "Life experiences, recent changes, with 'ever/never'");
        tips.put("Conditionals 0,1,2",   "If this, then that - the tense shows how real it is!");
        tips.put("Passive Voice",        "Focus on WHAT happened, not WHO did it");
        return tips.getOrDefault(topic.name, "Practice with examples until it feels natural!");
    }

    private double calculateProgress(GrammarState state) {
        if (state == null) return 0;

        double topicProgress    = state.averageScore;
        double completionBonus  = Math.min(20, state.completedTopics.size() * 2.0);

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
        if (level >= INTERMEDIATE_THRESHOLD)
            tips.add("Notice how this grammar point changes in different contexts");
        if (level >= ADVANCED_THRESHOLD) {
            tips.add("Pay attention to how native speakers use this naturally");
            tips.add("Try using this structure in complex sentences");
        }
        return tips;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD)     return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD)     return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, GrammarState state) {
        if (state == null) return LearningMode.GRAMMAR;
        if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_10
                && state.averageScore >= GOOD_SCORE)  return LearningMode.CONVERSATION;
        if (state.averageScore < PASSING_SCORE)        return LearningMode.EXERCISE;
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

        if (state.completedTopics.size() < 3)
            recommendations.add("Master the basics before moving to advanced topics");
        if (state.getSuccessRate() < 60)
            recommendations.add("Take your time with each exercise - accuracy matters more than speed");
        if (state.averageScore > 80 && state.completedTopics.size() > 5)
            recommendations.add("Great progress! Try using these grammar points in conversation");
        if (recommendations.isEmpty())
            recommendations.add("Keep practicing! Try mixing different grammar topics");

        return recommendations;
    }

    private List<String> getAchievements(GrammarState state) {
        List<String> achievements = new ArrayList<>();
        if (state == null) return achievements;

        if      (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_20)
            achievements.add("🏆 Grammar Master - " + state.completedTopics.size() + " topics mastered!");
        else if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_10)
            achievements.add("🎓 Grammar Expert - " + state.completedTopics.size() + " topics mastered");
        else if (state.completedTopics.size() >= ACHIEVEMENT_TOPICS_5)
            achievements.add("📚 Grammar Learner - " + state.completedTopics.size() + " topics mastered");

        if      (state.exercisesDone >= ACHIEVEMENT_EXERCISES_200)
            achievements.add("💪 200+ grammar exercises completed!");
        else if (state.exercisesDone >= ACHIEVEMENT_EXERCISES_100)
            achievements.add("🎯 100+ grammar exercises completed!");
        else if (state.exercisesDone >= ACHIEVEMENT_EXERCISES_50)
            achievements.add("⭐ 50+ grammar exercises completed!");

        if      (state.averageScore >= ACHIEVEMENT_AVG_95)
            achievements.add("🌟 Near-perfect grammar! " + String.format("%.1f%%", state.averageScore));
        else if (state.averageScore >= ACHIEVEMENT_AVG_90)
            achievements.add("📊 Excellent grammar! " + String.format("%.1f%%", state.averageScore));
        else if (state.averageScore >= ACHIEVEMENT_AVG_85)
            achievements.add("📈 Strong grammar skills! " + String.format("%.1f%%", state.averageScore));

        if (state.bestScore > 95)
            achievements.add("🔥 Personal best: " + String.format("%.1f%%", state.bestScore));

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        GrammarState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("completedTopics", new ArrayList<>(state.completedTopics));
        stateMap.put("topicScores",     new HashMap<>(state.topicScores));
        stateMap.put("topicAttempts",   new HashMap<>(state.topicAttempts));
        stateMap.put("scoreHistory",    new ArrayList<>(state.scoreHistory));
        stateMap.put("currentTopic",    state.currentTopic);
        stateMap.put("exercisesDone",   state.exercisesDone);
        stateMap.put("correctAnswers",  state.correctAnswers);
        stateMap.put("averageScore",    state.averageScore);
        stateMap.put("bestScore",       state.bestScore);
        stateMap.put("totalTimeSpent",  state.totalTimeSpent);
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
        Map<String, int[]> topicAttempts = (Map<String, int[]>) stateMap.getOrDefault("topicAttempts", Collections.emptyMap());
        state.topicAttempts.putAll(topicAttempts);

        @SuppressWarnings("unchecked")
        List<Double> scoreHistory = (List<Double>) stateMap.getOrDefault("scoreHistory", Collections.emptyList());
        state.scoreHistory.addAll(scoreHistory);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.exercisesDone = (int) stateMap.getOrDefault("exercisesDone",0);
        state.correctAnswers = (int) stateMap.getOrDefault("correctAnswers",0);
        state.averageScore = (double) stateMap.getOrDefault("averageScore",0.0);
        state.bestScore = (double) stateMap.getOrDefault("bestScore",0.0);
        state.totalTimeSpent = (long) stateMap.getOrDefault("totalTimeSpent", 0L);
        state.lastActivity = Instant.now();

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние грамматической сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Грамматическая сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        GrammarState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("topicsMastered",    state.completedTopics.size());
        stats.put("exercisesCompleted", state.exercisesDone);
        stats.put("averageScore",      state.averageScore);
        stats.put("bestScore",         state.bestScore);
        stats.put("successRate",       state.getSuccessRate());
        stats.put("averageTimeMs",     state.getAverageTimePerExercise());
        stats.put("currentTopic",      state.currentTopic);
        return stats;
    }
}