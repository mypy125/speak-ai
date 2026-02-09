package com.mygitgor.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class TextToSpeechService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechService.class);

    private volatile boolean closed = false;

    public TextToSpeechService() {
        logger.info("TextToSpeechService инициализирован");
    }

    public void speak(String text) {
        if (closed) {
            throw new IllegalStateException("TextToSpeechService закрыт");
        }

        if (text == null || text.trim().isEmpty()) {
            logger.warn("Пустой текст для озвучки");
            return;
        }

        logger.info("Озвучка текста (длина: {} символов)", text.length());

        try {
            // В реальном приложении здесь был бы вызов TTS API (Google, Azure, и т.д.)
            // Для демо используем системную озвучку или заглушку

            // Попробуем использовать Java Speech API если доступно
            trySystemTTS(text);

        } catch (Exception e) {
            logger.error("Ошибка при озвучке текста", e);
            // В демо-режиме просто логируем
            System.out.println("ДЕМО: Озвучка текста: " +
                    text.substring(0, Math.min(50, text.length())) + "...");
        }
    }

    private void trySystemTTS(String text) {
        // Попытка использования системного TTS
        try {
            // Очищаем текст от markdown и форматирования для озвучки
            String cleanText = cleanTextForSpeech(text);

            // Для Windows можно использовать голосовую строку
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows - используем PowerShell или команду
                executeWindowsTTS(cleanText);
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                // macOS - используем say команду
                executeMacTTS(cleanText);
            } else {
                // Linux - используем espeak или festival
                executeLinuxTTS(cleanText);
            }

        } catch (Exception e) {
            logger.warn("Системный TTS недоступен: {}", e.getMessage());
            // Заглушка для демо
            logger.info("ДЕМО ОЗВУЧКА: {}", text.substring(0, Math.min(100, text.length())));
        }
    }

    private String cleanTextForSpeech(String text) {
        // Убираем markdown, эмодзи и форматирование
        return text
                .replaceAll("#+", "") // заголовки
                .replaceAll("\\*\\*", "") // жирный
                .replaceAll("\\*", "") // курсив
                .replaceAll("`", "") // код
                .replaceAll("\\[.*?\\]\\(.*?\\)", "") // ссылки
                .replaceAll("\\n+", ". ") // переносы строк
                .replaceAll("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️]", "") // эмодзи
                .replaceAll("\\s+", " ") // множественные пробелы
                .trim();
    }

    private void executeWindowsTTS(String text) throws IOException {
        // Для Windows можно использовать PowerShell
        String command = "powershell.exe -Command \"Add-Type -AssemblyName System.speech; " +
                "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$speak.Speak('" + text.replace("'", "''") + "');\"";

        Process process = Runtime.getRuntime().exec(command);
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Озвучка прервана", e);
        }
    }

    private void executeMacTTS(String text) throws IOException {
        // macOS команда say
        Process process = Runtime.getRuntime().exec(new String[]{"say", text});
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Озвучка прервана", e);
        }
    }

    private void executeLinuxTTS(String text) throws IOException {
        // Linux - пытаемся использовать espeak или festival
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"espeak", text});
            process.waitFor();
        } catch (Exception e1) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"festival", "--tts"});
                process.getOutputStream().write(text.getBytes());
                process.getOutputStream().close();
                process.waitFor();
            } catch (Exception e2) {
                throw new IOException("Ни espeak, ни festival не доступны");
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие TextToSpeechService...");
        logger.info("TextToSpeechService закрыт");
    }
}
