import type { ControlSink, SinkContext, ControlCommand, SinkAck } from '../types';

export interface BridgeSinkOptions {
  client?: any; // Should be BridgeClient type
  maxRetries?: number;
  retryDelayMs?: number;
}

export class BridgeSink implements ControlSink {
  readonly kind = 'bridge' as const;
  private ctx?: SinkContext;
  private client: any; // Should be BridgeClient type
  private options: Required<BridgeSinkOptions>;

  constructor(options: BridgeSinkOptions = {}) {
    this.client = options.client;
    // @ts-ignore - client is optional in constructor but required in type
    this.options = {
      client: options.client,
      maxRetries: options.maxRetries ?? 3,
      retryDelayMs: options.retryDelayMs ?? 1000,
    };
  }

  async init(ctx: SinkContext): Promise<void> {
    this.ctx = ctx;
    if (this.client) {
      await this.client.connect();
    }
  }

  async enqueue(command: ControlCommand): Promise<void> {
    await this.ctx?.queue.enqueue(command);
  }

  async flush(): Promise<SinkAck[]> {
    const acks: SinkAck[] = [];
    const span = this.ctx?.tracer?.startSpan('BridgeSink.flush');

    try {
      let command = await this.ctx?.queue.peek();

      while (command) {
        const ack = await this.sendWithRetry(command);
        acks.push(ack);

        if (ack.ok) {
          await this.ctx?.queue.dequeue();
          command = await this.ctx?.queue.peek();
        } else {
          break;
        }
      }

      span?.setAttribute('flushedCount', acks.length);
      span?.setStatus({ code: 'ok' });
      return acks;
    } catch (error) {
      // @ts-ignore - recordException may not be in all Span implementations
      span?.recordException?.(error as Error);
      span?.setStatus({ code: 'error', message: (error as Error).message });
      throw error;
    } finally {
      span?.end();
    }
  }

  private async sendWithRetry(command: ControlCommand): Promise<SinkAck> {
    if (!this.client) {
      return {
        ok: false,
        commandId: command.id,
        error: 'Bridge client not initialized',
      };
    }

    let lastError: Error | undefined;
    const maxRetries = this.options.maxRetries;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        const ack = await this.client.sendCommand(command);
        if (ack.ok) {
          this.ctx?.logger?.debug('Command delivered', {
            commandId: command.id,
            category: command.category,
            attempt: attempt + 1,
          });
          return ack;
        }
        throw new Error(ack.error || 'Command delivery failed');
      } catch (error) {
        lastError = error as Error;
        const shouldRetry = attempt < maxRetries;

        this.ctx?.logger?.warn('Command send failed', {
          commandId: command.id,
          attempt: attempt + 1,
          maxRetries,
          error: lastError.message,
          willRetry: shouldRetry,
        });

        if (shouldRetry) {
          await new Promise(resolve =>
            setTimeout(resolve, this.options.retryDelayMs)
          );
        }
      }
    }

    return {
      ok: false,
      commandId: command.id,
      error: `Failed after ${maxRetries} attempts: ${lastError?.message}`,
    };
  }

  async close(): Promise<void> {
    await this.client?.disconnect();
  }
}

export const createBridgeSink = (options: BridgeSinkOptions = {}) => {
  return new BridgeSink(options);
};
