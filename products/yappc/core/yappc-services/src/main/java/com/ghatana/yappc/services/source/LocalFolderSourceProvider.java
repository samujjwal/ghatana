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
 * @doc.purpose Trusted runtime local-folder source provider with configurable allowed roots, path safety checks, content SHA-256, and deterministic snapshots
 * @doc.layer service
 * @doc.pattern Strategy
 * 
 * P0: Added configurable allowed roots instead of hardcoded user.dir for security.
 * P0: Added content SHA-256 checksums for actual file content instead of path+size.
 * P0: Added deterministic snapshotId based on content hash.
 * P0: Integrated RepositoryInventoryScanner for .gitignore filtering and file classification.
 */
public final class LocalFolderSourceProvider implements SourceProvider {

    private final RepositoryInventoryScanner scanner;
    private final java.util.Set<Path> allowedRoots;

    public LocalFolderSourceProvider() {
        this(new RepositoryInventoryScanner(), java.util.Set.of(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()));
    }

    public LocalFolderSourceProvider(RepositoryInventoryScanner scanner) {
        this(scanner, java.util.Set.of(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()));
    }

    public LocalFolderSourceProvider(RepositoryInventoryScanner scanner, java.util.Set<Path> allowedRoots) {
        this.scanner = scanner;
        this.allowedRoots = allowedRoots.stream()
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

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
            Path candidate = normalizeCandidatePath(locator.repoId());
            
            // P0: Verify candidate path is within one of the allowed roots
            boolean isAllowed = allowedRoots.stream()
                .anyMatch(root -> candidate.startsWith(root));
            
            if (!isAllowed) {
                return Promise.ofException(new IllegalArgumentException(
                    "Local folder source must be inside one of the allowed roots: " + allowedRoots
                ));
            }
            
            if (!Files.exists(candidate) || !Files.isDirectory(candidate)) {
                return Promise.ofException(new IllegalArgumentException(
                    "Local folder source path does not exist or is not a directory"
                ));
            }

            // P0: Use RepositoryInventoryScanner for .gitignore filtering and file classification
            java.util.Set<String> gitignorePatterns = scanner.parseGitignore(candidate);
            RepositoryInventoryScanner.InventoryResult inventory = scanner.scanRepository(candidate, gitignorePatterns);

            List<RepositorySnapshot.SnapshotFile> files = inventory.files().stream()
                .map(entry -> new RepositorySnapshot.SnapshotFile(
                    entry.relativePath(),
                    candidate.resolve(entry.relativePath()).toString(),
                    entry.sizeBytes(),
                    Instant.now(),
                    entry.checksum()
                ))
                .toList();

            String checksum = computeDirectoryChecksum(candidate, files);
            // P0: Compute deterministic snapshotId from content hash
            String deterministicSnapshotId = computeDeterministicSnapshotId(candidate.toString(), checksum);
            
            RepositorySnapshot snapshot = RepositorySnapshot.builder()
                .snapshotId(deterministicSnapshotId)
                .provider(providerId())
                .repoId(candidate.toString())
                .contentHash(checksum)
                .materializedRoot(candidate.toString())
                .checksum(checksum)
                .files(files)
                .diagnostics(List.of(new RepositorySnapshot.SnapshotDiagnostic(
                    RepositorySnapshot.DiagnosticLevel.INFO,
                    "LOCAL_FOLDER_RESOLVED",
                    "Local folder snapshot resolved with deterministic snapshotId: " + deterministicSnapshotId + ", files: " + files.size(),
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
            "supportsDeterministicSnapshot", true,
            "supportsConfigurableAllowedRoots", true
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
            // P0: Compute SHA-256 from actual file content instead of path+size
            String checksum = computeFileContentChecksum(file);
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

    /**
     * P0: Compute SHA-256 checksum from actual file content.
     */
    private static String computeFileContentChecksum(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(file);
            digest.update(content);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute file content checksum", e);
        }
    }

    private static String computeDirectoryChecksum(Path root, List<RepositorySnapshot.SnapshotFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RepositorySnapshot.SnapshotFile file : files) {
                // P0: Use file content checksum instead of just path+size
                digest.update(file.relativePath().getBytes());
                digest.update(file.contentChecksum().getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * P0: Compute deterministic snapshotId from path and content hash.
     */
    private static String computeDeterministicSnapshotId(String path, String contentHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(path.getBytes());
            digest.update(contentHash.getBytes());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
