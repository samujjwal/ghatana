/**
 * Boundary tests for model-registry UI coverage.
 *
 * The launcher exposes canonical `/api/v1/models*` endpoints, but the current
 * Data Cloud UI intentionally keeps model-registry concerns inside consolidated
 * surfaces rather than routing to a standalone page.
 *
 * @doc.type test
 * @doc.purpose Assert current route boundary for model-registry UI coverage
 * @doc.layer frontend
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const routesSource = readFileSync(
  path.resolve(__dirname, "../../routes.tsx"),
  "utf8",
);
const canonicalOpenApi = readFileSync(
  path.resolve(__dirname, "../../../../../contracts/openapi/data-cloud.yaml"),
  "utf8",
);

describe("ModelPage — current route boundary", () => {
  it("does not define a standalone model-registry page route in the current consolidated IA", () => {
    expect(routesSource).not.toContain("ModelRegistryPage");
    expect(routesSource).not.toContain("path: 'models'");
    expect(routesSource).toContain('path: "insights"');
  });

  it("still exposes the canonical launcher model-registry endpoints for shared contract coverage", () => {
    expect(canonicalOpenApi).toContain("/api/v1/models:");
    expect(canonicalOpenApi).toContain("/api/v1/models/{modelName}:");
    expect(canonicalOpenApi).toContain("/api/v1/models/{modelName}/promote:");
  });
});
