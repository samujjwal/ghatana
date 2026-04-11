/**
 * Data Metric Element — KPI / metric card rendered on the canvas.
 *
 * @doc.type class
 * @doc.purpose Canvas element for displaying single key metrics with trend indicators
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Use-cases:
 * - Data Cloud: pipeline health, record counts, latency p99
 * - YAPPC: sprint velocity, open bugs, code coverage
 * - AEP: agent invocations, success rate, average latency
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export type MetricTrend = "up" | "down" | "neutral";
export type MetricStatus = "healthy" | "warning" | "critical" | "unknown";

export interface MetricThreshold {
  warning: number;
  critical: number;
  /** Whether higher is worse (e.g., error rate) */
  higherIsBad?: boolean;
}

export interface DataMetricProps extends BaseElementProps {
  /** Metric label / title */
  label?: string;
  /** Current value (may be a formatted string) */
  value?: number | string;
  /** Previous period value for trend calculation */
  previousValue?: number;
  /** Unit appended after value, e.g. "ms", "%", "req/s" */
  unit?: string;
  /** Trend direction override (auto-computed when previousValue is provided) */
  trend?: MetricTrend;
  /** Percentage change string, e.g. "+12.3%" */
  changeLabel?: string;
  /** Status configuration */
  status?: MetricStatus;
  /** Auto-compute status from threshold */
  threshold?: MetricThreshold;
  /** Optional subtitle / description */
  description?: string;
  /** Icon glyph or emoji */
  icon?: string;
  /** Background color */
  backgroundColor?: string;
  /** Whether to show a sparkline of recent values */
  sparklineData?: number[];
  /** Timestamp of last update */
  updatedAt?: Date | string;
}

const STATUS_COLORS: Record<MetricStatus, string> = {
  healthy: "#4ade80",
  warning: "#facc15",
  critical: "#f87171",
  unknown: "#94a3b8",
};

export class DataMetricElement extends CanvasElement {
  public label: string;
  public value: number | string;
  public previousValue: number | undefined;
  public unit: string;
  public trend: MetricTrend;
  public changeLabel: string | undefined;
  public status: MetricStatus;
  public threshold: MetricThreshold | undefined;
  public description: string | undefined;
  public icon: string | undefined;
  public backgroundColor: string;
  public sparklineData: number[];
  public updatedAt: Date | undefined;

  constructor(props: DataMetricProps) {
    super(props);
    this.label = props.label ?? "";
    this.value = props.value ?? 0;
    this.previousValue = props.previousValue;
    this.unit = props.unit ?? "";
    this.trend = props.trend ?? this._computeTrend(props);
    this.changeLabel = props.changeLabel;
    this.status = props.status ?? this._computeStatus(props);
    this.threshold = props.threshold;
    this.description = props.description;
    this.icon = props.icon;
    this.backgroundColor = props.backgroundColor ?? "#1e293b";
    this.sparklineData = props.sparklineData ?? [];
    this.updatedAt =
      props.updatedAt instanceof Date
        ? props.updatedAt
        : props.updatedAt
          ? new Date(props.updatedAt)
          : undefined;
  }

  get type(): CanvasElementType {
    return "data-metric";
  }

  /** Update the current value (called by live data feeds) */
  updateValue(newValue: number | string, previousValue?: number): void {
    this.previousValue = previousValue ?? (typeof this.value === "number" ? this.value : undefined);
    this.value = newValue;
    this.trend = this._computeTrend({
      value: typeof newValue === "number" ? newValue : 0,
      previousValue: this.previousValue,
    });
    this.status = this._computeStatus({ value: typeof newValue === "number" ? newValue : 0, threshold: this.threshold });
    this.updatedAt = new Date();
  }

  private _computeTrend(props: Partial<DataMetricProps>): MetricTrend {
    if (props.previousValue === undefined || typeof props.value !== "number") {
      return "neutral";
    }
    if (props.value > props.previousValue) return "up";
    if (props.value < props.previousValue) return "down";
    return "neutral";
  }

