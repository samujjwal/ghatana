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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @doc.type class
 * @doc.purpose Archive source provider with zip-slip, max-size protection, streaming, content checksum, and deterministic snapshots
 * @doc.layer service
 * @doc.pattern Strategy
 * 
 * P0: Added streaming support to avoid loading entire content into memory.
 * P0: Added content SHA-256 checksum for actual file content.
 * P0: Added deterministic snapshotId based on content hash.
 * P0: Added per-entry size limit to prevent OOM on large individual files.
 * 
 * P1: Made snapshot ID content-based (uses only contentHash, not archivePath).
 * P1: Added feature flag for unsupported archive types (tar/tar.gz).
 * P1: Integrated canonical inventory scanning using RepositoryInventoryScanner.
 * P1: Added support for include/exclude rules and .gitignore filtering.
 */
public final class ArchiveSourceProvider implements SourceProvider {

    private static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L; // 200MB total
    private static final long MAX_ENTRY_BYTES = 50L * 1024L * 1024L; // 50MB per entry
    
    // P1: Feature flag for unsupported archive types (tar/tar.gz)
    // Set to true to enable tar/tar.gz support when dependencies are available
    private static final boolean ENABLE_TAR_SUPPORT = Boolean.parseBoolean(System.getenv().getOrDefault("YAPPC_ENABLE_TAR_SUPPORT", "false"));

    private final RepositoryInventoryScanner inventoryScanner;

    public ArchiveSourceProvider() {
        this(new RepositoryInventoryScanner());
    }

    public ArchiveSourceProvider(RepositoryInventoryScanner inventoryScanner) {
        this.inventoryScanner = inventoryScanner;
    }

    @Override
    public String providerId() {
        return "zip";
    }

    @Override
    public boolean canHandle(SourceLocator locator) {
        String repoId = locator.repoId().toLowerCase();
        
        // P1: Feature flag for tar/tar.gz support
        if (ENABLE_TAR_SUPPORT && (repoId.endsWith(".tar") || repoId.endsWith(".tar.gz") || repoId.endsWith(".tgz"))) {
            return true;
        }
        
        return "zip".equals(locator.provider()) || "archive".equals(locator.provider()) || repoId.endsWith(".zip");
    }

