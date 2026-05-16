package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @doc.type class
 * @doc.purpose Trusted runtime local-folder source provider with path safety checks
 * @doc.layer service
 * @doc.pattern Strategy
 */
public final class LocalFolderSourceProvider implements SourceProvider {

    @Override
    public String providerId() {
        return "local-folder";
    }

    @Override
    public boolean canHandle(SourceLocator locator) {
        return "local-folder".equals(locator.provider())
            || locator.repoId().startsWith("file://")
            || locator.repoId().startsWith("/");
    }

    @Override
    public Promise<RepositorySnapshot> resolve(SourceLocator locator, ScopeContext scope) {
        try {
            Path workspaceRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path candidate = normalizeCandidatePath(locator.repoId());
            if (!candidate.startsWith(workspaceRoot)) {
                return Promise.ofException(new IllegalArgumentException(
                    "Local folder source must be inside workspace root"
                ));
            }
            if (!Files.exists(candidate) || !Files.isDirectory(candidate)) {
                return Promise.ofException(new IllegalArgumentException(
                    "Local folder source path does not exist or is not a directory"
                ));
            }

            List<RepositorySnapshot.SnapshotFile> files;
            try (Stream<Path> stream = Files.walk(candidate)) {
                files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> toSnapshotFile(candidate, path))
                    .toList();
            }

            String checksum = computeDirectoryChecksum(candidate, files);
            RepositorySnapshot snapshot = RepositorySnapshot.builder()
                .snapshotId(UUID.randomUUID().toString())
                .provider(providerId())
                .repoId(candidate.toString())
                .contentHash(checksum)
                .materializedRoot(candidate.toString())
                .checksum(checksum)
                .files(files)
                .diagnostics(List.of(new RepositorySnapshot.SnapshotDiagnostic(
                    RepositorySnapshot.DiagnosticLevel.INFO,
                    "LOCAL_FOLDER_RESOLVED",
                    "Local folder snapshot resolved",
                    candidate.toString(),
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
            "supportsCommitPinning", false,
            "supportsPathTraversalProtection", true,
            "supportsDeterministicSnapshot", true
        );
    }

    private static Path normalizeCandidatePath(String repoId) {
        String normalized = repoId.startsWith("file://") ? repoId.substring("file://".length()) : repoId;
        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    private static RepositorySnapshot.SnapshotFile toSnapshotFile(Path root, Path file) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(file);
            long size = Files.size(file);
            // Compute simple checksum from path and size
            String checksum = computeFileChecksum(file, size);
            return new RepositorySnapshot.SnapshotFile(
                root.relativize(file).toString().replace('\\', '/'),
                file.toString(),
                size,
                lastModified.toInstant(),
                checksum
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read local file metadata", e);
        }
    }

    private static String computeFileChecksum(Path file, long size) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.toString().getBytes());
            digest.update(Long.toString(size).getBytes());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return "sha256-unavailable";
        }
    }

    private static String computeDirectoryChecksum(Path root, List<RepositorySnapshot.SnapshotFile> files) {
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
