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

    @Test
    @DisplayName("inline event handler props are rejected")
    void validate_inlineEventHandlerPropFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Button",
                                        "props", Map.of("onClick", "alert(1)"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("must not contain inline executable handlers"));
    }

    @Test
    @DisplayName("unsafe image src schemes are rejected")
    void validate_unsafeImageSourceFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Image",
                                        "props", Map.of("src", "javascript:alert(1)"),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(
                error -> error.contains("unsafe") || error.contains("executable payload")
        );
    }

    @Test
    @DisplayName("governance record scope must match document")
    void validate_governanceScopeMismatchFails() {
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
                List.of(
                        new PageArtifactDocument.GovernanceRecord(
                                "other-artifact",
                                "other-document",
                                new PageArtifactDocument.GovernanceLineage(
                                        "action-1",
                                        "property-completion",
                                        "generated",
                                        0.9,
                                        true,
                                        "approved",
                                        List.of("root"),
                                        Instant.parse("2026-01-01T00:00:00Z").toString(),
                                        List.of("evidence:1")
                                )
                        )
                )
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("artifactId does not match document artifactId"));
        assertThat(result.errors()).anyMatch(error -> error.contains("documentId does not match document documentId"));
    }

    @Test
    @DisplayName("unsupported action binding type fails validation")
    void validate_unsupportedActionBindingTypeFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Button",
                                        "props", Map.of(
                                                "actionBinding", Map.of(
                                                        "type", "DROP_DATABASE",
                                                        "target", "admin"
                                                )
                                        ),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unsupported action binding type"));
    }

    @Test
    @DisplayName("data binding missing required fields fails validation")
    void validate_dataBindingMissingFieldsFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Summary",
                                                "dataBinding", Map.of("source", "dataset")
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("missing required field 'path'"));
    }

    @Test
    @DisplayName("builder metadata classification mismatch fails validation")
    void validate_builderMetadataClassificationMismatchFails() {
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
                        ),
                        "metadata", Map.of("dataClassification", "PUBLIC")
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("dataClassification does not match"));
    }

    @Test
    @DisplayName("Image node with javascript: src scheme fails validation")
    void validate_imageSrcJavascriptSchemeFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Image",
                                        "props", Map.of("src", "javascript:alert(1)", "alt", "img"),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unsafe src scheme"));
    }

    @Test
    @DisplayName("non-Image node with unsafe src scheme is also rejected")
    void validate_nonImageContractWithUnsafeSrcSchemeFails() {
        // A hypothetical Video or Embed contract that exposes 'src' must be covered by
        // the generalized scheme check — not only Image.
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of("src", "data:text/html,<script>evil()</script>"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unsafe src scheme"));
    }

    @Test
    @DisplayName("node dataClassification lower than document classification fails validation")
    void validate_nodeUnderDeclaredClassificationFails() {
        // Document is RESTRICTED; a node prop declaring PUBLIC would under-declare sensitivity.
        PageArtifactDocument document = new PageArtifactDocument(
                "artifact-1",
                "doc-1",
                "Sensitive Page",
                "user-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "SYNCED",
                "TRUSTED",
                "RESTRICTED",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of("dataClassification", "PUBLIC"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                new PageArtifactDocument.ValidationSummary(true, 0, 0),
                List.of(),
                "manual",
                0,
                0.95
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("under-declare sensitivity"));
    }

    @Test
    @DisplayName("node dataClassification matching or exceeding document classification passes")
    void validate_nodeMatchingClassificationPasses() {
        PageArtifactDocument document = new PageArtifactDocument(
                "artifact-1",
                "doc-1",
                "Sensitive Page",
                "user-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "SYNCED",
                "TRUSTED",
                "INTERNAL",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Box",
                                        "props", Map.of("dataClassification", "CONFIDENTIAL"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                new PageArtifactDocument.ValidationSummary(true, 0, 0),
                List.of(),
                "manual",
                0,
                0.95
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        // Should not produce the under-declaration error
        assertThat(result.errors()).noneMatch(error -> error.contains("under-declare sensitivity"));
    }

    // P1-009/P1-010: Consent policy and data-classification enforcement on data bindings

    @Test
    @DisplayName("data binding with INTERNAL classification and no consentPolicy fails validation")
    void validate_dataBindingInternalClassificationMissingConsentPolicyFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "PII Display",
                                                "dataBinding", Map.of(
                                                        "source", "user-profile",
                                                        "path", "email",
                                                        "classification", "INTERNAL"
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("must declare a 'consentPolicy'"));
    }

    @Test
    @DisplayName("data binding with CONFIDENTIAL classification and no consentPolicy fails validation")
    void validate_dataBindingConfidentialClassificationMissingConsentPolicyFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Medical Summary",
                                                "dataBinding", Map.of(
                                                        "source", "health-records",
                                                        "path", "diagnosis",
                                                        "classification", "CONFIDENTIAL"
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("must declare a 'consentPolicy'"));
    }

    @Test
    @DisplayName("data binding with CONFIDENTIAL classification and valid consentPolicy passes")
    void validate_dataBindingConfidentialWithValidConsentPolicyPasses() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Medical Summary",
                                                "dataBinding", Map.of(
                                                        "source", "health-records",
                                                        "path", "diagnosis",
                                                        "classification", "CONFIDENTIAL",
                                                        "consentPolicy", "EXPLICIT_CONSENT"
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.errors()).noneMatch(error -> error.contains("consentPolicy"));
    }

    @Test
    @DisplayName("data binding with PUBLIC classification does not require consentPolicy")
    void validate_dataBindingPublicClassificationNoConsentPolicyNeeded() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Product Name",
                                                "dataBinding", Map.of(
                                                        "source", "catalog",
                                                        "path", "name",
                                                        "classification", "PUBLIC"
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.errors()).noneMatch(error -> error.contains("consentPolicy"));
    }

    @Test
    @DisplayName("data binding with unrecognized classification fails validation")
    void validate_dataBindingUnrecognizedClassificationFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Data",
                                                "dataBinding", Map.of(
                                                        "source", "ds",
                                                        "path", "field",
                                                        "classification", "SUPER_SECRET"
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unrecognized data binding classification"));
    }

    @Test
    @DisplayName("data binding with RESTRICTED classification and unrecognized consentPolicy fails validation")
    void validate_dataBindingRestrictedWithInvalidConsentPolicyFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Restricted Data",
                                                "dataBinding", Map.of(
                                                        "source", "finance",
                                                        "path", "account",
                                                        "classification", "RESTRICTED",
                                                        "consentPolicy", "JUST_TRUST_ME"
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unrecognized consentPolicy"));
    }

    @Test
    @DisplayName("data binding list with a CONFIDENTIAL entry missing consentPolicy fails validation")
    void validate_dataBindingListMissingConsentPolicyFails() {
        PageArtifactDocument document = sampleDocument(
                "manual",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "contractName", "Text",
                                        "props", Map.of(
                                                "text", "Data",
                                                "dataBindings", List.of(
                                                        Map.of(
                                                                "source", "public-data",
                                                                "path", "title",
                                                                "classification", "PUBLIC"
                                                        ),
                                                        Map.of(
                                                                "source", "private-data",
                                                                "path", "ssn",
                                                                "classification", "CONFIDENTIAL"
                                                                // consentPolicy missing
                                                        )
                                                )
                                        ),
                                        "slots", Map.of()
                                )
                        )
                ),
                List.of()
        );

        PageArtifactValidator.ValidationResult result = PageArtifactValidator.validate(document);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("must declare a 'consentPolicy'"));
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
