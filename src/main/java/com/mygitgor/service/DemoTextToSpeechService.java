package com.mygitgor.service;

import com.mygitgor.service.interfaces.ITTSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

public class DemoTextToSpeechService implements ITTSService {
    private static final Logger logger = LoggerFactory.getLogger(DemoTextToSpeechService.class);

    private volatile boolean closed = false;

    public DemoTextToSpeechService() {
        logger.info("Инициализирован демо-режим TTS (без реальной озвучки)");
    }

    @Override
    public CompletableFuture<Void> speakAsync(String text) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Demo TTS сервис закрыт"));
        }

        logger.info("🔊 [ДЕМО-РЕЖИМ] Озвучка текста ({} символов): {}",
                text != null ? text.length() : 0,
                text != null ? text.substring(0, Math.min(50, text.length())) + "..." : "null");

        // Имитируем задержку озвучки
        return CompletableFuture.runAsync(() -> {
            try {
                // Имитация времени озвучки (примерно 100 символов в секунду)
                int duration = text != null ? Math.max(500, text.length() * 5) : 500;
                Thread.sleep(Math.min(duration, 3000));
                logger.debug("✅ [ДЕМО-РЕЖИМ] Озвучка завершена");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[ДЕМО-РЕЖИМ] Озвучка прервана");
            }
        });
    }

    @Override
    public void stopSpeaking() {
        logger.info("⏹️ [ДЕМО-РЕЖИМ] Остановка озвучки");
    }

    @Override
    public boolean isAvailable() {
        return !closed;
    }

    @Override
    public void close() {
        if (closed) return;

        logger.info("Закрытие демо-режима TTS...");
        closed = true;
        logger.info("Демо-режим TTS закрыт");
    }

}
