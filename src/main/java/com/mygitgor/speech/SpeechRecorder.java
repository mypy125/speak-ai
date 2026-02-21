package com.mygitgor.speech;

import javax.sound.sampled.*;
import java.io.*;

import com.mygitgor.utils.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SpeechRecorder implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecorder.class);

    private static final int BUFFER_SIZE = 4096;
    private static final float DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_SAMPLE_SIZE_BITS = 16;
    private static final int DEFAULT_CHANNELS = 1;
    private static final int STOP_WAIT_TIMEOUT_MS = 100;
    private static final int MAX_RECORDING_DURATION_SECONDS = 300;

    private final AudioFormat audioFormat;

    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong startTime = new AtomicLong(0);
    private final AtomicReference<String> currentFilePath = new AtomicReference<>(null);

    private final AtomicReference<TargetDataLine> targetLine = new AtomicReference<>(null);
    private final AtomicReference<ByteArrayOutputStream> byteArrayOutputStream = new AtomicReference<>(null);
    private final AtomicReference<Thread> recordingThread = new AtomicReference<>(null);

    private final ThreadPoolManager threadPoolManager;
    private final ExecutorService backgroundExecutor;

    public SpeechRecorder() {
        this.audioFormat = createDefaultAudioFormat();
        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.backgroundExecutor = threadPoolManager.getBackgroundExecutor();
        logger.info("SpeechRecorder инициализирован с форматом: {} Гц, {} бит, {} канал(ов)",
                audioFormat.getSampleRate(), audioFormat.getSampleSizeInBits(), audioFormat.getChannels());
    }

    private AudioFormat createDefaultAudioFormat() {
        return new AudioFormat(
                DEFAULT_SAMPLE_RATE,
                DEFAULT_SAMPLE_SIZE_BITS,
                DEFAULT_CHANNELS,
                true,
                false
        );
    }

    public void startRecording() {
        if (closed.get()) {
            throw new IllegalStateException("SpeechRecorder закрыт");
        }

        if (!isRecording.compareAndSet(false, true)) {
            logger.warn("Запись уже идет");
            return;
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(audioFormat);
            line.start();

            targetLine.set(line);
            byteArrayOutputStream.set(new ByteArrayOutputStream());
            startTime.set(System.currentTimeMillis());

            Thread thread = new Thread(this::recordLoop, "Speech-Recorder-Thread");
            thread.setDaemon(true);
            recordingThread.set(thread);
            thread.start();

            logger.info("🔴 Начата запись аудио");

        } catch (LineUnavailableException e) {
            isRecording.set(false);
            logger.error("Ошибка при доступе к микрофону", e);
            throw new RuntimeException("Не удалось получить доступ к микрофону: " + e.getMessage(), e);
        } catch (Exception e) {
            isRecording.set(false);
            logger.error("Неожиданная ошибка при начале записи", e);
            throw new RuntimeException("Ошибка при начале записи: " + e.getMessage(), e);
        }
    }

    private void recordLoop() {
        TargetDataLine line = targetLine.get();
        ByteArrayOutputStream baos = byteArrayOutputStream.get();

        if (line == null || baos == null) {
            logger.error("Ресурсы записи не инициализированы");
            return;
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        logger.debug("Поток записи запущен");

        while (isRecording.get() && !Thread.currentThread().isInterrupted()) {
            try {
                bytesRead = line.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    baos.write(buffer, 0, bytesRead);

                    long duration = getCurrentDuration();
                    if (duration > MAX_RECORDING_DURATION_SECONDS) {
                        logger.warn("Достигнута максимальная длительность записи ({} сек)", MAX_RECORDING_DURATION_SECONDS);
                        break;
                    }
                }
            } catch (Exception e) {
                if (isRecording.get()) {
                    logger.error("Ошибка при записи аудио", e);
                }
                break;
            }
        }

        logger.debug("Поток записи завершен");
    }

    public File stopRecording(String filePath) {
        if (!isRecording.compareAndSet(true, false)) {
            logger.warn("Запись не идет");
            return null;
        }
        currentFilePath.set(filePath);

        TargetDataLine line = targetLine.getAndSet(null);
        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }

        Thread thread = recordingThread.getAndSet(null);
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt();
                thread.join(STOP_WAIT_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Прерывание при ожидании завершения потока записи");
            }
        }

        if (filePath != null && !filePath.isEmpty()) {
            return saveAudioToFile(filePath);
        }

        cleanup();
        return null;
    }

    private File saveAudioToFile(String filePath) {
        ByteArrayOutputStream baos = byteArrayOutputStream.getAndSet(null);
        if (baos == null || baos.size() == 0) {
            logger.warn("Нет аудиоданных для сохранения");
            return null;
        }

        try {
            byte[] audioData = baos.toByteArray();
            File audioFile = new File(filePath);

            File parentDir = audioFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("Не удалось создать директорию: {}", parentDir);
                    return null;
                }
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream audioStream = new AudioInputStream(
                         bais,
                         audioFormat,
                         audioData.length / audioFormat.getFrameSize()
                 )) {

                AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, audioFile);
            }

            long duration = getCurrentDuration();
            logger.info("✅ Запись сохранена в файл: {} ({} байт, {} сек)",
                    filePath, audioData.length, duration);

            return audioFile;

        } catch (IOException e) {
            logger.error("Ошибка при сохранении аудиофайла", e);
            return null;
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        ByteArrayOutputStream baos = byteArrayOutputStream.getAndSet(null);
        if (baos != null) {
            try {
                baos.close();
            } catch (IOException e) {
                logger.debug("Ошибка при закрытии ByteArrayOutputStream: {}", e.getMessage());
            }
        }

        TargetDataLine line = targetLine.getAndSet(null);
        if (line != null && line.isOpen()) {
            line.close();
        }

        startTime.set(0);
        logger.debug("Ресурсы записи очищены");
    }

    public CompletableFuture<Void> startRecordingAsync() {
        return CompletableFuture.runAsync(this::startRecording, backgroundExecutor);
    }

    public CompletableFuture<File> stopRecordingAsync(String filePath) {
        return CompletableFuture.supplyAsync(() -> stopRecording(filePath), backgroundExecutor);
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    public long getCurrentDuration() {
        if (!isRecording.get() || startTime.get() == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - startTime.get()) / 1000;
    }

    public long getRecordedBytes() {
        ByteArrayOutputStream baos = byteArrayOutputStream.get();
        return baos != null ? baos.size() : 0;
    }

    public long getEstimatedDuration() {
        ByteArrayOutputStream baos = byteArrayOutputStream.get();
        if (baos == null || audioFormat == null) {
            return 0;
        }

        long bytesRecorded = baos.size();
        long framesRecorded = bytesRecorded / audioFormat.getFrameSize();
        return (long) (framesRecorded / audioFormat.getFrameRate());
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        logger.info("Закрытие SpeechRecorder...");

        if (isRecording.get()) {
            stopRecording(null);
        }

        cleanup();

        Thread thread = recordingThread.getAndSet(null);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(STOP_WAIT_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("SpeechRecorder закрыт");
    }

    public boolean isClosed() {
        return closed.get();
    }

    public static boolean isMicrophoneAvailable() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            return AudioSystem.getLine(info) != null;
        } catch (Exception e) {
            logger.debug("Микрофон не доступен: {}", e.getMessage());
            return false;
        }
    }

    public static Mixer.Info[] getAvailableMicrophones() {
        return AudioSystem.getMixerInfo();
    }

    @Override
    public String toString() {
        return String.format("SpeechRecorder{recording=%s, duration=%d сек, bytes=%d}",
                isRecording.get(), getCurrentDuration(), getRecordedBytes());
    }
}