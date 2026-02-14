package com.mygitgor.ai;

import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MockAiService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(MockAiService.class);

    private static final int MIN_PRONUNCIATION_SCORE = 70;
    private static final int MAX_PRONUNCIATION_SCORE = 95;
    private static final int MIN_FLUENCY_SCORE = 65;
    private static final int MAX_FLUENCY_SCORE = 90;
    private static final int MIN_GRAMMAR_SCORE = 75;
    private static final int MAX_GRAMMAR_SCORE = 98;
    private static final int MIN_VOCABULARY_SCORE = 72;
    private static final int MAX_VOCABULARY_SCORE = 96;
    private static final int SIMULATION_DELAY_MS = 100;
    private static final int MAX_HISTORY_SIZE = 100;

    private final AtomicBoolean isAvailable = new AtomicBoolean(true);
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;

    private final String[][] pronunciationTips = {
            {"th", "Кончик языка между зубами, воздух проходит через щель"},
            {"r", "Кончик языка не касается нёба, оттянут назад"},
            {"æ", "Рот открыт шире, чем для русского 'э'"},
            {"ʃ", "Шипящий звук, язык поднят к нёбу"},
            {"w", "Губы округлены и выдвинуты вперед"}
    };

    private final String[][] grammarTips = {
            {"Present Perfect", "Have/has + past participle - для действий, связанных с настоящим"},
            {"Conditionals", "If + past, would + infinitive - для воображаемых ситуаций"},
            {"Passive Voice", "Be + past participle - когда действие важнее исполнителя"}
    };

    public MockAiService() {
        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();
        logger.info("Инициализирован MockAiService (демо-режим)");
    }

    public CompletableFuture<String> analyzeTextAsync(String text) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            simulateWork();
            String result = analyzeText(text);
            recordMetrics(startTime);
            return result;
        }, backgroundExecutor);
    }

    public CompletableFuture<SpeechAnalysis> analyzePronunciationAsync(String text, String audioPath) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            simulateWork();
            SpeechAnalysis result = analyzePronunciation(text, audioPath);
            recordMetrics(startTime);
            return result;
        }, backgroundExecutor);
    }

    public CompletableFuture<String> generateBotResponseAsync(String userMessage, SpeechAnalysis analysis) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            simulateWork();
            String result = generateBotResponse(userMessage, analysis);
            recordMetrics(startTime);
            return result;
        }, backgroundExecutor);
    }

    public CompletableFuture<String> generateExerciseAsync(String topic, String difficulty) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            simulateWork();
            String result = generateExercise(topic, difficulty);
            recordMetrics(startTime);
            return result;
        }, backgroundExecutor);
    }

    @Override
    public String analyzeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Получен пустой текст для анализа");
            return getEmptyTextResponse();
        }

        int requestId = requestCounter.incrementAndGet();
        logger.info("[{}] Мок-анализ текста: {}", requestId, truncateText(text));

        return generateAnalysisResponse(text, requestId);
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        int requestId = requestCounter.incrementAndGet();
        logger.info("[{}] Мок-анализ произношения для текста: {}", requestId, truncateText(text));

        SpeechAnalysis analysis = new SpeechAnalysis();
        analysis.setText(text);
        analysis.setAudioPath(audioPath);

        populateAnalysisWithMockData(analysis, requestId);

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        int requestId = requestCounter.incrementAndGet();
        logger.info("[{}] Мок-генерация ответа для: {}", requestId, truncateText(userMessage));

        return generateBotResponseMessage(userMessage, analysis, requestId);
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        int requestId = requestCounter.incrementAndGet();
        logger.info("[{}] Мок-генерация упражнения: тема={}, уровень={}",
                requestId, topic, difficulty);

        return generateExerciseContent(topic, difficulty, requestId);
    }

    @Override
    public boolean isAvailable() {
        return isAvailable.get();
    }

    private String generateAnalysisResponse(String text, int requestId) {
        return String.format("""
                ## 📊 Анализ вашего текста (демо-режим) [ID: %d]
                
                **Исходный текст:** "%s"
                
                ### 📝 Грамматический анализ:
                • ✅ Предложение грамматически правильное
                • 📌 Хороший выбор временных форм
                • 🔍 Обратите внимание на артикли
                
                ### 💡 Предложения по улучшению:
                1. Попробуйте использовать более сложные конструкции
                2. Добавьте вводные слова для связности
                3. Экспериментируйте с разными временами
                
                ### 🔄 Альтернативные варианты:
                • "That's an excellent suggestion!"
                • "I fully support your perspective"
                • "Your point is well-taken"
                
                ### 🎯 Рекомендации:
                Практикуйте использование идиом и фразовых глаголов для более естественной речи.
                Обратите внимание на произношение окончаний -ed в прошедшем времени.
                
                *✨ Демо-режим: для персонализированного анализа настройте API ключ*
                """, requestId, text);
    }

    private void populateAnalysisWithMockData(SpeechAnalysis analysis, int requestId) {
        double pronunciationScore = generateScore(MIN_PRONUNCIATION_SCORE, MAX_PRONUNCIATION_SCORE);
        double fluencyScore = generateScore(MIN_FLUENCY_SCORE, MAX_FLUENCY_SCORE);
        double grammarScore = generateScore(MIN_GRAMMAR_SCORE, MAX_GRAMMAR_SCORE);
        double vocabularyScore = generateScore(MIN_VOCABULARY_SCORE, MAX_VOCABULARY_SCORE);

        analysis.setPronunciationScore(pronunciationScore);
        analysis.setFluencyScore(fluencyScore);
        analysis.setGrammarScore(grammarScore);
        analysis.setVocabularyScore(vocabularyScore);

        addMockErrors(analysis, pronunciationScore, fluencyScore);

        addMockRecommendations(analysis, pronunciationScore, grammarScore, vocabularyScore);

        logger.debug("[{}] Сгенерированы оценки: произн={:.1f}, бегл={:.1f}, грамм={:.1f}, слов={:.1f}",
                requestId, pronunciationScore, fluencyScore, grammarScore, vocabularyScore);
    }

    private double generateScore(int min, int max) {
        return min + (Math.random() * (max - min));
    }

    private void addMockErrors(SpeechAnalysis analysis, double pronunciationScore, double fluencyScore) {
        if (pronunciationScore < 80) {
            String[] thWords = {"the", "that", "think", "thought"};
            String randomWord = thWords[(int)(Math.random() * thWords.length)];
            analysis.addError(String.format("Слабые звуки 'th' в слове '%s'", randomWord));
        }

        if (pronunciationScore < 75) {
            analysis.addError("Нечеткое произношение окончаний -ed и -s");
        }

        if (fluencyScore < 75) {
            analysis.addError("Много пауз-заполнителей ('э-э', 'мм')");
        }

        if (Math.random() > 0.7) {
            analysis.addError("Интонация в вопросах нуждается в улучшении");
        }
    }

    private void addMockRecommendations(SpeechAnalysis analysis, double pronunciationScore,
                                        double grammarScore, double vocabularyScore) {
        if (pronunciationScore < 85) {
            int tipIndex = (int)(Math.random() * pronunciationTips.length);
            String[] tip = pronunciationTips[tipIndex];
            analysis.addRecommendation(String.format("🔊 Звук /%s/: %s", tip[0], tip[1]));

            analysis.addRecommendation("Практикуйте минимальные пары: 'ship' vs 'sheep', 'live' vs 'leave'");
        }

        if (grammarScore < 85) {
            int tipIndex = (int)(Math.random() * grammarTips.length);
            String[] tip = grammarTips[tipIndex];
            analysis.addRecommendation(String.format("📚 %s: %s", tip[0], tip[1]));
        }

        analysis.addRecommendation("🎧 Слушайте подкасты на английском ежедневно по 15 минут");
        analysis.addRecommendation("📝 Ведите дневник на английском");

        if (vocabularyScore < 80) {
            analysis.addRecommendation("📖 Учите по 10 новых слов каждый день");
        }

        analysis.addRecommendation("🗣️ Используйте технику 'shadowing' для улучшения произношения");
    }

    private String generateBotResponseMessage(String userMessage, SpeechAnalysis analysis, int requestId) {
        String strengths = generateStrengths(analysis);
        String improvements = generateImprovements(analysis);
        String tip = generateDailyTip();

        return String.format("""
                👋 Привет! Спасибо за ваше сообщение: "%s"
                
                ### 📊 Я проанализировал вашу речь [ID: %d]
                
                **💪 Сильные стороны:**
                %s
                
                **🎯 Области для улучшения:**
                %s
                
                **✨ Совет на сегодня:**
                %s
                
                **📝 Следующий шаг:**
                Попробуйте использовать новые слова и конструкции из нашего обсуждения.
                Запишите себя и проанализируйте прогресс через неделю!
                
                Давайте продолжим практиковаться! Какая тема вас интересует сегодня?
                
                *💡 Демо-режим: ответы генерируются по шаблону*
                """,
                truncateText(userMessage, 50),
                requestId,
                strengths,
                improvements,
                tip);
    }

    private String generateStrengths(SpeechAnalysis analysis) {
        List<String> strengths = new ArrayList<>();

        if (analysis != null) {
            if (analysis.getVocabularyScore() > 80) {
                strengths.add("• ✅ Хороший словарный запас");
            }
            if (analysis.getGrammarScore() > 80) {
                strengths.add("• ✅ Грамматически правильные предложения");
            }
            if (analysis.getFluencyScore() > 75) {
                strengths.add("• ✅ Достаточно беглая речь");
            }
        }

        if (strengths.isEmpty()) {
            strengths.add("• 📝 Четкая структура предложений");
            strengths.add("• 💪 Уверенное использование базовой лексики");
        }

        strengths.add("• 🔄 Хорошая вовлеченность в диалог");

        return String.join("\n", strengths);
    }

    private String generateImprovements(SpeechAnalysis analysis) {
        List<String> improvements = new ArrayList<>();

        if (analysis != null) {
            if (analysis.getPronunciationScore() < 80) {
                improvements.add("• 🔊 Произношение некоторых звуков");
            }
            if (analysis.getFluencyScore() < 75) {
                improvements.add("• ⚡ Темп речи (можно немного быстрее)");
            }
        }

        improvements.add("• 🎯 Работа над интонацией в вопросах");
        improvements.add("• 📚 Расширение словарного запаса по темам");

        return String.join("\n", improvements);
    }

    private String generateDailyTip() {
        String[] tips = {
                "Говорите медленнее и четче произносите окончания слов",
                "Слушайте аудиокниги и повторяйте за диктором",
                "Ведите дневник на английском - это улучшит письменную речь",
                "Смотрите фильмы с английскими субтитрами",
                "Практикуйте скороговорки для улучшения дикции",
                "Общайтесь с носителями языка в языковых клубах",
                "Используйте новые слова в разных контекстах"
        };

        return tips[(int)(Math.random() * tips.length)];
    }

    private String generateExerciseContent(String topic, String difficulty, int requestId) {
        String levelEmoji = getLevelEmoji(difficulty);
        String[] exercises = generateExercisesForTopic(topic, difficulty);

        return String.format("""
                ## 🎯 Упражнение: %s %s
                **Уровень:** %s %s
                **ID:** %d
                
                ### 📖 Часть 1: Объяснение
                Тема "%s" важна для развития %s речи. 
                Она поможет вам %s.
                
                ### ✍️ Часть 2: Практические задания
                %s
                
                ### 💡 Часть 3: Примеры
                • ✅ Правильно: "I have been learning English for 5 years"
                • ❌ Неправильно: "I am learn English 5 years"
                • ✅ Правильно: "She suggested that we should start earlier"
                • ❌ Неправильно: "She suggested that we start earlier"
                
                ### 📝 Часть 4: Дополнительные упражнения
                1. Составьте 5 своих предложений
                2. Переведите эти предложения на английский
                3. Составьте диалог с партнером
                
                Удачи в выполнении упражнения! 🌟
                """,
                topic,
                getTopicEmoji(topic),
                difficulty,
                levelEmoji,
                requestId,
                topic,
                getLevelDescription(difficulty),
                getGoalForTopic(topic),
                formatExercises(exercises));
    }

    private String getLevelEmoji(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "beginner" -> "🌱";
            case "intermediate" -> "🌿";
            case "advanced" -> "🌳";
            default -> "📚";
        };
    }

    private String getTopicEmoji(String topic) {
        return switch (topic.toLowerCase()) {
            case "present perfect" -> "⏰";
            case "conditionals" -> "🔄";
            case "phrasal verbs" -> "🔗";
            case "idioms" -> "🎭";
            default -> "📌";
        };
    }

    private String getLevelDescription(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "beginner" -> "начинающего";
            case "intermediate" -> "среднего";
            case "advanced" -> "продвинутого";
            default -> "любого";
        };
    }

    private String getGoalForTopic(String topic) {
        return switch (topic.toLowerCase()) {
            case "present perfect" -> "связывать прошлое с настоящим";
            case "conditionals" -> "говорить о воображаемых ситуациях";
            case "phrasal verbs" -> "звучать более естественно";
            default -> "улучшить грамматику";
        };
    }

    private String[] generateExercisesForTopic(String topic, String difficulty) {
        int count = difficulty.equals("beginner") ? 3 : 5;
        String[] exercises = new String[count];

        for (int i = 0; i < count; i++) {
            exercises[i] = String.format("Задание %d: %s", i + 1,
                    getExerciseDescription(topic, difficulty, i));
        }

        return exercises;
    }

    private String getExerciseDescription(String topic, String difficulty, int index) {
        String[] templates = {
                "Составьте %d предложений, используя %s",
                "Перефразируйте следующие предложения, используя %s",
                "Найдите ошибки в тексте и исправьте их",
                "Ответьте на вопросы, используя %s",
                "Напишите короткий диалог с использованием %s"
        };

        return String.format(templates[index % templates.length],
                index + 3, topic);
    }

    private String formatExercises(String[] exercises) {
        StringBuilder sb = new StringBuilder();
        for (String exercise : exercises) {
            sb.append("   ").append(exercise).append("\n");
        }
        return sb.toString();
    }

    private String getEmptyTextResponse() {
        return """
                ### ⚠️ Пустой текст
                
                Пожалуйста, введите текст для анализа.
                
                Я могу помочь с:
                • 📝 Грамматическим анализом
                • 🔄 Улучшением формулировок
                • 📚 Расширением словарного запаса
                
                Напишите что-нибудь, и я сразу приступлю к анализу!
                """;
    }

    private String truncateText(String text) {
        return truncateText(text, 50);
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private void simulateWork() {
        try {
            Thread.sleep(SIMULATION_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordMetrics(long startTime) {
        long responseTime = System.currentTimeMillis() - startTime;
        totalResponseTime.addAndGet(responseTime);

        if (responseTime > 1000) {
            logger.warn("Медленный ответ: {} мс", responseTime);
        }
    }

    public void setAvailable(boolean available) {
        boolean wasAvailable = isAvailable.getAndSet(available);
        if (wasAvailable != available) {
            logger.info("MockAiService доступность изменена: {} -> {}", wasAvailable, available);
        }
    }

    public String getStats() {
        return String.format("""
                MockAiService Statistics:
                • Всего запросов: %d
                • Среднее время ответа: %.1f мс
                • Ошибок: %d
                • Доступен: %s
                """,
                requestCounter.get(),
                requestCounter.get() > 0 ?
                        (double) totalResponseTime.get() / requestCounter.get() : 0,
                errorCount.get(),
                isAvailable.get() ? "✅" : "❌"
        );
    }

    public void resetStats() {
        requestCounter.set(0);
        totalResponseTime.set(0);
        errorCount.set(0);
        logger.info("Статистика MockAiService сброшена");
    }
}