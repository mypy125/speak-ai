package com.mygitgor.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;

@DatabaseTable(tableName = "users")
public class User {
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false)
    private String username;

    @DatabaseField(canBeNull = false, unique = true)
    private String email;

    @DatabaseField
    private String passwordHash;

    @DatabaseField
    private String languageLevel; // A1, A2, B1, B2, C1, C2

    @DatabaseField
    private String nativeLanguage;

    @DatabaseField
    private Date createdAt;

    @DatabaseField
    private Date lastLogin;

    // Конструкторы
    public User() {
        // ORMLite requires no-arg constructor
    }

    public User(String username, String email, String languageLevel, String nativeLanguage) {
        this.username = username;
        this.email = email;
        this.languageLevel = languageLevel;
        this.nativeLanguage = nativeLanguage;
        this.createdAt = new Date();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getLanguageLevel() { return languageLevel; }
    public void setLanguageLevel(String languageLevel) { this.languageLevel = languageLevel; }

    public String getNativeLanguage() { return nativeLanguage; }
    public void setNativeLanguage(String nativeLanguage) { this.nativeLanguage = nativeLanguage; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getLastLogin() { return lastLogin; }
    public void setLastLogin(Date lastLogin) { this.lastLogin = lastLogin; }
}
