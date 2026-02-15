package com.mygitgor.ai;

import com.mygitgor.ai.strategy.core.LearningMode;
import com.mygitgor.ai.strategy.LearningModeStrategy;
import com.mygitgor.ai.strategy.type.*;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.service.AudioAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LearningStrategyFactory {
    private static final Logger logger = LoggerFactory.getLogger(LearningStrategyFactory.class);

    private final AiService aiService;
    private final PronunciationTrainer pronunciationTrainer;
    private final AudioAnalyzer audioAnalyzer;

    private final Map<LearningMode, LearningModeStrategy> strategyCache = new ConcurrentHashMap<>();

    public LearningStrategyFactory(AiService aiService,
                                   PronunciationTrainer pronunciationTrainer,
                                   AudioAnalyzer audioAnalyzer) {
        this.aiService = aiService;
        this.pronunciationTrainer = pronunciationTrainer;
        this.audioAnalyzer = audioAnalyzer;

        initializeStrategies();
    }

    private void initializeStrategies() {
        for (LearningMode mode : LearningMode.values()) {
            getStrategy(mode);
        }

        logger.info("LearningStrategyFactory инициализирован с {} стратегиями",
                strategyCache.size());
    }

    public LearningModeStrategy getStrategy(LearningMode mode) {
        return strategyCache.computeIfAbsent(mode, this::createStrategy);
    }

    private LearningModeStrategy createStrategy(LearningMode mode) {
        logger.debug("Создание стратегии для режима: {}", mode);

        return switch (mode) {
            case CONVERSATION -> new ConversationStrategy(aiService);
            case PRONUNCIATION -> new PronunciationStrategy(pronunciationTrainer, audioAnalyzer);
            case GRAMMAR -> new GrammarStrategy(aiService);
            case VOCABULARY -> new VocabularyStrategy(aiService);
            case EXERCISE -> new ExerciseStrategy(aiService);
            case WRITING -> new WritingStrategy(aiService);
            case LISTENING -> new ListeningStrategy(aiService);
        };
    }

    public boolean isStrategyAvailable(LearningMode mode) {
        LearningModeStrategy strategy = strategyCache.get(mode);
        return strategy != null && strategy.isSupported();
    }

    public Map<LearningMode, LearningModeStrategy> getAvailableStrategies() {
        Map<LearningMode, LearningModeStrategy> available = new ConcurrentHashMap<>();
        for (LearningMode mode : LearningMode.values()) {
            LearningModeStrategy strategy = getStrategy(mode);
            if (strategy.isSupported()) {
                available.put(mode, strategy);
            }
        }
        return available;
    }

    public void clearCache() {
        strategyCache.clear();
        initializeStrategies();
        logger.info("Кэш стратегий очищен");
    }
}