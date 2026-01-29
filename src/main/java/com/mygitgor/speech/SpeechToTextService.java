package com.mygitgor.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class SpeechToTextService {
    private static final Logger logger = LoggerFactory.getLogger(SpeechToTextService.class);

    // Конфигурация (можно вынести в properties)
    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String GOOGLE_SPEECH_URL = "https://speech.googleapis.com/v1/speech:recognize";

    private String apiKey;
    private ServiceType serviceType;

    public enum ServiceType {
        WHISPER,    // OpenAI Whisper
        GOOGLE,     // Google Speech-to-Text
        VOSK,       // Offline Vosk
        MOCK        // Для тестов
    }

    public SpeechToTextService(ServiceType serviceType, String apiKey) {
        this.serviceType = serviceType;
        this.apiKey = apiKey;
        logger.info("Инициализирован сервис распознавания речи: {}", serviceType);
    }

    /**
     * Основной метод распознавания речи
     */
    public SpeechRecognitionResult transcribe(String audioFilePath) {
        logger.info("Начинается распознавание речи из файла: {}", audioFilePath);

        try {
            switch (serviceType) {
                case WHISPER:
                    return transcribeWithWhisper(audioFilePath);

                case GOOGLE:
                    return transcribeWithGoogle(audioFilePath);

                case VOSK:
                    return transcribeWithVosk(audioFilePath);

                case MOCK:
                default:
                    return mockTranscription(audioFilePath);
            }
        } catch (Exception e) {
            logger.error("Ошибка при распознавании речи", e);
            return new SpeechRecognitionResult("", 0.0, "Ошибка: " + e.getMessage());
        }
    }

    /**
     * Распознавание с помощью OpenAI Whisper
     */
    private SpeechRecognitionResult transcribeWithWhisper(String audioFilePath) throws IOException {
        // Для MVP - эмуляция
        // В реальном приложении нужно сделать HTTP запрос к Whisper API

        logger.debug("Используется Whisper API для файла: {}", audioFilePath);

        // Эмуляция запроса
        String text = simulateTranscription(audioFilePath);
        double confidence = 0.8 + Math.random() * 0.15;

        return new SpeechRecognitionResult(text, confidence, "Распознано с помощью Whisper");
    }

    /**
     * Распознавание с помощью Google Speech-to-Text
     */
    private SpeechRecognitionResult transcribeWithGoogle(String audioFilePath) throws IOException {
        logger.debug("Используется Google Speech API для файла: {}", audioFilePath);

        // Эмуляция
        String text = simulateTranscription(audioFilePath);
        double confidence = 0.85 + Math.random() * 0.1;

        return new SpeechRecognitionResult(text, confidence, "Распознано с помощью Google");
    }

    /**
     * Распознавание с помощью Vosk (оффлайн)
     */
    private SpeechRecognitionResult transcribeWithVosk(String audioFilePath) {
        logger.debug("Используется Vosk для файла: {}", audioFilePath);

        try {
            // В реальном приложении:
            // 1. Загрузить модель Vosk
            // 2. Обработать аудио через модель
            // 3. Получить результат

            String text = simulateTranscription(audioFilePath);
            double confidence = 0.75 + Math.random() * 0.2;

            return new SpeechRecognitionResult(text, confidence, "Распознано с помощью Vosk (оффлайн)");

        } catch (Exception e) {
            logger.error("Ошибка Vosk распознавания", e);
            return new SpeechRecognitionResult("", 0.0, "Vosk error: " + e.getMessage());
        }
    }

    /**
     * Мок-распознавание для тестов
     */
    private SpeechRecognitionResult mockTranscription(String audioFilePath) {
        logger.debug("Используется мок-распознавание для файла: {}", audioFilePath);

        // Чтение текста из сопутствующего файла или генерация
        String text = generateMockTranscription(audioFilePath);
        double confidence = 0.9;

        return new SpeechRecognitionResult(text, confidence, "Тестовое распознавание");
    }

    /**
     * Эмуляция распознавания речи
     */
    private String simulateTranscription(String audioFilePath) {
        // В реальном приложении здесь будет вызов API
        // Для демонстрации возвращаем фиктивный текст

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

        // Используем имя файла как seed для детерминированности
        int hash = audioFilePath.hashCode();
        int index = Math.abs(hash) % sampleTexts.length;

        return sampleTexts[index];
    }

    /**
     * Генерация мок-транскрипции
     */
    private String generateMockTranscription(String audioFilePath) {
        // Генерация текста на основе имени файла
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

    /**
     * Конвертация аудио в Base64 для API запросов
     */
    private String audioToBase64(String audioFilePath) throws IOException {
        byte[] audioBytes = Files.readAllBytes(Paths.get(audioFilePath));
        return Base64.getEncoder().encodeToString(audioBytes);
    }

    /**
     * Создание запроса к Whisper API
     */
    private String createWhisperRequest(String base64Audio) {
        // Формирование JSON запроса для Whisper API
        return String.format("""
            {
                "model": "whisper-1",
                "file": "data:audio/wav;base64,%s",
                "language": "en",
                "response_format": "json"
            }
            """, base64Audio);
    }

    /**
     * Создание запроса к Google Speech API
     */
    private String createGoogleRequest(String base64Audio) {
        return String.format("""
            {
                "config": {
                    "encoding": "LINEAR16",
                    "sampleRateHertz": 44100,
                    "languageCode": "en-US",
                    "enableAutomaticPunctuation": true
                },
                "audio": {
                    "content": "%s"
                }
            }
            """, base64Audio);
    }

    /**
     * Выполнение HTTP запроса
     */
    private String executeHttpRequest(String url, String requestBody, String apiKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } else {
            throw new IOException("HTTP error: " + responseCode);
        }
    }

    /**
     * Результат распознавания речи
     */
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
            return String.format("Text: '%s' (Confidence: %.2f%%)",
                    text, confidence * 100);
        }
    }

    /**
     * Тестирование сервиса
     */
    public static void main(String[] args) {
        // Пример использования
        SpeechToTextService service = new SpeechToTextService(
                ServiceType.MOCK,
                "test-api-key"
        );

        SpeechRecognitionResult result = service.transcribe("test_audio.wav");
        System.out.println("Результат распознавания: " + result);
    }
}
