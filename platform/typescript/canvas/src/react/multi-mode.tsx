/**
 * @fileoverview Multi-mode canvas React API.
 *
 * Provides a unified `MultiModeCanvas` component that can switch between:
 * - `freeform`   — free-form drawing / whiteboard mode
 * - `graph`      — node-graph / flow-diagram mode
 * - `builder`    — high-fidelity UI builder mode
 * - `read-only`  — non-interactive presentation view
 *
 * The mode is controlled externally via props and can be animated between
 * transitions. Each mode preserves its viewport state independently.
 *
 * @doc.type component
 * @doc.purpose Multi-mode canvas component for @ghatana/canvas/react
 * @doc.layer platform
 * @doc.pattern CompositeComponent
 */
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

// ── Types ──────────────────────────────────────────────────────────────────

/** All supported canvas modes. */
export type CanvasMode = 'freeform' | 'graph' | 'builder' | 'read-only';

/** Per-mode viewport snapshot saved when switching modes. */
export interface ModeViewport {
  readonly x: number;
  readonly y: number;
  readonly zoom: number;
}

/** Canvas tool active in the current mode. */
export type CanvasTool =
  | 'select'
  | 'hand'
  | 'pencil'
  | 'shape'
  | 'text'
  | 'connector'
  | 'frame'
  | 'eraser';

/** State managed by the multi-mode canvas context. */
export interface MultiModeCanvasState {
  readonly mode: CanvasMode;
  readonly tool: CanvasTool;
  readonly zoom: number;
  readonly readOnly: boolean;
  readonly viewports: Readonly<Record<CanvasMode, ModeViewport>>;
  readonly transitioning: boolean;
}

/** Actions exposed by the multi-mode canvas context. */
export interface MultiModeCanvasActions {
  setMode(mode: CanvasMode): void;
  setTool(tool: CanvasTool): void;
  setZoom(zoom: number): void;
  zoomIn(): void;
  zoomOut(): void;
  resetViewport(): void;
}

export type MultiModeCanvasContextValue = MultiModeCanvasState & MultiModeCanvasActions;

// ── Context ────────────────────────────────────────────────────────────────

const MultiModeCanvasContext = createContext<MultiModeCanvasContextValue | null>(null);

/** Access the multi-mode canvas context. Must be used inside MultiModeCanvas. */
export function useMultiModeCanvas(): MultiModeCanvasContextValue {
  const ctx = useContext(MultiModeCanvasContext);
  if (ctx === null) {
    throw new Error('useMultiModeCanvas must be used inside <MultiModeCanvas>');
  }
  return ctx;
}

/** Access only the current mode without subscribing to other changes. */
export function useCanvasMode(): CanvasMode {
  return useMultiModeCanvas().mode;
}

/** Access only the active tool. */
export function useActiveTool(): CanvasTool {
  return useMultiModeCanvas().tool;
}

/** Access zoom level. */
export function useCanvasZoom(): number {
  return useMultiModeCanvas().zoom;
}

// ── Default viewports per mode ─────────────────────────────────────────────

const DEFAULT_VIEWPORT: ModeViewport = { x: 0, y: 0, zoom: 1 };

const DEFAULT_VIEWPORTS: Record<CanvasMode, ModeViewport> = {
  freeform: DEFAULT_VIEWPORT,
  graph: DEFAULT_VIEWPORT,
  builder: DEFAULT_VIEWPORT,
  'read-only': DEFAULT_VIEWPORT,
};

const DEFAULT_TOOL_PER_MODE: Record<CanvasMode, CanvasTool> = {
  freeform: 'pencil',
  graph: 'select',
  builder: 'select',
  'read-only': 'hand',
};

const MIN_ZOOM = 0.1;
const MAX_ZOOM = 8;
const ZOOM_STEP = 0.25;
const TRANSITION_DURATION_MS = 200;

// ── Provider ───────────────────────────────────────────────────────────────

export interface MultiModeCanvasProps {
  /** Starting mode. Default: 'freeform'. */
  initialMode?: CanvasMode;
  /** Force a specific mode (controlled). When set, `setMode` calls are no-ops unless `onModeChange` updates this prop. */
  mode?: CanvasMode;
  /** Fired when the user or API requests a mode change. */
  onModeChange?: (mode: CanvasMode) => void;
  /** Whether the canvas is globally read-only. Default: false. */
  readOnly?: boolean;
  /** CSS class applied to the root container. */
  className?: string;
  /** Inline style for the root container. */
  style?: React.CSSProperties;
  /** Accessible label. Default: 'Canvas'. */
  ariaLabel?: string;
  children: React.ReactNode;
}

