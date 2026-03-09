/**
 * Ingest Sink Adapter
 *
 * Routes events from TypeScript connectors to Rust IngestService.
 * This is the primary bridge between the TypeScript connector layer
 * and the native Rust processing pipeline.
 */
import { type IConnector } from '@ghatana/dcmaar-connectors';
import type { SinkAdapter } from '../types';
/**
 * Sink adapter that routes events to Rust IngestService
 *
 * This adapter creates connectors that send events through the
 * Rust bridge to the IngestService for processing.
 */
export declare class IngestSinkAdapter implements SinkAdapter {
    readonly type = "ingest";
    private logger;
    constructor();
    create(config: unknown): Promise<IConnector>;
}
//# sourceMappingURL=IngestSinkAdapter.d.ts.map