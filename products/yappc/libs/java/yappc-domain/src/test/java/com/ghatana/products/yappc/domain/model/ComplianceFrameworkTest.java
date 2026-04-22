package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ComplianceFramework} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates ComplianceFramework entity behavior, factory methods, and built-in frameworks
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ComplianceFramework Domain Model Tests [GH-90000]")
class ComplianceFrameworkTest {

    private static final String FRAMEWORK_NAME = "SOC2";
    private static final String DISPLAY_NAME = "Service Organization Control 2";
    private static final String DESCRIPTION = "SOC 2 compliance framework for service organizations";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates framework with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            ComplianceFramework framework = ComplianceFramework.of(FRAMEWORK_NAME); // GH-90000

            // THEN
            assertThat(framework.getName()).isEqualTo(FRAMEWORK_NAME); // GH-90000
            assertThat(framework.isBuiltin()).isFalse(); // GH-90000
            assertThat(framework.isEnabledByDefault()).isFalse(); // GH-90000
            assertThat(framework.getControlCount()).isZero(); // GH-90000
            assertThat(framework.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(framework.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null [GH-90000]")
        void ofThrowsWhenNameNull() { // GH-90000
            assertThatThrownBy(() -> ComplianceFramework.of(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name must not be null [GH-90000]");
        }

        @Test
        @DisplayName("builtin() creates built-in framework with all fields [GH-90000]")
        void builtinCreatesBuiltInFramework() { // GH-90000
            // WHEN
            ComplianceFramework framework = ComplianceFramework.builtin(FRAMEWORK_NAME, DISPLAY_NAME, DESCRIPTION); // GH-90000

            // THEN
            assertThat(framework.getName()).isEqualTo(FRAMEWORK_NAME); // GH-90000
            assertThat(framework.getDisplayName()).isEqualTo(DISPLAY_NAME); // GH-90000
            assertThat(framework.getDescription()).isEqualTo(DESCRIPTION); // GH-90000
            assertThat(framework.isBuiltin()).isTrue(); // GH-90000
            assertThat(framework.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(framework.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("builtin() throws NullPointerException when name is null [GH-90000]")
        void builtinThrowsWhenNameNull() { // GH-90000
            assertThatThrownBy(() -> ComplianceFramework.builtin(null, DISPLAY_NAME, DESCRIPTION)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates framework with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            ComplianceFramework framework = ComplianceFramework.builder() // GH-90000
                    .id(id) // GH-90000
                    .name(FRAMEWORK_NAME) // GH-90000
                    .displayName(DISPLAY_NAME) // GH-90000
                    .description(DESCRIPTION) // GH-90000
                    .frameworkVersion("2.0 [GH-90000]")
                    .category("SECURITY [GH-90000]")
                    .documentationUrl("https://docs.example.com/soc2 [GH-90000]")
                    .enabledByDefault(true) // GH-90000
                    .isBuiltin(true) // GH-90000
                    .controlCount(150) // GH-90000
                    .createdAt(now) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(2) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(framework.getId()).isEqualTo(id); // GH-90000
            assertThat(framework.getName()).isEqualTo(FRAMEWORK_NAME); // GH-90000
            assertThat(framework.getDisplayName()).isEqualTo(DISPLAY_NAME); // GH-90000
            assertThat(framework.getDescription()).isEqualTo(DESCRIPTION); // GH-90000
            assertThat(framework.getFrameworkVersion()).isEqualTo("2.0 [GH-90000]");
            assertThat(framework.getCategory()).isEqualTo("SECURITY [GH-90000]");
            assertThat(framework.getDocumentationUrl()).isEqualTo("https://docs.example.com/soc2 [GH-90000]");
            assertThat(framework.isEnabledByDefault()).isTrue(); // GH-90000
            assertThat(framework.isBuiltin()).isTrue(); // GH-90000
            assertThat(framework.getControlCount()).isEqualTo(150); // GH-90000
            assertThat(framework.getVersion()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("builder defaults enabledByDefault to false [GH-90000]")
        void builderDefaultsEnabledByDefaultToFalse() { // GH-90000
            ComplianceFramework framework = ComplianceFramework.builder() // GH-90000
                    .name(FRAMEWORK_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(framework.isEnabledByDefault()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults isBuiltin to false [GH-90000]")
        void builderDefaultsIsBuiltinToFalse() { // GH-90000
            ComplianceFramework framework = ComplianceFramework.builder() // GH-90000
                    .name(FRAMEWORK_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(framework.isBuiltin()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults controlCount to 0 [GH-90000]")
        void builderDefaultsControlCountToZero() { // GH-90000
            ComplianceFramework framework = ComplianceFramework.builder() // GH-90000
                    .name(FRAMEWORK_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(framework.getControlCount()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            ComplianceFramework framework1 = ComplianceFramework.builder().id(id).name("SOC2 [GH-90000]").build();
            ComplianceFramework framework2 = ComplianceFramework.builder().id(id).name("PCI-DSS [GH-90000]").build();

            assertThat(framework1).isEqualTo(framework2); // GH-90000
            assertThat(framework1.hashCode()).isEqualTo(framework2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            ComplianceFramework framework1 = ComplianceFramework.builder().id(UUID.randomUUID()).build(); // GH-90000
            ComplianceFramework framework2 = ComplianceFramework.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(framework1).isNotEqualTo(framework2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Framework Category Tests [GH-90000]")
    class CategoryTests {

        @Test
        @DisplayName("can set various framework categories [GH-90000]")
        void canSetVariousCategories() { // GH-90000
            String[] categories = {"SECURITY", "PRIVACY", "INDUSTRY", "GOVERNMENT", "FINANCIAL"};

            for (String category : categories) { // GH-90000
                ComplianceFramework framework = ComplianceFramework.builder() // GH-90000
                        .name(FRAMEWORK_NAME) // GH-90000
                        .category(category) // GH-90000
                        .build(); // GH-90000

                assertThat(framework.getCategory()).isEqualTo(category); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Common Frameworks Tests [GH-90000]")
    class CommonFrameworksTests {

        @Test
        @DisplayName("can create SOC 2 framework [GH-90000]")
        void canCreateSoc2Framework() { // GH-90000
            ComplianceFramework soc2 = ComplianceFramework.builtin( // GH-90000
                    "SOC2",
                    "Service Organization Control 2",
                    "SOC 2 Type II compliance for service organizations"
            );

            assertThat(soc2.getName()).isEqualTo("SOC2 [GH-90000]");
            assertThat(soc2.isBuiltin()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("can create PCI-DSS framework [GH-90000]")
        void canCreatePciDssFramework() { // GH-90000
            ComplianceFramework pciDss = ComplianceFramework.builtin( // GH-90000
                    "PCI-DSS",
                    "Payment Card Industry Data Security Standard",
                    "Security standard for organizations handling credit card data"
            );

            assertThat(pciDss.getName()).isEqualTo("PCI-DSS [GH-90000]");
            assertThat(pciDss.isBuiltin()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("can create HIPAA framework [GH-90000]")
        void canCreateHipaaFramework() { // GH-90000
            ComplianceFramework hipaa = ComplianceFramework.builtin( // GH-90000
                    "HIPAA",
                    "Health Insurance Portability and Accountability Act",
                    "Healthcare data protection standard"
            );

            assertThat(hipaa.getName()).isEqualTo("HIPAA [GH-90000]");
            assertThat(hipaa.isBuiltin()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("can create GDPR framework [GH-90000]")
        void canCreateGdprFramework() { // GH-90000
            ComplianceFramework gdpr = ComplianceFramework.builtin( // GH-90000
                    "GDPR",
                    "General Data Protection Regulation",
                    "EU data protection and privacy regulation"
            );

            assertThat(gdpr.getName()).isEqualTo("GDPR [GH-90000]");
            assertThat(gdpr.isBuiltin()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Control Count Tests [GH-90000]")
    class ControlCountTests {

        @Test
        @DisplayName("can update control count [GH-90000]")
        void canUpdateControlCount() { // GH-90000
            ComplianceFramework framework = ComplianceFramework.of(FRAMEWORK_NAME); // GH-90000
            framework.setControlCount(125); // GH-90000

            assertThat(framework.getControlCount()).isEqualTo(125); // GH-90000
        }
    }
}
