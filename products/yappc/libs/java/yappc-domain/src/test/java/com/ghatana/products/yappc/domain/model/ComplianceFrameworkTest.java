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
@DisplayName("ComplianceFramework Domain Model Tests")
class ComplianceFrameworkTest {

    private static final String FRAMEWORK_NAME = "SOC2";
    private static final String DISPLAY_NAME = "Service Organization Control 2";
    private static final String DESCRIPTION = "SOC 2 compliance framework for service organizations";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates framework with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            ComplianceFramework framework = ComplianceFramework.of(FRAMEWORK_NAME);

            // THEN
            assertThat(framework.getName()).isEqualTo(FRAMEWORK_NAME);
            assertThat(framework.isBuiltin()).isFalse();
            assertThat(framework.isEnabledByDefault()).isFalse();
            assertThat(framework.getControlCount()).isZero();
            assertThat(framework.getCreatedAt()).isNotNull();
            assertThat(framework.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null")
        void ofThrowsWhenNameNull() {
            assertThatThrownBy(() -> ComplianceFramework.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name must not be null");
        }

        @Test
        @DisplayName("builtin() creates built-in framework with all fields")
        void builtinCreatesBuiltInFramework() {
            // WHEN
            ComplianceFramework framework = ComplianceFramework.builtin(FRAMEWORK_NAME, DISPLAY_NAME, DESCRIPTION);

            // THEN
            assertThat(framework.getName()).isEqualTo(FRAMEWORK_NAME);
            assertThat(framework.getDisplayName()).isEqualTo(DISPLAY_NAME);
            assertThat(framework.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(framework.isBuiltin()).isTrue();
            assertThat(framework.getCreatedAt()).isNotNull();
            assertThat(framework.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("builtin() throws NullPointerException when name is null")
        void builtinThrowsWhenNameNull() {
            assertThatThrownBy(() -> ComplianceFramework.builtin(null, DISPLAY_NAME, DESCRIPTION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name must not be null");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates framework with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            ComplianceFramework framework = ComplianceFramework.builder()
                    .id(id)
                    .name(FRAMEWORK_NAME)
                    .displayName(DISPLAY_NAME)
                    .description(DESCRIPTION)
                    .frameworkVersion("2.0")
                    .category("SECURITY")
                    .documentationUrl("https://docs.example.com/soc2")
                    .enabledByDefault(true)
                    .isBuiltin(true)
                    .controlCount(150)
                    .createdAt(now)
                    .updatedAt(now)
                    .version(2)
                    .build();

            // THEN
            assertThat(framework.getId()).isEqualTo(id);
            assertThat(framework.getName()).isEqualTo(FRAMEWORK_NAME);
            assertThat(framework.getDisplayName()).isEqualTo(DISPLAY_NAME);
            assertThat(framework.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(framework.getFrameworkVersion()).isEqualTo("2.0");
            assertThat(framework.getCategory()).isEqualTo("SECURITY");
            assertThat(framework.getDocumentationUrl()).isEqualTo("https://docs.example.com/soc2");
            assertThat(framework.isEnabledByDefault()).isTrue();
            assertThat(framework.isBuiltin()).isTrue();
            assertThat(framework.getControlCount()).isEqualTo(150);
            assertThat(framework.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("builder defaults enabledByDefault to false")
        void builderDefaultsEnabledByDefaultToFalse() {
            ComplianceFramework framework = ComplianceFramework.builder()
                    .name(FRAMEWORK_NAME)
                    .build();

            assertThat(framework.isEnabledByDefault()).isFalse();
        }

        @Test
        @DisplayName("builder defaults isBuiltin to false")
        void builderDefaultsIsBuiltinToFalse() {
            ComplianceFramework framework = ComplianceFramework.builder()
                    .name(FRAMEWORK_NAME)
                    .build();

            assertThat(framework.isBuiltin()).isFalse();
        }

        @Test
        @DisplayName("builder defaults controlCount to 0")
        void builderDefaultsControlCountToZero() {
            ComplianceFramework framework = ComplianceFramework.builder()
                    .name(FRAMEWORK_NAME)
                    .build();

            assertThat(framework.getControlCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            ComplianceFramework framework1 = ComplianceFramework.builder().id(id).name("SOC2").build();
            ComplianceFramework framework2 = ComplianceFramework.builder().id(id).name("PCI-DSS").build();

            assertThat(framework1).isEqualTo(framework2);
            assertThat(framework1.hashCode()).isEqualTo(framework2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            ComplianceFramework framework1 = ComplianceFramework.builder().id(UUID.randomUUID()).build();
            ComplianceFramework framework2 = ComplianceFramework.builder().id(UUID.randomUUID()).build();

            assertThat(framework1).isNotEqualTo(framework2);
        }
    }

    @Nested
    @DisplayName("Framework Category Tests")
    class CategoryTests {

        @Test
        @DisplayName("can set various framework categories")
        void canSetVariousCategories() {
            String[] categories = {"SECURITY", "PRIVACY", "INDUSTRY", "GOVERNMENT", "FINANCIAL"};

            for (String category : categories) {
                ComplianceFramework framework = ComplianceFramework.builder()
                        .name(FRAMEWORK_NAME)
                        .category(category)
                        .build();

                assertThat(framework.getCategory()).isEqualTo(category);
            }
        }
    }

    @Nested
    @DisplayName("Common Frameworks Tests")
    class CommonFrameworksTests {

        @Test
        @DisplayName("can create SOC 2 framework")
        void canCreateSoc2Framework() {
            ComplianceFramework soc2 = ComplianceFramework.builtin(
                    "SOC2",
                    "Service Organization Control 2",
                    "SOC 2 Type II compliance for service organizations"
            );

            assertThat(soc2.getName()).isEqualTo("SOC2");
            assertThat(soc2.isBuiltin()).isTrue();
        }

        @Test
        @DisplayName("can create PCI-DSS framework")
        void canCreatePciDssFramework() {
            ComplianceFramework pciDss = ComplianceFramework.builtin(
                    "PCI-DSS",
                    "Payment Card Industry Data Security Standard",
                    "Security standard for organizations handling credit card data"
            );

            assertThat(pciDss.getName()).isEqualTo("PCI-DSS");
            assertThat(pciDss.isBuiltin()).isTrue();
        }

        @Test
        @DisplayName("can create HIPAA framework")
        void canCreateHipaaFramework() {
            ComplianceFramework hipaa = ComplianceFramework.builtin(
                    "HIPAA",
                    "Health Insurance Portability and Accountability Act",
                    "Healthcare data protection standard"
            );

            assertThat(hipaa.getName()).isEqualTo("HIPAA");
            assertThat(hipaa.isBuiltin()).isTrue();
        }

        @Test
        @DisplayName("can create GDPR framework")
        void canCreateGdprFramework() {
            ComplianceFramework gdpr = ComplianceFramework.builtin(
                    "GDPR",
                    "General Data Protection Regulation",
                    "EU data protection and privacy regulation"
            );

            assertThat(gdpr.getName()).isEqualTo("GDPR");
            assertThat(gdpr.isBuiltin()).isTrue();
        }
    }

    @Nested
    @DisplayName("Control Count Tests")
    class ControlCountTests {

        @Test
        @DisplayName("can update control count")
        void canUpdateControlCount() {
            ComplianceFramework framework = ComplianceFramework.of(FRAMEWORK_NAME);
            framework.setControlCount(125);

            assertThat(framework.getControlCount()).isEqualTo(125);
        }
    }
}
