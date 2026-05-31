/**
 * Analytics OpenAPI Route Alignment Tests (DC-P1-006)
 *
 * Verifies that the client-side analytics.service URL paths match the canonical
 * paths defined in the data-cloud.yaml OpenAPI contract.  Also documents which
 * OpenAPI-defined analytics endpoints are intentionally unimplemented on the
 * client side (e.g. server-side-only, marked UNSUPPORTED, or not yet reached).
 *
 * These tests do NOT exercise network I/O — they parse the source of the
 * analytics.service module and the OpenAPI spec to prove structural alignment.
 */

import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const serviceSource = readFileSync(
  path.resolve(__dirname, "../../api/analytics.service.ts"),
  "utf8",
);

const openApiSpec = readFileSync(
  path.resolve(__dirname, "../../../../../contracts/openapi/data-cloud.yaml"),
  "utf8",
);

describe("analytics client — OpenAPI route alignment (DC-P1-006)", () => {
  // ---------------------------------------------------------------------------
  // 1. Paths implemented in the client must match the OpenAPI contract exactly.
  // ---------------------------------------------------------------------------
  describe("implemented paths match OpenAPI spec", () => {
    const implementedPaths: Array<{
      description: string;
      clientPath: string;
      openapiPath: string | null;
    }> = [
      {
        description: "SQL query submission",
        clientPath: "/analytics/query",
        openapiPath: "/api/v1/analytics/query",
      },
      {
        description: "Query explain (execution plan preview)",
        clientPath: "/analytics/explain",
        openapiPath: "/api/v1/analytics/explain",
      },
      {
        // policy-evaluate is a client-side extension called by the service but
        // not yet formally declared in data-cloud.yaml.  The OpenAPI check is
        // skipped (openapiPath: null) until the spec is updated.
        description:
          "Policy evaluation for a query (client extension, pending OpenAPI declaration)",
        clientPath: "/analytics/policy-evaluate",
        openapiPath: null,
      },
      {
        description: "AI-assisted query suggestions",
        clientPath: "/analytics/suggest",
        openapiPath: "/api/v1/analytics/suggest",
      },
      {
        description: "Federated query via Trino connector",
        clientPath: "/queries/federated",
        openapiPath: "/api/v1/queries/federated",
      },
    ];

    for (const { description, clientPath, openapiPath } of implementedPaths) {
      it(`client path '${clientPath}' appears in service source — ${description}`, () => {
        expect(
          serviceSource,
          `analytics.service.ts must call '${clientPath}' for: ${description}`,
        ).toContain(`"${clientPath}"`);
      });

      if (openapiPath !== null) {
        it(`OpenAPI contract declares path '${openapiPath}' — ${description}`, () => {
          expect(
            openApiSpec,
            `data-cloud.yaml is missing path '${openapiPath}' for: ${description}`,
          ).toContain(openapiPath);
        });
      }
    }
  });

  // ---------------------------------------------------------------------------
  // 2. OpenAPI-defined analytics paths that are NOT client-implemented.
  //    Each entry below is intentionally absent from the client with a reason.
  // ---------------------------------------------------------------------------
  describe("unimplemented paths are intentionally excluded", () => {
    const unimplementedPaths: Array<{
      openapiPath: string;
      // Explicit client-side substring to assert is absent.
      // When the path is parameterised and shares a prefix with an implemented
      // path (e.g. /analytics/query vs /analytics/query/{id}), provide the
      // most specific distinguishing substring instead of the full stripped path.
      absentSubstring?: string;
      reason: string;
    }> = [
      {
        openapiPath: "/api/v1/analytics/queries/{queryId}",
        reason:
          "Cancellation endpoint — marked x-capability-required: analytics-query-cancellation " +
          "and flagged UNSUPPORTED in the OpenAPI spec; client does not call it.",
      },
      {
        openapiPath: "/api/v1/analytics/query/{queryId}",
        // Use the trailing slash to distinguish from POST /analytics/query.
        absentSubstring: "/analytics/query/",
        reason:
          "Async result retrieval — the current synchronous query model returns results " +
          "inline from POST /analytics/query; polling by queryId is not used yet.",
      },
      {
        openapiPath: "/api/v1/analytics/query/{queryId}/plan",
        absentSubstring: "/analytics/query/",
        reason:
          "Async execution plan retrieval — same as result retrieval: synchronous explain " +
          "via POST /analytics/explain is used instead.",
      },
      {
        openapiPath: "/api/v1/analytics/aggregation",
        reason:
          "Aggregation endpoint — not yet consumed by the Data Cloud UI. " +
          "Aggregations are performed inline by ClickHouse-backed queries.",
      },
    ];

    for (const { openapiPath, absentSubstring, reason } of unimplementedPaths) {
      it(`'${openapiPath}' is declared in the contract but absent from the client (expected)`, () => {
        // The OpenAPI contract must declare the path.
        expect(
          openApiSpec,
          `data-cloud.yaml must still declare '${openapiPath}'`,
        ).toContain(openapiPath);

        // The client must NOT call the absent segment.
        // Use an explicit absentSubstring when the auto-derived one would
        // collide with an implemented path prefix.
        const segment =
          absentSubstring ??
          openapiPath
            .replace("/api/v1", "")
            .replace(/{[^}]+}/g, "")
            .replace(/\/$/, "");

        expect(
          serviceSource,
          `analytics.service.ts must NOT call '${segment}' — intentionally unimplemented: ${reason}`,
        ).not.toContain(`"${segment}"`);
      });
    }
  });

  // ---------------------------------------------------------------------------
  // 3. The analytics tag group is present in the OpenAPI contract.
  // ---------------------------------------------------------------------------
  it("OpenAPI contract declares an analytics tag group", () => {
    expect(openApiSpec).toContain("name: analytics");
    expect(openApiSpec).toContain("tags: [analytics]");
  });

  // ---------------------------------------------------------------------------
  // 4. All implemented client paths carry the X-Tenant-ID header.
  //    Tenant isolation is a hard requirement for every analytics request.
  // ---------------------------------------------------------------------------
  it("all analytics API calls propagate the X-Tenant-ID header", () => {
    // Each call site that uses apiClient.post or apiClient.get for analytics
    // must pass the tenant header.  We count header inclusions vs. call sites.
    const callSitePattern =
      /apiClient\.(post|get|delete)<[^>]*>\s*\(\s*"\/analytics/g;
    const headerPattern = /"X-Tenant-ID"/g;

    const callSites = [...serviceSource.matchAll(callSitePattern)];
    const headerOccurrences = [...serviceSource.matchAll(headerPattern)];

    expect(callSites.length).toBeGreaterThan(0);
    // Every analytics call site must have a corresponding X-Tenant-ID header.
    expect(headerOccurrences.length).toBeGreaterThanOrEqual(callSites.length);
  });
});
