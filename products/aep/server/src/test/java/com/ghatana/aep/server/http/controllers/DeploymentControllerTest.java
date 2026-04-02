package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.orchestrator.deployment.contract.DeploymentResponse;
import com.ghatana.orchestrator.deployment.http.DeploymentHttpAdapter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verify deployment request authorization and validation at the HTTP controller boundary
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DeploymentController")
@ExtendWith(MockitoExtension.class)
class DeploymentControllerTest extends EventloopTestBase {

    @Mock
    private DeploymentHttpAdapter deploymentAdapter;

    private DeploymentController controller;

    @BeforeEach
    void setUp() {
        controller = new DeploymentController(deploymentAdapter, false);
    }

    @Test
    @DisplayName("create deployment rejects requests without deployment privileges")
    void createDeploymentRejectsUnauthorizedPrincipal() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/deployments")
            .withBody(ByteBuf.wrapForReading(validDeploymentJson().getBytes(StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> controller.handleCreateDeployment(request));

        assertThat(response.getCode()).isEqualTo(403);
        verify(deploymentAdapter, never()).handleDeploymentRequest(any());
    }

    @Test
    @DisplayName("create deployment rejects invalid deployment payloads even for authorized users")
    void createDeploymentRejectsInvalidPayload() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/deployments")
            .withBody(ByteBuf.wrapForReading("{\"tenantId\":\"tenant-a\",\"environment\":\"prod\"}".getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT,
            new AepAuthFilter.JwtPayload("alice", "issuer", 1L, 1L, List.of("admin"), List.of(), "tenant-a"));

        HttpResponse response = runPromise(() -> controller.handleCreateDeployment(request));

        assertThat(response.getCode()).isEqualTo(400);
        verify(deploymentAdapter, never()).handleDeploymentRequest(any());
    }

    @Test
    @DisplayName("create deployment accepts authorized valid requests")
    void createDeploymentAcceptsAuthorizedValidRequest() {
        when(deploymentAdapter.handleDeploymentRequest(any()))
            .thenReturn(DeploymentResponse.builder()
                .deploymentId("dep-1")
                .pipelineId("pipeline-1")
                .tenantId("tenant-a")
                .status("DEPLOYED")
                .build());

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/deployments")
            .withBody(ByteBuf.wrapForReading(validDeploymentJson().getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT,
            new AepAuthFilter.JwtPayload("alice", "issuer", 1L, 1L, List.of("deployer"), List.of(), "tenant-a"));

        HttpResponse response = runPromise(() -> controller.handleCreateDeployment(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(deploymentAdapter).handleDeploymentRequest(any());
    }

    private String validDeploymentJson() {
        return "{" +
            "\"pipelineId\":\"pipeline-1\"," +
            "\"tenantId\":\"tenant-a\"," +
            "\"environment\":\"production\"" +
            "}";
    }
}