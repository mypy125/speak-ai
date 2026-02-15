package com.mygitgor.chatbot.components;

import com.mygitgor.ai.LearningStrategyFactory;
import com.mygitgor.ai.strategy.core.*;
import com.mygitgor.ai.strategy.LearningModeStrategy;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class LearningModeManager {
    private static final Logger logger = LoggerFactory.getLogger(LearningModeManager.class);

    private final LearningStrategyFactory strategyFactory;
    private final ExecutorService executor;

    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    private static class UserSession {
        final String userId;
        LearningMode currentMode;
        final Map<LearningMode, Double> modeProgress = new ConcurrentHashMap<>();
        EnhancedSpeechAnalysis lastAnalysis;
        LearningContext lastContext;

        UserSession(String userId) {
            this.userId = userId;
            this.currentMode = LearningMode.CONVERSATION; // По умолчанию
        }
    }

    public LearningModeManager(LearningStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
        this.executor = ThreadPoolManager.getInstance().getBackgroundExecutor();
        logger.info("LearningModeManager инициализирован");
    }

    public CompletableFuture<LearningResponse> processInput(String userId, String userInput,
                                                            Optional<String> audioPath) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("processInput: userId is null or empty");
            return CompletableFuture.completedFuture(
                    LearningResponse.builder()
                            .message("Ошибка: идентификатор пользователя не определен")
                            .progress(0)
                            .build()
            );
        }

        if (userInput == null) {
            logger.error("processInput: userInput is null for user {}", userId);
            return CompletableFuture.completedFuture(
                    LearningResponse.builder()
                            .message("Ошибка: пустой ввод")
                            .progress(0)
                            .build()
            );
        }

        UserSession session = userSessions.computeIfAbsent(userId, k -> {
            logger.debug("Создана новая сессия для пользователя {}", userId);
            return new UserSession(userId);
        });

        LearningModeStrategy strategy;
        try {
            strategy = strategyFactory.getStrategy(session.currentMode);
        } catch (Exception e) {
            logger.error("Ошибка получения стратегии для режима {}", session.currentMode, e);
            return CompletableFuture.completedFuture(
                    LearningResponse.builder()
                            .message("Ошибка: не удалось загрузить режим обучения")
                            .progress(0)
                            .build()
            );
        }

        LearningContext context = buildContext(session);

        return strategy.processInput(userInput, context)
                .thenApply(response -> {
                    updateSession(session, response);
                    return response;
                })
                .exceptionally(throwable -> {
                    logger.error("Ошибка обработки ввода для пользователя {}", userId, throwable);
                    return LearningResponse.builder()
                            .message("Извините, произошла ошибка при обработке запроса: " + throwable.getMessage())
                            .progress(getCurrentProgress(session))
                            .build();
                });
    }

    public CompletableFuture<LearningResponse> switchMode(String userId, LearningMode newMode) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("switchMode: userId is null or empty");
            return CompletableFuture.completedFuture(
                    LearningResponse.builder()
                            .message("Ошибка: идентификатор пользователя не определен")
                            .progress(0)
                            .build()
            );
        }

        if (newMode == null) {
            logger.error("switchMode: newMode is null for user {}", userId);
            return CompletableFuture.completedFuture(
                    LearningResponse.builder()
                            .message("Ошибка: режим обучения не указан")
                            .progress(0)
                            .build()
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            UserSession session = userSessions.computeIfAbsent(userId, k -> {
                logger.debug("Создана новая сессия для пользователя {}", userId);
                return new UserSession(userId);
            });

            LearningMode oldMode = session.currentMode;
            session.currentMode = newMode;

            logger.info("Пользователь {} сменил режим: {} -> {}",
                    userId, oldMode, newMode);

            LearningModeStrategy strategy;
            try {
                strategy = strategyFactory.getStrategy(newMode);
            } catch (Exception e) {
                logger.error("Ошибка получения стратегии для режима {}", newMode, e);
                return LearningResponse.builder()
                        .message("Ошибка: не удалось загрузить режим " + newMode)
                        .progress(0)
                        .build();
            }

            LearningContext context = buildContext(session);

            try {
                LearningTask task = strategy.getNextTask(context).join();
                return LearningResponse.builder()
                        .message(String.format("Переключено на режим: %s\n\n%s",
                                newMode.getDisplayName(), task.getDescription()))
                        .nextMode(newMode)
                        .nextTask(task)
                        .progress(session.modeProgress.getOrDefault(newMode, 0.0))
                        .build();
            } catch (Exception e) {
                logger.error("Ошибка при получении следующего задания для режима {}", newMode, e);
                return LearningResponse.builder()
                        .message(String.format("Переключено на режим: %s", newMode.getDisplayName()))
                        .nextMode(newMode)
                        .progress(session.modeProgress.getOrDefault(newMode, 0.0))
                        .build();
            }
        }, executor);
    }

    public CompletableFuture<LearningMode> getRecommendedMode(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("getRecommendedMode: userId is null or empty");
            return CompletableFuture.completedFuture(LearningMode.CONVERSATION);
        }

        return CompletableFuture.supplyAsync(() -> {
            UserSession session = userSessions.get(userId);
            if (session == null) return LearningMode.CONVERSATION;

            Map<LearningMode, Double> progress = session.modeProgress;

            return progress.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(LearningMode.CONVERSATION);
        }, executor);
    }

    public CompletableFuture<Map<LearningMode, Double>> analyzeOverallProgress(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("analyzeOverallProgress: userId is null or empty");
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        return CompletableFuture.supplyAsync(() -> {
            UserSession session = userSessions.get(userId);
            if (session == null) return Collections.emptyMap();

            Map<LearningMode, Double> overallProgress = new ConcurrentHashMap<>();

            for (LearningMode mode : LearningMode.values()) {
                try {
                    LearningModeStrategy strategy = strategyFactory.getStrategy(mode);
                    if (strategy.isSupported()) {
                        LearningContext context = buildContext(session);
                        double progress = strategy.analyzeProgress(context)
                                .thenApply(LearningProgress::getOverallProgress)
                                .join();
                        overallProgress.put(mode, progress);
                    }
                } catch (Exception e) {
                    logger.warn("Ошибка при анализе прогресса для режима {}", mode, e);
                }
            }

            return overallProgress;
        }, executor);
    }

    public CompletableFuture<LearningTask> getNextTask(String userId, LearningMode mode) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("getNextTask: userId is null or empty");
            return CompletableFuture.completedFuture(createEmptyTask());
        }

        if (mode == null) {
            logger.error("getNextTask: mode is null for user {}", userId);
            return CompletableFuture.completedFuture(createEmptyTask());
        }

        UserSession session = userSessions.get(userId);
        if (session == null) {
            session = new UserSession(userId);
            userSessions.put(userId, session);
        }

        try {
            LearningModeStrategy strategy = strategyFactory.getStrategy(mode);
            LearningContext context = buildContext(session);
            return strategy.getNextTask(context);
        } catch (Exception e) {
            logger.error("Ошибка при получении следующего задания для пользователя {}", userId, e);
            return CompletableFuture.completedFuture(createEmptyTask());
        }
    }

    public void updateSpeechAnalysis(String userId, EnhancedSpeechAnalysis analysis) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("updateSpeechAnalysis: userId is null or empty");
            return;
        }

        UserSession session = userSessions.computeIfAbsent(userId, k -> new UserSession(userId));
        session.lastAnalysis = analysis;
    }

    private LearningContext buildContext(UserSession session) {
        if (session == null) {
            return LearningContext.builder()
                    .userId("unknown")
                    .mode(LearningMode.CONVERSATION)
                    .currentLevel(50.0)
                    .build();
        }

        return LearningContext.builder()
                .userId(session.userId)
                .mode(session.currentMode)
                .currentLevel(calculateOverallLevel(session))
                .lastAnalysis(session.lastAnalysis)
                .sessionCount(getSessionCount(session.userId))
                .sessionDuration(getSessionDuration(session.userId))
                .build();
    }

    private double calculateOverallLevel(UserSession session) {
        if (session == null || session.modeProgress.isEmpty()) return 50.0;
        return session.modeProgress.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(50.0);
    }

    private void updateSession(UserSession session, LearningResponse response) {
        if (session == null || response == null) return;

        session.modeProgress.put(session.currentMode, response.getProgress());
        session.lastContext = LearningContext.builder()
                .userId(session.userId)
                .mode(session.currentMode)
                .currentLevel(calculateOverallLevel(session))
                .lastAnalysis(session.lastAnalysis)
                .build();
    }

    private double getCurrentProgress(UserSession session) {
        if (session == null) return 0;
        return session.modeProgress.getOrDefault(session.currentMode, 0.0);
    }

    private int getSessionCount(String userId) {
        // В реальном приложении здесь будет получение из БД
        return 1;
    }

    private long getSessionDuration(String userId) {
        // В реальном приложении здесь будет получение из БД
        return 300000; // 5 минут
    }

    private LearningTask createEmptyTask() {
        return LearningTask.builder()
                .id("empty_" + System.currentTimeMillis())
                .title("Задание недоступно")
                .description("Не удалось загрузить задание. Попробуйте позже.")
                .mode(LearningMode.CONVERSATION)
                .difficulty(LearningTask.DifficultyLevel.BEGINNER)
                .build();
    }

    /**
     * Очистка сессии пользователя
     */
    public void clearUserSession(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("clearUserSession: userId is null or empty");
            return;
        }

        userSessions.remove(userId);
        logger.info("Сессия пользователя {} очищена", userId);
    }

    public String getModeStats(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "Нет данных: пользователь не указан";
        }

        UserSession session = userSessions.get(userId);
        if (session == null) return "Нет данных для пользователя " + userId;

        StringBuilder stats = new StringBuilder("Статистика по режимам:\n");
        session.modeProgress.forEach((mode, progress) ->
                stats.append(String.format("• %s: %.1f%%\n",
                        mode.getDisplayName(), progress)));

        return stats.toString();
    }
}