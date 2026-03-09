import {
  WebSocketBridgeTransport,
  createEnvelope,
  mergeEnvelopeUpdate,
  validateEnvelope,
  type AckPayload,
  type BridgeEnvelope,
  type BridgeMetadata,
  type BridgeTransport,
  type CommandPayload,
  type HeartbeatPayload,
  type TelemetryPayload,
  type WebSocketBridgeTransportOptions,
} from '../../lib/bridge-protocol-local';
import type { ControlCommand, Logger, SinkAck } from '../adapters/types';

export interface BridgeClientOptions {
  transport?: BridgeTransport;
  transportOptions?: WebSocketBridgeTransportOptions;
  workspaceId?: string;
  logger?: Logger;
  metadata?: BridgeMetadata;
  ackTimeoutMs?: number;
}

export interface BridgeClientLike {
  connect(): Promise<void>;
  disconnect(): Promise<void>;
  addTelemetryListener(listener: (payload: TelemetryPayload) => void): () => void;
  addHeartbeatListener(listener: (payload: HeartbeatPayload) => void): () => void;
  sendCommand(command: ControlCommand): Promise<SinkAck>;
  sendAck(envelope: BridgeEnvelope<AckPayload>): Promise<void>;
}

export type TelemetryListener = (payload: TelemetryPayload) => void;

interface PendingAck {
  resolve: (ack: SinkAck) => void;
  reject: (error: Error) => void;
  timeout: ReturnType<typeof setTimeout>;
}

const DEFAULT_ACK_TIMEOUT_MS = 10_000;

export class BridgeClient {
  private transport: BridgeTransport;
  private options: BridgeClientOptions;
  private connected = false;
  private pendingAcks = new Map<string, PendingAck>();
  private telemetryListeners = new Set<TelemetryListener>();
  private heartbeatListeners = new Set<(payload: HeartbeatPayload) => void>();
  private logger?: Logger;

  constructor(options: BridgeClientOptions = {}) {
    this.transport = options.transport ?? new WebSocketBridgeTransport(options.transportOptions ?? { url: 'ws://127.0.0.1:9773' });
    this.options = options;
    this.logger = options.logger;
    this.transport.setMessageHandler((message: any) => {
      void this.handleMessage(message);
    });
    this.transport.setErrorHandler?.((error: any) => {
      this.logger?.error('bridge.transport.error', error instanceof Error ? error : undefined, {
        message: error instanceof Error ? error.message : String(error),
      });
      this.connected = false;
    });
    this.transport.setCloseHandler?.(() => {
      this.logger?.warn('bridge.transport.closed');
      this.connected = false;
    });
  }

  setLogger(logger: Logger) {
    this.logger = logger;
  }

  async connect(): Promise<void> {
    if (this.connected) {
      return;
    }
    await this.transport.connect();
    this.connected = true;
    await this.sendHeartbeat('connected');
  }

  async disconnect(): Promise<void> {
    await this.transport.disconnect();
    this.connected = false;
  }

  async sendCommand(command: ControlCommand): Promise<SinkAck> {
    await this.connect();
    const correlationId = command.id;

    const payload: CommandPayload = {
      commandId: command.id,
      category: command.category,
      body: command.payload as Record<string, unknown>,
      // bridge-protocol expected numeric priority; convert if provided as string
      priority: typeof command.metadata.priority === 'number' ? command.metadata.priority : undefined,
      requestedAt: command.metadata.issuedAt,
      requestedBy: command.metadata.issuedBy,
    };

    const envelope = createEnvelope({
      payload,
      direction: 'desktop→extension',
      kind: 'command',
      correlationId,
      metadata: {
        bridgeVersion: '1.0.0',
        workspaceId: this.options.workspaceId,
        ...this.options.metadata,
      },
    });

    return new Promise<SinkAck>((resolve, reject) => {
      const ackTimeoutMs = this.options.ackTimeoutMs ?? DEFAULT_ACK_TIMEOUT_MS;
      const timeout = setTimeout(() => {
        this.pendingAcks.delete(correlationId);
        reject(new Error(`Ack timeout for command ${command.id}`));
      }, ackTimeoutMs);

      this.pendingAcks.set(correlationId, {
        resolve,
        reject,
        timeout,
      });

      this.transport
        .send(JSON.stringify(envelope))
        .catch((error: any) => {
          const pending = this.pendingAcks.get(correlationId);
          if (pending) {
            clearTimeout(pending.timeout);
            this.pendingAcks.delete(correlationId);
            pending.reject(error instanceof Error ? error : new Error(String(error)));
          }
        });
    });
  }

