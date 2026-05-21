import { describe, expect, it } from "vitest";
import {
  ProductInteractionContractSchema,
  isProductInteractionContract,
  parseProductInteractionEvidenceRecord,
  parseProductInteractionContract,
} from "../ProductInteractionContract.js";

const consentStatusContract = {
  contractId: "kernel://interactions/phr.consent-status.v1",
  schemaVersion: "1.0.0",
  providerProductId: "phr",
  consumerProductIds: ["digital-marketing"],
  mode: "request-response",
  requestSchemaRef: "schemas/consent-status-request.v1.json",
  responseSchemaRef: "schemas/consent-status-response.v1.json",
  policy: {
    requiresAuth: true,
    requiresTenant: true,
    requiresConsent: true,
    piiClassification: "healthcare-contact",
    tenantScope: "same-tenant",
    allowedCallerRoles: ["lifecycle-runner"],
    allowedLifecyclePhases: ["validate", "deploy", "verify"],
  },
  evidence: {
    required: true,
    manifestType: "interaction-evidence",
    evidenceRefs: ["evidence://phr/consent-status"],
  },
} as const;

describe("ProductInteractionContract", () => {
  it("accepts a consent-protected request-response contract", () => {
    const parsed = parseProductInteractionContract(consentStatusContract);

    expect(parsed.contractId).toBe("kernel://interactions/phr.consent-status.v1");
    expect(isProductInteractionContract(parsed)).toBe(true);
  });

  it("requires event topics and event schemas for event interactions", () => {
    expect(() =>
      ProductInteractionContractSchema.parse({
        ...consentStatusContract,
        mode: "event-publish",
        requestSchemaRef: undefined,
        responseSchemaRef: undefined,
      }),
    ).toThrow(/event interactions require topic/);
  });

  it("requires piiClassification when consent is required", () => {
    expect(() =>
      ProductInteractionContractSchema.parse({
        ...consentStatusContract,
        policy: {
          ...consentStatusContract.policy,
          piiClassification: undefined,
        },
      }),
    ).toThrow(/piiClassification/);
  });

  it("validates canonical interaction evidence records", () => {
    const evidence = parseProductInteractionEvidenceRecord({
      schemaVersion: "1.0.0",
      evidenceId: "evidence-interaction-1",
      manifestType: "interaction-evidence",
      contractId: consentStatusContract.contractId,
      contractVersion: "1.0.0",
      providerProductId: "phr",
      consumerProductId: "digital-marketing",
      mode: "request-response",
      tenantId: "tenant-1",
      workspaceId: "workspace-1",
      productUnitId: "digital-marketing",
      runId: "run-1",
      correlationId: "corr-1",
      requestedAt: "2026-05-21T00:00:00.000Z",
      completedAt: "2026-05-21T00:00:01.000Z",
      status: "succeeded",
      policyDecision: "allowed",
      evidenceRefs: ["evidence://policy/decision-1"],
    });

    expect(evidence.manifestType).toBe("interaction-evidence");
  });

  it("requires reasonCode for blocked interaction evidence", () => {
    expect(() =>
      parseProductInteractionEvidenceRecord({
        schemaVersion: "1.0.0",
        evidenceId: "evidence-interaction-1",
        manifestType: "interaction-evidence",
        contractId: consentStatusContract.contractId,
        contractVersion: "1.0.0",
        providerProductId: "phr",
        consumerProductId: "digital-marketing",
        mode: "request-response",
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        productUnitId: "digital-marketing",
        runId: "run-1",
        correlationId: "corr-1",
        requestedAt: "2026-05-21T00:00:00.000Z",
        status: "blocked",
        policyDecision: "denied",
        evidenceRefs: [],
      }),
    ).toThrow(/reasonCode/);
  });
});
