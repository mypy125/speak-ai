package com.mygitgor.repository.DAO;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.User;
import com.mygitgor.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class ConversationDao {
    private static final Logger logger = LoggerFactory.getLogger(ConversationDao.class);
    private Dao<Conversation, Integer> conversationDao;

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
        try {
            conversationDao.create(conversation);
            logger.debug("Создана новая запись разговора для пользователя: {}",
                    conversation.getUser().getUsername());
            return conversation;
        } catch (SQLException e) {
            logger.error("Ошибка при создании записи разговора", e);
            throw new RuntimeException("Не удалось сохранить разговор", e);
        }
    }

    public List<Conversation> getConversationsByUser(User user) {
        try {
            QueryBuilder<Conversation, Integer> queryBuilder = conversationDao.queryBuilder();
            queryBuilder.where().eq("user_id", user.getId());
            queryBuilder.orderBy("timestamp", false); // Сначала новые
            return conversationDao.query(queryBuilder.prepare());
        } catch (SQLException e) {
            logger.error("Ошибка при получении разговоров пользователя", e);
            throw new RuntimeException("Не удалось получить историю разговоров", e);
        }
    }

    public List<Conversation> getConversationsByDateRange(User user, Date startDate, Date endDate) {
        try {
            QueryBuilder<Conversation, Integer> queryBuilder = conversationDao.queryBuilder();
            queryBuilder.where()
                    .eq("user_id", user.getId())
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

    public void deleteConversationsByUser(User user) {
        try {
            DeleteBuilder<Conversation, Integer> deleteBuilder = conversationDao.deleteBuilder();
            deleteBuilder.where().eq("user_id", user.getId());
            int deleted = deleteBuilder.delete();
            logger.info("Удалено {} записей разговоров для пользователя: {}", deleted, user.getUsername());
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
}
