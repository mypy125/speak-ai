package com.mygitgor.chatbot.components;

import com.mygitgor.service.interfaces.ITTSService;
import com.mygitgor.utils.HtmlUtils;
import com.mygitgor.utils.ThreadPoolManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import javafx.concurrent.Worker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.*;

public class ChatMessagesManager {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessagesManager.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private static final int AVATAR_SIZE = 32;
    private static final int MESSAGE_MAX_WIDTH = 400;
    private static final int TEXT_MAX_WIDTH = 380;
    private static final int SCROLL_DELAY_MS = 50;
    private static final int BATCH_SIZE = 10;
    private static final int WEBVIEW_MIN_HEIGHT = 50;
    private static final int WEBVIEW_MAX_HEIGHT = 600;
    private static final int WEBVIEW_LOAD_TIMEOUT = 2000;

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
        final LocalTime now = LocalTime.now();

        Platform.runLater(() -> {
            try {
                HBox messageContainer = createMessageContainer(Pos.TOP_RIGHT);

                VBox messageContent = createMessageContent();
                VBox messageBubble = createUserMessageBubble(finalText);
                HBox messageInfo = createMessageInfo(now);

                messageContent.getChildren().addAll(messageBubble, messageInfo);

                StackPane userAvatar = createAvatar("U", "user");
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
        addAIMessage(text, null);
    }

    public void addAIMessage(String text, String ttsText) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Попытка добавить пустое сообщение AI");
            return;
        }

        final String finalText = text;
        final String finalTtsText = ttsText;
        final LocalTime now = LocalTime.now();

        Platform.runLater(() -> {
            try {
                HBox messageContainer = createMessageContainer(Pos.TOP_LEFT);
                StackPane aiAvatar = createAvatar("AI", "ai");
                VBox messageContent = createMessageContent();

                VBox messageBubble = createAIMessageBubble(finalText);

                if (finalTtsText != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("tts", finalTtsText);
                    data.put("display", finalText);
                    data.put("type", "text");
                    data.put("timestamp", now);
                    messageBubble.setUserData(data);
                }

                Label timeLabel = createTimeLabel(now);

                messageContent.getChildren().addAll(messageBubble, timeLabel);
                messageContainer.getChildren().addAll(aiAvatar, messageContent);
                messagesContainer.getChildren().add(messageContainer);

                logger.debug("Добавлено текстовое сообщение AI (длина: {}, TTS: {})",
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
        final LocalTime now = LocalTime.now();

        Platform.runLater(() -> {
            try {
                HBox messageContainer = createMessageContainer(Pos.TOP_LEFT);
                StackPane aiAvatar = createAvatar("AI", "ai");
                VBox messageContent = createMessageContent();

                VBox messageBubble = createAIMessageBubbleWithHTML(finalHtml);

                if (finalTtsText != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("tts", finalTtsText);
                    data.put("html", finalHtml);
                    data.put("type", "html");
                    data.put("timestamp", now);
                    messageBubble.setUserData(data);
                }

                Label timeLabel = createTimeLabel(now);

                messageContent.getChildren().addAll(messageBubble, timeLabel);
                messageContainer.getChildren().addAll(aiAvatar, messageContent);
                messagesContainer.getChildren().add(messageContainer);

                logger.debug("Добавлено HTML сообщение AI (длина: {})", finalHtml.length());
                scheduleScrollToBottom();
            } catch (Exception e) {
                logger.error("Ошибка при добавлении HTML сообщения", e);
                // В случае ошибки показываем как обычный текст
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
                HBox timeDivider = new HBox();
                timeDivider.getStyleClass().add("time-divider");

                Region line1 = createDividerLine();
                Region line2 = createDividerLine();

                Label label = new Label(finalTimeText);
                label.getStyleClass().add("divider-label");

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

    public CompletableFuture<Void> speakAllMessagesAsync(ITTSService ttsService) {
        if (ttsService == null) {
            logger.error("TTS сервис не инициализирован");
            return CompletableFuture.failedFuture(new IllegalStateException("TTS service not available"));
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < messagesContainer.getChildren().size(); i++) {
            int index = i;
            getTtsTextForMessage(i).ifPresent(ttsText -> {
                CompletableFuture<Void> future = ttsService.speakAsync(ttsText)
                        .thenRun(() -> logger.debug("Озвучено сообщение {}", index))
                        .exceptionally(throwable -> {
                            logger.error("Ошибка при озвучивании сообщения {}: {}",
                                    index, throwable.getMessage());
                            return null;
                        });
                futures.add(future);
            });
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.debug("Все сообщения озвучены"));
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
        if (node instanceof HBox) {
            HBox hbox = (HBox) node;
            for (Node child : hbox.getChildren()) {
                if (child instanceof VBox) {
                    VBox vbox = (VBox) child;
                    for (Node contentChild : vbox.getChildren()) {
                        if (contentChild instanceof VBox) {
                            VBox bubble = (VBox) contentChild;
                            Object userData = bubble.getUserData();
                            if (userData instanceof Map) {
                                Map<?, ?> data = (Map<?, ?>) userData;
                                Object tts = data.get("tts");
                                if (tts instanceof String) {
                                    return Optional.of((String) tts);
                                }
                            }
                        } else if (contentChild instanceof Label) {
                            // Для обычных текстовых сообщений без userData
                            Label label = (Label) contentChild;
                            String text = label.getText();
                            if (text != null && !text.isEmpty()) {
                                return Optional.of(text);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void highlightMessage(int index) {
        if (index < 0 || index >= messagesContainer.getChildren().size()) {
            return;
        }

        Node node = messagesContainer.getChildren().get(index);
        String originalStyle = node.getStyle();

        node.setStyle(originalStyle + "-fx-effect: dropshadow(gaussian, #3498db, 10, 0, 0, 0);");

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> node.setStyle(originalStyle));
        pause.play();
    }

    private void highlightLastMessage() {
        highlightMessage(messagesContainer.getChildren().size() - 1);
    }

    private HBox createMessageContainer(Pos alignment) {
        HBox container = new HBox();
        container.setSpacing(8);
        container.setAlignment(alignment);
        container.setPadding(new Insets(0, 0, 5, 0));
        container.setMaxWidth(Double.MAX_VALUE);

        String styleClass = alignment == Pos.TOP_LEFT ?
                "message-container-left" : "message-container-right";
        container.getStyleClass().addAll("message-container", styleClass);

        return container;
    }

    private VBox createMessageContent() {
        VBox content = new VBox();
        content.setSpacing(3);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getStyleClass().add("message-content");
        return content;
    }

    private VBox createUserMessageBubble(String text) {
        VBox bubble = new VBox();
        bubble.getStyleClass().addAll("message-bubble", "message-bubble-user");
        bubble.setMaxWidth(MESSAGE_MAX_WIDTH);

        Label messageText = new Label(text);
        messageText.getStyleClass().add("message-text-user");
        messageText.setWrapText(true);
        messageText.setMaxWidth(TEXT_MAX_WIDTH);

        setupCopyHandler(bubble, text);

        bubble.getChildren().add(messageText);
        return bubble;
    }

    private VBox createAIMessageBubble(String text) {
        VBox bubble = new VBox();
        bubble.getStyleClass().addAll("message-bubble", "message-bubble-ai");
        bubble.setMaxWidth(MESSAGE_MAX_WIDTH);

        Label messageText = new Label(text);
        messageText.getStyleClass().add("message-text-ai");
        messageText.setWrapText(true);
        messageText.setMaxWidth(TEXT_MAX_WIDTH);

        setupCopyHandler(bubble, text);

        bubble.getChildren().add(messageText);
        return bubble;
    }

    private VBox createAIMessageBubbleWithHTML(String htmlContent) {
        VBox bubble = new VBox();
        bubble.getStyleClass().add("webview-bubble");
        bubble.setMaxWidth(MESSAGE_MAX_WIDTH);

        WebView webView = new WebView();
        webView.getStyleClass().add("webview");

        WebEngine webEngine = webView.getEngine();

        webView.setContextMenuEnabled(false);
        webView.setFocusTraversable(false);

        webEngine.loadContent(htmlContent);

        webView.setPrefHeight(WEBVIEW_MIN_HEIGHT);
        webView.setPrefWidth(MESSAGE_MAX_WIDTH - 40);

        final long startTime = System.currentTimeMillis();

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                long loadTime = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    try {
                        if (loadTime > WEBVIEW_LOAD_TIMEOUT) {
                            logger.warn("WebView загружался слишком долго ({}мс)", loadTime);
                            return;
                        }

                        Object result = webEngine.executeScript(
                                "document.documentElement.scrollHeight"
                        );

                        if (result instanceof Number) {
                            double height = ((Number) result).doubleValue();
                            height = Math.min(height + 20, WEBVIEW_MAX_HEIGHT);
                            if (height > WEBVIEW_MIN_HEIGHT) {
                                webView.setPrefHeight(height);
                                logger.trace("WebView height: {}px (loaded in {}ms)", height, loadTime);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Ошибка при調整 высоты WebView: {}", e.getMessage());
                    }
                });
            }
        });

        setupCopyHandler(bubble, HtmlUtils.stripHtml(htmlContent));

        bubble.getChildren().add(webView);
        return bubble;
    }

    private HBox createMessageInfo(LocalTime time) {
        HBox info = new HBox();
        info.getStyleClass().add("message-info");

        Label timeLabel = createTimeLabel(time);
        Label statusLabel = new Label("✔");
        statusLabel.getStyleClass().add("status-label");

        info.getChildren().addAll(timeLabel, statusLabel);
        return info;
    }

    private Label createTimeLabel(LocalTime time) {
        Label timeLabel = new Label(time.format(TIME_FORMATTER));
        timeLabel.getStyleClass().add("time-label");
        return timeLabel;
    }

    private StackPane createAvatar(String text, String type) {
        StackPane avatar = new StackPane();
        avatar.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.getStyleClass().addAll("avatar", "avatar-" + type);

        Label label = new Label(text);
        label.getStyleClass().add("avatar-label");

        avatar.getChildren().add(label);
        return avatar;
    }

    private Region createDividerLine() {
        Region line = new Region();
        line.getStyleClass().add("divider-line");
        HBox.setHgrow(line, Priority.ALWAYS);
        return line;
    }

    private void setupCopyHandler(VBox bubble, String content) {
        bubble.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(content);
                clipboard.setContent(clipboardContent);

                String originalStyle = bubble.getStyle();
                bubble.setStyle(originalStyle + "-fx-effect: dropshadow(gaussian, #3498db, 10, 0, 0, 0);");

                PauseTransition pause = new PauseTransition(Duration.millis(200));
                pause.setOnFinished(e -> bubble.setStyle(originalStyle));
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

    public String exportChatForTtsWithPauses(long pauseMs) {
        StringBuilder sb = new StringBuilder();
        int messageCount = 0;

        for (Node node : messagesContainer.getChildren()) {
            Optional<String> ttsOpt = extractTtsFromNode(node);
            if (ttsOpt.isPresent()) {
                if (messageCount > 0) {
                    sb.append("<break time=\"").append(pauseMs).append("ms\"/> ");
                }
                sb.append(ttsOpt.get()).append(" ");
                messageCount++;
            }
        }

        return sb.toString();
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