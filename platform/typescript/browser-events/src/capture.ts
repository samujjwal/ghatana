import type {
  BrowserEvent,
  BrowserEventFilter,
  BrowserEventHandler,
  BrowserEventSource,
} from "./types";

// ---------------------------------------------------------------------------
// Capture status
// ---------------------------------------------------------------------------

export interface CaptureStatus {
  readonly tabs: boolean;
  readonly navigation: boolean;
  readonly network: boolean;
  readonly webRequest: boolean;
  readonly history: boolean;
}

// ---------------------------------------------------------------------------
// BrowserEventCapture interface
// ---------------------------------------------------------------------------

/**
 * Platform-level browser event capture contract.
 *
 * Implementations bridge the browser extension API (e.g. webextension-polyfill)
 * to the platform PlatformEvent<T> shape. Concrete implementations live in the
 * product layer (e.g. @dcmaar/browser-extension-core).
 *
 * @see UnifiedBrowserEventCapture for a concrete implementation that targets all event categories.
 */
export interface BrowserEventCapture {
  /** The extension instance identifier used as the event source. */
  readonly sourceId: string;

  /** Start capturing browser tab events. */
  captureTabEvents(filter?: BrowserEventFilter): void;

  /** Start capturing browser navigation events. */
  captureNavigationEvents(filter?: BrowserEventFilter): void;

  /** Start capturing network (fetch/XHR) events via content script. */
  captureNetworkEvents(filter?: BrowserEventFilter): void;

  /** Start capturing webRequest API events. */
  captureWebRequestEvents(filter?: BrowserEventFilter): void;

  /** Start capturing browser history events. */
  captureHistoryEvents(filter?: BrowserEventFilter): void;

  /** Register an event handler. Called for every captured event. */
  onEvent(handler: BrowserEventHandler): void;

  /** Remove a previously registered event handler. */
  offEvent(handler: BrowserEventHandler): void;

  /** Stop all capture and clean up listeners. */
  stop(): void;

  /** Returns the current capture activation status per category. */
  getStatus(): CaptureStatus;
}

// ---------------------------------------------------------------------------
// UnifiedBrowserEventCapture interface
// ---------------------------------------------------------------------------

/**
 * Extended capture interface that captures all supported event categories in one call.
 */
export interface UnifiedBrowserEventCapture extends BrowserEventCapture {
  /** Start capturing all supported categories with an optional shared filter. */
  captureAll(filter?: BrowserEventFilter): void;

  /** Returns exact counts of captured events per event type string. */
  getEventCounts(): Readonly<Record<string, number>>;

  /** Reset event count statistics. */
  clearEventCounts(): void;
}

// ---------------------------------------------------------------------------
// AbstractBrowserEventCapture base class
// ---------------------------------------------------------------------------

/**
 * Abstract base class that manages handler registration, event dispatch, and count tracking.
 * Product-layer implementations extend this and implement the `startCapture*` methods.
 *
 * @example
 * ```ts
 * class MyCaptureImpl extends AbstractBrowserEventCapture {
 *   protected async startCaptureTabEvents(filter?: BrowserEventFilter) {
 *     // register browser.tabs.onCreated etc.
 *   }
 *   // ...other abstract methods
 * }
 * ```
 */
export abstract class AbstractBrowserEventCapture
  implements UnifiedBrowserEventCapture
{
  protected readonly handlers: Set<BrowserEventHandler> = new Set();
  protected readonly counts: Map<string, number> = new Map();
  protected readonly activeCaptures: Set<string> = new Set();

  constructor(public readonly sourceId: string) {}

  // -------------------------------------------------------------------------
  // Handler management
  // -------------------------------------------------------------------------

  onEvent(handler: BrowserEventHandler): void {
    this.handlers.add(handler);
  }

  offEvent(handler: BrowserEventHandler): void {
    this.handlers.delete(handler);
  }

  // -------------------------------------------------------------------------
  // Dispatch
  // -------------------------------------------------------------------------

  /**
   * Dispatches a captured browser event to all registered handlers.
   * Errors in individual handlers are logged but do not interrupt other handlers.
   */
  protected async emit(event: BrowserEvent): Promise<void> {
    const count = this.counts.get(event.type) ?? 0;
    this.counts.set(event.type, count + 1);

    for (const handler of this.handlers) {
      try {
        await handler(event);
      } catch (err) {
        // Non-fatal: log and continue dispatching to other handlers
        console.error(
          `[BrowserEventCapture] Handler error for event "${event.type}":`,
          err
        );
      }
    }
  }

  // -------------------------------------------------------------------------
  // Count statistics
  // -------------------------------------------------------------------------

  getEventCounts(): Readonly<Record<string, number>> {
    return Object.fromEntries(this.counts.entries());
  }

  clearEventCounts(): void {
    this.counts.clear();
  }

  // -------------------------------------------------------------------------
  // CaptureAll
  // -------------------------------------------------------------------------

  captureAll(filter?: BrowserEventFilter): void {
    this.captureTabEvents(filter);
    this.captureNavigationEvents(filter);
    this.captureNetworkEvents(filter);
    this.captureWebRequestEvents(filter);
    this.captureHistoryEvents(filter);
  }

  // -------------------------------------------------------------------------
  // Status
  // -------------------------------------------------------------------------

  getStatus(): CaptureStatus {
    return {
      tabs: this.activeCaptures.has("tabs"),
      navigation: this.activeCaptures.has("navigation"),
      network: this.activeCaptures.has("network"),
      webRequest: this.activeCaptures.has("webRequest"),
      history: this.activeCaptures.has("history"),
    };
  }

  // -------------------------------------------------------------------------
  // Abstract capture methods — implement in product layer
  // -------------------------------------------------------------------------

  abstract captureTabEvents(filter?: BrowserEventFilter): void;
  abstract captureNavigationEvents(filter?: BrowserEventFilter): void;
  abstract captureNetworkEvents(filter?: BrowserEventFilter): void;
  abstract captureWebRequestEvents(filter?: BrowserEventFilter): void;
  abstract captureHistoryEvents(filter?: BrowserEventFilter): void;
  abstract stop(): void;

  // -------------------------------------------------------------------------
  // Event factory helpers for product-layer implementations
  // -------------------------------------------------------------------------

  /**
   * Creates a properly-shaped `BrowserEventSource` for this capture instance.
   */
  protected makeSource(): BrowserEventSource {
    return { type: "browser", id: this.sourceId };
  }

  /**
   * Creates a new event id suitable for use as `PlatformEvent.id`.
   */
  protected newEventId(): string {
    return typeof crypto !== "undefined" && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}
