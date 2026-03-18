export interface UndoRedoItem {
  id: string;
  type: "add" | "remove" | "update" | "move";
  elementId: string;
  data: unknown;
  timestamp: number;
}

export interface UndoRedoOptions {
  maxStackSize?: number;
  debounceMs?: number;
}

export class UndoRedoManager {
  private undoStack: UndoRedoItem[] = [];
  private redoStack: UndoRedoItem[] = [];
  private maxStackSize: number;
  private debounceTimer: number | null = null;
  private listeners: Set<(canUndo: boolean, canRedo: boolean) => void> =
    new Set();

  constructor(options: UndoRedoOptions = {}) {
    this.maxStackSize = options.maxStackSize || 50;
  }

  addItem(item: UndoRedoItem): void {
    // Clear redo stack when new action is performed
    this.redoStack = [];

    // Add to undo stack
    this.undoStack.push(item);

    // Limit stack size
    if (this.undoStack.length > this.maxStackSize) {
      this.undoStack.shift();
    }

    this.notifyListeners();
  }

  undo(): UndoRedoItem | null {
    if (this.undoStack.length === 0) return null;

    const item = this.undoStack.pop()!;
    this.redoStack.push(item);
    this.notifyListeners();

    return item;
  }

  redo(): UndoRedoItem | null {
    if (this.redoStack.length === 0) return null;

    const item = this.redoStack.pop()!;
    this.undoStack.push(item);
    this.notifyListeners();

    return item;
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  clear(): void {
    this.undoStack = [];
    this.redoStack = [];
    this.notifyListeners();
  }

  subscribe(
    listener: (canUndo: boolean, canRedo: boolean) => void,
  ): () => void {
    this.listeners.add(listener);
    listener(this.canUndo(), this.canRedo());

    return () => this.listeners.delete(listener);
  }

  private notifyListeners(): void {
    for (const listener of this.listeners) {
      listener(this.canUndo(), this.canRedo());
    }
  }

  getUndoStack(): UndoRedoItem[] {
    return [...this.undoStack];
  }

  getRedoStack(): UndoRedoItem[] {
    return [...this.redoStack];
  }
}

export interface KeyboardShortcut {
  key: string;
  modifiers: ("ctrl" | "shift" | "alt" | "meta")[];
  action: () => void;
  description?: string;
  precondition?: () => boolean;
}

export class KeyboardShortcutManager {
  private shortcuts: Map<string, KeyboardShortcut> = new Map();
  private listeners: Set<(event: KeyboardEvent, handled: boolean) => void> =
    new Set();

  register(shortcut: KeyboardShortcut): void {
    const key = this.getShortcutKey(shortcut.key, shortcut.modifiers);
    this.shortcuts.set(key, shortcut);
  }

  unregister(
    key: string,
    modifiers: ("ctrl" | "shift" | "alt" | "meta")[],
  ): void {
    const shortcutKey = this.getShortcutKey(key, modifiers);
    this.shortcuts.delete(shortcutKey);
  }

  handle(event: KeyboardEvent): boolean {
    const key = this.getShortcutKey(event.key, this.getModifiers(event));
    const shortcut = this.shortcuts.get(key);

    if (!shortcut) {
      this.notifyListeners(event, false);
      return false;
    }

    if (shortcut.precondition && !shortcut.precondition()) {
      this.notifyListeners(event, false);
      return false;
    }

    event.preventDefault();
    event.stopPropagation();
    shortcut.action();

    this.notifyListeners(event, true);
    return true;
  }

  subscribe(
    listener: (event: KeyboardEvent, handled: boolean) => void,
  ): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(event: KeyboardEvent, handled: boolean): void {
    for (const listener of this.listeners) {
      listener(event, handled);
    }
  }

  private getShortcutKey(
    key: string,
    modifiers: ("ctrl" | "shift" | "alt" | "meta")[],
  ): string {
    return [...modifiers.sort(), key.toLowerCase()].join("+");
  }

