package com.mygitgor.ai;

import com.mygitgor.model.SpeechAnalysis;

public interface AiService {
    String analyzeText(String text);
    SpeechAnalysis analyzePronunciation(String text, String audioPath);
    String generateBotResponse(String userMessage, SpeechAnalysis analysis);
    String generateExercise(String topic, String difficulty);
    boolean isAvailable();
}
