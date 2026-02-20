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

    private static final int ACHIEVEMENT_EXERCISES_10 = 10;
    private static final int ACHIEVEMENT_EXERCISES_25 = 25;
    private static final int ACHIEVEMENT_EXERCISES_50 = 50;
    private static final int ACHIEVEMENT_EXERCISES_100 = 100;
    private static final double ACHIEVEMENT_COMPREHENSION_70 = 70.0;
    private static final double ACHIEVEMENT_COMPREHENSION_85 = 85.0;
    private static final double ACHIEVEMENT_COMPREHENSION_95 = 95.0;

    private static final Map<String, String> TOPIC_TO_SPEECH = new HashMap<>();

    private static final Map<String, List<ListeningTopic>> listeningTopics = new HashMap<>();

    static {
        // (A1-A2)
        listeningTopics.put("beginner", Arrays.asList(
                new ListeningTopic("Simple Greetings",
                        "Basic introductions and greetings",
                        "slow", 30,
                        Arrays.asList("Hello, how are you?", "My name is...", "Nice to meet you")),

                new ListeningTopic("Daily Routines",
                        "Everyday activities and habits",
                        "slow", 45,
                        Arrays.asList("I wake up at 7am", "I have breakfast", "I go to work")),

                new ListeningTopic("Shopping Dialogues",
                        "Simple conversations in shops",
                        "slow", 40,
                        Arrays.asList("How much is this?", "I'd like to buy...", "Here's your change")),

                new ListeningTopic("Weather Reports",
                        "Basic weather descriptions",
                        "slow", 35,
                        Arrays.asList("It's sunny today", "The temperature is 20 degrees", "It might rain")),

                new ListeningTopic("Family Descriptions",
                        "Talking about family members",
                        "slow", 40,
                        Arrays.asList("I have two brothers", "My mother is a teacher", "We live together")),

                new ListeningTopic("Food and Restaurants",
                        "Ordering food and simple restaurant talk",
                        "slow", 45,
                        Arrays.asList("I'd like a pizza", "Can I have the menu?", "The food is delicious"))
        ));

        // (B1-B2)
        listeningTopics.put("intermediate", Arrays.asList(
                new ListeningTopic("News Summaries",
                        "Short news reports on current events",
                        "normal", 60,
                        Arrays.asList("Breaking news: major event", "According to reports", "Experts say that")),

                new ListeningTopic("Podcast Excerpts",
                        "Excerpts from popular podcasts",
                        "normal", 90,
                        Arrays.asList("Welcome to our show", "Today we're discussing", "Let's hear from our guest")),

                new ListeningTopic("Movie Dialogues",
                        "Conversations from films",
                        "normal", 75,
                        Arrays.asList("I can't believe it", "What happened next?", "That's incredible")),

                new ListeningTopic("Interview Segments",
                        "Job interviews and celebrity interviews",
                        "normal", 80,
                        Arrays.asList("Tell me about yourself", "What are your strengths?", "Why do you want this job?")),

                new ListeningTopic("Lectures",
                        "Short academic lectures",
                        "normal", 120,
                        Arrays.asList("Today's topic is...", "As we can see", "In conclusion")),

                new ListeningTopic("Phone Conversations",
                        "Realistic phone dialogues",
                        "normal", 60,
                        Arrays.asList("Hello, who's calling?", "I'll call you back", "Can you hear me?"))
        ));

        // (C1-C2)
        listeningTopics.put("advanced", Arrays.asList(
                new ListeningTopic("Academic Lectures",
                        "University-level lectures",
                        "fast", 180,
                        Arrays.asList("The hypothesis suggests", "Empirical evidence shows", "This theory posits")),

                new ListeningTopic("Business Meetings",
                        "Professional meeting discussions",
                        "fast", 150,
                        Arrays.asList("Let's review the agenda", "Quarterly results", "Moving forward")),

                new ListeningTopic("Debates",
                        "Formal debates on complex topics",
                        "fast", 200,
                        Arrays.asList("I'd like to rebut", "My opponent claims", "The evidence suggests")),

                new ListeningTopic("Documentaries",
                        "Narration from documentaries",
                        "fast", 180,
                        Arrays.asList("Throughout history", "Scientists have discovered", "This phenomenon occurs")),

                new ListeningTopic("Technical Presentations",
                        "Specialized technical talks",
                        "fast", 240,
                        Arrays.asList("The architecture consists of", "Implementation details", "Performance metrics")),

                new ListeningTopic("Accented Speech",
                        "Various English accents",
                        "fast", 150,
                        Arrays.asList("Different pronunciations", "Regional variations", "Global English"))
        ));

        TOPIC_TO_SPEECH.put("Simple Greetings", "simple greetings");
        TOPIC_TO_SPEECH.put("Daily Routines", "daily routines");
        TOPIC_TO_SPEECH.put("Shopping Dialogues", "shopping dialogues");
        TOPIC_TO_SPEECH.put("Weather Reports", "weather reports");
        TOPIC_TO_SPEECH.put("Family Descriptions", "family descriptions");
        TOPIC_TO_SPEECH.put("Food and Restaurants", "food and restaurants");
        TOPIC_TO_SPEECH.put("News Summaries", "news summaries");
        TOPIC_TO_SPEECH.put("Podcast Excerpts", "podcast excerpts");
        TOPIC_TO_SPEECH.put("Movie Dialogues", "movie dialogues");
        TOPIC_TO_SPEECH.put("Interview Segments", "interview segments");
        TOPIC_TO_SPEECH.put("Lectures", "lectures");
        TOPIC_TO_SPEECH.put("Phone Conversations", "phone conversations");
        TOPIC_TO_SPEECH.put("Academic Lectures", "academic lectures");
        TOPIC_TO_SPEECH.put("Business Meetings", "business meetings");
        TOPIC_TO_SPEECH.put("Debates", "debates");
        TOPIC_TO_SPEECH.put("Documentaries", "documentaries");
        TOPIC_TO_SPEECH.put("Technical Presentations", "technical presentations");
        TOPIC_TO_SPEECH.put("Accented Speech", "accented speech");
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
        final List<Long> responseTimes = new ArrayList<>();
        String currentTopic;
        int exercisesCompleted;
        int correctKeyPoints;
        double averageComprehension;
        double bestScore;
        String lastTranscript;
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
            exercisesCompleted++;
            if (correct) {
                correctKeyPoints++;
            }
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
        logger.info("ListeningStrategy инициализирована с {} уровнями", listeningTopics.size());
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.LISTENING;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            ListeningState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ListeningState());

            if (state.currentTopic == null) {
                ListeningTopic topic = getNextTopic(context);
                state.currentTopic = topic.name;
                state.lastTranscript = generateTranscript(topic, context.getCurrentLevel());
            }

            ListeningTopic currentTopic = getTopicByName(state.currentTopic);
            state.startExercise();

            double comprehensionScore = evaluateComprehension(userInput, state.lastTranscript, currentTopic);
            boolean isCorrect = comprehensionScore >= GOOD_COMPREHENSION;

            long responseTime = System.currentTimeMillis() - startTime;
            state.responseTimes.add(responseTime);
            if (state.responseTimes.size() > 20) {
                state.responseTimes.remove(0);
            }

            state.endExercise(isCorrect);
            state.comprehensionScores.add(comprehensionScore);

            if (comprehensionScore > state.bestScore) {
                state.bestScore = comprehensionScore;
            }

            double currentTopicScore = state.topicScores.getOrDefault(state.currentTopic, 0.0);
            state.topicScores.put(state.currentTopic,
                    (currentTopicScore + comprehensionScore) / 2);

            state.exercisesCompleted++;
            updateAverageComprehension(state);

            String prompt = buildListeningPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            String displayText = generateDisplayText(aiResponse, comprehensionScore, state, currentTopic);

            String ttsText = generateTtsText(aiResponse, comprehensionScore, state, currentTopic);

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
            ListeningState state = sessions.get(context.getUserId());
            ListeningTopic currentTopic = state != null && state.currentTopic != null
                    ? getTopicByName(state.currentTopic)
                    : getNextTopic(context);

            String prompt = buildListeningPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            return generateTtsText(aiResponse,
                    state != null ? state.averageComprehension : 0,
                    state, currentTopic);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ListeningState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("comprehension", state.averageComprehension);
                skills.put("best_score", state.bestScore);
                skills.put("success_rate", state.getSuccessRate());
                skills.put("exercises", (double) state.exercisesCompleted);
                skills.put("topics_practiced", (double) state.practicedTopics.size());
                skills.put("avg_response_time", state.getAverageResponseTime());

                state.topicScores.forEach((topic, score) ->
                        skills.put("topic_" + topic, score));
            }

            List<String> achievements = getAchievements(state);

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.exercisesCompleted : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(achievements)
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
            topic = getNextTopic(context);
            if (state != null) {
                state.currentTopic = topic.name;
                state.practicedTopics.add(topic.name);
            }
        }

        String transcript = generateTranscript(topic, context.getCurrentLevel());
        String speed = getSpeed(context.getCurrentLevel(), topic);

        String displayDescription = generateTaskDisplayText(topic, context);

        String ttsDescription = generateTaskTtsText(topic, context);

        return LearningTask.builder()
                .id("lis_" + System.currentTimeMillis())
                .title("🎧 Listening: " + topic.name)
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.LISTENING)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(topic.keywords)
                .metadata(Map.of(
                        "topic", topic.name,
                        "transcript", transcript,
                        "speed", speed,
                        "duration", topic.durationSeconds,
                        "difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel(),
                        "tips", generateListeningTips(topic, context.getCurrentLevel())
                ))
                .build();
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Listening Practice Strategy";
    }

    private ListeningTopic getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<ListeningTopic> topics = listeningTopics.get(level);
        return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
    }

    private ListeningTopic getTopicByName(String name) {
        for (List<ListeningTopic> topics : listeningTopics.values()) {
            for (ListeningTopic topic : topics) {
                if (topic.name.equals(name)) {
                    return topic;
                }
            }
        }
        return listeningTopics.get("beginner").get(0);
    }

    private String determineLevel(double level) {
        if (level < BEGINNER_THRESHOLD) return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private String buildListeningPrompt(String userInput, ListeningState state,
                                        LearningContext context, ListeningTopic topic) {
        double level = context.getCurrentLevel();
        double avgComprehension = (state != null) ? state.averageComprehension : 0.0;
        String levelGuide = getLevelGuide(level);

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
            4. What was missed (with exact parts)
            5. Clear explanation of difficult sections
            6. Specific listening tips
            7. Motivating closing + follow-up challenge

            Generate a complete, structured listening feedback response.
            """,
                level,
                topic.name,
                topic.description,
                getSpeed(level, topic),
                topic.durationSeconds,
                String.join(", ", topic.keywords),
                determineLevel(level),
                avgComprehension,
                state != null ? state.exercisesCompleted : 0,
                levelGuide,
                userInput
        );
    }

    private String getLevelGuide(double level) {
        if (level < BEGINNER_THRESHOLD) {
            return """
                • Use VERY simple language
                • Focus on catching key words
                • Explain slowly and clearly
                • Provide written support
                • Be extremely encouraging""";
        } else if (level < INTERMEDIATE_THRESHOLD) {
            return """
                • Use clear, moderate language
                • Focus on main ideas and details
                • Introduce listening strategies
                • Discuss context clues
                • Encourage active listening""";
        } else if (level < ADVANCED_THRESHOLD) {
            return """
                • Use natural, sophisticated language
                • Focus on nuance and inference
                • Discuss speaker's attitude
                • Analyze rhetorical devices
                • Challenge with complex content""";
        } else {
            return """
                • Use advanced, nuanced language
                • Analyze subtext and implication
                • Discuss cultural references
                • Focus on advanced comprehension
                • Challenge with authentic materials""";
        }
    }

    private String generateDisplayText(String aiResponse, double score,
                                       ListeningState state, ListeningTopic topic) {
        StringBuilder display = new StringBuilder();

        display.append("🎧 LISTENING PRACTICE\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append(aiResponse).append("\n\n");

        display.append("📊 YOUR RESULTS\n");
        display.append("────────────────\n");
        display.append(String.format("  Comprehension: %.1f%%\n", score));
        display.append(String.format("  Best score: %.1f%%\n", state.bestScore));
        display.append(String.format("  Exercises completed: %d\n", state.exercisesCompleted));
        display.append(String.format("  Current topic: %s\n\n", topic.name));

        display.append("💡 QUICK TIPS\n");
        display.append("─────────────\n");
        display.append("  • Focus on key words you recognize\n");
        display.append("  • Listen for context clues\n");
        display.append("  • Don't worry about every single word\n");
        display.append("  • Practice regularly with varied content\n\n");

        display.append("Keep going! You're improving with every session 🔥\n");

        return display.toString();
    }

    private String generateTtsText(String aiResponse, double score,
                                   ListeningState state, ListeningTopic topic) {
        StringBuilder tts = new StringBuilder();

        String cleanResponse = aiResponse.replaceAll("[\\n\\r]+", " ").trim();
        if (cleanResponse.length() > 200) {
            cleanResponse = cleanResponse.substring(0, 200) + "... ";
        }
        tts.append(cleanResponse).append(" ");

        tts.append(String.format("Your comprehension score is %.1f percent. ", score));
        tts.append(String.format("Your best score is %.1f percent. ", state.bestScore));
        tts.append(String.format("You have completed %d listening exercises. ", state.exercisesCompleted));
        tts.append("Keep practicing to improve your listening skills.");

        return tts.toString();
    }

    private String generateTaskDisplayText(ListeningTopic topic, LearningContext context) {
        StringBuilder display = new StringBuilder();

        String level = determineLevel(context.getCurrentLevel());

        display.append("🎧 LISTENING EXERCISE: ").append(topic.name.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("📝 Description:\n");
        display.append(topic.description).append("\n\n");

        display.append("📊 Difficulty: ").append(level).append("\n");
        display.append("⚡ Speed: ").append(getSpeed(context.getCurrentLevel(), topic)).append("\n");
        display.append("⏱️ Duration: ").append(topic.durationSeconds).append(" seconds\n\n");

        display.append("🔑 KEY VOCABULARY\n");
        display.append("────────────────\n");
        for (String keyword : topic.keywords) {
            display.append("  • ").append(keyword).append("\n");
        }
        display.append("\n");

        display.append("💡 TIPS\n");
        display.append("───────\n");
        display.append("  • Listen for the main idea first\n");
        display.append("  • Don't pause - try to understand in real-time\n");
        display.append("  • Focus on words you recognize\n");
        display.append("  • Try to understand the context\n\n");

        display.append("Listen carefully and answer the questions that follow!\n");

        return display.toString();
    }

    private String generateTaskTtsText(ListeningTopic topic, LearningContext context) {
        StringBuilder tts = new StringBuilder();

        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic.name, topic.name);
        String speed = getSpeed(context.getCurrentLevel(), topic);

        tts.append("New listening exercise on ").append(topicSpeech).append(". ");
        tts.append(topic.description).append(". ");

        tts.append("Difficulty level is ").append(determineLevel(context.getCurrentLevel())).append(". ");
        tts.append("Speed is ").append(speed).append(". ");
        tts.append("Duration is ").append(topic.durationSeconds).append(" seconds. ");

        tts.append("Key vocabulary includes: ");
        List<String> keywords = topic.keywords;
        for (int i = 0; i < Math.min(3, keywords.size()); i++) {
            tts.append(keywords.get(i));
            if (i < Math.min(3, keywords.size()) - 1) {
                tts.append(", ");
            }
        }
        tts.append(". ");

        tts.append("Listen carefully and answer the questions.");

        return tts.toString();
    }

    private String generateTranscript(ListeningTopic topic, double level) {
        // В реальном приложении здесь будет генерация через AI
        // Сейчас возвращаем демо-текст
        return String.format("""
            This is a sample listening exercise about %s.
            
            %s
            
            Focus on these key words: %s.
            
            Try to understand the main ideas and specific details.
            """,
                topic.name,
                topic.description,
                String.join(", ", topic.keywords)
        );
    }

    private List<String> generateListeningTips(ListeningTopic topic, double level) {
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

    private String getSpeed(double level, ListeningTopic topic) {
        if (level < BEGINNER_THRESHOLD) return "slow";
        if (level < INTERMEDIATE_THRESHOLD) return "normal";
        return "fast";
    }

    private double evaluateComprehension(String userInput, String transcript, ListeningTopic topic) {
        if (transcript == null || userInput == null) {
            return 50 + ThreadLocalRandom.current().nextDouble() * 40;
        }

        String[] userWords = userInput.toLowerCase().split("\\s+");
        int matches = 0;

        for (String keyword : topic.keywords) {
            String keyword_lower = keyword.toLowerCase();
            for (String word : userWords) {
                if (word.contains(keyword_lower) || keyword_lower.contains(word)) {
                    matches++;
                    break;
                }
            }
        }

        double baseScore = (double) matches / topic.keywords.size() * 100;

        double lengthBonus = Math.min(20, userWords.length * 2);

        return Math.min(100, baseScore + lengthBonus);
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

        double comprehensionComponent = state.averageComprehension * 0.6;
        double consistencyComponent = Math.min(30, state.exercisesCompleted * 0.5);
        double improvementComponent = (state.bestScore - state.averageComprehension) * 0.5;

        return Math.min(100, comprehensionComponent + consistencyComponent + improvementComponent);
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

        if (state.exercisesCompleted < 10) {
            recommendations.add("Aim for 10-15 minutes of listening practice daily");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great progress! Try varying your listening materials");
        }

        return recommendations;
    }

    private LearningMode determineNextMode(LearningContext context, ListeningState state) {
        if (state == null) return LearningMode.LISTENING;

        if (state.averageComprehension >= EXCELLENT_COMPREHENSION &&
                state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_25) {
            return LearningMode.CONVERSATION;
        } else if (state.averageComprehension < GOOD_COMPREHENSION) {
            return LearningMode.PRONUNCIATION;
        }

        return LearningMode.LISTENING;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private List<String> getAchievements(ListeningState state) {
        List<String> achievements = new ArrayList<>();

        if (state == null) return achievements;

        if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_100) {
            achievements.add("🏆 Listening Master - 100+ exercises!");
        } else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_50) {
            achievements.add("🎯 Dedicated Listener - 50+ exercises");
        } else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_25) {
            achievements.add("📚 Regular Listener - 25+ exercises");
        } else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_10) {
            achievements.add("🎧 Started Listening Journey - 10 exercises");
        }

        if (state.bestScore >= ACHIEVEMENT_COMPREHENSION_95) {
            achievements.add("🔥 Near-perfect comprehension! " + String.format("%.1f%%", state.bestScore));
        } else if (state.averageComprehension >= ACHIEVEMENT_COMPREHENSION_85) {
            achievements.add("🌟 Excellent comprehension! " + String.format("%.1f%%", state.averageComprehension));
        } else if (state.averageComprehension >= ACHIEVEMENT_COMPREHENSION_70) {
            achievements.add("📈 Good comprehension! " + String.format("%.1f%%", state.averageComprehension));
        }

        if (state.practicedTopics.size() >= 10) {
            achievements.add("🌍 Explored 10+ different listening topics");
        }

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        ListeningState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("comprehensionScores", new ArrayList<>(state.comprehensionScores));
        stateMap.put("practicedTopics", new ArrayList<>(state.practicedTopics));
        stateMap.put("topicScores", new HashMap<>(state.topicScores));
        stateMap.put("responseTimes", new ArrayList<>(state.responseTimes));
        stateMap.put("currentTopic", state.currentTopic);
        stateMap.put("exercisesCompleted", state.exercisesCompleted);
        stateMap.put("correctKeyPoints", state.correctKeyPoints);
        stateMap.put("averageComprehension", state.averageComprehension);
        stateMap.put("bestScore", state.bestScore);
        stateMap.put("lastTranscript", state.lastTranscript);
        stateMap.put("totalTimeSpent", state.totalTimeSpent);

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
        List<Long> responseTimes = (List<Long>) stateMap.getOrDefault("responseTimes", Collections.emptyList());
        state.responseTimes.addAll(responseTimes);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.exercisesCompleted = (int) stateMap.getOrDefault("exercisesCompleted", 0);
        state.correctKeyPoints = (int) stateMap.getOrDefault("correctKeyPoints", 0);
        state.averageComprehension = (double) stateMap.getOrDefault("averageComprehension", 0.0);
        state.bestScore = (double) stateMap.getOrDefault("bestScore", 0.0);
        state.lastTranscript = (String) stateMap.get("lastTranscript");
        state.totalTimeSpent = (long) stateMap.getOrDefault("totalTimeSpent", 0L);

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        ListeningState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageComprehension", state.averageComprehension);
        stats.put("bestScore", state.bestScore);
        stats.put("exercisesCompleted", state.exercisesCompleted);
        stats.put("topicsExplored", state.practicedTopics.size());
        stats.put("successRate", state.getSuccessRate());
        stats.put("averageResponseTime", state.getAverageResponseTime());
        stats.put("currentTopic", state.currentTopic);

        return stats;
    }
}