package com.dailystudio.annotation.processor.database;

import com.dailystudio.annotation.DBColumn;
import com.dailystudio.annotation.processor.utils.LogUtils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

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
        if (specs.fieldSpec == null) {
            return specs;
        }

        specs.setterMethodSpec = composeColumnSetterMethod(varName, varTypeName,
                specs.fieldSpec.name);

        specs.getterMethodSpec = composeColumnGetterMethod(varName, varTypeName,
                specs.fieldSpec.name);

        return specs;
    }

    private static MethodSpec composeColumnSetterMethod(String varName,
                                                        String varType,
                                                        String colName) {
        if (varName == null || varName.isEmpty()
                || varType == null || varType.isEmpty()
                || colName == null || colName.isEmpty()) {
            return null;
        }

        String setterName = varNameToMethodName(varName, CommonVariables.SETTER_PREFIX);
        LogUtils.debug("dbfield: setter name = %s", setterName);
        String paramName = varNameToParameterName(varName);
        LogUtils.debug("dbfield: parameter name = %s", paramName);

        TypeName paramTypeName = getParamOrReturnTypeNameByType(varType);
        if (paramTypeName == null) {
            return null;
        }

        MethodSpec.Builder builder = MethodSpec.methodBuilder(setterName)
                .addParameter(paramTypeName, paramName)
                .addModifiers(Modifier.PUBLIC);

        if (paramTypeName == TypeName.BOOLEAN) {
            builder.addStatement("setValue($L, ($L ? 1 : 0))", colName, paramName);
        } else {
            builder.addStatement("setValue($L, $L)", colName, paramName);
        }

        return builder.build();
    }

    private static MethodSpec composeColumnGetterMethod(String varName,
                                                        String varType,
                                                        String colName) {
        if (varName == null || varName.isEmpty()
                || varType == null || varType.isEmpty()
                || colName == null || colName.isEmpty()) {
            return null;
        }

        String setterName = varNameToMethodName(varName, CommonVariables.GETTER_PREFIX);
        LogUtils.debug("dbfield: getter name = %s", setterName);

        TypeName returnTypeName = getParamOrReturnTypeNameByType(varType);
        if (returnTypeName == null) {
            return null;
        }

        String getValueFuncName = getGetValueFunctionNameByType(varType);
        if (getValueFuncName == null || getValueFuncName.isEmpty()) {
            return null;
        }
        LogUtils.debug("dbfield: getter value func = %s", getValueFuncName);

        MethodSpec.Builder builder = MethodSpec.methodBuilder(setterName)
                .returns(returnTypeName)
                .addModifiers(Modifier.PUBLIC);

        if (returnTypeName == TypeName.BOOLEAN) {
            builder.addStatement("return ($L($L) == 1)", getValueFuncName, colName);
        } else {
            builder.addStatement("return $L($L)", getValueFuncName, colName);
        }

        return builder.build();
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
            colName = varNameToColumnName(varName);
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

        TypeName colTypeName = getColumnTypeNameByType(varType);
        if (colTypeName == null) {
            return null;
        }

        return FieldSpec.builder(
                ClassName.get(CommonVariables.DATABASE_OBJECT_PACKAGE, "Column"),
                "COLUMN_" + fieldNameSuffix,
                Modifier.STATIC,
                Modifier.PUBLIC)
                .initializer("new $T($S, $L, $L, $L)",
                        colTypeName, colName, allowNull, primary, version)
                .build();
    }


    private static String getGetValueFunctionNameByType(String varType) {
        if (varType == null || varType.isEmpty()) {
            return null;
        }

        String funcName = null;
        switch (varType.toLowerCase()) {
            case "int":
            case "boolean":
                funcName = "getIntegerValue";
                break;

            case "java.lang.string":
                funcName = "getTextValue";
                break;

            case "long":
                funcName = "getLongValue";
                break;

            case "double":
                funcName = "getDoubleValue";
                break;

            default:
                LogUtils.warn("[%s] is unsupported data type. ignored!", varType);
                break;
        }

        return funcName;
    }


    private static TypeName getParamOrReturnTypeNameByType(String varType) {
        if (varType == null || varType.isEmpty()) {
            return null;
        }

        TypeName cn = null;
        switch (varType.toLowerCase()) {
            case "int":
                cn = TypeName.INT;
                break;

            case "boolean":
                cn = TypeName.BOOLEAN;
                break;

            case "java.lang.string":
                cn = ClassName.get("java.lang", "String");
                break;

            case "long":
                cn = TypeName.LONG;
                break;

            case "double":
                cn = TypeName.DOUBLE;
                break;

            default:
                LogUtils.warn("[%s] is unsupported data type. ignored!", varType);
                break;
        }

        return cn;
    }

    private static TypeName getColumnTypeNameByType(String varType) {
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

    private static String varNameToColumnName(String varName) {
        if (varName == null || varName.isEmpty()) {
            return varName;
        }

        String colName = varName.replaceAll("([A-Z])", "_$1").toLowerCase();

        if (colName.startsWith("m_") || colName.startsWith("s_")) {
            colName = colName.substring(2);
        }

        return colName;
    }

    private static String varNameToMethodName(String varName, String methodPrefix) {
        if (varName == null || varName.isEmpty()) {
            return varName;
        }

        StringBuilder builder = new StringBuilder(methodPrefix);

        String methodName = varName;
        if (varName.startsWith("m") || varName.startsWith("s")) {
            methodName = methodName.substring(1);
        }

        builder.append(Character.toUpperCase(methodName.charAt(0)));
        builder.append(methodName.substring(1));

        return builder.toString();
    }

    private static String varNameToParameterName(String varName) {
        if (varName == null || varName.isEmpty()) {
            return varName;
        }

        StringBuilder builder = new StringBuilder();

        String paramName = varName;
        if (varName.startsWith("m") || varName.startsWith("s")) {
            paramName = paramName.substring(1);
        }

        builder.append(Character.toLowerCase(paramName.charAt(0)));
        builder.append(paramName.substring(1));

        return builder.toString();
    }


    public static boolean isValidSpecs(ColumnSpecs specs) {
        if (specs == null) {
            return false;
        }

//        return (specs.fieldSpec != null);
        return (specs.fieldSpec != null
                && specs.setterMethodSpec != null
                && specs.getterMethodSpec != null);
    }

}
