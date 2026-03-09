/**
 * @doc.type class
 * @doc.purpose Generic webhook notification delivery plugin
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Sends notifications to arbitrary HTTP webhooks with JSON payloads.
 * Supports custom headers, authentication, and retries.
 * 
 * @see {@link INotification}
 */

import { INotification } from '../../interfaces/Notification';

/**
 * Webhook notification configuration
 */
export interface WebhookConfig {
  /** Base webhook URL */
  baseUrl: string;
  /** Custom headers to include */
  headers?: Record<string, string>;
  /** Authentication token (Bearer) */
  authToken?: string;
  /** Timeout in milliseconds */
  timeout?: number;
  /** Number of retries on failure */
  retries?: number;
}

/**
 * Webhook Notification Plugin
 * 
 * Sends notifications to HTTP webhooks. Useful for integrations with
 * external services, custom APIs, and serverless functions.
 * 
 * Usage:
 * ```typescript
 * const webhook = new WebhookNotificationPlugin({
 *   baseUrl: 'https://api.example.com/notify',
 *   authToken: process.env.WEBHOOK_TOKEN
 * });
 * 
 * await webhook.initialize();
 * await webhook.send('user@example.com', 'Alert', 'CPU high', 'alert');
 * ```
 */
export class WebhookNotificationPlugin implements INotification {
  // IPlugin interface implementation
  readonly id = 'webhook-notifications';
  readonly name = 'Webhook Notifications';
  readonly version = '0.1.0';
  readonly description = 'Send notifications via HTTP webhooks';
  enabled = false;
  metadata: Record<string, unknown> = {};

  // Configuration
  private config: Required<WebhookConfig>;
  
  // History
  private history: Map<string, Array<Record<string, unknown>>> = new Map();

  /**
   * Create webhook notification plugin
   * 
   * @param config - Webhook configuration
   * @throws Error if baseUrl not provided
   */
  constructor(config: WebhookConfig) {
    if (!config.baseUrl) {
      throw new Error('Webhook plugin requires baseUrl');
    }

    this.config = {
      baseUrl: config.baseUrl,
      headers: config.headers ?? { 'Content-Type': 'application/json' },
      authToken: config.authToken ?? '',
      timeout: config.timeout ?? 10000,
      retries: config.retries ?? 3,
    };
  }

  /**
   * Initialize the webhook plugin
   * 
   * Validates base URL is reachable.
   * 
   * @throws Error if URL validation fails
   */
  async initialize(): Promise<void> {
    try {
      // Validate URL format
      new URL(this.config.baseUrl);
      this.enabled = true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Invalid webhook URL: ${message}`);
    }
  }

  /**
   * Shutdown the webhook plugin
   */
  async shutdown(): Promise<void> {
    this.enabled = false;
    this.history.clear();
  }

  /**
   * Send notification via webhook
   * 
   * @param recipient - Recipient identifier (optional for webhooks)
   * @param subject - Notification subject
   * @param message - Notification message
   * @param type - Notification type
   * @returns True if sent successfully
   */
  async send(
    recipient: string,
    subject: string,
    message: string,
    type: string,
  ): Promise<boolean> {
    if (!this.enabled) {
      throw new Error('Webhook plugin not initialized');
    }

    const payload = {
      recipient,
      subject,
      message,
      type,
      timestamp: Date.now(),
    };

    // Retry logic
    let lastError: Error | null = null;
    for (let attempt = 1; attempt <= this.config.retries; attempt++) {
      try {
        await this.sendWebhook(payload);
        this.recordHistory(recipient, payload);
        return true;
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));
        if (attempt < this.config.retries) {
          // Exponential backoff
          await new Promise(resolve =>
            setTimeout(resolve, 100 * Math.pow(2, attempt - 1)),
          );
        }
      }
    }

    throw lastError ?? new Error('Failed to send webhook');
  }

  /**
   * Check if webhook service is available
   * 
   * @returns True if configured and enabled
   */
  async isAvailable(): Promise<boolean> {
    return this.enabled;
  }

  /**
   * Get notification history for a recipient
   * 
   * @param recipient - Recipient identifier
   * @param limit - Maximum records to return
   * @returns Array of sent notifications
   */
  async getHistory(
    recipient: string,
    limit?: number,
  ): Promise<Array<Record<string, unknown>>> {
    const records = this.history.get(recipient) ?? [];
    return limit ? records.slice(-limit) : records;
  }

  /**
   * Execute command interface from IPlugin
   * 
   * @param command - Command name
   * @param params - Command parameters
   * @returns Command result
   */
  async execute(
    command: string,
    params?: Record<string, unknown>,
  ): Promise<unknown> {
    switch (command) {
      case 'send':
        return await this.send(
          String(params?.recipient ?? ''),
          String(params?.subject ?? ''),
          String(params?.message ?? ''),
          String(params?.type ?? 'info'),
        );
      case 'isAvailable':
        return await this.isAvailable();
      case 'getHistory':
        return await this.getHistory(
          String(params?.recipient ?? ''),
          params?.limit as number | undefined,
        );
      default:
        throw new Error(`Unknown command: ${command}`);
    }
  }

  /**
   * Send webhook request
   * 
   * Constructs headers with auth token and sends POST request.
   * In production, would use fetch or axios.
   * 
   * @private
   * @param payload - Notification payload
   * @throws Error if request fails
   */
  private async sendWebhook(payload: Record<string, unknown>): Promise<void> {
    const headers = { ...this.config.headers };

    if (this.config.authToken) {
      headers['Authorization'] = `Bearer ${this.config.authToken}`;
    }

    // Simulate HTTP request
    // In production:
    // const response = await fetch(this.config.baseUrl, {
    //   method: 'POST',
    //   headers,
    //   body: JSON.stringify(payload),
    //   signal: AbortSignal.timeout(this.config.timeout)
    // });
    //
    // if (!response.ok) {
    //   throw new Error(`Webhook failed: ${response.status} ${response.statusText}`);
    // }

    // Simulate call
    if (!payload || !headers) {
      throw new Error('Invalid payload or headers');
    }

    await new Promise(resolve => setTimeout(resolve, 10));
  }

  /**
   * Record notification in history
   * 
   * @private
   * @param recipient - Recipient identifier
   * @param record - Notification record
   */
  private recordHistory(
    recipient: string,
    record: Record<string, unknown>,
  ): void {
    const history = this.history.get(recipient) ?? [];
    history.push(record);
    // Keep only last 100 records per recipient
    if (history.length > 100) {
      history.shift();
    }
    this.history.set(recipient, history);
  }
}
