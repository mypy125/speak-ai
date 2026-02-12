package com.mygitgor.chatbot.components;

import com.mygitgor.error.ErrorHandler;
import com.mygitgor.service.components.ResponseMode;
import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.state.ChatBotState;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ResponseModeManager {
    private static final Logger logger = LoggerFactory.getLogger(ResponseModeManager.class);

    private static final int SPEECH_TIMEOUT_SECONDS = 30;
    private static final int SHUTDOWN_TIMEOUT_MS = 500;

    private final ToggleGroup responseModeToggleGroup;
    private final Button playResponseButton;
    private final ToggleButton textToggle;
    private final ToggleButton voiceToggle;
    private final Label statusLabel;

    private final ChatBotState state;
    private final ITTSService ttsService;
    private final Runnable onStatusUpdate;

    private final AtomicReference<CompletableFuture<Void>> currentSpeechFuture = new AtomicReference<>(null);
    private final AtomicBoolean isSpeechPlaying = new AtomicBoolean(false);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService speechControlExecutor;
    private final ScheduledExecutorService scheduledExecutor;

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

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.speechControlExecutor = threadPoolManager.getBackgroundExecutor();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

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
            if (isSpeechPlaying.get() || state.isPlayingSpeech()) {
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

            Platform.runLater(() -> {
                playResponseButton.setVisible(shouldShow);
                playResponseButton.setManaged(shouldShow);

                if (shouldShow) {
                    updatePlayButtonState();
                }
            });
        }

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

        if (!isSpeechPlaying.compareAndSet(false, true)) {
            logger.debug("Озвучка уже выполняется, игнорируем повторный запрос");
            return;
        }

        state.setPlayingSpeech(true);

        Platform.runLater(() -> {
            updatePlayButtonState();
            logger.info("▶️ Запуск озвучки...");
        });

        CompletableFuture<Void> oldFuture = currentSpeechFuture.getAndSet(null);
        if (oldFuture != null && !oldFuture.isDone()) {
            oldFuture.cancel(true);
        }

        CompletableFuture<Void> future = ttsService.speakAsync(response);
        currentSpeechFuture.set(future);

        ScheduledFuture<?> timeout = scheduledExecutor.schedule(() -> {
            CompletableFuture<Void> currentFuture = currentSpeechFuture.get();
            if (currentFuture != null && !currentFuture.isDone()) {
                currentFuture.cancel(true);
                logger.warn("Таймаут озвучки ({} сек)", SPEECH_TIMEOUT_SECONDS);

                Platform.runLater(() -> {
                    ErrorHandler.showWarning("Таймаут",
                            "Озвучка заняла слишком много времени");
                    isSpeechPlaying.set(false);
                    state.setPlayingSpeech(false);
                    updatePlayButtonState();
                });
            }
        }, SPEECH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        future.whenComplete((result, throwable) -> {
            timeout.cancel(false);

            isSpeechPlaying.set(false);
            state.setPlayingSpeech(false);

            Platform.runLater(() -> {
                updatePlayButtonState();

                if (throwable == null) {
                    logger.info("✅ Озвучка завершена");
                } else {
                    handleSpeechError(throwable);
                }
            });

            currentSpeechFuture.compareAndSet(future, null);
        });
    }

    private void handleSpeechError(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

        if (cause instanceof CancellationException) {
            logger.debug("Озвучка отменена пользователем");
        } else if (cause instanceof TimeoutException) {
            logger.warn("Таймаут озвучки");
            ErrorHandler.showWarning("Таймаут", "Озвучка прервана по таймауту");
        } else {
            logger.error("Ошибка озвучки: {}", cause.getMessage(), cause);
            ErrorHandler.showError("Ошибка озвучки",
                    "Не удалось воспроизвести речь: " + cause.getMessage());
        }
    }

    public void updatePlayButtonVisibility() {
        boolean isVoiceMode = state.getCurrentResponseMode() == ResponseMode.VOICE;
        boolean hasResponse = state.getLastBotResponse() != null &&
                !state.getLastBotResponse().isEmpty();
        boolean ttsAvailable = ttsService != null && ttsService.isAvailable();

        if (playResponseButton != null) {
            boolean shouldShow = isVoiceMode && hasResponse && ttsAvailable;

            Platform.runLater(() -> {
                playResponseButton.setVisible(shouldShow);
                playResponseButton.setManaged(shouldShow);

                if (shouldShow) {
                    updatePlayButtonState();
                }
            });
        }
    }

    public void stopSpeaking() {
        if (!isSpeechPlaying.compareAndSet(true, false)) {
            return;
        }

        state.setPlayingSpeech(false);
        Platform.runLater(() -> {
            updatePlayButtonState();
            logger.info("⏹️ Озвучка остановлена");
        });

        CompletableFuture.runAsync(() -> {
            if (ttsService != null) {
                ttsService.stopSpeaking();
            }

            CompletableFuture<Void> future = currentSpeechFuture.getAndSet(null);
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }, speechControlExecutor);
    }

    private void updatePlayButtonState() {
        if (playResponseButton == null) return;

        boolean isSpeaking = isSpeechPlaying.get() || state.isPlayingSpeech();

        if (isSpeaking) {
            playResponseButton.setText("⏸️ Остановить");
            playResponseButton.setStyle(
                    "-fx-background-color: #e74c3c; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-weight: bold; " +
                            "-fx-cursor: hand;"
            );
            playResponseButton.setTooltip(new Tooltip("Остановить озвучку"));
        } else {
            playResponseButton.setText("▶️ Прослушать");
            playResponseButton.setStyle(
                    "-fx-background-color: #3498db; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-weight: bold; " +
                            "-fx-cursor: hand;"
            );
            playResponseButton.setTooltip(new Tooltip("Прослушать ответ"));
        }
        playResponseButton.setDisable(false);
    }

    public void onNewResponse(String response) {
        state.setLastBotResponse(response);

        Platform.runLater(() -> {
            updateUIMode(state.getCurrentResponseMode());
        });

        logger.debug("Новый ответ сохранен, режим: {}", state.getCurrentResponseMode());
    }

    public void reset() {
        isSpeechPlaying.set(false);
        state.setLastBotResponse("");
        state.setPlayingSpeech(false);

        CompletableFuture<Void> future = currentSpeechFuture.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        if (ttsService != null) {
            ttsService.stopSpeaking();
        }

        Platform.runLater(() -> {
            updateUIMode(state.getCurrentResponseMode());
            updatePlayButtonState();
        });
    }

    public ResponseMode getCurrentMode() {
        return state.getCurrentResponseMode();
    }

    public void shutdown() {
        logger.info("Завершение работы ResponseModeManager...");
        reset();
        logger.info("ResponseModeManager завершил работу");
    }
}