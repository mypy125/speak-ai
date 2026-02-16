package com.mygitgor.service.components;

import com.mygitgor.model.LearningMode;
import com.mygitgor.model.core.Conversation;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.model.core.User;
import com.mygitgor.repository.DAO.ConversationDao;
import com.mygitgor.repository.DAO.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConversationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    private final ConversationDao conversationDao;
    private final UserDao userDao;
    private final LearningProgressManager progressManager;

    private User currentUser;

    // Константы для расчета прогресса
    private static final double PROGRESS_PER_CONVERSATION = 0.5;
    private static final double BONUS_PROGRESS_GOOD_SCORE = 2.0;
    private static final double GOOD_SCORE_THRESHOLD = 80.0;

    public ConversationManager() {
        this.conversationDao = new ConversationDao();
        this.userDao = new UserDao();
        this.progressManager = new LearningProgressManager();
    }

    public User getOrCreateDefaultUser() {
        try {
            User user = userDao.getUserByEmail("demo@speakai.com");

            if (user == null) {
                user = createDefaultUser();
                User createdUser = userDao.createUser(user);
                if (createdUser != null) {
                    user = createdUser;
                    logger.info("Создан новый пользователь в БД с ID: {}", user.getId());
                    initializeUserProgress(user);
                } else {
                    logger.warn("Не удалось создать пользователя в БД");
                    return createTemporaryUser();
                }
            } else {
                logger.info("Найден существующий пользователь с ID: {}", user.getId());
            }

            currentUser = user;
            return user;

        } catch (Exception e) {
            logger.error("Ошибка при работе с пользователем", e);
            return createTemporaryUser();
        }
    }

    public User getCurrentUser() {
        if (currentUser != null) {
            return currentUser;
        }

        try {
            currentUser = getOrCreateDefaultUser();
            if (currentUser != null) {
                logger.debug("Получен пользователь из БД с ID: {}", currentUser.getId());
                return currentUser;
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении пользователя из БД", e);
        }

        currentUser = createTemporaryUser();
        logger.info("Создан временный пользователь с ID: {}", currentUser.getId());
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        logger.debug("Установлен текущий пользователь: ID={}", user != null ? user.getId() : null);
    }

    private User createDefaultUser() {
        User user = new User();
        user.setUsername("Demo User");
        user.setEmail("demo@speakai.com");
        user.setLanguageLevel("B1");
        user.setNativeLanguage("Russian");
        user.setCreatedAt(new Date());
        return user;
    }

    private User createTemporaryUser() {
        User user = new User();
        user.setId(-1 * (int)(System.currentTimeMillis() % 10000));
        user.setUsername("Guest_" + System.currentTimeMillis());
        user.setEmail("guest_" + System.currentTimeMillis() + "@temp.com");
        user.setLanguageLevel("B1");
        user.setNativeLanguage("Russian");
        user.setCreatedAt(new Date());
        return user;
    }

    private void initializeUserProgress(User user) {
        CompletableFuture.runAsync(() -> {
            try {
                for (LearningMode mode : LearningMode.values()) {
                    LearningProgress progress = LearningProgress.builder()
                            .user(user)
                            .learningMode(mode)
                            .overallProgress(0)
                            .tasksCompleted(0)
                            .timeSpent(0)
                            .build();
                    progressManager.saveProgress(progress);
                }
                logger.info("Инициализирован прогресс для пользователя {}", user.getId());
            } catch (Exception e) {
                logger.error("Ошибка при инициализации прогресса пользователя", e);
            }
        });
    }

    // ========================================
    // Управление разговорами
    // ========================================

    /**
     * Сохранение разговора
     */
    public void saveConversation(String userMessage, String botResponse,
                                 SpeechAnalysis analysis, String audioPath) {
        try {
            User user = getOrCreateDefaultUser();

            Conversation conversation = new Conversation();
            conversation.setUser(user);
            conversation.setUserMessage(userMessage);
            conversation.setBotResponse(botResponse);
            conversation.setAudioPath(audioPath);
            conversation.setTimestamp(new Date());

            if (analysis != null) {
                conversation.setPronunciationScore(analysis.getPronunciationScore());
                conversation.setGrammarScore(analysis.getGrammarScore());
                conversation.setVocabularyScore(analysis.getVocabularyScore());
                conversation.setAnalysisResult(analysis.getSummary());

                if (analysis.getRecommendations() != null && !analysis.getRecommendations().isEmpty()) {
                    conversation.setRecommendations(String.join("; ", analysis.getRecommendations()));
                }
            }

            conversationDao.createConversation(conversation);
            logger.info("Разговор сохранен в БД, ID: {}", conversation.getId());

            // Обновляем прогресс асинхронно
            updateProgressAfterConversation(user, analysis);

        } catch (Exception e) {
            logger.error("Ошибка при сохранении разговора", e);
        }
    }

    /**
     * Обновление прогресса после разговора
     */
    private void updateProgressAfterConversation(User user, SpeechAnalysis analysis) {
        CompletableFuture.runAsync(() -> {
            try {
                LearningMode mode = LearningMode.CONVERSATION;

                // Базовый прогресс за разговор
                double progressDelta = PROGRESS_PER_CONVERSATION;

                // Бонус за хорошую оценку
                if (analysis != null && analysis.getPronunciationScore() > GOOD_SCORE_THRESHOLD) {
                    progressDelta += BONUS_PROGRESS_GOOD_SCORE;

                    // Добавляем достижение за отличное произношение
                    if (analysis.getPronunciationScore() > 90) {
                        progressManager.addAchievement(user, mode, "🎤 Отличное произношение!");
                    }
                }

                // Обновляем прогресс
                progressManager.updateProgress(user, mode, progressDelta, 1, 300);

                // Обновляем навыки
                if (analysis != null) {
                    progressManager.updateSkillProgress(user, mode, "pronunciation",
                            analysis.getPronunciationScore());
                    progressManager.updateSkillProgress(user, mode, "grammar",
                            analysis.getGrammarScore());
                    progressManager.updateSkillProgress(user, mode, "vocabulary",
                            analysis.getVocabularyScore());
                }

            } catch (Exception e) {
                logger.error("Ошибка при обновлении прогресса после разговора", e);
            }
        });
    }

    /**
     * Получение истории разговоров
     */
    public List<Conversation> getHistory() {
        try {
            User user = getOrCreateDefaultUser();
            return conversationDao.getConversationsByUser(user.getId());
        } catch (Exception e) {
            logger.error("Ошибка при получении истории", e);
            return List.of();
        }
    }

    /**
     * Очистка истории
     */
    public void clearHistory() {
        try {
            User user = getOrCreateDefaultUser();
            conversationDao.deleteConversationsByUser(user.getId());
            logger.info("История очищена для пользователя: {}", user.getUsername());
        } catch (Exception e) {
            logger.error("Ошибка при очистке истории", e);
        }
    }

    // ========================================
    // Дополнительные методы
    // ========================================

    /**
     * Получение количества разговоров
     */
    public int getConversationCount() {
        try {
            return getHistory().size();
        } catch (Exception e) {
            logger.error("Ошибка при получении количества разговоров", e);
            return 0;
        }
    }

    /**
     * Получение последнего разговора
     */
    public Conversation getLastConversation() {
        try {
            List<Conversation> history = getHistory();
            return history.isEmpty() ? null : history.get(0);
        } catch (Exception e) {
            logger.error("Ошибка при получении последнего разговора", e);
            return null;
        }
    }

    /**
     * Получение средней оценки за разговоры
     */
    public double getAverageScore() {
        try {
            List<Conversation> history = getHistory();
            if (history.isEmpty()) return 0;

            return history.stream()
                    .filter(c -> c.getPronunciationScore() > 0)
                    .mapToDouble(Conversation::getPronunciationScore)
                    .average()
                    .orElse(0);
        } catch (Exception e) {
            logger.error("Ошибка при получении средней оценки", e);
            return 0;
        }
    }

    /**
     * Получение менеджера прогресса
     */
    public LearningProgressManager getProgressManager() {
        return progressManager;
    }

    /**
     * Экспорт истории в текст
     */
    public String exportHistory() {
        StringBuilder sb = new StringBuilder();
        List<Conversation> history = getHistory();

        sb.append("=== ИСТОРИЯ РАЗГОВОРОВ ===\n\n");

        for (Conversation conv : history) {
            sb.append(String.format("[%s]\n", conv.getTimestamp()));
            sb.append("Вы: ").append(conv.getUserMessage()).append("\n");
            sb.append("Бот: ").append(conv.getBotResponse()).append("\n");
            if (conv.getPronunciationScore() > 0) {
                sb.append(String.format("Оценка: %.1f/100\n", conv.getPronunciationScore()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Поиск разговоров по дате
     */
    public List<Conversation> getConversationsByDate(Date date) {
        try {
            // В реальном приложении здесь должен быть запрос к БД
            return getHistory().stream()
                    .filter(c -> isSameDay(c.getTimestamp(), date))
                    .toList();
        } catch (Exception e) {
            logger.error("Ошибка при поиске разговоров по дате", e);
            return List.of();
        }
    }

    private boolean isSameDay(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;

        java.time.LocalDate ld1 = d1.toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        java.time.LocalDate ld2 = d2.toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        return ld1.equals(ld2);
    }
}