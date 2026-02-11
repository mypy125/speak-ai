package com.mygitgor.chatbot;

import com.mygitgor.ai.AIServiceFactory;
import com.mygitgor.ai.AiService;
import com.mygitgor.ai.MockAiService;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.chatbot.components.*;
import com.mygitgor.config.AppConstants;
import com.mygitgor.config.ServicesConfig;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.error.ServiceInitializationException;
import com.mygitgor.model.Conversation;
import com.mygitgor.service.ChatBotService;
import com.mygitgor.service.AudioAnalyzer;
import com.mygitgor.service.DemoTextToSpeechService;
import com.mygitgor.service.components.ResponseMode;
import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.speech.SpeechRecorder;
import com.mygitgor.service.GoogleCloudTextToSpeechService;
import com.mygitgor.state.ChatBotState;
import com.mygitgor.utils.ResourceManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class ChatBotController implements Initializable, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotController.class);

    // ========================================
    // FXML UI Elements - Chat Area
    // ========================================
    @FXML private TextArea inputField;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Button historyButton;
    @FXML private VBox chatMessagesContainer;
    @FXML private ScrollPane chatScrollPane;

    // ========================================
    // FXML UI Elements - TTS Panel
    // ========================================
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

    // ========================================
    // FXML UI Elements - Speech Recognition
    // ========================================
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

    // ========================================
    // FXML UI Elements - Recording Controls
    // ========================================
    @FXML private Button recordButton;
    @FXML private Button stopButton;
    @FXML private ProgressIndicator recordingIndicator;
    @FXML private Label recordingTimeLabel;

    // ========================================
    // FXML UI Elements - Analysis
    // ========================================
    @FXML private TextArea analysisArea;
    @FXML private TextArea detailedAnalysisArea;
    @FXML private TextArea recommendationsArea;
    @FXML private Button analyzeButton;
    @FXML private Button pronunciationButton;
    @FXML private ProgressBar analysisProgress;
    @FXML private Label phonemeLabel;

    // ========================================
    // FXML UI Elements - Response Mode
    // ========================================
    @FXML private ToggleGroup responseModeToggleGroup;
    @FXML private Button playResponseButton;
    @FXML private ToggleButton textToggle;
    @FXML private ToggleButton voiceToggle;

    // ========================================
    // FXML UI Elements - Status & Statistics
    // ========================================
    @FXML private Label statusLabel;
    @FXML private Label messagesCountLabel;
    @FXML private Label analysisCountLabel;
    @FXML private Label recordingsCountLabel;

    // ========================================
    // Services
    // ========================================
    private ChatBotService chatBotService;
    private ITTSService textToSpeechService;
    private SpeechRecorder speechRecorder;
    private AudioAnalyzer audioAnalyzer;
    private PronunciationTrainer pronunciationTrainer;
    private ResourceManager resourceManager;

    // ========================================
    // State & Managers
    // ========================================
    private ChatBotState state;
    private ServicesConfig config;
    private Stage stage;

    // UI Managers
    private ChatMessagesManager messagesManager;
    private StatisticsManager statisticsManager;
    private TTSControlsManager ttsControlsManager;
    private SpeechRecognitionUIManager speechRecognitionUIManager;
    private RecordingManager recordingManager;
    private ResponseModeManager responseModeManager;
    private AnalysisManager analysisManager;

    // ========================================
    // Initialization
    // ========================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Инициализация ChatBotController");

        ErrorHandler.handle(() -> {
            initializeComponents();
            setupServices();
            setupManagers();
            setupUI();
            showWelcomeMessage();
            logger.info("ChatBotController успешно инициализирован");
            return null;
        }, e -> {
            logger.error("Критическая ошибка при инициализации", e);
            ErrorHandler.showError("Ошибка инициализации",
                    "Не удалось инициализировать приложение: " + e.getMessage());
            return null;
        }, "Ошибка инициализации контроллера");
    }

    private void initializeComponents() {
        this.state = new ChatBotState();
        this.resourceManager = new ResourceManager();
        this.speechRecorder = new SpeechRecorder();
        this.audioAnalyzer = new AudioAnalyzer();
        this.pronunciationTrainer = new PronunciationTrainer();

        this.messagesManager = new ChatMessagesManager(chatMessagesContainer, chatScrollPane);
    }

    private void setupServices() {
        // Load configuration
        this.config = ServicesConfig.load();

        // Initialize services in correct order
        this.audioAnalyzer = new AudioAnalyzer();
        this.pronunciationTrainer = new PronunciationTrainer();
        this.speechRecorder = new SpeechRecorder();

        // Register basic services first
        resourceManager.register(audioAnalyzer);
        resourceManager.register(pronunciationTrainer);
        resourceManager.register(speechRecorder);

        // Initialize TTS service with fallback
        this.textToSpeechService = initializeTTSService();
        resourceManager.register(textToSpeechService);

        // Initialize AI service
        AiService aiService = initializeAIService();
        resourceManager.registerIfCloseable(aiService);

        // Initialize ChatBotService
        this.chatBotService = new ChatBotService(
                aiService,
                audioAnalyzer,
                pronunciationTrainer,
                textToSpeechService,
                speechRecorder
        );
        resourceManager.register(chatBotService);

        // Update state
        state.setAiServiceAvailable(aiService.isAvailable());

        logger.info("Все сервисы успешно инициализированы");
    }

    private ITTSService initializeTTSService() {
        // Сначала пробуем настроить Google Cloud TTS
        setupGoogleCloudTTSCredentials();

        // Пробуем инициализировать Google Cloud TTS с retry
        try {
            GoogleCloudTextToSpeechService googleService = ErrorHandler.retry(() -> {
                try {
                    // Создаем сервис с автоматическим определением credentials
                    GoogleCloudTextToSpeechService service = new GoogleCloudTextToSpeechService();

                    // Даем время на проверку доступности
                    Thread.sleep(AppConstants.GOOGLE_TTS_INIT_DELAY_MS);

                    if (service.isAvailable()) {
                        logger.info("✅ Google Cloud TTS успешно инициализирован");
                        logger.info("   Голос: {}", service.getCurrentVoice().getDescription());
                        logger.info("   Метод аутентификации: {}", service.getAuthMethod());
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
                // Показываем сообщение об успешном подключении
                showTTSConnectionSuccess(googleService);
                return googleService;
            }

        } catch (Exception e) {
            logger.warn("Не удалось инициализировать Google Cloud TTS: {}", e.getMessage());
        }

        // Fallback на демо-режим
        return initializeDemoTTSService();
    }

    private ITTSService initializeDemoTTSService() {
        logger.warn("⚠️ Используется демо-режим TTS (без реальной озвучки)");

        DemoTextToSpeechService demoServices = new DemoTextToSpeechService();

        // Показываем предупреждение пользователю в UI
        Platform.runLater(() -> {
            showTTSConnectionWarning();

            // Обновляем статус в UI
            if (ttsStatusLabel != null) {
                ttsStatusLabel.setText("⚠️ Демо-режим TTS");
                ttsStatusLabel.setStyle("-fx-text-fill: #f39c12;");
            }
            if (ttsStatusIndicator != null) {
                ttsStatusIndicator.setFill(Color.ORANGE);
            }
            if (ttsModeLabel != null) {
                ttsModeLabel.setText("Демо-режим TTS");
            }
            if (ttsInfoTextArea != null) {
                ttsInfoTextArea.setText("""
                ⚠️ TTS В ДЕМО-РЕЖИМЕ

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
                """);
            }
        });

        return demoServices;
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

        // Создаем расширенный контент с информацией о найденных/не найденных файлах
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

        // Также проверяем переменную окружения
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

    private void setupGoogleCloudTTSCredentials() {
        try {
            logger.info("=== НАСТРОЙКА GOOGLE CLOUD TTS ===");

            // Получаем путь к корневой папке проекта
            String projectRoot = System.getProperty("user.dir");
            logger.info("Корневая папка проекта: {}", projectRoot);

            // Проверяем разные возможные расположения файла
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

            // Также проверяем переменную окружения
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

            // Устанавливаем переменную окружения
            System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", credentialsFile.getAbsolutePath());
            logger.info("✅ Установлена переменная: GOOGLE_APPLICATION_CREDENTIALS={}",
                    credentialsFile.getAbsolutePath());

            // Проверяем, что файл можно прочитать
            try {
                String content = new String(Files.readAllBytes(credentialsFile.toPath()));
                logger.info("✅ Файл успешно прочитан, размер: {} байт", content.length());

                // Парсим JSON для проверки
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

    private GoogleCloudTextToSpeechService createGoogleTTSService() {
        String credentialsPath = findGoogleCredentials();

        if (credentialsPath == null) {
            throw new ServiceInitializationException(
                    "Файл google-credentials.json не найден");
        }

        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", credentialsPath);
        return new GoogleCloudTextToSpeechService(credentialsPath);
    }

    private String findGoogleCredentials() {
        String projectRoot = System.getProperty("user.dir");

        for (String path : AppConstants.GOOGLE_CREDENTIALS_PATHS) {
            String fullPath = path.startsWith("./") ?
                    projectRoot + path.substring(1) : path;

            java.io.File file = new java.io.File(fullPath);
            logger.debug("Проверка пути: {} -> {}", fullPath, file.exists());

            if (file.exists() && file.canRead()) {
                logger.info("✅ Найден файл учетных данных: {}", fullPath);
                return fullPath;
            }
        }

        // Проверяем переменную окружения
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null && !envPath.isEmpty()) {
            File envFile = new File(envPath);
            if (envFile.exists() && envFile.canRead()) {
                logger.info("✅ Найден файл учетных данных из переменной окружения: {}", envPath);
                return envPath;
            }
        }

        return null;
    }

    private AiService initializeAIService() {
        return ErrorHandler.orElse(() -> {
            AiService service = AIServiceFactory.createService(config.getRawProperties());
            logger.info("✅ AI сервис инициализирован: {}",
                    service.getClass().getSimpleName());
            return service;
        }, new MockAiService(), "Ошибка инициализации AI сервиса, используется Mock");
    }

    private void setupManagers() {
        // Statistics Manager
        this.statisticsManager = new StatisticsManager(
                messagesCountLabel, analysisCountLabel, recordingsCountLabel, state);

        // TTS Controls Manager
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

        // Speech Recognition UI Manager
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

        // Recording Manager
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

        // Response Mode Manager
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

        // Analysis Manager
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

    private void showWelcomeMessage() {
        String ttsMode = textToSpeechService != null && textToSpeechService.isAvailable()
                ? "Google Cloud TTS (WaveNet)"
                : "TTS недоступен";

        String welcomeMessage = String.format("""
            🌟 Добро пожаловать в SpeakAI!
            
            Текущий режим: %s
            Режим ответа: %s
            
            Как это работает:
            1. Напишите сообщение или нажмите кнопку записи 🎤
            2. Получите анализ речи и рекомендации
            3. Используйте тренажер произношения для проблемных звуков
            
            Давайте начнем! ✨
            """, ttsMode,
                state.getCurrentResponseMode() == ResponseMode.VOICE ?
                        "🔊 Голосовой" : "📝 Текстовый");

        messagesManager.addAIMessage(welcomeMessage);
    }

    private void updateStatusLabel() {
        if (statusLabel == null) return;

        String status = state.isAiServiceAvailable()
                ? "✅ ИИ-сервис доступен"
                : "⚠️ Демо-режим";

        String finalStatus = status;
        Platform.runLater(() -> statusLabel.setText(finalStatus));
    }

    private void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(show ? "⏳ Обработка..." :
                        state.isAiServiceAvailable() ? "✅ ИИ-сервис доступен" : "⚠️ Демо-режим");
            }
            if (sendButton != null) {
                sendButton.setDisable(show);
            }
        });
    }

    // ========================================
    // Event Handlers - Main Actions
    // ========================================

    @FXML
    private void onSendMessage() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }

        String text = inputField.getText().trim();

        if (text.isEmpty() && !state.hasAudioFile()) {
            ErrorHandler.showWarning("Внимание", "Введите сообщение или запишите аудио");
            return;
        }

        // Add user message
        if (!text.isEmpty()) {
            messagesManager.addUserMessage(text);
            inputField.clear();
        } else if (state.hasAudioFile()) {
            messagesManager.addUserMessage("🎤 [Аудиосообщение]");
        }

        statisticsManager.onMessageSent();
        showLoadingIndicator(true);

        CompletableFuture.runAsync(() -> {
            try {
                ChatBotService.ChatResponse response = chatBotService.processUserInput(
                        text,
                        state.getCurrentAudioFile(),
                        state.getCurrentResponseMode()
                );

                Platform.runLater(() -> {
                    messagesManager.addAIMessage(response.getFullResponse());

                    // ВАЖНО: Передаем ответ, но НЕ запускаем автоматическую озвучку
                    // responseModeManager.onNewResponse(response.getFullResponse()); // УДАЛИТЬ!

                    // Вместо этого просто сохраняем ответ
                    state.setLastBotResponse(response.getFullResponse());

                    // Обновляем кнопку воспроизведения
                    responseModeManager.updatePlayButtonVisibility();

                    if (response.getSpeechAnalysis() != null) {
                        analysisManager.processAnalysisResponse(response);
                    }

                    showLoadingIndicator(false);

                    // Clear audio file after processing
                    if (state.hasAudioFile()) {
                        state.clearCurrentAudioFile();
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка при обработке сообщения", e);
                Platform.runLater(() -> {
                    ErrorHandler.showError("Ошибка",
                            "Не удалось обработать сообщение: " + e.getMessage());
                    showLoadingIndicator(false);
                });
            }
        });
    }

    // ========================================
    // Event Handlers - Recording
    // ========================================

    @FXML
    private void onStartRecording() {
        recordingManager.startRecording();
    }

    @FXML
    private void onStopRecording() {
        recordingManager.stopRecording();
    }

    // ========================================
    // Event Handlers - Analysis
    // ========================================

    @FXML
    private void onAnalyzeAudio() {
        analysisManager.analyzeCurrentAudio(inputField.getText().trim());
    }

    @FXML
    private void onPronunciationTraining() {
        analysisManager.showPronunciationTrainer();
    }

    // ========================================
    // Event Handlers - Speech Recognition
    // ========================================

    @FXML
    private void testSpeechRecognition() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        speechRecognitionUIManager.startSpeechRecognitionTest(AppConstants.TEST_AUDIO_PATH);
    }

    @FXML
    private void testMicrophone() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        speechRecognitionUIManager.startMicrophoneTest();
    }

    @FXML
    private void onMicrophoneRecognition() {
        if (state.isClosed()) {
            ErrorHandler.showError("Ошибка", "Приложение закрывается");
            return;
        }
        speechRecognitionUIManager.startMicrophoneRecognition(inputField);
    }

    @FXML
    private void toggleSpeechPanel() {
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

    // ========================================
    // Event Handlers - TTS
    // ========================================

    @FXML
    private void onTestGoogleTTS() {
        if (textToSpeechService == null) {
            ErrorHandler.showError("Ошибка", "TTS сервис не инициализирован");
            return;
        }

        // Проверяем, является ли сервис Google Cloud TTS
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
            // Если это не Google Cloud TTS (например, демо-режим)
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
        responseModeManager.stopSpeaking();
    }

    @FXML
    private void onPlayResponse() {
        responseModeManager.playLastResponse();
    }

    // ========================================
    // Event Handlers - History & Navigation
    // ========================================

    @FXML
    private void onShowHistory() {
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
            
            📞 КОНТАКТЫ:
            support@speakai.com
            """;

        ErrorHandler.showInfo("Помощь", helpText);
    }

    @FXML
    private void onSettings() {
        ErrorHandler.showInfo("Настройки",
                "Настройки будут доступны в следующей версии");
    }

    // ========================================
    // Helper Methods
    // ========================================

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

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, width, height);
        textStage.setScene(scene);
        textStage.show();
    }

    // ========================================
    // Lifecycle
    // ========================================

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> close());

        // Update analysis manager with stage
        if (analysisManager != null) {
            // Recreate analysis manager with stage
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
        if (state.setClosed(true)) {
            logger.info("Закрытие ChatBotController...");

            // Stop all ongoing operations
            if (responseModeManager != null) {
                responseModeManager.stopSpeaking();
            }

            if (speechRecognitionUIManager != null) {
                speechRecognitionUIManager.cancelCurrentTest();
            }

            if (recordingManager != null && recordingManager.isRecording()) {
                recordingManager.stopRecording();
            }

            if (textToSpeechService != null) {
                textToSpeechService.stopSpeaking();
            }

            // Close resources
            ErrorHandler.safeClose(resourceManager, "ResourceManager");

            logger.info("ChatBotController закрыт");
        }
    }

    public void onTestTTS(ActionEvent actionEvent) {
    }
}