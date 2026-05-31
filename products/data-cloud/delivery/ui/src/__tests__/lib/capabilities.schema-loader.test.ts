import { beforeEach, describe, expect, it, vi } from "vitest";

type CapabilitiesModule = typeof import("@/lib/capabilities");

async function loadCapabilitiesModule(): Promise<CapabilitiesModule> {
  vi.resetModules();
  return import("@/lib/capabilities");
}

describe("capability schema loader endpoint sequencing", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("prefers the canonical /surfaces/schema endpoint", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === "/api/v1/surfaces/schema") {
        return {
          ok: true,
          status: 200,
          statusText: "OK",
          json: async () => ({
            data: {
              version: "test-version",
              metadata: {
                description: "schema",
                last_updated: "2026-05-08",
                generators: ["test"],
              },
              kernel_capabilities: [],
              data_cloud_capabilities: [],
              aep_capabilities: [],
              ui_feature_gates: [],
              status_definitions: {
                stable: {
                  description: "stable",
                  ui_indicator: "green",
                  allowed_in_production: true,
                },
                preview: {
                  description: "preview",
                  ui_indicator: "amber",
                  allowed_in_production: false,
                },
                deprecated: {
                  description: "deprecated",
                  ui_indicator: "red",
                  allowed_in_production: false,
                },
                experimental: {
                  description: "experimental",
                  ui_indicator: "purple",
                  allowed_in_production: false,
                },
              },
            },
          }),
        };
      }

      throw new Error(`Unexpected fetch url: ${url}`);
    });

    vi.stubGlobal("fetch", fetchMock);

    const capabilities = await loadCapabilitiesModule();
    const schema = await capabilities.loadCapabilitySchema();

    expect(schema.version).toBe("test-version");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/surfaces/schema");
  });

  // DC-P1.12: Removed fallback test for /capabilities/schema; use canonical /api/v1/surfaces/schema only
});
