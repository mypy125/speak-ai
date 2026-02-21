package com.mygitgor.service.components;

import com.mygitgor.config.AppConstants;
import com.mygitgor.service.TextCleanerService;
import com.mygitgor.service.SpeechToTextService;
import com.mygitgor.speech.SpeechRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Properties;

public class SpeechServiceManager {
    private static final Logger logger = LoggerFactory.getLogger(SpeechServiceManager.class);

    private final SpeechToTextService speechToTextService;
    private final TextCleanerService textCleaner;
    private final SpeechRecorder speechRecorder;

    public SpeechServiceManager() {
        this.speechToTextService = createSpeechToTextService();
        this.textCleaner = new TextCleanerService();
        this.speechRecorder = new SpeechRecorder();
    }

    public SpeechServiceManager(SpeechRecorder speechRecorder) {
        this.speechToTextService = createSpeechToTextService();
        this.textCleaner = new TextCleanerService();
        this.speechRecorder = speechRecorder;
    }

    private SpeechToTextService createSpeechToTextService() {
        try {
            Properties props = new Properties();
            props.load(getClass().getResourceAsStream("/application.properties"));

            String serviceTypeStr = props.getProperty("speech.service.type", "MOCK");
            String apiKey = props.getProperty("speech.api.key", "");
            String defaultLanguage = props.getProperty("speech.default.language", "en");

            SpeechToTextService.ServiceType serviceType;
            try {
                serviceType = SpeechToTextService.ServiceType.valueOf(serviceTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Неизвестный тип сервиса: {}, используем MOCK", serviceTypeStr);
                serviceType = SpeechToTextService.ServiceType.MOCK;
            }

            logger.info("Создан SpeechToTextService типа: {}, язык: {}", serviceType, defaultLanguage);
            return new SpeechToTextService(serviceType, apiKey, defaultLanguage);

        } catch (Exception e) {
            logger.error("Ошибка создания SpeechToTextService, используем MOCK", e);
            return new SpeechToTextService(SpeechToTextService.ServiceType.MOCK, "", "en");
        }
    }

    public String recognizeSpeechInRealTime() {
        SpeechRecorder recorder = this.speechRecorder;

        try {
            logger.info("Начало распознавания речи в реальном времени...");

            String tempFilePath = generateAudioFileName();

            recorder.startRecording();
            logger.info("Запись начата... Говорите...");

            Thread.sleep(AppConstants.RECORDING_DURATION_MS);

            File audioFile = recorder.stopRecording(tempFilePath);

            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                SpeechToTextService.SpeechRecognitionResult result =
                        speechToTextService.transcribe(audioFile.getAbsolutePath());

                String recognizedText = result.getText();
                logger.info("Распознано: {}, уверенность: {:.1f}%",
                        recognizedText, result.getConfidence() * 100);

                // Удаляем временный файл
                audioFile.delete();

                return recognizedText;
            }

            return "";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Распознавание прервано", e);
        } catch (Exception e) {
            logger.error("Ошибка распознавания", e);
            throw new RuntimeException("Ошибка распознавания речи", e);
        }
    }

    public SpeechToTextService.SpeechRecognitionResult transcribe(String audioFilePath) {
        return speechToTextService.transcribe(audioFilePath);
    }

    public void testMicrophone(int durationSeconds) {
        speechToTextService.testMicrophone(durationSeconds);
    }

    public void setMicrophoneSensitivity(double sensitivity) {
        speechToTextService.setMicrophoneSensitivity(sensitivity);
    }

    public double getMicrophoneSensitivity() {
        return speechToTextService.getMicrophoneSensitivity();
    }

    public void switchSpeechLanguage(String languageCode) {
        if (speechToTextService.getServiceType() == SpeechToTextService.ServiceType.VOSK) {
            speechToTextService.switchLanguage(languageCode);
            logger.info("Язык изменен на: {}", speechToTextService.getCurrentLanguageName());
        } else {
            logger.warn("Смена языка поддерживается только для Vosk");
        }
    }

    public List<String> getSupportedLanguages() {
        return speechToTextService.getSupportedLanguages();
    }

    public Map<String, String> getSupportedLanguagesWithNames() {
        return speechToTextService.getSupportedLanguagesWithNames();
    }

    public String getCurrentSpeechLanguage() {
        return speechToTextService.getCurrentLanguage();
    }

    public String getCurrentSpeechLanguageName() {
        return speechToTextService.getCurrentLanguageName();
    }

    public String generateAudioFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());

        File recordingsDir = new File(AppConstants.AUDIO_DIRECTORY);
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs();
        }

        return AppConstants.AUDIO_DIRECTORY + "/audio_" + timestamp + ".wav";
    }

    public SpeechToTextService getService() {
        return speechToTextService;
    }
}
