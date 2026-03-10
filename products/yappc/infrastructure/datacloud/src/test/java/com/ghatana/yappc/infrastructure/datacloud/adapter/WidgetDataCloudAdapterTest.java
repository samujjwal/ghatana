package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.products.yappc.domain.model.Widget;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
    private EntityRepository entityRepository;

    private YappcEntityMapper mapper;
    private WidgetDataCloudAdapter widgetAdapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mapper = new YappcEntityMapper(objectMapper);

        widgetAdapter = new WidgetDataCloudAdapter(entityRepository, mapper);
    }

    @Test
    @DisplayName("Should find widgets by dashboard ID")
    void shouldFindByDashboardId() {
        UUID dashboardId = UUID.randomUUID();

        when(entityRepository.findAll(anyString(), anyString(), any(), any(), anyInt(), anyInt()))
            .thenReturn(Promise.of(List.of()));

        List<Widget> result = runPromise(() -> widgetAdapter.findByDashboardId(dashboardId));

        assertThat(result).isNotNull();
        verify(entityRepository).findAll(anyString(), anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should find widgets by workspace ID")
    void shouldFindByWorkspaceId() {
        UUID workspaceId = UUID.randomUUID();

        when(entityRepository.findAll(anyString(), anyString(), any(), any(), anyInt(), anyInt()))
            .thenReturn(Promise.of(List.of()));

        List<Widget> result = runPromise(() -> widgetAdapter.findByWorkspaceId(workspaceId));

        assertThat(result).isNotNull();
    }
}
