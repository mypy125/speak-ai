package com.mygitgor.view.components;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.shape.Line;

public class MessageComponentLoader {
    private static final Logger logger = LoggerFactory.getLogger(MessageComponentLoader.class);

    private static final String COMPONENTS_FXML = "/com/mygitgor/view/fxml/message-components.fxml";
    private static MessageComponentsController componentsController;
    private static boolean useFxml = true;

    static {
        loadComponents();
    }

    private static void loadComponents() {
        try {
            URL fxmlUrl = MessageComponentLoader.class.getResource(COMPONENTS_FXML);
            if (fxmlUrl == null) {
                logger.error("FXML файл не найден: {}", COMPONENTS_FXML);
                useFxml = false;
                logger.info("Используются программно созданные компоненты");
                return;
            }

            logger.info("Загрузка FXML из: {}", fxmlUrl);
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.load();
            componentsController = loader.getController();

            if (componentsController == null) {
                logger.error("Controller is null after loading FXML");
                useFxml = false;
            } else {
                logger.info("Компоненты сообщений загружены из {}", COMPONENTS_FXML);
                useFxml = true;
            }
        } catch (IOException e) {
            logger.error("Ошибка при загрузке компонентов сообщений", e);
            useFxml = false;
            logger.info("Используются программно созданные компоненты после ошибки");
        }
    }

    public static HBox createUserMessage(String text, String time) {
        if (useFxml && componentsController != null) {
            HBox template = componentsController.getUserMessageTemplate();
            if (template != null) {
                return cloneAndFillUserMessage(template, text, time);
            }
        }
        return createFallbackUserMessage(text, time);
    }

    public static HBox createAiMessage(String text, String time) {
        return createAiMessage(text, time, null);
    }

    public static HBox createAiMessage(String text, String time, String ttsText) {
        if (useFxml && componentsController != null) {
            HBox template = componentsController.getAiMessageTemplate();
            if (template != null) {
                return cloneAndFillAiMessage(template, text, time, ttsText);
            }
        }
        return createFallbackAiMessage(text, time, ttsText);
    }

    public static HBox createAiHtmlMessage(String html, String time) {
        return createAiHtmlMessage(html, time, null);
    }

    public static HBox createAiHtmlMessage(String html, String time, String ttsText) {
        if (useFxml && componentsController != null) {
            HBox template = componentsController.getAiHtmlMessageTemplate();
            if (template != null) {
                return cloneAndFillAiHtmlMessage(template, html, time, ttsText);
            }
        }
        return createFallbackAiHtmlMessage(html, time, ttsText);
    }

    public static HBox createTimeDivider(String timeText) {
        if (useFxml && componentsController != null) {
            HBox template = componentsController.getTimeDividerTemplate();
            if (template != null) {
                return cloneAndFillTimeDivider(template, timeText);
            }
        }
        return createFallbackTimeDivider(timeText);
    }

    private static HBox cloneAndFillUserMessage(HBox template, String text, String time) {
        HBox clone = cloneHBox(template);
        if (clone == null) return createFallbackUserMessage(text, time);

        Label textLabel = findLabelById(clone, "userMessageText");
        Label timeLabel = findLabelById(clone, "userTimeLabel");

        if (textLabel != null) textLabel.setText(text);
        if (timeLabel != null) timeLabel.setText(time);

        return clone;
    }

    private static HBox cloneAndFillAiMessage(HBox template, String text, String time, String ttsText) {
        HBox clone = cloneHBox(template);
        if (clone == null) return createFallbackAiMessage(text, time, ttsText);

        Label textLabel = findLabelById(clone, "aiMessageText");
        Label timeLabel = findLabelById(clone, "aiTimeLabel");

        if (textLabel != null) textLabel.setText(text);
        if (timeLabel != null) timeLabel.setText(time);

        if (ttsText != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("tts", ttsText);
            data.put("display", text);
            data.put("type", "text");
            clone.setUserData(data);
        }

        return clone;
    }

