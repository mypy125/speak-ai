package com.mygitgor.speech.tovoice;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface TextToSpeechService extends AutoCloseable {
    void speak(String text);
    CompletableFuture<Void> speakAsync(String text);
    void stopSpeaking();
    boolean isAvailable();
    default Map<String, String> getAvailableVoices() {
        return Collections.emptyMap(); // Реализация по умолчанию
    }
}
