package com.mygitgor.repository.DAO;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.User;
import com.mygitgor.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Date;
import java.util.List;

public class ConversationDao {
    private static final Logger logger = LoggerFactory.getLogger(ConversationDao.class);
    private Dao<Conversation, Integer> conversationDao;
    private ConnectionSource connectionSource;

    public ConversationDao() {
        try {
            conversationDao = DaoManager.createDao(
                    DatabaseManager.getInstance().getConnectionSource(),
                    Conversation.class
            );
        } catch (SQLException e) {
            logger.error("Ошибка при создании ConversationDao", e);
            throw new RuntimeException("Не удалось создать ConversationDao", e);
        }
    }

    public Conversation createConversation(Conversation conversation) {
        java.sql.Connection jdbcConnection = null;
        java.sql.PreparedStatement stmt = null;
        java.sql.ResultSet generatedKeys = null;

        try {
            jdbcConnection = java.sql.DriverManager.getConnection("jdbc:sqlite:data/speakai.db");

            String sql = "INSERT INTO conversations (user_id, userMessage, botResponse, audioPath, " +
                    "analysisResult, recommendations, timestamp, pronunciationScore, grammarScore, vocabularyScore) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            stmt = jdbcConnection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

            stmt.setInt(1, conversation.getUserId());
            stmt.setString(2, conversation.getUserMessage());
            stmt.setString(3, conversation.getBotResponse());
            stmt.setString(4, conversation.getAudioPath());
            stmt.setString(5, conversation.getAnalysisResult() != null ? conversation.getAnalysisResult() : "");
            stmt.setString(6, conversation.getRecommendations() != null ? conversation.getRecommendations() : "");
            stmt.setLong(7, conversation.getTimestamp().getTime());
            stmt.setDouble(8, conversation.getPronunciationScore());
            stmt.setDouble(9, conversation.getGrammarScore());
            stmt.setDouble(10, conversation.getVocabularyScore());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    conversation.setId(id);
                    logger.info("Разговор сохранен (JDBC), ID: {}", id);
                }
            }

            return conversation;

        } catch (java.sql.SQLException e) {
            logger.error("Ошибка при создании записи разговора", e);
            throw new RuntimeException("Не удалось сохранить разговор", e);
        } finally {
            try {
                if (generatedKeys != null) generatedKeys.close();
                if (stmt != null) stmt.close();
                if (jdbcConnection != null) jdbcConnection.close();
            } catch (java.sql.SQLException e) {
                logger.error("Ошибка при закрытии ресурсов", e);
            }
        }
    }

    private Conversation createConversationWithDirectJDBC(Conversation conversation) {
        String sql = "INSERT INTO conversations (user_id, userMessage, botResponse, audioPath, " +
                "analysisResult, recommendations, timestamp, pronunciationScore, grammarScore, vocabularyScore) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseManager.getInstance().getJdbcConnection();
             PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Определяем user_id
            int userId = conversation.getUser() != null ? conversation.getUser().getId() :
                    (conversation.getUserId() > 0 ? conversation.getUserId() : 1);

            stmt.setInt(1, userId);
            stmt.setString(2, conversation.getUserMessage());
            stmt.setString(3, conversation.getBotResponse());
            stmt.setString(4, conversation.getAudioPath());
            stmt.setString(5, conversation.getAnalysisResult());
            stmt.setString(6, conversation.getRecommendations());
            stmt.setLong(7, conversation.getTimestamp() != null ?
                    conversation.getTimestamp().getTime() : System.currentTimeMillis());
            stmt.setDouble(8, conversation.getPronunciationScore());
            stmt.setDouble(9, conversation.getGrammarScore());
            stmt.setDouble(10, conversation.getVocabularyScore());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        conversation.setId(id);
                        logger.debug("Разговор сохранен (JDBC), ID: {}", id);
                    }
                }
                return conversation;
            } else {
                throw new RuntimeException("Не удалось сохранить разговор: affectedRows = 0");
            }

        } catch (SQLException e) {
            logger.error("Ошибка при сохранении разговора через JDBC", e);
            throw new RuntimeException("Не удалось сохранить разговор (JDBC метод)", e);
        }
    }

    public List<Conversation> getConversationsByUser(int userId) {
        try {
            QueryBuilder<Conversation, Integer> queryBuilder = conversationDao.queryBuilder();
            queryBuilder.where().eq("user_id", userId);
            queryBuilder.orderBy("timestamp", false);
            return conversationDao.query(queryBuilder.prepare());
        } catch (SQLException e) {
            logger.error("Ошибка при получении разговоров пользователя", e);
            throw new RuntimeException("Не удалось получить историю разговоров", e);
        }
    }

    public List<Conversation> getConversationsByDateRange(int userId, Date startDate, Date endDate) {
        try {
            QueryBuilder<Conversation, Integer> queryBuilder = conversationDao.queryBuilder();
            queryBuilder.where()
                    .eq("user_id", userId)
                    .and()
                    .ge("timestamp", startDate)
                    .and()
                    .le("timestamp", endDate);
            queryBuilder.orderBy("timestamp", false);
            return conversationDao.query(queryBuilder.prepare());
        } catch (SQLException e) {
            logger.error("Ошибка при получении разговоров по диапазону дат", e);
            throw new RuntimeException("Не удалось получить разговоры", e);
        }
    }

    public void updateConversation(Conversation conversation) {
        try {
            conversationDao.update(conversation);
            logger.debug("Запись разговора обновлена: ID={}", conversation.getId());
        } catch (SQLException e) {
            logger.error("Ошибка при обновлении записи разговора", e);
            throw new RuntimeException("Не удалось обновить запись разговора", e);
        }
    }

    public void deleteConversation(int id) {
        try {
            conversationDao.deleteById(id);
            logger.info("Запись разговора удалена: ID={}", id);
        } catch (SQLException e) {
            logger.error("Ошибка при удалении записи разговора", e);
            throw new RuntimeException("Не удалось удалить запись разговора", e);
        }
    }

    public void deleteConversationsByUser(int userId) {
        try {
            DeleteBuilder<Conversation, Integer> deleteBuilder = conversationDao.deleteBuilder();
            deleteBuilder.where().eq("user_id", userId);
            int deleted = deleteBuilder.delete();
            logger.info("Удалено {} записей разговоров для пользователя ID: {}", deleted, userId);
        } catch (SQLException e) {
            logger.error("Ошибка при удалении разговоров пользователя", e);
            throw new RuntimeException("Не удалось удалить разговоры пользователя", e);
        }
    }

    public Conversation getConversationById(int id) {
        try {
            return conversationDao.queryForId(id);
        } catch (SQLException e) {
            logger.error("Ошибка при получении разговора по ID", e);
            return null;
        }
    }

    public List<Conversation> getAllConversations() {
        try {
            QueryBuilder<Conversation, Integer> queryBuilder = conversationDao.queryBuilder();
            queryBuilder.orderBy("timestamp", false);
            return conversationDao.query(queryBuilder.prepare());
        } catch (SQLException e) {
            logger.error("Ошибка при получении всех разговоров", e);
            throw new RuntimeException("Не удалось получить все разговоры", e);
        }
    }

    // Метод для получения разговоров по userId (альтернатива)
    public List<Conversation> getConversationsByUserId(int userId) {
        return getConversationsByUser(userId);
    }
}
