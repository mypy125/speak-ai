package com.mygitgor.service.interfaces;

import java.util.concurrent.CompletableFuture;

public interface ITTSService extends AutoCloseable{
    CompletableFuture<Void> speakAsync(String text);
    void stopSpeaking();
    boolean isAvailable();
    @Override
    void close();
}
