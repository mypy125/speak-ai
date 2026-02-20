package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolManager.class);

    private static volatile ThreadPoolManager instance;
    private final ExecutorService ttsExecutor;
    private final ExecutorService speechRecognitionExecutor;
    private final ExecutorService audioAnalysisExecutor;
    private final ExecutorService backgroundExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    private volatile boolean shutdown = false;

    private ThreadPoolManager() {
        ThreadFactory ttsFactory = new NamedThreadFactory("TTS");
        ThreadFactory speechFactory = new NamedThreadFactory("Speech-Rec");
        ThreadFactory analysisFactory = new NamedThreadFactory("Audio-Analysis");
        ThreadFactory backgroundFactory = new NamedThreadFactory("Background");
        ThreadFactory scheduledFactory = new NamedThreadFactory("Scheduled");

        this.ttsExecutor = Executors.newFixedThreadPool(2, ttsFactory);
        this.speechRecognitionExecutor = Executors.newFixedThreadPool(2, speechFactory);
        this.audioAnalysisExecutor = Executors.newFixedThreadPool(2, analysisFactory);
        this.backgroundExecutor = Executors.newCachedThreadPool(backgroundFactory);
        this.scheduledExecutor = Executors.newScheduledThreadPool(3, scheduledFactory);

        logger.info("ThreadPoolManager инициализирован");
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-Thread-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    public static ThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolManager.class) {
                if (instance == null) {
                    instance = new ThreadPoolManager();
                }
            }
        }
        return instance;
    }

    public ExecutorService getTtsExecutor() {
        checkShutdown();
        return ttsExecutor;
    }

    public ExecutorService getSpeechRecognitionExecutor() {
        checkShutdown();
        return speechRecognitionExecutor;
    }

    public ExecutorService getAudioAnalysisExecutor() {
        checkShutdown();
        return audioAnalysisExecutor;
    }

    public ExecutorService getBackgroundExecutor() {
        checkShutdown();
        return backgroundExecutor;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        checkShutdown();
        return scheduledExecutor;
    }

    private void checkShutdown() {
        if (shutdown) {
            throw new IllegalStateException("ThreadPoolManager уже остановлен");
        }
    }


    public static void runWithDelay(Runnable task, long delayMillis) {
        PauseTransition pause = new PauseTransition(Duration.millis(delayMillis));
        pause.setOnFinished(event -> task.run());
        pause.play();
    }


    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        checkShutdown();
        return scheduledExecutor.schedule(task, delay, unit);
    }


    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        checkShutdown();
        return scheduledExecutor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }


    public void shutdown() {
        if (shutdown) return;

        shutdown = true;
        logger.info("Завершение работы ThreadPoolManager...");

        shutdownExecutor(ttsExecutor, "TTS");
        shutdownExecutor(speechRecognitionExecutor, "SpeechRecognition");
        shutdownExecutor(audioAnalysisExecutor, "AudioAnalysis");
        shutdownExecutor(backgroundExecutor, "Background");
        shutdownExecutor(scheduledExecutor, "Scheduled");

        logger.info("ThreadPoolManager завершил работу");
        instance = null;
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) return;

        try {
            logger.debug("Остановка пула: {}", name);
            executor.shutdown();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    logger.warn("Пул {} не остановлен принудительно", name);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
