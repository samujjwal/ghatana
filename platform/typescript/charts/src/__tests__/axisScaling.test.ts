/**
 * @file axisScaling.test.ts
 * Tests for axis bound and scale calculations derived from ChartDataPoint arrays —
 * domain clamping, nice-number rounding, and padding.
 *
 * @doc.type module
 * @doc.purpose Tests for chart axis scaling and domain calculation
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect } from "vitest";
import type { ChartDataPoint } from "../types";

// ── Axis scale utilities (co-located helpers for testing) ────────────────────

/** Computes the [min, max] domain from a dataset. */
function computeDomain(points: ChartDataPoint[]): [number, number] {
  if (points.length === 0) return [0, 0];
  const values = points.map((p) => p.value);
  return [Math.min(...values), Math.max(...values)];
}

/** Adds percentage padding to a domain. */
function padDomain(
  domain: [number, number],
  paddingPercent: number,
): [number, number] {
  const range = domain[1] - domain[0];
  const pad = range * (paddingPercent / 100);
  return [domain[0] - pad, domain[1] + pad];
}

/** Rounds a raw value up to a "nice" number for tick display. */
function niceUpperBound(value: number): number {
  if (value <= 0) return 0;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  return Math.ceil(value / magnitude) * magnitude;
}

/** Returns evenly spaced tick values within [min, max]. */
function computeTicks(min: number, max: number, count: number): number[] {
  if (count <= 1) return [min];
  const step = (max - min) / (count - 1);
  return Array.from({ length: count }, (_, i) => min + i * step);
}

