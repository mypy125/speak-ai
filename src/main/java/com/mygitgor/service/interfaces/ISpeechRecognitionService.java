package com.mygitgor.service.interfaces;

import java.util.List;
import java.util.Map;

public interface ISpeechRecognitionService {
    String recognizeSpeechInRealTime();
    void testMicrophone(int durationSeconds);
    void setMicrophoneSensitivity(double sensitivity);
    double getMicrophoneSensitivity();
    void switchSpeechLanguage(String languageCode);
    List<String> getSupportedLanguages();
    Map<String, String> getSupportedLanguagesWithNames();
    String getCurrentSpeechLanguage();
    String getCurrentSpeechLanguageName();
}

