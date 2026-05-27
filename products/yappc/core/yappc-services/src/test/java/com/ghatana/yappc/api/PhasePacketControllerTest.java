package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.phase.PhasePacketService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies phase packet request correlation ID extraction
 * @doc.layer api
 * @doc.pattern Test
 */
@DisplayName("PhasePacketController")
@ExtendWith(MockitoExtension.class)
class PhasePacketControllerTest extends EventloopTestBase {

    @Mock
    private PhasePacketService phasePacketService;

    @Test
    @DisplayName("GET phase packet propagates X-Correlation-ID header when query correlation is absent")
    void getPhasePacketWithQueryUsesCorrelationHeader() {
        when(phasePacketService.buildPhasePacket(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Promise.of(null));

        HttpRequest request = HttpRequest.get(
                        "http://localhost/api/v1/phase/packet?phase=shape&projectId=project-1&workspaceId=workspace-1")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-header-1")
                .build();
        request.attach(Principal.class, new Principal("designer-1", List.of("editor"), "tenant-1"));

        runPromise(() -> controller().getPhasePacketWithQuery(request));

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(phasePacketService).buildPhasePacket(
                anyString(), anyString(), anyString(), any(), correlationId.capture());
        assertThat(correlationId.getValue()).isEqualTo("corr-header-1");
    }

    @Test
    @DisplayName("GET phase packet query correlation ID takes precedence over header")
    void getPhasePacketWithQueryPrefersQueryCorrelationId() {
        when(phasePacketService.buildPhasePacket(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Promise.of(null));

        HttpRequest request = HttpRequest.get(
                        "http://localhost/api/v1/phase/packet?phase=shape&projectId=project-1&workspaceId=workspace-1&correlationId=corr-query-1")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-header-1")
                .build();
        request.attach(Principal.class, new Principal("designer-1", List.of("editor"), "tenant-1"));

        runPromise(() -> controller().getPhasePacketWithQuery(request));

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(phasePacketService).buildPhasePacket(
                anyString(), anyString(), anyString(), any(), correlationId.capture());
        assertThat(correlationId.getValue()).isEqualTo("corr-query-1");
    }

    private PhasePacketController controller() {
        return new PhasePacketController(new ObjectMapper(), phasePacketService);
    }
}
