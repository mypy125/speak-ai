package com.mygitgor.chatbot;

import com.mygitgor.ai.*;
import com.mygitgor.model.LearningMode;
import com.mygitgor.model.LearningResponse;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.chatbot.components.*;
import com.mygitgor.config.AppConstants;
import com.mygitgor.config.ServicesConfig;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.model.core.Conversation;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.model.core.User;
import com.mygitgor.service.ChatBotService;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.service.DemoTextToSpeechService;
import com.mygitgor.service.components.ResponseMode;
import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.speech.SpeechRecorder;
import com.mygitgor.service.GoogleCloudTextToSpeechService;
import com.mygitgor.state.ChatBotState;
import com.mygitgor.utils.ConfigLoader;
import com.mygitgor.utils.ResourceManager;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;

public class ChatBotController implements Initializable, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotController.class);

    @FXML private TextArea inputField;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Button historyButton;
    @FXML private VBox chatMessagesContainer;
    @FXML private ScrollPane chatScrollPane;

    @FXML private VBox ttsControlPanel;
    @FXML private Label ttsModeLabel;
    @FXML private Circle ttsStatusIndicator;
    @FXML private Label ttsStatusLabel;
    @FXML private TextArea ttsInfoTextArea;
    @FXML private VBox googleCloudSettings;
    @FXML private ComboBox<String> googleVoiceComboBox;
    @FXML private Label googleVoiceDescriptionLabel;
    @FXML private Slider googlePitchSlider;
    @FXML private Label pitchLabel;
    @FXML private ComboBox<String> googleLanguageComboBox;
    @FXML private Slider googleVolumeSlider;
    @FXML private Label volumeLabel;
    @FXML private Slider googleSpeedSlider;
    @FXML private Label googleSpeedLabel;
    @FXML private Button testGoogleTTSButton;
    @FXML private Button showTTSInfoButton;
    @FXML private Button configureGoogleTTSButton;

    @FXML private VBox speechControlPanel;
    @FXML private ComboBox<String> languageComboBox;
    @FXML private ComboBox<String> serviceTypeComboBox;
    @FXML private Button testSpeechButton;
    @FXML private Button testMicrophoneButton;
    @FXML private Slider microphoneSensitivitySlider;
    @FXML private Label sensitivityLabel;
    @FXML private Label speechServiceStatusLabel;
    @FXML private Label microphoneStatusLabel;
    @FXML private Button microphoneButton;

    @FXML private Button recordButton;
    @FXML private Button stopButton;
    @FXML private ProgressIndicator recordingIndicator;
    @FXML private Label recordingTimeLabel;

    @FXML private TextArea analysisArea;
    @FXML private TextArea detailedAnalysisArea;
    @FXML private TextArea recommendationsArea;
    @FXML private Button analyzeButton;
    @FXML private Button pronunciationButton;
    @FXML private ProgressBar analysisProgress;
    @FXML private Label phonemeLabel;

    @FXML private ToggleGroup responseModeToggleGroup;
    @FXML private Button playResponseButton;
    @FXML private ToggleButton textToggle;
    @FXML private ToggleButton voiceToggle;

    @FXML private Label statusLabel;
    @FXML private Label messagesCountLabel;
    @FXML private Label analysisCountLabel;
    @FXML private Label recordingsCountLabel;

    @FXML private ComboBox<LearningMode> learningModeComboBox;
    @FXML private Button switchModeButton;
    @FXML private Label currentModeLabel;
    @FXML private ProgressBar modeProgressBar;
    @FXML private TextArea modeInfoArea;
    @FXML private VBox learningModePanel;
    @FXML private Label modeStatusLabel;
    @FXML private Label modeProgressLabel;
    @FXML private TextArea modeStatsArea;

    private ChatBotService chatBotService;
    private ITTSService textToSpeechService;
    private SpeechRecorder speechRecorder;
    private AudioAnalyzer audioAnalyzer;
    private PronunciationTrainer pronunciationTrainer;
    private ResourceManager resourceManager;

    private ChatBotState state;
    private Stage stage;

    private ChatMessagesManager messagesManager;
    private StatisticsManager statisticsManager;
    private TTSControlsManager ttsControlsManager;
    private SpeechRecognitionUIManager speechRecognitionUIManager;
    private RecordingManager recordingManager;
    private ResponseModeManager responseModeManager;
    private AnalysisManager analysisManager;
    private ThreadPoolManager threadPoolManager;

    private LearningStrategyFactory learningStrategyFactory;
    private LearningModeManager learningModeManager;
    private LearningMode currentLearningMode = LearningMode.CONVERSATION;

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> currentOperation = new AtomicReference<>(null);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Начало инициализации ChatBotController");

        ErrorHandler.handle(() -> {
            initializeComponents();
            setupUI();
            setupLearningModeUI();

            if (statusLabel != null) {
                statusLabel.setText("Загрузка моделей...");
            }
            return null;
        }, e -> {
            logger.error("Ошибка при сборке UI", e);
            return null;
        }, "Настройка интерфейса");

        Thread initThread = new Thread(() -> {
            try {
                Thread.sleep(300);

                logger.info("Запуск фоновой загрузки сервисов (Vosk, Google TTS, AI)...");

                setupServices();
                setupLearningStrategies();
                setupManagers();

                Platform.runLater(() -> {
                    updateModeStats();
                    showWelcomeMessage();

                    if (statusLabel != null && state != null) {
                        statusLabel.setText(state.isAiServiceAvailable() ? "Online" : "Offline");
                    }

                    logger.info("ChatBotController полностью загружен и готов к работе!");
                });

            } catch (Exception e) {
                logger.error("Критическая ошибка при фоновой инициализации", e);
                Platform.runLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Ошибка загрузки");
                    ErrorHandler.showError("Ошибка сервисов",
                            "Не удалось загрузить компоненты ИИ: " + e.getMessage());
                });
            }
        });

        initThread.setName("JPro-Background-Init");
        initThread.setDaemon(true);
        initThread.start();
    }

    private void initializeComponents() {
        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.state = new ChatBotState();
        this.resourceManager = new ResourceManager();
        this.speechRecorder = new SpeechRecorder();
        this.audioAnalyzer = new AudioAnalyzer();
        this.pronunciationTrainer = new PronunciationTrainer();

        this.messagesManager = new ChatMessagesManager(chatMessagesContainer, chatScrollPane);
    }

    private void setupServices() {
        logger.info("Загрузка конфигурации из .env и ресурсов...");
        Properties appProperties = ConfigLoader.loadConfig();

        this.audioAnalyzer = new AudioAnalyzer();
        this.pronunciationTrainer = new PronunciationTrainer();
        this.speechRecorder = new SpeechRecorder();

        resourceManager.register(audioAnalyzer);
        resourceManager.register(pronunciationTrainer);
        resourceManager.register(speechRecorder);

        this.textToSpeechService = initializeTTSService(appProperties);
        resourceManager.register(textToSpeechService);

        AiService aiService = initializeAIService(appProperties);
        resourceManager.registerIfCloseable(aiService);

        this.chatBotService = new ChatBotService(
                aiService,
                audioAnalyzer,
                pronunciationTrainer,
                textToSpeechService,
                speechRecorder
        );
        resourceManager.register(chatBotService);

        state.setAiServiceAvailable(aiService.isAvailable());

        logger.info("Все сервисы успешно инициализированы с параметрами из .env");
    }

    private void setupLearningStrategies() {
        try {
            this.learningStrategyFactory = new LearningStrategyFactory(
                    chatBotService,
                    pronunciationTrainer,
                    audioAnalyzer
            );

            this.learningModeManager = new LearningModeManager(learningStrategyFactory);

            logger.info("Learning strategies initialized: {} modes available",
                    learningStrategyFactory.getAvailableStrategies().size());
        } catch (Exception e) {
            logger.error("Ошибка при инициализации стратегий обучения", e);
        }
    }

    private void setupManagers() {
        this.statisticsManager = new StatisticsManager(
                messagesCountLabel, analysisCountLabel, recordingsCountLabel, state);

        this.ttsControlsManager = new TTSControlsManager(
                textToSpeechService,
                googleSpeedSlider, googleSpeedLabel,
                googlePitchSlider, pitchLabel,
                googleVolumeSlider, volumeLabel,
                googleLanguageComboBox,
                googleVoiceComboBox,
                googleVoiceDescriptionLabel,
                ttsStatusIndicator, ttsStatusLabel,
                ttsInfoTextArea,
                ttsModeLabel,
                googleCloudSettings
        );

        this.speechRecognitionUIManager = new SpeechRecognitionUIManager(
                speechControlPanel,
                serviceTypeComboBox,
                languageComboBox,
                testSpeechButton,
                testMicrophoneButton,
                microphoneSensitivitySlider,
                sensitivityLabel,
                speechServiceStatusLabel,
                microphoneStatusLabel,
                microphoneButton,
                chatBotService,
                this::updateStatusLabel
        );

        this.recordingManager = new RecordingManager(
                recordButton,
                stopButton,
                recordingIndicator,
                recordingTimeLabel,
                analyzeButton,
                speechRecorder,
                chatBotService,
                state,
                () -> statisticsManager.onRecordingStarted()
        );

        this.responseModeManager = new ResponseModeManager(
                responseModeToggleGroup,
                playResponseButton,
                textToggle,
                voiceToggle,
                statusLabel,
                state,
                textToSpeechService,
                this::updateStatusLabel
        );

        this.analysisManager = new AnalysisManager(
                analysisArea,
                detailedAnalysisArea,
                recommendationsArea,
                analyzeButton,
                pronunciationButton,
                analysisProgress,
                phonemeLabel,
                statusLabel,
                audioAnalyzer,
                pronunciationTrainer,
                chatBotService,
                state,
                stage
        );
    }

    private void setupUI() {
        setupInputField();
        updateStatusLabel();
        setupButtonStyles();
    }

    private void setupInputField() {
        if (inputField == null) return;

        inputField.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    if (event.isShiftDown()) {
                        inputField.appendText("\n");
                    } else {
                        event.consume();
                        onSendMessage();
                    }
                    break;
            }
        });
    }

    private void setupButtonStyles() {
        Platform.runLater(() -> {
            if (sendButton != null) {
                sendButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
            }
            if (clearButton != null) {
                clearButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;");
            }
            if (historyButton != null) {
                historyButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold;");
            }
            if (switchModeButton != null) {
                switchModeButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
            }
        });
    }

    private void setupLearningModeUI() {
        Platform.runLater(() -> {
            if (learningModeComboBox != null) {
                learningModeComboBox.getItems().addAll(LearningMode.values());
                learningModeComboBox.setValue(currentLearningMode);
                learningModeComboBox.setCellFactory(this::createModeCell);
                learningModeComboBox.setButtonCell(createModeCell(null));
            }

            if (switchModeButton != null) {
                switchModeButton.setOnAction(e -> switchLearningMode());
            }

            if (currentModeLabel != null) {
                updateCurrentModeDisplay();
            }
        });
    }

    private ListCell<LearningMode> createModeCell(ListView<LearningMode> param) {
        return new ListCell<>() {
            @Override
            protected void updateItem(LearningMode mode, boolean empty) {
                super.updateItem(mode, empty);
                if (empty || mode == null) {
                    setText(null);
                } else {
                    setText(mode.getDisplayName());
                    setTooltip(new Tooltip(mode.getDescription()));
                }
            }
        };
    }

    @FXML
    private void onSendMessage() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        String text = inputField.getText().trim();
        String currentAudioFile = state.getCurrentAudioFile();
        ResponseMode currentResponseMode = state.getCurrentResponseMode();

        if (text.isEmpty() && currentAudioFile == null) {
            ErrorHandler.showWarning("Внимание", "Введите сообщение или запишите аудио");
            return;
        }

        final String finalText = text;
        final String finalAudioFile = currentAudioFile;
        final ResponseMode finalResponseMode = currentResponseMode;

        if (!text.isEmpty()) {
            messagesManager.addUserMessage(text);
            inputField.clear();
        } else if (currentAudioFile != null) {
            messagesManager.addUserMessage("🎤 [Аудиосообщение]");
        }

        statisticsManager.onMessageSent();
        showLoadingIndicator(true);

        String userId = getCurrentUserIdSafely();

        if (userId.trim().isEmpty()) {
            logger.error("Не удалось получить userId даже после создания временного");
            userId = "temp_" + System.currentTimeMillis();
            logger.warn("Создан временный ID в контроллере: {}", userId);
        }

        logger.debug("Используется userId: {}", userId);

        CompletableFuture<Void> future;

        if (learningModeManager != null) {
            logger.debug("Обработка через LearningModeManager для пользователя {}", userId);
            future = processWithLearningMode(userId, finalText, finalAudioFile);
        } else {
            logger.debug("Обработка через ChatBotService (обычный режим)");
            future = processWithChatBotService(finalText, finalAudioFile, finalResponseMode);
        }

        currentOperation.set(future);
    }

    @FXML
    private void switchLearningMode() {
        if (learningModeComboBox == null || learningModeManager == null) {
            logger.warn("LearningModeManager не инициализирован");
            return;
        }

        LearningMode newMode = learningModeComboBox.getValue();
        if (newMode == null) {
            ErrorHandler.showWarning("Внимание", "Выберите режим обучения");
            return;
        }

        if (newMode == currentLearningMode) {
            logger.debug("Режим {} уже активен", newMode);
            return;
        }

        String userId = chatBotService.getCurrentUserIdSafely();
        if (userId == null) {
            ErrorHandler.showError("Ошибка", "Не удалось определить пользователя");
            return;
        }

        logger.info("Смена режима обучения для пользователя {}: {} -> {}",
                userId, currentLearningMode, newMode);

        showLoadingIndicator(true);

        learningModeManager.switchMode(userId, newMode)
                .thenAccept(response -> {
                    currentLearningMode = newMode;
                    Platform.runLater(() -> {
                        try {
                            updateCurrentModeDisplay();

                            messagesManager.addAIMessage(
                                    formatModeSwitchMessage(response),
                                    response.getTtsText()
                            );

                            showLoadingIndicator(false);

                            if (modeProgressBar != null) {
                                modeProgressBar.setProgress(response.getProgress() / 100.0);
                            }
                            if (modeInfoArea != null) {
                                modeInfoArea.setText(formatModeInfo(response));
                            }

                            responseModeManager.updatePlayButtonVisibility();
                        } catch (Exception e) {
                            logger.error("Ошибка при обновлении UI после смены режима", e);
                            showLoadingIndicator(false);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("Ошибка при смене режима", throwable);
                    Platform.runLater(() -> {
                        ErrorHandler.showError("Ошибка",
                                "Не удалось сменить режим обучения: " + throwable.getMessage());
                        showLoadingIndicator(false);
                    });
                    return null;
                });
    }

    @FXML
    private void onStartRecording() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        recordingManager.startRecording();
    }

    @FXML
    private void onStopRecording() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        recordingManager.stopRecording();
    }

    @FXML
    private void onAnalyzeAudio() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        String inputText = inputField.getText().trim();
        analysisManager.analyzeCurrentAudio(inputText);
    }

    @FXML
    private void onPronunciationTraining() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        analysisManager.showPronunciationTrainer();
    }

    @FXML
    private void testSpeechRecognition() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        speechRecognitionUIManager.startSpeechRecognitionTest(AppConstants.TEST_AUDIO_PATH);
    }

    @FXML
    private void testMicrophone() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        speechRecognitionUIManager.startMicrophoneTest();
    }

    @FXML
    private void onMicrophoneRecognition() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        speechRecognitionUIManager.startMicrophoneRecognition(inputField);
    }

    @FXML
    private void toggleSpeechPanel() {
        if (speechControlPanel == null) return;
        speechRecognitionUIManager.togglePanel();

        if (speechRecognitionUIManager.isPanelVisible() && ttsControlPanel != null) {
            ttsControlPanel.setVisible(false);
            ttsControlPanel.setManaged(false);
        }
    }

    @FXML
    private void toggleTTSPanel() {
        if (ttsControlPanel == null) return;

        boolean isVisible = ttsControlPanel.isVisible();
        ttsControlPanel.setVisible(!isVisible);
        ttsControlPanel.setManaged(!isVisible);

        if (isVisible && speechControlPanel != null) {
            speechControlPanel.setVisible(false);
            speechControlPanel.setManaged(false);
        }
    }

    @FXML
    private void toggleLearningModePanel() {
        if (learningModePanel == null) return;

        boolean isVisible = learningModePanel.isVisible();
        learningModePanel.setVisible(!isVisible);
        learningModePanel.setManaged(!isVisible);

        if (isVisible) {
            updateModeStats();
        } else {
            if (speechControlPanel != null) {
                speechControlPanel.setVisible(false);
                speechControlPanel.setManaged(false);
            }
            if (ttsControlPanel != null) {
                ttsControlPanel.setVisible(false);
                ttsControlPanel.setManaged(false);
            }
        }

        logger.info("Панель обучения {}", isVisible ? "скрыта" : "показана");
    }

    @FXML
    private void onTestGoogleTTS() {
        if (textToSpeechService == null) {
            ErrorHandler.showError("Ошибка", "TTS сервис не инициализирован");
            return;
        }

        if (textToSpeechService instanceof GoogleCloudTextToSpeechService) {
            GoogleCloudTextToSpeechService googleService = (GoogleCloudTextToSpeechService) textToSpeechService;
            boolean isAvailable = googleService.isAvailable();

            String message = isAvailable
                    ? String.format("✅ Google Cloud TTS доступен\n\nКонфигурация:\n• Голос: %s\n• Скорость: %.1fx\n• Тон: %.1f\n• Громкость: %.0f дБ\n\nТестовая фраза будет озвучена...",
                    googleService.getCurrentVoice().getDescription(),
                    googleService.getCurrentSpeed(),
                    googleService.getCurrentPitch(),
                    googleService.getCurrentVolumeGainDb())
                    : "⚠️ Google Cloud TTS не работает\n\nПроверьте настройки";

            ErrorHandler.showInfo("Тест Google Cloud TTS", message);

            if (isAvailable) {
                ErrorHandler.handleAsyncFuture(
                        googleService.speakAsync("Hello! This is a test of Google Cloud Text-to-Speech."),
                        throwable -> ErrorHandler.showError("Ошибка теста",
                                "Не удалось выполнить тест: " + throwable.getMessage())
                );
            }
        } else {
            ErrorHandler.showInfo("Тест Google Cloud TTS",
                    "⚠️ В данный момент используется демо-режим TTS.\n\n" +
                            "Google Cloud TTS не настроен или недоступен.\n" +
                            "Для использования Google Cloud TTS:\n" +
                            "1. Получите файл google-credentials.json\n" +
                            "2. Поместите его в корень проекта\n" +
                            "3. Перезапустите приложение");
        }
    }

    @FXML
    private void onShowTTSInfo() {
        if (textToSpeechService == null) {
            ErrorHandler.showInfo("Информация", "TTS сервис не инициализирован");
            return;
        }

        if (textToSpeechService instanceof GoogleCloudTextToSpeechService) {
            GoogleCloudTextToSpeechService googleService = (GoogleCloudTextToSpeechService) textToSpeechService;
            StringBuilder info = new StringBuilder();
            info.append("=== Информация о TTS сервисе ===\n\n");
            info.append("Текущий сервис: Google Cloud TTS\n");
            info.append("Доступность: ").append(googleService.isAvailable() ? "✅ Доступен" : "❌ Недоступен").append("\n\n");
            info.append("Метод аутентификации: ").append(googleService.getAuthMethod()).append("\n");
            info.append("Голос: ").append(googleService.getCurrentVoice().getDescription()).append("\n");
            info.append("Скорость: ").append(String.format("%.1f", googleService.getCurrentSpeed())).append("x\n");
            info.append("Тон: ").append(String.format("%.1f", googleService.getCurrentPitch())).append("\n");
            info.append("Громкость: ").append(String.format("%.0f", googleService.getCurrentVolumeGainDb())).append(" дБ\n");

            ErrorHandler.showInfo("Информация о TTS", info.toString());
        } else {
            ErrorHandler.showInfo("Информация о TTS",
                    "Текущий сервис: Демо-режим TTS\n" +
                            "Статус: ⚠️ Демо-режим (без реальной озвучки)\n\n" +
                            "Для использования Google Cloud TTS:\n" +
                            "1. Получите файл google-credentials.json\n" +
                            "2. Поместите его в корень проекта\n" +
                            "3. Включите Text-to-Speech API\n" +
                            "4. Перезапустите приложение");
        }
    }

    @FXML
    private void onConfigureGoogleTTS() {
        String helpText = """
            === Настройка Google Cloud TTS ===
            
            1. Создайте Service Account в Google Cloud Console
            2. Скачайте ключ в формате JSON
            3. Сохраните как google-credentials.json в папке с приложением
            4. Включите Cloud Text-to-Speech API
            
            Бесплатный лимит: 1 млн символов в месяц
            """;

        ErrorHandler.showInfo("Настройка Google Cloud TTS", helpText);
    }

    @FXML
    private void onStopSpeaking() {
        if (responseModeManager != null) {
            responseModeManager.stopSpeaking();
        }
    }

    @FXML
    private void onPlayResponse() {
        if (responseModeManager != null) {
            responseModeManager.playLastResponse();
        }
    }

    @FXML
    private void onShowHistory() {
        if (state.isClosed() || isShuttingDown.get()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        List<Conversation> history = chatBotService.getConversationHistory();

        if (history.isEmpty()) {
            ErrorHandler.showInfo("История", "История разговоров пуста");
            return;
        }

        StringBuilder historyText = new StringBuilder();
        historyText.append("История разговоров:\n\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (Conversation conv : history) {
            historyText.append("=".repeat(50)).append("\n");
            historyText.append("[").append(sdf.format(conv.getTimestamp())).append("]\n");
            historyText.append("Вы: ").append(conv.getUserMessage()).append("\n");
            if (conv.getPronunciationScore() > 0) {
                historyText.append("Оценка: ")
                        .append(String.format("%.1f/100", conv.getPronunciationScore()))
                        .append("\n");
            }
            historyText.append("\n");
        }

        showTextWindow("История разговоров", historyText.toString(), 620, 450);
    }

    @FXML
    private void onClearChat() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Очистка чата");
        alert.setHeaderText("Вы уверены, что хотите очистить историю чата?");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            messagesManager.clear();
            statisticsManager.reset();
            analysisManager.clear();
            recordingManager.reset();
            responseModeManager.reset();
            state.reset();
            showWelcomeMessage();
            logger.info("История чата очищена");
        }
    }

    @FXML
    private void onHelp() {
        String helpText = """
            SpeakAI - Руководство пользователя
            
            📝 ОСНОВНЫЕ ФУНКЦИИ:
            
            1. Чат с ИИ
               • Пишите сообщения на английском
               • Получайте естественные ответы
            
            2. Запись голоса 🎤
               • Записывайте свою речь
               • Анализ произношения
            
            3. Анализ речи
               • Оценка произношения
               • Детальный отчет по фонемам
               • Персонализированные рекомендации
            
            4. Тренажер произношения
               • Упражнения для проблемных звуков
               • Примеры и советы
            
            5. Озвучка (TTS)
               • Google Cloud WaveNet
               • Настройка голоса, скорости, тона
            
            6. Режимы обучения
               • 💬 Conversation - разговорная практика
               • 🔊 Pronunciation - тренировка произношения
               • 📚 Grammar - изучение грамматики
               • 🎯 Exercise - выполнение упражнений
               • 📖 Vocabulary - расширение словарного запаса
               • ✍️ Writing - практика письма
               • 🎧 Listening - развитие аудирования
            
            📞 КОНТАКТЫ:
            +(374)44082124
            gor1990.mkhitatryan@gmail.com
            """;

        ErrorHandler.showInfo("Помощь", helpText);
    }

    @FXML
    private void onSettings() {
        ErrorHandler.showInfo("Настройки",
                "Настройки будут доступны в следующей версии");
    }

    @FXML
    public void onTestTTS(ActionEvent actionEvent) {
        onTestGoogleTTS();
    }

    private CompletableFuture<Void> processWithLearningMode(String userId, String text, String audioFile) {
        return learningModeManager.processInput(userId, text, Optional.ofNullable(audioFile))
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        try {
                            messagesManager.addAIMessage(
                                    formatLearningResponse(response),
                                    response.getTtsText()
                            );

                            state.setLastBotResponse(response.getMessage());
                            state.setLastTtsText(response.getTtsText());

                            if (modeProgressBar != null) {
                                modeProgressBar.setProgress(response.getProgress() / 100.0);
                            }

                            if (audioFile != null && !audioFile.isEmpty()) {
                                analyzeAudioWithManagers(audioFile, text);
                            }

                            responseModeManager.updatePlayButtonVisibility();
                            showLoadingIndicator(false);

                            if (state.hasAudioFile()) {
                                state.clearCurrentAudioFile();
                            }

                            logger.debug("Сообщение успешно обработано через LearningModeManager");
                        } catch (Exception e) {
                            logger.error("Ошибка при обработке ответа от LearningModeManager", e);
                            showLoadingIndicator(false);
                            ErrorHandler.showError("Ошибка",
                                    "Не удалось обработать ответ: " + e.getMessage());
                        }
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("Ошибка при обработке сообщения через LearningModeManager", throwable);
                    Platform.runLater(() -> {
                        ErrorHandler.showError("Ошибка",
                                "Не удалось обработать сообщение в режиме обучения: " + throwable.getMessage());
                        showLoadingIndicator(false);
                    });
                    return null;
                });
    }

    private CompletableFuture<Void> processWithChatBotService(String text, String audioFile,
                                                              ResponseMode responseMode) {
        return CompletableFuture.runAsync(() -> {
            try {
                ChatBotService.ChatResponse response = chatBotService.processUserInput(
                        text,
                        audioFile,
                        responseMode
                );

                final ChatBotService.ChatResponse finalResponse = response;
                final String finalAudioFile = audioFile;
                final String finalText = text;

                Platform.runLater(() -> {
                    try {
                        messagesManager.addAIMessage(finalResponse.getFullResponse());
                        state.setLastBotResponse(finalResponse.getFullResponse());

                        if (finalResponse.getSpeechAnalysis() != null) {
                            analysisManager.processAnalysisResponse(finalResponse);
                        }
                        else if (finalAudioFile != null && !finalAudioFile.isEmpty()) {
                            analyzeAudioWithManagers(finalAudioFile, finalText);
                        }

                        responseModeManager.updatePlayButtonVisibility();
                        showLoadingIndicator(false);

                        if (state.hasAudioFile()) {
                            state.clearCurrentAudioFile();
                        }

                        logger.debug("Сообщение успешно обработано через ChatBotService");
                    } catch (Exception e) {
                        logger.error("Ошибка при обновлении UI после получения ответа", e);
                        showLoadingIndicator(false);
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка при обработке сообщения через ChatBotService", e);
                Platform.runLater(() -> {
                    ErrorHandler.showError("Ошибка",
                            "Не удалось обработать сообщение: " + e.getMessage());
                    showLoadingIndicator(false);
                });
            }
        }, threadPoolManager.getBackgroundExecutor());
    }

    private void analyzeAudioWithManagers(String audioFile, String text) {
        if (audioFile == null || audioFile.isEmpty()) {
            logger.debug("Нет аудиофайла для анализа");
            return;
        }

        logger.debug("Запуск анализа аудио: {}", audioFile);

        Platform.runLater(() -> {
            if (analysisProgress != null) {
                analysisProgress.setVisible(true);
            }
            if (detailedAnalysisArea != null) {
                detailedAnalysisArea.setVisible(true);
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(audioFile, text);

                Platform.runLater(() -> {
                    if (analysisArea != null) {
                        analysisArea.setText(analysis.getSummary());
                    }

                    if (detailedAnalysisArea != null && analysis.getDetailedReport() != null) {
                        detailedAnalysisArea.setText(analysis.getDetailedReport());
                        detailedAnalysisArea.setVisible(true);
                    }

                    updatePhonemeLabel(analysis);

                    if (analyzeButton != null) {
                        analyzeButton.setDisable(false);
                    }
                    if (pronunciationButton != null) {
                        pronunciationButton.setDisable(false);
                        updatePronunciationButtonText(analysis);
                    }

                    updateRecommendationsFromAnalysis(analysis);

                    if (statisticsManager != null) {
                        statisticsManager.onAnalysisPerformed();
                    }

                    if (analysisProgress != null) {
                        analysisProgress.setVisible(false);
                    }

                    logger.info("Анализ аудио завершен. Общий балл: {}/100",
                            String.format("%.1f", analysis.getOverallScore()));
                });

            } catch (Exception e) {
                logger.error("Ошибка при анализе аудио", e);
                Platform.runLater(() -> {
                    ErrorHandler.showError("Ошибка анализа",
                            "Не удалось выполнить анализ аудио: " + e.getMessage());
                    if (analysisProgress != null) {
                        analysisProgress.setVisible(false);
                    }
                });
            }
        }, threadPoolManager.getBackgroundExecutor());
    }

    private String formatLearningResponse(LearningResponse response) {
        StringBuilder sb = new StringBuilder();

        sb.append("### 🤖 AI Репетитор\n\n");
        sb.append(response.getMessage()).append("\n\n");

        if (response.getNextTask() != null) {
            sb.append("**📋 Следующее задание:**\n");
            sb.append(response.getNextTask().getDescription()).append("\n\n");
        }

        if (response.getRecommendations() != null && !response.getRecommendations().isEmpty()) {
            sb.append("**💡 Рекомендации:**\n");
            response.getRecommendations().forEach(r -> sb.append("• ").append(r).append("\n"));
            sb.append("\n");
        }

        sb.append(String.format("**📊 Прогресс в режиме %s:** %.1f%%",
                currentLearningMode.getDisplayName(), response.getProgress()));

        Platform.runLater(() -> {
            updateCurrentModeDisplay();
            updateModeStats();
        });

        return sb.toString();
    }

    private String formatModeSwitchMessage(LearningResponse response) {
        StringBuilder sb = new StringBuilder();

        sb.append("🔄 **Режим обучения изменен**\n\n");
        sb.append("**Новый режим:** ").append(currentLearningMode.getDisplayName()).append("\n");
        sb.append("**Описание:** ").append(currentLearningMode.getDescription()).append("\n\n");

        if (response.getNextTask() != null) {
            sb.append("📋 **Следующее задание:**\n");
            sb.append(response.getNextTask().getDescription()).append("\n\n");
        }

        sb.append(String.format("📊 **Прогресс:** %.1f%%\n", response.getProgress()));

        if (response.getRecommendations() != null && !response.getRecommendations().isEmpty()) {
            sb.append("\n💡 **Рекомендации:**\n");
            response.getRecommendations().forEach(r -> sb.append("• ").append(r).append("\n"));
        }

        return sb.toString();
    }

    private String formatModeInfo(LearningResponse response) {
        return String.format("""
            Режим: %s
            Прогресс: %.1f%%
            Заданий выполнено: %d
            """,
                currentLearningMode.getDisplayName(),
                response.getProgress(),
                response.getNextTask() != null ? 1 : 0
        );
    }

    private String formatRecommendations(List<String> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n**Рекомендации:**\n");
        recommendations.forEach(r -> sb.append("• ").append(r).append("\n"));
        return sb.toString();
    }

    private void updatePhonemeLabel(EnhancedSpeechAnalysis analysis) {
        if (phonemeLabel == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            analysis.getPhonemeScores().entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(entry -> {
                        String phoneme = "/" + entry.getKey() + "/";
                        float score = entry.getValue();

                        String text = String.format("🔊 Слабая фонема: %s (%.1f/100)", phoneme, score);
                        phonemeLabel.setText(text);
                        phonemeLabel.setVisible(true);
                        phonemeLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
                    });
        } else {
            phonemeLabel.setVisible(false);
        }
    }

    private void updatePronunciationButtonText(EnhancedSpeechAnalysis analysis) {
        if (pronunciationButton == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            long weakPhonemesCount = analysis.getPhonemeScores().values().stream()
                    .filter(score -> score < 70)
                    .count();

            if (weakPhonemesCount > 0) {
                pronunciationButton.setText("🎯 Тренажер (" + weakPhonemesCount + " проблем)");
                pronunciationButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");
            } else {
                pronunciationButton.setText("✅ Тренажер произношения");
                pronunciationButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
            }
        }
    }

    private void updateRecommendationsFromAnalysis(EnhancedSpeechAnalysis analysis) {
        if (recommendationsArea == null) return;

        StringBuilder recText = new StringBuilder();

        if (analysis.getOverallScore() < 70) {
            recText.append("📋 **ОСНОВНЫЕ РЕКОМЕНДАЦИИ:**\n\n");
        } else {
            recText.append("📋 **РЕКОМЕНДАЦИИ ПО УЛУЧШЕНИЮ:**\n\n");
        }

        if (analysis.getRecommendations() != null && !analysis.getRecommendations().isEmpty()) {
            analysis.getRecommendations().forEach(rec ->
                    recText.append("• ").append(rec).append("\n"));
        } else {
            if (analysis.getPronunciationScore() < 75) {
                recText.append("• 🔊 Уделите внимание произношению сложных звуков\n");
            }
            if (analysis.getFluencyScore() < 70) {
                recText.append("• ⚡ Работайте над беглостью речи - меньше пауз\n");
            }
            if (analysis.getIntonationScore() < 70) {
                recText.append("• 🎵 Улучшайте интонацию в вопросах и утверждениях\n");
            }
            if (analysis.getVolumeScore() < 70) {
                recText.append("• 🔊 Говорите громче и увереннее\n");
            }
        }

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            recText.append("\n**🔊 ПРОБЛЕМНЫЕ ЗВУКИ:**\n");
            analysis.getPhonemeScores().entrySet().stream()
                    .filter(e -> e.getValue() < 70)
                    .limit(3)
                    .forEach(e -> recText.append("• /").append(e.getKey())
                            .append("/ - ").append(String.format("%.1f", e.getValue()))
                            .append("/100\n"));
        }

        recommendationsArea.setText(recText.toString());
    }

    private void showWelcomeMessage() {
        String ttsMode = textToSpeechService != null && textToSpeechService.isAvailable()
                ? "Google Cloud TTS (WaveNet)"
                : "TTS недоступен";

        ResponseMode currentResponseMode = state.getCurrentResponseMode();
        String responseModeText = currentResponseMode == ResponseMode.VOICE ? "🔊 Голосовой" : "📝 Текстовый";

        String welcomeMessage = String.format("""
            🌟 Добро пожаловать в SpeakAI!
            
            Текущий режим: %s
            Режим ответа: %s
            Режим обучения: %s
            
            Как это работает:
            1. Выберите режим обучения в панели справа
            2. Напишите сообщение или нажмите кнопку записи 🎤
            3. Получите анализ речи и рекомендации
            4. Используйте тренажер произношения для проблемных звуков
            
            Доступные режимы обучения:
            • 💬 Conversation - разговорная практика
            • 🔊 Pronunciation - тренировка произношения
            • 📚 Grammar - изучение грамматики
            • 🎯 Exercise - выполнение упражнений
            • 📖 Vocabulary - расширение словарного запаса
            • ✍️ Writing - практика письма
            • 🎧 Listening - развитие аудирования
            
            Давайте начнем! ✨
            """, ttsMode, responseModeText, currentLearningMode.getDisplayName());

        messagesManager.addAIMessage(welcomeMessage);

        ThreadPoolManager.runWithDelay(() -> {
            logger.debug("Приветственное сообщение показано");
        }, 100);
    }

    private void updateStatusLabel() {
        if (statusLabel == null) return;

        String status = state.isAiServiceAvailable()
                ? "✅ ИИ-сервис доступен"
                : "⚠️ Демо-режим";

        Platform.runLater(() -> {
            statusLabel.setText(status);
            statusLabel.setStyle("-fx-font-weight: bold;");
        });
    }

    private void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(show ? "⏳ Обработка..." :
                        state.isAiServiceAvailable() ? "✅ ИИ-сервис доступен" : "⚠️ Демо-режим");
            }
            if (sendButton != null) {
                sendButton.setDisable(show);
                sendButton.setStyle(show ?
                        "-fx-background-color: #95a5a6; -fx-text-fill: white;" :
                        "-fx-background-color: #3498db; -fx-text-fill: white;");
            }
            if (switchModeButton != null) {
                switchModeButton.setDisable(show);
            }
        });
    }

    private String getCurrentUserIdSafely() {
        try {
            if (chatBotService != null) {
                String userId = chatBotService.getCurrentUserIdSafely();
                if (userId != null && !userId.trim().isEmpty()) {
                    return userId;
                }

                User user = chatBotService.getCurrentUser();
                if (user != null) {
                    return String.valueOf(user.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка в getCurrentUserIdSafely", e);
        }

        String tempId = "temp_" + System.currentTimeMillis();
        logger.warn("Создан временный ID в контроллере: {}", tempId);
        return tempId;
    }

    private void updateModeStats() {
        if (learningModeManager == null || modeStatsArea == null) return;

        String userId = getCurrentUserIdSafely();

        learningModeManager.analyzeOverallProgress(userId)
                .thenAccept(progress -> {
                    Platform.runLater(() -> {
                        StringBuilder stats = new StringBuilder();
                        progress.forEach((mode, value) ->
                                stats.append(String.format("• %s: %.1f%%\n",
                                        mode.getDisplayName(), value)));

                        if (stats.isEmpty()) {
                            stats.append("Нет данных. Начните обучение!");
                        }

                        modeStatsArea.setText(stats.toString());
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("Ошибка при получении статистики", throwable);
                    Platform.runLater(() ->
                            modeStatsArea.setText("Ошибка загрузки статистики"));
                    return null;
                });
    }

    private void updateCurrentModeDisplay() {
        if (currentModeLabel != null) {
            currentModeLabel.setText(currentLearningMode.getDisplayName());
            currentModeLabel.setTooltip(new Tooltip(currentLearningMode.getDescription()));
        }

        if (modeStatusLabel != null) {
            modeStatusLabel.setText("✅ Активен");
        }

        if (modeProgressLabel != null && modeProgressBar != null) {
            double progress = getCurrentModeProgress();
            modeProgressBar.setProgress(progress / 100.0);
            modeProgressLabel.setText(String.format("%.1f%%", progress));
        }
    }

    private double getCurrentModeProgress() {
        if (learningModeManager == null) return 0;

        String userId = getCurrentUserIdSafely();

        try {
            return learningModeManager.analyzeOverallProgress(userId)
                    .thenApply(progress -> progress.getOrDefault(currentLearningMode, 0.0))
                    .get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("Не удалось получить прогресс: {}", e.getMessage());
            return 0;
        }
    }

    private void showTextWindow(String title, String content, int width, int height) {
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFitToWidth(true);

        Stage textStage = new Stage();
        textStage.setTitle(title);
        if (stage != null) {
            textStage.initOwner(stage);
        }

        Scene scene = new Scene(scrollPane, width, height);
        textStage.setScene(scene);
        textStage.show();
    }

    private ITTSService initializeTTSService(Properties props) {
        setupGoogleCloudTTSCredentials();

        try {
            GoogleCloudTextToSpeechService googleService = ErrorHandler.retry(() -> {
                try {
                    GoogleCloudTextToSpeechService service = new GoogleCloudTextToSpeechService();

                    Thread.sleep(AppConstants.GOOGLE_TTS_INIT_DELAY_MS);

                    if (service.isAvailable()) {
                        logger.info("✅ Google Cloud TTS успешно инициализирован");
                        logger.info("   Голос: {}", service.getCurrentVoice().getDescription());
                        logger.info("   Метод аутентификации: {}", service.getAuthMethod());

                        String defaultLang = props.getProperty("speech.default.language");
                        if (defaultLang != null && !defaultLang.trim().isEmpty()) {
                            service.setLanguage(defaultLang.trim());
                        }

                        return service;
                    } else {
                        logger.warn("⚠️ Google Cloud TTS создан, но недоступен");
                        return null;
                    }
                } catch (Exception e) {
                    logger.error("❌ Ошибка при создании Google Cloud TTS: {}", e.getMessage());
                    return null;
                }
            }, 2, 1000, "инициализация Google Cloud TTS");

            if (googleService != null && googleService.isAvailable()) {
                showTTSConnectionSuccess(googleService);
                return googleService;
            }

        } catch (Exception e) {
            logger.warn("Не удалось инициализировать Google Cloud TTS: {}", e.getMessage());
        }

        return initializeDemoTTSService();
    }

    private ITTSService initializeDemoTTSService() {
        logger.warn("⚠️ Используется демо-режим TTS (без реальной озвучки)");

        DemoTextToSpeechService demoServices = new DemoTextToSpeechService();

        Platform.runLater(() -> {
            showTTSConnectionWarning();

            if (ttsStatusLabel != null) {
                ttsStatusLabel.setText("⚠️ Демо-режим TTS");
                ttsStatusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
            }
            if (ttsStatusIndicator != null) {
                ttsStatusIndicator.setFill(Color.ORANGE);
            }
            if (ttsModeLabel != null) {
                ttsModeLabel.setText("Демо-режим TTS");
                ttsModeLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
            }
            if (ttsInfoTextArea != null) {
                ttsInfoTextArea.setText(getDemoTTSInfoText());
            }
        });

        return demoServices;
    }

    private String getDemoTTSInfoText() {
        return """
            ⚠️ TTS В ДЕМО-РЕЖИМЕ
            =======================
            
            Google Cloud TTS не настроен или недоступен.
            
            📋 ЧТОБЫ ВКЛЮЧИТЬ РЕАЛЬНУЮ ОЗВУЧКУ:
            
            1. Получите файл учетных данных:
               • Перейдите в Google Cloud Console
               • Создайте Service Account
               • Скачайте ключ в формате JSON
               • Переименуйте в google-credentials.json
            
            2. Поместите файл в одну из папок:
               • Корень проекта
               • src/main/resources/
               • Укажите путь в переменной GOOGLE_APPLICATION_CREDENTIALS
            
            3. Включите Text-to-Speech API в Google Cloud Console
            
            4. Перезапустите приложение
            
            🔧 ТЕКУЩИЙ СТАТУС:
            • Режим: Демо (без звука)
            • Функция: Только логирование
            """;
    }

    private void setupGoogleCloudTTSCredentials() {
        try {
            logger.info("=== НАСТРОЙКА GOOGLE CLOUD TTS ===");

            String projectRoot = System.getProperty("user.dir");
            logger.info("Корневая папка проекта: {}", projectRoot);

            String[] possiblePaths = {
                    projectRoot + "/google-credentials.json",
                    projectRoot + "/src/main/resources/google-credentials.json",
                    "google-credentials.json",
                    "./google-credentials.json"
            };

            File credentialsFile = null;
            for (String path : possiblePaths) {
                File file = new File(path);
                logger.info("Проверяем путь: {} -> существует: {}", path, file.exists());
                if (file.exists() && file.canRead()) {
                    credentialsFile = file;
                    logger.info("✅ Найден файл учетных данных: {}", path);
                    break;
                }
            }

            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsFile == null && envPath != null && !envPath.isEmpty()) {
                File envFile = new File(envPath);
                if (envFile.exists() && envFile.canRead()) {
                    credentialsFile = envFile;
                    logger.info("✅ Найден файл учетных данных из переменной окружения: {}", envPath);
                }
            }

            if (credentialsFile == null) {
                logger.error("❌ Файл google-credentials.json не найден ни в одном из возможных мест");
                logger.info("Создайте файл google-credentials.json в корне проекта или в папке src/main/resources/");
                return;
            }

            System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", credentialsFile.getAbsolutePath());
            logger.info("✅ Установлена переменная: GOOGLE_APPLICATION_CREDENTIALS={}",
                    credentialsFile.getAbsolutePath());

            try {
                String content = new String(Files.readAllBytes(credentialsFile.toPath()));
                logger.info("✅ Файл успешно прочитан, размер: {} байт", content.length());

                JSONObject json = new JSONObject(content);
                String projectId = json.optString("project_id", "не указан");
                String clientEmail = json.optString("client_email", "не указан");
                logger.info("✅ Project ID: {}", projectId);
                logger.info("✅ Client Email: {}", clientEmail);

            } catch (Exception e) {
                logger.error("❌ Ошибка при чтении файла: {}", e.getMessage());
            }

            logger.info("=== НАСТРОЙКА ЗАВЕРШЕНА ===");

        } catch (Exception e) {
            logger.error("Ошибка при настройке Google Cloud TTS", e);
        }
    }

    private void showTTSConnectionSuccess(GoogleCloudTextToSpeechService service) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Google Cloud TTS подключен");
            alert.setHeaderText("✅ Голосовая озвучка активирована");
            alert.setContentText(String.format("""
            Google Cloud Text-to-Speech успешно подключен!
            
            📊 ИНФОРМАЦИЯ:
            • Метод: %s
            • Голос: %s
            • Язык: %s
            • Качество: %s
            
            🔊 Теперь приложение может озвучивать ответы голосом.
            """,
                    service.getAuthMethod(),
                    service.getCurrentVoice().getDescription(),
                    service.getCurrentLanguage(),
                    service.getCurrentVoice().getModelType()
            ));
            alert.show();
        });
    }

    private void showTTSConnectionWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("TTS в демо-режиме");
        alert.setHeaderText("⚠️ Google Cloud TTS не настроен");

        StringBuilder content = new StringBuilder();
        content.append("Используется демо-режим без реальной озвучки.\n\n");
        content.append("📁 ПРОВЕРЕННЫЕ ПУТИ:\n");

        String projectRoot = System.getProperty("user.dir");
        String[] paths = {
                projectRoot + "/google-credentials.json",
                projectRoot + "/src/main/resources/google-credentials.json",
                "google-credentials.json",
                "./google-credentials.json"
        };

        boolean foundAny = false;
        for (String path : paths) {
            File file = new File(path);
            String status = file.exists() ? "✅ Файл существует" : "❌ Не найден";
            if (file.exists()) foundAny = true;
            content.append("  • ").append(path).append(" - ").append(status).append("\n");
        }

        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null && !envPath.isEmpty()) {
            File envFile = new File(envPath);
            String status = envFile.exists() ? "✅ Файл существует" : "❌ Не найден";
            content.append("  • ").append(envPath).append(" (переменная окружения) - ").append(status).append("\n");
            if (envFile.exists()) foundAny = true;
        }

        if (foundAny) {
            content.append("\n⚠️ Файл найден, но Google Cloud TTS недоступен.\n");
            content.append("   Проверьте:\n");
            content.append("   • Включен ли Text-to-Speech API в проекте\n");
            content.append("   • Есть ли у Service Account права на TTS\n");
            content.append("   • Работает ли интернет-соединение\n");
        } else {
            content.append("\n📋 ИНСТРУКЦИЯ ПО НАСТРОЙКЕ:\n");
            content.append("   1. Создайте проект в Google Cloud Console\n");
            content.append("   2. Включите Text-to-Speech API\n");
            content.append("   3. Создайте Service Account и скачайте ключ JSON\n");
            content.append("   4. Сохраните файл как google-credentials.json\n");
            content.append("   5. Перезапустите приложение\n");
        }

        alert.setContentText(content.toString());
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(700);
        alert.show();
    }

    private AiService initializeAIService(Properties props) {
        return ErrorHandler.orElse(() -> {
            AiService service = AIServiceFactory.createService(props);
            logger.info("✅ AI сервис инициализирован: {}",
                    service.getClass().getSimpleName());
            return service;
        }, new MockAiService(), "Ошибка инициализации AI сервиса, используется Mock");
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            event.consume();
            close();
        });

        if (analysisManager != null) {
            this.analysisManager = new AnalysisManager(
                    analysisArea,
                    detailedAnalysisArea,
                    recommendationsArea,
                    analyzeButton,
                    pronunciationButton,
                    analysisProgress,
                    phonemeLabel,
                    statusLabel,
                    audioAnalyzer,
                    pronunciationTrainer,
                    chatBotService,
                    state,
                    stage
            );
        }
    }

    @Override
    public void close() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }

        logger.info("Закрытие ChatBotController...");

        CompletableFuture<Void> operation = currentOperation.getAndSet(null);
        if (operation != null && !operation.isDone()) {
            operation.cancel(true);
        }

        if (responseModeManager != null) {
            responseModeManager.stopSpeaking();
            responseModeManager.shutdown();
        }

        if (speechRecognitionUIManager != null) {
            speechRecognitionUIManager.cancelCurrentTest();
        }

        if (recordingManager != null && recordingManager.isRecording()) {
            recordingManager.forceStop();
        }

        if (textToSpeechService != null) {
            textToSpeechService.stopSpeaking();
        }

        if (analysisManager != null) {
            analysisManager.cancelAnalysis();
        }

        if (learningModeManager != null && chatBotService.getCurrentUser() != null) {
            String userId = chatBotService.getCurrentUserId();
            if (userId != null) {
                learningModeManager.clearUserSession(userId);
            }
        }

        ErrorHandler.safeClose(resourceManager, "ResourceManager");

        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }

        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });

        logger.info("ChatBotController закрыт");
    }
}