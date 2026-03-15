package com.mygitgor.repository;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.mygitgor.model.core.Conversation;
import com.mygitgor.model.core.LearningProgress;
import com.mygitgor.model.core.LearningSession;
import com.mygitgor.model.core.User;
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
            connectionSource = new JdbcConnectionSource(databaseUrl);
            createTables();
            logger.info("База данных успешно инициализирована: {}", databaseUrl);
        } catch (SQLException e) {
            logger.error("Ошибка при инициализации базы данных", e);
            throw new RuntimeException("Не удалось инициализировать базу данных", e);
        }
    }

    private void createTables() throws SQLException {
        createTablesWithDirectJDBC();

        TableUtils.createTableIfNotExists(connectionSource, User.class);
        TableUtils.createTableIfNotExists(connectionSource, Conversation.class);
        TableUtils.createTableIfNotExists(connectionSource, LearningProgress.class);
        TableUtils.createTableIfNotExists(connectionSource, LearningSession.class);

        logger.info("Таблицы созданы или уже существуют");
    }

    private void createTablesWithDirectJDBC() {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement stmt = connection.createStatement()) {

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
                            "vocabularyScore REAL, " +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                            ")";
            stmt.executeUpdate(createConversationsTable);

            String createLearningProgressTable =
                    "CREATE TABLE IF NOT EXISTS learning_progress (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER NOT NULL, " +
                            "learningMode TEXT NOT NULL, " +
                            "overallProgress REAL DEFAULT 0, " +
                            "skillsProgressJson TEXT, " +
                            "timeSpent INTEGER DEFAULT 0, " +
                            "tasksCompleted INTEGER DEFAULT 0, " +
                            "startDate INTEGER, " +
                            "lastUpdated INTEGER, " +
                            "achievementsJson TEXT, " +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, " +
                            "UNIQUE(user_id, learningMode)" +
                            ")";
            stmt.executeUpdate(createLearningProgressTable);

            String createLearningSessionsTable =
                    "CREATE TABLE IF NOT EXISTS learning_sessions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER NOT NULL, " +
                            "currentMode TEXT NOT NULL, " +
                            "sessionStart INTEGER, " +
                            "sessionEnd INTEGER, " +
                            "messagesExchanged INTEGER DEFAULT 0, " +
                            "sessionProgress REAL DEFAULT 0, " +
                            "currentLevel REAL DEFAULT 50, " +
                            "contextJson TEXT, " +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                            ")";
            stmt.executeUpdate(createLearningSessionsTable);

            logger.debug("Все таблицы проверены/созданы");

        } catch (SQLException e) {
            logger.error("Ошибка при создании таблиц через JDBC", e);
        }
    }

    public ConnectionSource getConnectionSource() {
        if (connectionSource == null) {
            throw new IllegalStateException("База данных не инициализирована. Вызовите initializeDatabase() сначала.");
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

    public Connection getJdbcConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }

    public boolean checkTablesExist() {
        try (Connection conn = getJdbcConnection()) {
            java.sql.DatabaseMetaData meta = conn.getMetaData();

            String[] tables = {"users", "conversations", "learning_progress", "learning_sessions"};
            for (String table : tables) {
                try (java.sql.ResultSet rs = meta.getTables(null, null, table, null)) {
                    if (!rs.next()) {
                        logger.warn("Таблица {} не существует", table);
                        return false;
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            logger.error("Ошибка при проверке таблиц", e);
            return false;
        }
    }

    public void clearAllData() {
        try (Connection conn = getJdbcConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DELETE FROM learning_sessions");
            stmt.executeUpdate("DELETE FROM learning_progress");
            stmt.executeUpdate("DELETE FROM conversations");
            stmt.executeUpdate("DELETE FROM users");

            logger.info("Все данные в базе очищены");
        } catch (SQLException e) {
            logger.error("Ошибка при очистке данных", e);
        }
    }

    public void resetAutoIncrement() {
        try (Connection conn = getJdbcConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('users', 'conversations', 'learning_progress', 'learning_sessions')");
            logger.info("Счетчики автоинкремента сброшены");
        } catch (SQLException e) {
            logger.error("Ошибка при сбросе автоинкремента", e);
        }
    }
}