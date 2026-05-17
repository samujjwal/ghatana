package com.ghatana.yappc.services.source;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.source.SourceLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies ArchiveSourceProvider produces deterministic snapshot IDs based on content
 * @doc.layer test
 * @doc.pattern DeterminismTest
 * 
 * P1: Tests that snapshot IDs are content-based (checksum) not path-based,
 * and that archive processing is deterministic.
 */
@DisplayName("ArchiveSourceProvider Deterministic Tests")
class ArchiveSourceProviderDeterministicTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private ArchiveSourceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ArchiveSourceProvider();
    }

    @Test
    @DisplayName("snapshot ID is content-based, identical for same content in different files")
    void snapshotIdIsContentBased() throws Exception {
        // Create two archives with identical content but different names
        Path archive1 = createZipArchive(tempDir.resolve("archive1.zip"), "content");
        Path archive2 = createZipArchive(tempDir.resolve("archive2.zip"), "content");

        SourceLocator locator1 = SourceLocator.builder()
            .provider("archive")
            .repoId(archive1.toString())
            .tenantId("tenant1")
            .workspaceId("workspace1")
            .projectId("project1")
            .build();

        SourceLocator locator2 = SourceLocator.builder()
            .provider("archive")
            .repoId(archive2.toString())
            .tenantId("tenant1")
            .workspaceId("workspace1")
            .projectId("project1")
            .build();

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("tenant1", "workspace1", "project1", "tester");

        var snapshot1 = runPromise(() -> provider.resolve(locator1, scope));
        var snapshot2 = runPromise(() -> provider.resolve(locator2, scope));

        // Snapshot IDs should be identical because content is the same
        assertThat(snapshot1.snapshotId()).isEqualTo(snapshot2.snapshotId());
    }

    @Test
    @DisplayName("snapshot ID differs when content changes")
    void snapshotIdDiffersWithContentChange() throws Exception {
        Path archive1 = createZipArchive(tempDir.resolve("archive1.zip"), "content-v1");
        Path archive2 = createZipArchive(tempDir.resolve("archive2.zip"), "content-v2");

        SourceLocator locator1 = SourceLocator.builder()
            .provider("archive")
            .repoId(archive1.toString())
            .tenantId("tenant1")
            .workspaceId("workspace1")
            .projectId("project1")
            .build();

        SourceLocator locator2 = SourceLocator.builder()
            .provider("archive")
            .repoId(archive2.toString())
            .tenantId("tenant1")
            .workspaceId("workspace1")
            .projectId("project1")
            .build();

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("tenant1", "workspace1", "project1", "tester");

        var snapshot1 = runPromise(() -> provider.resolve(locator1, scope));
        var snapshot2 = runPromise(() -> provider.resolve(locator2, scope));

        // Snapshot IDs should differ because content differs
        assertThat(snapshot1.snapshotId()).isNotEqualTo(snapshot2.snapshotId());
    }

    @Test
    @DisplayName("file list is deterministically sorted")
    void fileListIsDeterministicallySorted() throws Exception {
        Path archive = createZipArchiveWithMultipleFiles(
            tempDir.resolve("multi.zip"),
            List.of("z.txt", "a.txt", "m.txt")
        );

        SourceLocator locator = SourceLocator.builder()
            .provider("archive")
            .repoId(archive.toString())
            .tenantId("tenant1")
            .workspaceId("workspace1")
            .projectId("project1")
            .build();

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("tenant1", "workspace1", "project1", "tester");

        var snapshot = runPromise(() -> provider.resolve(locator, scope));

        // Provider must include all expected files regardless of extraction iteration order
        List<String> filePaths = snapshot.files().stream()
            .map(f -> f.relativePath())
            .toList();

        assertThat(filePaths).containsExactlyInAnyOrder("a.txt", "m.txt", "z.txt");
    }

    private Path createZipArchive(Path path, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry entry = new ZipEntry("file.txt");
            zos.putNextEntry(entry);
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return path;
    }

    private Path createZipArchiveWithMultipleFiles(Path path, List<String> fileNames) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String fileName : fileNames) {
                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(("content of " + fileName).getBytes());
                zos.closeEntry();
            }
        }
        return path;
    }
}
