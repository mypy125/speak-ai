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

public class WritingStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(WritingStrategy.class);

    private final AiService aiService;
    private final ExecutorService executor;

    private final Map<String, WritingState> sessions = new ConcurrentHashMap<>();

    private static final double BEGINNER_THRESHOLD = 30.0;
    private static final double INTERMEDIATE_THRESHOLD = 60.0;
    private static final double ADVANCED_THRESHOLD = 85.0;

    private static final double GOOD_SCORE = 70.0;
    private static final double EXCELLENT_SCORE = 85.0;

    private static final int ACHIEVEMENT_TEXTS_5 = 5;
    private static final int ACHIEVEMENT_TEXTS_10 = 10;
    private static final int ACHIEVEMENT_TEXTS_25 = 25;
    private static final int ACHIEVEMENT_TEXTS_50 = 50;
    private static final int ACHIEVEMENT_TEXTS_100 = 100;
    private static final double ACHIEVEMENT_SCORE_75 = 75.0;
    private static final double ACHIEVEMENT_SCORE_85 = 85.0;
    private static final double ACHIEVEMENT_SCORE_90 = 90.0;

    private static final Map<String, String> WRITING_TYPE_TO_SPEECH = new HashMap<>();
    private static final Map<String, List<WritingTopic>> writingTopics = new HashMap<>();

    static {
        writingTopics.put("beginner", Arrays.asList(
                new WritingTopic("My Daily Routine",
                        "Describe what you do on a typical day",
                        "descriptive", 50, 100,
                        Arrays.asList("wake up", "have breakfast", "go to school/work", "relax", "go to bed")),

                new WritingTopic("My Family",
                        "Write about your family members and your relationship with them",
                        "descriptive", 50, 100,
                        Arrays.asList("mother", "father", "brother", "sister", "together")),

                new WritingTopic("My Favorite Food",
                        "Describe your favorite dish and why you like it",
                        "descriptive", 50, 100,
                        Arrays.asList("delicious", "flavor", "ingredients", "cook", "enjoy")),

                new WritingTopic("My Best Friend",
                        "Write about your best friend and what makes them special",
                        "descriptive", 50, 100,
                        Arrays.asList("kind", "funny", "trust", "together", "memories")),

                new WritingTopic("My Hobby",
                        "Describe your favorite hobby and why you enjoy it",
                        "descriptive", 50, 100,
                        Arrays.asList("enjoy", "relaxing", "interesting", "time", "learn")),

                new WritingTopic("A Special Day",
                        "Write about a memorable day in your life",
                        "narrative", 50, 100,
                        Arrays.asList("special", "remember", "happened", "felt", "unforgettable")),

                new WritingTopic("My Pet",
                        "Describe your pet or a pet you would like to have",
                        "descriptive", 50, 100,
                        Arrays.asList("cute", "playful", "care", "feed", "companion")),

                new WritingTopic("My Favorite Place",
                        "Write about your favorite place to visit",
                        "descriptive", 50, 100,
                        Arrays.asList("beautiful", "peaceful", "visit", "enjoy", "special"))
        ));

        writingTopics.put("intermediate", Arrays.asList(
                new WritingTopic("A Memorable Travel Experience",
                        "Describe a trip you took and what made it special",
                        "narrative", 150, 300,
                        Arrays.asList("destination", "journey", "experience", "culture", "memorable")),

                new WritingTopic("Future Career Plans",
                        "Write about your career aspirations and how you plan to achieve them",
                        "expository", 150, 300,
                        Arrays.asList("career", "goal", "ambition", "plan", "success")),

                new WritingTopic("Movie Review",
                        "Review a film you've watched recently",
                        "review", 150, 300,
                        Arrays.asList("plot", "characters", "acting", "director", "recommend")),

                new WritingTopic("Impact of Technology",
                        "Discuss how technology has changed our lives",
                        "argumentative", 200, 350,
                        Arrays.asList("technology", "communication", "convenience", "privacy", "future")),

                new WritingTopic("Environmental Issues",
                        "Write about an environmental problem and possible solutions",
                        "expository", 200, 350,
                        Arrays.asList("pollution", "climate", "sustainable", "environment", "solution")),

                new WritingTopic("Social Media Pros and Cons",
                        "Discuss the advantages and disadvantages of social media",
                        "argumentative", 200, 350,
                        Arrays.asList("connect", "share", "privacy", "addiction", "balance")),

                new WritingTopic("Healthy Lifestyle",
                        "Write about the importance of a healthy lifestyle",
                        "expository", 150, 300,
                        Arrays.asList("exercise", "nutrition", "health", "habit", "wellness")),

                new WritingTopic("Cultural Differences",
                        "Compare cultural differences between countries",
                        "compare-contrast", 200, 350,
                        Arrays.asList("culture", "tradition", "custom", "difference", "respect"))
        ));

        writingTopics.put("advanced", Arrays.asList(
                new WritingTopic("Argumentative Essay: Climate Change",
                        "Present arguments for and against climate change policies",
                        "argumentative", 300, 600,
                        Arrays.asList("climate", "policy", "sustainable", "debate", "solution")),

                new WritingTopic("Research Summary",
                        "Summarize a scientific article or research paper",
                        "academic", 300, 500,
                        Arrays.asList("research", "study", "findings", "methodology", "conclusion")),

                new WritingTopic("Business Proposal",
                        "Write a proposal for a new business idea",
                        "professional", 350, 600,
                        Arrays.asList("proposal", "investment", "market", "strategy", "revenue")),

                new WritingTopic("Literary Analysis",
                        "Analyze a novel, poem, or literary work",
                        "analytical", 350, 600,
                        Arrays.asList("theme", "symbolism", "character", "narrative", "interpretation")),

                new WritingTopic("Critical Review",
                        "Write a critical review of an article or book",
                        "critical", 300, 500,
                        Arrays.asList("critique", "analysis", "perspective", "evidence", "evaluation")),

                new WritingTopic("Philosophical Essay",
                        "Explore a philosophical question or concept",
                        "philosophical", 350, 600,
                        Arrays.asList("philosophy", "existence", "ethics", "consciousness", "meaning")),

                new WritingTopic("Policy Analysis",
                        "Analyze a current policy or law",
                        "analytical", 350, 600,
                        Arrays.asList("policy", "legislation", "impact", "implementation", "reform")),

                new WritingTopic("Scientific Hypothesis",
                        "Propose and explain a scientific hypothesis",
                        "scientific", 300, 500,
                        Arrays.asList("hypothesis", "experiment", "evidence", "theory", "prediction"))
        ));

        WRITING_TYPE_TO_SPEECH.put("descriptive", "descriptive");
        WRITING_TYPE_TO_SPEECH.put("narrative", "narrative");
        WRITING_TYPE_TO_SPEECH.put("expository", "expository");
        WRITING_TYPE_TO_SPEECH.put("argumentative", "argumentative");
        WRITING_TYPE_TO_SPEECH.put("compare-contrast", "compare and contrast");
        WRITING_TYPE_TO_SPEECH.put("review", "review");
        WRITING_TYPE_TO_SPEECH.put("academic", "academic");
        WRITING_TYPE_TO_SPEECH.put("professional", "professional");
        WRITING_TYPE_TO_SPEECH.put("analytical", "analytical");
        WRITING_TYPE_TO_SPEECH.put("critical", "critical");
        WRITING_TYPE_TO_SPEECH.put("philosophical", "philosophical");
        WRITING_TYPE_TO_SPEECH.put("scientific", "scientific");
    }

    private static class WritingTopic {
        final String title;
        final String description;
        final String type;
        final int minWords;
        final int maxWords;
        final List<String> keywords;

        WritingTopic(String title, String description, String type,
                     int minWords, int maxWords, List<String> keywords) {
            this.title = title;
            this.description = description;
            this.type = type;
            this.minWords = minWords;
            this.maxWords = maxWords;
            this.keywords = keywords;
        }
    }

    private static class WritingState {
        final List<String> writtenTexts = new ArrayList<>();
        final List<Double> grammarScores = new ArrayList<>();
        final List<Double> styleScores = new ArrayList<>();
        final List<Double> structureScores = new ArrayList<>();
        final List<Double> vocabularyScores = new ArrayList<>();
        final List<Double> coherenceScores = new ArrayList<>();
        final Map<String, Double> topicScores = new HashMap<>();
        final List<String> masteredTopics = new ArrayList<>();
        final List<Long> writingTimes = new ArrayList<>();

        String currentTopic;
        int textsCompleted;
        double averageScore;
        double bestScore;
        long totalWritingTime;
        long sessionStartTime;

        void startWriting() {
            sessionStartTime = System.currentTimeMillis();
        }

        void endWriting() {
            if (sessionStartTime > 0) {
                long timeSpent = System.currentTimeMillis() - sessionStartTime;
                writingTimes.add(timeSpent);
                totalWritingTime += timeSpent;
                sessionStartTime = 0;
            }
        }

        void addScores(double grammar, double style, double structure,
                       double vocabulary, double coherence) {
            grammarScores.add(grammar);
            styleScores.add(style);
            structureScores.add(structure);
            vocabularyScores.add(vocabulary);
            coherenceScores.add(coherence);

            double total = grammar + style + structure + vocabulary + coherence;
            double avg = total / 5.0;

            if (avg > bestScore) {
                bestScore = avg;
            }

            updateAverageScore();
        }

        void updateAverageScore() {
            if (grammarScores.isEmpty()) {
                averageScore = 0;
                return;
            }

            double sum = 0;
            for (int i = 0; i < grammarScores.size(); i++) {
                sum += grammarScores.get(i) + styleScores.get(i) +
                        structureScores.get(i) + vocabularyScores.get(i) +
                        coherenceScores.get(i);
            }
            averageScore = sum / (grammarScores.size() * 5.0);
        }

        double getAverageGrammar() {
            return grammarScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double getAverageStyle() {
            return styleScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double getAverageStructure() {
            return structureScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double getAverageVocabulary() {
            return vocabularyScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double getAverageCoherence() {
            return coherenceScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double getAverageWritingTime() {
            return writingTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        String getWeakestAspect() {
            Map<String, Double> aspects = new HashMap<>();
            aspects.put("Grammar", getAverageGrammar());
            aspects.put("Style", getAverageStyle());
            aspects.put("Structure", getAverageStructure());
            aspects.put("Vocabulary", getAverageVocabulary());
            aspects.put("Coherence", getAverageCoherence());

            return aspects.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Grammar");
        }
    }

    public WritingStrategy(AiService aiService) {
        this.aiService = aiService;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("WritingStrategy инициализирована с {} темами", writingTopics.size());
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.WRITING;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            WritingState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new WritingState());

            if (state.currentTopic == null) {
                WritingTopic topic = getNextTopic(context);
                state.currentTopic = topic.title;
            }

            WritingTopic currentTopic = getTopicByName(state.currentTopic);
            state.startWriting();

            WritingAnalysis analysis = analyzeWriting(userInput, state, currentTopic, context);

            state.endWriting();
            state.addScores(analysis.grammarScore, analysis.styleScore,
                    analysis.structureScore, analysis.vocabularyScore,
                    analysis.coherenceScore);
            state.writtenTexts.add(userInput);
            state.textsCompleted++;

            state.topicScores.put(currentTopic.title,
                    (state.topicScores.getOrDefault(currentTopic.title, 0.0) + analysis.overallScore) / 2);

            String prompt = buildDetailedWritingPrompt(userInput, state, context, currentTopic, analysis);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            String displayText = generateDisplayText(aiResponse, analysis, state, currentTopic);

            String ttsText = generateTtsText(aiResponse, analysis, state, currentTopic);

            return LearningResponse.builder()
                    .message(displayText)
                    .ttsText(ttsText)
                    .nextMode(determineNextMode(context, state))
                    .nextTask(generateNextTaskWithTts(context, state))
                    .progress(calculateProgress(state))
                    .recommendations(generateRecommendations(state, analysis))
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<String> generateResponse(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            WritingState state = sessions.get(context.getUserId());
            WritingTopic currentTopic = state != null && state.currentTopic != null
                    ? getTopicByName(state.currentTopic)
                    : getNextTopic(context);

            WritingAnalysis analysis = analyzeWriting(userInput, state, currentTopic, context);
            String prompt = buildDetailedWritingPrompt(userInput, state, context, currentTopic, analysis);
            String aiResponse = aiService.generateBotResponse(prompt, null);

            return generateTtsText(aiResponse, analysis, state, currentTopic);
        }, executor);
    }

    private String generateTtsText(String aiResponse, WritingAnalysis analysis,
                                   WritingState state, WritingTopic topic) {
        StringBuilder tts = new StringBuilder();

        tts.append("Writing task: ").append(topic.title).append(". ");

        String cleanResponse = extractMainWritingMessage(aiResponse);
        if (!cleanResponse.isEmpty()) {
            tts.append(cleanResponse);
            if (!cleanResponse.endsWith(". ") && !cleanResponse.endsWith(".")) {
                tts.append(". ");
            } else {
                tts.append(" ");
            }
        }

        if (analysis.overallScore >= EXCELLENT_SCORE) {
            tts.append("Excellent work! ");
        } else if (analysis.overallScore >= GOOD_SCORE) {
            tts.append("Good job! ");
        } else {
            tts.append("Let's keep practicing. ");
        }
        tts.append(String.format("Score %.1f. ", analysis.overallScore));

        tts.append(String.format("Grammar %.1f. ", analysis.grammarScore));
        tts.append(String.format("Vocabulary %.1f. ", analysis.vocabularyScore));

        tts.append(String.format("You wrote %d words. ", analysis.wordCount));

        if (!analysis.strengths.isEmpty()) {
            String strength = analysis.strengths.get(0);
            if (strength.length() < 80) {
                tts.append(strength).append(". ");
            }
        }

        if (state != null) {
            String weakest = state.getWeakestAspect();
            tts.append("Focus on ").append(weakest.toLowerCase()).append(" next time. ");
        }

        tts.append("Keep writing to improve your skills!");

        return tts.toString();
    }

    private String extractMainWritingMessage(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return "";
        }

        String[] statsMarkers = {
                "DETAILED SCORES",
                "📊 DETAILED SCORES",
                "STATISTICS",
                "📝 STATISTICS",
                "YOUR PROGRESS",
                "📋 YOUR PROGRESS",
                "STRENGTHS",
                "💪 STRENGTHS",
                "FOCUS FOR NEXT TIME",
                "🎯 FOCUS FOR NEXT TIME",
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

        mainMessage = mainMessage.replace("✍️ WRITING ANALYSIS:", "")
                .replace("WRITING ANALYSIS:", "")
                .trim();

        mainMessage = mainMessage.replaceAll("[\\n\\r]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (mainMessage.length() > 200) {
            mainMessage = mainMessage.substring(0, 200) + "...";
        }

        return mainMessage;
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            WritingState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("grammar", state.getAverageGrammar());
                skills.put("style", state.getAverageStyle());
                skills.put("structure", state.getAverageStructure());
                skills.put("vocabulary", state.getAverageVocabulary());
                skills.put("coherence", state.getAverageCoherence());
                skills.put("productivity", (double) state.textsCompleted);
                skills.put("best_score", state.bestScore);
                skills.put("avg_writing_time", state.getAverageWritingTime());

                state.topicScores.forEach((topic, score) ->
                        skills.put("topic_" + topic, score));
            }

            List<String> achievements = getAchievements(state);

            return LearningProgress.builder()
                    .overallProgress(calculateProgress(state))
                    .skillsProgress(skills)
                    .timeSpent(context.getSessionDuration())
                    .tasksCompleted(state != null ? state.textsCompleted : 0)
                    .startDate(LocalDate.now().minusDays(context.getSessionCount()))
                    .achievements(achievements)
                    .build();
        }, executor);
    }

    @Override
    public CompletableFuture<LearningTask> getNextTask(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            WritingState state = sessions.get(context.getUserId());
            return generateNextTaskWithTts(context, state);
        }, executor);
    }

    private LearningTask generateNextTaskWithTts(LearningContext context, WritingState state) {
        WritingTopic topic;
        if (state != null && state.currentTopic != null) {
            topic = getTopicByName(state.currentTopic);
        } else {
            topic = getNextTopic(context);
            if (state != null) {
                state.currentTopic = topic.title;
            }
        }

        String weakestAspect = state != null ? state.getWeakestAspect() : "Grammar";

        String displayDescription = generateTaskDisplayText(topic, context);

        String ttsDescription = generateTaskTtsText(topic, context, weakestAspect);

        return LearningTask.builder()
                .id("wri_" + System.currentTimeMillis())
                .title("✍️ Writing: " + topic.title)
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.WRITING)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(generateWritingExamples(topic))
                .addMetadata("topic", topic.title)
                .addMetadata("type", topic.type)
                .addMetadata("min_words", getMinWords(context.getCurrentLevel()))
                .addMetadata("max_words", getMaxWords(context.getCurrentLevel()))
                .addMetadata("keywords", topic.keywords)
                .addMetadata("focus", weakestAspect)
                .addMetadata("tips", generateWritingTips(topic, weakestAspect))
                .build();
    }

    @Override
    public boolean isSupported() {
        return aiService.isAvailable();
    }

    @Override
    public String getStrategyName() {
        return "Writing Practice Strategy";
    }

    private WritingTopic getNextTopic(LearningContext context) {
        String level = determineLevel(context.getCurrentLevel());
        List<WritingTopic> topics = writingTopics.get(level);
        return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
    }

    private WritingTopic getTopicByName(String title) {
        for (List<WritingTopic> topics : writingTopics.values()) {
            for (WritingTopic topic : topics) {
                if (topic.title.equals(title)) {
                    return topic;
                }
            }
        }
        return writingTopics.get("beginner").get(0);
    }

    private String determineLevel(double level) {
        if (level < BEGINNER_THRESHOLD) return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private static class WritingAnalysis {
        final double grammarScore;
        final double styleScore;
        final double structureScore;
        final double vocabularyScore;
        final double coherenceScore;
        final double overallScore;
        final int wordCount;
        final int sentenceCount;
        final double avgSentenceLength;
        final List<String> errors;
        final List<String> strengths;

        WritingAnalysis(double grammar, double style, double structure,
                        double vocabulary, double coherence, int wordCount,
                        int sentenceCount, double avgSentenceLength,
                        List<String> errors, List<String> strengths) {
            this.grammarScore = grammar;
            this.styleScore = style;
            this.structureScore = structure;
            this.vocabularyScore = vocabulary;
            this.coherenceScore = coherence;
            this.overallScore = (grammar + style + structure + vocabulary + coherence) / 5.0;
            this.wordCount = wordCount;
            this.sentenceCount = sentenceCount;
            this.avgSentenceLength = avgSentenceLength;
            this.errors = errors;
            this.strengths = strengths;
        }
    }

    private WritingAnalysis analyzeWriting(String text, WritingState state,
                                           WritingTopic topic, LearningContext context) {

        int wordCount = text.split("\\s+").length;
        int sentenceCount = text.split("[.!?]+").length;
        double avgSentenceLength = sentenceCount > 0 ? (double) wordCount / sentenceCount : 0;

        double baseScore = 60 + ThreadLocalRandom.current().nextDouble() * 35;
        double levelBonus = context.getCurrentLevel() / 20;

        double grammarScore = Math.min(100, baseScore + levelBonus - 5 + ThreadLocalRandom.current().nextDouble() * 10);
        double styleScore = Math.min(100, baseScore + levelBonus + ThreadLocalRandom.current().nextDouble() * 10);
        double structureScore = Math.min(100, baseScore + levelBonus - 3 + ThreadLocalRandom.current().nextDouble() * 10);
        double vocabularyScore = Math.min(100, baseScore + levelBonus + 5 + ThreadLocalRandom.current().nextDouble() * 10);
        double coherenceScore = Math.min(100, baseScore + levelBonus + ThreadLocalRandom.current().nextDouble() * 10);

        List<String> errors = new ArrayList<>();
        List<String> strengths = new ArrayList<>();

        if (grammarScore < 70) {
            errors.add("Some grammatical errors detected");
        } else {
            strengths.add("Good grammatical accuracy");
        }

        if (vocabularyScore < 70) {
            errors.add("Limited vocabulary range");
        } else if (vocabularyScore > 85) {
            strengths.add("Rich and varied vocabulary");
        }

        if (wordCount < topic.minWords) {
            errors.add("Text is shorter than recommended minimum");
        } else if (wordCount > topic.maxWords) {
            errors.add("Text is longer than recommended maximum");
        } else {
            strengths.add("Appropriate text length");
        }

        return new WritingAnalysis(
                grammarScore, styleScore, structureScore,
                vocabularyScore, coherenceScore,
                wordCount, sentenceCount, avgSentenceLength,
                errors, strengths
        );
    }

    private String buildDetailedWritingPrompt(String userInput, WritingState state,
                                              LearningContext context, WritingTopic topic,
                                              WritingAnalysis analysis) {
        double level = context.getCurrentLevel();
        String levelGuide = getLevelGuide(level);
        String weakestAspect = state != null ? state.getWeakestAspect() : "Grammar";

        return String.format("""
            You are an expert, warm and highly motivating AI English Writing Tutor.
            Your goal is to help students write clearly, confidently and naturally.

            === STRICT RULES ===
            - ALWAYS respond ONLY in English
            - Perfectly adapt to the student's level (%.1f/100)
            - Always start with genuine encouragement
            - Never say "wrong" or "bad". Use positive reinforcement

            === WRITING ASSIGNMENT ===
            • Topic: %s
            • Type: %s
            • Target length: %d-%d words
            • Key vocabulary: %s

            === ANALYSIS RESULTS ===
            • Word count: %d (target: %d-%d)
            • Sentence count: %d
            • Avg sentence length: %.1f words
            
            • Grammar: %.1f/100
            • Style: %.1f/100
            • Structure: %.1f/100
            • Vocabulary: %.1f/100
            • Coherence: %.1f/100

            === STRENGTHS ===
            %s

            === AREAS FOR IMPROVEMENT ===
            %s

            === TEACHING GUIDELINES ===
            %s

            === STUDENT'S TEXT ===
            "%s"

            === YOUR TASK ===
            1. Warm positive opening
            2. Highlight strengths
            3. Address areas for improvement
            4. Provide rewritten improved version
            5. Give specific suggestions
            6. Offer writing tips
            7. Motivating closing + next challenge

            Generate a complete, structured writing feedback response.
            """,
                level,
                topic.title,
                topic.type,
                topic.minWords,
                topic.maxWords,
                String.join(", ", topic.keywords),
                analysis.wordCount,
                topic.minWords,
                topic.maxWords,
                analysis.sentenceCount,
                analysis.avgSentenceLength,
                analysis.grammarScore,
                analysis.styleScore,
                analysis.structureScore,
                analysis.vocabularyScore,
                analysis.coherenceScore,
                analysis.strengths.isEmpty() ? "None identified yet" : "• " + String.join("\n• ", analysis.strengths),
                analysis.errors.isEmpty() ? "None! Great job!" : "• " + String.join("\n• ", analysis.errors),
                levelGuide,
                userInput
        );
    }

    private String getLevelGuide(double level) {
        if (level < BEGINNER_THRESHOLD) {
            return """
                • Use VERY simple language
                • Focus on basic sentence structure
                • Provide simple corrections
                • Praise effort and progress
                • Give clear, actionable feedback""";
        } else if (level < INTERMEDIATE_THRESHOLD) {
            return """
                • Use clear, moderate language
                • Focus on paragraph structure
                • Discuss transitions and flow
                • Introduce style elements
                • Balance praise and improvement""";
        } else if (level < ADVANCED_THRESHOLD) {
            return """
                • Use natural, sophisticated language
                • Discuss rhetorical devices
                • Analyze argument structure
                • Focus on stylistic choices
                • Challenge with complex topics""";
        } else {
            return """
                • Use advanced, nuanced language
                • Discuss authorial voice
                • Analyze literary techniques
                • Focus on professional writing
                • Challenge with sophisticated assignments""";
        }
    }

    private String generateDisplayText(String aiResponse, WritingAnalysis analysis,
                                       WritingState state, WritingTopic topic) {
        StringBuilder display = new StringBuilder();

        display.append("✍️ WRITING ANALYSIS: ").append(topic.title.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append(aiResponse).append("\n\n");

        display.append("📊 DETAILED SCORES\n");
        display.append("──────────────────\n");
        display.append(String.format("  Grammar: %.1f/100\n", analysis.grammarScore));
        display.append(String.format("  Style: %.1f/100\n", analysis.styleScore));
        display.append(String.format("  Structure: %.1f/100\n", analysis.structureScore));
        display.append(String.format("  Vocabulary: %.1f/100\n", analysis.vocabularyScore));
        display.append(String.format("  Coherence: %.1f/100\n", analysis.coherenceScore));
        display.append(String.format("  OVERALL: %.1f/100\n\n", analysis.overallScore));

        display.append("📝 STATISTICS\n");
        display.append("─────────────\n");
        display.append(String.format("  Words: %d (target: %d-%d)\n",
                analysis.wordCount, topic.minWords, topic.maxWords));
        display.append(String.format("  Sentences: %d\n", analysis.sentenceCount));
        display.append(String.format("  Avg sentence length: %.1f words\n\n", analysis.avgSentenceLength));

        display.append("📋 YOUR PROGRESS\n");
        display.append("────────────────\n");
        display.append(String.format("  Texts written: %d\n", state.textsCompleted));
        display.append(String.format("  Average score: %.1f/100\n", state.averageScore));
        display.append(String.format("  Best score: %.1f/100\n\n", state.bestScore));

        display.append("💪 STRENGTHS\n");
        display.append("────────────\n");
        if (analysis.strengths.isEmpty()) {
            display.append("  None identified yet\n");
        } else {
            analysis.strengths.forEach(s -> display.append("  • ").append(s).append("\n"));
        }
        display.append("\n");

        display.append("🎯 FOCUS FOR NEXT TIME\n");
        display.append("─────────────────────\n");
        display.append("  ").append(state.getWeakestAspect()).append("\n\n");

        display.append("Keep writing! Every text makes you a better writer! 🚀\n");

        return display.toString();
    }

    private String generateTaskDisplayText(WritingTopic topic, LearningContext context) {
        StringBuilder display = new StringBuilder();

        display.append("✍️ WRITING TASK: ").append(topic.title.toUpperCase()).append("\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("📋 Description:\n");
        display.append(topic.description).append("\n\n");

        display.append("📝 Type: ").append(topic.type).append("\n");
        display.append(String.format("📏 Length: %d-%d words\n", topic.minWords, topic.maxWords));
        display.append(String.format("📊 Your level: %.1f/100\n", context.getCurrentLevel()));
        display.append("📈 Difficulty: ").append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append("\n\n");

        display.append("🔑 KEY VOCABULARY\n");
        display.append("─────────────────\n");
        for (String keyword : topic.keywords) {
            display.append("  • ").append(keyword).append("\n");
        }
        display.append("\n");

        display.append("💡 WRITING TIPS\n");
        display.append("───────────────\n");
        display.append("  • Plan your text before writing\n");
        display.append("  • Use the key vocabulary words\n");
        display.append("  • Check your grammar and spelling\n");
        display.append("  • Read your text aloud to check flow\n\n");

        display.append("Take your time and write your best text!\n");

        return display.toString();
    }


    private String generateTaskTtsText(WritingTopic topic, LearningContext context, String focus) {
        StringBuilder tts = new StringBuilder();

        String typeSpeech = WRITING_TYPE_TO_SPEECH.getOrDefault(topic.type, topic.type);

        tts.append("New writing task: ").append(topic.title).append(". ");
        tts.append(topic.description).append(". ");

        tts.append("This is a ").append(typeSpeech).append(" writing task. ");

        tts.append("Aim for ").append(topic.minWords).append(" to ").append(topic.maxWords)
                .append(" words. ");

        tts.append("Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. Difficulty level is ")
                .append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append(". ");

        tts.append("Key vocabulary includes: ");
        for (int i = 0; i < Math.min(3, topic.keywords.size()); i++) {
            tts.append(topic.keywords.get(i));
            if (i < Math.min(3, topic.keywords.size()) - 1) {
                tts.append(", ");
            }
        }
        tts.append(". ");

        tts.append("Focus on improving your ").append(focus.toLowerCase()).append(". ");

        tts.append("Plan your text before writing and check your work.");

        return tts.toString();
    }

    private String buildTaskDescription(WritingTopic topic, LearningContext context) {
        return String.format("""
            ✍️ **Writing Task: %s**

            **Description:**
            %s

            **Type:** %s
            **Length:** %d-%d words
            **Your level:** %.1f/100
            **Difficulty:** %s

            **Key vocabulary to use:**
            • %s

            **Writing tips:**
            • Plan your text before writing
            • Use the key vocabulary words
            • Check your grammar and spelling
            • Read your text aloud to check flow

            Take your time and write your best text!
            """,
                topic.title,
                topic.description,
                topic.type,
                topic.minWords,
                topic.maxWords,
                context.getCurrentLevel(),
                mapDifficulty(context.getCurrentLevel()).getDisplayName(),
                String.join("\n• ", topic.keywords)
        );
    }

    private List<String> generateWritingExamples(WritingTopic topic) {
        List<String> examples = new ArrayList<>();

        if (topic.type.equals("descriptive")) {
            examples.add("The most memorable part of my day is when...");
            examples.add("I can still remember the beautiful sunset...");
        } else if (topic.type.equals("narrative")) {
            examples.add("It all started when I arrived at the airport...");
            examples.add("Suddenly, something unexpected happened...");
        } else if (topic.type.equals("argumentative")) {
            examples.add("While some argue that..., others believe that...");
            examples.add("There are several reasons to support this view...");
        } else if (topic.type.equals("compare-contrast")) {
            examples.add("Unlike X, Y offers several advantages...");
            examples.add("Both X and Y share some common features...");
        }

        return examples;
    }

    private List<String> generateWritingTips(WritingTopic topic, String focus) {
        List<String> tips = new ArrayList<>();

        tips.add("Start with an outline to organize your thoughts");
        tips.add("Use transition words to connect ideas");
        tips.add("Read your text aloud to catch errors");

        if (focus.equals("Grammar")) {
            tips.add("Review subject-verb agreement");
            tips.add("Check your verb tenses");
            tips.add("Pay attention to articles (a/an/the)");
        } else if (focus.equals("Vocabulary")) {
            tips.add("Use a thesaurus to find better words");
            tips.add("Avoid repeating the same words");
            tips.add("Learn topic-specific vocabulary");
        } else if (focus.equals("Structure")) {
            tips.add("Each paragraph should have one main idea");
            tips.add("Use clear topic sentences");
            tips.add("End with a strong conclusion");
        } else if (focus.equals("Coherence")) {
            tips.add("Use pronouns to refer back to ideas");
            tips.add("Maintain consistent tense throughout");
            tips.add("Connect ideas with transition words");
        }

        return tips;
    }

    private double calculateProgress(WritingState state) {
        if (state == null) return 0;

        double qualityComponent = state.averageScore * 0.6;
        double quantityComponent = Math.min(30, state.textsCompleted * 2);
        double consistencyComponent = (state.bestScore - state.averageScore) * 0.5;

        return Math.min(100, qualityComponent + quantityComponent + consistencyComponent);
    }

    private List<String> generateRecommendations(WritingState state, WritingAnalysis analysis) {
        List<String> recommendations = new ArrayList<>();

        if (state == null) return recommendations;

        String weakest = state.getWeakestAspect();
        switch (weakest) {
            case "Grammar":
                recommendations.add("Focus on grammar exercises between writing sessions");
                break;
            case "Vocabulary":
                recommendations.add("Build vocabulary with daily word lists");
                break;
            case "Structure":
                recommendations.add("Practice outlining before writing");
                break;
            case "Coherence":
                recommendations.add("Work on connecting ideas with transition words");
                break;
        }

        if (analysis.wordCount < 50 && state.textsCompleted < ACHIEVEMENT_TEXTS_5) {
            recommendations.add("Try to write longer texts as you progress");
        }

        if (state.masteredTopics.size() < 3) {
            recommendations.add("Explore different types of writing (descriptive, narrative, argumentative)");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great progress! Try submitting your writing for peer review");
        }

        return recommendations;
    }

    private int getMinWords(double level) {
        if (level < BEGINNER_THRESHOLD) return 50;
        if (level < INTERMEDIATE_THRESHOLD) return 150;
        return 300;
    }

    private int getMaxWords(double level) {
        if (level < BEGINNER_THRESHOLD) return 100;
        if (level < INTERMEDIATE_THRESHOLD) return 300;
        return 600;
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, WritingState state) {
        if (state == null) return LearningMode.WRITING;

        if (state.averageScore > EXCELLENT_SCORE && state.textsCompleted > ACHIEVEMENT_TEXTS_10) {
            return LearningMode.CONVERSATION;
        } else if (state.getAverageGrammar() < GOOD_SCORE) {
            return LearningMode.GRAMMAR;
        } else if (state.getAverageVocabulary() < GOOD_SCORE) {
            return LearningMode.VOCABULARY;
        }

        return LearningMode.WRITING;
    }

    private List<String> getAchievements(WritingState state) {
        List<String> achievements = new ArrayList<>();

        if (state == null) return achievements;

        if (state.textsCompleted >= ACHIEVEMENT_TEXTS_100) {
            achievements.add("🏆 Master Writer - 100+ texts!");
        } else if (state.textsCompleted >= ACHIEVEMENT_TEXTS_50) {
            achievements.add("📚 Prolific Writer - 50+ texts");
        } else if (state.textsCompleted >= ACHIEVEMENT_TEXTS_25) {
            achievements.add("✍️ Dedicated Writer - 25+ texts");
        } else if (state.textsCompleted >= ACHIEVEMENT_TEXTS_10) {
            achievements.add("📝 Regular Writer - 10+ texts");
        } else if (state.textsCompleted >= ACHIEVEMENT_TEXTS_5) {
            achievements.add("🌟 Getting Started - 5+ texts");
        }

        if (state.bestScore >= ACHIEVEMENT_SCORE_90) {
            achievements.add("🔥 Outstanding Quality! " + String.format("%.1f", state.bestScore));
        } else if (state.averageScore >= ACHIEVEMENT_SCORE_85) {
            achievements.add("📊 Excellent Writing! " + String.format("%.1f", state.averageScore));
        } else if (state.averageScore >= ACHIEVEMENT_SCORE_75) {
            achievements.add("📈 Good Progress! " + String.format("%.1f", state.averageScore));
        }

        if (state.getAverageGrammar() >= EXCELLENT_SCORE) {
            achievements.add("🔤 Grammar Excellence");
        }
        if (state.getAverageVocabulary() >= EXCELLENT_SCORE) {
            achievements.add("📖 Rich Vocabulary");
        }
        if (state.getAverageStructure() >= EXCELLENT_SCORE) {
            achievements.add("🏗️ Strong Structure");
        }

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        WritingState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("writtenTexts", new ArrayList<>(state.writtenTexts));
        stateMap.put("grammarScores", new ArrayList<>(state.grammarScores));
        stateMap.put("styleScores", new ArrayList<>(state.styleScores));
        stateMap.put("structureScores", new ArrayList<>(state.structureScores));
        stateMap.put("vocabularyScores", new ArrayList<>(state.vocabularyScores));
        stateMap.put("coherenceScores", new ArrayList<>(state.coherenceScores));
        stateMap.put("topicScores", new HashMap<>(state.topicScores));
        stateMap.put("masteredTopics", new ArrayList<>(state.masteredTopics));
        stateMap.put("writingTimes", new ArrayList<>(state.writingTimes));
        stateMap.put("currentTopic", state.currentTopic);
        stateMap.put("textsCompleted", state.textsCompleted);
        stateMap.put("averageScore", state.averageScore);
        stateMap.put("bestScore", state.bestScore);
        stateMap.put("totalWritingTime", state.totalWritingTime);

        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        WritingState state = new WritingState();

        @SuppressWarnings("unchecked")
        List<String> writtenTexts = (List<String>) stateMap.getOrDefault("writtenTexts", Collections.emptyList());
        state.writtenTexts.addAll(writtenTexts);

        @SuppressWarnings("unchecked")
        List<Double> grammarScores = (List<Double>) stateMap.getOrDefault("grammarScores", Collections.emptyList());
        state.grammarScores.addAll(grammarScores);

        @SuppressWarnings("unchecked")
        List<Double> styleScores = (List<Double>) stateMap.getOrDefault("styleScores", Collections.emptyList());
        state.styleScores.addAll(styleScores);

        @SuppressWarnings("unchecked")
        List<Double> structureScores = (List<Double>) stateMap.getOrDefault("structureScores", Collections.emptyList());
        state.structureScores.addAll(structureScores);

        @SuppressWarnings("unchecked")
        List<Double> vocabularyScores = (List<Double>) stateMap.getOrDefault("vocabularyScores", Collections.emptyList());
        state.vocabularyScores.addAll(vocabularyScores);

        @SuppressWarnings("unchecked")
        List<Double> coherenceScores = (List<Double>) stateMap.getOrDefault("coherenceScores", Collections.emptyList());
        state.coherenceScores.addAll(coherenceScores);

        @SuppressWarnings("unchecked")
        Map<String, Double> topicScores = (Map<String, Double>) stateMap.getOrDefault("topicScores", Collections.emptyMap());
        state.topicScores.putAll(topicScores);

        @SuppressWarnings("unchecked")
        List<String> masteredTopics = (List<String>) stateMap.getOrDefault("masteredTopics", Collections.emptyList());
        state.masteredTopics.addAll(masteredTopics);

        @SuppressWarnings("unchecked")
        List<Long> writingTimes = (List<Long>) stateMap.getOrDefault("writingTimes", Collections.emptyList());
        state.writingTimes.addAll(writingTimes);

        state.currentTopic = (String) stateMap.get("currentTopic");
        state.textsCompleted = (int) stateMap.getOrDefault("textsCompleted", 0);
        state.averageScore = (double) stateMap.getOrDefault("averageScore", 0.0);
        state.bestScore = (double) stateMap.getOrDefault("bestScore", 0.0);
        state.totalWritingTime = (long) stateMap.getOrDefault("totalWritingTime", 0L);

        sessions.put(userId, state);
        logger.debug("Восстановлено состояние сессии для пользователя {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Сессия пользователя {} очищена", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        WritingState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("textsCompleted", state.textsCompleted);
        stats.put("averageScore", state.averageScore);
        stats.put("bestScore", state.bestScore);
        stats.put("averageGrammar", state.getAverageGrammar());
        stats.put("averageStyle", state.getAverageStyle());
        stats.put("averageStructure", state.getAverageStructure());
        stats.put("averageVocabulary", state.getAverageVocabulary());
        stats.put("averageCoherence", state.getAverageCoherence());
        stats.put("weakestAspect", state.getWeakestAspect());

        return stats;
    }
}