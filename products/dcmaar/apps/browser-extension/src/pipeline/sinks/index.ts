/**
 * @fileoverview Pipeline Sinks
 * 
 * Event sinks for the Guardian pipeline.
 * 
 * @module pipeline/sinks
 */

export { LocalStorageSink } from './LocalStorageSink';
export type { LocalStorageSinkConfig } from './LocalStorageSink';

export { RealTimeSyncSink } from './RealTimeSyncSink';
export type {
    RealTimeSyncSinkConfig,
    SyncMessage,
    SyncMessageType,
    ConnectionState,
    DownstreamHandler,
} from './RealTimeSyncSink';

export { CommandExecutionSink } from './CommandExecutionSink';
export type {
    CommandExecutionSinkConfig,
    CommandResult,
    CommandExecutionEvent,
} from './CommandExecutionSink';

export { TelemetrySink } from './TelemetrySink';
export type {
    TelemetrySinkConfig,
    GuardianEventPayload,
} from './TelemetrySink';
