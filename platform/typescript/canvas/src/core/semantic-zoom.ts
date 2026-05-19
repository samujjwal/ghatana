/**
 * @fileoverview Semantic Zoom / Multilevel Visual Context for Canvas
 *
 * Provides zoom levels, focus paths, viewport contexts, and disclosure policies
 * for navigating complex visual information spaces. Product-neutral with no
 * business-specific semantics.
 *
 * @doc.type module
 * @doc.purpose Semantic zoom and multilevel visual context management
 * @doc.layer platform
 */

import { atom } from "jotai";

// ============================================================================
// SEMANTIC ZOOM LEVELS
// ============================================================================

/**
 * Predefined semantic zoom levels for canvas navigation.
 * Each level represents a different abstraction of information density.
 */
export type SemanticZoomLevel =
  | "overview" // Highest abstraction - clusters, summaries
  | "group" // Grouped collections - teams, modules
  | "node" // Individual elements - single items
  | "detail" // Detailed view - properties, attributes
  | "evidence" // Supporting data - references, sources
  | "source"; // Original source - raw data, code

/**
 * Detail level for content rendering within a semantic zoom context.
 *
 * Complements {@link SemanticZoomLevel} by specifying how much detail to render
 * for a specific node or edge regardless of the current viewport zoom level.
 * Use this to control per-element information density independently of the
 * global zoom state.
 *
 * - `"minimal"` — only identity information (id, type icon).
 * - `"summary"` — key properties and status indicators.
 * - `"full"` — all attributes visible in the viewport.
 * - `"expanded"` — full content plus auxiliary panels (annotations, references).
 */
export type DetailLevel = "minimal" | "summary" | "full" | "expanded";

/**
 * Band definition for a semantic zoom level.
 * Maps scale ranges to semantic meanings.
 */
export interface SemanticZoomBand {
  readonly level: SemanticZoomLevel;
  readonly minScale: number;
  readonly maxScale: number;
  readonly label: string;
  readonly description: string;
  readonly defaultScale: number;
  readonly showLabels: boolean;
  readonly showDetails: boolean;
  readonly showConnections: boolean;
}

/** Default zoom band configurations. */
export const DEFAULT_ZOOM_BANDS: readonly SemanticZoomBand[] = [
  {
    level: "overview",
    minScale: 0.1,
    maxScale: 0.3,
    label: "Overview",
    description: "Bird's eye view of the entire canvas",
    defaultScale: 0.2,
    showLabels: false,
    showDetails: false,
    showConnections: true,
  },
  {
    level: "group",
    minScale: 0.3,
    maxScale: 0.6,
    label: "Groups",
    description: "View clustered groups and collections",
    defaultScale: 0.5,
    showLabels: true,
    showDetails: false,
    showConnections: true,
  },
  {
    level: "node",
    minScale: 0.6,
    maxScale: 1.0,
    label: "Nodes",
    description: "Individual elements at standard size",
    defaultScale: 1.0,
    showLabels: true,
    showDetails: false,
    showConnections: true,
  },
  {
    level: "detail",
    minScale: 1.0,
    maxScale: 1.5,
    label: "Details",
    description: "Detailed view with property visibility",
    defaultScale: 1.2,
    showLabels: true,
    showDetails: true,
    showConnections: true,
  },
  {
    level: "evidence",
    minScale: 1.5,
    maxScale: 2.5,
    label: "Evidence",
    description: "Supporting references and annotations",
    defaultScale: 2.0,
    showLabels: true,
    showDetails: true,
    showConnections: true,
  },
  {
    level: "source",
    minScale: 2.5,
    maxScale: 5.0,
    label: "Source",
    description: "Raw source data and code view",
    defaultScale: 3.0,
    showLabels: true,
    showDetails: true,
    showConnections: false,
  },
] as const;

// ============================================================================
// FOCUS PATH
// ============================================================================

/**
 * A segment in the focus path representing navigation hierarchy.
 */
