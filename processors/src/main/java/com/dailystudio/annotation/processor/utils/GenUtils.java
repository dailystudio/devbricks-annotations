package com.dailystudio.annotation.processor.utils;

public class GenUtils {

    private final static String DB_OBJECT_CLASS_NAME_SUFFIX = "DBObject";

    public static String getDBObjectGenClassName(String className) {
        return className + DB_OBJECT_CLASS_NAME_SUFFIX;
    }

}
