package com.mygitgor.service.interfaces;

import com.mygitgor.model.EnhancedSpeechAnalysis;

public interface IAudioAnalysisService {
    EnhancedSpeechAnalysis analyzeAudio(String audioFilePath, String text);
}
