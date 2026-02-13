package com.mygitgor.chatbot.components;

import com.mygitgor.config.AppConstants;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatMessagesManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessagesManager.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private static final int AVATAR_SIZE = 32;
    private static final int MESSAGE_MAX_WIDTH = 400;
    private static final int TEXT_MAX_WIDTH = 380;
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
                messagesContainer.setStyle("-fx-background-color: #fafafa;");
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
        final LocalTime now = LocalTime.now();

        Platform.runLater(() -> {
            try {
                HBox messageContainer = createMessageContainer(Pos.TOP_RIGHT);

                VBox messageContent = createMessageContent();
                VBox messageBubble = createUserMessageBubble(finalText);
                HBox messageInfo = createMessageInfo(now);

                messageContent.getChildren().addAll(messageBubble, messageInfo);

                StackPane userAvatar = createAvatar("U",
                        Color.web("#27ae60"),
                        Color.WHITE);

                messageContainer.getChildren().addAll(messageContent, userAvatar);
                messagesContainer.getChildren().add(messageContainer);

                logger.debug("Добавлено сообщение пользователя (длина: {})", finalText.length());
                scheduleScrollToBottom();
            } catch (Exception e) {
                logger.error("Ошибка при добавлении сообщения пользователя", e);
            }
        });
    }

    public void addAIMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Попытка добавить пустое сообщение AI");
            return;
        }

        final String finalText = text;
        final LocalTime now = LocalTime.now();

        Platform.runLater(() -> {
            try {
                HBox messageContainer = createMessageContainer(Pos.TOP_LEFT);

                StackPane aiAvatar = createAvatar("AI",
                        Color.web("#3498db"),
                        Color.WHITE);

                VBox messageContent = createMessageContent();
                VBox messageBubble = createAIMessageBubble(finalText);
                Label timeLabel = createTimeLabel(now);

                messageContent.getChildren().addAll(messageBubble, timeLabel);
                messageContainer.getChildren().addAll(aiAvatar, messageContent);
                messagesContainer.getChildren().add(messageContainer);

                logger.debug("Добавлено сообщение AI (длина: {})", finalText.length());
                scheduleScrollToBottom();
            } catch (Exception e) {
                logger.error("Ошибка при добавлении сообщения AI", e);
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
                HBox timeDivider = new HBox();
                timeDivider.setAlignment(Pos.CENTER);
                timeDivider.setSpacing(10);
                timeDivider.setPadding(new Insets(5, 0, 5, 0));

                Region line1 = createDividerLine();
                Region line2 = createDividerLine();

                Label label = new Label(finalTimeText);
                label.setStyle(
                        "-fx-text-fill: #95a5a6; " +
                                "-fx-font-size: 11px; " +
                                "-fx-background-color: white; " +
                                "-fx-padding: 0 10;"
                );

                HBox.setHgrow(line1, Priority.ALWAYS);
                HBox.setHgrow(line2, Priority.ALWAYS);

                timeDivider.getChildren().addAll(line1, label, line2);
                messagesContainer.getChildren().add(timeDivider);

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

    public void addMessagesBatch(java.util.List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < messages.size(); i += BATCH_SIZE) {
                    final int start = i;
                    final int end = Math.min(i + BATCH_SIZE, messages.size());
                    final List<ChatMessage> batch = messages.subList(start, end);

                    Platform.runLater(() -> {
                        for (ChatMessage msg : batch) {
                            if (msg.isUser()) {
                                addUserMessage(msg.getText());
                            } else {
                                addAIMessage(msg.getText());
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

    private HBox createMessageContainer(Pos alignment) {
        HBox container = new HBox();
        container.setSpacing(8);
        container.setAlignment(alignment);
        container.setStyle("-fx-padding: 0 0 5 0;");
        container.setMaxWidth(Double.MAX_VALUE);
        return container;
    }

    private VBox createMessageContent() {
        VBox content = new VBox();
        content.setSpacing(3);
        content.setMaxWidth(Double.MAX_VALUE);
        return content;
    }

    private VBox createUserMessageBubble(String text) {
        VBox bubble = new VBox();
        bubble.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #3498db, #2980b9); " +
                        "-fx-background-radius: 12 12 4 12; " +
                        "-fx-padding: 12 16; " +
                        "-fx-effect: dropshadow(gaussian, rgba(52, 152, 219, 0.2), 4, 0, 0, 2);"
        );
        bubble.setMaxWidth(MESSAGE_MAX_WIDTH);

        Label messageText = new Label(text);
        messageText.setStyle(
                "-fx-text-fill: white; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-family: 'Segoe UI', sans-serif; " +
                        "-fx-wrap-text: true;"
        );
        messageText.setWrapText(true);
        messageText.setMaxWidth(TEXT_MAX_WIDTH);

        bubble.getChildren().add(messageText);
        return bubble;
    }

    private VBox createAIMessageBubble(String text) {
        VBox bubble = new VBox();
        bubble.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #ecf0f1); " +
                        "-fx-background-radius: 12 12 12 4; " +
                        "-fx-border-color: #e0e0e0; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 12 12 12 4; " +
                        "-fx-padding: 12 16; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.05), 3, 0, 0, 1);"
        );
        bubble.setMaxWidth(MESSAGE_MAX_WIDTH);

        Label messageText = new Label(text);
        messageText.setStyle(
                "-fx-text-fill: #2c3e50; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-family: 'Segoe UI', sans-serif; " +
                        "-fx-wrap-text: true;"
        );
        messageText.setWrapText(true);
        messageText.setMaxWidth(TEXT_MAX_WIDTH);

        bubble.getChildren().add(messageText);
        return bubble;
    }

    private HBox createMessageInfo(LocalTime time) {
        HBox info = new HBox();
        info.setAlignment(Pos.CENTER_RIGHT);
        info.setSpacing(8);

        Label timeLabel = createTimeLabel(time);

        Label statusLabel = new Label("✔");
        statusLabel.setStyle(
                "-fx-text-fill: #27ae60; " +
                        "-fx-font-size: 11px;"
        );

        info.getChildren().addAll(timeLabel, statusLabel);
        return info;
    }

    private Label createTimeLabel(LocalTime time) {
        Label timeLabel = new Label(time.format(TIME_FORMATTER));
        timeLabel.setStyle(
                "-fx-text-fill: #7f8c8d; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-style: italic;"
        );
        return timeLabel;
    }

    private StackPane createAvatar(String text, Color bgColor, Color textColor) {
        StackPane avatar = new StackPane();
        avatar.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setStyle(
                String.format("-fx-background-color: #%02x%02x%02x; -fx-background-radius: 50%%; -fx-alignment: center;",
                        (int)(bgColor.getRed() * 255),
                        (int)(bgColor.getGreen() * 255),
                        (int)(bgColor.getBlue() * 255))
        );

        Label label = new Label(text);
        label.setStyle(
                String.format("-fx-text-fill: #%02x%02x%02x; -fx-font-size: 14px; -fx-font-weight: bold;",
                        (int)(textColor.getRed() * 255),
                        (int)(textColor.getGreen() * 255),
                        (int)(textColor.getBlue() * 255))
        );

        avatar.getChildren().add(label);
        return avatar;
    }

    private Region createDividerLine() {
        Region line = new Region();
        line.setPrefHeight(1);
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color: #e0e0e0;");
        HBox.setHgrow(line, Priority.ALWAYS);
        return line;
    }

    public static class ChatMessage {
        private final String text;
        private final boolean isUser;
        private final LocalTime timestamp;

        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
            this.timestamp = LocalTime.now();
        }

        public ChatMessage(String text, boolean isUser, LocalTime timestamp) {
            this.text = text;
            this.isUser = isUser;
            this.timestamp = timestamp;
        }

        public String getText() { return text; }
        public boolean isUser() { return isUser; }
        public LocalTime getTimestamp() { return timestamp; }
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
        Platform.runLater(() -> {
            for (javafx.scene.Node node : messagesContainer.getChildren()) {
                if (node instanceof HBox) {
                    sb.append(node.toString()).append("\n");
                }
            }
        });
        return sb.toString();
    }
}