/**
 * `MultiModeCanvas` — root provider component for multi-mode canvas.
 *
 * Wraps canvas content in a context that tracks mode, tool, and per-mode
 * viewport state. Child components (freeform layers, graph layers, etc.)
 * read from this context via `useMultiModeCanvas()`.
 *
 * @example
 * ```tsx
 * <MultiModeCanvas initialMode="graph" onModeChange={setMode}>
 *   <CanvasModeToolbar />
 *   <CanvasModeContent />
 * </MultiModeCanvas>
 * ```
 */
export function MultiModeCanvas({
  initialMode = 'freeform',
  mode: controlledMode,
  onModeChange,
  readOnly = false,
  className,
  style,
  ariaLabel = 'Canvas',
  children,
}: MultiModeCanvasProps): React.JSX.Element {
  const isControlled = controlledMode !== undefined;

  const [internalMode, setInternalMode] = useState<CanvasMode>(
    isControlled ? controlledMode : initialMode,
  );
  const [tool, setToolState] = useState<CanvasTool>(
    DEFAULT_TOOL_PER_MODE[isControlled ? controlledMode : initialMode],
  );
  const [viewports, setViewports] = useState<Record<CanvasMode, ModeViewport>>(
    DEFAULT_VIEWPORTS,
  );
  const [transitioning, setTransitioning] = useState(false);
  const transitionTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const activeMode = isControlled ? controlledMode : internalMode;

  const zoom = viewports[activeMode].zoom;

  const setMode = useCallback(
    (next: CanvasMode) => {
      if (next === activeMode) return;

      if (transitionTimer.current !== null) {
        clearTimeout(transitionTimer.current);
      }

      setTransitioning(true);
      transitionTimer.current = setTimeout(() => {
        setTransitioning(false);
        transitionTimer.current = null;
      }, TRANSITION_DURATION_MS);

      if (!isControlled) {
        setInternalMode(next);
        setToolState(DEFAULT_TOOL_PER_MODE[next]);
      }
      onModeChange?.(next);
    },
    [activeMode, isControlled, onModeChange],
  );

  const setTool = useCallback(
    (t: CanvasTool) => {
      if (!readOnly) setToolState(t);
    },
    [readOnly],
  );

  const setZoom = useCallback(
    (z: number) => {
      const clamped = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z));
      setViewports((prev) => ({
        ...prev,
        [activeMode]: { ...prev[activeMode], zoom: clamped },
      }));
    },
    [activeMode],
  );

  const zoomIn = useCallback(() => {
    setZoom(zoom + ZOOM_STEP);
  }, [setZoom, zoom]);

  const zoomOut = useCallback(() => {
    setZoom(zoom - ZOOM_STEP);
  }, [setZoom, zoom]);

  const resetViewport = useCallback(() => {
    setViewports((prev) => ({
      ...prev,
      [activeMode]: DEFAULT_VIEWPORT,
    }));
  }, [activeMode]);

  // Sync tool when controlled mode changes externally.
  useEffect(() => {
    if (isControlled && controlledMode !== undefined) {
      setToolState(DEFAULT_TOOL_PER_MODE[controlledMode]);
    }
  }, [isControlled, controlledMode]);

  // Cleanup on unmount.
  useEffect(() => {
    return () => {
      if (transitionTimer.current !== null) clearTimeout(transitionTimer.current);
    };
  }, []);

  const value = useMemo<MultiModeCanvasContextValue>(
    () => ({
      mode: activeMode,
      tool,
      zoom,
      readOnly,
      viewports,
      transitioning,
      setMode,
      setTool,
      setZoom,
      zoomIn,
      zoomOut,
      resetViewport,
    }),
    [activeMode, tool, zoom, readOnly, viewports, transitioning, setMode, setTool, setZoom, zoomIn, zoomOut, resetViewport],
  );

  return (
    <MultiModeCanvasContext.Provider value={value}>
      <div
        role="application"
        aria-label={ariaLabel}
        aria-readonly={readOnly || undefined}
        data-canvas-mode={activeMode}
        data-canvas-transitioning={transitioning || undefined}
        className={className}
        style={{ position: 'relative', overflow: 'hidden', width: '100%', height: '100%', ...style }}
      >
        {children}
      </div>
    </MultiModeCanvasContext.Provider>
  );
}

// ── Mode-conditional render helpers ───────────────────────────────────────

