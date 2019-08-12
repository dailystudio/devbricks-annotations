package com.dailystudio.annotation;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.ref.WeakReference;

public class GlobalEnvironment {

    private static WeakReference<ProcessingEnvironment> sProcessingEnv = null;

    public static synchronized void attachToEnvironment(ProcessingEnvironment processingEnv) {
        sProcessingEnv = new WeakReference<>(processingEnv);
    }

    public static synchronized void detachFromEnvironment() {
        sProcessingEnv.clear();
        sProcessingEnv = null;
    }

    public static synchronized ProcessingEnvironment get() {
        if (sProcessingEnv == null) {
            return null;
        }

        return sProcessingEnv.get();
    }



}