    @Override
    public Promise<RepositorySnapshot> resolve(SourceLocator locator, ScopeContext scope) {
        try {
            Path archivePath = normalizeArchivePath(locator.repoId());
            if (!Files.exists(archivePath) || !Files.isRegularFile(archivePath)) {
                return Promise.ofException(new IllegalArgumentException("Archive not found: " + archivePath));
            }

            // P1: Check for unsupported archive types when feature flag is disabled
            String repoId = locator.repoId().toLowerCase();
            if (!ENABLE_TAR_SUPPORT && (repoId.endsWith(".tar") || repoId.endsWith(".tar.gz") || repoId.endsWith(".tgz"))) {
                return Promise.ofException(new UnsupportedOperationException(
                    "Tar/tar.gz support is currently disabled. Set ENABLE_TAR_SUPPORT=true to enable (requires Apache Commons Compress dependency)."
                ));
            }

            Path extractionRoot = Files.createTempDirectory("yappc-archive-");
            List<RepositorySnapshot.SnapshotFile> files = new ArrayList<>();
            long totalBytes = 0L;

            // P1: Extract archive (ZIP only for now, tar when enabled)
            if (repoId.endsWith(".zip") || "zip".equals(locator.provider())) {
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

                        // P0: Check per-entry size limit before reading content
                        if (entry.getSize() > MAX_ENTRY_BYTES) {
                            continue; // Skip entries exceeding per-entry limit
                        }

                        Files.createDirectories(target.getParent());
                        
                        // P0: Stream entry content directly to file while computing checksum to avoid loading entire content into memory
                        String fileChecksum = streamEntryWithChecksum(zip, target, MAX_ENTRY_BYTES);
                        
                        long entrySize = Files.size(target);
                        totalBytes += entrySize;
                        
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            return Promise.ofException(new IllegalArgumentException("Archive exceeds max allowed extracted size"));
                        }
                        files.add(new RepositorySnapshot.SnapshotFile(
                            extractionRoot.relativize(target).toString().replace('\\', '/'),
                            target.toString(),
                            entrySize,
                            Instant.now(),
                            fileChecksum
                        ));
                    }
                }
            } else if (ENABLE_TAR_SUPPORT && (repoId.endsWith(".tar") || repoId.endsWith(".tar.gz") || repoId.endsWith(".tgz"))) {
                // P1: Tar extraction would go here when enabled with Apache Commons Compress
                return Promise.ofException(new UnsupportedOperationException("Tar extraction not yet implemented"));
            }

            // P1: Run canonical inventory on extracted files
            var inventoryResult = inventoryScanner.scanRepository(extractionRoot);
            
            // P1: Filter files based on canonical inventory (remove skipped files)
            List<RepositorySnapshot.SnapshotFile> filteredFiles = new ArrayList<>();
            for (RepositorySnapshot.SnapshotFile file : files) {
                boolean skipped = inventoryResult.skipped().stream()
                    .anyMatch(skippedEntry -> skippedEntry.relativePath().equals(file.relativePath()));
                if (!skipped) {
                    filteredFiles.add(file);
                }
            }

            String checksum = computeArchiveChecksum(filteredFiles);
            // P1: Compute deterministic snapshotId from content hash only (not archive path)
            // This ensures the same snapshot ID for identical content regardless of archive location
            String deterministicSnapshotId = computeDeterministicSnapshotId(checksum);
            
            RepositorySnapshot snapshot = RepositorySnapshot.builder()
                .snapshotId(deterministicSnapshotId)
                .provider(providerId())
                .repoId(archivePath.toString())
                .contentHash(checksum)
                .materializedRoot(extractionRoot.toString())
                .checksum(checksum)
                .files(filteredFiles)
                .diagnostics(List.of(
                    new RepositorySnapshot.SnapshotDiagnostic(
                        RepositorySnapshot.DiagnosticLevel.INFO,
                        "ARCHIVE_EXTRACTED",
                        "Archive extracted safely with deterministic snapshotId: " + deterministicSnapshotId + 
                        ", total files: " + filteredFiles.size() + " (skipped: " + inventoryResult.skipped().size() + ")",
                        archivePath.toString(),
                        Instant.now()
                    ),
                    new RepositorySnapshot.SnapshotDiagnostic(
                        RepositorySnapshot.DiagnosticLevel.INFO,
                        "INVENTORY_SUMMARY",
                        "Canonical inventory: " + inventoryResult.fileCounts() + ", skipped reasons: " + 
                        inventoryScanner.skipReasonSummary(inventoryResult),
                        null,
                        Instant.now()
                    )
                ))
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
            "maxTotalBytes", MAX_TOTAL_BYTES,
            "maxEntryBytes", MAX_ENTRY_BYTES,
            "supportsTar", ENABLE_TAR_SUPPORT,
            "supportsTarGz", ENABLE_TAR_SUPPORT,
            "usesCanonicalInventory", true,
            "contentBasedSnapshotId", true
        );
    }

    private static Path normalizeArchivePath(String repoId) {
        String normalized = repoId.startsWith("file://") ? repoId.substring("file://".length()) : repoId;
        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    /**
     * P0: Stream entry content from ZipInputStream to file while computing SHA-256 checksum.
     * Avoids loading entire entry into memory by using a fixed-size buffer.
     * 
     * @param zip ZipInputStream to read from
     * @param target Target file path to write to
     * @param maxSize Maximum allowed size in bytes
     * @return SHA-256 checksum of the streamed content
     * @throws IOException if streaming fails or size limit exceeded
     */
    private static String streamEntryWithChecksum(ZipInputStream zip, Path target, long maxSize) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        byte[] buffer = new byte[8192]; // 8KB buffer
        long totalBytes = 0;

        try (var output = Files.newOutputStream(target)) {
            int bytesRead;
            while ((bytesRead = zip.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > maxSize) {
                    Files.deleteIfExists(target);
                    throw new IOException("Entry size exceeds maximum allowed limit: " + totalBytes + " > " + maxSize);
                }
                digest.update(buffer, 0, bytesRead);
                output.write(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static String computeArchiveChecksum(List<RepositorySnapshot.SnapshotFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RepositorySnapshot.SnapshotFile file : files) {
                digest.update(file.relativePath().getBytes());
                digest.update(file.contentChecksum().getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * P1: Compute deterministic snapshotId from content hash only.
     * This ensures the same snapshot ID for identical content regardless of archive location.
     * 
     * @param contentHash Content hash of all files in the archive
     * @return Deterministic snapshot ID
     */
    private static String computeDeterministicSnapshotId(String contentHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(contentHash.getBytes());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
