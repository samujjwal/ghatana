import { describe, expect, it } from "vitest";
import {
  isProductUnitIntent,
  validateProductUnitIntent,
  type ProductUnitIntent,
} from "../ProductUnitIntent";

const baseIntent: ProductUnitIntent = {
  schemaVersion: "1.0.0",
  intentId: "intent-1",
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
};

describe("ProductUnitIntent", () => {
  it("accepts a YAPPC-produced intent", () => {
    expect(isProductUnitIntent(baseIntent)).toBe(true);
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

  it("rejects raw secret-like fields", () => {
    const intent = {
      ...baseIntent,
      provenance: {
        githubToken: "do-not-put-this-here",
      },
    };

    expect(validateProductUnitIntent(intent).errors).toContain(
      "ProductUnitIntent must not include raw secret-like fields"
    );
  });
});
