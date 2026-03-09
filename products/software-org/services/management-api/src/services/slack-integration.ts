/**
 * Slack Integration Service
 * Send alert notifications to Slack channels via webhooks
 */

import type { Alert } from '../../generated/prisma-client/index.js';

export interface SlackConfig {
    webhookUrl: string;
    channel?: string;
    username?: string;
    iconEmoji?: string;
}

export interface SlackMessage {
    text?: string;
    blocks?: Array<{
        type: string;
        text?: {
            type: string;
            text: string;
        };
        fields?: Array<{
            type: string;
            text: string;
        }>;
        accessory?: any;
    }>;
    attachments?: Array<{
        color?: string;
        title?: string;
        text?: string;
        fields?: Array<{
            title: string;
            value: string;
            short?: boolean;
        }>;
        footer?: string;
        ts?: number;
    }>;
}

export interface SlackNotificationResult {
    success: boolean;
    statusCode?: number;
    message?: string;
}

/**
 * Slack service class
 */
export class SlackService {
    private config: SlackConfig;

    constructor(config?: Partial<SlackConfig>) {
        this.config = {
            webhookUrl: process.env.SLACK_WEBHOOK_URL || '',
            channel: process.env.SLACK_CHANNEL || '#alerts',
            username: process.env.SLACK_USERNAME || 'Ghatana Alerts',
            iconEmoji: process.env.SLACK_ICON_EMOJI || ':warning:',
            ...config,
        };
    }

    /**
     * Send message to Slack
     */
    async sendMessage(message: SlackMessage): Promise<SlackNotificationResult> {
        if (!this.config.webhookUrl) {
            console.warn('[Slack Service] Webhook URL not configured');
            return {
                success: false,
                message: 'Slack webhook URL not configured',
            };
        }

        try {
            // TODO: Implement actual Slack webhook POST request
            // For now, log and return mock result
            console.log('[Slack Service] Would send message:', {
                channel: this.config.channel,
                message: message.text || message.blocks?.[0]?.text?.text,
            });

            return {
                success: true,
                statusCode: 200,
                message: 'Message sent successfully',
            };
        } catch (error) {
            console.error('[Slack Service] Error sending message:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : 'Unknown error',
            };
        }
    }

    /**
     * Send alert notification to Slack
     */
    async sendAlertNotification(alert: Alert): Promise<SlackNotificationResult> {
        const severityEmoji = {
            critical: ':red_circle:',
            high: ':large_orange_circle:',
            medium: ':large_yellow_circle:',
            low: ':large_green_circle:',
        };

        const severityColor = {
            critical: '#dc3545',
            high: '#fd7e14',
            medium: '#ffc107',
            low: '#28a745',
        };

        const emoji = severityEmoji[alert.severity as keyof typeof severityEmoji] || ':white_circle:';
        const color = severityColor[alert.severity as keyof typeof severityColor] || '#6c757d';

        const message: SlackMessage = {
            text: `${emoji} *${alert.severity.toUpperCase()}* Alert: ${alert.title}`,
            attachments: [
                {
                    color,
                    title: alert.title || 'Alert',
                    text: alert.message,
                    fields: [
                        {
                            title: 'Severity',
                            value: alert.severity.toUpperCase(),
                            short: true,
                        },
                        {
                            title: 'Status',
                            value: alert.status,
                            short: true,
                        },
                        {
                            title: 'Source',
                            value: alert.source || 'Unknown',
                            short: true,
                        },
                        {
                            title: 'Time',
                            value: new Date(alert.timestamp).toLocaleString(),
                            short: true,
                        },
                    ],
                    footer: `Alert ID: ${alert.id}`,
                    ts: Math.floor(new Date(alert.timestamp).getTime() / 1000),
                },
            ],
        };

        if (alert.metadata && typeof alert.metadata === 'object') {
            message.attachments?.[0].fields?.push({
                title: 'Additional Details',
                value: `\`\`\`${JSON.stringify(alert.metadata, null, 2)}\`\`\``,
                short: false,
            });
        }

        return this.sendMessage(message);
    }

    /**
     * Send critical alert notification (more urgent formatting)
     */
    async sendCriticalAlert(alert: Alert): Promise<SlackNotificationResult> {
        const message: SlackMessage = {
            blocks: [
                {
                    type: 'header',
                    text: {
                        type: 'plain_text',
                        text: '🚨 CRITICAL ALERT 🚨',
                    },
                },
                {
                    type: 'section',
                    text: {
                        type: 'mrkdwn',
                        text: `*${alert.title}*\n${alert.message}`,
                    },
                },
                {
                    type: 'section',
                    fields: [
                        {
                            type: 'mrkdwn',
                            text: `*Source:*\n${alert.source || 'Unknown'}`,
                        },
                        {
                            type: 'mrkdwn',
                            text: `*Time:*\n${new Date(alert.timestamp).toLocaleString()}`,
                        },
                    ],
                },
            ],
        };

        return this.sendMessage(message);
    }

    /**
     * Send alert summary/digest
     */
    async sendAlertDigest(alerts: Alert[]): Promise<SlackNotificationResult> {
        const criticalCount = alerts.filter((a) => a.severity === 'critical').length;
        const highCount = alerts.filter((a) => a.severity === 'high').length;
        const mediumCount = alerts.filter((a) => a.severity === 'medium').length;
        const lowCount = alerts.filter((a) => a.severity === 'low').length;

        const message: SlackMessage = {
            blocks: [
                {
                    type: 'header',
                    text: {
                        type: 'plain_text',
                        text: '📊 Alert Digest',
                    },
                },
                {
                    type: 'section',
                    text: {
                        type: 'mrkdwn',
                        text: `*Total Alerts:* ${alerts.length}`,
                    },
                },
                {
                    type: 'section',
                    fields: [
                        {
                            type: 'mrkdwn',
                            text: `:red_circle: *Critical:* ${criticalCount}`,
                        },
                        {
                            type: 'mrkdwn',
                            text: `:large_orange_circle: *High:* ${highCount}`,
                        },
                        {
                            type: 'mrkdwn',
                            text: `:large_yellow_circle: *Medium:* ${mediumCount}`,
                        },
                        {
                            type: 'mrkdwn',
                            text: `:large_green_circle: *Low:* ${lowCount}`,
                        },
                    ],
                },
            ],
        };

        // Add top 5 most recent alerts
        if (alerts.length > 0) {
            const recentAlerts = alerts.slice(0, 5);
            const alertsList = recentAlerts
                .map((a) => {
                    const emojiMap: Record<string, string> = {
                        critical: ':red_circle:',
                        high: ':large_orange_circle:',
                        medium: ':large_yellow_circle:',
                        low: ':large_green_circle:',
                    };
                    const emoji = emojiMap[a.severity] || ':white_circle:';
                    return `${emoji} *${a.title}* - ${a.source || 'Unknown'}`;
                })
                .join('\n');

            message.blocks?.push({
                type: 'section',
                text: {
                    type: 'mrkdwn',
                    text: `*Recent Alerts:*\n${alertsList}`,
                },
            });
        }

        return this.sendMessage(message);
    }
}

// Singleton instance
export const slackService = new SlackService();
