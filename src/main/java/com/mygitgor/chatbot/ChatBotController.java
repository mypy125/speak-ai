package com.mygitgor.chatbot;

import com.mygitgor.ai.AIServiceFactory;
import com.mygitgor.ai.AiService;
import com.mygitgor.ai.MockAiService;
import com.mygitgor.ai.UniversalAIService;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.speech.AudioAnalyzer;
import com.mygitgor.speech.SpeechPlayer;
import com.mygitgor.speech.SpeechRecorder;
import com.mygitgor.speech.SpeechToTextService;
import com.mygitgor.utils.ResourceManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ChatBotController implements Initializable, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotController.class);

    // ========================================
    // FXML UI Elements - Chat Area
    // ========================================
    @FXML private TextArea inputField;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Button historyButton;

    // ========================================
    // FXML UI Elements - Recording Controls
    // ========================================
    @FXML private Button recordButton;
    @FXML private Button stopButton;
    @FXML private ProgressIndicator recordingIndicator;
    @FXML private Label recordingTimeLabel;
    @FXML private Button microphoneButton;
    @FXML private Label microphoneStatusLabel;

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

    // ========================================
    // FXML UI Elements - Status & Statistics
    // ========================================
    @FXML private Label statusLabel;
    @FXML private Label messagesCountLabel;
    @FXML private Label analysisCountLabel;
    @FXML private Label recordingsCountLabel;

    // ========================================
    // FXML UI Elements - Container
    // ========================================
    @FXML private VBox mainContainer;
    @FXML private VBox chatMessagesContainer;

    // ========================================
    // Services
    // ========================================
    private ChatBotService chatBotService;
    private SpeechRecorder speechRecorder;
    private SpeechPlayer speechPlayer;
    private AudioAnalyzer audioAnalyzer;
    private PronunciationTrainer pronunciationTrainer;
    private ResourceManager resourceManager;

    // ========================================
    // State
    // ========================================
    private Stage stage;
    private String currentAudioFile;
    private Thread recordingTimerThread;
    private boolean isAiServiceAvailable;
    private volatile boolean closed = false;

    // ========================================
    // Statistics
    // ========================================
    private int messagesCount = 0;
    private int analysisCount = 0;
    private int recordingsCount = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Инициализация ChatBotController");
        resourceManager = new ResourceManager();

        try {
            initializeServices();
            setupUI();
            setupSpeechRecognitionUI();
            initializeStatistics();
            loadConversationHistory();
            showWelcomeMessage();
            logger.info("ChatBotController успешно инициализирован");
        } catch (Exception e) {
            logger.error("Критическая ошибка при инициализации контроллера", e);
            showError("Ошибка инициализации",
                    "Не удалось инициализировать приложение: " + e.getMessage());
        }
    }

    // ========================================
    // Initialization Methods
    // ========================================

    private void initializeServices() {
        try {
            Properties props = new Properties();
            props.load(getClass().getResourceAsStream("/application.properties"));

            // Создаем AI сервис
            AiService aiService = AIServiceFactory.createService(props);
            isAiServiceAvailable = aiService.isAvailable();

            if (aiService instanceof UniversalAIService universalService) {
                logger.info("✅ {} сервис инициализирован", universalService.getProvider());
                logger.info("Модель: {}", universalService.getModel());
            } else if (aiService instanceof MockAiService) {
                logger.warn("🔄 Используется Mock сервис (демо-режим)");
            }

            // Создаем компоненты
            this.audioAnalyzer = new AudioAnalyzer();
            logger.info("AudioAnalyzer создан в контроллере");

            this.pronunciationTrainer = new PronunciationTrainer();
            logger.info("PronunciationTrainer создан");

            // Создаем главный сервис
            this.chatBotService = new ChatBotService(
                    aiService,
                    audioAnalyzer,
                    pronunciationTrainer
            );
            logger.info("ChatBotService создан с внешним AudioAnalyzer");

            // Создаем компоненты для работы с аудио
            this.speechRecorder = new SpeechRecorder();
            this.speechPlayer = new SpeechPlayer();

            // Регистрируем ресурсы для автоматического закрытия
            resourceManager.register(speechPlayer);
            resourceManager.register(speechRecorder);
            resourceManager.register(chatBotService);
            resourceManager.register(audioAnalyzer);

            if (aiService instanceof Closeable) {
                resourceManager.register((Closeable) aiService);
            }

            if (pronunciationTrainer instanceof Closeable) {
                resourceManager.register((Closeable) pronunciationTrainer);
            }

            showServiceStatus(aiService);
            logger.info("Все сервисы инициализированы и зарегистрированы");

        } catch (Exception e) {
            logger.error("Ошибка при инициализации сервисов", e);
            cleanupResources();
            throw new RuntimeException("Не удалось инициализировать сервисы", e);
        }
    }

    private void setupUI() {
        logger.info("Настройка UI элементов");

        // Настройка обработчиков событий для поля ввода
        if (inputField != null) {
            inputField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
                    // Shift+Enter - новая строка
                    inputField.appendText("\n");
                    event.consume();
                } else if (event.getCode() == KeyCode.ENTER) {
                    // Enter - отправка сообщения
                    event.consume();
                    onSendMessage();
                }
            });
        }

        // Инициализация состояния кнопок
        if (stopButton != null) stopButton.setDisable(true);
        if (recordingIndicator != null) recordingIndicator.setVisible(false);
        if (recordingTimeLabel != null) recordingTimeLabel.setText("00:00");
        if (analyzeButton != null) analyzeButton.setDisable(true);
        if (pronunciationButton != null) pronunciationButton.setDisable(true);
        if (analysisProgress != null) analysisProgress.setVisible(false);
        if (detailedAnalysisArea != null) detailedAnalysisArea.setVisible(false);
        if (phonemeLabel != null) phonemeLabel.setVisible(false);

        // Инициализация слайдера чувствительности
        setupMicrophoneSensitivitySlider();

        // Обновление статуса
        updateStatusLabel();

        // Инициализация контейнера для сообщений
        if (chatMessagesContainer != null) {
            chatMessagesContainer.setStyle("-fx-background-color: #fafafa;");
        }

        logger.info("UI элементы настроены");
    }

    private void setupMicrophoneSensitivitySlider() {
        if (microphoneSensitivitySlider != null) {
            microphoneSensitivitySlider.setMin(0.1);
            microphoneSensitivitySlider.setMax(1.0);
            microphoneSensitivitySlider.setValue(0.5);

            microphoneSensitivitySlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                double sensitivity = Math.round(newValue.doubleValue() * 10) / 10.0;
                if (sensitivityLabel != null) {
                    sensitivityLabel.setText(String.format("%.1f", sensitivity));
                }
                if (chatBotService != null) {
                    chatBotService.setMicrophoneSensitivity(sensitivity);
                }
            });
        }

        // Начальное значение для метки чувствительности
        if (sensitivityLabel != null) {
            sensitivityLabel.setText(String.format("%.1f",
                    microphoneSensitivitySlider != null ? microphoneSensitivitySlider.getValue() : 0.5));
        }
    }

    private void setupSpeechRecognitionUI() {
        // Проверяем наличие элементов UI
        if (serviceTypeComboBox == null || languageComboBox == null) {
            logger.warn("Элементы управления распознаванием речи не найдены в FXML");
            return;
        }

        // Настройка ComboBox с типами сервисов
        serviceTypeComboBox.getItems().addAll(
                "MOCK - Тестовый режим",
                "VOSK - Оффлайн распознавание",
                "WHISPER - OpenAI Whisper",
                "GOOGLE - Google Speech API"
        );
        serviceTypeComboBox.setValue("MOCK - Тестовый режим");

        // Обработчик изменения выбора типа сервиса
        serviceTypeComboBox.setOnAction(event -> {
            String selected = serviceTypeComboBox.getValue();
            updateSpeechServiceStatus(selected);
        });

        // Настройка ComboBox с языками
        languageComboBox.setVisible(false);

        // Загрузка языков в фоновом потоке
        new Thread(() -> {
            try {
                Map<String, String> languages = chatBotService.getSupportedLanguagesWithNames();
                Platform.runLater(() -> {
                    if (languageComboBox != null) {
                        languageComboBox.getItems().clear();
                        languages.forEach((code, name) -> {
                            languageComboBox.getItems().add(name + " (" + code + ")");
                        });
                        String currentLangName = chatBotService.getCurrentSpeechLanguageName();
                        String currentLangCode = chatBotService.getCurrentSpeechLanguage();
                        languageComboBox.setValue(currentLangName + " (" + currentLangCode + ")");
                        languageComboBox.setVisible(true);
                    }
                });
            } catch (Exception e) {
                logger.error("Ошибка при загрузке языков", e);
            }
        }).start();

        // Обработчик изменения языка
        languageComboBox.setOnAction(event -> {
            String selected = languageComboBox.getValue();
            if (selected != null) {
                try {
                    String languageCode = selected.substring(
                            selected.lastIndexOf("(") + 1,
                            selected.lastIndexOf(")")
                    );
                    chatBotService.switchSpeechLanguage(languageCode);
                    updateSpeechServiceStatus(serviceTypeComboBox.getValue());
                } catch (Exception e) {
                    logger.error("Ошибка при обработке выбора языка", e);
                }
            }
        });

        // Инициализация статуса
        if (speechServiceStatusLabel != null) {
            updateSpeechServiceStatus(serviceTypeComboBox.getValue());
        }

        logger.info("UI распознавания речи настроен");
    }

    private void initializeStatistics() {
        updateStatistics();
    }

    private void showWelcomeMessage() {
        // Добавляем разделитель времени
        addTimeDivider("Сегодня");

        String welcomeMessage = """
            🌟 Добро пожаловать в SpeakAI!
            
            Я ваш персональный ИИ-репетитор английского языка.
            
            Как это работает:
            1. Напишите сообщение на английском (или нажмите кнопку записи)
            2. Я проанализирую вашу речь и грамматику
            3. Получите подробную обратную связь и рекомендации
            
            Настройки распознавания речи:
            • Выберите язык в выпадающем списке
            • Отрегулируйте чувствительность микрофона
            • Протестируйте микрофон перед записью
            
            Совет: Используйте микрофон для записи речи - так я смогу проанализировать ваше произношение!
            
            Давайте начнем обучение! ✨
            """;

        addAIMessage(welcomeMessage);
    }

    // ========================================
    // Event Handlers - Main Actions
    // ========================================

    @FXML
    private void onSendMessage() {
        if (closed) {
            showError("Ошибка", "Приложение закрывается. Невозможно отправить сообщение.");
            return;
        }

        String text = inputField.getText().trim();

        if (text.isEmpty() && currentAudioFile == null) {
            showAlert("Внимание", "Введите сообщение или запишите аудио перед отправкой");
            return;
        }

        // Добавляем сообщение пользователя в чат
        if (!text.isEmpty()) {
            addUserMessage(text);
        } else if (currentAudioFile != null) {
            addUserMessage("🎤 [Аудиосообщение]");
        }

        // Очищаем поле ввода
        inputField.clear();

        // Показываем индикатор загрузки
        showLoadingIndicator(true);

        // Увеличиваем счетчик сообщений
        messagesCount++;
        updateStatistics();

        // Обработка в фоновом потоке
        new Thread(() -> {
            try {
                ChatBotService.ChatResponse response = chatBotService.processUserInput(text, currentAudioFile);

                Platform.runLater(() -> processResponse(response));

            } catch (Exception e) {
                logger.error("Ошибка при обработке сообщения", e);
                Platform.runLater(() -> {
                    showError("Ошибка", "Не удалось обработать сообщение: " + e.getMessage());
                    showLoadingIndicator(false);
                });
            }
        }).start();
    }

    private void processResponse(ChatBotService.ChatResponse response) {
        // Добавляем ответ бота
        addAIMessage(response.getFullResponse());

        // Обновляем области анализа
        if (response.getSpeechAnalysis() != null) {
            processAnalysisResponse(response);
            analysisCount++;
            updateStatistics();
        } else {
            // Деактивируем кнопки анализа
            if (analyzeButton != null) analyzeButton.setDisable(true);
            if (pronunciationButton != null) pronunciationButton.setDisable(true);
            if (phonemeLabel != null) phonemeLabel.setVisible(false);
            if (detailedAnalysisArea != null) detailedAnalysisArea.setVisible(false);
        }

        // Скрываем индикатор загрузки
        showLoadingIndicator(false);

        // Показываем уведомление о завершении
        if (response.getSpeechAnalysis() != null) {
            showAnalysisCompletionNotification(response.getSpeechAnalysis());
        } else {
            if (statusLabel != null) {
                statusLabel.setText("✅ Сообщение обработано");
            }
        }
    }

    private void processAnalysisResponse(ChatBotService.ChatResponse response) {
        SpeechAnalysis analysis = response.getSpeechAnalysis();

        // Обработка EnhancedSpeechAnalysis
        if (analysis instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
            if (analysisArea != null) {
                analysisArea.setText(enhancedAnalysis.getSummary());
            }

            // Детальный анализ
            if (detailedAnalysisArea != null &&
                    enhancedAnalysis.getDetailedReport() != null &&
                    !enhancedAnalysis.getDetailedReport().isEmpty()) {
                detailedAnalysisArea.setText(enhancedAnalysis.getDetailedReport());
                detailedAnalysisArea.setVisible(true);
            }

            // Обновляем метку фонемы
            if (phonemeLabel != null &&
                    enhancedAnalysis.getPhonemeScores() != null &&
                    !enhancedAnalysis.getPhonemeScores().isEmpty()) {

                String worstPhoneme = enhancedAnalysis.getPhonemeScores().entrySet().stream()
                        .min((e1, e2) -> Float.compare(e1.getValue(), e2.getValue()))
                        .map(entry -> "/" + entry.getKey() + "/")
                        .orElse("");

                if (!worstPhoneme.isEmpty()) {
                    phonemeLabel.setText("Слабая фонема: " + worstPhoneme);
                    phonemeLabel.setVisible(true);
                }
            }

            // Активируем кнопки
            if (analyzeButton != null) analyzeButton.setDisable(false);
            if (pronunciationButton != null) {
                pronunciationButton.setDisable(false);
                updatePronunciationButtonText(enhancedAnalysis);
            }

        } else {
            if (analysisArea != null) {
                analysisArea.setText(analysis.getSummary());
            }
        }

        // Обновляем рекомендации
        updateRecommendationsArea(response);
    }

    private void updatePronunciationButtonText(EnhancedSpeechAnalysis analysis) {
        if (pronunciationButton == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            long weakPhonemesCount = analysis.getPhonemeScores().values().stream()
                    .filter(score -> score < 70)
                    .count();

            if (weakPhonemesCount > 0) {
                pronunciationButton.setText("🗣️ Тренажер (" + weakPhonemesCount + " проблем)");
            } else {
                pronunciationButton.setText("🗣️ Тренажер произношения");
            }
        }
    }

    private void updateRecommendationsArea(ChatBotService.ChatResponse response) {
        if (recommendationsArea == null) return;

        StringBuilder recommendationsText = new StringBuilder();

        // Базовые рекомендации
        if (response.getSpeechAnalysis() != null &&
                !response.getSpeechAnalysis().getRecommendations().isEmpty()) {
            recommendationsText.append("БАЗОВЫЕ РЕКОМЕНДАЦИИ:\n");
            for (String rec : response.getSpeechAnalysis().getRecommendations()) {
                recommendationsText.append("• ").append(rec).append("\n");
            }
            recommendationsText.append("\n");
        }

        // Персонализированные рекомендации
        if (response.getPersonalizedRecommendations() != null &&
                !response.getPersonalizedRecommendations().isEmpty()) {

            recommendationsText.append("ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ:\n\n");

            for (RecommendationEngine.PersonalizedRecommendation rec :
                    response.getPersonalizedRecommendations()) {
                recommendationsText.append("[").append(rec.getPriority()).append("] ")
                        .append(rec.getTitle()).append("\n");
                recommendationsText.append(rec.getDescription()).append("\n");
                recommendationsText.append("Упражнения:\n");
                for (String exercise : rec.getExercises()) {
                    recommendationsText.append("  • ").append(exercise).append("\n");
                }
                recommendationsText.append("Ожидаемое улучшение: ")
                        .append(String.format("%.1f", rec.getExpectedImprovement()))
                        .append("%\n\n");
            }
        }

        // Недельный план
        if (response.getWeeklyPlan() != null) {
            appendWeeklyPlanToRecommendations(recommendationsText, response.getWeeklyPlan());
        }

        if (recommendationsText.length() > 0) {
            recommendationsArea.setText(recommendationsText.toString());
        }
    }

    private void appendWeeklyPlanToRecommendations(StringBuilder text,
                                                   RecommendationEngine.WeeklyLearningPlan plan) {
        text.append("НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ:\n\n");
        text.append("Цель недели: ").append(plan.getWeeklyGoal()).append("\n");
        text.append("Целевой уровень: ").append(plan.getTargetLevel()).append("\n");
        text.append("Ожидаемое улучшение: ")
                .append(String.format("%.1f", plan.getExpectedImprovement()))
                .append(" пунктов\n\n");

        text.append("Расписание на неделю:\n");
        for (RecommendationEngine.DailySchedule day : plan.getSchedule()) {
            text.append("• ").append(day.getDay()).append(": ")
                    .append(day.getFocus()).append(" (")
                    .append(day.getDurationMinutes()).append(" мин)\n");
            if (day.getExercises() != null && !day.getExercises().isEmpty()) {
                text.append("  Упражнения: ")
                        .append(String.join(", ",
                                day.getExercises().subList(0, Math.min(2, day.getExercises().size()))))
                        .append("\n");
            }
        }
    }

    private void showAnalysisCompletionNotification(SpeechAnalysis analysis) {
        float overallScore = (float) analysis.getOverallScore();
        String message;
        String emoji;

        if (overallScore >= 90) {
            message = "Великолепно! Ваша речь на продвинутом уровне!";
            emoji = "🏆";
        } else if (overallScore >= 85) {
            message = "Отличный результат! Продолжайте в том же духе!";
            emoji = "🎉";
        } else if (overallScore >= 75) {
            message = "Хорошая работа! Стабильный прогресс.";
            emoji = "👍";
        } else if (overallScore >= 65) {
            message = "Неплохо! Есть над чем поработать.";
            emoji = "💪";
        } else if (overallScore >= 55) {
            message = "Нужна регулярная практика. Следуйте рекомендациям!";
            emoji = "📚";
        } else {
            message = "Требуется серьезная работа над речью. Начните с основ!";
            emoji = "🔧";
        }

        if (statusLabel != null) {
            statusLabel.setText(String.format("✅ Анализ завершен: %.1f/100 %s",
                    overallScore, emoji));
        }
    }

    // ========================================
    // Event Handlers - Recording
    // ========================================

    @FXML
    private void onStartRecording() {
        if (closed) {
            showError("Ошибка", "Приложение закрывается");
            return;
        }

        try {
            // Генерация имени файла
            currentAudioFile = chatBotService.generateAudioFileName();

            // Начало записи
            speechRecorder.startRecording();

            // Обновление UI
            Platform.runLater(() -> {
                if (recordButton != null) {
                    recordButton.setDisable(true);
                    recordButton.setText("🔴 Запись...");
                }
                if (stopButton != null) stopButton.setDisable(false);
                if (recordingIndicator != null) recordingIndicator.setVisible(true);
                if (analyzeButton != null) analyzeButton.setDisable(true);
                if (pronunciationButton != null) pronunciationButton.setDisable(true);

                // Запуск таймера записи
                startRecordingTimer();
            });

            // Увеличиваем счетчик записей
            recordingsCount++;
            updateStatistics();

            logger.info("Начата запись аудио в файл: {}", currentAudioFile);

        } catch (Exception e) {
            logger.error("Ошибка при начале записи", e);
            showError("Ошибка записи", "Не удалось начать запись: " + e.getMessage());
        }
    }

    @FXML
    private void onStopRecording() {
        if (closed) {
            showError("Ошибка", "Приложение закрывается");
            return;
        }

        try {
            // Остановка записи
            File audioFile = speechRecorder.stopRecording(currentAudioFile);

            // Остановка таймера
            stopRecordingTimer();

            // Обновление UI
            Platform.runLater(() -> {
                if (recordButton != null) {
                    recordButton.setDisable(false);
                    recordButton.setText("● Запись");
                }
                if (stopButton != null) stopButton.setDisable(true);
                if (recordingIndicator != null) recordingIndicator.setVisible(false);
                if (recordingTimeLabel != null) recordingTimeLabel.setText("00:00");

                // Активируем кнопку анализа
                if (audioFile != null && audioFile.exists() && analyzeButton != null) {
                    analyzeButton.setDisable(false);
                }
            });

            if (audioFile != null && audioFile.exists()) {
                long fileSize = audioFile.length() / 1024; // KB
                logger.info("Запись завершена. Размер файла: {} KB", fileSize);

                Platform.runLater(() -> {
                    showAlert("Запись завершена",
                            String.format("Аудиофайл сохранен (%d KB). Теперь вы можете отправить или проанализировать его.",
                                    fileSize));
                });
            }

        } catch (Exception e) {
            logger.error("Ошибка при остановке записи", e);
            showError("Ошибка записи", "Не удалось остановить запись: " + e.getMessage());
        }
    }

    // ========================================
    // Event Handlers - Analysis
    // ========================================

    @FXML
    private void onAnalyzeAudio() {
        if (closed) {
            showError("Ошибка", "Приложение закрывается");
            return;
        }

        if (currentAudioFile == null) {
            showAlert("Внимание", "Сначала запишите аудио");
            return;
        }

        new Thread(() -> {
            if (closed) {
                Platform.runLater(() -> showError("Ошибка", "Приложение закрывается"));
                return;
            }

            Platform.runLater(() -> {
                if (analysisProgress != null) analysisProgress.setVisible(true);
                if (detailedAnalysisArea != null) detailedAnalysisArea.setVisible(true);
            });

            try {
                String text = inputField != null && !inputField.getText().isEmpty()
                        ? inputField.getText()
                        : "[Аудиосообщение]";

                EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(
                        currentAudioFile, text);

                Platform.runLater(() -> {
                    if (analysisArea != null) {
                        analysisArea.setText(analysis.getSummary());
                    }
                    if (detailedAnalysisArea != null) {
                        detailedAnalysisArea.setText(analysis.getDetailedReport());
                    }
                    if (analysisProgress != null) {
                        analysisProgress.setVisible(false);
                    }

                    // Обновляем метку фонемы
                    updatePhonemeLabel(analysis);
                });

            } catch (Exception e) {
                logger.error("Ошибка анализа аудио", e);
                Platform.runLater(() -> {
                    showError("Ошибка анализа", e.getMessage());
                    if (analysisProgress != null) analysisProgress.setVisible(false);
                });
            }
        }).start();
    }

    private void updatePhonemeLabel(EnhancedSpeechAnalysis analysis) {
        if (phonemeLabel == null) return;

        if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
            String worstPhoneme = analysis.getPhonemeScores().entrySet().stream()
                    .min((e1, e2) -> Float.compare(e1.getValue(), e2.getValue()))
                    .map(entry -> "/" + entry.getKey() + "/")
                    .orElse("");

            if (!worstPhoneme.isEmpty()) {
                phonemeLabel.setText("Слабая фонема: " + worstPhoneme);
                phonemeLabel.setVisible(true);
            }
        }
    }

    @FXML
    private void onPronunciationTraining() {
        if (currentAudioFile == null) {
            showAlert("Внимание", "Сначала запишите аудио и проанализируйте его");
            return;
        }

        try {
            String text = inputField != null && !inputField.getText().isEmpty()
                    ? inputField.getText()
                    : "[Аудиосообщение]";

            EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(currentAudioFile, text);

            if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
                showPronunciationTrainer(analysis);
            } else {
                showAlert("Данные не найдены",
                        "Не удалось получить данные о фонемах. Попробуйте проанализировать аудио заново.");
            }

        } catch (Exception e) {
            logger.error("Ошибка при создании тренажера произношения", e);
            showError("Ошибка", "Не удалось создать тренажер: " + e.getMessage());
        }
    }

    private void showPronunciationTrainer(EnhancedSpeechAnalysis analysis) {
        List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                .filter(e -> e.getValue() < 80)
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .limit(3)
                .collect(Collectors.toList());

        if (weakPhonemes.isEmpty()) {
            showAlert("Отличное произношение!",
                    "У вас нет проблемных звуков! Все фонемы оценены выше 80 баллов.");
            return;
        }

        StringBuilder exercisesText = new StringBuilder();
        exercisesText.append("ТРЕНАЖЕР ПРОИЗНОШЕНИЯ\n");
        exercisesText.append("=====================\n\n");

        for (Map.Entry<String, Float> phonemeEntry : weakPhonemes) {
            String phoneme = phonemeEntry.getKey();
            float score = phonemeEntry.getValue();

            String difficulty = score < 60 ? "beginner" : score < 75 ? "intermediate" : "advanced";
            PronunciationTrainer.PronunciationExercise exercise =
                    pronunciationTrainer.createExercise(phoneme, difficulty);

            exercisesText.append("ЗВУК /").append(phoneme).append("/\n");
            exercisesText.append("Оценка: ").append(String.format("%.1f", score)).append("/100\n\n");
            exercisesText.append("Инструкции:\n").append(exercise.getInstructions()).append("\n\n");
            exercisesText.append("Примеры:\n");
            for (String example : exercise.getExamples()) {
                exercisesText.append("• ").append(example).append("\n");
            }
            exercisesText.append("\nСоветы:\n");
            for (String tip : exercise.getTips()) {
                exercisesText.append("• ").append(tip).append("\n");
            }
            exercisesText.append("\n" + "=".repeat(50) + "\n\n");
        }

        // Показываем в отдельном окне
        showExercisesWindow(exercisesText.toString());
    }

    private void showExercisesWindow(String content) {
        TextArea trainingArea = new TextArea(content);
        trainingArea.setEditable(false);
        trainingArea.setWrapText(true);
        trainingArea.setPrefSize(800, 600);
        trainingArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        ScrollPane scrollPane = new ScrollPane(trainingArea);
        scrollPane.setFitToWidth(true);

        Stage trainingStage = new Stage();
        trainingStage.setTitle("Тренажер произношения - Проблемные звуки");
        if (stage != null) {
            trainingStage.initOwner(stage);
        }

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 820, 650);
        trainingStage.setScene(scene);
        trainingStage.show();
    }

    // ========================================
    // Event Handlers - History & Navigation
    // ========================================

    @FXML
    private void onShowHistory() {
        List<Conversation> history = chatBotService.getConversationHistory();

        if (history.isEmpty()) {
            showAlert("История", "История разговоров пуста");
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
                historyText.append("Оценка произношения: ")
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
        alert.setContentText("Это действие нельзя отменить.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            // Очищаем контейнер сообщений
            if (chatMessagesContainer != null) {
                chatMessagesContainer.getChildren().clear();
            }

            // Очищаем аналитические области
            if (analysisArea != null) analysisArea.clear();
            if (recommendationsArea != null) recommendationsArea.clear();
            if (detailedAnalysisArea != null) detailedAnalysisArea.clear();

            chatBotService.clearHistory();

            // Сбрасываем статистику
            messagesCount = 0;
            analysisCount = 0;
            recordingsCount = 0;
            updateStatistics();

            // Показываем приветственное сообщение
            showWelcomeMessage();

            logger.info("История чата очищена");
        }
    }

    // ========================================
    // Event Handlers - Speech Recognition
    // ========================================

    @FXML
    private void testSpeechRecognition() {
        if (closed) {
            showError("Ошибка", "Приложение закрывается");
            return;
        }

        String testAudioPath = "recordings/test_audio.wav";
        File testFile = new File(testAudioPath);

        if (!testFile.exists()) {
            showAlert("Тест", "Сначала запишите тестовое аудио");
            return;
        }

        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    if (speechServiceStatusLabel != null) {
                        speechServiceStatusLabel.setText("🔍 Тестирование...");
                    }
                });

                SpeechToTextService service = chatBotService.getSpeechToTextService();
                long startTime = System.currentTimeMillis();
                SpeechToTextService.SpeechRecognitionResult result = service.transcribe(testAudioPath);
                long elapsedTime = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    String message = String.format(
                            "Результат теста:\n\n" +
                                    "Текст: %s\n" +
                                    "Уверенность: %.1f%%\n" +
                                    "Время: %d мс\n" +
                                    "Сервис: %s\n" +
                                    "Язык: %s",
                            result.getText(),
                            result.getConfidence() * 100,
                            elapsedTime,
                            result.getServiceInfo(),
                            chatBotService.getCurrentSpeechLanguageName()
                    );

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Тест распознавания речи");
                    alert.setHeaderText("Результат тестирования");
                    alert.setContentText(message);
                    alert.showAndWait();

                    if (serviceTypeComboBox != null) {
                        updateSpeechServiceStatus(serviceTypeComboBox.getValue());
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка тестирования распознавания", e);
                Platform.runLater(() -> {
                    showError("Ошибка теста",
                            "Не удалось протестировать распознавание: " + e.getMessage());
                    if (serviceTypeComboBox != null) {
                        updateSpeechServiceStatus(serviceTypeComboBox.getValue());
                    }
                });
            }
        }).start();
    }

    @FXML
    private void testMicrophone() {
        if (closed) {
            showError("Ошибка", "Приложение закрывается");
            return;
        }

        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    if (testMicrophoneButton != null) {
                        testMicrophoneButton.setDisable(true);
                    }
                    if (speechServiceStatusLabel != null) {
                        speechServiceStatusLabel.setText("🎤 Тестирование микрофона...");
                    }
                });

                chatBotService.testMicrophone(3);

                Platform.runLater(() -> {
                    if (testMicrophoneButton != null) {
                        testMicrophoneButton.setDisable(false);
                    }
                    if (serviceTypeComboBox != null) {
                        updateSpeechServiceStatus(serviceTypeComboBox.getValue());
                    }

                    showAlert("Тест микрофона",
                            "Тестирование микрофона завершено. Проверьте логи в консоли для деталей.");
                });

            } catch (Exception e) {
                logger.error("Ошибка тестирования микрофона", e);
                Platform.runLater(() -> {
                    showError("Ошибка теста",
                            "Не удалось протестировать микрофон: " + e.getMessage());
                    if (testMicrophoneButton != null) {
                        testMicrophoneButton.setDisable(false);
                    }
                    if (serviceTypeComboBox != null) {
                        updateSpeechServiceStatus(serviceTypeComboBox.getValue());
                    }
                });
            }
        }).start();
    }

    @FXML
    private void toggleSpeechPanel() {
        if (speechControlPanel == null) return;

        boolean isVisible = speechControlPanel.isVisible();
        speechControlPanel.setVisible(!isVisible);
        speechControlPanel.setManaged(!isVisible);
    }

    private void updateSpeechServiceStatus(String serviceInfo) {
        Platform.runLater(() -> {
            if (speechServiceStatusLabel == null) return;

            if (serviceInfo.contains("MOCK")) {
                speechServiceStatusLabel.setText("🔧 Тестовый режим");
                if (languageComboBox != null) languageComboBox.setDisable(true);
            } else if (serviceInfo.contains("WHISPER")) {
                speechServiceStatusLabel.setText("✅ Whisper API (требуется ключ)");
                if (languageComboBox != null) languageComboBox.setDisable(false);
            } else if (serviceInfo.contains("GOOGLE")) {
                speechServiceStatusLabel.setText("✅ Google Speech API (требуется ключ)");
                if (languageComboBox != null) languageComboBox.setDisable(false);
            } else if (serviceInfo.contains("VOSK")) {
                speechServiceStatusLabel.setText("📁 Оффлайн распознавание (требуется модель)");
                if (languageComboBox != null) languageComboBox.setDisable(false);
            }
        });
    }

    // ========================================
    // Event Handlers - Settings & Help
    // ========================================

    @FXML
    private void onSettings() {
        showAlert("Настройки", "Настройки будут доступны в следующей версии");
    }

    @FXML
    private void onHelp() {
        String helpText = """
            SpeakAI - Руководство пользователя
            
            Основные функции:
            1. Текстовый чат - пишите сообщения на английском
            2. Запись голоса - нажмите 🎤 для записи речи
            3. Анализ речи - автоматический анализ произношения
            4. Рекомендации - персонализированные советы по улучшению
            
            Настройки распознавания речи:
            • Выберите язык в выпадающем списке
            • Отрегулируйте чувствительность микрофона
            • Протестируйте микрофон перед записью
            
            Советы:
            • Записывайте свою речь для анализа произношения
            • Используйте разнообразные темы для разговора
            • Обращайте внимание на рекомендации ИИ-репетитора
            
            Контактная информация:
            support@speakai.com
            """;

        showAlert("Помощь", helpText);
    }

    // ========================================
    // Helper Methods - UI Updates
    // ========================================

    private void updateStatistics() {
        Platform.runLater(() -> {
            if (messagesCountLabel != null) {
                messagesCountLabel.setText(String.valueOf(messagesCount));
            }
            if (analysisCountLabel != null) {
                analysisCountLabel.setText(String.valueOf(analysisCount));
            }
            if (recordingsCountLabel != null) {
                recordingsCountLabel.setText(String.valueOf(recordingsCount));
            }
        });
    }

    private void updateStatusLabel() {
        if (statusLabel == null) return;

        String status = isAiServiceAvailable
                ? "✅ ИИ-сервис доступен"
                : "⚠️ Демо-режим (установите API ключ)";

        Platform.runLater(() -> statusLabel.setText(status));
    }

    private void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show) {
                if (statusLabel != null) statusLabel.setText("⏳ Обработка...");
                if (sendButton != null) sendButton.setDisable(true);
            } else {
                updateStatusLabel();
                if (sendButton != null) sendButton.setDisable(false);
            }
        });
    }

    private void loadConversationHistory() {
        new Thread(() -> {
            try {
                List<Conversation> history = chatBotService.getConversationHistory();

                if (!history.isEmpty()) {
                    Platform.runLater(() -> {
                        if (statusLabel != null) {
                            statusLabel.setText("Загружено " + history.size() + " прошлых разговоров");
                        }
                    });
                }
            } catch (Exception e) {
                logger.error("Ошибка при загрузке истории", e);
            }
        }).start();
    }

    // ========================================
    // Helper Methods - Recording Timer
    // ========================================

    private void startRecordingTimer() {
        recordingTimerThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();

            while (speechRecorder != null && speechRecorder.isRecording()) {
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
                    break;
                }
            }
        });

        recordingTimerThread.setDaemon(true);
        recordingTimerThread.start();
    }

    private void stopRecordingTimer() {
        if (recordingTimerThread != null && recordingTimerThread.isAlive()) {
            recordingTimerThread.interrupt();
        }
    }

    // ========================================
    // Helper Methods - Service Status
    // ========================================

    private void showServiceStatus(AiService aiService) {
        Platform.runLater(() -> {
            if (aiService instanceof UniversalAIService universalService) {
                String providerName = getProviderDisplayName(universalService.getProvider());
                String model = universalService.getModel();

                showInfo("AI сервис подключен",
                        String.format("✅ Успешно подключено к %s\n\nМодель: %s\n\nПриложение готово к работе!",
                                providerName, model));

                if (statusLabel != null) {
                    statusLabel.setText(String.format("✅ %s (%s)", providerName, model));
                }

            } else if (aiService instanceof MockAiService) {
                showWarning("Демонстрационный режим",
                        "Приложение работает без AI API.\n\n" +
                                "Для подключения к AI репетитору:\n\n" +
                                "1. Получите БЕСПЛАТНЫЙ API ключ:\n" +
                                "   • Groq: https://console.groq.com/\n" +
                                "   • DeepSeek: https://platform.deepseek.com/\n\n" +
                                "2. Обновите application.properties\n" +
                                "3. Перезапустите приложение");

                if (statusLabel != null) {
                    statusLabel.setText("⚠️ Демо-режим (без AI)");
                }
            }
        });
    }

    private String getProviderDisplayName(String provider) {
        return switch (provider) {
            case "groq" -> "Groq";
            case "openai" -> "OpenAI GPT";
            case "deepseek" -> "DeepSeek";
            case "anthropic" -> "Anthropic Claude";
            case "together" -> "Together AI";
            case "ollama" -> "Ollama (локальный)";
            default -> "AI Service";
        };
    }

    // ========================================
    // Helper Methods - Dialogs
    // ========================================

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.setResizable(true);
            alert.getDialogPane().setPrefSize(600, 400);
            alert.show();
        });
    }

    private void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.setResizable(true);
            alert.getDialogPane().setPrefSize(700, 500);
            alert.showAndWait();
        });
    }

    private void showTextWindow(String title, String content, int width, int height) {
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefSize(width - 20, height - 50);
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
    // Lifecycle Methods
    // ========================================

    public void setStage(Stage stage) {
        this.stage = stage;

        stage.setOnCloseRequest(event -> {
            logger.info("Окно приложения закрывается");
            close();
            logger.info("Окно приложения закрыто");
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие ChatBotController...");

        try {
            stopRecordingTimer();

            if (resourceManager != null) {
                resourceManager.close();
            }

            logger.info("ChatBotController закрыт");

        } catch (Exception e) {
            logger.error("Ошибка при закрытии ChatBotController", e);
        }
    }

    private void cleanupResources() {
        if (resourceManager != null) {
            try {
                resourceManager.close();
            } catch (Exception e) {
                logger.error("Ошибка при очистке ресурсов", e);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                logger.warn("ChatBotController не был закрыт явно, вызываем close() в finalize()");
                close();
            }
        } finally {
            super.finalize();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    // ========================================
    // Chat Message Methods
    // ========================================

    private void addUserMessage(String text) {
        Platform.runLater(() -> {
            HBox messageContainer = new HBox();
            messageContainer.setSpacing(8);
            messageContainer.setAlignment(Pos.TOP_RIGHT);
            messageContainer.setStyle("-fx-padding: 0 0 5 0;");

            // Контейнер сообщения
            VBox messageContent = new VBox();
            messageContent.setAlignment(Pos.TOP_RIGHT);
            messageContent.setSpacing(3);

            // Текст сообщения
            VBox messageBubble = new VBox();
            messageBubble.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #3498db, #2980b9); " +
                            "-fx-background-radius: 12 12 4 12; " +
                            "-fx-padding: 12 16; " +
                            "-fx-effect: dropshadow(gaussian, rgba(52, 152, 219, 0.2), 4, 0, 0, 2);"
            );
            messageBubble.setMaxWidth(400);

            Label messageText = new Label(text);
            messageText.setStyle(
                    "-fx-text-fill: white; " +
                            "-fx-font-size: 13px; " +
                            "-fx-font-family: 'Segoe UI', sans-serif; " +
                            "-fx-wrap-text: true;"
            );
            messageText.setWrapText(true);
            messageText.setMaxWidth(380);

            messageBubble.getChildren().add(messageText);

            // Время и статус
            HBox messageInfo = new HBox();
            messageInfo.setAlignment(Pos.CENTER_RIGHT);
            messageInfo.setSpacing(8);

            Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
            timeLabel.setStyle(
                    "-fx-text-fill: #7f8c8d; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-style: italic;"
            );

            Label statusLabel = new Label("✔");
            statusLabel.setStyle(
                    "-fx-text-fill: #27ae60; " +
                            "-fx-font-size: 11px;"
            );

            messageInfo.getChildren().addAll(timeLabel, statusLabel);
            messageContent.getChildren().addAll(messageBubble, messageInfo);

            // Аватар пользователя
            StackPane userAvatar = new StackPane();
            userAvatar.setPrefSize(32, 32);
            userAvatar.setMinSize(32, 32);
            userAvatar.setMaxSize(32, 32);
            userAvatar.setStyle(
                    "-fx-background-color: #27ae60; " +
                            "-fx-background-radius: 50%; " +
                            "-fx-alignment: center;"
            );

            Label userAvatarLabel = new Label("U");
            userAvatarLabel.setStyle(
                    "-fx-text-fill: white; " +
                            "-fx-font-size: 14px; " +
                            "-fx-font-weight: bold;"
            );
            userAvatar.getChildren().add(userAvatarLabel);

            messageContainer.getChildren().addAll(messageContent, userAvatar);
            chatMessagesContainer.getChildren().add(messageContainer);

            // Прокрутка вниз
            ScrollPane scrollPane = (ScrollPane) chatMessagesContainer.getParent();
            scrollPane.setVvalue(1.0);
        });
    }

    // Метод для добавления сообщения ИИ
    private void addAIMessage(String text) {
        Platform.runLater(() -> {
            HBox messageContainer = new HBox();
            messageContainer.setSpacing(8);
            messageContainer.setAlignment(Pos.TOP_LEFT);
            messageContainer.setStyle("-fx-padding: 0 0 5 0;");

            // Аватар ИИ
            StackPane aiAvatar = new StackPane();
            aiAvatar.setPrefSize(32, 32);
            aiAvatar.setMinSize(32, 32);
            aiAvatar.setMaxSize(32, 32);
            aiAvatar.setStyle(
                    "-fx-background-color: #3498db; " +
                            "-fx-background-radius: 50%; " +
                            "-fx-alignment: center;"
            );

            Label aiAvatarLabel = new Label("AI");
            aiAvatarLabel.setStyle(
                    "-fx-text-fill: white; " +
                            "-fx-font-size: 14px; " +
                            "-fx-font-weight: bold;"
            );
            aiAvatar.getChildren().add(aiAvatarLabel);

            // Контейнер сообщения
            VBox messageContent = new VBox();
            messageContent.setAlignment(Pos.TOP_LEFT);
            messageContent.setSpacing(3);

            // Текст сообщения
            VBox messageBubble = new VBox();
            messageBubble.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #ecf0f1); " +
                            "-fx-background-radius: 12 12 12 4; " +
                            "-fx-border-color: #e0e0e0; " +
                            "-fx-border-width: 1; " +
                            "-fx-border-radius: 12 12 12 4; " +
                            "-fx-padding: 12 16; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.05), 3, 0, 0, 1);"
            );
            messageBubble.setMaxWidth(400);

            Label messageText = new Label(text);
            messageText.setStyle(
                    "-fx-text-fill: #2c3e50; " +
                            "-fx-font-size: 13px; " +
                            "-fx-font-family: 'Segoe UI', sans-serif; " +
                            "-fx-wrap-text: true;"
            );
            messageText.setWrapText(true);
            messageText.setMaxWidth(380);

            messageBubble.getChildren().add(messageText);

            // Время
            Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
            timeLabel.setStyle(
                    "-fx-text-fill: #7f8c8d; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-style: italic;"
            );

            messageContent.getChildren().addAll(messageBubble, timeLabel);
            messageContainer.getChildren().addAll(aiAvatar, messageContent);
            chatMessagesContainer.getChildren().add(messageContainer);

            // Прокрутка вниз
            ScrollPane scrollPane = (ScrollPane) chatMessagesContainer.getParent();
            scrollPane.setVvalue(1.0);
        });
    }

    // Метод для добавления разделителя времени
    private void addTimeDivider(String timeText) {
        Platform.runLater(() -> {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setSpacing(10);
            timeDivider.setPadding(new Insets(5, 0, 5, 0));

            Region line1 = new Region();
            line1.setPrefHeight(1);
            line1.setMinHeight(1);
            line1.setMaxHeight(1);
            line1.setStyle("-fx-background-color: #e0e0e0;");
            HBox.setHgrow(line1, Priority.ALWAYS);

            Label label = new Label(timeText);
            label.setStyle(
                    "-fx-text-fill: #95a5a6; " +
                            "-fx-font-size: 11px; " +
                            "-fx-background-color: white; " +
                            "-fx-padding: 0 10;"
            );

            Region line2 = new Region();
            line2.setPrefHeight(1);
            line2.setMinHeight(1);
            line2.setMaxHeight(1);
            line2.setStyle("-fx-background-color: #e0e0e0;");
            HBox.setHgrow(line2, Priority.ALWAYS);

            timeDivider.getChildren().addAll(line1, label, line2);
            chatMessagesContainer.getChildren().add(timeDivider);
        });
    }


    @FXML
    private void onMicrophoneRecognition(ActionEvent actionEvent) {
        if (closed) {
            showError("Ошибка", "Приложение закрывается");
            return;
        }

        // Показываем статус
        Platform.runLater(() -> {
            if (microphoneButton != null) {
                microphoneButton.setDisable(true);
                microphoneButton.setText("🔴");
                microphoneButton.setStyle("-fx-background-color: #e74c3c;");
            }

            if (microphoneStatusLabel != null) {
                microphoneStatusLabel.setText("Говорите...");
                microphoneStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
            }

            if (statusLabel != null) {
                statusLabel.setText("🎤 Распознавание речи...");
            }
        });

        new Thread(() -> {
            try {
                // Вызываем метод распознавания речи
                String recognizedText = chatBotService.recognizeSpeechInRealTime();

                Platform.runLater(() -> {
                    // Обновляем статус
                    if (microphoneStatusLabel != null) {
                        if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                            microphoneStatusLabel.setText("✓ Распознано");
                            microphoneStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                        } else {
                            microphoneStatusLabel.setText("✗ Не распознано");
                            microphoneStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                        }
                    }

                    // Вставляем распознанный текст
                    if (inputField != null && recognizedText != null && !recognizedText.trim().isEmpty()) {
                        String currentText = inputField.getText();
                        if (!currentText.isEmpty()) {
                            // Добавляем пробел если в конце нет знака препинания
                            if (!currentText.matches(".*[.!?\\s]$")) {
                                currentText += " ";
                            }
                            inputField.setText(currentText + recognizedText);
                        } else {
                            inputField.setText(recognizedText);
                        }
                    }

                    // Восстанавливаем кнопку
                    if (microphoneButton != null) {
                        microphoneButton.setDisable(false);
                        microphoneButton.setText("🎤");
                        microphoneButton.setStyle("");
                    }

                    if (statusLabel != null) {
                        updateStatusLabel();
                    }

                    // Через 2 секунды сбрасываем статус лейбл
                    if (microphoneStatusLabel != null) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            Platform.runLater(() -> {
                                if (microphoneStatusLabel != null) {
                                    microphoneStatusLabel.setText("");
                                }
                            });
                        }).start();
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка при распознавании речи", e);
                Platform.runLater(() -> {
                    // Обновляем статус ошибки
                    if (microphoneStatusLabel != null) {
                        microphoneStatusLabel.setText("Ошибка");
                        microphoneStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    }

                    showError("Ошибка распознавания",
                            "Не удалось распознать речь: " + e.getMessage());

                    // Восстанавливаем кнопку
                    if (microphoneButton != null) {
                        microphoneButton.setDisable(false);
                        microphoneButton.setText("🎤");
                        microphoneButton.setStyle("");
                    }

                    if (statusLabel != null) {
                        updateStatusLabel();
                    }

                    // Через 3 секунды сбрасываем статус лейбл
                    if (microphoneStatusLabel != null) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            Platform.runLater(() -> {
                                if (microphoneStatusLabel != null) {
                                    microphoneStatusLabel.setText("");
                                }
                            });
                        }).start();
                    }
                });
            }
        }).start();
    }
}