/** Normalises a value into [0, 1] within the given domain. */
function normalise(value: number, min: number, max: number): number {
  if (max === min) return 0;
  return (value - min) / (max - min);
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const defaultData: ChartDataPoint[] = [
  { label: "Mon", value: 120 },
  { label: "Tue", value: 340 },
  { label: "Wed", value: 55 },
  { label: "Thu", value: 480 },
  { label: "Fri", value: 210 },
];

// ── computeDomain ─────────────────────────────────────────────────────────────

describe("computeDomain", () => {
  it("returns correct [min, max] from a typical dataset", () => {
    const [min, max] = computeDomain(defaultData);
    expect(min).toBe(55);
    expect(max).toBe(480);
  });

  it("returns [0, 0] for an empty dataset", () => {
    expect(computeDomain([])).toEqual([0, 0]);
  });

  it("returns [value, value] for a single-element dataset", () => {
    const [min, max] = computeDomain([{ label: "x", value: 75 }]);
    expect(min).toBe(75);
    expect(max).toBe(75);
  });

  it("handles all-negative values", () => {
    const data: ChartDataPoint[] = [
      { label: "a", value: -300 },
      { label: "b", value: -100 },
      { label: "c", value: -500 },
    ];
    const [min, max] = computeDomain(data);
    expect(min).toBe(-500);
    expect(max).toBe(-100);
  });

  it("handles mixed positive and negative values", () => {
    const data: ChartDataPoint[] = [
      { label: "loss", value: -200 },
      { label: "gain", value: 400 },
    ];
    const [min, max] = computeDomain(data);
    expect(min).toBe(-200);
    expect(max).toBe(400);
  });

  it("returns same domain for two identical values", () => {
    const data: ChartDataPoint[] = [
      { label: "a", value: 50 },
      { label: "b", value: 50 },
    ];
    const [min, max] = computeDomain(data);
    expect(min).toBe(50);
    expect(max).toBe(50);
  });
});

// ── padDomain ─────────────────────────────────────────────────────────────────

describe("padDomain", () => {
  it("adds 10% padding to domain", () => {
    const padded = padDomain([0, 100], 10);
    expect(padded[0]).toBeCloseTo(-10);
    expect(padded[1]).toBeCloseTo(110);
  });

  it("returns unchanged domain when padding is 0%", () => {
    const padded = padDomain([50, 200], 0);
    expect(padded[0]).toBe(50);
    expect(padded[1]).toBe(200);
  });

  it("collapses symmetrically for a zero-range domain", () => {
    const padded = padDomain([100, 100], 10);
    // range = 0, pad = 0 → unchanged
    expect(padded[0]).toBe(100);
    expect(padded[1]).toBe(100);
  });
});

// ── niceUpperBound ────────────────────────────────────────────────────────────

describe("niceUpperBound", () => {
  it("rounds 480 up to 500", () => {
    expect(niceUpperBound(480)).toBe(500);
  });

  it("rounds 55 up to 100", () => {
    expect(niceUpperBound(55)).toBe(100);
  });

  it("returns 0 for 0 or negative input", () => {
    expect(niceUpperBound(0)).toBe(0);
    expect(niceUpperBound(-50)).toBe(0);
  });

  it("returns exact magnitude for a power of 10", () => {
    expect(niceUpperBound(1000)).toBe(1000);
  });

  it("rounds 1200 up to 2000", () => {
    expect(niceUpperBound(1200)).toBe(2000);
  });
});

// ── computeTicks ──────────────────────────────────────────────────────────────

describe("computeTicks", () => {
  it("returns the requested number of ticks", () => {
    const ticks = computeTicks(0, 100, 5);
    expect(ticks).toHaveLength(5);
  });

  it("first tick equals min", () => {
    const ticks = computeTicks(0, 100, 5);
    expect(ticks[0]).toBe(0);
  });

  it("last tick equals max", () => {
    const ticks = computeTicks(0, 100, 5);
    expect(ticks[ticks.length - 1]).toBe(100);
  });

  it("ticks are evenly spaced", () => {
    const ticks = computeTicks(0, 100, 6);
    const steps = ticks.slice(1).map((t, i) => t - ticks[i]!);
    const firstStep = steps[0]!;
    for (const step of steps) {
      expect(step).toBeCloseTo(firstStep);
    }
  });

  it("handles count of 1 by returning [min]", () => {
    const ticks = computeTicks(0, 200, 1);
    expect(ticks).toEqual([0]);
  });

  it("handles negative domain range", () => {
    const ticks = computeTicks(-100, 100, 3);
    expect(ticks[0]).toBe(-100);
    expect(ticks[2]).toBe(100);
  });
});

// ── normalise ─────────────────────────────────────────────────────────────────

describe("normalise", () => {
  it("returns 0 for min value", () => {
    expect(normalise(0, 0, 100)).toBe(0);
  });

  it("returns 1 for max value", () => {
    expect(normalise(100, 0, 100)).toBe(1);
  });

  it("returns 0.5 for midpoint", () => {
    expect(normalise(50, 0, 100)).toBeCloseTo(0.5);
  });

  it("returns 0 when min equals max (degenerate range)", () => {
    expect(normalise(50, 50, 50)).toBe(0);
  });

  it("handles negative domain", () => {
    expect(normalise(0, -100, 100)).toBeCloseTo(0.5);
  });

  it("returns value > 1 for values above max (no clamping)", () => {
    expect(normalise(150, 0, 100)).toBeCloseTo(1.5);
  });
});

// ── Integration: domain → ticks pipeline ─────────────────────────────────────

describe("Axis scaling pipeline", () => {
  it("produces plottable ticks within padded domain for real data", () => {
    const domain = computeDomain(defaultData);
    const padded = padDomain(domain, 5);
    const ticks = computeTicks(padded[0], padded[1], 5);

    // All ticks should fall within padded domain
    for (const tick of ticks) {
      expect(tick).toBeGreaterThanOrEqual(padded[0]);
      expect(tick).toBeLessThanOrEqual(padded[1]);
    }
  });

  it("each data point normalises to [0,1] within its domain", () => {
    const [minVal, maxVal] = computeDomain(defaultData);
    for (const point of defaultData) {
      const n = normalise(point.value, minVal, maxVal);
      expect(n).toBeGreaterThanOrEqual(0);
      expect(n).toBeLessThanOrEqual(1);
    }
  });
});
