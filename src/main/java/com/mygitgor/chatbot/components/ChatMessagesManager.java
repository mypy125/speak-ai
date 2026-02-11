package com.mygitgor.chatbot.components;

import com.mygitgor.config.AppConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class ChatMessagesManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessagesManager.class);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a");

    private final VBox messagesContainer;
    private final ScrollPane scrollPane;

    public ChatMessagesManager(VBox messagesContainer, ScrollPane scrollPane) {
        this.messagesContainer = messagesContainer;
        this.scrollPane = scrollPane;

        if (messagesContainer != null) {
            messagesContainer.setStyle("-fx-background-color: #fafafa;");
        }
    }

    public void addUserMessage(String text) {
        Platform.runLater(() -> {
            HBox messageContainer = createMessageContainer(Pos.TOP_RIGHT);

            VBox messageContent = createMessageContent();
            VBox messageBubble = createUserMessageBubble(text);
            HBox messageInfo = createMessageInfo();

            messageContent.getChildren().addAll(messageBubble, messageInfo);

            StackPane userAvatar = createAvatar("U",
                    Color.web("#27ae60"),
                    Color.WHITE);

            messageContainer.getChildren().addAll(messageContent, userAvatar);
            messagesContainer.getChildren().add(messageContainer);

            safeScrollToBottom();
        });
    }

    public void addAIMessage(String text) {
        Platform.runLater(() -> {
            HBox messageContainer = createMessageContainer(Pos.TOP_LEFT);

            StackPane aiAvatar = createAvatar("AI",
                    Color.web("#3498db"),
                    Color.WHITE);

            VBox messageContent = createMessageContent();
            VBox messageBubble = createAIMessageBubble(text);
            Label timeLabel = createTimeLabel();

            messageContent.getChildren().addAll(messageBubble, timeLabel);
            messageContainer.getChildren().addAll(aiAvatar, messageContent);
            messagesContainer.getChildren().add(messageContainer);

            safeScrollToBottom();
        });
    }

    public void addTimeDivider(String timeText) {
        Platform.runLater(() -> {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setSpacing(10);
            timeDivider.setPadding(new Insets(5, 0, 5, 0));

            Region line1 = createDividerLine();
            Region line2 = createDividerLine();

            Label label = new Label(timeText);
            label.setStyle(
                    "-fx-text-fill: #95a5a6; " +
                            "-fx-font-size: 11px; " +
                            "-fx-background-color: white; " +
                            "-fx-padding: 0 10;"
            );

            HBox.setHgrow(line1, javafx.scene.layout.Priority.ALWAYS);
            HBox.setHgrow(line2, javafx.scene.layout.Priority.ALWAYS);

            timeDivider.getChildren().addAll(line1, label, line2);
            messagesContainer.getChildren().add(timeDivider);
        });
    }

    public void clear() {
        Platform.runLater(() -> {
            messagesContainer.getChildren().clear();
            logger.info("Чат очищен");
        });
    }

    // ========================================
    // Private helper methods
    // ========================================

    private HBox createMessageContainer(Pos alignment) {
        HBox container = new HBox();
        container.setSpacing(8);
        container.setAlignment(alignment);
        container.setStyle("-fx-padding: 0 0 5 0;");
        return container;
    }

    private VBox createMessageContent() {
        VBox content = new VBox();
        content.setSpacing(3);
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
        bubble.setMaxWidth(400);

        Label messageText = new Label(text);
        messageText.setStyle(
                "-fx-text-fill: white; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-family: 'Segoe UI', sans-serif; " +
                        "-fx-wrap-text: true;"
        );
        messageText.setWrapText(true);
        messageText.setMaxWidth(380);

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
        bubble.setMaxWidth(400);

        Label messageText = new Label(text);
        messageText.setStyle(
                "-fx-text-fill: #2c3e50; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-family: 'Segoe UI', sans-serif; " +
                        "-fx-wrap-text: true;"
        );
        messageText.setWrapText(true);
        messageText.setMaxWidth(380);

        bubble.getChildren().add(messageText);
        return bubble;
    }

    private HBox createMessageInfo() {
        HBox info = new HBox();
        info.setAlignment(Pos.CENTER_RIGHT);
        info.setSpacing(8);

        Label timeLabel = createTimeLabel();

        Label statusLabel = new Label("✔");
        statusLabel.setStyle(
                "-fx-text-fill: #27ae60; " +
                        "-fx-font-size: 11px;"
        );

        info.getChildren().addAll(timeLabel, statusLabel);
        return info;
    }

    private Label createTimeLabel() {
        Label timeLabel = new Label(LocalTime.now().format(TIME_FORMATTER));
        timeLabel.setStyle(
                "-fx-text-fill: #7f8c8d; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-style: italic;"
        );
        return timeLabel;
    }

    private StackPane createAvatar(String text, Color bgColor, Color textColor) {
        StackPane avatar = new StackPane();
        avatar.setPrefSize(32, 32);
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);
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
        return line;
    }

    private void safeScrollToBottom() {
        if (scrollPane == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(AppConstants.SCROLL_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            Platform.runLater(() -> {
                scrollPane.setVvalue(1.0);
            });
        });
    }
}
