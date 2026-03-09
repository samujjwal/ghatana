/**
 * Console Sink Adapter
 *
 * Outputs events to console for debugging and development.
 * Supports pretty-printing and filtering options.
 */
import { type IConnector } from '@ghatana/dcmaar-connectors';
import type { SinkAdapter } from '../types';
/**
 * Sink adapter that outputs events to console
 *
 * Useful for development, debugging, and testing.
 * Can format output as JSON or pretty-printed.
 */
export declare class ConsoleSinkAdapter implements SinkAdapter {
    readonly type = "console";
    private logger;
    constructor();
    create(config: unknown): Promise<IConnector>;
}
//# sourceMappingURL=ConsoleSinkAdapter.d.ts.map