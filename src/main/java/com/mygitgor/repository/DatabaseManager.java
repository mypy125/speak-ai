package com.mygitgor.repository;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.jdbc.db.SqliteDatabaseType;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.mygitgor.model.Conversation;
import com.mygitgor.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

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
            // Простое создание connection source без кастомного типа базы данных
            connectionSource = new JdbcConnectionSource(databaseUrl);
            createTables();
            logger.info("База данных успешно инициализирована: {}", databaseUrl);

        } catch (SQLException e) {
            logger.error("Ошибка при инициализации базы данных", e);
            throw new RuntimeException("Не удалось инициализировать базу данных", e);
        }
    }

    private void createTables() throws SQLException {
        // Сначала создаем таблицу через прямое соединение JDBC
        createTablesWithDirectJDBC();

        // Затем через ORMLite для создания если не существует
        TableUtils.createTableIfNotExists(connectionSource, User.class);
        TableUtils.createTableIfNotExists(connectionSource, Conversation.class);

        logger.info("Таблицы созданы или уже существуют");
    }

    private void createTablesWithDirectJDBC() {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement stmt = connection.createStatement()) {

            // Создаем таблицу users если не существует
            String createUsersTable =
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "username TEXT NOT NULL, " +
                            "email TEXT NOT NULL UNIQUE, " +
                            "passwordHash TEXT, " +
                            "languageLevel TEXT, " +
                            "nativeLanguage TEXT, " +
                            "createdAt INTEGER, " +
                            "lastLogin INTEGER" +
                            ")";

            stmt.executeUpdate(createUsersTable);
            logger.debug("Таблица users проверена/создана");

            // Создаем таблицу conversations если не существует
            String createConversationsTable =
                    "CREATE TABLE IF NOT EXISTS conversations (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "userMessage TEXT NOT NULL, " +
                            "botResponse TEXT NOT NULL, " +
                            "audioPath TEXT, " +
                            "analysisResult TEXT, " +
                            "recommendations TEXT, " +
                            "timestamp INTEGER, " +
                            "pronunciationScore REAL, " +
                            "grammarScore REAL, " +
                            "vocabularyScore REAL" +
                            ")";

            stmt.executeUpdate(createConversationsTable);
            logger.debug("Таблица conversations проверена/создана");

        } catch (SQLException e) {
            logger.error("Ошибка при создании таблиц через JDBC", e);
            // Продолжаем - возможно таблицы уже созданы через ORMLite
        }
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

    // Вспомогательный метод для прямого доступа к соединению JDBC
    public Connection getJdbcConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }
}