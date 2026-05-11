/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.services.phase.PhasePacket;
import com.ghatana.yappc.services.phase.PhasePacketService;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose E2E tests for phase packet endpoint across all mounted phases
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhasePacketController E2E")
class PhasePacketControllerE2ETest extends EventloopTestBase {

    private InMemoryPhasePacketService phasePacketService;
    private ObjectMapper objectMapper;
    private PhasePacketController controller;
    private Principal testPrincipal;

    @BeforeEach
    void setUp() {
        phasePacketService = new InMemoryPhasePacketService();
        objectMapper = JsonMapper.objectMapper();
        controller = new PhasePacketController(objectMapper, phasePacketService);
        testPrincipal = new Principal("user-1", List.of("builder"), "tenant-1");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for intent phase")
    void getPhasePacketReturns200ForIntentPhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("intent", "project-1"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"intent\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-1\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for shape phase")
    void getPhasePacketReturns200ForShapePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("shape", "project-2"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=shape&projectId=project-2&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"shape\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-2\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for validate phase")
    void getPhasePacketReturns200ForValidatePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("validate", "project-3"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=validate&projectId=project-3&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"validate\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-3\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for generate phase")
    void getPhasePacketReturns200ForGeneratePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("generate", "project-4"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=generate&projectId=project-4&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"generate\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-4\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for run phase")
    void getPhasePacketReturns200ForRunPhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("run", "project-5"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=run&projectId=project-5&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"run\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-5\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for observe phase")
    void getPhasePacketReturns200ForObservePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("observe", "project-6"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=observe&projectId=project-6&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"observe\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-6\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for learn phase")
    void getPhasePacketReturns200ForLearnPhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("learn", "project-7"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=learn&projectId=project-7&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"learn\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-7\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 200 for evolve phase")
    void getPhasePacketReturns200ForEvolvePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("evolve", "project-8"));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=evolve&projectId=project-8&workspaceId=workspace-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"evolve\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-8\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for intent phase")
    void postPhasePacketReturns200ForIntentPhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("intent", "project-1"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "intent",
            "tenant-1",
            "project-1",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"intent\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-1\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for shape phase")
    void postPhasePacketReturns200ForShapePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("shape", "project-2"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "shape",
            "tenant-1",
            "project-2",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"shape\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-2\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for validate phase")
    void postPhasePacketReturns200ForValidatePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("validate", "project-3"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "validate",
            "tenant-1",
            "project-3",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"validate\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-3\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for generate phase")
    void postPhasePacketReturns200ForGeneratePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("generate", "project-4"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "generate",
            "tenant-1",
            "project-4",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"generate\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-4\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for run phase")
    void postPhasePacketReturns200ForRunPhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("run", "project-5"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "run",
            "tenant-1",
            "project-5",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"run\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-5\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for observe phase")
    void postPhasePacketReturns200ForObservePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("observe", "project-6"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "observe",
            "tenant-1",
            "project-6",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"observe\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-6\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for learn phase")
    void postPhasePacketReturns200ForLearnPhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("learn", "project-7"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "learn",
            "tenant-1",
            "project-7",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"learn\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-7\"");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 200 for evolve phase")
    void postPhasePacketReturns200ForEvolvePhase() throws Exception {
        phasePacketService.setPacket(createTestPacket("evolve", "project-8"));

        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "evolve",
            "tenant-1",
            "project-8",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"phase\" : \"evolve\"");
        assertThat(responseJson).contains("\"projectId\" : \"project-8\"");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 400 when phase is missing")
    void getPhasePacketReturns400WhenPhaseMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?projectId=project-1")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(400);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("phase is required");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 400 when projectId is missing")
    void getPhasePacketReturns400WhenProjectIdMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent")
            .withAttachment(Principal.class, testPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(400);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("projectId is required");
    }

    @Test
    @DisplayName("GET /api/v1/phase/packet returns 401 when principal is missing")
    void getPhasePacketReturns401WhenPrincipalMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1");

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(401);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("Unauthenticated");
    }

    @Test
    @DisplayName("POST /api/v1/phase/packet returns 403 when tenant scope mismatches")
    void postPhasePacketReturns403WhenTenantScopeMismatches() throws Exception {
        Principal differentTenantPrincipal = new Principal("user-1", List.of("builder"), "different-tenant");
        
        String requestJson = JsonMapper.toJson(new PhasePacketController.PhasePacketRequest(
            "intent",
            "tenant-1",
            "project-1",
            "workspace-1",
            "correlation-123"
        ));
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/phase/packet")
            .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
            .withAttachment(Principal.class, differentTenantPrincipal);

        HttpResponse response = runPromise(() -> controller.getPhasePacket(request));

        assertThat(response.getCode()).isEqualTo(403);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("Forbidden: tenant scope mismatch");
    }

    @Test
    @DisplayName("Phase packet returns read-only permissions for viewer role")
    void phasePacketReturnsReadOnlyPermissionsForViewerRole() throws Exception {
        phasePacketService.setPacket(createTestPacketWithPermissions("intent", "project-1", 
            true, false, false, false, false, false, false));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("viewer-user", List.of("viewer"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"canRead\" : true");
        assertThat(responseJson).contains("\"canCreate\" : false");
        assertThat(responseJson).contains("\"canUpdate\" : false");
        assertThat(responseJson).contains("\"canDelete\" : false");
        assertThat(responseJson).contains("\"canApprove\" : false");
        assertThat(responseJson).contains("\"canReject\" : false");
        assertThat(responseJson).contains("\"canRollback\" : false");
    }

    @Test
    @DisplayName("Phase packet returns editor permissions for collaborator role")
    void phasePacketReturnsEditorPermissionsForCollaboratorRole() throws Exception {
        phasePacketService.setPacket(createTestPacketWithPermissions("intent", "project-1", 
            true, true, true, false, false, false, false));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("editor-user", List.of("collaborator"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"canRead\" : true");
        assertThat(responseJson).contains("\"canCreate\" : true");
        assertThat(responseJson).contains("\"canUpdate\" : true");
        assertThat(responseJson).contains("\"canDelete\" : false");
        assertThat(responseJson).contains("\"canApprove\" : false");
        assertThat(responseJson).contains("\"canReject\" : false");
        assertThat(responseJson).contains("\"canRollback\" : false");
    }

    @Test
    @DisplayName("Phase packet returns admin permissions for admin role")
    void phasePacketReturnsAdminPermissionsForAdminRole() throws Exception {
        phasePacketService.setPacket(createTestPacketWithPermissions("intent", "project-1", 
            true, true, true, true, true, true, false));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("admin-user", List.of("admin"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"canRead\" : true");
        assertThat(responseJson).contains("\"canCreate\" : true");
        assertThat(responseJson).contains("\"canUpdate\" : true");
        assertThat(responseJson).contains("\"canDelete\" : true");
        assertThat(responseJson).contains("\"canApprove\" : true");
        assertThat(responseJson).contains("\"canReject\" : true");
        assertThat(responseJson).contains("\"canRollback\" : false");
    }

    @Test
    @DisplayName("Phase packet returns owner permissions for owner role")
    void phasePacketReturnsOwnerPermissionsForOwnerRole() throws Exception {
        phasePacketService.setPacket(createTestPacketWithPermissions("intent", "project-1", 
            true, true, true, true, true, true, true));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("owner-user", List.of("owner"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"canRead\" : true");
        assertThat(responseJson).contains("\"canCreate\" : true");
        assertThat(responseJson).contains("\"canUpdate\" : true");
        assertThat(responseJson).contains("\"canDelete\" : true");
        assertThat(responseJson).contains("\"canApprove\" : true");
        assertThat(responseJson).contains("\"canReject\" : true");
        assertThat(responseJson).contains("\"canRollback\" : true");
    }

    @Test
    @DisplayName("Phase packet respects FREE tier limitations")
    void phasePacketRespectsFreeTierLimitations() throws Exception {
        phasePacketService.setPacket(createTestPacketWithTier("intent", "project-1", "FREE", Set.of()));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("free-user", List.of("owner"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"tenantTier\" : \"FREE\"");
        assertThat(responseJson).contains("\"enabledPhaseFlags\" : []");
    }

    @Test
    @DisplayName("Phase packet respects PRO tier capabilities")
    void phasePacketRespectsProTierCapabilities() throws Exception {
        phasePacketService.setPacket(createTestPacketWithTier("intent", "project-1", "PRO", Set.of("AI_SUGGESTIONS", "DASHBOARD")));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("pro-user", List.of("owner"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"tenantTier\" : \"PRO\"");
        assertThat(responseJson).contains("\"enabledPhaseFlags\" : [\"AI_SUGGESTIONS\", \"DASHBOARD\"]");
    }

    @Test
    @DisplayName("Phase packet respects ENTERPRISE tier capabilities")
    void phasePacketRespectsEnterpriseTierCapabilities() throws Exception {
        phasePacketService.setPacket(createTestPacketWithTier("intent", "project-1", "ENTERPRISE", 
            Set.of("AI_SUGGESTIONS", "DASHBOARD", "ADVANCED_ANALYTICS", "CUSTOM_INTEGRATIONS")));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/phase/packet?phase=intent&projectId=project-1&workspaceId=workspace-1")
            .withAttachment(Principal.class, new Principal("enterprise-user", List.of("owner"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.getPhasePacketWithQuery(request));

        assertThat(response.getCode()).isEqualTo(200);
        String responseJson = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseJson).contains("\"tenantTier\" : \"ENTERPRISE\"");
        assertThat(responseJson).contains("\"enabledPhaseFlags\" : [\"AI_SUGGESTIONS\", \"DASHBOARD\", \"ADVANCED_ANALYTICS\", \"CUSTOM_INTEGRATIONS\"]");
    }

    private PhasePacket createTestPacket(String phase, String projectId) {
        return PhasePacket.builder()
            .phase(phase)
            .projectId(projectId)
            .projectName("Test Project")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .workspaceName("Test Workspace")
            .currentPhase(phase.toUpperCase())
            .tenantTier("PRO")
            .enabledPhaseFlags(Set.of("AI_SUGGESTIONS", "DASHBOARD"))
            .canRead(true)
            .canCreate(true)
            .canUpdate(true)
            .canDelete(false)
            .canApprove(false)
            .canReject(false)
            .canRollback(false)
            .blockers(List.of())
            .readiness(new PhasePacket.PhaseReadiness(
                true,
                "SHAPE",
                List.of(),
                0.95,
                false
            ))
            .requiredArtifacts(List.of())
            .completedArtifacts(List.of())
            .activityFeed(List.of())
            .evidence(List.of())
            .governance(List.of())
            .availableActions(List.of())
            .dashboardActions(new PhasePacket.DashboardActions(
                "primary-action",
                List.of(),
                List.of(),
                List.of()
            ))
            .healthSignals(new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                new PhasePacket.GenerationHealth(true, "healthy", null, List.of()),
                new PhasePacket.RuntimeHealth(true, "healthy", null, List.of())
            ))
            .generatedAt(Instant.now().toEpochMilli())
            .correlationId("test-correlation")
            .build();
    }

    private PhasePacket createTestPacketWithPermissions(String phase, String projectId,
            boolean canRead, boolean canCreate, boolean canUpdate, boolean canDelete,
            boolean canApprove, boolean canReject, boolean canRollback) {
        return PhasePacket.builder()
            .phase(phase)
            .projectId(projectId)
            .projectName("Test Project")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .workspaceName("Test Workspace")
            .currentPhase(phase.toUpperCase())
            .tenantTier("PRO")
            .enabledPhaseFlags(Set.of("AI_SUGGESTIONS", "DASHBOARD"))
            .canRead(canRead)
            .canCreate(canCreate)
            .canUpdate(canUpdate)
            .canDelete(canDelete)
            .canApprove(canApprove)
            .canReject(canReject)
            .canRollback(canRollback)
            .blockers(List.of())
            .readiness(new PhasePacket.PhaseReadiness(
                true,
                "SHAPE",
                List.of(),
                0.95,
                false
            ))
            .requiredArtifacts(List.of())
            .completedArtifacts(List.of())
            .activityFeed(List.of())
            .evidence(List.of())
            .governance(List.of())
            .availableActions(List.of())
            .dashboardActions(new PhasePacket.DashboardActions(
                "primary-action",
                List.of(),
                List.of(),
                List.of()
            ))
            .healthSignals(new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                new PhasePacket.GenerationHealth(true, "healthy", null, List.of()),
                new PhasePacket.RuntimeHealth(true, "healthy", null, List.of())
            ))
            .generatedAt(Instant.now().toEpochMilli())
            .correlationId("test-correlation")
            .build();
    }

    private PhasePacket createTestPacketWithTier(String phase, String projectId, String tier, Set<String> enabledFlags) {
        return PhasePacket.builder()
            .phase(phase)
            .projectId(projectId)
            .projectName("Test Project")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .workspaceName("Test Workspace")
            .currentPhase(phase.toUpperCase())
            .tenantTier(tier)
            .enabledPhaseFlags(enabledFlags)
            .canRead(true)
            .canCreate(true)
            .canUpdate(true)
            .canDelete(true)
            .canApprove(true)
            .canReject(true)
            .canRollback(true)
            .blockers(List.of())
            .readiness(new PhasePacket.PhaseReadiness(
                true,
                "SHAPE",
                List.of(),
                0.95,
                false
            ))
            .requiredArtifacts(List.of())
            .completedArtifacts(List.of())
            .activityFeed(List.of())
            .evidence(List.of())
            .governance(List.of())
            .availableActions(List.of())
            .dashboardActions(new PhasePacket.DashboardActions(
                "primary-action",
                List.of(),
                List.of(),
                List.of()
            ))
            .healthSignals(new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                new PhasePacket.GenerationHealth(true, "healthy", null, List.of()),
                new PhasePacket.RuntimeHealth(true, "healthy", null, List.of())
            ))
            .generatedAt(Instant.now().toEpochMilli())
            .correlationId("test-correlation")
            .build();
    }

    private static final class InMemoryPhasePacketService implements PhasePacketService {
        private PhasePacket packet = null;

        void setPacket(PhasePacket packet) {
            this.packet = packet;
        }

        @Override
        public Promise<PhasePacket> buildPhasePacket(
                String phase,
                String projectId,
                String workspaceId,
                Principal principal,
                String correlationId
        ) {
            return Promise.of(packet);
        }
    }
}