export interface FocusPathSegment {
  readonly id: string;
  readonly type: "group" | "node" | "detail" | "evidence" | "source";
  readonly label: string;
  readonly metadata?: Record<string, unknown>;
}

/**
 * Focus path represents the user's current navigation context.
 * Immutable stack-like structure for drill-down navigation.
 */
export interface FocusPath {
  readonly segments: readonly FocusPathSegment[];
  readonly depth: number;
}

/**
 * Create an empty focus path.
 */
export function createFocusPath(): FocusPath {
  return { segments: [], depth: 0 };
}

/**
 * Push a segment onto the focus path.
 */
export function pushFocusSegment(
  path: FocusPath,
  segment: FocusPathSegment,
): FocusPath {
  return {
    segments: [...path.segments, segment],
    depth: path.segments.length + 1,
  };
}

/**
 * Pop the last segment from the focus path.
 */
export function popFocusSegment(path: FocusPath): FocusPath {
  if (path.segments.length === 0) return path;
  return {
    segments: path.segments.slice(0, -1),
    depth: path.segments.length - 1,
  };
}

/**
 * Navigate to a specific depth in the focus path.
 */
export function truncateFocusPath(path: FocusPath, targetDepth: number): FocusPath {
  if (targetDepth < 0 || targetDepth >= path.segments.length) return path;
  return {
    segments: path.segments.slice(0, targetDepth),
    depth: targetDepth,
  };
}

/**
 * Get the current (deepest) segment in the focus path.
 */
export function getCurrentFocusSegment(
  path: FocusPath,
): FocusPathSegment | undefined {
  return path.segments[path.segments.length - 1];
}

// ============================================================================
// VIEWPORT CONTEXT
// ============================================================================

/**
 * Viewport context captures the current visual state of the canvas.
 * Persistable for session restoration.
 */
export interface ViewportContext {
  readonly centerX: number;
  readonly centerY: number;
  readonly scale: number;
  readonly semanticLevel: SemanticZoomLevel;
  readonly focusPath: FocusPath;
  readonly visibleBounds: {
    readonly minX: number;
    readonly minY: number;
    readonly maxX: number;
    readonly maxY: number;
  };
  readonly timestamp: number;
}

/**
 * Create a default viewport context.
 */
export function createViewportContext(
  overrides?: Partial<ViewportContext>,
): ViewportContext {
  const now = Date.now();
  return {
    centerX: 0,
    centerY: 0,
    scale: 1.0,
    semanticLevel: "node",
    focusPath: createFocusPath(),
    visibleBounds: { minX: 0, minY: 0, maxX: 0, maxY: 0 },
    timestamp: now,
    ...overrides,
  };
}

/**
 * Serialize viewport context for persistence.
 */
export function serializeViewportContext(context: ViewportContext): string {
  return JSON.stringify(context);
}

/**
 * Deserialize viewport context from string.
 */
export function deserializeViewportContext(json: string): ViewportContext {
  try {
    const parsed = JSON.parse(json) as ViewportContext;
    return createViewportContext(parsed);
  } catch {
    return createViewportContext();
  }
}

// ============================================================================
// CONTEXT SHIFT POLICY
// ============================================================================

/**
 * Policy for handling context shifts during navigation.
 */
export interface ContextShiftPolicy {
  readonly id: string;
  readonly preserveViewportOnFocusChange: boolean;
  readonly autoZoomToFit: boolean;
  readonly animateTransitions: boolean;
  readonly snapToSemanticLevels: boolean;
  readonly minTransitionDuration: number; // ms
  readonly maxTransitionDuration: number; // ms
  readonly keyboardNavigationEnabled: boolean;
}

/** Default context shift policy. */
export const DEFAULT_CONTEXT_SHIFT_POLICY: ContextShiftPolicy = {
  id: "default",
  preserveViewportOnFocusChange: false,
  autoZoomToFit: true,
  animateTransitions: true,
  snapToSemanticLevels: true,
  minTransitionDuration: 150,
  maxTransitionDuration: 500,
  keyboardNavigationEnabled: true,
};

