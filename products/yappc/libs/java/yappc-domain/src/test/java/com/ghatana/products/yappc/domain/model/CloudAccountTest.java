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
 * Unit tests for {@link CloudAccount} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates CloudAccount entity behavior, state transitions, and factory methods
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("CloudAccount Domain Model Tests [GH-90000]")
class CloudAccountTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final CloudProvider PROVIDER = CloudProvider.AWS;
    private static final String ACCOUNT_ID = "123456789012";
    private static final String ACCOUNT_NAME = "Production AWS Account";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates account with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME); // GH-90000

            // THEN
            assertThat(account.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(account.getProvider()).isEqualTo(PROVIDER); // GH-90000
            assertThat(account.getAccountId()).isEqualTo(ACCOUNT_ID); // GH-90000
            assertThat(account.getName()).isEqualTo(ACCOUNT_NAME); // GH-90000
            assertThat(account.isEnabled()).isTrue(); // GH-90000
            assertThat(account.getConnectionStatus()).isEqualTo("PENDING [GH-90000]");
            assertThat(account.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(account.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null [GH-90000]")
        void ofThrowsWhenWorkspaceIdNull() { // GH-90000
            assertThatThrownBy(() -> CloudAccount.of(null, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when provider is null [GH-90000]")
        void ofThrowsWhenProviderNull() { // GH-90000
            assertThatThrownBy(() -> CloudAccount.of(WORKSPACE_ID, null, ACCOUNT_ID, ACCOUNT_NAME)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("provider must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when accountId is null [GH-90000]")
        void ofThrowsWhenAccountIdNull() { // GH-90000
            assertThatThrownBy(() -> CloudAccount.of(WORKSPACE_ID, PROVIDER, null, ACCOUNT_NAME)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("accountId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null [GH-90000]")
        void ofThrowsWhenNameNull() { // GH-90000
            assertThatThrownBy(() -> CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("State Transition Tests [GH-90000]")
    class StateTransitionTests {

        @Test
        @DisplayName("markConnected() updates status and timestamp [GH-90000]")
        void markConnectedUpdatesStatusAndTimestamp() { // GH-90000
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME); // GH-90000
            Instant beforeConnect = Instant.now(); // GH-90000

            // WHEN
            CloudAccount result = account.markConnected(); // GH-90000

            // THEN
            assertThat(result).isSameAs(account); // Fluent chaining // GH-90000
            assertThat(account.getConnectionStatus()).isEqualTo("CONNECTED [GH-90000]");
            assertThat(account.getLastConnectedAt()).isNotNull(); // GH-90000
            assertThat(account.getLastConnectedAt()).isAfterOrEqualTo(beforeConnect); // GH-90000
            assertThat(account.getUpdatedAt()).isAfterOrEqualTo(beforeConnect); // GH-90000
        }

        @Test
        @DisplayName("markDisconnected() updates status [GH-90000]")
        void markDisconnectedUpdatesStatus() { // GH-90000
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME); // GH-90000
            account.markConnected(); // GH-90000
            Instant lastConnected = account.getLastConnectedAt(); // GH-90000

            // WHEN
            CloudAccount result = account.markDisconnected(); // GH-90000

            // THEN
            assertThat(result).isSameAs(account); // GH-90000
            assertThat(account.getConnectionStatus()).isEqualTo("DISCONNECTED [GH-90000]");
            assertThat(account.getLastConnectedAt()).isEqualTo(lastConnected); // Not cleared // GH-90000
        }

        @Test
        @DisplayName("disable() sets enabled to false [GH-90000]")
        void disableSetsEnabledFalse() { // GH-90000
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME); // GH-90000
            assertThat(account.isEnabled()).isTrue(); // GH-90000

            // WHEN
            CloudAccount result = account.disable(); // GH-90000

            // THEN
            assertThat(result).isSameAs(account); // GH-90000
            assertThat(account.isEnabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("state transitions can be chained [GH-90000]")
        void stateTransitionsCanBeChained() { // GH-90000
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME); // GH-90000

            // WHEN
            account.markConnected().markDisconnected().disable(); // GH-90000

            // THEN
            assertThat(account.getConnectionStatus()).isEqualTo("DISCONNECTED [GH-90000]");
            assertThat(account.isEnabled()).isFalse(); // GH-90000
            assertThat(account.getLastConnectedAt()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates account with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            CloudAccount account = CloudAccount.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .provider(PROVIDER) // GH-90000
                    .accountId(ACCOUNT_ID) // GH-90000
                    .name(ACCOUNT_NAME) // GH-90000
                    .region("us-east-1 [GH-90000]")
                    .externalId("ext-12345 [GH-90000]")
                    .roleArn("arn:aws:iam::123456789012:role/SecurityAudit [GH-90000]")
                    .enabled(true) // GH-90000
                    .connectionStatus("CONNECTED [GH-90000]")
                    .lastConnectedAt(now) // GH-90000
                    .createdAt(now) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(1) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(account.getId()).isEqualTo(id); // GH-90000
            assertThat(account.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(account.getProvider()).isEqualTo(PROVIDER); // GH-90000
            assertThat(account.getAccountId()).isEqualTo(ACCOUNT_ID); // GH-90000
            assertThat(account.getName()).isEqualTo(ACCOUNT_NAME); // GH-90000
            assertThat(account.getRegion()).isEqualTo("us-east-1 [GH-90000]");
            assertThat(account.getExternalId()).isEqualTo("ext-12345 [GH-90000]");
            assertThat(account.getRoleArn()).isEqualTo("arn:aws:iam::123456789012:role/SecurityAudit [GH-90000]");
            assertThat(account.isEnabled()).isTrue(); // GH-90000
            assertThat(account.getConnectionStatus()).isEqualTo("CONNECTED [GH-90000]");
            assertThat(account.getLastConnectedAt()).isEqualTo(now); // GH-90000
            assertThat(account.getVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("builder defaults enabled to true [GH-90000]")
        void builderDefaultsEnabledToTrue() { // GH-90000
            CloudAccount account = CloudAccount.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .provider(PROVIDER) // GH-90000
                    .accountId(ACCOUNT_ID) // GH-90000
                    .name(ACCOUNT_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(account.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults connectionStatus to PENDING [GH-90000]")
        void builderDefaultsConnectionStatusToPending() { // GH-90000
            CloudAccount account = CloudAccount.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .provider(PROVIDER) // GH-90000
                    .accountId(ACCOUNT_ID) // GH-90000
                    .name(ACCOUNT_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(account.getConnectionStatus()).isEqualTo("PENDING [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            CloudAccount account1 = CloudAccount.builder().id(id).provider(CloudProvider.AWS).build(); // GH-90000
            CloudAccount account2 = CloudAccount.builder().id(id).provider(CloudProvider.GCP).build(); // GH-90000

            assertThat(account1).isEqualTo(account2); // GH-90000
            assertThat(account1.hashCode()).isEqualTo(account2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            CloudAccount account1 = CloudAccount.builder().id(UUID.randomUUID()).build(); // GH-90000
            CloudAccount account2 = CloudAccount.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(account1).isNotEqualTo(account2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cloud Provider Integration Tests [GH-90000]")
    class CloudProviderTests {

        @Test
        @DisplayName("can create account for each cloud provider [GH-90000]")
        void canCreateAccountForEachProvider() { // GH-90000
            for (CloudProvider provider : CloudProvider.values()) { // GH-90000
                CloudAccount account = CloudAccount.of(WORKSPACE_ID, provider, "account-" + provider.name(), provider.getDisplayName()); // GH-90000

                assertThat(account.getProvider()).isEqualTo(provider); // GH-90000
                assertThat(account.getAccountId()).isEqualTo("account-" + provider.name()); // GH-90000
            }
        }

        @Test
        @DisplayName("AWS account has role ARN support [GH-90000]")
        void awsAccountHasRoleArnSupport() { // GH-90000
            CloudAccount awsAccount = CloudAccount.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .provider(CloudProvider.AWS) // GH-90000
                    .accountId(ACCOUNT_ID) // GH-90000
                    .name("AWS Prod [GH-90000]")
                    .roleArn("arn:aws:iam::123456789012:role/SecurityAudit [GH-90000]")
                    .externalId("unique-external-id [GH-90000]")
                    .build(); // GH-90000

            assertThat(awsAccount.getRoleArn()).isNotNull(); // GH-90000
            assertThat(awsAccount.getExternalId()).isNotNull(); // GH-90000
        }
    }
}
