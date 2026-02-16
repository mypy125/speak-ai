package com.mygitgor.model.core;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import com.mygitgor.model.LearningMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

@DatabaseTable(tableName = "learning_progress")
public class LearningProgress {
    private static final Logger logger = LoggerFactory.getLogger(LearningProgress.class);
    private static final Gson gson = new Gson();
    private static final Type mapType = new TypeToken<Map<String, Double>>(){}.getType();
    private static final Type listType = new TypeToken<List<String>>(){}.getType();

    @DatabaseField(generatedId = true)
    private int id;
    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "user_id", canBeNull = false)
    private User user;
    @DatabaseField(canBeNull = false)
    private String learningMode;
    @DatabaseField
    private double overallProgress;
    @DatabaseField(width = 4000)
    private String skillsProgressJson;
    @DatabaseField
    private long timeSpent;
    @DatabaseField
    private int tasksCompleted;
    @DatabaseField
    private Date startDate;
    @DatabaseField
    private Date lastUpdated;
    @DatabaseField(width = 4000)
    private String achievementsJson;

    private transient Map<String, Double> skillsProgress;
    private transient List<String> achievements;
    private transient final Object lock = new Object();

    public LearningProgress() {
        this.lastUpdated = new Date();
        this.startDate = new Date();
        this.skillsProgress = new ConcurrentHashMap<>();
        this.achievements = new ArrayList<>();
    }

    private LearningProgress(Builder builder) {
        this.user = builder.user;
        this.learningMode = builder.learningMode != null ? builder.learningMode.name() : null;
        this.overallProgress = Math.min(100, Math.max(0, builder.overallProgress));
        this.timeSpent = Math.max(0, builder.timeSpent);
        this.tasksCompleted = Math.max(0, builder.tasksCompleted);
        this.startDate = builder.startDate != null ?
                Date.from(builder.startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()) :
                new Date();
        this.lastUpdated = new Date();

        this.skillsProgress = builder.skillsProgress != null ?
                new ConcurrentHashMap<>(builder.skillsProgress) :
                new ConcurrentHashMap<>();
        this.achievements = builder.achievements != null ?
                new ArrayList<>(builder.achievements) :
                new ArrayList<>();

        serializeCollections();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;
        private LearningMode learningMode;
        private double overallProgress;
        private Map<String, Double> skillsProgress;
        private long timeSpent;
        private int tasksCompleted;
        private LocalDate startDate;
        private List<String> achievements;

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder learningMode(LearningMode mode) {
            this.learningMode = mode;
            return this;
        }

        public Builder overallProgress(double progress) {
            this.overallProgress = Math.min(100, Math.max(0, progress));
            return this;
        }

        public Builder skillsProgress(Map<String, Double> skills) {
            this.skillsProgress = skills != null ? new HashMap<>(skills) : null;
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
            this.achievements = achievements != null ? new ArrayList<>(achievements) : null;
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
            if (user == null) {
                throw new IllegalStateException("User cannot be null");
            }
            if (learningMode == null) {
                learningMode = LearningMode.CONVERSATION;
            }
        }
    }

    private void serializeCollections() {
        synchronized (lock) {
            try {
                if (skillsProgress != null && !skillsProgress.isEmpty()) {
                    this.skillsProgressJson = gson.toJson(skillsProgress);
                } else {
                    this.skillsProgressJson = null;
                }

                if (achievements != null && !achievements.isEmpty()) {
                    this.achievementsJson = gson.toJson(achievements);
                } else {
                    this.achievementsJson = null;
                }
            } catch (Exception e) {
                logger.error("Ошибка при сериализации коллекций", e);
            }
        }
    }

    private void deserializeCollections() {
        synchronized (lock) {
            try {
                if (skillsProgressJson != null && !skillsProgressJson.isEmpty()) {
                    this.skillsProgress = gson.fromJson(skillsProgressJson, mapType);
                }
                if (this.skillsProgress == null) {
                    this.skillsProgress = new ConcurrentHashMap<>();
                }

                if (achievementsJson != null && !achievementsJson.isEmpty()) {
                    this.achievements = gson.fromJson(achievementsJson, listType);
                }
                if (this.achievements == null) {
                    this.achievements = new ArrayList<>();
                }
            } catch (Exception e) {
                logger.error("Ошибка при десериализации коллекций", e);
                this.skillsProgress = new ConcurrentHashMap<>();
                this.achievements = new ArrayList<>();
            }
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) {
        this.user = user;
    }

    public String getLearningMode() { return learningMode; }
    public void setLearningMode(String learningMode) {
        this.learningMode = learningMode;
    }

    public double getOverallProgress() { return overallProgress; }

    public void setOverallProgress(double overallProgress) {
        this.overallProgress = Math.min(100, Math.max(0, overallProgress));
        this.lastUpdated = new Date();
    }

    public long getTimeSpent() { return timeSpent; }

    public void setTimeSpent(long timeSpent) {
        this.timeSpent = Math.max(0, timeSpent);
        this.lastUpdated = new Date();
    }

    public int getTasksCompleted() { return tasksCompleted; }

    public void setTasksCompleted(int tasksCompleted) {
        this.tasksCompleted = Math.max(0, tasksCompleted);
        this.lastUpdated = new Date();
    }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }

    public Map<String, Double> getSkillsProgress() {
        if (skillsProgress == null) {
            deserializeCollections();
        }
        return Collections.unmodifiableMap(skillsProgress);
    }

    public void setSkillsProgress(Map<String, Double> skillsProgress) {
        synchronized (lock) {
            this.skillsProgress = new ConcurrentHashMap<>(skillsProgress);
            serializeCollections();
        }
        this.lastUpdated = new Date();
    }

    public void updateSkillProgress(String skill, double progress) {
        synchronized (lock) {
            if (skillsProgress == null) {
                deserializeCollections();
            }
            skillsProgress.put(skill, Math.min(100, Math.max(0, progress)));
            serializeCollections();
        }
        this.lastUpdated = new Date();
    }

    public double getSkillProgress(String skill) {
        if (skillsProgress == null) {
            deserializeCollections();
        }
        return skillsProgress.getOrDefault(skill, 0.0);
    }

    public List<String> getAchievements() {
        if (achievements == null) {
            deserializeCollections();
        }
        return Collections.unmodifiableList(achievements);
    }

    public void setAchievements(List<String> achievements) {
        synchronized (lock) {
            this.achievements = new ArrayList<>(achievements);
            serializeCollections();
        }
        this.lastUpdated = new Date();
    }

    public void addAchievement(String achievement) {
        synchronized (lock) {
            if (achievements == null) {
                deserializeCollections();
            }
            if (!achievements.contains(achievement)) {
                achievements.add(achievement);
                serializeCollections();
            }
        }
        this.lastUpdated = new Date();
    }

    public boolean hasAchievement(String achievement) {
        if (achievements == null) {
            deserializeCollections();
        }
        return achievements.contains(achievement);
    }

    public LocalDate getStartLocalDate() {
        return startDate != null ?
                startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() :
                LocalDate.now();
    }

    public void addTimeSpent(long additionalTime) {
        this.timeSpent = Math.max(0, this.timeSpent + additionalTime);
        this.lastUpdated = new Date();
    }

    public void addCompletedTask() {
        this.tasksCompleted++;
        this.lastUpdated = new Date();
    }

    public void updateOverallProgress() {
        if (skillsProgress == null || skillsProgress.isEmpty()) {
            return;
        }

        double sum = skillsProgress.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        this.overallProgress = sum / skillsProgress.size();
        this.lastUpdated = new Date();
    }

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

    public void prepareForSave() {
        serializeCollections();
    }

    public void afterLoad() {
        deserializeCollections();
    }

    @Override
    public String toString() {
        return String.format("LearningProgress{id=%d, user=%d, mode=%s, progress=%.1f%%, tasks=%d, time=%s}",
                id, user != null ? user.getId() : 0, learningMode, overallProgress,
                tasksCompleted, getFormattedTimeSpent());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningProgress that = (LearningProgress) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}