/**
 * Tests for ProductUnitKind type definition.
 */

import { describe, it, expect } from "vitest";
import {
  isProductUnitKind,
  getProductUnitKindLabel,
  type ProductUnitKind,
} from "../ProductUnitKind";

describe("ProductUnitKind", () => {
  describe("isProductUnitKind", () => {
    it("returns true for valid ProductUnitKind values", () => {
      const validKinds: ProductUnitKind[] = [
        "business-product",
        "platform-provider",
        "shared-service",
        "demo-example",
        "domain-pack",
        "sdk",
        "plugin",
        "data-pipeline",
        "agent-runtime",
        "external-application",
      ];

      for (const kind of validKinds) {
        expect(isProductUnitKind(kind)).toBe(true);
      }
    });

    it("returns false for invalid values", () => {
      expect(isProductUnitKind("invalid-kind")).toBe(false);
      expect(isProductUnitKind("")).toBe(false);
      expect(isProductUnitKind(null)).toBe(false);
      expect(isProductUnitKind(undefined)).toBe(false);
      expect(isProductUnitKind(123)).toBe(false);
      expect(isProductUnitKind({})).toBe(false);
    });
  });

  describe("getProductUnitKindLabel", () => {
    it("returns human-readable labels for all ProductUnitKind values", () => {
      expect(getProductUnitKindLabel("business-product")).toBe("Business Product");
      expect(getProductUnitKindLabel("platform-provider")).toBe("Platform Provider");
      expect(getProductUnitKindLabel("shared-service")).toBe("Shared Service");
      expect(getProductUnitKindLabel("demo-example")).toBe("Demo Example");
      expect(getProductUnitKindLabel("domain-pack")).toBe("Domain Pack");
      expect(getProductUnitKindLabel("sdk")).toBe("SDK");
      expect(getProductUnitKindLabel("plugin")).toBe("Plugin");
      expect(getProductUnitKindLabel("data-pipeline")).toBe("Data Pipeline");
      expect(getProductUnitKindLabel("agent-runtime")).toBe("Agent Runtime");
      expect(getProductUnitKindLabel("external-application")).toBe(
        "External Application"
      );
    });
  });
});