/** Keyboard-only navigation policy. */
export const KEYBOARD_CONTEXT_SHIFT_POLICY: ContextShiftPolicy = {
  ...DEFAULT_CONTEXT_SHIFT_POLICY,
  id: "keyboard",
  animateTransitions: false,
  keyboardNavigationEnabled: true,
};

/** Reduced motion policy for accessibility. */
export const REDUCED_MOTION_CONTEXT_SHIFT_POLICY: ContextShiftPolicy = {
  ...DEFAULT_CONTEXT_SHIFT_POLICY,
  id: "reduced-motion",
  animateTransitions: false,
  minTransitionDuration: 0,
  maxTransitionDuration: 0,
};

/**
 * Evaluate if a context shift is allowed under the given policy.
 */
export function isContextShiftAllowed(
  _from: ViewportContext,
  _to: ViewportContext,
  policy: ContextShiftPolicy,
): boolean {
  // Default: always allow, policy governs how the shift happens
  return policy.keyboardNavigationEnabled || true;
}

/**
 * Calculate transition duration based on context distance and policy.
 */
export function calculateTransitionDuration(
  from: ViewportContext,
  to: ViewportContext,
  policy: ContextShiftPolicy,
): number {
  if (!policy.animateTransitions) return 0;

  // Calculate distance between viewports
  const dx = to.centerX - from.centerX;
  const dy = to.centerY - from.centerY;
  const scaleRatio = Math.max(to.scale / from.scale, from.scale / to.scale);
  const distance = Math.sqrt(dx * dx + dy * dy);

  // Base duration on distance and scale change
  const baseDuration = Math.min(
    distance / 100 + scaleRatio * 100,
    policy.maxTransitionDuration,
  );

  return Math.max(baseDuration, policy.minTransitionDuration);
}

// ============================================================================
// DETAIL DISCLOSURE POLICY
// ============================================================================

/**
 * Policy controlling how detail information is disclosed at different zoom levels.
 */
export interface DetailDisclosurePolicy {
  readonly id: string;
  readonly thresholds: ReadonlyMap<SemanticZoomLevel, DisclosureThreshold>;
  readonly progressiveDisclosure: boolean;
  readonly lazyLoadDetails: boolean;
}

/**
 * Threshold for disclosing details at a specific zoom level.
 */
export interface DisclosureThreshold {
  readonly level: SemanticZoomLevel;
  readonly showMetadata: boolean;
  readonly showAttributes: boolean;
  readonly showRelations: boolean;
  readonly showContent: boolean;
  readonly maxItemsToShow: number;
}

/** Progressive disclosure policy - reveals more as you zoom in. */
export const PROGRESSIVE_DISCLOSURE_POLICY: DetailDisclosurePolicy = {
  id: "progressive",
  thresholds: new Map([
    [
      "overview",
      {
        level: "overview",
        showMetadata: false,
        showAttributes: false,
        showRelations: false,
        showContent: false,
        maxItemsToShow: 0,
      },
    ],
    [
      "group",
      {
        level: "group",
        showMetadata: true,
        showAttributes: false,
        showRelations: true,
        showContent: false,
        maxItemsToShow: 5,
      },
    ],
    [
      "node",
      {
        level: "node",
        showMetadata: true,
        showAttributes: true,
        showRelations: true,
        showContent: false,
        maxItemsToShow: 10,
      },
    ],
    [
      "detail",
      {
        level: "detail",
        showMetadata: true,
        showAttributes: true,
        showRelations: true,
        showContent: true,
        maxItemsToShow: 25,
      },
    ],
    [
      "evidence",
      {
        level: "evidence",
        showMetadata: true,
        showAttributes: true,
        showRelations: true,
        showContent: true,
        maxItemsToShow: 50,
      },
    ],
    [
      "source",
      {
        level: "source",
        showMetadata: true,
        showAttributes: true,
        showRelations: false,
        showContent: true,
        maxItemsToShow: Number.POSITIVE_INFINITY,
      },
    ],
  ]),
  progressiveDisclosure: true,
  lazyLoadDetails: true,
};

