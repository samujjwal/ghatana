package com.ghatana.products.yappc.infrastructure.persistence;

import com.ghatana.products.yappc.domain.model.Widget;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JpaWidgetRepository.
 *
 * Tests validate:
 * - Widget CRUD operations
 * - Type-based filtering
 * - Batch position updates
 * - Configuration storage
 * - Visibility queries
 * - Dashboard scoping
 *
 * @see JpaWidgetRepository
 */
@DisplayName("JPA Widget Repository Tests")
/**
 * @doc.type class
 * @doc.purpose Handles jpa widget repository test operations
 * @doc.layer platform
 * @doc.pattern Test
 */
class JpaWidgetRepositoryTest extends EventloopTestBase {

    private JpaWidgetRepository repository;
    private EntityManager entityManager;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        metricsCollector = NoopMetricsCollector.getInstance();
        repository = new JpaWidgetRepository(entityManager, metricsCollector);
    }

    /**
     * Verifies saving a widget with configuration.
     *
     * GIVEN: Widget with JSON config
     * WHEN: save() is called
     * THEN: Widget and config are persisted
     */
    @Test
    @DisplayName("Should persist widget with configuration")
    void shouldPersistWidgetWithConfiguration() {
        // GIVEN: Widget with config
        Widget widget = new Widget();
        widget.setTenantId("tenant-123");
        widget.setDashboardId(UUID.randomUUID());
        widget.setType("KPI_CARD");
        widget.setTitle("Total Incidents");
        widget.setConfig("{\"query\": \"SELECT COUNT(*)\"}");
        widget.setX(0);
        widget.setY(0);
        widget.setWidth(4);
        widget.setHeight(2);

        when(entityManager.merge(any(Widget.class)))
            .thenReturn(widget);

        // WHEN: Save widget
        Widget saved = runPromise(() -> repository.save(widget));

        // THEN: Widget is persisted
        assertThat(saved).isNotNull();
        assertThat(saved.getConfig()).contains("SELECT COUNT(*)");
        verify(entityManager).merge(any(Widget.class));
    }

    /**
     * Verifies finding widgets by type.
     *
     * GIVEN: Dashboard with KPI_CARD and LINE_CHART widgets
     * WHEN: findByType() is called with KPI_CARD
     * THEN: Only KPI_CARD widgets returned
     */
    @Test
    @DisplayName("Should find widgets by type")
    void shouldFindWidgetsByType() {
        // GIVEN: Widgets of different types
        UUID dashboardId = UUID.randomUUID();

        Widget kpiWidget = new Widget();
        kpiWidget.setType("KPI_CARD");

        TypedQuery<Widget> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Widget.class)))
            .thenReturn(query);
        when(query.setParameter(anyString(), any()))
            .thenReturn(query);
        when(query.getResultList())
            .thenReturn(List.of(kpiWidget));

        // WHEN: Find KPI widgets
        List<Widget> results = runPromise(() ->
            repository.findByType("tenant-123", dashboardId, "KPI_CARD"));

        // THEN: Only KPI widgets returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo("KPI_CARD");
    }

    /**
     * Verifies batch position update.
     *
     * GIVEN: Multiple widgets with new positions
     * WHEN: updatePositions() is called
     * THEN: All widget positions are updated
     */
    @Test
    @DisplayName("Should update widget positions in batch")
    void shouldUpdateWidgetPositionsInBatch() {
        // GIVEN: Position updates
        UUID widget1Id = UUID.randomUUID();
        UUID widget2Id = UUID.randomUUID();

        Widget widget1 = new Widget();
        widget1.setId(widget1Id);
        
        Widget widget2 = new Widget();
        widget2.setId(widget2Id);

        when(entityManager.find(Widget.class, widget1Id))
            .thenReturn(widget1);
        when(entityManager.find(Widget.class, widget2Id))
            .thenReturn(widget2);

        // WHEN: Update positions
        List<WidgetPosition> positions = List.of(
            new WidgetPosition(widget1Id, 0, 0, 4, 2),
            new WidgetPosition(widget2Id, 4, 0, 8, 4)
        );

        int updated = runPromise(() ->
            repository.updatePositions("tenant-123", positions));

        // THEN: Both widgets updated
        assertThat(updated).isEqualTo(2);
        verify(entityManager).find(Widget.class, widget1Id);
        verify(entityManager).find(Widget.class, widget2Id);
    }

    /**
     * Verifies finding visible widgets only.
     *
     * GIVEN: Dashboard with visible and hidden widgets
     * WHEN: findVisible() is called
     * THEN: Only visible widgets returned
     */
    @Test
    @DisplayName("Should find only visible widgets")
    void shouldFindOnlyVisibleWidgets() {
        // GIVEN: Visible widgets
        UUID dashboardId = UUID.randomUUID();

        Widget visible1 = new Widget();
        visible1.setVisible(true);

        Widget visible2 = new Widget();
        visible2.setVisible(true);

        TypedQuery<Widget> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Widget.class)))
            .thenReturn(query);
        when(query.setParameter(anyString(), any()))
            .thenReturn(query);
        when(query.getResultList())
            .thenReturn(List.of(visible1, visible2));

        // WHEN: Find visible widgets
        List<Widget> results = runPromise(() ->
            repository.findVisible("tenant-123", dashboardId));

        // THEN: Only visible widgets returned
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(Widget::isVisible);
    }

    /**
     * Verifies deleting a widget.
     *
     * GIVEN: Existing widget
     * WHEN: delete() is called
     * THEN: Widget is removed
     */
    @Test
    @DisplayName("Should delete widget")
    void shouldDeleteWidget() {
        // GIVEN: Widget to delete
        UUID widgetId = UUID.randomUUID();

        Widget widget = new Widget();
        widget.setId(widgetId);

        when(entityManager.find(Widget.class, widgetId))
            .thenReturn(widget);

        // WHEN: Delete widget
        runPromise(() -> repository.delete("tenant-123", widgetId));

        // THEN: Widget is removed
        verify(entityManager).remove(widget);
    }
}

class WidgetPosition {
    private final UUID widgetId;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public WidgetPosition(UUID widgetId, int x, int y, int width, int height) {
        this.widgetId = widgetId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public UUID getWidgetId() { return widgetId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
