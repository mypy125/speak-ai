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
        try {
            setupToggleGroup();
            setupPlayButton();
            setInitialMode();
            logger.debug("ResponseModeManager инициализирован");
        } catch (Exception e) {
            logger.error("Ошибка при инициализации ResponseModeManager", e);
        }
    }

    private void setupToggleGroup() {
        if (responseModeToggleGroup == null) {
            logger.warn("responseModeToggleGroup is null, пропускаем настройку");
            return;
        }

        responseModeToggleGroup.selectedToggleProperty().addListener(
                (obs, oldVal, newVal) -> {
                    try {
                        if (newVal != null && newVal.getUserData() != null) {
                            String mode = (String) newVal.getUserData();
                            ResponseMode newMode = ResponseMode.valueOf(mode);

                            if (state != null) {
                                state.setCurrentResponseMode(newMode);
                            }
                            updateUIMode(newMode);

                            if (onStatusUpdate != null) {
                                onStatusUpdate.run();
                            }

                            logger.info("Режим ответа изменен на: {}", newMode);
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при смене режима ответа", e);
                    }
                }
        );
    }

    private void setupPlayButton() {
        if (playResponseButton == null) {
            logger.warn("playResponseButton is null, пропускаем настройку");
            return;
        }

        try {
            playResponseButton.setVisible(false);
            playResponseButton.setManaged(false);
            playResponseButton.setTooltip(new Tooltip("Прослушать ответ"));

            playResponseButton.setOnAction(event -> {
                try {
                    if (isSpeechPlaying.get() || (state != null && state.isPlayingSpeech())) {
                        stopSpeaking();
                    } else {
                        playLastResponse();
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при нажатии на кнопку воспроизведения", e);
                    ErrorHandler.showError("Ошибка", "Не удалось воспроизвести речь: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.error("Ошибка при настройке кнопки воспроизведения", e);
        }
    }

    private void setInitialMode() {
        try {
            if (textToggle != null) {
                textToggle.setSelected(true);
                textToggle.setUserData("TEXT");
            }
            if (voiceToggle != null) {
                voiceToggle.setUserData("VOICE");
            }
            if (state != null) {
                state.setCurrentResponseMode(ResponseMode.TEXT);
            }
            updateUIMode(ResponseMode.TEXT);
        } catch (Exception e) {
            logger.error("Ошибка при установке начального режима", e);
        }
    }

    private void updateUIMode(ResponseMode mode) {
        try {
            boolean isVoiceMode = mode == ResponseMode.VOICE;
            boolean hasResponse = state != null && state.getLastBotResponse() != null &&
                    !state.getLastBotResponse().isEmpty();
            boolean ttsAvailable = ttsService != null && ttsService.isAvailable();

            if (playResponseButton != null) {
                boolean shouldShow = isVoiceMode && hasResponse && ttsAvailable;

                Platform.runLater(() -> {
                    try {
                        playResponseButton.setVisible(shouldShow);
                        playResponseButton.setManaged(shouldShow);

                        if (shouldShow) {
                            updatePlayButtonState();
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при обновлении видимости кнопки", e);
                    }
                });
            }

            updateToggleButtonStyles();
        } catch (Exception e) {
            logger.error("Ошибка при обновлении UI режима", e);
        }
    }

    private void updateToggleButtonStyles() {
        Platform.runLater(() -> {
            try {
                if (textToggle != null && state != null) {
                    textToggle.setStyle(getToggleButtonStyle(
                            state.getCurrentResponseMode() == ResponseMode.TEXT));
                }
                if (voiceToggle != null && state != null) {
                    voiceToggle.setStyle(getToggleButtonStyle(
                            state.getCurrentResponseMode() == ResponseMode.VOICE));
                }
            } catch (Exception e) {
                logger.error("Ошибка при обновлении стилей кнопок", e);
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
                    "-fx-padding: 5 10;" +
                    "-fx-font-weight: bold;";
        } else {
            return "-fx-background-color: #f0f0f0; " +
                    "-fx-text-fill: black; " +
                    "-fx-border-color: #ccc; " +
                    "-fx-border-radius: 5; " +
                    "-fx-background-radius: 5; " +
                    "-fx-padding: 5 10;" +
                    "-fx-font-weight: normal;";
        }
    }

    public void playLastResponse() {
        if (state == null) {
            logger.error("state is null, cannot play response");
            ErrorHandler.showError("Ошибка", "Состояние приложения не инициализировано");
            return;
        }

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

        try {
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
                try {
                    CompletableFuture<Void> currentFuture = currentSpeechFuture.get();
                    if (currentFuture != null && !currentFuture.isDone()) {
                        currentFuture.cancel(true);
                        logger.warn("Таймаут озвучки ({} сек)", SPEECH_TIMEOUT_SECONDS);

                        Platform.runLater(() -> {
                            try {
                                ErrorHandler.showWarning("Таймаут",
                                        "Озвучка заняла слишком много времени");
                                isSpeechPlaying.set(false);
                                if (state != null) {
                                    state.setPlayingSpeech(false);
                                }
                                updatePlayButtonState();
                            } catch (Exception e) {
                                logger.error("Ошибка при обработке таймаута", e);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при обработке таймаута", e);
                }
            }, SPEECH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            future.whenComplete((result, throwable) -> {
                timeout.cancel(false);

                try {
                    isSpeechPlaying.set(false);
                    if (state != null) {
                        state.setPlayingSpeech(false);
                    }

                    Platform.runLater(() -> {
                        try {
                            updatePlayButtonState();

                            if (throwable == null) {
                                logger.info("✅ Озвучка завершена");
                            } else {
                                handleSpeechError(throwable);
                            }
                        } catch (Exception e) {
                            logger.error("Ошибка при обновлении UI после озвучки", e);
                        }
                    });

                    currentSpeechFuture.compareAndSet(future, null);

                } catch (Exception e) {
                    logger.error("Ошибка при обработке завершения озвучки", e);
                }
            });

        } catch (Exception e) {
            logger.error("Ошибка при запуске озвучки", e);
            isSpeechPlaying.set(false);
            if (state != null) {
                state.setPlayingSpeech(false);
            }
            Platform.runLater(() -> updatePlayButtonState());
            ErrorHandler.showError("Ошибка", "Не удалось запустить озвучку: " + e.getMessage());
        }
    }

    private void handleSpeechError(Throwable throwable) {
        try {
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
        } catch (Exception e) {
            logger.error("Ошибка при обработке ошибки озвучки", e);
        }
    }

    public void updatePlayButtonVisibility() {
        try {
            if (state == null) {
                logger.warn("state is null, cannot update button visibility");
                return;
            }

            boolean isVoiceMode = state.getCurrentResponseMode() == ResponseMode.VOICE;
            boolean hasResponse = state.getLastBotResponse() != null &&
                    !state.getLastBotResponse().isEmpty();
            boolean ttsAvailable = ttsService != null && ttsService.isAvailable();

            if (playResponseButton != null) {
                boolean shouldShow = isVoiceMode && hasResponse && ttsAvailable;

                Platform.runLater(() -> {
                    try {
                        playResponseButton.setVisible(shouldShow);
                        playResponseButton.setManaged(shouldShow);

                        if (shouldShow) {
                            updatePlayButtonState();
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при обновлении видимости кнопки", e);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Ошибка в updatePlayButtonVisibility", e);
        }
    }

    public void stopSpeaking() {
        try {
            // CAS операция - если флаг уже false, выходим
            if (!isSpeechPlaying.compareAndSet(true, false)) {
                return;
            }

            if (state != null) {
                state.setPlayingSpeech(false);
            }

            Platform.runLater(() -> {
                try {
                    updatePlayButtonState();
                    logger.info("⏹️ Озвучка остановлена");
                } catch (Exception e) {
                    logger.error("Ошибка при обновлении UI после остановки", e);
                }
            });

            CompletableFuture.runAsync(() -> {
                try {
                    if (ttsService != null) {
                        ttsService.stopSpeaking();
                    }

                    CompletableFuture<Void> future = currentSpeechFuture.getAndSet(null);
                    if (future != null && !future.isDone()) {
                        future.cancel(true);
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при остановке TTS сервиса", e);
                }
            }, speechControlExecutor);

        } catch (Exception e) {
            logger.error("Ошибка в stopSpeaking", e);
        }
    }

    private void updatePlayButtonState() {
        if (playResponseButton == null) return;

        try {
            boolean isSpeaking = isSpeechPlaying.get() || (state != null && state.isPlayingSpeech());

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
        } catch (Exception e) {
            logger.error("Ошибка при обновлении состояния кнопки", e);
        }
    }

    public void onNewResponse(String response) {
        try {
            if (state != null) {
                state.setLastBotResponse(response);
            }

            Platform.runLater(() -> {
                try {
                    if (state != null) {
                        updateUIMode(state.getCurrentResponseMode());
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при обновлении UI после нового ответа", e);
                }
            });

            logger.debug("Новый ответ сохранен");
        } catch (Exception e) {
            logger.error("Ошибка в onNewResponse", e);
        }
    }

    public void reset() {
        try {
            isSpeechPlaying.set(false);

            if (state != null) {
                state.setLastBotResponse("");
                state.setPlayingSpeech(false);
            }

            CompletableFuture<Void> future = currentSpeechFuture.getAndSet(null);
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }

            if (ttsService != null) {
                ttsService.stopSpeaking();
            }

            Platform.runLater(() -> {
                try {
                    if (state != null) {
                        updateUIMode(state.getCurrentResponseMode());
                    }
                    updatePlayButtonState();
                } catch (Exception e) {
                    logger.error("Ошибка при сбросе UI", e);
                }
            });
        } catch (Exception e) {
            logger.error("Ошибка в reset", e);
        }
    }

    public ResponseMode getCurrentMode() {
        return state != null ? state.getCurrentResponseMode() : ResponseMode.TEXT;
    }

    public void shutdown() {
        logger.info("Завершение работы ResponseModeManager...");

        try {
            reset();
            logger.info("ResponseModeManager завершил работу");
        } catch (Exception e) {
            logger.error("Ошибка при завершении работы ResponseModeManager", e);
        }
    }
}