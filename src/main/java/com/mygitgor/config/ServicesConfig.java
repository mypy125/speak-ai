package com.mygitgor.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicesConfig {
    private static final Logger logger = LoggerFactory.getLogger(ServicesConfig.class);

    private final Properties properties;

    private ServicesConfig(Properties properties) {
        this.properties = properties;
    }

    public static ServicesConfig load() {
        Properties props = new Properties();
        try (InputStream input = ServicesConfig.class.getResourceAsStream(
                AppConstants.APPLICATION_PROPERTIES_PATH)) {

            if (input != null) {
                props.load(input);
                logger.info("✅ Конфигурация загружена из {}", AppConstants.APPLICATION_PROPERTIES_PATH);
            } else {
                logger.warn("⚠️ Файл конфигурации не найден, используются значения по умолчанию");
                setDefaultProperties(props);
            }
        } catch (IOException e) {
            logger.error("❌ Ошибка загрузки конфигурации", e);
            setDefaultProperties(props);
        }

        return new ServicesConfig(props);
    }

    private static void setDefaultProperties(Properties props) {
        props.setProperty("tts.type", "GOOGLE");
        props.setProperty("ai.service.type", "MOCK");
        props.setProperty("speech.service.type", "MOCK");
        props.setProperty("speech.language", "en-US");
    }

    public String getTtsType() {
        return properties.getProperty("tts.type", "GOOGLE");
    }

    public String getAiServiceType() {
        return properties.getProperty("ai.service.type", "MOCK");
    }

    public String getSpeechServiceType() {
        return properties.getProperty("speech.service.type", "MOCK");
    }

    public String getSpeechLanguage() {
        return properties.getProperty("speech.language", "en-US");
    }

    public String getGoogleCredentialsPath() {
        return properties.getProperty("google.credentials.path", "");
    }

    public String getGoogleApiKey() {
        return properties.getProperty("google.cloud.api.key", "");
    }

    public boolean isGoogleTtsEnabled() {
        return "GOOGLE".equalsIgnoreCase(getTtsType());
    }

    public Properties getRawProperties() {
        return (Properties) properties.clone();
    }
}
