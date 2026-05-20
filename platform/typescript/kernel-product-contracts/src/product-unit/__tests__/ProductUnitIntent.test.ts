import { describe, expect, it } from "vitest";
import {
  isProductUnitIntent,
  validateProductUnitIntent,
  validateProductUnitIntentDetailed,
  type ProductUnitIntent,
} from "../ProductUnitIntent";

const baseIntent: ProductUnitIntent = {
  schemaVersion: "1.0.0",
  intentId: "intent-1",
  intentType: "create",
  scope: {
    tenantId: "tenant-1",
    workspaceId: "workspace-1",
    projectId: "project-1",
  },
  producer: {
    id: "yappc-control-plane",
    type: "yappc",
    correlationId: "corr-1",
  },
  target: {
    registryProvider: "ghatana-file-registry",
    sourceProvider: "github",
  },
  productUnit: {
    id: "external-demo",
    name: "External Demo",
    kind: "external-application",
    surfaces: [
      {
        id: "external-demo-web",
        type: "web",
        implementationStatus: "planned",
        sourceRef: "https://github.com/example/external-demo.git",
      },
    ],
  },
  requestedLifecycle: {
    profile: "standard-web-product",
    enableExecution: false,
    phases: ["validate", "build"],
  },
  governanceHints: {
    privacyLevel: "internal",
    evidencePrivacyClassification: "confidential",
    regulatedDomain: "marketing",
    requiresHumanApproval: true,
    requiredPolicyPacks: ["marketing-default"],
    dataSensitivity: "moderate",
    retentionPolicyId: "marketing-evidence-365",
    retentionDays: 365,
    evidenceRequired: true,
  },
  provenance: {
    sourceSystem: "yappc",
    sourceArtifactRefs: ["artifact:yappc:blueprint-1"],
    createdBy: "user:builder",
    createdAt: "2026-05-14T00:00:00.000Z",
    evidenceRefs: ["evidence:canvas:1"],
  },
};

