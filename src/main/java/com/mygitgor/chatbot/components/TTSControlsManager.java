package com.mygitgor.chatbot.components;

import com.mygitgor.config.AppConstants;
import com.mygitgor.service.GoogleCloudTextToSpeechService;
import com.mygitgor.service.interfaces.ITTSService;
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

public class TTSControlsManager {
    private static final Logger logger = LoggerFactory.getLogger(TTSControlsManager.class);

    private final ITTSService ttsService;
    private final boolean isGoogleTTS;
    private GoogleCloudTextToSpeechService googleService; // для доступа к специфичным методам

    // UI Elements
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
        this.isGoogleTTS = ttsService instanceof GoogleCloudTextToSpeechService;

        // Сохраняем ссылку на Google сервис если он используется
        if (isGoogleTTS) {
            this.googleService = (GoogleCloudTextToSpeechService) ttsService;
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

        initializeControls();
        updateUI();
    }

    private void initializeControls() {
        if (ttsService == null) {
            logger.warn("TTS сервис не доступен, контролы не будут инициализированы");
            return;
        }

        // Инициализируем контролы только для Google Cloud TTS
        if (isGoogleTTS && googleService != null) {
            setupSpeedSlider();
            setupPitchSlider();
            setupVolumeSlider();
            setupLanguageComboBox();
            setupVoiceComboBox();
        }
    }

    private void setupSpeedSlider() {
        if (googleSpeedSlider == null || googleSpeedLabel == null || !isGoogleTTS) return;

        googleSpeedSlider.setMin(AppConstants.MIN_SPEECH_SPEED);
        googleSpeedSlider.setMax(AppConstants.MAX_SPEECH_SPEED);
        googleSpeedSlider.setValue(googleService != null ? googleService.getCurrentSpeed() : AppConstants.DEFAULT_SPEECH_SPEED);

        googleSpeedSlider.valueProperty().addListener((obs, old, val) -> {
            double speed = Math.round(val.doubleValue() * 10) / 10.0;
            googleSpeedLabel.setText(String.format("%.1fx", speed));

            if (googleService != null) {
                googleService.setSpeed((float) speed);
            }
        });
    }

    private void setupPitchSlider() {
        if (googlePitchSlider == null || pitchLabel == null || !isGoogleTTS) return;

        googlePitchSlider.setMin(AppConstants.MIN_PITCH);
        googlePitchSlider.setMax(AppConstants.MAX_PITCH);
        googlePitchSlider.setValue(googleService != null ? googleService.getCurrentPitch() : AppConstants.DEFAULT_PITCH);

        googlePitchSlider.valueProperty().addListener((obs, old, val) -> {
            double pitch = Math.round(val.doubleValue() * 10) / 10.0;
            pitchLabel.setText(String.format("%.1f", pitch));

            if (googleService != null) {
                googleService.setPitch((float) pitch);
            }
        });
    }

    private void setupVolumeSlider() {
        if (googleVolumeSlider == null || volumeLabel == null || !isGoogleTTS) return;

        googleVolumeSlider.setMin(AppConstants.MIN_VOLUME_DB);
        googleVolumeSlider.setMax(AppConstants.MAX_VOLUME_DB);
        googleVolumeSlider.setValue(googleService != null ? googleService.getCurrentVolumeGainDb() : AppConstants.DEFAULT_VOLUME_DB);

        googleVolumeSlider.valueProperty().addListener((obs, old, val) -> {
            int volume = val.intValue();
            volumeLabel.setText(volume + " дБ");

            if (googleService != null) {
                googleService.setVolumeGainDb(volume);
            }
        });
    }

    private void setupLanguageComboBox() {
        if (googleLanguageComboBox == null || !isGoogleTTS) return;

        googleLanguageComboBox.setOnAction(event -> {
            String selected = googleLanguageComboBox.getValue();
            if (selected != null && googleService != null) {
                String languageCode = selected.split(" - ")[0];
                googleService.setLanguage(languageCode);
                updateVoicesForLanguage(languageCode);
            }
        });
    }

    private void setupVoiceComboBox() {
        if (googleVoiceComboBox == null || googleVoiceDescriptionLabel == null || !isGoogleTTS) return;

        googleVoiceComboBox.setOnAction(event -> {
            String selected = googleVoiceComboBox.getValue();
            if (selected != null && googleService != null) {
                googleVoiceDescriptionLabel.setText("Выбран: " + selected);

                String voiceName = selected.split(" \\(")[0];
                try {
                    GoogleCloudTextToSpeechService.GoogleVoice voice =
                            GoogleCloudTextToSpeechService.GoogleVoice.valueOf(voiceName);
                    googleService.setVoice(voice);
                } catch (IllegalArgumentException e) {
                    logger.warn("Неизвестный голос Google TTS: {}", voiceName);
                }
            }
        });
    }

    public void updateUI() {
        Platform.runLater(() -> {
            if (isGoogleTTS && googleService != null) {
                updateGoogleTTSUI();
            } else {
                updateDemoTTSUI();
            }
            updateTTSStatus();
        });
    }

