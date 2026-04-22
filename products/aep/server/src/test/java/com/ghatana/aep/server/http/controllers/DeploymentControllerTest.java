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
@DisplayName("DeploymentController [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DeploymentControllerTest extends EventloopTestBase {

    @Mock
    private DeploymentHttpAdapter deploymentAdapter;

    private DeploymentController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new DeploymentController(deploymentAdapter, false); // GH-90000
    }

    @Test
    @DisplayName("create deployment rejects requests without deployment privileges [GH-90000]")
    void createDeploymentRejectsUnauthorizedPrincipal() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/deployments [GH-90000]")
            .withBody(ByteBuf.wrapForReading(validDeploymentJson().getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.handleCreateDeployment(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
        verify(deploymentAdapter, never()).handleDeploymentRequest(any()); // GH-90000
    }

    @Test
    @DisplayName("create deployment rejects invalid deployment payloads even for authorized users [GH-90000]")
    void createDeploymentRejectsInvalidPayload() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/deployments [GH-90000]")
            .withBody(ByteBuf.wrapForReading("{\"tenantId\":\"tenant-a\",\"environment\":\"prod\"}".getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000
        request.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT, // GH-90000
            new AepAuthFilter.JwtPayload("alice", "issuer", 1L, 1L, List.of("admin [GH-90000]"), List.of(), "tenant-a"));

        HttpResponse response = runPromise(() -> controller.handleCreateDeployment(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        verify(deploymentAdapter, never()).handleDeploymentRequest(any()); // GH-90000
    }

    @Test
    @DisplayName("create deployment accepts authorized valid requests [GH-90000]")
    void createDeploymentAcceptsAuthorizedValidRequest() { // GH-90000
        when(deploymentAdapter.handleDeploymentRequest(any())) // GH-90000
            .thenReturn(io.activej.promise.Promise.of(DeploymentResponse.builder() // GH-90000
                .deploymentId("dep-1 [GH-90000]")
                .pipelineId("pipeline-1 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .status("DEPLOYED [GH-90000]")
                .build())); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/deployments [GH-90000]")
            .withBody(ByteBuf.wrapForReading(validDeploymentJson().getBytes(StandardCharsets.UTF_8))) // GH-90000
            .build(); // GH-90000
        request.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT, // GH-90000
            new AepAuthFilter.JwtPayload("alice", "issuer", 1L, 1L, List.of("deployer [GH-90000]"), List.of(), "tenant-a"));

        HttpResponse response = runPromise(() -> controller.handleCreateDeployment(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        verify(deploymentAdapter).handleDeploymentRequest(any()); // GH-90000
    }

    private String validDeploymentJson() { // GH-90000
        return "{" +
            "\"pipelineId\":\"pipeline-1\"," +
            "\"tenantId\":\"tenant-a\"," +
            "\"environment\":\"production\"" +
            "}";
    }
}
