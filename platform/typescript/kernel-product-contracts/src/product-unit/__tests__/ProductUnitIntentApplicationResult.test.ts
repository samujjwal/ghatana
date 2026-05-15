/**
 * Tests for ProductUnitIntentApplicationResult contract.
 *
 * @doc.type test
 * @doc.purpose Validate ProductUnitIntentApplicationResult schema and type guards
 * @doc.layer kernel-product-contracts
 * @doc.pattern Unit Test
 */

import { describe, it, expect } from "vitest";
import {
  ProductUnitIntentApplicationResultSchema,
  type ProductUnitIntentApplicationResult,
  type ProductUnitIntentApplicationStatus,
} from "../ProductUnitIntent.js";

describe("ProductUnitIntentApplicationResult", () => {
  describe("schema validation", () => {
    it("accepts valid preview result", () => {
      const validPreview: ProductUnitIntentApplicationResult = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "previewed",
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        previewRef: "yappc://preview/123",
        lifecycleEventRefs: ["event://1"],
        provenanceRefs: ["prov://1"],
        runtimeTruthRefs: ["truth://1"],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(validPreview);
      expect(result.success).toBe(true);
    });

    it("accepts valid applied result with event/provenance refs", () => {
      const validApplied: ProductUnitIntentApplicationResult = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "applied",
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "platform",
        registryProviderId: "data-cloud-registry",
        sourceProviderId: "github",
        applicationRef: "kernel://apply/123",
        appliedAt: "2026-05-15T10:00:00-07:00",
        lifecycleEventRefs: ["event://1", "event://2"],
        provenanceRefs: ["prov://1", "prov://2"],
        runtimeTruthRefs: ["truth://1"],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(validApplied);
      expect(result.success).toBe(true);
    });

    it("rejects result missing correlationId", () => {
      const missingCorrelationId = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "applied" as ProductUnitIntentApplicationStatus,
        productUnitId: "product-456",
        correlationId: "", // empty string
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(missingCorrelationId);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.path.includes("correlationId"))).toBe(true);
      }
    });

    it("rejects result with unknown status", () => {
      const unknownStatus = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "unknown-status" as ProductUnitIntentApplicationStatus,
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(unknownStatus);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.path.includes("status"))).toBe(true);
      }
    });

    it("rejects result with empty productUnitId", () => {
      const emptyProductUnitId = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "applied" as ProductUnitIntentApplicationStatus,
        productUnitId: "", // empty string
        correlationId: "corr-789",
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(emptyProductUnitId);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.path.includes("productUnitId"))).toBe(true);
      }
    });

    it("accepts blocked result with reason codes", () => {
      const blockedResult: ProductUnitIntentApplicationResult = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "blocked",
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "platform",
        registryProviderId: "data-cloud-registry",
        sourceProviderId: "github",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: ["target-provider-mismatch", "missing-apply-permission"],
        errors: ["Registry provider mismatch", "Apply permission missing"],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(blockedResult);
      expect(result.success).toBe(true);
    });

    it("rejects result with invalid schemaVersion", () => {
      const invalidSchemaVersion = {
        schemaVersion: "2.0.0", // wrong version
        intentId: "intent-123",
        status: "applied" as ProductUnitIntentApplicationStatus,
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(invalidSchemaVersion);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.path.includes("schemaVersion"))).toBe(true);
      }
    });

    it("rejects result with invalid providerMode", () => {
      const invalidProviderMode = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "applied" as ProductUnitIntentApplicationStatus,
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "invalid-mode" as "bootstrap" | "platform",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(invalidProviderMode);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.path.includes("providerMode"))).toBe(true);
      }
    });

    it("rejects result with invalid appliedAt timestamp", () => {
      const invalidTimestamp = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "applied" as ProductUnitIntentApplicationStatus,
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        appliedAt: "not-a-timestamp",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(invalidTimestamp);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((issue) => issue.path.includes("appliedAt"))).toBe(true);
      }
    });

    it("rejects result with extra fields (strict mode)", () => {
      const extraFields = {
        schemaVersion: "1.0.0",
        intentId: "intent-123",
        status: "applied" as ProductUnitIntentApplicationStatus,
        productUnitId: "product-456",
        correlationId: "corr-789",
        providerMode: "bootstrap",
        registryProviderId: "ghatana-file-registry",
        sourceProviderId: "ghatana-file-registry",
        lifecycleEventRefs: [],
        provenanceRefs: [],
        runtimeTruthRefs: [],
        blockedReasons: [],
        errors: [],
        extraField: "not-allowed", // extra field
      };

      const result = ProductUnitIntentApplicationResultSchema.safeParse(extraFields);
      expect(result.success).toBe(false);
    });
  });

  describe("status values", () => {
    it("accepts all valid status values", () => {
      const validStatuses: ProductUnitIntentApplicationStatus[] = [
        "previewed",
        "queued",
        "applied",
        "blocked",
        "failed",
      ];

      for (const status of validStatuses) {
        const result = ProductUnitIntentApplicationResultSchema.safeParse({
          schemaVersion: "1.0.0",
          intentId: "intent-123",
          status,
          productUnitId: "product-456",
          correlationId: "corr-789",
          providerMode: "bootstrap",
          registryProviderId: "ghatana-file-registry",
          sourceProviderId: "ghatana-file-registry",
          lifecycleEventRefs: [],
          provenanceRefs: [],
          runtimeTruthRefs: [],
          blockedReasons: [],
          errors: [],
        });
        expect(result.success).toBe(true);
      }
    });
  });
});
