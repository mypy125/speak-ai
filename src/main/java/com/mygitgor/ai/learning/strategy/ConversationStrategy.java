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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class ConversationStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ConversationStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;
    private final Map<String, ConversationState> sessions = new ConcurrentHashMap<>();

    private static final double TURN_PROGRESS_WEIGHT = 0.40;
    private static final double FLUENCY_PROGRESS_WEIGHT = 0.30;
    private static final double VOCABULARY_PROGRESS_WEIGHT = 0.15;
    private static final double GRAMMAR_PROGRESS_WEIGHT = 0.15;

    private static final int MAX_HISTORY_SIZE = 50;
    private static final int ACHIEVEMENT_TURNS_50 = 50;
    private static final int ACHIEVEMENT_TURNS_100 = 100;
    private static final int ACHIEVEMENT_TURNS_200 = 200;
    private static final double ACHIEVEMENT_FLUENCY_80 = 80.0;

    private static final Duration SESSION_TIMEOUT = Duration.ofHours(2);

    private static final Map<String, String> TOPIC_TO_SPEECH = new HashMap<>();

    static {
        TOPIC_TO_SPEECH.put("Daily routines",           "daily routines");
        TOPIC_TO_SPEECH.put("My family",                "family");
        TOPIC_TO_SPEECH.put("Favorite foods",           "favorite foods");
        TOPIC_TO_SPEECH.put("Weather today",            "weather");
        TOPIC_TO_SPEECH.put("Weekend plans",            "weekend plans");
        TOPIC_TO_SPEECH.put("My hobbies",               "hobbies");
        TOPIC_TO_SPEECH.put("Learning English",         "learning English");
        TOPIC_TO_SPEECH.put("My hometown",              "hometown");
        TOPIC_TO_SPEECH.put("Favorite movies",          "movies");
        TOPIC_TO_SPEECH.put("Music preferences",        "music");
        TOPIC_TO_SPEECH.put("Travel experiences",       "travel");
        TOPIC_TO_SPEECH.put("Career goals",             "career");
        TOPIC_TO_SPEECH.put("Technology impact",        "technology");
        TOPIC_TO_SPEECH.put("Cultural differences",     "culture");
        TOPIC_TO_SPEECH.put("Environmental issues",     "environment");
        TOPIC_TO_SPEECH.put("Health and fitness",       "health");
        TOPIC_TO_SPEECH.put("Education system",         "education");
        TOPIC_TO_SPEECH.put("Social media",             "social media");
        TOPIC_TO_SPEECH.put("Future predictions",       "future");
        TOPIC_TO_SPEECH.put("Work-life balance",        "work life balance");
        TOPIC_TO_SPEECH.put("Global economy",           "economy");
        TOPIC_TO_SPEECH.put("Political systems",        "politics");
        TOPIC_TO_SPEECH.put("Philosophical questions",  "philosophy");
        TOPIC_TO_SPEECH.put("Scientific breakthroughs", "science");
        TOPIC_TO_SPEECH.put("Art and creativity",       "art");
        TOPIC_TO_SPEECH.put("Human psychology",         "psychology");
        TOPIC_TO_SPEECH.put("Social justice",           "social justice");
        TOPIC_TO_SPEECH.put("Technological ethics",     "ethics");
        TOPIC_TO_SPEECH.put("Climate change solutions", "climate change");
        TOPIC_TO_SPEECH.put("Future of humanity",       "future of humanity");
    }

    private static class ConversationState {
        final List<String> history = Collections.synchronizedList(new ArrayList<>());
        String currentTopic;
        int turnCount;
        double fluencyScore = 50.0;
        double averageResponseTime;
        int totalUserWords;

        double grammarScore = 55.0;

        Instant lastActivity = Instant.now();

        double levelAtSessionStart = -1;

        List<String> topicsDiscussed = new ArrayList<>();
        Map<String, Integer>  vocabularyUsed  = new ConcurrentHashMap<>();

        void addToHistory(String message) {
            history.add(message);
            if (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
        }

        void addTopic(String topic) {
            if (!topicsDiscussed.contains(topic)) {
                topicsDiscussed.add(topic);
            }
        }
    }

    public ConversationStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor  = ThreadPoolManager.getInstance().getBackgroundExecutor();
        startSessionCleanup();
        logger.info("ConversationStrategy инициализирована");
    }

    private void startSessionCleanup() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            Instant cutoff = Instant.now().minus(SESSION_TIMEOUT);
            int removed = 0;
            for (Map.Entry<String, ConversationState> entry : sessions.entrySet()) {
                if (entry.getValue().lastActivity.isBefore(cutoff)) {
                    sessions.remove(entry.getKey());
                    removed++;
                }
            }
            if (removed > 0) logger.info("Очищено {} устаревших сессий", removed);
        }, 30, 30, TimeUnit.MINUTES);
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.CONVERSATION;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            ConversationState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new ConversationState());

            state.lastActivity = Instant.now();

            state.addToHistory("user: " + userInput);
            state.turnCount++;

            analyzeVocabulary(userInput, state);

            updateFluencyScore(state, userInput);

            if (context.getLastAnalysis() != null) {
                updateStateFromAnalysis(state, context);
            }

            String prompt = buildConversationPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            long responseTime = System.currentTimeMillis() - startTime;
            state.averageResponseTime =
                    (state.averageResponseTime * (state.turnCount - 1) + responseTime) / state.turnCount;

            state.addToHistory("ai: " + aiResponse);

            String displayText = generateDisplayText(aiResponse, state);
            String ttsText = generateTtsText(aiResponse, state);

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
            ConversationState state = sessions.get(context.getUserId());
            if (state == null) {
                return "Hello! How can I help you practice English today?";
            }
            String prompt = buildConversationPrompt(userInput, state, context);
            String aiResponse = aiService.generateBotResponse(prompt, null);
            return generateTtsText(aiResponse, state);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ConversationState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            skills.put("fluency",             calculateFluencyScore(state));
            skills.put("vocabulary",          calculateVocabularyScore(state));
            skills.put("grammar",             calculateGrammarScore(state));
            skills.put("engagement",          calculateEngagementScore(state));
            skills.put("conversation_depth",  calculateConversationDepth(state));

            List<String> achievements = getAchievements(state);

            return LearningProgress.builder()
                    .overallProgress(calculateOverallProgress(skills))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.turnCount / 2 : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(achievements)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ConversationState state = sessions.get(context.getUserId());
            return generateNextTaskWithTts(context, state);
        }, executor);
    }

    private LearningTask generateNextTaskWithTts(LearningContext context, ConversationState state) {
        String topic = selectPersonalizedTopic(state, context);
        LearningTask.DifficultyLevel difficulty = determineDifficulty(context.getCurrentLevel());
        List<String> examples = generateExamplesForTopic(topic, difficulty);

        if (state != null) {
            state.currentTopic = topic;
            state.addTopic(topic);
        }

        return LearningTask.builder()
                .id("conv_" + System.currentTimeMillis())
                .title("🗣️ Conversation: " + topic)
                .description(generateTaskDisplayText(topic, difficulty, context))
                .ttsDescription(generateTaskTtsText(topic, difficulty, context))
                .mode(LearningMode.CONVERSATION)
                .difficulty(difficulty)
                .examples(examples)
                .metadata(Map.of(
                        "topic",              topic,
                        "suggested_duration", getSuggestedDuration(difficulty),
                        "difficulty_level",   difficulty.getLevel(),
                        "tips",               generateConversationTips(difficulty)
                ))
                .build();
    }

    @Override public boolean isSupported()  { return aiService.isAvailable(); }
    @Override public String getStrategyName() { return "Conversation Practice Strategy"; }

    private String buildConversationPrompt(String userInput, ConversationState state,
                                           LearningContext context) {
        return String.format("""
        %s
        
        You are an AI English tutor specializing in technical interview preparation. 
        Your goal is to help the student practice English conversation skills needed 
        for an upcoming technical interview while maintaining a friendly and supportive 
        atmosphere. Get to know their interests and adapt to their personality and 
        communication style.
        
        === Interview & Conversation Context ===
        Student Level: %s (%.1f/100)
        Current Topic: %s
        Exchanges so far: %d
        Average Response Time: %.1fs
        Technical Interview Focus: %s
        
        === Recent Messages (last %d) ===
        %s
        
        === Student's Latest Message ===
        %s
        
        === Your Response Guidelines ===
        %s
        
        === Interview Preparation Integration ===
        1. Naturally weave in technical vocabulary relevant to their field
        2. Practice common interview question patterns
        3. Help them articulate technical concepts clearly
        4. Build confidence in discussing their experience and projects
        5. Balance friendly conversation with interview preparation
        
        Remember: 
        - Be friendly and engaging - get to know them as a person
        - Adapt to their communication style and personality
        - Discuss their interests while gently steering toward interview-relevant topics
        - Respond ONLY in English. Be natural, supportive, and professional.
        """,
                getSystemPrompt(context.getCurrentLevel()),
                getLevelDescription(context.getCurrentLevel()),
                context.getCurrentLevel(),
                state.currentTopic != null ? state.currentTopic : "getting to know you",
                state.turnCount,
                state.averageResponseTime / 1000.0,
                getTechnicalFocus(context),
                Math.min(4, state.history.size()),
                String.join("\n", state.history.subList(
                        Math.max(0, state.history.size() - 4), state.history.size())),
                userInput,
                getInstructionsForLevel(context.getCurrentLevel())
        );
    }

    private String getSystemPrompt(double level) {
        if (level < 30)
            return "You are speaking with a BEGINNER English learner preparing for a technical interview. " +
                    "Use VERY simple vocabulary and short sentences. Be EXTREMELY patient and encouraging. " +
                    "Focus on basic自我介绍 and simple technical terms. Show genuine interest in their " +
                    "personality and background.";
        if (level < 60)
            return "You are speaking with an INTERMEDIATE English learner preparing for a technical interview. " +
                    "Use moderate vocabulary and help them practice describing their experience and projects. " +
                    "Encourage elaboration while maintaining a friendly conversation about their interests. " +
                    "Correct gently by rephrasing.";
        if (level < 85)
            return "You are speaking with an ADVANCED English learner preparing for a technical interview. " +
                    "Use natural professional language. Help them articulate complex technical concepts clearly. " +
                    "Engage in deeper discussions about their career goals and technical philosophy while " +
                    "keeping the conversation friendly and personalized.";
        return "You are speaking with an EXPERT English speaker preparing for a senior technical interview. " +
                "Use sophisticated professional vocabulary. Challenge them with nuanced technical questions " +
                "while maintaining engaging conversation about their interests and leadership style. " +
                "Focus on cultural fit and communication effectiveness.";
    }

    private String getInstructionsForLevel(double level) {
        if (level < 30) return """
            1. Keep responses under 50 words
            2. Introduce 1-2 basic technical terms naturally
            3. Ask simple questions about their background
            4. Praise their efforts and progress
            5. Show enthusiasm for their interests""";
        if (level < 60) return """
            1. Keep responses between 50-100 words
            2. Practice describing work experience and projects
            3. Gently correct technical term usage
            4. Ask follow-up questions about their interests
            5. Model good interview response structure""";
        if (level < 85) return """
            1. Responses can be up to 150 words
            2. Practice STAR method responses naturally
            3. Discuss technical challenges they've faced
            4. Help refine technical explanations
            5. Balance technical and personal conversation""";
        return """
            1. Responses up to 200 words, be thorough
            2. Discuss system design and architecture concepts
            3. Practice leadership and conflict resolution scenarios
            4. Provide detailed feedback on communication style
            5. Engage in technical debates while being friendly""";
    }

    private String getTechnicalFocus(LearningContext context) {

        return "general software engineering";
    }

    private String getLevelDescription(double level) {
        if (level < 30) return "BEGINNER (A1-A2)";
        if (level < 60) return "INTERMEDIATE (B1-B2)";
        if (level < 85) return "ADVANCED (C1)";
        return "EXPERT (C2)";
    }

    private void updateStateFromAnalysis(ConversationState state, LearningContext context) {
        if (context.getLastAnalysis() == null) return;

        double analysisGrammar = context.getLastAnalysis().getGrammarScore();
        state.grammarScore = state.grammarScore * 0.7 + analysisGrammar * 0.3;

        double analysisFluency = context.getLastAnalysis().getFluencyScore();
        state.fluencyScore = state.fluencyScore * 0.7 + analysisFluency * 0.3;
    }

    private void updateFluencyScore(ConversationState state, String userInput) {
        String[] words = userInput.trim().split("\\s+");
        int wordCount = words.length;
        state.totalUserWords += wordCount;

        double lengthBonus = wordCount >= 15 ? 2.0
                : wordCount >= 10 ? 1.5
                : wordCount >= 5  ? 0.8
                : 0.2;

        long punctuationCount = userInput.chars()
                .filter(c -> c == '.' || c == '!' || c == '?' || c == ',')
                .count();
        double punctuationBonus = Math.min(1.0, punctuationCount * 0.25);

        long uniqueInResponse = Arrays.stream(words)
                .map(String::toLowerCase)
                .distinct()
                .count();
        double ttrBonus = wordCount > 0
                ? Math.min(0.5, (double) uniqueInResponse / wordCount * 0.5)
                : 0;

        double delta = lengthBonus + punctuationBonus + ttrBonus;
        state.fluencyScore = Math.min(100, state.fluencyScore + delta);
    }

    private void analyzeVocabulary(String userInput, ConversationState state) {
        String[] words = userInput.toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");
        for (String word : words) {
            if (word.length() > 2) {
                state.vocabularyUsed.merge(word, 1, Integer::sum);
            }
        }
    }

    private double calculateFluencyScore(ConversationState state) {
        if (state == null) return 50.0;
        double turnBonus = Math.min(10, state.turnCount * 0.3);
        double responseTimeBonus = Math.min(5, 500 / Math.max(1, state.averageResponseTime));
        return Math.min(100, state.fluencyScore + turnBonus + responseTimeBonus);
    }

    private double calculateVocabularyScore(ConversationState state) {
        if (state == null) return 50.0;
        int uniqueWords = state.vocabularyUsed.size();
        return Math.min(100, 50 + uniqueWords * 0.5);
    }

    private double calculateGrammarScore(ConversationState state) {
        if (state == null) return 50.0;
        return Math.min(100, state.grammarScore);
    }

    private double calculateEngagementScore(ConversationState state) {
        if (state == null) return 0;
        return Math.min(100, state.turnCount * 2.0);
    }

    private double calculateConversationDepth(ConversationState state) {
        if (state == null || state.topicsDiscussed.isEmpty()) return 0;
        double topicVariety = Math.min(50, state.topicsDiscussed.size() * 5.0);
        double turnDepth = Math.min(50, state.turnCount * 0.5);
        return topicVariety + turnDepth;
    }

    private double calculateProgress(ConversationState state) {
        if (state == null) return 0;
        double turnProgress = Math.min(100, state.turnCount * 5.0);
        double fluencyProgress = calculateFluencyScore(state);
        double vocabProgress = calculateVocabularyScore(state);
        double grammarProgress = calculateGrammarScore(state);

        return Math.min(100,
                turnProgress    * TURN_PROGRESS_WEIGHT +
                        fluencyProgress * FLUENCY_PROGRESS_WEIGHT +
                        vocabProgress   * VOCABULARY_PROGRESS_WEIGHT +
                        grammarProgress * GRAMMAR_PROGRESS_WEIGHT
        );
    }

    private double calculateOverallProgress(Map<String, Double> skills) {
        return skills.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private LearningMode determineNextMode(LearningContext context, ConversationState state) {
        if (state == null) return LearningMode.CONVERSATION;

        double fluency = calculateFluencyScore(state);
        double vocabulary = calculateVocabularyScore(state);
        double grammar = calculateGrammarScore(state);

        if (fluency < 50) return LearningMode.PRONUNCIATION;
        if (state.turnCount > 30) return LearningMode.EXERCISE;
        if (vocabulary < 60) return LearningMode.VOCABULARY;
        if (grammar < 65) return LearningMode.GRAMMAR;

        return LearningMode.CONVERSATION;
    }

    private String selectPersonalizedTopic(ConversationState state, LearningContext context) {
        String[] beginnerTopics = {
                "Your background and interests",
                "Why you're learning English",
                "Your favorite technologies",
                "Daily work routines",
                "Team collaboration experiences",
                "Learning programming journey",
                "Simple project you've built",
                "Tools you use at work",
                "Work-life balance preferences",
                "Career aspirations"
        };
        String[] intermediateTopics = {
                "Technical challenges you've solved",
                "Agile methodology experience",
                "Code review practices",
                "Database design decisions",
                "API development experience",
                "Testing strategies you use",
                "Version control workflows",
                "Cloud platform experience",
                "Security considerations",
                "Performance optimization"
        };
        String[] advancedTopics = {
                "System architecture decisions",
                "Scalability challenges",
                "Microservices vs monoliths",
                "Technical debt management",
                "Team leadership experience",
                "Mentoring junior developers",
                "Technical roadmap planning",
                "Emerging technology trends",
                "Engineering culture building",
                "Cross-team collaboration"
        };

        double level = context.getCurrentLevel();

        if (state != null) {
            if (state.levelAtSessionStart < 0) {
                state.levelAtSessionStart = level;
            } else if (Math.abs(level - state.levelAtSessionStart) >= 10) {
                logger.debug("Level changed {:.1f} → {:.1f}, resetting topics",
                        state.levelAtSessionStart, level);
                state.topicsDiscussed.clear();
                state.levelAtSessionStart = level;
            }
        }

        String[] topics;
        if (level < 30) topics = beginnerTopics;
        else if (level < 60) topics = intermediateTopics;
        else topics = advancedTopics;

        if (state != null && !state.topicsDiscussed.isEmpty()) {
            List<String> available = new ArrayList<>(Arrays.asList(topics));
            available.removeAll(state.topicsDiscussed);
            if (!available.isEmpty()) {
                return available.get(ThreadLocalRandom.current().nextInt(available.size()));
            }
        }

        return topics[ThreadLocalRandom.current().nextInt(topics.length)];
    }

    private LearningTask.DifficultyLevel determineDifficulty(double level) {
        if (level < 30) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < 60) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < 85) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private List<String> getAchievements(ConversationState state) {
        List<String> achievements = new ArrayList<>();
        if (state == null) return achievements;

        if (state.turnCount >= ACHIEVEMENT_TURNS_50)
            achievements.add("💬 50+ conversation exchanges");
        if (state.turnCount >= ACHIEVEMENT_TURNS_100)
            achievements.add("🎯 100+ exchanges - great conversation!");
        if (state.turnCount >= ACHIEVEMENT_TURNS_200)
            achievements.add("🏆 200+ exchanges - conversation master!");

        if (state.fluencyScore >= ACHIEVEMENT_FLUENCY_80)
            achievements.add("⚡ Excellent fluency!");

        if (state.topicsDiscussed.size() >= 5)
            achievements.add("🌍 5+ topics discussed");
        if (state.topicsDiscussed.size() >= 10)
            achievements.add("🌍 Explored 10+ different topics");
        if (state.vocabularyUsed.size() >= 50)
            achievements.add("📚 50+ unique words used");
        if (state.vocabularyUsed.size() >= 100)
            achievements.add("📚 Rich vocabulary (100+ words)");

        return achievements;
    }

    private List<String> generateRecommendations(ConversationState state) {
        List<String> recommendations = new ArrayList<>();
        if (state == null) return recommendations;

        if (state.turnCount < 10)
            recommendations.add("Try to have longer conversations - aim for 10+ exchanges");
        if (calculateFluencyScore(state) < 60)
            recommendations.add("Practice speaking more smoothly - focus on connecting your ideas");
        if (state.topicsDiscussed.size() < 3)
            recommendations.add("Explore different topics to expand your vocabulary");
        if (state.vocabularyUsed.size() < 20)
            recommendations.add("Try to use a wider variety of words in your responses");
        if (recommendations.isEmpty())
            recommendations.add("Great progress! Keep practicing regularly");

        return recommendations;
    }

    private String generateDisplayText(String aiResponse, ConversationState state) {
        return "🗣️ CONVERSATION\n" +
                "═══════════════════════════════════════\n\n" +
                aiResponse + "\n\n" +
                "📊 CONVERSATION STATS\n" +
                "────────────────────\n" +
                String.format("  Exchanges: %d%n", state.turnCount) +
                String.format("  Fluency score: %.1f%%%n", calculateFluencyScore(state)) +
                String.format("  Grammar score: %.1f%%%n", calculateGrammarScore(state)) +
                String.format("  Vocabulary used: %d unique words%n", state.vocabularyUsed.size()) +
                String.format("  Topics discussed: %d%n%n", state.topicsDiscussed.size()) +
                "Keep the conversation going! 😊\n";
    }

    private String generateTtsText(String aiResponse, ConversationState state) {
        String mainMessage = extractMainMessage(aiResponse);

        String cleanResponse = mainMessage
                .replaceAll("[\\*\\_\\`\\#]", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1")
                .trim();

        return cleanResponse;
    }

    private String extractMainMessage(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return "";
        }

        String[] lines = fullResponse.split("\n");
        StringBuilder mainMessage = new StringBuilder();

        boolean inStats = false;
        boolean inTopic = false;

        for (String line : lines) {
            if (line.contains("CONVERSATION STATS") ||
                    line.contains("═══════════════════════════════════════") ||
                    line.contains("CONVERSATION TOPIC") ||
                    line.contains("📊 CONVERSATION STATS") ||
                    line.contains("📊 CONVERSATION GOALS")) {
                inStats = true;
                continue;
            }

            if (inStats && line.contains("Keep the conversation going")) {
                inStats = false;
                continue;
            }

            if (inStats || inTopic) {
                continue;
            }

            if (line.contains("════════════════") ||
                    line.contains("────────────────────") ||
                    line.trim().isEmpty()) {
                continue;
            }

            if (!line.startsWith("📊") &&
                    !line.startsWith("🎯") &&
                    !line.startsWith("⏱️") &&
                    !line.contains("Exchanges:") &&
                    !line.contains("Fluency score:") &&
                    !line.contains("Grammar score:") &&
                    !line.contains("Vocabulary used:") &&
                    !line.contains("Topics discussed:")) {

                mainMessage.append(line).append(" ");
            }
        }

        String result = mainMessage.toString().trim();

        if (result.isEmpty()) {
            int statsIndex = fullResponse.indexOf("CONVERSATION STATS");
            if (statsIndex == -1) {
                statsIndex = fullResponse.indexOf("📊 CONVERSATION STATS");
            }
            if (statsIndex != -1) {
                result = fullResponse.substring(0, statsIndex).trim();
            } else {
                result = fullResponse;
            }
        }

        return result;
    }

    private String generateTaskDisplayText(String topic, LearningTask.DifficultyLevel difficulty,
                                           LearningContext context) {
        String levelText = switch (difficulty) {
            case BEGINNER     -> "beginner-friendly";
            case INTERMEDIATE -> "intermediate";
            case ADVANCED     -> "advanced";
            case EXPERT       -> "expert-level";
        };
        return "🗣️ CONVERSATION TOPIC: " + topic.toUpperCase() + "\n" +
                "═══════════════════════════════════════\n\n" +
                "Let's have a " + levelText + " conversation about '" + topic + "'.\n\n" +
                String.format("📊 Your level: %.1f/100%n%n", context.getCurrentLevel()) +
                "🎯 CONVERSATION GOALS\n" +
                "────────────────────\n" +
                "  • Practice speaking naturally about this topic\n" +
                "  • Express your opinions and ideas\n" +
                "  • Learn topic-specific vocabulary\n" +
                "  • Improve your fluency\n\n" +
                String.format("⏱️ Suggested duration: %d minutes%n%n", getSuggestedDuration(difficulty)) +
                "I'll guide the conversation and provide feedback along the way.\n";
    }

    private String generateTaskTtsText(String topic, LearningTask.DifficultyLevel difficulty,
                                       LearningContext context) {
        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic, topic);
        String levelText = switch (difficulty) {
            case BEGINNER     -> "beginner friendly";
            case INTERMEDIATE -> "intermediate";
            case ADVANCED     -> "advanced";
            case EXPERT       -> "expert level";
        };
        return "Let's have a " + levelText + " conversation about " + topicSpeech + ". " +
                String.format("Your current level is %.1f percent. ", context.getCurrentLevel()) +
                "Conversation goals include: practicing natural speech, expressing your opinions, " +
                "learning topic specific vocabulary, and improving fluency. " +
                "Suggested duration is " + getSuggestedDuration(difficulty) + " minutes. " +
                "I will guide the conversation and provide feedback.";
    }

    private List<String> generateExamplesForTopic(String topic, LearningTask.DifficultyLevel difficulty) {
        List<String> examples = new ArrayList<>();
        switch (difficulty) {
            case BEGINNER:
                examples.add("Tell me about your favorite " + topic.toLowerCase());
                examples.add("What " + topic + " do you like?");
                examples.add("Do you enjoy " + topic.toLowerCase() + "? Why?");
                break;
            case INTERMEDIATE:
                examples.add("What do you think about " + topic + " in your country?");
                examples.add("How has " + topic + " changed in recent years?");
                examples.add("Can you describe your ideal " + topic.toLowerCase() + " experience?");
                break;
            case ADVANCED:
                examples.add("What are the most significant impacts of " + topic + " on society?");
                examples.add("Can you analyze the pros and cons of current " + topic.toLowerCase() + " trends?");
                examples.add("How might " + topic + " evolve in the next decade?");
                break;
            case EXPERT:
                examples.add("What philosophical implications does " + topic + " have?");
                examples.add("Can you critique different perspectives on " + topic + "?");
                examples.add("How does " + topic + " intersect with other global challenges?");
                break;
        }
        return examples;
    }

    private List<String> generateConversationTips(LearningTask.DifficultyLevel difficulty) {
        List<String> tips = new ArrayList<>();
        switch (difficulty) {
            case BEGINNER:
                tips.add("Don't worry about making mistakes - they're part of learning");
                tips.add("Take your time to think before responding");
                tips.add("If you don't understand a word, ask me to explain");
                break;
            case INTERMEDIATE:
                tips.add("Try to elaborate on your answers with examples");
                tips.add("Challenge yourself to use new vocabulary");
                tips.add("Pay attention to my phrasing and try to use similar structures");
                break;
            case ADVANCED:
                tips.add("Express nuanced opinions and support them with reasoning");
                tips.add("Use a variety of sentence structures");
                tips.add("Incorporate idiomatic expressions naturally");
                break;
            case EXPERT:
                tips.add("Engage in debate and consider multiple perspectives");
                tips.add("Use sophisticated vocabulary and complex grammatical structures");
                tips.add("Aim for native-like fluency and natural expression");
                break;
        }
        return tips;
    }

    private int getSuggestedDuration(LearningTask.DifficultyLevel difficulty) {
        return switch (difficulty) {
            case BEGINNER     -> 10;
            case INTERMEDIATE -> 15;
            case ADVANCED     -> 20;
            case EXPERT       -> 25;
        };
    }

    public Map<String, Object> getSessionState(String userId) {
        ConversationState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("history",             new ArrayList<>(state.history));
        stateMap.put("currentTopic",        state.currentTopic);
        stateMap.put("turnCount",           state.turnCount);
        stateMap.put("fluencyScore",        state.fluencyScore);
        stateMap.put("grammarScore",        state.grammarScore);      
        stateMap.put("totalUserWords",      state.totalUserWords);
        stateMap.put("averageResponseTime", state.averageResponseTime);
        stateMap.put("topicsDiscussed",     new ArrayList<>(state.topicsDiscussed));
        stateMap.put("vocabularyUsed",      new HashMap<>(state.vocabularyUsed));
        stateMap.put("levelAtSessionStart", state.levelAtSessionStart);
        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        ConversationState state = new ConversationState();

        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) stateMap.getOrDefault("history", Collections.emptyList());
        state.history.addAll(history);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.turnCount = (int) stateMap.getOrDefault("turnCount",0);
        state.fluencyScore = (double) stateMap.getOrDefault("fluencyScore",50.0);
        state.grammarScore = (double) stateMap.getOrDefault("grammarScore",55.0);
        state.totalUserWords = (int) stateMap.getOrDefault("totalUserWords",0);
        state.averageResponseTime = (double) stateMap.getOrDefault("averageResponseTime",0.0);
        state.levelAtSessionStart = (double) stateMap.getOrDefault("levelAtSessionStart",-1.0);
        state.lastActivity = Instant.now();

        @SuppressWarnings("unchecked")
        List<String> topicsDiscussed = (List<String>) stateMap.getOrDefault("topicsDiscussed", Collections.emptyList());
        state.topicsDiscussed.addAll(topicsDiscussed);

        @SuppressWarnings("unchecked")
        Map<String, Integer> vocabularyUsed = (Map<String, Integer>) stateMap.getOrDefault("vocabularyUsed", Collections.emptyMap());
        state.vocabularyUsed.putAll(vocabularyUsed);

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        ConversationState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExchanges",state.turnCount);
        stats.put("fluencyScore",calculateFluencyScore(state));
        stats.put("grammarScore",calculateGrammarScore(state));
        stats.put("vocabularySize",state.vocabularyUsed.size());
        stats.put("topicsExplored",state.topicsDiscussed.size());
        stats.put("averageResponseTime", state.averageResponseTime / 1000.0);
        stats.put("currentTopic",state.currentTopic);
        stats.put("totalUserWords",state.totalUserWords);
        return stats;
    }
}