package com.ghatana.audio.video.common.proto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Semantic proto compatibility checks between Java service protos and
 * desktop Rust protos for shared contracts.
 */
@DisplayName("Proto compatibility")
class ProtoCompatibilityTest {

    @Test
    @DisplayName("shared core RPC methods stay aligned across Java and Rust protos")
    void shouldKeepSharedRpcMethodsAligned() throws IOException { // GH-90000
        ProtoSchema ttsJava = loadSchema("products/audio-video/modules/speech/tts-service/src/main/proto/tts_service.proto");
        ProtoSchema ttsRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/tts.proto");
        assertSharedRpcMethods(ttsJava, ttsRust, "TTSService", Set.of("Synthesize", "StreamSynthesize", "GetStatus")); // GH-90000

        ProtoSchema sttJava = loadSchema("products/audio-video/modules/speech/stt-service/src/main/proto/stt_service.proto");
        ProtoSchema sttRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/stt.proto");
        assertSharedRpcMethods(sttJava, sttRust, "STTService", Set.of("Transcribe", "StreamTranscribe", "GetStatus", "HealthCheck")); // GH-90000

        ProtoSchema visionJava = loadSchema("products/audio-video/modules/vision/vision-service/src/main/proto/vision_service.proto");
        ProtoSchema visionRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/vision.proto");
        assertSharedRpcMethods(visionJava, visionRust, "VisionService", Set.of("DetectObjects", "AnalyzeImage", "HealthCheck")); // GH-90000

        ProtoSchema multimodalJava = loadSchema("products/audio-video/modules/intelligence/multimodal-service/src/main/proto/multimodal_service.proto");
        ProtoSchema multimodalRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/multimodal.proto");
        assertSharedRpcMethods(multimodalJava, multimodalRust, "MultimodalService", Set.of("ProcessMultimodal", "GenerateDescription", "HealthCheck")); // GH-90000
    }

    @Test
    @DisplayName("shared TTS message shapes stay compatible")
    void shouldKeepTtsSharedMessagesCompatible() throws IOException { // GH-90000
        ProtoSchema javaSchema = loadSchema("products/audio-video/modules/speech/tts-service/src/main/proto/tts_service.proto");
        ProtoSchema rustSchema = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/tts.proto");

        assertMessageEqual(javaSchema, rustSchema, "SynthesizeRequest"); // GH-90000
        assertRustMessageSubset(javaSchema, rustSchema, "SynthesisOptions"); // GH-90000
        assertMessageEqual(javaSchema, rustSchema, "SynthesizeResponse"); // GH-90000
        assertMessageEqual(javaSchema, rustSchema, "AudioChunk"); // GH-90000
    }

    @Test
    @DisplayName("shared Vision and Multimodal message shapes stay compatible")
    void shouldKeepVisionAndMultimodalMessagesCompatible() throws IOException { // GH-90000
        ProtoSchema visionJava = loadSchema("products/audio-video/modules/vision/vision-service/src/main/proto/vision_service.proto");
        ProtoSchema visionRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/vision.proto");

        assertMessageEqual(visionJava, visionRust, "DetectRequest"); // GH-90000
        assertMessageEqual(visionJava, visionRust, "DetectResponse"); // GH-90000
        assertMessageEqual(visionJava, visionRust, "Detection"); // GH-90000
        assertMessageEqual(visionJava, visionRust, "BoundingBox"); // GH-90000

        ProtoSchema multimodalJava = loadSchema("products/audio-video/modules/intelligence/multimodal-service/src/main/proto/multimodal_service.proto");
        ProtoSchema multimodalRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/multimodal.proto");

        assertMessageEqual(multimodalJava, multimodalRust, "MultimodalRequest"); // GH-90000
        assertMessageEqual(multimodalJava, multimodalRust, "MultimodalResponse"); // GH-90000
        assertMessageEqual(multimodalJava, multimodalRust, "AudioAnalysis"); // GH-90000
        assertMessageEqual(multimodalJava, multimodalRust, "VisualAnalysis"); // GH-90000
        assertMessageEqual(multimodalJava, multimodalRust, "DescriptionRequest"); // GH-90000
        assertMessageEqual(multimodalJava, multimodalRust, "DescriptionResponse"); // GH-90000
    }

