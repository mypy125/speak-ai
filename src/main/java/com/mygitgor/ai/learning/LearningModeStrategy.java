package com.mygitgor.ai.learning;

import com.mygitgor.model.LearningContext;
import com.mygitgor.model.LearningMode;
import com.mygitgor.model.LearningResponse;
import com.mygitgor.model.LearningTask;
import com.mygitgor.model.core.LearningProgress;

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