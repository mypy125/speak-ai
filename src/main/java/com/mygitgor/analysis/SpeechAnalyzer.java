package com.mygitgor.analysis;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "lessons")
public class SpeechAnalyzer {
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField
    private String title;

    @DatabaseField
    private boolean isCompleted;

    public SpeechAnalyzer() {}
}
