import { CanvasElement } from "../elements/base.js";
import { ShapeElement, type ShapeProps } from "../elements/shape.js";
import { TextElement } from "../elements/text.js";
import { ConnectorElement, type ConnectorProps } from "../elements/connector.js";
import { BrushElement, type BrushProps } from "../elements/brush.js";
import { CodeElement, type CodeProps } from "../elements/code.js";
import { DiagramElement, type DiagramProps } from "../elements/diagram.js";
import { GroupElement, type GroupProps } from "../elements/group.js";
import { FrameElement, type FrameProps } from "../elements/frame.js";
import { NoteElement, type NoteProps } from "../elements/note.js";
import { ImageElement, type ImageProps } from "../elements/image.js";
import { PipelineNodeElement, type PipelineNodeProps } from "../elements/pipeline-node.js";
import { themeManager } from "../theme/index.js";

const emitCanvasImportExportDiagnostic = (
  level: "warn" | "error",
  message: string,
  context?: Record<string, unknown>,
): void => {
  if (typeof globalThis.dispatchEvent !== "function" || typeof CustomEvent === "undefined") {
    return;
  }

  globalThis.dispatchEvent(
    new CustomEvent("canvas-import-export-diagnostic", {
      detail: {
        level,
        message,
        context,
        timestamp: new Date().toISOString(),
      },
    }),
  );
};

export interface ExportOptions {
  format: "png" | "svg" | "json";
  quality?: number;
  backgroundColor?: string;
  includeGrid?: boolean;
  scale?: number;
}

export interface ImportOptions {
  format: "json";
  mergeWithExisting?: boolean;
}

export class CanvasExporter {
  static exportCanvas(
    elements: CanvasElement[],
    options: ExportOptions,
  ): Promise<Blob | string> {
    switch (options.format) {
      case "png":
        return this.exportAsPNG(elements, options);
      case "svg":
        return this.exportAsSVG(elements, options);
      case "json":
        return this.exportAsJSON(elements, options);
      default:
        throw new Error(`Unsupported export format: ${options.format}`);
    }
  }

  static importCanvas(
    data: string | Blob,
    options: ImportOptions,
  ): Promise<CanvasElement[]> {
    switch (options.format) {
      case "json":
        return this.importFromJSON(data);
      default:
        throw new Error(`Unsupported import format: ${options.format}`);
    }
  }