/**
 * Get disclosure threshold for a specific zoom level.
 */
export function getDisclosureThreshold(
  policy: DetailDisclosurePolicy,
  level: SemanticZoomLevel,
): DisclosureThreshold {
  return (
    policy.thresholds.get(level) || {
      level,
      showMetadata: false,
      showAttributes: false,
      showRelations: false,
      showContent: false,
      maxItemsToShow: 0,
    }
  );
}

/**
 * Check if detail disclosure is allowed at a given zoom level.
 */
export function isDetailDisclosureAllowed(
  policy: DetailDisclosurePolicy,
  level: SemanticZoomLevel,
  detailType: keyof DisclosureThreshold,
): boolean {
  const threshold = getDisclosureThreshold(policy, level);
  return threshold[detailType] as boolean;
}

// ============================================================================
// ZOOM LEVEL RESOLUTION
// ============================================================================

/**
 * Resolve semantic zoom level from scale value.
 */
export function resolveZoomLevel(
  scale: number,
  bands: readonly SemanticZoomBand[] = DEFAULT_ZOOM_BANDS,
): SemanticZoomLevel {
  for (const band of bands) {
    if (scale >= band.minScale && scale <= band.maxScale) {
      return band.level;
    }
  }

  // Fallback: clamp to nearest band
  if (scale < bands[0].minScale) return bands[0].level;
  return bands[bands.length - 1].level;
}

/**
 * Get the band configuration for a zoom level.
 */
export function getZoomBand(
  level: SemanticZoomLevel,
  bands: readonly SemanticZoomBand[] = DEFAULT_ZOOM_BANDS,
): SemanticZoomBand | undefined {
  return bands.find((b) => b.level === level);
}

/**
 * Get default scale for a zoom level.
 */
export function getDefaultScaleForLevel(
  level: SemanticZoomLevel,
  bands: readonly SemanticZoomBand[] = DEFAULT_ZOOM_BANDS,
): number {
  const band = getZoomBand(level, bands);
  return band?.defaultScale ?? 1.0;
}

/**
 * Snap scale to nearest semantic level's default scale.
 */
export function snapToSemanticLevel(
  scale: number,
  bands: readonly SemanticZoomBand[] = DEFAULT_ZOOM_BANDS,
): number {
  const level = resolveZoomLevel(scale, bands);
  return getDefaultScaleForLevel(level, bands);
}

/**
 * Validate zoom configuration.
 * Returns validation result with error messages if invalid.
 */
export interface ZoomValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

/**
 * Validate semantic zoom bands configuration.
 */
export function validateZoomBands(
  bands: readonly SemanticZoomBand[],
): ZoomValidationResult {
  const errors: string[] = [];

  if (bands.length === 0) {
    errors.push("At least one zoom band is required");
    return { valid: false, errors };
  }

  // Check for gaps and overlaps
  const sorted = [...bands].sort((a, b) => a.minScale - b.minScale);

  // Validate each band range independently so single-band configurations are checked.
  for (const band of sorted) {
    if (band.maxScale < band.minScale) {
      errors.push(
        `Band "${band.level}" has invalid range: maxScale (${band.maxScale}) < minScale (${band.minScale})`,
      );
    }
  }

  for (let i = 0; i < sorted.length - 1; i++) {
    const current = sorted[i];
    const next = sorted[i + 1];

    if (next.minScale < current.maxScale) {
      errors.push(
        `Bands "${current.level}" and "${next.level}" overlap: ${current.maxScale} > ${next.minScale}`,
      );
    }

    if (next.minScale > current.maxScale) {
      errors.push(
        `Gap between bands "${current.level}" and "${next.level}": ${current.maxScale} to ${next.minScale}`,
      );
    }
  }

  // Check for duplicate levels
  const levels = bands.map((b) => b.level);
  const uniqueLevels = new Set(levels);
  if (levels.length !== uniqueLevels.size) {
    errors.push("Duplicate zoom levels detected");
  }

  return { valid: errors.length === 0, errors };
}

