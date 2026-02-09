package com.mygitgor.speech;

import javax.sound.sampled.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeechRecorder implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecorder.class);

    private TargetDataLine targetLine;
    private AudioFormat audioFormat;
    private boolean isRecording;
    private ByteArrayOutputStream byteArrayOutputStream;
    private volatile boolean closed = false;

    public SpeechRecorder() {
        this.audioFormat = getDefaultAudioFormat();
        this.isRecording = false;
    }

    private AudioFormat getDefaultAudioFormat() {
        float sampleRate = 44100;
        int sampleSizeInBits = 16;
        int channels = 1; // mono
        boolean signed = true;
        boolean bigEndian = false;

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public void startRecording() {
        if (isRecording) {
            logger.warn("Запись уже идет");
            return;
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(audioFormat);
            targetLine.start();

            byteArrayOutputStream = new ByteArrayOutputStream();
            isRecording = true;

            // Запуск потока для записи
            Thread recordingThread = new Thread(this::record);
            recordingThread.start();

            logger.info("Начата запись аудио");

        } catch (LineUnavailableException e) {
            logger.error("Ошибка при доступе к микрофону", e);
            throw new RuntimeException("Не удалось получить доступ к микрофону", e);
        }
    }

    private void record() {
        byte[] buffer = new byte[4096];
        int bytesRead;

        while (isRecording) {
            bytesRead = targetLine.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    public File stopRecording(String filePath) {
        if (!isRecording) {
            logger.warn("Запись не идет");
            return null;
        }

        isRecording = false;

        try {
            // Ждем завершения потока записи
            Thread.sleep(100);

            targetLine.stop();
            targetLine.close();

            // Сохраняем в файл
            byte[] audioData = byteArrayOutputStream.toByteArray();
            File audioFile = new File(filePath);

            // Создаем директорию если нужно
            audioFile.getParentFile().mkdirs();

            // Сохраняем как WAV файл
            AudioInputStream audioStream = new AudioInputStream(
                    new ByteArrayInputStream(audioData),
                    audioFormat,
                    audioData.length / audioFormat.getFrameSize()
            );

            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, audioFile);

            logger.info("Запись сохранена в файл: {}", filePath);

            return audioFile;

        } catch (Exception e) {
            logger.error("Ошибка при остановке записи", e);
            return null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public long getRecordingDuration() {
        if (byteArrayOutputStream == null || audioFormat == null) {
            return 0;
        }

        long bytesRecorded = byteArrayOutputStream.size();
        long framesRecorded = bytesRecorded / audioFormat.getFrameSize();

        return (long) (framesRecorded / audioFormat.getFrameRate());
    }

    public void cleanup() {
        if (targetLine != null && targetLine.isOpen()) {
            targetLine.close();
        }
        isRecording = false;
        logger.debug("Ресурсы записи очищены");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие SpeechRecorder...");

        // Останавливаем запись если идет
        if (isRecording()) {
            stopRecording(null);
        }

//        if (microphone != null) {
//            microphone.close();
//        }

        logger.info("SpeechRecorder закрыт");
    }
}
