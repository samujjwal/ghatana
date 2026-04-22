package com.ghatana.testing.nativesupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Local stub for NativeDependencySupport.
 * Mirrors platform:java:testing but avoids Java 21 dependency for this Java 17 module.
 */
public class NativeDependencySupport {

    @Target({ElementType.TYPE, ElementType.METHOD}) // GH-90000
    @Retention(RetentionPolicy.RUNTIME) // GH-90000
    @ExtendWith(NativeDependencyCondition.class) // GH-90000
    public @interface RequireNative {
        NativeType value() default NativeType.ANY; // GH-90000
        String message() default "Native dependencies not available"; // GH-90000
    }

    public enum NativeType {
        WHISPER_CPP,
        COQUI_TTS,
        ANY
    }

    public static class NativeDependencyCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) { // GH-90000
            RequireNative annotation = context.getElement() // GH-90000
                .map(el -> el.getAnnotation(RequireNative.class)) // GH-90000
                .orElse(null); // GH-90000

            if (annotation == null) { // GH-90000
                return ConditionEvaluationResult.enabled("No @RequireNative annotation [GH-90000]");
            }

            NativeType requiredType = annotation.value(); // GH-90000
            String customMessage = annotation.message(); // GH-90000

            if (isNativeAvailable(requiredType)) { // GH-90000
                return ConditionEvaluationResult.enabled("Native dependencies available [GH-90000]");
            } else {
                String message = String.format("%s: %s not available", // GH-90000
                    customMessage, requiredType);
                return ConditionEvaluationResult.disabled(message); // GH-90000
            }
        }
    }

    private static boolean isNativeAvailable(NativeType type) { // GH-90000
        switch (type) { // GH-90000
            case WHISPER_CPP:
                return isClassAvailable("com.ghatana.stt.core.whisper.WhisperCppAdapter [GH-90000]");
            case COQUI_TTS:
                return isClassAvailable("com.ghatana.tts.core.coqui.CoquiTTSAdapter [GH-90000]");
            case ANY:
                return isClassAvailable("com.ghatana.stt.core.whisper.WhisperCppAdapter [GH-90000]") ||
                       isClassAvailable("com.ghatana.tts.core.coqui.CoquiTTSAdapter [GH-90000]");
            default:
                return false;
        }
    }

    private static boolean isClassAvailable(String className) { // GH-90000
        try {
            Class<?> clazz = Class.forName(className); // GH-90000
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("NATIVE_LIBRARY_AVAILABLE [GH-90000]");
                field.setAccessible(true); // GH-90000
                return field.getBoolean(null); // GH-90000
            } catch (NoSuchFieldException | IllegalAccessException e) { // GH-90000
                return true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError | UnsatisfiedLinkError e) { // GH-90000
            return false;
        }
    }
}