// ============================================================================
// KEYBOARD NAVIGATION
// ============================================================================

/**
 * Keyboard navigation commands for semantic zoom.
 */
export type ZoomKeyboardCommand =
  | "zoom-in"
  | "zoom-out"
  | "zoom-reset"
  | "focus-up"
  | "focus-down"
  | "focus-back"
  | "pan-left"
  | "pan-right"
  | "pan-up"
  | "pan-down";

/**
 * Default keyboard shortcuts for zoom navigation.
 */
export const ZOOM_KEYBOARD_SHORTCUTS: ReadonlyMap<ZoomKeyboardCommand, string[]> =
  new Map([
    ["zoom-in", ["Ctrl++", "Ctrl+=", "Plus"]],
    ["zoom-out", ["Ctrl+-", "Minus"]],
    ["zoom-reset", ["Ctrl+0", "Ctrl+Home"]],
    ["focus-up", ["Alt+Up", "Ctrl+Up"]],
    ["focus-down", ["Alt+Down", "Ctrl+Down"]],
    ["focus-back", ["Alt+Left", "Escape"]],
    ["pan-left", ["ArrowLeft"]],
    ["pan-right", ["ArrowRight"]],
    ["pan-up", ["ArrowUp"]],
    ["pan-down", ["ArrowDown"]],
  ]);

/**
 * Handle keyboard event for zoom navigation.
 */
export function handleZoomKeyboardEvent(
  event: KeyboardEvent,
  handlers: Partial<Record<ZoomKeyboardCommand, () => void>>,
): boolean {
  const key = getNormalizedKeyString(event);

  for (const [command, shortcuts] of ZOOM_KEYBOARD_SHORTCUTS) {
    if (shortcuts.some((shortcut) => matchesShortcut(key, shortcut))) {
      const handler = handlers[command];
      if (handler) {
        event.preventDefault();
        handler();
        return true;
      }
    }
  }

  return false;
}

/**
 * Normalize key event to string representation.
 */
function getNormalizedKeyString(event: KeyboardEvent): string {
  const parts: string[] = [];
  if (event.ctrlKey) parts.push("Ctrl");
  if (event.altKey) parts.push("Alt");
  if (event.shiftKey) parts.push("Shift");
  if (event.metaKey) parts.push("Meta");
  parts.push(event.key);
  return parts.join("+");
}

/**
 * Check if key matches shortcut pattern.
 */
function matchesShortcut(key: string, shortcut: string): boolean {
  return key.toLowerCase() === shortcut.toLowerCase();
}

// ============================================================================
// STATE ATOMS
// ============================================================================

/**
 * Current semantic zoom level atom.
 */
export const semanticZoomLevelAtom = atom<SemanticZoomLevel>("node");

/**
 * Current focus path atom.
 */
export const focusPathAtom = atom<FocusPath>(createFocusPath());

/**
 * Current viewport context atom.
 */
export const viewportContextAtom = atom<ViewportContext>(createViewportContext());

/**
 * Context shift policy atom.
 */
export const contextShiftPolicyAtom = atom<ContextShiftPolicy>(
  DEFAULT_CONTEXT_SHIFT_POLICY,
);

/**
 * Detail disclosure policy atom.
 */
export const detailDisclosurePolicyAtom = atom<DetailDisclosurePolicy>(
  PROGRESSIVE_DISCLOSURE_POLICY,
);

// ============================================================================
// SEMANTIC ZOOM MANAGER
// ============================================================================

