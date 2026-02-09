package com.mygitgor.speech;

import javax.sound.sampled.*;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeechPlayer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SpeechPlayer.class);

    private Clip audioClip;
    private boolean isPlaying;
    private volatile boolean closed = false;

    public SpeechPlayer() {
        this.isPlaying = false;
    }

    public void playAudio(String filePath) {
        if (isPlaying) {
            stopAudio();
        }

        try {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                logger.error("Аудиофайл не найден: {}", filePath);
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);

            audioClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    isPlaying = false;
                    cleanup();
                }
            });

            audioClip.start();
            isPlaying = true;

            logger.debug("Воспроизведение аудиофайла: {}", filePath);

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            logger.error("Ошибка при воспроизведении аудио", e);
        }
    }

    public void stopAudio() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
            isPlaying = false;
            logger.debug("Воспроизведение остановлено");
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public long getDuration(String filePath) {
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

    private void cleanup() {
        if (audioClip != null) {
            audioClip.close();
            audioClip = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие SpeechPlayer...");

        if (isPlaying()) {
            stopAudio();
        }

        if (audioClip != null) {
            audioClip.close();
        }

        logger.info("SpeechPlayer закрыт");
    }
}
