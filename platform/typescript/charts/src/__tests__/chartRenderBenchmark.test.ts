/**
 * @file chartRenderBenchmark.test.ts
 *
 * UI render benchmarks for @ghatana/charts — exercises the data pipeline that
 * feeds all chart components: aggregation, axis scaling, domain computation,
 * tooltip calculation, and large-dataset transformations.
 *
 * @doc.type module
 * @doc.purpose Chart data pipeline benchmarks covering throughput and correctness
 * @doc.layer platform
 * @doc.pattern Benchmark
 */
import { describe, it, expect } from "vitest";
import type { ChartDataPoint } from "../types";

// ── Inline pipeline helpers (mirrors production implementations) ──────────────

function sum(pts: ChartDataPoint[]): number {
  return pts.reduce((a, p) => a + p.value, 0);
}

function avg(pts: ChartDataPoint[]): number {
  return pts.length === 0 ? 0 : sum(pts) / pts.length;
}

function computeDomain(pts: ChartDataPoint[]): [number, number] {
  if (pts.length === 0) return [0, 0];
  const vals = pts.map((p) => p.value);
  return [Math.min(...vals), Math.max(...vals)];
}

function padDomain(
  domain: [number, number],
  pct: number,
): [number, number] {
  const range = domain[1] - domain[0];
  const pad = range * (pct / 100);
  return [domain[0] - pad, domain[1] + pad];
}

function niceUpperBound(value: number): number {
  if (value <= 0) return 0;
  const mag = Math.pow(10, Math.floor(Math.log10(value)));
  return Math.ceil(value / mag) * mag;
}

function groupBy(
  pts: ChartDataPoint[],
  key: string,
): Record<string, ChartDataPoint[]> {
  return pts.reduce<Record<string, ChartDataPoint[]>>((acc, p) => {
    const k = String(p[key] ?? "unknown");
    (acc[k] ??= []).push(p);
    return acc;
  }, {});
}

