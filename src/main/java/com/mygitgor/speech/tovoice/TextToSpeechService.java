package com.mygitgor.speech.tovoice;

import java.util.concurrent.CompletableFuture;

public interface TextToSpeechService extends AutoCloseable {
    void speak(String text);
    CompletableFuture<Void> speakAsync(String text);
    void stopSpeaking();
    boolean isAvailable();
}
