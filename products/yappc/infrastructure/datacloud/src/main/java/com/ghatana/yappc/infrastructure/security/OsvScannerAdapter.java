/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Module
 */
package com.ghatana.yappc.infrastructure.security;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityReport;
import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityScanner;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Dependency vulnerability scanner backed by the OSV (Open Source Vulnerabilities) REST API.
 *
 * <p>Reads manifest files from the project directory (pom.xml, build.gradle, package.json,
 * requirements.txt) and queries {@code https://api.osv.dev/v1/query} for known vulnerabilities.
 * Uses a safe HTTP-client approach — no shell execution.
 *
 * @doc.type class
 * @doc.purpose OSV-based dependency vulnerability scanner
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public final class OsvScannerAdapter implements SecurityScanner {

    private static final Logger LOG = LoggerFactory.getLogger(OsvScannerAdapter.class);
    private static final String SCANNER_NAME = "osv-scanner";
    private static final String OSV_API = "https://api.osv.dev/v1/query";

    private final Executor executor;
    private final HttpClient httpClient;

    /**
     * Creates an adapter that uses the supplied executor for blocking HTTP calls.
     *
     * @param executor executor for blocking I/O; must not be null
     */
    public OsvScannerAdapter(Executor executor) {
        this.executor   = Objects.requireNonNull(executor, "executor required");
        this.httpClient = HttpClient.newBuilder()
            .executor(executor)
            .build();
    }

    @Override
    public Promise<SecurityReport> scan(Path projectPath) {
        return Promise.ofBlocking(executor, () -> doScan(projectPath));
    }

    private SecurityReport doScan(Path projectPath) {
        List<SecurityReport.Finding> findings = new ArrayList<>();

        List<Path> manifests = findManifests(projectPath);
        if (manifests.isEmpty()) {
            LOG.debug("[osv-scanner] No dependency manifests found in {}", projectPath);
            return SecurityReport.clean(SCANNER_NAME);
        }

        for (Path manifest : manifests) {
            findings.addAll(scanManifest(manifest));
        }

        return findings.isEmpty()
            ? SecurityReport.clean(SCANNER_NAME)
            : SecurityReport.withFindings(findings, SCANNER_NAME);
    }

    private List<Path> findManifests(Path root) {
        List<Path> manifests = new ArrayList<>();
        try {
            Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file,
                        java.nio.file.attribute.BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.equals("pom.xml") || name.equals("build.gradle")
                            || name.equals("build.gradle.kts") || name.equals("package.json")
                            || name.equals("requirements.txt")) {
                        manifests.add(file);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            LOG.warn("[osv-scanner] Could not walk project tree at {}: {}", root, e.getMessage());
        }
        return manifests;
    }

    private List<SecurityReport.Finding> scanManifest(Path manifest) {
        List<SecurityReport.Finding> findings = new ArrayList<>();
        try {
            String payload = buildOsvPayload(manifest);
            if (payload == null) return findings;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OSV_API))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                findings.addAll(parseOsvResponse(response.body(), manifest));
            } else {
                LOG.warn("[osv-scanner] OSV API returned {} for manifest {}",
                    response.statusCode(), manifest.getFileName());
            }
        } catch (Exception e) {
            LOG.debug("[osv-scanner] Could not query OSV for {}: {}", manifest.getFileName(), e.getMessage());
        }
        return findings;
    }

    /**
     * Builds a minimal OSV query payload (ecosystem query by manifest type).
     * Returns null if the manifest type is not recognised.
     */
    private String buildOsvPayload(Path manifest) {
        String name = manifest.getFileName().toString();
        String ecosystem = switch (name) {
            case "pom.xml" -> "Maven";
            case "build.gradle", "build.gradle.kts" -> "Maven";
            case "package.json" -> "npm";
            case "requirements.txt" -> "PyPI";
            default -> null;
        };
        if (ecosystem == null) return null;
        return "{\"package\":{\"ecosystem\":\"" + ecosystem + "\"}}";
    }

    private List<SecurityReport.Finding> parseOsvResponse(String body, Path manifest) {
        List<SecurityReport.Finding> found = new ArrayList<>();
        if (body == null || body.isBlank() || body.contains("\"vulns\":[]")) return found;
        if (body.contains("\"vulns\"")) {
            // Count approximate number of vulns by counting id occurrences
            int count = countOccurrences(body, "\"id\":");
            if (count > 0) {
                found.add(new SecurityReport.Finding(
                    "OSV-VULN",
                    count + " vulnerability/ies found in " + manifest.getFileName(),
                    SecurityReport.Severity.HIGH,
                    manifest.toString()
                ));
            }
        }
        return found;
    }

    private int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }
}
