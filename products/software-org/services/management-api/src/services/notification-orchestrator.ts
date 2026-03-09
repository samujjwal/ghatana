/**
 * Notification Orchestrator
 * Coordinates email, Slack, and other notification channels
 */

import type { Alert } from '../../generated/prisma-client/index.js';
import { emailService } from './email-notification.js';
import { slackService } from './slack-integration.js';

export interface NotificationConfig {
    enableEmail: boolean;
    enableSlack: boolean;
    emailRecipients: string[];
    criticalThreshold: 'critical' | 'high' | 'medium' | 'low';
}

export interface NotificationResult {
    email?: {
        success: boolean;
        messageId?: string;
        error?: string;
    };
    slack?: {
        success: boolean;
        message?: string;
    };
    timestamp: string;
}

/**
 * Notification orchestrator class
 */
export class NotificationOrchestrator {
    private config: NotificationConfig;

    constructor(config?: Partial<NotificationConfig>) {
        this.config = {
            enableEmail: process.env.ENABLE_EMAIL_NOTIFICATIONS === 'true',
            enableSlack: process.env.ENABLE_SLACK_NOTIFICATIONS === 'true',
            emailRecipients: process.env.ALERT_EMAIL_RECIPIENTS?.split(',') || [],
            criticalThreshold: (process.env.CRITICAL_THRESHOLD as any) || 'high',
            ...config,
        };
    }

    /**
     * Send notification for a single alert
     */
    async notifyAlert(alert: Alert): Promise<NotificationResult> {
        const result: NotificationResult = {
            timestamp: new Date().toISOString(),
        };

        // Check if alert meets threshold for notification
        if (!this.shouldNotify(alert.severity)) {
            console.log(`[Notification] Alert ${alert.id} below notification threshold`);
            return result;
        }

        // Send email notification
        if (this.config.enableEmail && this.config.emailRecipients.length > 0) {
            try {
                const emailResult = await emailService.sendAlertNotification(
                    alert,
                    this.config.emailRecipients,
                );
                result.email = {
                    success: true,
                    messageId: emailResult.messageId,
                };
                console.log(`[Notification] Email sent for alert ${alert.id}:`, emailResult.messageId);
            } catch (error) {
                result.email = {
                    success: false,
                    error: error instanceof Error ? error.message : 'Unknown error',
                };
                console.error(`[Notification] Email failed for alert ${alert.id}:`, error);
            }
        }

        // Send Slack notification
        if (this.config.enableSlack) {
            try {
                const slackResult =
                    alert.severity === 'critical'
                        ? await slackService.sendCriticalAlert(alert)
                        : await slackService.sendAlertNotification(alert);

                result.slack = {
                    success: slackResult.success,
                    message: slackResult.message,
                };
                console.log(`[Notification] Slack sent for alert ${alert.id}:`, slackResult.success);
            } catch (error) {
                result.slack = {
                    success: false,
                    message: error instanceof Error ? error.message : 'Unknown error',
                };
                console.error(`[Notification] Slack failed for alert ${alert.id}:`, error);
            }
        }

        return result;
    }

    /**
     * Send digest notification for multiple alerts
     */
    async notifyDigest(alerts: Alert[]): Promise<NotificationResult> {
        const result: NotificationResult = {
            timestamp: new Date().toISOString(),
        };

        if (alerts.length === 0) {
            return result;
        }

        // Send email digest
        if (this.config.enableEmail && this.config.emailRecipients.length > 0) {
            try {
                const emailResult = await emailService.sendAlertDigest(alerts, this.config.emailRecipients);
                result.email = {
                    success: true,
                    messageId: emailResult.messageId,
                };
                console.log(`[Notification] Email digest sent:`, emailResult.messageId);
            } catch (error) {
                result.email = {
                    success: false,
                    error: error instanceof Error ? error.message : 'Unknown error',
                };
                console.error(`[Notification] Email digest failed:`, error);
            }
        }

        // Send Slack digest
        if (this.config.enableSlack) {
            try {
                const slackResult = await slackService.sendAlertDigest(alerts);
                result.slack = {
                    success: slackResult.success,
                    message: slackResult.message,
                };
                console.log(`[Notification] Slack digest sent:`, slackResult.success);
            } catch (error) {
                result.slack = {
                    success: false,
                    message: error instanceof Error ? error.message : 'Unknown error',
                };
                console.error(`[Notification] Slack digest failed:`, error);
            }
        }

        return result;
    }

    /**
     * Check if alert severity meets notification threshold
     */
    private shouldNotify(severity: string): boolean {
        const severityLevels = ['low', 'medium', 'high', 'critical'];
        const alertLevel = severityLevels.indexOf(severity);
        const thresholdLevel = severityLevels.indexOf(this.config.criticalThreshold);

        return alertLevel >= thresholdLevel;
    }

    /**
     * Update notification configuration
     */
    updateConfig(config: Partial<NotificationConfig>): void {
        this.config = { ...this.config, ...config };
    }

    /**
     * Get current configuration
     */
    getConfig(): NotificationConfig {
        return { ...this.config };
    }
}

// Singleton instance
export const notificationOrchestrator = new NotificationOrchestrator();
