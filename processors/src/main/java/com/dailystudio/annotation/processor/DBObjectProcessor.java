package com.dailystudio.annotation.processor;

import androidx.annotation.Keep;
import com.dailystudio.annotation.DBObject;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

public class DBObjectProcessor extends AbstractProcessor {

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
        Set<? extends Element> dbObjectElements =
                roundEnv.getElementsAnnotatedWith(DBObject.class);

        TypeElement typeElement;
        for (Element element : dbObjectElements) {
            if (element instanceof TypeElement == false) {
                continue;
            }

            typeElement = (TypeElement) element;

            String packageName = mElementUtils.getPackageOf(typeElement).getQualifiedName().toString();
            String typeName = typeElement.getSimpleName().toString();
            ClassName className = ClassName.get(packageName, typeName);

            note("package: %s", packageName);
            note("type: %s", typeName);

            ClassName generatedClassName = ClassName
                    .get(packageName, GenUtils.getDBObjectGenClassName(typeName));
            note("gen class: %s", generatedClassName);

            MethodSpec constructorBase = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ClassName.get("android.content", "Context"),
                            "context")
                    .addParameter(TypeName.INT, "version")
                    .addStatement("super(context, version)")
                    .build();

            // define the wrapper class
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                    .addModifiers(Modifier.PUBLIC)
                    .superclass(ClassName.get("com.dailystudio.dataobject", "DatabaseObject"))
                    .addMethod(constructorBase)
                    .addAnnotation(Keep.class);

            try {
                JavaFile.builder(packageName,
                        classBuilder.build())
                        .build()
                        .writeTo(mFiler);
            } catch (IOException e) {
                error("generate class for %s failed: %s", typeElement, e.toString());
            }
        }

        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(DBObject.class.getCanonicalName());
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

}