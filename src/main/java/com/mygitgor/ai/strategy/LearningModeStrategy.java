package com.mygitgor.ai.strategy;

import com.mygitgor.ai.strategy.core.*;

import java.util.concurrent.CompletableFuture;

public interface LearningModeStrategy {
    LearningMode getMode();
    CompletableFuture<LearningResponse> processInput(String userInput, LearningContext context);
    CompletableFuture<String> generateResponse(String userInput, LearningContext context);
    CompletableFuture<LearningProgress> analyzeProgress(LearningContext context);
    CompletableFuture<LearningTask> getNextTask(LearningContext context);
    boolean isSupported();
    String getStrategyName();
}