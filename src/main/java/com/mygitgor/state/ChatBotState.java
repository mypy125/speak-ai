package com.mygitgor.state;

import com.mygitgor.model.LearningMode;
import com.mygitgor.service.components.ResponseMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;
import java.util.Map;

public class ChatBotState {
    private static final Logger logger = LoggerFactory.getLogger(ChatBotState.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean isPlayingSpeech = new AtomicBoolean(false);
    private final AtomicBoolean isAiServiceAvailable = new AtomicBoolean(false);
    private final AtomicBoolean isRecording = new AtomicBoolean(false);

    private final AtomicReference<String> lastBotResponse = new AtomicReference<>("");
    private final AtomicReference<String> lastTtsText = new AtomicReference<>(""); // ДОБАВЛЕНО
    private final AtomicReference<String> currentTextInput = new AtomicReference<>("");
    private final AtomicReference<ResponseMode> currentResponseMode =
            new AtomicReference<>(ResponseMode.TEXT);
    private final AtomicReference<String> currentAudioFile = new AtomicReference<>();
    private final AtomicReference<LearningMode> currentLearningMode =
            new AtomicReference<>(LearningMode.CONVERSATION);

    private final Statistics statistics = new Statistics();

    public static class Statistics {
        private final AtomicInteger messagesCount = new AtomicInteger(0);
        private final AtomicInteger analysisCount = new AtomicInteger(0);
        private final AtomicInteger recordingsCount = new AtomicInteger(0);

        private final AtomicInteger conversationCount = new AtomicInteger(0);
        private final AtomicInteger pronunciationCount = new AtomicInteger(0);
        private final AtomicInteger grammarCount = new AtomicInteger(0);
        private final AtomicInteger vocabularyCount = new AtomicInteger(0);
        private final AtomicInteger exerciseCount = new AtomicInteger(0);
        private final AtomicInteger writingCount = new AtomicInteger(0);
        private final AtomicInteger listeningCount = new AtomicInteger(0);

        public int incrementMessages() {
            return messagesCount.incrementAndGet();
        }

        public int incrementAnalysis() {
            return analysisCount.incrementAndGet();
        }

        public int incrementRecordings() {
            return recordingsCount.incrementAndGet();
        }

        public int getMessagesCount() {
            return messagesCount.get();
        }

        public int getAnalysisCount() {
            return analysisCount.get();
        }

        public int getRecordingsCount() {
            return recordingsCount.get();
        }

        public int incrementConversation() {
            return conversationCount.incrementAndGet();
        }

        public int incrementPronunciation() {
            return pronunciationCount.incrementAndGet();
        }

        public int incrementGrammar() {
            return grammarCount.incrementAndGet();
        }

        public int incrementVocabulary() {
            return vocabularyCount.incrementAndGet();
        }

        public int incrementExercise() {
            return exerciseCount.incrementAndGet();
        }

        public int incrementWriting() {
            return writingCount.incrementAndGet();
        }

        public int incrementListening() {
            return listeningCount.incrementAndGet();
        }

        public int getConversationCount() {
            return conversationCount.get();
        }

        public int getPronunciationCount() {
            return pronunciationCount.get();
        }

        public int getGrammarCount() {
            return grammarCount.get();
        }

        public int getVocabularyCount() {
            return vocabularyCount.get();
        }

        public int getExerciseCount() {
            return exerciseCount.get();
        }

        public int getWritingCount() {
            return writingCount.get();
        }

        public int getListeningCount() {
            return listeningCount.get();
        }

        public void reset() {
            messagesCount.set(0);
            analysisCount.set(0);
            recordingsCount.set(0);
            conversationCount.set(0);
            pronunciationCount.set(0);
            grammarCount.set(0);
            vocabularyCount.set(0);
            exerciseCount.set(0);
            writingCount.set(0);
            listeningCount.set(0);
        }

        public Statistics snapshot() {
            Statistics snapshot = new Statistics();
            snapshot.messagesCount.set(this.messagesCount.get());
            snapshot.analysisCount.set(this.analysisCount.get());
            snapshot.recordingsCount.set(this.recordingsCount.get());
            snapshot.conversationCount.set(this.conversationCount.get());
            snapshot.pronunciationCount.set(this.pronunciationCount.get());
            snapshot.grammarCount.set(this.grammarCount.get());
            snapshot.vocabularyCount.set(this.vocabularyCount.get());
            snapshot.exerciseCount.set(this.exerciseCount.get());
            snapshot.writingCount.set(this.writingCount.get());
            snapshot.listeningCount.set(this.listeningCount.get());
            return snapshot;
        }

        @Override
        public String toString() {
            return String.format(
                    "Statistics{total={msg=%d, anl=%d, rec=%d}, modes={conv=%d, pron=%d, gram=%d, voc=%d, ex=%d, writ=%d, lis=%d}}",
                    messagesCount.get(), analysisCount.get(), recordingsCount.get(),
                    conversationCount.get(), pronunciationCount.get(), grammarCount.get(),
                    vocabularyCount.get(), exerciseCount.get(), writingCount.get(), listeningCount.get()
            );
        }
    }

    public ChatBotState() {
        logger.debug("ChatBotState инициализирован");
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean setClosed(boolean closed) {
        boolean wasClosed = this.closed.getAndSet(closed);
        if (wasClosed != closed) {
            logger.debug("Состояние closed изменено: {} -> {}", wasClosed, closed);
        }
        return wasClosed;
    }

    public boolean isPlayingSpeech() {
        return isPlayingSpeech.get();
    }

    public boolean setPlayingSpeech(boolean playing) {
        boolean wasPlaying = isPlayingSpeech.getAndSet(playing);
        if (wasPlaying != playing) {
            logger.debug("Состояние озвучки: {}", playing ? "▶️ Начало" : "⏹️ Остановка");
        }
        return wasPlaying;
    }

    public boolean isAiServiceAvailable() {
        return isAiServiceAvailable.get();
    }

    public void setAiServiceAvailable(boolean available) {
        boolean wasAvailable = isAiServiceAvailable.getAndSet(available);
        if (wasAvailable != available) {
            logger.info("AI сервис: {}", available ? "✅ Доступен" : "❌ Недоступен");
        }
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    public boolean setRecording(boolean recording) {
        boolean wasRecording = isRecording.getAndSet(recording);
        if (wasRecording != recording) {
            logger.info("Запись: {}", recording ? "🔴 Начата" : "⏹️ Остановлена");
        }
        return wasRecording;
    }

    public String getLastBotResponse() {
        return lastBotResponse.get();
    }

    public String setLastBotResponse(String response) {
        String previous = lastBotResponse.getAndSet(response);
        if (response != null && !response.equals(previous)) {
            logger.debug("Последний ответ бота обновлен (длина: {})", response.length());
        }
        return previous;
    }

    public String getLastTtsText() {
        return lastTtsText.get();
    }

    public String setLastTtsText(String ttsText) {
        String previous = lastTtsText.getAndSet(ttsText);
        if (ttsText != null && !ttsText.equals(previous)) {
            logger.debug("TTS текст обновлен (длина: {})", ttsText.length());
        }
        return previous;
    }

    public boolean hasTtsText() {
        String tts = lastTtsText.get();
        return tts != null && !tts.isEmpty();
    }

    public void clearTtsText() {
        lastTtsText.set("");
        logger.debug("TTS текст очищен");
    }

    public ResponseMode getCurrentResponseMode() {
        return currentResponseMode.get();
    }

    public ResponseMode setCurrentResponseMode(ResponseMode mode) {
        ResponseMode previous = currentResponseMode.getAndSet(mode);
        if (previous != mode) {
            logger.info("Режим ответа: {}", mode == ResponseMode.VOICE ? "🔊 Голосовой" : "📝 Текстовый");
        }
        return previous;
    }

    public String getCurrentAudioFile() {
        return currentAudioFile.get();
    }

    public String setCurrentAudioFile(String file) {
        String previous = currentAudioFile.getAndSet(file);
        if (file != null && !file.equals(previous)) {
            logger.debug("Текущий аудиофайл: {}", file);
        }
        return previous;
    }

    public void clearCurrentAudioFile() {
        String previous = currentAudioFile.getAndSet(null);
        if (previous != null) {
            logger.debug("Аудиофайл очищен: {}", previous);
        }
    }

    public String getCurrentTextInput() {
        return currentTextInput.get();
    }

    public String setCurrentTextInput(String text) {
        return currentTextInput.getAndSet(text);
    }

    public void clearCurrentTextInput() {
        currentTextInput.set("");
    }

    public LearningMode getCurrentLearningMode() {
        return currentLearningMode.get();
    }

    public LearningMode setCurrentLearningMode(LearningMode mode) {
        LearningMode previous = currentLearningMode.getAndSet(mode);
        if (previous != mode) {
            logger.info("Режим обучения: {}", mode != null ? mode.getDisplayName() : "не задан");
        }
        return previous;
    }

    public Statistics getStatistics() {
        return statistics.snapshot();
    }

    public int incrementMessagesCount() {
        return statistics.incrementMessages();
    }

    public int incrementAnalysisCount() {
        return statistics.incrementAnalysis();
    }

    public int incrementRecordingsCount() {
        return statistics.incrementRecordings();
    }

    public int incrementConversationCount() {
        return statistics.incrementConversation();
    }

    public int incrementPronunciationCount() {
        return statistics.incrementPronunciation();
    }

    public int incrementGrammarCount() {
        return statistics.incrementGrammar();
    }

    public int incrementVocabularyCount() {
        return statistics.incrementVocabulary();
    }

    public int incrementExerciseCount() {
        return statistics.incrementExercise();
    }

    public int incrementWritingCount() {
        return statistics.incrementWriting();
    }

    public int incrementListeningCount() {
        return statistics.incrementListening();
    }

    public void resetStatistics() {
        statistics.reset();
        logger.info("Статистика сброшена");
    }

    public Map<LearningMode, Integer> getModeStats() {
        Map<LearningMode, Integer> stats = new HashMap<>();
        stats.put(LearningMode.CONVERSATION, statistics.getConversationCount());
        stats.put(LearningMode.PRONUNCIATION, statistics.getPronunciationCount());
        stats.put(LearningMode.GRAMMAR, statistics.getGrammarCount());
        stats.put(LearningMode.VOCABULARY, statistics.getVocabularyCount());
        stats.put(LearningMode.EXERCISE, statistics.getExerciseCount());
        stats.put(LearningMode.WRITING, statistics.getWritingCount());
        stats.put(LearningMode.LISTENING, statistics.getListeningCount());
        return stats;
    }

    public boolean canSendMessage() {
        return !isClosed() && (hasTextInput() || hasAudioFile());
    }

    public boolean hasTextInput() {
        String text = currentTextInput.get();
        return text != null && !text.trim().isEmpty();
    }

    public boolean hasAudioFile() {
        String file = currentAudioFile.get();
        return file != null && !file.isEmpty();
    }

    public boolean canStartRecording() {
        return isActive() && !isRecording.get();
    }

    public boolean canStopRecording() {
        return isRecording.get();
    }

    public boolean canPlaySpeech() {
        return isActive() && !isPlayingSpeech.get() &&
                (hasTtsText() || (lastBotResponse.get() != null && !lastBotResponse.get().isEmpty()));
    }

    public boolean canStopSpeech() {
        return isPlayingSpeech.get();
    }

    public boolean isActive() {
        return !closed.get();
    }


    public void reset() {
        setLastBotResponse("");
        setLastTtsText("");
        clearCurrentAudioFile();
        clearCurrentTextInput();
        setPlayingSpeech(false);
        setRecording(false);
        setCurrentLearningMode(LearningMode.CONVERSATION);
        logger.debug("Состояние сброшено");
    }

    public void resetAll() {
        reset();
        setCurrentResponseMode(ResponseMode.TEXT);
        setAiServiceAvailable(false);
        resetStatistics();
        logger.debug("Полный сброс состояния выполнен");
    }

    public String getStateString() {
        return String.format(
                "ChatBotState{closed=%s, playingSpeech=%s, aiAvailable=%s, recording=%s, " +
                        "responseMode=%s, learningMode=%s, hasAudio=%s, hasText=%s, hasTts=%s}",
                closed.get(), isPlayingSpeech.get(), isAiServiceAvailable.get(),
                isRecording.get(), currentResponseMode.get(), currentLearningMode.get(),
                hasAudioFile(), hasTextInput(), hasTtsText()
        );
    }

    public Map<String, Object> getAllStates() {
        Map<String, Object> states = new HashMap<>();
        states.put("closed", closed.get());
        states.put("playingSpeech", isPlayingSpeech.get());
        states.put("aiAvailable", isAiServiceAvailable.get());
        states.put("recording", isRecording.get());
        states.put("responseMode", currentResponseMode.get());
        states.put("learningMode", currentLearningMode.get());
        states.put("hasAudioFile", hasAudioFile());
        states.put("hasTextInput", hasTextInput());
        states.put("hasTtsText", hasTtsText());
        states.put("lastBotResponseLength", lastBotResponse.get() != null ? lastBotResponse.get().length() : 0);
        states.put("lastTtsTextLength", lastTtsText.get() != null ? lastTtsText.get().length() : 0);
        states.put("statistics", statistics.snapshot());
        return states;
    }

    public String getFormattedStats() {
        Statistics stats = statistics.snapshot();
        return String.format(
                "📊 Статистика:\n" +
                        "• Сообщений: %d\n" +
                        "• Анализов: %d\n" +
                        "• Записей: %d\n" +
                        "• Режимы: Conversation=%d, Pronunciation=%d, Grammar=%d, Vocabulary=%d, Exercise=%d, Writing=%d, Listening=%d",
                stats.getMessagesCount(),
                stats.getAnalysisCount(),
                stats.getRecordingsCount(),
                stats.getConversationCount(),
                stats.getPronunciationCount(),
                stats.getGrammarCount(),
                stats.getVocabularyCount(),
                stats.getExerciseCount(),
                stats.getWritingCount(),
                stats.getListeningCount()
        );
    }
}