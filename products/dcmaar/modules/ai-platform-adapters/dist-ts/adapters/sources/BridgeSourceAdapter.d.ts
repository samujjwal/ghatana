/**
 * Bridge Source Adapter
 *
 * Receives events from Desktop and Extension apps via the bridge protocol.
 * Implements the DCMAAR bridge protocol v1 for cross-application telemetry.
 */
import { type IConnector } from '@ghatana/dcmaar-connectors';
import type { SourceAdapter } from '../types';
/**
 * Source adapter for Extension/Desktop bridge protocol
 *
 * Receives telemetry from browser extensions and desktop apps
 * via WebSocket or IPC bridge connections.
 */
export declare class BridgeSourceAdapter implements SourceAdapter {
    readonly type = "bridge";
    private logger;
    constructor();
    create(config: unknown): Promise<IConnector>;
}
//# sourceMappingURL=BridgeSourceAdapter.d.ts.map