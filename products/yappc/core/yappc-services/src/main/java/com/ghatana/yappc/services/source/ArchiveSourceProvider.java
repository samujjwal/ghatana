package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @doc.type class
 * @doc.purpose Archive source provider with zip-slip and max-size protection
 * @doc.layer service
 * @doc.pattern Strategy
 */
public final class ArchiveSourceProvider implements SourceProvider {

    private static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L;

    @Override
    public String providerId() {
        return "zip";
    }

    @Override
    public boolean canHandle(SourceLocator locator) {
        String repoId = locator.repoId().toLowerCase();
        return "zip".equals(locator.provider()) || "archive".equals(locator.provider()) || repoId.endsWith(".zip");
    }

    @Override
    public Promise<RepositorySnapshot> resolve(SourceLocator locator, ScopeContext scope) {
        try {
            Path archivePath = normalizeArchivePath(locator.repoId());
            if (!Files.exists(archivePath) || !Files.isRegularFile(archivePath)) {
                return Promise.ofException(new IllegalArgumentException("Archive not found: " + archivePath));
            }

            Path extractionRoot = Files.createTempDirectory("yappc-archive-");
            List<RepositorySnapshot.SnapshotFile> files = new ArrayList<>();
            long totalBytes = 0L;

            try (InputStream input = Files.newInputStream(archivePath);
                 ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    Path target = extractionRoot.resolve(entry.getName()).normalize();
                    if (!target.startsWith(extractionRoot)) {
                        return Promise.ofException(new IllegalArgumentException("Zip-slip detected in archive entry: " + entry.getName()));
                    }

                    Files.createDirectories(target.getParent());
                    byte[] content = zip.readAllBytes();
                    totalBytes += content.length;
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        return Promise.ofException(new IllegalArgumentException("Archive exceeds max allowed extracted size"));
                    }

                    Files.write(target, content);
                    String fileChecksum = computeContentChecksum(content);
                    files.add(new RepositorySnapshot.SnapshotFile(
                        extractionRoot.relativize(target).toString().replace('\\', '/'),
                        target.toString(),
                        content.length,
                        Instant.now(),
                        fileChecksum
                    ));
                }
            }

            String checksum = computeArchiveChecksum(files);
            RepositorySnapshot snapshot = RepositorySnapshot.builder()
                .snapshotId(UUID.randomUUID().toString())
                .provider(providerId())
                .repoId(archivePath.toString())
                .contentHash(checksum)
                .materializedRoot(extractionRoot.toString())
                .checksum(checksum)
                .files(files)
                .diagnostics(List.of(new RepositorySnapshot.SnapshotDiagnostic(
                    RepositorySnapshot.DiagnosticLevel.INFO,
                    "ARCHIVE_EXTRACTED",
                    "Archive extracted safely",
                    archivePath.toString(),
                    Instant.now()
                )))
                .tenantId(scope.tenantId())
                .workspaceId(scope.workspaceId())
                .projectId(scope.projectId())
                .build();
            return Promise.of(snapshot);
        } catch (IOException e) {
            return Promise.ofException(e);
        }
    }

    @Override
    public Map<String, Object> capabilities() {
        return Map.of(
            "supportsZipSlipProtection", true,
            "supportsMaxArchiveSize", true,
            "maxTotalBytes", MAX_TOTAL_BYTES
        );
    }

    private static Path normalizeArchivePath(String repoId) {
        String normalized = repoId.startsWith("file://") ? repoId.substring("file://".length()) : repoId;
        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    private static String computeContentChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(content);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return "sha256-unavailable";
        }
    }

    private static String computeArchiveChecksum(List<RepositorySnapshot.SnapshotFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RepositorySnapshot.SnapshotFile file : files) {
                digest.update(file.relativePath().getBytes());
                digest.update(Long.toString(file.sizeBytes()).getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