    private void updateGoogleTTSUI() {
        // Обновляем метку режима
        if (ttsModeLabel != null) {
            ttsModeLabel.setText("Google Cloud TTS");
            ttsModeLabel.setTooltip(new Tooltip("Используется Google Cloud Text-to-Speech API"));
            ttsModeLabel.setStyle("-fx-text-fill: #27ae60;");
        }

        // Показываем настройки Google Cloud TTS
        if (googleCloudSettings != null) {
            googleCloudSettings.setVisible(true);
            googleCloudSettings.setManaged(true);
        }

        // Обновляем слайдеры с текущими значениями
        if (googleService != null) {
            if (googleSpeedSlider != null) {
                googleSpeedSlider.setValue(googleService.getCurrentSpeed());
            }
            if (googlePitchSlider != null) {
                googlePitchSlider.setValue(googleService.getCurrentPitch());
            }
            if (googleVolumeSlider != null) {
                googleVolumeSlider.setValue(googleService.getCurrentVolumeGainDb());
            }
        }

        // Заполняем список языков
        if (googleLanguageComboBox != null && googleService != null) {
            googleLanguageComboBox.getItems().clear();

            // Получаем уникальные языки из доступных голосов
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

            String currentLangCode = googleService.getCurrentVoice().getLanguageCode();
            String currentLangName = AppConstants.LANGUAGE_NAMES.getOrDefault(currentLangCode, currentLangCode);
            String currentLangItem = currentLangCode + " - " + currentLangName;

            if (googleLanguageComboBox.getItems().contains(currentLangItem)) {
                googleLanguageComboBox.setValue(currentLangItem);
            } else if (!googleLanguageComboBox.getItems().isEmpty()) {
                googleLanguageComboBox.setValue(googleLanguageComboBox.getItems().get(0));
            }

            updateVoicesForLanguage(currentLangCode);
        }

        // Обновляем информацию в текстовом поле
        if (ttsInfoTextArea != null && googleService != null) {
            StringBuilder info = new StringBuilder();
            info.append("✅ GOOGLE CLOUD TTS АКТИВИРОВАН\n");
            info.append("================================\n\n");
            info.append("📊 ТЕКУЩАЯ КОНФИГУРАЦИЯ:\n");
            info.append("• Голос: ").append(googleService.getCurrentVoice().getDescription()).append("\n");
            info.append("• Язык: ").append(googleService.getCurrentLanguage()).append("\n");
            info.append("• Скорость: ").append(googleService.getCurrentSpeed()).append("x\n");
            info.append("• Тон: ").append(googleService.getCurrentPitch()).append("\n");
            info.append("• Громкость: ").append(googleService.getCurrentVolumeGainDb()).append(" дБ\n");
            info.append("• Метод аутентификации: ").append(googleService.getAuthMethod()).append("\n\n");

            info.append("🔧 ТЕХНИЧЕСКАЯ ИНФОРМАЦИЯ:\n");
            info.append("• Тип: WaveNet (нейросетевой синтез)\n");
            info.append("• Качество: Премиум\n");
            info.append("• Лимит: 5000 байт на запрос\n");

            ttsInfoTextArea.setText(info.toString());
        }
    }

    private void updateDemoTTSUI() {
        // Обновляем метку режима
        if (ttsModeLabel != null) {
            ttsModeLabel.setText("Демо-режим TTS");
            ttsModeLabel.setTooltip(new Tooltip("Используется демо-режим без реальной озвучки"));
            ttsModeLabel.setStyle("-fx-text-fill: #f39c12;");
        }

        // Скрываем настройки Google Cloud TTS для демо-режима
        if (googleCloudSettings != null) {
            googleCloudSettings.setVisible(false);
            googleCloudSettings.setManaged(false);
        }

        // Деактивируем слайдеры
        if (googleSpeedSlider != null) googleSpeedSlider.setDisable(true);
        if (googlePitchSlider != null) googlePitchSlider.setDisable(true);
        if (googleVolumeSlider != null) googleVolumeSlider.setDisable(true);
        if (googleLanguageComboBox != null) googleLanguageComboBox.setDisable(true);
        if (googleVoiceComboBox != null) googleVoiceComboBox.setDisable(true);

        // Обновляем информацию в текстовом поле для демо-режима
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
        if (!isGoogleTTS || googleVoiceComboBox == null || googleService == null) return;

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
                // Выбираем текущий голос или первый в списке
                String currentVoiceName = googleService.getCurrentVoice().name();
                String currentVoiceItem = googleVoiceComboBox.getItems().stream()
                        .filter(item -> item.startsWith(currentVoiceName))
                        .findFirst()
                        .orElse(googleVoiceComboBox.getItems().get(0));

                googleVoiceComboBox.setValue(currentVoiceItem);
            }
        });
    }

    public void updateTTSStatus() {
        if (ttsService == null || ttsStatusIndicator == null || ttsStatusLabel == null) return;

        Platform.runLater(() -> {
            boolean isAvailable = ttsService.isAvailable();

            if (isGoogleTTS) {
                if (isAvailable) {
                    ttsStatusIndicator.setFill(AppConstants.TTS_STATUS_AVAILABLE);
                    ttsStatusLabel.setText("✅ Google Cloud TTS");
                    ttsStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                } else {
                    ttsStatusIndicator.setFill(AppConstants.TTS_STATUS_UNAVAILABLE);
                    ttsStatusLabel.setText("❌ Google Cloud TTS");
                    ttsStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                }
            } else {
                ttsStatusIndicator.setFill(Color.ORANGE);
                ttsStatusLabel.setText("⚠️ Демо-режим");
                ttsStatusLabel.setStyle("-fx-text-fill: #f39c12;");
            }
        });
    }

    public boolean isTtsAvailable() {
        return ttsService != null && ttsService.isAvailable();
    }

    public boolean isGoogleTTS() {
        return isGoogleTTS;
    }

    public ITTSService getTtsService() {
        return ttsService;
    }
}