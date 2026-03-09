/**
 * @fileoverview NATS connector template supporting pub/sub, request-reply, and JetStream hooks.
 *
 * Demonstrates how to configure resilient connections, manage subscriptions, and integrate
 * JetStream operations. Pair with `RetryPolicy` to handle transient publish failures and
 * `Telemetry` to capture latency across subjects.
 *
 * @see {@link NatsConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 */
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';

/**
 * Configuration contract for `NatsConnector`.
 */
export interface NatsConnectorConfig extends ConnectionOptions {
  /**
   * NATS server URLs
   */
  servers: string[];
  
  /**
   * Client name
   */
  name?: string;
  
  /**
   * Username for authentication
   */
  user?: string;
  
  /**
   * Password for authentication
   */
  pass?: string;
  
  /**
   * Token for authentication
   */
  token?: string;
  
  /**
   * Maximum reconnection attempts
   * @default -1 (infinite)
   */
  maxReconnectAttempts?: number;
  
  /**
   * Reconnection time wait in milliseconds
   * @default 2000
   */
  reconnectTimeWait?: number;
  
  /**
   * Connection timeout in milliseconds
   * @default 20000
   */
  timeout?: number;
  
  /**
   * Ping interval in milliseconds
   * @default 120000 (2 minutes)
   */
  pingInterval?: number;
  
  /**
   * Max ping out
   * @default 2
   */
  maxPingOut?: number;
  
  /**
   * Enable/disable verbose mode
   * @default false
   */
  verbose?: boolean;
  
  /**
   * Enable/disable pedantic mode
   * @default false
   */
  pedantic?: boolean;
  
  /**
   * TLS configuration
   */
  tls?: {
    cert?: string;
    key?: string;
    ca?: string;
    rejectUnauthorized?: boolean;
  };
  
  /**
   * Enable/disable JetStream
   * @default false
   */
  jetstream?: boolean;
  
  /**
   * JetStream options
   */
  jetstreamOptions?: {
    domain?: string;
    timeout?: number;
  };
}

/**
 * NATS connector showcasing lifecycle scaffolding for messaging and JetStream patterns.
 *
 * **Example (basic publish/subscribe):**
 * ```ts
 * const connector = new NatsConnector({ servers: ['nats://localhost:4222'] });
 * await connector.connect();
 * await connector.subscribe('demo.events');
 * await connector.send({ hello: 'world' }, { subject: 'demo.events' });
 * ```
 *
 * **Example (JetStream publish with durable consumer):**
 * ```ts
 * const connector = new NatsConnector({ servers: ['nats://localhost:4222'], jetstream: true });
 * await connector.connect();
 * await connector.jsCreateStream({ name: 'events', subjects: ['events.*'] });
 * await connector.jsCreateConsumer('events', { durable_name: 'processor' });
 * await connector.jsPublish('events.created', { id: '123' });
 * ```
 */
export class NatsConnector extends BaseConnector<NatsConnectorConfig> {
  private connection: unknown = null;
  private subscriptions: Map<string, any> = new Map();
  private jetstream: unknown = null;

