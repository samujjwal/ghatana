/**
 * @file tooltipCalculations.test.ts
 * Tests for tooltip-related calculations — label formatting,
 * value formatting, percentage display, and delta computations.
 *
 * @doc.type module
 * @doc.purpose Tests for chart tooltip calculation utilities
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect } from "vitest";
import type { ChartDataPoint, ChartMetricConfig } from "../types";

// ── Tooltip utilities (inline helpers to test) ────────────────────────────────

/** Formats a numeric value using a metric config formatter or a default. */
function formatValue(
  value: number,
  config?: Pick<ChartMetricConfig, "formatter">,
): string {
  if (config?.formatter) return config.formatter(value);
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }).format(
    value,
  );
}

/** Computes the percentage a value represents of the total. */
function toPercent(value: number, total: number): string {
  if (total === 0) return "0%";
  return `${((value / total) * 100).toFixed(1)}%`;
}

/** Computes the absolute delta between current and previous values. */
function absoluteDelta(current: number, previous: number): number {
  return current - previous;
}

/** Computes the relative delta as a percentage change. */
function relativeDelta(current: number, previous: number): string {
  if (previous === 0) return "N/A";
  const pct = ((current - previous) / Math.abs(previous)) * 100;
  const sign = pct >= 0 ? "+" : "";
  return `${sign}${pct.toFixed(1)}%`;
}

/** Builds a tooltip label by combining data point fields. */
function buildTooltipLabel(point: ChartDataPoint): string {
  const parts: string[] = [point.label];
  if (point.target !== undefined) {
    const achieved = point.value >= point.target;
    parts.push(achieved ? "✓" : "✗");
  }
  return parts.join(" ");
}

/** Selects the best tooltip position given a mouse position and viewport size. */
function tooltipPosition(
  mouseX: number,
  mouseY: number,
  tooltipWidth: number,
  tooltipHeight: number,
  viewportWidth: number,
  viewportHeight: number,
): { x: number; y: number } {
  const offset = 10;
  let x = mouseX + offset;
  let y = mouseY + offset;

  if (x + tooltipWidth > viewportWidth) x = mouseX - tooltipWidth - offset;
  if (y + tooltipHeight > viewportHeight) y = mouseY - tooltipHeight - offset;

  return { x, y };
}

// ── formatValue ───────────────────────────────────────────────────────────────

describe("formatValue", () => {
  it("formats an integer using default Intl formatter", () => {
    expect(formatValue(1234)).toBe("1,234");
  });

  it("formats a decimal to at most 2 fraction digits", () => {
    expect(formatValue(3.14159)).toBe("3.14");
  });

  it('formats 0 as "0"', () => {
    expect(formatValue(0)).toBe("0");
  });

  it("uses the custom formatter when provided", () => {
    const config: Pick<ChartMetricConfig, "formatter"> = {
      formatter: (v) => `$${v.toFixed(2)}`,
    };
    expect(formatValue(9.5, config)).toBe("$9.50");
  });

  it("custom formatter can return arbitrary units", () => {
    const config: Pick<ChartMetricConfig, "formatter"> = {
      formatter: (v) => `${v} ms`,
    };
    expect(formatValue(250, config)).toBe("250 ms");
  });

  it("ignores undefined formatter and uses default", () => {
    const config: Pick<ChartMetricConfig, "formatter"> = {};
    expect(formatValue(100, config)).toBe("100");
  });
});

// ── toPercent ─────────────────────────────────────────────────────────────────

describe("toPercent", () => {
  it("computes correct percentage", () => {
    expect(toPercent(25, 100)).toBe("25.0%");
  });

  it('returns "0%" for zero total', () => {
    expect(toPercent(50, 0)).toBe("0%");
  });

  it('returns "100.0%" when value equals total', () => {
    expect(toPercent(500, 500)).toBe("100.0%");
  });

  it("rounds to one decimal place", () => {
    expect(toPercent(1, 3)).toBe("33.3%");
  });

  it("handles values greater than total", () => {
    expect(toPercent(150, 100)).toBe("150.0%");
  });
});

