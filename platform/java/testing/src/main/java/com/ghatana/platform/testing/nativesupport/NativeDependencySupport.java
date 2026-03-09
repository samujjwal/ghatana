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
 * Utility for handling native dependencies in tests.
 * 
 * This provides:
 * - @RequireNative annotation to skip tests when native deps aren't available
 * - NativeDependencyChecker utility class for runtime detection
 * - Automatic test skipping with informative messages
 * 
 * @doc.type class
 * @doc.purpose Test support for native dependencies
 * @doc.layer testing
 * @doc.pattern Conditional Testing, Native Integration
 */
public class NativeDependencySupport {

    /**
     * Annotation to mark tests that require native dependencies.
     * Tests will be automatically skipped if native dependencies aren't available.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(NativeDependencyCondition.class)
    public @interface RequireNative {
        /**
         * @return The type of native dependency required
         */
        NativeType value() default NativeType.ANY;
        
        /**
         * @return Custom message to display when test is skipped
         */
        String message() default "Native dependencies not available";
    }

    /**
     * Types of native dependencies that can be required.
     */
    public enum NativeType {
        /** Whisper.cpp for speech-to-text */
        WHISPER_CPP,
        /** Coqui TTS for text-to-speech */
        COQUI_TTS,
        /** Any native dependency */
        ANY
    }

    /**
     * JUnit 5 condition for skipping tests when native dependencies aren't available.
     */
    public static class NativeDependencyCondition implements ExecutionCondition {
        
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            RequireNative annotation = context.getElement()
                .map(el -> el.getAnnotation(RequireNative.class))
                .orElse(null);
                
            if (annotation == null) {
                return ConditionEvaluationResult.enabled("No @RequireNative annotation");
            }
            
            NativeType requiredType = annotation.value();
            String customMessage = annotation.message();
            
            if (NativeDependencyChecker.isNativeAvailable(requiredType)) {
                return ConditionEvaluationResult.enabled("Native dependencies available");
            } else {
                String message = String.format("%s: %s not available", 
                    customMessage, requiredType);
                return ConditionEvaluationResult.disabled(message);
            }
        }
    }

    /**
     * Utility class for checking native dependency availability at runtime.
     */
    public static class NativeDependencyChecker {
        
        private static final String WHISPER_CPP_CLASS = "com.ghatana.stt.core.whisper.WhisperCppAdapter";
        private static final String COQUI_TTS_CLASS = "com.ghatana.tts.core.coqui.CoquiTTSAdapter";
        
        /**
         * Check if native dependencies are available for the specified type.
         * 
         * @param type The type of native dependency to check
         * @return true if available, false otherwise
         */
        public static boolean isNativeAvailable(NativeType type) {
            switch (type) {
                case WHISPER_CPP:
                    return isClassAvailable(WHISPER_CPP_CLASS);
                case COQUI_TTS:
                    return isClassAvailable(COQUI_TTS_CLASS);
                case ANY:
                    return isClassAvailable(WHISPER_CPP_CLASS) || 
                           isClassAvailable(COQUI_TTS_CLASS);
                default:
                    return false;
            }
        }
        
        /**
         * Check if a specific class can be loaded (indicating native library availability).
         * 
         * @param className The fully qualified class name to check
         * @return true if the class can be loaded AND native library is available, false otherwise
         */
        private static boolean isClassAvailable(String className) {
            try {
                Class<?> clazz = Class.forName(className);
                // Check if the native library is actually available by looking for NATIVE_LIBRARY_AVAILABLE field
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField("NATIVE_LIBRARY_AVAILABLE");
                    field.setAccessible(true);
                    return field.getBoolean(null);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // If field doesn't exist, class loading success means native lib is available
                    return true;
                }
            } catch (ClassNotFoundException e) {
                return false;
            } catch (NoClassDefFoundError e) {
                return false;
            } catch (UnsatisfiedLinkError e) {
                // Native library not found
                return false;
            }
        }
        
        /**
         * Get a descriptive message about native dependency availability.
         * 
         * @return A human-readable status message
         */
        public static String getNativeStatusMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append("Native Dependencies Status:\n");
            
            boolean whisperAvailable = isClassAvailable(WHISPER_CPP_CLASS);
            boolean coquiAvailable = isClassAvailable(COQUI_TTS_CLASS);
            
            sb.append("  Whisper.cpp: ").append(whisperAvailable ? "✅ Available" : "❌ Not Available").append("\n");
            sb.append("  Coqui TTS: ").append(coquiAvailable ? "✅ Available" : "❌ Not Available").append("\n");
            
            if (!whisperAvailable || !coquiAvailable) {
                sb.append("\nTo install native dependencies, run:\n");
                sb.append("  ./scripts/setup-native-deps.sh\n");
            }
            
            return sb.toString();
        }
        
        /**
         * Assert that native dependencies are available, throwing an informative exception if not.
         * 
         * @param type The type of native dependency required
         * @throws AssertionError if the native dependency is not available
         */
        public static void assertNativeAvailable(NativeType type) {
            if (!isNativeAvailable(type)) {
                throw new AssertionError(String.format(
                    "Native dependency %s is not available. %s\n\n%s",
                    type,
                    "Run './scripts/setup-native-deps.sh' to install native dependencies.",
                    getNativeStatusMessage()
                ));
            }
        }
    }
}
