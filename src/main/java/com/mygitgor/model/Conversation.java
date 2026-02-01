package com.mygitgor.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;
import com.j256.ormlite.field.DataType;

@DatabaseTable(tableName = "conversations")
public class Conversation {
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "user_id", canBeNull = false)
    private User user;

    @DatabaseField(canBeNull = false, width = 2000)
    private String userMessage;

    @DatabaseField(canBeNull = false, width = 4000)
    private String botResponse;

    @DatabaseField
    private String audioPath;

    @DatabaseField(width = 4000)
    private String analysisResult;

    @DatabaseField(width = 4000)
    private String recommendations;

    @DatabaseField(dataType = DataType.DATE_LONG)
    private Date timestamp;

    @DatabaseField
    private double pronunciationScore;

    @DatabaseField
    private double grammarScore;

    @DatabaseField
    private double vocabularyScore;

    public Conversation() {
        this.timestamp = new Date();
    }

    public Conversation(User user, String userMessage, String botResponse) {
        this();
        this.user = user;
        this.userMessage = userMessage;
        this.botResponse = botResponse;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getUserId() {
        return user != null ? user.getId() : 1;
    }

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