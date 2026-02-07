package com.mygitgor.analysis;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.speech.AudioAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PronunciationTrainer {
    private static final Logger logger = LoggerFactory.getLogger(PronunciationTrainer.class);

    private final AudioAnalyzer audioAnalyzer;
    private final Map<String, String> phonemeExamples;

    public PronunciationTrainer() {
        this.audioAnalyzer = new AudioAnalyzer();
        this.phonemeExamples = createPhonemeExamples();
    }

    /**
     * Создание примеров произношения для каждого звука
     */
    private Map<String, String> createPhonemeExamples() {
        Map<String, String> examples = new HashMap<>();

        // Гласные звуки английского языка
        examples.put("iː", "sheep, see, eat"); // как в "see"
        examples.put("ɪ", "ship, sit, pin"); // как в "sit"
        examples.put("e", "bed, head, said"); // как в "bed"
        examples.put("æ", "cat, bat, apple"); // как в "cat"
        examples.put("ɑː", "car, park, father"); // как в "car"
        examples.put("ɒ", "hot, rock, watch"); // как в "hot"
        examples.put("ɔː", "door, more, law"); // как в "door"
        examples.put("ʊ", "book, put, could"); // как в "book"
        examples.put("uː", "blue, food, too"); // как в "blue"
        examples.put("ʌ", "cup, luck, up"); // как в "cup"
        examples.put("ɜː", "bird, learn, turn"); // как в "bird"
        examples.put("ə", "about, banana, supply"); // schwa

        // Дифтонги
        examples.put("eɪ", "day, make, rain"); // как в "day"
        examples.put("aɪ", "my, time, eye"); // как в "my"
        examples.put("ɔɪ", "boy, join, toy"); // как в "boy"
        examples.put("aʊ", "now, house, out"); // как в "now"
        examples.put("oʊ", "go, home, show"); // как в "go"
        examples.put("ɪə", "here, near, ear"); // как в "here"
        examples.put("eə", "hair, care, there"); // как в "hair"
        examples.put("ʊə", "tour, sure, pure"); // как в "tour"

        // Согласные
        examples.put("p", "pen, stop, apple");
        examples.put("b", "big, baby, rubber");
        examples.put("t", "tea, letter, time");
        examples.put("d", "day, ladder, did");
        examples.put("k", "cat, back, key");
        examples.put("g", "go, get, big");
        examples.put("f", "fish, off, phone");
        examples.put("v", "voice, have, very");
        examples.put("θ", "think, both, thick"); // voiceless th
        examples.put("ð", "this, mother, that"); // voiced th
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

        logger.info("Создано упражнение для звука: {}", phoneme);
        return exercise;
    }

    /**
     * Проверка произношения звука
     */
    public PronunciationResult checkPronunciation(String audioFilePath, String targetPhoneme) {
        PronunciationResult result = new PronunciationResult();
        result.setTargetPhoneme(targetPhoneme);

        try {
            // Анализ аудио
            EnhancedSpeechAnalysis analysis = (EnhancedSpeechAnalysis) audioAnalyzer.analyzeAudio(
                    audioFilePath,
                    "Practice pronunciation of " + targetPhoneme
            );

            result.setAnalysis(analysis);

            // Специфичная проверка для звука
            double phonemeScore = evaluatePhoneme(targetPhoneme, analysis);
            result.setScore(phonemeScore);

            // Генерация обратной связи
            result.setFeedback(generatePhonemeFeedback(targetPhoneme, phonemeScore, analysis));

            logger.debug("Проверка произношения звука {}: оценка {}",
                    targetPhoneme, String.format("%.1f", phonemeScore));

        } catch (Exception e) {
            logger.error("Ошибка при проверке произношения", e);
            result.setScore(0);
            result.setFeedback("Ошибка при анализе произношения: " + e.getMessage());
        }

        return result;
    }

    /**
     * Оценка произношения конкретного звука
     */
    private double evaluatePhoneme(String phoneme, EnhancedSpeechAnalysis analysis) {
        // Эмуляция оценки конкретного звука
        double baseScore = analysis.getPronunciationScore();

        // Корректировка на основе типа звука
        switch (phoneme) {
            case "θ": // th как в "think"
            case "ð": // th как в "this"
                // Русскоязычные часто заменяют на "s" или "z"
                return baseScore * 0.8;

            case "r": // Английский "r"
                // Отличается от русского
                return baseScore * 0.85;

            case "w": // Английский "w"
            case "v": // Отличие от русского "в"
                return baseScore * 0.9;

            case "æ": // как в "cat"
            case "ʌ": // как в "cup"
                // Гласные, которых нет в русском
                return baseScore * 0.75;

            default:
                return baseScore;
        }
    }

    /**
     * Генерация инструкций для упражнения
     */
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

    /**
     * Генерация примеров для практики
     */
    private List<String> generateExamples(String phoneme, String difficulty) {
        List<String> examples = new ArrayList<>();
        String exampleString = phonemeExamples.get(phoneme);

        if (exampleString != null) {
            String[] words = exampleString.split(", ");

            for (String word : words) {
                examples.add(word);

                // Добавляем больше примеров для сложного уровня
                if ("advanced".equals(difficulty)) {
                    examples.add(word + " in sentence");
                }
            }
        }

        // Добавляем дополнительные примеры
        examples.add("Repeat after me: " + phoneme);
        examples.add("Practice slowly: " + phoneme + " - " + phoneme + " - " + phoneme);

        return examples;
    }

    /**
     * Генерация слов для практики
     */
    private List<String> generatePracticeWords(String phoneme, String difficulty) {
        List<String> words = new ArrayList<>();

        // Базовые слова для каждого звука
        switch (phoneme) {
            case "θ": // voiceless th
                words.add("think");
                words.add("thought");
                words.add("thank");
                words.add("thing");
                if (!"beginner".equals(difficulty)) {
                    words.add("throughout");
                    words.add("thorough");
                }
                break;

            case "ð": // voiced th
                words.add("this");
                words.add("that");
                words.add("there");
                words.add("then");
                if (!"beginner".equals(difficulty)) {
                    words.add("therefore");
                    words.add("nevertheless");
                }
                break;

            case "r": // English r
                words.add("red");
                words.add("right");
                words.add("rain");
                words.add("road");
                if (!"beginner".equals(difficulty)) {
                    words.add("rural");
                    words.add("repository");
                }
                break;

            case "æ": // cat vowel
                words.add("cat");
                words.add("bat");
                words.add("hat");
                words.add("mat");
                if (!"beginner".equals(difficulty)) {
                    words.add("backpack");
                    words.add("abstract");
                }
                break;

            default:
                // Общие слова для других звуков
                words.add("practice");
                words.add("pronunciation");
                words.add("speak");
                words.add("clear");
                break;
        }

        return words;
    }

    /**
     * Генерация советов по произношению
     */
    private List<String> generateTips(String phoneme) {
        List<String> tips = new ArrayList<>();

        switch (phoneme) {
            case "θ": // voiceless th
                tips.add("Прижмите кончик языка к верхним зубам");
                tips.add("Выдыхайте воздух через небольшую щель");
                tips.add("Не заменяйте звук на русский 'с'");
                break;

            case "ð": // voiced th
                tips.add("Положение языка как для /θ/, но с голосом");
                tips.add("Ощутите вибрацию в горле");
                tips.add("Практикуйте в словах 'this', 'that', 'there'");
                break;

            case "r": // English r
                tips.add("Кончик языка не касается нёба");
                tips.add("Язык оттянут назад");
                tips.add("Губы немного округлены");
                break;

            case "æ": // cat vowel
                tips.add("Рот открыт шире, чем для русского 'э'");
                tips.add("Язык лежит плоско");
                tips.add("Звук короткий и открытый");
                break;

            case "ɪ": // sit vowel
                tips.add("Звук короче и закрытее, чем /iː/");
                tips.add("Язык чуть ниже, чем для русского 'и'");
                tips.add("Не растягивайте звук");
                break;

            default:
                tips.add("Слушайте и повторяйте за носителями");
                tips.add("Записывайте себя и сравнивайте");
                tips.add("Практикуйтесь регулярно");
                break;
        }

        return tips;
    }

    /**
     * Получение описания звука
     */
    private String getPhonemeDescription(String phoneme) {
        Map<String, String> descriptions = new HashMap<>();

        descriptions.put("θ", "Безголосый межзубный звук (как в 'think'). Кончик языка между зубами.");
        descriptions.put("ð", "Голосовой межзубный звук (как в 'this'). Вибрация в горле.");
        descriptions.put("r", "Английский 'r'. Кончик языка не вибрирует, оттянут назад.");
        descriptions.put("æ", "Открытый гласный (как в 'cat'). Рот широко открыт.");
        descriptions.put("ɪ", "Короткий гласный (как в 'sit'). Звук между 'и' и 'ы'.");
        descriptions.put("iː", "Долгий гласный (как в 'see'). Углы губ растянуты.");
        descriptions.put("ʃ", "Шипящий звук (как в 'she'). Язык поднят к нёбу.");
        descriptions.put("ʒ", "Звонкий вариант /ʃ/ (как в 'pleasure').");
        descriptions.put("ŋ", "Носовой звук (как в 'sing'). Воздух проходит через нос.");
        descriptions.put("w", "Губно-губной звук (как в 'we'). Губы округлены.");
        descriptions.put("v", "Губно-зубной звук (как в 'very'). Нижняя губа к верхним зубам.");

        return descriptions.getOrDefault(phoneme,
                "Международный фонетический символ: /" + phoneme + "/. " +
                        "Практикуйте, слушая примеры произношения.");
    }

    /**
     * Генерация обратной связи по произношению звука
     */
    private String generatePhonemeFeedback(String phoneme, double score, EnhancedSpeechAnalysis analysis) {
        StringBuilder feedback = new StringBuilder();

        feedback.append("РЕЗУЛЬТАТ ПРОВЕРКИ ПРОИЗНОШЕНИЯ /").append(phoneme).append("/\n\n");
        feedback.append("Ваша оценка: ").append(String.format("%.1f", score)).append("/100\n\n");

        if (score >= 90) {
            feedback.append("🌟 ОТЛИЧНО! Ваше произношение звука /").append(phoneme)
                    .append("/ практически идеальное!\n");
        } else if (score >= 75) {
            feedback.append("✅ ХОРОШО! Звук распознается правильно, есть небольшие нюансы.\n");
        } else if (score >= 60) {
            feedback.append("⚠️ НЕПЛОХО, но нужна практика. Звук близок к правильному.\n");
        } else {
            feedback.append("📝 НУЖНО УЛУЧШИТЬ. Обратите внимание на технику произношения.\n");
        }

        feedback.append("\nАНАЛИЗ ВАШЕЙ РЕЧИ:\n");
        feedback.append("• Общая оценка произношения: ").append(String.format("%.1f", analysis.getPronunciationScore())).append("/100\n");
        feedback.append("• Беглость: ").append(String.format("%.1f", analysis.getFluencyScore())).append("/100\n");
        feedback.append("• Интонация: ").append(String.format("%.1f", analysis.getIntonationScore())).append("/100\n");
        feedback.append("• Громкость: ").append(String.format("%.1f", analysis.getVolumeScore())).append("/100\n");

        feedback.append("\nСОВЕТЫ ДЛЯ ЗВУКА /").append(phoneme).append("/:\n");
        for (String tip : generateTips(phoneme)) {
            feedback.append("• ").append(tip).append("\n");
        }

        feedback.append("\nСЛЕДУЮЩИЙ ШАГ:\n");
        if (score < 70) {
            feedback.append("Повторите упражнение 3-5 раз, сосредоточившись на технике.\n");
        } else {
            feedback.append("Перейдите к более сложным словам и фразам с этим звуком.\n");
        }

        return feedback.toString();
    }

    /**
     * Создание плана тренировок
     */
    public TrainingPlan createTrainingPlan(String[] weakPhonemes, String level) {
        TrainingPlan plan = new TrainingPlan();
        plan.setLevel(level);
        plan.setDurationWeeks(4);

        List<WeekSchedule> schedule = new ArrayList<>();

        for (int week = 1; week <= 4; week++) {
            WeekSchedule weekSchedule = new WeekSchedule();
            weekSchedule.setWeekNumber(week);
            weekSchedule.setFocusPhonemes(getWeeklyFocus(weakPhonemes, week));
            weekSchedule.setDailyExercises(createDailyExercises(weekSchedule.getFocusPhonemes(), level));
            weekSchedule.setGoals(getWeeklyGoals(week));

            schedule.add(weekSchedule);
        }

        plan.setSchedule(schedule);
        plan.setFinalGoal("Улучшить произношение " + weakPhonemes.length + " проблемных звуков");

        logger.info("Создан план тренировок на 4 недели");
        return plan;
    }

    private String[] getWeeklyFocus(String[] weakPhonemes, int week) {
        if (weakPhonemes.length <= 3) {
            return weakPhonemes; // Все звуки каждый неделю
        }

        // Разделяем звуки по неделям
        int start = (week - 1) * 2;
        int end = Math.min(start + 2, weakPhonemes.length);

        if (start >= weakPhonemes.length) {
            start = 0;
            end = Math.min(2, weakPhonemes.length);
        }

        return Arrays.copyOfRange(weakPhonemes, start, end);
    }

    private List<PronunciationExercise> createDailyExercises(String[] phonemes, String level) {
        List<PronunciationExercise> exercises = new ArrayList<>();

        for (String phoneme : phonemes) {
            exercises.add(createExercise(phoneme, level));
        }

        return exercises;
    }

    private String getWeeklyGoals(int week) {
        switch (week) {
            case 1: return "Освоить правильную артикуляцию звуков";
            case 2: return "Улучшить четкость произношения в словах";
            case 3: return "Автоматизировать звуки в быстрой речи";
            case 4: return "Закрепить результат и провести итоговую проверку";
            default: return "Улучшить общее произношение";
        }
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

    /**
     * Результат проверки произношения
     */
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

    /**
     * План тренировок
     */
    public static class TrainingPlan {
        private String level;
        private int durationWeeks;
        private List<WeekSchedule> schedule;
        private String finalGoal;

        // Getters and Setters
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public int getDurationWeeks() { return durationWeeks; }
        public void setDurationWeeks(int durationWeeks) { this.durationWeeks = durationWeeks; }

        public List<WeekSchedule> getSchedule() { return schedule; }
        public void setSchedule(List<WeekSchedule> schedule) { this.schedule = schedule; }

        public String getFinalGoal() { return finalGoal; }
        public void setFinalGoal(String finalGoal) { this.finalGoal = finalGoal; }
    }

    /**
     * Расписание на неделю
     */
    public static class WeekSchedule {
        private int weekNumber;
        private String[] focusPhonemes;
        private List<PronunciationExercise> dailyExercises;
        private String goals;

        // Getters and Setters
        public int getWeekNumber() { return weekNumber; }
        public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }

        public String[] getFocusPhonemes() { return focusPhonemes; }
        public void setFocusPhonemes(String[] focusPhonemes) { this.focusPhonemes = focusPhonemes; }

        public List<PronunciationExercise> getDailyExercises() { return dailyExercises; }
        public void setDailyExercises(List<PronunciationExercise> dailyExercises) { this.dailyExercises = dailyExercises; }

        public String getGoals() { return goals; }
        public void setGoals(String goals) { this.goals = goals; }
    }
}