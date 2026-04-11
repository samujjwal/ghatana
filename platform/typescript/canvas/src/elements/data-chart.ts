/**
 * Data Chart Element — renders charts and data visualizations on the canvas.
 *
 * @doc.type class
 * @doc.purpose Canvas element for displaying live or static data charts
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Use-cases:
 * - Data Cloud: lineage overview charts, pipeline throughput, schema distributions
 * - YAPPC: sprint burndown, velocity charts in retrospective boards
 * - AEP: agent execution metrics, flow success rates
 *
 * At high zoom levels the chart is fully interactive via a DOM overlay
 * (see src/react/DataChartOverlay.tsx).
 * At low zoom (bird's-eye view) the element draws a lightweight SVG-like thumbnail.
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export type ChartVariant =
  | "line"
  | "area"
  | "bar"
  | "stacked-bar"
  | "horizontal-bar"
  | "pie"
  | "donut"
  | "scatter"
  | "heatmap"
  | "treemap"
  | "funnel"
  | "sparkline";

export interface ChartDataPoint {
  label: string;
  value: number;
  /** Optional secondary grouping key (stacked charts) */
  group?: string;
  color?: string;
}

export interface ChartSeries {
  name: string;
  data: ChartDataPoint[];
  color?: string;
  /** Whether to show this series in the legend */
  showInLegend?: boolean;
}

export interface ChartAxis {
  label?: string;
  unit?: string;
  min?: number;
  max?: number;
  gridLines?: boolean;
}

export interface ChartDataProps extends BaseElementProps {
  variant?: ChartVariant;
  /** Chart heading displayed at the top */
  title?: string;
  /** Subtitle / description */
  subtitle?: string;
  /** One or more data series (defaults to empty) */
  series?: ChartSeries[];
  /** X-axis configuration */
  xAxis?: ChartAxis;
  /** Y-axis configuration */
  yAxis?: ChartAxis;
  /** Show legend */
  showLegend?: boolean;
  /** Show data labels on bars/segments */
  showDataLabels?: boolean;
  /** Color palette override */
  colors?: string[];
  /** Background color */
  backgroundColor?: string;
  /** Border radius */
  borderRadius?: number;
  /** Whether the chart updates in real-time via a data feed key */
  liveDataKey?: string;
  /** Refresh interval in ms for live data (0 = no auto-refresh) */
  refreshIntervalMs?: number;
}

// Default color palette
const DEFAULT_COLORS = [
  "#6366f1",
  "#06b6d4",
  "#10b981",
  "#f59e0b",
  "#ef4444",
  "#8b5cf6",
  "#ec4899",
  "#14b8a6",
];

export class DataChartElement extends CanvasElement {
  public variant: ChartVariant;
  public title: string | undefined;
  public subtitle: string | undefined;
  public series: ChartSeries[];
  public xAxis: ChartAxis;
  public yAxis: ChartAxis;
  public showLegend: boolean;
  public showDataLabels: boolean;
  public colors: string[];
  public backgroundColor: string;
  public borderRadius: number;
  public liveDataKey: string | undefined;
  public refreshIntervalMs: number;

  /** Alias for `variant` — convenience accessor matching legacy API */
  get chartType(): ChartVariant { return this.variant; }
  set chartType(v: ChartVariant) { this.variant = v; }

  constructor(props: ChartDataProps) {
    super(props);
    this.variant = props.variant ?? "bar";
    this.title = props.title;
    this.subtitle = props.subtitle;
    this.series = props.series ?? [];
    this.xAxis = props.xAxis ?? {};
    this.yAxis = props.yAxis ?? {};
    this.showLegend = props.showLegend ?? true;
    this.showDataLabels = props.showDataLabels ?? false;
    this.colors = props.colors ?? DEFAULT_COLORS;
    this.backgroundColor = props.backgroundColor ?? "#1e1e2e";
    this.borderRadius = props.borderRadius ?? 8;
    this.liveDataKey = props.liveDataKey;
    this.refreshIntervalMs = props.refreshIntervalMs ?? 0;
  }

  get type(): CanvasElementType {
    return "data-chart";
  }

  /** Update series data (called by live data feed) */
  updateSeries(series: ChartSeries[]): void {
    this.series = series;
  }

  // ---------------------------------------------------------------------------
  // Rendering (canvas 2D, viewport-adaptive)
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();
    this._drawBackground(ctx, b);

    const headerH = this._drawHeader(ctx, b, zoom);
    const chartArea = {
      x: b.x + 12,
      y: b.y + headerH + 4,
      w: b.w - 24,
      h: b.h - headerH - 16,
    };

    if (zoom < 0.25) {
      this._drawThumbnail(ctx, b);
    } else {
      switch (this.variant) {
        case "bar":
        case "stacked-bar":
          this._drawBarChart(ctx, chartArea);
          break;
        case "line":
        case "area":
          this._drawLineChart(ctx, chartArea);
          break;
        case "pie":
        case "donut":
          this._drawPieChart(ctx, chartArea, this.variant === "donut");
          break;
        case "sparkline":
          this._drawSparkline(ctx, chartArea);
          break;
        default:
          this._drawPlaceholder(ctx, chartArea, this.variant);
      }
    }

