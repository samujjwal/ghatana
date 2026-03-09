/**
 * @fileoverview MQTT connector template demonstrating subscription, publish, and session flows.
 *
 * Provides scaffolding for integrating with MQTT brokers, managing subscriptions, and handling
 * reconnect logic. Combine with `RetryPolicy` for retrying publishes and `Telemetry` to trace message
 * round trips.
 *
 * @see {@link MqttConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 */
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';

/**
 * Configuration options accepted by `MqttConnector`.
 */
export interface MqttConnectorConfig extends ConnectionOptions {
  /** MQTT broker URL (mqtt:// or mqtts://). */
  url: string;
  /** Custom client identifier; defaults to randomly generated UUID. */
  clientId?: string;
  /** Username for authentication. */
  username?: string;
  /** Password for authentication. */
  password?: string;
  /** Whether to start a clean session. @default true */
  clean?: boolean;
  /** Keepalive interval in seconds. @default 60 */
  keepalive?: number;
  /** Connection timeout in milliseconds. @default 30000 */
  connectTimeout?: number;
  /** Reconnect period in milliseconds. @default 1000 */
  reconnectPeriod?: number;
  /** Default QoS for publish/subscribe. @default 0 */
  qos?: 0 | 1 | 2;
  /** Topics to subscribe to upon connection. */
  topics?: string[];
  /** Last will configuration. */
  will?: {
    topic: string;
    payload: string | Buffer;
    qos?: 0 | 1 | 2;
    retain?: boolean;
  };
  /** Automatically re-subscribe on reconnect. @default true */
  resubscribe?: boolean;
  /** MQTT protocol version (3,4,5). @default 4 */
  protocolVersion?: 3 | 4 | 5;
}

/**
 * MQTT connector handling publish/subscribe boilerplate (uses template client interactions).
 *
 * **Example (basic publish/subscribe):**
 * ```ts
 * const connector = new MqttConnector({ url: 'mqtt://localhost:1883', topics: ['sensor/+/temperature'] });
 * connector.onEvent('message', event => console.log(event.payload));
 * await connector.connect();
 * await connector.send({ temp: 21.5 }, { topic: 'sensor/lab/temperature' });
 * ```
 *
 * **Example (with RetryPolicy and retained messages):**
 * ```ts
 * const retry = new RetryPolicy({ maxAttempts: 4, reconnectDelay: 1000 });
 * const connector = new MqttConnector({ url: 'mqtt://broker', qos: 1, resubscribe: true });
 * await connector.connect();
 * await retry.execute(() => connector.send({ status: 'online' }, { topic: 'device/status', retain: true }));
 * ```
 */
export class MqttConnector extends BaseConnector<MqttConnectorConfig> {
  private client: unknown = null;
  private subscribedTopics: Set<string> = new Set();
  private messageQueue: Array<{ topic: string; message: unknown; options: unknown }> = [];

  /**
   * Creates connector with generated client id and default mqtt settings.
   *
   * @param config - Configuration options for the connector.
   */
  constructor(config: MqttConnectorConfig) {
    super({
      clientId: `dcmaar-${uuidv4()}`,
      clean: true,
      keepalive: 60,
      connectTimeout: 30000,
      reconnectPeriod: 1000,
      qos: 0,
      resubscribe: true,
      protocolVersion: 4,
      ...config,
      type: 'mqtt',
    });
  }