describe("ProductUnitIntent", () => {
  it("rejects non-object input", () => {
    expect(validateProductUnitIntent(null).errors).toContain(
      "ProductUnitIntent must be an object"
    );
  });

  it("accepts a YAPPC-produced intent", () => {
    expect(isProductUnitIntent(baseIntent)).toBe(true);
  });

  it("accepts semantic artifact, source, risk, and generated change-set handoff refs", () => {
    const intent: ProductUnitIntent = {
      ...baseIntent,
      productUnit: {
        ...baseIntent.productUnit,
        productShape: "artifact-intelligence",
        sourceRefs: [
          {
            kind: "github-ref",
            ref: "https://github.com/example/external-demo/tree/main",
            providerId: "github",
          },
        ],
        semanticArtifactRefs: ["artifact-evidence://semantic/source-1"],
      },
      provenance: {
        ...baseIntent.provenance!,
        sourceRefs: [
          {
            kind: "github-ref",
            ref: "https://github.com/example/external-demo/tree/main",
            providerId: "github",
          },
        ],
      },
      semanticArtifactRefs: ["artifact-evidence://semantic/source-1"],
      riskHotspotRefs: ["artifact-evidence://risk/hotspot-1"],
      generatedChangeSetRefs: ["artifact-evidence://changeset/1"],
    };

    expect(isProductUnitIntent(intent)).toBe(true);
  });

  it("accepts an external repository source provider without assuming products path", () => {
    const intent: ProductUnitIntent = {
      ...baseIntent,
      producer: {
        id: "external-api",
        type: "external",
        correlationId: "external-corr-1",
      },
      target: {
        registryProvider: "ghatana-file-registry",
        sourceProvider: "github",
      },
    };

    expect(isProductUnitIntent(intent)).toBe(true);
  });

  it("rejects missing target providers", () => {
    const intent = {
      ...baseIntent,
      target: {
        registryProvider: "",
        sourceProvider: "",
      },
    };

    const result = validateProductUnitIntent(intent);
    expect(result.valid).toBe(false);
    expect(result.errors).toContain(
      "target.registryProvider must be a non-empty string"
    );
    expect(result.errors).toContain(
      "target.sourceProvider must be a non-empty string"
    );
  });

  it("rejects missing target object", () => {
    const { target: _target, ...intent } = baseIntent;

    expect(validateProductUnitIntent(intent).errors).toContain(
      "target must be an object"
    );
  });

  it("rejects missing producer object", () => {
    const { producer: _producer, ...intent } = baseIntent;

    expect(validateProductUnitIntent(intent).errors).toContain(
      "producer must be an object"
    );
  });

  it("rejects empty producer fields", () => {
    const intent = {
      ...baseIntent,
      producer: {
        id: "",
        type: "yappc",
        correlationId: "",
      },
    };

    const errors = validateProductUnitIntent(intent).errors;
    expect(errors).toContain("producer.id must be a non-empty string");
    expect(errors).toContain("producer.correlationId must be a non-empty string");
  });

  it("rejects invalid intent metadata", () => {
    const invalidSchemaVersion = {
      ...baseIntent,
      schemaVersion: "2.0.0",
      intentId: "",
      intentType: "delete",
    };

    const errors = validateProductUnitIntent(invalidSchemaVersion).errors;
    expect(errors).toContain('schemaVersion must be "1.0.0"');
    expect(errors).toContain("intentId must be a non-empty string");
    expect(errors).toContain("intentType is not supported");
  });

  it("rejects empty surfaces", () => {
    const intent = {
      ...baseIntent,
      productUnit: {
        ...baseIntent.productUnit,
        surfaces: [],
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "productUnit.surfaces must contain at least one surface"
    );
  });

  it("rejects missing scope", () => {
    const { scope: _scope, ...intent } = baseIntent;

    expect(validateProductUnitIntent(intent).errors).toContain(
      "scope must be an object"
    );
  });

  it("rejects unsupported producer types", () => {
    const intent = {
      ...baseIntent,
      producer: {
        ...baseIntent.producer,
        type: "unknown-producer",
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "producer.type is not supported"
    );
  });

  it("rejects unsupported surface implementation status", () => {
    const intent = {
      ...baseIntent,
      productUnit: {
        ...baseIntent.productUnit,
        surfaces: [
          {
            id: "external-demo-web",
            type: "web",
            implementationStatus: "half-built",
          },
        ],
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "productUnit.surfaces[0].implementationStatus is not supported"
    );
  });

  it("rejects unsupported surface type", () => {
    const intent = {
      ...baseIntent,
      productUnit: {
        ...baseIntent.productUnit,
        surfaces: [
          {
            id: "external-demo-web",
            type: "unknown-surface",
            implementationStatus: "planned",
          },
        ],
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "productUnit.surfaces[0].type is not supported"
    );
  });

  it("rejects missing productUnit", () => {
    const { productUnit: _productUnit, ...intent } = baseIntent;

    expect(validateProductUnitIntent(intent).errors).toContain(
      "productUnit must be an object"
    );
  });

  it("rejects unknown productUnit kind", () => {
    const intent = {
      ...baseIntent,
      productUnit: {
        ...baseIntent.productUnit,
        kind: "mystery-product",
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "productUnit.kind is not a known ProductUnit kind"
    );
  });

  it("rejects raw secret-like fields", () => {
    const intent = {
      ...baseIntent,
      productUnit: {
        ...baseIntent.productUnit,
        metadata: {
          githubToken: "do-not-put-this-here",
        },
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "ProductUnitIntent must not include raw secret-like fields"
    );
  });

  it("accepts YAPPC promote-candidate with evidenceRefs", () => {
    expect(isProductUnitIntent(baseIntent)).toBe(true);
  });

  it("rejects promote-candidate without sourceArtifactRefs", () => {
    const intent = {
      ...baseIntent,
      intentType: "promote-candidate",
      provenance: {
        ...baseIntent.provenance,
        sourceArtifactRefs: [],
      },
    };

    const result = validateProductUnitIntentDetailed(intent);

    expect(result.valid).toBe(false);
    expect(result.issues.map((issue) => issue.reasonCode)).toContain(
      "missing-source-artifact-refs"
    );
  });

  it("rejects promote-candidate without evidenceRefs", () => {
    const { evidenceRefs: _evidenceRefs, ...provenance } = baseIntent.provenance;
    const intent = {
      ...baseIntent,
      intentType: "promote-candidate",
      provenance,
    };

    const result = validateProductUnitIntentDetailed(intent);

    expect(result.valid).toBe(false);
    expect(result.issues.map((issue) => issue.reasonCode)).toContain(
      "missing-evidence"
    );
  });

  it("rejects invalid privacy classification and retention hints", () => {
    const intent = {
      ...baseIntent,
      governanceHints: {
        ...baseIntent.governanceHints,
        evidencePrivacyClassification: "private",
        retentionPolicyId: "",
        retentionDays: -1,
      },
    };

    const errors = validateProductUnitIntent(intent).errors;
    expect(errors).toContain("governanceHints.evidencePrivacyClassification is invalid");
    expect(errors).toContain("governanceHints.retentionPolicyId must be a non-empty string");
    expect(errors).toContain("governanceHints.retentionDays must be non-negative");
  });
});
