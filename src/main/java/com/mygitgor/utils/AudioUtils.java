package com.mygitgor.utils;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioUtils {
    private static final Logger logger = LoggerFactory.getLogger(AudioUtils.class);

    public static boolean saveAudioToFile(byte[] audioData, AudioFormat format, String filePath) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioStream = new AudioInputStream(bais, format, audioData.length / format.getFrameSize());

            File audioFile = new File(filePath);
            audioFile.getParentFile().mkdirs();

            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, audioFile);
            logger.debug("Аудио сохранено в файл: {}", filePath);
            return true;

        } catch (IOException e) {
            logger.error("Ошибка при сохранении аудио в файл", e);
            return false;
        }
    }

    public static byte[] loadAudioFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.readAllBytes(path);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке аудио из файла", e);
            return new byte[0];
        }
    }

    public static long getAudioDuration(String filePath) {
        try {
            File audioFile = new File(filePath);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioStream.getFormat();
            long frames = audioStream.getFrameLength();

            return (long) (frames / format.getFrameRate());
        } catch (Exception e) {
            logger.error("Ошибка при получении длительности аудио", e);
            return 0;
        }
    }

    public static boolean isValidAudioFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".wav") || fileName.endsWith(".mp3") ||
                fileName.endsWith(".ogg") || fileName.endsWith(".flac");
    }

    public static String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
}
