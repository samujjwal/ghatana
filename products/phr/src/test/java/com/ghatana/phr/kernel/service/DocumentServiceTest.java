package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.DocumentService.DocumentUploadRequest;
import com.ghatana.phr.kernel.service.DocumentService.PatientDocument;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link DocumentService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR document upload boundary validation and sanitization
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DocumentService")
class DocumentServiceTest extends EventloopTestBase {

    private DocumentService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new DocumentService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    @Test
    @DisplayName("escapes uploaded document metadata")
    void escapesUploadedDocumentMetadata() {
        PatientDocument stored = runPromise(() -> service.uploadDocument(new DocumentUploadRequest(
            "patient-1",
            "lab-result",
            "<b>Lab Report</b>",
            "<script>alert('xss')</script>",
            "application/pdf",
            "pdf".getBytes(StandardCharsets.UTF_8),
            "abc123",
            "private"
        )));

        Optional<PatientDocument> fetched = runPromise(() -> service.getDocument(stored.getId(), "patient-1"));

        assertThat(fetched).isPresent();
        assertThat(fetched.orElseThrow().getTitle()).isEqualTo("&lt;b&gt;Lab Report&lt;/b&gt;");
        assertThat(fetched.orElseThrow().getDescription())
            .isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
    }

    @Test
    @DisplayName("rejects unsupported document visibility")
    void rejectsUnsupportedVisibility() {
        assertThrows(Exception.class, () -> runPromise(() -> service.uploadDocument(new DocumentUploadRequest(
            "patient-1",
            "lab-result",
            "Lab Report",
            "Normal",
            "application/pdf",
            "pdf".getBytes(StandardCharsets.UTF_8),
            "abc123",
            "public"
        ))));
        clearFatalError();
    }
}