// ── absoluteDelta ─────────────────────────────────────────────────────────────

describe("absoluteDelta", () => {
  it("computes positive delta for growth", () => {
    expect(absoluteDelta(150, 100)).toBe(50);
  });

  it("computes negative delta for decline", () => {
    expect(absoluteDelta(80, 100)).toBe(-20);
  });

  it("returns 0 when values are equal", () => {
    expect(absoluteDelta(200, 200)).toBe(0);
  });
});

// ── relativeDelta ─────────────────────────────────────────────────────────────

describe("relativeDelta", () => {
  it("computes positive percentage change", () => {
    expect(relativeDelta(150, 100)).toBe("+50.0%");
  });

  it("computes negative percentage change", () => {
    expect(relativeDelta(80, 100)).toBe("-20.0%");
  });

  it('returns "N/A" when previous is 0', () => {
    expect(relativeDelta(50, 0)).toBe("N/A");
  });

  it('returns "+0.0%" for unchanged value', () => {
    expect(relativeDelta(100, 100)).toBe("+0.0%");
  });

  it("handles negative previous value correctly (from -100 to -50)", () => {
    // (-50 - -100) / |-100| = 50%
    expect(relativeDelta(-50, -100)).toBe("+50.0%");
  });
});

// ── buildTooltipLabel ─────────────────────────────────────────────────────────

describe("buildTooltipLabel", () => {
  it("returns just the label when no target", () => {
    const point: ChartDataPoint = { label: "Jan", value: 100 };
    expect(buildTooltipLabel(point)).toBe("Jan");
  });

  it("appends checkmark when value meets or exceeds target", () => {
    const point: ChartDataPoint = { label: "Feb", value: 120, target: 100 };
    expect(buildTooltipLabel(point)).toBe("Feb ✓");
  });

  it("appends cross when value is below target", () => {
    const point: ChartDataPoint = { label: "Mar", value: 80, target: 100 };
    expect(buildTooltipLabel(point)).toBe("Mar ✗");
  });

  it("handles exact target match with checkmark", () => {
    const point: ChartDataPoint = { label: "Apr", value: 100, target: 100 };
    expect(buildTooltipLabel(point)).toBe("Apr ✓");
  });
});

// ── tooltipPosition ───────────────────────────────────────────────────────────

describe("tooltipPosition", () => {
  it("positions tooltip to the right and below mouse by default", () => {
    const pos = tooltipPosition(200, 150, 120, 60, 800, 600);
    expect(pos.x).toBe(210);
    expect(pos.y).toBe(160);
  });

  it("flips tooltip to the left when it would overflow right edge", () => {
    // mouseX=750, tooltipWidth=120, viewport=800 → 750+10+120=880 > 800 → flip
    const pos = tooltipPosition(750, 100, 120, 60, 800, 600);
    expect(pos.x).toBe(750 - 120 - 10);
  });

  it("flips tooltip upward when it would overflow bottom edge", () => {
    // mouseY=560, tooltipHeight=60, viewport=600 → 560+10+60=630 > 600 → flip
    const pos = tooltipPosition(200, 560, 120, 60, 800, 600);
    expect(pos.y).toBe(560 - 60 - 10);
  });

  it("handles tooltip at center of viewport without flipping", () => {
    const pos = tooltipPosition(400, 300, 100, 50, 800, 600);
    expect(pos.x).toBe(410);
    expect(pos.y).toBe(310);
  });

  it("flips both axes when near bottom-right corner", () => {
    const pos = tooltipPosition(760, 565, 100, 50, 800, 600);
    expect(pos.x).toBe(760 - 100 - 10);
    expect(pos.y).toBe(565 - 50 - 10);
  });
});
