package com.mygitgor.ai.strategy.type;

import com.mygitgor.ai.strategy.*;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.model.*;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.utils.HtmlTemplateLoader;
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

public class PronunciationStrategy implements LearningModeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(PronunciationStrategy.class);

    private final PronunciationTrainer pronunciationTrainer;
    private final AudioAnalyzer audioAnalyzer;
    private final ExecutorService executor;

    private final Map<String, PronunciationState> sessions = new ConcurrentHashMap<>();

    private static final double BEGINNER_THRESHOLD = 30.0;
    private static final double INTERMEDIATE_THRESHOLD = 60.0;
    private static final double ADVANCED_THRESHOLD = 85.0;

    private static final double GOOD_PRONUNCIATION = 70.0;
    private static final double EXCELLENT_PRONUNCIATION = 85.0;

    private static final int ACHIEVEMENT_PHONEMES_5 = 5;
    private static final int ACHIEVEMENT_PHONEMES_10 = 10;
    private static final int ACHIEVEMENT_PHONEMES_15 = 15;
    private static final int ACHIEVEMENT_EXERCISES_20 = 20;
    private static final int ACHIEVEMENT_EXERCISES_50 = 50;
    private static final int ACHIEVEMENT_EXERCISES_100 = 100;
    private static final double ACHIEVEMENT_SCORE_70 = 70.0;
    private static final double ACHIEVEMENT_SCORE_85 = 85.0;
    private static final double ACHIEVEMENT_SCORE_95 = 95.0;

    private static final Map<String, PhonemeInfo> phonemeDatabase = new HashMap<>();
    private static final Map<String, String> PHONEME_TO_SPEECH = new HashMap<>();

    static {
        phonemeDatabase.put("iː", new PhonemeInfo(
                "Long 'ee' sound",
                "A long, tense vowel sound",
                "The tongue is high and front, lips are spread",
                Arrays.asList("see", "sheep", "feel", "green", "machine")));

        phonemeDatabase.put("ɪ", new PhonemeInfo(
                "Short 'i' sound",
                "A short, relaxed vowel sound",
                "The tongue is near the front but lower than for /iː/",
                Arrays.asList("sit", "ship", "fill", "gym", "myth")));

        phonemeDatabase.put("e", new PhonemeInfo(
                "Short 'e' sound",
                "A short, mid-front vowel",
                "The tongue is mid-high and front, lips are slightly spread",
                Arrays.asList("bed", "head", "said", "friend", "any")));

        phonemeDatabase.put("æ", new PhonemeInfo(
                "Short 'a' sound",
                "A short, open front vowel",
                "The tongue is low and front, mouth is more open",
                Arrays.asList("cat", "bat", "hat", "man", "happy")));

        phonemeDatabase.put("ɑː", new PhonemeInfo(
                "Long 'ar' sound",
                "A long, open back vowel",
                "The tongue is low and back, mouth is open",
                Arrays.asList("car", "park", "father", "calm", "heart")));

        phonemeDatabase.put("ɒ", new PhonemeInfo(
                "Short 'o' sound",
                "A short, open back vowel",
                "The tongue is low and back, lips are slightly rounded",
                Arrays.asList("hot", "rock", "watch", "what", "want")));

        phonemeDatabase.put("ɔː", new PhonemeInfo(
                "Long 'or' sound",
                "A long, mid-back vowel",
                "The tongue is mid-back, lips are rounded",
                Arrays.asList("door", "more", "law", "four", "caught")));

        phonemeDatabase.put("ʊ", new PhonemeInfo(
                "Short 'oo' sound",
                "A short, near-close back vowel",
                "The tongue is near the back, lips are rounded",
                Arrays.asList("book", "put", "could", "woman", "foot")));

        phonemeDatabase.put("uː", new PhonemeInfo(
                "Long 'oo' sound",
                "A long, close back vowel",
                "The tongue is high and back, lips are rounded",
                Arrays.asList("blue", "food", "too", "group", "through")));

        phonemeDatabase.put("ʌ", new PhonemeInfo(
                "Short 'u' sound",
                "A short, open-mid vowel",
                "The tongue is central and relaxed",
                Arrays.asList("cup", "luck", "up", "money", "young")));

        phonemeDatabase.put("ɜː", new PhonemeInfo(
                "Long 'er' sound",
                "A long, mid-central vowel",
                "The tongue is mid and central, lips are neutral",
                Arrays.asList("bird", "learn", "turn", "work", "world")));

        phonemeDatabase.put("ə", new PhonemeInfo(
                "Schwa sound",
                "The most common vowel sound",
                "The tongue is mid and central, completely relaxed",
                Arrays.asList("about", "banana", "supply", "the", "family")));

        phonemeDatabase.put("eɪ", new PhonemeInfo(
                "'ay' sound",
                "A glide from /e/ to /ɪ/",
                "Start with /e/ and glide to /ɪ/",
                Arrays.asList("day", "make", "rain", "eight", "they")));

        phonemeDatabase.put("aɪ", new PhonemeInfo(
                "'eye' sound",
                "A glide from /a/ to /ɪ/",
                "Start with /a/ and glide to /ɪ/",
                Arrays.asList("my", "time", "eye", "five", "why")));

        phonemeDatabase.put("ɔɪ", new PhonemeInfo(
                "'oy' sound",
                "A glide from /ɔ/ to /ɪ/",
                "Start with /ɔ/ and glide to /ɪ/",
                Arrays.asList("boy", "join", "toy", "voice", "enjoy")));

        phonemeDatabase.put("aʊ", new PhonemeInfo(
                "'ow' sound",
                "A glide from /a/ to /ʊ/",
                "Start with /a/ and glide to /ʊ/",
                Arrays.asList("now", "house", "out", "brown", "about")));

        phonemeDatabase.put("oʊ", new PhonemeInfo(
                "'oh' sound",
                "A glide from /o/ to /ʊ/",
                "Start with /o/ and glide to /ʊ/",
                Arrays.asList("go", "home", "show", "know", "though")));

        phonemeDatabase.put("θ", new PhonemeInfo(
                "Voiceless 'th' sound",
                "Air passes between the tongue and teeth",
                "Place tongue tip between teeth, blow air without voice",
                Arrays.asList("think", "both", "thick", "math", "through")));

        phonemeDatabase.put("ð", new PhonemeInfo(
                "Voiced 'th' sound",
                "Same as /θ/ but with voice",
                "Place tongue between teeth, vibrate vocal cords",
                Arrays.asList("this", "mother", "that", "there", "weather")));

        phonemeDatabase.put("r", new PhonemeInfo(
                "English 'r' sound",
                "Unlike the trilled 'r' in many languages",
                "Tongue is curled back, not touching the roof",
                Arrays.asList("red", "very", "right", "rain", "sorry")));

        phonemeDatabase.put("ʃ", new PhonemeInfo(
                "'sh' sound",
                "A voiceless palato-alveolar fricative",
                "Tongue is near the roof, air passes over",
                Arrays.asList("she", "wish", "sure", "nation", "ocean")));

        phonemeDatabase.put("ʒ", new PhonemeInfo(
                "'zh' sound",
                "The voiced version of /ʃ/",
                "Same position as /ʃ/ but with voice",
                Arrays.asList("pleasure", "vision", "measure", "usual", "garage")));

        phonemeDatabase.put("tʃ", new PhonemeInfo(
                "'ch' sound",
                "A voiceless affricate",
                "Start with /t/ and release into /ʃ/",
                Arrays.asList("chair", "match", "church", "teach", "nature")));

        phonemeDatabase.put("dʒ", new PhonemeInfo(
                "'j' sound",
                "A voiced affricate",
                "Start with /d/ and release into /ʒ/",
                Arrays.asList("jump", "age", "judge", "large", "suggest")));

        phonemeDatabase.put("w", new PhonemeInfo(
                "'w' sound",
                "A labio-velar approximant",
                "Lips are rounded, back of tongue is raised",
                Arrays.asList("we", "want", "what", "swim", "language")));

        phonemeDatabase.put("j", new PhonemeInfo(
                "'y' sound",
                "A palatal approximant",
                "Tongue is high and front, like /iː/ but shorter",
                Arrays.asList("yes", "year", "you", "beauty", "computer")));

        phonemeDatabase.put("ŋ", new PhonemeInfo(
                "'ng' sound",
                "A velar nasal",
                "Air passes through the nose, back of tongue raised",
                Arrays.asList("sing", "think", "long", "morning", "language")));

        phonemeDatabase.put("l", new PhonemeInfo(
                "'l' sound",
                "A lateral approximant",
                "Tongue tip touches the alveolar ridge",
                Arrays.asList("leg", "little", "like", "hello", "milk")));

        phonemeDatabase.put("v", new PhonemeInfo(
                "'v' sound",
                "A labiodental fricative",
                "Lower lip touches upper teeth, air passes through",
                Arrays.asList("voice", "have", "very", "love", "video")));

        PHONEME_TO_SPEECH.put("iː", "long ee");
        PHONEME_TO_SPEECH.put("ɪ", "short i");
        PHONEME_TO_SPEECH.put("e", "short e");
        PHONEME_TO_SPEECH.put("æ", "short a");
        PHONEME_TO_SPEECH.put("ɑː", "long ar");
        PHONEME_TO_SPEECH.put("ɒ", "short o");
        PHONEME_TO_SPEECH.put("ɔː", "long or");
        PHONEME_TO_SPEECH.put("ʊ", "short oo");
        PHONEME_TO_SPEECH.put("uː", "long oo");
        PHONEME_TO_SPEECH.put("ʌ", "short u");
        PHONEME_TO_SPEECH.put("ɜː", "long er");
        PHONEME_TO_SPEECH.put("ə", "schwa");
        PHONEME_TO_SPEECH.put("eɪ", "ay");
        PHONEME_TO_SPEECH.put("aɪ", "eye");
        PHONEME_TO_SPEECH.put("ɔɪ", "oy");
        PHONEME_TO_SPEECH.put("aʊ", "ow");
        PHONEME_TO_SPEECH.put("oʊ", "oh");
        PHONEME_TO_SPEECH.put("θ", "theta");
        PHONEME_TO_SPEECH.put("ð", "the");
        PHONEME_TO_SPEECH.put("ʃ", "sh");
        PHONEME_TO_SPEECH.put("ʒ", "zh");
        PHONEME_TO_SPEECH.put("tʃ", "ch");
        PHONEME_TO_SPEECH.put("dʒ", "j");
        PHONEME_TO_SPEECH.put("ŋ", "ng");
        PHONEME_TO_SPEECH.put("r", "r");
        PHONEME_TO_SPEECH.put("l", "l");
        PHONEME_TO_SPEECH.put("w", "w");
        PHONEME_TO_SPEECH.put("j", "y");
        PHONEME_TO_SPEECH.put("v", "v");
    }

    private static class PhonemeInfo {
        final String name;
        final String description;
        final String articulation;
        final List<String> exampleWords;

        PhonemeInfo(String name, String description, String articulation, List<String> exampleWords) {
            this.name = name;
            this.description = description;
            this.articulation = articulation;
            this.exampleWords = exampleWords;
        }
    }

    private static class PronunciationState {
        final List<String> practicedPhonemes = new ArrayList<>();
        final Map<String, Double> phonemeScores = new HashMap<>();
        final Map<String, Integer> phonemeAttempts = new HashMap<>();
        final List<Double> scoreHistory = new ArrayList<>();
        String currentPhoneme;
        int exercisesCompleted;
        int correctAttempts;
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
            exercisesCompleted++;
            if (correct) {
                correctAttempts++;
            }
        }

        double getSuccessRate() {
            return exercisesCompleted > 0 ? (double) correctAttempts / exercisesCompleted * 100 : 0;
        }

        void updateStats(String phoneme, double score) {
            phonemeScores.put(phoneme, score);
            phonemeAttempts.merge(phoneme, 1, Integer::sum);
            scoreHistory.add(score);

            if (scoreHistory.size() > 20) {
                scoreHistory.remove(0);
            }

            if (score > bestScore) {
                bestScore = score;
            }

            averageScore = phonemeScores.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);
        }

        List<String> getWeakPhonemes() {
            return phonemeScores.entrySet().stream()
                    .filter(e -> e.getValue() < GOOD_PRONUNCIATION)
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .limit(3)
                    .collect(Collectors.toList());
        }
    }

    public PronunciationStrategy(PronunciationTrainer trainer, AudioAnalyzer analyzer) {
        this.pronunciationTrainer = trainer;
        this.audioAnalyzer = analyzer;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("PronunciationStrategy initialized with {} phonemes", phonemeDatabase.size());
    }

    @Override
    public LearningMode getMode() {
        return LearningMode.PRONUNCIATION;
    }

    @Override
    public CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            PronunciationState state = sessions.computeIfAbsent(
                    context.getUserId(), k -> new PronunciationState());

            String phoneme = selectNextPhoneme(state, context);
            state.currentPhoneme = phoneme;

            PhonemeInfo info = phonemeDatabase.getOrDefault(phoneme,
                    new PhonemeInfo("Unknown", "Practice this sound", "", Arrays.asList("practice")));

            state.startExercise();

            double score = analyzePronunciation(userInput, phoneme, context);
            boolean isCorrect = score >= GOOD_PRONUNCIATION;

            state.endExercise(isCorrect);
            state.updateStats(phoneme, score);

            if (!state.practicedPhonemes.contains(phoneme)) {
                state.practicedPhonemes.add(phoneme);
            }

            String displayText = generateDisplayText(phoneme, score, state, info, context);

            String ttsText = generateTtsText(phoneme, score, state, info);

            logger.debug("Generated response for phoneme /{}/ with score {:.1f}", phoneme, score);

            return LearningResponse.builder()
                    .message(displayText)
                    .ttsText(ttsText)
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
            PronunciationState state = sessions.get(context.getUserId());
            String phoneme = state != null ? state.currentPhoneme : getNextPhoneme(context);
            double score = analyzePronunciation(userInput, phoneme, context);
            PhonemeInfo info = phonemeDatabase.getOrDefault(phoneme,
                    new PhonemeInfo("Sound", "Practice this sound", "", getDefaultWords(phoneme)));

            // Возвращаем TTS текст
            return generateTtsText(phoneme, score, state, info);
        }, executor);
    }

    @Override
    public CompletableFuture<LearningProgress> analyzeProgress(LearningContext context) {
        return CompletableFuture.supplyAsync(() -> {
            PronunciationState state = sessions.get(context.getUserId());

            Map<String, Double> skills = new HashMap<>();
            if (state != null) {
                skills.put("averageScore", state.averageScore);
                skills.put("bestScore", state.bestScore);
                skills.put("successRate", state.getSuccessRate());
                skills.put("phonemesPracticed", (double) state.practicedPhonemes.size());
                skills.put("totalExercises", (double) state.exercisesCompleted);

                state.phonemeScores.forEach((phoneme, score) ->
                        skills.put("phoneme_" + phoneme, score));

                if (!state.scoreHistory.isEmpty()) {
                    int size = state.scoreHistory.size();
                    skills.put("lastScore", state.scoreHistory.get(size - 1));
                    if (size >= 5) {
                        double recentAvg = state.scoreHistory.subList(size - 5, size).stream()
                                .mapToDouble(Double::doubleValue).average().orElse(0);
                        skills.put("recentTrend", recentAvg - state.averageScore);
                    }
                }
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
            PronunciationState state = sessions.get(context.getUserId());
            return generateNextTask(context, state);
        }, executor);
    }

    private LearningTask generateNextTask(LearningContext context, PronunciationState state) {
        String phoneme = selectNextPhoneme(state, context);
        PhonemeInfo info = phonemeDatabase.getOrDefault(phoneme,
                new PhonemeInfo("Practice", "Work on this sound", "", getDefaultWords(phoneme)));

        var exercise = pronunciationTrainer.createExercise(phoneme,
                determineDifficultyLevel(context.getCurrentLevel()));

        List<String> examples = new ArrayList<>(info.exampleWords);
        if (exercise.getExamples() != null) {
            examples.addAll(exercise.getExamples());
        }

        // Текст для отображения
        String displayDescription = generateTaskDisplayText(phoneme, info, context);

        // Текст для TTS
        String ttsDescription = generateTaskTtsText(phoneme, info, context);

        return LearningTask.builder()
                .id("pron_" + System.currentTimeMillis())
                .title("Pronunciation: /" + phoneme + "/")
                .description(displayDescription)
                .ttsDescription(ttsDescription)
                .mode(LearningMode.PRONUNCIATION)
                .difficulty(mapDifficulty(context.getCurrentLevel()))
                .examples(examples)
                .metadata(Map.of(
                        "phoneme", phoneme,
                        "articulation", info.articulation,
                        "exercises_count", 5,
                        "difficulty_level", mapDifficulty(context.getCurrentLevel()).getLevel(),
                        "tips", String.join(", ", generatePronunciationTips(phoneme, info))
                ))
                .build();
    }

    @Override
    public boolean isSupported() {
        return pronunciationTrainer != null;
    }

    @Override
    public String getStrategyName() {
        return "Pronunciation Training Strategy";
    }

    private String selectNextPhoneme(PronunciationState state, LearningContext context) {
        if (state == null) {
            return getNextPhoneme(context);
        }

        List<String> weakPhonemes = state.getWeakPhonemes();
        if (!weakPhonemes.isEmpty()) {
            return weakPhonemes.get(0);
        }

        List<String> allPhonemes = new ArrayList<>(phonemeDatabase.keySet());
        allPhonemes.removeAll(state.practicedPhonemes);

        if (!allPhonemes.isEmpty()) {
            return allPhonemes.get(ThreadLocalRandom.current().nextInt(allPhonemes.size()));
        }

        return getNextPhoneme(context);
    }

    private String getNextPhoneme(LearningContext context) {
        List<String> phonemes = new ArrayList<>(phonemeDatabase.keySet());

        double level = context.getCurrentLevel();
        if (level < BEGINNER_THRESHOLD) {
            List<String> beginnerPhonemes = Arrays.asList("θ", "ð", "r", "æ", "ɪ", "p", "b", "t", "d", "k", "g");
            return beginnerPhonemes.get(ThreadLocalRandom.current().nextInt(beginnerPhonemes.size()));
        } else if (level < INTERMEDIATE_THRESHOLD) {
            return phonemes.get(ThreadLocalRandom.current().nextInt(phonemes.size()));
        } else {
            List<String> advancedPhonemes = Arrays.asList("ʒ", "ŋ", "ɜː", "ɔː", "aʊ", "ə");
            return advancedPhonemes.get(ThreadLocalRandom.current().nextInt(advancedPhonemes.size()));
        }
    }

    private double analyzePronunciation(String text, String phoneme, LearningContext context) {
        // Упрощенная реализация для демонстрации
        // В реальном приложении здесь будет анализ аудио
        double baseScore = 60 + ThreadLocalRandom.current().nextDouble() * 35;

        double levelBonus = context.getCurrentLevel() / 10;
        baseScore = Math.min(100, baseScore + levelBonus);

        PhonemeInfo info = phonemeDatabase.get(phoneme);
        if (info != null) {
            for (String word : info.exampleWords) {
                if (text.toLowerCase().contains(word)) {
                    baseScore = Math.min(100, baseScore + 5);
                    break;
                }
            }
        }

        return baseScore;
    }

    /**
     * Генерирует текст для отображения (без HTML)
     */
    private String generateDisplayText(String phoneme, double score,
                                       PronunciationState state,
                                       PhonemeInfo info,
                                       LearningContext context) {
        StringBuilder display = new StringBuilder();

        // Заголовок
        display.append("🔤 PRONUNCIATION PRACTICE: /").append(phoneme).append("/\n");
        display.append("═══════════════════════════════════════════\n\n");

        // Оценка
        display.append("📊 SCORE: ").append(String.format("%.1f", score)).append("/100");
        if (score >= EXCELLENT_PRONUNCIATION) {
            display.append(" ⭐ EXCELLENT!\n");
        } else if (score >= GOOD_PRONUNCIATION) {
            display.append(" 👍 GOOD JOB!\n");
        } else {
            display.append(" 🔄 KEEP PRACTICING!\n");
        }
        display.append("\n");

        // Информация о звуке
        display.append("🔊 ABOUT THIS SOUND\n");
        display.append("──────────────────\n");
        display.append("📝 Description: ").append(info.description).append("\n");
        display.append("🗣️ How to pronounce: ").append(info.articulation).append("\n\n");

        // Примеры слов
        display.append("📚 EXAMPLE WORDS\n");
        display.append("────────────────\n");
        for (String word : info.exampleWords) {
            display.append("  • ").append(word).append("\n");
        }
        display.append("\n");

        // Прогресс
        if (state != null) {
            display.append("📈 YOUR PROGRESS\n");
            display.append("────────────────\n");
            display.append(String.format("  Average score: %.1f%%\n", state.averageScore));
            display.append(String.format("  Best score: %.1f%%\n", state.bestScore));
            display.append(String.format("  Success rate: %.1f%%\n", state.getSuccessRate()));
            display.append(String.format("  Exercises completed: %d\n", state.exercisesCompleted));
            display.append(String.format("  Phonemes practiced: %d\n", state.practicedPhonemes.size()));
            display.append("\n");
        }

        // Советы
        display.append("💡 PRACTICE TIPS\n");
        display.append("────────────────\n");
        List<String> tips = generatePronunciationTips(phoneme, info);
        for (String tip : tips) {
            display.append("  • ").append(tip).append("\n");
        }
        display.append("\n");

        // Следующий фокус
        if (state != null) {
            List<String> weakPhonemes = state.getWeakPhonemes();
            if (!weakPhonemes.isEmpty() && !weakPhonemes.get(0).equals(phoneme)) {
                display.append("🎯 NEXT FOCUS\n");
                display.append("─────────────\n");
                display.append("  Try working on the /").append(weakPhonemes.get(0)).append("/ sound next.\n");
            }
        }

        return display.toString();
    }

    private String generateTtsText(String phoneme, double score,
                                   PronunciationState state,
                                   PhonemeInfo info) {
        StringBuilder tts = new StringBuilder();

        if (score >= EXCELLENT_PRONUNCIATION) {
            tts.append("Excellent! ");
        } else if (score >= GOOD_PRONUNCIATION) {
            tts.append("Good job! ");
        } else {
            tts.append("Keep practicing. ");
        }

        tts.append(String.format("Your score is %.1f out of 100. ", score));

        String phonemeSpeech = PHONEME_TO_SPEECH.getOrDefault(phoneme, "the sound " + phoneme);
        tts.append("You are practicing the ").append(phonemeSpeech).append(" sound. ");
        tts.append(info.description).append(". ");
        tts.append("To pronounce it, ").append(info.articulation).append(". ");

        tts.append("Practice with these words: ");
        List<String> words = info.exampleWords;
        for (int i = 0; i < Math.min(3, words.size()); i++) {
            tts.append(words.get(i));
            if (i < Math.min(3, words.size()) - 1) {
                tts.append(", ");
            }
        }
        tts.append(". ");

        if (state != null) {
            tts.append(String.format("Your average score is %.1f percent. ", state.averageScore));
            tts.append(String.format("You have completed %d exercises. ", state.exercisesCompleted));

            List<String> weakPhonemes = state.getWeakPhonemes();
            if (!weakPhonemes.isEmpty() && !weakPhonemes.get(0).equals(phoneme)) {
                String nextPhoneme = PHONEME_TO_SPEECH.getOrDefault(weakPhonemes.get(0), weakPhonemes.get(0));
                tts.append("Next, try working on the ").append(nextPhoneme).append(" sound. ");
            }
        }

        List<String> tips = generatePronunciationTips(phoneme, info);
        if (!tips.isEmpty()) {
            tts.append("Tip: ").append(tips.get(0)).append(". ");
        }

        return tts.toString();
    }

    private String generateTaskDisplayText(String phoneme, PhonemeInfo info, LearningContext context) {
        StringBuilder display = new StringBuilder();

        display.append("🔤 PRACTICE THE /").append(phoneme).append("/ SOUND\n");
        display.append("═══════════════════════════════════════\n\n");

        display.append("📝 About this sound: ").append(info.description).append("\n");
        display.append("🗣️ How to pronounce: ").append(info.articulation).append("\n\n");

        display.append(String.format("📊 Your level: %.1f/100\n", context.getCurrentLevel()));
        display.append("📈 Difficulty: ").append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append("\n\n");

        display.append("📚 Practice words:\n");
        display.append("────────────────\n");
        for (String word : info.exampleWords) {
            display.append("  • ").append(word).append("\n");
        }
        display.append("\n");

        display.append("💡 Try to pronounce each word clearly, focusing on the /")
                .append(phoneme).append("/ sound.\n");
        display.append("🎙️ Record yourself and compare with the examples.\n");

        return display.toString();
    }

    private String generateTaskTtsText(String phoneme, PhonemeInfo info, LearningContext context) {
        StringBuilder tts = new StringBuilder();

        String phonemeSpeech = PHONEME_TO_SPEECH.getOrDefault(phoneme, "the sound " + phoneme);

        tts.append("Practice the ").append(phonemeSpeech).append(" sound. ");
        tts.append(info.description).append(". ");
        tts.append("To pronounce it, ").append(info.articulation).append(". ");

        tts.append("Your current level is ").append(String.format("%.1f", context.getCurrentLevel()))
                .append(" percent. Difficulty level is ")
                .append(mapDifficulty(context.getCurrentLevel()).getDisplayName()).append(". ");

        tts.append("Practice with these words: ");
        List<String> words = info.exampleWords;
        for (int i = 0; i < Math.min(5, words.size()); i++) {
            tts.append(words.get(i));
            if (i < Math.min(5, words.size()) - 1) {
                tts.append(", ");
            }
        }
        tts.append(". ");

        tts.append("Try to pronounce each word clearly, focusing on the ")
                .append(phonemeSpeech).append(" sound. ");

        tts.append("Record yourself and compare with the examples.");

        return tts.toString();
    }

    private List<String> generatePronunciationTips(String phoneme, PhonemeInfo info) {
        List<String> tips = new ArrayList<>();

        tips.add("Listen to native speakers and try to imitate");
        tips.add("Record yourself and compare with examples");
        tips.add("Practice minimal pairs (e.g., ship/sheep)");

        if (phoneme.equals("θ") || phoneme.equals("ð")) {
            tips.add("Put your tongue between your teeth");
            tips.add("Blow air out for /θ/, add voice for /ð/");
        } else if (phoneme.equals("r")) {
            tips.add("Do not trill your tongue like in Russian/Spanish");
            tips.add("Curve your tongue back without touching the roof");
        } else if (phoneme.equals("æ")) {
            tips.add("Open your mouth wider than for /e/");
            tips.add("Think 'cat' - a very open sound");
        } else if (phoneme.equals("ɪ")) {
            tips.add("Shorter and more relaxed than /iː/");
            tips.add("Think 'sit' - a quick, relaxed sound");
        }

        return tips;
    }

    private List<String> getDefaultWords(String phoneme) {
        return Arrays.asList("word with " + phoneme, "practice", "repeat");
    }

    private String determineDifficultyLevel(double level) {
        if (level < BEGINNER_THRESHOLD) return "beginner";
        if (level < INTERMEDIATE_THRESHOLD) return "intermediate";
        return "advanced";
    }

    private LearningTask.DifficultyLevel mapDifficulty(double level) {
        if (level < BEGINNER_THRESHOLD) return LearningTask.DifficultyLevel.BEGINNER;
        if (level < INTERMEDIATE_THRESHOLD) return LearningTask.DifficultyLevel.INTERMEDIATE;
        if (level < ADVANCED_THRESHOLD) return LearningTask.DifficultyLevel.ADVANCED;
        return LearningTask.DifficultyLevel.EXPERT;
    }

    private LearningMode determineNextMode(LearningContext context, PronunciationState state) {
        if (state == null) return LearningMode.PRONUNCIATION;

        double avgScore = state.averageScore;

        if (avgScore > EXCELLENT_PRONUNCIATION && state.practicedPhonemes.size() > ACHIEVEMENT_PHONEMES_10) {
            return LearningMode.CONVERSATION;
        } else if (avgScore < GOOD_PRONUNCIATION) {
            return LearningMode.LISTENING;
        }

        return LearningMode.PRONUNCIATION;
    }

    private double calculateProgress(PronunciationState state) {
        if (state == null || state.phonemeScores.isEmpty()) return 0;

        double averageScore = state.averageScore;
        double phonemeBonus = Math.min(25, state.practicedPhonemes.size() * 2);
        double consistencyBonus = Math.min(15, state.getSuccessRate() * 0.15);

        return Math.min(100, averageScore * 0.6 + phonemeBonus + consistencyBonus);
    }

    private List<String> generateRecommendations(PronunciationState state) {
        List<String> recommendations = new ArrayList<>();

        if (state == null) return recommendations;

        List<String> weakPhonemes = state.getWeakPhonemes();
        if (!weakPhonemes.isEmpty()) {
            recommendations.add("Focus on these sounds: " +
                    weakPhonemes.stream().map(p -> "/" + p + "/").collect(Collectors.joining(", ")));
        }

        if (state.practicedPhonemes.size() < ACHIEVEMENT_PHONEMES_5) {
            recommendations.add("Try practicing more different sounds");
        }

        if (state.getSuccessRate() < 50) {
            recommendations.add("Slow down and focus on accuracy first");
        }

        if (state.averageScore < GOOD_PRONUNCIATION) {
            recommendations.add("Listen to native speakers and shadow their pronunciation");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great progress! Try using these sounds in conversation");
        }

        return recommendations;
    }

    private List<String> getAchievements(PronunciationState state) {
        List<String> achievements = new ArrayList<>();

        if (state == null) return achievements;

        if (state.practicedPhonemes.size() >= ACHIEVEMENT_PHONEMES_15) {
            achievements.add("Master of Sounds - 15+ phonemes practiced!");
        } else if (state.practicedPhonemes.size() >= ACHIEVEMENT_PHONEMES_10) {
            achievements.add("Sound Explorer - 10+ phonemes practiced");
        } else if (state.practicedPhonemes.size() >= ACHIEVEMENT_PHONEMES_5) {
            achievements.add("Beginning Phonetics - 5+ phonemes practiced");
        }

        if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_100) {
            achievements.add("Pronunciation Pro - 100+ exercises!");
        } else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_50) {
            achievements.add("Dedicated Learner - 50+ exercises");
        } else if (state.exercisesCompleted >= ACHIEVEMENT_EXERCISES_20) {
            achievements.add("Getting Started - 20+ exercises");
        }

        if (state.bestScore >= ACHIEVEMENT_SCORE_95) {
            achievements.add("Near-native pronunciation! " + String.format("%.1f%%", state.bestScore));
        } else if (state.averageScore >= ACHIEVEMENT_SCORE_85) {
            achievements.add("Excellent pronunciation! " + String.format("%.1f%%", state.averageScore));
        } else if (state.averageScore >= ACHIEVEMENT_SCORE_70) {
            achievements.add("Good progress! " + String.format("%.1f%%", state.averageScore));
        }

        return achievements;
    }

    public Map<String, Object> getSessionState(String userId) {
        PronunciationState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("practicedPhonemes", new ArrayList<>(state.practicedPhonemes));
        stateMap.put("phonemeScores", new HashMap<>(state.phonemeScores));
        stateMap.put("phonemeAttempts", new HashMap<>(state.phonemeAttempts));
        stateMap.put("scoreHistory", new ArrayList<>(state.scoreHistory));
        stateMap.put("currentPhoneme", state.currentPhoneme);
        stateMap.put("exercisesCompleted", state.exercisesCompleted);
        stateMap.put("correctAttempts", state.correctAttempts);
        stateMap.put("averageScore", state.averageScore);
        stateMap.put("bestScore", state.bestScore);
        stateMap.put("totalTimeSpent", state.totalTimeSpent);

        return stateMap;
    }

    public void restoreSessionState(String userId, Map<String, Object> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) return;

        PronunciationState state = new PronunciationState();

        @SuppressWarnings("unchecked")
        List<String> practicedPhonemes = (List<String>) stateMap.getOrDefault("practicedPhonemes", Collections.emptyList());
        state.practicedPhonemes.addAll(practicedPhonemes);

        @SuppressWarnings("unchecked")
        Map<String, Double> phonemeScores = (Map<String, Double>) stateMap.getOrDefault("phonemeScores", Collections.emptyMap());
        state.phonemeScores.putAll(phonemeScores);

        @SuppressWarnings("unchecked")
        Map<String, Integer> phonemeAttempts = (Map<String, Integer>) stateMap.getOrDefault("phonemeAttempts", Collections.emptyMap());
        state.phonemeAttempts.putAll(phonemeAttempts);

        @SuppressWarnings("unchecked")
        List<Double> scoreHistory = (List<Double>) stateMap.getOrDefault("scoreHistory", Collections.emptyList());
        state.scoreHistory.addAll(scoreHistory);

        state.currentPhoneme = (String) stateMap.get("currentPhoneme");
        state.exercisesCompleted = (int) stateMap.getOrDefault("exercisesCompleted", 0);
        state.correctAttempts = (int) stateMap.getOrDefault("correctAttempts", 0);
        state.averageScore = (double) stateMap.getOrDefault("averageScore", 0.0);
        state.bestScore = (double) stateMap.getOrDefault("bestScore", 0.0);
        state.totalTimeSpent = (long) stateMap.getOrDefault("totalTimeSpent", 0L);

        sessions.put(userId, state);
        logger.debug("Session state restored for user {}", userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
        logger.debug("Session cleared for user {}", userId);
    }

    public Map<String, Object> getSessionStats(String userId) {
        PronunciationState state = sessions.get(userId);
        if (state == null) return Collections.emptyMap();

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageScore", state.averageScore);
        stats.put("bestScore", state.bestScore);
        stats.put("successRate", state.getSuccessRate());
        stats.put("phonemesPracticed", state.practicedPhonemes.size());
        stats.put("exercisesCompleted", state.exercisesCompleted);
        stats.put("weakPhonemes", state.getWeakPhonemes());

        return stats;
    }
}