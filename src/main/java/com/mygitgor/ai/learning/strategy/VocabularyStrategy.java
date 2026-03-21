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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class VocabularyStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(VocabularyStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, VocabularyState> sessions = new ConcurrentHashMap<>();

    private static final double BEGINNER_THRESHOLD = 30.0;
    private static final double INTERMEDIATE_THRESHOLD = 60.0;
    private static final double ADVANCED_THRESHOLD = 85.0;

    private static final int WORDS_PER_TOPIC = 10;
    private static final int MASTERY_LEVEL_GOOD = 3;
    private static final int MASTERY_LEVEL_EXCELLENT = 5;
    private static final double RETENTION_GOOD = 60.0;
    private static final double RETENTION_EXCELLENT = 80.0;

    private static final int ACHIEVEMENT_WORDS_50 = 50;
    private static final int ACHIEVEMENT_WORDS_100 = 100;
    private static final int ACHIEVEMENT_WORDS_250 = 250;
    private static final int ACHIEVEMENT_WORDS_500 = 500;
    private static final int ACHIEVEMENT_WORDS_1000 = 1000;
    private static final int ACHIEVEMENT_TOPICS_5 = 5;
    private static final int ACHIEVEMENT_TOPICS_10 = 10;
    private static final int ACHIEVEMENT_TOPICS_20 = 20;
    private static final double ACHIEVEMENT_RETENTION_70 = 70.0;
    private static final double ACHIEVEMENT_RETENTION_85 = 85.0;
    private static final double ACHIEVEMENT_RETENTION_95 = 95.0;

    private static final Map<String, String> TOPIC_TO_SPEECH = new HashMap<>();

    private static final Map<String, List<VocabularyTopic>> vocabularyTopics = new HashMap<>();

    static {
        vocabularyTopics.put("beginner", Arrays.asList(
                new VocabularyTopic("Family", "Basic family members and relationships",
                        Arrays.asList("mother", "father", "brother", "sister", "grandparents", "aunt", "uncle", "cousin")),

                new VocabularyTopic("Food", "Common foods, meals, and eating habits",
                        Arrays.asList("breakfast", "lunch", "dinner", "apple", "bread", "rice", "meat", "vegetables")),

                new VocabularyTopic("Animals", "Common domestic and wild animals",
                        Arrays.asList("dog", "cat", "bird", "fish", "horse", "cow", "lion", "elephant")),

                new VocabularyTopic("Colors", "Basic colors and descriptions",
                        Arrays.asList("red", "blue", "green", "yellow", "black", "white", "brown", "orange")),

                new VocabularyTopic("Numbers", "Numbers, dates, and quantities",
                        Arrays.asList("one", "two", "three", "first", "second", "dozen", "hundred", "thousand")),

                new VocabularyTopic("Daily Routine", "Everyday activities and habits",
                        Arrays.asList("wake up", "get dressed", "have breakfast", "go to work", "take a shower", "go to bed")),

                new VocabularyTopic("Weather", "Weather conditions and descriptions",
                        Arrays.asList("sunny", "rainy", "cloudy", "windy", "hot", "cold", "warm", "storm")),

                new VocabularyTopic("Clothes", "Items of clothing and accessories",
                        Arrays.asList("shirt", "pants", "dress", "shoes", "hat", "jacket", "socks", "glasses")),

                new VocabularyTopic("House", "Rooms and household items",
                        Arrays.asList("kitchen", "bedroom", "bathroom", "living room", "table", "chair", "bed", "window")),

                new VocabularyTopic("School", "School subjects and classroom objects",
                        Arrays.asList("teacher", "student", "book", "pen", "desk", "homework", "exam", "lesson"))
        ));

        vocabularyTopics.put("intermediate", Arrays.asList(
                new VocabularyTopic("Work", "Professional environment and career",
                        Arrays.asList("colleague", "deadline", "promotion", "salary", "interview", "resume", "manager", "project")),

                new VocabularyTopic("Travel", "Transportation and tourism",
                        Arrays.asList("destination", "accommodation", "itinerary", "passport", "visa", "luggage", "booking", "sightseeing")),

                new VocabularyTopic("Health", "Health, fitness, and medical terms",
                        Arrays.asList("exercise", "nutrition", "symptoms", "treatment", "appointment", "prescription", "recovery", "wellness")),

                new VocabularyTopic("Technology", "Modern technology and digital life",
                        Arrays.asList("device", "application", "software", "hardware", "network", "database", "interface", "update")),

                new VocabularyTopic("Environment", "Environmental issues and nature",
                        Arrays.asList("pollution", "recycling", "sustainability", "conservation", "climate", "ecosystem", "renewable", "habitat")),

                new VocabularyTopic("Education", "Learning and academic life",
                        Arrays.asList("curriculum", "assignment", "lecture", "seminar", "degree", "scholarship", "research", "thesis")),

                new VocabularyTopic("Culture", "Customs, traditions, and society",
                        Arrays.asList("tradition", "festival", "celebration", "custom", "heritage", "diversity", "community", "identity")),

                new VocabularyTopic("Business", "Commerce and professional communication",
                        Arrays.asList("negotiation", "contract", "investment", "revenue", "partnership", "strategy", "marketing", "client")),

                new VocabularyTopic("Media", "News, entertainment, and social media",
                        Arrays.asList("headline", "article", "broadcast", "platform", "influencer", "subscription", "content", "audience")),

                new VocabularyTopic("Sports", "Sports, games, and competitions",
                        Arrays.asList("tournament", "championship", "competition", "athlete", "spectator", "score", "victory", "defeat"))
        ));

        vocabularyTopics.put("advanced", Arrays.asList(
                new VocabularyTopic("Psychology", "Mental processes and behavior",
                        Arrays.asList("cognitive", "perception", "motivation", "personality", "emotion", "behavioral", "consciousness", "subconscious")),

                new VocabularyTopic("Philosophy", "Philosophical concepts and thinkers",
                        Arrays.asList("existence", "ethics", "logic", "metaphysics", "rational", "empirical", "subjective", "objective")),

                new VocabularyTopic("Economics", "Economic systems and principles",
                        Arrays.asList("inflation", "recession", "gdp", "market", "supply", "demand", "equilibrium", "elasticity")),

                new VocabularyTopic("Politics", "Political systems and governance",
                        Arrays.asList("democracy", "legislation", "policy", "election", "constituency", "parliament", "diplomacy", "sovereignty")),

                new VocabularyTopic("Science", "Scientific methods and discoveries",
                        Arrays.asList("hypothesis", "experiment", "analysis", "empirical", "quantum", "molecular", "genetic", "synthesis")),

                new VocabularyTopic("Art", "Artistic movements and criticism",
                        Arrays.asList("aesthetic", "abstract", "expressionism", "impressionism", "composition", "perspective", "medium", "curator")),

                new VocabularyTopic("Literature", "Literary terms and genres",
                        Arrays.asList("narrative", "protagonist", "antagonist", "metaphor", "allegory", "symbolism", "irony", "satire")),

                new VocabularyTopic("Law", "Legal terms and justice system",
                        Arrays.asList("jurisdiction", "precedent", "litigation", "defendant", "plaintiff", "verdict", "appeal", "constitution")),

                new VocabularyTopic("Medicine", "Advanced medical terminology",
                        Arrays.asList("diagnosis", "prognosis", "chronic", "acute", "benign", "malignant", "symptom", "syndrome")),

                new VocabularyTopic("Engineering", "Technical and engineering concepts",
                        Arrays.asList("infrastructure", "innovation", "prototype", "efficiency", "optimization", "automation", "robotics", "sustainability"))
        ));

        TOPIC_TO_SPEECH.put("Family", "family");
        TOPIC_TO_SPEECH.put("Food", "food");
        TOPIC_TO_SPEECH.put("Animals", "animals");
        TOPIC_TO_SPEECH.put("Colors", "colors");
        TOPIC_TO_SPEECH.put("Numbers", "numbers");
        TOPIC_TO_SPEECH.put("Daily Routine", "daily routine");
        TOPIC_TO_SPEECH.put("Weather", "weather");
        TOPIC_TO_SPEECH.put("Clothes", "clothes");
        TOPIC_TO_SPEECH.put("House", "house");
        TOPIC_TO_SPEECH.put("School", "school");
        TOPIC_TO_SPEECH.put("Work", "work");
        TOPIC_TO_SPEECH.put("Travel", "travel");
        TOPIC_TO_SPEECH.put("Health", "health");
        TOPIC_TO_SPEECH.put("Technology", "technology");
        TOPIC_TO_SPEECH.put("Environment", "environment");
        TOPIC_TO_SPEECH.put("Education", "education");
        TOPIC_TO_SPEECH.put("Culture", "culture");
        TOPIC_TO_SPEECH.put("Business", "business");
        TOPIC_TO_SPEECH.put("Media", "media");
        TOPIC_TO_SPEECH.put("Sports", "sports");
        TOPIC_TO_SPEECH.put("Psychology", "psychology");
        TOPIC_TO_SPEECH.put("Philosophy", "philosophy");
        TOPIC_TO_SPEECH.put("Economics", "economics");
        TOPIC_TO_SPEECH.put("Politics", "politics");
        TOPIC_TO_SPEECH.put("Science", "science");
        TOPIC_TO_SPEECH.put("Art", "art");
        TOPIC_TO_SPEECH.put("Literature", "literature");
        TOPIC_TO_SPEECH.put("Law", "law");
        TOPIC_TO_SPEECH.put("Medicine", "medicine");
        TOPIC_TO_SPEECH.put("Engineering", "engineering");
    }

    private static class VocabularyTopic {
        final String name;
        final String description;
        final List<String> keywords;

        VocabularyTopic(String name, String description, List<String> keywords) {
            this.name = name;
            this.description = description;
            this.keywords = keywords;
        }
    }

    private static class VocabularyState {
        final List<String> learnedWords = new ArrayList<>();
        final Map<String, Integer> wordMastery = new HashMap<>();
        final Map<String, Integer> wordAttempts = new HashMap<>();
        final List<Double> retentionHistory = new ArrayList<>();
        final List<String> masteredTopics = new ArrayList<>();
        String currentTopic;
        int wordsLearned;
        int wordsMastered;
        double averageRetention;
        double bestRetention;
        long totalTimeSpent;
        long sessionStartTime;

        void startExercise() {
            sessionStartTime = System.currentTimeMillis();
        }

        void endExercise() {
            if (sessionStartTime > 0) {
                totalTimeSpent += System.currentTimeMillis() - sessionStartTime;
                sessionStartTime = 0;
            }
        }

        void addWord(String word, boolean correct) {
            if (!learnedWords.contains(word)) {
                learnedWords.add(word);
                wordsLearned = learnedWords.size();
            }

            wordAttempts.merge(word, 1, Integer::sum);

            if (correct) {
                int currentMastery = wordMastery.getOrDefault(word, 0);
                int newMastery = Math.min(MASTERY_LEVEL_EXCELLENT, currentMastery + 1);
                wordMastery.put(word, newMastery);

                if (newMastery == MASTERY_LEVEL_EXCELLENT && currentMastery < MASTERY_LEVEL_EXCELLENT) {
                    wordsMastered++;
                }
            }
        }

        double getWordScore(String word) {
            int mastery = wordMastery.getOrDefault(word, 0);
            return mastery * 20.0;
        }

        List<String> getWeakWords() {
            return wordMastery.entrySet().stream()
                    .filter(e -> e.getValue() < MASTERY_LEVEL_GOOD)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        List<String> getStrongWords() {
            return wordMastery.entrySet().stream()
                    .filter(e -> e.getValue() >= MASTERY_LEVEL_EXCELLENT)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }

    public VocabularyStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("VocabularyStrategy initialized with {} topics", vocabularyTopics.size());
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.VOCABULARY;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            VocabularyState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new VocabularyState());

            if (state.currentTopic == null) {
                VocabularyTopic topic = getNextTopic(context);
                state.currentTopic = topic.name;
            }

            VocabularyTopic currentTopic = getTopicByName(state.currentTopic);
            state.startExercise();

            analyzeVocabularyUsage(userInput, state, currentTopic);

            String prompt = buildDetailedVocabularyPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            state.endExercise();
            updateWordMastery(state);
            calculateRetention(state);

            String displayText = generateDisplayText(aiResponse, state, currentTopic);

            String ttsText = generateTtsText(aiResponse, state, currentTopic);

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
            VocabularyState state = sessions.get(context.getUserId());
            VocabularyTopic currentTopic = state != null && state.currentTopic != null
                    ? getTopicByName(state.currentTopic)
                    : getNextTopic(context);

            String prompt = buildDetailedVocabularyPrompt(userInput, state, context, currentTopic);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            return generateTtsText(aiResponse, state, currentTopic);
        }, executor);
    }

    private String generateTtsText(String aiResponse, VocabularyState state, VocabularyTopic topic) {
        StringBuilder tts = new StringBuilder();

        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic.name, topic.name);
        tts.append("Vocabulary topic: ").append(topicSpeech).append(". ");

        String cleanResponse = extractMainVocabularyMessage(aiResponse);
        if (!cleanResponse.isEmpty()) {
            tts.append(cleanResponse);
            if (!cleanResponse.endsWith(". ") && !cleanResponse.endsWith(".")) {
                tts.append(". ");
            } else {
                tts.append(" ");
            }
        }

        List<String> newWords = topic.keywords.stream()
                .limit(5)
                .collect(Collectors.toList());
        if (!newWords.isEmpty()) {
            tts.append("Learn words like: ");
            for (int i = 0; i < newWords.size(); i++) {
                tts.append(newWords.get(i));
                if (i < newWords.size() - 1) tts.append(", ");
            }
            tts.append(". ");
        }

        List<String> tips = generateVocabularyTips(topic);
        if (!tips.isEmpty()) {
            tts.append("Tip: ").append(tips.get(0)).append(". ");
        }

        if (state != null && state.wordsLearned > 0) {
            tts.append(String.format("You're expanding your vocabulary! ", state.wordsLearned));
        }
        tts.append("Keep practicing!");

        return tts.toString();
    }

    /**
     * Извлекает основное сообщение от AI, убирая статистику и заголовки
     */
    private String extractMainVocabularyMessage(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return "";
        }

        // Ищем начало статистики
        String[] statsMarkers = {
                "YOUR PROGRESS",
                "📊 YOUR PROGRESS",
                "💪 STRONG WORDS",
                "🎯 NEED MORE PRACTICE",
                "💡 PRACTICE TIPS",
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

        // Убираем заголовок "VOCABULARY MASTERY" если есть
        mainMessage = mainMessage.replace("📚 VOCABULARY MASTERY:", "")
                .replace("VOCABULARY MASTERY:", "")
                .trim();

        // Очищаем от лишних символов и переносов строк
        mainMessage = mainMessage.replaceAll("[\\n\\r]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Обрезаем слишком длинное сообщение
        if (mainMessage.length() > 200) {
            mainMessage = mainMessage.substring(0, 200) + "...";
        }

        return mainMessage;
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            VocabularyState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("words_learned", (double) state.wordsLearned);
                skills.put("words_mastered", (double) state.wordsMastered);
                skills.put("retention_rate", state.averageRetention);
                skills.put("best_retention", state.bestRetention);
                skills.put("mastery_average", calculateAverageMastery(state));
                skills.put("topics_covered", (double) state.masteredTopics.size());

                state.wordMastery.forEach((word, mastery) ->
                        skills.put("word_" + word, mastery * 20.0));

                if (!state.retentionHistory.isEmpty()) {
                    skills.put("last_retention", state.retentionHistory.get(state.retentionHistory.size() - 1));
                }
            }

            List<String> achievements = getAchievements(state);

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.wordsLearned / 5 : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(achievements)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            VocabularyState state = sessions.get(context.getUserId());
            return generateNextTaskWithTts(context, state);
        }, executor);
    }

    private LearningTask generateNextTaskWithTts(LearningContext context, VocabularyState state) {
        VocabularyTopic topic;
        if (state != null && state.currentTopic != null) {
            topic = getTopicByName(state.currentTopic);
        } else {
            topic = getNextTopic(context);
            if (state != null) {
                state.currentTopic = topic.name;
            }
        }

        List<String> wordList = generateWordList(topic, context.getCurrentLevel());
        List<String> weakWords = state != null ? state.getWeakWords() : new ArrayList<>();

        String displayDescription = generateTaskDisplayText(topic, context);

        String ttsDescription = generateTaskTtsText(topic, context);

        return LearningTask.builder()
                .id("voc_" + System.currentTimeMillis())
                .title("Vocabulary: " + topic.name)
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.VOCABULARY)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(wordList)
                .addMetadata("topic", topic.name)
                .addMetadata("words", wordList)
                .addMetadata("weak_words", weakWords)
                .addMetadata("count", wordList.size())
                .addMetadata("difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel())
                .addMetadata("tips", generateVocabularyTips(topic))
                .build();
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Vocabulary Learning Strategy";
    }

    private VocabularyTopic getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<VocabularyTopic> topics = vocabularyTopics.get(level);
        return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
    }

    private VocabularyTopic getTopicByName(String name) {
        for (List<VocabularyTopic> topics : vocabularyTopics.values()) {
            for (VocabularyTopic topic : topics) {
                if (topic.name.equals(name)) {
                    return topic;
                }
            }
        }
        return vocabularyTopics.get("beginner").get(0);
    }

    private String determineLevel(double level) {
        if (level < BEGINNER_THRESHOLD) return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private String buildDetailedVocabularyPrompt(String userInput, VocabularyState state,
                                                 LearningContext context, VocabularyTopic topic) {
        double level = context.getCurrentLevel();
        String levelGuide = getLevelGuide(level);
        List<String> weakWords = state != null ? state.getWeakWords() : new ArrayList<>();

        return String.format("""
            You are an expert, warm and highly motivating AI English Vocabulary Tutor.
            Your goal is to help students expand their active vocabulary, remember words long-term.

            === STRICT RULES ===
            - ALWAYS respond ONLY in English
            - Adapt perfectly to the student's level (%.1f/100)
            - Always start with genuine praise
            - Never say "wrong". Use positive reinforcement

            === CURRENT TOPIC: %s ===
            • Description: %s
            • Key vocabulary: %s
            
            === STUDENT PROFILE ===
            • Level: %s
            • Words learned: %d
            • Average retention: %.1f%%
            • Weak words: %s

            === TEACHING GUIDELINES ===
            %s

            === STUDENT'S MESSAGE ===
            "%s"

            === YOUR TASK ===
            1. Warm positive opening
            2. Highlight correctly used words
            3. Suggest improvements and alternatives
            4. Introduce 3-5 new words with examples
            5. Give a memorable tip for retention
            6. Interactive challenge
            7. Motivating closing

            Generate a complete, structured vocabulary feedback response.
            """,
                level,
                topic.name,
                topic.description,
                String.join(", ", topic.keywords),
                determineLevel(level),
                state != null ? state.wordsLearned : 0,
                state != null ? state.averageRetention : 0.0,
                weakWords.isEmpty() ? "none" : String.join(", ", weakWords),
                levelGuide,
                userInput
        );
    }

    private String getLevelGuide(double level) {
        if (level < BEGINNER_THRESHOLD) {
            return """
                • Use VERY simple language
                • Focus on basic, common words
                • Provide clear examples
                • Use repetition for retention
                • Be extremely encouraging""";
        } else if (level < INTERMEDIATE_THRESHOLD) {
            return """
                • Use clear, moderate language
                • Introduce synonyms and antonyms
                • Show words in context
                • Discuss word families
                • Encourage active use""";
        } else if (level < ADVANCED_THRESHOLD) {
            return """
                • Use natural, sophisticated language
                • Discuss collocations and idioms
                • Explore nuances and connotations
                • Analyze word origins
                • Challenge with complex contexts""";
        } else {
            return """
                • Use advanced, nuanced language
                • Discuss stylistic choices
                • Explore rare and specialized terms
                • Analyze authentic texts
                • Discuss etymology and word formation""";
        }
    }

    private String generateDisplayText(String aiResponse, VocabularyState state, VocabularyTopic topic) {
        StringBuilder display = new StringBuilder();

        display.append("📚 VOCABULARY MASTERY: ").append(topic.name.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════════\n\n");

        display.append(aiResponse).append("\n\n");

        display.append("📊 YOUR PROGRESS\n");
        display.append("────────────────\n");
        display.append(String.format("  Words learned: %d\n", state.wordsLearned));
        display.append(String.format("  Words mastered: %d\n", state.wordsMastered));
        display.append(String.format("  Retention rate: %.1f%%\n", state.averageRetention));
        display.append("\n");

        List<String> strongWords = state.getStrongWords();
        if (!strongWords.isEmpty()) {
            display.append("💪 STRONG WORDS\n");
            display.append("───────────────\n");
            strongWords.stream().limit(10).forEach(word -> display.append("  • ").append(word).append("\n"));
            display.append("\n");
        }

        List<String> weakWords = state.getWeakWords();
        if (!weakWords.isEmpty()) {
            display.append("🎯 NEED MORE PRACTICE\n");
            display.append("────────────────────\n");
            weakWords.stream().limit(10).forEach(word -> display.append("  • ").append(word).append("\n"));
            display.append("\n");
        }

        display.append("💡 PRACTICE TIPS\n");
        display.append("────────────────\n");
        generateVocabularyTips(topic).forEach(tip -> display.append("  • ").append(tip).append("\n"));

        return display.toString();
    }

    private String generateTaskDisplayText(VocabularyTopic topic, LearningContext context) {
        StringBuilder display = new StringBuilder();

        display.append("📚 TOPIC: ").append(topic.name.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("📝 Description: ").append(topic.description).append("\n\n");

        display.append(String.format("📊 Your level: %.1f/100\n", context.getCurrentLevel()));
        display.append("📈 Difficulty: ").append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append("\n\n");

        display.append("🔤 WORDS TO LEARN\n");
        display.append("────────────────\n");
        for (String word : topic.keywords) {
            display.append("  • ").append(word).append("\n");
        }
        display.append("\n");

        display.append("💡 Practice using these words in sentences and conversations!\n");

        return display.toString();
    }

    private String generateTaskTtsText(VocabularyTopic topic, LearningContext context) {
        StringBuilder tts = new StringBuilder();

        String topicSpeech = TOPIC_TO_SPEECH.getOrDefault(topic.name, topic.name);

        tts.append("New vocabulary topic: ").append(topicSpeech).append(". ");
        tts.append(topic.description).append(". ");

        tts.append("Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. Difficulty level is ")
                .append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append(". ");

        tts.append("Words to learn: ");
        List<String> words = topic.keywords;
        for (int i = 0; i < Math.min(5, words.size()); i++) {
            tts.append(words.get(i));
            if (i < Math.min(5, words.size()) - 1) {
                tts.append(", ");
            }
        }
        tts.append(". ");

        tts.append("Practice using these words in sentences and conversations.");

        return tts.toString();
    }

    private String generateWordChips(List<String> words) {
        return String.join(", ", words);
    }

    private List<String> generateVocabularyTips(VocabularyTopic topic) {
        List<String> tips = new ArrayList<>();

        tips.add("Create sentences with each new word");
        tips.add("Group related words together");
        tips.add("Use flashcards for daily review");
        tips.add("Practice with the 'spaced repetition' method");

        if (topic.name.equals("Business") || topic.name.equals("Work")) {
            tips.add("Try to use these words in professional emails");
        } else if (topic.name.equals("Travel")) {
            tips.add("Imagine using these words while traveling");
        } else if (topic.name.equals("Science") || topic.name.equals("Technology")) {
            tips.add("Read articles about this topic to see words in context");
        }

        return tips;
    }

    private List<String> generateWordList(VocabularyTopic topic, double level) {
        List<String> words = new ArrayList<>(topic.keywords);

        if (level >= INTERMEDIATE_THRESHOLD) {
            words.addAll(Arrays.asList("additional", "vocabulary", "for", "this", "topic"));
        }

        return words.subList(0, Math.min(WORDS_PER_TOPIC, words.size()));
    }

    private void analyzeVocabularyUsage(String userInput, VocabularyState state, VocabularyTopic topic) {
        String[] words = userInput.toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");

        for (String word : words) {
            if (word.length() > 2) {
                boolean isTargetWord = topic.keywords.stream()
                        .anyMatch(kw -> kw.toLowerCase().contains(word) || word.contains(kw.toLowerCase()));

                state.addWord(word, isTargetWord);
            }
        }
    }

    private void updateWordMastery(VocabularyState state) {
        // Постепенное затухание для слов, которые давно не использовались
        // В реальном приложении здесь будет более сложная логика
    }

    private void calculateRetention(VocabularyState state) {
        if (state.wordMastery.isEmpty()) {
            state.averageRetention = 0;
            return;
        }

        double retention = state.wordMastery.values().stream()
                .mapToDouble(Integer::doubleValue)
                .average()
                .orElse(0) * 20;

        state.averageRetention = retention;
        state.retentionHistory.add(retention);

        if (retention > state.bestRetention) {
            state.bestRetention = retention;
        }

        if (state.retentionHistory.size() > 20) {
            state.retentionHistory.remove(0);
        }
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

        double retentionComponent = state.averageRetention * 0.4;
        double wordsComponent = Math.min(40, state.wordsLearned * 0.1);
        double masteryComponent = state.wordsMastered * 0.5;

        return Math.min(100, retentionComponent + wordsComponent + masteryComponent);
    }

    private List<String> generateRecommendations(VocabularyState state) {
        List<String> recommendations = new ArrayList<>();

        if (state == null) return recommendations;

        if (state.wordsLearned < ACHIEVEMENT_WORDS_50) {
            recommendations.add("Try to learn 10 new words every day");
        }

        if (state.averageRetention < RETENTION_GOOD) {
            recommendations.add("Review previously learned words regularly");
            recommendations.add("Use spaced repetition for better retention");
        }

        List<String> weakWords = state.getWeakWords();
        if (!weakWords.isEmpty()) {
            recommendations.add("Focus on these words: " +
                    weakWords.stream().limit(5).collect(Collectors.joining(", ")));
        }

        if (state.masteredTopics.size() < ACHIEVEMENT_TOPICS_5) {
            recommendations.add("Try to master complete topics for better context");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great progress! Try using new words in conversations");
        }

        return recommendations;
    }

    private LearningMode determineNextMode(LearningContext context, VocabularyState state) {
        if (state == null) return LearningMode.VOCABULARY;

        if (state.wordsLearned >= ACHIEVEMENT_WORDS_100 &&
                state.averageRetention >= RETENTION_EXCELLENT) {
            return LearningMode.CONVERSATION;
        } else if (state.averageRetention < RETENTION_GOOD) {
            return LearningMode.EXERCISE;
        }

        return LearningMode.VOCABULARY;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private List<String> getAchievements(VocabularyState state) {
        List<String> achievements = new ArrayList<>();

        if (state == null) return achievements;

        if (state.wordsLearned >= ACHIEVEMENT_WORDS_1000) {
            achievements.add("Vocabulary Master - 1000+ words!");
        } else if (state.wordsLearned >= ACHIEVEMENT_WORDS_500) {
            achievements.add("Advanced Vocabulary - 500+ words");
        } else if (state.wordsLearned >= ACHIEVEMENT_WORDS_250) {
            achievements.add("Growing Vocabulary - 250+ words");
        } else if (state.wordsLearned >= ACHIEVEMENT_WORDS_100) {
            achievements.add("100+ words learned!");
        } else if (state.wordsLearned >= ACHIEVEMENT_WORDS_50) {
            achievements.add("50+ words - Great start!");
        }

        if (state.bestRetention >= ACHIEVEMENT_RETENTION_95) {
            achievements.add("Excellent memory! " + String.format("%.1f%% retention", state.bestRetention));
        } else if (state.averageRetention >= ACHIEVEMENT_RETENTION_85) {
            achievements.add("Strong retention! " + String.format("%.1f%%", state.averageRetention));
        } else if (state.averageRetention >= ACHIEVEMENT_RETENTION_70) {
            achievements.add("Good retention! " + String.format("%.1f%%", state.averageRetention));
        }

        if (state.wordsMastered >= 50) {
            achievements.add("50+ words mastered!");
        }

        if (state.masteredTopics.size() >= ACHIEVEMENT_TOPICS_20) {
            achievements.add("Vocabulary Explorer - 20+ topics");
        } else if (state.masteredTopics.size() >= ACHIEVEMENT_TOPICS_10) {
            achievements.add("Topic Master - 10+ topics");
        } else if (state.masteredTopics.size() >= ACHIEVEMENT_TOPICS_5) {
            achievements.add("5+ topics completed");
        }

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        VocabularyState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("learnedWords", new ArrayList<>(state.learnedWords));
        stateMap.put("wordMastery", new HashMap<>(state.wordMastery));
        stateMap.put("wordAttempts", new HashMap<>(state.wordAttempts));
        stateMap.put("retentionHistory", new ArrayList<>(state.retentionHistory));
        stateMap.put("masteredTopics", new ArrayList<>(state.masteredTopics));
        stateMap.put("currentTopic", state.currentTopic);
        stateMap.put("wordsLearned", state.wordsLearned);
        stateMap.put("wordsMastered", state.wordsMastered);
        stateMap.put("averageRetention", state.averageRetention);
        stateMap.put("bestRetention", state.bestRetention);
        stateMap.put("totalTimeSpent", state.totalTimeSpent);

        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        VocabularyState state = new VocabularyState();

        @SuppressWarnings("unchecked")
        List<String> learnedWords = (List<String>) stateMap.getOrDefault("learnedWords", Collections.emptyList());
        state.learnedWords.addAll(learnedWords);

        @SuppressWarnings("unchecked")
        Map<String, Integer> wordMastery = (Map<String, Integer>) stateMap.getOrDefault("wordMastery", Collections.emptyMap());
        state.wordMastery.putAll(wordMastery);

        @SuppressWarnings("unchecked")
        Map<String, Integer> wordAttempts = (Map<String, Integer>) stateMap.getOrDefault("wordAttempts", Collections.emptyMap());
        state.wordAttempts.putAll(wordAttempts);

        @SuppressWarnings("unchecked")
        List<Double> retentionHistory = (List<Double>) stateMap.getOrDefault("retentionHistory", Collections.emptyList());
        state.retentionHistory.addAll(retentionHistory);

        @SuppressWarnings("unchecked")
        List<String> masteredTopics = (List<String>) stateMap.getOrDefault("masteredTopics", Collections.emptyList());
        state.masteredTopics.addAll(masteredTopics);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.wordsLearned = (int) stateMap.getOrDefault("wordsLearned", 0);
        state.wordsMastered = (int) stateMap.getOrDefault("wordsMastered", 0);
        state.averageRetention = (double) stateMap.getOrDefault("averageRetention", 0.0);
        state.bestRetention = (double) stateMap.getOrDefault("bestRetention", 0.0);
        state.totalTimeSpent = (long) stateMap.getOrDefault("totalTimeSpent", 0L);

        sessions.put(userId, state);
        logger.debug("Session state restored for user {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Session cleared for user {}", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        VocabularyState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("wordsLearned", state.wordsLearned);
        stats.put("wordsMastered", state.wordsMastered);
        stats.put("averageRetention", state.averageRetention);
        stats.put("bestRetention", state.bestRetention);
        stats.put("topicsCompleted", state.masteredTopics.size());
        stats.put("weakWords", state.getWeakWords());
        stats.put("strongWords", state.getStrongWords());

        return stats;
    }
}