  private getModifiers(
    event: KeyboardEvent,
  ): ("ctrl" | "shift" | "alt" | "meta")[] {
    const modifiers: ("ctrl" | "shift" | "alt" | "meta")[] = [];

    if (event.ctrlKey) modifiers.push("ctrl");
    if (event.shiftKey) modifiers.push("shift");
    if (event.altKey) modifiers.push("alt");
    if (event.metaKey) modifiers.push("meta");

    return modifiers;
  }

  getAll(): KeyboardShortcut[] {
    return Array.from(this.shortcuts.values());
  }

  clear(): void {
    this.shortcuts.clear();
  }
}

export interface PerformanceMetrics {
  renderTime: number;
  elementCount: number;
  fps: number;
  memoryUsage?: number;
}

export class PerformanceMonitor {
  private metrics: PerformanceMetrics = {
    renderTime: 0,
    elementCount: 0,
    fps: 60,
    memoryUsage: 0,
  };

  private listeners: Set<(metrics: PerformanceMetrics) => void> = new Set();
  private frameCount: number = 0;
  private lastFrameTime: number = performance.now();
  private rafId: number | null = null;

  startMonitoring(): void {
    this.measureFrame();
  }

  stopMonitoring(): void {
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  updateMetrics(elementCount: number, renderTime: number): void {
    this.metrics.elementCount = elementCount;
    this.metrics.renderTime = renderTime;

    // Calculate FPS
    this.frameCount++;
    const now = performance.now();
    const delta = now - this.lastFrameTime;

    if (delta >= 1000) {
      this.metrics.fps = Math.round((this.frameCount * 1000) / delta);
      this.frameCount = 0;
      this.lastFrameTime = now;
    }

    // Memory usage if available
    if ("memory" in performance) {
      this.metrics.memoryUsage = (performance as unknown as Record<string, Record<string, number>>).memory.usedJSHeapSize;
    }

    this.notifyListeners();
  }

  subscribe(listener: (metrics: PerformanceMetrics) => void): () => void {
    this.listeners.add(listener);
    listener(this.metrics);
    return () => this.listeners.delete(listener);
  }

  getMetrics(): PerformanceMetrics {
    return { ...this.metrics };
  }

  private measureFrame(): void {
    this.rafId = requestAnimationFrame(() => {
      this.frameCount++;
      this.measureFrame();
    });
  }

  private notifyListeners(): void {
    for (const listener of this.listeners) {
      listener({ ...this.metrics });
    }
  }
}

export interface VirtualizationOptions {
  itemHeight: number;
  containerHeight: number;
  overscan?: number;
  threshold?: number;
}

export class VirtualizationManager {
  private options: VirtualizationOptions;
  private scrollTop: number = 0;
  private visibleRange: { start: number; end: number } = { start: 0, end: 0 };

  constructor(options: VirtualizationOptions) {
    this.options = {
      overscan: 5,
      threshold: 100,
      ...options,
    };
  }

  updateScrollTop(scrollTop: number): void {
    this.scrollTop = scrollTop;
    this.calculateVisibleRange();
  }

  getVisibleRange(): { start: number; end: number } {
    return { ...this.visibleRange };
  }

  getVisibleItems<T>(items: T[]): T[] {
    const { start, end } = this.visibleRange;
    return items.slice(start, end);
  }

  shouldVirtualize(itemCount: number): boolean {
    return itemCount > (this.options.threshold || 100);
  }

  private calculateVisibleRange(): void {
    const { itemHeight, containerHeight, overscan } = this.options;

    const start = Math.max(
      0,
      Math.floor(this.scrollTop / itemHeight) - (overscan || 0),
    );
    const visibleCount = Math.ceil(containerHeight / itemHeight);
    const end = Math.min(
      this.visibleRange.end || start + visibleCount + (overscan || 0) * 2,
      start + visibleCount + (overscan || 0) * 2,
    );

    this.visibleRange = { start, end };
  }
}
