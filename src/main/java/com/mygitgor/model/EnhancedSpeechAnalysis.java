package com.mygitgor.model;

import java.util.*;

public class EnhancedSpeechAnalysis extends SpeechAnalysis {
    private float intonationScore;
    private float volumeScore;
    private float clarityScore;
    private float confidenceScore;
    private float speakingRate; // слова в минуту
    private int pauseCount;
    private float totalPauseDuration;
    private float pausePercentage;
    private float volumeLevel; // RMS уровень
    private float analysisDuration;
    private String proficiencyLevel;
    private Map<String, Float> phonemeScores;
    private List<String> detectedErrors;
    private List<String> warnings;
    private float overallScore;

    public EnhancedSpeechAnalysis() {
        super();
        this.phonemeScores = new HashMap<>();
        this.detectedErrors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.overallScore = 0.0f;
        this.pronunciationScore = 0.0f;
        this.fluencyScore = 0.0f;
        this.grammarScore = 0.0f;
        this.vocabularyScore = 0.0f;
    }

    public void addPhonemeScore(String phoneme, float score) {
        phonemeScores.put(phoneme, score);
    }

    public float getPhonemeScore(String phoneme) {
        return phonemeScores.getOrDefault(phoneme, 0.0f);
    }

    public Map<String, Float> getPhonemeScores() {
        return Collections.unmodifiableMap(phonemeScores);
    }

    public void addDetectedError(String error) {
        detectedErrors.add(error);
    }

    public List<String> getDetectedErrors() {
        return Collections.unmodifiableList(detectedErrors);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public double getOverallScore() {
        if (overallScore > 0) {
            return overallScore;
        }
        return (pronunciationScore + fluencyScore + grammarScore + vocabularyScore) / 4.0;
    }

    public void setOverallScore(float overallScore) {
        this.overallScore = overallScore;
    }

    @Override
    public void setPronunciationScore(double pronunciationScore) {
        this.pronunciationScore = (float) pronunciationScore;
    }

    @Override
    public void setFluencyScore(double fluencyScore) {
        this.fluencyScore = (float) fluencyScore;
    }

    @Override
    public void setGrammarScore(double grammarScore) {
        this.grammarScore = (float) grammarScore;
    }

    @Override
    public void setVocabularyScore(double vocabularyScore) {
        this.vocabularyScore = (float) vocabularyScore;
    }

    public void setAnalysisDuration(float analysisDuration) {
        this.analysisDuration = analysisDuration;
    }

    public void setVolumeLevel(float volumeLevel) {
        this.volumeLevel = volumeLevel;
    }

    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append("ДЕТАЛЬНЫЙ АНАЛИЗ РЕЧИ\n");
        report.append("=====================\n\n");

        report.append("ОБЩАЯ ОЦЕНКА: ").append(String.format("%.1f", getOverallScore())).append("/100\n");
        report.append("УРОВЕНЬ: ").append(proficiencyLevel != null ? proficiencyLevel : "Не определен").append("\n\n");

        report.append("ПОДРОБНЫЕ МЕТРИКИ:\n");
        report.append("• Произношение: ").append(String.format("%.1f", getPronunciationScore())).append("/100\n");
        report.append("• Беглость: ").append(String.format("%.1f", getFluencyScore())).append("/100\n");
        report.append("• Интонация: ").append(String.format("%.1f", intonationScore)).append("/100\n");
        report.append("• Громкость: ").append(String.format("%.1f", volumeScore)).append("/100\n");
        report.append("• Четкость: ").append(String.format("%.1f", clarityScore)).append("/100\n");
        report.append("• Уверенность: ").append(String.format("%.1f", confidenceScore)).append("/100\n\n");

        report.append("СТАТИСТИКА РЕЧИ:\n");
        report.append("• Скорость речи: ").append(String.format("%.1f", speakingRate)).append(" слов/мин\n");
        report.append("• Количество пауз: ").append(pauseCount).append("\n");
        report.append("• Общая длительность пауз: ").append(String.format("%.1f", totalPauseDuration)).append(" сек\n");
        report.append("• Процент пауз: ").append(String.format("%.1f", pausePercentage)).append("%\n");
        report.append("• Уровень громкости: ").append(String.format("%.3f", volumeLevel)).append("\n");
        report.append("• Длительность анализа: ").append(String.format("%.1f", analysisDuration)).append(" сек\n\n");

        if (!phonemeScores.isEmpty()) {
            report.append("АНАЛИЗ ОТДЕЛЬНЫХ ЗВУКОВ:\n");
            phonemeScores.forEach((phoneme, score) -> {
                report.append("• ").append(phoneme).append(": ").append(String.format("%.1f", score)).append("/100\n");
            });
            report.append("\n");
        }

        if (!detectedErrors.isEmpty()) {
            report.append("ОБНАРУЖЕННЫЕ ОШИБКИ:\n");
            for (String error : detectedErrors) {
                report.append("• ").append(error).append("\n");
            }
            report.append("\n");
        }

        if (!warnings.isEmpty()) {
            report.append("ПРЕДУПРЕЖДЕНИЯ:\n");
            for (String warning : warnings) {
                report.append("• ").append(warning).append("\n");
            }
            report.append("\n");
        }

        if (!getRecommendations().isEmpty()) {
            report.append("РЕКОМЕНДАЦИИ ДЛЯ УЛУЧШЕНИЯ:\n");
            for (String rec : getRecommendations()) {
                report.append("• ").append(rec).append("\n");
            }
        }

        return report.toString();
    }

    public float getIntonationScore() { return intonationScore; }
    public void setIntonationScore(float intonationScore) { this.intonationScore = intonationScore; }

    public float getVolumeScore() { return volumeScore; }
    public void setVolumeScore(float volumeScore) { this.volumeScore = volumeScore; }

    public float getClarityScore() { return clarityScore; }
    public void setClarityScore(float clarityScore) { this.clarityScore = clarityScore; }

    public float getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(float confidenceScore) { this.confidenceScore = confidenceScore; }

    public float getSpeakingRate() { return speakingRate; }
    public void setSpeakingRate(float speakingRate) { this.speakingRate = speakingRate; }

    public int getPauseCount() { return pauseCount; }
    public void setPauseCount(int pauseCount) { this.pauseCount = pauseCount; }

    public float getTotalPauseDuration() { return totalPauseDuration; }
    public void setTotalPauseDuration(float totalPauseDuration) { this.totalPauseDuration = totalPauseDuration; }

    public float getPausePercentage() { return pausePercentage; }
    public void setPausePercentage(float pausePercentage) { this.pausePercentage = pausePercentage; }

    public float getVolumeLevel() { return volumeLevel; }

    public float getAnalysisDuration() { return analysisDuration; }

    public String getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(String proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }
}