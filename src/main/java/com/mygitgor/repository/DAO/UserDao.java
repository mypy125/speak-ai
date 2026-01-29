package com.mygitgor.repository.DAO;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.QueryBuilder;
import com.mygitgor.model.User;
import com.mygitgor.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    private Dao<User, Integer> userDao;

    public UserDao() {
        try {
            userDao = DaoManager.createDao(
                    DatabaseManager.getInstance().getConnectionSource(),
                    User.class
            );
        } catch (SQLException e) {
            logger.error("Ошибка при создании UserDao", e);
            throw new RuntimeException("Не удалось создать UserDao", e);
        }
    }

    public User createUser(User user) {
        try {
            userDao.create(user);
            logger.info("Создан новый пользователь: {}", user.getUsername());
            return user;
        } catch (SQLException e) {
            logger.error("Ошибка при создании пользователя", e);
            throw new RuntimeException("Не удалось создать пользователя", e);
        }
    }

    public User getUserById(int id) {
        try {
            return userDao.queryForId(id);
        } catch (SQLException e) {
            logger.error("Ошибка при получении пользователя по ID", e);
            return null;
        }
    }

    public User getUserByEmail(String email) {
        try {
            QueryBuilder<User, Integer> queryBuilder = userDao.queryBuilder();
            queryBuilder.where().eq("email", email);
            List<User> users = userDao.query(queryBuilder.prepare());
            return users.isEmpty() ? null : users.get(0);
        } catch (SQLException e) {
            logger.error("Ошибка при получении пользователя по email", e);
            return null;
        }
    }

    public void updateUser(User user) {
        try {
            userDao.update(user);
            logger.debug("Пользователь обновлен: {}", user.getUsername());
        } catch (SQLException e) {
            logger.error("Ошибка при обновлении пользователя", e);
            throw new RuntimeException("Не удалось обновить пользователя", e);
        }
    }

    public List<User> getAllUsers() {
        try {
            return userDao.queryForAll();
        } catch (SQLException e) {
            logger.error("Ошибка при получении всех пользователей", e);
            throw new RuntimeException("Не удалось получить список пользователей", e);
        }
    }

    public void deleteUser(int id) {
        try {
            userDao.deleteById(id);
            logger.info("Пользователь удален: ID={}", id);
        } catch (SQLException e) {
            logger.error("Ошибка при удалении пользователя", e);
            throw new RuntimeException("Не удалось удалить пользователя", e);
        }
    }
}
