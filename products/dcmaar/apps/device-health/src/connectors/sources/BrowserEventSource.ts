/**
 * @fileoverview Browser Event Source - OOB (Out-of-Box) Implementation
 *
 * Captures browser events without external dependencies, enabling the extension
 * to work standalone. This is the primary source for monitoring browser activity,
 * performance metrics, and user interactions.
 *
 * **Features**:
 * - Tab lifecycle events (created, updated, removed, activated)
 * - Navigation events (completed, committed, error)
 * - Performance metrics (page load, resource timing, web vitals)
 * - User interactions (clicks, form submissions, scrolls)
 * - Window events (focus, blur, resize)
 * - Configurable sampling and filtering
 * - No external dependencies
 *
 * **Usage**:
 * ```typescript
 * const source = new BrowserEventSource({
 *   id: 'browser-events-main',
 *   events: ['tabs', 'navigation', 'performance'],
 *   sampling: { rate: 1.0 },
 *   filters: {
 *     includePatterns: ['https://*'],
 *     excludePatterns: ['chrome://*']
 *   }
 * });
 *
 * source.onData((event) => {
 *   console.log('Browser event:', event);
 * });
 *
 * await source.start();
 * ```
 *
 * @module connectors/sources/BrowserEventSource
 */

import browser from 'webextension-polyfill';
import type { Event } from '@ghatana/dcmaar-connectors';
import { throttle, type ThrottledFunction } from '../../utils/throttle';

/**
 * Browser event source configuration
 */
export interface BrowserEventSourceConfig {
  /** Unique source identifier */
  id: string;

  /** Event types to capture */
  events: Array<'tabs' | 'navigation' | 'performance' | 'interactions' | 'windows'>;

  /** Sampling configuration */
  sampling?: {
    /** Sampling rate (0.0 to 1.0) */
    rate: number;
    /** Sampling strategy */
    strategy?: 'uniform' | 'adaptive';
  };

  /** URL filtering */
  filters?: {
    /** Patterns to include (glob format) */
    includePatterns?: string[];
    /** Patterns to exclude (glob format) */
    excludePatterns?: string[];
  };

  /** Batching configuration */
  batching?: {
    /** Batch size */
    size: number;
    /** Flush interval in milliseconds */
    flushIntervalMs: number;
  };

  /** Performance monitoring config */
  performance?: {
    /** Capture web vitals (LCP, FID, CLS) */
    captureWebVitals: boolean;
    /** Capture resource timing */
    captureResourceTiming: boolean;
    /** Capture navigation timing */
    captureNavigationTiming: boolean;
  };

  /** Throttling configuration for high-frequency events */
  throttling?: {
    /** Tab update events throttle interval (ms) */
    tabUpdates?: number;
    /** Navigation events throttle interval (ms) */
    navigation?: number;
    /** Interaction events throttle interval (ms) */
    interactions?: number;
  };
}

/**
 * Source state
 */
export type SourceState = 'idle' | 'starting' | 'running' | 'stopping' | 'error';

/**
 * Browser Event Source
 *
 * Captures browser events and emits them as standardized Event objects.
 * This is an OOB (out-of-box) source that requires no external dependencies.
 */
export class BrowserEventSource {
  readonly id: string;
  readonly type = 'browser-events';
  state: SourceState = 'idle';

  private config: BrowserEventSourceConfig;
  private dataHandlers: Array<(data: Event) => void> = [];
  private errorHandlers: Array<(error: Error) => void> = [];
  private eventBuffer: Event[] = [];
  private flushTimer: NodeJS.Timeout | null = null;
  private sampleCounter = 0;

  // ✅ Memory management constants
  private readonly MAX_BUFFER_SIZE = 1000; // Max events in buffer
  private readonly BUFFER_CLEANUP_THRESHOLD = 0.8; // Cleanup when 80% full
  private readonly BUFFER_CLEANUP_PERCENTAGE = 0.2; // Remove oldest 20%

  // Browser API listeners (stored for cleanup)
  private listeners = new Map<string, Function>();

  // Throttled event emitters
  private throttledEmitters = new Map<string, ThrottledFunction<any>>();

