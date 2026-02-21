package com.mygitgor.chatbot.components;

import com.mygitgor.model.*;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.model.core.LearningSession;
import com.mygitgor.ai.LearningStrategyFactory;
import com.mygitgor.ai.learning.LearningModeStrategy;
import com.mygitgor.model.core.User;
import com.mygitgor.repository.DAO.LearningSessionDao;
import com.mygitgor.service.components.LearningProgressManager;
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
    private final LearningProgressManager progressManager;
    private final LearningSessionDao sessionDao;
    private final ExecutorService executor;

    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    private static class UserSession {
        final String userId;
        LearningMode currentMode;
        LearningSession currentDbSession;
        final Map<LearningMode, Double> modeProgress = new ConcurrentHashMap<>();
        LearningContext lastContext;
        Date sessionStart;

        UserSession(String userId) {
            this.userId = userId;
            this.currentMode = LearningMode.CONVERSATION;
            this.sessionStart = new Date();
        }
    }

    public LearningModeManager(LearningStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
        this.progressManager = new LearningProgressManager();
        this.sessionDao = new LearningSessionDao();
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

        UserSession session = getUserSession(userId);

        if (session.currentDbSession != null) {
            session.currentDbSession.incrementMessages();
            updateSessionAsync(session.currentDbSession);
        }

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
                    updateSessionAfterInput(session, response, userInput);
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
            UserSession session = getUserSession(userId);

            LearningMode oldMode = session.currentMode;
            session.currentMode = newMode;

            if (session.currentDbSession != null) {
                session.currentDbSession.setCurrentMode(newMode.name());
                updateSessionAsync(session.currentDbSession);
            }

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

                saveModeSwitch(session, oldMode, newMode);

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

    private UserSession getUserSession(String userId) {
        return userSessions.computeIfAbsent(userId, k -> {
            logger.debug("Создана новая сессия для пользователя {}", userId);
            UserSession session = new UserSession(userId);

            loadUserProgress(session);
            loadOrCreateDbSession(session);
            return session;
        });
    }

    private void loadOrCreateDbSession(UserSession session) {
        try {
            User user = new User();
            user.setId(Integer.parseInt(session.userId));

            LearningSession activeSession = sessionDao.getActiveSession(user);

            if (activeSession == null) {
                activeSession = LearningSession.builder()
                        .user(user)
                        .currentMode(session.currentMode)
                        .currentLevel(calculateOverallLevel(session))
                        .build();

                sessionDao.createSession(activeSession);
                logger.info("Создана новая сессия в БД для пользователя {}", session.userId);
            } else {
                logger.debug("Загружена активная сессия для пользователя {}: id={}, mode={}",
                        session.userId, activeSession.getId(), activeSession.getCurrentMode());

                session.currentMode = activeSession.getCurrentLearningMode();
            }
            session.currentDbSession = activeSession;

        } catch (Exception e) {
            logger.error("Ошибка при загрузке/создании сессии в БД для пользователя {}", session.userId, e);
        }
    }

    private void updateSessionAsync(LearningSession dbSession) {
        CompletableFuture.runAsync(() -> {
            try {
                sessionDao.updateSession(dbSession);
            } catch (Exception e) {
                logger.error("Ошибка при обновлении сессии в БД", e);
            }
        }, executor);
    }

    private void loadUserProgress(UserSession session) {
        try {
            User user = new User();
            user.setId(Integer.parseInt(session.userId));

            Map<LearningMode, LearningProgress> progresses =
                    progressManager.getUserProgress(user);

            for (Map.Entry<LearningMode, LearningProgress> entry : progresses.entrySet()) {
                session.modeProgress.put(entry.getKey(), entry.getValue().getOverallProgress());
            }

            logger.debug("Загружен прогресс для пользователя {}: {} режимов",
                    session.userId, session.modeProgress.size());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке прогресса пользователя {}", session.userId, e);
        }
    }

    private void updateSessionAfterInput(UserSession session, LearningResponse response, String userInput) {
        session.modeProgress.put(session.currentMode, response.getProgress());
        session.lastContext = buildContext(session);

        if (session.currentDbSession != null) {
            session.currentDbSession.setSessionProgress(response.getProgress());
            session.currentDbSession.setCurrentLevel(calculateOverallLevel(session));
            updateSessionAsync(session.currentDbSession);
        }
        saveProgressAsync(session, response);
        updateSessionStats(session, userInput);
    }

    private void saveProgressAsync(UserSession session, LearningResponse response) {
        CompletableFuture.runAsync(() -> {
            try {
                User user = new User();
                user.setId(Integer.parseInt(session.userId));

                LearningProgress progress = progressManager.getProgress(user, session.currentMode);

                LearningProgress updated = LearningProgress.builder()
                        .user(user)
                        .learningMode(session.currentMode)
                        .overallProgress(response.getProgress())
                        .tasksCompleted(progress.getTasksCompleted() + 1)
                        .timeSpent(progress.getTimeSpent() + 60)
                        .startDate(progress.getStartLocalDate())
                        .build();

                progress.getSkillsProgress().forEach(updated::updateSkillProgress);
                progress.getAchievements().forEach(updated::addAchievement);

                progressManager.saveProgress(updated);

                logger.debug("Прогресс сохранен для пользователя {} в режиме {}",
                        session.userId, session.currentMode);
            } catch (Exception e) {
                logger.error("Ошибка при сохранении прогресса", e);
            }
        }, executor);
    }

    private void saveModeSwitch(UserSession session, LearningMode oldMode, LearningMode newMode) {
        CompletableFuture.runAsync(() -> {
            try {
                User user = new User();
                user.setId(Integer.parseInt(session.userId));

                LearningProgress progress = progressManager.getProgress(user, newMode);
                if (!progress.hasAchievement("Первый режим")) {
                    progressManager.addAchievement(user, newMode,
                            "🎯 Начато изучение режима " + newMode.getDisplayName());
                }

                logger.debug("Смена режима сохранена: {} -> {} для пользователя {}",
                        oldMode, newMode, session.userId);
            } catch (Exception e) {
                logger.error("Ошибка при сохранении смены режима", e);
            }
        });
    }

    private void updateSessionStats(UserSession session, String userInput) {
        // TODO: update statistics for the current session
    }

    public CompletableFuture<LearningMode> getRecommendedMode(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("getRecommendedMode: userId is null or empty");
            return CompletableFuture.completedFuture(LearningMode.CONVERSATION);
        }

        return CompletableFuture.supplyAsync(() -> {
            UserSession session = userSessions.get(userId);
            if (session == null) return LearningMode.CONVERSATION;

            return session.modeProgress.entrySet().stream()
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

            return new HashMap<>(session.modeProgress);
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
        UserSession session = getUserSession(userId);

        try {
            LearningModeStrategy strategy = strategyFactory.getStrategy(mode);
            LearningContext context = buildContext(session);
            return strategy.getNextTask(context);
        } catch (Exception e) {
            logger.error("Ошибка при получении следующего задания для пользователя {}", userId, e);
            return CompletableFuture.completedFuture(createEmptyTask());
        }
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
                .sessionCount(session.modeProgress.size())
                .sessionDuration(getSessionDuration(session))
                .build();
    }

    private double calculateOverallLevel(UserSession session) {
        if (session == null || session.modeProgress.isEmpty()) return 50.0;

        return session.modeProgress.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(50.0);
    }

    private long getSessionDuration(UserSession session) {
        if (session == null || session.sessionStart == null) return 0;
        return (System.currentTimeMillis() - session.sessionStart.getTime()) / 1000;
    }

    private double getCurrentProgress(UserSession session) {
        if (session == null) return 0;
        return session.modeProgress.getOrDefault(session.currentMode, 0.0);
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

    public void clearUserSession(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("clearUserSession: userId is null or empty");
            return;
        }

        UserSession session = userSessions.remove(userId);
        if (session != null) {
            if (session.currentDbSession != null) {
                session.currentDbSession.endSession();
                updateSessionAsync(session.currentDbSession);
                logger.info("Сессия в БД завершена для пользователя {}", userId);
            }
            saveFinalProgress(session);
            logger.info("Сессия пользователя {} очищена", userId);
        }
    }

    private void saveFinalProgress(UserSession session) {
        CompletableFuture.runAsync(() -> {
            try {
                User user = new User();
                user.setId(Integer.parseInt(session.userId));

                for (Map.Entry<LearningMode, Double> entry : session.modeProgress.entrySet()) {
                    LearningProgress progress = progressManager.getProgress(user, entry.getKey());

                    LearningProgress updated = LearningProgress.builder()
                            .user(user)
                            .learningMode(entry.getKey())
                            .overallProgress(entry.getValue())
                            .tasksCompleted(progress.getTasksCompleted())
                            .timeSpent(progress.getTimeSpent() + getSessionDuration(session))
                            .startDate(progress.getStartLocalDate())
                            .build();

                    progress.getSkillsProgress().forEach(updated::updateSkillProgress);
                    progress.getAchievements().forEach(updated::addAchievement);
                    progressManager.saveProgress(updated);
                }

                logger.debug("Финальный прогресс сохранен для пользователя {}", session.userId);
            } catch (Exception e) {
                logger.error("Ошибка при сохранении финального прогресса", e);
            }
        });
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

    public CompletableFuture<String> getDetailedStats(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return CompletableFuture.completedFuture("Нет данных");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = new User();
                user.setId(Integer.parseInt(userId));

                LearningProgressManager.TotalStats stats = progressManager.getTotalStats(user);
                LearningSessionDao.SessionStats sessionStats = sessionDao.getSessionStats(user);

                StringBuilder sb = new StringBuilder();
                sb.append("=== ДЕТАЛЬНАЯ СТАТИСТИКА ===\n\n");
                sb.append("📊 **ПРОГРЕСС:**\n");
                sb.append(String.format("• Активных режимов: %d\n", stats.getActiveModes()));
                sb.append(String.format("• Всего заданий: %d\n", stats.getTotalTasks()));
                sb.append(String.format("• Общее время: %s\n", stats.getFormattedTotalTime()));
                sb.append(String.format("• Средний прогресс: %.1f%%\n", stats.getAverageProgress()));
                sb.append(String.format("• Достижений: %d\n\n", stats.getTotalAchievements()));

                sb.append("📈 **СЕССИИ:**\n");
                sb.append(String.format("• Всего сессий: %d\n", sessionStats.getTotalSessions()));
                sb.append(String.format("• Всего сообщений: %d\n", sessionStats.getTotalMessages()));
                sb.append(String.format("• Общее время сессий: %s\n", sessionStats.getFormattedTotalDuration()));
                sb.append(String.format("• Средний прогресс сессии: %.1f%%\n\n", sessionStats.getAverageProgress()));

                sb.append("🏆 **Лучший режим:** ").append(progressManager.getBestMode(user).getDisplayName()).append("\n\n");

                sb.append("📋 **Режимы для улучшения:**\n");
                progressManager.getModesToImprove(user).forEach(mode ->
                        sb.append("• ").append(mode.getDisplayName()).append("\n"));

                return sb.toString();
            } catch (Exception e) {
                logger.error("Ошибка при получении детальной статистики", e);
                return "Ошибка загрузки статистики";
            }
        }, executor);
    }
}