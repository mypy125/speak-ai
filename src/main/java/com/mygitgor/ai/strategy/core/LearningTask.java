package com.mygitgor.ai.strategy.core;

import java.util.List;
import java.util.Map;
import java.util.*;

public class LearningTask {
    private final String id;
    private final String title;
    private final String description;
    private final LearningMode mode;
    private final DifficultyLevel difficulty;
    private final List<String> examples;
    private final Map<String, Object> metadata;

    public enum DifficultyLevel {
        BEGINNER("Начинающий", 1),
        INTERMEDIATE("Средний", 2),
        ADVANCED("Продвинутый", 3),
        EXPERT("Эксперт", 4);

        private final String displayName;
        private final int level;

        DifficultyLevel(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }

        public String getDisplayName() { return displayName; }
        public int getLevel() { return level; }

        public static DifficultyLevel fromLevel(int level) {
            for (DifficultyLevel dl : values()) {
                if (dl.level == level) return dl;
            }
            return BEGINNER;
        }
    }

    private LearningTask(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.mode = builder.mode;
        this.difficulty = builder.difficulty;
        this.examples = builder.examples != null ?
                Collections.unmodifiableList(new ArrayList<>(builder.examples)) :
                Collections.emptyList();
        this.metadata = builder.metadata != null ?
                Collections.unmodifiableMap(new HashMap<>(builder.metadata)) :
                Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String title;
        private String description;
        private LearningMode mode;
        private DifficultyLevel difficulty;
        private List<String> examples;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder mode(LearningMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder difficulty(DifficultyLevel difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder examples(List<String> examples) {
            this.examples = examples;
            return this;
        }

        public Builder addExample(String example) {
            if (this.examples == null) {
                this.examples = new ArrayList<>();
            }
            this.examples.add(example);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public LearningTask build() {
            validate();
            return new LearningTask(this);
        }

        private void validate() {
            if (id == null || id.trim().isEmpty()) {
                id = "task_" + System.currentTimeMillis();
            }
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalStateException("Title cannot be empty");
            }
            if (mode == null) {
                throw new IllegalStateException("Learning mode cannot be null");
            }
            if (difficulty == null) {
                difficulty = DifficultyLevel.BEGINNER;
            }
        }
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LearningMode getMode() { return mode; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public List<String> getExamples() { return examples; }
    public Map<String, Object> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return String.format("LearningTask{id='%s', title='%s', mode=%s, difficulty=%s}",
                id, title, mode, difficulty);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningTask that = (LearningTask) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
