package com.mygitgor.repository;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private ConnectionSource connectionSource;
    private final String databaseUrl;

    private DatabaseManager() {
        File dbDir = new File("data");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        databaseUrl = "jdbc:sqlite:data/speakai.db";
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void initializeDatabase() {
        try {
            connectionSource = new JdbcConnectionSource(databaseUrl);
            createTables();
            logger.info("База данных успешно инициализирована: {}", databaseUrl);
        } catch (SQLException e) {
            logger.error("Ошибка при инициализации базы данных", e);
            throw new RuntimeException("Не удалось инициализировать базу данных", e);
        }
    }

    private void createTables() throws SQLException {
        TableUtils.createTableIfNotExists(connectionSource, User.class);
        TableUtils.createTableIfNotExists(connectionSource, Conversation.class);
        logger.info("Таблицы созданы или уже существуют");
    }

    public ConnectionSource getConnectionSource() {
        if (connectionSource == null) {
            throw new IllegalStateException("База данных не инициализирована");
        }
        return connectionSource;
    }

    public void closeConnection() {
        if (connectionSource != null) {
            try {
                connectionSource.close();
                logger.info("Соединение с базой данных закрыто");
            } catch (Exception e) {
                logger.error("Ошибка при закрытии соединения с БД", e);
            }
        }
    }
}
