package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Dashboard} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates Dashboard entity behavior, factory methods, and equality
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Dashboard Domain Model Tests")
class DashboardTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String DASHBOARD_NAME = "Security Overview";
    private static final String DESCRIPTION = "Main security metrics dashboard";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates dashboard with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            Dashboard dashboard = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME);

            // THEN
            assertThat(dashboard.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(dashboard.getName()).isEqualTo(DASHBOARD_NAME);
            assertThat(dashboard.isDefault()).isFalse();
            assertThat(dashboard.getCreatedAt()).isNotNull();
            assertThat(dashboard.getUpdatedAt()).isNotNull();
            assertThat(dashboard.getCreatedAt()).isEqualTo(dashboard.getUpdatedAt());
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() {
            assertThatThrownBy(() -> Dashboard.of(null, DASHBOARD_NAME))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null")
        void ofThrowsWhenNameNull() {
            assertThatThrownBy(() -> Dashboard.of(WORKSPACE_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name must not be null");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates dashboard with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            String widgetConfig = "{\"widgets\": []}";

            // WHEN
            Dashboard dashboard = Dashboard.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .description(DESCRIPTION)
                    .widgetConfig(widgetConfig)
                    .isDefault(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .version(1)
                    .build();

            // THEN
            assertThat(dashboard.getId()).isEqualTo(id);
            assertThat(dashboard.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(dashboard.getName()).isEqualTo(DASHBOARD_NAME);
            assertThat(dashboard.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(dashboard.getWidgetConfig()).isEqualTo(widgetConfig);
            assertThat(dashboard.isDefault()).isTrue();
            assertThat(dashboard.getCreatedAt()).isEqualTo(now);
            assertThat(dashboard.getUpdatedAt()).isEqualTo(now);
            assertThat(dashboard.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("builder defaults isDefault to false")
        void builderDefaultsIsDefaultToFalse() {
            Dashboard dashboard = Dashboard.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .build();

            assertThat(dashboard.isDefault()).isFalse();
        }

        @Test
        @DisplayName("builder defaults version to 0")
        void builderDefaultsVersionToZero() {
            Dashboard dashboard = Dashboard.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .build();

            assertThat(dashboard.getVersion()).isZero();
        }

        @Test
        @DisplayName("toBuilder creates modifiable copy")
        void toBuilderCreatesModifiableCopy() {
            // GIVEN
            Dashboard original = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME);

            // WHEN
            Dashboard copy = original.toBuilder()
                    .description(DESCRIPTION)
                    .isDefault(true)
                    .build();

            // THEN
            assertThat(copy.getWorkspaceId()).isEqualTo(original.getWorkspaceId());
            assertThat(copy.getName()).isEqualTo(original.getName());
            assertThat(copy.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(copy.isDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Dashboard dashboard1 = Dashboard.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .name("Dashboard 1")
                    .build();
            Dashboard dashboard2 = Dashboard.builder()
                    .id(id)
                    .workspaceId(UUID.randomUUID())  // Different workspace
                    .name("Dashboard 2")  // Different name
                    .build();

            // THEN
            assertThat(dashboard1).isEqualTo(dashboard2);
            assertThat(dashboard1.hashCode()).isEqualTo(dashboard2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            Dashboard dashboard1 = Dashboard.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .build();
            Dashboard dashboard2 = Dashboard.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .build();

            assertThat(dashboard1).isNotEqualTo(dashboard2);
        }

        @Test
        @DisplayName("equals returns false for null")
        void equalsReturnsFalseForNull() {
            Dashboard dashboard = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME);
            assertThat(dashboard).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals returns false for different type")
        void equalsReturnsFalseForDifferentType() {
            Dashboard dashboard = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME);
            assertThat(dashboard).isNotEqualTo("not a dashboard");
        }

        @Test
        @DisplayName("equals is reflexive")
        void equalsIsReflexive() {
            Dashboard dashboard = Dashboard.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .build();

            assertThat(dashboard).isEqualTo(dashboard);
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("setters update fields correctly")
        void settersUpdateFieldsCorrectly() {
            // GIVEN
            Dashboard dashboard = new Dashboard();
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            dashboard.setId(id);
            dashboard.setWorkspaceId(WORKSPACE_ID);
            dashboard.setName(DASHBOARD_NAME);
            dashboard.setDescription(DESCRIPTION);
            dashboard.setDefault(true);
            dashboard.setCreatedAt(now);
            dashboard.setUpdatedAt(now);
            dashboard.setVersion(5);

            // THEN
            assertThat(dashboard.getId()).isEqualTo(id);
            assertThat(dashboard.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(dashboard.getName()).isEqualTo(DASHBOARD_NAME);
            assertThat(dashboard.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(dashboard.isDefault()).isTrue();
            assertThat(dashboard.getCreatedAt()).isEqualTo(now);
            assertThat(dashboard.getUpdatedAt()).isEqualTo(now);
            assertThat(dashboard.getVersion()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("handles empty widget config")
        void handlesEmptyWidgetConfig() {
            Dashboard dashboard = Dashboard.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .widgetConfig("")
                    .build();

            assertThat(dashboard.getWidgetConfig()).isEmpty();
        }

        @Test
        @DisplayName("handles null widget config")
        void handlesNullWidgetConfig() {
            Dashboard dashboard = Dashboard.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .widgetConfig(null)
                    .build();

            assertThat(dashboard.getWidgetConfig()).isNull();
        }

        @Test
        @DisplayName("handles complex JSON widget config")
        void handlesComplexJsonWidgetConfig() {
            String complexConfig = """
                    {
                        "widgets": [
                            {"type": "chart", "data": {"series": [1, 2, 3]}},
                            {"type": "table", "columns": ["a", "b"]}
                        ],
                        "layout": {"columns": 2}
                    }
                    """;

            Dashboard dashboard = Dashboard.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(DASHBOARD_NAME)
                    .widgetConfig(complexConfig)
                    .build();

            assertThat(dashboard.getWidgetConfig()).isEqualTo(complexConfig);
        }
    }
}