  private static exportAsPNG(
    elements: CanvasElement[],
    options: ExportOptions,
  ): Promise<Blob> {
    return new Promise((resolve, reject) => {
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d")!;

      if (!ctx) {
        reject(new Error("Failed to get 2D context"));
        return;
      }

      // Calculate canvas bounds
      const bounds = this.calculateBounds(elements);
      const scale = options.scale || 1;

      canvas.width = bounds.width * scale;
      canvas.height = bounds.height * scale;

      // Set background
      if (options.backgroundColor) {
        ctx.fillStyle = options.backgroundColor;
      } else {
        ctx.fillStyle = themeManager.getColor("colors.background");
      }
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // Apply scale
      ctx.scale(scale, scale);
      ctx.translate(-bounds.x, -bounds.y);

      // Draw grid if requested
      if (options.includeGrid) {
        this.drawGrid(ctx, bounds);
      }

      // Draw elements
      for (const element of elements) {
        try {
          element.render(ctx);
        } catch (error) {
          emitCanvasImportExportDiagnostic("error", "Error rendering element", {
            error,
            elementId: element.id,
            elementType: element.type,
          });
        }
      }

      // Convert to blob
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(blob);
          } else {
            reject(new Error("Failed to create blob"));
          }
        },
        "image/png",
        options.quality || 0.9,
      );
    });
  }

  private static exportAsSVG(
    elements: CanvasElement[],
    options: ExportOptions,
  ): Promise<string> {
    return new Promise((resolve) => {
      const bounds = this.calculateBounds(elements);

      let svg = `<svg width="${bounds.width}" height="${bounds.height}" xmlns="http://www.w3.org/2000/svg">`;

      // Add background
      const bgColor =
        options.backgroundColor || themeManager.getColor("colors.background");
      svg += `<rect width="${bounds.width}" height="${bounds.height}" fill="${bgColor}"/>`;

      // Add grid if requested
      if (options.includeGrid) {
        svg += this.generateSVGGrid(bounds);
      }

      // Add elements (simplified representation)
      for (const element of elements) {
        svg += this.elementToSVG(element, bounds);
      }

      svg += "</svg>";
      resolve(svg);
    });
  }

  private static exportAsJSON(
    elements: CanvasElement[],
    options: ExportOptions,
  ): Promise<string> {
    return new Promise((resolve) => {
      const data = {
        version: "1.0",
        timestamp: new Date().toISOString(),
        elements: elements.map((element) => this.elementToJSON(element)),
        metadata: {
          bounds: this.calculateBounds(elements),
          elementCount: elements.length,
          theme: themeManager.getTheme().name,
        },
      };

      resolve(JSON.stringify(data, null, 2));
    });
  }

  private static importFromJSON(data: string | Blob): Promise<CanvasElement[]> {
    return new Promise((resolve, reject) => {
      const jsonString = typeof data === "string" ? data : data.toString();

      try {
        const parsed = JSON.parse(jsonString);

        if (!parsed.elements || !Array.isArray(parsed.elements)) {
          reject(new Error("Invalid JSON format"));
          return;
        }

        const elements: CanvasElement[] = [];

        for (const elementData of parsed.elements) {
          try {
            const element = this.jsonToElement(elementData);
            if (element) {
              elements.push(element);
            }
          } catch (error) {
            emitCanvasImportExportDiagnostic("error", "Error importing element", {
              error,
              elementData,
            });
          }
        }

        resolve(elements);
      } catch (error) {
        reject(new Error("Failed to parse JSON"));
      }
    });
  }

  private static calculateBounds(elements: CanvasElement[]): {
    x: number;
    y: number;
    width: number;
    height: number;
  } {
    if (elements.length === 0) {
      return { x: 0, y: 0, width: 800, height: 600 };
    }

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const element of elements) {
      const bound = element.getBounds();
      minX = Math.min(minX, bound.x);
      minY = Math.min(minY, bound.y);
      maxX = Math.max(maxX, bound.x + bound.w);
      maxY = Math.max(maxY, bound.y + bound.h);
    }

    const padding = 40;
    return {
      x: minX - padding,
      y: minY - padding,
      width: maxX - minX + padding * 2,
      height: maxY - minY + padding * 2,
    };
  }

  private static drawGrid(
    ctx: CanvasRenderingContext2D,
    bounds: { x: number; y: number; width: number; height: number },
  ): void {
    const gridSize = 20;
    const theme = themeManager.getTheme();

    ctx.strokeStyle = theme.colors.canvas.grid;
    ctx.lineWidth = 0.5;

    // Calculate grid boundaries
    const startX = Math.floor(bounds.x / gridSize) * gridSize;
    const startY = Math.floor(bounds.y / gridSize) * gridSize;
    const endX = bounds.x + bounds.width;
    const endY = bounds.y + bounds.height;

    // Draw vertical lines
    for (let x = startX; x <= endX; x += gridSize) {
      ctx.beginPath();
      ctx.moveTo(x, startY);
      ctx.lineTo(x, endY);
      ctx.stroke();
    }

    // Draw horizontal lines
    for (let y = startY; y <= endY; y += gridSize) {
      ctx.beginPath();
      ctx.moveTo(startX, y);
      ctx.lineTo(endX, y);
      ctx.stroke();
    }
  }

  private static generateSVGGrid(bounds: {
    x: number;
    y: number;
    width: number;
    height: number;
  }): string {
    const gridSize = 20;
    const theme = themeManager.getTheme();
    let svg = "";

    const startX = Math.floor(bounds.x / gridSize) * gridSize;
    const startY = Math.floor(bounds.y / gridSize) * gridSize;
    const endX = bounds.x + bounds.width;
    const endY = bounds.y + bounds.height;

    // Vertical lines
    for (let x = startX; x <= endX; x += gridSize) {
      svg += `<line x1="${x}" y1="${startY}" x2="${x}" y2="${endY}" stroke="${theme.colors.canvas.grid}" stroke-width="0.5"/>`;
    }

    // Horizontal lines
    for (let y = startY; y <= endY; y += gridSize) {
      svg += `<line x1="${startX}" y1="${y}" x2="${endX}" y2="${y}" stroke="${theme.colors.canvas.grid}" stroke-width="0.5"/>`;
    }

    return svg;
  }

  private static elementToSVG(
    element: CanvasElement,
    bounds: { x: number; y: number; width: number; height: number },
  ): string {
    const elementBounds = element.getBounds();

    // Simplified SVG representation
    switch (element.type) {
      case "shape":
        return `<rect x="${elementBounds.x - bounds.x}" y="${elementBounds.y - bounds.y}" width="${elementBounds.w}" height="${elementBounds.h}" fill="#3b82f6" stroke="#1e40af" stroke-width="2"/>`;
      case "text":
        return `<text x="${elementBounds.x - bounds.x}" y="${elementBounds.y - bounds.y + elementBounds.h / 2}" fill="#1f2937" font-family="Arial" font-size="16">Text Element</text>`;
      case "connector":
        return `<line x1="${elementBounds.x - bounds.x}" y1="${elementBounds.y - bounds.y}" x2="${elementBounds.x + elementBounds.w - bounds.x}" y2="${elementBounds.y + elementBounds.h - bounds.y}" stroke="#64748b" stroke-width="2"/>`;
      default:
        return `<rect x="${elementBounds.x - bounds.x}" y="${elementBounds.y - bounds.y}" width="${elementBounds.w}" height="${elementBounds.h}" fill="#e5e7eb" stroke="#9ca3af" stroke-width="1"/>`;
    }
  }

  private static elementToJSON(element: CanvasElement): Record<string, unknown> {
    const record = element as unknown as Record<string, unknown>;
    return {
      type: element.type,
      id: element.id,
      xywh: element.xywh,
      rotate: element.rotate,
      index: element.index,
      // Add element-specific properties
      ...(record.shapeType !== undefined ? {
        shapeType: record.shapeType,
      } : {}),
      ...(record.text !== undefined ? { text: record.text } : {}),
      ...(record.color !== undefined ? { color: record.color } : {}),
      ...(record.fillColor !== undefined ? {
        fillColor: record.fillColor,
      } : {}),
      ...(record.strokeColor !== undefined ? {
        strokeColor: record.strokeColor,
      } : {}),
    };
  }

  private static jsonToElement(data: Record<string, unknown>): CanvasElement | null {
    // Every element requires at minimum: id, xywh, type
    if (!data.id || !data.xywh || !data.type) {
      emitCanvasImportExportDiagnostic("warn", "Skipping element missing required fields", { data });
      return null;
    }

    // Build the shared base props from the JSON record
    const base = {
      id: data.id as string,
      xywh: data.xywh as string,
      rotate: (data.rotate as number) ?? 0,
      index: (data.index as string) ?? "0",
    };

    try {
      switch (data.type as string) {
        case "shape":
          return new ShapeElement({
            ...base,
            shapeType: (data.shapeType as "rect" | "circle" | "diamond" | "triangle" | "ellipse" | "star") ?? "rect",
            fillColor: (data.fillColor as string) ?? "#e5e7eb",
            strokeColor: (data.strokeColor as string) ?? "#9ca3af",
            strokeWidth: (data.strokeWidth as number) ?? 2,
            filled: (data.filled as boolean) ?? true,
            text: data.text as string | undefined,
            color: data.color as string | undefined,
          } as ShapeProps);

        case "text":
          return new TextElement({
            ...base,
            text: (data.text as string) ?? "",
            fontSize: (data.fontSize as number) ?? 16,
            fontFamily: (data.fontFamily as string) ?? "sans-serif",
            color: (data.color as string) ?? "#1f2937",
            textAlign: data.textAlign as "left" | "center" | "right" | undefined,
          });

        case "connector":
          return new ConnectorElement({
            ...base,
            // ConnectorElement accepts just BaseElementProps; other fields are optional
          } as ConnectorProps);

        case "brush":
          return new BrushElement({
            ...base,
            points: ((data.points as number[][]) ?? []).map((point) => ({
              x: point[0] ?? 0,
              y: point[1] ?? 0,
              pressure: point[2],
            })),
            color: (data.color as string) ?? "#1f2937",
            lineWidth: (data.lineWidth as number) ?? 2,
          } as BrushProps);

        case "code":
          return new CodeElement({
            ...base,
            code: (data.code as string) ?? "",
            language: (data.language as string) ?? "plaintext",
          } as CodeProps);

        case "diagram":
          return new DiagramElement({
            ...base,
            diagramType: (data.diagramType as "flowchart" | "mindmap" | "sequence" | "class") ?? "flowchart",
            nodes: (data.nodes as DiagramProps["nodes"]) ?? [],
            connections: (data.connections as DiagramProps["connections"]) ?? [],
            layout: (data.layout as DiagramProps["layout"]) ?? "hierarchical",
          } as DiagramProps);

        case "group":
          return new GroupElement({
            ...base,
            childIds: (data.childIds as string[]) ?? [],
          } as GroupProps);

        case "frame":
          return new FrameElement({
            ...base,
            title: (data.title as string) ?? "",
            background: data.background as string | undefined,
          } as FrameProps);

        case "note":
          return new NoteElement({
            ...base,
            content: (data.content as string) ?? "",
            background: data.background as string | undefined,
          } as NoteProps);

        case "image":
          return new ImageElement({
            ...base,
            src:
              (data.src as string) ??
              (data.url as string) ??
              (data.sourceId as string) ??
              "",
            alt: (data.alt as string) ?? "",
            caption: (data.caption as string) ?? "",
          } as ImageProps);

        case "pipeline-node":
          return new PipelineNodeElement({
            ...base,
            label: (data.label as string) ?? "",
            nodeType: data.nodeType as string | undefined,
          } as PipelineNodeProps);

        default:
          // Unknown element type: skip rather than hard-fail.
          emitCanvasImportExportDiagnostic("warn", "Unsupported element type", {
            elementType: data.type,
          });
          return null;
      }
    } catch (err) {
      emitCanvasImportExportDiagnostic("error", "Failed to construct element", {
        error: err,
        elementType: data.type,
      });
      return null;
    }
  }
}
