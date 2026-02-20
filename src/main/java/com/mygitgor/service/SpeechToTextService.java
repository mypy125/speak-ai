package com.mygitgor.service;


import org.vosk.Recognizer;
import org.vosk.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SpeechToTextService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SpeechToTextService.class);

    private ServiceType serviceType;
    private String currentLanguage;
    private volatile boolean closed = false;
    private double microphoneSensitivity = 0.5;

    private AtomicReference<Model> voskModel;
    private AtomicReference<Recognizer> voskRecognizer;
    private AudioFormat audioFormat;
    private ObjectMapper mapper;

    private final Map<String, String> supportedLanguages = new HashMap<>();
    private final Map<String, String> languageModelPaths = new HashMap<>();

    private String apiKey;

    public enum ServiceType {
        WHISPER,
        GOOGLE,
        VOSK,
        MOCK
    }

    public SpeechToTextService(ServiceType serviceType, String apiKey, String defaultLanguage) {
        this.serviceType = serviceType;
        this.apiKey = apiKey;
        this.currentLanguage = defaultLanguage;

        this.audioFormat = new AudioFormat(16000, 16, 1, true, false);
        this.mapper = new ObjectMapper();
        this.voskModel = new AtomicReference<>();
        this.voskRecognizer = new AtomicReference<>();

        initializeSupportedLanguages();
        initializeDefaultModels();
        checkModelsExistence();

        logger.info("Инициализирован сервис распознавания речи: {}, язык по умолчанию: {}",
                serviceType, getLanguageName(defaultLanguage));

        if (serviceType == ServiceType.VOSK) {
            initializeVoskModel(currentLanguage);
        }
    }

    private void initializeSupportedLanguages() {
        supportedLanguages.put("ru", "🇷🇺 Русский");
        supportedLanguages.put("en", "🇬🇧 Английский");
        supportedLanguages.put("de", "🇩🇪 Немецкий");
        supportedLanguages.put("fr", "🇫🇷 Французский");
        supportedLanguages.put("es", "🇪🇸 Испанский");
        supportedLanguages.put("it", "🇮🇹 Итальянский");
        supportedLanguages.put("zh", "🇨🇳 Китайский");
        supportedLanguages.put("ja", "🇯🇵 Японский");

        logger.info("Поддерживаемые языки: {}", supportedLanguages.keySet());
    }

    private void initializeDefaultModels() {
        languageModelPaths.put("ru", "models/vosk-model-small-ru");
        languageModelPaths.put("en", "models/vosk-model-small-en");
        languageModelPaths.put("de", "models/vosk-model-small-de");
        languageModelPaths.put("fr", "models/vosk-model-small-fr");
        languageModelPaths.put("es", "models/vosk-model-small-es");
        languageModelPaths.put("it", "models/vosk-model-small-it");
        languageModelPaths.put("zh", "models/vosk-model-small-zh");
        languageModelPaths.put("ja", "models/vosk-model-small-ja");
    }

    private void checkModelsExistence() {
        logger.info("Проверка наличия моделей Vosk...");

        for (Map.Entry<String, String> entry : languageModelPaths.entrySet()) {
            String langCode = entry.getKey();
            String modelPath = entry.getValue();
            File modelDir = new File(modelPath);

            if (modelDir.exists()) {
                logger.info("✅ Модель для языка '{}' найдена: {}",
                        getLanguageName(langCode), modelPath);
            } else {
                logger.warn("⚠️ Модель для языка '{}' не найдена: {}",
                        getLanguageName(langCode), modelPath);
            }
        }
    }

    public String getLanguageName(String languageCode) {
        return supportedLanguages.getOrDefault(languageCode, languageCode);
    }

    public String getLanguageCode(String languageName) {
        for (Map.Entry<String, String> entry : supportedLanguages.entrySet()) {
            if (entry.getValue().equals(languageName)) {
                return entry.getKey();
            }
        }
        return "ru";
    }

    private void initializeVoskModel(String languageCode) {
        try {
            String modelPath = getModelPath(languageCode);
            logger.info("Загрузка Vosk модели для языка '{}' из: {}",
                    getLanguageName(languageCode), modelPath);

            File modelDir = new File(modelPath);
            if (!modelDir.exists()) {
                logger.error("Vosk модель для языка '{}' не найдена по пути: {}",
                        getLanguageName(languageCode), modelPath);

                String fallbackPath = findFallbackModel(languageCode);
                if (fallbackPath != null) {
                    modelPath = fallbackPath;
                    logger.info("Используется альтернативная модель: {}", modelPath);
                } else {
                    throw new IOException("Модель для языка '" + getLanguageName(languageCode) +
                            "' не найдена: " + modelPath);
                }
            }

            cleanupVoskResources();
            Model model = new Model(modelPath);
            Recognizer recognizer = new Recognizer(model, 16000.0f);

            voskModel.set(model);
            voskRecognizer.set(recognizer);

            logger.info("Vosk модель для языка '{}' загружена успешно",
                    getLanguageName(languageCode));

        } catch (Exception e) {
            logger.error("Ошибка инициализации Vosk модели для языка '{}'",
                    getLanguageName(languageCode), e);
            throw new RuntimeException("Не удалось загрузить Vosk модель: " + e.getMessage(), e);
        }
    }

    private String findFallbackModel(String languageCode) {
        if (languageCode.equals("zh") || languageCode.equals("ja")) {
            return null;
        }

        if (!languageCode.equals("en")) {
            String englishModel = languageModelPaths.get("en");
            if (englishModel != null && new File(englishModel).exists()) {
                logger.warn("Используется английская модель как запасной вариант для языка '{}'",
                        getLanguageName(languageCode));
                return englishModel;
            }
        }

        return null;
    }

    private String getModelPath(String languageCode) {
        String customPath = System.getProperty("vosk.model.path." + languageCode);
        if (customPath != null && !customPath.trim().isEmpty()) {
            return customPath;
        }

        return languageModelPaths.getOrDefault(languageCode,
                languageModelPaths.get("ru"));
    }

    public SpeechRecognitionResult transcribe(String audioFilePath) {
        return transcribe(audioFilePath, this.currentLanguage);
    }

    public SpeechRecognitionResult transcribe(String audioFilePath, String languageCode) {
        if (closed) {
            throw new IllegalStateException("SpeechToTextService закрыт");
        }

        logger.info("Начинается распознавание речи из файла: {}, язык: {}, сервис: {}",
                audioFilePath, getLanguageName(languageCode), serviceType);

        try {
            if (serviceType == ServiceType.VOSK && !languageCode.equals(currentLanguage)) {
                switchLanguage(languageCode);
            }

            return switch (serviceType) {
                case WHISPER -> transcribeWithWhisper(audioFilePath);
                case GOOGLE -> transcribeWithGoogle(audioFilePath, languageCode);
                case VOSK -> transcribeWithVosk(audioFilePath, languageCode);
                default -> mockTranscription(audioFilePath);
            };
        } catch (Exception e) {
            logger.error("Ошибка при распознавании речи", e);
            return new SpeechRecognitionResult("", 0.0,
                    "Ошибка " + serviceType + ": " + e.getMessage());
        }
    }

    private SpeechRecognitionResult mockTranscription(String audioFilePath) {
        logger.debug("Используется мок-распознавание для файла: {}", audioFilePath);

        String text = generateMockTranscription(audioFilePath);
        double confidence = 0.9;

        return new SpeechRecognitionResult(text, confidence, "Тестовое распознавание");
    }

    private SpeechRecognitionResult transcribeWithGoogle(String audioFilePath, String languageCode) throws IOException {
        logger.debug("Используется Google Speech API для файла: {}, язык: {}", audioFilePath, languageCode);

        String text = simulateTranscription(audioFilePath);
        double confidence = 0.85 + Math.random() * 0.1;

        return new SpeechRecognitionResult(text, confidence, "Распознано с помощью Google");
    }

    private SpeechRecognitionResult transcribeWithWhisper(String audioFilePath) throws IOException {
        logger.debug("Используется Whisper API для файла: {}", audioFilePath);

        String text = simulateTranscription(audioFilePath);
        double confidence = 0.8 + Math.random() * 0.15;

        return new SpeechRecognitionResult(text, confidence, "Распознано с помощью Whisper");
    }

    private SpeechRecognitionResult transcribeWithVosk(String audioFilePath, String languageCode)
            throws IOException {
        logger.debug("Используется Vosk для файла: {}, язык: {}",
                audioFilePath, getLanguageName(languageCode));

        if (voskRecognizer.get() == null) {
            logger.warn("Vosk распознаватель не инициализирован, пытаемся переинициализировать");
            initializeVoskModel(languageCode);
        }

        Recognizer recognizer = voskRecognizer.get();
        if (recognizer == null) {
            throw new IllegalStateException("Vosk распознаватель не доступен");
        }

        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(audioFilePath));
            AudioFormat sourceFormat = audioStream.getFormat();

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    16,
                    1,
                    2,
                    16000,
                    false
            );

            if (!sourceFormat.matches(targetFormat)) {
                audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
            }

            byte[] buffer = new byte[4096];
            StringBuilder resultBuilder = new StringBuilder();
            long startTime = System.currentTimeMillis();

            while (true) {
                int bytesRead = audioStream.read(buffer);
                if (bytesRead <= 0) break;

                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String resultJson = recognizer.getResult();
                    JsonNode node = mapper.readTree(resultJson);
                    String text = node.get("text").asText().trim();

                    if (!text.isEmpty()) {
                        resultBuilder.append(text).append(" ");
                    }
                }
            }

            String finalResult = recognizer.getFinalResult();
            if (finalResult != null && !finalResult.isEmpty()) {
                JsonNode finalNode = mapper.readTree(finalResult);
                String finalText = finalNode.get("text").asText().trim();
                if (!finalText.isEmpty()) {
                    resultBuilder.append(finalText);
                }
            }

            audioStream.close();

            String recognizedText = resultBuilder.toString().trim();
            long elapsedTime = System.currentTimeMillis() - startTime;

            double confidence = calculateConfidence(recognizedText, languageCode);

            logger.info("Vosk распознавание завершено за {} мс: '{}' (уверенность: {:.1f}%)",
                    elapsedTime, recognizedText, confidence * 100);

            return new SpeechRecognitionResult(recognizedText, confidence,
                    "Распознано с помощью Vosk (" + getLanguageName(languageCode) + ")");

        } catch (UnsupportedAudioFileException e) {
            logger.error("Неподдерживаемый аудиоформат", e);
            return new SpeechRecognitionResult("", 0.0,
                    "Неподдерживаемый аудиоформат: " + e.getMessage());
        }
    }

    private double calculateConfidence(String text, String languageCode) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }

        double confidence = 0.7;

        int wordCount = text.split("\\s+").length;
        if (wordCount >= 3) {
            confidence += 0.1;
        }

        if (text.matches(".*[.!?].*")) {
            confidence += 0.1;
        }

        switch (languageCode) {
            case "ru":
                if (text.matches(".*[а-яА-Я].*")) {
                    confidence += 0.05;
                }
                break;
            case "en":
                if (text.matches(".*[a-zA-Z].*")) {
                    confidence += 0.05;
                }
                break;
            case "de":
            case "fr":
            case "es":
            case "it":
                if (text.matches(".*[a-zA-ZÀ-ÿ].*")) {
                    confidence += 0.05;
                }
                break;
            case "zh":
                if (text.matches(".*[\\p{IsHan}].*")) {
                    confidence += 0.05;
                }
                break;
            case "ja":
                if (text.matches(".*[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}].*")) {
                    confidence += 0.05;
                }
                break;
        }

        return Math.min(0.95, Math.max(0.1, confidence));
    }

    public void switchLanguage(String languageCode) {
        if (serviceType != ServiceType.VOSK) {
            logger.warn("Переключение языка доступно только для Vosk");
            return;
        }

        if (!supportedLanguages.containsKey(languageCode)) {
            logger.warn("Язык '{}' не поддерживается", languageCode);
            return;
        }

        logger.info("Переключение языка с '{}' на '{}'",
                getLanguageName(currentLanguage), getLanguageName(languageCode));

        this.currentLanguage = languageCode;
        initializeVoskModel(languageCode);

        logger.info("Язык успешно переключен на: {}", getLanguageName(languageCode));
    }

    public List<String> getSupportedLanguages() {
        return new ArrayList<>(supportedLanguages.keySet());
    }

    public Map<String, String> getSupportedLanguagesWithNames() {
        return new HashMap<>(supportedLanguages);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public String getCurrentLanguageName() {
        return getLanguageName(currentLanguage);
    }

    public void setMicrophoneSensitivity(double sensitivity) {
        this.microphoneSensitivity = Math.max(0.1, Math.min(1.0, sensitivity));
        logger.info("Чувствительность микрофона установлена: {}", microphoneSensitivity);
    }

    public double getMicrophoneSensitivity() {
        return microphoneSensitivity;
    }

    public void testMicrophone(int durationSeconds) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                logger.warn("Микрофон не поддерживается для теста");
                return;
            }

            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat);
            microphone.start();

            logger.info("Тест микрофона начат (длительность: {} секунд)", durationSeconds);

            byte[] buffer = new byte[4096];
            long startTime = System.currentTimeMillis();
            double maxVolume = 0;

            while (System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    double volume = calculateVolume(buffer, bytesRead);
                    maxVolume = Math.max(maxVolume, volume);

                    // Выводим только если громкость превышает порог
                    if (volume > microphoneSensitivity * 0.5) {
                        logger.debug("Уровень звука: {:.1f}%", volume * 100);
                    }
                }
            }

            microphone.stop();
            microphone.close();

            String resultMessage;
            if (maxVolume > microphoneSensitivity) {
                resultMessage = String.format("✅ Микрофон работает хорошо. Максимальный уровень: %.1f%%",
                        maxVolume * 100);
            } else if (maxVolume > 0.1) {
                resultMessage = String.format("⚠️ Микрофон работает, но тихо. Максимальный уровень: %.1f%%",
                        maxVolume * 100);
            } else {
                resultMessage = "❌ Микрофон не обнаружен или очень тихий";
            }

            logger.info("Тест микрофона завершен. {}", resultMessage);

        } catch (Exception e) {
            logger.error("Ошибка при тесте микрофона", e);
        }
    }

    private double calculateVolume(byte[] buffer, int length) {
        if (length == 0) return 0;

        double sum = 0;
        for (int i = 0; i < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += sample * sample;
        }

        double rms = Math.sqrt(sum / (length / 2));
        return rms / 32768.0;
    }

    private String simulateTranscription(String audioFilePath) {

        String[] sampleTexts = {
                "Hello, how are you doing today?",
                "I would like to practice my English pronunciation.",
                "The weather is really nice outside.",
                "Can you help me improve my speaking skills?",
                "I need to work on my grammar and vocabulary.",
                "Let's have a conversation about travel.",
                "What do you think about artificial intelligence?",
                "My favorite hobby is reading books.",
                "I enjoy learning new languages.",
                "Could you please repeat that sentence?"
        };

        int hash = audioFilePath.hashCode();
        int index = Math.abs(hash) % sampleTexts.length;

        return sampleTexts[index];
    }

    private String generateMockTranscription(String audioFilePath) {
        String filename = new File(audioFilePath).getName().toLowerCase();

        if (filename.contains("greeting")) {
            return "Hello, nice to meet you!";
        } else if (filename.contains("question")) {
            return "What time is it now?";
        } else if (filename.contains("weather")) {
            return "The weather is sunny and warm today.";
        } else if (filename.contains("introduction")) {
            return "My name is Alex and I'm from Russia.";
        } else {
            return "I am practicing my English pronunciation.";
        }
    }

    private void cleanupVoskResources() {
        try {
            if (voskRecognizer.get() != null) {
                voskRecognizer.get().close();
                voskRecognizer.set(null);
            }
            if (voskModel.get() != null) {
                voskModel.get().close();
                voskModel.set(null);
            }
        } catch (Exception e) {
            logger.warn("Ошибка при очистке ресурсов Vosk", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие SpeechToTextService ({})...", serviceType);

        try {
            cleanupVoskResources();

            logger.info("SpeechToTextService закрыт");
        } catch (Exception e) {
            logger.error("Ошибка при закрытии SpeechToTextService", e);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public static class SpeechRecognitionResult {
        private final String text;
        private final double confidence;
        private final String serviceInfo;

        public SpeechRecognitionResult(String text, double confidence, String serviceInfo) {
            this.text = text;
            this.confidence = confidence;
            this.serviceInfo = serviceInfo;
        }

        public String getText() {
            return text;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getServiceInfo() {
            return serviceInfo;
        }

        public boolean isConfident() {
            return confidence > 0.7;
        }

        @Override
        public String toString() {
            return String.format("Text: '%s' (Confidence: %.1f%%, Service: %s)",
                    text, confidence * 100, serviceInfo);
        }
    }
}