import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  COLLECTION_RUNTIME_OPENAPI_PATHS,
  DEPRECATED_COLLECTION_ROUTE_REDIRECTS,
  DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS,
} from "../../../test-fixtures/deprecatedRoutes";

const canonicalOpenApi = readFileSync(
  path.resolve(__dirname, "../../../../../contracts/openapi/data-cloud.yaml"),
  "utf8",
);

const mswHandlersSource = readFileSync(
  path.resolve(__dirname, "../../mocks/handlers.ts"),
  "utf8",
);

const playwrightMocksSource = readFileSync(
  path.resolve(__dirname, "../../../e2e/helpers/api-mocks.ts"),
  "utf8",
);

describe("OpenAPI-driven collection mocks", () => {
  it("keeps canonical collection mock routes anchored to documented OpenAPI paths", () => {
    COLLECTION_RUNTIME_OPENAPI_PATHS.forEach((openApiPath) => {
      expect(canonicalOpenApi).toContain(`${openApiPath}:`);
    });
  });

  it("keeps deprecated collection aliases mapped to canonical documented routes", () => {
    DEPRECATED_COLLECTION_ROUTE_REDIRECTS.forEach(
      ({ legacyPath, canonicalPath, openApiPath }) => {
        expect(canonicalOpenApi).toContain(`${openApiPath}:`);
        expect(mswHandlersSource).toContain(legacyPath.replace("{id}", ":id"));
        expect(playwrightMocksSource).toContain(
          legacyPath.replace("{id}", "*"),
        );
        expect(mswHandlersSource).toContain(
          canonicalPath
            .replace("/api/v1", "${BASE}")
            .replace("{id}", "${params.id}"),
        );
      },
    );
  });

  it("keeps runtime-truth compatibility aliases mapped to canonical surface routes", () => {
    DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS.forEach(
      ({ legacyPath, canonicalPath, openApiPath }) => {
        expect(canonicalOpenApi).toContain(`${openApiPath}:`);
        expect(mswHandlersSource).toContain(
          legacyPath.replace("/api/v1", "${BASE}"),
        );
        expect(mswHandlersSource).toContain(
          canonicalPath.replace("/api/v1", "${BASE}"),
        );
        expect(playwrightMocksSource).toContain(
          legacyPath.replace("/api/v1", "**/api/v1"),
        );
        expect(playwrightMocksSource).toContain(
          canonicalPath.replace("/api/v1", "**/api/v1"),
        );
      },
    );
  });

  it("uses explicit deprecated-route warnings when mock adapters keep compatibility redirects", () => {
    const configuredRedirectCount =
      DEPRECATED_COLLECTION_ROUTE_REDIRECTS.length +
      DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS.length;

    if (configuredRedirectCount === 0) {
      expect(DEPRECATED_COLLECTION_ROUTE_REDIRECTS).toEqual([]);
      expect(DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS).toEqual([]);
      return;
    }

    expect(mswHandlersSource).toContain("warnDeprecatedRoute");
    expect(playwrightMocksSource).toContain("warnDeprecatedRoute");
    expect(mswHandlersSource).toContain("buildDeprecatedRouteHeaders");
    expect(playwrightMocksSource).toContain("buildDeprecatedRouteHeaders");
  });
});