    private static void assertSharedRpcMethods( // GH-90000
            ProtoSchema javaSchema,
            ProtoSchema rustSchema,
            String serviceName,
            Set<String> requiredMethods
    ) {
        Map<String, RpcSignature> javaMethods = javaSchema.services.get(serviceName); // GH-90000
        Map<String, RpcSignature> rustMethods = rustSchema.services.get(serviceName); // GH-90000

        assertThat(javaMethods) // GH-90000
                .as("Java proto must define service %s", serviceName) // GH-90000
                .isNotNull(); // GH-90000
        assertThat(rustMethods) // GH-90000
                .as("Rust proto must define service %s", serviceName) // GH-90000
                .isNotNull(); // GH-90000

        assertThat(javaMethods.keySet()).containsAll(requiredMethods); // GH-90000
        assertThat(rustMethods.keySet()).containsAll(requiredMethods); // GH-90000

        for (String methodName : requiredMethods) { // GH-90000
            assertThat(javaMethods.get(methodName)) // GH-90000
                    .as("Java RPC signature for %s", methodName) // GH-90000
                    .isEqualTo(rustMethods.get(methodName)); // GH-90000
        }
    }

    private static void assertMessageEqual( // GH-90000
            ProtoSchema javaSchema,
            ProtoSchema rustSchema,
            String messageName
    ) {
        Map<Integer, String> javaFields = javaSchema.messages.get(messageName); // GH-90000
        Map<Integer, String> rustFields = rustSchema.messages.get(messageName); // GH-90000

        assertThat(javaFields) // GH-90000
                .as("Java proto must define message %s", messageName) // GH-90000
                .isNotNull(); // GH-90000
        assertThat(rustFields) // GH-90000
                .as("Rust proto must define message %s", messageName) // GH-90000
                .isNotNull(); // GH-90000
        assertThat(javaFields) // GH-90000
                .as("Message %s must stay semantically aligned", messageName) // GH-90000
                .isEqualTo(rustFields); // GH-90000
    }

        private static void assertRustMessageSubset( // GH-90000
            ProtoSchema javaSchema,
            ProtoSchema rustSchema,
            String messageName
        ) {
        Map<Integer, String> javaFields = javaSchema.messages.get(messageName); // GH-90000
        Map<Integer, String> rustFields = rustSchema.messages.get(messageName); // GH-90000

        assertThat(javaFields) // GH-90000
            .as("Java proto must define message %s", messageName) // GH-90000
            .isNotNull(); // GH-90000
        assertThat(rustFields) // GH-90000
            .as("Rust proto must define message %s", messageName) // GH-90000
            .isNotNull(); // GH-90000

        for (Map.Entry<Integer, String> rustField : rustFields.entrySet()) { // GH-90000
            assertThat(javaFields) // GH-90000
                .as("Java message %s must include Rust field #%s", messageName, rustField.getKey()) // GH-90000
                .containsEntry(rustField.getKey(), rustField.getValue()); // GH-90000
        }
        }

    private static ProtoSchema loadSchema(String repoRelativePath) throws IOException { // GH-90000
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        Path directCandidate = repoRoot.resolve(repoRelativePath); // GH-90000
        Path fallbackCandidate = repoRoot.resolve(repoRelativePath.replaceFirst("^products/audio-video/", "")); // GH-90000

        Path resolvedPath;
        if (Files.exists(directCandidate)) { // GH-90000
            resolvedPath = directCandidate;
        } else if (Files.exists(fallbackCandidate)) { // GH-90000
            resolvedPath = fallbackCandidate;
        } else {
            throw new IOException("Proto file not found: " + repoRelativePath); // GH-90000
        }

        String content = Files.readString(resolvedPath); // GH-90000
        return parseProto(content); // GH-90000
    }

