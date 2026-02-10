package com.mygitgor.speech.TTS;

import com.mygitgor.speech.TTS.type.DemoTextToSpeechService;
import com.mygitgor.speech.TTS.type.GoogleCloudTextToSpeechService;
import com.mygitgor.speech.TTS.type.LocalTextToSpeechService;
import com.mygitgor.speech.TTS.type.OpenAITextToSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class TextToSpeechFactory {
    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechFactory.class);

    public enum TTSType {
        SMART,      // Умный выбор с автоматическим fallback
        OPENAI,     // OpenAI TTS API
        GOOGLE,     // Google Cloud TTS (WaveNet)
        GROQ,       // Groq API (для чата, но TTS не поддерживается)
        LOCAL,      // Локальный системный TTS
        DEMO,       // Демо-режим (текст в консоль)
        COMPOSITE   // Составной сервис с приоритетами
    }

    /**
     * Создает TTS сервис на основе конфигурации
     */
    public static TextToSpeechService createService(Properties props) {
        String ttsTypeStr = props.getProperty("tts.type", "SMART").toUpperCase();
        TTSType ttsType = parseTTSType(ttsTypeStr);

        logger.info("Создание TTS сервиса типа: {}", ttsType);

        return createTtsService(ttsType, props);
    }

    /**
     * Создает TTS сервис для конкретного типа
     */
    public static TextToSpeechService createTtsService(TTSType type, Properties props) {
        return switch (type) {
            case SMART -> createSmartTtsService(props);
            case OPENAI -> createOpenAITtsService(props);
            case GOOGLE -> createGoogleTtsService(props);
            case GROQ -> createGroqTtsService(props);
            case LOCAL -> createLocalTtsService(props);
            case DEMO -> createDemoTtsService(props);
            case COMPOSITE -> createCompositeTtsService(props);
            default -> {
                logger.warn("Неизвестный тип TTS: {}, используем SMART", type);
                yield createSmartTtsService(props);
            }
        };
    }

    /**
     * Умный TTS сервис с автоматическим выбором лучшего варианта
     */
    private static TextToSpeechService createSmartTtsService(Properties props) {
        logger.info("Создание Smart TTS Service...");

        // Пробуем в порядке приоритета
        TextToSpeechService[] candidates = {
                createGoogleTtsService(props),   // 1. Google TTS (WaveNet - лучшее качество)
                createOpenAITtsService(props),   // 2. OpenAI TTS (хорошее качество)
                createLocalTtsService(props),    // 3. Локальный TTS (оффлайн)
                createDemoTtsService(props)      // 4. Демо-режим (всегда доступен)
        };

        for (int i = 0; i < candidates.length; i++) {
            TextToSpeechService service = candidates[i];
            String serviceName = service.getClass().getSimpleName();

            if (service.isAvailable()) {
                logger.info("✅ Smart TTS выбрал: {} (приоритет {})", serviceName, i + 1);

                // Логируем причину выбора
                logServiceSelectionReason(service, i);

                return service;
            } else {
                logger.debug("❌ {} недоступен", serviceName);
            }
        }

        // Все кандидаты недоступны - возвращаем демо как последний вариант
        logger.warn("⚠️ Все TTS сервисы недоступны, используем демо-режим");
        return candidates[candidates.length - 1];
    }

    /**
     * Создает OpenAI TTS сервис с проверкой доступности
     */
    private static TextToSpeechService createOpenAITtsService(Properties props) {
        String apiKey = getApiKey(props, "openai.api.key", "OPENAI_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty() ||
                apiKey.equals("your-openai-api-key-here")) {
            logger.debug("OpenAI API ключ не настроен");
            return createUnavailableService("OpenAI TTS (ключ не настроен)");
        }

        try {
            OpenAITextToSpeechService service = new OpenAITextToSpeechService(apiKey);

            // Настраиваем параметры из конфигурации
            String voiceName = props.getProperty("tts.openai.voice", "alloy");
            String model = props.getProperty("tts.openai.model", "tts-1");
            String speedStr = props.getProperty("tts.openai.speed", "1.0");

            try {
                OpenAITextToSpeechService.OpenAIVoice voice =
                        OpenAITextToSpeechService.OpenAIVoice.valueOf(voiceName.toUpperCase());
                service.setVoice(voice);
            } catch (IllegalArgumentException e) {
                logger.debug("Неизвестный голос OpenAI TTS: {}, используем alloy", voiceName);
            }

            try {
                if (model.equals("tts-1-hd")) {
                    service.setModel(OpenAITextToSpeechService.TTSModel.TTS_1_HD);
                }
            } catch (Exception e) {
                logger.debug("Некорректная модель OpenAI TTS: {}", model);
            }

            try {
                float speed = Float.parseFloat(speedStr);
                if (speed >= 0.25f && speed <= 4.0f) {
                    service.setSpeed(speed);
                }
            } catch (NumberFormatException e) {
                logger.debug("Некорректная скорость OpenAI TTS: {}", speedStr);
            }

            // Проверяем доступность асинхронно, но сразу возвращаем сервис
            checkServiceAvailabilityAsync(service, "OpenAI TTS");

            return service;

        } catch (Exception e) {
            logger.error("Ошибка при создании OpenAI TTS: {}", e.getMessage());
            return createUnavailableService("OpenAI TTS (ошибка инициализации)");
        }
    }

    /**
     * Создает Google Cloud TTS сервис
     */
    private static TextToSpeechService createGoogleTtsService(Properties props) {
        String apiKey = getApiKey(props, "google.cloud.api.key", "GOOGLE_CLOUD_API_KEY");
        String credentialsFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        // Проверяем разные способы аутентификации
        boolean hasApiKey = apiKey != null && !apiKey.trim().isEmpty() &&
                !apiKey.equals("your-google-cloud-api-key-here");
        boolean hasCredentialsFile = credentialsFile != null && !credentialsFile.trim().isEmpty();

        if (!hasApiKey && !hasCredentialsFile) {
            logger.debug("Google Cloud API ключ не настроен");
            logger.debug("Способы настройки Google TTS:\n" +
                    "1. Укажите google.cloud.api.key в application.properties\n" +
                    "2. Установите переменную окружения GOOGLE_CLOUD_API_KEY\n" +
                    "3. Установите GOOGLE_APPLICATION_CREDENTIALS для файла учетных данных");
            return createUnavailableService("Google Cloud TTS (не настроен API ключ)");
        }

        try {
            GoogleCloudTextToSpeechService service;

            if (hasApiKey) {
                service = new GoogleCloudTextToSpeechService(apiKey);
                logger.debug("Используется Google Cloud API ключ");
            } else {
                service = new GoogleCloudTextToSpeechService("CREDENTIALS_FILE");
                logger.debug("Используется файл учетных данных: {}", credentialsFile);
            }

            // Настраиваем параметры из конфигурации
            String voiceName = props.getProperty("tts.google.voice", "EN_US_WAVENET_B");
            String speedStr = props.getProperty("tts.google.speed", "1.0");
            String pitchStr = props.getProperty("tts.google.pitch", "0.0");
            String volumeStr = props.getProperty("tts.google.volume", "0.0");

            try {
                GoogleCloudTextToSpeechService.GoogleVoice voice =
                        GoogleCloudTextToSpeechService.GoogleVoice.valueOf(voiceName);
                service.setVoice(voice);
            } catch (IllegalArgumentException e) {
                logger.debug("Неизвестный голос Google TTS: {}, используем стандартный", voiceName);
            }

            try {
                float speed = Float.parseFloat(speedStr);
                if (speed >= 0.25f && speed <= 4.0f) {
                    service.setSpeed(speed);
                }
            } catch (NumberFormatException e) {
                logger.debug("Некорректная скорость Google TTS: {}", speedStr);
            }

            try {
                float pitch = Float.parseFloat(pitchStr);
                if (pitch >= -20.0f && pitch <= 20.0f) {
                    service.setPitch(pitch);
                }
            } catch (NumberFormatException e) {
                logger.debug("Некорректный тон Google TTS: {}", pitchStr);
            }

            try {
                float volume = Float.parseFloat(volumeStr);
                if (volume >= -96.0f && volume <= 16.0f) {
                    service.setVolume(volume);
                }
            } catch (NumberFormatException e) {
                logger.debug("Некорректная громкость Google TTS: {}", volumeStr);
            }

            // Проверяем доступность асинхронно
            checkServiceAvailabilityAsync(service, "Google Cloud TTS");

            logger.info("✅ Google Cloud TTS Service создан (WaveNet technology)");

            return service;

        } catch (Exception e) {
            logger.error("Ошибка при создании Google Cloud TTS: {}", e.getMessage());
            return createUnavailableService("Google Cloud TTS (ошибка инициализации)");
        }
    }

    /**
     * Создает Groq TTS сервис (напоминание: Groq не поддерживает TTS API!)
     */
    private static TextToSpeechService createGroqTtsService(Properties props) {
        logger.warn("⚠️ ВНИМАНИЕ: Groq не поддерживает TTS API");
        logger.info("Используем обходное решение: Groq для чата + другие TTS сервисы");

        String apiKey = getApiKey(props, "groq.api.key", "GROQ_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.debug("Groq API ключ не настроен");
        }

        // Создаем умный сервис как fallback (пропускаем Groq в приоритетах)
        return createSmartTtsServiceWithoutGroq(props);
    }

    /**
     * Умный TTS сервис без Groq (чтобы избежать рекурсии)
     */
    private static TextToSpeechService createSmartTtsServiceWithoutGroq(Properties props) {
        logger.info("Создание Smart TTS Service (без Groq)...");

        // Пробуем в порядке приоритета, без Groq
        TextToSpeechService[] candidates = {
                createGoogleTtsService(props),   // 1. Google TTS
                createOpenAITtsService(props),   // 2. OpenAI TTS
                createLocalTtsService(props),    // 3. Локальный TTS
                createDemoTtsService(props)      // 4. Демо-режим
        };

        for (int i = 0; i < candidates.length; i++) {
            TextToSpeechService service = candidates[i];
            String serviceName = service.getClass().getSimpleName();

            if (service.isAvailable()) {
                logger.info("✅ Smart TTS (без Groq) выбрал: {} (приоритет {})", serviceName, i + 1);
                return service;
            } else {
                logger.debug("❌ {} недоступен", serviceName);
            }
        }

        logger.warn("⚠️ Все TTS сервисы недоступны, используем демо-режим");
        return candidates[candidates.length - 1];
    }

    /**
     * Создает локальный системный TTS сервис
     */
    private static TextToSpeechService createLocalTtsService(Properties props) {
        try {
            LocalTextToSpeechService service = new LocalTextToSpeechService();

            if (service.isAvailable()) {
                // Настраиваем параметры из конфигурации
                String voice = props.getProperty("tts.local.voice", "default");
                String speedStr = props.getProperty("tts.local.speed", "1.0");

                try {
                    float speed = Float.parseFloat(speedStr);
                    if (speed < 0.5f) speed = 0.5f;
                    if (speed > 2.0f) speed = 2.0f;

                    service.setSpeed(speed);
                    logger.debug("Локальный TTS настроен: голос={}, скорость={}", voice, speed);
                } catch (NumberFormatException e) {
                    logger.debug("Некорректная скорость TTS: {}", speedStr);
                }

                logger.info("✅ Локальный TTS доступен для ОС: {}",
                        service.getOperatingSystem());
                return service;
            } else {
                logger.debug("Локальный TTS недоступен: нет системных утилит");
                return service; // Все равно возвращаем, но isAvailable() = false
            }

        } catch (Exception e) {
            logger.error("Ошибка при создании локального TTS: {}", e.getMessage());
            return createUnavailableService("Local TTS (ошибка инициализации)");
        }
    }

    /**
     * Создает демо TTS сервис (всегда доступен)
     */
    private static TextToSpeechService createDemoTtsService(Properties props) {
        DemoTextToSpeechService service = new DemoTextToSpeechService();

        // Настраиваем демо-режим из конфигурации
        String speedStr = props.getProperty("tts.demo.speed", "50");
        String mode = props.getProperty("tts.demo.mode", "CONSOLE");

        try {
            int speed = Integer.parseInt(speedStr);
            // Здесь можно настроить сервис если есть соответствующие методы
            logger.debug("Демо TTS настроен: скорость={}, режим={}", speed, mode);
        } catch (NumberFormatException e) {
            logger.debug("Некорректная скорость демо TTS: {}", speedStr);
        }

        logger.info("✅ Демо TTS создан (всегда доступен)");
        return service;
    }

    /**
     * Создает составной TTS сервис с явными приоритетами
     */
    private static TextToSpeechService createCompositeTtsService(Properties props) {
        logger.info("Создание Composite TTS Service...");

        // Читаем конфигурацию приоритетов
        String prioritiesStr = props.getProperty("tts.composite.priorities",
                "GOOGLE,OPENAI,LOCAL,DEMO");
        String[] priorities = prioritiesStr.split(",");

        for (String priority : priorities) {
            try {
                TTSType type = TTSType.valueOf(priority.trim().toUpperCase());
                TextToSpeechService service = createTtsService(type, props);

                if (service.isAvailable()) {
                    logger.info("✅ Composite TTS выбрал: {}", type);
                    return service;
                } else {
                    logger.debug("Composite TTS: {} недоступен", type);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Неизвестный тип TTS в приоритетах: {}", priority);
            }
        }

        logger.warn("⚠️ Все сервисы в Composite недоступны, используем демо");
        return createDemoTtsService(props);
    }

    /**
     * Вспомогательные методы
     */
    private static TTSType parseTTSType(String typeStr) {
        try {
            return TTSType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            // Поддержка старых значений для обратной совместимости
            return switch (typeStr) {
                case "GOOGLE" -> TTSType.GOOGLE;
                case "OPENAI" -> TTSType.OPENAI;
                case "GROQ" -> TTSType.GROQ;
                case "LOCAL" -> TTSType.LOCAL;
                case "DEMO" -> TTSType.DEMO;
                default -> TTSType.SMART;
            };
        }
    }

    private static String getApiKey(Properties props, String propKey, String envKey) {
        // 1. Проверяем свойство в файле конфигурации
        String key = props.getProperty(propKey, "").trim();

        if (!key.isEmpty() && !key.equals("your-api-key-here")) {
            return key;
        }

        // 2. Проверяем переменную окружения
        key = System.getenv(envKey);
        if (key != null && !key.trim().isEmpty()) {
            logger.debug("Используется API ключ из переменной окружения: {}", envKey);
            return key.trim();
        }

        // 3. Проверяем системные свойства
        key = System.getProperty(propKey);
        if (key != null && !key.trim().isEmpty()) {
            logger.debug("Используется API ключ из системных свойств: {}", propKey);
            return key.trim();
        }

        return null;
    }

    private static void checkServiceAvailabilityAsync(TextToSpeechService service, String serviceName) {
        // Запускаем проверку в фоне
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Даем время на инициализацию
                if (service.isAvailable()) {
                    logger.info("✅ {} проверен и доступен", serviceName);
                } else {
                    logger.warn("⚠️ {} не прошел проверку доступности", serviceName);
                }
            } catch (Exception e) {
                logger.debug("Ошибка при проверке {}: {}", serviceName, e.getMessage());
            }
        }).start();
    }

    private static void logServiceSelectionReason(TextToSpeechService service, int priority) {
        String reason = switch (priority) {
            case 0 -> "Google Cloud TTS (WaveNet) выбран как сервис с лучшим качеством звука";
            case 1 -> "OpenAI TTS выбран как качественный облачный сервис";
            case 2 -> "Локальный TTS выбран как оффлайн альтернатива";
            case 3 -> "Демо TTS выбран как последний fallback";
            default -> "Выбран по приоритету " + (priority + 1);
        };

        logger.info("📋 Причина выбора: {}", reason);

        // Дополнительная информация о сервисе
        if (service instanceof GoogleCloudTextToSpeechService googleService) {
            logger.info("   • Тип: Google Cloud TTS (WaveNet)");
            logger.info("   • Технология: Нейросетевая (премиум качество)");
            logger.info("   • Голос: {}", googleService.getCurrentVoice().getDescription());
            logger.info("   • Скорость: {}", googleService.getCurrentSpeed());
            logger.info("   • Требуется: Интернет + API ключ");
        } else if (service instanceof OpenAITextToSpeechService) {
            logger.info("   • Тип: OpenAI TTS");
            logger.info("   • Качество: Очень хорошее");
            logger.info("   • Требуется: Интернет + API ключ");
            logger.info("   • Преимущества: Натуральное звучание, много голосов");
        } else if (service instanceof LocalTextToSpeechService localService) {
            logger.info("   • Тип: Локальный (системные утилиты)");
            logger.info("   • Требуется: Установленные TTS утилиты");
            logger.info("   • ОС: {}", localService.getOperatingSystem());
            logger.info("   • Преимущества: Не требует интернета");
        } else if (service instanceof DemoTextToSpeechService) {
            logger.info("   • Тип: Демонстрационный");
            logger.info("   • Требуется: Ничего");
            logger.info("   • Режим: Вывод текста в консоль");
            logger.info("   • Преимущества: Всегда работает, идеален для тестирования");
        } else if (service.getClass().getSimpleName().contains("Groq")) {
            logger.info("   • Тип: Groq (заглушка)");
            logger.info("   • Примечание: Groq не поддерживает TTS API");
            logger.info("   • Используется: Другой TTS сервис как fallback");
        }
    }

    /**
     * Создает сервис, который всегда возвращает false в isAvailable()
     * Используется для временной замены недоступных сервисов
     */
    private static TextToSpeechService createUnavailableService(String reason) {
        return new TextToSpeechService() {
            @Override
            public CompletableFuture<Void> speakAsync(String text) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new IOException("TTS сервис недоступен: " + reason));
                return future;
            }

            @Override
            public void speak(String text) {
                throw new IllegalStateException("TTS сервис недоступен: " + reason);
            }

            @Override
            public void stopSpeaking() {
                // Ничего не делаем
            }

            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public void close() {
                // Ничего не делаем
            }

            @Override
            public java.util.Map<String, String> getAvailableVoices() {
                return java.util.Collections.emptyMap();
            }

            @Override
            public String toString() {
                return "UnavailableTTS[" + reason + "]";
            }
        };
    }

    /**
     * Утилитарный метод для получения информации о доступных TTS сервисах
     */
    public static String getTtsSystemInfo(Properties props) {
        StringBuilder info = new StringBuilder();
        info.append("=== Информация о системе TTS ===\n\n");

        // Проверяем каждый тип сервиса
        for (TTSType type : TTSType.values()) {
            info.append(type).append(":\n");

            try {
                TextToSpeechService service = createTtsService(type, props);
                boolean available = service.isAvailable();

                info.append("  Статус: ").append(available ? "✅ Доступен" : "❌ Недоступен").append("\n");
                info.append("  Класс: ").append(service.getClass().getSimpleName()).append("\n");

                // Добавляем специфическую информацию
                if (service instanceof LocalTextToSpeechService localService) {
                    info.append("  ОС: ").append(localService.getOperatingSystem()).append("\n");
                }

                if (type == TTSType.OPENAI || type == TTSType.GOOGLE) {
                    String apiKeyProp = type == TTSType.OPENAI ? "openai.api.key" : "google.cloud.api.key";
                    String envKey = type == TTSType.OPENAI ? "OPENAI_API_KEY" : "GOOGLE_CLOUD_API_KEY";
                    String apiKey = getApiKey(props, apiKeyProp, envKey);
                    info.append("  API ключ: ").append(
                            apiKey != null && !apiKey.isEmpty() ? "Настроен" : "Не настроен"
                    ).append("\n");
                }

                if (type == TTSType.GROQ) {
                    info.append("  Примечание: Не поддерживает TTS API\n");
                    info.append("  Используется: Другой TTS сервис как fallback\n");
                }

            } catch (Exception e) {
                info.append("  Ошибка: ").append(e.getMessage()).append("\n");
            }

            info.append("\n");
        }

        // Рекомендации
        info.append("=== Рекомендации ===\n");
        info.append("• Для лучшего качества: используйте GOOGLE (WaveNet)\n");
        info.append("• Для хорошего качества: используйте OPENAI\n");
        info.append("• Для оффлайн работы: используйте LOCAL\n");
        info.append("• Для тестирования: используйте DEMO\n");
        info.append("• Для автоматического выбора: используйте SMART (рекомендуется)\n");
        info.append("• Groq не поддерживает TTS - используйте для чата только\n\n");

        info.append("=== Сравнение качества ===\n");
        info.append("1. Google WaveNet: ⭐⭐⭐⭐⭐ (нейросетевое, премиум)\n");
        info.append("2. OpenAI TTS: ⭐⭐⭐⭐ (очень хорошее)\n");
        info.append("3. Локальный TTS: ⭐⭐ (базовое)\n");
        info.append("4. Демо TTS: ⭐ (текст в консоль)\n");

        return info.toString();
    }

    /**
     * Создает тестовый TTS сервис для проверки конфигурации
     */
    public static TextToSpeechService createTestService(Properties props) {
        logger.info("Создание тестового TTS сервиса для проверки конфигурации...");

        // Создаем демо-сервис для тестирования
        TextToSpeechService testService = createDemoTtsService(props);

        // Проверяем доступность других сервисов
        new Thread(() -> {
            try {
                logger.info("=== Начало тестирования TTS сервисов ===");

                TextToSpeechService[] services = {
                        createGoogleTtsService(props),
                        createOpenAITtsService(props),
                        createLocalTtsService(props)
                };

                String[] serviceNames = {"Google TTS", "OpenAI TTS", "Local TTS"};

                for (int i = 0; i < services.length; i++) {
                    try {
                        Thread.sleep(500);
                        boolean available = services[i].isAvailable();
                        logger.info("{}: {}", serviceNames[i],
                                available ? "✅ Доступен" : "❌ Недоступен");

                        if (!available && services[i] instanceof LocalTextToSpeechService) {
                            logger.info("   Для локального TTS установите: espeak, festival и т.д.");
                        }
                    } catch (Exception e) {
                        logger.warn("Ошибка при проверке {}: {}", serviceNames[i], e.getMessage());
                    }
                }

                logger.info("=== Тестирование завершено ===");

            } catch (Exception e) {
                logger.error("Ошибка при тестировании TTS сервисов", e);
            }
        }).start();

        return testService;
    }
}