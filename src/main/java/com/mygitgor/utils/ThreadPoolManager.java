package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Централизованный менеджер пулов потоков для всего приложения.
 * Обеспечивает единое управление потоками и предотвращает создание новых потоков без необходимости.
 */
public class ThreadPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolManager.class);

    // Singleton instance
    private static volatile ThreadPoolManager instance;

    // Пул для TTS операций
    private final ExecutorService ttsExecutor;

    // Пул для операций распознавания речи
    private final ExecutorService speechRecognitionExecutor;

    // Пул для анализа аудио
    private final ExecutorService audioAnalysisExecutor;

    // Пул для общих фоновых задач
    private final ExecutorService backgroundExecutor;

    // Пул для таймеров и задержек
    private final ScheduledExecutorService scheduledExecutor;

    // Состояние менеджера
    private volatile boolean shutdown = false;

    private ThreadPoolManager() {
        // Создаем именованные ThreadFactory для легкой отладки
        ThreadFactory ttsFactory = new NamedThreadFactory("TTS");
        ThreadFactory speechFactory = new NamedThreadFactory("Speech-Rec");
        ThreadFactory analysisFactory = new NamedThreadFactory("Audio-Analysis");
        ThreadFactory backgroundFactory = new NamedThreadFactory("Background");
        ThreadFactory scheduledFactory = new NamedThreadFactory("Scheduled");

        // Инициализируем пулы с оптимальными размерами
        this.ttsExecutor = Executors.newFixedThreadPool(2, ttsFactory);
        this.speechRecognitionExecutor = Executors.newFixedThreadPool(2, speechFactory);
        this.audioAnalysisExecutor = Executors.newFixedThreadPool(2, analysisFactory);
        this.backgroundExecutor = Executors.newCachedThreadPool(backgroundFactory);
        this.scheduledExecutor = Executors.newScheduledThreadPool(3, scheduledFactory);

        logger.info("ThreadPoolManager инициализирован");
    }

    /**
     * ThreadFactory с именованными потоками для удобной отладки
     */
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

    // ========== Геттеры для ExecutorService ==========

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

    // ========== Утилитные методы для задержек ==========

    /**
     * Выполнить задачу с задержкой (JavaFX PauseTransition)
     * Рекомендуется для UI операций
     */
    public static void runWithDelay(Runnable task, long delayMillis) {
        PauseTransition pause = new PauseTransition(Duration.millis(delayMillis));
        pause.setOnFinished(event -> task.run());
        pause.play();
    }

    /**
     * Выполнить задачу с задержкой (ScheduledExecutorService)
     * Рекомендуется для не-UI операций
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        checkShutdown();
        return scheduledExecutor.schedule(task, delay, unit);
    }

    /**
     * Выполнить задачу с фиксированной задержкой
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        checkShutdown();
        return scheduledExecutor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    // ========== Методы для graceful shutdown ==========

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