    private static HBox cloneAndFillAiHtmlMessage(HBox template, String html, String time, String ttsText) {
        HBox clone = cloneHBox(template);
        if (clone == null) return createFallbackAiHtmlMessage(html, time, ttsText);

        WebView webView = findWebViewById(clone, "webView");
        Label timeLabel = findLabelById(clone, "aiHtmlTimeLabel");

        if (webView != null) {
            configureWebView(webView);

            String fullHtml = wrapHtmlWithStyles(html);
            webView.getEngine().loadContent(fullHtml);
        }
        if (timeLabel != null) timeLabel.setText(time);

        if (ttsText != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("tts", ttsText);
            data.put("html", html);
            data.put("type", "html");
            clone.setUserData(data);
        }

        return clone;
    }

    private static HBox cloneAndFillTimeDivider(HBox template, String timeText) {
        HBox clone = cloneHBox(template);
        if (clone == null) return createFallbackTimeDivider(timeText);

        Label label = findLabelById(clone, "dividerLabel");
        if (label != null) label.setText(timeText);

        return clone;
    }

    private static HBox cloneHBox(HBox original) {
        if (original == null) return null;

        HBox clone = new HBox();
        clone.getStyleClass().addAll(original.getStyleClass());
        clone.setSpacing(original.getSpacing());
        clone.setAlignment(original.getAlignment());
        clone.setPadding(original.getPadding());
        clone.setMaxWidth(original.getMaxWidth());

        for (Node child : original.getChildren()) {
            if (child instanceof VBox) {
                clone.getChildren().add(cloneVBox((VBox) child));
            } else if (child instanceof StackPane) {
                clone.getChildren().add(cloneStackPane((StackPane) child));
            } else if (child instanceof Region) {
                clone.getChildren().add(cloneRegion((Region) child));
            } else {
                clone.getChildren().add(child);
            }
        }

        return clone;
    }

    private static VBox cloneVBox(VBox original) {
        VBox clone = new VBox();
        clone.getStyleClass().addAll(original.getStyleClass());
        clone.setSpacing(original.getSpacing());
        clone.setMaxWidth(original.getMaxWidth());
        clone.setPadding(original.getPadding());

        for (Node child : original.getChildren()) {
            if (child instanceof Label) {
                clone.getChildren().add(cloneLabel((Label) child));
            } else if (child instanceof HBox) {
                clone.getChildren().add(cloneHBox((HBox) child));
            } else if (child instanceof VBox) {
                clone.getChildren().add(cloneVBox((VBox) child));
            } else if (child instanceof WebView) {
                clone.getChildren().add(cloneWebView((WebView) child));
            } else {
                clone.getChildren().add(child);
            }
        }

        return clone;
    }

    private static StackPane cloneStackPane(StackPane original) {
        StackPane clone = new StackPane();
        clone.getStyleClass().addAll(original.getStyleClass());
        clone.setPrefSize(original.getPrefWidth(), original.getPrefHeight());
        clone.setMinSize(original.getMinWidth(), original.getMinHeight());
        clone.setMaxSize(original.getMaxWidth(), original.getMaxHeight());

        for (Node child : original.getChildren()) {
            if (child instanceof Label) {
                clone.getChildren().add(cloneLabel((Label) child));
            } else {
                clone.getChildren().add(child);
            }
        }

        return clone;
    }

    private static Label cloneLabel(Label original) {
        Label clone = new Label();
        clone.getStyleClass().addAll(original.getStyleClass());
        clone.setText(original.getText());
        clone.setWrapText(original.isWrapText());
        clone.setMaxWidth(original.getMaxWidth());
        clone.setId(original.getId());
        return clone;
    }

    private static Region cloneRegion(Region original) {
        Region clone = new Region();
        clone.getStyleClass().addAll(original.getStyleClass());
        clone.setPrefSize(original.getPrefWidth(), original.getPrefHeight());
        clone.setMinSize(original.getMinWidth(), original.getMinHeight());
        clone.setMaxSize(original.getMaxWidth(), original.getMaxHeight());
        return clone;
    }

    private static WebView cloneWebView(WebView original) {
        WebView clone = new WebView();
        clone.getStyleClass().addAll(original.getStyleClass());
        clone.setId(original.getId());
        clone.setContextMenuEnabled(original.isContextMenuEnabled());
        clone.setFocusTraversable(original.isFocusTraversable());
        clone.setPrefHeight(original.getPrefHeight());
        clone.setMaxHeight(original.getMaxHeight());
        return clone;
    }

