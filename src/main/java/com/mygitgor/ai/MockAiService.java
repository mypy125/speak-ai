package com.mygitgor.ai;

import com.mygitgor.model.SpeechAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockAiService implements AiService {
    private static final Logger logger = LoggerFactory.getLogger(MockAiService.class);

    @Override
    public String analyzeText(String text) {
        logger.info("Мок-анализ текста: {}", text);

        return String.format("""
            ## Анализ вашего текста:
            
            **Исходный текст:** "%s"
            
            ### Коррекция ошибок:
            • Предложение грамматически правильное
            • Хороший выбор слов
            
            ### Предложения по улучшению:
            1. Попробуйте использовать более сложные конструкции
            2. Обратите внимание на использование артиклей
            
            ### Альтернативные варианты:
            • "That's a great idea!"
            • "I completely agree with your point"
            
            ### Рекомендации:
            Практикуйте использование идиом и фразовых глаголов для более естественной речи.
            """, text);
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        logger.info("Мок-анализ произношения для текста: {}", text);

        SpeechAnalysis analysis = new SpeechAnalysis();
        analysis.setText(text);
        analysis.setAudioPath(audioPath);

        // Демонстрационные данные
        analysis.setPronunciationScore(82.5);
        analysis.setFluencyScore(78.0);
        analysis.setGrammarScore(88.0);
        analysis.setVocabularyScore(85.0);

        analysis.addError("Слабые звуки 'th' в словах 'the' и 'that'");
        analysis.addError("Интонация в вопросах нуждается в улучшении");

        analysis.addRecommendation("Практикуйте минимальные пары: 'ship' vs 'sheep'");
        analysis.addRecommendation("Слушайте носителей языка и повторяйте за ними");
        analysis.addRecommendation("Используйте приложения для тренировки произношения");

        return analysis;
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        return String.format("""
            Привет! Спасибо за ваше сообщение: "%s"
            
            Я проанализировал вашу речь и вот что заметил:
            
            **Сильные стороны:**
            • Хороший словарный запас
            • Грамматически правильные предложения
            
            **Области для улучшения:**
            • Произношение некоторых звуков
            • Темп речи
            
            **Совет на сегодня:**
            Попробуйте говорить медленнее и четче произносить окончания слов.
            
            Давайте продолжим практиковаться! Какая тема вас интересует сегодня?
            """, userMessage);
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        return String.format("""
            ## Упражнение: %s
            **Уровень:** %s
            
            ### Часть 1: Объяснение
            %s - важная тема в английском языке. Она помогает выражать...
            
            ### Часть 2: Практика
            1. Составьте 3 предложения используя...
            2. Перефразируйте следующие предложения...
            3. Найдите ошибки в тексте...
            4. Ответьте на вопросы...
            5. Напишите короткий диалог...
            
            ### Часть 3: Примеры
            • Пример 1: ...
            • Пример 2: ...
            
            Удачи в выполнении упражнения!
            """, topic, difficulty, topic);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}