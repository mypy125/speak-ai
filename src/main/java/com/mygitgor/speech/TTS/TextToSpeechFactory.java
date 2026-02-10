package com.mygitgor.speech.TTS;

import com.mygitgor.speech.TTS.type.DemoTextToSpeechService;
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
                createOpenAITtsService(props),   // 1. OpenAI TTS (если доступен)
                createLocalTtsService(props),    // 2. Локальный TTS (если есть утилиты)
                createDemoTtsService(props)      // 3. Демо-режим (всегда доступен)
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

            // Проверяем доступность асинхронно, но сразу возвращаем сервис
            checkServiceAvailabilityAsync(service, "OpenAI TTS");

            return service;

        } catch (Exception e) {
            logger.error("Ошибка при создании OpenAI TTS: {}", e.getMessage());
            return createUnavailableService("OpenAI TTS (ошибка инициализации)");
        }
    }

    /**
     * Создает Groq TTS сервис (напоминание: Groq не поддерживает TTS API!)
     */
    private static TextToSpeechService createGroqTtsService(Properties props) {
        logger.warn("⚠️ ВНИМАНИЕ: Groq не поддерживает TTS API");
        logger.info("Используем обходное решение: Groq для чата + OpenAI/Demo для TTS");

        String apiKey = getApiKey(props, "groq.api.key", "GROQ_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.debug("Groq API ключ не настроен");
        }

        // Создаем умный сервис как fallback
        return createSmartTtsService(props);
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

                    // Здесь можно настроить сервис если есть соответствующие методы
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
                "OPENAI,LOCAL,DEMO");
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
                case "GROQ" -> TTSType.SMART; // Groq не поддерживает TTS
                case "OPENAI" -> TTSType.OPENAI;
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
            case 0 -> "OpenAI TTS выбран как основной облачный сервис";
            case 1 -> "Локальный TTS выбран как оффлайн альтернатива";
            case 2 -> "Демо TTS выбран как последний fallback";
            default -> "Выбран по приоритету " + (priority + 1);
        };

        logger.info("📋 Причина выбора: {}", reason);

        // Дополнительная информация о сервисе
        if (service instanceof OpenAITextToSpeechService) {
            logger.info("   • Тип: Облачный (OpenAI API)");
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

                if (type == TTSType.OPENAI) {
                    String apiKey = getApiKey(props, "openai.api.key", "OPENAI_API_KEY");
                    info.append("  API ключ: ").append(
                            apiKey != null && !apiKey.isEmpty() ? "Настроен" : "Не настроен"
                    ).append("\n");
                }

            } catch (Exception e) {
                info.append("  Ошибка: ").append(e.getMessage()).append("\n");
            }

            info.append("\n");
        }

        // Рекомендации
        info.append("=== Рекомендации ===\n");
        info.append("• Для продакшена: используйте SMART или OPENAI\n");
        info.append("• Для оффлайн работы: используйте LOCAL\n");
        info.append("• Для тестирования: используйте DEMO\n");
        info.append("• Groq не поддерживает TTS - используйте для чата только\n");

        return info.toString();
    }
}