  constructor(config: BrowserEventSourceConfig) {
    this.id = config.id;
    this.config = {
      ...config,
      sampling: config.sampling || { rate: 1.0, strategy: 'uniform' },
      filters: config.filters || { includePatterns: ['*'], excludePatterns: [] },
      batching: config.batching || { size: 50, flushIntervalMs: 5000 },
      performance: config.performance || {
        captureWebVitals: true,
        captureResourceTiming: false,
        captureNavigationTiming: true,
      },
      throttling: config.throttling || {
        tabUpdates: 1000, // 1 second
        navigation: 500,   // 500ms
        interactions: 100, // 100ms for interactions
      },
    };
  }

  /**
   * Start capturing browser events
   */
  async start(): Promise<void> {
    if (this.state === 'running') {
      return;
    }

    this.state = 'starting';

    try {
      // Register event listeners based on config
      if (this.config.events.includes('tabs')) {
        this.registerTabListeners();
      }

      if (this.config.events.includes('navigation')) {
        this.registerNavigationListeners();
      }

      if (this.config.events.includes('performance')) {
        this.registerPerformanceListeners();
      }

      if (this.config.events.includes('interactions')) {
        this.registerInteractionListeners();
      }

      if (this.config.events.includes('windows')) {
        this.registerWindowListeners();
      }

      // Start auto-flush timer
      this.startAutoFlush();

      this.state = 'running';
    } catch (error) {
      this.state = 'error';
      this.emitError(error instanceof Error ? error : new Error(String(error)));
      throw error;
    }
  }

  /**
   * Stop capturing browser events
   */
  async stop(): Promise<void> {
    if (this.state === 'idle') {
      return;
    }

    this.state = 'stopping';

    try {
      // Remove all listeners
      this.removeAllListeners();

      // Cancel all throttled emitters to flush pending calls
      for (const [key, throttled] of this.throttledEmitters.entries()) {
        try {
          throttled.flush?.(); // Flush any pending throttled calls
          throttled.cancel?.(); // Cancel any scheduled calls
        } catch (error) {
          console.warn(`[BrowserEventSource] Error cleaning up throttled emitter ${key}:`, error);
        }
      }
      this.throttledEmitters.clear();

      // Stop auto-flush
      if (this.flushTimer) {
        clearInterval(this.flushTimer);
        this.flushTimer = null;
      }

      // Flush remaining events
      await this.flush();

      this.state = 'idle';
    } catch (error) {
      this.state = 'error';
      this.emitError(error instanceof Error ? error : new Error(String(error)));
      throw error;
    }
  }

  /**
   * Register data handler
   */
  onData(handler: (data: Event) => void): void {
    this.dataHandlers.push(handler);
  }

  /**
   * Register error handler
   */
  onError(handler: (error: Error) => void): void {
    this.errorHandlers.push(handler);
  }

  /**
   * Register tab event listeners
   */
  private registerTabListeners(): void {
    const onCreated = (tab: browser.Tabs.Tab) => {
      this.emitEvent({
        type: 'tab.created',
        payload: {
          tabId: tab.id,
          url: tab.url,
          title: tab.title,
          active: tab.active,
          windowId: tab.windowId,
        },
      });
    };

    // Throttle tab updates to prevent CPU spikes during rapid tab changes
    const throttleInterval = this.config.throttling?.tabUpdates ?? 1000;
    const onUpdatedThrottled = throttle(
      (tabId: number, changeInfo: browser.Tabs.OnUpdatedChangeInfoType, tab: browser.Tabs.Tab) => {
        if (changeInfo.status === 'complete') {
          this.emitEvent({
            type: 'tab.updated',
            payload: {
              tabId,
              url: tab.url,
              title: tab.title,
              changeInfo,
            },
          });
        }
      },
      throttleInterval,
      { leading: true, trailing: true }
    );

    const onUpdated = (tabId: number, changeInfo: browser.Tabs.OnUpdatedChangeInfoType, tab: browser.Tabs.Tab) => {
      onUpdatedThrottled(tabId, changeInfo, tab);
    };

    const onRemoved = (tabId: number, removeInfo: browser.Tabs.OnRemovedRemoveInfoType) => {
      this.emitEvent({
        type: 'tab.removed',
        payload: {
          tabId,
          windowId: removeInfo.windowId,
          isWindowClosing: removeInfo.isWindowClosing,
        },
      });
    };

    const onActivated = (activeInfo: browser.Tabs.OnActivatedActiveInfoType) => {
      this.emitEvent({
        type: 'tab.activated',
        payload: {
          tabId: activeInfo.tabId,
          windowId: activeInfo.windowId,
        },
      });
    };

    browser.tabs.onCreated.addListener(onCreated);
    browser.tabs.onUpdated.addListener(onUpdated);
    browser.tabs.onRemoved.addListener(onRemoved);
    browser.tabs.onActivated.addListener(onActivated);

    this.listeners.set('tabs.onCreated', onCreated);
    this.listeners.set('tabs.onUpdated', onUpdated);
    this.listeners.set('tabs.onRemoved', onRemoved);
    this.listeners.set('tabs.onActivated', onActivated);
    this.throttledEmitters.set('tabs.onUpdated', onUpdatedThrottled);
  }

