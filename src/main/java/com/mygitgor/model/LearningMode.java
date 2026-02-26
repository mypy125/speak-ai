package com.mygitgor.model;

public enum LearningMode {
    CONVERSATION("💬 Conversation Practice", "Общайтесь с AI для развития разговорных навыков"),
    PRONUNCIATION("🔊 Pronunciation", "Тренируйте правильное произношение звуков"),
    GRAMMAR("📚 Grammar", "Изучайте грамматические правила и структуры"),
    VOCABULARY("📖 Vocabulary", "Расширяйте словарный запас"),
    EXERCISE("🎯 Exercises", "Выполняйте практические упражнения"),
    WRITING("✍️ Writing", "Практикуйте письменную речь"),
    LISTENING("🎧 Listening", "Развивайте навыки восприятия на слух");

    private final String displayName;
    private final String description;

    LearningMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return displayName;
    }
}
