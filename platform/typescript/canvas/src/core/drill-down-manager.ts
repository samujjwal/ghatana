/**
 * Drill-Down Navigation Manager
 *
 * @doc.type class
 * @doc.purpose Manages hierarchical canvas navigation (zoom-into / zoom-out of frames and portals)
 * @doc.layer core
 * @doc.pattern Manager
 *
 * Drill-down enables a hierarchy of canvas documents to be navigated
 * like nested containers, where double-clicking a frame or portal element
 * "enters" it — setting a new active root with its own coordinate space —
 * and a breadcrumb trail allows navigating back out.
 *
 * Architecture:
 * - Each "level" in the hierarchy is an entry on the navigation stack.
 * - Entering a level pans/zooms to the frame/portal bounds and then
 *   conceptually clips the canvas to that region.
 * - The drill-down manager fires subscription callbacks when the stack changes
 *   so that the chrome (breadcrumbs, back button) can update reactively.
 */

export type DrillDownEntryType = "root" | "frame" | "portal";

export interface DrillDownEntry {
  /** Unique level identifier */
  id: string;
  /** Human-readable label shown in the breadcrumb */
  label: string;
  /** Type of entry point */
  type: DrillDownEntryType;
  /**
   * Element ID that was entered (frame or portal element ID).
   * undefined for the root level.
   */
  elementId?: string;
  /**
   * For portal entries: the canvas document ID loaded.
   */
  documentId?: string;
  /**
   * Viewport state that was active when this entry was pushed.
   * Restored on pop.
   */
  savedViewport?: { x: number; y: number; zoom: number };
}

export type DrillDownListener = (stack: DrillDownEntry[]) => void;

/**
 * DrillDownManager
 *
 * Singleton-style class that products/canvas hosts instantiate once and
 * attach to the canvas controller.
 */
export class DrillDownManager {
  private _stack: DrillDownEntry[] = [];
  private _listeners = new Set<DrillDownListener>();

  constructor(rootLabel: string = "Canvas") {
    this._stack = [
      {
        id: "root",
        label: rootLabel,
        type: "root",
      },
    ];
  }

  // ---------------------------------------------------------------------------
  // Stack accessors
  // ---------------------------------------------------------------------------

  /** The full navigation stack (root at index 0, deepest at end) */
  get stack(): readonly DrillDownEntry[] {
    return this._stack;
  }

  /** Current active (deepest) entry */
  get current(): DrillDownEntry {
    const last = this._stack[this._stack.length - 1];
    if (!last) throw new Error("DrillDownManager: stack is empty");
    return last;
  }

  /** True when we are deeper than the root level */
  get canGoBack(): boolean {
    return this._stack.length > 1;
  }

  /** True when we are at the root level */
  get isAtRoot(): boolean {
    return this._stack.length === 1;
  }

  /** Alias for `stack` — breadcrumbs-friendly name for the full navigation path */
  get breadcrumbs(): readonly DrillDownEntry[] {
    return this._stack;
  }

  /** Depth (0 = root) */
  get depth(): number {
    return this._stack.length - 1;
  }

  // ---------------------------------------------------------------------------
  // Navigation
  // ---------------------------------------------------------------------------

  /**
   * Enter a frame or portal element.
   *
   * @param entry - Information about the level being entered.
   *                Caller should snapshot current viewport into `savedViewport`.
   */
  enter(entry: Omit<DrillDownEntry, "id"> & { id?: string }): void {
    const id = entry.id ?? `level-${Date.now()}`;
    this._stack = [...this._stack, { ...entry, id }];
    this._notify();
  }

  /**
   * Navigate back one level.
   * Returns the entry that was popped.
   */
  back(): DrillDownEntry | undefined {
    if (!this.canGoBack) return undefined;
    const popped = this._stack[this._stack.length - 1]!;
    this._stack = this._stack.slice(0, -1);
    this._notify();
    return popped;
  }

  /**
   * Navigate to a specific level by index (pop to that level).
   */
  goTo(index: number): void {
    if (index < 0 || index >= this._stack.length) return;
    this._stack = this._stack.slice(0, index + 1);
    this._notify();
  }

  /**
   * Reset to root level.
   */
  backToRoot(): void {
    if (this._stack.length <= 1) return;
    this._stack = [this._stack[0]!];
    this._notify();
  }

  /**
   * Update the root label (e.g., when the document name changes).
   */
  setRootLabel(label: string): void {
    const root = this._stack[0];
    if (root) {
      this._stack = [{ ...root, label }, ...this._stack.slice(1)];
      this._notify();
    }
  }

  // ---------------------------------------------------------------------------
  // Subscriptions
  // ---------------------------------------------------------------------------

  subscribe(listener: DrillDownListener): () => void {
    this._listeners.add(listener);
    return () => { this._listeners.delete(listener); };
  }

  private _notify(): void {
    const snapshot = [...this._stack];
    for (const l of this._listeners) {
      l(snapshot);
    }
  }
}
