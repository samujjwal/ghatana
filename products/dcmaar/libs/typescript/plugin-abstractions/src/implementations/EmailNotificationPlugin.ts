/**
 * Email Notification Plugin Implementation
 * Provides email notification delivery
 */

import { INotification } from '../interfaces/Notification';

export class EmailNotificationPlugin implements INotification {
  id = 'email-notification-plugin';
  name = 'Email Notification Plugin';
  version = '1.0.0';
  description = 'Sends notifications via email';
  enabled = true;
  metadata = {};
  private emailService: string = 'sendgrid'; // Could be configured

  constructor() {
    // No-op for now
  }

  async initialize(): Promise<void> {
    console.log('EmailNotificationPlugin initialized');
  }

  async shutdown(): Promise<void> {
    console.log('EmailNotificationPlugin shutdown');
  }

  async execute(command: string, params?: Record<string, unknown>): Promise<unknown> {
    if (command === 'send' && params) {
      return this.send(
        params.recipient as string,
        params.subject as string,
        params.message as string,
        params.type as string
      );
    }
    throw new Error(`Unknown command: ${command}`);
  }

  async send(
    recipient: string,
    subject: string,
    message: string,
    type: string
  ): Promise<boolean> {
    if (type !== 'email') {
      console.warn(`EmailNotificationPlugin: Ignoring non-email type "${type}"`);
      return false;
    }

    try {
      // Validate email format
      if (!this.isValidEmail(recipient)) {
        throw new Error(`Invalid email address: ${recipient}`);
      }

      // In a real implementation, this would call an email service API
      console.log(`[${this.emailService}] Sending email to ${recipient}`);
      console.log(`Subject: ${subject}`);
      console.log(`Message: ${message}`);

      // Simulate email sending
      await new Promise((resolve) => setTimeout(resolve, 100));

      return true;
    } catch (error) {
      console.error('Error sending email:', error);
      return false;
    }
  }

  async isAvailable(): Promise<boolean> {
    // Check if email service is configured and accessible
    return true; // Simulated availability
  }

  async getHistory(recipient: string, limit: number = 10): Promise<Array<Record<string, unknown>>> {
    // In a real implementation, this would fetch from a database
    console.log(`Fetching last ${limit} emails for ${recipient}`);
    return [
      {
        recipient,
        subject: 'Sample email',
        sentAt: new Date().toISOString(),
        status: 'delivered',
      },
    ];
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}
