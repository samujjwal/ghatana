/**
 * Console Sink Adapter
 *
 * Outputs events to console for debugging and development.
 * Supports pretty-printing and filtering options.
 */

import { BaseConnector, type IConnector } from '@ghatana/dcmaar-connectors';
import type { SinkAdapter } from '../types';
import { Logger } from '../../utils/logger';

/**
 * Sink adapter that outputs events to console
 *
 * Useful for development, debugging, and testing.
 * Can format output as JSON or pretty-printed.
 */
export class ConsoleSinkAdapter implements SinkAdapter {
  readonly type = 'console';
  private logger: Logger;

  constructor() {
    this.logger = new Logger('ConsoleSinkAdapter');
  }

  async create(config: unknown): Promise<IConnector> {
    this.logger.debug('Creating ConsoleSink connector', { id: config.id });
    return new ConsoleSinkConnector(config);
  }
}

/**
 * Console Sink Connector
 *
 * Writes events to console output with optional formatting.
 */
class ConsoleSinkConnector extends BaseConnector<unknown> {
  private logger: Logger;
  private pretty: boolean;
  private filter: string | null;

  constructor(config: unknown) {
    super(config);
    this.logger = new Logger('ConsoleSinkConnector');

    // Get configuration from metadata
    const metadata = config.metadata || {};
    this.pretty = metadata.pretty !== false; // Pretty by default
    this.filter = metadata.filter || null; // Optional event type filter
  }

  protected async _connect(): Promise<void> {
    this.logger.info('ConsoleSink connected', {
      id: this._config.id,
      pretty: this.pretty,
      filter: this.filter || 'none',
    });
  }

  protected async _disconnect(): Promise<void> {
    this.logger.info('ConsoleSink disconnected', { id: this._config.id });
  }

  async send(data: unknown, _options?: Record<string, any>): Promise<void> {
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
    } else {
      // JSON output
      console.log(JSON.stringify(data));
    }
  }
}
