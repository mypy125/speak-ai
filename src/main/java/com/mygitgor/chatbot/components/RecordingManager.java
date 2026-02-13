package com.mygitgor.chatbot.components;

import com.mygitgor.service.ChatBotService;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.speech.SpeechRecorder;
import com.mygitgor.state.ChatBotState;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RecordingManager {
    private static final Logger logger = LoggerFactory.getLogger(RecordingManager.class);

    private static final int TIMER_UPDATE_INTERVAL_MS = 100;
    private static final int MAX_RECORDING_DURATION_SECONDS = 300;
    private static final int STOP_TIMEOUT_MS = 100;

    private final Button recordButton;
    private final Button stopButton;
    private final ProgressIndicator recordingIndicator;
    private final Label recordingTimeLabel;
    private final Button analyzeButton;

    private final SpeechRecorder speechRecorder;
    private final ChatBotService chatBotService;
    private final ChatBotState state;
    private final Runnable onRecordingComplete;

    private final AtomicBoolean isTimerRunning = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> timerFuture = new AtomicReference<>(null);
    private final AtomicLong recordingStartTime = new AtomicLong(0);

    private final ThreadPoolManager threadPoolManager;
    private final ScheduledExecutorService scheduledExecutor;

    public RecordingManager(
            Button recordButton,
            Button stopButton,
            ProgressIndicator recordingIndicator,
            Label recordingTimeLabel,
            Button analyzeButton,
            SpeechRecorder speechRecorder,
            ChatBotService chatBotService,
            ChatBotState state,
            Runnable onRecordingComplete) {

        this.recordButton = recordButton;
        this.stopButton = stopButton;
        this.recordingIndicator = recordingIndicator;
        this.recordingTimeLabel = recordingTimeLabel;
        this.analyzeButton = analyzeButton;
        this.speechRecorder = speechRecorder;
        this.chatBotService = chatBotService;
        this.state = state;
        this.onRecordingComplete = onRecordingComplete;

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        initializeUI();
    }

    private void initializeUI() {
        Platform.runLater(() -> {
            if (stopButton != null) stopButton.setDisable(true);
            if (recordingIndicator != null) recordingIndicator.setVisible(false);
            if (recordingTimeLabel != null) recordingTimeLabel.setText("00:00");
            if (analyzeButton != null) analyzeButton.setDisable(true);
        });
    }

    public void startRecording() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        if (state.isRecording()) {
            logger.warn("Запись уже идет");
            return;
        }

        try {
            String audioFile = chatBotService.generateAudioFileName();
            state.setCurrentAudioFile(audioFile);

            speechRecorder.startRecording();
            state.setRecording(true);
            recordingStartTime.set(System.currentTimeMillis());

            updateUIRecordingStarted();

            startTimer();

            logger.info("🔴 Начата запись аудио: {}", audioFile);

        } catch (Exception e) {
            logger.error("Ошибка при начале записи", e);
            state.setRecording(false);
            ErrorHandler.showError("Ошибка записи",
                    "Не удалось начать запись: " + e.getMessage());
        }
    }

    public void stopRecording() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        if (!state.isRecording()) {
            logger.warn("Запись не идет");
            return;
        }

        stopTimer();

        final String currentAudioFile = state.getCurrentAudioFile();

        CompletableFuture.supplyAsync(() -> {
            try {
                return speechRecorder.stopRecording(currentAudioFile);
            } catch (Exception e) {
                logger.error("Ошибка при остановке записи", e);
                return null;
            }
        }, threadPoolManager.getBackgroundExecutor()).thenAccept(audioFile -> {
            state.setRecording(false);

            Platform.runLater(() -> {
                updateUIStopped();

                if (audioFile != null && audioFile.exists()) {
                    handleSuccessfulRecording(audioFile);
                } else {
                    handleFailedRecording();
                }
            });
        }).exceptionally(throwable -> {
            logger.error("Ошибка при остановке записи", throwable);
            Platform.runLater(() -> {
                ErrorHandler.showError("Ошибка записи",
                        "Не удалось остановить запись: " + throwable.getMessage());
                updateUIStopped();
            });
            return null;
        });
    }

    private void updateUIRecordingStarted() {
        Platform.runLater(() -> {
            if (recordButton != null) {
                recordButton.setDisable(true);
                recordButton.setText("🔴 Запись...");
                recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            }
            if (stopButton != null) {
                stopButton.setDisable(false);
                stopButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
            }
            if (recordingIndicator != null) {
                recordingIndicator.setVisible(true);
            }
            if (analyzeButton != null) {
                analyzeButton.setDisable(true);
            }
        });
    }

    private void updateUIStopped() {
        Platform.runLater(() -> {
            if (recordButton != null) {
                recordButton.setDisable(false);
                recordButton.setText("● Запись");
                recordButton.setStyle("");
            }
            if (stopButton != null) {
                stopButton.setDisable(true);
                stopButton.setStyle("");
            }
            if (recordingIndicator != null) {
                recordingIndicator.setVisible(false);
            }
            if (recordingTimeLabel != null) {
                recordingTimeLabel.setText("00:00");
            }
        });
    }

    private void handleSuccessfulRecording(File audioFile) {
        long fileSize = audioFile.length() / 1024;
        logger.info("✅ Запись завершена. Размер: {} KB", fileSize);

        if (analyzeButton != null) {
            analyzeButton.setDisable(false);
            analyzeButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        }

        ErrorHandler.showInfo("Запись завершена",
                String.format("Аудиофайл сохранен (%d KB)\nТеперь вы можете проанализировать запись.", fileSize));

        onRecordingComplete.run();
    }

    private void handleFailedRecording() {
        logger.warn("Запись не удалась или файл не создан");

        if (analyzeButton != null) {
            analyzeButton.setDisable(true);
        }

        state.clearCurrentAudioFile();

        ErrorHandler.showWarning("Запись не удалась",
                "Не удалось сохранить аудиофайл. Попробуйте еще раз.");
    }

    private void startTimer() {
        if (!isTimerRunning.compareAndSet(false, true)) {
            return;
        }

        recordingStartTime.set(System.currentTimeMillis());

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(() -> {
            if (!isTimerRunning.get() || !state.isRecording()) {
                return;
            }

            long elapsedTime = System.currentTimeMillis() - recordingStartTime.get();
            long seconds = elapsedTime / 1000;

            if (seconds > MAX_RECORDING_DURATION_SECONDS) {
                Platform.runLater(() -> {
                    ErrorHandler.showWarning("Максимальная длительность",
                            "Достигнута максимальная длительность записи (" +
                                    MAX_RECORDING_DURATION_SECONDS + " сек)");
                });
                stopRecording();
                return;
            }

            long minutes = seconds / 60;
            final String timeText = String.format("%02d:%02d", minutes, seconds % 60);

            Platform.runLater(() -> {
                if (recordingTimeLabel != null) {
                    recordingTimeLabel.setText(timeText);
                }
            });

        }, 0, TIMER_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        timerFuture.set(future);
        logger.debug("Таймер записи запущен");
    }

    private void stopTimer() {
        isTimerRunning.set(false);

        ScheduledFuture<?> future = timerFuture.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        logger.debug("Таймер записи остановлен");
    }

    public void reset() {
        stopTimer();

        state.clearCurrentAudioFile();

        Platform.runLater(() -> {
            if (recordButton != null) {
                recordButton.setDisable(false);
                recordButton.setText("● Запись");
                recordButton.setStyle("");
            }
            if (stopButton != null) {
                stopButton.setDisable(true);
                stopButton.setStyle("");
            }
            if (recordingIndicator != null) {
                recordingIndicator.setVisible(false);
            }
            if (recordingTimeLabel != null) {
                recordingTimeLabel.setText("00:00");
            }
            if (analyzeButton != null) {
                analyzeButton.setDisable(true);
                analyzeButton.setStyle("");
            }
        });

        logger.debug("RecordingManager сброшен");
    }

    public void forceStop() {
        if (state.isRecording()) {
            logger.info("Принудительная остановка записи");
            stopTimer();

            try {
                speechRecorder.stopRecording(null);
            } catch (Exception e) {
                logger.warn("Ошибка при принудительной остановке записи: {}", e.getMessage());
            }

            state.setRecording(false);
            state.clearCurrentAudioFile();

            Platform.runLater(this::updateUIStopped);
        }
    }

    public boolean isRecording() {
        return state.isRecording();
    }

    public long getCurrentDuration() {
        if (!state.isRecording() || recordingStartTime.get() == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - recordingStartTime.get()) / 1000;
    }

    public String getFormattedDuration() {
        long seconds = getCurrentDuration();
        long minutes = seconds / 60;
        return String.format("%02d:%02d", minutes, seconds % 60);
    }

    @Override
    public String toString() {
        return String.format("RecordingManager{recording=%s, duration=%d сек, file=%s}",
                state.isRecording(), getCurrentDuration(), state.getCurrentAudioFile());
    }
}