    ctx.restore();
  }

  private _drawBackground(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    ctx.fillStyle = this.backgroundColor;
    if (this.borderRadius > 0) {
      ctx.beginPath();
      ctx.roundRect(b.x, b.y, b.w, b.h, this.borderRadius);
      ctx.fill();
    } else {
      ctx.fillRect(b.x, b.y, b.w, b.h);
    }
  }

  private _drawHeader(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
    zoom: number,
  ): number {
    if (!this.title || zoom < 0.35) return 0;
    const titleH = 22;
    ctx.fillStyle = "rgba(255,255,255,0.9)";
    ctx.font = `bold ${Math.min(13, titleH * 0.65)}px sans-serif`;
    ctx.textBaseline = "top";
    ctx.fillText(this.title, b.x + 12, b.y + 8, b.w - 24);
    if (this.subtitle) {
      ctx.fillStyle = "rgba(255,255,255,0.5)";
      ctx.font = `${Math.min(10, titleH * 0.5)}px sans-serif`;
      ctx.fillText(this.subtitle, b.x + 12, b.y + 8 + titleH, b.w - 24);
      return titleH + 14 + 8;
    }
    return titleH + 8;
  }

  private _drawBarChart(
    ctx: CanvasRenderingContext2D,
    area: { x: number; y: number; w: number; h: number },
  ): void {
    const allValues = this.series.flatMap((s) => s.data.map((d) => d.value));
    const maxVal = Math.max(...allValues, 1);
    const firstSeries = this.series[0];
    if (!firstSeries) return;
    const count = firstSeries.data.length;
    if (count === 0) return;
    const barW = (area.w / count) * 0.7;
    const gap = (area.w / count) * 0.3;

    for (let i = 0; i < count; i++) {
      const point = firstSeries.data[i]!;
      const barH = (point.value / maxVal) * area.h;
      const x = area.x + i * (barW + gap);
      const y = area.y + area.h - barH;
      ctx.fillStyle = point.color ?? this.colors[i % this.colors.length] ?? "#6366f1";
      ctx.fillRect(x, y, barW, barH);
    }
  }

  private _drawLineChart(
    ctx: CanvasRenderingContext2D,
    area: { x: number; y: number; w: number; h: number },
  ): void {
    for (let si = 0; si < this.series.length; si++) {
      const series = this.series[si]!;
      if (series.data.length < 2) continue;
      const allVals = series.data.map((d) => d.value);
      const maxVal = Math.max(...allVals, 1);
      const color = series.color ?? this.colors[si % this.colors.length] ?? "#6366f1";

      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.lineJoin = "round";
      ctx.beginPath();

      if (this.variant === "area") {
        ctx.fillStyle = color + "33";
        const firstD = series.data[0]!;
        ctx.moveTo(
          area.x,
          area.y + area.h - (firstD.value / maxVal) * area.h,
        );
        for (let i = 1; i < series.data.length; i++) {
          const d = series.data[i]!;
          ctx.lineTo(
            area.x + (i / (series.data.length - 1)) * area.w,
            area.y + area.h - (d.value / maxVal) * area.h,
          );
        }
        ctx.lineTo(area.x + area.w, area.y + area.h);
        ctx.lineTo(area.x, area.y + area.h);
        ctx.closePath();
        ctx.fill();
        ctx.beginPath();
      }

      for (let i = 0; i < series.data.length; i++) {
        const d = series.data[i]!;
        const x = area.x + (i / (series.data.length - 1)) * area.w;
        const y = area.y + area.h - (d.value / maxVal) * area.h;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }
  }

  private _drawPieChart(
    ctx: CanvasRenderingContext2D,
    area: { x: number; y: number; w: number; h: number },
    donut: boolean,
  ): void {
    const firstSeries = this.series[0];
    if (!firstSeries) return;
    const total = firstSeries.data.reduce((s, d) => s + d.value, 0) || 1;
    const cx = area.x + area.w / 2;
    const cy = area.y + area.h / 2;
    const r = Math.min(area.w, area.h) / 2 - 4;
    let startAngle = -Math.PI / 2;

    for (let i = 0; i < firstSeries.data.length; i++) {
      const d = firstSeries.data[i]!;
      const slice = (d.value / total) * Math.PI * 2;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.arc(cx, cy, r, startAngle, startAngle + slice);
      ctx.closePath();
      ctx.fillStyle = d.color ?? this.colors[i % this.colors.length] ?? "#6366f1";
      ctx.fill();
      startAngle += slice;
    }

    if (donut) {
      ctx.beginPath();
      ctx.arc(cx, cy, r * 0.55, 0, Math.PI * 2);
      ctx.fillStyle = this.backgroundColor;
      ctx.fill();
    }
  }

  private _drawSparkline(
    ctx: CanvasRenderingContext2D,
    area: { x: number; y: number; w: number; h: number },
  ): void {
    const series = this.series[0];
    if (!series || series.data.length < 2) return;
    const vals = series.data.map((d) => d.value);
    const min = Math.min(...vals);
    const max = Math.max(...vals);
    const range = max - min || 1;
    const color = series.color ?? this.colors[0] ?? "#6366f1";

    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
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

  private _drawPlaceholder(
    ctx: CanvasRenderingContext2D,
    area: { x: number; y: number; w: number; h: number },
    variant: string,
  ): void {
    ctx.fillStyle = "rgba(255,255,255,0.4)";
    ctx.font = `${Math.min(12, area.h * 0.15)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(`[${variant}]`, area.x + area.w / 2, area.y + area.h / 2);
    ctx.textAlign = "left";
  }

  private _drawThumbnail(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    ctx.fillStyle = this.colors[0] ?? "#6366f1";
    ctx.font = `${Math.min(24, b.h * 0.4)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    const icon =
      this.variant === "pie" || this.variant === "donut"
        ? "◎"
        : this.variant === "line" || this.variant === "area"
          ? "📈"
          : "▅▇█▇▅";
    ctx.fillText(icon, b.x + b.w / 2, b.y + b.h / 2);
    ctx.textAlign = "left";
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }
}
