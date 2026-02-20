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

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

public class ConversationStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ConversationStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;
    private final Map<String, ConversationState> sessions = new ConcurrentHashMap<>();

    private static final double TURN_PROGRESS_WEIGHT = 0.4;
    private static final double FLUENCY_PROGRESS_WEIGHT = 0.3;
    private static final double VOCABULARY_PROGRESS_WEIGHT = 0.15;
    private static final double GRAMMAR_PROGRESS_WEIGHT = 0.15;

    private static final int MAX_HISTORY_SIZE = 50;
    private static final int ACHIEVEMENT_TURNS_50 = 50;
    private static final int ACHIEVEMENT_TURNS_100 = 100;
    private static final int ACHIEVEMENT_TURNS_200 = 200;
    private static final double ACHIEVEMENT_FLUENCY_80 = 80.0;

    private static final Map<String, String> TOPIC_TO_SPEECH = new HashMap<>();

    static {
        TOPIC_TO_SPEECH.put("Daily routines", "daily routines");
        TOPIC_TO_SPEECH.put("My family", "family");
        TOPIC_TO_SPEECH.put("Favorite foods", "favorite foods");
        TOPIC_TO_SPEECH.put("Weather today", "weather");
        TOPIC_TO_SPEECH.put("Weekend plans", "weekend plans");
        TOPIC_TO_SPEECH.put("My hobbies", "hobbies");
        TOPIC_TO_SPEECH.put("Learning English", "learning English");
        TOPIC_TO_SPEECH.put("My hometown", "hometown");
        TOPIC_TO_SPEECH.put("Favorite movies", "movies");
        TOPIC_TO_SPEECH.put("Music preferences", "music");
        TOPIC_TO_SPEECH.put("Travel experiences", "travel");
        TOPIC_TO_SPEECH.put("Career goals", "career");
        TOPIC_TO_SPEECH.put("Technology impact", "technology");
        TOPIC_TO_SPEECH.put("Cultural differences", "culture");
        TOPIC_TO_SPEECH.put("Environmental issues", "environment");
        TOPIC_TO_SPEECH.put("Health and fitness", "health");
        TOPIC_TO_SPEECH.put("Education system", "education");
        TOPIC_TO_SPEECH.put("Social media", "social media");
        TOPIC_TO_SPEECH.put("Future predictions", "future");
        TOPIC_TO_SPEECH.put("Work-life balance", "work life balance");
        TOPIC_TO_SPEECH.put("Global economy", "economy");
        TOPIC_TO_SPEECH.put("Political systems", "politics");
        TOPIC_TO_SPEECH.put("Philosophical questions", "philosophy");
        TOPIC_TO_SPEECH.put("Scientific breakthroughs", "science");
        TOPIC_TO_SPEECH.put("Art and creativity", "art");
        TOPIC_TO_SPEECH.put("Human psychology", "psychology");
        TOPIC_TO_SPEECH.put("Social justice", "social justice");
        TOPIC_TO_SPEECH.put("Technological ethics", "ethics");
        TOPIC_TO_SPEECH.put("Climate change solutions", "climate change");
        TOPIC_TO_SPEECH.put("Future of humanity", "future of humanity");
    }

    private static class ConversationState {
        final List<String> history = Collections.synchronizedList(new ArrayList<>());
        String currentTopic;
        int turnCount;
        double fluencyScore;
        double averageResponseTime;
        List<String> topicsDiscussed = new ArrayList<>();
        Map<String, Integer> vocabularyUsed = new ConcurrentHashMap<>();

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
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("ConversationStrategy инициализирована");
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

            state.addToHistory("user: " + userInput);
            state.turnCount++;

            if (context.getLastAnalysis() != null) {
                updateStateFromAnalysis(state, context);
            }

            analyzeVocabulary(userInput, state);

            String prompt = buildConversationPrompt(userInput, state, context);

            String aiResponse = aiService.generateBotResponse(prompt, null);

            long responseTime = System.currentTimeMillis() - startTime;
            state.averageResponseTime = (state.averageResponseTime * (state.turnCount - 1) + responseTime) / state.turnCount;

            state.addToHistory("ai: " + aiResponse);

            updateFluencyScore(state, aiResponse);

            // Генерируем текст для отображения
            String displayText = generateDisplayText(aiResponse, state);

            // Генерируем текст для TTS
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

            // Возвращаем TTS текст
            return generateTtsText(aiResponse, state);
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
            skills.put("conversation_depth", calculateConversationDepth(state));

            List<String> achievements = getAchievements(state);

            if (state != null && state.topicsDiscussed.size() >= 5) {
                achievements.add("🌍 5+ topics discussed");
            }

            if (state != null && state.vocabularyUsed.size() >= 50) {
                achievements.add("📚 50+ unique words used");
            }

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

        String displayDescription = generateTaskDisplayText(topic, difficulty, context);

        String ttsDescription = generateTaskTtsText(topic, difficulty, context);

        return LearningTask.builder()
                .id("conv_" + System.currentTimeMillis())
                .title("🗣️ Conversation: " + topic)
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.CONVERSATION)
                .difficulty(difficulty)
                .examples(examples)
                .metadata(Map.of(
                        "topic", topic,
                        "suggested_duration", getSuggestedDuration(difficulty),
                        "difficulty_level", difficulty.getLevel(),
                        "tips", generateConversationTips(difficulty)
                ))
                .build();
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
        String level = getLevelDescription(context.getCurrentLevel());
        String instructions = getInstructionsForLevel(context.getCurrentLevel());
        String systemPrompt = getSystemPrompt(context.getCurrentLevel());

        return String.format("""
            %s
            
            %s
            
            === Conversation Context ===
            Student Level: %s (%.1f/100)
            Current Topic: %s
            Exchanges so far: %d
            Average Response Time: %.1fs
            
            === Recent Messages (last %d) ===
            %s
            
            === Student's Latest Message ===
            %s
            
            === Your Response Guidelines ===
            %s
            
            Remember: Respond ONLY in English. Be natural, engaging, and supportive.
            """,
                systemPrompt,
                "You are an AI English tutor. Help the student practice conversation.",
                level,
                context.getCurrentLevel(),
                state.currentTopic != null ? state.currentTopic : "general conversation",
                state.turnCount,
                state.averageResponseTime / 1000.0,
                Math.min(4, state.history.size()),
                String.join("\n", state.history.subList(
                        Math.max(0, state.history.size() - 4), state.history.size())),
                userInput,
                instructions
        );
    }

    private String getSystemPrompt(double level) {
        if (level < 30) {
            return "You are speaking with a BEGINNER English learner. Use VERY simple vocabulary, short sentences (5-10 words), and basic grammar (present simple, past simple). Be EXTREMELY patient and encouraging. Avoid idioms and complex structures.";
        } else if (level < 60) {
            return "You are speaking with an INTERMEDIATE English learner. Use moderate vocabulary and grammar (present perfect, conditionals). You can introduce some common idioms but explain them briefly if needed. Encourage elaboration.";
        } else if (level < 85) {
            return "You are speaking with an ADVANCED English learner. Use natural idiomatic expressions, complex grammar (passive voice, reported speech), and varied vocabulary. Engage in deeper discussions about abstract topics.";
        } else {
            return "You are speaking with an EXPERT English speaker. Feel free to use sophisticated vocabulary, complex grammatical structures, and nuanced expressions. Challenge the speaker with thought-provoking questions and topics.";
        }
    }

    private String getInstructionsForLevel(double level) {
        if (level < 30) {
            return """
                1. Keep responses under 50 words
                2. Focus on basic comprehension
                3. Use simple question structures
                4. Repeat key vocabulary naturally
                5. Praise often for encouragement""";
        } else if (level < 60) {
            return """
                1. Keep responses between 50-100 words
                2. Encourage elaboration with follow-up questions
                3. Gently introduce new vocabulary in context
                4. Correct major errors by rephrasing
                5. Vary sentence structures""";
        } else if (level < 85) {
            return """
                1. Responses can be up to 150 words
                2. Use a mix of simple and complex structures
                3. Introduce idioms and explain subtly
                4. Challenge with nuanced topics
                5. Provide constructive feedback on style""";
        } else {
            return """
                1. Responses up to 200 words, be thorough
                2. Use sophisticated vocabulary naturally
                3. Discuss abstract concepts and ideas
                4. Provide detailed feedback on language use
                5. Encourage critical thinking and debate""";
        }
    }

    private String getLevelDescription(double level) {
        if (level < 30) return "BEGINNER (A1-A2)";
        if (level < 60) return "INTERMEDIATE (B1-B2)";
        if (level < 85) return "ADVANCED (C1)";
        return "EXPERT (C2)";
    }

    private void updateStateFromAnalysis(ConversationState state, LearningContext context) {
        if (context.getLastAnalysis() != null) {
            state.fluencyScore = (state.fluencyScore + context.getLastAnalysis().getFluencyScore()) / 2;
        }
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

    private void updateFluencyScore(ConversationState state, String aiResponse) {
        if (aiResponse.length() > 100) {
            state.fluencyScore = Math.min(100, state.fluencyScore + 1);
        }
    }

    private String generateDisplayText(String aiResponse, ConversationState state) {
        StringBuilder display = new StringBuilder();

        display.append("🗣️ CONVERSATION\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append(aiResponse).append("\n\n");

        display.append("📊 CONVERSATION STATS\n");
        display.append("────────────────────\n");
        display.append(String.format("  Exchanges: %d\n", state.turnCount));
        display.append(String.format("  Fluency score: %.1f%%\n", calculateFluencyScore(state)));
        display.append(String.format("  Vocabulary used: %d unique words\n", state.vocabularyUsed.size()));
        display.append(String.format("  Topics discussed: %d\n\n", state.topicsDiscussed.size()));

        display.append("Keep the conversation going! 😊\n");

        return display.toString();
    }

    private String generateTtsText(String aiResponse, ConversationState state) {
        StringBuilder tts = new StringBuilder();

        String cleanResponse = aiResponse.replaceAll("[\\*\\_\\`\\#]", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1")
                .trim();

        tts.append(cleanResponse).append(" ");

        if (state.turnCount % 5 == 0) {
            tts.append(String.format("You've had %d exchanges so far. ", state.turnCount));
            tts.append(String.format("You've used %d different words. ", state.vocabularyUsed.size()));
            tts.append("Keep up the great work! ");
        }

        return tts.toString();
    }

    private String generateTaskDisplayText(String topic, LearningTask.DifficultyLevel difficulty,
                                           LearningContext context) {
        StringBuilder display = new StringBuilder();

        String levelText = switch (difficulty) {
            case BEGINNER -> "beginner-friendly";
            case INTERMEDIATE -> "intermediate";
            case ADVANCED -> "advanced";
            case EXPERT -> "expert-level";
        };

        display.append("🗣️ CONVERSATION TOPIC: ").append(topic.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("Let's have a ").append(levelText).append(" conversation about '")
                .append(topic).append("'.\n\n");

        display.append(String.format("📊 Your level: %.1f/100\n\n", context.getCurrentLevel()));

        display.append("🎯 CONVERSATION GOALS\n");
        display.append("────────────────────\n");
        display.append("  • Practice speaking naturally about this topic\n");
        display.append("  • Express your opinions and ideas\n");
        display.append("  • Learn topic-specific vocabulary\n");
        display.append("  • Improve your fluency\n\n");

        display.append(String.format("⏱️ Suggested duration: %d minutes\n\n", getSuggestedDuration(difficulty)));

        display.append("I'll guide the conversation and provide feedback along the way.\n");

        return display.toString();
    }

    private String generateTaskTtsText(String topic, LearningTask.DifficultyLevel difficulty,
                                       LearningContext context) {
        StringBuilder tts = new StringBuilder();

        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic, topic);
        String levelText = switch (difficulty) {
            case BEGINNER -> "beginner friendly";
            case INTERMEDIATE -> "intermediate";
            case ADVANCED -> "advanced";
            case EXPERT -> "expert level";
        };

        tts.append("Let's have a ").append(levelText).append(" conversation about ")
                .append(topicSpeech).append(". ");

        tts.append("Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. ");

        tts.append("Conversation goals include: practicing natural speech, expressing your opinions, ")
                .append("learning topic specific vocabulary, and improving fluency. ");

        tts.append("Suggested duration is ").append(getSuggestedDuration(difficulty))
                .append(" minutes. ");

        tts.append("I will guide the conversation and provide feedback.");

        return tts.toString();
    }

    private String selectPersonalizedTopic(ConversationState state, LearningContext context) {
        String[] beginnerTopics = {
                "Daily routines", "My family", "Favorite foods", "Weather today",
                "Weekend plans", "My hobbies", "Learning English", "My hometown",
                "Favorite movies", "Music preferences"
        };

        String[] intermediateTopics = {
                "Travel experiences", "Career goals", "Technology impact",
                "Cultural differences", "Environmental issues", "Health and fitness",
                "Education system", "Social media", "Future predictions",
                "Work-life balance"
        };

        String[] advancedTopics = {
                "Global economy", "Political systems", "Philosophical questions",
                "Scientific breakthroughs", "Art and creativity", "Human psychology",
                "Social justice", "Technological ethics", "Climate change solutions",
                "Future of humanity"
        };

        double level = context.getCurrentLevel();
        String[] topics;

        if (level < 30) topics = beginnerTopics;
        else if (level < 60) topics = intermediateTopics;
        else topics = advancedTopics;

        if (state != null && !state.topicsDiscussed.isEmpty()) {
            List<String> availableTopics = new ArrayList<>(Arrays.asList(topics));
            availableTopics.removeAll(state.topicsDiscussed);

            if (!availableTopics.isEmpty()) {
                return availableTopics.get(ThreadLocalRandom.current().nextInt(availableTopics.size()));
            }
        }

        return topics[ThreadLocalRandom.current().nextInt(topics.length)];
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
            case BEGINNER -> 10;
            case INTERMEDIATE -> 15;
            case ADVANCED -> 20;
            case EXPERT -> 25;
        };
    }

    private double calculateFluencyScore(ConversationState state) {
        if (state == null) return 50.0;

        double baseScore = state.fluencyScore;
        double turnBonus = Math.min(20, state.turnCount * 0.5);
        double responseTimeBonus = Math.min(10, 1000 / Math.max(1, state.averageResponseTime));

        return Math.min(100, baseScore + turnBonus + responseTimeBonus);
    }

    private double calculateVocabularyScore(ConversationState state) {
        if (state == null) return 50.0;

        int uniqueWords = state.vocabularyUsed.size();
        return Math.min(100, 50 + uniqueWords * 0.5);
    }

    private double calculateGrammarScore(ConversationState state) {
        if (state == null) return 50.0;

        return 60 + (state.turnCount * 0.2);
    }

    private double calculateEngagementScore(ConversationState state) {
        if (state == null) return 0;
        return Math.min(100, state.turnCount * 2);
    }

    private double calculateConversationDepth(ConversationState state) {
        if (state == null || state.topicsDiscussed.isEmpty()) return 0;

        double topicVariety = Math.min(50, state.topicsDiscussed.size() * 5);
        double turnDepth = Math.min(50, state.turnCount * 0.5);

        return topicVariety + turnDepth;
    }

    private double calculateProgress(ConversationState state) {
        if (state == null) return 0;

        double turnProgress = Math.min(40, state.turnCount * 2) * TURN_PROGRESS_WEIGHT;
        double fluencyProgress = state.fluencyScore * FLUENCY_PROGRESS_WEIGHT;
        double vocabularyProgress = calculateVocabularyScore(state) * VOCABULARY_PROGRESS_WEIGHT;
        double grammarProgress = calculateGrammarScore(state) * GRAMMAR_PROGRESS_WEIGHT;

        return Math.min(100, turnProgress + fluencyProgress + vocabularyProgress + grammarProgress);
    }

    private double calculateOverallProgress(Map<String, Double> skills) {
        return skills.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private List<String> generateRecommendations(ConversationState state) {
        List<String> recommendations = new ArrayList<>();

        if (state == null) return recommendations;

        if (state.turnCount < 10) {
            recommendations.add("Try to have longer conversations - aim for 10+ exchanges");
        }

        if (calculateFluencyScore(state) < 60) {
            recommendations.add("Practice speaking more smoothly - focus on connecting your ideas");
        }

        if (state.topicsDiscussed.size() < 3) {
            recommendations.add("Explore different topics to expand your vocabulary");
        }

        if (state.vocabularyUsed.size() < 20) {
            recommendations.add("Try to use a wider variety of words in your responses");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great progress! Keep practicing regularly");
        }

        return recommendations;
    }

    private LearningMode determineNextMode(LearningContext context, ConversationState state) {
        if (state == null) return LearningMode.CONVERSATION;

        if (state.fluencyScore < 50) {
            return LearningMode.PRONUNCIATION;
        } else if (state.turnCount > 30) {
            return LearningMode.EXERCISE;
        } else if (calculateVocabularyScore(state) < 60) {
            return LearningMode.VOCABULARY;
        } else if (calculateGrammarScore(state) < 65) {
            return LearningMode.GRAMMAR;
        }

        return LearningMode.CONVERSATION;
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

        if (state.turnCount >= ACHIEVEMENT_TURNS_50) {
            achievements.add("💬 50+ conversation exchanges");
        }
        if (state.turnCount >= ACHIEVEMENT_TURNS_100) {
            achievements.add("🎯 100+ exchanges - great conversation!");
        }
        if (state.turnCount >= ACHIEVEMENT_TURNS_200) {
            achievements.add("🏆 200+ exchanges - conversation master!");
        }

        if (state.fluencyScore >= ACHIEVEMENT_FLUENCY_80) {
            achievements.add("⚡ Excellent fluency!");
        }

        if (state.topicsDiscussed.size() >= 10) {
            achievements.add("🌍 Explored 10+ different topics");
        }

        if (state.vocabularyUsed.size() >= 100) {
            achievements.add("📚 Rich vocabulary (100+ words)");
        }

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        ConversationState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("history", new ArrayList<>(state.history));
        stateMap.put("currentTopic", state.currentTopic);
        stateMap.put("turnCount", state.turnCount);
        stateMap.put("fluencyScore", state.fluencyScore);
        stateMap.put("averageResponseTime", state.averageResponseTime);
        stateMap.put("topicsDiscussed", new ArrayList<>(state.topicsDiscussed));
        stateMap.put("vocabularyUsed", new HashMap<>(state.vocabularyUsed));

        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        ConversationState state = new ConversationState();

        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) stateMap.getOrDefault("history", Collections.emptyList());
        state.history.addAll(history);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.turnCount = (int) stateMap.getOrDefault("turnCount", 0);
        state.fluencyScore = (double) stateMap.getOrDefault("fluencyScore", 50.0);
        state.averageResponseTime = (double) stateMap.getOrDefault("averageResponseTime", 0.0);

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
        stats.put("totalExchanges", state.turnCount);
        stats.put("fluencyScore", state.fluencyScore);
        stats.put("vocabularySize", state.vocabularyUsed.size());
        stats.put("topicsExplored", state.topicsDiscussed.size());
        stats.put("averageResponseTime", state.averageResponseTime / 1000.0);
        stats.put("currentTopic", state.currentTopic);

        return stats;
    }
}