export interface CanvasModeGateProps {
  /** Render only when canvas is in one of these modes. */
  modes: CanvasMode[];
  children: React.ReactNode;
  /** Fallback rendered in all other modes. Default: null. */
  fallback?: React.ReactNode;
}

/**
 * Conditionally renders `children` only when the canvas is in one of the
 * given modes. Useful for per-mode toolbars, overlays, and panels.
 */
export function CanvasModeGate({
  modes,
  children,
  fallback = null,
}: CanvasModeGateProps): React.JSX.Element {
  const { mode } = useMultiModeCanvas();
  return <>{modes.includes(mode) ? children : fallback}</>;
}

// ── Zoom controls ──────────────────────────────────────────────────────────

export interface CanvasZoomControlsProps {
  className?: string;
}

/**
 * Ready-to-use zoom control buttons (+, –, reset).
 * Renders in the bottom-right corner by default via absolute positioning.
 */
export function CanvasZoomControls({
  className,
}: CanvasZoomControlsProps): React.JSX.Element {
  const { zoom, zoomIn, zoomOut, resetViewport, readOnly } = useMultiModeCanvas();
  const pct = Math.round(zoom * 100);

  return (
    <div
      role="toolbar"
      aria-label="Zoom controls"
      className={className}
      style={{
        position: 'absolute',
        bottom: 16,
        right: 16,
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        background: 'rgba(255,255,255,0.9)',
        border: '1px solid #e5e7eb',
        borderRadius: 6,
        padding: '4px 8px',
        userSelect: 'none',
        zIndex: 50,
      }}
    >
      <button
        type="button"
        aria-label="Zoom out"
        disabled={readOnly || zoom <= MIN_ZOOM}
        onClick={zoomOut}
        style={{ cursor: readOnly ? 'not-allowed' : 'pointer', border: 'none', background: 'none', fontSize: 16, padding: '0 4px' }}
      >
        −
      </button>
      <button
        type="button"
        aria-label={`Current zoom ${pct}%, click to reset`}
        onClick={resetViewport}
        style={{ cursor: 'pointer', border: 'none', background: 'none', fontSize: 12, minWidth: 44, textAlign: 'center' }}
      >
        {pct}%
      </button>
      <button
        type="button"
        aria-label="Zoom in"
        disabled={readOnly || zoom >= MAX_ZOOM}
        onClick={zoomIn}
        style={{ cursor: readOnly ? 'not-allowed' : 'pointer', border: 'none', background: 'none', fontSize: 16, padding: '0 4px' }}
      >
        +
      </button>
    </div>
  );
}

// ── Mode switcher ──────────────────────────────────────────────────────────

export interface CanvasModeSwitcherProps {
  /** Modes to show tabs for. Default: all modes. */
  availableModes?: CanvasMode[];
  /** Human-readable labels. Defaults to title-cased mode name. */
  modeLabels?: Partial<Record<CanvasMode, string>>;
  className?: string;
}

const DEFAULT_MODE_LABELS: Record<CanvasMode, string> = {
  freeform: 'Freeform',
  graph: 'Graph',
  builder: 'Builder',
  'read-only': 'View',
};

/**
 * Tab-strip component for switching between canvas modes.
 */
export function CanvasModeSwitcher({
  availableModes = ['freeform', 'graph', 'builder', 'read-only'],
  modeLabels = {},
  className,
}: CanvasModeSwitcherProps): React.JSX.Element {
  const { mode, setMode, readOnly } = useMultiModeCanvas();

  return (
    <div
      role="tablist"
      aria-label="Canvas mode"
      className={className}
      style={{ display: 'flex', gap: 2 }}
    >
      {availableModes.map((m) => {
        const label = modeLabels[m] ?? DEFAULT_MODE_LABELS[m];
        const isActive = m === mode;
        return (
          <button
            key={m}
            role="tab"
            type="button"
            aria-selected={isActive}
            aria-label={`Switch to ${label} mode`}
            disabled={readOnly && m !== 'read-only'}
            onClick={() => setMode(m)}
            style={{
              padding: '4px 12px',
              fontSize: 13,
              fontWeight: isActive ? 600 : 400,
              border: '1px solid',
              borderColor: isActive ? '#3b82f6' : '#e5e7eb',
              borderRadius: 4,
              background: isActive ? '#eff6ff' : '#fff',
              color: isActive ? '#1d4ed8' : '#374151',
              cursor: readOnly && m !== 'read-only' ? 'not-allowed' : 'pointer',
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}
