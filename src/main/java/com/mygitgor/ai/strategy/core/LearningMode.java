package com.mygitgor.ai.strategy.core;

public enum LearningMode {
    CONVERSATION("💬 Разговорная практика", "Общайтесь с AI для развития разговорных навыков"),
    PRONUNCIATION("🔊 Произношение", "Тренируйте правильное произношение звуков"),
    GRAMMAR("📚 Грамматика", "Изучайте грамматические правила и структуры"),
    VOCABULARY("📖 Словарный запас", "Расширяйте словарный запас"),
    EXERCISE("🎯 Упражнения", "Выполняйте практические упражнения"),
    WRITING("✍️ Письмо", "Практикуйте письменную речь"),
    LISTENING("🎧 Аудирование", "Развивайте навыки восприятия на слух");

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