/**
 * Manager for semantic zoom state and operations.
 */
export class SemanticZoomManager {
  private bands: readonly SemanticZoomBand[];
  private currentLevel: SemanticZoomLevel;
  private focusPath: FocusPath;
  private policy: ContextShiftPolicy;

  constructor(
    bands: readonly SemanticZoomBand[] = DEFAULT_ZOOM_BANDS,
    policy: ContextShiftPolicy = DEFAULT_CONTEXT_SHIFT_POLICY,
  ) {
    const validation = validateZoomBands(bands);
    if (!validation.valid) {
      throw new Error(
        `Invalid zoom configuration: ${validation.errors.join(", ")}`,
      );
    }

    this.bands = bands;
    this.currentLevel = "node";
    this.focusPath = createFocusPath();
    this.policy = policy;
  }

  /**
   * Get current zoom level.
   */
  getCurrentLevel(): SemanticZoomLevel {
    return this.currentLevel;
  }

  /**
   * Get current zoom band configuration.
   */
  getCurrentBand(): SemanticZoomBand | undefined {
    return getZoomBand(this.currentLevel, this.bands);
  }

  /**
   * Resolve zoom level from scale.
   */
  resolveLevel(scale: number): SemanticZoomLevel {
    return resolveZoomLevel(scale, this.bands);
  }

  /**
   * Set zoom level directly.
   */
  setLevel(level: SemanticZoomLevel): void {
    const band = getZoomBand(level, this.bands);
    if (!band) {
      throw new Error(`Unknown zoom level: ${level}`);
    }
    this.currentLevel = level;
  }

  /**
   * Zoom in to next semantic level.
   */
  zoomIn(): SemanticZoomLevel {
    const levels: SemanticZoomLevel[] = [
      "overview",
      "group",
      "node",
      "detail",
      "evidence",
      "source",
    ];
    const currentIndex = levels.indexOf(this.currentLevel);
    if (currentIndex < levels.length - 1) {
      this.currentLevel = levels[currentIndex + 1];
    }
    return this.currentLevel;
  }

  /**
   * Zoom out to previous semantic level.
   */
  zoomOut(): SemanticZoomLevel {
    const levels: SemanticZoomLevel[] = [
      "overview",
      "group",
      "node",
      "detail",
      "evidence",
      "source",
    ];
    const currentIndex = levels.indexOf(this.currentLevel);
    if (currentIndex > 0) {
      this.currentLevel = levels[currentIndex - 1];
    }
    return this.currentLevel;
  }

  /**
   * Get default scale for current level.
   */
  getDefaultScale(): number {
    return getDefaultScaleForLevel(this.currentLevel, this.bands);
  }

  /**
   * Get current focus path.
   */
  getFocusPath(): FocusPath {
    return this.focusPath;
  }

  /**
   * Push focus segment.
   */
  pushFocus(segment: FocusPathSegment): void {
    this.focusPath = pushFocusSegment(this.focusPath, segment);
  }

  /**
   * Pop focus segment.
   */
  popFocus(): void {
    this.focusPath = popFocusSegment(this.focusPath);
  }

  /**
   * Navigate focus to specific depth.
   */
  navigateToDepth(depth: number): void {
    this.focusPath = truncateFocusPath(this.focusPath, depth);
  }

  /**
   * Get context shift policy.
   */
  getPolicy(): ContextShiftPolicy {
    return this.policy;
  }

  /**
   * Set context shift policy.
   */
  setPolicy(policy: ContextShiftPolicy): void {
    this.policy = policy;
  }

  /**
   * Create viewport context from current state.
   */
  createViewportContext(
    centerX: number,
    centerY: number,
    scale: number,
  ): ViewportContext {
    return createViewportContext({
      centerX,
      centerY,
      scale,
      semanticLevel: this.currentLevel,
      focusPath: this.focusPath,
    });
  }
}
