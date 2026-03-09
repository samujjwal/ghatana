/**
 * MockSink: In-memory mock control sink for testing and offline use.
 * Stores commands in memory with optional persistence to auditStore.
 */

import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  HealthStatus,
} from '../types';

export interface MockSinkOptions {
  persistToAudit?: boolean;
  maxCommands?: number;
}

export class MockSink implements ControlSink {
  readonly kind = 'mock' as const;

  private ctx?: SinkContext;
  private options: MockSinkOptions;
  private commands: ControlCommand[] = [];

  constructor(options: MockSinkOptions = {}) {
    this.options = {
      persistToAudit: true,
      maxCommands: 1000,
      ...options,
    };
  }

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;
    ctx.logger.info('MockSink initialized', {
      persistToAudit: this.options.persistToAudit,
    });
  }

  async enqueue(command: ControlCommand): Promise<void> {
    const span = this.ctx?.tracer.startSpan('MockSink.enqueue');

    try {
      this.commands.push(command);

      if (this.commands.length > (this.options.maxCommands ?? 1000)) {
        this.commands.shift(); // Remove oldest
      }

      span?.setAttribute('commandId', command.id);
      span?.setAttribute('queueSize', this.commands.length);
      span?.setStatus({ code: 'ok' });
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async flush(): Promise<SinkAck[]> {
    const span = this.ctx?.tracer.startSpan('MockSink.flush');
    const acks: SinkAck[] = [];

    try {
      for (const command of this.commands) {
        acks.push({
          ok: true,
          commandId: command.id,
          deliveredAt: new Date().toISOString(),
        });

        this.ctx?.logger.debug('Mock command processed', {
          commandId: command.id,
          category: command.category,
        });
      }

      this.commands = [];
      span?.setAttribute('flushedCount', acks.length);
      span?.setStatus({ code: 'ok' });

      return acks;
    } catch (error) {
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  async healthCheck(): Promise<HealthStatus> {
    return {
      healthy: true,
      lastCheck: new Date().toISOString(),
      latencyMs: 0,
      details: {
        source: 'mock',
        queueSize: this.commands.length,
      },
    };
  }

  async close(): Promise<void> {
    if (this.commands.length > 0) {
      await this.flush();
    }
    this.ctx?.logger.info('MockSink closed');
  }

  getCommands(): ControlCommand[] {
    return [...this.commands];
  }

  clear(): void {
    this.commands = [];
  }
}

export const createMockSink = (options?: MockSinkOptions): ControlSink => {
  return new MockSink(options);
};
