package com.ghatana.products.yappc.design.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Automates GitHub pull request creation for design token updates.
 *
 * <p><b>Purpose</b><br>
 * Creates GitHub branches, commits files, and opens pull requests
 * programmatically. Used for automated design token updates from Figma.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GitHubPRAutomation github = new GitHubPRAutomation(
 *     "owner",
 *     "repo",
 *     "ghp_...",
 *     objectMapper,
 *     metrics
 * );
 *
 * // Create PR with updated tokens
 * github.createPullRequest(
 *     "design-tokens.json",
 *     tokensJson,
 *     "design-tokens/update-123",
 *     "Update design tokens",
 *     "Update design tokens from Figma",
 *     "Full PR description..."
 * ).await();
 * }</pre>
 *
 * <p><b>Authentication</b><br>
 * Uses GitHub Personal Access Token with permissions:
 * - repo (full access)
 * - workflow (if updating workflow files)
 *
 * <p><b>Workflow</b><br>
 * 1. Get default branch (main/master)
 * 2. Get latest commit SHA
 * 3. Create new branch from latest commit
 * 4. Create/update file on new branch
 * 5. Create pull request from new branch to default
 *
 * <p><b>API Rate Limits</b><br>
 * GitHub API: 5,000 requests/hour (authenticated)
 * Tracks API calls via metrics.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Stateless HTTP client.
 *
 * @see FigmaWebhookHandler
 * @doc.type class
 * @doc.purpose GitHub PR automation
 * @doc.layer product
 * @doc.pattern Automation/Client
 */
