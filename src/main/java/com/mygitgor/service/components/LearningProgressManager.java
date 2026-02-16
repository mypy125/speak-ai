package com.mygitgor.service.components;

import com.mygitgor.model.LearningMode;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.model.core.User;
import com.mygitgor.repository.DAO.LearningProgressDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LearningProgressManager {
    private static final Logger logger = LoggerFactory.getLogger(LearningProgressManager.class);

    private final LearningProgressDao progressDao;
    private final Map<String, LearningProgress> cache = new ConcurrentHashMap<>();

    private static final int ACHIEVEMENT_TASKS_10 = 10;
    private static final int ACHIEVEMENT_TASKS_50 = 50;
    private static final int ACHIEVEMENT_TASKS_100 = 100;
    private static final double ACHIEVEMENT_PROGRESS_50 = 50.0;
    private static final double ACHIEVEMENT_PROGRESS_75 = 75.0;
    private static final double ACHIEVEMENT_PROGRESS_90 = 90.0;

    public LearningProgressManager() {
        this.progressDao = new LearningProgressDao();
    }

    public Map<LearningMode, LearningProgress> getUserProgress(User user) {
        Map<LearningMode, LearningProgress> result = new HashMap<>();

        try {
            List<LearningProgress> progresses = progressDao.getProgressByUser(user);
            for (LearningProgress progress : progresses) {
                try {
                    LearningMode mode = LearningMode.valueOf(progress.getLearningMode());
                    result.put(mode, progress);
                    cache.put(getCacheKey(user, mode), progress);
                } catch (IllegalArgumentException e) {
                    logger.warn("Неизвестный режим обучения: {}", progress.getLearningMode());
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении прогресса пользователя", e);
        }

        return result;
    }

    public LearningProgress getProgress(User user, LearningMode mode) {
        String cacheKey = getCacheKey(user, mode);
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        try {
            LearningProgress progress = progressDao.getProgressByUserAndMode(user, mode.name());
            if (progress != null) {
                cache.put(cacheKey, progress);
                return progress;
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении прогресса для режима {}", mode, e);
        }

        LearningProgress newProgress = LearningProgress.builder()
                .user(user)
                .learningMode(mode)
                .overallProgress(0)
                .tasksCompleted(0)
                .timeSpent(0)
                .startDate(LocalDate.now())
                .build();

        saveProgress(newProgress);
        cache.put(cacheKey, newProgress);
        return newProgress;
    }

    public void saveProgress(LearningProgress progress) {
        try {
            LearningProgress existing = progressDao.getProgressByUserAndMode(
                    progress.getUser(),
                    progress.getLearningMode()
            );

            if (existing == null) {
                progressDao.createProgress(progress);
                logger.debug("Прогресс создан: user={}, mode={}, progress={}",
                        progress.getUser().getId(), progress.getLearningMode(), progress.getOverallProgress());
            } else {
                progress.setId(existing.getId());
                progressDao.updateProgress(progress);
                logger.debug("Прогресс обновлен: user={}, mode={}, progress={}",
                        progress.getUser().getId(), progress.getLearningMode(), progress.getOverallProgress());
            }

            updateCache(progress);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении прогресса", e);
        }
    }

    public void updateProgress(User user, LearningMode mode,
                               double progressDelta, int tasksDelta, long timeDelta) {
        LearningProgress current = getProgress(user, mode);

        LearningProgress updated = LearningProgress.builder()
                .user(user)
                .learningMode(mode)
                .overallProgress(current.getOverallProgress() + progressDelta)
                .tasksCompleted(current.getTasksCompleted() + tasksDelta)
                .timeSpent(current.getTimeSpent() + timeDelta)
                .startDate(current.getStartLocalDate())
                .build();

        current.getSkillsProgress().forEach(updated::updateSkillProgress);
        current.getAchievements().forEach(updated::addAchievement);
        checkAndAddAchievements(updated);
        saveProgress(updated);
    }

    public void addTimeSpent(User user, LearningMode mode, long seconds) {
        LearningProgress current = getProgress(user, mode);
        current.addTimeSpent(seconds);
        saveProgress(current);
    }

    public void addCompletedTask(User user, LearningMode mode) {
        LearningProgress current = getProgress(user, mode);
        current.addCompletedTask();
        checkAndAddAchievements(current);
        saveProgress(current);
    }

    public void updateSkillProgress(User user, LearningMode mode, String skill, double progress) {
        LearningProgress current = getProgress(user, mode);
        current.updateSkillProgress(skill, progress);
        current.updateOverallProgress();
        saveProgress(current);
    }

    public void addAchievement(User user, LearningMode mode, String achievement) {
        LearningProgress current = getProgress(user, mode);
        current.addAchievement(achievement);
        saveProgress(current);
    }

    private void checkAndAddAchievements(LearningProgress progress) {
        int tasks = progress.getTasksCompleted();
        double overall = progress.getOverallProgress();

        if (tasks >= ACHIEVEMENT_TASKS_100 && !progress.hasAchievement("100 заданий")) {
            progress.addAchievement("🏆 Выполнено 100 заданий! Мастер!");
        } else if (tasks >= ACHIEVEMENT_TASKS_50 && !progress.hasAchievement("50 заданий")) {
            progress.addAchievement("⭐ Выполнено 50 заданий! Отличный прогресс!");
        } else if (tasks >= ACHIEVEMENT_TASKS_10 && !progress.hasAchievement("10 заданий")) {
            progress.addAchievement("🎯 Выполнено 10 заданий! Так держать!");
        }

        if (overall >= ACHIEVEMENT_PROGRESS_90 && !progress.hasAchievement("90% прогресса")) {
            progress.addAchievement("🔥 90% прогресса! Почти идеально!");
        } else if (overall >= ACHIEVEMENT_PROGRESS_75 && !progress.hasAchievement("75% прогресса")) {
            progress.addAchievement("📈 75% прогресса! Отличный результат!");
        } else if (overall >= ACHIEVEMENT_PROGRESS_50 && !progress.hasAchievement("50% прогресса")) {
            progress.addAchievement("🎉 50% прогресса! Половина пути пройдена!");
        }
    }

    private String getCacheKey(User user, LearningMode mode) {
        return user.getId() + ":" + mode.name();
    }

    private void updateCache(LearningProgress progress) {
        try {
            LearningMode mode = LearningMode.valueOf(progress.getLearningMode());
            String key = getCacheKey(progress.getUser(), mode);
            cache.put(key, progress);
        } catch (IllegalArgumentException e) {
            logger.warn("Не удалось обновить кэш для режима: {}", progress.getLearningMode());
        }
    }

    public void clearCache(User user) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(user.getId() + ":"));
        logger.debug("Кэш прогресса очищен для пользователя {}", user.getId());
    }

    public Map<LearningMode, Double> getAllProgress(User user) {
        Map<LearningMode, Double> stats = new HashMap<>();

        for (LearningMode mode : LearningMode.values()) {
            try {
                LearningProgress progress = getProgress(user, mode);
                stats.put(mode, progress.getOverallProgress());
            } catch (Exception e) {
                stats.put(mode, 0.0);
            }
        }

        return stats;
    }

    public TotalStats getTotalStats(User user) {
        List<LearningProgress> progresses = progressDao.getProgressByUser(user);

        int totalTasks = 0;
        long totalTime = 0;
        double avgProgress = 0;
        int totalAchievements = 0;

        for (LearningProgress progress : progresses) {
            totalTasks += progress.getTasksCompleted();
            totalTime += progress.getTimeSpent();
            avgProgress += progress.getOverallProgress();
            totalAchievements += progress.getAchievements().size();
        }

        if (!progresses.isEmpty()) {
            avgProgress /= progresses.size();
        }

        return new TotalStats(progresses.size(), totalTasks, totalTime, avgProgress, totalAchievements);
    }

    public LearningMode getBestMode(User user) {
        Map<LearningMode, Double> allProgress = getAllProgress(user);

        return allProgress.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(LearningMode.CONVERSATION);
    }

    public List<LearningMode> getModesToImprove(User user) {
        Map<LearningMode, Double> allProgress = getAllProgress(user);

        return allProgress.entrySet().stream()
                .filter(e -> e.getValue() < 50)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingDouble(allProgress::get))
                .toList();
    }

    public void resetProgress(User user, LearningMode mode) {
        try {
            LearningProgress existing = progressDao.getProgressByUserAndMode(user, mode.name());
            if (existing != null) {
                progressDao.deleteProgress(existing.getId());
            }
            cache.remove(getCacheKey(user, mode));
            logger.info("Прогресс сброшен: user={}, mode={}", user.getId(), mode);
        } catch (Exception e) {
            logger.error("Ошибка при сбросе прогресса", e);
        }
    }

    public void resetAllProgress(User user) {
        try {
            List<LearningProgress> progresses = progressDao.getProgressByUser(user);
            for (LearningProgress progress : progresses) {
                progressDao.deleteProgress(progress.getId());
            }
            clearCache(user);
            logger.info("Весь прогресс сброшен для пользователя {}", user.getId());
        } catch (Exception e) {
            logger.error("Ошибка при сбросе всего прогресса", e);
        }
    }

    public static class TotalStats {
        private final int activeModes;
        private final int totalTasks;
        private final long totalTime;
        private final double averageProgress;
        private final int totalAchievements;

        public TotalStats(int activeModes, int totalTasks, long totalTime,
                          double averageProgress, int totalAchievements) {
            this.activeModes = activeModes;
            this.totalTasks = totalTasks;
            this.totalTime = totalTime;
            this.averageProgress = averageProgress;
            this.totalAchievements = totalAchievements;
        }

        public int getActiveModes() { return activeModes; }
        public int getTotalTasks() { return totalTasks; }
        public long getTotalTime() { return totalTime; }
        public double getAverageProgress() { return averageProgress; }
        public int getTotalAchievements() { return totalAchievements; }

        public String getFormattedTotalTime() {
            long hours = totalTime / 3600;
            long minutes = (totalTime % 3600) / 60;

            if (hours > 0) {
                return String.format("%dч %dм", hours, minutes);
            } else {
                return String.format("%dм", minutes);
            }
        }

        @Override
        public String toString() {
            return String.format("TotalStats{modes=%d, tasks=%d, time=%s, avgProgress=%.1f%%, achievements=%d}",
                    activeModes, totalTasks, getFormattedTotalTime(), averageProgress, totalAchievements);
        }
    }
}