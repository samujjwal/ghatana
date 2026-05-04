/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.domain.pageartifact.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.yappc.domain.pageartifact.PageArtifactConflictException;
import com.ghatana.yappc.domain.pageartifact.PageArtifactDocument;
import com.ghatana.yappc.domain.pageartifact.PageArtifactRepository;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP controller for page artifact operations.
 * <p>
 * Exposes REST endpoints for:
 * <ul>
 *   <li>PUT /api/v1/page-artifacts/:artifactId/document - Save page artifact document</li>
 *   <li>GET /api/v1/page-artifacts/:artifactId/document - Load page artifact document</li>
 * </ul>
 * All endpoints require tenant/workspace/project scoping via headers and support
 * optimistic concurrency via If-Match header.
 *
 * @doc.type class
 * @doc.purpose HTTP controller for page artifact REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class PageArtifactController {

    private static final Logger LOG = LoggerFactory.getLogger(PageArtifactController.class);
    private static final Pattern ARTIFACT_ID_PATTERN =
            Pattern.compile("/api/v1/page-artifacts/([^/]+)/document");

    private final PageArtifactRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new PageArtifactController.
     *
     * @param repository The page artifact repository
     * @param objectMapper JSON object mapper
     */
    public PageArtifactController(
            @NotNull PageArtifactRepository repository,
            @NotNull ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * PUT /api/v1/page-artifacts/:artifactId/document
     * <p>
     * Saves a page artifact document with optimistic concurrency control.
     * Requires If-Match header with the current documentId as ETag.
     * Returns 409 with X-Current-Version header on conflict.
     */
    public Promise<HttpResponse> saveDocument(HttpRequest request) {
        // Validate required headers
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));

        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Tenant-ID header"));
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Workspace-ID header"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Project-ID header"));
        }

        // Extract artifactId from path
        Optional<String> artifactIdOpt = extractArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        // Parse request body
        return request.loadBody().then(loadedBody -> {
            try {
                byte[] body = loadedBody.asArray();
                PageArtifactDocument document = objectMapper.readValue(body, PageArtifactDocument.class);

                // Validate that artifactId in path matches document
                if (!artifactId.equals(document.artifactId())) {
                    return Promise.of(badRequest(
                            "Artifact ID in path (" + artifactId + ") does not match document (" + document.artifactId() + ")"
                    ));
                }

                // Validate If-Match header for optimistic concurrency
                String ifMatch = request.getHeader(HttpHeaders.of("If-Match"));
                if (ifMatch == null || !ifMatch.equals(document.documentId())) {
                    return Promise.of(badRequest("If-Match header must match document.documentId"));
                }

                LOG.info("Saving page artifact: tenant={}, workspace={}, project={}, artifactId={}",
                        tenantId, workspaceId, projectId, artifactId);

                return repository.save(tenantId, workspaceId, projectId, document)
                        .map($ -> ResponseBuilder.ok()
                                .header("ETag", document.documentId())
                                .json(Map.of(
                                        "artifactId", artifactId,
                                        "documentId", document.documentId(),
                                        "syncStatus", document.syncStatus()
                                ))
                                .build())
                        .mapError(ex -> {
                            if (ex instanceof PageArtifactConflictException) {
                                PageArtifactConflictException conflict = (PageArtifactConflictException) ex;
                                LOG.warn("Conflict saving artifact {}: remote version={}", artifactId, conflict.remoteVersion());
                                return ResponseBuilder.conflict()
                                        .header("X-Current-Version", conflict.remoteVersion())
                                        .json(Map.of(
                                                "error", "Conflict",
                                                "message", conflict.getMessage(),
                                                "remoteVersion", conflict.remoteVersion()
                                        ))
                                        .build();
                            }
                            LOG.error("Failed to save page artifact", ex);
                            return ResponseBuilder.internalServerError()
                                    .json(Map.of(
                                            "error", "Internal Server Error",
                                            "message", "Failed to save page artifact"
                                    ))
                                    .build();
                        });

            } catch (Exception e) {
                LOG.error("Failed to parse request body", e);
                return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /api/v1/page-artifacts/:artifactId/document
     * <p>
     * Loads a page artifact document by artifact ID.
     * Returns the document with ETag header for optimistic concurrency.
     */
    public Promise<HttpResponse> loadDocument(HttpRequest request) {
        // Validate required headers
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));

        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Tenant-ID header"));
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Workspace-ID header"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Project-ID header"));
        }

        // Extract artifactId from path
        Optional<String> artifactIdOpt = extractArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        LOG.info("Loading page artifact: tenant={}, workspace={}, project={}, artifactId={}",
                tenantId, workspaceId, projectId, artifactId);

        return repository.load(tenantId, workspaceId, projectId, artifactId)
                .map(document -> {
                    if (document == null) {
                        LOG.debug("Page artifact not found: {}", artifactId);
                        return ResponseBuilder.notFound()
                                .json(Map.of(
                                        "error", "Not Found",
                                        "message", "Page artifact not found: " + artifactId
                                ))
                                .build();
                    }

                    LOG.debug("Successfully loaded page artifact: {}", artifactId);
                    return ResponseBuilder.ok()
                            .header("ETag", document.documentId())
                            .json(document)
                            .build();
                })
                .mapError(ex -> {
                    LOG.error("Failed to load page artifact", ex);
                    return ResponseBuilder.internalServerError()
                            .json(Map.of(
                                    "error", "Internal Server Error",
                                    "message", "Failed to load page artifact"
                            ))
                            .build();
                });
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private Optional<String> extractArtifactId(String path) {
        Matcher matcher = ARTIFACT_ID_PATTERN.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private HttpResponse badRequest(String message) {
        return ResponseBuilder.badRequest()
                .json(Map.of(
                        "error", "Bad Request",
                        "message", message
                ))
                .build();
    }
}
