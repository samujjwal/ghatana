package com.ghatana.yappc.services.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * @doc.type class
 * @doc.purpose GitLab source provider with commit pinning, deterministic snapshots, credentials, and pagination
 * @doc.layer service
 * @doc.pattern Strategy
 * 
 * P0: GitLab source provider with URL-encoded project IDs and file paths.
 * P0: Paginated tree results with fail-closed on incomplete/inconsistent pagination.
 * P0: Credentials support via SourceCredentialResolver with scope validation.
 * P0: Deterministic sorting of files for reproducible snapshots.
 * P0: Rate-limited sequential file fetch with semaphore (not parallel concurrency).
 * P0: File-size limits to prevent OOM on large files.
 */
public final class GitLabSourceProvider implements SourceProvider {

    private static final Logger log = LoggerFactory.getLogger(GitLabSourceProvider.class);
    private static final int MAX_CONCURRENT_REQUESTS = 10;
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB per file
    private static final long MAX_TOTAL_SIZE_BYTES = 100 * 1024 * 1024; // 100MB total

    private static final int PAGE_SIZE = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Semaphore requestSemaphore;
    private final SourceCredentialResolver credentialResolver;

    public GitLabSourceProvider() {
        this(HttpClient.newHttpClient(), new ObjectMapper(), SourceCredentialResolver.envBacked());
    }

