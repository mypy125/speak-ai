package com.mygitgor.chatbot.components;

import com.mygitgor.service.ChatBotService;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.speech.SpeechRecorder;
import com.mygitgor.state.ChatBotState;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingManager {
    private static final Logger logger = LoggerFactory.getLogger(RecordingManager.class);

    // UI Elements
    private final Button recordButton;
    private final Button stopButton;
    private final ProgressIndicator recordingIndicator;
    private final Label recordingTimeLabel;
    private final Button analyzeButton;

    // Dependencies
    private final SpeechRecorder speechRecorder;
    private final ChatBotService chatBotService;
    private final ChatBotState state;
    private final Runnable onRecordingComplete;

    // State
    private Thread recordingTimerThread;
    private AtomicBoolean isTimerRunning = new AtomicBoolean(false);

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

        initializeUI();
    }

    private void initializeUI() {
        if (stopButton != null) stopButton.setDisable(true);
        if (recordingIndicator != null) recordingIndicator.setVisible(false);
        if (recordingTimeLabel != null) recordingTimeLabel.setText("00:00");
        if (analyzeButton != null) analyzeButton.setDisable(true);
    }

    public void startRecording() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        try {
            // Generate filename
            String audioFile = chatBotService.generateAudioFileName();
            state.setCurrentAudioFile(audioFile);

            // Start recording
            speechRecorder.startRecording();
            state.setRecording(true);

            // Update UI
            Platform.runLater(() -> {
                if (recordButton != null) {
                    recordButton.setDisable(true);
                    recordButton.setText("🔴 Запись...");
                }
                if (stopButton != null) stopButton.setDisable(false);
                if (recordingIndicator != null) recordingIndicator.setVisible(true);
                if (analyzeButton != null) analyzeButton.setDisable(true);

                startTimer();
            });

            logger.info("Начата запись аудио: {}", audioFile);

        } catch (Exception e) {
            logger.error("Ошибка при начале записи", e);
            ErrorHandler.showError("Ошибка записи",
                    "Не удалось начать запись: " + e.getMessage());
        }
    }

    public void stopRecording() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        try {
            // Stop recording
            File audioFile = speechRecorder.stopRecording(state.getCurrentAudioFile());
            state.setRecording(false);

            // Stop timer
            stopTimer();

            // Update UI
            Platform.runLater(() -> {
                if (recordButton != null) {
                    recordButton.setDisable(false);
                    recordButton.setText("● Запись");
                }
                if (stopButton != null) stopButton.setDisable(true);
                if (recordingIndicator != null) recordingIndicator.setVisible(false);
                if (recordingTimeLabel != null) recordingTimeLabel.setText("00:00");

                // Enable analyze button if recording successful
                if (audioFile != null && audioFile.exists() && analyzeButton != null) {
                    analyzeButton.setDisable(false);
                }
            });

            if (audioFile != null && audioFile.exists()) {
                long fileSize = audioFile.length() / 1024;
                logger.info("Запись завершена. Размер: {} KB", fileSize);

                Platform.runLater(() -> {
                    ErrorHandler.showInfo("Запись завершена",
                            String.format("Аудиофайл сохранен (%d KB)", fileSize));
                });

                onRecordingComplete.run();
            }

        } catch (Exception e) {
            logger.error("Ошибка при остановке записи", e);
            ErrorHandler.showError("Ошибка записи",
                    "Не удалось остановить запись: " + e.getMessage());
        }
    }

    private void startTimer() {
        if (isTimerRunning.get()) return;

        isTimerRunning.set(true);
        recordingTimerThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();

            while (isTimerRunning.get() && state.isRecording()) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                long seconds = elapsedTime / 1000;
                long minutes = seconds / 60;

                final String timeText = String.format("%02d:%02d", minutes, seconds % 60);

                Platform.runLater(() -> {
                    if (recordingTimeLabel != null) {
                        recordingTimeLabel.setText(timeText);
                    }
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        recordingTimerThread.setDaemon(true);
        recordingTimerThread.start();
    }

    private void stopTimer() {
        isTimerRunning.set(false);
        if (recordingTimerThread != null && recordingTimerThread.isAlive()) {
            recordingTimerThread.interrupt();
        }
    }

    public void reset() {
        stopTimer();
        state.clearCurrentAudioFile();
        Platform.runLater(() -> {
            if (recordButton != null) {
                recordButton.setDisable(false);
                recordButton.setText("● Запись");
            }
            if (stopButton != null) stopButton.setDisable(true);
            if (recordingIndicator != null) recordingIndicator.setVisible(false);
            if (recordingTimeLabel != null) recordingTimeLabel.setText("00:00");
            if (analyzeButton != null) analyzeButton.setDisable(true);
        });
    }

    public boolean isRecording() {
        return state.isRecording();
    }
}
