package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.CloudProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CloudResource} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates CloudResource entity behavior, sync tracking, and security properties
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("CloudResource Domain Model Tests [GH-90000]")
class CloudResourceTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final UUID CLOUD_ACCOUNT_ID = UUID.randomUUID(); // GH-90000
    private static final CloudProvider PROVIDER = CloudProvider.AWS;
    private static final String RESOURCE_TYPE = "ec2:instance";
    private static final String IDENTIFIER = "arn:aws:ec2:us-east-1:123456789012:instance/i-1234567890abcdef0";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates resource with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER); // GH-90000

            // THEN
            assertThat(resource.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(resource.getCloudAccountId()).isEqualTo(CLOUD_ACCOUNT_ID); // GH-90000
            assertThat(resource.getProvider()).isEqualTo(PROVIDER); // GH-90000
            assertThat(resource.getResourceType()).isEqualTo(RESOURCE_TYPE); // GH-90000
            assertThat(resource.getIdentifier()).isEqualTo(IDENTIFIER); // GH-90000
            assertThat(resource.getRiskScore()).isZero(); // GH-90000
            assertThat(resource.isPublic()).isFalse(); // GH-90000
            assertThat(resource.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(resource.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields [GH-90000]")
        void ofThrowsForNullRequiredFields() { // GH-90000
            assertThatThrownBy(() -> CloudResource.of(null, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, null, PROVIDER, RESOURCE_TYPE, IDENTIFIER)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("cloudAccountId must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, null, RESOURCE_TYPE, IDENTIFIER)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("provider must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, null, IDENTIFIER)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("resourceType must not be null [GH-90000]");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("identifier must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Sync Tracking Tests [GH-90000]")
    class SyncTrackingTests {

        @Test
        @DisplayName("recordSync() sets lastSyncedAt and updates timestamp [GH-90000]")
        void recordSyncSetsLastSyncedAtAndUpdates() { // GH-90000
            // GIVEN
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER); // GH-90000
            assertThat(resource.getLastSyncedAt()).isNull(); // GH-90000
            Instant beforeSync = Instant.now(); // GH-90000

            // WHEN
            CloudResource result = resource.recordSync(); // GH-90000

            // THEN
            assertThat(result).isSameAs(resource); // GH-90000
            assertThat(resource.getLastSyncedAt()).isAfterOrEqualTo(beforeSync); // GH-90000
            assertThat(resource.getUpdatedAt()).isAfterOrEqualTo(beforeSync); // GH-90000
        }

        @Test
        @DisplayName("multiple syncs update lastSyncedAt each time [GH-90000]")
        void multipleSyncsUpdateLastSyncedAt() throws InterruptedException { // GH-90000
            // GIVEN
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER); // GH-90000

            // WHEN
            resource.recordSync(); // GH-90000
            Instant firstSync = resource.getLastSyncedAt(); // GH-90000
            Thread.sleep(10); // GH-90000
            resource.recordSync(); // GH-90000
            Instant secondSync = resource.getLastSyncedAt(); // GH-90000

            // THEN
            assertThat(secondSync).isAfter(firstSync); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates resource with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000
            String tags = """
                    {"Environment": "Production", "Owner": "security-team"}
                    """;
            String config = """
                    {"instanceType": "t3.large", "securityGroups": ["sg-123"]}
                    """;

            // WHEN
            CloudResource resource = CloudResource.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                    .provider(PROVIDER) // GH-90000
                    .resourceType(RESOURCE_TYPE) // GH-90000
                    .identifier(IDENTIFIER) // GH-90000
                    .name("web-server-01 [GH-90000]")
                    .region("us-east-1 [GH-90000]")
                    .tags(tags) // GH-90000
                    .configuration(config) // GH-90000
                    .riskScore(75) // GH-90000
                    .isPublic(true) // GH-90000
                    .lastSyncedAt(now) // GH-90000
                    .createdAt(now) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(2) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(resource.getId()).isEqualTo(id); // GH-90000
            assertThat(resource.getName()).isEqualTo("web-server-01 [GH-90000]");
            assertThat(resource.getRegion()).isEqualTo("us-east-1 [GH-90000]");
            assertThat(resource.getTags()).isEqualTo(tags); // GH-90000
            assertThat(resource.getConfiguration()).isEqualTo(config); // GH-90000
            assertThat(resource.getRiskScore()).isEqualTo(75); // GH-90000
            assertThat(resource.isPublic()).isTrue(); // GH-90000
            assertThat(resource.getVersion()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("builder defaults riskScore to 0 [GH-90000]")
        void builderDefaultsRiskScoreToZero() { // GH-90000
            CloudResource resource = CloudResource.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                    .provider(PROVIDER) // GH-90000
                    .resourceType(RESOURCE_TYPE) // GH-90000
                    .identifier(IDENTIFIER) // GH-90000
                    .build(); // GH-90000

            assertThat(resource.getRiskScore()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults isPublic to false [GH-90000]")
        void builderDefaultsIsPublicToFalse() { // GH-90000
            CloudResource resource = CloudResource.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .cloudAccountId(CLOUD_ACCOUNT_ID) // GH-90000
                    .provider(PROVIDER) // GH-90000
                    .resourceType(RESOURCE_TYPE) // GH-90000
                    .identifier(IDENTIFIER) // GH-90000
                    .build(); // GH-90000

            assertThat(resource.isPublic()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            CloudResource resource1 = CloudResource.builder().id(id).resourceType("ec2:instance [GH-90000]").build();
            CloudResource resource2 = CloudResource.builder().id(id).resourceType("s3:bucket [GH-90000]").build();

            assertThat(resource1).isEqualTo(resource2); // GH-90000
            assertThat(resource1.hashCode()).isEqualTo(resource2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            CloudResource resource1 = CloudResource.builder().id(UUID.randomUUID()).build(); // GH-90000
            CloudResource resource2 = CloudResource.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(resource1).isNotEqualTo(resource2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Resource Type Tests [GH-90000]")
    class ResourceTypeTests {

        @Test
        @DisplayName("can set various AWS resource types [GH-90000]")
        void canSetVariousAwsResourceTypes() { // GH-90000
            String[] awsTypes = {"ec2:instance", "s3:bucket", "lambda:function", "rds:instance", "iam:role"};

            for (String type : awsTypes) { // GH-90000
                CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, CloudProvider.AWS, type, IDENTIFIER); // GH-90000
                assertThat(resource.getResourceType()).isEqualTo(type); // GH-90000
            }
        }

        @Test
        @DisplayName("can set various Azure resource types [GH-90000]")
        void canSetVariousAzureResourceTypes() { // GH-90000
            String[] azureTypes = {"vm:instance", "storage:account", "function:app", "sql:database"};

            for (String type : azureTypes) { // GH-90000
                CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, CloudProvider.AZURE, type, "resource-id"); // GH-90000
                assertThat(resource.getResourceType()).isEqualTo(type); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Risk Score Tests [GH-90000]")
    class RiskScoreTests {

        @Test
        @DisplayName("can set risk score via setter [GH-90000]")
        void canSetRiskScoreViaSetter() { // GH-90000
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER); // GH-90000

            resource.setRiskScore(85); // GH-90000

            assertThat(resource.getRiskScore()).isEqualTo(85); // GH-90000
        }

        @Test
        @DisplayName("risk score range from 0 to 100 [GH-90000]")
        void riskScoreRangeFromZeroToHundred() { // GH-90000
            CloudResource low = CloudResource.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .riskScore(0) // GH-90000
                    .build(); // GH-90000

            CloudResource high = CloudResource.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .riskScore(100) // GH-90000
                    .build(); // GH-90000

            assertThat(low.getRiskScore()).isEqualTo(0); // GH-90000
            assertThat(high.getRiskScore()).isEqualTo(100); // GH-90000
        }
    }
}
