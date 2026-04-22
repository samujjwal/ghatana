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
@DisplayName("Dashboard Domain Model Tests [GH-90000]")
class DashboardTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final String DASHBOARD_NAME = "Security Overview";
    private static final String DESCRIPTION = "Main security metrics dashboard";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates dashboard with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            Dashboard dashboard = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME); // GH-90000

            // THEN
            assertThat(dashboard.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(dashboard.getName()).isEqualTo(DASHBOARD_NAME); // GH-90000
            assertThat(dashboard.isDefault()).isFalse(); // GH-90000
            assertThat(dashboard.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(dashboard.getUpdatedAt()).isNotNull(); // GH-90000
            assertThat(dashboard.getCreatedAt()).isEqualTo(dashboard.getUpdatedAt()); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null [GH-90000]")
        void ofThrowsWhenWorkspaceIdNull() { // GH-90000
            assertThatThrownBy(() -> Dashboard.of(null, DASHBOARD_NAME)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null [GH-90000]")
        void ofThrowsWhenNameNull() { // GH-90000
            assertThatThrownBy(() -> Dashboard.of(WORKSPACE_ID, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates dashboard with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000
            String widgetConfig = "{\"widgets\": []}";

            // WHEN
            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .description(DESCRIPTION) // GH-90000
                    .widgetConfig(widgetConfig) // GH-90000
                    .isDefault(true) // GH-90000
                    .createdAt(now) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(1) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(dashboard.getId()).isEqualTo(id); // GH-90000
            assertThat(dashboard.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(dashboard.getName()).isEqualTo(DASHBOARD_NAME); // GH-90000
            assertThat(dashboard.getDescription()).isEqualTo(DESCRIPTION); // GH-90000
            assertThat(dashboard.getWidgetConfig()).isEqualTo(widgetConfig); // GH-90000
            assertThat(dashboard.isDefault()).isTrue(); // GH-90000
            assertThat(dashboard.getCreatedAt()).isEqualTo(now); // GH-90000
            assertThat(dashboard.getUpdatedAt()).isEqualTo(now); // GH-90000
            assertThat(dashboard.getVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("builder defaults isDefault to false [GH-90000]")
        void builderDefaultsIsDefaultToFalse() { // GH-90000
            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(dashboard.isDefault()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults version to 0 [GH-90000]")
        void builderDefaultsVersionToZero() { // GH-90000
            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(dashboard.getVersion()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("toBuilder creates modifiable copy [GH-90000]")
        void toBuilderCreatesModifiableCopy() { // GH-90000
            // GIVEN
            Dashboard original = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME); // GH-90000

            // WHEN
            Dashboard copy = original.toBuilder() // GH-90000
                    .description(DESCRIPTION) // GH-90000
                    .isDefault(true) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(copy.getWorkspaceId()).isEqualTo(original.getWorkspaceId()); // GH-90000
            assertThat(copy.getName()).isEqualTo(original.getName()); // GH-90000
            assertThat(copy.getDescription()).isEqualTo(DESCRIPTION); // GH-90000
            assertThat(copy.isDefault()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Dashboard dashboard1 = Dashboard.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name("Dashboard 1 [GH-90000]")
                    .build(); // GH-90000
            Dashboard dashboard2 = Dashboard.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(UUID.randomUUID())  // Different workspace // GH-90000
                    .name("Dashboard 2 [GH-90000]")  // Different name
                    .build(); // GH-90000

            // THEN
            assertThat(dashboard1).isEqualTo(dashboard2); // GH-90000
            assertThat(dashboard1.hashCode()).isEqualTo(dashboard2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            Dashboard dashboard1 = Dashboard.builder() // GH-90000
                    .id(UUID.randomUUID()) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .build(); // GH-90000
            Dashboard dashboard2 = Dashboard.builder() // GH-90000
                    .id(UUID.randomUUID()) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(dashboard1).isNotEqualTo(dashboard2); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for null [GH-90000]")
        void equalsReturnsFalseForNull() { // GH-90000
            Dashboard dashboard = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME); // GH-90000
            assertThat(dashboard).isNotEqualTo(null); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different type [GH-90000]")
        void equalsReturnsFalseForDifferentType() { // GH-90000
            Dashboard dashboard = Dashboard.of(WORKSPACE_ID, DASHBOARD_NAME); // GH-90000
            assertThat(dashboard).isNotEqualTo("not a dashboard [GH-90000]");
        }

        @Test
        @DisplayName("equals is reflexive [GH-90000]")
        void equalsIsReflexive() { // GH-90000
            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .id(UUID.randomUUID()) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(dashboard).isEqualTo(dashboard); // GH-90000
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests [GH-90000]")
    class GetterSetterTests {

        @Test
        @DisplayName("setters update fields correctly [GH-90000]")
        void settersUpdateFieldsCorrectly() { // GH-90000
            // GIVEN
            Dashboard dashboard = new Dashboard(); // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            dashboard.setId(id); // GH-90000
            dashboard.setWorkspaceId(WORKSPACE_ID); // GH-90000
            dashboard.setName(DASHBOARD_NAME); // GH-90000
            dashboard.setDescription(DESCRIPTION); // GH-90000
            dashboard.setDefault(true); // GH-90000
            dashboard.setCreatedAt(now); // GH-90000
            dashboard.setUpdatedAt(now); // GH-90000
            dashboard.setVersion(5); // GH-90000

            // THEN
            assertThat(dashboard.getId()).isEqualTo(id); // GH-90000
            assertThat(dashboard.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(dashboard.getName()).isEqualTo(DASHBOARD_NAME); // GH-90000
            assertThat(dashboard.getDescription()).isEqualTo(DESCRIPTION); // GH-90000
            assertThat(dashboard.isDefault()).isTrue(); // GH-90000
            assertThat(dashboard.getCreatedAt()).isEqualTo(now); // GH-90000
            assertThat(dashboard.getUpdatedAt()).isEqualTo(now); // GH-90000
            assertThat(dashboard.getVersion()).isEqualTo(5); // GH-90000
        }
    }

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCases {

        @Test
        @DisplayName("handles empty widget config [GH-90000]")
        void handlesEmptyWidgetConfig() { // GH-90000
            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .widgetConfig(" [GH-90000]")
                    .build(); // GH-90000

            assertThat(dashboard.getWidgetConfig()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("handles null widget config [GH-90000]")
        void handlesNullWidgetConfig() { // GH-90000
            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .widgetConfig(null) // GH-90000
                    .build(); // GH-90000

            assertThat(dashboard.getWidgetConfig()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("handles complex JSON widget config [GH-90000]")
        void handlesComplexJsonWidgetConfig() { // GH-90000
            String complexConfig = """
                    {
                        "widgets": [
                            {"type": "chart", "data": {"series": [1, 2, 3]}},
                            {"type": "table", "columns": ["a", "b"]}
                        ],
                        "layout": {"columns": 2}
                    }
                    """;

            Dashboard dashboard = Dashboard.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(DASHBOARD_NAME) // GH-90000
                    .widgetConfig(complexConfig) // GH-90000
                    .build(); // GH-90000

            assertThat(dashboard.getWidgetConfig()).isEqualTo(complexConfig); // GH-90000
        }
    }
}
