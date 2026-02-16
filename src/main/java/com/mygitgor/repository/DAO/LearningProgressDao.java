package com.mygitgor.repository.DAO;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.QueryBuilder;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.model.core.User;
import com.mygitgor.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class LearningProgressDao {
    private static final Logger logger = LoggerFactory.getLogger(LearningProgressDao.class);
    private Dao<LearningProgress, Integer> progressDao;

    public LearningProgressDao() {
        try {
            progressDao = DaoManager.createDao(
                    DatabaseManager.getInstance().getConnectionSource(),
                    LearningProgress.class
            );
        } catch (SQLException e) {
            logger.error("Ошибка при создании LearningProgressDao", e);
            throw new RuntimeException("Не удалось создать LearningProgressDao", e);
        }
    }

    public LearningProgress createProgress(LearningProgress progress) {
        try {
            progress.prepareForSave(); // Сериализуем перед сохранением
            progressDao.create(progress);
            progress.afterLoad(); // Десериализуем после загрузки
            logger.info("Создан прогресс: user={}, mode={}, progress={}",
                    progress.getUser().getId(), progress.getLearningMode(), progress.getOverallProgress());
            return progress;
        } catch (SQLException e) {
            logger.error("Ошибка при создании прогресса", e);
            throw new RuntimeException("Не удалось создать прогресс", e);
        }
    }

    public LearningProgress getProgressByUserAndMode(User user, String mode) {
        try {
            QueryBuilder<LearningProgress, Integer> queryBuilder = progressDao.queryBuilder();
            queryBuilder.where()
                    .eq("user_id", user.getId())
                    .and()
                    .eq("learningMode", mode);

            LearningProgress progress = queryBuilder.queryForFirst();
            if (progress != null) {
                progress.afterLoad(); // Десериализуем
            }
            return progress;
        } catch (SQLException e) {
            logger.error("Ошибка при получении прогресса", e);
            return null;
        }
    }

    public List<LearningProgress> getProgressByUser(User user) {
        try {
            QueryBuilder<LearningProgress, Integer> queryBuilder = progressDao.queryBuilder();
            queryBuilder.where().eq("user_id", user.getId());
            List<LearningProgress> list = progressDao.query(queryBuilder.prepare());
            list.forEach(LearningProgress::afterLoad); // Десериализуем все
            return list;
        } catch (SQLException e) {
            logger.error("Ошибка при получении прогресса пользователя", e);
            return List.of();
        }
    }

    public void updateProgress(LearningProgress progress) {
        try {
            progress.prepareForSave(); // Сериализуем перед обновлением
            progressDao.update(progress);
            logger.debug("Прогресс обновлен: id={}", progress.getId());
        } catch (SQLException e) {
            logger.error("Ошибка при обновлении прогресса", e);
            throw new RuntimeException("Не удалось обновить прогресс", e);
        }
    }

    public void deleteProgress(int id) {
        try {
            progressDao.deleteById(id);
            logger.info("Прогресс удален: id={}", id);
        } catch (SQLException e) {
            logger.error("Ошибка при удалении прогресса", e);
            throw new RuntimeException("Не удалось удалить прогресс", e);
        }
    }
}