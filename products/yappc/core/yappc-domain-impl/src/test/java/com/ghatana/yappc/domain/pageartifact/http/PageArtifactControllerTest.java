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
import com.ghatana.yappc.domain.pageartifact.InMemoryPageArtifactRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactConflictException;
import com.ghatana.yappc.domain.pageartifact.PageArtifactDocument;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PageArtifactController.
 *
 * @doc.type test
 * @doc.purpose Validate page artifact HTTP endpoint behavior
 * @doc.layer product
 * @doc.pattern Controller Test
 */
@DisplayName("PageArtifactController Tests")
class PageArtifactControllerTest {

    private InMemoryPageArtifactRepository repository;
    private PageArtifactController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPageArtifactRepository();
        objectMapper = new ObjectMapper();
        controller = new PageArtifactController(repository, objectMapper);
    }

    @Test
    @DisplayName("Should save document successfully with valid headers and If-Match")
    void shouldSaveDocumentSuccessfully() {
        PageArtifactDocument document = createTestDocument("artifact-1", "doc-v1");
        String body = toJson(document);

        HttpRequest request = createPutRequest(
                "/api/v1/page-artifacts/artifact-1/document",
                body,
                "tenant-1",
                "workspace-1",
                "project-1",
                "doc-v1"
        );

        Promise<HttpResponse> responsePromise = controller.saveDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(200, response.getCode());
    }

    @Test
    @DisplayName("Should return 409 conflict on version mismatch")
    void shouldReturnConflictOnVersionMismatch() {
        // Save initial version
        PageArtifactDocument doc1 = createTestDocument("artifact-2", "doc-v1");
        repository.save("tenant-1", "workspace-1", "project-1", doc1).getResult();

        // Try to save with wrong version
        PageArtifactDocument doc2 = createTestDocument("artifact-2", "doc-v2");
        String body = toJson(doc2);

        HttpRequest request = createPutRequest(
                "/api/v1/page-artifacts/artifact-2/document",
                body,
                "tenant-1",
                "workspace-1",
                "project-1",
                "doc-v1"  // Wrong version
        );

        Promise<HttpResponse> responsePromise = controller.saveDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(409, response.getCode());
        assertNotNull(response.getHeader(HttpHeaders.of("X-Current-Version")));
    }

    @Test
    @DisplayName("Should return 400 when X-Tenant-ID header is missing")
    void shouldReturnBadRequestWhenTenantIdMissing() {
        PageArtifactDocument document = createTestDocument("artifact-3", "doc-v1");
        String body = toJson(document);

        HttpRequest request = createPutRequest(
                "/api/v1/page-artifacts/artifact-3/document",
                body,
                null,  // Missing tenant ID
                "workspace-1",
                "project-1",
                "doc-v1"
        );

        Promise<HttpResponse> responsePromise = controller.saveDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(400, response.getCode());
    }

    @Test
    @DisplayName("Should return 400 when X-Workspace-ID header is missing")
    void shouldReturnBadRequestWhenWorkspaceIdMissing() {
        PageArtifactDocument document = createTestDocument("artifact-4", "doc-v1");
        String body = toJson(document);

        HttpRequest request = createPutRequest(
                "/api/v1/page-artifacts/artifact-4/document",
                body,
                "tenant-1",
                null,  // Missing workspace ID
                "project-1",
                "doc-v1"
        );

        Promise<HttpResponse> responsePromise = controller.saveDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(400, response.getCode());
    }

    @Test
    @DisplayName("Should return 400 when X-Project-ID header is missing")
    void shouldReturnBadRequestWhenProjectIdMissing() {
        PageArtifactDocument document = createTestDocument("artifact-5", "doc-v1");
        String body = toJson(document);

        HttpRequest request = createPutRequest(
                "/api/v1/page-artifacts/artifact-5/document",
                body,
                "tenant-1",
                "workspace-1",
                null,  // Missing project ID
                "doc-v1"
        );

        Promise<HttpResponse> responsePromise = controller.saveDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(400, response.getCode());
    }

    @Test
    @DisplayName("Should return 400 when If-Match header doesn't match documentId")
    void shouldReturnBadRequestWhenIfMatchMismatch() {
        PageArtifactDocument document = createTestDocument("artifact-6", "doc-v1");
        String body = toJson(document);

        HttpRequest request = createPutRequest(
                "/api/v1/page-artifacts/artifact-6/document",
                body,
                "tenant-1",
                "workspace-1",
                "project-1",
                "wrong-version"  // Mismatched If-Match
        );

        Promise<HttpResponse> responsePromise = controller.saveDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(400, response.getCode());
    }

    @Test
    @DisplayName("Should load document successfully")
    void shouldLoadDocumentSuccessfully() {
        PageArtifactDocument document = createTestDocument("artifact-7", "doc-v1");
        repository.save("tenant-1", "workspace-1", "project-1", document).getResult();

        HttpRequest request = createGetRequest(
                "/api/v1/page-artifacts/artifact-7/document",
                "tenant-1",
                "workspace-1",
                "project-1"
        );

        Promise<HttpResponse> responsePromise = controller.loadDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(200, response.getCode());
        assertNotNull(response.getHeader(HttpHeaders.of("ETag")));
    }

    @Test
    @DisplayName("Should return 404 when document not found")
    void shouldReturnNotFoundWhenDocumentNotFound() {
        HttpRequest request = createGetRequest(
                "/api/v1/page-artifacts/nonexistent/document",
                "tenant-1",
                "workspace-1",
                "project-1"
        );

        Promise<HttpResponse> responsePromise = controller.loadDocument(request);
        HttpResponse response = responsePromise.getResult();

        assertEquals(404, response.getCode());
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private PageArtifactDocument createTestDocument(String artifactId, String documentId) {
        return new PageArtifactDocument(
                artifactId,
                documentId,
                "Test Page",
                "test-user",
                Instant.now(),
                Instant.now(),
                "synced",
                "UNKNOWN",
                "UNCLASSIFIED",
                Map.of("rootNodes", new String[0], "nodes", Map.of()),
                null,
                null,
                null,
                0,
                1.0
        );
    }

    private String toJson(PageArtifactDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest createPutRequest(
            String path,
            String body,
            String tenantId,
            String workspaceId,
            String projectId,
            String ifMatch
    ) {
        // Create a mock request - in real tests you'd use a test framework
        // For now, we'll use a simple stub
        return new TestHttpRequest(path, body, tenantId, workspaceId, projectId, ifMatch);
    }

    private HttpRequest createGetRequest(
            String path,
            String tenantId,
            String workspaceId,
            String projectId
    ) {
        return new TestHttpRequest(path, null, tenantId, workspaceId, projectId, null);
    }

    // Simple test stub for HttpRequest
    private static class TestHttpRequest extends HttpRequest {
        private final String path;
        private final String body;
        private final String tenantId;
        private final String workspaceId;
        private final String projectId;
        private final String ifMatch;

        TestHttpRequest(String path, String body, String tenantId, String workspaceId, String projectId, String ifMatch) {
            this.path = path;
            this.body = body;
            this.tenantId = tenantId;
            this.workspaceId = workspaceId;
            this.projectId = projectId;
            this.ifMatch = ifMatch;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getHeader(HttpHeaders header) {
            if (header == HttpHeaders.of("X-Tenant-ID")) return tenantId;
            if (header == HttpHeaders.of("X-Workspace-ID")) return workspaceId;
            if (header == HttpHeaders.of("X-Project-ID")) return projectId;
            if (header == HttpHeaders.of("If-Match")) return ifMatch;
            return null;
        }

        @Override
        public Promise<Body> loadBody() {
            return Promise.of(new Body(body != null ? body.getBytes() : new byte[0]));
        }

        // Stub other required methods
        @Override public HttpMethod getMethod() { return HttpMethod.PUT; }
        @Override public String getUrl() { return path; }
    }
}
