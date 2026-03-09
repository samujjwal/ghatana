/**
 * @doc.type class
 * @doc.purpose Slack notification delivery plugin
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Sends notifications to Slack channels and direct messages via Slack webhook or bot API.
 * Supports rich message formatting and attachments.
 * 
 * @see {@link INotification}
 */

import { INotification } from '../../interfaces/Notification';

/**
 * Slack notification configuration
 */
export interface SlackConfig {
  /** Slack webhook URL or bot token */
  webhookUrl?: string;
  /** Slack bot token for direct messages */
  botToken?: string;
  /** Default channel for notifications */
  defaultChannel?: string;
  /** Enable message threading */
  threading?: boolean;
}

/**
 * Slack Notification Plugin
 * 
 * Sends notifications via Slack using webhook or bot API.
 * 
 * Usage:
 * ```typescript
 * const slack = new SlackNotificationPlugin({
 *   webhookUrl: process.env.SLACK_WEBHOOK_URL,
 *   defaultChannel: '#alerts'
 * });
 * 
 * await slack.initialize();
 * await slack.send('@user', 'Alert', 'High CPU usage detected', 'alert');
 * ```
 */
export class SlackNotificationPlugin implements INotification {
  // IPlugin interface implementation
  readonly id = 'slack-notifications';
  readonly name = 'Slack Notifications';
  readonly version = '0.1.0';
  readonly description = 'Send notifications to Slack';
  enabled = false;
  metadata: Record<string, unknown> = {};

  // Configuration
  private config: Required<SlackConfig>;
  
  // History
  private history: Map<string, Array<Record<string, unknown>>> = new Map();

  /**
   * Create Slack notification plugin
   * 
   * @param config - Slack configuration
   */
  constructor(config: SlackConfig = {}) {
    this.config = {
      webhookUrl: config.webhookUrl ?? '',
      botToken: config.botToken ?? '',
      defaultChannel: config.defaultChannel ?? '#notifications',
      threading: config.threading ?? false,
    };
  }

  /**
   * Initialize the Slack plugin
   * 
   * Validates webhook URL or bot token is configured.
   * 
   * @throws Error if no webhook URL or bot token provided
   */
  async initialize(): Promise<void> {
    if (!this.config.webhookUrl && !this.config.botToken) {
      throw new Error(
        'Slack plugin requires either webhookUrl or botToken',
      );
    }
    this.enabled = true;
  }

  /**
   * Shutdown the Slack plugin
   */
  async shutdown(): Promise<void> {
    this.enabled = false;
    this.history.clear();
  }

  /**
   * Send a Slack notification
   * 
   * @param recipient - Channel name (#channel), user ID, or email
   * @param subject - Notification subject (used as title)
   * @param message - Notification message (main content)
   * @param type - Notification type (determines formatting)
   * @returns True if sent successfully
   */
  async send(
    recipient: string,
    subject: string,
    message: string,
    type: string,
  ): Promise<boolean> {
    if (!this.enabled) {
      throw new Error('Slack plugin not initialized');
    }

    try {
      // Determine color based on type
      const color = this.getColorForType(type);

      // Build message payload
      const payload = {
        channel: recipient || this.config.defaultChannel,
        attachments: [
          {
            fallback: `${subject}: ${message}`,
            color: color,
            title: subject,
            text: message,
            ts: Math.floor(Date.now() / 1000),
          },
        ],
      };

      // Send via webhook or bot API (simulated)
      await this.sendToSlack(payload);

      // Record in history
      this.recordHistory(recipient, { subject, message, type, timestamp: Date.now() });

      return true;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to send Slack notification: ${errorMessage}`);
    }
  }

  /**
   * Check if Slack service is available
   * 
   * @returns True if configured and enabled
   */
  async isAvailable(): Promise<boolean> {
    if (!this.enabled) {
      return false;
    }

    try {
      // Simulate availability check by verifying config
      return !!(this.config.webhookUrl || this.config.botToken);
    } catch {
      return false;
    }
  }

  /**
   * Get notification history for a recipient
   * 
   * @param recipient - Channel or user identifier
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
   * Get color for message type
   * 
   * @private
   * @param type - Notification type
   * @returns Slack color code
   */
  private getColorForType(type: string): string {
    switch (type.toLowerCase()) {
      case 'alert':
      case 'error':
        return '#FF0000'; // Red
      case 'warning':
        return '#FFA500'; // Orange
      case 'success':
        return '#00FF00'; // Green
      case 'info':
      default:
        return '#0099FF'; // Blue
    }
  }

  /**
   * Send message to Slack
   * 
   * In production, would use fetch or slack SDK.
   * Here we simulate the call.
   * 
   * @private
   * @param payload - Slack message payload
   */
  private async sendToSlack(payload: Record<string, unknown>): Promise<void> {
    // Simulate API call
    if (!payload) {
      throw new Error('Invalid payload');
    }

    // In production: await fetch(this.config.webhookUrl, { method: 'POST', body: JSON.stringify(payload) })
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