  addTelemetryListener(listener: TelemetryListener): () => void {
    this.telemetryListeners.add(listener);
    return () => {
      this.telemetryListeners.delete(listener);
    };
  }

  addHeartbeatListener(listener: (payload: HeartbeatPayload) => void): () => void {
    this.heartbeatListeners.add(listener);
    return () => {
      this.heartbeatListeners.delete(listener);
    };
  }

  async sendAck(envelope: BridgeEnvelope<AckPayload>): Promise<void> {
    await this.connect();
    await this.transport.send(JSON.stringify(envelope));
  }

  private async handleMessage(raw: string): Promise<void> {
    try {
      const parsed = JSON.parse(raw);
      const envelope = validateEnvelope(parsed);
      switch (envelope.kind) {
        case 'telemetry':
          this.handleTelemetry(envelope.payload as TelemetryPayload);
          break;
        case 'ack':
          // @ts-ignore - envelope type compatibility
          this.handleAck(envelope);
          break;
        case 'heartbeat':
          this.handleHeartbeat(envelope.payload as HeartbeatPayload);
          break;
        case 'command':
          // Desktop currently does not expect inbound commands; acknowledge receipt to avoid retries
          await this.sendAck(
            mergeEnvelopeUpdate(envelope, {
              kind: 'ack',
              direction: 'desktop→extension',
              payload: {
                ok: false,
                reason: 'desktop_cannot_execute_commands',
                receivedAt: new Date().toISOString(),
              },
            }) as BridgeEnvelope<AckPayload>,
          );
          break;
        default:
          this.logger?.warn?.('bridge.unhandled.kind', { kind: envelope.kind });
      }
    } catch (error) {
      this.logger?.error('bridge.message.error', error instanceof Error ? error : undefined, {
        raw,
        message: error instanceof Error ? error.message : String(error),
      });
    }
  }

  private handleTelemetry(payload: TelemetryPayload) {
    for (const listener of this.telemetryListeners) {
      try {
        listener(payload);
      } catch (error) {
        this.logger?.error('bridge.telemetry.listener.error', error instanceof Error ? error : undefined);
      }
    }
  }

  private handleAck(envelope: BridgeEnvelope<AckPayload>) {
    if (!envelope.correlationId) {
      this.logger?.warn('bridge.ack.missingCorrelation');
      return;
    }
    const pending = this.pendingAcks.get(envelope.correlationId);
    if (!pending) {
      this.logger?.warn('bridge.ack.unknown', { correlationId: envelope.correlationId });
      return;
    }

    clearTimeout(pending.timeout);
    this.pendingAcks.delete(envelope.correlationId);

    const payload = envelope.payload as AckPayload;
    if (payload.ok) {
      pending.resolve({
        ok: true,
        commandId: envelope.correlationId,
        deliveredAt: payload.receivedAt,
      });
    } else {
      pending.resolve({
        ok: false,
        commandId: envelope.correlationId,
        error: payload.reason ?? 'unknown',
      });
    }
  }

  private handleHeartbeat(payload: HeartbeatPayload) {
    for (const listener of this.heartbeatListeners) {
      try {
        listener(payload);
      } catch (error) {
        this.logger?.error('bridge.heartbeat.listener.error', error instanceof Error ? error : undefined);
      }
    }
  }

  private async sendHeartbeat(status: string) {
    const envelope = createEnvelope({
      payload: {
        sequence: Date.now(),
        reportedAt: new Date().toISOString(),
        status,
      } satisfies HeartbeatPayload,
      direction: 'desktop→extension',
      kind: 'heartbeat',
      metadata: {
        bridgeVersion: '1.0.0',
        workspaceId: this.options.workspaceId,
        ...this.options.metadata,
      },
    });
    await this.transport.send(JSON.stringify(envelope));
  }
}

const clients = new Map<string, BridgeClient>();

export function getBridgeClient(options: BridgeClientOptions = {}): BridgeClient {
  const key = options.transportOptions?.url ?? 'ws://127.0.0.1:9773';
  const existing = clients.get(key);
  if (existing) {
    if (options.logger) {
      existing.setLogger(options.logger);
    }
    return existing;
  }
  const client = new BridgeClient(options);
  clients.set(key, client);
  return client;
}
