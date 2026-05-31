import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getAiQualitySummary } from "../../api/ai-observability.service";
import SessionBootstrap from "../../lib/auth/session";
import {
  AI_OBSERVABILITY_DISABLED_BOUNDARY_MESSAGE,
  UnsupportedRuntimeBoundaryError,
} from "../../lib/runtime-boundaries";
import { TEST_TENANT_ID } from "../test-utils/tenants";

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}));

vi.mock("../../lib/api/client", () => ({
  apiClient: {
    get: getMock,
  },
}));

describe("ai-observability.service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
    vi.unstubAllEnvs();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("loads the canonical AI quality summary with tenant scoping", async () => {
    getMock.mockResolvedValue({
      data: {
        generatedAt: "2026-04-18T12:40:00Z",
        scope: "launcher-process",
        summary: {
          requestCount: 8,
          fallbackCount: 2,
          fallbackRate: 0.25,
          llmConfigured: true,
        },
        types: [
          {
            type: "analytics_suggest",
            label: "Analytics suggestions",
            route: "/api/v1/analytics/suggest",
            requestCount: 5,
            fallbackCount: 1,
            fallbackRate: 0.2,
            meanConfidence: 0.82,
            provenanceMode: "ai-envelope",
            reviewGuidance:
              "Fallback-heavy analytics suggestions should trigger manual SQL review before execution.",
          },
        ],
      },
      meta: {
        requestId: "req-ai-quality",
        tenantId: TEST_TENANT_ID,
        timestamp: "2026-04-18T12:40:00Z",
        apiVersion: "v1",
      },
    });

    const result = await getAiQualitySummary();

    expect(getMock).toHaveBeenCalledWith("/ai/quality-summary", {
      headers: { "X-Tenant-ID": TEST_TENANT_ID },
    });
    expect(result.summary.requestCount).toBe(8);
    expect(result.types[0]?.type).toBe("analytics_suggest");
  });

  it("getAiQualitySummary throws UnsupportedRuntimeBoundaryError when AI operations gate is off", async () => {
    vi.stubEnv("VITE_FEATURE_AI_OPERATIONS", "false");

    await expect(getAiQualitySummary()).rejects.toThrow(
      UnsupportedRuntimeBoundaryError,
    );
    await expect(getAiQualitySummary()).rejects.toThrow(
      AI_OBSERVABILITY_DISABLED_BOUNDARY_MESSAGE,
    );
    expect(getMock).not.toHaveBeenCalled();
  });
});
