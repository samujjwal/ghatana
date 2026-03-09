/**
 * @fileoverview Unified Event Capture
 *
 * Captures browser events from tabs, navigation, network, web requests, and history.
 * Uses browser.tabs, browser.webNavigation, browser.webRequest, and browser.history APIs.
 *
 * @module browser/events/UnifiedBrowserEventCapture
 */

import browser from "webextension-polyfill";

import type {
  UnifiedEventCapture,
  EventFilter,
  BrowserEvent,
  EventHandler,
  WebRequestEvent,
} from "./EventCapture.interface";

/**
 * Network request data captured from content scripts
 */
interface NetworkRequestData {
  requestId: string;
  url: string;
  method: string;
  timestamp: number;
  type: "fetch" | "xhr";
  headers?: Record<string, string>;
  body?: string;
}

/**
 * Network response data captured from content scripts
 */
interface NetworkResponseData {
  requestId: string;
  url: string;
  method: string;
  status: number;
  statusText: string;
  timestamp: number;
  duration: number;
  type: "fetch" | "xhr";
  headers?: Record<string, string>;
  responseSize?: number;
}

/**
 * Network error data captured from content scripts
 */
interface NetworkErrorData {
  requestId: string;
  url: string;
  method: string;
  timestamp: number;
  duration: number;
  type: "fetch" | "xhr";
  error: string;
}

type ContentScriptNetworkEvent =
  | ({ eventType: "network-request" } & NetworkRequestData)
  | ({ eventType: "network-response" } & NetworkResponseData)
  | ({ eventType: "network-error" } & NetworkErrorData);

/**
 * Unified Browser Event Capture implementation
 *
 * Captures all types of browser events using browser extension APIs.
 * Must be run in background script context for full functionality.
 *
 * @example
 * ```typescript
 * const capture = new UnifiedBrowserEventCapture();
 * capture.onEvent((event) => {
 *   console.log('Browser event:', event);
 * });
 * capture.captureAll();
 * ```
 */
export class UnifiedBrowserEventCapture implements UnifiedEventCapture {
  private eventHandlers = new Set<EventHandler<BrowserEvent>>();
  private eventCounts: Record<BrowserEvent["type"], number> = {
    tab: 0,
    navigation: 0,
    network: 0,
    webrequest: 0,
    history: 0,
    flow: 0,
  };
  private networkFallbackActive = false;
  private networkFilter?: EventFilter;

  // Capture status flags
  private captureStatus = {
    tabs: false,
    navigation: false,
    network: false,
    webrequest: false,
    history: false,
    flow: false,
  };

  // Browser API listeners cleanup functions
  private tabListeners: (() => void)[] = [];
  private navigationListeners: (() => void)[] = [];
  private networkListeners: (() => void)[] = [];
  private webRequestListeners: (() => void)[] = [];
  private historyListeners: (() => void)[] = [];

  private getWebRequestApi():
    | {
        onBeforeRequest?: {
          addListener: (...args: unknown[]) => void;
          removeListener: (...args: unknown[]) => void;
        };
        onCompleted?: {
          addListener: (...args: unknown[]) => void;
          removeListener: (...args: unknown[]) => void;
        };
        onBeforeSendHeaders?: {
          addListener: (...args: unknown[]) => void;
          removeListener: (...args: unknown[]) => void;
        };
      }
    | undefined {
    const browserApi = (browser as unknown as { webRequest?: unknown })?.webRequest;
    if (browserApi) {
      return browserApi;
    }
    const globalChrome = (globalThis as any).chrome;
    if (typeof globalChrome !== "undefined" && globalChrome.webRequest) {
      return globalChrome.webRequest;
    }
    return undefined;
  }

