package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for OperatorCatalogAdmissionPolicy hardening (AEP-005).
 * Validates that operator catalog admission policy enforces production-specific constraints
 * including commit SHA binding, evidence persistence, and approval requirements.
 *
 * @doc.type class
 * @doc.purpose Validates OperatorCatalogAdmissionPolicy hardening features
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("OperatorCatalogAdmissionPolicy Hardening Tests")
class OperatorCatalogAdmissionPolicyTest {

    @Nested
    @DisplayName("Approval Status Validation")
    class ApprovalStatusValidation {

        @Test
        @DisplayName("rejects operator without approval status")
        void rejectsOperatorWithoutApprovalStatus() {
            OperatorCatalogEntry entry = createEntry(Map.of("owner", "platform"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Operator is not approved for runtime use");
        }

        @Test
        @DisplayName("rejects operator with denied approval status")
        void rejectsOperatorWithDeniedApprovalStatus() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "denied"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Operator is not approved for runtime use");
        }

        @Test
        @DisplayName("accepts operator with approved status")
        void acceptsOperatorWithApprovedStatus() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved"));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts operator with uppercase APPROVED status")
        void acceptsOperatorWithUppercaseApprovedStatus() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "APPROVED"));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Side-Effecting Operator Validation")
    class SideEffectingOperatorValidation {

        @Test
        @DisplayName("rejects side-effecting operator without tool policy")
        void rejectsSideEffectingOperatorWithoutToolPolicy() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved"),
                Set.of(AgentSideEffectProfile.SIDE_EFFECTING));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Side-effecting operators require tool policy");
        }

        @Test
        @DisplayName("accepts side-effecting operator with tool policy")
        void acceptsSideEffectingOperatorWithToolPolicy() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved",
                "toolPolicyDeclared", "true"),
            Set.of(AgentSideEffectProfile.SIDE_EFFECTING));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("allows non-side-effecting operator without tool policy")
        void allowsNonSideEffectingOperatorWithoutToolPolicy() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved"),
            Set.of(AgentSideEffectProfile.PURE_INFERENCE));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry)
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Commit SHA Binding (AEP-005)")
    class CommitShaBinding {

        @Test
        @DisplayName("production requires commit SHA")
        void productionRequiresCommitSha() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry, null, "production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Production requires commit SHA binding");
        }

        @Test
        @DisplayName("production accepts valid commit SHA")
        void productionAcceptsValidCommitSha() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved",
                "commitSha", "7f84bc08e9e4e6d7e209cb49a855f199f7c90347"));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(
                    entry, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("production rejects invalid commit SHA format")
        void productionRejectsInvalidCommitShaFormat() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved",
                "commitSha", "invalid-sha"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry, "invalid-sha", "production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Invalid commit SHA format");
        }

        @Test
        @DisplayName("production requires operator metadata commit SHA")
        void productionRequiresOperatorMetadataCommitSha() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus",
                "approved"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(
                    entry, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Operator metadata must include commit SHA in production");
        }

        @Test
        @DisplayName("production validates commit SHA match")
        void productionValidatesCommitShaMatch() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved",
                "commitSha", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(
                    entry, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Operator commit SHA does not match deployment commit SHA");
        }

        @Test
        @DisplayName("non-production does not require commit SHA")
        void nonProductionDoesNotRequireCommitSha() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved"));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry, null, "development")
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Evidence Policy Validation")
    class EvidencePolicyValidation {

        @Test
        @DisplayName("production requires evidence policy")
        void productionRequiresEvidencePolicy() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved",
                "commitSha", "7f84bc08e9e4e6d7e209cb49a855f199f7c90347"));

            assertThatThrownBy(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(
                    entry, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Production requires evidence policy");
        }

        @Test
        @DisplayName("production accepts evidence policy")
        void productionAcceptsEvidencePolicy() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved",
                "commitSha", "7f84bc08e9e4e6d7e209cb49a855f199f7c90347",
                "evidencePolicy", "retention-90-days"));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(
                    entry, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("non-production does not require evidence policy")
        void nonProductionDoesNotRequireEvidencePolicy() {
            OperatorCatalogEntry entry = createEntry(Map.of(
                "owner", "platform",
                "approvalStatus", "approved"));

            assertThatCode(() -> 
                OperatorCatalogAdmissionPolicy.requireApproved(entry, null, "development")
            ).doesNotThrowAnyException();
        }
    }

    private static OperatorCatalogEntry createEntry(Map<String, String> metadata) {
        return createEntry(metadata, Set.of());
    }

    private static OperatorCatalogEntry createEntry(Map<String, String> metadata, Set<AgentSideEffectProfile> sideEffectProfiles) {
        return new OperatorCatalogEntry(
            OperatorId.of("tenant-a", "stream", "test-operator", "1.0.0"),
            OperatorType.STREAM,
            "Test Operator",
            "1.0.0",
            "Test operator for validation",
            Set.of("stream.filter"),
            sideEffectProfiles,
            metadata
        );
    }
}
