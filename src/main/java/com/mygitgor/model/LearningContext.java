package com.mygitgor.model;

import java.util.Objects;

public class LearningContext {
    private final String userId;
    private final LearningMode mode;
    private final double currentLevel;
    private final EnhancedSpeechAnalysis lastAnalysis;
    private final int sessionCount;
    private final long sessionDuration;

    private LearningContext(Builder builder) {
        this.userId = builder.userId;
        this.mode = builder.mode;
        this.currentLevel = Math.min(100, Math.max(0, builder.currentLevel));
        this.lastAnalysis = builder.lastAnalysis;
        this.sessionCount = Math.max(0, builder.sessionCount);
        this.sessionDuration = Math.max(0, builder.sessionDuration);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private LearningMode mode;
        private double currentLevel = 50.0;
        private EnhancedSpeechAnalysis lastAnalysis;
        private int sessionCount = 0;
        private long sessionDuration = 0;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder mode(LearningMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder currentLevel(double level) {
            this.currentLevel = level;
            return this;
        }

        public Builder lastAnalysis(EnhancedSpeechAnalysis analysis) {
            this.lastAnalysis = analysis;
            return this;
        }

        public Builder sessionCount(int count) {
            this.sessionCount = count;
            return this;
        }

        public Builder sessionDuration(long duration) {
            this.sessionDuration = duration;
            return this;
        }

        public LearningContext build() {
            validate();
            return new LearningContext(this);
        }

        private void validate() {
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalStateException("User ID cannot be empty");
            }
            if (mode == null) {
                mode = LearningMode.CONVERSATION;
            }
        }
    }

    public String getUserId() { return userId; }
    public LearningMode getMode() { return mode; }
    public double getCurrentLevel() { return currentLevel; }
    public EnhancedSpeechAnalysis getLastAnalysis() { return lastAnalysis; }
    public int getSessionCount() { return sessionCount; }
    public long getSessionDuration() { return sessionDuration; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningContext that = (LearningContext) o;
        return Objects.equals(userId, that.userId) && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, mode);
    }

    @Override
    public String toString() {
        return String.format("LearningContext{user='%s', mode=%s, level=%.1f, sessions=%d}",
                userId, mode, currentLevel, sessionCount);
    }
}