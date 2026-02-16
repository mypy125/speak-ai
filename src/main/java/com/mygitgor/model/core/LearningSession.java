package com.mygitgor.model.core;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mygitgor.model.LearningMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@DatabaseTable(tableName = "learning_sessions")
public class LearningSession {
    private static final Logger logger = LoggerFactory.getLogger(LearningSession.class);
    private static final Gson gson = new Gson();
    private static final Type mapType = new TypeToken<Map<String, Object>>(){}.getType();

    @DatabaseField(generatedId = true)
    private int id;
    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "user_id", canBeNull = false)
    private User user;
    @DatabaseField(canBeNull = false)
    private String currentMode;
    @DatabaseField
    private Date sessionStart;
    @DatabaseField
    private Date sessionEnd;
    @DatabaseField
    private int messagesExchanged;
    @DatabaseField
    private double sessionProgress;
    @DatabaseField
    private double currentLevel;
    @DatabaseField(width = 4000)
    private String contextJson;

    private transient Map<String, Object> contextData;
    private transient final Object lock = new Object();

    public LearningSession() {
        this.sessionStart = new Date();
        this.contextData = new ConcurrentHashMap<>();
        this.messagesExchanged = 0;
        this.sessionProgress = 0;
        this.currentLevel = 50.0;
    }

    private LearningSession(Builder builder) {
        this.user = builder.user;
        this.currentMode = builder.currentMode != null ? builder.currentMode.name() : "CONVERSATION";
        this.sessionStart = builder.sessionStart != null ? builder.sessionStart : new Date();
        this.sessionEnd = builder.sessionEnd;
        this.messagesExchanged = Math.max(0, builder.messagesExchanged);
        this.sessionProgress = Math.min(100, Math.max(0, builder.sessionProgress));
        this.currentLevel = Math.min(100, Math.max(0, builder.currentLevel));

        this.contextData = builder.contextData != null ?
                new ConcurrentHashMap<>(builder.contextData) :
                new ConcurrentHashMap<>();

        serializeContext();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;
        private LearningMode currentMode;
        private Date sessionStart;
        private Date sessionEnd;
        private int messagesExchanged;
        private double sessionProgress;
        private double currentLevel = 50.0;
        private Map<String, Object> contextData;

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder currentMode(LearningMode mode) {
            this.currentMode = mode;
            return this;
        }

        public Builder sessionStart(Date sessionStart) {
            this.sessionStart = sessionStart;
            return this;
        }

        public Builder sessionEnd(Date sessionEnd) {
            this.sessionEnd = sessionEnd;
            return this;
        }

        public Builder messagesExchanged(int count) {
            this.messagesExchanged = Math.max(0, count);
            return this;
        }

        public Builder sessionProgress(double progress) {
            this.sessionProgress = Math.min(100, Math.max(0, progress));
            return this;
        }

        public Builder currentLevel(double level) {
            this.currentLevel = Math.min(100, Math.max(0, level));
            return this;
        }

        public Builder contextData(Map<String, Object> contextData) {
            this.contextData = contextData != null ? new HashMap<>(contextData) : null;
            return this;
        }

        public Builder addContextData(String key, Object value) {
            if (this.contextData == null) {
                this.contextData = new HashMap<>();
            }
            this.contextData.put(key, value);
            return this;
        }

        public LearningSession build() {
            validate();
            return new LearningSession(this);
        }

        private void validate() {
            if (user == null) {
                throw new IllegalStateException("User cannot be null");
            }
            if (currentMode == null) {
                currentMode = LearningMode.CONVERSATION;
            }
        }
    }

    private void serializeContext() {
        synchronized (lock) {
            try {
                if (contextData != null && !contextData.isEmpty()) {
                    this.contextJson = gson.toJson(contextData);
                } else {
                    this.contextJson = null;
                }
            } catch (Exception e) {
                logger.error("Ошибка при сериализации контекста", e);
            }
        }
    }

    private void deserializeContext() {
        synchronized (lock) {
            try {
                if (contextJson != null && !contextJson.isEmpty()) {
                    this.contextData = gson.fromJson(contextJson, mapType);
                }
                if (this.contextData == null) {
                    this.contextData = new ConcurrentHashMap<>();
                }
            } catch (Exception e) {
                logger.error("Ошибка при десериализации контекста", e);
                this.contextData = new ConcurrentHashMap<>();
            }
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCurrentMode() { return currentMode; }

    public void setCurrentMode(String currentMode) {
        this.currentMode = currentMode;
    }

    public LearningMode getCurrentLearningMode() {
        try {
            return LearningMode.valueOf(currentMode);
        } catch (IllegalArgumentException e) {
            logger.warn("Неизвестный режим: {}", currentMode);
            return LearningMode.CONVERSATION;
        }
    }

    public Date getSessionStart() { return sessionStart; }
    public void setSessionStart(Date sessionStart) { this.sessionStart = sessionStart; }

    public Date getSessionEnd() { return sessionEnd; }

    public void setSessionEnd(Date sessionEnd) {
        this.sessionEnd = sessionEnd;
    }

    public int getMessagesExchanged() { return messagesExchanged; }

    public void setMessagesExchanged(int messagesExchanged) {
        this.messagesExchanged = Math.max(0, messagesExchanged);
    }

    public double getSessionProgress() { return sessionProgress; }

    public void setSessionProgress(double sessionProgress) {
        this.sessionProgress = Math.min(100, Math.max(0, sessionProgress));
    }

    public double getCurrentLevel() { return currentLevel; }

    public void setCurrentLevel(double currentLevel) {
        this.currentLevel = Math.min(100, Math.max(0, currentLevel));
    }

    public Map<String, Object> getContextData() {
        if (contextData == null) {
            deserializeContext();
        }
        return contextData;
    }

    public void setContextData(Map<String, Object> contextData) {
        synchronized (lock) {
            this.contextData = new ConcurrentHashMap<>(contextData);
            serializeContext();
        }
    }

    public void addContextData(String key, Object value) {
        synchronized (lock) {
            if (contextData == null) {
                deserializeContext();
            }
            contextData.put(key, value);
            serializeContext();
        }
    }

    public Object getContextValue(String key) {
        if (contextData == null) {
            deserializeContext();
        }
        return contextData.get(key);
    }

    public String getContextString(String key) {
        Object value = getContextValue(key);
        return value != null ? value.toString() : null;
    }

    public Double getContextDouble(String key) {
        Object value = getContextValue(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
    public boolean isActive() {
        return sessionEnd == null;
    }

    public void endSession() {
        this.sessionEnd = new Date();
    }

    public void incrementMessages() {
        this.messagesExchanged++;
    }

    public long getDurationSeconds() {
        Date end = sessionEnd != null ? sessionEnd : new Date();
        return (end.getTime() - sessionStart.getTime()) / 1000;
    }

    public String getFormattedDuration() {
        long seconds = getDurationSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dч %dм %dс", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, secs);
        } else {
            return String.format("%dс", secs);
        }
    }

    public void updateProgress(double newProgress) {
        this.sessionProgress = Math.min(100, Math.max(0, newProgress));
    }

    public void updateLevel(double delta) {
        this.currentLevel = Math.min(100, Math.max(0, this.currentLevel + delta));
    }

    public void prepareForSave() {
        serializeContext();
    }

    public void afterLoad() {
        deserializeContext();
    }

    @Override
    public String toString() {
        return String.format("LearningSession{id=%d, user=%d, mode=%s, active=%s, messages=%d, duration=%s}",
                id, user != null ? user.getId() : 0, currentMode, isActive(),
                messagesExchanged, getFormattedDuration());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningSession that = (LearningSession) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}