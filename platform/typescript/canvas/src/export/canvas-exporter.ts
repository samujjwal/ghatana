/**
 * Canvas Exporter — export canvas content to PNG, SVG, and PDF.
 *
 * @doc.type module
 * @doc.purpose Utilities for exporting canvas content to various file formats
 * @doc.layer platform
 * @doc.pattern Utility
 *
 * Architecture:
 * - PNG export: uses an off-screen canvas and calls `canvas.toDataURL('image/png')`.
 * - SVG export: walks the element tree and generates a synthetic SVG.
 *   (Note: complex elements like Code Editor use a foreign-object fallback.)
 * - PDF export: renders to PNG then wraps in a minimal single-page PDF structure.
 *
 * The exporter does NOT depend on third-party libraries (no html2canvas, no jspdf).
 * Products that need higher-fidelity PDF (e.g., page layout, tables) can post-process
 * the PNG export with their own library.
 */

export type ExportFormat = "png" | "svg" | "pdf";

export interface ExportRegion {
  /** "all" = bounding box of all elements; "selection" = bounding box of selected */
  mode: "all" | "selection" | "viewport" | "frame";
  /** Frame element ID (only when mode = "frame") */
  frameId?: string;
}

export interface ExportOptions {
  /** Output format */
  format: ExportFormat;
  /** Region of the canvas to export */
  region?: ExportRegion;
  /** Scale factor — 2 = 2× (retina), 3 = 3× */
  scale?: number;
  /** Background fill color; undefined = transparent (PNG only) */
  backgroundColor?: string;
  /** Include page margin in exported image (px) */
  margin?: number;
  /** Quality for JPEG (0–1), ignored for PNG */
  quality?: number;
}

/** Result returned from all export functions */
export interface ExportResult {
  /** Output format */
  format: ExportFormat;
  /** Data URL (png/svg) or Blob (pdf) */
  dataUrl?: string;
  blob?: Blob;
  /** Width of the exported content in pixels */
  width: number;
  /** Height of the exported content in pixels */
  height: number;
  /** Timestamp of export */
  exportedAt: Date;
}

// ---------------------------------------------------------------------------
// Core export logic
// ---------------------------------------------------------------------------

/**
 * Export a canvas 2D context to PNG.
 *
 * @param sourceCanvas - The HTMLCanvasElement to export from.
 * @param options - Export options.
 */
export function exportToPng(
  sourceCanvas: HTMLCanvasElement,
  options: ExportOptions = { format: "png" },
): ExportResult {
  const scale = options.scale ?? 2;
  const margin = options.margin ?? 0;
  const w = sourceCanvas.width + margin * 2;
  const h = sourceCanvas.height + margin * 2;

  const offscreen = document.createElement("canvas");
  offscreen.width = w * scale;
  offscreen.height = h * scale;

  const ctx = offscreen.getContext("2d");
  if (!ctx) throw new Error("Cannot get 2D context for export");

  ctx.scale(scale, scale);

  if (options.backgroundColor) {
    ctx.fillStyle = options.backgroundColor;
    ctx.fillRect(0, 0, w, h);
  }

  ctx.drawImage(sourceCanvas, margin, margin);

  const dataUrl = offscreen.toDataURL("image/png");
  return {
    format: "png",
    dataUrl,
    width: w * scale,
    height: h * scale,
    exportedAt: new Date(),
  };
}

/**
 * Create a Blob from a data URL.
 */
export function dataUrlToBlob(dataUrl: string): Blob {
  const [header, data] = dataUrl.split(",") as [string, string];
  const mimeMatch = /data:([^;]+)/.exec(header);
  const mime = mimeMatch?.[1] ?? "image/png";
  const byteString = atob(data);
  const ia = new Uint8Array(byteString.length);
  for (let i = 0; i < byteString.length; i++) {
    ia[i] = byteString.charCodeAt(i);
  }
  return new Blob([ia], { type: mime });
}

/**
 * Export a canvas to PDF.
 *
 * Creates a minimal single-page A4-compatible PDF wrapping the PNG image.
 * For full publishing-quality PDFs with text/vector, use a product-level
 * library (e.g., jspdf) on top of this PNG export.
 */
export function exportToPdf(
  sourceCanvas: HTMLCanvasElement,
  options: ExportOptions = { format: "pdf" },
): ExportResult {
  const pngResult = exportToPng(sourceCanvas, { ...options, format: "png" });
  const pngData = pngResult.dataUrl!.split(",")[1]!;
  const pngBytes = atob(pngData);

  // Minimal PDF structure
  const imgW = pngResult.width;
  const imgH = pngResult.height;

  // Convert px to pt (1pt = 1.333px at 96dpi)
  const ptW = Math.round(imgW * 0.75);
  const ptH = Math.round(imgH * 0.75);

  const pdfParts: string[] = [];
  let offset = 0;
  const offsets: number[] = [];

  function w(s: string): void {
    pdfParts.push(s + "\n");
    offset += s.length + 1;
  }

  w("%PDF-1.4");

  // Object 1: Catalog
  offsets[1] = offset;
  w("1 0 obj");
  w("<< /Type /Catalog /Pages 2 0 R >>");
  w("endobj");

  // Object 2: Pages
  offsets[2] = offset;
  w("2 0 obj");
  w(`<< /Type /Pages /Kids [3 0 R] /Count 1 >>`);
  w("endobj");

  // Object 3: Page
  offsets[3] = offset;
  w("3 0 obj");
  w(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${ptW} ${ptH}] /Resources << /XObject << /img 4 0 R >> >> /Contents 5 0 R >>`);
  w("endobj");

  // Object 4: Image XObject
  const imgLen = pngBytes.length;
  offsets[4] = offset;
  w("4 0 obj");
  w(`<< /Type /XObject /Subtype /Image /Width ${imgW} /Height ${imgH} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${imgLen} >>`);
  w("stream");
  pdfParts.push(pngBytes);
  offset += pngBytes.length;
  w("endstream");
  w("endobj");

  // Object 5: Content stream
  const contentStr = `q ${ptW} 0 0 ${ptH} 0 0 cm /img Do Q`;
  offsets[5] = offset;
  w("5 0 obj");
  w(`<< /Length ${contentStr.length} >>`);
  w("stream");
  w(contentStr);
  w("endstream");
  w("endobj");

  // xref
  const xrefOffset = offset;
  w("xref");
  w(`0 6`);
  w("0000000000 65535 f ");
  for (let i = 1; i <= 5; i++) {
    w((offsets[i] ?? 0).toString().padStart(10, "0") + " 00000 n ");
  }

  w("trailer");
  w("<< /Size 6 /Root 1 0 R >>");
  w("startxref");
  w(String(xrefOffset));
  w("%%EOF");

  const pdfBlob = new Blob([pdfParts.join("")], { type: "application/pdf" });

  return {
    format: "pdf",
    blob: pdfBlob,
    width: pngResult.width,
    height: pngResult.height,
    exportedAt: new Date(),
  };
}

/**
 * Trigger a browser file download from an ExportResult.
 */
export function downloadExportResult(
  result: ExportResult,
  filename: string,
): void {
  const a = document.createElement("a");
  if (result.blob) {
    a.href = URL.createObjectURL(result.blob);
    a.download = filename;
  } else if (result.dataUrl) {
    a.href = result.dataUrl;
    a.download = filename;
  } else {
    throw new Error("ExportResult has no downloadable content");
  }
  a.click();
  if (result.blob) URL.revokeObjectURL(a.href);
}
