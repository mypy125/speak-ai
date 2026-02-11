package com.mygitgor.service.interfaces;

import com.mygitgor.model.Conversation;
import com.mygitgor.service.ChatBotService;
import com.mygitgor.service.components.ResponseMode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IChatBotService extends AutoCloseable {
    ChatBotService.ChatResponse processUserInput(String text, String audioFilePath, ResponseMode responseMode);
    CompletableFuture<Void> speakTextAsync(String text);
    void stopSpeaking();
    boolean isTTSAvailable();
    List<Conversation> getConversationHistory();
    void clearHistory();
    String generateAudioFileName();
    boolean isAiServiceAvailable();
}