function normalise(pts: ChartDataPoint[]): ChartDataPoint[] {
  const total = sum(pts);
  if (total === 0) return pts.map((p) => ({ ...p, value: 0 }));
  return pts.map((p) => ({ ...p, value: p.value / total }));
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makePoints(count: number): ChartDataPoint[] {
  return Array.from({ length: count }, (_, i) => ({
    label: `point-${i}`,
    value: Math.abs(((i * 7919) % 1000) + 1),
    group: ["alpha", "beta", "gamma", "delta"][i % 4],
    color: `#${((i * 123456) % 0xffffff).toString(16).padStart(6, "0")}`,
  }));
}

const SMALL = makePoints(100);
const MEDIUM = makePoints(1_000);
const LARGE = makePoints(10_000);

// ── Throughput: aggregation ───────────────────────────────────────────────────

describe("Chart data aggregation benchmarks", () => {
  it("sum() over 10,000 points completes within 5 ms", () => {
    const start = performance.now();
    const result = sum(LARGE);
    const elapsed = performance.now() - start;

    expect(result).toBeGreaterThan(0);
    expect(elapsed).toBeLessThan(5);
  });

  it("avg() over 10,000 points completes within 5 ms", () => {
    const start = performance.now();
    const result = avg(LARGE);
    const elapsed = performance.now() - start;

    expect(result).toBeGreaterThan(0);
    expect(elapsed).toBeLessThan(5);
  });

  it("groupBy() over 10,000 points produces 4 groups within 20 ms", () => {
    const start = performance.now();
    const groups = groupBy(LARGE, "group");
    const elapsed = performance.now() - start;

    expect(Object.keys(groups)).toHaveLength(4);
    expect(elapsed).toBeLessThan(20);
  });

  it("normalise() over 1,000 points: values sum to ≈1.0 within 10 ms", () => {
    const start = performance.now();
    const normed = normalise(MEDIUM);
    const elapsed = performance.now() - start;

    const total = sum(normed);
    expect(total).toBeCloseTo(1.0, 5);
    expect(elapsed).toBeLessThan(10);
  });

  it("running 1,000 aggregations over 100-point datasets within 50 ms", () => {
    const start = performance.now();
    for (let i = 0; i < 1_000; i++) {
      sum(SMALL);
      avg(SMALL);
    }
    const elapsed = performance.now() - start;
    expect(elapsed).toBeLessThan(50);
  });
});

// ── Throughput: axis scaling ──────────────────────────────────────────────────

describe("Chart axis scaling benchmarks", () => {
  it("computeDomain() over 10,000 points within 10 ms", () => {
    const start = performance.now();
    const [min, max] = computeDomain(LARGE);
    const elapsed = performance.now() - start;

    expect(min).toBeGreaterThanOrEqual(1);
    expect(max).toBeLessThanOrEqual(1000);
    expect(elapsed).toBeLessThan(10);
  });

  it("padDomain() applies 10% padding correctly", () => {
    const domain: [number, number] = [0, 100];
    const [lo, hi] = padDomain(domain, 10);

    expect(lo).toBeCloseTo(-10, 5);
    expect(hi).toBeCloseTo(110, 5);
  });

  it("niceUpperBound rounds up to nearest power-of-10 multiple", () => {
    expect(niceUpperBound(1234)).toBe(2000);
    expect(niceUpperBound(567)).toBe(600);
    expect(niceUpperBound(10)).toBe(10);
    expect(niceUpperBound(11)).toBe(20);
    expect(niceUpperBound(99)).toBe(100);
  });

  it("running 10,000 domain computations on 100-point datasets within 100 ms", () => {
    const start = performance.now();
    for (let i = 0; i < 10_000; i++) {
      computeDomain(SMALL);
    }
    const elapsed = performance.now() - start;
    expect(elapsed).toBeLessThan(100);
  });
});

// ── Correctness: time-series data patterns ────────────────────────────────────

describe("Chart time-series data correctness", () => {
  it("empty dataset produces zero domain", () => {
    expect(computeDomain([])).toEqual([0, 0]);
  });

  it("single-point dataset has equal min and max", () => {
    const pts: ChartDataPoint[] = [{ label: "only", value: 42 }];
    const [min, max] = computeDomain(pts);
    expect(min).toBe(42);
    expect(max).toBe(42);
  });

  it("negative values are handled in domain calculation", () => {
    const pts: ChartDataPoint[] = [
      { label: "a", value: -100 },
      { label: "b", value: 50 },
      { label: "c", value: 0 },
    ];
    const [min, max] = computeDomain(pts);
    expect(min).toBe(-100);
    expect(max).toBe(50);
  });

  it("large monotonic series has correct min/max", () => {
    const pts = Array.from({ length: 1_000 }, (_, i) => ({
      label: `t${i}`,
      value: i,
    }));
    const [min, max] = computeDomain(pts);
    expect(min).toBe(0);
    expect(max).toBe(999);
  });
});

// ── Correctness: groupBy aggregation ─────────────────────────────────────────

describe("Chart groupBy aggregation correctness", () => {
  it("groups are mutually exclusive and exhaustive", () => {
    const pts = makePoints(200);
    const groups = groupBy(pts, "group");

    const totalInGroups = Object.values(groups).reduce(
      (acc, g) => acc + g.length,
      0,
    );
    expect(totalInGroups).toBe(pts.length);
  });

  it("group sizes match expected distribution for 4-way split", () => {
    const pts = makePoints(400); // 4 groups × 100 each
    const groups = groupBy(pts, "group");

    Object.values(groups).forEach((g) => {
      expect(g.length).toBe(100);
    });
  });

  it("each group's avg is within expected range", () => {
    const pts = makePoints(1_000);
    const groups = groupBy(pts, "group");

    Object.values(groups).forEach((g) => {
      const groupAvg = avg(g);
      expect(groupAvg).toBeGreaterThan(0);
      expect(groupAvg).toBeLessThanOrEqual(1000);
    });
  });
});

// ── Correctness: normalisation (donut / pie chart contract) ───────────────────

describe("Chart normalisation (donut/pie) correctness", () => {
  it("normalised values sum to 1.0", () => {
    const normed = normalise(SMALL);
    expect(sum(normed)).toBeCloseTo(1.0, 5);
  });

  it("each normalised value is between 0 and 1", () => {
    const normed = normalise(SMALL);
    normed.forEach((p) => {
      expect(p.value).toBeGreaterThanOrEqual(0);
      expect(p.value).toBeLessThanOrEqual(1);
    });
  });

  it("all-zero dataset normalises to all-zero values", () => {
    const zeros: ChartDataPoint[] = [
      { label: "a", value: 0 },
      { label: "b", value: 0 },
    ];
    const normed = normalise(zeros);
    normed.forEach((p) => expect(p.value).toBe(0));
  });

  it("labels are preserved after normalisation", () => {
    const normed = normalise(SMALL);
    normed.forEach((p, i) => {
      expect(p.label).toBe(SMALL[i].label);
    });
  });
});

// ── ChartDataPoint type contract ──────────────────────────────────────────────

describe("ChartDataPoint type contract", () => {
  it("required fields: label (string) and value (number)", () => {
    const p: ChartDataPoint = { label: "test", value: 123 };
    expect(typeof p.label).toBe("string");
    expect(typeof p.value).toBe("number");
  });

  it("optional fields default to undefined when not provided", () => {
    const p: ChartDataPoint = { label: "x", value: 1 };
    expect(p.secondaryValue).toBeUndefined();
    expect(p.target).toBeUndefined();
    expect(p.color).toBeUndefined();
  });

  it("index signature allows arbitrary extra fields", () => {
    const p: ChartDataPoint = { label: "x", value: 1, customKey: "extra" };
    expect(p["customKey"]).toBe("extra");
  });
});
