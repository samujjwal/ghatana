/**
 * @file dataAggregation.test.ts
 * Tests for data aggregation operations on ChartDataPoint arrays —
 * sum, average, min, max, count, and group-by utilities.
 *
 * @doc.type module
 * @doc.purpose Tests for chart data aggregation functions
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect } from "vitest";
import type { ChartDataPoint } from "../types";

// ── Aggregation helpers (inline for chart data point sets) ───────────────────

function sum(points: ChartDataPoint[]): number {
  return points.reduce((acc, p) => acc + p.value, 0);
}

function average(points: ChartDataPoint[]): number {
  if (points.length === 0) return 0;
  return sum(points) / points.length;
}

function min(points: ChartDataPoint[]): number {
  if (points.length === 0) return 0;
  return Math.min(...points.map((p) => p.value));
}

function max(points: ChartDataPoint[]): number {
  if (points.length === 0) return 0;
  return Math.max(...points.map((p) => p.value));
}

function filterAbove(
  points: ChartDataPoint[],
  threshold: number,
): ChartDataPoint[] {
  return points.filter((p) => p.value > threshold);
}

function filterBelow(
  points: ChartDataPoint[],
  threshold: number,
): ChartDataPoint[] {
  return points.filter((p) => p.value < threshold);
}

function groupByLabel(
  points: ChartDataPoint[],
  labelFn: (label: string) => string,
): Map<string, ChartDataPoint[]> {
  const map = new Map<string, ChartDataPoint[]>();
  for (const point of points) {
    const key = labelFn(point.label);
    const group = map.get(key) ?? [];
    group.push(point);
    map.set(key, group);
  }
  return map;
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const salesData: ChartDataPoint[] = [
  { label: "Jan", value: 100 },
  { label: "Feb", value: 200 },
  { label: "Mar", value: 300 },
  { label: "Apr", value: 150 },
  { label: "May", value: 250 },
  { label: "Jun", value: 350 },
];

const uniformData: ChartDataPoint[] = Array.from({ length: 5 }, (_, i) => ({
  label: `item-${i + 1}`,
  value: 10,
}));

const emptyData: ChartDataPoint[] = [];

// ── Sum tests ─────────────────────────────────────────────────────────────────

describe("sum", () => {
  it("sums all data point values", () => {
    expect(sum(salesData)).toBe(1350);
  });

  it("returns 0 for empty array", () => {
    expect(sum(emptyData)).toBe(0);
  });

  it("returns the single value for a one-element array", () => {
    expect(sum([{ label: "solo", value: 42 }])).toBe(42);
  });

  it("correctly sums uniform values", () => {
    expect(sum(uniformData)).toBe(50);
  });

  it("handles negative values", () => {
    const data: ChartDataPoint[] = [
      { label: "loss", value: -100 },
      { label: "gain", value: 200 },
    ];
    expect(sum(data)).toBe(100);
  });

  it("handles zero values", () => {
    const data: ChartDataPoint[] = [
      { label: "a", value: 0 },
      { label: "b", value: 0 },
    ];
    expect(sum(data)).toBe(0);
  });
});

// ── Average tests ─────────────────────────────────────────────────────────────

describe("average", () => {
  it("computes correct average for sales data", () => {
    expect(average(salesData)).toBeCloseTo(225);
  });

  it("returns 0 for empty array", () => {
    expect(average(emptyData)).toBe(0);
  });

  it("returns the single value for a one-element array", () => {
    expect(average([{ label: "x", value: 77 }])).toBe(77);
  });

  it("returns uniform value for all-equal data", () => {
    expect(average(uniformData)).toBe(10);
  });
});

// ── Min / Max tests ───────────────────────────────────────────────────────────

describe("min", () => {
  it("returns the minimum value from sales data", () => {
    expect(min(salesData)).toBe(100);
  });

  it("returns 0 for empty array", () => {
    expect(min(emptyData)).toBe(0);
  });

  it("returns the single element value for one-element array", () => {
    expect(min([{ label: "x", value: 55 }])).toBe(55);
  });

  it("correctly identifies min among negative values", () => {
    const data: ChartDataPoint[] = [
      { label: "a", value: -50 },
      { label: "b", value: 10 },
      { label: "c", value: -200 },
    ];
    expect(min(data)).toBe(-200);
  });
});

describe("max", () => {
  it("returns the maximum value from sales data", () => {
    expect(max(salesData)).toBe(350);
  });

  it("returns 0 for empty array", () => {
    expect(max(emptyData)).toBe(0);
  });

  it("returns the single element value for one-element array", () => {
    expect(max([{ label: "x", value: 99 }])).toBe(99);
  });

  it("correctly identifies max among negative values", () => {
    const data: ChartDataPoint[] = [
      { label: "a", value: -100 },
      { label: "b", value: -10 },
      { label: "c", value: -500 },
    ];
    expect(max(data)).toBe(-10);
  });
});

// ── Filter tests ──────────────────────────────────────────────────────────────

describe("filterAbove", () => {
  it("returns only points with value above the threshold", () => {
    const result = filterAbove(salesData, 200);
    expect(result.every((p) => p.value > 200)).toBe(true);
  });

  it("returns empty array when no points exceed threshold", () => {
    expect(filterAbove(salesData, 1000)).toHaveLength(0);
  });

  it("returns all points when threshold is below minimum", () => {
    expect(filterAbove(salesData, 0)).toHaveLength(salesData.length);
  });
});

describe("filterBelow", () => {
  it("returns only points with value below the threshold", () => {
    const result = filterBelow(salesData, 200);
    expect(result.every((p) => p.value < 200)).toBe(true);
  });

  it("returns empty array when all points exceed threshold", () => {
    expect(filterBelow(salesData, 50)).toHaveLength(0);
  });
});

// ── Group-by tests ────────────────────────────────────────────────────────────

describe("groupByLabel", () => {
  it("groups data by first character of label", () => {
    const data: ChartDataPoint[] = [
      { label: "Jan", value: 100 },
      { label: "Jun", value: 200 },
      { label: "Feb", value: 150 },
    ];

    const groups = groupByLabel(data, (l) => l[0]!);

    expect(groups.get("J")).toHaveLength(2);
    expect(groups.get("F")).toHaveLength(1);
  });

  it("returns an empty map for empty input", () => {
    const groups = groupByLabel(emptyData, (l) => l);
    expect(groups.size).toBe(0);
  });

  it("puts all points in one group when all share the same key", () => {
    const groups = groupByLabel(uniformData, () => "all");
    expect(groups.get("all")).toHaveLength(uniformData.length);
  });
});

// ── Derived metrics ───────────────────────────────────────────────────────────

describe("Derived metrics", () => {
  it("range equals max minus min", () => {
    const range = max(salesData) - min(salesData);
    expect(range).toBe(250);
  });

  it("sum of filtered above plus sum of filtered below (at exclusive boundary) equals total", () => {
    const threshold = 200;
    const aboveOrEqual = salesData.filter((p) => p.value >= threshold);
    const below = filterBelow(salesData, threshold);
    const combined = sum([...aboveOrEqual, ...below]);
    expect(combined).toBe(sum(salesData));
  });

  it("cumulative sum accumulates correctly", () => {
    const sorted = [...salesData].sort((a, b) => a.value - b.value);
    const cumulative = sorted.reduce<number[]>((acc, p) => {
      acc.push((acc[acc.length - 1] ?? 0) + p.value);
      return acc;
    }, []);

    expect(cumulative[cumulative.length - 1]).toBe(sum(salesData));
  });
});
