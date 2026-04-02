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
    void shouldKeepSharedRpcMethodsAligned() throws IOException {
        ProtoSchema ttsJava = loadSchema("products/audio-video/modules/speech/tts-service/src/main/proto/tts_service.proto");
        ProtoSchema ttsRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/tts.proto");
        assertSharedRpcMethods(ttsJava, ttsRust, "TTSService", Set.of("Synthesize", "StreamSynthesize", "GetStatus"));

        ProtoSchema sttJava = loadSchema("products/audio-video/modules/speech/stt-service/src/main/proto/stt_service.proto");
        ProtoSchema sttRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/stt.proto");
        assertSharedRpcMethods(sttJava, sttRust, "STTService", Set.of("Transcribe", "StreamTranscribe", "GetStatus", "HealthCheck"));

        ProtoSchema visionJava = loadSchema("products/audio-video/modules/vision/vision-service/src/main/proto/vision_service.proto");
        ProtoSchema visionRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/vision.proto");
        assertSharedRpcMethods(visionJava, visionRust, "VisionService", Set.of("DetectObjects", "AnalyzeImage", "HealthCheck"));

        ProtoSchema multimodalJava = loadSchema("products/audio-video/modules/intelligence/multimodal-service/src/main/proto/multimodal_service.proto");
        ProtoSchema multimodalRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/multimodal.proto");
        assertSharedRpcMethods(multimodalJava, multimodalRust, "MultimodalService", Set.of("ProcessMultimodal", "GenerateDescription", "HealthCheck"));
    }

    @Test
    @DisplayName("shared TTS message shapes stay compatible")
    void shouldKeepTtsSharedMessagesCompatible() throws IOException {
        ProtoSchema javaSchema = loadSchema("products/audio-video/modules/speech/tts-service/src/main/proto/tts_service.proto");
        ProtoSchema rustSchema = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/tts.proto");

        assertMessageEqual(javaSchema, rustSchema, "SynthesizeRequest");
        assertRustMessageSubset(javaSchema, rustSchema, "SynthesisOptions");
        assertMessageEqual(javaSchema, rustSchema, "SynthesizeResponse");
        assertMessageEqual(javaSchema, rustSchema, "AudioChunk");
    }

    @Test
    @DisplayName("shared Vision and Multimodal message shapes stay compatible")
    void shouldKeepVisionAndMultimodalMessagesCompatible() throws IOException {
        ProtoSchema visionJava = loadSchema("products/audio-video/modules/vision/vision-service/src/main/proto/vision_service.proto");
        ProtoSchema visionRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/vision.proto");

        assertMessageEqual(visionJava, visionRust, "DetectRequest");
        assertMessageEqual(visionJava, visionRust, "DetectResponse");
        assertMessageEqual(visionJava, visionRust, "Detection");
        assertMessageEqual(visionJava, visionRust, "BoundingBox");

        ProtoSchema multimodalJava = loadSchema("products/audio-video/modules/intelligence/multimodal-service/src/main/proto/multimodal_service.proto");
        ProtoSchema multimodalRust = loadSchema("products/audio-video/apps/desktop/src-tauri/proto/multimodal.proto");

        assertMessageEqual(multimodalJava, multimodalRust, "MultimodalRequest");
        assertMessageEqual(multimodalJava, multimodalRust, "MultimodalResponse");
        assertMessageEqual(multimodalJava, multimodalRust, "AudioAnalysis");
        assertMessageEqual(multimodalJava, multimodalRust, "VisualAnalysis");
        assertMessageEqual(multimodalJava, multimodalRust, "DescriptionRequest");
        assertMessageEqual(multimodalJava, multimodalRust, "DescriptionResponse");
    }

    private static void assertSharedRpcMethods(
            ProtoSchema javaSchema,
            ProtoSchema rustSchema,
            String serviceName,
            Set<String> requiredMethods
    ) {
        Map<String, RpcSignature> javaMethods = javaSchema.services.get(serviceName);
        Map<String, RpcSignature> rustMethods = rustSchema.services.get(serviceName);

        assertThat(javaMethods)
                .as("Java proto must define service %s", serviceName)
                .isNotNull();
        assertThat(rustMethods)
                .as("Rust proto must define service %s", serviceName)
                .isNotNull();

        assertThat(javaMethods.keySet()).containsAll(requiredMethods);
        assertThat(rustMethods.keySet()).containsAll(requiredMethods);

        for (String methodName : requiredMethods) {
            assertThat(javaMethods.get(methodName))
                    .as("Java RPC signature for %s", methodName)
                    .isEqualTo(rustMethods.get(methodName));
        }
    }

    private static void assertMessageEqual(
            ProtoSchema javaSchema,
            ProtoSchema rustSchema,
            String messageName
    ) {
        Map<Integer, String> javaFields = javaSchema.messages.get(messageName);
        Map<Integer, String> rustFields = rustSchema.messages.get(messageName);

        assertThat(javaFields)
                .as("Java proto must define message %s", messageName)
                .isNotNull();
        assertThat(rustFields)
                .as("Rust proto must define message %s", messageName)
                .isNotNull();
        assertThat(javaFields)
                .as("Message %s must stay semantically aligned", messageName)
                .isEqualTo(rustFields);
    }

        private static void assertRustMessageSubset(
            ProtoSchema javaSchema,
            ProtoSchema rustSchema,
            String messageName
        ) {
        Map<Integer, String> javaFields = javaSchema.messages.get(messageName);
        Map<Integer, String> rustFields = rustSchema.messages.get(messageName);

        assertThat(javaFields)
            .as("Java proto must define message %s", messageName)
            .isNotNull();
        assertThat(rustFields)
            .as("Rust proto must define message %s", messageName)
            .isNotNull();

        for (Map.Entry<Integer, String> rustField : rustFields.entrySet()) {
            assertThat(javaFields)
                .as("Java message %s must include Rust field #%s", messageName, rustField.getKey())
                .containsEntry(rustField.getKey(), rustField.getValue());
        }
        }

    private static ProtoSchema loadSchema(String repoRelativePath) throws IOException {
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());
        Path directCandidate = repoRoot.resolve(repoRelativePath);
        Path fallbackCandidate = repoRoot.resolve(repoRelativePath.replaceFirst("^products/audio-video/", ""));

        Path resolvedPath;
        if (Files.exists(directCandidate)) {
            resolvedPath = directCandidate;
        } else if (Files.exists(fallbackCandidate)) {
            resolvedPath = fallbackCandidate;
        } else {
            throw new IOException("Proto file not found: " + repoRelativePath);
        }

        String content = Files.readString(resolvedPath);
        return parseProto(content);
    }

    private static Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static ProtoSchema parseProto(String content) {
        Map<String, Map<String, RpcSignature>> services = new LinkedHashMap<>();
        Map<String, Map<Integer, String>> messages = new LinkedHashMap<>();

        Pattern servicePattern = Pattern.compile("service\\s+(\\w+)\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher serviceMatcher = servicePattern.matcher(content);
        while (serviceMatcher.find()) {
            String serviceName = serviceMatcher.group(1);
            String body = serviceMatcher.group(2);
            services.put(serviceName, parseRpcMethods(body));
        }

        Pattern messagePattern = Pattern.compile("message\\s+(\\w+)\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher messageMatcher = messagePattern.matcher(content);
        while (messageMatcher.find()) {
            String messageName = messageMatcher.group(1);
            String body = messageMatcher.group(2);
            messages.put(messageName, parseMessageFields(body));
        }

        return new ProtoSchema(services, messages);
    }

    private static Map<String, RpcSignature> parseRpcMethods(String serviceBody) {
        Map<String, RpcSignature> methods = new LinkedHashMap<>();
        Pattern rpcPattern = Pattern.compile(
                "rpc\\s+(\\w+)\\s*\\(\\s*(?:stream\\s+)?([\\w.]+)\\s*\\)\\s*returns\\s*\\(\\s*(?:stream\\s+)?([\\w.]+)\\s*\\)",
                Pattern.MULTILINE
        );
        Matcher rpcMatcher = rpcPattern.matcher(serviceBody);
        while (rpcMatcher.find()) {
            String name = rpcMatcher.group(1);
            String request = rpcMatcher.group(2);
            String response = rpcMatcher.group(3);
            methods.put(name, new RpcSignature(request, response));
        }
        return methods;
    }

    private static Map<Integer, String> parseMessageFields(String messageBody) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        Pattern fieldPattern = Pattern.compile(
                "^\\s*(repeated\\s+)?(map<[^>]+>|[\\w.]+)\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;",
                Pattern.MULTILINE
        );
        Matcher fieldMatcher = fieldPattern.matcher(messageBody);
        while (fieldMatcher.find()) {
            boolean repeated = fieldMatcher.group(1) != null;
            String type = fieldMatcher.group(2);
            String name = fieldMatcher.group(3);
            int index = Integer.parseInt(fieldMatcher.group(4));
            String signature = (repeated ? "repeated " : "") + type + " " + name;
            fields.put(index, signature);
        }
        return fields;
    }

    private record RpcSignature(String requestType, String responseType) {
    }

    private record ProtoSchema(
            Map<String, Map<String, RpcSignature>> services,
            Map<String, Map<Integer, String>> messages
    ) {
    }
}
