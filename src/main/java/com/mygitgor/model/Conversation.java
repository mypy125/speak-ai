package com.mygitgor.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;

@DatabaseTable(tableName = "conversations")
public class Conversation {
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(foreign = true, foreignAutoRefresh = true)
    private User user;

    @DatabaseField(canBeNull = false)
    private String userMessage;

    @DatabaseField(canBeNull = false)
    private String botResponse;

    @DatabaseField
    private String audioPath;

    @DatabaseField
    private String analysisResult;

    @DatabaseField
    private String recommendations;

    @DatabaseField
    private Date timestamp;

    @DatabaseField
    private double pronunciationScore;

    @DatabaseField
    private double grammarScore;

    @DatabaseField
    private double vocabularyScore;

    public Conversation() {
        // ORMLite requires no-arg constructor
    }

    public Conversation(User user, String userMessage, String botResponse) {
        this.user = user;
        this.userMessage = userMessage;
        this.botResponse = botResponse;
        this.timestamp = new Date();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public String getBotResponse() { return botResponse; }
    public void setBotResponse(String botResponse) { this.botResponse = botResponse; }

    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }

    public String getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(String analysisResult) { this.analysisResult = analysisResult; }

    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public double getPronunciationScore() { return pronunciationScore; }
    public void setPronunciationScore(double pronunciationScore) { this.pronunciationScore = pronunciationScore; }

    public double getGrammarScore() { return grammarScore; }
    public void setGrammarScore(double grammarScore) { this.grammarScore = grammarScore; }

    public double getVocabularyScore() { return vocabularyScore; }
    public void setVocabularyScore(double vocabularyScore) { this.vocabularyScore = vocabularyScore; }
}
