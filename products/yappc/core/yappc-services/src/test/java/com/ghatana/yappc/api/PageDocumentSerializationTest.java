/**
 * Page Document Serialization/Deserialization Tests
 * 
 * Production-grade tests for page document serialization and deserialization.
 * Ensures round-trip fidelity for page documents.
 * 
 * @doc.type test
 * @doc.purpose Page document serialization tests
 * @doc.layer test
 * @doc.pattern Serialization Test
 */

package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade tests for page document serialization/deserialization.
 */
@DisplayName("Page Document Serialization Tests")
class PageDocumentSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("Should serialize and deserialize page document envelope")
    void shouldSerializeAndDeserializePageDocumentEnvelope() throws Exception {
        PageDocumentEnvelope envelope = createValidPageDocumentEnvelope();

        String json = objectMapper.writeValueAsString(envelope);
        PageDocumentEnvelope deserialized = objectMapper.readValue(json, PageDocumentEnvelope.class);

        assertEquals(envelope.id(), deserialized.id());
        assertEquals(envelope.artifactId(), deserialized.artifactId());
        assertEquals(envelope.projectId(), deserialized.projectId());
        assertEquals(envelope.workspaceId(), deserialized.workspaceId());
        assertEquals(envelope.tenantId(), deserialized.tenantId());
        assertEquals(envelope.version(), deserialized.version());
        assertEquals(envelope.revision(), deserialized.revision());
    }

    @Test
    @DisplayName("Should serialize and deserialize page document with nested structures")
    void shouldSerializeAndDeserializePageDocumentWithNestedStructures() throws Exception {
        PageDocumentEnvelope envelope = createValidPageDocumentEnvelope();

        String json = objectMapper.writeValueAsString(envelope);
        PageDocumentEnvelope deserialized = objectMapper.readValue(json, PageDocumentEnvelope.class);

        assertNotNull(deserialized.document());
        assertEquals(envelope.document().documentId(), deserialized.document().documentId());
        assertEquals(envelope.document().format(), deserialized.document().format());
        
        assertNotNull(deserialized.metadata());
        assertEquals(envelope.metadata().name(), deserialized.metadata().name());
        
        assertNotNull(deserialized.state());
        assertEquals(envelope.state().status(), deserialized.state().status());
    }

    @Test
    @DisplayName("Should serialize and deserialize operation log")
    void shouldSerializeAndDeserializeOperationLog() throws Exception {
        PageDocumentEnvelope envelope = createValidPageDocumentEnvelope();

        String json = objectMapper.writeValueAsString(envelope);
        PageDocumentEnvelope deserialized = objectMapper.readValue(json, PageDocumentEnvelope.class);

        assertNotNull(deserialized.operationLog());
        assertEquals(envelope.operationLog().size(), deserialized.operationLog().size());
        
        if (!deserialized.operationLog().isEmpty()) {
            PageDocumentEnvelope.PageOperationRecord firstRecord = deserialized.operationLog().get(0);
            assertEquals("document-update", firstRecord.operationType());
            assertEquals(PageDocumentEnvelope.PageOperationRecord.OperationStatus.SUCCEEDED, firstRecord.status());
        }
    }

    @Test
    @DisplayName("Should handle empty operation log")
    void shouldHandleEmptyOperationLog() throws Exception {
        PageDocumentEnvelope envelope = new PageDocumentEnvelope(
                "envelope-1",
                "artifact-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                createValidDocument(),
                createValidDocumentState(),
                List.of(), // Empty operation log
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1",
                0L
        );

        String json = objectMapper.writeValueAsString(envelope);
        PageDocumentEnvelope deserialized = objectMapper.readValue(json, PageDocumentEnvelope.class);

        assertNotNull(deserialized.operationLog());
        assertTrue(deserialized.operationLog().isEmpty());
    }

    @Test
    @DisplayName("Should preserve document validation state")
    void shouldPreserveDocumentValidationState() throws Exception {
        PageDocumentEnvelope envelope = createValidPageDocumentEnvelope();

        String json = objectMapper.writeValueAsString(envelope);
        PageDocumentEnvelope deserialized = objectMapper.readValue(json, PageDocumentEnvelope.class);

        PageDocumentEnvelope.DocumentValidation validation = deserialized.document().validation();
        assertEquals(envelope.document().validation().isValid(), validation.isValid());
        assertEquals(envelope.document().validation().errors(), validation.errors());
    }

    @Test
    @DisplayName("Should preserve document fidelity metrics")
    void shouldPreserveDocumentFidelityMetrics() throws Exception {
        PageDocumentEnvelope envelope = createValidPageDocumentEnvelope();

        String json = objectMapper.writeValueAsString(envelope);
        PageDocumentEnvelope deserialized = objectMapper.readValue(json, PageDocumentEnvelope.class);

        PageDocumentEnvelope.DocumentFidelity fidelity = deserialized.document().fidelity();
        assertEquals(envelope.document().fidelity().roundTripSuccessful(), fidelity.roundTripSuccessful());
        assertEquals(envelope.document().fidelity().fidelityScore(), fidelity.fidelityScore(), 0.001);
    }

    // Helper methods to create test data

    private PageDocumentEnvelope createValidPageDocumentEnvelope() {
        return new PageDocumentEnvelope(
                "envelope-1",
                "artifact-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                createValidDocument(),
                createValidDocumentState(),
                createOperationLog(),
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1",
                0L
        );
    }

    private PageDocumentEnvelope.PageMetadata createValidMetadata() {
        return new PageDocumentEnvelope.PageMetadata(
                "Test Page",
                "Test Description",
                "generate",
                Set.of("test", "page"),
                Map.of("key", "value")
        );
    }

    private PageDocumentEnvelope.PageDocument createValidDocument() {
        return new PageDocumentEnvelope.PageDocument(
                "doc-1",
                "{\"root\": {\"type\": \"page\"}}",
                PageDocumentEnvelope.PageDocument.DocumentFormat.SERIALIZED,
                new PageDocumentEnvelope.DocumentValidation(
                        true,
                        List.of(),
                        List.of(),
                        Instant.now()
                ),
                new PageDocumentEnvelope.DocumentFidelity(
                        true,
                        1.0,
                        List.of(),
                        Instant.now()
                )
        );
    }

    private PageDocumentEnvelope.PageDocumentState createValidDocumentState() {
        return new PageDocumentEnvelope.PageDocumentState(
                PageDocumentEnvelope.PageDocumentState.DocumentStatus.DRAFT,
                false,
                true,
                false,
                null,
                null,
                List.of()
        );
    }

    private List<PageDocumentEnvelope.PageOperationRecord> createOperationLog() {
        return List.of(
                new PageDocumentEnvelope.PageOperationRecord(
                        "op-1",
                        "document-update",
                        PageDocumentEnvelope.PageOperationRecord.OperationStatus.SUCCEEDED,
                        "user-1",
                        "Test User",
                        "Document updated",
                        "generate",
                        Map.of("version", "1"),
                        Instant.now()
                )
        );
    }
}