  /**
   * Start capturing tab events
   */
  captureTabEvents(filter?: EventFilter): void {
    if (this.captureStatus.tabs) {
      return;
    }

    this.captureStatus.tabs = true;

    // Tab created
    const onCreated = (tab: browser.Tabs.Tab) => {
      const browserTab = tab as {
        id?: number;
        url?: string;
        title?: string;
        windowId?: number;
        active?: boolean;
      };

      if (this.shouldIncludeEvent(browserTab.url, filter)) {
        this.emitEvent({
          type: "tab",
          action: "created",
          tabId: browserTab.id!,
          url: browserTab.url,
          title: browserTab.title,
          windowId: browserTab.windowId,
          active: browserTab.active,
          timestamp: Date.now(),
        });
      }
    };
    browser.tabs.onCreated.addListener(onCreated);
    this.tabListeners.push(() =>
      browser.tabs.onCreated.removeListener(onCreated)
    );

    // Tab updated
    const onUpdated = (
      tabId: number,
      _changeInfo: browser.Tabs.OnUpdatedChangeInfoType,
      tab: browser.Tabs.Tab
    ) => {
      const browserTab = tab as {
        url?: string;
        title?: string;
        windowId?: number;
        active?: boolean;
      };

      if (this.shouldIncludeEvent(browserTab.url, filter)) {
        this.emitEvent({
          type: "tab",
          action: "updated",
          tabId,
          url: browserTab.url,
          title: browserTab.title,
          windowId: browserTab.windowId,
          active: browserTab.active,
          timestamp: Date.now(),
        });
      }
    };
    browser.tabs.onUpdated.addListener(onUpdated);
    this.tabListeners.push(() =>
      browser.tabs.onUpdated.removeListener(onUpdated)
    );

    // Tab removed
    const onRemoved = (tabId: number) => {
      this.emitEvent({
        type: "tab",
        action: "removed",
        tabId,
        timestamp: Date.now(),
      });
    };
    browser.tabs.onRemoved.addListener(onRemoved);
    this.tabListeners.push(() =>
      browser.tabs.onRemoved.removeListener(onRemoved)
    );

    // Tab activated
    const onActivated = (
      activeInfo: browser.Tabs.OnActivatedActiveInfoType
    ) => {
      const info = activeInfo as { tabId: number; windowId: number };
      this.emitEvent({
        type: "tab",
        action: "activated",
        tabId: info.tabId,
        windowId: info.windowId,
        timestamp: Date.now(),
      });
    };
    browser.tabs.onActivated.addListener(onActivated);
    this.tabListeners.push(() =>
      browser.tabs.onActivated.removeListener(onActivated)
    );
  }

  /**
   * Start capturing navigation events
   */
  captureNavigationEvents(filter?: EventFilter): void {
    if (this.captureStatus.navigation) {
      return;
    }

    this.captureStatus.navigation = true;

    // Navigation committed
    const onCommitted = (
      details: browser.WebNavigation.OnCommittedDetailsType
    ) => {
      const navDetails = details as {
        url: string;
        tabId: number;
        frameId: number;
        transitionType?: string;
      };

      if (this.shouldIncludeEvent(navDetails.url, filter)) {
        this.emitEvent({
          type: "navigation",
          action: "committed",
          tabId: navDetails.tabId,
          url: navDetails.url,
          frameId: navDetails.frameId,
          transitionType: navDetails.transitionType,
          timestamp: Date.now(),
        });
      }
    };
    browser.webNavigation.onCommitted.addListener(onCommitted);
    this.navigationListeners.push(() =>
      browser.webNavigation.onCommitted.removeListener(onCommitted)
    );

    // Navigation completed
    const onCompleted = (
      details: browser.WebNavigation.OnCompletedDetailsType
    ) => {
      const navDetails = details as {
        url: string;
        tabId: number;
        frameId: number;
      };

      if (this.shouldIncludeEvent(navDetails.url, filter)) {
        this.emitEvent({
          type: "navigation",
          action: "completed",
          tabId: navDetails.tabId,
          url: navDetails.url,
          frameId: navDetails.frameId,
          timestamp: Date.now(),
        });
      }
    };
    browser.webNavigation.onCompleted.addListener(onCompleted);
    this.navigationListeners.push(() =>
      browser.webNavigation.onCompleted.removeListener(onCompleted)
    );
  }

  /**
   * Start capturing network events
   */
  captureNetworkEvents(filter?: EventFilter): void {
    if (this.captureStatus.network) {
      return;
    }

    this.networkFilter = filter;

    const webRequestApi = this.getWebRequestApi();
    if (!webRequestApi) {
      console.warn(
        "[UnifiedBrowserEventCapture] webRequest API not available. Falling back to content-script interception."
      );
      this.captureStatus.network = true;
      this.networkFallbackActive = true;
      return;
    }

    const beforeRequest = webRequestApi.onBeforeRequest;
    if (!beforeRequest || typeof beforeRequest.addListener !== "function") {
      console.warn(
        "[UnifiedBrowserEventCapture] webRequest API not available. Falling back to content-script interception."
      );
      this.captureStatus.network = true;
      this.networkFallbackActive = true;
      return;
    }

    this.captureStatus.network = true;
    this.networkFallbackActive = false;

    const urlFilter = filter?.urlPatterns || ["<all_urls>"];

    // Network request started
    const onBeforeRequest = (
      details: browser.WebRequest.OnBeforeRequestDetailsType
    ) => {
      const req = details as {
        url: string;
        requestId: string;
        method: string;
        tabId: number;
      };

      if (this.shouldIncludeEvent(req.url, filter)) {
        this.emitEvent({
          type: "network",
          action: "request",
          requestId: req.requestId,
          url: req.url,
          method: req.method as "GET" | "POST" | "PUT" | "DELETE" | "PATCH",
          tabId: req.tabId,
          timestamp: Date.now(),
        });
      }
    };
    beforeRequest.addListener(onBeforeRequest, { urls: urlFilter });
    this.networkListeners.push(() =>
      beforeRequest.removeListener(onBeforeRequest)
    );

    // Network request completed
    const onCompleted = (
      details: browser.WebRequest.OnCompletedDetailsType
    ) => {
      const req = details as {
        url: string;
        requestId: string;
        method: string;
        tabId: number;
        statusCode?: number;
      };

      if (this.shouldIncludeEvent(req.url, filter)) {
        this.emitEvent({
          type: "network",
          action: "response",
          requestId: req.requestId,
          url: req.url,
          method: req.method as "GET" | "POST" | "PUT" | "DELETE" | "PATCH",
          tabId: req.tabId,
          statusCode: req.statusCode,
          timestamp: Date.now(),
        });
      }
    };
    const completed = webRequestApi.onCompleted;
    if (completed && typeof completed.addListener === "function") {
      completed.addListener(onCompleted, { urls: urlFilter });
      this.networkListeners.push(() => completed.removeListener(onCompleted));
    }
  }

