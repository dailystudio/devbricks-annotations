package com.dailystudio.annotation.processor;

import androidx.annotation.Keep;
import com.dailystudio.annotation.DBColumn;
import com.dailystudio.annotation.DBObject;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

public class DBObjectProcessor extends AbstractProcessor {

    private final static String DATABASE_OBJECT_PACKAGE = "com.dailystudio.dataobject";
    private final static int DEFAULT_VERSION = 0x1;

    private Filer mFiler;
    private Elements mElementUtils;
    private Messager mMessager;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mFiler = processingEnv.getFiler();
        mMessager = processingEnv.getMessager();
        mElementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        Set<? extends Element> elements =
                roundEnv.getElementsAnnotatedWith(DBObject.class);

        TypeElement typeElement;
        VariableElement varElement;
        for (Element element : elements) {
            if (element instanceof TypeElement) {
                typeElement = (TypeElement) element;

                int latestVersion = DEFAULT_VERSION;
                DBObject dbObject = typeElement.getAnnotation(DBObject.class);
                if (dbObject != null) {
                    latestVersion = dbObject.latestVersion();
                }

                String packageName = mElementUtils.getPackageOf(typeElement).getQualifiedName().toString();
                String typeName = typeElement.getSimpleName().toString();

                ClassName generatedClassName = ClassName
                        .get(packageName, GenUtils.getDBObjectGenClassName(typeName));
                note("gen class: %s", generatedClassName);

                MethodSpec constructorBase = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get("android.content", "Context"),
                                "context")
                        .addParameter(TypeName.INT, "version")
                        .addStatement("super(context, version)")
                        .addStatement("initMembers()")
                        .build();

                MethodSpec constructorShortcut = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get("android.content", "Context"),
                                "context")
                        .addStatement("this(context, $L)", latestVersion)
                        .build();

                // define the wrapper class
                TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(ClassName.get(DATABASE_OBJECT_PACKAGE, "DatabaseObject"))
                        .addMethod(constructorShortcut)
                        .addMethod(constructorBase)
                        .addAnnotation(Keep.class);

                note("dbobject: package = %s", packageName);
                note("dbobject: class = %s", typeName);
                List<? extends Element> subElements = element.getEnclosedElements();
                note("dbobject: sub-elements = %s", subElements);

                Map<Integer, List<FieldSpec>> fieldsMap = new HashMap<>();
                FieldSpec fieldSpec;
                for (Element subElement: subElements) {
                    if (subElement instanceof VariableElement) {
                        varElement = (VariableElement) subElement;

                        String varName = varElement.getSimpleName().toString();
                        DBColumn dbColumn = varElement.getAnnotation(DBColumn.class);
                        if (dbColumn == null) {
                            continue;
                        }

                        TypeMirror fieldType = varElement.asType();
                        String varTypeName = fieldType.toString();

                        note("dbfield: name = %s", varName);
                        note("dbfield: type = %s", varTypeName);

                        int version = dbColumn.version();

                        fieldSpec = composeColumnField(varName, varTypeName, dbColumn);
                        if (fieldSpec != null) {
                            classBuilder.addField(fieldSpec);

                            List<FieldSpec> specs;
                            if (fieldsMap.containsKey(version)) {
                                specs = fieldsMap.get(version);
                            } else {
                                specs = new ArrayList<>();
                            }

                            specs.add(fieldSpec);
                            fieldsMap.put(dbColumn.version(), specs);
                        }
                    }
                }

                List<FieldSpec> columnsFields = composeColumnsFields(fieldsMap);
                if (columnsFields != null) {

                    FieldSpec spec;
                    for (int i = 0; i < columnsFields.size(); i++) {
                        spec = columnsFields.get(i);

                        classBuilder.addField(spec);
                    }
                }

                MethodSpec initMemberMethod = composeInitMemberMethod(fieldsMap);
                if (initMemberMethod != null) {
                    classBuilder.addMethod(initMemberMethod);
                }

