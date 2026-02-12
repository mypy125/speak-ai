package com.mygitgor.config;

import javafx.scene.paint.Color;
import java.util.Map;

public final class AppConstants {

    private AppConstants() {} // Prevent instantiation

    // ========================================
    // Application Settings
    // ========================================
    public static final String APP_NAME = "SpeakAI";
    public static final String APP_VERSION = "1.0.0";

    // ========================================
    // UI Constants
    // ========================================
    public static final int MAX_MESSAGE_LENGTH = 400;
    public static final int SCROLL_DELAY_MS = 50;
    public static final int STATUS_MESSAGE_DURATION_MS = 3000;
    public static final int SPEECH_COMPLETION_BUFFER_MS = 1000;

    // ========================================
    // Audio Settings
    // ========================================
    public static final int RECORDING_DURATION_MS = 3000;
    public static final int TTS_DELAY_MS = 300;
    public static final int MAX_SPEECH_TEXT_LENGTH = 2000;
    public static final String DEFAULT_SPEECH_TEXT = "I recorded an audio message";
    public static final String AUDIO_DIRECTORY = "recordings";
    public static final String AUDIO_FILE_PREFIX = "recording_";
    public static final String AUDIO_FILE_EXTENSION = ".wav";
    public static final int TEST_RECORDING_DURATION_SECONDS = 3;

    // ========================================
    // TTS Settings
    // ========================================
    public static final double MIN_SPEECH_SPEED = 0.25;
    public static final double MAX_SPEECH_SPEED = 4.0;
    public static final double DEFAULT_SPEECH_SPEED = 1.0;

    public static final double MIN_PITCH = -20.0;
    public static final double MAX_PITCH = 20.0;
    public static final double DEFAULT_PITCH = 0.0;

    public static final double MIN_VOLUME_DB = -96.0;
    public static final double MAX_VOLUME_DB = 16.0;
    public static final double DEFAULT_VOLUME_DB = 0.0;

    public static final double MIN_MICROPHONE_SENSITIVITY = 0.1;
    public static final double MAX_MICROPHONE_SENSITIVITY = 1.0;
    public static final double DEFAULT_MICROPHONE_SENSITIVITY = 0.5;

    // ========================================
    // Google Cloud TTS
    // =======================================
    public static final String GOOGLE_CREDENTIALS_FILENAME = "google-credentials.json";
    public static final String[] GOOGLE_CREDENTIALS_PATHS = {
            "./" + GOOGLE_CREDENTIALS_FILENAME,
            "src/main/resources/" + GOOGLE_CREDENTIALS_FILENAME,
            GOOGLE_CREDENTIALS_FILENAME,
            "config/" + GOOGLE_CREDENTIALS_FILENAME,
            "../" + GOOGLE_CREDENTIALS_FILENAME
    };
    public static final int GOOGLE_TTS_INIT_DELAY_MS = 2000;

    // ========================================
    // Speech Recognition Settings
    // ========================================
    public static final String DEFAULT_SPEECH_SERVICE = "MOCK - Тестовый режим";

    // ========================================
    // Language Settings
    // ========================================
    public static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("en-US", "Английский (США)"),
            Map.entry("ru-RU", "Русский"),
            Map.entry("en-GB", "Английский (Великобритания)"),
            Map.entry("de-DE", "Немецкий"),
            Map.entry("fr-FR", "Французский"),
            Map.entry("es-ES", "Испанский"),
            Map.entry("it-IT", "Итальянский"),
            Map.entry("ja-JP", "Японский"),
            Map.entry("ko-KR", "Корейский"),
            Map.entry("zh-CN", "Китайский (упрощенный)"),
            Map.entry("zh-TW", "Китайский (традиционный)")
    );

    // ========================================
    // UI Colors
    // ========================================
    public static final Color TTS_STATUS_AVAILABLE = Color.LIMEGREEN;
    public static final Color TTS_STATUS_UNAVAILABLE = Color.ORANGERED;
    public static final Color USER_MESSAGE_COLOR = Color.rgb(52, 152, 219);
    public static final Color AI_MESSAGE_COLOR = Color.rgb(248, 249, 250);
    public static final Color ERROR_COLOR = Color.rgb(231, 76, 60);
    public static final Color SUCCESS_COLOR = Color.rgb(46, 204, 113);


    // ========================================
    // Speech Analysis Thresholds
    // ========================================
    public static final double EXCELLENT_SCORE_THRESHOLD = 90.0;
    public static final double GOOD_SCORE_THRESHOLD = 85.0;
    public static final double AVERAGE_SCORE_THRESHOLD = 75.0;
    public static final double FAIR_SCORE_THRESHOLD = 65.0;
    public static final double POOR_SCORE_THRESHOLD = 55.0;

    public static final double WEAK_PHONEME_THRESHOLD = 70.0;
    public static final double TRAINER_PHONEME_THRESHOLD = 80.0;

    // ========================================
    // Speech Duration Calculation
    // ========================================
    public static final double WORDS_PER_SECOND = 2.5;
    public static final int MIN_SPEECH_DURATION_MS = 2000;
    public static final int MAX_SPEECH_DURATION_MS = 30000;
    public static final int DEFAULT_SPEECH_DURATION_MS = 3000;

    // ========================================
    // File Paths
    // ========================================
    public static final String APPLICATION_PROPERTIES_PATH = "/application.properties";
    public static final String TEST_AUDIO_PATH = "recordings/test_audio.wav";

    // ========================================
    // Service Providers
    // ========================================
    public static final Map<String, String> PROVIDER_DISPLAY_NAMES = Map.of(
            "groq", "Groq",
            "openai", "OpenAI GPT",
            "deepseek", "DeepSeek",
            "anthropic", "Anthropic Claude",
            "together", "Together AI",
            "ollama", "Ollama (локальный)"
    );
}
