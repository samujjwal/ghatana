package com.ghatana.yappc.domain.pageartifact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageArtifactValidator Tests")
class PageArtifactValidatorTest {

    @Test
    @DisplayName("generated documents require governance records")
    void validate_generatedDocumentRequiresGovernance() {
        PageArtifactDocument document = sampleDocument(
                "generated",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("Governance records are required"));
    }

    @Test
    @DisplayName("invalid root node references fail validation")
    void validate_invalidRootNodeReferenceFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("missing-root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("references unknown node"));
    }

    @Test
    @DisplayName("slot references must point at existing nodes")
    void validate_invalidSlotReferenceFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of("child-1"))
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("references missing child node"));
    }

    @Test
    @DisplayName("duplicate child references fail validation")
    void validate_duplicateChildReferenceFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root", "sidebar"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of("child-1"))
                                ),
                                "sidebar", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of("child-1"))
                                ),
                                "child-1", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("referenced by multiple parents"));
    }

    @Test
    @DisplayName("orphan nodes fail validation")
    void validate_orphanNodeFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of())
                                ),
                                "orphan", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("orphan node"));
    }

    @Test
    @DisplayName("manual documents remain valid without governance but surface a warning")
    void validate_manualDocumentWarnsWithoutGovernance() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("No governance records present"));
    }

    @Test
    @DisplayName("documents containing executable payloads are rejected")
    void validate_rejectsExecutablePayloads() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of("onLoad", "javascript:alert(1)"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("potentially executable content"));
    }

    @Test
    @DisplayName("unknown contracts fail validation")
    void validate_unknownContractFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "AlienWidget",
                                        "props", Map.of(),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unknown contract 'AlienWidget'"));
    }

    @Test
    @DisplayName("invalid contract slots fail validation")
    void validate_invalidContractSlotFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of("text", "Hello"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("does not allow slot 'default'"));
    }

    @Test
    @DisplayName("missing required contract props fail validation")
    void validate_missingRequiredContractPropFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "TextField",
                                        "props", Map.of("label", "Email"),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("missing required prop 'name'"));
    }

    @Test
    @DisplayName("invalid contract prop types fail validation")
    void validate_invalidContractPropTypeFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Button",
                                        "props", Map.of("disabled", "nope"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("prop 'disabled' must be of type boolean"));
    }

    private static PageArtifactDocument sampleDocument(
            String source,
            Map<String, Object> builderDocument,
            List<PageArtifactDocument.GovernanceRecord> governanceRecords
    ) {
        return new PageArtifactDocument(
                "artifact-1",
                "doc-1",
                "Landing Page",
                "user-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "SYNCED",
                "TRUSTED",
                "INTERNAL",
                builderDocument,
                new PageArtifactDocument.ValidationSummary(true, 0, 0),
                governanceRecords,
                source,
                0,
                0.95
        );
    }
}
