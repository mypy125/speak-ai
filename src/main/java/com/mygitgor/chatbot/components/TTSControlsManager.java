package com.mygitgor.chatbot.components;

import com.mygitgor.config.AppConstants;
import com.mygitgor.service.GoogleCloudTextToSpeechService;
import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import java.util.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TTSControlsManager {
    private static final Logger logger = LoggerFactory.getLogger(TTSControlsManager.class);

    private static final int UI_UPDATE_DELAY_MS = 50;

    private final ITTSService ttsService;
    private final AtomicBoolean isGoogleTTS = new AtomicBoolean(false);
    private final AtomicReference<GoogleCloudTextToSpeechService> googleService = new AtomicReference<>(null);

    private final Slider googleSpeedSlider;
    private final Label googleSpeedLabel;
    private final Slider googlePitchSlider;
    private final Label pitchLabel;
    private final Slider googleVolumeSlider;
    private final Label volumeLabel;
    private final ComboBox<String> googleLanguageComboBox;
    private final ComboBox<String> googleVoiceComboBox;
    private final Label googleVoiceDescriptionLabel;
    private final Circle ttsStatusIndicator;
    private final Label ttsStatusLabel;
    private final TextArea ttsInfoTextArea;
    private final Label ttsModeLabel;
    private final VBox googleCloudSettings;

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;

    private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private final AtomicBoolean needsUpdate = new AtomicBoolean(false);

    public TTSControlsManager(ITTSService ttsService,
                              Slider googleSpeedSlider, Label googleSpeedLabel,
                              Slider googlePitchSlider, Label pitchLabel,
                              Slider googleVolumeSlider, Label volumeLabel,
                              ComboBox<String> googleLanguageComboBox,
                              ComboBox<String> googleVoiceComboBox,
                              Label googleVoiceDescriptionLabel,
                              Circle ttsStatusIndicator, Label ttsStatusLabel,
                              TextArea ttsInfoTextArea, Label ttsModeLabel,
                              VBox googleCloudSettings) {
        this.ttsService = ttsService;
        this.isGoogleTTS.set(ttsService instanceof GoogleCloudTextToSpeechService);

        if (isGoogleTTS.get()) {
            this.googleService.set((GoogleCloudTextToSpeechService) ttsService);
        }

        this.googleSpeedSlider = googleSpeedSlider;
        this.googleSpeedLabel = googleSpeedLabel;
        this.googlePitchSlider = googlePitchSlider;
        this.pitchLabel = pitchLabel;
        this.googleVolumeSlider = googleVolumeSlider;
        this.volumeLabel = volumeLabel;
        this.googleLanguageComboBox = googleLanguageComboBox;
        this.googleVoiceComboBox = googleVoiceComboBox;
        this.googleVoiceDescriptionLabel = googleVoiceDescriptionLabel;
        this.ttsStatusIndicator = ttsStatusIndicator;
        this.ttsStatusLabel = ttsStatusLabel;
        this.ttsInfoTextArea = ttsInfoTextArea;
        this.ttsModeLabel = ttsModeLabel;
        this.googleCloudSettings = googleCloudSettings;

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();

        initializeControls();
        scheduleUIUpdate();
    }

    private void initializeControls() {
        if (ttsService == null) {
            logger.warn("TTS сервис не доступен, контролы не будут инициализированы");
            disableAllControls();
            return;
        }

        if (isGoogleTTS.get() && googleService.get() != null) {
            setupSpeedSlider();
            setupPitchSlider();
            setupVolumeSlider();
            setupLanguageComboBox();
            setupVoiceComboBox();
            enableGoogleControls(true);
        } else {
            enableGoogleControls(false);
        }
    }

    private void enableGoogleControls(boolean enable) {
        Platform.runLater(() -> {
            if (googleSpeedSlider != null) googleSpeedSlider.setDisable(!enable);
            if (googlePitchSlider != null) googlePitchSlider.setDisable(!enable);
            if (googleVolumeSlider != null) googleVolumeSlider.setDisable(!enable);
            if (googleLanguageComboBox != null) googleLanguageComboBox.setDisable(!enable);
            if (googleVoiceComboBox != null) googleVoiceComboBox.setDisable(!enable);
        });
    }

    private void disableAllControls() {
        Platform.runLater(() -> {
            if (googleSpeedSlider != null) googleSpeedSlider.setDisable(true);
            if (googlePitchSlider != null) googlePitchSlider.setDisable(true);
            if (googleVolumeSlider != null) googleVolumeSlider.setDisable(true);
            if (googleLanguageComboBox != null) googleLanguageComboBox.setDisable(true);
            if (googleVoiceComboBox != null) googleVoiceComboBox.setDisable(true);
            if (googleCloudSettings != null) {
                googleCloudSettings.setVisible(false);
                googleCloudSettings.setManaged(false);
            }
        });
    }

    private void setupSpeedSlider() {
        if (googleSpeedSlider == null || googleSpeedLabel == null || !isGoogleTTS.get()) return;

        GoogleCloudTextToSpeechService service = googleService.get();
        if (service == null) return;

        Platform.runLater(() -> {
            googleSpeedSlider.setMin(AppConstants.MIN_SPEECH_SPEED);
            googleSpeedSlider.setMax(AppConstants.MAX_SPEECH_SPEED);
            googleSpeedSlider.setValue(service.getCurrentSpeed());
            googleSpeedLabel.setText(String.format("%.1fx", service.getCurrentSpeed()));
        });

        googleSpeedSlider.valueProperty().addListener((obs, old, val) -> {
            double speed = Math.round(val.doubleValue() * 10) / 10.0;
            Platform.runLater(() -> googleSpeedLabel.setText(String.format("%.1fx", speed)));

            threadPoolManager.schedule(() -> {
                GoogleCloudTextToSpeechService currentService = googleService.get();
                if (currentService != null) {
                    currentService.setSpeed((float) speed);
                    logger.debug("Скорость TTS обновлена: {}", speed);
                }
            }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private void setupPitchSlider() {
        if (googlePitchSlider == null || pitchLabel == null || !isGoogleTTS.get()) return;

        GoogleCloudTextToSpeechService service = googleService.get();
        if (service == null) return;

        Platform.runLater(() -> {
            googlePitchSlider.setMin(AppConstants.MIN_PITCH);
            googlePitchSlider.setMax(AppConstants.MAX_PITCH);
            googlePitchSlider.setValue(service.getCurrentPitch());
            pitchLabel.setText(String.format("%.1f", service.getCurrentPitch()));
        });

        googlePitchSlider.valueProperty().addListener((obs, old, val) -> {
            double pitch = Math.round(val.doubleValue() * 10) / 10.0;
            Platform.runLater(() -> pitchLabel.setText(String.format("%.1f", pitch)));

            threadPoolManager.schedule(() -> {
                GoogleCloudTextToSpeechService currentService = googleService.get();
                if (currentService != null) {
                    currentService.setPitch((float) pitch);
                    logger.debug("Тон TTS обновлен: {}", pitch);
                }
            }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private void setupVolumeSlider() {
        if (googleVolumeSlider == null || volumeLabel == null || !isGoogleTTS.get()) return;

        GoogleCloudTextToSpeechService service = googleService.get();
        if (service == null) return;

        Platform.runLater(() -> {
            googleVolumeSlider.setMin(AppConstants.MIN_VOLUME_DB);
            googleVolumeSlider.setMax(AppConstants.MAX_VOLUME_DB);
            googleVolumeSlider.setValue(service.getCurrentVolumeGainDb());
            volumeLabel.setText((int)service.getCurrentVolumeGainDb() + " дБ");
        });

        googleVolumeSlider.valueProperty().addListener((obs, old, val) -> {
            int volume = val.intValue();
            Platform.runLater(() -> volumeLabel.setText(volume + " дБ"));

            threadPoolManager.schedule(() -> {
                GoogleCloudTextToSpeechService currentService = googleService.get();
                if (currentService != null) {
                    currentService.setVolumeGainDb(volume);
                    logger.debug("Громкость TTS обновлена: {} дБ", volume);
                }
            }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private void setupLanguageComboBox() {
        if (googleLanguageComboBox == null || !isGoogleTTS.get()) return;

        googleLanguageComboBox.setOnAction(event -> {
            String selected = googleLanguageComboBox.getValue();
            if (selected != null) {
                String languageCode = selected.split(" - ")[0];

                CompletableFuture.runAsync(() -> {
                    GoogleCloudTextToSpeechService currentService = googleService.get();
                    if (currentService != null) {
                        currentService.setLanguage(languageCode);
                        logger.info("Язык TTS изменен на: {}", languageCode);

                        updateVoicesForLanguage(languageCode);
                    }
                }, backgroundExecutor);
            }
        });
    }

    private void setupVoiceComboBox() {
        if (googleVoiceComboBox == null || googleVoiceDescriptionLabel == null || !isGoogleTTS.get()) return;

        googleVoiceComboBox.setOnAction(event -> {
            String selected = googleVoiceComboBox.getValue();
            if (selected != null) {
                googleVoiceDescriptionLabel.setText("Выбран: " + selected);

                String voiceName = selected.split(" \\(")[0];
                try {
                    GoogleCloudTextToSpeechService.GoogleVoice voice =
                            GoogleCloudTextToSpeechService.GoogleVoice.valueOf(voiceName);

                    GoogleCloudTextToSpeechService currentService = googleService.get();
                    if (currentService != null) {
                        currentService.setVoice(voice);
                        logger.info("Голос TTS изменен на: {}", voice.getDescription());

                        scheduleUIUpdate();
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Неизвестный голос Google TTS: {}", voiceName);
                }
            }
        });
    }

    private void scheduleUIUpdate() {
        if (isUpdating.compareAndSet(false, true)) {
            needsUpdate.set(true);

            threadPoolManager.schedule(() -> {
                if (needsUpdate.getAndSet(false)) {
                    performUIUpdate();
                }
                isUpdating.set(false);
            }, UI_UPDATE_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            needsUpdate.set(true);
        }
    }

    private void performUIUpdate() {
        Platform.runLater(() -> {
            if (isGoogleTTS.get() && googleService.get() != null) {
                updateGoogleTTSUI();
            } else {
                updateDemoTTSUI();
            }
            updateTTSStatus();
        });
    }

    private void updateGoogleTTSUI() {
        GoogleCloudTextToSpeechService service = googleService.get();
        if (service == null) return;

        if (ttsModeLabel != null) {
            ttsModeLabel.setText("Google Cloud TTS");
            ttsModeLabel.setTooltip(new Tooltip("Используется Google Cloud Text-to-Speech API"));
            ttsModeLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        }

        if (googleCloudSettings != null) {
            googleCloudSettings.setVisible(true);
            googleCloudSettings.setManaged(true);
        }

        if (googleSpeedSlider != null) {
            googleSpeedSlider.setValue(service.getCurrentSpeed());
        }
        if (googlePitchSlider != null) {
            googlePitchSlider.setValue(service.getCurrentPitch());
        }
        if (googleVolumeSlider != null) {
            googleVolumeSlider.setValue(service.getCurrentVolumeGainDb());
        }

        if (googleLanguageComboBox != null && googleLanguageComboBox.getItems().isEmpty()) {
            populateLanguageComboBox(service);
        }

        if (ttsInfoTextArea != null) {
            ttsInfoTextArea.setText(formatGoogleTTSInfo(service));
        }
    }

    private void populateLanguageComboBox(GoogleCloudTextToSpeechService service) {
        Set<String> languages = new HashSet<>();
        for (GoogleCloudTextToSpeechService.GoogleVoice voice :
                GoogleCloudTextToSpeechService.GoogleVoice.values()) {
            languages.add(voice.getLanguageCode());
        }

        List<String> sortedLanguages = new ArrayList<>(languages);
        Collections.sort(sortedLanguages);

        for (String langCode : sortedLanguages) {
            String langName = AppConstants.LANGUAGE_NAMES.getOrDefault(langCode, langCode);
            googleLanguageComboBox.getItems().add(langCode + " - " + langName);
        }

        String currentLangCode = service.getCurrentVoice().getLanguageCode();
        String currentLangName = AppConstants.LANGUAGE_NAMES.getOrDefault(currentLangCode, currentLangCode);
        String currentLangItem = currentLangCode + " - " + currentLangName;

        if (googleLanguageComboBox.getItems().contains(currentLangItem)) {
            googleLanguageComboBox.setValue(currentLangItem);
        } else if (!googleLanguageComboBox.getItems().isEmpty()) {
            googleLanguageComboBox.setValue(googleLanguageComboBox.getItems().get(0));
        }

        updateVoicesForLanguage(currentLangCode);
    }

    private String formatGoogleTTSInfo(GoogleCloudTextToSpeechService service) {
        return String.format("""
            ✅ GOOGLE CLOUD TTS АКТИВИРОВАН
            ================================
            
            📊 ТЕКУЩАЯ КОНФИГУРАЦИЯ:
            • Голос: %s
            • Язык: %s
            • Скорость: %.1fx
            • Тон: %.1f
            • Громкость: %.0f дБ
            • Метод аутентификации: %s
            
            🔧 ТЕХНИЧЕСКАЯ ИНФОРМАЦИЯ:
            • Тип: WaveNet (нейросетевой синтез)
            • Качество: Премиум
            • Лимит: 5000 байт на запрос
            """,
                service.getCurrentVoice().getDescription(),
                service.getCurrentLanguage(),
                service.getCurrentSpeed(),
                service.getCurrentPitch(),
                service.getCurrentVolumeGainDb(),
                service.getAuthMethod()
        );
    }


    private void updateDemoTTSUI() {
        if (ttsModeLabel != null) {
            ttsModeLabel.setText("Демо-режим TTS");
            ttsModeLabel.setTooltip(new Tooltip("Используется демо-режим без реальной озвучки"));
            ttsModeLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
        }

        if (googleCloudSettings != null) {
            googleCloudSettings.setVisible(false);
            googleCloudSettings.setManaged(false);
        }

        enableGoogleControls(false);

        if (ttsInfoTextArea != null) {
            ttsInfoTextArea.setText("""
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
                • Настройки: Недоступны
                
                💡 Совет: Настройте Google Cloud TTS для получения 
                  высококачественной голосовой озвучки!
                """);
        }
    }

    private void updateVoicesForLanguage(String languageCode) {
        if (!isGoogleTTS.get() || googleVoiceComboBox == null) return;

        GoogleCloudTextToSpeechService service = googleService.get();
        if (service == null) return;

        Platform.runLater(() -> {
            googleVoiceComboBox.getItems().clear();

            for (GoogleCloudTextToSpeechService.GoogleVoice voice :
                    GoogleCloudTextToSpeechService.GoogleVoice.values()) {

                if (voice.getLanguageCode().equals(languageCode)) {
                    googleVoiceComboBox.getItems().add(
                            voice.name() + " (" + voice.getDescription() + ")"
                    );
                }
            }

            if (!googleVoiceComboBox.getItems().isEmpty()) {
                String currentVoiceName = service.getCurrentVoice().name();
                String currentVoiceItem = googleVoiceComboBox.getItems().stream()
                        .filter(item -> item.startsWith(currentVoiceName))
                        .findFirst()
                        .orElse(googleVoiceComboBox.getItems().get(0));

                googleVoiceComboBox.setValue(currentVoiceItem);

                if (googleVoiceDescriptionLabel != null) {
                    googleVoiceDescriptionLabel.setText("Выбран: " + currentVoiceItem);
                }
            }
        });
    }

    public void updateTTSStatus() {
        if (ttsService == null || ttsStatusIndicator == null || ttsStatusLabel == null) return;

        Platform.runLater(() -> {
            boolean isAvailable = ttsService.isAvailable();

            if (isGoogleTTS.get()) {
                if (isAvailable) {
                    ttsStatusIndicator.setFill(AppConstants.TTS_STATUS_AVAILABLE);
                    ttsStatusLabel.setText("✅ Google Cloud TTS");
                    ttsStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    ttsStatusIndicator.setFill(AppConstants.TTS_STATUS_UNAVAILABLE);
                    ttsStatusLabel.setText("❌ Google Cloud TTS");
                    ttsStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            } else {
                ttsStatusIndicator.setFill(Color.ORANGE);
                ttsStatusLabel.setText("⚠️ Демо-режим");
                ttsStatusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
            }
        });
    }

    public boolean isTtsAvailable() {
        return ttsService != null && ttsService.isAvailable();
    }

    public boolean isGoogleTTS() {
        return isGoogleTTS.get();
    }

    public ITTSService getTtsService() {
        return ttsService;
    }

    public void refreshUI() {
        scheduleUIUpdate();
    }
}