package com.ghatana.platform.testing.nativesupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Local stub — tts-service Java 17 can't depend on platform:java:testing (Java 21). // GH-90000
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
        WHISPER_CPP, COQUI_TTS, ANY
    }

    static class NativeDependencyCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) { // GH-90000
            return ConditionEvaluationResult.disabled("Native dependencies not available in test environment [GH-90000]");
        }
    }
}
