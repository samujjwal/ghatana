/**
 * @file newElements.test.ts
 * Tests for newly added canvas elements: VideoElement, AudioElement,
 * DataChartElement, DataMetricElement, WhiteboardElement, PortalElement.
 *
 * @doc.type module
 * @doc.purpose Tests for new canvas element types
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import type { BaseElementProps, CanvasElementType } from "../types/index.js";

// ---------------------------------------------------------------------------
// VideoElement
// ---------------------------------------------------------------------------

describe("VideoElement", () => {
  it("constructs with default state", async () => {
    const { VideoElement } = await import("../elements/video.js");
    const el = new VideoElement({
      id: "v1",
      xywh: JSON.stringify([0, 0, 320, 180]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a0",
    });
    expect(el.type).toBe("video");
    expect(el.isPlaying).toBe(false);
    expect(el.currentTime).toBe(0);
    expect(el.volume).toBe(1);
    expect(el.loop).toBe(false);
  });

  it("has correct element type", async () => {
    const { VideoElement } = await import("../elements/video.js");
    const el = new VideoElement({
      id: "v2",
      xywh: JSON.stringify([0, 0, 100, 100]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a1",
    });
    const t: CanvasElementType = el.type;
    expect(t).toBe("video");
  });
});

// ---------------------------------------------------------------------------
// AudioElement
// ---------------------------------------------------------------------------

describe("AudioElement", () => {
  it("constructs with default state", async () => {
    const { AudioElement } = await import("../elements/audio.js");
    const el = new AudioElement({
      id: "au1",
      xywh: JSON.stringify([0, 0, 300, 80]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a2",
    });
    expect(el.type).toBe("audio");
    expect(el.isPlaying).toBe(false);
    expect(el.currentTime).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// DataChartElement
// ---------------------------------------------------------------------------

describe("DataChartElement", () => {
  it("constructs with default chart config", async () => {
    const { DataChartElement } = await import("../elements/data-chart.js");
    const el = new DataChartElement({
      id: "dc1",
      xywh: JSON.stringify([0, 0, 400, 300]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a3",
    });
    expect(el.type).toBe("data-chart");
    expect(el.chartType).toBe("bar");
    expect(el.series).toEqual([]);
  });

  it("updates series via updateSeries()", async () => {
    const { DataChartElement } = await import("../elements/data-chart.js");
    const el = new DataChartElement({
      id: "dc2",
      xywh: JSON.stringify([0, 0, 400, 300]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a4",
    });

    const series = [
      {
        name: "Revenue",
        data: [{ label: "Q1", value: 10 }, { label: "Q2", value: 20 }],
        color: "#6366f1",
      },
    ];
    el.updateSeries(series);
    expect(el.series).toHaveLength(1);
    expect(el.series[0]?.name).toBe("Revenue");
  });

  it("supports all required chart types", async () => {
    const { DataChartElement } = await import("../elements/data-chart.js");
    const types = ["bar", "line", "area", "pie", "donut", "sparkline"] as const;
    for (const chartType of types) {
      const el = new DataChartElement({
        id: `d-${chartType}`,
        xywh: JSON.stringify([0, 0, 200, 150]) as BaseElementProps["xywh"],
        rotate: 0,
        index: "a0",
      });
      el.chartType = chartType;
      expect(el.chartType).toBe(chartType);
    }
  });
});

// ---------------------------------------------------------------------------
// DataMetricElement
// ---------------------------------------------------------------------------

describe("DataMetricElement", () => {
  it("constructs with default KPI state", async () => {
    const { DataMetricElement } = await import("../elements/data-metric.js");
    const el = new DataMetricElement({
      id: "dm1",
      xywh: JSON.stringify([0, 0, 200, 120]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a5",
    });
    expect(el.type).toBe("data-metric");
    expect(el.status).toBe("unknown");
    expect(el.trend).toBe("neutral");
  });

  it("updateValue changes value and recomputes status", async () => {
    const { DataMetricElement } = await import("../elements/data-metric.js");
    const el = new DataMetricElement({
      id: "dm2",
      xywh: JSON.stringify([0, 0, 200, 120]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a6",
      label: "Error rate",
      value: 0,
      threshold: { warning: 80, critical: 90, higherIsBad: true },
    });
    el.updateValue(75);
    expect(el.value).toBe(75);
    expect(el.status).toBe("healthy");

    el.updateValue(85);
    expect(el.status).toBe("warning");

    el.updateValue(95);
    expect(el.status).toBe("critical");
  });
});

// ---------------------------------------------------------------------------
// WhiteboardElement
// ---------------------------------------------------------------------------

describe("WhiteboardElement", () => {
  it("constructs with empty strokes", async () => {
    const { WhiteboardElement } = await import("../elements/whiteboard.js");
    const el = new WhiteboardElement({
      id: "wb1",
      xywh: JSON.stringify([0, 0, 800, 600]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a7",
    });
    expect(el.type).toBe("whiteboard");
    expect(el.strokes).toHaveLength(0);
  });

  it("addStroke / removeLastStroke round-trip", async () => {
    const { WhiteboardElement } = await import("../elements/whiteboard.js");
    const el = new WhiteboardElement({
      id: "wb2",
      xywh: JSON.stringify([0, 0, 800, 600]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a8",
    });

    el.addStroke({
      id: "s1",
      mode: "pen",
      color: "#000",
      width: 2,
      opacity: 1,
      points: [{ x: 0, y: 0, pressure: 1 }],
      smoothed: [],
    });
    expect(el.strokes).toHaveLength(1);

    el.removeLastStroke();
    expect(el.strokes).toHaveLength(0);
  });

  it("clearStrokes empties all strokes", async () => {
    const { WhiteboardElement } = await import("../elements/whiteboard.js");
    const el = new WhiteboardElement({
      id: "wb3",
      xywh: JSON.stringify([0, 0, 800, 600]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "a9",
    });

    for (let i = 0; i < 5; i++) {
      el.addStroke({
        id: `s${i}`,
        mode: "pen",
        color: "#000",
        width: 2,
        opacity: 1,
        points: [],
        smoothed: [],
      });
    }
    expect(el.strokes).toHaveLength(5);
    el.clearStrokes();
    expect(el.strokes).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// PortalElement
// ---------------------------------------------------------------------------

describe("PortalElement", () => {
  it("constructs with default portal state", async () => {
    const { PortalElement } = await import("../elements/portal.js");
    const el = new PortalElement({
      id: "p1",
      xywh: JSON.stringify([0, 0, 160, 100]) as BaseElementProps["xywh"],
      rotate: 0,
      index: "b0",
    });
    expect(el.type).toBe("portal");
    expect(el.locked).toBe(false);
    expect(el.targetDocumentId).toBeUndefined();
  });
});
