package com.mygitgor.service;

import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.config.AppConstants;
import com.mygitgor.ai.AiService;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.model.User;
import com.mygitgor.service.components.ConversationManager;
import com.mygitgor.service.components.ResponseFormatter;
import com.mygitgor.service.components.ResponseMode;
import com.mygitgor.service.components.SpeechServiceManager;
import com.mygitgor.service.interfaces.*;

import com.mygitgor.speech.SpeechRecorder;
import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatBotService implements IChatBotService, ISpeechRecognitionService, AiService, ITTSService, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotService.class);

    private static final int SPEECH_TIMEOUT_SECONDS = 30;

    private final AiService aiService;
    private final IAudioAnalysisService audioAnalysisService;
    private final IRecommendationService recommendationService;
    private ITTSService ttsService;
    private final SpeechServiceManager speechServiceManager;

    private final ConversationManager conversationManager;
    private final ResponseFormatter responseFormatter;
    private final TextCleanerService textCleaner;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean isSpeaking = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> currentSpeechFuture = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    public ChatBotService(AiService aiService,
                          IAudioAnalysisService audioAnalysisService,
                          IRecommendationService recommendationService,
                          ITTSService ttsService,
                          SpeechRecorder speechRecorder) {
        this.aiService = aiService;
        this.audioAnalysisService = audioAnalysisService;
        this.recommendationService = recommendationService;
        this.ttsService = ttsService;

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        this.speechServiceManager = new SpeechServiceManager(speechRecorder);
        this.conversationManager = new ConversationManager();
        this.responseFormatter = new ResponseFormatter();
        this.textCleaner = new TextCleanerService();

        logger.info("ChatBotService инициализирован с {}, {} и {}",
                aiService.getClass().getSimpleName(),
                ttsService.getClass().getSimpleName(),
                speechRecorder.getClass().getSimpleName());
    }

    @Override
    public ChatResponse processUserInput(String text, String audioFilePath, ResponseMode responseMode) {
        checkClosed();

        try {
            logger.info("Обработка ввода. Текст: {}, Аудио: {}, Режим: {}",
                    text != null ? text.substring(0, Math.min(50, text.length())) + "..." : "null",
                    audioFilePath, responseMode);

            ResponseFormatter.ResponseMode mode = convertResponseMode(responseMode);

            String processedText = text;
            EnhancedSpeechAnalysis speechAnalysis = null;

            if (audioFilePath != null && new File(audioFilePath).exists()) {
                SpeechProcessingResult result = processAudio(audioFilePath, text);
                processedText = result.getText();
                speechAnalysis = result.getAnalysis();
            }

            if (processedText == null || processedText.trim().isEmpty()) {
                processedText = AppConstants.DEFAULT_SPEECH_TEXT;
            }

            String textAnalysis = aiService.analyzeText(processedText);
            String botResponse = aiService.generateBotResponse(processedText, speechAnalysis);

            List<RecommendationEngine.PersonalizedRecommendation> recommendations = null;
            RecommendationEngine.WeeklyLearningPlan weeklyPlan = null;

            if (speechAnalysis != null) {
                recommendations = recommendationService.generateRecommendations(speechAnalysis);
                weeklyPlan = recommendationService.generateWeeklyPlan(speechAnalysis);
            }

            final String finalProcessedText = processedText;
            final String finalBotResponse = botResponse;
            final EnhancedSpeechAnalysis finalSpeechAnalysis = speechAnalysis;
            final String finalAudioFilePath = audioFilePath;

            CompletableFuture.runAsync(() -> {
                conversationManager.saveConversation(
                        finalProcessedText,
                        finalBotResponse,
                        finalSpeechAnalysis,
                        finalAudioFilePath
                );
            }, backgroundExecutor);

            String fullResponse = responseFormatter.formatResponse(
                    botResponse, textAnalysis, speechAnalysis,
                    recommendations, weeklyPlan, mode,
                    conversationManager.getCurrentUser()
            );

            if (responseMode == ResponseMode.VOICE && ttsService != null && ttsService.isAvailable()) {
                speakResponseAsync(fullResponse);
            }

            return new ChatResponse(fullResponse, speechAnalysis, recommendations, weeklyPlan);

        } catch (Exception e) {
            logger.error("Ошибка обработки ввода", e);
            return createErrorResponse(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> speakTextAsync(String text) {
        checkClosed();
        if (ttsService == null || !ttsService.isAvailable()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("TTS сервис недоступен"));
        }

        String cleanText = textCleaner.cleanForSpeech(text);
        if (cleanText.isEmpty()) {
            logger.warn("Пустой текст для озвучки");
            return CompletableFuture.completedFuture(null);
        }

        return ttsService.speakAsync(cleanText);
    }

    @Override
    public void stopSpeaking() {
        CompletableFuture<Void> future = currentSpeechFuture.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        if (ttsService != null) {
            ttsService.stopSpeaking();
        }

        isSpeaking.set(false);

        logger.info("⏹️ Озвучка остановлена");
    }

    @Override
    public boolean isTTSAvailable() {
        return ttsService != null && ttsService.isAvailable() && !closed.get();
    }

    @Override
    public List<Conversation> getConversationHistory() {
        checkClosed();
        return conversationManager.getHistory();
    }

    @Override
    public void clearHistory() {
        checkClosed();
        conversationManager.clearHistory();
    }

    @Override
    public String generateAudioFileName() {
        return speechServiceManager.generateAudioFileName();
    }

    @Override
    public boolean isAiServiceAvailable() {
        return aiService != null && aiService.isAvailable() && !closed.get();
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        checkClosed();
        if (ttsService == null || !ttsService.isAvailable()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("TTS сервис недоступен"));
        }

        CompletableFuture<Void> future = ttsService.speakAsync(text);
        currentSpeechFuture.set(future);

        return future;
    }

    @Override
    public String analyzeText(String text) {
        return aiService.analyzeText(text);
    }

    @Override
    public SpeechAnalysis analyzePronunciation(String text, String audioPath) {
        return aiService.analyzePronunciation(text, audioPath);
    }

    @Override
    public String generateBotResponse(String userMessage, SpeechAnalysis analysis) {
        return aiService.generateBotResponse(userMessage, analysis);
    }

    @Override
    public String generateExercise(String topic, String difficulty) {
        return aiService.generateExercise(topic, difficulty);
    }

    @Override
    public boolean isAvailable() {
        return ttsService != null && ttsService.isAvailable() && !closed.get();
    }

    @Override
    public String recognizeSpeechInRealTime() {
        checkClosed();
        return speechServiceManager.recognizeSpeechInRealTime();
    }

    @Override
    public void testMicrophone(int durationSeconds) {
        checkClosed();
        speechServiceManager.testMicrophone(durationSeconds);
    }

    @Override
    public void setMicrophoneSensitivity(double sensitivity) {
        speechServiceManager.setMicrophoneSensitivity(sensitivity);
    }

    @Override
    public double getMicrophoneSensitivity() {
        return speechServiceManager.getMicrophoneSensitivity();
    }

    @Override
    public void switchSpeechLanguage(String languageCode) {
        speechServiceManager.switchSpeechLanguage(languageCode);
    }

    @Override
    public List<String> getSupportedLanguages() {
        return speechServiceManager.getSupportedLanguages();
    }

    @Override
    public Map<String, String> getSupportedLanguagesWithNames() {
        return speechServiceManager.getSupportedLanguagesWithNames();
    }

    @Override
    public String getCurrentSpeechLanguage() {
        return speechServiceManager.getCurrentSpeechLanguage();
    }

    @Override
    public String getCurrentSpeechLanguageName() {
        return speechServiceManager.getCurrentSpeechLanguageName();
    }

    public void speakText(String text) {
        checkClosed();
        if (ttsService == null) {
            throw new IllegalStateException("TTS сервис недоступен");
        }

        String cleanText = textCleaner.cleanForSpeech(text);
        if (!cleanText.isEmpty()) {
            CompletableFuture<Void> future = ttsService.speakAsync(cleanText);
            try {
                future.get(SPEECH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.error("Таймаут озвучки");
                future.cancel(true);
            } catch (Exception e) {
                logger.error("Ошибка озвучки", e);
            }
        }
    }

    public SpeechToTextService getSpeechToTextService() {
        return speechServiceManager.getService();
    }

    public User getCurrentUser() {
        return conversationManager.getCurrentUser();
    }

    public String getCurrentUserId() {
        try {
            User user = conversationManager.getCurrentUser();
            if (user != null) {
                // ID теперь int, не может быть null
                return String.valueOf(user.getId());
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении ID пользователя", e);
        }
        return null;
    }

    public String getCurrentUserIdSafely() {
        // Пробуем получить существующего пользователя
        String userId = getCurrentUserId();
        if (userId != null && !userId.trim().isEmpty()) {
            logger.debug("Получен ID существующего пользователя: {}", userId);
            return userId;
        }

        logger.warn("Пользователь не найден, создаем временного");

        // Создаем временного пользователя
        User tempUser = createTemporaryUser();
        conversationManager.setCurrentUser(tempUser);
        String tempId = String.valueOf(tempUser.getId());
        logger.info("Создан временный пользователь с ID: {}", tempId);
        return tempId;
    }

    private User createTemporaryUser() {
        User user = new User();
        user.setId(-1 * (int)(System.currentTimeMillis() % 10000));
        user.setUsername("Guest_" + System.currentTimeMillis());
        user.setEmail("guest_" + System.currentTimeMillis() + "@temp.com");
        user.setLanguageLevel("B1");
        user.setNativeLanguage("Russian");
        user.setCreatedAt(new Date());
        return user;
    }

    public void setCurrentUser(User user) {
        conversationManager.setCurrentUser(user);
    }

    public String getTTSStatus() {
        if (ttsService == null) return "TTS сервис не инициализирован";
        return ttsService.isAvailable()
                ? "✅ TTS доступен"
                : "⚠️ TTS в демо-режиме";
    }

    public void setTextToSpeechService(ITTSService newService) {
        if (newService == null) {
            throw new IllegalArgumentException("TTS сервис не может быть null");
        }

        if (ttsService != null && ttsService != newService) {
            try {
                ttsService.close();
            } catch (Exception e) {
                logger.error("Ошибка закрытия старого TTS сервиса", e);
            }
        }

        this.ttsService = newService;
        logger.info("TTS сервис обновлен: {}", newService.getClass().getSimpleName());
    }

    public boolean isClosed() {
        return closed.get();
    }

    private void checkClosed() {
        if (closed.get()) {
            throw new IllegalStateException("ChatBotService закрыт");
        }
    }

    private ResponseFormatter.ResponseMode convertResponseMode(ResponseMode mode) {
        return mode == ResponseMode.VOICE
                ? ResponseFormatter.ResponseMode.VOICE
                : ResponseFormatter.ResponseMode.TEXT;
    }

    private SpeechProcessingResult processAudio(String audioFilePath, String originalText) {
        try {
            SpeechToTextService.SpeechRecognitionResult recognition =
                    speechServiceManager.transcribe(audioFilePath);

            String recognizedText = recognition.isConfident() && !recognition.getText().isEmpty()
                    ? recognition.getText()
                    : (originalText != null && !originalText.isEmpty() ? originalText : AppConstants.DEFAULT_SPEECH_TEXT);

            EnhancedSpeechAnalysis analysis =
                    audioAnalysisService.analyzeAudio(audioFilePath, recognizedText);

            logger.info("Анализ аудио: оценка {}/100",
                    String.format("%.1f", analysis.getOverallScore()));

            return new SpeechProcessingResult(recognizedText, analysis);

        } catch (Exception e) {
            logger.error("Ошибка обработки аудио", e);
            return new SpeechProcessingResult(originalText != null ? originalText : AppConstants.DEFAULT_SPEECH_TEXT, null);
        }
    }

    private void speakResponseAsync(String response) {
        if (ttsService == null || !ttsService.isAvailable() || closed.get()) {
            return;
        }

        if (!isSpeaking.compareAndSet(false, true)) {
            logger.debug("Озвучка уже выполняется, игнорируем новый запрос");
            return;
        }

        ThreadPoolManager.runWithDelay(() -> {
            if (closed.get() || Thread.currentThread().isInterrupted()) {
                isSpeaking.set(false);
                return;
            }

            String cleanText = textCleaner.cleanForSpeech(response);
            if (!cleanText.isEmpty()) {
                CompletableFuture<Void> future = ttsService.speakAsync(cleanText);
                currentSpeechFuture.set(future);

                future.thenRun(() -> {
                    isSpeaking.set(false);
                    logger.info("✅ Озвучка завершена");
                }).exceptionally(e -> {
                    isSpeaking.set(false);
                    if (!(e instanceof CancellationException)) {
                        logger.warn("Ошибка озвучки: {}", e.getMessage());
                    }
                    return null;
                });
            } else {
                isSpeaking.set(false);
            }
        }, AppConstants.TTS_DELAY_MS);
    }

    private ChatResponse createErrorResponse(String errorMessage) {
        String message = "Извините, произошла ошибка: " + errorMessage +
                "\nПожалуйста, попробуйте еще раз.";
        return new ChatResponse(message, null, null, null);
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;

        logger.info("Закрытие ChatBotService...");

        stopSpeaking();

        ErrorHandler.safeClose(speechServiceManager.getService(), "SpeechToTextService");
        ErrorHandler.safeClose(ttsService, "TTSService");
        if (aiService instanceof AutoCloseable) {
            ErrorHandler.safeClose((AutoCloseable) aiService, "AiService");
        }

        logger.info("ChatBotService закрыт");
    }

    private static class SpeechProcessingResult {
        private final String text;
        private final EnhancedSpeechAnalysis analysis;

        public SpeechProcessingResult(String text, EnhancedSpeechAnalysis analysis) {
            this.text = text;
            this.analysis = analysis;
        }

        public String getText() { return text; }
        public EnhancedSpeechAnalysis getAnalysis() { return analysis; }
    }

    public static class ChatResponse {
        private final String fullResponse;
        private final SpeechAnalysis speechAnalysis;
        private final List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations;
        private final RecommendationEngine.WeeklyLearningPlan weeklyPlan;

        public ChatResponse(String fullResponse,
                            SpeechAnalysis speechAnalysis,
                            List<RecommendationEngine.PersonalizedRecommendation> personalizedRecommendations,
                            RecommendationEngine.WeeklyLearningPlan weeklyPlan) {
            this.fullResponse = fullResponse;
            this.speechAnalysis = speechAnalysis;
            this.personalizedRecommendations = personalizedRecommendations;
            this.weeklyPlan = weeklyPlan;
        }

        public String getFullResponse() { return fullResponse; }
        public SpeechAnalysis getSpeechAnalysis() { return speechAnalysis; }
        public List<RecommendationEngine.PersonalizedRecommendation> getPersonalizedRecommendations() {
            return personalizedRecommendations;
        }
        public RecommendationEngine.WeeklyLearningPlan getWeeklyPlan() { return weeklyPlan; }
    }
}