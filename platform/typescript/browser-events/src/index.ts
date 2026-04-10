/**
 * @ghatana/browser-events
 *
 * Browser extension event abstractions for Ghatana applications.
 *
 * Provides:
 * - Typed browser event interfaces (TabEvent, NavigationEvent, NetworkEvent, etc.)
 * - `BrowserEventSource` — identification of browser-origin events
 * - `BrowserEventCapture` / `UnifiedBrowserEventCapture` interfaces
 * - `AbstractBrowserEventCapture` — base class for product-layer implementations
 * - Domain filter helpers: `createDomainFilter`, `createUrlPatternFilter`
 * - Type guards: `isBrowserEvent`, `isBrowserEventOfType`
 *
 * All events in this package extend `PlatformEvent<T>` from `@ghatana/events`.
 *
 * @example
 * ```ts
 * import { AbstractBrowserEventCapture } from '@ghatana/browser-events/capture';
 *
 * class MyCapture extends AbstractBrowserEventCapture {
 *   captureTabEvents() {
 *     browser.tabs.onCreated.addListener(async (tab) => {
 *       await this.emit({
 *         id: this.newEventId(),
 *         type: 'tab.created',
 *         timestamp: Date.now(),
 *         source: this.makeSource(),
 *         data: { action: 'created', tabId: tab.id ?? -1, url: tab.url },
 *       });
 *     });
 *     this.activeCaptures.add('tabs');
 *   }
 *   // ...other required overrides
 * }
 * ```
 *
 * @module @ghatana/browser-events
 */

// Types
export type {
  BrowserEventSource,
  TabEventData,
  NavigationEventData,
  NetworkRequestData,
  NetworkResponseData,
  NetworkErrorData,
  NetworkEventData,
  WebRequestEventData,
  HistoryEventData,
  TabEvent,
  NavigationEvent,
  NetworkEvent,
  WebRequestEvent,
  HistoryEvent,
  BrowserEvent,
  BrowserEventFilter,
  BrowserEventHandler,
} from "./types";

export {
  TabEventDataSchema,
  NavigationEventDataSchema,
  HistoryEventDataSchema,
  isBrowserEvent,
  isBrowserEventOfType,
  createDomainFilter,
  createUrlPatternFilter,
} from "./types";

// Capture abstractions
export type {
  CaptureStatus,
  BrowserEventCapture,
  UnifiedBrowserEventCapture,
} from "./capture";

export { AbstractBrowserEventCapture } from "./capture";
