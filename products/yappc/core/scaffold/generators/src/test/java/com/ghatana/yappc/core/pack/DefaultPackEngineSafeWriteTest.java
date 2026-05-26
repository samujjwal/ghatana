package com.ghatana.yappc.core.pack;

import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies safe and idempotent scaffold pack file writes
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("DefaultPackEngine safe writes")
class DefaultPackEngineSafeWriteTest {

    @Test
    @DisplayName("skips identical replacement content on repeated generation")
    void skipsIdenticalReplacementContentOnRepeat(@TempDir Path tempDir) throws Exception {
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine());
        Pack pack = pack("hello", PackMetadata.TemplateFile.MergeStrategy.REPLACE, "Hello {{name}}");
        Path output = tempDir.resolve("app");

        PackEngine.GenerationResult first = engine.generateFromPack(pack, output, Map.of("name", "YAPPC"));
        PackEngine.GenerationResult second = engine.generateFromPack(pack, output, Map.of("name", "YAPPC"));

        assertThat(first.successful()).isTrue();
        assertThat(second.successful()).isTrue();
        assertThat(Files.readString(output.resolve("src/hello.txt"))).isEqualTo("Hello YAPPC");
        assertThat(second.filesGenerated()).isZero();
        assertThat(metadataList(second, "unchangedFiles")).containsExactly("src/hello.txt");
        assertThat(output.resolve(".yappc/backups")).doesNotExist();
    }

    @Test
    @DisplayName("backs up changed files before replacing them")
    void backsUpChangedFilesBeforeReplacing(@TempDir Path tempDir) throws Exception {
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine());
        Pack pack = pack("hello", PackMetadata.TemplateFile.MergeStrategy.REPLACE, "Hello {{name}}");
        Path output = tempDir.resolve("app");
        Files.createDirectories(output.resolve("src"));
        Files.writeString(output.resolve("src/hello.txt"), "local edit");

        PackEngine.GenerationResult result = engine.generateFromPack(pack, output, Map.of("name", "YAPPC"));

        assertThat(result.successful()).isTrue();
        assertThat(Files.readString(output.resolve("src/hello.txt"))).isEqualTo("Hello YAPPC");
        assertThat(metadataList(result, "updatedFiles")).containsExactly("src/hello.txt");
        assertThat(metadataList(result, "backupFiles"))
                .singleElement()
                .satisfies(backup -> assertThat(Files.readString(output.resolve(backup))).isEqualTo("local edit"));
    }

    @Test
    @DisplayName("does not duplicate appended generated content on repeated generation")
    void doesNotDuplicateAppendContent(@TempDir Path tempDir) throws Exception {
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine());
        Pack pack = pack("env", PackMetadata.TemplateFile.MergeStrategy.APPEND, "GENERATED=true");
        Path output = tempDir.resolve("app");

        PackEngine.GenerationResult first = engine.generateFromPack(pack, output, Map.of());
        PackEngine.GenerationResult second = engine.generateFromPack(pack, output, Map.of());

        assertThat(first.successful()).isTrue();
        assertThat(second.successful()).isTrue();
        assertThat(Files.readString(output.resolve("src/env.txt"))).isEqualTo("GENERATED=true");
        assertThat(second.filesGenerated()).isZero();
        assertThat(metadataList(second, "unchangedFiles")).containsExactly("src/env.txt");
    }

    @Test
    @DisplayName("rejects template targets outside the output directory")
    void rejectsTargetsOutsideOutputDirectory(@TempDir Path tempDir) throws Exception {
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine());
        Pack pack = packWithTarget("../outside.txt", "escape");
        Path output = tempDir.resolve("app");

        PackEngine.GenerationResult result = engine.generateFromPack(pack, output, Map.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("Template target escapes output directory"));
        assertThat(tempDir.resolve("outside.txt")).doesNotExist();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<String> metadataList(PackEngine.GenerationResult result, String key) {
        return (java.util.List<String>) result.metadata().get(key);
    }

    private static Pack pack(
            String name,
            PackMetadata.TemplateFile.MergeStrategy mergeStrategy,
            String templateContent) {
        return packWithTarget("src/" + name + ".txt", templateContent, mergeStrategy);
    }

    private static Pack packWithTarget(String target, String templateContent) {
        return packWithTarget(target, templateContent, PackMetadata.TemplateFile.MergeStrategy.REPLACE);
    }

    private static Pack packWithTarget(
            String target,
            String templateContent,
            PackMetadata.TemplateFile.MergeStrategy mergeStrategy) {
        PackMetadata.TemplateFile templateFile = new PackMetadata.TemplateFile(
                "template.txt",
                target,
                null,
                false,
                mergeStrategy);
        PackMetadata metadata = new PackMetadata(
                "safe-write-pack",
                "1.0.0",
                "Safe write test pack",
                "Ghatana",
                "Apache-2.0",
                java.util.List.of("test"),
                PackMetadata.PackType.APPLICATION,
                "typescript",
                "react",
                "pnpm",
                "web",
                null,
                "test",
                null,
                null,
                Map.of("template", templateFile),
                null,
                null,
                null,
                null,
                null,
                null);
        return new Pack(metadata, Path.of("test-pack"), Map.of("template", templateContent));
    }
}
