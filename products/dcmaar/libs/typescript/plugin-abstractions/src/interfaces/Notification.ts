/**
 * Notification Plugin Interface
 * Defines the contract for notification delivery plugins
 */

import { IPlugin } from '@ghatana/dcmaar-types';

export interface INotification extends IPlugin {
  /**
   * Send a notification
   * @param recipient - Recipient identifier (email, phone, etc.)
   * @param subject - Notification subject
   * @param message - Notification message
   * @param type - Notification type (email, sms, push, etc.)
   */
  send(
    recipient: string,
    subject: string,
    message: string,
    type: string
  ): Promise<boolean>;

  /**
   * Check if notification service is available
   * @returns Promise with availability status
   */
  isAvailable(): Promise<boolean>;

  /**
   * Get notification history
   * @param recipient - Recipient identifier
   * @param limit - Maximum number of records to return
   */
  getHistory(recipient: string, limit?: number): Promise<Array<Record<string, unknown>>>;
}
