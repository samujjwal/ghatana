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
@DisplayName("CloudAccount Domain Model Tests")
class CloudAccountTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final CloudProvider PROVIDER = CloudProvider.AWS;
    private static final String ACCOUNT_ID = "123456789012";
    private static final String ACCOUNT_NAME = "Production AWS Account";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates account with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME);

            // THEN
            assertThat(account.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(account.getProvider()).isEqualTo(PROVIDER);
            assertThat(account.getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(account.getName()).isEqualTo(ACCOUNT_NAME);
            assertThat(account.isEnabled()).isTrue();
            assertThat(account.getConnectionStatus()).isEqualTo("PENDING");
            assertThat(account.getCreatedAt()).isNotNull();
            assertThat(account.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() {
            assertThatThrownBy(() -> CloudAccount.of(null, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when provider is null")
        void ofThrowsWhenProviderNull() {
            assertThatThrownBy(() -> CloudAccount.of(WORKSPACE_ID, null, ACCOUNT_ID, ACCOUNT_NAME))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("provider must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when accountId is null")
        void ofThrowsWhenAccountIdNull() {
            assertThatThrownBy(() -> CloudAccount.of(WORKSPACE_ID, PROVIDER, null, ACCOUNT_NAME))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null")
        void ofThrowsWhenNameNull() {
            assertThatThrownBy(() -> CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name must not be null");
        }
    }

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("markConnected() updates status and timestamp")
        void markConnectedUpdatesStatusAndTimestamp() {
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME);
            Instant beforeConnect = Instant.now();

            // WHEN
            CloudAccount result = account.markConnected();

            // THEN
            assertThat(result).isSameAs(account); // Fluent chaining
            assertThat(account.getConnectionStatus()).isEqualTo("CONNECTED");
            assertThat(account.getLastConnectedAt()).isNotNull();
            assertThat(account.getLastConnectedAt()).isAfterOrEqualTo(beforeConnect);
            assertThat(account.getUpdatedAt()).isAfterOrEqualTo(beforeConnect);
        }

        @Test
        @DisplayName("markDisconnected() updates status")
        void markDisconnectedUpdatesStatus() {
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME);
            account.markConnected();
            Instant lastConnected = account.getLastConnectedAt();

            // WHEN
            CloudAccount result = account.markDisconnected();

            // THEN
            assertThat(result).isSameAs(account);
            assertThat(account.getConnectionStatus()).isEqualTo("DISCONNECTED");
            assertThat(account.getLastConnectedAt()).isEqualTo(lastConnected); // Not cleared
        }

        @Test
        @DisplayName("disable() sets enabled to false")
        void disableSetsEnabledFalse() {
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME);
            assertThat(account.isEnabled()).isTrue();

            // WHEN
            CloudAccount result = account.disable();

            // THEN
            assertThat(result).isSameAs(account);
            assertThat(account.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("state transitions can be chained")
        void stateTransitionsCanBeChained() {
            // GIVEN
            CloudAccount account = CloudAccount.of(WORKSPACE_ID, PROVIDER, ACCOUNT_ID, ACCOUNT_NAME);

            // WHEN
            account.markConnected().markDisconnected().disable();

            // THEN
            assertThat(account.getConnectionStatus()).isEqualTo("DISCONNECTED");
            assertThat(account.isEnabled()).isFalse();
            assertThat(account.getLastConnectedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates account with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            CloudAccount account = CloudAccount.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .provider(PROVIDER)
                    .accountId(ACCOUNT_ID)
                    .name(ACCOUNT_NAME)
                    .region("us-east-1")
                    .externalId("ext-12345")
                    .roleArn("arn:aws:iam::123456789012:role/SecurityAudit")
                    .enabled(true)
                    .connectionStatus("CONNECTED")
                    .lastConnectedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .version(1)
                    .build();

            // THEN
            assertThat(account.getId()).isEqualTo(id);
            assertThat(account.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(account.getProvider()).isEqualTo(PROVIDER);
            assertThat(account.getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(account.getName()).isEqualTo(ACCOUNT_NAME);
            assertThat(account.getRegion()).isEqualTo("us-east-1");
            assertThat(account.getExternalId()).isEqualTo("ext-12345");
            assertThat(account.getRoleArn()).isEqualTo("arn:aws:iam::123456789012:role/SecurityAudit");
            assertThat(account.isEnabled()).isTrue();
            assertThat(account.getConnectionStatus()).isEqualTo("CONNECTED");
            assertThat(account.getLastConnectedAt()).isEqualTo(now);
            assertThat(account.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("builder defaults enabled to true")
        void builderDefaultsEnabledToTrue() {
            CloudAccount account = CloudAccount.builder()
                    .workspaceId(WORKSPACE_ID)
                    .provider(PROVIDER)
                    .accountId(ACCOUNT_ID)
                    .name(ACCOUNT_NAME)
                    .build();

            assertThat(account.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("builder defaults connectionStatus to PENDING")
        void builderDefaultsConnectionStatusToPending() {
            CloudAccount account = CloudAccount.builder()
                    .workspaceId(WORKSPACE_ID)
                    .provider(PROVIDER)
                    .accountId(ACCOUNT_ID)
                    .name(ACCOUNT_NAME)
                    .build();

            assertThat(account.getConnectionStatus()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            CloudAccount account1 = CloudAccount.builder().id(id).provider(CloudProvider.AWS).build();
            CloudAccount account2 = CloudAccount.builder().id(id).provider(CloudProvider.GCP).build();

            assertThat(account1).isEqualTo(account2);
            assertThat(account1.hashCode()).isEqualTo(account2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            CloudAccount account1 = CloudAccount.builder().id(UUID.randomUUID()).build();
            CloudAccount account2 = CloudAccount.builder().id(UUID.randomUUID()).build();

            assertThat(account1).isNotEqualTo(account2);
        }
    }

    @Nested
    @DisplayName("Cloud Provider Integration Tests")
    class CloudProviderTests {

        @Test
        @DisplayName("can create account for each cloud provider")
        void canCreateAccountForEachProvider() {
            for (CloudProvider provider : CloudProvider.values()) {
                CloudAccount account = CloudAccount.of(WORKSPACE_ID, provider, "account-" + provider.name(), provider.getDisplayName());

                assertThat(account.getProvider()).isEqualTo(provider);
                assertThat(account.getAccountId()).isEqualTo("account-" + provider.name());
            }
        }

        @Test
        @DisplayName("AWS account has role ARN support")
        void awsAccountHasRoleArnSupport() {
            CloudAccount awsAccount = CloudAccount.builder()
                    .workspaceId(WORKSPACE_ID)
                    .provider(CloudProvider.AWS)
                    .accountId(ACCOUNT_ID)
                    .name("AWS Prod")
                    .roleArn("arn:aws:iam::123456789012:role/SecurityAudit")
                    .externalId("unique-external-id")
                    .build();

            assertThat(awsAccount.getRoleArn()).isNotNull();
            assertThat(awsAccount.getExternalId()).isNotNull();
        }
    }
}