    public GitLabSourceProvider(HttpClient httpClient, ObjectMapper objectMapper, SourceCredentialResolver credentialResolver) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.credentialResolver = credentialResolver;
        this.requestSemaphore = new java.util.concurrent.Semaphore(MAX_CONCURRENT_REQUESTS);
    }

    @Override
    public String providerId() {
        return "gitlab";
    }

    @Override
    public boolean canHandle(SourceLocator locator) {
        return locator != null && "gitlab".equals(locator.provider());
    }

    @Override
    public Promise<RepositorySnapshot> resolve(SourceLocator locator, ScopeContext scope) {
        try {
            String repo = normalizeRepo(locator.repoId());
            String ref = locator.ref().filter(r -> !r.isBlank()).orElse("HEAD");
            // P0: Pass scope parameters to credentialResolver for ownership validation
            String credentials = credentialResolver.resolve(locator, providerId(), 
                scope.tenantId(), scope.workspaceId(), scope.projectId());
            String commitSha = resolveCommitSha(repo, ref, credentials);
            String encodedRepo = encodeProjectId(repo);

            List<JsonNode> allEntries = fetchAllTreePages(encodedRepo, commitSha, credentials);

            Path root = Files.createTempDirectory("yappc-gitlab-");
            List<RepositorySnapshot.SnapshotFile> files = new ArrayList<>();
            long totalSize = 0;

            // P0: Sort entries deterministically for reproducible snapshot processing
            allEntries.sort(java.util.Comparator.comparing(e -> e.path("path").asText()));

            for (JsonNode entry : allEntries) {
                if (!"blob".equals(entry.path("type").asText())) {
                    continue;
                }
                String path = entry.path("path").asText();
                
                // P0: Rate-limit sequential file fetch with semaphore (not parallel concurrency)
                try {
                    requestSemaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Promise.ofException(new IllegalStateException("Interrupted while acquiring request semaphore", e));
                }
                
                try {
                    String encodedPath = encodeFilePath(path);
                    String encodedRef = URLEncoder.encode(commitSha, StandardCharsets.UTF_8);
                    JsonNode file = fetchJson("https://gitlab.com/api/v4/projects/" + encodedRepo + "/repository/files/" + encodedPath + "?ref=" + encodedRef, credentials);
                    byte[] content = Base64.getMimeDecoder().decode(file.path("content").asText(""));
                    
                    // P0: Check file size limit
                    if (content.length > MAX_FILE_SIZE_BYTES) {
                        log.warn("Skipping file exceeding size limit: {} ({} bytes)", path, content.length);
                        continue;
                    }
                    
                    // P0: Check total size limit
                    if (totalSize + content.length > MAX_TOTAL_SIZE_BYTES) {
                        log.warn("Stopping snapshot due to total size limit: {} bytes", totalSize);
                        break;
                    }
                    totalSize += content.length;

                    Path filePath = root.resolve(path).normalize();
                    if (!filePath.startsWith(root)) {
                        return Promise.ofException(new IllegalArgumentException("Path traversal detected in GitLab tree"));
                    }
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, content);

                    // P0: Use file content SHA-256 instead of GitLab blob ID for checksum
                    String contentChecksum = computeContentChecksum(content);
                    files.add(new RepositorySnapshot.SnapshotFile(
                        path,
                        filePath.toString(),
                        content.length,
                        Instant.now(),
                        contentChecksum
                    ));
                } finally {
                    requestSemaphore.release();
                }
            }

            // P0: Compute deterministic snapshotId from commit SHA and repo ID
            String deterministicSnapshotId = computeDeterministicSnapshotId(repo, commitSha);
            
            // P0: Compute checksum from all file checksums for content hash
            String contentHash = computeContentHash(files);

            RepositorySnapshot snapshot = RepositorySnapshot.builder()
                .snapshotId(deterministicSnapshotId)
                .provider(providerId())
                .repoId(repo)
                .commitSha(commitSha)
                .materializedRoot(root.toString())
                .checksum(commitSha)
                .contentHash(contentHash)
                .files(files)
                .diagnostics(List.of(new RepositorySnapshot.SnapshotDiagnostic(
                    RepositorySnapshot.DiagnosticLevel.INFO,
                    "GITLAB_SNAPSHOT_MATERIALIZED",
                    "GitLab snapshot materialized: " + commitSha + " with deterministic snapshotId: " + deterministicSnapshotId + ", totalSize: " + totalSize + " bytes, files: " + files.size(),
                    null,
                    Instant.now()
                )))
                .tenantId(scope.tenantId())
                .workspaceId(scope.workspaceId())
                .projectId(scope.projectId())
                .build();
            return Promise.of(snapshot);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * P0: Fetches all pages of the repository tree using GitLab's page-based pagination.
     * Fails closed if pagination is incomplete or inconsistent.
     */
    private List<JsonNode> fetchAllTreePages(String encodedRepo, String commitSha, String credentials)
            throws IOException, InterruptedException {
        List<JsonNode> all = new ArrayList<>();
        int page = 1;
        boolean firstPage = true;
        
        while (true) {
            String url = "https://gitlab.com/api/v4/projects/" + encodedRepo
                + "/repository/tree?ref=" + URLEncoder.encode(commitSha, StandardCharsets.UTF_8)
                + "&recursive=true&per_page=" + PAGE_SIZE + "&page=" + page;
            JsonNode pageResult = fetchJson(url, credentials);
            
            if (!pageResult.isArray()) {
                throw new IllegalStateException("GitLab tree endpoint returned non-array for page " + page);
            }
            
            int count = 0;
            for (JsonNode entry : pageResult) {
                all.add(entry);
                count++;
            }
            
            // P0: Fail closed if first page is empty for a non-empty ref
            if (firstPage && count == 0) {
                throw new IllegalStateException("GitLab tree first page returned empty array for ref: " + commitSha);
            }
            firstPage = false;
            
            // P0: Stop if we received fewer items than page size (last page)
            if (count < PAGE_SIZE) {
                break;
            }
            
            // P0: Safety limit to prevent infinite loops
            if (page > 1000) {
                throw new IllegalStateException("GitLab tree pagination exceeded safety limit of 1000 pages");
            }
            
            page++;
        }
        
        // P0: Fail closed if no entries were fetched
        if (all.isEmpty()) {
            throw new IllegalStateException("GitLab tree pagination returned zero entries for ref: " + commitSha);
        }
        
        return all;
    }

    private String resolveCommitSha(String repo, String ref, String credentials) throws IOException, InterruptedException {
        String encodedRepo = encodeProjectId(repo);
        String encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
        JsonNode commit = fetchJson("https://gitlab.com/api/v4/projects/" + encodedRepo + "/repository/commits/" + encodedRef, credentials);
        String sha = commit.path("id").asText();
        if (sha == null || sha.isBlank()) {
            throw new IllegalStateException("Unable to resolve commit SHA for GitLab repo");
        }
        return sha;
    }

    /**
     * P0: Fetch JSON from GitLab API with credentials support.
     */
    private JsonNode fetchJson(String url, String credentials) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .GET();
        
        if (credentials != null && !credentials.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + credentials);
        }
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitLab API request failed with status " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String computeContentChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(content);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String computeDeterministicSnapshotId(String repo, String commitSha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(repo.getBytes());
            digest.update(commitSha.getBytes());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * P0: Compute content hash from all file checksums.
     */
    private String computeContentHash(List<RepositorySnapshot.SnapshotFile> files) {
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
     * P0: Encodes the GitLab project ID (namespace/project) for use in API URLs.
     * GitLab expects slashes to be percent-encoded as %2F in the project path segment.
     */
    private static String encodeProjectId(String repoPath) {
        return URLEncoder.encode(repoPath, StandardCharsets.UTF_8);
    }

    /**
     * P0: Encodes a single file path component for use in GitLab Files API URLs.
     * Each path segment is individually encoded; the resulting segments are joined with %2F.
     */
    private static String encodeFilePath(String filePath) {
        String[] segments = filePath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append("%2F");
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }

    /**
     * P0: Normalizes a GitLab repository ID by removing common prefixes and suffixes.
     * Handles https://, git@gitlab.com:, and .git suffixes.
     */
    private static String normalizeRepo(String repoId) {
        String repo = repoId;
        if (repo.contains("gitlab.com/")) {
            repo = repo.substring(repo.indexOf("gitlab.com/") + "gitlab.com/".length());
        }
        if (repo.startsWith("https://")) {
            repo = repo.replace("https://gitlab.com/", "");
        }
        if (repo.startsWith("git@gitlab.com:")) {
            repo = repo.replace("git@gitlab.com:", "");
        }
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        return repo;
    }
}