  /**
   * Register navigation event listeners
   */
  private registerNavigationListeners(): void {
    // Throttle navigation completed events to prevent CPU spikes
    const throttleInterval = this.config.throttling?.navigation ?? 500;
    const onCompletedThrottled = throttle(
      (details: browser.WebNavigation.OnCompletedDetailsType) => {
        if (!this.shouldCaptureUrl(details.url)) {
          return;
        }

        this.emitEvent({
          type: 'navigation.completed',
          payload: {
            tabId: details.tabId,
            url: details.url,
            frameId: details.frameId,
            timeStamp: details.timeStamp,
          },
        });
      },
      throttleInterval,
      { leading: true, trailing: true }
    );

    const onCompleted = (details: browser.WebNavigation.OnCompletedDetailsType) => {
      onCompletedThrottled(details);
    };

    const onErrorOccurred = (details: browser.WebNavigation.OnErrorOccurredDetailsType) => {
      this.emitEvent({
        type: 'navigation.error',
        payload: {
          tabId: details.tabId,
          url: details.url,
          frameId: details.frameId,
          timeStamp: details.timeStamp,
        },
      });
    };

    browser.webNavigation.onCompleted.addListener(onCompleted);
    browser.webNavigation.onErrorOccurred.addListener(onErrorOccurred);

    this.listeners.set('webNavigation.onCompleted', onCompleted);
    this.listeners.set('webNavigation.onErrorOccurred', onErrorOccurred);
    this.throttledEmitters.set('webNavigation.onCompleted', onCompletedThrottled);
  }

  /**
   * Register performance monitoring listeners
   */
  private registerPerformanceListeners(): void {
    // Listen for navigation completed to capture performance metrics
    const onCompleted = async (details: browser.WebNavigation.OnCompletedDetailsType) => {
      if (!this.shouldCaptureUrl(details.url) || details.frameId !== 0) {
        return;
      }

      // Wait a bit for performance metrics to be available
      setTimeout(async () => {
        try {
          // Execute script in page to get performance metrics
          const results = await browser.tabs.executeScript(details.tabId, {
            code: `
              (function() {
                const perf = window.performance;
                const nav = perf.getEntriesByType('navigation')[0];
                const paint = perf.getEntriesByType('paint');
                
                return {
                  navigation: nav ? {
                    domContentLoaded: nav.domContentLoadedEventEnd - nav.domContentLoadedEventStart,
                    loadComplete: nav.loadEventEnd - nav.loadEventStart,
                    domInteractive: nav.domInteractive,
                    domComplete: nav.domComplete,
                  } : null,
                  paint: paint.map(p => ({ name: p.name, startTime: p.startTime })),
                  memory: performance.memory ? {
                    usedJSHeapSize: performance.memory.usedJSHeapSize,
                    totalJSHeapSize: performance.memory.totalJSHeapSize,
                  } : null
                };
              })();
            `,
          });

          if (results && results[0]) {
            this.emitEvent({
              type: 'performance.metrics',
              payload: {
                tabId: details.tabId,
                url: details.url,
                metrics: results[0],
                timestamp: Date.now(),
              },
            });
          }
        } catch (error) {
          // Ignore errors (e.g., restricted pages)
        }
      }, 1000);
    };

    browser.webNavigation.onCompleted.addListener(onCompleted);
    this.listeners.set('performance.onCompleted', onCompleted);
  }

