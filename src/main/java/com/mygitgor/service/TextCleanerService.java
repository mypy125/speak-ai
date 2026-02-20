package com.mygitgor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;
import com.mygitgor.config.AppConstants;

public class TextCleanerService {
    private static final Logger logger = LoggerFactory.getLogger(TextCleanerService.class);

    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("#+\\s*|\\*\\*|\\*|`|\\[.*?\\]\\(.*?\\)");
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[🤖🎤📝📊🗣️✅⚠️🔴🔊▶️🏆🎉👍💪📚🔧❤️✨🌟🔥💡🎯📅❌ℹ️]");
    private static final Pattern VOICE_MARKER_PATTERN = Pattern.compile("🔊 \\*\\*Ответ озвучен\\*\\*\\n\\n");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{2,}");
    private static final Pattern SINGLE_NEWLINE = Pattern.compile("\\n");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    public String cleanForSpeech(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String cleanText = text;

        cleanText = MARKDOWN_PATTERN.matcher(cleanText).replaceAll("");
        cleanText = HTML_PATTERN.matcher(cleanText).replaceAll("");
        cleanText = EMOJI_PATTERN.matcher(cleanText).replaceAll("");
        cleanText = VOICE_MARKER_PATTERN.matcher(cleanText).replaceAll("");
        cleanText = MULTIPLE_NEWLINES.matcher(cleanText).replaceAll(". ");
        cleanText = SINGLE_NEWLINE.matcher(cleanText).replaceAll(" ");
        cleanText = MULTIPLE_SPACES.matcher(cleanText).replaceAll(" ");

        cleanText = cleanText.trim();

        if (cleanText.length() > AppConstants.MAX_SPEECH_TEXT_LENGTH) {
            cleanText = cleanText.substring(0, AppConstants.MAX_SPEECH_TEXT_LENGTH) +
                    "... [текст сокращен]";
        }

        logger.debug("Текст очищен: {} -> {}", text.length(), cleanText.length());
        return cleanText;
    }

    public String cleanForDisplay(String text) {
        if (text == null) return "";
        return text.trim();
    }
}
