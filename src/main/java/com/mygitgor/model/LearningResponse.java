package com.mygitgor.model;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class LearningResponse {
    private final String message;
    private final String ttsText;
    private final LearningMode nextMode;
    private final LearningTask nextTask;
    private final double progress;
    private final List<String> recommendations;

    private LearningResponse(Builder builder) {
        this.message = builder.message;
        this.ttsText = builder.ttsText;
        this.nextMode = builder.nextMode;
        this.nextTask = builder.nextTask;
        this.progress = Math.min(100, Math.max(0, builder.progress));
        this.recommendations = builder.recommendations != null ?
                Collections.unmodifiableList(new ArrayList<>(builder.recommendations)) :
                Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String message;
        private String ttsText;
        private LearningMode nextMode;
        private LearningTask nextTask;
        private double progress;
        private List<String> recommendations;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder ttsText(String ttsText) {
            this.ttsText = ttsText;
            return this;
        }

        public Builder nextMode(LearningMode nextMode) {
            this.nextMode = nextMode;
            return this;
        }

        public Builder nextTask(LearningTask nextTask) {
            this.nextTask = nextTask;
            return this;
        }

        public Builder progress(double progress) {
            this.progress = progress;
            return this;
        }

        public Builder recommendations(List<String> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder addRecommendation(String recommendation) {
            if (this.recommendations == null) {
                this.recommendations = new ArrayList<>();
            }
            this.recommendations.add(recommendation);
            return this;
        }

        public LearningResponse build() {
            validate();
            return new LearningResponse(this);
        }

        private void validate() {
            if (message == null || message.trim().isEmpty()) {
                message = "Готов к обучению!";
            }
            if (ttsText == null || ttsText.trim().isEmpty()) {
                ttsText = message;
            }
        }
    }

    public String getMessage() { return message; }
    public String getTtsText() { return ttsText; }
    public LearningMode getNextMode() { return nextMode; }
    public LearningTask getNextTask() { return nextTask; }
    public double getProgress() { return progress; }
    public List<String> getRecommendations() { return recommendations; }

    @Override
    public String toString() {
        return String.format("LearningResponse{progress=%.1f%%, mode=%s, hasTask=%s, hasTts=%s}",
                progress, nextMode, nextTask != null, ttsText != null);
    }
}