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

public class ListeningStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ListeningStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, ListeningState> sessions = new ConcurrentHashMap<>();

    private static final double BEGINNER_THRESHOLD = 30.0;
    private static final double INTERMEDIATE_THRESHOLD = 60.0;
    private static final double ADVANCED_THRESHOLD = 85.0;

    private static final double GOOD_COMPREHENSION = 70.0;
    private static final double EXCELLENT_COMPREHENSION = 85.0;

    private static final int    ACHIEVEMENT_EXERCISES_10 = 10;
    private static final int    ACHIEVEMENT_EXERCISES_25 = 25;
    private static final int    ACHIEVEMENT_EXERCISES_50 = 50;
    private static final int    ACHIEVEMENT_EXERCISES_100 = 100;
    private static final double ACHIEVEMENT_COMPREHENSION_70 = 70.0;
    private static final double ACHIEVEMENT_COMPREHENSION_85 = 85.0;
    private static final double ACHIEVEMENT_COMPREHENSION_95 = 95.0;

    private static final Duration SESSION_TIMEOUT = Duration.ofHours(2);

    private static final int MAX_SCORE_HISTORY = 30;
    private static final int MAX_TOPICS_HISTORY = 50;

    private static final Map<String, String> TOPIC_TO_SPEECH = new HashMap<>();
    private static final Map<String, List<ListeningTopic>> listeningTopics = new HashMap<>();

    static {
        listeningTopics.put("beginner", Arrays.asList(
                new ListeningTopic("Simple Greetings",
                        "Basic introductions and greetings", "slow", 30,
                        Arrays.asList("Hello, how are you?", "My name is...", "Nice to meet you")),
                new ListeningTopic("Daily Routines",
                        "Everyday activities and habits", "slow", 45,
                        Arrays.asList("I wake up at 7am", "I have breakfast", "I go to work")),
                new ListeningTopic("Shopping Dialogues",
                        "Simple conversations in shops", "slow", 40,
                        Arrays.asList("How much is this?", "I'd like to buy...", "Here's your change")),
                new ListeningTopic("Weather Reports",
                        "Basic weather descriptions", "slow", 35,
                        Arrays.asList("It's sunny today", "The temperature is 20 degrees", "It might rain")),
                new ListeningTopic("Family Descriptions",
                        "Talking about family members", "slow", 40,
                        Arrays.asList("I have two brothers", "My mother is a teacher", "We live together")),
                new ListeningTopic("Food and Restaurants",
                        "Ordering food and simple restaurant talk", "slow", 45,
                        Arrays.asList("I'd like a pizza", "Can I have the menu?", "The food is delicious"))
        ));

        listeningTopics.put("intermediate", Arrays.asList(
                new ListeningTopic("News Summaries",
                        "Short news reports on current events", "normal", 60,
                        Arrays.asList("Breaking news: major event", "According to reports", "Experts say that")),
                new ListeningTopic("Podcast Excerpts",
                        "Excerpts from popular podcasts", "normal", 90,
                        Arrays.asList("Welcome to our show", "Today we're discussing", "Let's hear from our guest")),
                new ListeningTopic("Movie Dialogues",
                        "Conversations from films", "normal", 75,
                        Arrays.asList("I can't believe it", "What happened next?", "That's incredible")),
                new ListeningTopic("Interview Segments",
                        "Job interviews and celebrity interviews", "normal", 80,
                        Arrays.asList("Tell me about yourself", "What are your strengths?", "Why do you want this job?")),
                new ListeningTopic("Lectures",
                        "Short academic lectures", "normal", 120,
                        Arrays.asList("Today's topic is...", "As we can see", "In conclusion")),
                new ListeningTopic("Phone Conversations",
                        "Realistic phone dialogues", "normal", 60,
                        Arrays.asList("Hello, who's calling?", "I'll call you back", "Can you hear me?"))
        ));

        listeningTopics.put("advanced", Arrays.asList(
                new ListeningTopic("Academic Lectures",
                        "University-level lectures", "fast", 180,
                        Arrays.asList("The hypothesis suggests", "Empirical evidence shows", "This theory posits")),
                new ListeningTopic("Business Meetings",
                        "Professional meeting discussions", "fast", 150,
                        Arrays.asList("Let's review the agenda", "Quarterly results", "Moving forward")),
                new ListeningTopic("Debates",
                        "Formal debates on complex topics", "fast", 200,
                        Arrays.asList("I'd like to rebut", "My opponent claims", "The evidence suggests")),
                new ListeningTopic("Documentaries",
                        "Narration from documentaries", "fast", 180,
                        Arrays.asList("Throughout history", "Scientists have discovered", "This phenomenon occurs")),
                new ListeningTopic("Technical Presentations",
                        "Specialized technical talks", "fast", 240,
                        Arrays.asList("The architecture consists of", "Implementation details", "Performance metrics")),
                new ListeningTopic("Accented Speech",
                        "Various English accents", "fast", 150,
                        Arrays.asList("Different pronunciations", "Regional variations", "Global English"))
        ));

        TOPIC_TO_SPEECH.put("Simple Greetings",        "simple greetings");
        TOPIC_TO_SPEECH.put("Daily Routines",           "daily routines");
        TOPIC_TO_SPEECH.put("Shopping Dialogues",       "shopping dialogues");
        TOPIC_TO_SPEECH.put("Weather Reports",          "weather reports");
        TOPIC_TO_SPEECH.put("Family Descriptions",      "family descriptions");
        TOPIC_TO_SPEECH.put("Food and Restaurants",     "food and restaurants");
        TOPIC_TO_SPEECH.put("News Summaries",           "news summaries");
        TOPIC_TO_SPEECH.put("Podcast Excerpts",         "podcast excerpts");
        TOPIC_TO_SPEECH.put("Movie Dialogues",          "movie dialogues");
        TOPIC_TO_SPEECH.put("Interview Segments",       "interview segments");
        TOPIC_TO_SPEECH.put("Lectures",                 "lectures");
        TOPIC_TO_SPEECH.put("Phone Conversations",      "phone conversations");
        TOPIC_TO_SPEECH.put("Academic Lectures",        "academic lectures");
        TOPIC_TO_SPEECH.put("Business Meetings",        "business meetings");
        TOPIC_TO_SPEECH.put("Debates",                  "debates");
        TOPIC_TO_SPEECH.put("Documentaries",            "documentaries");
        TOPIC_TO_SPEECH.put("Technical Presentations",  "technical presentations");
        TOPIC_TO_SPEECH.put("Accented Speech",          "accented speech");
    }

    private static class ListeningTopic {
        final String name;
        final String description;
        final String defaultSpeed;
        final int durationSeconds;
        final List<String> keywords;

        ListeningTopic(String name, String description, String defaultSpeed,
                       int durationSeconds, List<String> keywords) {
            this.name = name;
            this.description = description;
            this.defaultSpeed = defaultSpeed;
            this.durationSeconds = durationSeconds;
            this.keywords = keywords;
        }
    }

    private static class ListeningState {
        final List<Double> comprehensionScores = new ArrayList<>();
        final List<String> practicedTopics = new ArrayList<>();
        final Map<String, Double> topicScores = new HashMap<>();
        final Map<String, int[]>  topicAttempts = new HashMap<>();
        final List<Long> responseTimes = new ArrayList<>();

        String currentTopic;
        int exercisesCompleted;
        int correctKeyPoints;
        double averageComprehension;
        double bestScore;
        String lastTranscript;
        long totalTimeSpent;
        long sessionStartTime;

        Instant lastActivity = Instant.now();

        void startExercise() {
            sessionStartTime = System.currentTimeMillis();
        }

        void endExercise(boolean correct) {
            if (sessionStartTime > 0) {
                totalTimeSpent += System.currentTimeMillis() - sessionStartTime;
                sessionStartTime = 0;
            }
            exercisesCompleted++;
            if (correct) correctKeyPoints++;
        }

        double getSuccessRate() {
            return exercisesCompleted > 0 ? (double) correctKeyPoints / exercisesCompleted * 100 : 0;
        }

        double getAverageResponseTime() {
            if (responseTimes.isEmpty()) return 0;
            return responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }

    public ListeningStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        startSessionCleanup();
        logger.info("ListeningStrategy инициализирована с {} уровнями", listeningTopics.size());
    }

    private void startSessionCleanup() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "listening-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            Instant cutoff = Instant.now().minus(SESSION_TIMEOUT);
            int removed = 0;
            for (Map.Entry<String, ListeningState> entry : sessions.entrySet()) {
                if (entry.getValue().lastActivity.isBefore(cutoff)) {
                    sessions.remove(entry.getKey());
                    removed++;
                }
            }
            if (removed > 0) logger.info("Очищено {} устаревших listening-сессий", removed);
        }, 30, 30, TimeUnit.MINUTES);
    }

    @Override
    public LearningMode getMode() { return LearningMode.LISTENING; }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            ListeningState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ListeningState());

            state.lastActivity = Instant.now();

            if (state.currentTopic == null) {
                ListeningTopic topic = getNextTopic(context, state);
                state.currentTopic = topic.name;
                state.lastTranscript = generateTranscript(topic, context.getCurrentLevel());
                addPracticedTopic(state, topic.name);
            }

            ListeningTopic currentTopic = getTopicByName(state.currentTopic);
            state.startExercise();

            double comprehensionScore = evaluateComprehension(userInput, state.lastTranscript, currentTopic, context);
            boolean isCorrect = comprehensionScore >= GOOD_COMPREHENSION;

            long responseTime = System.currentTimeMillis() - startTime;
            state.responseTimes.add(responseTime);
            if (state.responseTimes.size() > 20) state.responseTimes.remove(0);

            state.endExercise(isCorrect);

            state.comprehensionScores.add(comprehensionScore);
            if (state.comprehensionScores.size() > MAX_SCORE_HISTORY) {
                state.comprehensionScores.remove(0);
            }

            if (comprehensionScore > state.bestScore) state.bestScore = comprehensionScore;

            updateTopicScore(state, state.currentTopic, comprehensionScore);
            updateAverageComprehension(state);

            if (isCorrect && comprehensionScore >= EXCELLENT_COMPREHENSION) {
                state.currentTopic   = getNextTopic(context, state).name;
                state.lastTranscript = generateTranscript(getTopicByName(state.currentTopic),
                        context.getCurrentLevel());
                addPracticedTopic(state, state.currentTopic);
            }

            String prompt     = buildListeningPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            return LearningResponse.builder()
                    .message(generateDisplayText(aiResponse, comprehensionScore, state, currentTopic))
                    .ttsText(generateTtsText(aiResponse, comprehensionScore, state, currentTopic))
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
            ListeningState state = sessions.get(context.getUserId());
            ListeningTopic currentTopic = (state != null && state.currentTopic != null)
                    ? getTopicByName(state.currentTopic)
                    : getNextTopic(context, state);

            String prompt = buildListeningPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            return generateTtsText(aiResponse,
                    state != null ? state.averageComprehension : 0,
                    state, currentTopic);
        }, executor);
    }

    private String generateTtsText(String aiResponse, double score,
                                   ListeningState state, ListeningTopic topic) {
        StringBuilder tts = new StringBuilder();

        String cleanResponse = extractMainListeningMessage(aiResponse);

        if (score >= EXCELLENT_COMPREHENSION) {
            tts.append("Excellent comprehension! ");
        } else if (score >= GOOD_COMPREHENSION) {
            tts.append("Good comprehension! ");
        } else {
            tts.append("Let's work on understanding this better. ");
        }

        if (!cleanResponse.isEmpty()) {
            tts.append(cleanResponse);
            if (!cleanResponse.endsWith(". ") && !cleanResponse.endsWith(".")) {
                tts.append(". ");
            } else {
                tts.append(" ");
            }
        }

        if (state != null && state.currentTopic != null) {
            ListeningTopic nextTopic = getTopicByName(state.currentTopic);
            tts.append("Next topic: ").append(nextTopic.name).append(". ");
        }

        tts.append("Keep practicing your listening skills!");

        return tts.toString();
    }

    private String extractMainListeningMessage(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return "";
        }

        String[] statsMarkers = {
                "YOUR RESULTS",
                "📊 YOUR RESULTS",
                "💡 QUICK TIPS",
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

        mainMessage = mainMessage.replace("🎧 LISTENING PRACTICE", "")
                .replace("LISTENING PRACTICE", "")
                .trim();

        mainMessage = mainMessage.replaceAll("[\\n\\r]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (mainMessage.length() > 250) {
            int firstSentenceEnd = mainMessage.indexOf(". ", 150);
            if (firstSentenceEnd != -1 && firstSentenceEnd < 300) {
                mainMessage = mainMessage.substring(0, firstSentenceEnd + 1);
            } else {
                mainMessage = mainMessage.substring(0, 250) + "...";
            }
        }

        return mainMessage;
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ListeningState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("comprehension",     state.averageComprehension);
                skills.put("best_score",        state.bestScore);
                skills.put("success_rate",      state.getSuccessRate());
                skills.put("exercises",         (double) state.exercisesCompleted);
                skills.put("topics_practiced",  (double) state.practicedTopics.size());
                skills.put("avg_response_time", state.getAverageResponseTime());
                state.topicScores.forEach((topic, score) -> skills.put("topic_" + topic, score));
            }

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.exercisesCompleted : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(getAchievements(state))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ListeningState state = sessions.get(context.getUserId());
            return generateNextTaskWithTts(context, state);
        }, executor);
    }

    private LearningTask generateNextTaskWithTts(LearningContext context, ListeningState state) {
        ListeningTopic topic;
        if (state != null && state.currentTopic != null) {
            topic = getTopicByName(state.currentTopic);
        } else {
            topic = getNextTopic(context, state);
            if (state != null) {
                state.currentTopic = topic.name;
                addPracticedTopic(state, topic.name);
            }
        }

        String transcript = (state != null && state.lastTranscript != null)
                ? state.lastTranscript
                : generateTranscript(topic, context.getCurrentLevel());
        String speed = getSpeed(context.getCurrentLevel());

        return LearningTask.builder()
                .id("lis_" + System.currentTimeMillis())
                .title("🎧 Listening: " + topic.name)
                .description(generateTaskDisplayText(topic, context))
                .ttsDescription(generateTaskTtsText(topic, context))
                .mode(LearningMode.LISTENING)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(topic.keywords)
                .metadata(Map.of(
                        "topic",            topic.name,
                        "transcript",       transcript,
                        "speed",            speed,
                        "duration",         topic.durationSeconds,
                        "difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel(),
                        "tips",             generateListeningTips(context.getCurrentLevel())
                ))
                .build();
    }

    @Override public boolean isSupported()    { return aiService.isAvailable(); }
    @Override public String getStrategyName() { return "Listening Practice Strategy"; }

    private ListeningTopic getNextTopic(LearningContext context, ListeningState state) {
        String level = determineLevel(context.getCurrentLevel());
        List<ListeningTopic> topics = listeningTopics.get(level);

        if (state == null) {
            return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
        }

        Optional<ListeningTopic> weakTopic = topics.stream()
                .filter(t -> state.topicScores.containsKey(t.name)
                        && state.topicScores.get(t.name) < GOOD_COMPREHENSION)
                .min(Comparator.comparingDouble(t -> state.topicScores.getOrDefault(t.name, 0.0)));
        if (weakTopic.isPresent()) return weakTopic.get();

        List<ListeningTopic> fresh = topics.stream()
                .filter(t -> !state.topicScores.containsKey(t.name))
                .collect(java.util.stream.Collectors.toList());
        if (!fresh.isEmpty()) {
            return fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));
        }

        return topics.stream()
                .min(Comparator.comparingDouble(t -> state.topicScores.getOrDefault(t.name, 0.0)))
                .orElse(topics.get(0));
    }

    private ListeningTopic getTopicByName(String name) {
        for (List<ListeningTopic> topics : listeningTopics.values()) {
            for (ListeningTopic topic : topics) {
                if (topic.name.equals(name)) return topic;
            }
        }
        return listeningTopics.get("beginner").get(0);
    }

    private String determineLevel(double level) {
        if (level < BEGINNER_THRESHOLD)     return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private String generateTranscript(ListeningTopic topic, double level) {
        String prompt = String.format("""
            Generate a realistic English listening exercise transcript about "%s".
            
            Requirements:
            - Level: %s (%.1f/100)
            - Topic description: %s
            - Speed hint: %s (controls sentence complexity and vocabulary)
            - Duration target: ~%d seconds when read aloud
            - Naturally include these key phrases: %s
            - Use authentic dialogue or narration appropriate for the level
            - Do NOT include any meta-commentary or instructions
            - Output ONLY the transcript text
            """,
                topic.name,
                determineLevel(level), level,
                topic.description,
                topic.defaultSpeed,
                topic.durationSeconds,
                String.join(", ", topic.keywords)
        );

        try {
            String transcript = aiService.generateBotResponse(prompt, null);
            if (transcript != null && !transcript.isBlank()) return transcript.trim();
        } catch (Exception e) {
            logger.warn("Не удалось сгенерировать транскрипт через AI: {}", e.getMessage());
        }

        return String.format("This is a listening exercise about %s. %s Focus on: %s.",
                topic.name, topic.description, String.join(", ", topic.keywords));
    }

    private double evaluateComprehension(String userInput, String transcript,
                                         ListeningTopic topic, LearningContext context) {
        if (userInput == null || userInput.isBlank()) return 0;
        if (transcript == null) return 30;

        String prompt = String.format("""
            You are an English listening comprehension evaluator.
            
            Original transcript:
            ---
            %s
            ---
            
            Student level: %.1f/100 (%s)
            Topic: %s
            Student's answer/summary: "%s"
            
            Evaluate how well the student understood the listening material.
            Respond with ONLY a JSON object (no other text):
            {"score": <0-100>, "understood": ["<key point 1>", ...], "missed": ["<key point 1>", ...], "feedback": "<one sentence>"}
            
            Score guide:
            - 90-100: Excellent, captured main ideas and most details
            - 70-89: Good, understood the main ideas
            - 50-69: Partial, got some ideas but missed important points
            - 0-49: Poor comprehension or irrelevant answer
            """,
                transcript.length() > 800 ? transcript.substring(0, 800) : transcript,
                context.getCurrentLevel(),
                determineLevel(context.getCurrentLevel()),
                topic.name,
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
            logger.warn("Не удалось разобрать ответ AI для оценки comprehension: {}", e.getMessage());
        }

        return 40;
    }

    private void updateTopicScore(ListeningState state, String topicName, double newScore) {
        int[] counters = state.topicAttempts.computeIfAbsent(topicName, k -> new int[]{0, 0});
        counters[0]++;
        double currentAvg = state.topicScores.getOrDefault(topicName, 0.0);
        double updatedAvg = currentAvg + (newScore - currentAvg) / counters[0];
        state.topicScores.put(topicName, updatedAvg);
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

    private void addPracticedTopic(ListeningState state, String topicName) {
        if (!state.practicedTopics.contains(topicName)) {
            state.practicedTopics.add(topicName);
            if (state.practicedTopics.size() > MAX_TOPICS_HISTORY) {
                state.practicedTopics.remove(0);
            }
        }
    }

    private double calculateProgress(ListeningState state) {
        if (state == null) return 0;

        double comprehensionComponent = state.averageComprehension * 0.6;
        double consistencyComponent = Math.min(100, state.exercisesCompleted / 60.0 * 100) * 0.3;
        double varietyComponent = Math.min(100, state.practicedTopics.size() / 10.0 * 100) * 0.1;
        return Math.min(100, comprehensionComponent + consistencyComponent + varietyComponent);
    }

    private String buildListeningPrompt(String userInput, ListeningState state,
                                        LearningContext context, ListeningTopic topic) {
        double level = context.getCurrentLevel();
        double avgComprehension = (state != null) ? state.averageComprehension : 0.0;

        return String.format("""
            You are an expert, warm and highly motivating AI English Listening Tutor.
            Your goal is to help students dramatically improve their listening comprehension.

            === STRICT RULES ===
            - ALWAYS respond ONLY in English
            - Adapt language perfectly to the student's level (%.1f/100)
            - Always start with genuine encouragement
            - Never say "wrong". Use positive reinforcement

            === CURRENT SESSION ===
            • Topic: %s
            • Description: %s
            • Speed: %s
            • Duration: %d seconds
            • Key vocabulary: %s
            
            • Student level: %s
            • Average comprehension: %.1f%%
            • Exercises completed: %d

            === TEACHING GUIDELINES ===
            %s

            === STUDENT'S ANSWER ===
            "%s"

            === YOUR TASK ===
            1. Warm positive opening
            2. Overall comprehension score
            3. What they understood correctly
            4. What was missed (with exact parts from transcript)
            5. Clear explanation of difficult sections
            6. Specific listening tips for this type of content
            7. Motivating closing + follow-up challenge

            Generate a complete, structured listening feedback response.
            """,
                level,
                topic.name, topic.description,
                getSpeed(level), topic.durationSeconds,
                String.join(", ", topic.keywords),
                determineLevel(level),
                avgComprehension,
                state != null ? state.exercisesCompleted : 0,
                getLevelGuide(level),
                userInput
        );
    }

    private String getLevelGuide(double level) {
        if (level < BEGINNER_THRESHOLD) return """
                • Use VERY simple language
                • Focus on catching key words
                • Explain slowly and clearly
                • Provide written support
                • Be extremely encouraging""";
        if (level < INTERMEDIATE_THRESHOLD) return """
                • Use clear, moderate language
                • Focus on main ideas and details
                • Introduce listening strategies
                • Discuss context clues
                • Encourage active listening""";
        if (level < ADVANCED_THRESHOLD) return """
                • Use natural, sophisticated language
                • Focus on nuance and inference
                • Discuss speaker's attitude
                • Analyze rhetorical devices
                • Challenge with complex content""";
        return """
                • Use advanced, nuanced language
                • Analyze subtext and implication
                • Discuss cultural references
                • Focus on advanced comprehension
                • Challenge with authentic materials""";
    }

    private String generateDisplayText(String aiResponse, double score,
                                       ListeningState state, ListeningTopic topic) {
        return "🎧 LISTENING PRACTICE\n" +
                "═══════════════════════════════════════\n\n" +
                aiResponse + "\n\n" +
                "📊 YOUR RESULTS\n" +
                "────────────────\n" +
                String.format("  Comprehension: %.1f%%%n", score) +
                String.format("  Best score: %.1f%%%n", state.bestScore) +
                String.format("  Exercises completed: %d%n", state.exercisesCompleted) +
                String.format("  Current topic: %s%n%n", topic.name) +
                "💡 QUICK TIPS\n" +
                "─────────────\n" +
                "  • Focus on key words you recognize\n" +
                "  • Listen for context clues\n" +
                "  • Don't worry about every single word\n" +
                "  • Practice regularly with varied content\n\n" +
                "Keep going! You're improving with every session 🔥\n";
    }

    private String generateTaskDisplayText(ListeningTopic topic, LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        String speed = getSpeed(context.getCurrentLevel());

        return "🎧 LISTENING EXERCISE: " + topic.name.toUpperCase() + "\n" +
                "═══════════════════════════════════════\n\n" +
                "📝 Description:\n" + topic.description + "\n\n" +
                "📊 Difficulty: " + level + "\n" +
                "⚡ Speed: " + speed + "\n" +
                "⏱️ Duration: " + topic.durationSeconds + " seconds\n\n" +
                "🔑 KEY VOCABULARY\n" +
                "────────────────\n" +
                topic.keywords.stream().map(k -> "  • " + k + "\n").collect(java.util.stream.Collectors.joining()) +
                "\n💡 TIPS\n" +
                "───────\n" +
                "  • Listen for the main idea first\n" +
                "  • Don't pause - try to understand in real-time\n" +
                "  • Focus on words you recognize\n" +
                "  • Try to understand the context\n\n" +
                "Listen carefully and answer the questions that follow!\n";
    }

    private String generateTaskTtsText(ListeningTopic topic, LearningContext context) {
        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic.name, topic.name);
        String speed = getSpeed(context.getCurrentLevel());

        StringBuilder tts = new StringBuilder();
        tts.append("New listening exercise on ").append(topicSpeech).append(". ");
        tts.append(topic.description).append(". ");
        tts.append("Difficulty level is ").append(determineLevel(context.getCurrentLevel())).append(". ");
        tts.append("Speed is ").append(speed).append(". ");
        tts.append("Duration is ").append(topic.durationSeconds).append(" seconds. ");
        tts.append("Key vocabulary includes: ");
        for (int i = 0; i < Math.min(3, topic.keywords.size()); i++) {
            tts.append(topic.keywords.get(i));
            if (i < Math.min(3, topic.keywords.size()) - 1) tts.append(", ");
        }
        tts.append(". Listen carefully and answer the questions.");
        return tts.toString();
    }

    private String getSpeed(double level) {
        if (level < BEGINNER_THRESHOLD)     return "slow";
        if (level < INTERMEDIATE_THRESHOLD) return "normal";
        return "fast";
    }

    private List<String> generateListeningTips(double level) {
        List<String> tips = new ArrayList<>();
        if (level < BEGINNER_THRESHOLD) {
            tips.add("Don't worry about understanding every word");
            tips.add("Focus on catching key vocabulary");
            tips.add("Listen multiple times if needed");
        } else if (level < INTERMEDIATE_THRESHOLD) {
            tips.add("Try to identify the speaker's main point");
            tips.add("Notice how stress and intonation convey meaning");
            tips.add("Predict content based on context");
        } else {
            tips.add("Analyze the speaker's attitude and tone");
            tips.add("Notice rhetorical devices and emphasis");
            tips.add("Consider cultural and contextual implications");
        }
        return tips;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD)     return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD)     return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, ListeningState state) {
        if (state == null) return LearningMode.LISTENING;
        if (state.averageComprehension >= EXCELLENT_COMPREHENSION
                && state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_25) return LearningMode.CONVERSATION;
        if (state.averageComprehension < GOOD_COMPREHENSION)             return LearningMode.PRONUNCIATION;
        return LearningMode.LISTENING;
    }

    private List<String> generateRecommendations(ListeningState state) {
        List<String> recommendations = new ArrayList<>();
        if (state == null) return recommendations;

        if (state.averageComprehension < 50) {
            recommendations.add("Start with slower recordings and focus on key words");
            recommendations.add("Listen to the same recording multiple times");
        } else if (state.averageComprehension < 70) {
            recommendations.add("Try to identify main ideas and supporting details");
            recommendations.add("Practice with transcripts - read along while listening");
        } else if (state.averageComprehension < 85) {
            recommendations.add("Challenge yourself with different accents");
            recommendations.add("Listen to podcasts on topics you enjoy");
        } else {
            recommendations.add("Try listening to fast, authentic content");
            recommendations.add("Focus on nuance, tone, and implicit meaning");
        }

        if (state.exercisesCompleted < 10)
            recommendations.add("Aim for 10-15 minutes of listening practice daily");

        if (recommendations.isEmpty())
            recommendations.add("Great progress! Try varying your listening materials");

        return recommendations;
    }

    private List<String> getAchievements(ListeningState state) {
        List<String> achievements = new ArrayList<>();
        if (state == null) return achievements;

        if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_100) achievements.add("🏆 Listening Master - 100+ exercises!");
        else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_50) achievements.add("🎯 Dedicated Listener - 50+ exercises");
        else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_25) achievements.add("📚 Regular Listener - 25+ exercises");
        else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_10) achievements.add("🎧 Started Listening Journey - 10 exercises");

        if      (state.bestScore >= ACHIEVEMENT_COMPREHENSION_95)
            achievements.add("🔥 Near-perfect comprehension! " + String.format("%.1f%%", state.bestScore));
        else if (state.averageComprehension >= ACHIEVEMENT_COMPREHENSION_85)
            achievements.add("🌟 Excellent comprehension! " + String.format("%.1f%%", state.averageComprehension));
        else if (state.averageComprehension >= ACHIEVEMENT_COMPREHENSION_70)
            achievements.add("📈 Good comprehension! " + String.format("%.1f%%", state.averageComprehension));

        if (state.practicedTopics.size() >= 10)
            achievements.add("🌍 Explored 10+ different listening topics");

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        ListeningState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("comprehensionScores", new ArrayList<>(state.comprehensionScores));
        stateMap.put("practicedTopics",     new ArrayList<>(state.practicedTopics));
        stateMap.put("topicScores",         new HashMap<>(state.topicScores));
        stateMap.put("topicAttempts",       new HashMap<>(state.topicAttempts)); // FIX #3
        stateMap.put("responseTimes",       new ArrayList<>(state.responseTimes));
        stateMap.put("currentTopic",        state.currentTopic);
        stateMap.put("exercisesCompleted",  state.exercisesCompleted);
        stateMap.put("correctKeyPoints",    state.correctKeyPoints);
        stateMap.put("averageComprehension", state.averageComprehension);
        stateMap.put("bestScore",           state.bestScore);
        stateMap.put("lastTranscript",      state.lastTranscript);
        stateMap.put("totalTimeSpent",      state.totalTimeSpent);
        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        ListeningState state = new ListeningState();

        @SuppressWarnings("unchecked")
        List<Double> comprehensionScores = (List<Double>) stateMap.getOrDefault("comprehensionScores", Collections.emptyList());
        state.comprehensionScores.addAll(comprehensionScores);

        @SuppressWarnings("unchecked")
        List<String> practicedTopics = (List<String>) stateMap.getOrDefault("practicedTopics", Collections.emptyList());
        state.practicedTopics.addAll(practicedTopics);

        @SuppressWarnings("unchecked")
        Map<String, Double> topicScores = (Map<String, Double>) stateMap.getOrDefault("topicScores", Collections.emptyMap());
        state.topicScores.putAll(topicScores);

        @SuppressWarnings("unchecked")
        Map<String, int[]> topicAttempts = (Map<String, int[]>) stateMap.getOrDefault("topicAttempts", Collections.emptyMap());
        state.topicAttempts.putAll(topicAttempts);

        @SuppressWarnings("unchecked")
        List<Long> responseTimes = (List<Long>) stateMap.getOrDefault("responseTimes", Collections.emptyList());
        state.responseTimes.addAll(responseTimes);

        state.currentTopic          = (String) stateMap.get("currentTopic");
        state.exercisesCompleted    = (int)    stateMap.getOrDefault("exercisesCompleted",    0);
        state.correctKeyPoints      = (int)    stateMap.getOrDefault("correctKeyPoints",      0);
        state.averageComprehension  = (double) stateMap.getOrDefault("averageComprehension",  0.0);
        state.bestScore             = (double) stateMap.getOrDefault("bestScore",             0.0);
        state.lastTranscript        = (String) stateMap.get("lastTranscript");
        state.totalTimeSpent        = (long)   stateMap.getOrDefault("totalTimeSpent",        0L);
        state.lastActivity          = Instant.now();

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние listening-сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Listening-сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        ListeningState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageComprehension", state.averageComprehension);
        stats.put("bestScore",            state.bestScore);
        stats.put("exercisesCompleted",   state.exercisesCompleted);
        stats.put("topicsExplored",       state.practicedTopics.size());
        stats.put("successRate",          state.getSuccessRate());
        stats.put("averageResponseTime",  state.getAverageResponseTime());
        stats.put("currentTopic",         state.currentTopic);
        return stats;
    }
}