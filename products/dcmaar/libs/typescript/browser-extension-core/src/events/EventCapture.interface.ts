/**
 * @fileoverview Event Capture Interface
 *
 * Defines the contract for capturing browser events (tabs, navigation, network, etc.)
 * Implementations should use browser extension APIs only.
 *
 * @module core/interfaces/EventCapture
 */

/**
 * Browser event types
 */
export type BrowserEvent =
  | TabEvent
  | NavigationEvent
  | NetworkEvent
  | WebRequestEvent
  | HistoryEvent
  | FlowEvent;

/**
 * Tab event
 */
export interface TabEvent {
  type: 'tab';
  action: 'created' | 'updated' | 'removed' | 'activated' | 'moved' | 'attached' | 'detached';
  tabId: number;
  windowId?: number;
  url?: string;
  title?: string;
  active?: boolean;
  timestamp: number;
}

/**
 * Navigation event
 */
export interface NavigationEvent {
  type: 'navigation';
  action: 'before' | 'committed' | 'completed' | 'error' | 'replaced';
  tabId: number;
  url: string;
  frameId: number;
  transitionType?: string;
  timestamp: number;
}

/**
 * Network request event
 */
export interface NetworkEvent {
  type: 'network';
  action: 'request' | 'response' | 'error' | 'redirect';
  requestId: string;
  url: string;
  method: string;
  tabId?: number;
  statusCode?: number;
  contentType?: string;
  duration?: number;
  error?: string;
  timestamp: number;
}

/**
 * Web request event
 */
export interface WebRequestEvent {
  type: 'webrequest';
  action: 'before' | 'sent' | 'headers' | 'redirect' | 'response' | 'completed' | 'error';
  requestId: string;
  url: string;
  method: string;
  tabId?: number;
  resourceType: string; // main_frame, sub_frame, stylesheet, script, image, etc.
  timestamp: number;
}

/**
 * History navigation event
 */
export interface HistoryEvent {
  type: 'history';
  action: 'visited' | 'deleted';
  url: string;
  title?: string;
  visitTime: number;
  timestamp: number;
}

/**
 * Flow lifecycle event
 */
export interface FlowEvent {
  type: 'flow';
  flowId: string;
  action: 'start' | 'step' | 'complete' | 'abandon' | 'error';
  step?: string;
  url?: string;
  tabId?: number;
  timestamp: number;
  data?: Record<string, unknown>;
}

/**
 * Event handler function
 */
export type EventHandler<T = BrowserEvent> = (event: T) => void | Promise<void>;

/**
 * Event filter options
 */
export interface EventFilter {
  /** Filter by event types */
  types?: Array<BrowserEvent['type']>;
  /** Filter by URL patterns */
  urlPatterns?: string[];
  /** Filter by tab IDs */
  tabIds?: number[];
  /** Filter by window IDs */
  windowIds?: number[];
}

/**
 * Event Capture Interface
 *
 * Captures browser events using extension APIs and forwards them to handlers.
 * Implementations should be lightweight and non-blocking.
 *
 * @example
 * ```typescript
 * class TabEventCapture implements EventCapture {
 *   private handlers: Set<EventHandler> = new Set();
 *
 *   captureTabEvents(): void {
 *     browser.tabs.onCreated.addListener((tab) => {
 *       const event: TabEvent = {
 *         type: 'tab',
 *         action: 'created',
 *         tabId: tab.id!,
 *         url: tab.url,
 *         title: tab.title,
 *         timestamp: Date.now(),
 *       };
 *       this.handlers.forEach(h => h(event));
 *     });
 *   }
 *
 *   onEvent(handler: EventHandler): void {
 *     this.handlers.add(handler);
 *   }
 * }
 * ```
 */
export interface EventCapture {
  /**
   * Start capturing tab events (created, updated, removed, activated)
   *
   * @param filter - Optional filter for tab events
   */
  captureTabEvents(filter?: EventFilter): void;

  /**
   * Start capturing navigation events (before, committed, completed)
   *
   * @param filter - Optional filter for navigation events
   */
  captureNavigationEvents(filter?: EventFilter): void;

  /**
   * Start capturing network request events
   *
   * @param filter - Optional filter for network events
   */
  captureNetworkEvents(filter?: EventFilter): void;

  /**
   * Start capturing web request events (before, sent, response, etc.)
   *
   * @param filter - Optional filter for web request events
   */
  captureWebRequestEvents(filter?: EventFilter): void;

  /**
   * Start capturing history events (visited, deleted)
   *
   * @param filter - Optional filter for history events
   */
  captureHistoryEvents(filter?: EventFilter): void;

  /**
   * Register event handler
   *
   * @param handler - Function to call when events are captured
   */
  onEvent(handler: EventHandler): void;

  /**
   * Remove event handler
   *
   * @param handler - Handler to remove
   */
  offEvent(handler: EventHandler): void;

  /**
   * Stop all event capture and cleanup listeners
   */
  stop(): void;

  /**
   * Get capture status
   *
   * @returns Object with status of each event capture type
   */
  getStatus(): {
    tabs: boolean;
    navigation: boolean;
    network: boolean;
    webrequest: boolean;
    history: boolean;
  };
}

/**
 * Unified event capture for all browser events
 */
export interface UnifiedEventCapture extends EventCapture {
  /**
   * Start capturing all supported events
   *
   * @param filter - Optional global filter
   */
  captureAll(filter?: EventFilter): void;

  /**
   * Get count of captured events by type
   */
  getEventCounts(): Record<BrowserEvent['type'], number>;

  /**
   * Clear event counts
   */
  clearEventCounts(): void;
}
