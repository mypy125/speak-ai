package com.mygitgor.chatbot.components;

import com.mygitgor.state.ChatBotState;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class StatisticsManager {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsManager.class);

    private static final int UI_UPDATE_DELAY_MS = 100;
    private static final int MAX_BATCH_SIZE = 10;
    private static final int LABEL_FONT_SIZE = 14;
    private static final String LABEL_TEXT_COLOR = "#2c3e50";
    private static final int KILO = 1000;
    private static final int MILLION = 1_000_000;

    private final Label messagesCountLabel;
    private final Label analysisCountLabel;
    private final Label recordingsCountLabel;

    private final ChatBotState state;

    private final AtomicBoolean isUpdateScheduled = new AtomicBoolean(false);
    private final AtomicBoolean needsUpdate = new AtomicBoolean(false);
    private final AtomicReference<StatisticsSnapshot> lastSnapshot = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ScheduledExecutorService scheduledExecutor;

    public StatisticsManager(Label messagesCountLabel,
                             Label analysisCountLabel,
                             Label recordingsCountLabel,
                             ChatBotState state) {
        this.messagesCountLabel = messagesCountLabel;
        this.analysisCountLabel = analysisCountLabel;
        this.recordingsCountLabel = recordingsCountLabel;
        this.state = state;

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        initializeLabels();
        updateAll();
    }

    private void initializeLabels() {
        Platform.runLater(() -> {
            setLabelStyle(messagesCountLabel);
            setLabelStyle(analysisCountLabel);
            setLabelStyle(recordingsCountLabel);
        });
    }

    private void setLabelStyle(Label label) {
        if (label != null) {
            label.setStyle(
                    "-fx-font-size: " + LABEL_FONT_SIZE + "px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-text-fill: " + LABEL_TEXT_COLOR + ";"
            );
        }
    }

    public void updateAll() {
        if (Platform.isFxApplicationThread()) {
            performUpdate();
        } else {
            scheduleUpdate();
        }
    }

    public void forceUpdate() {
        needsUpdate.set(true);
        isUpdateScheduled.set(false);
        Platform.runLater(this::performUpdate);
    }

    public void onMessageSent() {
        CompletableFuture.runAsync(() -> {
            state.incrementMessagesCount();
            logger.debug("Сообщение отправлено. Всего: {}", state.getStatistics().getMessagesCount());
            scheduleUpdate();
        }, threadPoolManager.getBackgroundExecutor());
    }

    public void onAnalysisPerformed() {
        CompletableFuture.runAsync(() -> {
            state.incrementAnalysisCount();
            logger.debug("Анализ выполнен. Всего: {}", state.getStatistics().getAnalysisCount());
            scheduleUpdate();
        }, threadPoolManager.getBackgroundExecutor());
    }

    public void onRecordingStarted() {
        CompletableFuture.runAsync(() -> {
            state.incrementRecordingsCount();
            logger.debug("Запись начата. Всего: {}", state.getStatistics().getRecordingsCount());
            scheduleUpdate();
        }, threadPoolManager.getBackgroundExecutor());
    }

    public void batchUpdate(StatisticsUpdate update) {
        CompletableFuture.runAsync(() -> {
            if (update.incrementMessages > 0) {
                for (int i = 0; i < update.incrementMessages; i++) {
                    state.incrementMessagesCount();
                }
            }
            if (update.incrementAnalysis > 0) {
                for (int i = 0; i < update.incrementAnalysis; i++) {
                    state.incrementAnalysisCount();
                }
            }
            if (update.incrementRecordings > 0) {
                for (int i = 0; i < update.incrementRecordings; i++) {
                    state.incrementRecordingsCount();
                }
            }

            logger.debug("Пакетное обновление: {}", update);
            scheduleUpdate();
        }, threadPoolManager.getBackgroundExecutor());
    }

    public void reset() {
        CompletableFuture.runAsync(() -> {
            state.resetStatistics();
            lastSnapshot.set(null);
            logger.info("Статистика сброшена");
            scheduleUpdate();
        }, threadPoolManager.getBackgroundExecutor());
    }

    public StatisticsSnapshot getCurrentStatistics() {
        return new StatisticsSnapshot(state.getStatistics());
    }

    public boolean hasChanged() {
        StatisticsSnapshot current = new StatisticsSnapshot(state.getStatistics());
        StatisticsSnapshot last = lastSnapshot.get();
        return last == null || !last.equals(current);
    }

    public String getStatisticsString() {
        StatisticsSnapshot stats = getCurrentStatistics();
        return String.format("📊 Сообщений: %d | Анализов: %d | Записей: %d",
                stats.getMessagesCount(), stats.getAnalysisCount(), stats.getRecordingsCount());
    }

    public boolean isEmpty() {
        StatisticsSnapshot stats = getCurrentStatistics();
        return stats.getMessagesCount() == 0 &&
                stats.getAnalysisCount() == 0 &&
                stats.getRecordingsCount() == 0;
    }

    private void scheduleUpdate() {
        needsUpdate.set(true);

        if (isUpdateScheduled.compareAndSet(false, true)) {
            scheduledExecutor.schedule(() -> {
                if (needsUpdate.getAndSet(false)) {
                    Platform.runLater(this::performUpdate);
                }
                isUpdateScheduled.set(false);
            }, UI_UPDATE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void performUpdate() {
        try {
            ChatBotState.Statistics stats = state.getStatistics();
            StatisticsSnapshot snapshot = new StatisticsSnapshot(stats);

            StatisticsSnapshot last = lastSnapshot.get();
            if (last != null && last.equals(snapshot)) {
                return;
            }

            lastSnapshot.set(snapshot);

            if (messagesCountLabel != null) {
                messagesCountLabel.setText(formatNumber(stats.getMessagesCount()));
            }
            if (analysisCountLabel != null) {
                analysisCountLabel.setText(formatNumber(stats.getAnalysisCount()));
            }
            if (recordingsCountLabel != null) {
                recordingsCountLabel.setText(formatNumber(stats.getRecordingsCount()));
            }

            logger.trace("Статистика обновлена: {}", snapshot);
        } catch (Exception e) {
            logger.error("Ошибка при обновлении статистики", e);
        }
    }

    private String formatNumber(int number) {
        if (number < KILO) {
            return String.valueOf(number);
        } else if (number < MILLION) {
            return String.format("%.1fk", number / (double) KILO);
        } else {
            return String.format("%.1fm", number / (double) MILLION);
        }
    }

    public static class StatisticsSnapshot {
        private final int messagesCount;
        private final int analysisCount;
        private final int recordingsCount;

        public StatisticsSnapshot(ChatBotState.Statistics stats) {
            this.messagesCount = stats.getMessagesCount();
            this.analysisCount = stats.getAnalysisCount();
            this.recordingsCount = stats.getRecordingsCount();
        }

        public int getMessagesCount() { return messagesCount; }
        public int getAnalysisCount() { return analysisCount; }
        public int getRecordingsCount() { return recordingsCount; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            StatisticsSnapshot that = (StatisticsSnapshot) obj;
            return messagesCount == that.messagesCount &&
                    analysisCount == that.analysisCount &&
                    recordingsCount == that.recordingsCount;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(messagesCount, analysisCount, recordingsCount);
        }

        @Override
        public String toString() {
            return String.format("Stats{msg=%d, anl=%d, rec=%d}",
                    messagesCount, analysisCount, recordingsCount);
        }
    }

    public static class StatisticsUpdate {
        private final int incrementMessages;
        private final int incrementAnalysis;
        private final int incrementRecordings;

        public StatisticsUpdate(int incrementMessages, int incrementAnalysis, int incrementRecordings) {
            this.incrementMessages = Math.min(incrementMessages, MAX_BATCH_SIZE);
            this.incrementAnalysis = Math.min(incrementAnalysis, MAX_BATCH_SIZE);
            this.incrementRecordings = Math.min(incrementRecordings, MAX_BATCH_SIZE);
        }

        public static StatisticsUpdate message() {
            return new StatisticsUpdate(1, 0, 0);
        }

        public static StatisticsUpdate analysis() {
            return new StatisticsUpdate(0, 1, 0);
        }

        public static StatisticsUpdate recording() {
            return new StatisticsUpdate(0, 0, 1);
        }

        public static StatisticsUpdate all() {
            return new StatisticsUpdate(1, 1, 1);
        }

        @Override
        public String toString() {
            return String.format("Update{msg=%d, anl=%d, rec=%d}",
                    incrementMessages, incrementAnalysis, incrementRecordings);
        }
    }
}