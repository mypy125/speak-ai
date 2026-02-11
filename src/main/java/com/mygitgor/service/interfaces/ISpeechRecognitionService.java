package com.mygitgor.service.interfaces;

import java.util.List;
import java.util.Map;

public interface ISpeechRecognitionService {
    String recognizeSpeechInRealTime();
    void testMicrophone(int durationSeconds);
    void setMicrophoneSensitivity(double sensitivity);
    double getMicrophoneSensitivity(); // Добавляем这个方法
    void switchSpeechLanguage(String languageCode);
    List<String> getSupportedLanguages();
    Map<String, String> getSupportedLanguagesWithNames();
    String getCurrentSpeechLanguage(); // Добавляем这个方法
    String getCurrentSpeechLanguageName(); // Добавляем这个方法
}

