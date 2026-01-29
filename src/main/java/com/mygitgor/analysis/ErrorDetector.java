package com.mygitgor.analysis;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "words")
public class ErrorDetector {
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false)
    private String term;

    @DatabaseField(canBeNull = false)
    private String translation;

    @DatabaseField
    private String imageUrl;

    @DatabaseField
    private int knowledgeLevel;

    public ErrorDetector() {}
    // Геттеры и сеттеры
}
