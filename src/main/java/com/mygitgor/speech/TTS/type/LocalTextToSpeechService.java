package com.mygitgor.speech.TTS.type;

import com.mygitgor.speech.TTS.TextToSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

public class LocalTextToSpeechService implements TextToSpeechService {
    private static final Logger logger = LoggerFactory.getLogger(LocalTextToSpeechService.class);

    private final ExecutorService executorService;
    private volatile boolean closed = false;
    private Process currentProcess;
    private CompletableFuture<Void> currentSpeechFuture;

    // Поддерживаемые языки
    public enum Language {
        ENGLISH("en", "en-US", "Английский"),
        RUSSIAN("ru", "ru-RU", "Русский");

        private final String code;
        private final String voiceCode;
        private final String description;

        Language(String code, String voiceCode, String description) {
            this.code = code;
            this.voiceCode = voiceCode;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getVoiceCode() { return voiceCode; }
        public String getDescription() { return description; }
    }

    private Language currentLanguage = Language.ENGLISH;
    private float currentSpeed = 1.0f;
    private String currentVoice;

    // Определяем ОС
    private enum OS {
        WINDOWS, LINUX, MAC, UNKNOWN
    }

    private final OS os;
    private final List<String> availableEngines = new ArrayList<>();

    public LocalTextToSpeechService() {
        logger.info("Инициализация Local TTS Service...");

        this.os = detectOS();
        detectAvailableEngines();

        if (availableEngines.isEmpty()) {
            logger.warn("Не найдены TTS движки на системе. Будет использован DEMO режим.");
        } else {
            logger.info("Найдены TTS движки: {}", String.join(", ", availableEngines));
            this.currentVoice = getDefaultVoiceForOS();
        }

        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Local-TTS-Thread");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("Local TTS Service инициализирован для ОС: {}", os);
    }

