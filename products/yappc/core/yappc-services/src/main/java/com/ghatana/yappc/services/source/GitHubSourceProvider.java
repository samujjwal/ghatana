package com.ghatana.yappc.services.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose GitHub source provider with commit pinning and truncated-tree fail-closed behavior
 * @doc.layer service
 * @doc.pattern Strategy
 */
public final class GitHubSourceProvider implements SourceProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubSourceProvider() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    public GitHubSourceProvider(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return "github";
    }

    @Override
    public boolean canHandle(SourceLocator locator) {
        return "github".equals(locator.provider()) || locator.repoId().contains("github.com") || locator.repoId().contains("/");
    }

    @Override
    public Promise<RepositorySnapshot> resolve(SourceLocator locator, ScopeContext scope) {
        try {
            String repo = normalizeRepo(locator.repoId());
            String ref = (locator.ref() == null || locator.ref().isBlank()) ? "HEAD" : locator.ref();
            String commitSha = resolveCommitSha(repo, ref);
            JsonNode tree = fetchJson("https://api.github.com/repos/" + repo + "/git/trees/" + commitSha + "?recursive=1");

            if (tree.path("truncated").asBoolean(false)) {
                return Promise.ofException(new IllegalStateException("GitHub tree is truncated; failing closed"));
            }

            Path root = Files.createTempDirectory("yappc-github-");
            List<RepositorySnapshot.SnapshotFile> files = new ArrayList<>();
            for (JsonNode entry : tree.path("tree")) {
                if (!"blob".equals(entry.path("type").asText())) {
                    continue;
                }
                String path = entry.path("path").asText();
                String sha = entry.path("sha").asText();
                JsonNode blob = fetchJson("https://api.github.com/repos/" + repo + "/git/blobs/" + sha);
                byte[] content = Base64.getMimeDecoder().decode(blob.path("content").asText(""));

                Path filePath = root.resolve(path).normalize();
                if (!filePath.startsWith(root)) {
                    return Promise.ofException(new IllegalArgumentException("Path traversal detected in GitHub tree"));
                }
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, content);

                files.add(new RepositorySnapshot.SnapshotFile(
                    path,
                    filePath.toString(),
                    content.length,
                    Instant.now(),
                    true
                ));
            }

            RepositorySnapshot snapshot = new RepositorySnapshot(
                UUID.randomUUID().toString(),
                providerId(),
                repo,
                commitSha,
                ref,
                root.toString(),
                commitSha,
                Instant.now(),
                files,
                List.of(Map.of("level", "info", "message", "GitHub snapshot materialized", "commitSha", commitSha))
            );
            return Promise.of(snapshot);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    @Override
    public Map<String, Object> capabilities() {
        return Map.of(
            "supportsCommitPinning", true,
            "supportsTruncatedTreeFailClosed", true,
            "supportsBlobMaterialization", true
        );
    }

    private String resolveCommitSha(String repo, String ref) throws IOException, InterruptedException {
        JsonNode commit = fetchJson("https://api.github.com/repos/" + repo + "/commits/" + ref);
        String sha = commit.path("sha").asText();
        if (sha == null || sha.isBlank()) {
            throw new IllegalStateException("Unable to resolve commit SHA for GitHub repo");
        }
        return sha;
    }

    private JsonNode fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub API request failed with status " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private static String normalizeRepo(String repoId) {
        String repo = repoId;
        if (repo.contains("github.com/")) {
            repo = repo.substring(repo.indexOf("github.com/") + "github.com/".length());
        }
        if (repo.startsWith("https://")) {
            repo = repo.replace("https://github.com/", "");
        }
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        return repo;
    }
}
