package com.mygitgor.ai.strategy.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.*;

public class LearningProgress {
    private final double overallProgress;
    private final Map<String, Double> skillsProgress;
    private final long timeSpent;
    private final int tasksCompleted;
    private final LocalDate startDate;
    private final List<String> achievements;

    private LearningProgress(Builder builder) {
        this.overallProgress = builder.overallProgress;
        this.skillsProgress = builder.skillsProgress != null ?
                Collections.unmodifiableMap(new HashMap<>(builder.skillsProgress)) :
                Collections.emptyMap();
        this.timeSpent = builder.timeSpent;
        this.tasksCompleted = builder.tasksCompleted;
        this.startDate = builder.startDate != null ? builder.startDate : LocalDate.now();
        this.achievements = builder.achievements != null ?
                Collections.unmodifiableList(new ArrayList<>(builder.achievements)) :
                Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double overallProgress;
        private Map<String, Double> skillsProgress;
        private long timeSpent;
        private int tasksCompleted;
        private LocalDate startDate;
        private List<String> achievements;

        public Builder overallProgress(double overallProgress) {
            this.overallProgress = Math.min(100, Math.max(0, overallProgress));
            return this;
        }

        public Builder skillsProgress(Map<String, Double> skillsProgress) {
            this.skillsProgress = skillsProgress;
            return this;
        }

        public Builder addSkillProgress(String skill, Double progress) {
            if (this.skillsProgress == null) {
                this.skillsProgress = new HashMap<>();
            }
            this.skillsProgress.put(skill, Math.min(100, Math.max(0, progress)));
            return this;
        }

        public Builder timeSpent(long timeSpent) {
            this.timeSpent = Math.max(0, timeSpent);
            return this;
        }

        public Builder tasksCompleted(int tasksCompleted) {
            this.tasksCompleted = Math.max(0, tasksCompleted);
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder achievements(List<String> achievements) {
            this.achievements = achievements;
            return this;
        }

        public Builder addAchievement(String achievement) {
            if (this.achievements == null) {
                this.achievements = new ArrayList<>();
            }
            this.achievements.add(achievement);
            return this;
        }

        public LearningProgress build() {
            validate();
            return new LearningProgress(this);
        }

        private void validate() {
            if (overallProgress < 0 || overallProgress > 100) {
                overallProgress = Math.min(100, Math.max(0, overallProgress));
            }
            if (skillsProgress != null) {
                skillsProgress.values().removeIf(v -> v < 0 || v > 100);
            }
        }
    }

    // Геттеры
    public double getOverallProgress() { return overallProgress; }
    public Map<String, Double> getSkillsProgress() { return skillsProgress; }
    public long getTimeSpent() { return timeSpent; }
    public int getTasksCompleted() { return tasksCompleted; }
    public LocalDate getStartDate() { return startDate; }
    public List<String> getAchievements() { return achievements; }

    /**
     * Получение прогресса по конкретному навыку
     */
    public double getSkillProgress(String skill) {
        return skillsProgress.getOrDefault(skill, 0.0);
    }

    /**
     * Проверка, есть ли достижения
     */
    public boolean hasAchievements() {
        return achievements != null && !achievements.isEmpty();
    }

    /**
     * Получение отформатированного времени обучения
     */
    public String getFormattedTimeSpent() {
        long hours = timeSpent / 3600;
        long minutes = (timeSpent % 3600) / 60;
        long seconds = timeSpent % 60;

        if (hours > 0) {
            return String.format("%dч %dм %dс", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds);
        } else {
            return String.format("%dс", seconds);
        }
    }

    @Override
    public String toString() {
        return String.format("LearningProgress{overall=%.1f%%, tasks=%d, time=%s}",
                overallProgress, tasksCompleted, getFormattedTimeSpent());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningProgress that = (LearningProgress) o;
        return Double.compare(that.overallProgress, overallProgress) == 0 &&
                timeSpent == that.timeSpent &&
                tasksCompleted == that.tasksCompleted &&
                Objects.equals(startDate, that.startDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallProgress, timeSpent, tasksCompleted, startDate);
    }
}
