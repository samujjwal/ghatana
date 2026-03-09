/**
 * @ghatana/dcmaar-browser-extension-core
 *
 * Core framework for building browser extensions with Source-Processor-Sink architecture.
 * Extracted from the DCMAAR extension with proven production patterns.
 *
 * @module @ghatana/dcmaar-browser-extension-core
 */

// Pipeline Architecture - split type-only and value exports to satisfy `isolatedModules`
export type { EventSource } from "./pipeline";
export { BaseEventSource } from "./pipeline";

export type { EventProcessor, ProcessorStats } from "./pipeline";
export { BaseEventProcessor } from "./pipeline";

export type { EventSink, SinkStats } from "./pipeline";
export { BaseEventSink } from "./pipeline";

export { EventPipeline } from "./pipeline";
export type { PipelineConfig, PipelineStats } from "./pipeline";

// Storage Adapter
export {
  type StorageAdapter,
  type PrefixedStorageAdapter,
  type StorageChange,
  type StorageChangeListener,
  type StorageOptions,
  type StorageQuota,
} from "./adapters/StorageAdapter.interface";

export {
  BrowserStorageAdapter,
  PrefixedBrowserStorageAdapter,
} from "./adapters/BrowserStorageAdapter";

// Message Router
export {
  type MessageRouter,
  type ExtendedMessageRouter,
  type ExtensionMessage,
  type MessageSender,
  type MessageResponse,
  type MessageHandler,
  type PortConnection,
} from "./adapters/MessageRouter.interface";

export {
  BrowserMessageRouter,
  ExtendedBrowserMessageRouter,
} from "./adapters/BrowserMessageRouter";

// Metrics Collector
export {
  type MetricCollector,
  type BatchMetricCollector,
  type PageMetrics,
  type NavigationMetrics,
  type ResourceMetrics,
  type InteractionMetrics,
  type TabMetrics,
  type WebVitalRating,
} from "./metrics/MetricCollector.interface";

export {
  PageMetricsCollector,
  BatchPageMetricsCollector,
} from "./metrics/PageMetricsCollector";

// Event Capture
export {
  type BrowserEvent,
  type TabEvent,
  type NavigationEvent,
  type NetworkEvent,
  type WebRequestEvent,
  type HistoryEvent,
  type FlowEvent,
  type EventHandler,
  type EventFilter,
  type EventCapture,
  type UnifiedEventCapture,
} from "./events/EventCapture.interface";

export { UnifiedBrowserEventCapture } from "./events/UnifiedBrowserEventCapture";

// Controller Base Class
export {
  BaseExtensionController,
  type ControllerState,
  type ControllerConfig,
  type ControllerLifecycleHooks,
} from "./controller/BaseExtensionController";

// Plugin Host & Connector Bridge for extensions
export {
  ExtensionPluginHost,
  type ExtensionPluginHostOptions,
  type ExtensionPluginFactory,
  type ExtensionPluginFactoryContext,
} from "./plugins/ExtensionPluginHost";

export {
  ExtensionConnectorBridge,
  type ExtensionConnectorBridgeOptions,
} from "./plugins/ExtensionConnectorBridge";

// Re-export webextension-polyfill for convenience
export { default as browser } from "webextension-polyfill";