  /**
   * Register interaction listeners (via content script messages)
   */
  private registerInteractionListeners(): void {
    // Throttle interaction events (scroll, mousemove, etc.) to prevent CPU spikes
    const throttleInterval = this.config.throttling?.interactions ?? 100;
    const onMessageThrottled = throttle(
      (message: any, sender: browser.Runtime.MessageSender) => {
        if (message.type === 'interaction') {
          this.emitEvent({
            type: `interaction.${message.action}`,
            payload: {
              tabId: sender.tab?.id,
              url: sender.tab?.url,
              action: message.action,
              target: message.target,
              timestamp: message.timestamp,
            },
          });
        }
      },
      throttleInterval,
      { leading: true, trailing: true }
    );

    const onMessage = (message: any, sender: browser.Runtime.MessageSender) => {
      // Only throttle high-frequency interaction events
      const highFrequencyActions = ['scroll', 'mousemove', 'resize', 'pointermove'];
      if (message.type === 'interaction' && highFrequencyActions.includes(message.action)) {
        onMessageThrottled(message, sender);
      } else {
        // Don't throttle low-frequency events like clicks
        if (message.type === 'interaction') {
          this.emitEvent({
            type: `interaction.${message.action}`,
            payload: {
              tabId: sender.tab?.id,
              url: sender.tab?.url,
              action: message.action,
              target: message.target,
              timestamp: message.timestamp,
            },
          });
        }
      }
    };

    // Wrap the original onMessage so we can log and (if needed) attach a
    // source marker to any object responses. We keep the original handler
    // behavior intact.
    const wrappedOnMessage = (message: any, sender: browser.Runtime.MessageSender) => {
      try {
        const type = message && typeof message === 'object' && 'type' in message ? message.type : undefined;
        console.debug('[DCMAAR][BrowserEventSource] runtime.onMessage', { type, sender });
      } catch {}

      try {
        return onMessage(message, sender);
      } catch (e) {
        try {
          console.debug('[DCMAAR][BrowserEventSource] handler threw', String(e));
        } catch {}
        // Do not rethrow — this is a background event emitter
      }
    };

    browser.runtime.onMessage.addListener(wrappedOnMessage);
    this.listeners.set('runtime.onMessage.interaction', wrappedOnMessage as any);
    this.throttledEmitters.set('runtime.onMessage.interaction', onMessageThrottled);
  }

  /**
   * Register window event listeners
   */
  private registerWindowListeners(): void {
    const onFocusChanged = (windowId: number) => {
      this.emitEvent({
        type: 'window.focusChanged',
        payload: {
          windowId,
          focused: windowId !== browser.windows.WINDOW_ID_NONE,
        },
      });
    };

    browser.windows.onFocusChanged.addListener(onFocusChanged);
    this.listeners.set('windows.onFocusChanged', onFocusChanged);
  }

  /**
   * Remove all registered listeners
   */
  private removeAllListeners(): void {
    for (const [key, listener] of this.listeners.entries()) {
      try {
        if (key.startsWith('tabs.')) {
          const event = key.split('.')[1] as keyof typeof browser.tabs;
          (browser.tabs[event] as any).removeListener(listener);
        } else if (key.startsWith('webNavigation.')) {
          const event = key.split('.')[1] as keyof typeof browser.webNavigation;
          (browser.webNavigation[event] as any).removeListener(listener);
        } else if (key.startsWith('windows.')) {
          const event = key.split('.')[1] as keyof typeof browser.windows;
          (browser.windows[event] as any).removeListener(listener);
        } else if (key.startsWith('runtime.')) {
          browser.runtime.onMessage.removeListener(listener as any);
        }
      } catch (error) {
        // Ignore errors during cleanup
      }
    }

    this.listeners.clear();
  }

  /**
   * Check if URL should be captured based on filters
   */
  private shouldCaptureUrl(url: string): boolean {
    const { includePatterns = ['*'], excludePatterns = [] } = this.config.filters || {};

    // Check exclude patterns first
    for (const pattern of excludePatterns) {
      if (this.matchPattern(url, pattern)) {
        return false;
      }
    }

    // Check include patterns
    for (const pattern of includePatterns) {
      if (this.matchPattern(url, pattern)) {
        return this.shouldSample();
      }
    }

    return false;
  }

