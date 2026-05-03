package com.ghatana.digitalmarketing.domain.privacy;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DataSubjectRequest (DMOS-P1-017).
 *
 * @doc.type test
 * @doc.purpose Verify privacy lifecycle workflows for data subject requests
 * @doc.layer domain
 */
@DisplayName("DataSubjectRequest")
class DataSubjectRequestTest {

    @Test
    @DisplayName("build creates valid request")
    void build_createsValidRequest() {
        DmTenantId tenantId = new DmTenantId("tenant-123");
        DmWorkspaceId workspaceId = new DmWorkspaceId("workspace-456");
        String contactPointHash = "abc123def456";

        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .requestType(DataSubjectRequest.RequestType.DATA_EXPORT)
            .contactPointHash(contactPointHash)
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .build();

        assertThat(request.getId()).isEqualTo("request-789");
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(request.getRequestType()).isEqualTo(DataSubjectRequest.RequestType.DATA_EXPORT);
        assertThat(request.getContactPointHash()).isEqualTo(contactPointHash);
        assertThat(request.getStatus()).isEqualTo(DataSubjectRequest.RequestStatus.PENDING);
    }

    @Test
    @DisplayName("complete marks request as completed with evidence")
    void complete_marksRequestAsCompletedWithEvidence() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_EXPORT)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .build();

        DataSubjectRequest completed = request.complete("admin-123", "s3://evidence/request-789.zip");

        assertThat(completed.getStatus()).isEqualTo(DataSubjectRequest.RequestStatus.COMPLETED);
        assertThat(completed.getCompletedBy()).isEqualTo("admin-123");
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getEvidenceLocation()).isEqualTo("s3://evidence/request-789.zip");
    }

    @Test
    @DisplayName("complete throws when request is not pending")
    void complete_throwsWhenRequestIsNotPending() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_EXPORT)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .status(DataSubjectRequest.RequestStatus.COMPLETED)
            .build();

        assertThatThrownBy(() -> request.complete("admin-123", "s3://evidence/request-789.zip"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not pending");
    }

    @Test
    @DisplayName("reject marks request as rejected with reason")
    void reject_marksRequestAsRejectedWithReason() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_DELETION)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .build();

        DataSubjectRequest rejected = request.reject("admin-123", "Contact point not found");

        assertThat(rejected.getStatus()).isEqualTo(DataSubjectRequest.RequestStatus.REJECTED);
        assertThat(rejected.getCompletedBy()).isEqualTo("admin-123");
        assertThat(rejected.getCompletedAt()).isNotNull();
        assertThat(rejected.getRejectionReason()).isEqualTo("Contact point not found");
    }

    @Test
    @DisplayName("reject throws when request is not pending")
    void reject_throwsWhenRequestIsNotPending() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_DELETION)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .status(DataSubjectRequest.RequestStatus.IN_PROGRESS)
            .build();

        assertThatThrownBy(() -> request.reject("admin-123", "Contact point not found"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not pending");
    }

    @Test
    @DisplayName("markInProgress marks request as in progress")
    void markInProgress_marksRequestAsInProgress() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_CORRECTION)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .build();

        DataSubjectRequest inProgress = request.markInProgress();

        assertThat(inProgress.getStatus()).isEqualTo(DataSubjectRequest.RequestStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("markInProgress throws when request is not pending")
    void markInProgress_throwsWhenRequestIsNotPending() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_CORRECTION)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .status(DataSubjectRequest.RequestStatus.IN_PROGRESS)
            .build();

        assertThatThrownBy(() -> request.markInProgress())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not pending");
    }

    @Test
    @DisplayName("toString redacts contact point hash from logs")
    void toString_redactsContactPointHashFromLogs() {
        DataSubjectRequest request = DataSubjectRequest.builder()
            .id("request-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .requestType(DataSubjectRequest.RequestType.DATA_EXPORT)
            .contactPointHash("abc123def456")
            .submittedAt(java.time.Instant.now())
            .submittedBy("user-123")
            .build();

        String str = request.toString();

        assertThat(str).doesNotContain("abc123def456");
        assertThat(str).contains("requestType");
    }
}
