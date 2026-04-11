/**
 * Live React Element — renders an interactive, editable React component inside the canvas.
 *
 * @doc.type class
 * @doc.purpose Canvas element that hosts a live, hot-reloadable React component tree
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Use-cases:
 * - High-fidelity interactive prototype embedded in YAPPC design canvas
 * - Live form / widget preview during product design sessions
 * - Editable React page section with real props exposed in the property panel
 *
 * Architecture:
 * The element creates a React portal rendered into a positioned <div> that is
 * overlaid on the canvas coordinate-space box.  The host application must call
 * `LiveReactOverlayManager.mount()` on every render frame to synchronise the
 * DOM overlay with the canvas viewport transform.
 *
 * Props are live-editable via `updateProps()` from the property panel integration.
 * A `propsSchema` (JSON Schema draft-7) describes what props are user-editable.
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

/** A serializable prop value (no functions) */
export type PropValue =
  | string
  | number
  | boolean
  | null
  | PropValue[]
  | { [key: string]: PropValue };

/** JSON Schema (subset) describing a single editable prop */
export interface PropSchema {
  type: "string" | "number" | "boolean" | "object" | "array";
  title?: string;
  description?: string;
  default?: PropValue;
  enum?: PropValue[];
  minimum?: number;
  maximum?: number;
}

/** Describes the editable surface of the component */
export type PropsSchema = Record<string, PropSchema>;

/** Import descriptor that lets the canvas engine dynamically load the component */
export interface LiveReactImport {
  /**
   * Module specifier that will be passed to `import()`.
   * Must resolve within the product's module graph.
   * Example: `"@yappc/canvas-components/LiveFormPreview"`
   */
  moduleSpecifier: string;
  /** Named export to use (omit for default export) */
  exportName?: string;
}

export interface LiveReactProps extends BaseElementProps {
  /**
   * The fully-qualified component to render.
   * Alternatively pass a `componentKey` and register components ahead of time
   * via `LiveReactRegistry`.
   */
  importDescriptor?: LiveReactImport;
  /**
   * Key used to look up a pre-registered component from the global
   * `LiveReactRegistry`.  Preferred for security (no arbitrary imports).
   */
  componentKey?: string;
  /** Initial props passed to the component */
  componentProps?: Record<string, PropValue>;
  /** JSON Schema describing which props are user-editable */
  propsSchema?: PropsSchema;
  /** Display name shown in the element header */
  displayName?: string;
  /** Whether the component is interactive (pointer events through to React) */
  interactive?: boolean;
  /** Whether to show the component title bar */
  showTitleBar?: boolean;
  /** Theme override to pass to the component */
  themeOverride?: "light" | "dark";
  /** Error boundary fallback message */
  errorFallback?: string;
}

// ---------------------------------------------------------------------------
// Global component registry (pre-register components to avoid eval)
// ---------------------------------------------------------------------------

type ComponentEntry = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  component: React.ComponentType<any>;
  propsSchema?: PropsSchema;
  displayName?: string;
};

const _registry = new Map<string, ComponentEntry>();

/**
 * LiveReactRegistry — register React components that can be embedded in the canvas.
 *
 * Registering components ahead of time is the safe, recommended approach.
 * It avoids dynamic `import()` of arbitrary paths.
 */
export const LiveReactRegistry = {
  register(key: string, entry: ComponentEntry): void {
    _registry.set(key, entry);
  },
  get(key: string): ComponentEntry | undefined {
    return _registry.get(key);
  },
  has(key: string): boolean {
    return _registry.has(key);
  },
  unregister(key: string): void {
    _registry.delete(key);
  },
  keys(): string[] {
    return Array.from(_registry.keys());
  },
};

// ---------------------------------------------------------------------------
// Element class
// ---------------------------------------------------------------------------

/**
 * LiveReactElement — canvas element that hosts a React component overlay.
 *
 * Rendering is split:
 * 1. `render(ctx)` — draws a placeholder frame on the 2D canvas (always)
 * 2. The overlying `LiveReactOverlay` React component (mounted by the canvas
 *    host) renders the actual React component in a positioned <div> on top.
 */
export class LiveReactElement extends CanvasElement {
  public importDescriptor: LiveReactImport | undefined;
  public componentKey: string | undefined;
  public componentProps: Record<string, PropValue>;
  public propsSchema: PropsSchema;
  public displayName: string;
  public interactive: boolean;
  public showTitleBar: boolean;
  public themeOverride: "light" | "dark" | undefined;
  public errorFallback: string;

  constructor(props: LiveReactProps) {
    super(props);
    this.importDescriptor = props.importDescriptor;
    this.componentKey = props.componentKey;
    this.componentProps = props.componentProps ?? {};
    this.propsSchema = props.propsSchema ?? {};
    this.displayName =
      props.displayName ??
      props.componentKey ??
      props.importDescriptor?.exportName ??
      "React Component";
    this.interactive = props.interactive ?? true;
    this.showTitleBar = props.showTitleBar ?? true;
    this.themeOverride = props.themeOverride;
    this.errorFallback = props.errorFallback ?? "Component failed to render";
  }

  get type(): CanvasElementType {
    return "live-react";
  }

  /**
   * Update a single prop and emit change. The canvas host is expected to
   * subscribe to element changes and re-render the overlay.
   */
  updateProp(key: string, value: PropValue): void {
    this.componentProps = { ...this.componentProps, [key]: value };
  }

  /**
   * Resolve the registered or dynamically-loaded component.
   * Returns null if the key is not registered.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  resolveComponent(): React.ComponentType<any> | null {
    if (this.componentKey) {
      return _registry.get(this.componentKey)?.component ?? null;
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // 2D canvas rendering: placeholder frame while React overlay loads
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();
    const titleH = this.showTitleBar ? 28 : 0;

    // Outer frame
    ctx.strokeStyle = "#6366f1";
    ctx.lineWidth = 1.5 / zoom;
    ctx.setLineDash([4, 3]);
    ctx.strokeRect(b.x, b.y, b.w, b.h);
    ctx.setLineDash([]);

    // Title bar
    if (this.showTitleBar) {
      ctx.fillStyle = "#312e81";
      ctx.fillRect(b.x, b.y, b.w, titleH);
      if (zoom > 0.4) {
        ctx.fillStyle = "#c7d2fe";
        ctx.font = `bold ${Math.min(11, titleH * 0.5)}px sans-serif`;
        ctx.textBaseline = "middle";
        ctx.fillText(`⚛ ${this.displayName}`, b.x + 8, b.y + titleH / 2);
      }
    }

    // Body placeholder (will be covered by real React overlay when rendered)
    ctx.fillStyle = "rgba(99,102,241,0.06)";
    ctx.fillRect(b.x, b.y + titleH, b.w, b.h - titleH);

    // "Live" badge
    if (zoom > 0.5) {
      const bW = 36;
      const bH = 16;
      ctx.fillStyle = "#4ade80";
      ctx.beginPath();
      ctx.roundRect(b.x + b.w - bW - 4, b.y + b.h - bH - 4, bW, bH, 4);
      ctx.fill();
      ctx.fillStyle = "#052e16";
      ctx.font = `bold ${bH * 0.6}px sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText("LIVE", b.x + b.w - bW / 2 - 4, b.y + b.h - bH / 2 - 4);
      ctx.textAlign = "left";
    }

    ctx.restore();
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }
}

// ---------------------------------------------------------------------------
// React overlay component (renders in DOM, not on canvas 2D context)
// ---------------------------------------------------------------------------
// The actual React component for the overlay lives in:
//   `src/react/LiveReactOverlay.tsx`
// so that tree-shaking removes it in non-React environments.
