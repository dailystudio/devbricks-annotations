package com.dailystudio.annotation.processor.database;

import com.dailystudio.annotation.DBColumn;
import com.dailystudio.annotation.processor.utils.LogUtils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class ColumnSpecs {

    public FieldSpec fieldSpec = null;
    public MethodSpec getterMethodSpec = null;
    public MethodSpec setterMethodSpec = null;

    public int version = CommonVariables.DEFAULT_VERSION;

    private ColumnSpecs() {

    }

    public static ColumnSpecs fromVariableElement(VariableElement element) {
        if (element == null) {
            return null;
        }

        ColumnSpecs specs = new ColumnSpecs();

        String varName = element.getSimpleName().toString();
        DBColumn dbColumn = element.getAnnotation(DBColumn.class);
        if (dbColumn == null) {
            return null;
        }

        TypeMirror fieldType = element.asType();
        String varTypeName = fieldType.toString();

        LogUtils.debug("dbfield: name = %s", varName);
        LogUtils.debug("dbfield: type = %s", varTypeName);
        LogUtils.debug("dbfield: version = %s", dbColumn.version());

        specs.version = dbColumn.version();
        specs.fieldSpec = composeColumnField(varName, varTypeName, dbColumn);

        return specs;
    }


    private static FieldSpec composeColumnField(String varName,
                                                String varType,
                                                DBColumn dbColumn) {
        if (varName == null || varName.isEmpty()
                || varType == null || varType.isEmpty()) {
            return null;
        }

        String colName = dbColumn.name();
        if (colName == null || colName.isEmpty()) {
            colName = varName.replaceAll("([A-Z])", "_$1").toLowerCase();

            if (colName.startsWith("m_") || colName.startsWith("s_")) {
                colName = colName.substring(2);
            }
        }

        boolean allowNull = false;
        String allowNullStr = dbColumn.allowNull();
        try {
            allowNull = Boolean.parseBoolean(allowNullStr);
        } catch (Exception e) {
            LogUtils.warn("parse allowNull for [%s] failed: %s, use default",
                    varName, e.toString());

            allowNull = true;
        }

        boolean primary = false;
        String primaryStr = dbColumn.primary();
        try {
            primary = Boolean.parseBoolean(primaryStr);
        } catch (Exception e) {
            LogUtils.warn("parse primary for [%s] failed: %s, use default",
                    varName, e.toString());

            primary = false;
        }

        if (primary) {
            allowNull = false;
        }

        int version = dbColumn.version();

        String fieldNameSuffix = colName.toUpperCase();

        ClassName colClassName = getColumnClassNameByType(varType);
        if (colClassName == null) {
            return null;
        }

        return FieldSpec.builder(
                ClassName.get(CommonVariables.DATABASE_OBJECT_PACKAGE, "Column"),
                "COLUMN_" + fieldNameSuffix,
                Modifier.STATIC,
                Modifier.PUBLIC)
                .initializer("new $T($S, $L, $L, $L)",
                        colClassName, colName, allowNull, primary, version)
                .build();
    }


    private static ClassName getColumnClassNameByType(String varType) {
        if (varType == null || varType.isEmpty()) {
            return null;
        }

        String colClassName = null;
        switch (varType.toLowerCase()) {
            case "int":
            case "boolean":
                colClassName = "IntegerColumn";
                break;

            case "java.lang.string":
                colClassName = "TextColumn";
                break;

            case "long":
                colClassName = "LongColumn";
                break;

            case "double":
                colClassName = "DoubleColumn";
                break;

            default:
                LogUtils.warn("[%s] is unsupported data type. ignored!", varType);
                break;
        }

        if (colClassName == null) {
            return null;
        }

        return ClassName.get(CommonVariables.DATABASE_OBJECT_PACKAGE, colClassName);
    }

    public static boolean isValidSpecs(ColumnSpecs specs) {
        if (specs == null) {
            return false;
        }

        return (specs.fieldSpec != null);
//        return (specs.fieldSpec != null
//                && specs.setterMethodSpec != null
//                && specs.getterMethodSpec != null);
    }

}
