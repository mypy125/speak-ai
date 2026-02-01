package com.mygitgor.chatbot;

import com.mygitgor.ai.AIServiceFactory;
import com.mygitgor.ai.AiService;
import com.mygitgor.ai.MockAiService;
import com.mygitgor.ai.UniversalAIService;
import com.mygitgor.analysis.PronunciationTrainer;
import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.speech.AudioAnalyzer;
import com.mygitgor.speech.SpeechPlayer;
import com.mygitgor.speech.SpeechRecorder;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ChatBotController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotController.class);

    @FXML private TextArea chatArea;
    @FXML private TextArea inputField;
    @FXML private TextArea analysisArea;
    @FXML private TextArea recommendationsArea;
    @FXML private Button recordButton;
    @FXML private Button stopButton;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Button historyButton;
    @FXML private VBox mainContainer;
    @FXML private ProgressIndicator recordingIndicator;
    @FXML private Label recordingTimeLabel;
    @FXML private Label statusLabel;
    @FXML private Button analyzeButton;
    @FXML private Button pronunciationButton;
    @FXML private ProgressBar analysisProgress;
    @FXML private Label phonemeLabel;
    @FXML private TextArea detailedAnalysisArea;

    private ChatBotService chatBotService;
    private SpeechRecorder speechRecorder;
    private SpeechPlayer speechPlayer;
    private AudioAnalyzer audioAnalyzer;
    private PronunciationTrainer pronunciationTrainer;
    private Stage stage;
    private String currentAudioFile;
    private Thread recordingTimerThread;
    private boolean isAiServiceAvailable;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Инициализация ChatBotController");

        // Инициализация сервисов
        initializeServices();

        // Настройка UI
        setupUI();

        // Загрузка истории
        loadConversationHistory();

        // Приветственное сообщение
        showWelcomeMessage();
    }

    private void initializeServices() {
        try {
            // Загружаем конфигурацию
            Properties props = new Properties();
            props.load(getClass().getResourceAsStream("/application.properties"));

            // Создаем AI сервис через фабрику
            AiService aiService = AIServiceFactory.createService(props);
            isAiServiceAvailable = aiService.isAvailable();

            // Логируем информацию о сервисе
            if (aiService instanceof UniversalAIService universalService) {
                logger.info("✅ {} сервис инициализирован", universalService.getProvider());
                logger.info("Модель: {}", universalService.getModel());
            } else if (aiService instanceof MockAiService) {
                logger.warn("🔄 Используется Mock сервис (демо-режим)");
            }

            // Инициализация остальных сервисов
            this.audioAnalyzer = new AudioAnalyzer();
            this.pronunciationTrainer = new PronunciationTrainer();

            this.chatBotService = new ChatBotService(
                    aiService,
                    audioAnalyzer,
                    pronunciationTrainer
            );

            this.speechRecorder = new SpeechRecorder();
            this.speechPlayer = new SpeechPlayer();

            // Показываем статус пользователю
            showServiceStatus(aiService);

        } catch (Exception e) {
            logger.error("Ошибка при инициализации сервисов", e);
            throw new RuntimeException("Не удалось инициализировать сервисы", e);
        }
    }

    private void showServiceStatus(AiService aiService) {
        Platform.runLater(() -> {
            if (aiService instanceof UniversalAIService universalService) {
                String providerName = getProviderDisplayName(universalService.getProvider());
                String model = universalService.getModel();

                showInfo("AI сервис подключен",
                        String.format("✅ Успешно подключено к %s\n\nМодель: %s\n\nПриложение готово к работе!",
                                providerName, model));

                statusLabel.setText(String.format("✅ %s (%s)", providerName, model));
                statusLabel.setStyle("-fx-text-fill: #27ae60;");

            } else if (aiService instanceof MockAiService) {
                showWarning("Демонстрационный режим",
                        "Приложение работает без AI API.\n\n" +
                                "Для подключения к AI репетитору:\n\n" +
                                "1. Получите БЕСПЛАТНЫЙ API ключ:\n" +
                                "   • Groq: https://console.groq.com/\n" +
                                "   • DeepSeek: https://platform.deepseek.com/\n\n" +
                                "2. Обновите application.properties\n" +
                                "3. Перезапустите приложение");

                statusLabel.setText("⚠️ Демо-режим (без AI)");
                statusLabel.setStyle("-fx-text-fill: #f39c12;");
            }
        });
    }

    // Методы для показа диалоговых окон
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

    private void setupUI() {
        // Настройка обработчиков событий
        inputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
                // Shift+Enter - новая строка
                inputField.appendText("\n");
            } else if (event.getCode() == KeyCode.ENTER) {
                // Enter - отправка сообщения
                event.consume();
                onSendMessage();
            }
        });

        // Инициализация состояния кнопок
        stopButton.setDisable(true);
        recordingIndicator.setVisible(false);
        recordingTimeLabel.setText("00:00");
        analyzeButton.setDisable(true);
        pronunciationButton.setDisable(true);
        analysisProgress.setVisible(false);
        detailedAnalysisArea.setVisible(false);

        // Настройка стилей
        chatArea.setStyle("-fx-font-family: 'Segoe UI', 'Arial'; -fx-font-size: 14px;");
        inputField.setStyle("-fx-font-family: 'Segoe UI', 'Arial'; -fx-font-size: 14px;");
        analysisArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        detailedAnalysisArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

        // Обновление статуса
        updateStatusLabel();

        // Настройка кнопок анализа
        analyzeButton.setOnAction(event -> onAnalyzeAudio());
        pronunciationButton.setOnAction(event -> onPronunciationTraining());
    }

    private void showWelcomeMessage() {
        String welcomeMessage = """
            🌟 Добро пожаловать в SpeakAI!
            
            Я ваш персональный ИИ-репетитор английского языка.
            
            **Как это работает:**
            1. Напишите сообщение на английском (или нажмите кнопку записи)
            2. Я проанализирую вашу речь и грамматику
            3. Получите подробную обратную связь и рекомендации
            
            **Совет:** Используйте микрофон для записи речи - так я смогу проанализировать ваше произношение!
            
            Давайте начнем обучение! ✨
            """;

        appendToChat("Бот", welcomeMessage);
    }

    @FXML
    private void onSendMessage() {
        String text = inputField.getText().trim();

        // Если нет текста и нет аудио - предупреждение
        if (text.isEmpty() && currentAudioFile == null) {
            showAlert("Внимание", "Введите сообщение или запишите аудио перед отправкой");
            return;
        }

        // Добавляем сообщение пользователя в чат (если есть текст)
        if (!text.isEmpty()) {
            appendToChat("Вы", text);
        } else if (currentAudioFile != null) {
            appendToChat("Вы", "🎤 [Аудиосообщение]");
        }

        // Очищаем поле ввода
        inputField.clear();

        // Показываем индикатор загрузки
        showLoadingIndicator(true);

        // Обработка в фоновом потоке
        new Thread(() -> {
            try {
                ChatBotService.ChatResponse response = chatBotService.processUserInput(text, currentAudioFile);

                Platform.runLater(() -> {
                    // Добавляем ответ бота
                    appendToChat("Бот", response.getFullResponse());

                    // Обновляем области анализа
                    if (response.getSpeechAnalysis() != null) {
                        // Используем EnhancedSpeechAnalysis если доступен
                        if (response.getSpeechAnalysis() instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
                            analysisArea.setText(enhancedAnalysis.getSummary());

                            // Показываем детальный анализ если есть
                            if (enhancedAnalysis.getDetailedReport() != null &&
                                    !enhancedAnalysis.getDetailedReport().isEmpty()) {
                                detailedAnalysisArea.setText(enhancedAnalysis.getDetailedReport());
                                detailedAnalysisArea.setVisible(true);
                            }

                            // Обновляем метку фонемы если есть
                            if (enhancedAnalysis.getPhonemeScores() != null &&
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
                        } else {
                            analysisArea.setText(response.getSpeechAnalysis().getSummary());
                        }

                        // Обновляем область рекомендаций
                        StringBuilder recommendationsText = new StringBuilder();

                        // Добавляем базовые рекомендации из анализа
                        if (!response.getSpeechAnalysis().getRecommendations().isEmpty()) {
                            recommendationsText.append("БАЗОВЫЕ РЕКОМЕНДАЦИИ:\n");
                            recommendationsText.append(String.join("\n• ", response.getSpeechAnalysis().getRecommendations()));
                            recommendationsText.append("\n\n");
                        }

                        // Добавляем персонализированные рекомендации если есть
                        if (response.getPersonalizedRecommendations() != null &&
                                !response.getPersonalizedRecommendations().isEmpty()) {
                            recommendationsText.append("ПЕРСОНАЛИЗИРОВАННЫЕ РЕКОМЕНДАЦИИ:\n\n");

                            for (RecommendationEngine.PersonalizedRecommendation rec : response.getPersonalizedRecommendations()) {
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

                        // Добавляем недельный план если есть
                        if (response.getWeeklyPlan() != null) {
                            recommendationsText.append("НЕДЕЛЬНЫЙ ПЛАН ОБУЧЕНИЯ:\n\n");
                            recommendationsText.append("Цель недели: ").append(response.getWeeklyPlan().getWeeklyGoal()).append("\n");
                            recommendationsText.append("Целевой уровень: ").append(response.getWeeklyPlan().getTargetLevel()).append("\n");
                            recommendationsText.append("Ожидаемое улучшение: ")
                                    .append(String.format("%.1f", response.getWeeklyPlan().getExpectedImprovement()))
                                    .append(" пунктов\n\n");

                            recommendationsText.append("Расписание на неделю:\n");
                            for (RecommendationEngine.DailySchedule day : response.getWeeklyPlan().getSchedule()) {
                                recommendationsText.append("• ").append(day.getDay()).append(": ")
                                        .append(day.getFocus()).append(" (")
                                        .append(day.getDurationMinutes()).append(" мин)\n");
                                if (day.getExercises() != null && !day.getExercises().isEmpty()) {
                                    recommendationsText.append("  Упражнения: ")
                                            .append(String.join(", ", day.getExercises().subList(0, Math.min(2, day.getExercises().size()))))
                                            .append("\n");
                                }
                            }
                        }

                        if (recommendationsText.length() > 0) {
                            recommendationsArea.setText(recommendationsText.toString());
                        }

                        // Активируем кнопки анализа (только если был анализ аудио)
                        if (response.getSpeechAnalysis() instanceof EnhancedSpeechAnalysis) {
                            analyzeButton.setDisable(false);
                            pronunciationButton.setDisable(false);

                            // Активируем кнопку тренажера произношения если есть слабые фонемы
                            if (response.getSpeechAnalysis() instanceof EnhancedSpeechAnalysis enhancedAnalysis) {
                                if (enhancedAnalysis.getPhonemeScores() != null &&
                                        !enhancedAnalysis.getPhonemeScores().isEmpty()) {
                                    pronunciationButton.setDisable(false);

                                    // Подсчитываем проблемные фонемы (оценка < 70)
                                    long weakPhonemesCount = enhancedAnalysis.getPhonemeScores().values().stream()
                                            .filter(score -> score < 70)
                                            .count();

                                    if (weakPhonemesCount > 0) {
                                        pronunciationButton.setText("🗣️ Тренажер (" + weakPhonemesCount + " проблем. звуков)");
                                    } else {
                                        pronunciationButton.setText("🗣️ Тренажер произношения");
                                    }
                                }
                            }
                        }
                    } else {
                        // Если анализа речи не было, деактивируем кнопки анализа
                        analyzeButton.setDisable(true);
                        pronunciationButton.setDisable(true);
                        phonemeLabel.setVisible(false);
                        detailedAnalysisArea.setVisible(false);
                    }

                    // Не очищаем аудиофайл сразу - оставляем для возможного повторного анализа
                    // currentAudioFile = null;

                    // Прокрутка вниз
                    chatArea.positionCaret(chatArea.getLength());

                    // Скрываем индикатор загрузки
                    showLoadingIndicator(false);

                    // Показываем уведомление о завершении обработки
                    if (response.getSpeechAnalysis() != null) {
                        float overallScore = (float) response.getSpeechAnalysis().getOverallScore();
                        String message;
                        String emoji;

                        if (overallScore >= 90) {
                            message = "Великолепно! Ваша речь на продвинутом уровне! 🏆";
                            emoji = "🏆";
                        } else if (overallScore >= 85) {
                            message = "Отличный результат! Продолжайте в том же духе! 🎉";
                            emoji = "🎉";
                        } else if (overallScore >= 75) {
                            message = "Хорошая работа! Стабильный прогресс. 👍";
                            emoji = "👍";
                        } else if (overallScore >= 65) {
                            message = "Неплохо! Есть над чем поработать. 💪";
                            emoji = "💪";
                        } else if (overallScore >= 55) {
                            message = "Нужна регулярная практика. Следуйте рекомендациям! 📚";
                            emoji = "📚";
                        } else {
                            message = "Требуется серьезная работа над речью. Начните с основ! 🔧";
                            emoji = "🔧";
                        }

                        // Показываем краткое уведомление
                        if (overallScore >= 75) {
                            showAlert("✅ Анализ завершен успешно",
                                    String.format("Общая оценка: %.1f/100 %s\n%s", overallScore, emoji, message));
                        } else {
                            showAlert("⚠️ Результаты анализа",
                                    String.format("Общая оценка: %.1f/100 %s\n%s", overallScore, emoji, message));
                        }

                        // Обновляем статус в интерфейсе
                        String statusMessage;
                        if (response.getPersonalizedRecommendations() != null &&
                                !response.getPersonalizedRecommendations().isEmpty()) {
                            int highPriorityCount = (int) response.getPersonalizedRecommendations().stream()
                                    .filter(rec -> "Высокий".equals(rec.getPriority()))
                                    .count();

                            if (highPriorityCount > 0) {
                                statusMessage = String.format("✅ Анализ завершен. %d высокоприоритетных рекомендаций", highPriorityCount);
                            } else {
                                statusMessage = "✅ Анализ завершен. Персонализированные рекомендации готовы";
                            }
                        } else {
                            statusMessage = "✅ Анализ завершен";
                        }

                        statusLabel.setText(statusMessage);
                    } else {
                        statusLabel.setText("✅ Сообщение обработано");
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка при обработке сообщения", e);
                Platform.runLater(() -> {
                    showError("Ошибка", "Не удалось обработать сообщение: " + e.getMessage());
                    showLoadingIndicator(false);
                });
            }
        }).start();
    }

    @FXML
    private void onStartRecording() {
        try {
            // Генерация имени файла
            currentAudioFile = chatBotService.generateAudioFileName();

            // Начало записи
            speechRecorder.startRecording();

            // Обновление UI
            Platform.runLater(() -> {
                recordButton.setDisable(true);
                stopButton.setDisable(false);
                recordButton.setText("🔴 Запись...");
                recordingIndicator.setVisible(true);
                analyzeButton.setDisable(true);
                pronunciationButton.setDisable(true);

                // Запуск таймера записи
                startRecordingTimer();
            });

            logger.info("Начата запись аудио в файл: {}", currentAudioFile);

        } catch (Exception e) {
            logger.error("Ошибка при начале записи", e);
            showError("Ошибка записи", "Не удалось начать запись: " + e.getMessage());
        }
    }

    @FXML
    private void onStopRecording() {
        try {
            // Остановка записи
            File audioFile = speechRecorder.stopRecording(currentAudioFile);

            // Остановка таймера
            stopRecordingTimer();

            // Обновление UI
            Platform.runLater(() -> {
                recordButton.setDisable(false);
                stopButton.setDisable(true);
                recordButton.setText("🎤 Запись");
                recordingIndicator.setVisible(false);
                recordingTimeLabel.setText("00:00");

                // Активируем кнопку анализа
                if (audioFile != null && audioFile.exists()) {
                    analyzeButton.setDisable(false);
                }
            });

            if (audioFile != null && audioFile.exists()) {
                long fileSize = audioFile.length() / 1024; // KB
                logger.info("Запись завершена. Размер файла: {} KB", fileSize);

                // Показываем уведомление
                Platform.runLater(() -> {
                    showAlert("Запись завершена",
                            String.format("Аудиофайл сохранен (%s KB). Теперь вы можете отправить или проанализировать его.", fileSize));
                });
            }

        } catch (Exception e) {
            logger.error("Ошибка при остановке записи", e);
            showError("Ошибка записи", "Не удалось остановить запись: " + e.getMessage());
        }
    }

    @FXML
    private void onClearChat() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Очистка чата");
        alert.setHeaderText("Вы уверены, что хотите очистить историю чата?");
        alert.setContentText("Это действие нельзя отменить.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            chatArea.clear();
            analysisArea.clear();
            recommendationsArea.clear();
            detailedAnalysisArea.clear();
            chatBotService.clearHistory();
            showWelcomeMessage();
            logger.info("История чата очищена");
        }
    }

    @FXML
    private void onAnalyzeAudio() {
        if (currentAudioFile == null) {
            showAlert("Внимание", "Сначала запишите аудио");
            return;
        }

        new Thread(() -> {
            Platform.runLater(() -> {
                analysisProgress.setVisible(true);
                detailedAnalysisArea.setVisible(true);
            });

            try {
                EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(
                        currentAudioFile,
                        inputField.getText().isEmpty() ? "[Аудиосообщение]" : inputField.getText()
                );

                Platform.runLater(() -> {
                    analysisArea.setText(analysis.getSummary());
                    detailedAnalysisArea.setText(analysis.getDetailedReport());
                    analysisProgress.setVisible(false);

                    // Обновляем метку фонемы если есть
                    if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
                        String worstPhoneme = analysis.getPhonemeScores().entrySet().stream()
                                .min((e1, e2) -> Float.compare(e1.getValue(), e2.getValue()))
                                .map(entry -> "/" + entry.getKey() + "/")
                                .orElse("");
                        if (!worstPhoneme.isEmpty()) {
                            phonemeLabel.setText("Слабая фонема: " + worstPhoneme);
                        }
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка анализа аудио", e);
                Platform.runLater(() -> {
                    showError("Ошибка анализа", e.getMessage());
                    analysisProgress.setVisible(false);
                });
            }
        }).start();
    }

    @FXML
    private void onPronunciationTraining() {
        if (currentAudioFile == null) {
            showAlert("Внимание", "Сначала запишите аудио и проанализируйте его");
            return;
        }

        try {
            // Анализируем аудио для получения данных о фонемах
            EnhancedSpeechAnalysis analysis = audioAnalyzer.analyzeAudio(
                    currentAudioFile,
                    inputField.getText().isEmpty() ? "[Аудиосообщение]" : inputField.getText()
            );

            // Получаем слабые фонемы
            if (analysis.getPhonemeScores() != null && !analysis.getPhonemeScores().isEmpty()) {
                // Находим 3 самые слабые фонемы
                List<Map.Entry<String, Float>> weakPhonemes = analysis.getPhonemeScores().entrySet().stream()
                        .filter(e -> e.getValue() < 80) // Фонемы с оценкой ниже 80
                        .sorted(Comparator.comparing(Map.Entry::getValue))
                        .limit(3)
                        .collect(Collectors.toList());

                if (!weakPhonemes.isEmpty()) {
                    // Создаем упражнения для каждой слабой фонемы
                    StringBuilder exercisesText = new StringBuilder();
                    exercisesText.append("ТРЕНАЖЕР ПРОИЗНОШЕНИЯ\n");
                    exercisesText.append("=====================\n\n");

                    for (Map.Entry<String, Float> phonemeEntry : weakPhonemes) {
                        String phoneme = phonemeEntry.getKey();
                        float score = phonemeEntry.getValue();

                        PronunciationTrainer.PronunciationExercise exercise =
                                pronunciationTrainer.createExercise(phoneme,
                                        score < 60 ? "beginner" : score < 75 ? "intermediate" : "advanced");

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

                    // Показываем упражнения в отдельном окне
                    TextArea trainingArea = new TextArea(exercisesText.toString());
                    trainingArea.setEditable(false);
                    trainingArea.setWrapText(true);
                    trainingArea.setPrefSize(800, 600);
                    trainingArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

                    ScrollPane scrollPane = new ScrollPane(trainingArea);
                    scrollPane.setFitToWidth(true);

                    Stage trainingStage = new Stage();
                    trainingStage.setTitle("Тренажер произношения - Проблемные звуки");
                    trainingStage.initOwner(stage);

                    javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 820, 650);
                    trainingStage.setScene(scene);
                    trainingStage.show();

                } else {
                    showAlert("Отличное произношение!",
                            "У вас нет проблемных звуков! Все фонемы оценены выше 80 баллов.");
                }
            } else {
                showAlert("Данные не найдены",
                        "Не удалось получить данные о фонемах. Попробуйте проанализировать аудио заново.");
            }

        } catch (Exception e) {
            logger.error("Ошибка при создании тренажера произношения", e);
            showError("Ошибка", "Не удалось создать тренажер: " + e.getMessage());
        }
    }

    private void showPronunciationExercise(PronunciationTrainer.PronunciationExercise exercise) {
        TextArea exerciseArea = new TextArea();
        exerciseArea.setEditable(false);
        exerciseArea.setWrapText(true);
        exerciseArea.setPrefSize(800, 600);

        StringBuilder content = new StringBuilder();
        content.append("УПРАЖНЕНИЕ НА ПРОИЗНОШЕНИЕ\n");
        content.append("===========================\n\n");
        content.append("Целевой звук: /").append(exercise.getTargetPhoneme()).append("/\n");
        content.append("Уровень сложности: ").append(exercise.getDifficulty()).append("\n\n");

        content.append("ИНСТРУКЦИИ:\n");
        content.append(exercise.getInstructions()).append("\n\n");

        content.append("ПРИМЕРЫ:\n");
        for (String example : exercise.getExamples()) {
            content.append("• ").append(example).append("\n");
        }
        content.append("\n");

        content.append("СЛОВА ДЛЯ ПРАКТИКИ:\n");
        for (String word : exercise.getPracticeWords()) {
            content.append("• ").append(word).append("\n");
        }
        content.append("\n");

        content.append("СОВЕТЫ:\n");
        for (String tip : exercise.getTips()) {
            content.append("• ").append(tip).append("\n");
        }

        exerciseArea.setText(content.toString());

        ScrollPane scrollPane = new ScrollPane(exerciseArea);
        scrollPane.setFitToWidth(true);

        Stage exerciseStage = new Stage();
        exerciseStage.setTitle("Тренажер произношения - Звук /" + exercise.getTargetPhoneme() + "/");
        exerciseStage.initOwner(stage);

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 820, 650);
        exerciseStage.setScene(scene);
        exerciseStage.show();
    }

    @FXML
    private void onShowHistory() {
        List<Conversation> history = chatBotService.getConversationHistory();

        if (history.isEmpty()) {
            showAlert("История", "История разговоров пуста");
            return;
        }

        StringBuilder historyText = new StringBuilder();
        historyText.append("## История разговоров:\n\n");

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (Conversation conv : history) {
            historyText.append("**").append(sdf.format(conv.getTimestamp())).append("**\n");
            historyText.append("**Вы:** ").append(conv.getUserMessage()).append("\n");
            if (conv.getPronunciationScore() > 0) {
                historyText.append("Оценка произношения: ").append(
                        String.format("%.1f/100", conv.getPronunciationScore())
                ).append("\n");
            }
            historyText.append("---\n");
        }

        // Показываем историю в отдельном окне
        TextArea historyArea = new TextArea(historyText.toString());
        historyArea.setEditable(false);
        historyArea.setWrapText(true);
        historyArea.setPrefSize(600, 400);
        historyArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        ScrollPane scrollPane = new ScrollPane(historyArea);
        scrollPane.setFitToWidth(true);

        Stage historyStage = new Stage();
        historyStage.setTitle("История разговоров");
        historyStage.initOwner(stage);

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 620, 450);
        historyStage.setScene(scene);
        historyStage.show();
    }

    @FXML
    private void onSettings() {
        showAlert("Настройки", "Настройки будут доступны в следующей версии");
    }

    @FXML
    private void onHelp() {
        String helpText = """
            **SpeakAI - Руководство пользователя**
            
            **Основные функции:**
            1. **Текстовый чат** - пишите сообщения на английском
            2. **Запись голоса** - нажмите 🎤 для записи речи
            3. **Анализ речи** - автоматический анализ произношения
            4. **Рекомендации** - персонализированные советы по улучшению
            
            **Советы:**
            • Записывайте свою речь для анализа произношения
            • Используйте разнообразные темы для разговора
            • Обращайте внимание на рекомендации ИИ-репетитора
            
            **Контактная информация:**
            support@speakai.com
            """;

        showAlert("Помощь", helpText);
    }

    private void startRecordingTimer() {
        recordingTimerThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();

            while (speechRecorder.isRecording()) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                long seconds = elapsedTime / 1000;
                long minutes = seconds / 60;

                final String timeText = String.format("%02d:%02d", minutes, seconds % 60);

                Platform.runLater(() -> {
                    recordingTimeLabel.setText(timeText);
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

    private void appendToChat(String sender, String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String timestamp = sdf.format(new Date());

        String formattedMessage = String.format("\n[%s] **%s:**\n%s\n",
                timestamp, sender, message);

        Platform.runLater(() -> {
            chatArea.appendText(formattedMessage);

            // Прокрутка вниз
            chatArea.positionCaret(chatArea.getLength());
        });
    }

    private void loadConversationHistory() {
        // В фоновом потоке загружаем историю
        new Thread(() -> {
            List<Conversation> history = chatBotService.getConversationHistory();

            if (!history.isEmpty()) {
                Platform.runLater(() -> {
                    // Можно добавить кнопку для просмотра полной истории
                    statusLabel.setText("Загружено " + history.size() + " прошлых разговоров");
                });
            }
        }).start();
    }

    private void updateStatusLabel() {
        String status = isAiServiceAvailable ?
                "✅ ИИ-сервис доступен" : "⚠️ Демо-режим (установите API ключ)";

        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    private void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show) {
                // Можно добавить индикатор загрузки
                statusLabel.setText("Обработка...");
                sendButton.setDisable(true);
            } else {
                updateStatusLabel();
                sendButton.setDisable(false);
            }
        });
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Внимание");
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

    public void setStage(Stage stage) {
        this.stage = stage;

        // Обработка закрытия окна
        stage.setOnCloseRequest(event -> {
            if (speechRecorder != null && speechRecorder.isRecording()) {
                speechRecorder.stopRecording(currentAudioFile);
            }
            if (speechPlayer != null && speechPlayer.isPlaying()) {
                speechPlayer.stopAudio();
            }
            logger.info("Окно приложения закрыто");
        });
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (speechRecorder != null) {
                speechRecorder.cleanup();
            }
        } finally {
            super.finalize();
        }
    }
}