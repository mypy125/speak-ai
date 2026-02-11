package com.mygitgor.service.components;

import com.mygitgor.model.Conversation;
import com.mygitgor.model.SpeechAnalysis;
import com.mygitgor.model.User;
import com.mygitgor.repository.DAO.ConversationDao;
import com.mygitgor.repository.DAO.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import java.util.List;

public class ConversationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    private final ConversationDao conversationDao;
    private final UserDao userDao;
    private User currentUser;

    public ConversationManager() {
        this.conversationDao = new ConversationDao();
        this.userDao = new UserDao();
    }

    public User getOrCreateDefaultUser() {
        if (currentUser != null) {
            return currentUser;
        }

        try {
            // Сначала ищем существующего пользователя
            User user = userDao.getUserByEmail("demo@speakai.com");

            if (user == null) {
                // Создаем нового пользователя
                user = createDefaultUser();
                User createdUser = userDao.createUser(user);
                if (createdUser != null) {
                    user = createdUser;
                    logger.info("Создан новый пользователь с ID: {}", user.getId());
                }
            } else {
                logger.info("Найден существующий пользователь с ID: {}", user.getId());
            }

            currentUser = user;
            return user;

        } catch (Exception e) {
            logger.error("Ошибка при работе с пользователем", e);
            // Возвращаем временного пользователя без сохранения в БД
            return createDefaultUser();
        }
    }

    private User createDefaultUser() {
        User user = new User();
        user.setId(1);
        user.setUsername("Demo User");
        user.setEmail("demo@speakai.com");
        user.setLanguageLevel("B1");
        user.setNativeLanguage("Russian");
        user.setCreatedAt(new Date());
        return user;
    }

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

        } catch (Exception e) {
            logger.error("Ошибка при сохранении разговора", e);
        }
    }

    public List<Conversation> getHistory() {
        try {
            User user = getOrCreateDefaultUser();
            return conversationDao.getConversationsByUser(user.getId());
        } catch (Exception e) {
            logger.error("Ошибка при получении истории", e);
            return List.of();
        }
    }

    public void clearHistory() {
        try {
            User user = getOrCreateDefaultUser();
            conversationDao.deleteConversationsByUser(user.getId());
            logger.info("История очищена для пользователя: {}", user.getUsername());
        } catch (Exception e) {
            logger.error("Ошибка при очистке истории", e);
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
}
