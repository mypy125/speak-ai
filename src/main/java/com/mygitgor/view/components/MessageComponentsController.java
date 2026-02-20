package com.mygitgor.view.components;

import javafx.fxml.FXML;
import javafx.scene.layout.HBox;

public class MessageComponentsController {
    @FXML private HBox userMessageTemplate;
    @FXML private HBox aiMessageTemplate;
    @FXML private HBox aiHtmlMessageTemplate;
    @FXML private HBox timeDividerTemplate;

    public HBox getUserMessageTemplate() {
        return userMessageTemplate;
    }

    public HBox getAiMessageTemplate() {
        return aiMessageTemplate;
    }

    public HBox getAiHtmlMessageTemplate() {
        return aiHtmlMessageTemplate;
    }

    public HBox getTimeDividerTemplate() {
        return timeDividerTemplate;
    }
}
