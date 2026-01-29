package com.mygitgor.analysis;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RecommendationEngine {
    private static final Logger logger = LoggerFactory.getLogger(RecommendationEngine.class);

    // База знаний рекомендаций
    private final Map<String, List<RecommendationRule>> recommendationRules;
    private final Map<String, String> pronunciationTips;
    private final Map<String, List<String>> exerciseDatabase;

    public RecommendationEngine() {
        this.recommendationRules = initializeRecommendationRules();
        this.pronunciationTips = initializePronunciationTips();
        this.exerciseDatabase = initializeExerciseDatabase();
        logger.info("Инициализирован движок рекомендаций");
    }

    /**
     * Генерация персонализированных рекомендаций на основе анализа
     */
    public List<PersonalizedRecommendation> generateRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        try {
            logger.info("Генерация рекомендаций на основе анализа речи");

            // 1. Рекомендации по произношению
            recommendations.addAll(generatePronunciationRecommendations(analysis));

            // 2. Рекомендации по беглости
            recommendations.addAll(generateFluencyRecommendations(analysis));

            // 3. Рекомендации по интонации
            recommendations.addAll(generateIntonationRecommendations(analysis));

            // 4. Рекомендации по громкости и четкости
            recommendations.addAll(generateVolumeClarityRecommendations(analysis));

            // 5. Рекомендации на основе фонем
            recommendations.addAll(generatePhonemeBasedRecommendations(analysis));

            // 6. Общие рекомендации
            recommendations.addAll(generateGeneralRecommendations(analysis));

            // 7. Приоритизация рекомендаций
            recommendations = prioritizeRecommendations(recommendations, analysis);

            logger.info("Сгенерировано {} рекомендаций", recommendations.size());

        } catch (Exception e) {
            logger.error("Ошибка при генерации рекомендаций", e);
            recommendations.add(createFallbackRecommendation());
        }

        return recommendations;
    }

    /**
     * Генерация рекомендаций по произношению
     */
    private List<PersonalizedRecommendation> generatePronunciationRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float pronunciationScore = (float) analysis.getPronunciationScore();

        if (pronunciationScore < 60) {
            recommendations.add(new PersonalizedRecommendation(
                    "pronunciation_critical",
                    "Критическое улучшение произношения",
                    "Ваше произношение нуждается в серьезной работе. Основные проблемы: " +
                            String.join(", ", analysis.getDetectedErrors()),
                    "Высокий",
                    generatePronunciationExercises(analysis, 3),
                    90
            ));
        } else if (pronunciationScore < 75) {
            recommendations.add(new PersonalizedRecommendation(
                    "pronunciation_improve",
                    "Улучшение произношения",
                    "Есть несколько звуков, которые нужно отработать. Обратите внимание на четкость артикуляции.",
                    "Средний",
                    generatePronunciationExercises(analysis, 2),
                    75
            ));
        } else if (pronunciationScore < 85) {
            recommendations.add(new PersonalizedRecommendation(
                    "pronunciation_refine",
                    "Оттачивание произношения",
                    "Произношение хорошее, но можно сделать его еще лучше. Работайте над нюансами.",
                    "Низкий",
                    generatePronunciationExercises(analysis, 1),
                    60
            ));
        }

        // Специфические рекомендации на основе фонем
        if (!analysis.getPhonemeScores().isEmpty()) {
            List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                    .filter(e -> e.getValue() < 70)
                    .sorted(Comparator.comparing(Map.Entry::getValue))
                    .toList();

            if (!weakPhonemes.isEmpty()) {
                String weakestPhoneme = weakPhonemes.get(0).getKey();
                recommendations.add(new PersonalizedRecommendation(
                        "phoneme_" + weakestPhoneme,
                        "Работа над звуком /" + weakestPhoneme + "/",
                        "Этот звук вызывает наибольшие трудности. " + pronunciationTips.getOrDefault(weakestPhoneme,
                                "Практикуйте этот звук отдельно."),
                        "Высокий",
                        generatePhonemeExercises(weakestPhoneme),
                        85
                ));
            }
        }

        return recommendations;
    }

    /**
     * Генерация рекомендаций по беглости
     */
    private List<PersonalizedRecommendation> generateFluencyRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float fluencyScore = (float) analysis.getFluencyScore();
        float speakingRate = analysis.getSpeakingRate();
        int pauseCount = analysis.getPauseCount();

        if (fluencyScore < 65) {
            recommendations.add(new PersonalizedRecommendation(
                    "fluency_critical",
                    "Критическое улучшение беглости",
                    "Речь прерывистая, много пауз и колебаний. Нужно работать над автоматизацией речи.",
                    "Высокий",
                    Arrays.asList(
                            "Практикуйте скороговорки ежедневно по 5 минут",
                            "Читайте вслух тексты, постепенно увеличивая скорость",
                            "Записывайте и анализируйте свою речь"
                    ),
                    90
            ));
        } else if (fluencyScore < 80) {
            String issue = "";
            if (speakingRate < 110) issue = "скорость речи слишком медленная";
            else if (speakingRate > 190) issue = "скорость речи слишком быстрая";
            else if (pauseCount > 15) issue = "слишком много пауз";

            recommendations.add(new PersonalizedRecommendation(
                    "fluency_improve",
                    "Улучшение беглости речи",
                    "Беглость можно улучшить. " + issue,
                    "Средний",
                    Arrays.asList(
                            "Практикуйте связную речь без подготовки",
                            "Используйте таймер для контроля скорости",
                            "Работайте над уменьшением пауз-заполнителей ('э-э', 'мм')"
                    ),
                    70
            ));
        }

        // Рекомендации по паузам
        if (pauseCount > 20) {
            recommendations.add(new PersonalizedRecommendation(
                    "pause_reduction",
                    "Сокращение количества пауз",
                    "Слишком много пауз нарушает плавность речи. Старайтесь говорить более связно.",
                    "Средний",
                    Arrays.asList(
                            "Планируйте предложения перед произнесением",
                            "Используйте связующие слова (however, therefore, moreover)",
                            "Практикуйтесь с более короткими предложениями"
                    ),
                    65
            ));
        }

        return recommendations;
    }

    /**
     * Генерация рекомендаций по интонации
     */
    private List<PersonalizedRecommendation> generateIntonationRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float intonationScore = analysis.getIntonationScore();

        if (intonationScore < 70) {
            recommendations.add(new PersonalizedRecommendation(
                    "intonation_basic",
                    "Освоение базовой интонации",
                    "Интонация монотонная. Нужно научиться использовать восходящий и нисходящий тоны.",
                    "Средний",
                    Arrays.asList(
                            "Слушайте и повторяйте за носителями, обращая внимание на интонацию",
                            "Практикуйте вопросы с восходящей интонацией",
                            "Читайте диалоги с разными эмоциональными окрасками"
                    ),
                    75
            ));
        } else if (intonationScore < 85) {
            recommendations.add(new PersonalizedRecommendation(
                    "intonation_advanced",
                    "Развитие продвинутой интонации",
                    "Интонация хорошая, но можно добавить больше выразительности.",
                    "Низкий",
                    Arrays.asList(
                            "Экспериментируйте с эмоциональной окраской речи",
                            "Практикуйте разные стили речи (формальный, неформальный)",
                            "Анализируйте интонацию в фильмах и сериалах"
                    ),
                    60
            ));
        }

        return recommendations;
    }

    /**
     * Генерация рекомендаций по громкости и четкости
     */
    private List<PersonalizedRecommendation> generateVolumeClarityRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float volumeScore = analysis.getVolumeScore();
        float clarityScore = analysis.getClarityScore();
        float confidenceScore = analysis.getConfidenceScore();

        if (volumeScore < 70) {
            recommendations.add(new PersonalizedRecommendation(
                    "volume_improvement",
                    "Улучшение громкости речи",
                    "Речь слишком тихая. Говорите громче и увереннее.",
                    "Средний",
                    Arrays.asList(
                            "Тренируйтесь говорить перед зеркалом",
                            "Используйте диктофон для контроля громкости",
                            "Практикуйтесь в разных помещениях"
                    ),
                    70
            ));
        }

        if (clarityScore < 70) {
            recommendations.add(new PersonalizedRecommendation(
                    "clarity_improvement",
                    "Улучшение четкости речи",
                    "Артикуляция недостаточно четкая. Работайте над произношением окончаний.",
                    "Средний",
                    Arrays.asList(
                            "Практикуйте артикуляционную гимнастику",
                            "Четко произносите окончания слов",
                            "Замедлите темп для лучшей артикуляции"
                    ),
                    75
            ));
        }

        if (confidenceScore < 70) {
            recommendations.add(new PersonalizedRecommendation(
                    "confidence_building",
                    "Развитие уверенности в речи",
                    "Речь звучит неуверенно. Работайте над уверенностью и убедительностью.",
                    "Средний",
                    Arrays.asList(
                            "Практикуйтесь в ситуациях низкого риска",
                            "Используйте позитивные утверждения перед речью",
                            "Работайте над языком тела и зрительным контактом"
                    ),
                    80
            ));
        }

        return recommendations;
    }

    /**
     * Генерация рекомендаций на основе анализа фонем
     */
    private List<PersonalizedRecommendation> generatePhonemeBasedRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        if (!analysis.getPhonemeScores().isEmpty()) {
            // Находим 3 самые слабые фонемы
            List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                    .filter(e -> e.getValue() < 80)
                    .sorted(Comparator.comparing(Map.Entry::getValue))
                    .limit(3)
                    .toList();

            if (!weakPhonemes.isEmpty()) {
                StringBuilder phonemeList = new StringBuilder();
                for (Map.Entry<String, Float> entry : weakPhonemes) {
                    phonemeList.append("/").append(entry.getKey()).append("/ ");
                }

                recommendations.add(new PersonalizedRecommendation(
                        "weak_phonemes",
                        "Работа над проблемными звуками",
                        "Следующие звуки требуют особого внимания: " + phonemeList.toString(),
                        "Высокий",
                        generateTargetedPhonemeExercises(weakPhonemes),
                        85
                ));
            }
        }

        return recommendations;
    }

    /**
     * Генерация общих рекомендаций
     */
    private List<PersonalizedRecommendation> generateGeneralRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float overallScore = (float) analysis.getOverallScore();

        if (overallScore < 60) {
            recommendations.add(new PersonalizedRecommendation(
                    "foundation_building",
                    "Построение фундамента",
                    "Нужно работать над базовыми аспектами речи. Регулярность - ключ к успеху.",
                    "Высокий",
                    Arrays.asList(
                            "Занимайтесь ежедневно по 15-20 минут",
                            "Начните с простых упражнений и постепенно усложняйте",
                            "Ведите дневник прогресса"
                    ),
                    95
            ));
        } else if (overallScore < 75) {
            recommendations.add(new PersonalizedRecommendation(
                    "consistent_practice",
                    "Последовательная практика",
                    "Хороший прогресс! Продолжайте регулярно практиковаться.",
                    "Средний",
                    Arrays.asList(
                            "Увеличьте время практики до 30 минут в день",
                            "Добавьте разнообразия в упражнения",
                            "Практикуйтесь в реальных ситуациях"
                    ),
                    70
            ));
        } else if (overallScore < 90) {
            recommendations.add(new PersonalizedRecommendation(
                    "advanced_refinement",
                    "Продвинутое оттачивание",
                    "Отличные результаты! Работайте над тонкими аспектами речи.",
                    "Низкий",
                    Arrays.asList(
                            "Фокусируйтесь на идиомах и сложных конструкциях",
                            "Практикуйтесь с носителями языка",
                            "Работайте над акцентом и естественностью"
                    ),
                    60
            ));
        } else {
            recommendations.add(new PersonalizedRecommendation(
                    "mastery_maintenance",
                    "Поддержание мастерства",
                    "Превосходный уровень! Поддерживайте навыки и продолжайте развиваться.",
                    "Низкий",
                    Arrays.asList(
                            "Практикуйтесь в профессиональных контекстах",
                            "Обучайте других - лучший способ закрепить знания",
                            "Изучайте специализированную лексику"
                    ),
                    50
            ));
        }

        // Рекомендация по регулярности
        recommendations.add(new PersonalizedRecommendation(
                "consistency_tip",
                "Совет по регулярности",
                "Регулярные короткие занятия эффективнее редких длинных.",
                "Низкий",
                Arrays.asList("Занимайтесь по 15-20 минут ежедневно"),
                40
        ));

        return recommendations;
    }

    /**
     * Приоритизация рекомендаций
     */
    private List<PersonalizedRecommendation> prioritizeRecommendations(
            List<PersonalizedRecommendation> recommendations,
            EnhancedSpeechAnalysis analysis) {

        // Сортируем по приоритету и эффективности
        recommendations.sort((r1, r2) -> {
            int priorityCompare = getPriorityValue(r2.getPriority()) - getPriorityValue(r1.getPriority());
            if (priorityCompare != 0) return priorityCompare;

            return Float.compare(r2.getExpectedImprovement(), r1.getExpectedImprovement());
        });

        // Ограничиваем количество рекомендаций
        int maxRecommendations = 5;
        if (analysis.getOverallScore() < 70) maxRecommendations = 7;
        else if (analysis.getOverallScore() > 85) maxRecommendations = 3;

        return recommendations.stream()
                .limit(maxRecommendations)
                .toList();
    }

    private int getPriorityValue(String priority) {
        return switch (priority) {
            case "Высокий" -> 3;
            case "Средний" -> 2;
            case "Низкий" -> 1;
            default -> 0;
        };
    }

    /**
     * Генерация упражнений для произношения
     */
    private List<String> generatePronunciationExercises(EnhancedSpeechAnalysis analysis, int count) {
        List<String> exercises = new ArrayList<>();

        String[] baseExercises = {
                "Повторяйте минимальные пары (ship/sheep, bad/bed)",
                "Записывайте и сравнивайте свою речь с образцом",
                "Практикуйте артикуляцию перед зеркалом",
                "Читайте скороговорки, обращая внимание на проблемные звуки",
                "Слушайте и повторяйте за носителями с замедлением"
        };

        for (int i = 0; i < Math.min(count, baseExercises.length); i++) {
            exercises.add(baseExercises[i]);
        }

        return exercises;
    }

    /**
     * Генерация упражнений для конкретной фонемы
     */
    private List<String> generatePhonemeExercises(String phoneme) {
        List<String> exercises = new ArrayList<>();
        String tip = pronunciationTips.getOrDefault(phoneme,
                "Практикуйте этот звук в разных позициях в слове.");

        exercises.add("Техника для звука /" + phoneme + "/: " + tip);
        exercises.add("Повторяйте слова с /" + phoneme + "/ в начале, середине и конце");
        exercises.add("Используйте /" + phoneme + "/ в предложениях");
        exercises.add("Записывайте себя и сравнивайте с эталоном");

        return exercises;
    }

    /**
     * Генерация целевых упражнений для фонем
     */
    private List<String> generateTargetedPhonemeExercises(List<Map.Entry<String, Float>> weakPhonemes) {
        List<String> exercises = new ArrayList<>();

        exercises.add("Сосредоточьтесь на следующих звуках:");
        for (Map.Entry<String, Float> entry : weakPhonemes) {
            String phoneme = entry.getKey();
            exercises.add("  • /" + phoneme + "/: " +
                    pronunciationTips.getOrDefault(phoneme, "практикуйте отдельно"));
        }

        exercises.add("Практикуйте эти звуки в контрастных парах");
        exercises.add("Используйте их в спонтанной речи");

        return exercises;
    }

    /**
     * Создание запасной рекомендации при ошибке
     */
    private PersonalizedRecommendation createFallbackRecommendation() {
        return new PersonalizedRecommendation(
                "fallback",
                "Общие рекомендации",
                "Продолжайте регулярно практиковаться. Слушайте носителей и повторяйте за ними.",
                "Средний",
                Arrays.asList(
                        "Занимайтесь ежедневно по 15 минут",
                        "Слушайте подкасты на английском",
                        "Практикуйтесь с разговорными партнерами"
                ),
                50
        );
    }

    /**
     * Генерация плана обучения на неделю
     */
    public WeeklyLearningPlan generateWeeklyPlan(EnhancedSpeechAnalysis analysis) {
        WeeklyLearningPlan plan = new WeeklyLearningPlan();
        plan.setTargetLevel(analysis.getProficiencyLevel());
        plan.setExpectedImprovement((float) (100 - analysis.getOverallScore()) * 0.3f);

        List<DailySchedule> schedule = new ArrayList<>();
        String[] days = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};

        List<PersonalizedRecommendation> recommendations = generateRecommendations(analysis);

        for (int i = 0; i < 7; i++) {
            DailySchedule daySchedule = new DailySchedule();
            daySchedule.setDay(days[i]);
            daySchedule.setFocus(getDailyFocus(i, recommendations));
            daySchedule.setExercises(getDailyExercises(i, recommendations));
            daySchedule.setDurationMinutes(getDailyDuration(analysis, i));
            daySchedule.setTips(getDailyTips(i));

            schedule.add(daySchedule);
        }

        plan.setSchedule(schedule);
        plan.setWeeklyGoal("Улучшить общую оценку на " +
                String.format("%.1f", plan.getExpectedImprovement()) + " пунктов");

        logger.info("Сгенерирован недельный план обучения");
        return plan;
    }

    private String getDailyFocus(int dayIndex, List<PersonalizedRecommendation> recommendations) {
        String[] focuses = {"Произношение", "Беглость", "Интонация", "Громкость и четкость",
                "Уверенность", "Комплексная практика", "Повторение и закрепление"};

        if (dayIndex < focuses.length) {
            return focuses[dayIndex];
        }

        // Используем рекомендации для фокуса
        if (!recommendations.isEmpty()) {
            int recIndex = dayIndex % recommendations.size();
            return recommendations.get(recIndex).getTitle();
        }

        return "Общая практика";
    }

    private List<String> getDailyExercises(int dayIndex, List<PersonalizedRecommendation> recommendations) {
        List<String> exercises = new ArrayList<>();

        // Базовые упражнения для каждого дня
        String[][] dailyExercises = {
                {"Артикуляционная гимнастика", "Практика проблемных звуков", "Скороговорки"},
                {"Чтение вслух", "Пересказ текстов", "Упражнения на связность"},
                {"Интонационные паттерны", "Эмоциональное чтение", "Вопросы и ответы"},
                {"Контроль громкости", "Четкость артикуляции", "Дикция"},
                {"Уверенная речь", "Публичное выступление", "Преодоление волнения"},
                {"Диалоги", "Ситуативные упражнения", "Комплексная практика"},
                {"Повторение пройденного", "Тестирование", "Самоанализ"}
        };

        if (dayIndex < dailyExercises.length) {
            exercises.addAll(Arrays.asList(dailyExercises[dayIndex]));
        }

        // Добавляем рекомендации
        if (!recommendations.isEmpty() && dayIndex < recommendations.size()) {
            exercises.addAll(recommendations.get(dayIndex).getExercises());
        }

        return exercises;
    }

    private int getDailyDuration(EnhancedSpeechAnalysis analysis, int dayIndex) {
        float score = (float) analysis.getOverallScore();

        if (score < 60) return 30; // Новичкам больше времени
        if (score < 75) return 25;
        if (score < 85) return 20;
        return 15; // Продвинутым меньше, но регулярно
    }

    private List<String> getDailyTips(int dayIndex) {
        String[] tips = {
                "Начните с разминки речевого аппарата",
                "Сосредоточьтесь на плавности, а не скорости",
                "Экспериментируйте с разной интонацией",
                "Обращайте внимание на обратную связь от записей",
                "Визуализируйте успех перед практикой",
                "Используйте разнообразные материалы",
                "Отмечайте свой прогресс"
        };

        if (dayIndex < tips.length) {
            return Collections.singletonList(tips[dayIndex]);
        }

        return Collections.singletonList("Регулярность важнее продолжительности");
    }

    // ====================== ИНИЦИАЛИЗАЦИЯ БАЗЫ ЗНАНИЙ ======================

    private Map<String, List<RecommendationRule>> initializeRecommendationRules() {
        Map<String, List<RecommendationRule>> rules = new HashMap<>();

        // Правила для произношения
        rules.put("pronunciation", Arrays.asList(
                new RecommendationRule(0, 60, "Критическое улучшение произношения"),
                new RecommendationRule(60, 75, "Улучшение произношения"),
                new RecommendationRule(75, 85, "Оттачивание произношения"),
                new RecommendationRule(85, 100, "Поддержание произношения")
        ));

        // Правила для беглости
        rules.put("fluency", Arrays.asList(
                new RecommendationRule(0, 65, "Критическое улучшение беглости"),
                new RecommendationRule(65, 80, "Улучшение беглости"),
                new RecommendationRule(80, 90, "Развитие беглости"),
                new RecommendationRule(90, 100, "Естественная беглость")
        ));

        // Правила для интонации
        rules.put("intonation", Arrays.asList(
                new RecommendationRule(0, 70, "Освоение базовой интонации"),
                new RecommendationRule(70, 85, "Развитие интонации"),
                new RecommendationRule(85, 95, "Продвинутая интонация"),
                new RecommendationRule(95, 100, "Выразительная интонация")
        ));

        return rules;
    }

    private Map<String, String> initializePronunciationTips() {
        Map<String, String> tips = new HashMap<>();

        tips.put("θ", "Кончик языка между зубами, воздух проходит через щель. Практикуйте в словах 'think', 'thought'.");
        tips.put("ð", "Как /θ/, но с голосом. Язык между зубами, чувствуйте вибрацию. Слова: 'this', 'that', 'there'.");
        tips.put("r", "Кончик языка не касается нёба, оттянут назад. Губы немного округлены. Не вибрируйте как в русском.");
        tips.put("æ", "Рот открыт шире, чем для русского 'э'. Язык лежит плоско. Звук короткий и открытый. Слово: 'cat'.");
        tips.put("ɪ", "Короткий звук, язык чуть ниже, чем для русского 'и'. Не растягивайте. Слово: 'sit'.");
        tips.put("iː", "Долгий звук, углы губ растянуты. Слово: 'see'.");
        tips.put("ʃ", "Шипящий звук, язык поднят к нёбу. Слово: 'she'.");
        tips.put("w", "Губы округлены и выдвинуты вперед. Слово: 'we'.");
        tips.put("v", "Нижняя губа касается верхних зубов. Слово: 'very'.");
        tips.put("p", "Сильный взрывной звук. Слово: 'pen'.");
        tips.put("b", "Как /p/, но с голосом. Слово: 'big'.");
        tips.put("t", "Кончик языка у альвеол. Слово: 'tea'.");
        tips.put("d", "Как /t/, но с голосом. Слово: 'day'.");

        return tips;
    }

    private Map<String, List<String>> initializeExerciseDatabase() {
        Map<String, List<String>> exercises = new HashMap<>();

        exercises.put("pronunciation", Arrays.asList(
                "Минимальные пары (ship/sheep, bad/bed)",
                "Артикуляционная гимнастика",
                "Повторение за носителями",
                "Скороговорки",
                "Запись и анализ своей речи"
        ));

        exercises.put("fluency", Arrays.asList(
                "Чтение вслух с таймером",
                "Пересказ текстов",
                "Импровизированные речи",
                "Связующие слова практика",
                "Уменьшение пауз-заполнителей"
        ));

        exercises.put("intonation", Arrays.asList(
                "Воспроизведение интонационных паттернов",
                "Эмоциональное чтение",
                "Диалоги с разной интонацией",
                "Вопросы с восходящим тоном",
                "Анализ интонации в фильмах"
        ));

        exercises.put("confidence", Arrays.asList(
                "Публичные выступления (малой аудитории)",
                "Позитивные утверждения",
                "Язык тела и зрительный контакт",
                "Медитация перед речью",
                "Постепенное увеличение сложности"
        ));

        return exercises;
    }

    // ====================== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ======================

    /**
     * Персонализированная рекомендация
     */
    public static class PersonalizedRecommendation {
        private final String id;
        private final String title;
        private final String description;
        private final String priority; // Высокий, Средний, Низкий
        private final List<String> exercises;
        private final float expectedImprovement; // Ожидаемое улучшение в %

        public PersonalizedRecommendation(String id, String title, String description,
                                          String priority, List<String> exercises,
                                          float expectedImprovement) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.exercises = exercises;
            this.expectedImprovement = expectedImprovement;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getPriority() { return priority; }
        public List<String> getExercises() { return exercises; }
        public float getExpectedImprovement() { return expectedImprovement; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (Приоритет: %s)",
                    id, title, description, priority);
        }
    }

    /**
     * Правило рекомендации
     */
    private static class RecommendationRule {
        private final float minScore;
        private final float maxScore;
        private final String recommendation;

        public RecommendationRule(float minScore, float maxScore, String recommendation) {
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.recommendation = recommendation;
        }

        public boolean matches(float score) {
            return score >= minScore && score < maxScore;
        }

        public String getRecommendation() {
            return recommendation;
        }
    }

    /**
     * Недельный план обучения
     */
    public static class WeeklyLearningPlan {
        private String targetLevel;
        private float expectedImprovement;
        private List<DailySchedule> schedule;
        private String weeklyGoal;

        // Getters and Setters
        public String getTargetLevel() { return targetLevel; }
        public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }

        public float getExpectedImprovement() { return expectedImprovement; }
        public void setExpectedImprovement(float expectedImprovement) { this.expectedImprovement = expectedImprovement; }

        public List<DailySchedule> getSchedule() { return schedule; }
        public void setSchedule(List<DailySchedule> schedule) { this.schedule = schedule; }

        public String getWeeklyGoal() { return weeklyGoal; }
        public void setWeeklyGoal(String weeklyGoal) { this.weeklyGoal = weeklyGoal; }

        public String getPlanSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ\n");
            summary.append("=====================\n\n");
            summary.append("Целевой уровень: ").append(targetLevel).append("\n");
            summary.append("Ожидаемое улучшение: ").append(String.format("%.1f", expectedImprovement)).append(" пунктов\n");
            summary.append("Цель недели: ").append(weeklyGoal).append("\n\n");

            summary.append("РАСПИСАНИЕ:\n");
            for (DailySchedule day : schedule) {
                summary.append(day.getDay()).append(":\n");
                summary.append("  Фокус: ").append(day.getFocus()).append("\n");
                summary.append("  Длительность: ").append(day.getDurationMinutes()).append(" мин\n");
                summary.append("  Упражнения: ").append(String.join(", ", day.getExercises())).append("\n");
                if (!day.getTips().isEmpty()) {
                    summary.append("  Совет: ").append(day.getTips().get(0)).append("\n");
                }
                summary.append("\n");
            }

            return summary.toString();
        }
    }

    /**
     * Расписание на день
     */
    public static class DailySchedule {
        private String day;
        private String focus;
        private List<String> exercises;
        private int durationMinutes;
        private List<String> tips;

        // Getters and Setters
        public String getDay() { return day; }
        public void setDay(String day) { this.day = day; }

        public String getFocus() { return focus; }
        public void setFocus(String focus) { this.focus = focus; }

        public List<String> getExercises() { return exercises; }
        public void setExercises(List<String> exercises) { this.exercises = exercises; }

        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

        public List<String> getTips() { return tips; }
        public void setTips(List<String> tips) { this.tips = tips; }
    }

    /**
     * Тестирование движка рекомендаций
     */
    public static void main(String[] args) {
        RecommendationEngine engine = new RecommendationEngine();

        // Создаем тестовый анализ
        EnhancedSpeechAnalysis testAnalysis = new EnhancedSpeechAnalysis();
        testAnalysis.setPronunciationScore(65.0f);
        testAnalysis.setFluencyScore(70.0f);
        testAnalysis.setIntonationScore(60.0f);
        testAnalysis.setVolumeScore(75.0f);
        testAnalysis.setClarityScore(68.0f);
        testAnalysis.setConfidenceScore(62.0f);
        testAnalysis.setSpeakingRate(140.0f);
        testAnalysis.setPauseCount(25);
        testAnalysis.setOverallScore(67.0f);
        testAnalysis.setProficiencyLevel("Начинающий");

        // Добавляем демо-фонемы
        testAnalysis.addPhonemeScore("θ", 55.0f);
        testAnalysis.addPhonemeScore("r", 65.0f);
        testAnalysis.addPhonemeScore("æ", 70.0f);

        // Добавляем ошибки
        testAnalysis.addDetectedError("Замена 'th' на 's'");
        testAnalysis.addDetectedError("Много пауз-заполнителей");

        // Генерация рекомендаций
        List<PersonalizedRecommendation> recommendations = engine.generateRecommendations(testAnalysis);

        System.out.println("=== ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ ===");
        for (PersonalizedRecommendation rec : recommendations) {
            System.out.println("\n" + rec.getTitle());
            System.out.println("Приоритет: " + rec.getPriority());
            System.out.println("Описание: " + rec.getDescription());
            System.out.println("Упражнения:");
            for (String exercise : rec.getExercises()) {
                System.out.println("  • " + exercise);
            }
        }

        // Генерация недельного плана
        WeeklyLearningPlan weeklyPlan = engine.generateWeeklyPlan(testAnalysis);
        System.out.println("\n\n=== НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ ===");
        System.out.println(weeklyPlan.getPlanSummary());
    }
}