  /**
   * Simple glob pattern matching
   */
  private matchPattern(url: string, pattern: string): boolean {
    if (pattern === '*') return true;

    const regex = new RegExp(
      '^' + pattern.replace(/\*/g, '.*').replace(/\?/g, '.') + '$'
    );

    return regex.test(url);
  }

  /**
   * Check if event should be sampled
   */
  private shouldSample(): boolean {
    const { rate = 1.0, strategy = 'uniform' } = this.config.sampling || {};

    if (rate >= 1.0) return true;
    if (rate <= 0.0) return false;

    if (strategy === 'uniform') {
      return Math.random() < rate;
    }

    // Adaptive sampling (every Nth event)
    this.sampleCounter++;
    const threshold = Math.floor(1 / rate);
    if (this.sampleCounter >= threshold) {
      this.sampleCounter = 0;
      return true;
    }

    return false;
  }

  /**
   * Emit event to handlers
   */
  private emitEvent(event: { type: string; payload: any }): void {
    // ✅ Check buffer size before adding
    if (this.eventBuffer.length >= this.MAX_BUFFER_SIZE) {
      console.warn(
        `[BrowserEventSource] Buffer at max capacity (${this.MAX_BUFFER_SIZE}), dropping oldest events`
      );
      // Drop oldest events to free up space
      const dropCount = Math.floor(this.MAX_BUFFER_SIZE * this.BUFFER_CLEANUP_PERCENTAGE);
      this.eventBuffer.splice(0, dropCount);

      // Emit error to notify handlers
      this.emitError(new Error(`Buffer overflow: dropped ${dropCount} oldest events`));
    }

    const standardEvent: Event = {
      id: this.generateEventId(),
      type: event.type,
      timestamp: Date.now(),
      payload: event.payload,
      metadata: {
        sourceId: this.id,
        sourceType: this.type,
        version: '1.0.0',
      },
    };

    // Add to buffer
    this.eventBuffer.push(standardEvent);

    // ✅ Check if we should flush early (proactive cleanup)
    const bufferThreshold = Math.floor(this.MAX_BUFFER_SIZE * this.BUFFER_CLEANUP_THRESHOLD);
    if (this.eventBuffer.length >= bufferThreshold) {
      console.warn(
        `[BrowserEventSource] Buffer at ${Math.floor((this.eventBuffer.length / this.MAX_BUFFER_SIZE) * 100)}% capacity, triggering early flush`
      );
      void this.flush();
    }

    // Flush if buffer reaches normal batch size
    else if (this.eventBuffer.length >= (this.config.batching?.size || 50)) {
      void this.flush();
    }
  }

  /**
   * Emit error to handlers
   */
  private emitError(error: Error): void {
    this.errorHandlers.forEach((handler) => {
      try {
        handler(error);
      } catch (e) {
        console.error('[BrowserEventSource] Error in error handler:', e);
      }
    });
  }

  /**
   * Start auto-flush timer
   */
  private startAutoFlush(): void {
    const interval = this.config.batching?.flushIntervalMs || 5000;

    this.flushTimer = setInterval(() => {
      void this.flush();
    }, interval);
  }

  /**
   * Flush buffered events
   */
  private async flush(): Promise<void> {
    if (this.eventBuffer.length === 0) {
      return;
    }

    const events = [...this.eventBuffer];
    this.eventBuffer = [];

    // Emit each event to handlers
    for (const event of events) {
      this.dataHandlers.forEach((handler) => {
        try {
          handler(event);
        } catch (error) {
          console.error('[BrowserEventSource] Error in data handler:', error);
        }
      });
    }
  }

  /**
   * Generate unique event ID
   */
  private generateEventId(): string {
    return `${this.id}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Get source statistics
   */
  getStats() {
    return {
      id: this.id,
      type: this.type,
      state: this.state,
      bufferedEvents: this.eventBuffer.length,
      config: {
        events: this.config.events,
        samplingRate: this.config.sampling?.rate,
        batchSize: this.config.batching?.size,
      },
    };
  }
}