public class GitHubPRAutomation {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private static final Logger logger = LoggerFactory.getLogger(GitHubPRAutomation.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    private final String owner;
    private final String repo;
    private final String accessToken;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metrics;
    private final HttpClient httpClient;
    
    /**
     * Creates GitHub automation client
     *
     * @param owner Repository owner (username or org)
     * @param repo Repository name
     * @param accessToken GitHub Personal Access Token
     * @param objectMapper Jackson ObjectMapper
     * @param metrics Metrics collector
     */
    public GitHubPRAutomation(
            String owner,
            String repo,
            String accessToken,
            ObjectMapper objectMapper,
            MetricsCollector metrics) {
        this.owner = Objects.requireNonNull(owner, "Owner required");
        this.repo = Objects.requireNonNull(repo, "Repo required");
        this.accessToken = Objects.requireNonNull(accessToken, "Access token required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper required");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector required");
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }
    
    /**
     * Create pull request with file changes
     *
     * <p>Complete workflow: branch, commit, PR.
     *
     * @param filePath Path to file in repo (e.g., "src/tokens.json")
     * @param fileContent New file content
     * @param branchName New branch name
     * @param commitMessage Commit message
     * @param prTitle Pull request title
     * @param prBody Pull request description
     * @return Promise with PR URL
     * @throws GitHubApiException if API calls fail
     */
    public Promise<String> createPullRequest(
            String filePath,
            String fileContent,
            String branchName,
            String commitMessage,
            String prTitle,
            String prBody) {
        
        logger.info("[GitHub] Creating PR: {} → {}", branchName, prTitle);
        
        return getDefaultBranch()
                .then(defaultBranch -> getLatestCommitSha(defaultBranch)
                        .map(sha -> new BranchInfo(defaultBranch, sha)))
                .then(branchInfo -> createBranch(branchName, branchInfo.commitSha)
                        .map(v -> branchInfo))
                .then(branchInfo -> commitFile(filePath, fileContent, branchName, commitMessage)
                        .map(v -> branchInfo))
                .then(branchInfo -> createPR(branchName, branchInfo.branchName, prTitle, prBody))
                .whenComplete(prUrl -> {
                    logger.info("[GitHub] PR created: {}", prUrl);
                    metrics.incrementCounter("github.pr.created",
                            "status", "success");
                })
                .whenException(e -> {
                    logger.error("[GitHub] Failed to create PR", e);
                    metrics.incrementCounter("github.pr.created",
                            "status", "error");
                });
    }
    
    /**
     * Get default branch name (main or master)
     *
     * @return Promise with branch name
     */
    private Promise<String> getDefaultBranch() {
        String url = String.format("%s/repos/%s/%s", GITHUB_API_BASE, owner, repo);
        
        return makeRequest(url, "GET", null)
                .map(response -> {
                    JsonNode json = objectMapper.readTree(response);
                    String branch = json.path("default_branch").asText("main");
                    logger.debug("[GitHub] Default branch: {}", branch);
                    return branch;
                });
    }
    
    /**
     * Get latest commit SHA for branch
     *
     * @param branch Branch name
     * @return Promise with commit SHA
     */
    private Promise<String> getLatestCommitSha(String branch) {
        String url = String.format("%s/repos/%s/%s/git/refs/heads/%s",
                GITHUB_API_BASE, owner, repo, branch);
        
        return makeRequest(url, "GET", null)
                .map(response -> {
                    JsonNode json = objectMapper.readTree(response);
                    String sha = json.path("object").path("sha").asText();
                    logger.debug("[GitHub] Latest commit SHA: {}", sha);
                    return sha;
                });
    }
    
    /**
     * Create new branch from commit SHA
     *
     * @param branchName New branch name
     * @param fromSha Source commit SHA
     * @return Promise that completes when branch created
     */
    private Promise<Void> createBranch(String branchName, String fromSha) {
        String url = String.format("%s/repos/%s/%s/git/refs",
                GITHUB_API_BASE, owner, repo);
        
        ObjectNode body = objectMapper.createObjectNode();
        body.put("ref", "refs/heads/" + branchName);
        body.put("sha", fromSha);
        
        return makeRequest(url, "POST", body.toString())
                .map(response -> {
                    logger.debug("[GitHub] Branch created: {}", branchName);
                    return null;
                });
    }
    
    /**
     * Commit file to branch
     *
     * @param filePath File path in repo
     * @param content File content
     * @param branch Target branch
     * @param message Commit message
     * @return Promise that completes when committed
     */
    private Promise<Void> commitFile(String filePath, String content, 
                                      String branch, String message) {
        String url = String.format("%s/repos/%s/%s/contents/%s",
                GITHUB_API_BASE, owner, repo, filePath);
        
        // Base64 encode content
        String base64Content = Base64.getEncoder()
                .encodeToString(content.getBytes());
        
        ObjectNode body = objectMapper.createObjectNode();
        body.put("message", message);
        body.put("content", base64Content);
        body.put("branch", branch);
        
        return makeRequest(url, "PUT", body.toString())
                .map(response -> {
                    logger.debug("[GitHub] File committed: {}", filePath);
                    return null;
                });
    }
    
    /**
     * Create pull request
     *
     * @param headBranch Source branch (new changes)
     * @param baseBranch Target branch (main/master)
     * @param title PR title
     * @param body PR description
     * @return Promise with PR URL
     */
    private Promise<String> createPR(String headBranch, String baseBranch,
                                      String title, String body) {
        String url = String.format("%s/repos/%s/%s/pulls",
                GITHUB_API_BASE, owner, repo);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("head", headBranch);
        payload.put("base", baseBranch);
        
        return makeRequest(url, "POST", payload.toString())
                .map(response -> {
                    JsonNode json = objectMapper.readTree(response);
                    String prUrl = json.path("html_url").asText();
                    return prUrl;
                });
    }
    
    /**
     * Make HTTP request to GitHub API
     *
     * @param url API endpoint URL
     * @param method HTTP method (GET, POST, PUT)
     * @param body Request body (null for GET)
     * @return Promise with response body string
     */
    private Promise<String> makeRequest(String url, String method, String body) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .timeout(REQUEST_TIMEOUT);
                
                if ("POST".equals(method) || "PUT".equals(method)) {
                    builder.header("Content-Type", "application/json");
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                } else {
                    builder.GET();
                }
                
                HttpRequest request = builder.build();
                
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() >= 400) {
                    throw new GitHubApiException(
                            String.format("GitHub API error: %d - %s",
                                    response.statusCode(),
                                    response.body())
                    );
                }
                
                metrics.incrementCounter("github.api.calls",
                        "method", method,
                        "status", "success");
                
                return response.body();
                
            } catch (Exception e) {
                metrics.incrementCounter("github.api.calls",
                        "method", method,
                        "status", "error");
                throw new GitHubApiException("GitHub API request failed", e);
            }
        });
    }
    
    // ========================================================================
    // Helper Classes
    // ========================================================================
    
    /**
     * Branch information (name + commit SHA)
     */
    private static class BranchInfo {
        final String branchName;
        final String commitSha;
        
        BranchInfo(String branchName, String commitSha) {
            this.branchName = branchName;
            this.commitSha = commitSha;
        }
    }
    
    // ========================================================================
    // Exception
    // ========================================================================
    
    /**
     * Exception thrown when GitHub API calls fail
     *
     * @doc.type exception
     * @doc.purpose GitHub API error
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static class GitHubApiException extends RuntimeException {
        public GitHubApiException(String message) {
            super(message);
        }
        
        public GitHubApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
