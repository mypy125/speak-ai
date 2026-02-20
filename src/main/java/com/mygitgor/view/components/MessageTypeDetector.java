package com.mygitgor.view.components;

public class MessageTypeDetector {

    public static boolean needsHtmlRendering(String text) {
        if (text == null || text.isEmpty()) return false;

        return text.contains("<div") ||
                text.contains("<table") ||
                text.contains("<h1") ||
                text.contains("<h2") ||
                text.contains("<h3") ||
                (text.contains("<span") && text.contains("style=")) ||
                text.contains("<ul") ||
                text.contains("<ol") ||
                text.contains("class='") && (
                        text.contains("info-box") ||
                                text.contains("success-box") ||
                                text.contains("warning-box") ||
                                text.contains("tip-box")
                );
    }

    public static boolean isPlainText(String text) {
        if (text == null) return true;
        return !text.contains("<") && !text.contains(">");
    }

    public static String extractPlainTextForTts(String text) {
        if (text == null) return "";
        if (isPlainText(text)) return text;

        return text.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
