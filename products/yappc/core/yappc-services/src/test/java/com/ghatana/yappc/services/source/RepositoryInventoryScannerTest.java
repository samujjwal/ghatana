package com.ghatana.yappc.services.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies RepositoryInventoryScanner sorted walk, skip reasons, and package boundary detection
 * @doc.layer test
 * @doc.pattern UnitTest
 */
@DisplayName("RepositoryInventoryScanner Tests")
class RepositoryInventoryScannerTest {

    private final RepositoryInventoryScanner scanner = new RepositoryInventoryScanner();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("returns files in deterministic sorted order")
    void returnsSortedFiles(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("z_last.ts"), "export const z = 1;");
        Files.writeString(root.resolve("a_first.ts"), "export const a = 1;");
        Files.writeString(root.resolve("m_middle.ts"), "export const m = 1;");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root);

        List<String> paths = result.files().stream()
            .map(RepositoryInventoryScanner.InventoryEntry::relativePath)
            .toList();
        assertThat(paths).containsExactly("a_first.ts", "m_middle.ts", "z_last.ts");
    }

    @Test
    @DisplayName("skips node_modules with VENDOR_DIRECTORY reason")
    void skipsNodeModules(@TempDir Path root) throws Exception {
        Path nm = root.resolve("node_modules/lodash/index.js");
        Files.createDirectories(nm.getParent());
        Files.writeString(nm, "module.exports = {}");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/app.ts"), "export const app = 1;");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root);

        assertThat(result.files()).hasSize(1);
        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.VENDOR_DIRECTORY &&
            s.relativePath().contains("node_modules"));
    }

    @Test
    @DisplayName("skips binary files with BINARY_FILE reason")
    void skipsBinaryFiles(@TempDir Path root) throws Exception {
        Files.write(root.resolve("logo.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/app.ts"), "export const app = 1;");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root);

        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.BINARY_FILE &&
            s.relativePath().endsWith(".png"));
        assertThat(result.files()).hasSize(1);
    }

    @Test
    @DisplayName("skips generated directories")
    void skipsGeneratedDirectories(@TempDir Path root) throws Exception {
        Path dist = root.resolve("dist/bundle.js");
        Files.createDirectories(dist.getParent());
        Files.writeString(dist, "/* generated */");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/index.ts"), "export {};");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root);

        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.GENERATED_FILE);
        assertThat(result.files()).hasSize(1);
    }

    @Test
    @DisplayName("detects package boundaries from package.json presence")
    void detectsPackageBoundaries(@TempDir Path root) throws Exception {
        Path subPkg = root.resolve("packages/lib-a");
        Files.createDirectories(subPkg);
        Files.writeString(subPkg.resolve("package.json"), "{\"name\": \"lib-a\"}");
        Files.writeString(subPkg.resolve("index.ts"), "export {};");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.ts"), "export {};");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root);

        assertThat(result.packageBoundaries()).contains("packages/lib-a");
    }

    @Test
    @DisplayName("skips files matched by gitignore pattern")
    void skipsGitignoreMatched(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("secret.env"), "DB_PASSWORD=test");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/app.ts"), "export {};");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root, Set.of("*.env"));

        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.GITIGNORE &&
            s.relativePath().endsWith(".env"));
        assertThat(result.files()).hasSize(1);
    }

    @Test
    @DisplayName("skipReasonSummary groups skip entries by reason")
    void skipReasonSummaryGroupsByReason(@TempDir Path root) throws Exception {
        Path nm = root.resolve("node_modules/pkg/index.js");
        Files.createDirectories(nm.getParent());
        Files.writeString(nm, "{}");
        Files.write(root.resolve("icon.png"), new byte[]{0});
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/app.ts"), "export {};");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(root);
        Map<RepositoryInventoryScanner.SkipReason, Long> summary = scanner.skipReasonSummary(result);

        assertThat(summary.getOrDefault(RepositoryInventoryScanner.SkipReason.VENDOR_DIRECTORY, 0L)).isGreaterThanOrEqualTo(1L);
        assertThat(summary.getOrDefault(RepositoryInventoryScanner.SkipReason.BINARY_FILE, 0L)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("exclude pattern skip reason is EXCLUDE_PATTERN with matched pattern")
    void excludePatternUsesExplicitSkipReason(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/keep.ts"), "export const keep = true;");
        Files.writeString(root.resolve("src/skip.tmp.ts"), "export const skip = true;");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(
            root,
            Set.of(),
            Set.of(),
            Set.of("*.tmp.ts")
        );

        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.EXCLUDE_PATTERN
                && "*.tmp.ts".equals(s.matchedPattern())
                && s.relativePath().endsWith("skip.tmp.ts")
        );
        assertThat(result.files()).anyMatch(f -> f.relativePath().endsWith("keep.ts"));
    }

    @Test
    @DisplayName("include patterns do not override safety filters")
    void includePatternsDoNotOverrideSafetyFilters(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.write(root.resolve("src/logo.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.createDirectories(root.resolve("generated"));
        Files.writeString(root.resolve("generated/forced.ts"), "export const forced = true;");

        RepositoryInventoryScanner.InventoryResult result = scanner.scanRepository(
            root,
            Set.of(),
            Set.of("**/*.png", "generated/**"),
            Set.of()
        );

        assertThat(result.files()).isEmpty();
        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.BINARY_FILE
                && s.relativePath().endsWith("logo.png")
        );
        assertThat(result.skipped()).anyMatch(s ->
            s.reason() == RepositoryInventoryScanner.SkipReason.GENERATED_FILE
                && s.relativePath().endsWith("forced.ts")
        );
    }
}