                try {
                    JavaFile.builder(packageName,
                            classBuilder.build())
                            .build()
                            .writeTo(mFiler);
                } catch (IOException e) {
                    error("generate class for %s failed: %s", typeElement, e.toString());
                }
            }
        }

        return true;
    }

    private MethodSpec composeInitMemberMethod(Map<Integer, List<FieldSpec>> fieldSpecs) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("initMembers")
                .addStatement("final $T templ = getTemplate();",
                        ClassName.get(DATABASE_OBJECT_PACKAGE, "Template"))
                .addModifiers(Modifier.PRIVATE);
        if (fieldSpecs == null) {
            return builder.build();
        }

        Set<Integer> keys = fieldSpecs.keySet();
        if (keys == null || keys.size() <= 0) {
            return builder.build();
        }

        List<Integer> versions = new LinkedList<>(keys);
        Collections.sort(versions);

        int ver;
        String fieldName;
        for (int i = 0; i < versions.size(); i++) {
            ver = versions.get(i);


            fieldName = getVerColumnsFieldName(ver);

            if (i == 0) {
                builder.beginControlFlow("if (mVersion == $L)", ver);
            } else {
                builder.nextControlFlow("else if (mVersion == $L)", ver);
            }

            builder.addStatement("templ.addColumns($L)",
                    fieldName);
        }

        builder.endControlFlow();

        return builder.build();
    }

    private List<FieldSpec> composeColumnsFields(Map<Integer, List<FieldSpec>> fieldSpecs) {
        if (fieldSpecs == null || fieldSpecs.size() <= 0) {
            return null;
        }

        List<FieldSpec> columnsFields = new ArrayList<>();

        Set<Integer> keys = fieldSpecs.keySet();

        List<Integer> versions = new LinkedList<>(keys);
        Collections.sort(versions);

        StringBuilder columnsInitializationStatement = new StringBuilder();

        List<FieldSpec> verSpecs = new ArrayList<>();
        List<FieldSpec> currVerSpecs;
        FieldSpec columnsField;

        int ver;
        String fieldName;
        for (int i = 0; i < versions.size(); i++) {
            ver = versions.get(i);

            currVerSpecs = fieldSpecs.get(ver);
            if (currVerSpecs == null || currVerSpecs.size() <= 0) {
                continue;
            }

            columnsInitializationStatement.setLength(0);

            for (FieldSpec fieldSpec: currVerSpecs) {
                verSpecs.add(fieldSpec);
            }

            for (FieldSpec fieldSpec: verSpecs) {
                columnsInitializationStatement.append(fieldSpec.name);
                columnsInitializationStatement.append(",");
            }

            fieldName = getVerColumnsFieldName(ver);

            columnsField = FieldSpec.builder(
                    ArrayTypeName.of(ClassName.get(DATABASE_OBJECT_PACKAGE, "Column")),
                    fieldName,
                    Modifier.STATIC,
                    Modifier.PUBLIC)
                    .initializer("{" + columnsInitializationStatement.toString() + "}")
                    .build();

            columnsFields.add(columnsField);
        }

        return columnsFields;
    }

    private FieldSpec composeColumnField(String varName,
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
            warn("parse allowNull for [%s] failed: %s, use default",
                    varName, e.toString());

            allowNull = true;
        }

        boolean primary = false;
        String primaryStr = dbColumn.primary();
        try {
            primary = Boolean.parseBoolean(primaryStr);
        } catch (Exception e) {
            warn("parse primary for [%s] failed: %s, use default",
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
                ClassName.get(DATABASE_OBJECT_PACKAGE, "Column"),
                "COLUMN_" + fieldNameSuffix,
                Modifier.STATIC,
                Modifier.PUBLIC)
                .initializer("new $T($S, $L, $L, $L)",
                        colClassName, colName, allowNull, primary, version)
                .build();
    }

    private ClassName getColumnClassNameByType(String varType) {
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
                warn("[%s] is unsupported data type. ignored!", varType);
                break;
        }

        if (colClassName == null) {
            return null;
        }

        return ClassName.get(DATABASE_OBJECT_PACKAGE, colClassName);
    }

    private String getVerColumnsFieldName(int version) {
        return "sColumns_Ver" + version;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<>(Arrays.asList(
                DBObject.class.getCanonicalName(),
                DBColumn.class.getCanonicalName()));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private void note(String format, Object... args) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, String.format(format, args));
    }

    private void error(String format, Object... args) {
        mMessager.printMessage(Diagnostic.Kind.ERROR, String.format(format, args));
    }

    private void warn(String format, Object... args) {
        mMessager.printMessage(Diagnostic.Kind.WARNING, String.format(format, args));
    }

}