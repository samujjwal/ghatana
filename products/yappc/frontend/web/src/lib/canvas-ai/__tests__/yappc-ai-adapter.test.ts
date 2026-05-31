import { describe, expect, it, vi, afterEach } from "vitest";
import { YappcCanvasAIAdapter, canvasAIReadinessUnavailable } from "../yappc-ai-adapter";
import type { CanvasAIContext } from "@ghatana/canvas/ai";

const context: CanvasAIContext = {
  selectedElementIds: [],
  activeLayer: "main",
  visibleElementIds: [],
  userQuery: "build a Product dashboard",
  productMetadata: { productId: "sample-product" },
};

describe("YappcCanvasAIAdapter", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns a degraded readiness suggestion when suggestions endpoint is unavailable", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ message: "generation backend unavailable" }), {
        status: 503,
        statusText: "Service Unavailable",
        headers: { "Content-Type": "application/json" },
      }),
    );

    const suggestions = await new YappcCanvasAIAdapter().getSuggestions(context);

    expect(suggestions).toHaveLength(1);
    expect(suggestions[0]?.id).toBe("canvas-ai-readiness-degraded");
    expect(suggestions[0]?.payload).toMatchObject({
      readiness: {
        status: "degraded",
        reason: expect.stringContaining("generation backend unavailable"),
        retryable: true,
      },
    });
  });

  it("throws explicit readiness errors for unavailable element generation", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ error: "missing /api/canvas/generate-elements" }), {
        status: 404,
        statusText: "Not Found",
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(new YappcCanvasAIAdapter().generateElements("Product route", context))
      .rejects.toThrow("Canvas AI element generation unavailable");
  });

  it("throws explicit readiness errors for unavailable auto-layout", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ message: "layout service not ready" }), {
        status: 503,
        statusText: "Service Unavailable",
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(new YappcCanvasAIAdapter().autoLayout(context))
      .rejects.toThrow("Canvas AI auto-layout unavailable");
  });

  it("builds a typed degraded readiness payload", () => {
    const readiness = canvasAIReadinessUnavailable(new Error("readiness probe failed"));

    expect(readiness).toEqual({
      status: "degraded",
      reason: "readiness probe failed",
      retryable: true,
    });
  });
});