  /**
   * Start capturing web request events
   */
  captureWebRequestEvents(filter?: EventFilter): void {
    if (this.captureStatus.webrequest) {
      return;
    }

    const webRequestApi = this.getWebRequestApi();
    if (!webRequestApi) {
      console.warn(
        "[UnifiedBrowserEventCapture] webRequest API not available. WebRequest events will not be captured."
      );
      return;
    }

    const beforeSendHeaders = webRequestApi.onBeforeSendHeaders;
    if (
      !beforeSendHeaders ||
      typeof beforeSendHeaders.addListener !== "function"
    ) {
      console.warn(
        "[UnifiedBrowserEventCapture] webRequest API not available. WebRequest events will not be captured."
      );
      return;
    }

    this.captureStatus.webrequest = true;

    const urlFilter = filter?.urlPatterns || ["<all_urls>"];

    // Before sending headers
    const onBeforeSendHeaders = (
      details: browser.WebRequest.OnBeforeSendHeadersDetailsType
    ) => {
      const req = details as {
        url: string;
        requestId: string;
        method: string;
        tabId: number;
        type: string;
      };

      if (this.shouldIncludeEvent(req.url, filter)) {
        const event: WebRequestEvent = {
          type: "webrequest",
          action: "sent",
          requestId: req.requestId,
          url: req.url,
          method: req.method as "GET" | "POST" | "PUT" | "DELETE" | "PATCH",
          tabId: req.tabId,
          resourceType: req.type as
            | "main_frame"
            | "sub_frame"
            | "script"
            | "stylesheet"
            | "image"
            | "font"
            | "xhr"
            | "fetch"
            | "other",
          timestamp: Date.now(),
        };
        this.emitEvent(event);
      }
    };
    beforeSendHeaders.addListener(onBeforeSendHeaders, { urls: urlFilter });
    this.webRequestListeners.push(() =>
      beforeSendHeaders.removeListener(onBeforeSendHeaders)
    );
  }

  /**
   * Start capturing history events
   */
  captureHistoryEvents(_filter?: EventFilter): void {
    if (this.captureStatus.history) {
      return;
    }

    const historyApi = (browser as typeof browser | undefined)?.history;
    if (!historyApi || typeof historyApi.onVisited === "undefined") {
      console.warn(
        "[UnifiedBrowserEventCapture] History API not available. Skipping history capture."
      );
      return;
    }

    this.captureStatus.history = true;

    // History visited
    const onVisited = (result: browser.History.HistoryItem) => {
      const historyItem = result as {
        url?: string;
        title?: string;
        lastVisitTime?: number;
      };

      if (!historyItem.url) {
        return;
      }

      this.emitEvent({
        type: "history",
        action: "visited",
        url: historyItem.url,
        title: historyItem.title,
        visitTime: historyItem.lastVisitTime || Date.now(),
        timestamp: Date.now(),
      });
    };
    historyApi.onVisited.addListener(onVisited);
    this.historyListeners.push(() =>
      historyApi.onVisited.removeListener(onVisited)
    );
  }

  /**
   * Start capturing all events
   */
  captureAll(filter?: EventFilter): void {
    this.captureTabEvents(filter);
    this.captureNavigationEvents(filter);
    this.captureNetworkEvents(filter);
    this.captureWebRequestEvents(filter);
    this.captureHistoryEvents(filter);
  }

  isNetworkFallbackActive(): boolean {
    return this.networkFallbackActive;
  }

