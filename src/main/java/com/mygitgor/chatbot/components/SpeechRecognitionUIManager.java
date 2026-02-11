package com.mygitgor.chatbot.components;

import com.mygitgor.service.ChatBotService;
import com.mygitgor.config.AppConstants;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.service.SpeechToTextService;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SpeechRecognitionUIManager {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognitionUIManager.class);

    // ========================================
    // UI Elements
    // ========================================

    private final VBox speechControlPanel;
    private final ComboBox<String> serviceTypeComboBox;
    private final ComboBox<String> languageComboBox;
    private final Button testSpeechButton;
    private final Button testMicrophoneButton;
    private final Slider microphoneSensitivitySlider;
    private final Label sensitivityLabel;
    private final Label speechServiceStatusLabel;
    private final Label microphoneStatusLabel;
    private final Button microphoneButton;

    // ========================================
    // Dependencies
    // ========================================

    private final ChatBotService chatBotService;
    private final Runnable onStatusUpdate;

    // ========================================
    // State
    // ========================================

    private boolean isTesting = false;
    private CompletableFuture<Void> currentTest;

    public SpeechRecognitionUIManager(
            VBox speechControlPanel,
            ComboBox<String> serviceTypeComboBox,
            ComboBox<String> languageComboBox,
            Button testSpeechButton,
            Button testMicrophoneButton,
            Slider microphoneSensitivitySlider,
            Label sensitivityLabel,
            Label speechServiceStatusLabel,
            Label microphoneStatusLabel,
            Button microphoneButton,
            ChatBotService chatBotService,
            Runnable onStatusUpdate) {

        this.speechControlPanel = speechControlPanel;
        this.serviceTypeComboBox = serviceTypeComboBox;
        this.languageComboBox = languageComboBox;
        this.testSpeechButton = testSpeechButton;
        this.testMicrophoneButton = testMicrophoneButton;
        this.microphoneSensitivitySlider = microphoneSensitivitySlider;
        this.sensitivityLabel = sensitivityLabel;
        this.speechServiceStatusLabel = speechServiceStatusLabel;
        this.microphoneStatusLabel = microphoneStatusLabel;
        this.microphoneButton = microphoneButton;
        this.chatBotService = chatBotService;
        this.onStatusUpdate = onStatusUpdate;

        initializeUI();
    }

    // ========================================
    // Initialization
    // ========================================

    private void initializeUI() {
        if (!areUIElementsValid()) {
            logger.warn("Элементы управления распознаванием речи не найдены в FXML");
            return;
        }

        setupServiceTypeComboBox();
        setupMicrophoneSensitivitySlider();
        setupLanguageComboBox();

        // Initial status update
        updateServiceStatus(AppConstants.DEFAULT_SPEECH_SERVICE);
    }

    private boolean areUIElementsValid() {
        return serviceTypeComboBox != null &&
                languageComboBox != null &&
                microphoneSensitivitySlider != null;
    }

    // ========================================
    // Setup Methods
    // ========================================

    private void setupServiceTypeComboBox() {
        serviceTypeComboBox.getItems().addAll(
                "MOCK - Тестовый режим",
                "VOSK - Оффлайн распознавание",
                "WHISPER - OpenAI Whisper",
                "GOOGLE - Google Speech API"
        );
        serviceTypeComboBox.setValue(AppConstants.DEFAULT_SPEECH_SERVICE);

        serviceTypeComboBox.setOnAction(event -> {
            String selected = serviceTypeComboBox.getValue();
            updateServiceStatus(selected);
            onStatusUpdate.run();
        });
    }

    private void setupMicrophoneSensitivitySlider() {
        microphoneSensitivitySlider.setMin(AppConstants.MIN_MICROPHONE_SENSITIVITY);
        microphoneSensitivitySlider.setMax(AppConstants.MAX_MICROPHONE_SENSITIVITY);
        microphoneSensitivitySlider.setValue(AppConstants.DEFAULT_MICROPHONE_SENSITIVITY);

        microphoneSensitivitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double sensitivity = Math.round(newVal.doubleValue() * 10) / 10.0;
            if (sensitivityLabel != null) {
                sensitivityLabel.setText(String.format("%.1f", sensitivity));
            }
            chatBotService.setMicrophoneSensitivity(sensitivity);
        });

        // Initial sensitivity label
        if (sensitivityLabel != null) {
            sensitivityLabel.setText(String.format("%.1f",
                    AppConstants.DEFAULT_MICROPHONE_SENSITIVITY));
        }
    }

    private void setupLanguageComboBox() {
        languageComboBox.setVisible(false);
        languageComboBox.setDisable(true);

        // Load languages asynchronously
        CompletableFuture.runAsync(this::loadLanguages);

        languageComboBox.setOnAction(event -> {
            String selected = languageComboBox.getValue();
            if (selected != null) {
                try {
                    String languageCode = extractLanguageCode(selected);
                    chatBotService.switchSpeechLanguage(languageCode);
                    updateServiceStatus(serviceTypeComboBox.getValue());
                    logger.info("Язык распознавания изменен на: {}", languageCode);
                } catch (Exception e) {
                    logger.error("Ошибка при обработке выбора языка", e);
                    ErrorHandler.showError("Ошибка",
                            "Не удалось изменить язык: " + e.getMessage());
                }
            }
        });
    }

    private void loadLanguages() {
        try {
            Map<String, String> languages = chatBotService.getSupportedLanguagesWithNames();
            Platform.runLater(() -> {
                languageComboBox.getItems().clear();

                languages.forEach((code, name) -> {
                    languageComboBox.getItems().add(name + " (" + code + ")");
                });

                String currentLangName = chatBotService.getCurrentSpeechLanguageName();
                String currentLangCode = chatBotService.getCurrentSpeechLanguage();
                languageComboBox.setValue(currentLangName + " (" + currentLangCode + ")");

                languageComboBox.setVisible(true);
                languageComboBox.setDisable(false);

                logger.info("Загружено {} языков распознавания", languages.size());
            });
        } catch (Exception e) {
            logger.error("Ошибка при загрузке языков", e);
        }
    }

    // ========================================
    // Public Methods
    // ========================================

    public void updateServiceStatus(String serviceInfo) {
        if (speechServiceStatusLabel == null) return;

        Platform.runLater(() -> {
            String statusText = getStatusText(serviceInfo);
            String statusStyle = getStatusStyle(serviceInfo);
            boolean languageDisabled = isLanguageDisabled(serviceInfo);

            speechServiceStatusLabel.setText(statusText);
            speechServiceStatusLabel.setStyle(statusStyle);

            if (languageComboBox != null) {
                languageComboBox.setDisable(languageDisabled);
            }
        });
    }

    public void startMicrophoneTest() {
        if (isTesting) {
            logger.warn("Тестирование уже выполняется");
            return;
        }

        isTesting = true;
        updateMicrophoneTestUI(true);

        currentTest = CompletableFuture.runAsync(() -> {
            try {
                logger.info("Начало тестирования микрофона...");
                chatBotService.testMicrophone(AppConstants.TEST_RECORDING_DURATION_SECONDS);

                Platform.runLater(() -> {
                    updateMicrophoneStatus("✓ Тест завершен", "success");
                    showTestResult("Тест микрофона",
                            "Тестирование микрофона успешно завершено.\n" +
                                    "Проверьте консоль для детальной информации.");
                });

            } catch (Exception e) {
                logger.error("Ошибка тестирования микрофона", e);
                Platform.runLater(() -> {
                    updateMicrophoneStatus("✗ Ошибка теста", "error");
                    ErrorHandler.showError("Ошибка теста",
                            "Не удалось протестировать микрофон: " + e.getMessage());
                });
            } finally {
                isTesting = false;
                Platform.runLater(() -> updateMicrophoneTestUI(false));
            }
        });
    }

    public void startSpeechRecognitionTest(String testAudioPath) {
        if (isTesting) {
            logger.warn("Тестирование уже выполняется");
            return;
        }

        java.io.File testFile = new java.io.File(testAudioPath);
        if (!testFile.exists()) {
            ErrorHandler.showWarning("Тест", "Сначала запишите тестовое аудио");
            return;
        }

        isTesting = true;
        updateSpeechTestUI(true);

        currentTest = CompletableFuture.runAsync(() -> {
            try {
                updateStatus("🔍 Тестирование распознавания...");

                long startTime = System.currentTimeMillis();
                SpeechToTextService service = chatBotService.getSpeechToTextService();
                SpeechToTextService.SpeechRecognitionResult result =
                        service.transcribe(testAudioPath);
                long elapsedTime = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    showRecognitionResult(result, elapsedTime);
                    updateServiceStatus(serviceTypeComboBox.getValue());
                });

            } catch (Exception e) {
                logger.error("Ошибка тестирования распознавания", e);
                Platform.runLater(() -> {
                    ErrorHandler.showError("Ошибка теста",
                            "Не удалось протестировать распознавание: " + e.getMessage());
                    updateServiceStatus(serviceTypeComboBox.getValue());
                });
            } finally {
                isTesting = false;
                Platform.runLater(() -> updateSpeechTestUI(false));
            }
        });
    }

    public void startMicrophoneRecognition(TextArea inputField) {
        if (isTesting) {
            logger.warn("Распознавание уже выполняется");
            return;
        }

        isTesting = true;
        updateMicrophoneRecognitionUI(true);

        currentTest = CompletableFuture.runAsync(() -> {
            try {
                updateMicrophoneStatus("🎤 Говорите...", "recording");

                String recognizedText = chatBotService.recognizeSpeechInRealTime();

                Platform.runLater(() -> {
                    if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                        insertRecognizedText(inputField, recognizedText);
                        updateMicrophoneStatus("✓ Распознано", "success");
                    } else {
                        updateMicrophoneStatus("✗ Не распознано", "error");
                    }
                });

            } catch (Exception e) {
                logger.error("Ошибка при распознавании речи", e);
                Platform.runLater(() -> {
                    updateMicrophoneStatus("✗ Ошибка", "error");
                    ErrorHandler.showError("Ошибка распознавания",
                            "Не удалось распознать речь: " + e.getMessage());
                });
            } finally {
                isTesting = false;
                Platform.runLater(() -> {
                    updateMicrophoneRecognitionUI(false);
                    // Clear status after delay
                    CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS)
                            .execute(() -> Platform.runLater(() ->
                                    updateMicrophoneStatus("", "none")));
                });
            }
        });
    }

    public void cancelCurrentTest() {
        if (currentTest != null && !currentTest.isDone()) {
            currentTest.cancel(true);
            isTesting = false;
            updateMicrophoneTestUI(false);
            updateSpeechTestUI(false);
            updateMicrophoneRecognitionUI(false);
            updateMicrophoneStatus("⏹️ Прервано", "warning");
            logger.info("Текущее тестирование отменено");
        }
    }

    public void togglePanel() {
        if (speechControlPanel == null) return;

        boolean isVisible = speechControlPanel.isVisible();
        speechControlPanel.setVisible(!isVisible);
        speechControlPanel.setManaged(!isVisible);

        logger.info("Панель распознавания речи {}", isVisible ? "скрыта" : "показана");
    }

    public boolean isPanelVisible() {
        return speechControlPanel != null && speechControlPanel.isVisible();
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private String extractLanguageCode(String selectedItem) {
        return selectedItem.substring(
                selectedItem.lastIndexOf("(") + 1,
                selectedItem.lastIndexOf(")")
        );
    }

    private String getStatusText(String serviceInfo) {
        if (serviceInfo.contains("MOCK")) {
            return "🔧 Тестовый режим";
        } else if (serviceInfo.contains("WHISPER")) {
            return "✅ Whisper API (требуется ключ)";
        } else if (serviceInfo.contains("GOOGLE")) {
            return "✅ Google Speech API (требуется ключ)";
        } else if (serviceInfo.contains("VOSK")) {
            return "📁 Оффлайн распознавание (требуется модель)";
        }
        return "⚙️ Неизвестный сервис";
    }

    private String getStatusStyle(String serviceInfo) {
        if (serviceInfo.contains("MOCK")) {
            return "-fx-text-fill: #f39c12;"; // Orange
        } else if (serviceInfo.contains("WHISPER") || serviceInfo.contains("GOOGLE")) {
            return "-fx-text-fill: #27ae60;"; // Green
        } else if (serviceInfo.contains("VOSK")) {
            return "-fx-text-fill: #3498db;"; // Blue
        }
        return "-fx-text-fill: #7f8c8d;"; // Gray
    }

    private boolean isLanguageDisabled(String serviceInfo) {
        return serviceInfo.contains("MOCK");
    }

    private void updateMicrophoneTestUI(boolean testing) {
        if (testMicrophoneButton != null) {
            testMicrophoneButton.setDisable(testing);
            testMicrophoneButton.setText(testing ? "🔄 Тестирование..." : "🎤 Тест микрофона");
        }
    }

    private void updateSpeechTestUI(boolean testing) {
        if (testSpeechButton != null) {
            testSpeechButton.setDisable(testing);
            testSpeechButton.setText(testing ? "🔄 Тестирование..." : "🔊 Тест распознавания");
        }
    }

    private void updateMicrophoneRecognitionUI(boolean active) {
        if (microphoneButton != null) {
            microphoneButton.setDisable(active);
            microphoneButton.setText(active ? "🔴" : "🎤");
            microphoneButton.setStyle(active ?
                    "-fx-background-color: #e74c3c;" : "");
        }
    }

    private void updateMicrophoneStatus(String status, String type) {
        if (microphoneStatusLabel == null) return;

        String color;
        switch (type) {
            case "success":
                color = "#27ae60";
                break;
            case "error":
                color = "#e74c3c";
                break;
            case "recording":
                color = "#e74c3c";
                break;
            case "warning":
                color = "#f39c12";
                break;
            default:
                color = "#7f8c8d";
        }

        microphoneStatusLabel.setText(status);
        microphoneStatusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    private void updateStatus(String status) {
        if (speechServiceStatusLabel != null) {
            Platform.runLater(() ->
                    speechServiceStatusLabel.setText(status));
        }
    }

    private void insertRecognizedText(TextArea inputField, String text) {
        if (inputField == null) return;

        String currentText = inputField.getText();
        if (!currentText.isEmpty() && !currentText.matches(".*[.!?\\s]$")) {
            currentText += " ";
        }
        inputField.setText(currentText + text);
        inputField.positionCaret(inputField.getText().length());
    }

    private void showRecognitionResult(SpeechToTextService.SpeechRecognitionResult result,
                                       long elapsedTime) {
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

        ErrorHandler.showInfo("Тест распознавания речи", message);
    }

    private void showTestResult(String title, String message) {
        ErrorHandler.showInfo(title, message);
    }

    // ========================================
    // Getters
    // ========================================

    public String getSelectedServiceType() {
        return serviceTypeComboBox != null ?
                serviceTypeComboBox.getValue() : AppConstants.DEFAULT_SPEECH_SERVICE;
    }

    public String getSelectedLanguage() {
        return languageComboBox != null ?
                languageComboBox.getValue() : null;
    }

    public double getMicrophoneSensitivity() {
        return microphoneSensitivitySlider != null ?
                microphoneSensitivitySlider.getValue() :
                AppConstants.DEFAULT_MICROPHONE_SENSITIVITY;
    }

    public boolean isTesting() {
        return isTesting;
    }
}
