package com.mygitgor.chatbot.components;

import com.mygitgor.state.ChatBotState;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsManager {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsManager.class);

    private final Label messagesCountLabel;
    private final Label analysisCountLabel;
    private final Label recordingsCountLabel;
    private final ChatBotState state;

    public StatisticsManager(Label messagesCountLabel,
                             Label analysisCountLabel,
                             Label recordingsCountLabel,
                             ChatBotState state) {
        this.messagesCountLabel = messagesCountLabel;
        this.analysisCountLabel = analysisCountLabel;
        this.recordingsCountLabel = recordingsCountLabel;
        this.state = state;

        updateAll();
    }

    public void updateAll() {
        Platform.runLater(() -> {
            ChatBotState.Statistics stats = state.getStatistics();

            if (messagesCountLabel != null) {
                messagesCountLabel.setText(String.valueOf(stats.getMessagesCount()));
            }
            if (analysisCountLabel != null) {
                analysisCountLabel.setText(String.valueOf(stats.getAnalysisCount()));
            }
            if (recordingsCountLabel != null) {
                recordingsCountLabel.setText(String.valueOf(stats.getRecordingsCount()));
            }
        });
    }

    public void onMessageSent() {
        state.incrementMessagesCount();
        updateAll();
        logger.debug("Статистика сообщений обновлена: {}", state.getStatistics().getMessagesCount());
    }

    public void onAnalysisPerformed() {
        state.incrementAnalysisCount();
        updateAll();
        logger.debug("Статистика анализов обновлена: {}", state.getStatistics().getAnalysisCount());
    }

    public void onRecordingStarted() {
        state.incrementRecordingsCount();
        updateAll();
        logger.debug("Статистика записей обновлена: {}", state.getStatistics().getRecordingsCount());
    }

    public void reset() {
        state.resetStatistics();
        updateAll();
        logger.info("Статистика сброшена");
    }
}
