package com.mygitgor.speech.TTS.type;

import com.mygitgor.speech.TTS.TextToSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DemoTextToSpeechService implements TextToSpeechService {
    private static final Logger logger = LoggerFactory.getLogger(DemoTextToSpeechService.class);

    private volatile boolean closed = false;
    private volatile boolean speaking = false;
    private Process currentProcess;
    private ExecutorService executorService;

    // Состояния озвучки
    public enum SpeechState {
        IDLE,
        PREPARING,
        SPEAKING,
        STOPPED,
        ERROR,
        DEMO_MODE
    }

    private final Object lock = new Object();
    private SpeechState currentState = SpeechState.IDLE;
    private List<SpeechStateListener> listeners = new ArrayList<>();

    // Интерфейс для отслеживания состояния
    public interface SpeechStateListener {
        void onSpeechStateChanged(SpeechState state);
        void onSpeechProgress(double progress);
        void onSpeechError(String error);
    }

    public DemoTextToSpeechService() {
        logger.info("Инициализация DemoTextToSpeechService");
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TTS-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Проверяет доступность системного TTS
     */
    public boolean isTTSAvailable() {
        return checkSystemTTS();
    }

    private boolean checkSystemTTS() {
        String os = System.getProperty("os.name", "").toLowerCase();

        try {
            if (os.contains("linux")) {
                // Проверяем наличие festival или espeak
                Process process1 = Runtime.getRuntime().exec(new String[]{"which", "festival"});
                Process process2 = Runtime.getRuntime().exec(new String[]{"which", "espeak"});

                boolean festivalAvailable = process1.waitFor(2, TimeUnit.SECONDS) &&
                        process1.exitValue() == 0;
                boolean espeakAvailable = process2.waitFor(2, TimeUnit.SECONDS) &&
                        process2.exitValue() == 0;

                logger.info("TTS доступность - festival: {}, espeak: {}",
                        festivalAvailable, espeakAvailable);
                return festivalAvailable || espeakAvailable;

            } else if (os.contains("win")) {
                Process process = Runtime.getRuntime().exec(
                        new String[]{"powershell", "-Command", "Add-Type -AssemblyName System.speech; $true"}
                );
                return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;

            } else if (os.contains("mac")) {
                Process process = Runtime.getRuntime().exec(new String[]{"which", "say"});
                return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
            }
        } catch (Exception e) {
            logger.warn("Ошибка при проверке TTS: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Пытается использовать системный TTS
     */
    private boolean executeSystemTTS(String text) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();

        try {
            if (os.contains("linux")) {
                return executeLinuxTTS(text);
            } else if (os.contains("win")) {
                return executeWindowsTTS(text);
            } else if (os.contains("mac")) {
                return executeMacTTS(text);
            }
        } catch (Exception e) {
            logger.warn("Системный TTS недоступен: {}", e.getMessage());
            throw e;
        }

        return false;
    }

    /**
     * Windows TTS через PowerShell
     */
    private boolean executeWindowsTTS(String text) throws IOException {
        // Экранируем текст для PowerShell
        String escapedText = text
                .replace("'", "''")
                .replace("\"", "\\\"")
                .replace("`", "``")
                .replace("$", "`$");

        String command = String.format(
                "powershell.exe -Command \"Add-Type -AssemblyName System.speech; " +
                        "$speech = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$speech.Rate = 0; " + // Скорость: -10 до 10
                        "$speech.Volume = 100; " + // Громкость: 0-100
                        "$speech.Speak('%s');\"",
                escapedText
        );

        logger.debug("Запускаем Windows TTS: {}", command);
        currentProcess = Runtime.getRuntime().exec(command);

        startErrorMonitor(currentProcess);

        try {
            return waitForProcessWithTimeout(currentProcess, 30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            currentProcess.destroy();
            throw new IOException("Озвучка прервана", e);
        }
    }

    /**
     * macOS TTS через say команду
     */
    private boolean executeMacTTS(String text) throws IOException {
        // Разбиваем длинный текст на части
        List<String> parts = splitTextIntoChunks(text, 200);

        for (String part : parts) {
            String[] command = {
                    "say",
                    "-v", "Alex", // голос (можно менять)
                    "-r", "175",  // скорость (слов в минуту)
                    part
            };

            logger.debug("Запускаем Mac TTS: {}", String.join(" ", command));
            currentProcess = Runtime.getRuntime().exec(command);
            startErrorMonitor(currentProcess);

            try {
                if (!waitForProcessWithTimeout(currentProcess, 30)) {
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                currentProcess.destroy();
                throw new IOException("Озвучка прервана", e);
            }
        }

        return true;
    }

    /**
     * Linux TTS с festival и espeak
     */
    private boolean executeLinuxTTS(String text) throws IOException {
        // Сначала пробуем festival
        try {
            if (tryFestival(text)) {
                return true;
            }
        } catch (Exception e) {
            logger.debug("Festival недоступен: {}", e.getMessage());
        }

        // Потом пробуем espeak
        try {
            if (tryEspeak(text)) {
                return true;
            }
        } catch (Exception e) {
            logger.debug("Espeak недоступен: {}", e.getMessage());
        }

        throw new IOException("Ни festival, ни espeak не доступны");
    }

    private boolean tryFestival(String text) throws IOException {
        File tempScript = null;
        try {
            tempScript = File.createTempFile("festival_", ".scm");

            // Сначала проверяем какие голоса доступны
            String script = buildFestivalScript(text);

            Files.writeString(tempScript.toPath(), script, StandardCharsets.UTF_8);

            String[] command = {
                    "festival",
                    "--script",
                    tempScript.getAbsolutePath()
            };

            logger.debug("Запускаем festival: {}", String.join(" ", command));
            currentProcess = Runtime.getRuntime().exec(command);

            startErrorMonitor(currentProcess);

            return waitForProcessWithTimeout(currentProcess, 30);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Озвучка прервана", e);
        } finally {
            if (tempScript != null && tempScript.exists()) {
                try {
                    tempScript.delete();
                } catch (Exception e) {
                    logger.debug("Не удалось удалить временный файл: {}", e.getMessage());
                }
            }
        }
    }

    private String buildFestivalScript(String text) {
        StringBuilder script = new StringBuilder();

        // Попробуем разные голоса в порядке предпочтения
        String[] availableVoices = {
                "voice_kal_diphone",    // Базовый (обычно есть)
                "voice_rab_diphone",    // Альтернативный
                "voice_nitech_us_slt_arctic_hts",  // Качественный
                "voice_cmu_us_slt_arctic_hts",     // Качественный (требует установки)
                "voice_cmu_us_awb_arctic_hts",     // Качественный (требует установки)
                "voice_cmu_us_bdl_arctic_hts",     // Качественный (требует установки)
                "voice_cmu_us_jmk_arctic_hts",     // Качественный (требует установки)
                "voice_cmu_us_rms_arctic_hts",     // Качественный (требует установки)
                "voice_cmu_us_slt_arctic_hts"      // Качественный (требует установки)
        };

        // Пробуем загрузить голос, если не получается - используем без голоса
        script.append("(begin\n");

        for (String voice : availableVoices) {
            script.append("  (if (member '").append(voice).append(" (voice.list))\n");
            script.append("      (begin\n");
            script.append("        (").append(voice).append(")\n");
            script.append("        (SayText \"").append(escapeForFestival(text)).append("\")\n");
            script.append("        #t)\n");  // Возвращаем true если успешно
            script.append("      #f)\n");    // Возвращаем false если голос не найден
        }

        // Если ни один голос не загрузился, пробуем без голоса
        script.append("  (SayText \"").append(escapeForFestival(text)).append("\")\n");
        script.append("  #t\n");  // Всегда возвращаем true
        script.append(")\n");

        return script.toString();
    }

    private boolean tryEspeak(String text) throws IOException {
        try {
            // Ограничиваем длину текста для espeak
            String shortText = text.length() > 1000 ? text.substring(0, 1000) + "..." : text;

            String[] command = {
                    "espeak",
                    "-v", "en",
                    "-s", "150",
                    shortText
            };

            logger.debug("Запускаем espeak: {}", String.join(" ", command));
            currentProcess = Runtime.getRuntime().exec(command);

            startErrorMonitor(currentProcess);
            return waitForProcessWithTimeout(currentProcess, 30);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Озвучка прервана", e);
        } catch (Exception e) {
            logger.debug("Espeak ошибка: {}", e.getMessage());
            throw new IOException("Espeak недоступен", e);
        }
    }

    private String escapeForFestival(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ");
    }

    private List<String> splitTextIntoChunks(String text, int maxWords) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");

        StringBuilder currentChunk = new StringBuilder();
        int wordCount = 0;

        for (String word : words) {
            if (wordCount >= maxWords && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder();
                wordCount = 0;
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(word);
            wordCount++;
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    /**
     * Демо-режим TTS (вывод в консоль с паузами)
     */
    private boolean demoTextToSpeech(String text) {
        try {
            logger.info("ДЕМО-РЕЖИМ: Озвучка текста ({} символов)", text.length());

            // Разбиваем текст на абзацы
            String[] paragraphs = text.split("\\n\\s*\\n");

            for (String paragraph : paragraphs) {
                if (paragraph.trim().isEmpty()) continue;

                // Выводим заголовок
                System.out.println("\n" + "=".repeat(60));
                System.out.println("🔊 ДЕМО ОЗВУЧКА:");
                System.out.println("=".repeat(60));

                // Разбиваем на предложения для имитации речи
                String[] sentences = paragraph.split("(?<=[.!?])\\s+");

                for (int i = 0; i < sentences.length; i++) {
                    String sentence = sentences[i].trim();
                    if (sentence.isEmpty()) continue;

                    // Имитируем произношение предложения
                    System.out.println(sentence);

                    // Проверяем, не остановлена ли озвучка
                    synchronized (lock) {
                        if (!speaking) {
                            logger.info("Демо-озвучка остановлена пользователем");
                            return true;
                        }
                    }

                    // Пауза между предложениями
                    int wordCount = sentence.split("\\s+").length;
                    long pause = Math.min(wordCount * 300L, 2000L);

                    if (i < sentences.length - 1) {
                        Thread.sleep(pause);
                    }
                }

                // Пауза между абзацами
                Thread.sleep(500);
            }

            System.out.println("=".repeat(60));
            logger.info("ДЕМО-РЕЖИМ: Озвучка завершена");
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Демо-озвучка прервана");
            return true;
        } catch (Exception e) {
            logger.error("Ошибка в демо-режиме TTS", e);
            return false;
        }
    }

    /**
     * Ожидание завершения процесса с таймаутом
     */
    private boolean waitForProcessWithTimeout(Process process, int timeoutSeconds)
            throws InterruptedException {
        if (process == null) return false;

        if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                logger.debug("TTS процесс завершился успешно");
                return true;
            } else {
                logger.warn("TTS процесс завершился с кодом: {}", exitCode);
                return false;
            }
        } else {
            logger.warn("TTS процесс не завершился за {} секунд", timeoutSeconds);
            process.destroy();
            return false;
        }
    }

    /**
     * Асинхронная озвучка текста
     */
    public CompletableFuture<Void> speakAsync(String text) {
        if (closed) {
            throw new IllegalStateException("DemoTextToSpeechService закрыт");
        }

        if (text == null || text.trim().isEmpty()) {
            logger.warn("Пустой текст для озвучки");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        executorService.submit(() -> {
            try {
                speakInternal(text, future);
            } catch (Exception e) {
                logger.error("Ошибка при озвучке", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Синхронная озвучка (блокирует поток)
     */
    public void speak(String text) {
        if (closed) {
            throw new IllegalStateException("DemoTextToSpeechService закрыт");
        }

        if (text == null || text.trim().isEmpty()) {
            logger.warn("Пустой текст для озвучки");
            return;
        }

        try {
            speakInternal(text, null);
        } catch (Exception e) {
            logger.error("Ошибка при озвучке", e);
            // В демо-режиме не бросаем исключение
            if (currentState != SpeechState.DEMO_MODE) {
                throw new RuntimeException("Ошибка озвучки: " + e.getMessage(), e);
            }
        }
    }

    private void speakInternal(String text, CompletableFuture<Void> future) {
        synchronized (lock) {
            if (speaking) {
                stopSpeaking();
            }
            speaking = true;
            currentState = SpeechState.PREPARING;
            notifyStateChanged();
        }

        try {
            String cleanText = cleanTextForSpeech(text);
            logger.info("Начало озвучки текста ({} символов)", cleanText.length());

            synchronized (lock) {
                currentState = SpeechState.SPEAKING;
                notifyStateChanged();
            }

            // Пробуем системный TTS, если не удается - используем демо-режим
            boolean success = false;
            try {
                success = executeSystemTTS(cleanText);

                if (!success) {
                    // Переключаемся в демо-режим
                    logger.info("Системный TTS недоступен, переходим в демо-режим");
                    synchronized (lock) {
                        currentState = SpeechState.DEMO_MODE;
                        notifyStateChanged();
                    }
                    success = demoTextToSpeech(cleanText);
                }

            } catch (Exception e) {
                logger.warn("Ошибка TTS, переходим в демо-режим: {}", e.getMessage());
                synchronized (lock) {
                    currentState = SpeechState.DEMO_MODE;
                    notifyStateChanged();
                }
                success = demoTextToSpeech(cleanText);
            }

            synchronized (lock) {
                speaking = false;
                if (success) {
                    currentState = SpeechState.IDLE;
                } else {
                    currentState = SpeechState.ERROR;
                }
                notifyStateChanged();
            }

            if (future != null) {
                if (success) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(new RuntimeException("Ошибка озвучки"));
                }
            }

        } catch (Exception e) {
            logger.error("Критическая ошибка при озвучке текста", e);

            synchronized (lock) {
                speaking = false;
                currentState = SpeechState.ERROR;
                notifyStateChanged();
            }

            if (future != null) {
                future.completeExceptionally(e);
            }
        } finally {
            currentProcess = null;
        }
    }

    /**
     * Останавливает текущую озвучку
     */
    public void stopSpeaking() {
        synchronized (lock) {
            if (!speaking && currentProcess == null) {
                return;
            }

            logger.info("Остановка озвучки...");
            speaking = false;

            // Останавливаем системный процесс если есть
            if (currentProcess != null && currentProcess.isAlive()) {
                try {
                    currentProcess.destroy();
                    if (!currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                        currentProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Прервано ожидание завершения TTS процесса");
                }
                currentProcess = null;
            }

            currentState = SpeechState.STOPPED;
            notifyStateChanged();
        }
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    private void startErrorMonitor(Process process) {
        executorService.submit(() -> {
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        logger.warn("TTS ошибка: {}", line);
                    }
                }
            } catch (IOException e) {
                // Игнорируем, если поток закрыт
                if (!e.getMessage().contains("Stream closed")) {
                    logger.debug("Ошибка при чтении stderr TTS: {}", e.getMessage());
                }
            }
        });
    }

    private String cleanTextForSpeech(String text) {
        if (text == null) return "";

        // Очищаем текст для TTS
        return text
                .replaceAll("#+\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("`", "")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️🏆🎉👍💪📚🔧❤️✨🌟🔥💡🎯📅❌ℹ️]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    // Методы для управления состоянием
    public boolean isSpeaking() {
        return speaking;
    }

    public SpeechState getCurrentState() {
        return currentState;
    }

    public void addStateListener(SpeechStateListener listener) {
        listeners.add(listener);
    }

    public void removeStateListener(SpeechStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyStateChanged() {
        for (SpeechStateListener listener : listeners) {
            try {
                listener.onSpeechStateChanged(currentState);
            } catch (Exception e) {
                logger.warn("Ошибка в TTS listener", e);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        logger.info("Закрытие DemoTextToSpeechService...");
        closed = true;

        // Останавливаем текущую озвучку
        stopSpeaking();

        // Закрываем ExecutorService
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("DemoTextToSpeechService закрыт");
    }

    public boolean isClosed() {
        return closed;
    }
}