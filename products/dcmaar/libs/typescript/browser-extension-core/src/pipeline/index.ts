/**
 * @fileoverview Pipeline Module Exports
 *
 * @module pipeline
 */

// Split type-only and value exports to satisfy `isolatedModules` requirements
export type { EventSource } from "./EventSource";
export { BaseEventSource } from "./EventSource";

export type { EventProcessor, ProcessorStats } from "./EventProcessor";
export { BaseEventProcessor } from "./EventProcessor";

export type { EventSink, SinkStats } from "./EventSink";
export { BaseEventSink } from "./EventSink";

export { EventPipeline } from "./EventPipeline";
export type { PipelineConfig, PipelineStats } from "./EventPipeline";
