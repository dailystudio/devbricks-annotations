package com.dailystudio.annotation.example;

import com.dailystudio.annotation.DBColumn;
import com.dailystudio.annotation.DBObject;

@DBObject(latestVersion = 2)
public class User {

    @DBColumn(primary = "true")
    private long mUserId;

    @DBColumn(name = "user_name", allowNull = "false")
    private String mUserName;

    @DBColumn(name = "age")
    private int mAge;

    @DBColumn(name = "married")
    private boolean mMarried;

    @DBColumn(name = "score", version = 2)
    private double mScore;
}
