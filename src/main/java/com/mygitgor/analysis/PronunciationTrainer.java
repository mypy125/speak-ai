package com.mygitgor.analysis;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.service.interfaces.IRecommendationService;
import com.mygitgor.service.AudioAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PronunciationTrainer implements IRecommendationService, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PronunciationTrainer.class);

    private final AudioAnalyzer audioAnalyzer;
    private final Map<String, String> phonemeExamples;
    private volatile boolean closed = false;

    public PronunciationTrainer() {
        this.audioAnalyzer = new AudioAnalyzer();
        this.phonemeExamples = createPhonemeExamples();
        logger.info("Инициализирован тренажер произношения");
    }

    // ========================================
    // IRecommendationService Implementation
    // ========================================

    @Override
    public List<RecommendationEngine.PersonalizedRecommendation> generateRecommendations(EnhancedSpeechAnalysis analysis) {
        logger.info("Генерация персонализированных рекомендаций на основе анализа");

        List<RecommendationEngine.PersonalizedRecommendation> recommendations = new ArrayList<>();

        if (analysis == null) {
            return recommendations;
        }

        // Рекомендации на основе слабых фонем
        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            List<String> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                    .filter(e -> e.getValue() < 70)
                    .sorted(Map.Entry.comparingByValue())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (!weakPhonemes.isEmpty()) {
                recommendations.add(createPhonemeRecommendation(weakPhonemes, analysis));
            }
        }

        // Рекомендации на основе произношения
        if (analysis.getPronunciationScore() < 70) {
            recommendations.add(createPronunciationRecommendation(analysis));
        }

        // Рекомендации на основе беглости
        if (analysis.getFluencyScore() < 65) {
            recommendations.add(createFluencyRecommendation(analysis));
        }

        // Рекомендации на основе интонации
        if (analysis.getIntonationScore() < 70) {
            recommendations.add(createIntonationRecommendation(analysis));
        }

        // Рекомендации на основе громкости
        if (analysis.getVolumeScore() < 65) {
            recommendations.add(createVolumeRecommendation(analysis));
        }

        logger.info("Сгенерировано {} персонализированных рекомендаций", recommendations.size());
        return recommendations;
    }

    @Override
    public RecommendationEngine.WeeklyLearningPlan generateWeeklyPlan(EnhancedSpeechAnalysis analysis) {
        logger.info("Генерация недельного плана обучения");

        RecommendationEngine.WeeklyLearningPlan plan = new RecommendationEngine.WeeklyLearningPlan();

        // Определяем целевой уровень на основе текущей оценки
        double overallScore = analysis.getOverallScore();
        String targetLevel;
        float expectedImprovement;
        String weeklyGoal;

        if (overallScore < 60) {
            targetLevel = "A2 (Элементарный)";
            expectedImprovement = 15.0f;
            weeklyGoal = "Освоить базовые звуки и простые фразы";
        } else if (overallScore < 75) {
            targetLevel = "B1 (Средний)";
            expectedImprovement = 12.0f;
            weeklyGoal = "Улучшить произношение сложных звуков и беглость речи";
        } else if (overallScore < 85) {
            targetLevel = "B2 (Выше среднего)";
            expectedImprovement = 8.0f;
            weeklyGoal = "Работа над интонацией и естественностью речи";
        } else {
            targetLevel = "C1 (Продвинутый)";
            expectedImprovement = 5.0f;
            weeklyGoal = "Совершенствование акцента и сложных фонетических конструкций";
        }

        plan.setTargetLevel(targetLevel);
        plan.setExpectedImprovement(expectedImprovement);
        plan.setWeeklyGoal(weeklyGoal);

        // Создаем расписание на неделю
        List<RecommendationEngine.DailySchedule> schedule = createWeeklySchedule(analysis);
        plan.setSchedule(schedule);

        logger.info("Недельный план создан: цель='{}', улучшение={}%", weeklyGoal, expectedImprovement);

        return plan;
    }

    private RecommendationEngine.PersonalizedRecommendation createPhonemeRecommendation(
            List<String> weakPhonemes, EnhancedSpeechAnalysis analysis) {

        StringBuilder desc = new StringBuilder("Сосредоточьтесь на этих звуках: ");
        for (int i = 0; i < weakPhonemes.size(); i++) {
            if (i > 0) desc.append(", ");
            desc.append("/").append(weakPhonemes.get(i)).append("/");
        }

        List<String> exercises = new ArrayList<>();
        for (String phoneme : weakPhonemes) {
            PronunciationExercise exercise = createExercise(phoneme,
                    analysis.getPronunciationScore() < 60 ? "beginner" : "intermediate");
            if (exercise.getExamples() != null && !exercise.getExamples().isEmpty()) {
                exercises.addAll(exercise.getExamples().stream()
                        .limit(2)
                        .collect(Collectors.toList()));
            }
        }

        return new RecommendationEngine.PersonalizedRecommendation(
                "PHON_" + System.currentTimeMillis(),
                "🔊 Тренировка проблемных звуков",
                desc.toString(),
                "Высокий",
                exercises,
                25.0f
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createPronunciationRecommendation(
            EnhancedSpeechAnalysis analysis) {

        List<String> exercises = new ArrayList<>();
        exercises.add("Повторяйте минимальные пары: ship/sheep, live/leave, bit/beat");
        exercises.add("Практикуйте звук 'th' в словах: think, this, that, through");
        exercises.add("Записывайте себя и сравнивайте с произношением носителей");
        exercises.add("Используйте технику 'shadowing' - повторяйте за диктором");

        return new RecommendationEngine.PersonalizedRecommendation(
                "PRON_" + System.currentTimeMillis(),
                "🎯 Улучшение общего произношения",
                "Ваше общее произношение нуждается в улучшении. Сосредоточьтесь на базовых звуках и их различении.",
                "Высокий",
                exercises,
                20.0f
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createFluencyRecommendation(
            EnhancedSpeechAnalysis analysis) {

        List<String> exercises = new ArrayList<>();
        exercises.add("Техника 'shadowing': повторяйте за диктором без пауз");
        exercises.add("Читайте вслух, постепенно увеличивая темп");
        exercises.add("Говорите на таймер: 1 минута без остановки на любую тему");
        exercises.add("Практикуйте скороговорки для улучшения артикуляции");

        return new RecommendationEngine.PersonalizedRecommendation(
                "FLU_" + System.currentTimeMillis(),
                "⚡ Развитие беглости речи",
                "Работайте над скоростью и плавностью речи. Уменьшите количество и длительность пауз.",
                "Средний",
                exercises,
                18.0f
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createIntonationRecommendation(
            EnhancedSpeechAnalysis analysis) {

        List<String> exercises = new ArrayList<>();
        exercises.add("Практикуйте повышение тона в вопросах");
        exercises.add("Выделяйте ключевые слова в предложении голосом");
        exercises.add("Слушайте и имитируйте интонацию носителей");
        exercises.add("Читайте диалоги с разными эмоциями");

        return new RecommendationEngine.PersonalizedRecommendation(
                "INT_" + System.currentTimeMillis(),
                "🎵 Улучшение интонации",
                "Работайте над интонационными паттернами английского языка.",
                "Средний",
                exercises,
                15.0f
        );
    }

    private RecommendationEngine.PersonalizedRecommendation createVolumeRecommendation(
            EnhancedSpeechAnalysis analysis) {

        List<String> exercises = new ArrayList<>();
        exercises.add("Говорите с разным расстоянием до микрофона");
        exercises.add("Практикуйте изменение громкости в пределах одного предложения");
        exercises.add("Записывайте себя и анализируйте уровень громкости");

        return new RecommendationEngine.PersonalizedRecommendation(
                "VOL_" + System.currentTimeMillis(),
                "🔊 Контроль громкости",
                "Работайте над стабильностью и адекватностью громкости речи.",
                "Низкий",
                exercises,
                10.0f
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
            RecommendationEngine.DailySchedule day = new RecommendationEngine.DailySchedule();
            day.setDay(days[i]);
            day.setFocus(focuses[i % focuses.length]);
            day.setDurationMinutes(baseDuration + (i % 3) * 5);

            List<String> exercises = new ArrayList<>();
            exercises.add("Разминка: повторение звуков - 5 минут");
            exercises.add("Основная практика: " + focuses[i % focuses.length] + " - 15 минут");
            exercises.add("Запись и анализ своей речи - 5 минут");
            day.setExercises(exercises);

            List<String> tips = new ArrayList<>();
            tips.add(getDailyTip(i));
            day.setTips(tips);

            schedule.add(day);
        }

        return schedule;
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

    // ========================================
    // Existing PronunciationTrainer Methods
    // ========================================

    private Map<String, String> createPhonemeExamples() {
        Map<String, String> examples = new HashMap<>();

        // Гласные звуки английского языка
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

        // Дифтонги
        examples.put("eɪ", "day, make, rain");
        examples.put("aɪ", "my, time, eye");
        examples.put("ɔɪ", "boy, join, toy");
        examples.put("aʊ", "now, house, out");
        examples.put("oʊ", "go, home, show");
        examples.put("ɪə", "here, near, ear");
        examples.put("eə", "hair, care, there");
        examples.put("ʊə", "tour, sure, pure");

        // Согласные
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

    /**
     * Создание упражнения на произношение
     */
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

    /**
     * Проверка произношения звука
     */
    public PronunciationResult checkPronunciation(String audioFilePath, String targetPhoneme) {
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

        switch (phoneme) {
            case "θ": case "ð":
                return baseScore * 0.8;
            case "r":
                return baseScore * 0.85;
            case "w": case "v":
                return baseScore * 0.9;
            case "æ": case "ʌ":
                return baseScore * 0.75;
            default:
                return baseScore;
        }
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
        if (closed) return;

        logger.info("Закрытие PronunciationTrainer...");
        closed = true;

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
        return closed;
    }

    // ====================== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ======================

    /**
     * Упражнение на произношение
     */
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

        // Getters and Setters
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