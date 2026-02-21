package com.mygitgor.chatbot.components;

import com.mygitgor.service.ChatBotService;
import com.mygitgor.config.AppConstants;
import com.mygitgor.error.ErrorHandler;
import com.mygitgor.service.SpeechToTextService;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SpeechRecognitionUIManager {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognitionUIManager.class);

    private static final int STATUS_CLEAR_DELAY_SECONDS = 2;
    private static final int DEBOUNCE_DELAY_MS = 300;

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

    private final ChatBotService chatBotService;
    private final Runnable onStatusUpdate;

    private final AtomicBoolean isTesting = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> currentTest = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;
    private final ScheduledExecutorService scheduledExecutor;

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

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        initializeUI();
    }

    private void initializeUI() {
        if (!areUIElementsValid()) {
            logger.warn("Элементы управления распознаванием речи не найдены в FXML");
            return;
        }

        setupServiceTypeComboBox();
        setupMicrophoneSensitivitySlider();
        setupLanguageComboBox();

        updateServiceStatus(AppConstants.DEFAULT_SPEECH_SERVICE);
    }

    private boolean areUIElementsValid() {
        return serviceTypeComboBox != null &&
                languageComboBox != null &&
                microphoneSensitivitySlider != null;
    }

    private void setupServiceTypeComboBox() {
        Platform.runLater(() -> {
            serviceTypeComboBox.getItems().addAll(
                    "MOCK - Тестовый режим",
                    "VOSK - Оффлайн распознавание",
                    "WHISPER - OpenAI Whisper",
                    "GOOGLE - Google Speech API"
            );
            serviceTypeComboBox.setValue(AppConstants.DEFAULT_SPEECH_SERVICE);
        });

        serviceTypeComboBox.setOnAction(event -> {
            String selected = serviceTypeComboBox.getValue();
            updateServiceStatus(selected);
            onStatusUpdate.run();
        });
    }

    private void setupMicrophoneSensitivitySlider() {
        Platform.runLater(() -> {
            microphoneSensitivitySlider.setMin(AppConstants.MIN_MICROPHONE_SENSITIVITY);
            microphoneSensitivitySlider.setMax(AppConstants.MAX_MICROPHONE_SENSITIVITY);
            microphoneSensitivitySlider.setValue(AppConstants.DEFAULT_MICROPHONE_SENSITIVITY);

            if (sensitivityLabel != null) {
                sensitivityLabel.setText(String.format("%.1f",
                        AppConstants.DEFAULT_MICROPHONE_SENSITIVITY));
            }
        });

        AtomicReference<Double> lastSensitivity = new AtomicReference<>(
                AppConstants.DEFAULT_MICROPHONE_SENSITIVITY
        );

        microphoneSensitivitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double sensitivity = Math.round(newVal.doubleValue() * 10) / 10.0;

            if (sensitivityLabel != null) {
                Platform.runLater(() -> sensitivityLabel.setText(String.format("%.1f", sensitivity)));
            }

            lastSensitivity.set(sensitivity);

            scheduledExecutor.schedule(() -> {
                double currentSensitivity = lastSensitivity.get();
                chatBotService.setMicrophoneSensitivity(currentSensitivity);
                logger.debug("Чувствительность микрофона обновлена: {}", currentSensitivity);
            }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        });
    }

    private void setupLanguageComboBox() {
        Platform.runLater(() -> {
            languageComboBox.setVisible(false);
            languageComboBox.setDisable(true);
        });

        CompletableFuture.runAsync(this::loadLanguages, backgroundExecutor);

        languageComboBox.setOnAction(event -> {
            String selected = languageComboBox.getValue();
            if (selected != null) {
                try {
                    String languageCode = extractLanguageCode(selected);

                    CompletableFuture.runAsync(() -> {
                        chatBotService.switchSpeechLanguage(languageCode);
                        logger.info("Язык распознавания изменен на: {}", languageCode);

                        Platform.runLater(() -> {
                            updateServiceStatus(serviceTypeComboBox.getValue());
                        });
                    }, backgroundExecutor).exceptionally(throwable -> {
                        logger.error("Ошибка при смене языка", throwable);
                        Platform.runLater(() -> {
                            ErrorHandler.showError("Ошибка",
                                    "Не удалось изменить язык: " + throwable.getMessage());
                        });
                        return null;
                    });

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

                String currentItem = currentLangName + " (" + currentLangCode + ")";
                if (languageComboBox.getItems().contains(currentItem)) {
                    languageComboBox.setValue(currentItem);
                } else if (!languageComboBox.getItems().isEmpty()) {
                    languageComboBox.setValue(languageComboBox.getItems().get(0));
                }

                languageComboBox.setVisible(true);
                languageComboBox.setDisable(false);

                logger.info("Загружено {} языков распознавания", languages.size());
            });
        } catch (Exception e) {
            logger.error("Ошибка при загрузке языков", e);
            Platform.runLater(() -> {
                languageComboBox.setVisible(false);
                languageComboBox.setDisable(true);
            });
        }
    }

    public void updateServiceStatus(String serviceInfo) {
        if (speechServiceStatusLabel == null) return;

        Platform.runLater(() -> {
            String statusText = getStatusText(serviceInfo);
            String statusStyle = getStatusStyle(serviceInfo);
            boolean languageDisabled = isLanguageDisabled(serviceInfo);

            speechServiceStatusLabel.setText(statusText);
            speechServiceStatusLabel.setStyle(statusStyle + " -fx-font-weight: bold;");

            if (languageComboBox != null) {
                languageComboBox.setDisable(languageDisabled);
            }
        });
    }

    public void startMicrophoneTest() {
        if (!isTesting.compareAndSet(false, true)) {
            logger.warn("Тестирование уже выполняется");
            return;
        }

        updateMicrophoneTestUI(true);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
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
                isTesting.set(false);
                Platform.runLater(() -> updateMicrophoneTestUI(false));
            }
        }, backgroundExecutor);

        currentTest.set(future);

        scheduledExecutor.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                isTesting.set(false);
                Platform.runLater(() -> {
                    updateMicrophoneTestUI(false);
                    updateMicrophoneStatus("⏱️ Таймаут", "warning");
                });
                logger.warn("Таймаут теста микрофона");
            }
        }, 10, TimeUnit.SECONDS);
    }

    public void startSpeechRecognitionTest(String testAudioPath) {
        if (!isTesting.compareAndSet(false, true)) {
            logger.warn("Тестирование уже выполняется");
            return;
        }

        File testFile = new File(testAudioPath);
        if (!testFile.exists()) {
            isTesting.set(false);
            ErrorHandler.showWarning("Тест", "Сначала запишите тестовое аудио");
            return;
        }

        updateSpeechTestUI(true);
        updateStatus("🔍 Тестирование распознавания...");

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                SpeechToTextService service = chatBotService.getSpeechToTextService();
                SpeechToTextService.SpeechRecognitionResult result =
                        service.transcribe(testAudioPath);
                long elapsedTime = System.currentTimeMillis() - startTime;

                final SpeechToTextService.SpeechRecognitionResult finalResult = result;
                final long finalElapsedTime = elapsedTime;

                Platform.runLater(() -> {
                    showRecognitionResult(finalResult, finalElapsedTime);
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
                isTesting.set(false);
                Platform.runLater(() -> updateSpeechTestUI(false));
            }
        }, backgroundExecutor);

        currentTest.set(future);

        scheduledExecutor.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                isTesting.set(false);
                Platform.runLater(() -> {
                    updateSpeechTestUI(false);
                    updateStatus("⏱️ Таймаут");
                });
                logger.warn("Таймаут теста распознавания");
            }
        }, 30, TimeUnit.SECONDS);
    }

    public void startMicrophoneRecognition(TextArea inputField) {
        if (!isTesting.compareAndSet(false, true)) {
            logger.warn("Распознавание уже выполняется");
            return;
        }

        final TextArea finalInputField = inputField;

        updateMicrophoneRecognitionUI(true);
        updateMicrophoneStatus("🎤 Говорите...", "recording");

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                String recognizedText = chatBotService.recognizeSpeechInRealTime();

                final String finalText = recognizedText;

                Platform.runLater(() -> {
                    if (finalText != null && !finalText.trim().isEmpty()) {
                        insertRecognizedText(finalInputField, finalText);
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
                isTesting.set(false);
                Platform.runLater(() -> {
                    updateMicrophoneRecognitionUI(false);
                    scheduledExecutor.schedule(() ->
                                    Platform.runLater(() -> updateMicrophoneStatus("", "none")),
                            STATUS_CLEAR_DELAY_SECONDS, TimeUnit.SECONDS
                    );
                });
            }
        }, backgroundExecutor);

        currentTest.set(future);

        scheduledExecutor.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                isTesting.set(false);
                Platform.runLater(() -> {
                    updateMicrophoneRecognitionUI(false);
                    updateMicrophoneStatus("⏱️ Таймаут", "warning");
                });
                logger.warn("Таймаут распознавания речи");
            }
        }, 10, TimeUnit.SECONDS);
    }

    public void cancelCurrentTest() {
        CompletableFuture<Void> test = currentTest.getAndSet(null);
        if (test != null && !test.isDone()) {
            test.cancel(true);
        }

        isTesting.set(false);

        Platform.runLater(() -> {
            updateMicrophoneTestUI(false);
            updateSpeechTestUI(false);
            updateMicrophoneRecognitionUI(false);
            updateMicrophoneStatus("⏹️ Прервано", "warning");
        });

        logger.info("Текущее тестирование отменено");
    }

    public void togglePanel() {
        if (speechControlPanel == null) return;

        Platform.runLater(() -> {
            boolean isVisible = speechControlPanel.isVisible();
            speechControlPanel.setVisible(!isVisible);
            speechControlPanel.setManaged(!isVisible);
        });

        logger.info("Панель распознавания речи {}",
                speechControlPanel.isVisible() ? "показана" : "скрыта");
    }

    public boolean isPanelVisible() {
        return speechControlPanel != null && speechControlPanel.isVisible();
    }

    private String extractLanguageCode(String selectedItem) {
        int start = selectedItem.lastIndexOf("(") + 1;
        int end = selectedItem.lastIndexOf(")");
        return selectedItem.substring(start, end);
    }

    private String getStatusText(String serviceInfo) {
        if (serviceInfo.contains("MOCK")) {
            return "🔧 Тестовый режим";
        } else if (serviceInfo.contains("WHISPER")) {
            return "✅ Whisper API";
        } else if (serviceInfo.contains("GOOGLE")) {
            return "✅ Google Speech API";
        } else if (serviceInfo.contains("VOSK")) {
            return "📁 Vosk оффлайн";
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
            Platform.runLater(() -> {
                testMicrophoneButton.setDisable(testing);
                testMicrophoneButton.setText(testing ? "🔄 Тестирование..." : "🎤 Тест микрофона");
            });
        }
    }

    private void updateSpeechTestUI(boolean testing) {
        if (testSpeechButton != null) {
            Platform.runLater(() -> {
                testSpeechButton.setDisable(testing);
                testSpeechButton.setText(testing ? "🔄 Тестирование..." : "🔊 Тест распознавания");
            });
        }
    }

    private void updateMicrophoneRecognitionUI(boolean active) {
        if (microphoneButton != null) {
            Platform.runLater(() -> {
                microphoneButton.setDisable(active);
                microphoneButton.setText(active ? "🔴" : "🎤");
                microphoneButton.setStyle(active ?
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;" :
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
            });
        }
    }

    private void updateMicrophoneStatus(String status, String type) {
        if (microphoneStatusLabel == null) return;

        String color = switch (type) {
            case "success" -> "#27ae60";
            case "error" -> "#e74c3c";
            case "recording" -> "#e74c3c";
            case "warning" -> "#f39c12";
            default -> "#7f8c8d";
        };

        final String finalStatus = status;
        final String finalStyle = "-fx-text-fill: " + color + "; -fx-font-weight: bold;";

        Platform.runLater(() -> {
            microphoneStatusLabel.setText(finalStatus);
            microphoneStatusLabel.setStyle(finalStyle);
        });
    }

    private void updateStatus(String status) {
        if (speechServiceStatusLabel != null) {
            Platform.runLater(() ->
                    speechServiceStatusLabel.setText(status));
        }
    }

    private void insertRecognizedText(TextArea inputField, String text) {
        if (inputField == null) return;

        Platform.runLater(() -> {
            String currentText = inputField.getText();
            if (!currentText.isEmpty() && !currentText.matches(".*[.!?\\s]$")) {
                currentText += " ";
            }
            inputField.setText(currentText + text);
            inputField.positionCaret(inputField.getText().length());
        });
    }

    private void showRecognitionResult(SpeechToTextService.SpeechRecognitionResult result,
                                       long elapsedTime) {
        String message = String.format(
                "Результат теста:\n\n" +
                        "📝 Текст: %s\n" +
                        "📊 Уверенность: %.1f%%\n" +
                        "⏱️ Время: %d мс\n" +
                        "🔧 Сервис: %s\n" +
                        "🌐 Язык: %s",
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
        return isTesting.get();
    }
}