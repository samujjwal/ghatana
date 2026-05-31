import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildDeprecatedRouteHeaders,
  COLLECTION_RUNTIME_OPENAPI_PATHS,
  DEPRECATED_COLLECTION_ROUTE_REDIRECTS,
  DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS,
  warnDeprecatedRoute,
} from "../../../test-fixtures/deprecatedRoutes";

describe("deprecatedRoutes", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("emits an explicit deprecation warning for legacy collection routes", () => {
    const diagnosticListener = vi.fn();
    globalThis.addEventListener(
      "data-cloud-diagnostic",
      diagnosticListener as EventListener,
    );

    const warning = warnDeprecatedRoute(
      "/api/v1/collections",
      "/api/v1/entities/dc_collections",
    );

    expect(warning).toContain("/api/v1/collections");
    expect(warning).toContain("/api/v1/entities/dc_collections");
    expect(diagnosticListener).toHaveBeenCalledOnce();
    expect(
      (diagnosticListener.mock.calls[0][0] as CustomEvent).detail,
    ).toMatchObject({
      source: "deprecatedRoutes",
      level: "warn",
      message: warning,
      context: {
        legacyPath: "/api/v1/collections",
        canonicalPath: "/api/v1/entities/dc_collections",
      },
    });

    globalThis.removeEventListener(
      "data-cloud-diagnostic",
      diagnosticListener as EventListener,
    );
  });

  it("builds redirect headers that preserve replacement route metadata", () => {
    expect(
      buildDeprecatedRouteHeaders(
        "/api/v1/collections/orders",
        "/api/v1/entities/dc_collections/orders",
        "/api/v1/entities/dc_collections/orders",
      ),
    ).toEqual({
      Location: "/api/v1/entities/dc_collections/orders",
      Warning:
        '299 - "[DEPRECATED API ROUTE] /api/v1/collections/orders is deprecated; use /api/v1/entities/dc_collections/orders"',
      "X-Deprecated-Route": "/api/v1/collections/orders",
      "X-Replacement-Route": "/api/v1/entities/dc_collections/orders",
    });
  });

  it("tracks canonical collection runtime paths and deprecated CRUD aliases centrally", () => {
    expect(COLLECTION_RUNTIME_OPENAPI_PATHS).toEqual([
      "/api/v1/entities/{collection}",
      "/api/v1/entities/{collection}/{id}",
      "/api/v1/collections/{id}/cost-report",
      "/api/v1/collections/{id}/migrate",
    ]);
    expect(DEPRECATED_COLLECTION_ROUTE_REDIRECTS).toEqual([]);
    // DC-P1.12: Removed compatibility /api/v1/capabilities from DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS
    expect(DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS).toEqual([]);
  });
});
