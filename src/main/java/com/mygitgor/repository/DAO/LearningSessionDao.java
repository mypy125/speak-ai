package com.mygitgor.repository.DAO;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.QueryBuilder;
import com.mygitgor.model.core.LearningSession;
import com.mygitgor.model.core.User;
import com.mygitgor.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import com.j256.ormlite.stmt.UpdateBuilder;

public class LearningSessionDao {
    private static final Logger logger = LoggerFactory.getLogger(LearningSessionDao.class);
    private Dao<LearningSession, Integer> sessionDao;

    public LearningSessionDao() {
        try {
            sessionDao = DaoManager.createDao(
                    DatabaseManager.getInstance().getConnectionSource(),
                    LearningSession.class
            );
        } catch (SQLException e) {
            logger.error("Ошибка при создании LearningSessionDao", e);
            throw new RuntimeException("Не удалось создать LearningSessionDao", e);
        }
    }

    /**
     * Создание новой сессии
     */
    public LearningSession createSession(LearningSession session) {
        try {
            session.prepareForSave();
            sessionDao.create(session);
            session.afterLoad();
            logger.info("Создана сессия: user={}, mode={}",
                    session.getUser().getId(), session.getCurrentMode());
            return session;
        } catch (SQLException e) {
            logger.error("Ошибка при создании сессии", e);
            throw new RuntimeException("Не удалось создать сессию", e);
        }
    }

    /**
     * Получение активной сессии пользователя
     */
    public LearningSession getActiveSession(User user) {
        try {
            QueryBuilder<LearningSession, Integer> queryBuilder = sessionDao.queryBuilder();
            queryBuilder.where()
                    .eq("user_id", user.getId())
                    .and()
                    .isNull("sessionEnd");

            LearningSession session = queryBuilder.queryForFirst();
            if (session != null) {
                session.afterLoad();
            }
            return session;
        } catch (SQLException e) {
            logger.error("Ошибка при получении активной сессии", e);
            return null;
        }
    }

    /**
     * Получение сессии по ID
     */
    public LearningSession getSessionById(int id) {
        try {
            LearningSession session = sessionDao.queryForId(id);
            if (session != null) {
                session.afterLoad();
            }
            return session;
        } catch (SQLException e) {
            logger.error("Ошибка при получении сессии по ID", e);
            return null;
        }
    }

    /**
     * Получение всех сессий пользователя
     */
    public List<LearningSession> getSessionsByUser(User user) {
        try {
            QueryBuilder<LearningSession, Integer> queryBuilder = sessionDao.queryBuilder();
            queryBuilder.where().eq("user_id", user.getId());
            queryBuilder.orderBy("sessionStart", false);

            List<LearningSession> sessions = sessionDao.query(queryBuilder.prepare());
            sessions.forEach(LearningSession::afterLoad);
            return sessions;
        } catch (SQLException e) {
            logger.error("Ошибка при получении сессий пользователя", e);
            return List.of();
        }
    }

    /**
     * Получение последней сессии пользователя
     */
    public LearningSession getLastSession(User user) {
        try {
            QueryBuilder<LearningSession, Integer> queryBuilder = sessionDao.queryBuilder();
            queryBuilder.where().eq("user_id", user.getId());
            queryBuilder.orderBy("sessionStart", false);
            queryBuilder.limit(1L);

            LearningSession session = queryBuilder.queryForFirst();
            if (session != null) {
                session.afterLoad();
            }
            return session;
        } catch (SQLException e) {
            logger.error("Ошибка при получении последней сессии", e);
            return null;
        }
    }

    /**
     * Обновление сессии
     */
    public void updateSession(LearningSession session) {
        try {
            session.prepareForSave();
            sessionDao.update(session);
            logger.debug("Сессия обновлена: id={}", session.getId());
        } catch (SQLException e) {
            logger.error("Ошибка при обновлении сессии", e);
            throw new RuntimeException("Не удалось обновить сессию", e);
        }
    }

