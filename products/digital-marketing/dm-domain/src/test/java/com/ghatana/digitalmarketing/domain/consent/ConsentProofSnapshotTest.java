package com.ghatana.digitalmarketing.domain.consent;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ConsentProofSnapshot")
class ConsentProofSnapshotTest {

    @Test
    @DisplayName("builds consent proof snapshot")
    void shouldBuildSnapshot() {
        Instant now = Instant.now();
        ConsentProofSnapshot snapshot = ConsentProofSnapshot.builder()
            .snapshotId("snapshot-1")
            .contactId("contact-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .consentStatus("GRANTED")
            .consentPurpose("marketing-email")
            .evidenceType("form-submit")
            .evidenceReference("proof://abc")
            .recordedAt(now)
            .recordedBy("user-1")
            .correlationId("corr-1")
            .build();

        assertThat(snapshot.getSnapshotId()).isEqualTo("snapshot-1");
        assertThat(snapshot.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(snapshot.getConsentStatus()).isEqualTo("GRANTED");
        assertThat(snapshot.getConsentPurpose()).isEqualTo("marketing-email");
        assertThat(snapshot.getEvidenceType()).isEqualTo("form-submit");
        assertThat(snapshot.getEvidenceReference()).isEqualTo("proof://abc");
        assertThat(snapshot.getRecordedAt()).isEqualTo(now);
        assertThat(snapshot.getRecordedBy()).isEqualTo("user-1");
        assertThat(snapshot.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("rejects blank evidence reference")
    void shouldRejectBlankEvidenceReference() {
        assertThatIllegalArgumentException().isThrownBy(() -> ConsentProofSnapshot.builder()
            .snapshotId("snapshot-1")
            .contactId("contact-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .consentStatus("GRANTED")
            .evidenceType("form-submit")
            .evidenceReference(" ")
            .recordedAt(Instant.now())
            .recordedBy("user-1")
            .correlationId("corr-1")
            .build());

        assertThatIllegalArgumentException().isThrownBy(() -> ConsentProofSnapshot.builder()
            .snapshotId(" ")
            .contactId("contact-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .consentStatus("GRANTED")
            .evidenceType("form-submit")
            .evidenceReference("proof://abc")
            .recordedAt(Instant.now())
            .recordedBy("user-1")
            .correlationId("corr-1")
            .build());
    }
}