  handleContentScriptNetworkEvent(
    event: ContentScriptNetworkEvent,
    tabId?: number
  ): void {
    if (!event) {
      return;
    }

    if (!this.captureStatus.network) {
      this.captureStatus.network = true;
    }

    if (!this.networkFallbackActive) {
      return;
    }

    const filter = this.networkFilter;
    if (filter && !this.shouldIncludeEvent(event.url, filter)) {
      return;
    }

    const effectiveTabId = tabId;

    const toNetworkEvent = (
      action: "request" | "response" | "error"
    ): BrowserEvent => {
      if (action === "request") {
        const payload = event as NetworkRequestData & {
          eventType: "network-request";
        };
        return {
          type: "network",
          action,
          requestId: payload.requestId,
          url: payload.url,
          method: payload.method,
          tabId: effectiveTabId,
          timestamp: payload.timestamp,
        };
      }
      if (action === "response") {
        const payload = event as NetworkResponseData & {
          eventType: "network-response";
        };
        const headers = payload.headers ?? {};
        const contentType =
          headers["content-type"] ??
          headers["Content-Type"] ??
          headers["CONTENT-TYPE"];
        return {
          type: "network",
          action,
          requestId: payload.requestId,
          url: payload.url,
          method: payload.method,
          tabId: effectiveTabId,
          statusCode: payload.status,
          contentType,
          timestamp: payload.timestamp,
          duration: payload.duration,
        };
      }
      const payload = event as NetworkErrorData & {
        eventType: "network-error";
      };
      return {
        type: "network",
        action,
        requestId: payload.requestId,
        url: payload.url,
        method: payload.method,
        tabId: effectiveTabId,
        timestamp: payload.timestamp,
        duration: payload.duration,
        error: payload.error,
      };
    };

    switch (event.eventType) {
      case "network-request":
        this.emitEvent(toNetworkEvent("request"));
        break;
      case "network-response":
        this.emitEvent(toNetworkEvent("response"));
        break;
      case "network-error":
        this.emitEvent(toNetworkEvent("error"));
        break;
      default:
        break;
    }
  }

  /**
   * Register event handler
   */
  onEvent(handler: EventHandler<BrowserEvent>): void {
    this.eventHandlers.add(handler);
  }

  /**
   * Remove event handler
   */
  offEvent(handler: EventHandler<BrowserEvent>): void {
    this.eventHandlers.delete(handler);
  }

  /**
   * Stop all event capture
   */
  stop(): void {
    // Clear all listeners
    this.tabListeners.forEach((cleanup) => cleanup());
    this.navigationListeners.forEach((cleanup) => cleanup());
    this.networkListeners.forEach((cleanup) => cleanup());
    this.webRequestListeners.forEach((cleanup) => cleanup());
    this.historyListeners.forEach((cleanup) => cleanup());

    // Reset state
    this.tabListeners = [];
    this.navigationListeners = [];
    this.networkListeners = [];
    this.webRequestListeners = [];
    this.historyListeners = [];

    this.captureStatus = {
      tabs: false,
      navigation: false,
      network: false,
      webrequest: false,
      history: false,
      flow: false,
    };
    this.networkFallbackActive = false;
    this.networkFilter = undefined;
  }

  /**
   * Get capture status
   */
  getStatus(): {
    tabs: boolean;
    navigation: boolean;
    network: boolean;
    webrequest: boolean;
    history: boolean;
    flow: boolean;
  } {
    return { ...this.captureStatus };
  }

  /**
   * Get event counts
   */
  getEventCounts(): Record<BrowserEvent["type"], number> {
    return { ...this.eventCounts };
  }

  /**
   * Clear event counts
   */
  clearEventCounts(): void {
    this.eventCounts = {
      tab: 0,
      navigation: 0,
      network: 0,
      webrequest: 0,
      history: 0,
      flow: 0,
    };
  }

  /**
   * Emit event to all handlers
   */
  private emitEvent(event: BrowserEvent): void {
    // Increment count
    this.eventCounts[event.type]++;

    // Notify handlers
    this.eventHandlers.forEach((handler) => handler(event));
  }

  /**
   * Check if event should be included based on filter
   */
  private shouldIncludeEvent(
    url: string | undefined,
    filter?: EventFilter
  ): boolean {
    if (!filter || !url) {
      return true;
    }

    // Check URL patterns
    if (filter.urlPatterns && !this.matchesPatterns(url, filter.urlPatterns)) {
      return false;
    }

    return true;
  }

  /**
   * Match URL against patterns
   */
  private matchesPatterns(url: string, patterns: string[]): boolean {
    return patterns.some((pattern) => {
      if (pattern === "<all_urls>") {
        return true;
      }
      const regex = new RegExp(pattern.replace(/\*/g, ".*"));
      return regex.test(url);
    });
  }
}
