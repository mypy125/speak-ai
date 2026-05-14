package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String ENV_FILE_NAME = ".env";

    public static Properties loadConfig() {
        Properties properties = new Properties();

        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                properties.load(is);
                logger.debug("Загружена базовая конфигурация из application.properties");
            }
        } catch (IOException e) {
            logger.warn("Не удалось прочитать application.properties, используем только переменные окружения");
        }

        File envFile = new File(ENV_FILE_NAME);
        if (envFile.exists()) {
            logger.info("Обнаружен локальный файл .env, загружаем параметры...");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(envFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    int delimiterIdx = line.indexOf('=');
                    if (delimiterIdx == -1) continue;

                    String envKey = line.substring(0, delimiterIdx).trim();
                    String envValue = line.substring(delimiterIdx + 1).trim();

                    if ((envValue.startsWith("\"") && envValue.endsWith("\"")) ||
                            (envValue.startsWith("'") && envValue.endsWith("'"))) {
                        envValue = envValue.substring(1, envValue.length() - 1);
                    }

                    if (!envValue.isEmpty()) {
                        String propKey = convertEnvKeyToPropertyKey(envKey);
                        properties.setProperty(propKey, envValue);
                    }
                }
            } catch (IOException e) {
                logger.error("Ошибка при чтении файла .env", e);
            }
        }

        overrideFromSystemEnvironment(properties);

        return properties;
    }

    private static String convertEnvKeyToPropertyKey(String envKey) {
        return envKey.toLowerCase().replace('_', '.');
    }

    private static void overrideFromSystemEnvironment(Properties properties) {
        String[] targetKeys = {
                "AI_PROVIDER", "AI_API_KEY", "AI_MODEL", "AI_CUSTOM_URL",
                "AI_TEMPERATURE", "AI_MAX_TOKENS", "SPEECH_SERVICE_TYPE",
                "SPEECH_DEFAULT_LANGUAGE", "SPEECH_API_KEY", "TTS_TYPE"
        };

        for (String key : targetKeys) {
            String systemValue = System.getenv(key);
            if (systemValue != null && !systemValue.trim().isEmpty()) {
                String propKey = convertEnvKeyToPropertyKey(key);
                properties.setProperty(propKey, systemValue.trim());
            }
        }
    }
}