    private OS detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return OS.MAC;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        } else {
            return OS.UNKNOWN;
        }
    }

    private void detectAvailableEngines() {
        switch (os) {
            case WINDOWS:
                if (isCommandAvailable("powershell.exe")) {
                    availableEngines.add("Windows PowerShell TTS");
                }
                break;

            case MAC:
                if (isCommandAvailable("say")) {
                    availableEngines.add("macOS say");
                }
                break;

            case LINUX:
                // Проверяем различные Linux TTS движки
                if (isCommandAvailable("espeak-ng")) {
                    availableEngines.add("espeak-ng");
                }
                if (isCommandAvailable("espeak")) {
                    availableEngines.add("espeak");
                }
                if (isCommandAvailable("festival")) {
                    availableEngines.add("festival");
                }
                if (isCommandAvailable("spd-say")) {
                    availableEngines.add("spd-say (Speech Dispatcher)");
                }
                if (isCommandAvailable("pico2wave")) {
                    availableEngines.add("pico2wave (Pico TTS)");
                }
                if (isCommandAvailable("mimic3")) {
                    availableEngines.add("mimic3");
                }
                break;

            default:
                logger.warn("Неизвестная ОС: {}", System.getProperty("os.name"));
        }
    }

    private String getDefaultVoiceForOS() {
        switch (os) {
            case WINDOWS:
                return "Microsoft David Desktop";
            case MAC:
                return "Alex";
            case LINUX:
                // Для Linux возвращаем первый доступный движок
                return !availableEngines.isEmpty() ? availableEngines.get(0) : "espeak";
            default:
                return "default";
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            String[] cmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                cmd = new String[]{"cmd", "/c", "where", command};
            } else {
                cmd = new String[]{"which", command};
            }

            Process process = new ProcessBuilder(cmd).start();
            int exitCode = process.waitFor();
            boolean available = (exitCode == 0);

            if (available) {
                logger.debug("Команда '{}' доступна в системе", command);
            }

            return available;
        } catch (Exception e) {
            logger.debug("Команда '{}' недоступна: {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * Генерация речи через системные утилиты
     */
    private void generateAndPlaySpeech(String text, Language language, float speed) throws IOException {
        if (availableEngines.isEmpty()) {
            throw new IOException("Нет доступных TTS движков на системе. Установите один из: espeak, festival и т.д.");
        }

        String cleanText = cleanTextForSpeech(text);
        if (cleanText.trim().isEmpty()) {
            logger.warn("Текст для озвучки пуст");
            return;
        }

        logger.info("Local TTS: Озвучка текста ({} символов), язык: {}, скорость: {}",
                cleanText.length(), language.getDescription(), speed);

        ProcessBuilder processBuilder = null;
        IOException lastException = null;

        // Пробуем доступные движки по порядку
        for (String engine : availableEngines) {
            try {
                processBuilder = createProcessBuilder(engine, cleanText, language, speed);
                if (processBuilder != null) {
                    logger.debug("Используем TTS движок: {}", engine);
                    break;
                }
            } catch (Exception e) {
                lastException = new IOException("Ошибка создания ProcessBuilder для " + engine + ": " + e.getMessage());
                logger.debug("Движок {} не сработал: {}", engine, e.getMessage());
            }
        }

        if (processBuilder == null) {
            throw lastException != null ? lastException :
                    new IOException("Не удалось создать ProcessBuilder для доступных движков");
        }

        // Перенаправляем stderr чтобы избежать блокировок
        processBuilder.redirectErrorStream(true);

        try {
            logger.debug("Запуск TTS процесса...");
            currentProcess = processBuilder.start();

            // Читаем вывод в отдельном потоке чтобы избежать блокировок
            readProcessOutput(currentProcess);

            // Ждем завершения процесса с таймаутом
            boolean finished = currentProcess.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                logger.warn("TTS процесс не завершился за 30 секунд, прерываем...");
                currentProcess.destroy();
                if (currentProcess.waitFor(5, TimeUnit.SECONDS)) {
                    logger.debug("Процесс успешно завершен после destroy()");
                } else {
                    logger.warn("Процесс не завершился после destroy(), используем destroyForcibly()");
                    currentProcess.destroyForcibly();
                }
            }

            int exitCode = currentProcess.exitValue();
            if (exitCode != 0) {
                logger.warn("TTS процесс завершился с кодом ошибки: {}", exitCode);
            } else {
                logger.debug("Local TTS: Воспроизведение завершено успешно");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Воспроизведение прервано пользователем");
            throw new IOException("Воспроизведение прервано", e);
        } finally {
            if (currentProcess != null && currentProcess.isAlive()) {
                try {
                    currentProcess.destroy();
                } catch (Exception e) {
                    logger.debug("Ошибка при завершении процесса: {}", e.getMessage());
                }
            }
            currentProcess = null;
        }
    }

    /**
     * Создание ProcessBuilder для конкретного движка
     */
    private ProcessBuilder createProcessBuilder(String engine, String text, Language language, float speed) throws IOException {
        switch (engine) {
            case "Windows PowerShell TTS":
                return createWindowsPowerShellProcess(text, language, speed);
            case "macOS say":
                return createMacSayProcess(text, language, speed);
            case "espeak-ng":
            case "espeak":
                return createEspeakProcess(text, language, speed, engine);
            case "festival":
                return createFestivalProcess(text, language, speed);
            case "spd-say (Speech Dispatcher)":
                return createSpdSayProcess(text, language, speed);
            case "pico2wave (Pico TTS)":
                return createPico2WaveProcess(text, language, speed);
            default:
                throw new IOException("Неизвестный TTS движок: " + engine);
        }
    }

    private ProcessBuilder createWindowsPowerShellProcess(String text, Language language, float speed) {
        // PowerShell команда для Windows TTS
        String escapedText = text
                .replace("'", "''")
                .replace("\"", "`\"")
                .replace("$", "`$");

        String voice = getWindowsVoice(language);
        int rate = convertSpeedToWindowsRate(speed);

        String powerShellCommand = String.format(
                "Add-Type -AssemblyName System.speech; " +
                        "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$speak.Rate = %d; " +
                        "$speak.SelectVoice('%s'); " +
                        "$speak.Speak('%s');",
                rate, voice, escapedText
        );

        return new ProcessBuilder("powershell.exe", "-Command", powerShellCommand);
    }

    private ProcessBuilder createMacSayProcess(String text, Language language, float speed) {
        String voice = getMacVoice(language);
        int rate = convertSpeedToMacRate(speed);

        // Убедимся, что текст правильно экранирован
        String safeText = text.replace("\"", "\\\"").replace("`", "\\`");

        return new ProcessBuilder("say", "-v", voice, "--rate=" + rate, safeText);
    }

    private ProcessBuilder createEspeakProcess(String text, Language language, float speed, String engineName) {
        String voice = getEspeakVoice(language);
        int rate = convertSpeedToEspeakRate(speed);

        String command = engineName.equals("espeak-ng") ? "espeak-ng" : "espeak";

        // Для длинного текста лучше использовать stdin
        if (text.length() > 500) {
            return new ProcessBuilder(command, "-v", voice, "-s", String.valueOf(rate));
        } else {
            return new ProcessBuilder(command, "-v", voice, "-s", String.valueOf(rate), text);
        }
    }

    private ProcessBuilder createFestivalProcess(String text, Language language, float speed) {
        try {
            Path tempScript = createFestivalScript(text, language, speed);
            return new ProcessBuilder("festival", "--script", tempScript.toString());
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать скрипт для Festival", e);
        }
    }

    private ProcessBuilder createSpdSayProcess(String text, Language language, float speed) {
        int rate = (int)(speed * 100);
        String languageCode = language.getCode();

        return new ProcessBuilder("spd-say", "-r", String.valueOf(rate), "-l", languageCode, text);
    }

    private ProcessBuilder createPico2WaveProcess(String text, Language language, float speed) {
        try {
            Path tempFile = Files.createTempFile("tts_", ".wav");
            String languageParam = getPicoLanguage(language);
            String escapedText = text.replace("\"", "\\\"");

            // Создаем скрипт для воспроизведения
            String script = String.format(
                    "pico2wave -l=%s -w=%s \"%s\" && aplay %s 2>/dev/null || play %s 2>/dev/null || echo 'Не найдены команды для воспроизведения аудио'",
                    languageParam, tempFile.toString(), escapedText, tempFile.toString(), tempFile.toString()
            );

            // Удалим файл после использования
            tempFile.toFile().deleteOnExit();

            return new ProcessBuilder("sh", "-c", script);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать команду для pico2wave", e);
        }
    }

    private Path createFestivalScript(String text, Language language, float speed) throws IOException {
        Path tempFile = Files.createTempFile("festival_", ".scm");
        String voice = getFestivalVoice(language);

        String script = String.format(
                "(Parameter.set 'Audio_Command \"aplay -q -c 1 -t raw -f s16 -r $SR $FILE 2>/dev/null || play $FILE 2>/dev/null\")\n" +
                        "(Parameter.set 'Audio_Method 'Audio_Command)\n" +
                        "(set! duración_cara %f)\n" + // Скорость для festival
                        "(voice_%s)\n" +
                        "(SayText \"%s\")\n",
                speed, voice, text.replace("\"", "\\\"")
        );

        Files.writeString(tempFile, script);
        tempFile.toFile().deleteOnExit();

        return tempFile;
    }

    private void readProcessOutput(Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        logger.debug("TTS вывод: {}", line);
                    }
                }
            } catch (IOException e) {
                // Игнорируем ошибки чтения
            }
        }).start();
    }

    // Вспомогательные методы для конвертации параметров

    private int convertSpeedToWindowsRate(float speed) {
        // Windows: -10 (медленно) до 10 (быстро)
        return Math.max(-10, Math.min(10, Math.round((speed - 1.0f) * 10)));
    }

    private int convertSpeedToMacRate(float speed) {
        // Mac: стандартно 175
        return Math.round(175 * speed);
    }

    private int convertSpeedToEspeakRate(float speed) {
        // espeak: стандартно 175
        return Math.round(175 * speed);
    }

    private String getWindowsVoice(Language language) {
        switch (language) {
            case ENGLISH:
                return "Microsoft David Desktop"; // Английский мужской
            case RUSSIAN:
                return "Microsoft Irina Desktop"; // Русский женский
            default:
                return "Microsoft David Desktop";
        }
    }

    private String getMacVoice(Language language) {
        switch (language) {
            case ENGLISH:
                return "Alex";
            case RUSSIAN:
                return "Yuri"; // Русский мужской на Mac
            default:
                return "Alex";
        }
    }

    private String getEspeakVoice(Language language) {
        switch (language) {
            case ENGLISH:
                return "en-us";
            case RUSSIAN:
                return "ru";
            default:
                return "en";
        }
    }

    private String getFestivalVoice(Language language) {
        switch (language) {
            case ENGLISH:
                return "cmu_us_slt_arctic_hts";
            case RUSSIAN:
                return "russian";
            default:
                return "kal_diphone";
        }
    }

    private String getPicoLanguage(Language language) {
        switch (language) {
            case ENGLISH:
                return "en-US";
            case RUSSIAN:
                return "ru-RU";
            default:
                return "en-US";
        }
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        return speakAsync(text, currentLanguage, currentSpeed);
    }

    public CompletableFuture<Void> speakAsync(String text, Language language, float speed) {
        if (closed) {
            throw new IllegalStateException("Local TTS Service закрыт");
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        currentSpeechFuture = future;

        executorService.submit(() -> {
            try {
                generateAndPlaySpeech(text, language, speed);
                if (!future.isDone()) {
                    future.complete(null);
                }
            } catch (Exception e) {
                logger.error("Ошибка при локальной озвучке: {}", e.getMessage());
                if (!future.isDone()) {
                    future.completeExceptionally(new IOException("Не удалось озвучить текст: " + e.getMessage(), e));
                }
            }
        });

        return future;
    }

    @Override
    public void speak(String text) {
        speak(text, currentLanguage, currentSpeed);
    }

    public void speak(String text, Language language, float speed) {
        if (closed) {
            throw new IllegalStateException("Local TTS Service закрыт");
        }

        try {
            generateAndPlaySpeech(text, language, speed);
        } catch (Exception e) {
            logger.error("Ошибка при локальной озвучке: {}", e.getMessage());
            throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
        }
    }

    @Override
    public void stopSpeaking() {
        logger.info("Остановка локальной озвучки...");

        if (currentSpeechFuture != null && !currentSpeechFuture.isDone()) {
            currentSpeechFuture.cancel(true);
            currentSpeechFuture = null;
        }

        if (currentProcess != null && currentProcess.isAlive()) {
            try {
                currentProcess.destroy();
                if (currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                    logger.debug("TTS процесс успешно остановлен");
                } else {
                    currentProcess.destroyForcibly();
                    logger.debug("TTS процесс принудительно остановлен");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Остановка TTS прервана");
            } catch (Exception e) {
                logger.warn("Ошибка при остановке TTS процесса: {}", e.getMessage());
            }
            currentProcess = null;
        }
    }

    @Override
    public boolean isAvailable() {
        return !availableEngines.isEmpty();
    }

    public List<String> getAvailableEngines() {
        return new ArrayList<>(availableEngines);
    }

    public void setLanguage(Language language) {
        this.currentLanguage = language;
        logger.info("Установлен язык озвучки: {}", language.getDescription());
    }

    public void setSpeed(float speed) {
        if (speed < 0.5f || speed > 2.0f) {
            throw new IllegalArgumentException("Скорость должна быть от 0.5 до 2.0");
        }
        this.currentSpeed = speed;
        logger.info("Установлена скорость озвучки: {}", speed);
    }

    public OS getOperatingSystem() {
        return os;
    }

    public Map<String, String> getAvailableLanguages() {
        Map<String, String> languages = new HashMap<>();
        for (Language lang : Language.values()) {
            languages.put(lang.name(), lang.getDescription());
        }
        return languages;
    }

    /**
     * Проверка и установка TTS утилит для Linux
     */
    public static String getInstallationInstructions() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("linux")) {
            return """
                   Для использования TTS на Linux установите одну из утилит:
                   
                   Ubuntu/Debian:
                   sudo apt-get update
                   sudo apt-get install espeak-ng festival speech-dispatcher libttspico-utils
                   
                   Fedora/RHEL:
                   sudo dnf install espeak-ng festival speech-dispatcher pico2wave
                   
                   Arch Linux:
                   sudo pacman -S espeak-ng festival speech-dispatcher
                   """;
        } else if (osName.contains("win")) {
            return """
                   Windows имеет встроенный TTS через PowerShell.
                   Убедитесь, что голоса установлены в Панель управления -> Речь.
                   """;
        } else if (osName.contains("mac")) {
            return """
                   macOS имеет встроенную команду 'say'.
                   Для дополнительных голосов установите их через Системные настройки -> Универсальный доступ -> Речь.
                   """;
        }

        return "Неизвестная операционная система";
    }

    private String cleanTextForSpeech(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // Удаляем Markdown и специальные символы, но оставляем знаки препинания
        return text
                .replaceAll("#+\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("`", "")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "") // Ссылки Markdown
                .replaceAll("<[^>]*>", "") // HTML теги
                .replaceAll("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️🏆🎉👍💪📚🔧❤️✨🌟🔥💡🎯📅❌ℹ️]", "") // Эмодзи
                .replaceAll("\\s+", " ") // Множественные пробелы
                .replaceAll("\\n{3,}", "\n\n") // Множественные переносы строк
                .trim();
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;
        logger.info("Закрытие Local TTS Service...");

        stopSpeaking();

        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Local TTS Service закрыт");
    }
}