    private static Label findLabelById(HBox container, String id) {
        return (Label) container.lookup("#" + id);
    }

    private static WebView findWebViewById(HBox container, String id) {
        return (WebView) container.lookup("#" + id);
    }

    private static HBox createFallbackUserMessage(String text, String time) {
        HBox container = new HBox();
        container.getStyleClass().addAll("message-container", "message-container-right");
        container.setSpacing(8);
        container.setPadding(new Insets(0, 0, 5, 0));
        container.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox();
        content.getStyleClass().add("message-content");
        content.setSpacing(3);
        content.setMaxWidth(Double.MAX_VALUE);

        VBox bubble = new VBox();
        bubble.getStyleClass().addAll("message-bubble", "message-bubble-user");
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(12, 16, 12, 16));

        Label messageText = new Label(text);
        messageText.getStyleClass().add("message-text-user");
        messageText.setWrapText(true);
        messageText.setMaxWidth(380);

        HBox info = new HBox();
        info.getStyleClass().add("message-info");
        info.setSpacing(8);
        info.setAlignment(Pos.CENTER_RIGHT);

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("time-label");

        Label statusLabel = new Label("✔");
        statusLabel.getStyleClass().add("status-label");

        info.getChildren().addAll(timeLabel, statusLabel);
        bubble.getChildren().add(messageText);
        content.getChildren().addAll(bubble, info);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().addAll("avatar", "avatar-user");
        avatar.setPrefSize(32, 32);
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);

        Label avatarLabel = new Label("U");
        avatarLabel.getStyleClass().add("avatar-label");
        avatar.getChildren().add(avatarLabel);

        container.getChildren().addAll(content, avatar);
        return container;
    }

    private static HBox createFallbackAiMessage(String text, String time, String ttsText) {
        HBox container = new HBox();
        container.getStyleClass().addAll("message-container", "message-container-left");
        container.setSpacing(8);
        container.setPadding(new Insets(0, 0, 5, 0));
        container.setMaxWidth(Double.MAX_VALUE);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().addAll("avatar", "avatar-ai");
        avatar.setPrefSize(32, 32);
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);

        Label avatarLabel = new Label("AI");
        avatarLabel.getStyleClass().add("avatar-label");
        avatar.getChildren().add(avatarLabel);

        VBox content = new VBox();
        content.getStyleClass().add("message-content");
        content.setSpacing(3);
        content.setMaxWidth(Double.MAX_VALUE);

        VBox bubble = new VBox();
        bubble.getStyleClass().addAll("message-bubble", "message-bubble-ai");
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(12, 16, 12, 16));

        Label messageText = new Label(text);
        messageText.getStyleClass().add("message-text-ai");
        messageText.setWrapText(true);
        messageText.setMaxWidth(380);

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("time-label");

        bubble.getChildren().add(messageText);
        content.getChildren().addAll(bubble, timeLabel);

        if (ttsText != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("tts", ttsText);
            data.put("display", text);
            data.put("type", "text");
            container.setUserData(data);
        }

        container.getChildren().addAll(avatar, content);
        return container;
    }

    private static HBox createFallbackAiHtmlMessage(String html, String time, String ttsText) {
        HBox container = new HBox();
        container.getStyleClass().addAll("message-container", "message-container-left");
        container.setSpacing(8);
        container.setPadding(new Insets(0, 0, 5, 0));
        container.setMaxWidth(Double.MAX_VALUE);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().addAll("avatar", "avatar-ai");
        avatar.setPrefSize(32, 32);
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);

        Label avatarLabel = new Label("AI");
        avatarLabel.getStyleClass().add("avatar-label");
        avatar.getChildren().add(avatarLabel);

        VBox content = new VBox();
        content.getStyleClass().add("message-content");
        content.setSpacing(3);
        content.setMaxWidth(Double.MAX_VALUE);

        VBox bubble = new VBox();
        bubble.getStyleClass().add("webview-bubble");
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(8, 12, 8, 12));

        WebView webView = new WebView();
        webView.getStyleClass().add("webview");
        webView.setContextMenuEnabled(false);
        webView.setFocusTraversable(false);
        webView.setPrefHeight(50);
        configureWebView(webView);

        String fullHtml = wrapHtmlWithStyles(html);
        webView.getEngine().loadContent(fullHtml);

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("time-label");

        bubble.getChildren().add(webView);
        content.getChildren().addAll(bubble, timeLabel);

        if (ttsText != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("tts", ttsText);
            data.put("html", html);
            data.put("type", "html");
            container.setUserData(data);
        }

        container.getChildren().addAll(avatar, content);
        return container;
    }

    private static HBox createFallbackTimeDivider(String timeText) {
        HBox container = new HBox();
        container.getStyleClass().add("time-divider");
        container.setSpacing(10);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(5, 0, 5, 0));

        Line line1 = new Line();
        line1.setStartX(0);
        line1.setEndX(100);
        line1.getStyleClass().add("divider-line");

        Label label = new Label(timeText);
        label.getStyleClass().add("divider-label");

        Line line2 = new Line();
        line2.setStartX(0);
        line2.setEndX(100);
        line2.getStyleClass().add("divider-line");

        HBox.setHgrow(line1, Priority.ALWAYS);
        HBox.setHgrow(line2, Priority.ALWAYS);

        container.getChildren().addAll(line1, label, line2);
        return container;
    }

    private static void configureWebView(WebView webView) {
        webView.setContextMenuEnabled(false);
        webView.setFocusTraversable(false);
        webView.setPrefHeight(50);
        webView.setMaxHeight(600);

        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    try {
                        Object result = webView.getEngine().executeScript(
                                "document.documentElement.scrollHeight"
                        );
                        if (result instanceof Number) {
                            double height = ((Number) result).doubleValue();
                            height = Math.min(height + 20, 600);
                            webView.setPrefHeight(height);
                        }
                    } catch (Exception e) {
                        logger.warn("Ошибка при調整 высоты WebView: {}", e.getMessage());
                    }
                });
            }
        });
    }

    private static String wrapHtmlWithStyles(String content) {
        return String.format("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                /* ==================== БАЗОВЫЕ СТИЛИ ==================== */
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                
                body {
                    font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, sans-serif;
                    font-size: 14px;
                    line-height: 1.6;
                    color: #2c3e50;
                    background: transparent;
                    padding: 8px;
                }
                
                /* ==================== ЗАГОЛОВКИ ==================== */
                h1 {
                    color: #2c3e50;
                    font-size: 20px;
                    font-weight: 600;
                    margin: 15px 0 10px;
                    padding-bottom: 8px;
                    border-bottom: 2px solid #3498db;
                }
                
                h2 {
                    color: #34495e;
                    font-size: 18px;
                    font-weight: 600;
                    margin: 12px 0 8px;
                    padding-left: 10px;
                    border-left: 4px solid #9b59b6;
                }
                
                h3 {
                    color: #7f8c8d;
                    font-size: 16px;
                    font-weight: 600;
                    margin: 10px 0 5px;
                }
                
                /* ==================== ПАРАГРАФЫ ==================== */
                p {
                    margin: 8px 0;
                    color: #2c3e50;
                }
                
                /* ==================== СПИСКИ ==================== */
                ul, ol {
                    margin: 8px 0 8px 20px;
                    padding-left: 10px;
                }
                
                li {
                    margin: 4px 0;
                    line-height: 1.5;
                }
                
                li::marker {
                    color: #3498db;
                }
                
                /* ==================== ТАБЛИЦЫ ==================== */
                table {
                    width: 100%%;
                    border-collapse: collapse;
                    margin: 15px 0;
                    background: white;
                    border-radius: 8px;
                    overflow: hidden;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                }
                
                th {
                    background: #3498db;
                    color: white;
                    font-weight: 600;
                    padding: 10px;
                    text-align: left;
                }
                
                td {
                    padding: 8px 10px;
                    border-bottom: 1px solid #ecf0f1;
                }
                
                tr:last-child td {
                    border-bottom: none;
                }
                
                tr:hover {
                    background: #f5f9ff;
                }
                
                /* ==================== БЛОКИ КОДА ==================== */
                pre {
                    background: #2c3e50;
                    color: #ecf0f1;
                    padding: 12px;
                    border-radius: 8px;
                    overflow-x: auto;
                    font-family: 'Consolas', 'Monaco', monospace;
                    font-size: 13px;
                    margin: 10px 0;
                }
                
                code {
                    background: #ecf0f1;
                    color: #e74c3c;
                    padding: 2px 5px;
                    border-radius: 4px;
                    font-family: 'Consolas', monospace;
                    font-size: 12px;
                }
                
                /* ==================== ЦИТАТЫ ==================== */
                blockquote {
                    background: #f9f9f9;
                    border-left: 4px solid #9b59b6;
                    margin: 10px 0;
                    padding: 10px 15px;
                    color: #7f8c8d;
                    font-style: italic;
                    border-radius: 0 8px 8px 0;
                }
                
                /* ==================== ИНФОРМАЦИОННЫЕ КАРТОЧКИ ==================== */
                .info-card {
                    background: linear-gradient(135deg, #f8f9fa, #e9ecef);
                    border-radius: 10px;
                    padding: 15px;
                    margin: 15px 0;
                    border-left: 4px solid #3498db;
                }
                
                .success-card {
                    background: linear-gradient(135deg, #d4edda, #c3e6cb);
                    border-radius: 10px;
                    padding: 15px;
                    margin: 15px 0;
                    border-left: 4px solid #28a745;
                    color: #155724;
                }
                
                .warning-card {
                    background: linear-gradient(135deg, #fff3cd, #ffeeba);
                    border-radius: 10px;
                    padding: 15px;
                    margin: 15px 0;
                    border-left: 4px solid #ffc107;
                    color: #856404;
                }
                
                /* ==================== ТЕГИ И ЧИПСЫ ==================== */
                .tag {
                    display: inline-block;
                    background: #e0e0e0;
                    color: #2c3e50;
                    padding: 4px 12px;
                    border-radius: 20px;
                    font-size: 12px;
                    margin: 2px;
                    transition: all 0.3s ease;
                }
                
                .tag:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                }
                
                .tag-primary {
                    background: #3498db;
                    color: white;
                }
                
                .tag-success {
                    background: #27ae60;
                    color: white;
                }
                
                .tag-warning {
                    background: #f39c12;
                    color: white;
                }
                
                /* ==================== ПРОГРЕСС-БАРЫ ==================== */
                .progress-container {
                    background: #ecf0f1;
                    height: 24px;
                    border-radius: 12px;
                    overflow: hidden;
                    margin: 10px 0;
                }
                
                .progress-bar {
                    height: 100%%;
                    background: linear-gradient(90deg, #3498db, #9b59b6);
                    color: white;
                    text-align: center;
                    line-height: 24px;
                    font-size: 12px;
                    font-weight: bold;
                }
                
                /* ==================== КАРТОЧКИ ==================== */
                .card {
                    background: white;
                    border-radius: 10px;
                    padding: 15px;
                    margin: 10px 0;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                    border: 1px solid #ecf0f1;
                }
                
                /* ==================== ССЫЛКИ ==================== */
                a {
                    color: #3498db;
                    text-decoration: none;
                    transition: color 0.3s ease;
                }
                
                a:hover {
                    color: #2980b9;
                    text-decoration: underline;
                }
                
                /* ==================== РАЗДЕЛИТЕЛИ ==================== */
                hr {
                    border: none;
                    height: 1px;
                    background: linear-gradient(to right, transparent, #e0e0e0, transparent);
                    margin: 15px 0;
                }
                
                /* ==================== АНИМАЦИИ ==================== */
                @keyframes fadeIn {
                    from {
                        opacity: 0;
                        transform: translateY(10px);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0);
                    }
                }
                
                .message-content {
                    animation: fadeIn 0.5s ease;
                }
                
                /* ==================== АДАПТИВНОСТЬ ==================== */
                @media (max-width: 400px) {
                    body {
                        font-size: 13px;
                    }
                    
                    h1 {
                        font-size: 18px;
                    }
                    
                    h2 {
                        font-size: 16px;
                    }
                    
                    table {
                        font-size: 12px;
                    }
                    
                    .tag {
                        padding: 3px 8px;
                        font-size: 11px;
                    }
                }
            </style>
        </head>
        <body>
            <div class="message-content">
                %s
            </div>
        </body>
        </html>
        """, content);
    }
}