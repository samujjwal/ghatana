package com.ghatana.kernel.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KernelDocumentOcrPlugin")
@Tag("purity-validation")
class KernelDocumentOcrPluginTest {

    private final KernelDocumentOcrPlugin plugin = new KernelDocumentOcrPlugin();

    @Test
    @DisplayName("validates upload storage, provenance, and malware policy")
    void validatesUploadPolicy() {
        assertThat(plugin.validateStoragePolicy(
            new KernelDocumentOcrPlugin.StoragePolicy("NP", "25years", "managed-kms")
        )).isNotNull();
        assertThat(plugin.validateUploadProvenance(
            new KernelDocumentOcrPlugin.UploadProvenance("self-upload", "principal-1", "subject-1"),
            "subject-1"
        )).isNotNull();
        assertThat(plugin.validateMalwareScan(
            new KernelDocumentOcrPlugin.MalwareScanAttestation("clean", "scanner", Instant.parse("2026-01-01T00:00:00Z"))
        )).isNotNull();
    }

    @Test
    @DisplayName("enforces deterministic OCR review transitions")
    void enforcesReviewTransitions() {
        assertThat(plugin.validateReviewTransition("PENDING_REVIEW", null, "CONFIRMED", "idem-1"))
            .isEqualTo(KernelDocumentOcrPlugin.ReviewTransition.APPLY);
        assertThat(plugin.validateReviewTransition("CONFIRMED", "idem-1", "CONFIRMED", "idem-1"))
            .isEqualTo(KernelDocumentOcrPlugin.ReviewTransition.RETURN_EXISTING);

        assertThatThrownBy(() -> plugin.validateReviewTransition("CONFIRMED", "idem-1", "REJECTED", "idem-2"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already CONFIRMED");
    }
}