  /**
   * Establishes connection to MQTT broker (mocked in template).
   *
   * In production implementations, initialize the MQTT client, attach event handlers, and honor
   * configuration options such as `reconnectPeriod` and `keepalive`.
   *
   * @throws {Error} If connection fails.
   */
  protected async _connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        setTimeout(() => {
          this._handleConnect();
          resolve();
        }, 100);
      } catch (error) {
        reject(error as any);
      }
    });
  }

  /**
   * Disconnects from MQTT broker.
   *
   * Cleans up client references and subscription cache.
   *
   * @throws {Error} If disconnection fails.
   */
  protected async _disconnect(): Promise<void> {
    return new Promise((resolve) => {
      if (!this.client) {
        resolve();
        return;
      }

      this.client = null;
      this.subscribedTopics.clear();
      resolve();
    });
  }

  /**
   * Publishes payload to broker.
   *
   * Serializes objects to JSON, applies default QoS, and emits `published` events for observers. Hook
   * into this to drive metrics or audit logs.
   *
   * @param data - Message payload (stringified when object)
   * @param options - Publish options such as `topic`, `qos`, `retain`
   *
   * @throws {Error} If publish fails.
   */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    const topic = options.topic;
    if (!topic) {
      throw new Error('Topic is required for MQTT publish');
    }

    if (!this.client) {
      throw new Error('MQTT client is not connected');
    }

    const message = typeof data === 'string' ? data : JSON.stringify(data);
    const publishOptions = {
      qos: options.qos ?? this._config.qos ?? 0,
      retain: options.retain ?? false,
    };

    return new Promise((resolve) => {
      this.emit('published', { topic, message, options: publishOptions });
      resolve();
    });
  }

  /**
   * Subscribes connector to one or more topics.
   *
   * Maintains an internal topic set for use during reconnection and emits `subscribed` for
   * instrumentation.
   *
   * @param topic - Topic(s) to subscribe to
   * @param qos - Quality of Service (QoS) for subscription
   *
   * @throws {Error} If subscription fails.
   */
  public async subscribe(topic: string | string[], qos?: 0 | 1 | 2): Promise<void> {
    if (!this.client) {
      throw new Error('MQTT client is not connected');
    }

    const topics = Array.isArray(topic) ? topic : [topic];
    const subscribeQos = qos ?? this._config.qos ?? 0;

    return new Promise((resolve) => {
      topics.forEach(t => this.subscribedTopics.add(t));
      this.emit('subscribed', { topics, qos: subscribeQos });
      resolve();
    });
  }

  /**
   * Unsubscribes connector from supplied topic list.
   *
   * Updates the subscription cache and emits `unsubscribed`. Extend to propagate unsubscribe calls to
   * underlying MQTT clients.
   *
   * @param topic - Topic(s) to unsubscribe from
   *
   * @throws {Error} If unsubscription fails.
   */
  public async unsubscribe(topic: string | string[]): Promise<void> {
    if (!this.client) {
      throw new Error('MQTT client is not connected');
    }

    const topics = Array.isArray(topic) ? topic : [topic];

    return new Promise((resolve) => {
      // In a real implementation:
      // this.client.unsubscribe(topics, (error: Error | null) => {
      //   if (error) {
      //     this.emit('unsubscribeError', { topics, error });
      //   } else {
      //     topics.forEach(t => this.subscribedTopics.delete(t));
      //     this.emit('unsubscribed', { topics });
      //     resolve();
      //   }
      // });

      topics.forEach(t => this.subscribedTopics.delete(t));
      this.emit('unsubscribed', { topics });
      resolve();
    });
  }

  /**
   * Returns shallow copy of subscribed topics.
   *
   * @returns {string[]} Array of subscribed topics.
   */
  public getSubscribedTopics(): string[] {
    return Array.from(this.subscribedTopics);
  }

  /**
   * Handles mocked connect success, resubscribing and flushing message queue.
   */
  private _handleConnect(): void {
    this.emit('connected');

    // Subscribe to configured topics
    if (this._config.topics && this._config.topics.length > 0) {
      this.subscribe(this._config.topics).catch(error => {
        this.emit('error', error);
      });
    }

    // Process any queued messages
    this._processMessageQueue();
  }

  /**
   * Normalizes inbound message payload and emits connector event.
   *
   * @param {string} topic - Topic of incoming message
   * @param {Buffer} message - Incoming message payload
   */
  private _handleMessage(topic: string, message: Buffer): void {
    try {
      let payload: unknown;

      // Try to parse as JSON
      try {
        payload = JSON.parse(message.toString());
      } catch {
        payload = message.toString();
      }

      this._emitEvent({
        id: uuidv4(),
        type: 'message',
        timestamp: Date.now(),
        payload,
        metadata: {
          topic,
          size: message.length,
        },
      });
    } catch (error) {
      this.emit('error', error);
    }
  }

  /** Forwards MQTT client errors to listeners. */
  private _handleError(error: Error): void {
    this.emit('error', error);
  }

  /** Emits `disconnected` when the broker connection closes. */
  private _handleClose(): void {
    this.emit('disconnected');
  }

  /** Flushes queued publishes captured while disconnected. */
  private _processMessageQueue(): void {
    while (this.messageQueue.length > 0) {
      const { topic, message, options } = this.messageQueue.shift()!;
      this.send(message, { ...options, topic }).catch(error => {
        this.emit('error', error);
      });
    }
  }

  /** @inheritdoc */
  public override async destroy(): Promise<void> {
    this.messageQueue = [];
    await super.destroy();
  }
}
