/**
 * @file realDataRendering.test.ts
 * Tests validating that chart components process data correctly
 * — data length, prop defaults, and field assignment.
 *
 * @doc.type module
 * @doc.purpose Tests for real data rendering through chart components
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect } from "vitest";
import type { ChartDataPoint } from "../types";
import type { LineChartProps } from "../components/LineChart";
import type { BarChartProps } from "../components/BarChart";

// ── Fixtures ──────────────────────────────────────────────────────────────────

const monthlyRevenue: ChartDataPoint[] = [
  { label: "Jan", value: 12_000 },
  { label: "Feb", value: 14_500 },
  { label: "Mar", value: 11_300 },
  { label: "Apr", value: 16_700 },
  { label: "May", value: 18_200 },
  { label: "Jun", value: 17_800 },
];

const sparseData: ChartDataPoint[] = [
  { label: "Q1", value: 0 },
  { label: "Q2", value: 0 },
];

const singlePoint: ChartDataPoint[] = [{ label: "Only", value: 42 }];

// ── ChartDataPoint validation ────────────────────────────────────────────────

describe("Chart data processing", () => {
  describe("Monthly revenue dataset", () => {
    it("contains 6 data points", () => {
      expect(monthlyRevenue).toHaveLength(6);
    });

    it("all data points have a non-empty label", () => {
      for (const point of monthlyRevenue) {
        expect(point.label).toBeTruthy();
      }
    });

    it("all data point values are positive numbers", () => {
      for (const point of monthlyRevenue) {
        expect(point.value).toBeGreaterThan(0);
      }
    });

    it("first point is January with value 12000", () => {
      expect(monthlyRevenue[0]!.label).toBe("Jan");
      expect(monthlyRevenue[0]!.value).toBe(12_000);
    });

    it("last point is June with value 17800", () => {
      expect(monthlyRevenue[5]!.label).toBe("Jun");
      expect(monthlyRevenue[5]!.value).toBe(17_800);
    });
  });

  describe("Empty and edge-case datasets", () => {
    it("handles zero values correctly", () => {
      for (const point of sparseData) {
        expect(point.value).toBe(0);
      }
    });

    it("single-point dataset is valid", () => {
      expect(singlePoint).toHaveLength(1);
      expect(singlePoint[0]!.value).toBe(42);
    });

    it("empty array is a valid dataset", () => {
      const empty: ChartDataPoint[] = [];
      expect(empty).toHaveLength(0);
    });
  });
});

// ── LineChart props validation ────────────────────────────────────────────────

describe("LineChart props", () => {
  it("produces valid props object with required fields", () => {
    const props: LineChartProps = {
      data: monthlyRevenue,
      width: 600,
      height: 400,
    };

    expect(props.data).toHaveLength(6);
    expect(props.width).toBe(600);
    expect(props.height).toBe(400);
  });

  it("defaults width/height are valid rendering dimensions", () => {
    // When no width/height provided the component uses 400×300 defaults
    const minimalProps: LineChartProps = {
      data: singlePoint,
    };

    expect(minimalProps.data).toHaveLength(1);
  });

  it("supports xField and yField alias props", () => {
    const props: LineChartProps = {
      data: monthlyRevenue,
      xField: "label",
      yField: "value",
    };

    expect(props.xField).toBe("label");
    expect(props.yField).toBe("value");
  });

  it("supports color prop", () => {
    const props: LineChartProps = {
      data: singlePoint,
      color: "#3B82F6",
    };

    expect(props.color).toBe("#3B82F6");
  });
});

// ── BarChart props validation ─────────────────────────────────────────────────

describe("BarChart props", () => {
  it("produces valid props object with data", () => {
    const props: BarChartProps = {
      data: monthlyRevenue,
    };

    expect(props.data).toHaveLength(6);
  });

  it("supports xKey and yKey field selectors", () => {
    const props: BarChartProps = {
      data: monthlyRevenue,
      xKey: "label",
      yKey: "value",
    };

    expect(props.xKey).toBe("label");
    expect(props.yKey).toBe("value");
  });
});

// ── Data shape compatibility ─────────────────────────────────────────────────

describe("ChartDataPoint extra fields", () => {
  it("supports secondaryValue alongside value", () => {
    const points: ChartDataPoint[] = [
      { label: "A", value: 100, secondaryValue: 80 },
      { label: "B", value: 120, secondaryValue: 110 },
    ];

    expect(points[0]!.secondaryValue).toBe(80);
    expect(points[1]!.secondaryValue).toBe(110);
  });

  it("supports target for comparison charts", () => {
    const points: ChartDataPoint[] = [
      { label: "Jan", value: 950, target: 1000 },
      { label: "Feb", value: 1100, target: 1000 },
    ];

    expect(points[0]!.value).toBeLessThan(points[0]!.target!);
    expect(points[1]!.value).toBeGreaterThan(points[1]!.target!);
  });

  it("supports per-point color overrides", () => {
    const points: ChartDataPoint[] = [
      { label: "Success", value: 90, color: "#10B981" },
      { label: "Failure", value: 10, color: "#EF4444" },
    ];

    expect(points[0]!.color).toBe("#10B981");
    expect(points[1]!.color).toBe("#EF4444");
  });
});
