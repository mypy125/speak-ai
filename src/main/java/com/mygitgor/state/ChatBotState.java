package com.mygitgor.state;

import com.mygitgor.service.components.ResponseMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ChatBotState {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotState.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean isPlayingSpeech = new AtomicBoolean(false);
    private final AtomicBoolean isAiServiceAvailable = new AtomicBoolean(false);
    private final AtomicBoolean isRecording = new AtomicBoolean(false);

    private final AtomicReference<String> lastBotResponse = new AtomicReference<>("");
    private final AtomicReference<ResponseMode> currentResponseMode =
            new AtomicReference<>(ResponseMode.TEXT);
    private final AtomicReference<String> currentAudioFile = new AtomicReference<>();

    private final Statistics statistics = new Statistics();

    // ========================================
    // Statistics inner class
    // ========================================

    public static class Statistics {
        private final AtomicInteger messagesCount = new AtomicInteger(0);
        private final AtomicInteger analysisCount = new AtomicInteger(0);
        private final AtomicInteger recordingsCount = new AtomicInteger(0);

        public int incrementMessages() { return messagesCount.incrementAndGet(); }
        public int incrementAnalysis() { return analysisCount.incrementAndGet(); }
        public int incrementRecordings() { return recordingsCount.incrementAndGet(); }

        public int getMessagesCount() { return messagesCount.get(); }
        public int getAnalysisCount() { return analysisCount.get(); }
        public int getRecordingsCount() { return recordingsCount.get(); }

        public void reset() {
            messagesCount.set(0);
            analysisCount.set(0);
            recordingsCount.set(0);
        }

        public Statistics snapshot() {
            Statistics snapshot = new Statistics();
            snapshot.messagesCount.set(this.messagesCount.get());
            snapshot.analysisCount.set(this.analysisCount.get());
            snapshot.recordingsCount.set(this.recordingsCount.get());
            return snapshot;
        }
    }

    // ========================================
    // State getters/setters
    // ========================================

    public boolean isClosed() {
        return closed.get();
    }

    public boolean setClosed(boolean closed) {
        boolean wasClosed = this.closed.getAndSet(closed);
        if (wasClosed != closed) {
            logger.debug("Состояние closed изменено: {} -> {}", wasClosed, closed);
        }
        return wasClosed;
    }

    public boolean isPlayingSpeech() {
        return isPlayingSpeech.get();
    }

    public boolean setPlayingSpeech(boolean playing) {
        boolean wasPlaying = isPlayingSpeech.getAndSet(playing);
        if (wasPlaying != playing) {
            logger.debug("Состояние озвучки: {}", playing ? "▶️ Начало" : "⏹️ Остановка");
        }
        return wasPlaying;
    }

    public boolean isAiServiceAvailable() {
        return isAiServiceAvailable.get();
    }

    public void setAiServiceAvailable(boolean available) {
        boolean wasAvailable = isAiServiceAvailable.getAndSet(available);
        if (wasAvailable != available) {
            logger.info("AI сервис: {}", available ? "✅ Доступен" : "❌ Недоступен");
        }
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    public boolean setRecording(boolean recording) {
        boolean wasRecording = isRecording.getAndSet(recording);
        if (wasRecording != recording) {
            logger.info("Запись: {}", recording ? "🔴 Начата" : "⏹️ Остановлена");
        }
        return wasRecording;
    }

    public String getLastBotResponse() {
        return lastBotResponse.get();
    }

    public String setLastBotResponse(String response) {
        return lastBotResponse.getAndSet(response);
    }

    public ResponseMode getCurrentResponseMode() {
        return currentResponseMode.get();
    }

    public ResponseMode setCurrentResponseMode(ResponseMode mode) {
        ResponseMode previous = currentResponseMode.getAndSet(mode);
        if (previous != mode) {
            logger.info("Режим ответа: {}", mode == ResponseMode.VOICE ? "🔊 Голосовой" : "📝 Текстовый");
        }
        return previous;
    }

    public String getCurrentAudioFile() {
        return currentAudioFile.get();
    }

    public String setCurrentAudioFile(String file) {
        return currentAudioFile.getAndSet(file);
    }

    public void clearCurrentAudioFile() {
        currentAudioFile.set(null);
    }

    // ========================================
    // Statistics methods
    // ========================================

    public Statistics getStatistics() {
        return statistics.snapshot();
    }

    public void incrementMessagesCount() {
        statistics.incrementMessages();
    }

    public void incrementAnalysisCount() {
        statistics.incrementAnalysis();
    }

    public void incrementRecordingsCount() {
        statistics.incrementRecordings();
    }

    public void resetStatistics() {
        statistics.reset();
        logger.info("Статистика сброшена");
    }

    // ========================================
    // Utility methods
    // ========================================

    public boolean canSendMessage() {
        return !isClosed() &&
                (hasTextInput() || hasAudioFile());
    }

    public boolean hasTextInput() {
        return false; // Will be set by controller
    }

    public boolean hasAudioFile() {
        return currentAudioFile.get() != null &&
                !currentAudioFile.get().isEmpty();
    }

    public void reset() {
        setLastBotResponse("");
        clearCurrentAudioFile();
        setPlayingSpeech(false);
        setRecording(false);
        logger.debug("Состояние сброшено");
    }
}
