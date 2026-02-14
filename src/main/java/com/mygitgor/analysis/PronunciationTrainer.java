package com.mygitgor.analysis;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.service.interfaces.IRecommendationService;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class PronunciationTrainer implements IRecommendationService, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PronunciationTrainer.class);

    private static final int WEAK_PHONEME_THRESHOLD = 70;
    private static final int MAX_WEAK_PHONEMES = 3;
    private static final int ANALYSIS_TIMEOUT_SECONDS = 30;
    private static final float HIGH_PRIORITY_IMPROVEMENT = 25.0f;
    private static final float MEDIUM_PRIORITY_IMPROVEMENT = 18.0f;
    private static final float LOW_PRIORITY_IMPROVEMENT = 10.0f;

    private final AudioAnalyzer audioAnalyzer;
    private final Map<String, String> phonemeExamples;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<?>> currentOperation = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;

    public PronunciationTrainer() {
        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();
        this.audioAnalyzer = new AudioAnalyzer();
        this.phonemeExamples = createPhonemeExamples();

        logger.info("Инициализирован тренажер произношения");
    }

    @Override
    public List<RecommendationEngine.PersonalizedRecommendation> generateRecommendations(EnhancedSpeechAnalysis analysis) {
        logger.info("Генерация персонализированных рекомендаций на основе анализа");

        if (analysis == null || closed.get()) {
            return Collections.emptyList();
        }

        List<RecommendationEngine.PersonalizedRecommendation> recommendations = new ArrayList<>();

        // Рекомендации на основе слабых фонем
        addPhonemeRecommendations(analysis, recommendations);

        // Рекомендации на основе произношения
        if (analysis.getPronunciationScore() < WEAK_PHONEME_THRESHOLD) {
            recommendations.add(createPronunciationRecommendation(analysis));
        }

        // Рекомендации на основе беглости
        if (analysis.getFluencyScore() < 65) {
            recommendations.add(createFluencyRecommendation(analysis));
        }

        // Рекомендации на основе интонации
        if (analysis.getIntonationScore() < WEAK_PHONEME_THRESHOLD) {
            recommendations.add(createIntonationRecommendation(analysis));
        }

        // Рекомендации на основе громкости
        if (analysis.getVolumeScore() < 65) {
            recommendations.add(createVolumeRecommendation(analysis));
        }

        logger.info("Сгенерировано {} персонализированных рекомендаций", recommendations.size());
        return recommendations;
    }

    private void addPhonemeRecommendations(EnhancedSpeechAnalysis analysis,
                                           List<RecommendationEngine.PersonalizedRecommendation> recommendations) {
        if (analysis.getPhonemeScores() == null || analysis.getPhonemeScores().isEmpty()) {
            return;
        }

        List<String> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                .filter(e -> e.getValue() < WEAK_PHONEME_THRESHOLD)
                .sorted(Map.Entry.comparingByValue())
                .limit(MAX_WEAK_PHONEMES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!weakPhonemes.isEmpty()) {
            recommendations.add(createPhonemeRecommendation(weakPhonemes, analysis));
        }
    }

    @Override
    public RecommendationEngine.WeeklyLearningPlan generateWeeklyPlan(EnhancedSpeechAnalysis analysis) {
        logger.info("Генерация недельного плана обучения");

        if (analysis == null || closed.get()) {
            return createEmptyPlan();
        }

        RecommendationEngine.WeeklyLearningPlan plan = new RecommendationEngine.WeeklyLearningPlan();

        double overallScore = analysis.getOverallScore();
        PlanConfig config = determinePlanConfig(overallScore);

        plan.setTargetLevel(config.targetLevel);
        plan.setExpectedImprovement(config.expectedImprovement);
        plan.setWeeklyGoal(config.weeklyGoal);

        List<RecommendationEngine.DailySchedule> schedule = createWeeklySchedule(analysis);
        plan.setSchedule(schedule);

        logger.info("Недельный план создан: цель='{}', улучшение={}%",
                config.weeklyGoal, config.expectedImprovement);

        return plan;
    }

    private PlanConfig determinePlanConfig(double overallScore) {
        if (overallScore < 60) {
            return new PlanConfig(
                    "A2 (Элементарный)",
                    15.0f,
                    "Освоить базовые звуки и простые фразы"
            );
        } else if (overallScore < 75) {
            return new PlanConfig(
                    "B1 (Средний)",
                    12.0f,
                    "Улучшить произношение сложных звуков и беглость речи"
            );
        } else if (overallScore < 85) {
            return new PlanConfig(
                    "B2 (Выше среднего)",
                    8.0f,
                    "Работа над интонацией и естественностью речи"
            );
        } else {
            return new PlanConfig(
                    "C1 (Продвинутый)",
                    5.0f,
                    "Совершенствование акцента и сложных фонетических конструкций"
            );
        }
    }

    private RecommendationEngine.WeeklyLearningPlan createEmptyPlan() {
        RecommendationEngine.WeeklyLearningPlan plan = new RecommendationEngine.WeeklyLearningPlan();
        plan.setTargetLevel("Не определен");
        plan.setExpectedImprovement(0.0f);
        plan.setWeeklyGoal("Нет данных для анализа");
        plan.setSchedule(Collections.emptyList());
        return plan;
    }

    private RecommendationEngine.PersonalizedRecommendation createPhonemeRecommendation(
            List<String> weakPhonemes, EnhancedSpeechAnalysis analysis) {

        String description = formatPhonemeDescription(weakPhonemes);
        List<String> exercises = generatePhonemeExercises(weakPhonemes, analysis);

        return new RecommendationEngine.PersonalizedRecommendation(
                generateId("PHON"),
                "🔊 Тренировка проблемных звуков",
                description,
                "Высокий",
                exercises,
                HIGH_PRIORITY_IMPROVEMENT
        );
    }

    private String formatPhonemeDescription(List<String> weakPhonemes) {
        StringBuilder desc = new StringBuilder("Сосредоточьтесь на этих звуках: ");
        for (int i = 0; i < weakPhonemes.size(); i++) {
            if (i > 0) desc.append(", ");
            desc.append("/").append(weakPhonemes.get(i)).append("/");
        }
        return desc.toString();
    }

    private List<String> generatePhonemeExercises(List<String> weakPhonemes, EnhancedSpeechAnalysis analysis) {
        List<String> exercises = new ArrayList<>();

        for (String phoneme : weakPhonemes) {
            String difficulty = analysis.getPronunciationScore() < 60 ? "beginner" : "intermediate";
            PronunciationExercise exercise = createExercise(phoneme, difficulty);

            if (exercise.getExamples() != null && !exercise.getExamples().isEmpty()) {
                exercises.addAll(exercise.getExamples().stream()
                        .limit(2)
                        .collect(Collectors.toList()));
            }
        }

        return exercises;
    }

    private RecommendationEngine.PersonalizedRecommendation createPronunciationRecommendation(
            EnhancedSpeechAnalysis analysis) {
        return new RecommendationEngine.PersonalizedRecommendation(
                generateId("PRON"),
                "🎯 Улучшение общего произношения",
                "Ваше общее произношение нуждается в улучшении. Сосредоточьтесь на базовых звуках и их различении.",
                "Высокий",
                createPronunciationExercises(),
                20.0f
        );
    }

    private List<String> createPronunciationExercises() {
        return Arrays.asList(
                "Повторяйте минимальные пары: ship/sheep, live/leave, bit/beat",
                "Практикуйте звук 'th' в словах: think, this, that, through",
                "Записывайте себя и сравнивайте с произношением носителей",
                "Используйте технику 'shadowing' - повторяйте за диктором"
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createFluencyRecommendation(
            EnhancedSpeechAnalysis analysis) {
        return new RecommendationEngine.PersonalizedRecommendation(
                generateId("FLU"),
                "⚡ Развитие беглости речи",
                "Работайте над скоростью и плавностью речи. Уменьшите количество и длительность пауз.",
                "Средний",
                createFluencyExercises(),
                MEDIUM_PRIORITY_IMPROVEMENT
        );
    }

    private List<String> createFluencyExercises() {
        return Arrays.asList(
                "Техника 'shadowing': повторяйте за диктором без пауз",
                "Читайте вслух, постепенно увеличивая темп",
                "Говорите на таймер: 1 минута без остановки на любую тему",
                "Практикуйте скороговорки для улучшения артикуляции"
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createIntonationRecommendation(
            EnhancedSpeechAnalysis analysis) {
        return new RecommendationEngine.PersonalizedRecommendation(
                generateId("INT"),
                "🎵 Улучшение интонации",
                "Работайте над интонационными паттернами английского языка.",
                "Средний",
                createIntonationExercises(),
                15.0f
        );
    }

    private List<String> createIntonationExercises() {
        return Arrays.asList(
                "Практикуйте повышение тона в вопросах",
                "Выделяйте ключевые слова в предложении голосом",
                "Слушайте и имитируйте интонацию носителей",
                "Читайте диалоги с разными эмоциями"
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createVolumeRecommendation(
            EnhancedSpeechAnalysis analysis) {
        return new RecommendationEngine.PersonalizedRecommendation(
                generateId("VOL"),
                "🔊 Контроль громкости",
                "Работайте над стабильностью и адекватностью громкости речи.",
                "Низкий",
                createVolumeExercises(),
                LOW_PRIORITY_IMPROVEMENT
        );
    }

    private List<String> createVolumeExercises() {
        return Arrays.asList(
                "Говорите с разным расстоянием до микрофона",
                "Практикуйте изменение громкости в пределах одного предложения",
                "Записывайте себя и анализируйте уровень громкости"
        );
    }

    private List<RecommendationEngine.DailySchedule> createWeeklySchedule(EnhancedSpeechAnalysis analysis) {
        List<RecommendationEngine.DailySchedule> schedule = new ArrayList<>();

        String[] days = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
        String[] focuses = {
                "Фонемы /θ/ и /ð/ (межзубные звуки)",
                "Фонемы /r/ и /w/ (сонорные согласные)",
                "Гласные /iː/ и /ɪ/ (долгий и краткий)",
                "Дифтонги /eɪ/, /aɪ/, /ɔɪ/",
                "Интонация в вопросах и утверждениях",
                "Беглость речи и скороговорки",
                "Повторение и закрепление"
        };

        int baseDuration = analysis.getOverallScore() < 70 ? 30 : 20;

        for (int i = 0; i < days.length; i++) {
            schedule.add(createDailySchedule(i, days[i], focuses[i % focuses.length],
                    baseDuration + (i % 3) * 5));
        }

        return schedule;
    }

    private RecommendationEngine.DailySchedule createDailySchedule(int index, String day,
                                                                   String focus, int duration) {
        RecommendationEngine.DailySchedule schedule = new RecommendationEngine.DailySchedule();
        schedule.setDay(day);
        schedule.setFocus(focus);
        schedule.setDurationMinutes(duration);
        schedule.setExercises(createDailyExercises(focus));
        schedule.setTips(Collections.singletonList(getDailyTip(index)));
        return schedule;
    }

    private List<String> createDailyExercises(String focus) {
        return Arrays.asList(
                "Разминка: повторение звуков - 5 минут",
                "Основная практика: " + focus + " - 15 минут",
                "Запись и анализ своей речи - 5 минут"
        );
    }

    private String getDailyTip(int day) {
        String[] tips = {
                "Не забывайте про межзубное положение языка для звука /θ/",
                "Английский /r/ произносится без вибрации кончика языка",
                "Долгие гласные должны быть действительно долгими",
                "Дифтонги - это скольжение от одного звука к другому",
                "В вопросах голос повышается к концу предложения",
                "Скороговорки помогают улучшить дикцию",
                "Регулярность важнее длительности занятий"
        };
        return tips[day % tips.length];
    }

    private String generateId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }

    public CompletableFuture<PronunciationResult> checkPronunciationAsync(String audioFilePath,
                                                                          String targetPhoneme) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("PronunciationTrainer закрыт"));
        }

        CompletableFuture<PronunciationResult> future = CompletableFuture.supplyAsync(() ->
                checkPronunciation(audioFilePath, targetPhoneme), backgroundExecutor);

        currentOperation.set(future);

        threadPoolManager.getScheduledExecutor().schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                logger.warn("Таймаут проверки произношения для звука {}", targetPhoneme);
            }
        }, ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return future;
    }

    private Map<String, String> createPhonemeExamples() {
        Map<String, String> examples = new HashMap<>();

        examples.put("iː", "sheep, see, eat");
        examples.put("ɪ", "ship, sit, pin");
        examples.put("e", "bed, head, said");
        examples.put("æ", "cat, bat, apple");
        examples.put("ɑː", "car, park, father");
        examples.put("ɒ", "hot, rock, watch");
        examples.put("ɔː", "door, more, law");
        examples.put("ʊ", "book, put, could");
        examples.put("uː", "blue, food, too");
        examples.put("ʌ", "cup, luck, up");
        examples.put("ɜː", "bird, learn, turn");
        examples.put("ə", "about, banana, supply");

        examples.put("eɪ", "day, make, rain");
        examples.put("aɪ", "my, time, eye");
        examples.put("ɔɪ", "boy, join, toy");
        examples.put("aʊ", "now, house, out");
        examples.put("oʊ", "go, home, show");
        examples.put("ɪə", "here, near, ear");
        examples.put("eə", "hair, care, there");
        examples.put("ʊə", "tour, sure, pure");

        examples.put("p", "pen, stop, apple");
        examples.put("b", "big, baby, rubber");
        examples.put("t", "tea, letter, time");
        examples.put("d", "day, ladder, did");
        examples.put("k", "cat, back, key");
        examples.put("g", "go, get, big");
        examples.put("f", "fish, off, phone");
        examples.put("v", "voice, have, very");
        examples.put("θ", "think, both, thick");
        examples.put("ð", "this, mother, that");
        examples.put("s", "see, miss, say");
        examples.put("z", "zoo, has, zero");
        examples.put("ʃ", "she, wish, sure");
        examples.put("ʒ", "pleasure, vision");
        examples.put("h", "hot, have, who");
        examples.put("m", "man, lemon, my");
        examples.put("n", "no, ten, nice");
        examples.put("ŋ", "sing, think, long");
        examples.put("l", "leg, little, like");
        examples.put("r", "red, very, right");
        examples.put("j", "yes, year, you");
        examples.put("w", "we, want, what");
        examples.put("tʃ", "chair, match, church");
        examples.put("dʒ", "jump, age, judge");

        return examples;
    }

    public PronunciationExercise createExercise(String phoneme, String difficulty) {
        PronunciationExercise exercise = new PronunciationExercise();
        exercise.setTargetPhoneme(phoneme);
        exercise.setDifficulty(difficulty);
        exercise.setInstructions(generateInstructions(phoneme, difficulty));
        exercise.setExamples(generateExamples(phoneme, difficulty));
        exercise.setPracticeWords(generatePracticeWords(phoneme, difficulty));
        exercise.setTips(generateTips(phoneme));

        logger.debug("Создано упражнение для звука: {}", phoneme);
        return exercise;
    }

    public PronunciationResult checkPronunciation(String audioFilePath, String targetPhoneme) {
        if (closed.get()) {
            PronunciationResult result = new PronunciationResult();
            result.setTargetPhoneme(targetPhoneme);
            result.setScore(0);
            result.setFeedback("Тренажер произношения закрыт");
            return result;
        }

        PronunciationResult result = new PronunciationResult();
        result.setTargetPhoneme(targetPhoneme);

        try {
            EnhancedSpeechAnalysis analysis = (EnhancedSpeechAnalysis) audioAnalyzer.analyzeAudio(
                    audioFilePath,
                    "Practice pronunciation of " + targetPhoneme
            );

            result.setAnalysis(analysis);
            double phonemeScore = evaluatePhoneme(targetPhoneme, analysis);
            result.setScore(phonemeScore);
            result.setFeedback(generatePhonemeFeedback(targetPhoneme, phonemeScore, analysis));

            logger.debug("Проверка произношения звука {}: оценка {:.1f}",
                    targetPhoneme, phonemeScore);

        } catch (Exception e) {
            logger.error("Ошибка при проверке произношения", e);
            result.setScore(0);
            result.setFeedback("Ошибка при анализе произношения: " + e.getMessage());
        }

        return result;
    }

    private double evaluatePhoneme(String phoneme, EnhancedSpeechAnalysis analysis) {
        double baseScore = analysis.getPronunciationScore();

        return switch (phoneme) {
            case "θ", "ð" -> baseScore * 0.8;
            case "r" -> baseScore * 0.85;
            case "w", "v" -> baseScore * 0.9;
            case "æ", "ʌ" -> baseScore * 0.75;
            default -> baseScore;
        };
    }

    private String generateInstructions(String phoneme, String difficulty) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("УПРАЖНЕНИЕ: Произношение звука /").append(phoneme).append("/\n\n");
        instructions.append("ИНСТРУКЦИИ:\n");
        instructions.append("1. Прослушайте пример произношения\n");
        instructions.append("2. Повторите слова, обращая внимание на целевой звук\n");
        instructions.append("3. Запишите свою речь\n");
        instructions.append("4. Получите обратную связь и рекомендации\n\n");
        instructions.append("КАК ПРОИЗНОСИТСЯ:\n");
        instructions.append(getPhonemeDescription(phoneme));

        return instructions.toString();
    }

    private List<String> generateExamples(String phoneme, String difficulty) {
        List<String> examples = new ArrayList<>();
        String exampleString = phonemeExamples.get(phoneme);

        if (exampleString != null) {
            String[] words = exampleString.split(", ");
            examples.addAll(Arrays.asList(words));
        }

        examples.add("Repeat after me: " + phoneme);
        return examples;
    }

    private List<String> generatePracticeWords(String phoneme, String difficulty) {
        List<String> words = new ArrayList<>();

        switch (phoneme) {
            case "θ":
                words.add("think"); words.add("thought"); words.add("thank");
                if (!"beginner".equals(difficulty)) {
                    words.add("throughout"); words.add("thorough");
                }
                break;
            case "ð":
                words.add("this"); words.add("that"); words.add("there");
                if (!"beginner".equals(difficulty)) {
                    words.add("therefore"); words.add("nevertheless");
                }
                break;
            case "r":
                words.add("red"); words.add("right"); words.add("rain");
                if (!"beginner".equals(difficulty)) {
                    words.add("rural"); words.add("repository");
                }
                break;
            case "æ":
                words.add("cat"); words.add("bat"); words.add("hat");
                if (!"beginner".equals(difficulty)) {
                    words.add("backpack"); words.add("abstract");
                }
                break;
            default:
                words.add("practice"); words.add("pronunciation");
                break;
        }

        return words;
    }

    private List<String> generateTips(String phoneme) {
        List<String> tips = new ArrayList<>();

        switch (phoneme) {
            case "θ":
                tips.add("Прижмите кончик языка к верхним зубам");
                tips.add("Выдыхайте воздух через небольшую щель");
                tips.add("Не заменяйте звук на русский 'с'");
                break;
            case "ð":
                tips.add("Положение языка как для /θ/, но с голосом");
                tips.add("Ощутите вибрацию в горле");
                break;
            case "r":
                tips.add("Кончик языка не касается нёба");
                tips.add("Язык оттянут назад");
                break;
            case "æ":
                tips.add("Рот открыт шире, чем для русского 'э'");
                tips.add("Звук короткий и открытый");
                break;
            case "ɪ":
                tips.add("Звук короче и закрытее, чем /iː/");
                break;
            default:
                tips.add("Слушайте и повторяйте за носителями");
                break;
        }

        return tips;
    }

    private String getPhonemeDescription(String phoneme) {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("θ", "Безголосый межзубный звук. Кончик языка между зубами.");
        descriptions.put("ð", "Голосовой межзубный звук. Вибрация в горле.");
        descriptions.put("r", "Английский 'r'. Кончик языка не вибрирует.");
        descriptions.put("æ", "Открытый гласный. Рот широко открыт.");
        return descriptions.getOrDefault(phoneme,
                "Международный фонетический символ: /" + phoneme + "/");
    }

    private String generatePhonemeFeedback(String phoneme, double score, EnhancedSpeechAnalysis analysis) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("РЕЗУЛЬТАТ ПРОВЕРКИ ПРОИЗНОШЕНИЯ /").append(phoneme).append("/\n\n");
        feedback.append("Ваша оценка: ").append(String.format("%.1f", score)).append("/100\n\n");

        if (score >= 90) {
            feedback.append("🌟 ОТЛИЧНО! Произношение практически идеальное!\n");
        } else if (score >= 75) {
            feedback.append("✅ ХОРОШО! Звук распознается правильно.\n");
        } else if (score >= 60) {
            feedback.append("⚠️ НЕПЛОХО, но нужна практика.\n");
        } else {
            feedback.append("📝 НУЖНО УЛУЧШИТЬ.\n");
        }

        return feedback.toString();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        logger.info("Закрытие PronunciationTrainer...");

        CompletableFuture<?> operation = currentOperation.getAndSet(null);
        if (operation != null && !operation.isDone()) {
            operation.cancel(true);
        }

        if (audioAnalyzer != null && !audioAnalyzer.isClosed()) {
            try {
                audioAnalyzer.close();
            } catch (Exception e) {
                logger.warn("Ошибка при закрытии AudioAnalyzer", e);
            }
        }

        logger.info("PronunciationTrainer закрыт");
    }

    public boolean isClosed() {
        return closed.get();
    }

    private static class PlanConfig {
        final String targetLevel;
        final float expectedImprovement;
        final String weeklyGoal;

        PlanConfig(String targetLevel, float expectedImprovement, String weeklyGoal) {
            this.targetLevel = targetLevel;
            this.expectedImprovement = expectedImprovement;
            this.weeklyGoal = weeklyGoal;
        }
    }

    public static class PronunciationExercise {
        private String targetPhoneme;
        private String difficulty;
        private String instructions;
        private List<String> examples;
        private List<String> practiceWords;
        private List<String> tips;

        // Getters and Setters
        public String getTargetPhoneme() { return targetPhoneme; }
        public void setTargetPhoneme(String targetPhoneme) { this.targetPhoneme = targetPhoneme; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }
        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples; }
        public List<String> getPracticeWords() { return practiceWords; }
        public void setPracticeWords(List<String> practiceWords) { this.practiceWords = practiceWords; }
        public List<String> getTips() { return tips; }
        public void setTips(List<String> tips) { this.tips = tips; }
    }

    public static class PronunciationResult {
        private String targetPhoneme;
        private double score;
        private String feedback;
        private EnhancedSpeechAnalysis analysis;

        public String getTargetPhoneme() { return targetPhoneme; }
        public void setTargetPhoneme(String targetPhoneme) { this.targetPhoneme = targetPhoneme; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }
        public EnhancedSpeechAnalysis getAnalysis() { return analysis; }
        public void setAnalysis(EnhancedSpeechAnalysis analysis) { this.analysis = analysis; }
    }
}