    /**
     * Завершение сессии
     */
    public void endSession(int sessionId) {
        try {
            UpdateBuilder<LearningSession, Integer> updateBuilder = sessionDao.updateBuilder();
            updateBuilder.where().eq("id", sessionId);
            updateBuilder.updateColumnValue("sessionEnd", new Date());
            updateBuilder.update();
            logger.info("Сессия завершена: id={}", sessionId);
        } catch (SQLException e) {
            logger.error("Ошибка при завершении сессии", e);
        }
    }

    /**
     * Завершение всех активных сессий пользователя
     */
    public void endAllActiveSessions(User user) {
        try {
            UpdateBuilder<LearningSession, Integer> updateBuilder = sessionDao.updateBuilder();
            updateBuilder.where()
                    .eq("user_id", user.getId())
                    .and()
                    .isNull("sessionEnd");
            updateBuilder.updateColumnValue("sessionEnd", new Date());
            int updated = updateBuilder.update();
            if (updated > 0) {
                logger.info("Завершено {} активных сессий пользователя {}", updated, user.getId());
            }
        } catch (SQLException e) {
            logger.error("Ошибка при завершении активных сессий", e);
        }
    }

    /**
     * Удаление сессии
     */
    public void deleteSession(int id) {
        try {
            sessionDao.deleteById(id);
            logger.info("Сессия удалена: id={}", id);
        } catch (SQLException e) {
            logger.error("Ошибка при удалении сессии", e);
            throw new RuntimeException("Не удалось удалить сессию", e);
        }
    }

    /**
     * Удаление всех сессий пользователя
     */
    public void deleteSessionsByUser(User user) {
        try {
            QueryBuilder<LearningSession, Integer> queryBuilder = sessionDao.queryBuilder();
            queryBuilder.where().eq("user_id", user.getId());
            List<LearningSession> sessions = sessionDao.query(queryBuilder.prepare());
            sessionDao.delete(sessions);
            logger.info("Удалено {} сессий пользователя {}", sessions.size(), user.getId());
        } catch (SQLException e) {
            logger.error("Ошибка при удалении сессий пользователя", e);
        }
    }

    /**
     * Получение статистики по сессиям
     */
    public SessionStats getSessionStats(User user) {
        try {
            List<LearningSession> sessions = getSessionsByUser(user);

            int totalSessions = sessions.size();
            long totalDuration = 0;
            int totalMessages = 0;
            double avgProgress = 0;

            for (LearningSession session : sessions) {
                totalDuration += session.getDurationSeconds();
                totalMessages += session.getMessagesExchanged();
                avgProgress += session.getSessionProgress();
            }

            if (totalSessions > 0) {
                avgProgress /= totalSessions;
            }

            return new SessionStats(totalSessions, totalDuration, totalMessages, avgProgress);

        } catch (Exception e) {
            logger.error("Ошибка при получении статистики сессий", e);
            return new SessionStats(0, 0, 0, 0);
        }
    }

    /**
     * Внутренний класс для статистики
     */
    public static class SessionStats {
        private final int totalSessions;
        private final long totalDuration;
        private final int totalMessages;
        private final double averageProgress;

        public SessionStats(int totalSessions, long totalDuration, int totalMessages, double averageProgress) {
            this.totalSessions = totalSessions;
            this.totalDuration = totalDuration;
            this.totalMessages = totalMessages;
            this.averageProgress = averageProgress;
        }

        public int getTotalSessions() { return totalSessions; }
        public long getTotalDuration() { return totalDuration; }
        public int getTotalMessages() { return totalMessages; }
        public double getAverageProgress() { return averageProgress; }

        public String getFormattedTotalDuration() {
            long hours = totalDuration / 3600;
            long minutes = (totalDuration % 3600) / 60;

            if (hours > 0) {
                return String.format("%dч %dм", hours, minutes);
            } else {
                return String.format("%dм", minutes);
            }
        }

        @Override
        public String toString() {
            return String.format("SessionStats{sessions=%d, time=%s, messages=%d, avgProgress=%.1f%%}",
                    totalSessions, getFormattedTotalDuration(), totalMessages, averageProgress);
        }
    }
}