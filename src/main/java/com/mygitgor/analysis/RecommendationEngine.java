package com.mygitgor.analysis;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class RecommendationEngine {
    private static final Logger logger = LoggerFactory.getLogger(RecommendationEngine.class);

    private static final int MAX_RECOMMENDATIONS_DEFAULT = 5;
    private static final int MAX_RECOMMENDATIONS_LOW_SCORE = 7;
    private static final int MAX_RECOMMENDATIONS_HIGH_SCORE = 3;
    private static final float WEAK_PHONEME_THRESHOLD = 70.0f;
    private static final float PHONEME_ATTENTION_THRESHOLD = 80.0f;
    private static final float LOW_SCORE_THRESHOLD = 60.0f;
    private static final float MEDIUM_SCORE_THRESHOLD = 75.0f;
    private static final float HIGH_SCORE_THRESHOLD = 85.0f;
    private static final float EXCELLENT_SCORE_THRESHOLD = 90.0f;
    private static final int ANALYSIS_TIMEOUT_SECONDS = 10;

    private final Map<String, List<RecommendationRule>> recommendationRules;
    private final Map<String, String> pronunciationTips;
    private final Map<String, List<String>> exerciseDatabase;

    private final AtomicBoolean isInitialized = new AtomicBoolean(true);
    private final AtomicReference<CompletableFuture<?>> currentOperation = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;

    public RecommendationEngine() {
        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();

        this.recommendationRules = initializeRecommendationRules();
        this.pronunciationTips = initializePronunciationTips();
        this.exerciseDatabase = initializeExerciseDatabase();

        logger.info("Инициализирован движок рекомендаций");
    }

    public List<PersonalizedRecommendation> generateRecommendations(EnhancedSpeechAnalysis analysis) {
        if (analysis == null) {
            logger.warn("Получен null анализ при генерации рекомендаций");
            return Collections.singletonList(createFallbackRecommendation());
        }

        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        try {
            logger.info("Генерация рекомендаций на основе анализа речи");

            recommendations.addAll(generatePronunciationRecommendations(analysis));
            recommendations.addAll(generateFluencyRecommendations(analysis));
            recommendations.addAll(generateIntonationRecommendations(analysis));
            recommendations.addAll(generateVolumeClarityRecommendations(analysis));
            recommendations.addAll(generatePhonemeBasedRecommendations(analysis));
            recommendations.addAll(generateGeneralRecommendations(analysis));

            recommendations = prioritizeRecommendations(recommendations, analysis);

            logger.info("Сгенерировано {} рекомендаций", recommendations.size());

        } catch (Exception e) {
            logger.error("Ошибка при генерации рекомендаций", e);
            recommendations.add(createFallbackRecommendation());
        }

        return recommendations;
    }

    public CompletableFuture<List<PersonalizedRecommendation>> generateRecommendationsAsync(EnhancedSpeechAnalysis analysis) {
        CompletableFuture<List<PersonalizedRecommendation>> future = CompletableFuture.supplyAsync(
                () -> generateRecommendations(analysis), backgroundExecutor);

        currentOperation.set(future);

        threadPoolManager.getScheduledExecutor().schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                logger.warn("Таймаут генерации рекомендаций");
            }
        }, ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return future;
    }

    public WeeklyLearningPlan generateWeeklyPlan(EnhancedSpeechAnalysis analysis) {
        if (analysis == null) {
            logger.warn("Получен null анализ при генерации плана");
            return createEmptyPlan();
        }

        WeeklyLearningPlan plan = new WeeklyLearningPlan();
        plan.setTargetLevel(analysis.getProficiencyLevel());
        plan.setExpectedImprovement(calculateExpectedImprovement(analysis));

        List<DailySchedule> schedule = createWeeklySchedule(analysis);
        plan.setSchedule(schedule);
        plan.setWeeklyGoal(formatWeeklyGoal(plan.getExpectedImprovement()));

        logger.info("Сгенерирован недельный план обучения");
        return plan;
    }

    public CompletableFuture<WeeklyLearningPlan> generateWeeklyPlanAsync(EnhancedSpeechAnalysis analysis) {
        return CompletableFuture.supplyAsync(() -> generateWeeklyPlan(analysis), backgroundExecutor);
    }

    private List<PersonalizedRecommendation> generatePronunciationRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float pronunciationScore = (float) analysis.getPronunciationScore();

        if (pronunciationScore < LOW_SCORE_THRESHOLD) {
            recommendations.add(createPronunciationCriticalRecommendation(analysis));
        } else if (pronunciationScore < MEDIUM_SCORE_THRESHOLD) {
            recommendations.add(createPronunciationImproveRecommendation(analysis));
        } else if (pronunciationScore < HIGH_SCORE_THRESHOLD) {
            recommendations.add(createPronunciationRefineRecommendation(analysis));
        }

        recommendations.addAll(generatePhonemeSpecificRecommendations(analysis));

        return recommendations;
    }

    private PersonalizedRecommendation createPronunciationCriticalRecommendation(EnhancedSpeechAnalysis analysis) {
        return new PersonalizedRecommendation(
                "pronunciation_critical",
                "🔴 Критическое улучшение произношения",
                formatCriticalDescription(analysis),
                "Высокий",
                generatePronunciationExercises(analysis, 3),
                90.0f
        );
    }

    private String formatCriticalDescription(EnhancedSpeechAnalysis analysis) {
        return "Ваше произношение нуждается в серьезной работе. Основные проблемы: " +
                (analysis.getDetectedErrors().isEmpty()
                        ? "недостаточная четкость речи"
                        : String.join(", ", analysis.getDetectedErrors()));
    }

    private PersonalizedRecommendation createPronunciationImproveRecommendation(EnhancedSpeechAnalysis analysis) {
        return new PersonalizedRecommendation(
                "pronunciation_improve",
                "🟡 Улучшение произношения",
                "Есть несколько звуков, которые нужно отработать. Обратите внимание на четкость артикуляции.",
                "Средний",
                generatePronunciationExercises(analysis, 2),
                75.0f
        );
    }

    private PersonalizedRecommendation createPronunciationRefineRecommendation(EnhancedSpeechAnalysis analysis) {
        return new PersonalizedRecommendation(
                "pronunciation_refine",
                "🟢 Оттачивание произношения",
                "Произношение хорошее, но можно сделать его еще лучше. Работайте над нюансами.",
                "Низкий",
                generatePronunciationExercises(analysis, 1),
                60.0f
        );
    }

    private List<PersonalizedRecommendation> generatePhonemeSpecificRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        if (analysis.getPhonemeScores().isEmpty()) {
            return recommendations;
        }

        analysis.getPhonemeScores().entrySet().stream()
                .filter(e -> e.getValue() < WEAK_PHONEME_THRESHOLD)
                .sorted(Map.Entry.comparingByValue())
                .limit(1)
                .findFirst()
                .ifPresent(entry -> {
                    String phoneme = entry.getKey();
                    recommendations.add(new PersonalizedRecommendation(
                            "phoneme_" + phoneme,
                            "🔊 Работа над звуком /" + phoneme + "/",
                            formatPhonemeDescription(phoneme, entry.getValue()),
                            "Высокий",
                            generatePhonemeExercises(phoneme),
                            85.0f
                    ));
                });

        return recommendations;
    }

    private String formatPhonemeDescription(String phoneme, Float score) {
        return String.format("Этот звук вызывает наибольшие трудности (оценка: %.1f). %s",
                score, pronunciationTips.getOrDefault(phoneme, "Практикуйте этот звук отдельно."));
    }

    private List<PersonalizedRecommendation> generateFluencyRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float fluencyScore = (float) analysis.getFluencyScore();

        if (fluencyScore < 65) {
            recommendations.add(createFluencyCriticalRecommendation());
        } else if (fluencyScore < 80) {
            recommendations.add(createFluencyImproveRecommendation(analysis));
        }

        if (analysis.getPauseCount() > 20) {
            recommendations.add(createPauseReductionRecommendation());
        }

        return recommendations;
    }

    private PersonalizedRecommendation createFluencyCriticalRecommendation() {
        return new PersonalizedRecommendation(
                "fluency_critical",
                "⚡ Критическое улучшение беглости",
                "Речь прерывистая, много пауз и колебаний. Нужно работать над автоматизацией речи.",
                "Высокий",
                Arrays.asList(
                        "Практикуйте скороговорки ежедневно по 5 минут",
                        "Читайте вслух тексты, постепенно увеличивая скорость",
                        "Записывайте и анализируйте свою речь"
                ),
                90.0f
        );
    }

    private PersonalizedRecommendation createFluencyImproveRecommendation(EnhancedSpeechAnalysis analysis) {
        String issue = determineFluencyIssue(analysis);
        return new PersonalizedRecommendation(
                "fluency_improve",
                "📈 Улучшение беглости речи",
                "Беглость можно улучшить. " + issue,
                "Средний",
                Arrays.asList(
                        "Практикуйте связную речь без подготовки",
                        "Используйте таймер для контроля скорости",
                        "Работайте над уменьшением пауз-заполнителей ('э-э', 'мм')"
                ),
                70.0f
        );
    }

    private String determineFluencyIssue(EnhancedSpeechAnalysis analysis) {
        if (analysis.getSpeakingRate() < 110) return "скорость речи слишком медленная";
        if (analysis.getSpeakingRate() > 190) return "скорость речи слишком быстрая";
        if (analysis.getPauseCount() > 15) return "слишком много пауз";
        return "есть небольшие проблемы с плавностью";
    }

    private PersonalizedRecommendation createPauseReductionRecommendation() {
        return new PersonalizedRecommendation(
                "pause_reduction",
                "⏸️ Сокращение количества пауз",
                "Слишком много пауз нарушает плавность речи. Старайтесь говорить более связно.",
                "Средний",
                Arrays.asList(
                        "Планируйте предложения перед произнесением",
                        "Используйте связующие слова (however, therefore, moreover)",
                        "Практикуйтесь с более короткими предложениями"
                ),
                65.0f
        );
    }

    private List<PersonalizedRecommendation> generateIntonationRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float intonationScore = analysis.getIntonationScore();

        if (intonationScore < 70) {
            recommendations.add(createIntonationBasicRecommendation());
        } else if (intonationScore < 85) {
            recommendations.add(createIntonationAdvancedRecommendation());
        }

        return recommendations;
    }

    private PersonalizedRecommendation createIntonationBasicRecommendation() {
        return new PersonalizedRecommendation(
                "intonation_basic",
                "🎵 Освоение базовой интонации",
                "Интонация монотонная. Нужно научиться использовать восходящий и нисходящий тоны.",
                "Средний",
                Arrays.asList(
                        "Слушайте и повторяйте за носителями, обращая внимание на интонацию",
                        "Практикуйте вопросы с восходящей интонацией",
                        "Читайте диалоги с разными эмоциональными окрасками"
                ),
                75.0f
        );
    }

    private PersonalizedRecommendation createIntonationAdvancedRecommendation() {
        return new PersonalizedRecommendation(
                "intonation_advanced",
                "🎭 Развитие продвинутой интонации",
                "Интонация хорошая, но можно добавить больше выразительности.",
                "Низкий",
                Arrays.asList(
                        "Экспериментируйте с эмоциональной окраской речи",
                        "Практикуйте разные стили речи (формальный, неформальный)",
                        "Анализируйте интонацию в фильмах и сериалах"
                ),
                60.0f
        );
    }

    private List<PersonalizedRecommendation> generateVolumeClarityRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        if (analysis.getVolumeScore() < 70) {
            recommendations.add(createVolumeImprovementRecommendation());
        }
        if (analysis.getClarityScore() < 70) {
            recommendations.add(createClarityImprovementRecommendation());
        }
        if (analysis.getConfidenceScore() < 70) {
            recommendations.add(createConfidenceBuildingRecommendation());
        }
        return recommendations;
    }

    private PersonalizedRecommendation createVolumeImprovementRecommendation() {
        return new PersonalizedRecommendation(
                "volume_improvement",
                "🔊 Улучшение громкости речи",
                "Речь слишком тихая. Говорите громче и увереннее.",
                "Средний",
                Arrays.asList(
                        "Тренируйтесь говорить перед зеркалом",
                        "Используйте диктофон для контроля громкости",
                        "Практикуйтесь в разных помещениях"
                ),
                70.0f
        );
    }

    private PersonalizedRecommendation createClarityImprovementRecommendation() {
        return new PersonalizedRecommendation(
                "clarity_improvement",
                "🗣️ Улучшение четкости речи",
                "Артикуляция недостаточно четкая. Работайте над произношением окончаний.",
                "Средний",
                Arrays.asList(
                        "Практикуйте артикуляционную гимнастику",
                        "Четко произносите окончания слов",
                        "Замедлите темп для лучшей артикуляции"
                ),
                75.0f
        );
    }

    private PersonalizedRecommendation createConfidenceBuildingRecommendation() {
        return new PersonalizedRecommendation(
                "confidence_building",
                "💪 Развитие уверенности в речи",
                "Речь звучит неуверенно. Работайте над уверенностью и убедительностью.",
                "Средний",
                Arrays.asList(
                        "Практикуйтесь в ситуациях низкого риска",
                        "Используйте позитивные утверждения перед речью",
                        "Работайте над языком тела и зрительным контактом"
                ),
                80.0f
        );
    }

    private List<PersonalizedRecommendation> generatePhonemeBasedRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();

        if (analysis.getPhonemeScores().isEmpty()) {
            return recommendations;
        }

        List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                .filter(e -> e.getValue() < PHONEME_ATTENTION_THRESHOLD)
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .toList();

        if (!weakPhonemes.isEmpty()) {
            recommendations.add(createWeakPhonemesRecommendation(weakPhonemes));
        }

        return recommendations;
    }

    private PersonalizedRecommendation createWeakPhonemesRecommendation(List<Map.Entry<String, Float>> weakPhonemes) {
        String phonemeList = weakPhonemes.stream()
                .map(entry -> "/" + entry.getKey() + "/")
                .collect(Collectors.joining(" "));

        return new PersonalizedRecommendation(
                "weak_phonemes",
                "🎯 Работа над проблемными звуками",
                "Следующие звуки требуют особого внимания: " + phonemeList,
                "Высокий",
                generateTargetedPhonemeExercises(weakPhonemes),
                85.0f
        );
    }

    private List<PersonalizedRecommendation> generateGeneralRecommendations(EnhancedSpeechAnalysis analysis) {
        List<PersonalizedRecommendation> recommendations = new ArrayList<>();
        float overallScore = (float) analysis.getOverallScore();

        recommendations.add(createMainRecommendation(overallScore));
        recommendations.add(createConsistencyTipRecommendation());

        return recommendations;
    }

    private PersonalizedRecommendation createMainRecommendation(float overallScore) {
        if (overallScore < LOW_SCORE_THRESHOLD) {
            return new PersonalizedRecommendation(
                    "foundation_building",
                    "🏗️ Построение фундамента",
                    "Нужно работать над базовыми аспектами речи. Регулярность - ключ к успеху.",
                    "Высокий",
                    Arrays.asList(
                            "Занимайтесь ежедневно по 15-20 минут",
                            "Начните с простых упражнений и постепенно усложняйте",
                            "Ведите дневник прогресса"
                    ),
                    95.0f
            );
        } else if (overallScore < MEDIUM_SCORE_THRESHOLD) {
            return new PersonalizedRecommendation(
                    "consistent_practice",
                    "📚 Последовательная практика",
                    "Хороший прогресс! Продолжайте регулярно практиковаться.",
                    "Средний",
                    Arrays.asList(
                            "Увеличьте время практики до 30 минут в день",
                            "Добавьте разнообразия в упражнения",
                            "Практикуйтесь в реальных ситуациях"
                    ),
                    70.0f
            );
        } else if (overallScore < EXCELLENT_SCORE_THRESHOLD) {
            return new PersonalizedRecommendation(
                    "advanced_refinement",
                    "✨ Продвинутое оттачивание",
                    "Отличные результаты! Работайте над тонкими аспектами речи.",
                    "Низкий",
                    Arrays.asList(
                            "Фокусируйтесь на идиомах и сложных конструкциях",
                            "Практикуйтесь с носителями языка",
                            "Работайте над акцентом и естественностью"
                    ),
                    60.0f
            );
        } else {
            return new PersonalizedRecommendation(
                    "mastery_maintenance",
                    "🏆 Поддержание мастерства",
                    "Превосходный уровень! Поддерживайте навыки и продолжайте развиваться.",
                    "Низкий",
                    Arrays.asList(
                            "Практикуйтесь в профессиональных контекстах",
                            "Обучайте других - лучший способ закрепить знания",
                            "Изучайте специализированную лексику"
                    ),
                    50.0f
            );
        }
    }

    private PersonalizedRecommendation createConsistencyTipRecommendation() {
        return new PersonalizedRecommendation(
                "consistency_tip",
                "⏰ Совет по регулярности",
                "Регулярные короткие занятия эффективнее редких длинных.",
                "Низкий",
                Collections.singletonList("Занимайтесь по 15-20 минут ежедневно"),
                40.0f
        );
    }

    private List<PersonalizedRecommendation> prioritizeRecommendations(
            List<PersonalizedRecommendation> recommendations,
            EnhancedSpeechAnalysis analysis) {

        recommendations.sort((r1, r2) -> {
            int priorityCompare = getPriorityValue(r2.getPriority()) - getPriorityValue(r1.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            return Float.compare(r2.getExpectedImprovement(), r1.getExpectedImprovement());
        });

        int maxRecommendations = calculateMaxRecommendations(analysis);
        return recommendations.stream()
                .limit(maxRecommendations)
                .collect(Collectors.toList());
    }

    private int calculateMaxRecommendations(EnhancedSpeechAnalysis analysis) {
        float score = (float) analysis.getOverallScore();
        if (score < 70) return MAX_RECOMMENDATIONS_LOW_SCORE;
        if (score > 85) return MAX_RECOMMENDATIONS_HIGH_SCORE;
        return MAX_RECOMMENDATIONS_DEFAULT;
    }

    private int getPriorityValue(String priority) {
        return switch (priority) {
            case "Высокий" -> 3;
            case "Средний" -> 2;
            case "Низкий" -> 1;
            default -> 0;
        };
    }

    private List<String> generatePronunciationExercises(EnhancedSpeechAnalysis analysis, int count) {
        List<String> exercises = new ArrayList<>(exerciseDatabase.getOrDefault("pronunciation", Collections.emptyList()));
        return exercises.stream().limit(count).collect(Collectors.toList());
    }

    private List<String> generatePhonemeExercises(String phoneme) {
        List<String> exercises = new ArrayList<>();
        String tip = pronunciationTips.getOrDefault(phoneme,
                "Практикуйте этот звук в разных позициях в слове.");

        exercises.add("📝 Техника для звука /" + phoneme + "/: " + tip);
        exercises.add("🔄 Повторяйте слова с /" + phoneme + "/ в начале, середине и конце");
        exercises.add("📖 Используйте /" + phoneme + "/ в предложениях");
        exercises.add("🎙️ Записывайте себя и сравнивайте с эталоном");

        return exercises;
    }

    private List<String> generateTargetedPhonemeExercises(List<Map.Entry<String, Float>> weakPhonemes) {
        List<String> exercises = new ArrayList<>();

        exercises.add("🎯 Сосредоточьтесь на следующих звуках:");
        for (Map.Entry<String, Float> entry : weakPhonemes) {
            String phoneme = entry.getKey();
            exercises.add("   • /" + phoneme + "/: " +
                    pronunciationTips.getOrDefault(phoneme, "практикуйте отдельно"));
        }

        exercises.add("🔄 Практикуйте эти звуки в контрастных парах");
        exercises.add("💬 Используйте их в спонтанной речи");

        return exercises;
    }

    private PersonalizedRecommendation createFallbackRecommendation() {
        return new PersonalizedRecommendation(
                "fallback",
                "📋 Общие рекомендации",
                "Продолжайте регулярно практиковаться. Слушайте носителей и повторяйте за ними.",
                "Средний",
                Arrays.asList(
                        "Занимайтесь ежедневно по 15 минут",
                        "Слушайте подкасты на английском",
                        "Практикуйтесь с разговорными партнерами"
                ),
                50.0f
        );
    }

    private WeeklyLearningPlan createEmptyPlan() {
        WeeklyLearningPlan plan = new WeeklyLearningPlan();
        plan.setTargetLevel("Не определен");
        plan.setExpectedImprovement(0.0f);
        plan.setWeeklyGoal("Нет данных для анализа");
        plan.setSchedule(Collections.emptyList());
        return plan;
    }

    private float calculateExpectedImprovement(EnhancedSpeechAnalysis analysis) {
        return (float) (100 - analysis.getOverallScore()) * 0.3f;
    }

    private String formatWeeklyGoal(float improvement) {
        return String.format("Улучшить общую оценку на %.1f пунктов", improvement);
    }

    private List<DailySchedule> createWeeklySchedule(EnhancedSpeechAnalysis analysis) {
        List<DailySchedule> schedule = new ArrayList<>();
        String[] days = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};

        List<PersonalizedRecommendation> recommendations = generateRecommendations(analysis);

        for (int i = 0; i < days.length; i++) {
            schedule.add(createDailySchedule(i, days[i], recommendations, analysis));
        }

        return schedule;
    }

    private DailySchedule createDailySchedule(int index, String day,
                                              List<PersonalizedRecommendation> recommendations,
                                              EnhancedSpeechAnalysis analysis) {
        DailySchedule schedule = new DailySchedule();
        schedule.setDay(day);
        schedule.setFocus(getDailyFocus(index, recommendations));
        schedule.setExercises(getDailyExercises(index, recommendations));
        schedule.setDurationMinutes(getDailyDuration(analysis, index));
        schedule.setTips(getDailyTips(index));
        return schedule;
    }

    private String getDailyFocus(int dayIndex, List<PersonalizedRecommendation> recommendations) {
        String[] focuses = {
                "🔊 Произношение", "⚡ Беглость", "🎵 Интонация",
                "🔊 Громкость и четкость", "💪 Уверенность",
                "🔄 Комплексная практика", "📚 Повторение и закрепление"
        };

        if (dayIndex < focuses.length) {
            return focuses[dayIndex];
        }

        if (!recommendations.isEmpty()) {
            int recIndex = dayIndex % recommendations.size();
            return recommendations.get(recIndex).getTitle();
        }

        return "📝 Общая практика";
    }

    private List<String> getDailyExercises(int dayIndex, List<PersonalizedRecommendation> recommendations) {
        List<String> exercises = new ArrayList<>();

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

        if (!recommendations.isEmpty() && dayIndex < recommendations.size()) {
            exercises.addAll(recommendations.get(dayIndex).getExercises());
        }

        return exercises;
    }

    private int getDailyDuration(EnhancedSpeechAnalysis analysis, int dayIndex) {
        float score = (float) analysis.getOverallScore();
        if (score < 60) return 30;
        if (score < 75) return 25;
        if (score < 85) return 20;
        return 15;
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

        return dayIndex < tips.length
                ? Collections.singletonList(tips[dayIndex])
                : Collections.singletonList("Регулярность важнее продолжительности");
    }

    private Map<String, List<RecommendationRule>> initializeRecommendationRules() {
        Map<String, List<RecommendationRule>> rules = new HashMap<>();

        rules.put("pronunciation", Arrays.asList(
                new RecommendationRule(0, 60, "Критическое улучшение произношения"),
                new RecommendationRule(60, 75, "Улучшение произношения"),
                new RecommendationRule(75, 85, "Оттачивание произношения"),
                new RecommendationRule(85, 100, "Поддержание произношения")
        ));

        rules.put("fluency", Arrays.asList(
                new RecommendationRule(0, 65, "Критическое улучшение беглости"),
                new RecommendationRule(65, 80, "Улучшение беглости"),
                new RecommendationRule(80, 90, "Развитие беглости"),
                new RecommendationRule(90, 100, "Естественная беглость")
        ));

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

    public static class PersonalizedRecommendation {
        private final String id;
        private final String title;
        private final String description;
        private final String priority;
        private final List<String> exercises;
        private final float expectedImprovement;

        public PersonalizedRecommendation(String id, String title, String description,
                                          String priority, List<String> exercises,
                                          float expectedImprovement) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.exercises = new ArrayList<>(exercises);
            this.expectedImprovement = expectedImprovement;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getPriority() { return priority; }
        public List<String> getExercises() { return Collections.unmodifiableList(exercises); }
        public float getExpectedImprovement() { return expectedImprovement; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (Приоритет: %s, Улучшение: %.1f%%)",
                    id, title, description, priority, expectedImprovement);
        }
    }

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

        public String getRecommendation() { return recommendation; }
    }

    public static class WeeklyLearningPlan {
        private String targetLevel;
        private float expectedImprovement;
        private List<DailySchedule> schedule;
        private String weeklyGoal;

        public String getTargetLevel() { return targetLevel; }
        public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }

        public float getExpectedImprovement() { return expectedImprovement; }
        public void setExpectedImprovement(float expectedImprovement) { this.expectedImprovement = expectedImprovement; }

        public List<DailySchedule> getSchedule() { return schedule != null ? Collections.unmodifiableList(schedule) : Collections.emptyList(); }
        public void setSchedule(List<DailySchedule> schedule) { this.schedule = new ArrayList<>(schedule); }

        public String getWeeklyGoal() { return weeklyGoal; }
        public void setWeeklyGoal(String weeklyGoal) { this.weeklyGoal = weeklyGoal; }

        public String getPlanSummary() {
            if (schedule == null || schedule.isEmpty()) {
                return "План не сгенерирован";
            }

            StringBuilder summary = new StringBuilder();
            summary.append("📅 НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ\n");
            summary.append("========================\n\n");
            summary.append("🎯 Целевой уровень: ").append(targetLevel).append("\n");
            summary.append("📊 Ожидаемое улучшение: ").append(String.format("%.1f", expectedImprovement)).append(" пунктов\n");
            summary.append("⭐ Цель недели: ").append(weeklyGoal).append("\n\n");

            summary.append("📋 РАСПИСАНИЕ:\n");
            for (DailySchedule day : schedule) {
                summary.append("\n").append(day.getSummary());
            }

            return summary.toString();
        }
    }

    public static class DailySchedule {
        private String day;
        private String focus;
        private List<String> exercises;
        private int durationMinutes;
        private List<String> tips;

        public String getDay() { return day; }
        public void setDay(String day) { this.day = day; }

        public String getFocus() { return focus; }
        public void setFocus(String focus) { this.focus = focus; }

        public List<String> getExercises() { return exercises != null ? Collections.unmodifiableList(exercises) : Collections.emptyList(); }
        public void setExercises(List<String> exercises) { this.exercises = new ArrayList<>(exercises); }

        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

        public List<String> getTips() { return tips != null ? Collections.unmodifiableList(tips) : Collections.emptyList(); }
        public void setTips(List<String> tips) { this.tips = new ArrayList<>(tips); }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("  ").append(day).append(":\n");
            sb.append("    🎯 Фокус: ").append(focus).append("\n");
            sb.append("    ⏱️ Длительность: ").append(durationMinutes).append(" мин\n");
            sb.append("    📝 Упражнения:\n");
            if (exercises != null) {
                for (String ex : exercises) {
                    sb.append("      • ").append(ex).append("\n");
                }
            }
            if (tips != null && !tips.isEmpty()) {
                sb.append("    💡 Совет: ").append(tips.get(0)).append("\n");
            }
            return sb.toString();
        }
    }
}