  private _computeStatus(props: Partial<DataMetricProps>): MetricStatus {
    if (!props.threshold || typeof props.value !== "number") return "unknown";
    const v = props.value;
    const t = props.threshold;
    if (t.higherIsBad) {
      if (v >= t.critical) return "critical";
      if (v >= t.warning) return "warning";
      return "healthy";
    } else {
      if (v <= t.critical) return "critical";
      if (v <= t.warning) return "warning";
      return "healthy";
    }
  }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();
    const statusColor = STATUS_COLORS[this.status];

    // Background
    ctx.fillStyle = this.backgroundColor;
    ctx.beginPath();
    ctx.roundRect(b.x, b.y, b.w, b.h, 8);
    ctx.fill();

    // Status accent bar
    ctx.fillStyle = statusColor;
    ctx.fillRect(b.x, b.y, 4, b.h);

    if (zoom < 0.25) {
      // Tiny: just dot
      ctx.beginPath();
      ctx.arc(b.x + b.w / 2, b.y + b.h / 2, Math.min(8, b.h * 0.3), 0, Math.PI * 2);
      ctx.fillStyle = statusColor;
      ctx.fill();
      ctx.restore();
      return;
    }

    const pad = 12;
    const innerX = b.x + pad;
    const innerW = b.w - pad * 2;

    // Icon
    let labelX = innerX;
    if (this.icon && zoom > 0.4) {
      ctx.font = `${Math.min(18, b.h * 0.25)}px sans-serif`;
      ctx.textBaseline = "top";
      ctx.fillText(this.icon, innerX, b.y + pad);
      labelX = innerX + 24;
    }

    // Label
    ctx.fillStyle = "rgba(255,255,255,0.55)";
    ctx.font = `${Math.min(11, b.h * 0.14)}px sans-serif`;
    ctx.textBaseline = "top";
    ctx.fillText(this.label, labelX, b.y + pad, innerW);

    // Value
    const valueStr = `${this.value}${this.unit ? "\u202f" + this.unit : ""}`;
    ctx.fillStyle = "#ffffff";
    const valueFontSize = Math.min(28, b.h * 0.38);
    ctx.font = `bold ${valueFontSize}px sans-serif`;
    ctx.textBaseline = "middle";
    const valueY = b.y + b.h * 0.52;
    ctx.fillText(valueStr, innerX, valueY, innerW * 0.7);

    // Trend indicator
    if (this.trend !== "neutral" && zoom > 0.4) {
      const trendColor = this.trend === "up" ? "#4ade80" : "#f87171";
      const trendIcon = this.trend === "up" ? "▲" : "▼";
      ctx.fillStyle = trendColor;
      ctx.font = `${Math.min(13, b.h * 0.17)}px sans-serif`;
      ctx.fillText(
        `${trendIcon} ${this.changeLabel ?? ""}`,
        innerX + innerW * 0.72,
        valueY,
      );
    }

    // Sparkline
    if (this.sparklineData.length > 2 && zoom > 0.5) {
      this._drawSparkline(ctx, {
        x: innerX,
        y: b.y + b.h - 20,
        w: innerW,
        h: 14,
      });
    }

    // Description
    if (this.description && zoom > 0.6) {
      ctx.fillStyle = "rgba(255,255,255,0.35)";
      ctx.font = `${Math.min(10, b.h * 0.12)}px sans-serif`;
      ctx.textBaseline = "bottom";
      ctx.fillText(this.description, innerX, b.y + b.h - 6, innerW);
    }

    ctx.restore();
  }

  private _drawSparkline(
    ctx: CanvasRenderingContext2D,
    area: { x: number; y: number; w: number; h: number },
  ): void {
    const vals = this.sparklineData;
    const min = Math.min(...vals);
    const max = Math.max(...vals);
    const range = max - min || 1;
    const color = STATUS_COLORS[this.status];

    ctx.strokeStyle = color + "88";
    ctx.lineWidth = 1;
    ctx.lineJoin = "round";
    ctx.beginPath();
    for (let i = 0; i < vals.length; i++) {
      const x = area.x + (i / (vals.length - 1)) * area.w;
      const y = area.y + area.h - ((vals[i]! - min) / range) * area.h;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }
}
