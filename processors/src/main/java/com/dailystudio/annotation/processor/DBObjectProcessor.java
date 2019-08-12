package com.dailystudio.annotation.processor;

import androidx.annotation.Keep;
import com.dailystudio.annotation.DBColumn;
import com.dailystudio.annotation.DBObject;
import com.dailystudio.annotation.processor.database.ColumnSpecs;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.*;

public class DBObjectProcessor extends BaseProcessor {

    private final static String DATABASE_OBJECT_PACKAGE = "com.dailystudio.dataobject";
    private final static int DEFAULT_VERSION = 0x1;

    private Filer mFiler;
    private Elements mElementUtils;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mFiler = processingEnv.getFiler();
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
                ColumnSpecs columnSpecs;
                for (Element subElement: subElements) {
                    if (subElement instanceof VariableElement) {
                        varElement = (VariableElement) subElement;

                        columnSpecs = ColumnSpecs.fromVariableElement(varElement);
                        if (ColumnSpecs.isValidSpecs(columnSpecs)) {
                            classBuilder.addField(columnSpecs.fieldSpec);

                            List<FieldSpec> specs;
                            if (fieldsMap.containsKey(columnSpecs.version)) {
                                specs = fieldsMap.get(columnSpecs.version);
                            } else {
                                specs = new ArrayList<>();
                            }

                            specs.add(columnSpecs.fieldSpec);
                            fieldsMap.put(columnSpecs.version, specs);
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

}