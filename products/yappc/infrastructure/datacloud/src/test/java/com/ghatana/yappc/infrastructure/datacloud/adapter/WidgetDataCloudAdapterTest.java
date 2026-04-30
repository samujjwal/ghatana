package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.domain.model.Widget;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for WidgetDataCloudAdapter.
 */
@DisplayName("WidgetDataCloudAdapter Tests")
/**
 * @doc.type class
 * @doc.purpose Handles widget data cloud adapter test operations
 * @doc.layer platform
 * @doc.pattern Test
 */
class WidgetDataCloudAdapterTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private YappcEntityMapper mapper;
    private WidgetDataCloudAdapter widgetAdapter;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000

        ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        mapper = new YappcEntityMapper(objectMapper); // GH-90000

        widgetAdapter = new WidgetDataCloudAdapter(client, mapper); // GH-90000
    }

    @Test
    @DisplayName("Should find widgets by dashboard ID")
    void shouldFindByDashboardId() { // GH-90000
        UUID dashboardId = UUID.randomUUID(); // GH-90000

        when(client.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000

        List<Widget> result = runPromise(() -> widgetAdapter.findByDashboardId(dashboardId)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        verify(client).query(anyString(), anyString(), any(DataCloudClient.Query.class)); // GH-90000
    }

    @Test
    @DisplayName("Should find widgets by workspace ID")
    void shouldFindByWorkspaceId() { // GH-90000
        UUID workspaceId = UUID.randomUUID(); // GH-90000

        when(client.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000

        List<Widget> result = runPromise(() -> widgetAdapter.findByWorkspaceId(workspaceId)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
    }
}
