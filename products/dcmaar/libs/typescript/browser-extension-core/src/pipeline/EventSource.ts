/**
 * @fileoverview Event Source Interface
 *
 * Defines the interface for event sources in the pipeline architecture.
 * Sources capture events from browser APIs or external sources.
 *
 * @module pipeline/EventSource
 */

/**
 * Event Source Interface
 *
 * Sources are responsible for capturing events from various sources
 * (browser APIs, user interactions, network, etc.) and emitting them
 * into the processing pipeline.
 *
 * @example
 * ```typescript
 * class TabSwitchSource implements EventSource<TabEvent> {
 *   name = 'tab-switch';
 *   private callback?: (event: TabEvent) => void;
 *
 *   async start() {
 *     chrome.tabs.onActivated.addListener(this.handleTabSwitch);
 *   }
 *
 *   async stop() {
 *     chrome.tabs.onActivated.removeListener(this.handleTabSwitch);
 *   }
 *
 *   onEvent(callback: (event: TabEvent) => void) {
 *     this.callback = callback;
 *   }
 *
 *   private handleTabSwitch = (activeInfo) => {
 *     this.callback?.({ tabId: activeInfo.tabId, timestamp: Date.now() });
 *   };
 * }
 * ```
 */
export interface EventSource<T = unknown> {
  /**
   * Unique identifier for this source
   */
  readonly name: string;

  /**
   * Initialize and start emitting events
   */
  start(): Promise<void>;

  /**
   * Stop emitting events and cleanup resources
   */
  stop(): Promise<void>;

  /**
   * Register callback for when events occur
   * @param callback Function to call with each event
   */
  onEvent(callback: (event: T) => void): void;

  /**
   * Optional: Filter events before emission
   * @param event Event to check
   * @returns true if event should be emitted, false otherwise
   */
  shouldEmit?(event: T): boolean;

  /**
   * Optional: Get current source status
   */
  getStatus?(): "started" | "stopped" | "error";
}

/**
 * Base Event Source implementation with common functionality
 */
export abstract class BaseEventSource<T = unknown> implements EventSource<T> {
  abstract readonly name: string;
  protected callback?: (event: T) => void;
  protected status: "started" | "stopped" | "error" = "stopped";

  abstract start(): Promise<void>;
  abstract stop(): Promise<void>;

  onEvent(callback: (event: T) => void): void {
    this.callback = callback;
  }

  shouldEmit(_event: T): boolean {
    return true; // Default: emit all events
  }

  getStatus(): "started" | "stopped" | "error" {
    return this.status;
  }

  /**
   * Helper method to emit events
   */
  protected emit(event: T): void {
    if (this.shouldEmit(event) && this.callback) {
      try {
        this.callback(event);
      } catch (error) {
        console.error(`[${this.name}] Error emitting event:`, error);
      }
    }
  }
}
