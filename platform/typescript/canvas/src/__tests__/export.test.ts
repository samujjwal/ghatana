/**
 * @file export.test.ts
 * Tests for canvas export utilities (PNG / PDF).
 *
 * @doc.type module
 * @doc.purpose Tests for canvas export system
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Minimal browser API stubs needed in jsdom
// ---------------------------------------------------------------------------

const mockCanvas = {
  width: 0,
  height: 0,
  getContext: vi.fn().mockReturnValue({
    clearRect: vi.fn(),
    drawImage: vi.fn(),
    scale: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    fillRect: vi.fn(),
    fillStyle: "",
    imageSmoothingEnabled: true,
    imageSmoothingQuality: "high",
  }),
  toDataURL: vi.fn().mockReturnValue("data:image/png;base64,abc123"),
};

const mockCreateElement = vi.spyOn(document, "createElement").mockImplementation(
  (tag: string) => {
    if (tag === "canvas") return mockCanvas as unknown as HTMLElement;
    return document.createElementNS("http://www.w3.org/1999/xhtml", tag) as HTMLElement;
  },
);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("exportToPng", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCanvas.toDataURL.mockReturnValue("data:image/png;base64,abc123");
    mockCanvas.getContext.mockReturnValue({
      clearRect: vi.fn(),
      drawImage: vi.fn(),
      scale: vi.fn(),
      save: vi.fn(),
      restore: vi.fn(),
      fillRect: vi.fn(),
      fillStyle: "",
      imageSmoothingEnabled: true,
      imageSmoothingQuality: "high",
    });
  });

  it("returns a data URL starting with data:image/png", async () => {
    const { exportToPng } = await import("../export/canvas-exporter.js");

    const sourceCanvas = {
      width: 800,
      height: 600,
    } as HTMLCanvasElement;

    const result = await exportToPng(sourceCanvas, {
      format: "png",
      region: { mode: "all" },
      scale: 1,
    });

    expect(result.dataUrl).toMatch(/^data:image\/png/);
    expect(result.format).toBe("png");
    expect(result.width).toBeGreaterThan(0);
    expect(result.height).toBeGreaterThan(0);
  });

  it("uses provided scale factor", async () => {
    const { exportToPng } = await import("../export/canvas-exporter.js");

    const sourceCanvas = {
      width: 400,
      height: 300,
    } as HTMLCanvasElement;

    const result = await exportToPng(sourceCanvas, {
      format: "png",
      region: { mode: "all" },
      scale: 2,
    });

    // At scale 2, exported dimensions should be 2× the source
    expect(result.width).toBe(800);
    expect(result.height).toBe(600);
  });
});

describe("exportToPdf", () => {
  it("returns a blob with application/pdf content type", async () => {
    const { exportToPdf } = await import("../export/canvas-exporter.js");

    const sourceCanvas = {
      width: 1024,
      height: 768,
    } as HTMLCanvasElement;

    const result = await exportToPdf(sourceCanvas, {
      format: "pdf",
      region: { mode: "all" },
      scale: 1,
    });

    expect(result.format).toBe("pdf");
    expect(result.blob).toBeDefined();
    expect(result.blob?.type).toBe("application/pdf");
  });
});

describe("dataUrlToBlob", () => {
  it("converts a valid data URL to a Blob", async () => {
    const { dataUrlToBlob } = await import("../export/canvas-exporter.js");

    const dataUrl = "data:image/png;base64,iVBORw0KGgo=";
    const blob = dataUrlToBlob(dataUrl);

    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toBe("image/png");
  });
});
