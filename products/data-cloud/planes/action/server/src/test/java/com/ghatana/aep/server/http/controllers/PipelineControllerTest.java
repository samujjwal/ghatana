package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.core.pipeline.NaturalLanguagePipelineService;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PipelineController")
@ExtendWith(MockitoExtension.class)
class PipelineControllerTest extends EventloopTestBase {

    @Mock
    private PipelineRepository pipelineRepository;

    private PipelineController controller;

    @BeforeEach
    void setUp() {
        controller = new PipelineController(pipelineRepository, new ObjectMapper(), (NaturalLanguagePipelineService) null);
    }

    @Test
    @DisplayName("create pipeline rejects unauthenticated principal")
    void createPipelineRejectsUnauthenticatedPrincipal() throws Exception {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/pipelines")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-a")
            .withBody(ByteBuf.wrapForReading("{\"name\":\"demo\"}".getBytes(StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> controller.handle(request, ""));

        assertThat(response.getCode()).isEqualTo(403);
        verify(pipelineRepository, never()).save(any());
    }

    @Test
    @DisplayName("create pipeline accepts operator principal")
    void createPipelineAcceptsOperatorPrincipal() throws Exception {
        lenient().when(pipelineRepository.save(any(PipelineRegistration.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(0)));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/pipelines")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-a")
            .withBody(ByteBuf.wrapForReading("{\"name\":\"demo\"}".getBytes(StandardCharsets.UTF_8)))
            .build();
        request.attach(
            AepAuthFilter.JWT_PAYLOAD_ATTACHMENT,
            new AepAuthFilter.JwtPayload(
                "alice",
                "issuer",
                1L,
                1L,
                List.of("operator"),
                List.of("pipeline:create"),
                "tenant-a"
            )
        );

        HttpResponse response = runPromise(() -> controller.handle(request, ""));
        String body = response.getBody() != null
            ? response.getBody().getString(StandardCharsets.UTF_8)
            : "";

        assertThat(response.getCode())
            .withFailMessage("Expected 201 but got %d. Body: %s", response.getCode(), body)
            .isEqualTo(201);
    }

    @Test
    @DisplayName("list pipelines remains readable without write permission")
    void listPipelinesDoesNotRequireWritePermission() throws Exception {
        PipelineRegistration registration = PipelineRegistration.builder()
            .id("pipeline-1")
            .tenantId(TenantId.of("tenant-a"))
            .name("demo")
            .description("desc")
            .active(true)
            .version(1)
            .config("{}")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("tester")
            .updatedBy("tester")
            .build();
        when(pipelineRepository.findByTenantId("tenant-a")).thenReturn(Promise.of(List.of(registration)));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/pipelines")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-a")
            .build();

        HttpResponse response = runPromise(() -> controller.handle(request, ""));

        assertThat(response.getCode()).isEqualTo(200);
        verify(pipelineRepository).findByTenantId("tenant-a");
    }
}
