package com.mygitgor.model;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class SpeechAnalysis {
    private String text;
    private String audioPath;
    private Date analysisDate;
    protected double pronunciationScore;
    protected double fluencyScore;
    protected double grammarScore;
    protected double vocabularyScore;
    private List<String> errors;
    private List<String> recommendations;
    private String transcription;

    private float analysisDuration;
    private float volumeLevel;

    public SpeechAnalysis() {
        this.errors = new ArrayList<>();
        this.recommendations = new ArrayList<>();
        this.analysisDate = new Date();
        this.pronunciationScore = 0;
        this.fluencyScore = 0;
        this.grammarScore = 0;
        this.vocabularyScore = 0;
        this.analysisDuration = 0;
        this.volumeLevel = 0;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public void addRecommendation(String recommendation) {
        this.recommendations.add(recommendation);
    }

    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("ОБЩИЙ АНАЛИЗ РЕЧИ\n");
        summary.append("=================\n\n");

        summary.append("Общий балл: ").append(String.format("%.1f", getOverallScore())).append("/100\n");
        summary.append("Произношение: ").append(String.format("%.1f", pronunciationScore)).append("/100\n");
        summary.append("Беглость: ").append(String.format("%.1f", fluencyScore)).append("/100\n");
        summary.append("Грамматика: ").append(String.format("%.1f", grammarScore)).append("/100\n");
        summary.append("Словарный запас: ").append(String.format("%.1f", vocabularyScore)).append("/100\n");

        if (!errors.isEmpty()) {
            summary.append("\nОсновные ошибки:\n");
            for (String error : errors) {
                summary.append("• ").append(error).append("\n");
            }
        }

        if (!recommendations.isEmpty()) {
            summary.append("\nРекомендации:\n");
            for (String rec : recommendations) {
                summary.append("• ").append(rec).append("\n");
            }
        }

        return summary.toString();
    }

    public double getOverallScore() {
        return (pronunciationScore + fluencyScore + grammarScore + vocabularyScore) / 4.0;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }

    public Date getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(Date analysisDate) { this.analysisDate = analysisDate; }

    public double getPronunciationScore() { return pronunciationScore; }
    public void setPronunciationScore(double pronunciationScore) { this.pronunciationScore = pronunciationScore; }

    public double getFluencyScore() { return fluencyScore; }
    public void setFluencyScore(double fluencyScore) { this.fluencyScore = fluencyScore; }

    public double getGrammarScore() { return grammarScore; }
    public void setGrammarScore(double grammarScore) { this.grammarScore = grammarScore; }

    public double getVocabularyScore() { return vocabularyScore; }
    public void setVocabularyScore(double vocabularyScore) { this.vocabularyScore = vocabularyScore; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

    public String getTranscription() { return transcription; }
    public void setTranscription(String transcription) { this.transcription = transcription; }

    public float getAnalysisDuration() { return analysisDuration; }
    public void setAnalysisDuration(float analysisDuration) { this.analysisDuration = analysisDuration; }

    public float getVolumeLevel() { return volumeLevel; }
    public void setVolumeLevel(float volumeLevel) { this.volumeLevel = volumeLevel; }
}