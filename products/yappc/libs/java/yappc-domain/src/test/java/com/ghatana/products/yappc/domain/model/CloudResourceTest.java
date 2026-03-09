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
@DisplayName("CloudResource Domain Model Tests")
class CloudResourceTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID CLOUD_ACCOUNT_ID = UUID.randomUUID();
    private static final CloudProvider PROVIDER = CloudProvider.AWS;
    private static final String RESOURCE_TYPE = "ec2:instance";
    private static final String IDENTIFIER = "arn:aws:ec2:us-east-1:123456789012:instance/i-1234567890abcdef0";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates resource with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER);

            // THEN
            assertThat(resource.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(resource.getCloudAccountId()).isEqualTo(CLOUD_ACCOUNT_ID);
            assertThat(resource.getProvider()).isEqualTo(PROVIDER);
            assertThat(resource.getResourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(resource.getIdentifier()).isEqualTo(IDENTIFIER);
            assertThat(resource.getRiskScore()).isZero();
            assertThat(resource.isPublic()).isFalse();
            assertThat(resource.getCreatedAt()).isNotNull();
            assertThat(resource.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields")
        void ofThrowsForNullRequiredFields() {
            assertThatThrownBy(() -> CloudResource.of(null, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, null, PROVIDER, RESOURCE_TYPE, IDENTIFIER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("cloudAccountId must not be null");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, null, RESOURCE_TYPE, IDENTIFIER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("provider must not be null");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, null, IDENTIFIER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("resourceType must not be null");

            assertThatThrownBy(() -> CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("identifier must not be null");
        }
    }

    @Nested
    @DisplayName("Sync Tracking Tests")
    class SyncTrackingTests {

        @Test
        @DisplayName("recordSync() sets lastSyncedAt and updates timestamp")
        void recordSyncSetsLastSyncedAtAndUpdates() {
            // GIVEN
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER);
            assertThat(resource.getLastSyncedAt()).isNull();
            Instant beforeSync = Instant.now();

            // WHEN
            CloudResource result = resource.recordSync();

            // THEN
            assertThat(result).isSameAs(resource);
            assertThat(resource.getLastSyncedAt()).isAfterOrEqualTo(beforeSync);
            assertThat(resource.getUpdatedAt()).isAfterOrEqualTo(beforeSync);
        }

        @Test
        @DisplayName("multiple syncs update lastSyncedAt each time")
        void multipleSyncsUpdateLastSyncedAt() throws InterruptedException {
            // GIVEN
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER);

            // WHEN
            resource.recordSync();
            Instant firstSync = resource.getLastSyncedAt();
            Thread.sleep(10);
            resource.recordSync();
            Instant secondSync = resource.getLastSyncedAt();

            // THEN
            assertThat(secondSync).isAfter(firstSync);
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates resource with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            String tags = """
                    {"Environment": "Production", "Owner": "security-team"}
                    """;
            String config = """
                    {"instanceType": "t3.large", "securityGroups": ["sg-123"]}
                    """;

            // WHEN
            CloudResource resource = CloudResource.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .cloudAccountId(CLOUD_ACCOUNT_ID)
                    .provider(PROVIDER)
                    .resourceType(RESOURCE_TYPE)
                    .identifier(IDENTIFIER)
                    .name("web-server-01")
                    .region("us-east-1")
                    .tags(tags)
                    .configuration(config)
                    .riskScore(75)
                    .isPublic(true)
                    .lastSyncedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .version(2)
                    .build();

            // THEN
            assertThat(resource.getId()).isEqualTo(id);
            assertThat(resource.getName()).isEqualTo("web-server-01");
            assertThat(resource.getRegion()).isEqualTo("us-east-1");
            assertThat(resource.getTags()).isEqualTo(tags);
            assertThat(resource.getConfiguration()).isEqualTo(config);
            assertThat(resource.getRiskScore()).isEqualTo(75);
            assertThat(resource.isPublic()).isTrue();
            assertThat(resource.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("builder defaults riskScore to 0")
        void builderDefaultsRiskScoreToZero() {
            CloudResource resource = CloudResource.builder()
                    .workspaceId(WORKSPACE_ID)
                    .cloudAccountId(CLOUD_ACCOUNT_ID)
                    .provider(PROVIDER)
                    .resourceType(RESOURCE_TYPE)
                    .identifier(IDENTIFIER)
                    .build();

            assertThat(resource.getRiskScore()).isZero();
        }

        @Test
        @DisplayName("builder defaults isPublic to false")
        void builderDefaultsIsPublicToFalse() {
            CloudResource resource = CloudResource.builder()
                    .workspaceId(WORKSPACE_ID)
                    .cloudAccountId(CLOUD_ACCOUNT_ID)
                    .provider(PROVIDER)
                    .resourceType(RESOURCE_TYPE)
                    .identifier(IDENTIFIER)
                    .build();

            assertThat(resource.isPublic()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            CloudResource resource1 = CloudResource.builder().id(id).resourceType("ec2:instance").build();
            CloudResource resource2 = CloudResource.builder().id(id).resourceType("s3:bucket").build();

            assertThat(resource1).isEqualTo(resource2);
            assertThat(resource1.hashCode()).isEqualTo(resource2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            CloudResource resource1 = CloudResource.builder().id(UUID.randomUUID()).build();
            CloudResource resource2 = CloudResource.builder().id(UUID.randomUUID()).build();

            assertThat(resource1).isNotEqualTo(resource2);
        }
    }

    @Nested
    @DisplayName("Resource Type Tests")
    class ResourceTypeTests {

        @Test
        @DisplayName("can set various AWS resource types")
        void canSetVariousAwsResourceTypes() {
            String[] awsTypes = {"ec2:instance", "s3:bucket", "lambda:function", "rds:instance", "iam:role"};

            for (String type : awsTypes) {
                CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, CloudProvider.AWS, type, IDENTIFIER);
                assertThat(resource.getResourceType()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("can set various Azure resource types")
        void canSetVariousAzureResourceTypes() {
            String[] azureTypes = {"vm:instance", "storage:account", "function:app", "sql:database"};

            for (String type : azureTypes) {
                CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, CloudProvider.AZURE, type, "resource-id");
                assertThat(resource.getResourceType()).isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("Risk Score Tests")
    class RiskScoreTests {

        @Test
        @DisplayName("can set risk score via setter")
        void canSetRiskScoreViaSetter() {
            CloudResource resource = CloudResource.of(WORKSPACE_ID, CLOUD_ACCOUNT_ID, PROVIDER, RESOURCE_TYPE, IDENTIFIER);

            resource.setRiskScore(85);

            assertThat(resource.getRiskScore()).isEqualTo(85);
        }

        @Test
        @DisplayName("risk score range from 0 to 100")
        void riskScoreRangeFromZeroToHundred() {
            CloudResource low = CloudResource.builder()
                    .workspaceId(WORKSPACE_ID)
                    .riskScore(0)
                    .build();

            CloudResource high = CloudResource.builder()
                    .workspaceId(WORKSPACE_ID)
                    .riskScore(100)
                    .build();

            assertThat(low.getRiskScore()).isEqualTo(0);
            assertThat(high.getRiskScore()).isEqualTo(100);
        }
    }
}
