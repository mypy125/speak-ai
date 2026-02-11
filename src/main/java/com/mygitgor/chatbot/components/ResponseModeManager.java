package com.mygitgor.chatbot.components;

import com.mygitgor.error.ErrorHandler;
import com.mygitgor.service.GoogleCloudTextToSpeechService;
import com.mygitgor.service.components.ResponseMode;
import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.state.ChatBotState;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

public class ResponseModeManager {
    private static final Logger logger = LoggerFactory.getLogger(ResponseModeManager.class);

    // UI Elements
    private final ToggleGroup responseModeToggleGroup;
    private final Button playResponseButton;
    private final ToggleButton textToggle;
    private final ToggleButton voiceToggle;
    private final Label statusLabel;

    // Dependencies
    private final ChatBotState state;
    private final ITTSService ttsService;
    private final Runnable onStatusUpdate;

    // State
    private CompletableFuture<Void> currentSpeechFuture;

    public ResponseModeManager(
            ToggleGroup responseModeToggleGroup,
            Button playResponseButton,
            ToggleButton textToggle,
            ToggleButton voiceToggle,
            Label statusLabel,
            ChatBotState state,
            ITTSService ttsService,
            Runnable onStatusUpdate) {

        this.responseModeToggleGroup = responseModeToggleGroup;
        this.playResponseButton = playResponseButton;
        this.textToggle = textToggle;
        this.voiceToggle = voiceToggle;
        this.statusLabel = statusLabel;
        this.state = state;
        this.ttsService = ttsService;
        this.onStatusUpdate = onStatusUpdate;

        initialize();
    }

    private void initialize() {
        setupToggleGroup();
        setupPlayButton();
        setInitialMode();
    }

    private void setupToggleGroup() {
        if (responseModeToggleGroup == null) return;

        responseModeToggleGroup.selectedToggleProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null && newVal.getUserData() != null) {
                        String mode = (String) newVal.getUserData();
                        ResponseMode newMode = ResponseMode.valueOf(mode);

                        state.setCurrentResponseMode(newMode);
                        updateUIMode(newMode);
                        onStatusUpdate.run();

                        logger.info("Режим ответа изменен на: {}", newMode);
                    }
                }
        );
    }

    private void setupPlayButton() {
        if (playResponseButton == null) return;

        playResponseButton.setVisible(false);
        playResponseButton.setManaged(false);
        playResponseButton.setTooltip(new Tooltip("Прослушать ответ"));

        playResponseButton.setOnAction(event -> {
            if (state.isPlayingSpeech()) {
                stopSpeaking();
            } else {
                playLastResponse();
            }
        });
    }

    private void setInitialMode() {
        if (textToggle != null) {
            textToggle.setSelected(true);
            textToggle.setUserData("TEXT");
        }
        if (voiceToggle != null) {
            voiceToggle.setUserData("VOICE");
        }
        state.setCurrentResponseMode(ResponseMode.TEXT);
        updateUIMode(ResponseMode.TEXT);
    }

    private void updateUIMode(ResponseMode mode) {
        boolean isVoiceMode = mode == ResponseMode.VOICE;
        boolean hasResponse = state.getLastBotResponse() != null &&
                !state.getLastBotResponse().isEmpty();
        boolean ttsAvailable = ttsService != null && ttsService.isAvailable();

        if (playResponseButton != null) {
            boolean shouldShow = isVoiceMode && hasResponse && ttsAvailable;
            playResponseButton.setVisible(shouldShow);
            playResponseButton.setManaged(shouldShow);

            if (shouldShow) {
                updatePlayButtonState();
            }
        }

        // Update toggle button styles
        updateToggleButtonStyles();
    }

    private void updateToggleButtonStyles() {
        Platform.runLater(() -> {
            if (textToggle != null) {
                textToggle.setStyle(getToggleButtonStyle(
                        state.getCurrentResponseMode() == ResponseMode.TEXT));
            }
            if (voiceToggle != null) {
                voiceToggle.setStyle(getToggleButtonStyle(
                        state.getCurrentResponseMode() == ResponseMode.VOICE));
            }
        });
    }

    private String getToggleButtonStyle(boolean selected) {
        if (selected) {
            return "-fx-background-color: #3498db; " +
                    "-fx-text-fill: white; " +
                    "-fx-border-color: #2980b9; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5; " +
                    "-fx-padding: 5 10;";
        } else {
            return "-fx-background-color: #f0f0f0; " +
                    "-fx-text-fill: black; " +
                    "-fx-border-color: #ccc; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5; " +
                    "-fx-padding: 5 10;";
        }
    }

    public void playLastResponse() {
        String response = state.getLastBotResponse();
        if (response == null || response.isEmpty()) {
            ErrorHandler.showWarning("Внимание", "Нет ответа для озвучки");
            return;
        }

        if (ttsService == null || !ttsService.isAvailable()) {
            ErrorHandler.showError("Ошибка", "TTS сервис недоступен");
            return;
        }

        state.setPlayingSpeech(true);
        updatePlayButtonState();

        currentSpeechFuture = ttsService.speakAsync(response);

        currentSpeechFuture.thenRun(() -> {
            Platform.runLater(() -> {
                state.setPlayingSpeech(false);
                updatePlayButtonState();
                logger.info("✅ Озвучка завершена");
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                state.setPlayingSpeech(false);
                updatePlayButtonState();
                if (!(throwable instanceof java.util.concurrent.CancellationException)) {
                    logger.error("Ошибка озвучки", throwable);
                    ErrorHandler.showError("Ошибка озвучки",
                            throwable.getMessage());
                }
            });
            return null;
        });
    }

    public void updatePlayButtonVisibility() {
        boolean isVoiceMode = state.getCurrentResponseMode() == ResponseMode.VOICE;
        boolean hasResponse = state.getLastBotResponse() != null &&
                !state.getLastBotResponse().isEmpty();
        boolean ttsAvailable = ttsService != null && ttsService.isAvailable();

        if (playResponseButton != null) {
            boolean shouldShow = isVoiceMode && hasResponse && ttsAvailable;
            playResponseButton.setVisible(shouldShow);
            playResponseButton.setManaged(shouldShow);

            if (shouldShow) {
                updatePlayButtonState();
            }
        }
    }

    public void stopSpeaking() {
        if (ttsService != null) {
            ttsService.stopSpeaking();
        }
        if (currentSpeechFuture != null && !currentSpeechFuture.isDone()) {
            currentSpeechFuture.cancel(true);
        }
        state.setPlayingSpeech(false);
        updatePlayButtonState();
        logger.info("⏹️ Озвучка остановлена");
    }

    private void updatePlayButtonState() {
        if (playResponseButton == null) return;

        boolean isSpeaking = state.isPlayingSpeech();

        if (isSpeaking) {
            playResponseButton.setText("⏸️ Остановить");
            playResponseButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            playResponseButton.setTooltip(new Tooltip("Остановить озвучку"));
        } else {
            playResponseButton.setText("▶️ Прослушать");
            playResponseButton.setStyle("");
            playResponseButton.setTooltip(new Tooltip("Прослушать ответ"));
        }
    }

    public void onNewResponse(String response) {
        state.setLastBotResponse(response);

        updateUIMode(state.getCurrentResponseMode());

        logger.debug("Новый ответ сохранен, режим: {}", state.getCurrentResponseMode());
    }

    public void reset() {
        stopSpeaking();
        state.setLastBotResponse("");
        state.setPlayingSpeech(false);
        updateUIMode(state.getCurrentResponseMode());
    }

    public ResponseMode getCurrentMode() {
        return state.getCurrentResponseMode();
    }
}
