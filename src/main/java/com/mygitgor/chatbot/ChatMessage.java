package com.mygitgor.chatbot;

import java.util.Date;

public class ChatMessage {
    public enum Sender {
        USER, BOT
    }

    private final Sender sender;
    private final String content;
    private final Date timestamp;
    private final String audioPath;

    public ChatMessage(Sender sender, String content) {
        this(sender, content, null);
    }

    public ChatMessage(Sender sender, String content, String audioPath) {
        this.sender = sender;
        this.content = content;
        this.audioPath = audioPath;
        this.timestamp = new Date();
    }

    public Sender getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public boolean hasAudio() {
        return audioPath != null && !audioPath.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s",
                timestamp,
                sender == Sender.USER ? "Вы" : "Бот",
                content.length() > 50 ? content.substring(0, 50) + "..." : content
        );
    }
}
