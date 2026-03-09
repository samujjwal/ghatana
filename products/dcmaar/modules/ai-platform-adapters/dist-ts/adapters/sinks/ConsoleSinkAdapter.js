/**
 * Console Sink Adapter
 *
 * Outputs events to console for debugging and development.
 * Supports pretty-printing and filtering options.
 */
import { BaseConnector } from '@ghatana/dcmaar-connectors';
import { Logger } from '../../utils/logger';
/**
 * Sink adapter that outputs events to console
 *
 * Useful for development, debugging, and testing.
 * Can format output as JSON or pretty-printed.
 */
export class ConsoleSinkAdapter {
    type = 'console';
    logger;
    constructor() {
        this.logger = new Logger('ConsoleSinkAdapter');
    }
    async create(config) {
        this.logger.debug('Creating ConsoleSink connector', { id: config.id });
        return new ConsoleSinkConnector(config);
    }
}
/**
 * Console Sink Connector
 *
 * Writes events to console output with optional formatting.
 */
class ConsoleSinkConnector extends BaseConnector {
    logger;
    pretty;
    filter;
    constructor(config) {
        super(config);
        this.logger = new Logger('ConsoleSinkConnector');
        // Get configuration from metadata
        const metadata = config.metadata || {};
        this.pretty = metadata.pretty !== false; // Pretty by default
        this.filter = metadata.filter || null; // Optional event type filter
    }
    async _connect() {
        this.logger.info('ConsoleSink connected', {
            id: this._config.id,
            pretty: this.pretty,
            filter: this.filter || 'none',
        });
    }
    async _disconnect() {
        this.logger.info('ConsoleSink disconnected', { id: this._config.id });
    }
    async send(data, _options) {
        // Apply filter if configured
        if (this.filter && data.type !== this.filter) {
            return;
        }
        // Format output
        if (this.pretty) {
            console.log('\n┌─────────────────────────────────────────────────');
            console.log('│ Event:', data.type);
            console.log('│ ID:', data.id);
            console.log('│ Timestamp:', new Date(data.timestamp).toISOString());
            console.log('├─────────────────────────────────────────────────');
            console.log('│ Payload:');
            console.log(JSON.stringify(data.payload, null, 2).split('\n').map(line => '│   ' + line).join('\n'));
            if (data.metadata) {
                console.log('├─────────────────────────────────────────────────');
                console.log('│ Metadata:');
                console.log(JSON.stringify(data.metadata, null, 2).split('\n').map(line => '│   ' + line).join('\n'));
            }
            console.log('└─────────────────────────────────────────────────\n');
        }
        else {
            // JSON output
            console.log(JSON.stringify(data));
        }
    }
}
//# sourceMappingURL=ConsoleSinkAdapter.js.map