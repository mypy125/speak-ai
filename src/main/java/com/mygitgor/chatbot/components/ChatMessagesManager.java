package com.mygitgor.chatbot.components;

import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.utils.HtmlUtils;
import com.mygitgor.utils.ThreadPoolManager;
import com.mygitgor.view.components.MessageComponentLoader;
import com.mygitgor.view.components.MessageTypeDetector;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.*;

public class ChatMessagesManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessagesManager.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private static final int SCROLL_DELAY_MS = 50;
    private static final int BATCH_SIZE = 10;

    private final VBox messagesContainer;
    private final ScrollPane scrollPane;

    private final AtomicBoolean isScrolling = new AtomicBoolean(false);
    private final AtomicBoolean needsScroll = new AtomicBoolean(false);

    private final ThreadPoolManager threadPoolManager;
    private final ScheduledExecutorService scheduledExecutor;

    public ChatMessagesManager(VBox messagesContainer, ScrollPane scrollPane) {
        this.messagesContainer = messagesContainer;
        this.scrollPane = scrollPane;

        this.threadPoolManager = ThreadPoolManager.getInstance();
        this.scheduledExecutor = threadPoolManager.getScheduledExecutor();

        initializeContainer();
    }

    private void initializeContainer() {
        if (messagesContainer != null) {
            Platform.runLater(() -> {
                messagesContainer.getStyleClass().add("chat-container");
                messagesContainer.setFillWidth(true);
            });
        }
    }

    public void addUserMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Попытка добавить пустое сообщение пользователя");
            return;
        }

        final String finalText = text;
        final String timeStr = LocalTime.now().format(TIME_FORMATTER);

        Platform.runLater(() -> {
            try {
                HBox messageContainer = MessageComponentLoader.createUserMessage(finalText, timeStr);
                messagesContainer.getChildren().add(messageContainer);

                setupCopyHandler(messageContainer, finalText);

                logger.debug("Добавлено сообщение пользователя (длина: {})", finalText.length());
                scheduleScrollToBottom();
            } catch (Exception e) {
                logger.error("Ошибка при добавлении сообщения пользователя", e);
            }
        });
    }

    public void addAIMessage(String text) {
        addAIMessage(text, null);
    }

    public void addAIMessage(String text, String ttsText) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Попытка добавить пустое сообщение AI");
            return;
        }

        final String finalText = text;
        final String finalTtsText = ttsText;
        final String timeStr = LocalTime.now().format(TIME_FORMATTER);

        Platform.runLater(() -> {
            try {
                HBox messageContainer;

                if (MessageTypeDetector.needsHtmlRendering(finalText)) {
                    messageContainer = MessageComponentLoader.createAiHtmlMessage(finalText, timeStr, finalTtsText);
                } else {
                    messageContainer = MessageComponentLoader.createAiMessage(finalText, timeStr, finalTtsText);
                }

                messagesContainer.getChildren().add(messageContainer);
                setupCopyHandler(messageContainer, finalText);

                logger.debug("Добавлено сообщение AI (длина: {}, TTS: {})",
                        finalText.length(), finalTtsText != null ? "да" : "нет");
                scheduleScrollToBottom();
            } catch (Exception e) {
                logger.error("Ошибка при добавлении сообщения AI", e);
            }
        });
    }

    public void addAIMessageWithHtml(String htmlContent) {
        addAIMessageWithHtml(htmlContent, null);
    }

    public void addAIMessageWithHtml(String htmlContent, String ttsText) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            logger.warn("Попытка добавить пустое HTML сообщение");
            addAIMessage("", ttsText);
            return;
        }

        final String finalHtml = htmlContent;
        final String finalTtsText = ttsText;
        final String timeStr = LocalTime.now().format(TIME_FORMATTER);

        Platform.runLater(() -> {
            try {
                HBox messageContainer = MessageComponentLoader.createAiHtmlMessage(finalHtml, timeStr, finalTtsText);
                messagesContainer.getChildren().add(messageContainer);
                setupCopyHandler(messageContainer, HtmlUtils.stripHtml(finalHtml));

                logger.debug("Добавлено HTML сообщение AI (длина: {})", finalHtml.length());
                scheduleScrollToBottom();
            } catch (Exception e) {
                logger.error("Ошибка при добавлении HTML сообщения", e);
                addAIMessage(HtmlUtils.stripHtml(htmlContent), ttsText);
            }
        });
    }

    public void addTimeDivider(String timeText) {
        if (timeText == null || timeText.trim().isEmpty()) {
            return;
        }

        final String finalTimeText = timeText;

        Platform.runLater(() -> {
            try {
                HBox divider = MessageComponentLoader.createTimeDivider(finalTimeText);
                messagesContainer.getChildren().add(divider);
                logger.debug("Добавлен разделитель времени: {}", finalTimeText);
            } catch (Exception e) {
                logger.error("Ошибка при добавлении разделителя времени", e);
            }
        });
    }

    public void clear() {
        Platform.runLater(() -> {
            messagesContainer.getChildren().clear();
            logger.info("Чат очищен");
        });
    }

    public void addMessagesBatch(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < messages.size(); i += BATCH_SIZE) {
                    final int end = Math.min(i + BATCH_SIZE, messages.size());
                    final List<ChatMessage> batch = messages.subList(i, end);

                    Platform.runLater(() -> {
                        for (ChatMessage msg : batch) {
                            if (msg.isUser()) {
                                addUserMessage(msg.getText());
                            } else {
                                if (msg.isHtml()) {
                                    addAIMessageWithHtml(msg.getText(), msg.getTtsText());
                                } else {
                                    addAIMessage(msg.getText(), msg.getTtsText());
                                }
                            }
                        }
                    });

                    if (i + BATCH_SIZE < messages.size()) {
                        Thread.sleep(50);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Загрузка истории прервана");
            }
        }, threadPoolManager.getBackgroundExecutor());
    }

    public Optional<String> getTtsTextForMessage(int index) {
        if (index < 0 || index >= messagesContainer.getChildren().size()) {
            return Optional.empty();
        }

        Node node = messagesContainer.getChildren().get(index);
        return extractTtsFromNode(node);
    }

    public Optional<String> getLastMessageTts() {
        if (messagesContainer.getChildren().isEmpty()) {
            return Optional.empty();
        }
        return getTtsTextForMessage(messagesContainer.getChildren().size() - 1);
    }

    public CompletableFuture<Void> speakMessageAsync(int index, ITTSService ttsService) {
        if (ttsService == null) {
            logger.error("TTS сервис не инициализирован");
            return CompletableFuture.failedFuture(new IllegalStateException("TTS service not available"));
        }

        return getTtsTextForMessage(index)
                .map(ttsText -> {
                    highlightMessage(index);
                    logger.debug("Асинхронное озвучивание сообщения {}", index);
                    return ttsService.speakAsync(ttsText)
                            .thenRun(() -> logger.debug("Озвучивание сообщения {} завершено", index))
                            .exceptionally(throwable -> {
                                logger.error("Ошибка при озвучивании сообщения {}: {}",
                                        index, throwable.getMessage());
                                return null;
                            });
                })
                .orElseGet(() -> {
                    logger.warn("Нет TTS текста для сообщения {}", index);
                    return CompletableFuture.completedFuture(null);
                });
    }

    public CompletableFuture<Void> speakLastMessageAsync(ITTSService ttsService) {
        if (messagesContainer.getChildren().isEmpty()) {
            logger.warn("Нет сообщений для озвучивания");
            return CompletableFuture.completedFuture(null);
        }
        return speakMessageAsync(messagesContainer.getChildren().size() - 1, ttsService);
    }

    public void speakMessage(int index, ITTSService ttsService) {
        if (ttsService == null) {
            logger.error("TTS сервис не инициализирован");
            return;
        }

        getTtsTextForMessage(index).ifPresentOrElse(
                ttsText -> {
                    try {
                        ttsService.speakAsync(ttsText).join();
                        highlightMessage(index);
                        logger.debug("Озвучивание сообщения {} завершено", index);
                    } catch (Exception e) {
                        logger.error("Ошибка при озвучивании сообщения {}: {}", index, e.getMessage());
                    }
                },
                () -> logger.warn("Нет TTS текста для сообщения {}", index)
        );
    }

    public void speakLastMessage(ITTSService ttsService) {
        if (messagesContainer.getChildren().isEmpty()) {
            logger.warn("Нет сообщений для озвучивания");
            return;
        }
        speakMessage(messagesContainer.getChildren().size() - 1, ttsService);
    }

    public void stopSpeaking(ITTSService ttsService) {
        if (ttsService != null) {
            ttsService.stopSpeaking();
            logger.debug("Озвучивание остановлено");
        }
    }

    public boolean isTtsAvailable(ITTSService ttsService) {
        return ttsService != null && ttsService.isAvailable();
    }

    private Optional<String> extractTtsFromNode(Node node) {
        Object userData = node.getUserData();
        if (userData instanceof Map) {
            Map<?, ?> data = (Map<?, ?>) userData;
            Object tts = data.get("tts");
            if (tts instanceof String) {
                return Optional.of((String) tts);
            }
        }
        return Optional.empty();
    }

    private void highlightMessage(int index) {
        if (index < 0 || index >= messagesContainer.getChildren().size()) {
            return;
        }

        Node node = messagesContainer.getChildren().get(index);
        node.getStyleClass().add("message-highlight");

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> node.getStyleClass().remove("message-highlight"));
        pause.play();
    }

    private void setupCopyHandler(HBox container, String content) {
        container.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(content);
                clipboard.setContent(clipboardContent);

                container.getStyleClass().add("message-highlight");
                PauseTransition pause = new PauseTransition(Duration.millis(200));
                pause.setOnFinished(e -> container.getStyleClass().remove("message-highlight"));
                pause.play();

                logger.debug("Сообщение скопировано в буфер обмена");
            }
        });
    }

    private void scheduleScrollToBottom() {
        if (scrollPane == null) {
            return;
        }

        needsScroll.set(true);

        if (isScrolling.compareAndSet(false, true)) {
            scheduledExecutor.schedule(() -> {
                if (needsScroll.getAndSet(false)) {
                    performScrollToBottom();
                }
                isScrolling.set(false);
            }, SCROLL_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void performScrollToBottom() {
        if (scrollPane == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                scrollPane.setVvalue(1.0);
                logger.trace("Прокрутка вниз выполнена");
            } catch (Exception e) {
                logger.error("Ошибка при прокрутке", e);
            }
        });
    }

    public int getMessageCount() {
        return messagesContainer.getChildren().size();
    }

    public boolean isEmpty() {
        return messagesContainer.getChildren().isEmpty();
    }

    public void forceScrollToBottom() {
        needsScroll.set(true);
        performScrollToBottom();
    }

    public String exportMessages() {
        StringBuilder sb = new StringBuilder();
        int messageCount = 0;

        for (Node node : messagesContainer.getChildren()) {
            Optional<String> textOpt = extractTtsFromNode(node);
            if (textOpt.isPresent()) {
                sb.append("Message ").append(++messageCount).append(": ");
                sb.append(textOpt.get()).append("\n\n");
            }
        }

        return sb.toString();
    }

    public String exportChatForTts() {
        return exportMessages();
    }

    public static class ChatMessage {
        private final String id;
        private final String text;
        private final String ttsText;
        private final boolean isUser;
        private final LocalTime timestamp;
        private final MessageType type;

        public enum MessageType {
            PLAIN_TEXT,
            HTML,
            MARKDOWN
        }

        public ChatMessage(String text, boolean isUser) {
            this(UUID.randomUUID().toString(), text, null, isUser,
                    LocalTime.now(), detectType(text));
        }

        public ChatMessage(String text, String ttsText, boolean isUser) {
            this(UUID.randomUUID().toString(), text, ttsText, isUser,
                    LocalTime.now(), detectType(text));
        }

        public ChatMessage(String id, String text, String ttsText,
                           boolean isUser, LocalTime timestamp, MessageType type) {
            this.id = id;
            this.text = text;
            this.ttsText = ttsText;
            this.isUser = isUser;
            this.timestamp = timestamp;
            this.type = type;
        }

        private static MessageType detectType(String text) {
            if (text == null) return MessageType.PLAIN_TEXT;
            if (text.contains("<") && text.contains(">") &&
                    (text.contains("<div") || text.contains("<p") || text.contains("<h"))) {
                return MessageType.HTML;
            }
            return MessageType.PLAIN_TEXT;
        }

        public String getId() { return id; }
        public String getText() { return text; }
        public String getTtsText() { return ttsText; }
        public boolean isUser() { return isUser; }
        public LocalTime getTimestamp() { return timestamp; }
        public MessageType getType() { return type; }
        public boolean hasTts() { return ttsText != null && !ttsText.isEmpty(); }
        public boolean isHtml() { return type == MessageType.HTML; }
    }
}