    private static Path findRepoRoot(Path start) { // GH-90000
        Path current = start;
        while (current != null) { // GH-90000
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent(); // GH-90000
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static ProtoSchema parseProto(String content) { // GH-90000
        Map<String, Map<String, RpcSignature>> services = new LinkedHashMap<>(); // GH-90000
        Map<String, Map<Integer, String>> messages = new LinkedHashMap<>(); // GH-90000

        Pattern servicePattern = Pattern.compile("service\\s+(\\w+)\\s*\\{(.*?)\\}", Pattern.DOTALL); // GH-90000
        Matcher serviceMatcher = servicePattern.matcher(content); // GH-90000
        while (serviceMatcher.find()) { // GH-90000
            String serviceName = serviceMatcher.group(1); // GH-90000
            String body = serviceMatcher.group(2); // GH-90000
            services.put(serviceName, parseRpcMethods(body)); // GH-90000
        }

        Pattern messagePattern = Pattern.compile("message\\s+(\\w+)\\s*\\{(.*?)\\}", Pattern.DOTALL); // GH-90000
        Matcher messageMatcher = messagePattern.matcher(content); // GH-90000
        while (messageMatcher.find()) { // GH-90000
            String messageName = messageMatcher.group(1); // GH-90000
            String body = messageMatcher.group(2); // GH-90000
            messages.put(messageName, parseMessageFields(body)); // GH-90000
        }

        return new ProtoSchema(services, messages); // GH-90000
    }

    private static Map<String, RpcSignature> parseRpcMethods(String serviceBody) { // GH-90000
        Map<String, RpcSignature> methods = new LinkedHashMap<>(); // GH-90000
        Pattern rpcPattern = Pattern.compile( // GH-90000
                "rpc\\s+(\\w+)\\s*\\(\\s*(?:stream\\s+)?([\\w.]+)\\s*\\)\\s*returns\\s*\\(\\s*(?:stream\\s+)?([\\w.]+)\\s*\\)", // GH-90000
                Pattern.MULTILINE
        );
        Matcher rpcMatcher = rpcPattern.matcher(serviceBody); // GH-90000
        while (rpcMatcher.find()) { // GH-90000
            String name = rpcMatcher.group(1); // GH-90000
            String request = rpcMatcher.group(2); // GH-90000
            String response = rpcMatcher.group(3); // GH-90000
            methods.put(name, new RpcSignature(request, response)); // GH-90000
        }
        return methods;
    }

    private static Map<Integer, String> parseMessageFields(String messageBody) { // GH-90000
        Map<Integer, String> fields = new LinkedHashMap<>(); // GH-90000
        Pattern fieldPattern = Pattern.compile( // GH-90000
                "^\\s*(repeated\\s+)?(map<[^>]+>|[\\w.]+)\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;", // GH-90000
                Pattern.MULTILINE
        );
        Matcher fieldMatcher = fieldPattern.matcher(messageBody); // GH-90000
        while (fieldMatcher.find()) { // GH-90000
            boolean repeated = fieldMatcher.group(1) != null; // GH-90000
            String type = fieldMatcher.group(2); // GH-90000
            String name = fieldMatcher.group(3); // GH-90000
            int index = Integer.parseInt(fieldMatcher.group(4)); // GH-90000
            String signature = (repeated ? "repeated " : "") + type + " " + name; // GH-90000
            fields.put(index, signature); // GH-90000
        }
        return fields;
    }

    private record RpcSignature(String requestType, String responseType) { // GH-90000
    }

    private record ProtoSchema( // GH-90000
            Map<String, Map<String, RpcSignature>> services,
            Map<String, Map<Integer, String>> messages
    ) {
    }
}
