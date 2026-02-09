package com.mygitgor.speech.tovoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class TextToSpeechFactory {
    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechFactory.class);

    public static TextToSpeechService createService(Properties props) {
        String ttsType = props.getProperty("tts.type", "DEMO").toUpperCase();
        String apiKey = props.getProperty("openai.api.key", "");

        logger.info("Создание TTS сервиса типа: {}", ttsType);

        switch (ttsType) {
            case "OPENAI":
                if (apiKey != null && !apiKey.trim().isEmpty() &&
                        !apiKey.equals("your-openai-api-key-here")) {
                    try {
                        OpenAITextToSpeechService service = new OpenAITextToSpeechService(apiKey);
                        if (service.isAvailable()) {
                            logger.info("✅ OpenAI TTS Service создан");
                            return service;
                        }
                    } catch (Exception e) {
                        logger.warn("Не удалось создать OpenAI TTS: {}", e.getMessage());
                    }
                }
                logger.warn("OpenAI TTS недоступен, используем демо  режим");
                return new DemoTextToSpeechService();

            case "DEMO":
            default:
                logger.info("🔄 Используется Demo TTS Service");
                return new DemoTextToSpeechService();
        }
    }
}