  /**
   * Applies resilient defaults (infinite reconnects, JetStream disabled by default).
   */
  constructor(config: NatsConnectorConfig) {
    super({
      maxReconnectAttempts: -1,
      reconnectTimeWait: 2000,
      timeout: 20000,
      pingInterval: 120000,
      maxPingOut: 2,
      verbose: false,
      pedantic: false,
      jetstream: false,
      ...config,
      type: 'nats',
    });
  }

  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    try {
      // In a real implementation using 'nats' package:
      // const { connect, StringCodec } = require('nats');
      // 
      // this.connection = await connect({
      //   servers: this._config.servers,
      //   name: this._config.name,
      //   user: this._config.user,
      //   pass: this._config.pass,
      //   token: this._config.token,
      //   maxReconnectAttempts: this._config.maxReconnectAttempts,
      //   reconnectTimeWait: this._config.reconnectTimeWait,
      //   timeout: this._config.timeout,
      //   pingInterval: this._config.pingInterval,
      //   maxPingOut: this._config.maxPingOut,
      //   verbose: this._config.verbose,
      //   pedantic: this._config.pedantic,
      //   tls: this._config.tls,
      // });
      // 
      // // Set up event listeners
      // (async () => {
      //   for await (const status of this.connection.status()) {
      //     this._handleStatus(status);
      //   }
      // })();
      // 
      // // Initialize JetStream if enabled
      // if (this._config.jetstream) {
      //   this.jetstream = this.connection.jetstream(this._config.jetstreamOptions);
      // }

      this.emit('connected');
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /** @inheritdoc */
  protected async _disconnect(): Promise<void> {
    // Unsubscribe from all subscriptions
    for (const subject of this.subscriptions.keys()) {
      try {
        // await subscription.drain();
        this.subscriptions.delete(subject);
      } catch (error) {
        this.emit('error', error);
      }
    }

    if (this.connection) {
      try {
        // await this.connection.drain();
        // await this.connection.close();
        this.connection = null;
        this.jetstream = null;
      } catch (error) {
        this.emit('error', error);
      }
    }
  }

  /**
   * Publishes payload to a subject.
   *
   * Encodes payload, forwards to the underlying NATS client, and emits `published` for observability.
   * Combine with `RetryPolicy.execute()` in callers for guaranteed delivery semantics.
   *
   * @param data - Message payload (stringified when object).
   * @param options - Publish options including `subject`, optional reply and headers.
   */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    if (!this.connection) {
      throw new Error('NATS connection is not established');
    }

    const subject = options.subject;
    if (!subject) {
      throw new Error('Subject is required for NATS publish');
    }

    try {
      const payload = typeof data === 'string' ? data : JSON.stringify(data);
      
      // const sc = StringCodec();
      // this.connection.publish(subject, sc.encode(payload), {
      //   reply: options.reply,
      //   headers: options.headers,
      // });

      this.emit('published', { subject, payload, options });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Subscribes to a subject.
   *
   * Creates an async iterator to process inbound messages and emits `subscribed` when active. Use
   * queue groups via `options.queue` to distribute load.
   */
  public async subscribe(subject: string, options: Record<string, any> = {}): Promise<void> {
    if (!this.connection) {
      throw new Error('NATS connection is not established');
    }

    if (this.subscriptions.has(subject)) {
      throw new Error(`Already subscribed to subject: ${subject}`);
    }

    try {
      // const subscription = this.connection.subscribe(subject, {
      //   queue: options.queue,
      //   max: options.max,
      // });
      // 
      // this.subscriptions.set(subject, subscription);
      // 
      // (async () => {
      //   for await (const msg of subscription) {
      //     this._handleMessage(msg);
      //   }
      // })();

      this.emit('subscribed', { subject, options });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Unsubscribes from a subject.
   *
   * Drains the subscription (if supported) and removes internal bookkeeping before emitting
   * `unsubscribed`.
   */
  public async unsubscribe(subject: string): Promise<void> {
    const subscription = this.subscriptions.get(subject);
    if (!subscription) {
      return;
    }

    try {
      // await subscription.drain();
      this.subscriptions.delete(subject);
      this.emit('unsubscribed', { subject });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Issues request and awaits reply (request/reply pattern).
   *
   * Applies default timeouts and emits `requestSent` for instrumentation.
   */
  public async request(subject: string, data: unknown, options: Record<string, any> = {}): Promise<unknown> {
    if (!this.connection) {
      throw new Error('NATS connection is not established');
    }

    try {
      const payload = typeof data === 'string' ? data : JSON.stringify(data);
      const timeout = options.timeout || this._config.timeout || 5000;

      // const sc = StringCodec();
      // const msg = await this.connection.request(
      //   subject,
      //   sc.encode(payload),
      //   { timeout, headers: options.headers }
      // );
      // 
      // const response = sc.decode(msg.data);
      // 
      // try {
      //   return JSON.parse(response);
      // } catch {
      //   return response;
      // }

      this.emit('requestSent', { subject, payload, timeout });
      return null; // Placeholder
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * JetStream publish convenience helper.
   *
   * Assumes `jetstream` is enabled and emits `jsPublished` for observability.
   */
  public async jsPublish(subject: string, data: unknown, options: Record<string, any> = {}): Promise<void> {
    if (!this.jetstream) {
      throw new Error('JetStream is not enabled');
    }

    try {
      const payload = typeof data === 'string' ? data : JSON.stringify(data);

      // const sc = StringCodec();
      // const ack = await this.jetstream.publish(subject, sc.encode(payload), {
      //   msgID: options.msgId,
      //   headers: options.headers,
      //   timeout: options.timeout,
      // });

      this.emit('jsPublished', { subject, payload, options });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * JetStream subscription helper.
   *
   * Configures durable, ephemeral, or queue subscriptions and emits `jsSubscribed` when active.
   */
  public async jsSubscribe(subject: string, options: Record<string, any> = {}): Promise<void> {
    if (!this.jetstream) {
      throw new Error('JetStream is not enabled');
    }

    try {
      // const subscription = await this.jetstream.subscribe(subject, {
      //   stream: options.stream,
      //   durable: options.durable,
      //   queue: options.queue,
      //   config: options.config,
      // });
      // 
      // this.subscriptions.set(subject, subscription);
      // 
      // (async () => {
      //   for await (const msg of subscription) {
      //     this._handleJsMessage(msg);
      //   }
      // })();

      this.emit('jsSubscribed', { subject, options });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Creates JetStream stream definition.
   */
  public async jsCreateStream(config: Record<string, any>): Promise<void> {
    if (!this.jetstream) {
      throw new Error('JetStream is not enabled');
    }

    try {
      // const jsm = await this.connection.jetstreamManager();
      // await jsm.streams.add(config);

      this.emit('jsStreamCreated', { config });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Creates JetStream consumer definition.
   */
  public async jsCreateConsumer(stream: string, config: Record<string, any>): Promise<void> {
    if (!this.jetstream) {
      throw new Error('JetStream is not enabled');
    }

    try {
      // const jsm = await this.connection.jetstreamManager();
      // await jsm.consumers.add(stream, config);

      this.emit('jsConsumerCreated', { stream, config });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Normalizes standard NATS messages before emitting events.
   *
   * Attempts JSON parsing, captures metadata (subject, reply, sid), and emits connector events.
   */
  private _handleMessage(msg: unknown): void {
    try {
      // const sc = StringCodec();
      // const data = sc.decode(msg.data);
      
      let payload: unknown;
      try {
        payload = JSON.parse(msg.data);
      } catch {
        payload = msg.data;
      }

      this._emitEvent({
        id: uuidv4(),
        type: 'message',
        timestamp: Date.now(),
        payload,
        metadata: {
          subject: msg.subject,
          reply: msg.reply,
          sid: msg.sid,
          headers: msg.headers,
        },
      });

      // Auto-reply if reply subject is present
      if (msg.reply) {
        // msg.respond(sc.encode(JSON.stringify({ received: true })));
      }
    } catch (error) {
      this.emit('error', error);
    }
  }

  /**
   * Normalizes JetStream messages before emitting events.
   *
   * Emits `js_message` events including stream and consumer metadata; extend to acknowledge or NAK
   * messages.
   */
  private _handleJsMessage(msg: unknown): void {
    try {
      // const sc = StringCodec();
      // const data = sc.decode(msg.data);
      
      let payload: unknown;
      try {
        payload = JSON.parse(msg.data);
      } catch {
        payload = msg.data;
      }

      this._emitEvent({
        id: uuidv4(),
        type: 'js_message',
        timestamp: Date.now(),
        payload,
        metadata: {
          subject: msg.subject,
          seq: msg.seq,
          stream: msg.info?.stream,
          consumer: msg.info?.consumer,
        },
      });

      // Acknowledge the message
      // msg.ack();
    } catch (error) {
      this.emit('error', error);
      // msg.nak();
    }
  }

  /**
   * Forwards client status events to connector observers.
   */
  private _handleStatus(status: unknown): void {
    this.emit('status', status);

    switch (status.type) {
      case 'disconnect':
        this.emit('disconnected', status);
        break;
      case 'reconnect':
        this.emit('reconnected', status);
        break;
      case 'update':
        this.emit('serversChanged', status);
        break;
      case 'error':
        this.emit('error', status.data);
        break;
    }
  }

  /**
   * Retrieves connection statistics snapshot.
   */
  public getStats(): unknown {
    if (!this.connection) {
      return null;
    }

    // return this.connection.stats();
    return {
      inMsgs: 0,
      outMsgs: 0,
      inBytes: 0,
      outBytes: 0,
      reconnects: 0,
    };
  }

  /**
   * Returns list of active subscription subjects.
   */
  public getSubscriptions(): string[] {
    return Array.from(this.subscriptions.keys());
  }

  /**
   * Flushes pending client messages.
   */
  public async flush(): Promise<void> {
    if (!this.connection) {
      return;
    }

    // await this.connection.flush();
    this.emit('flushed');
  }

  /** @inheritdoc */
  public override async destroy(): Promise<void> {
    this.subscriptions.clear();
    await super.destroy();
  }
}
