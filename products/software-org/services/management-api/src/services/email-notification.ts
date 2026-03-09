/**
 * Email Notification Service
 * Send email alerts for critical events
 */

import type { Alert } from '../../generated/prisma-client/index.js';

export interface EmailConfig {
    host: string;
    port: number;
    secure: boolean;
    auth: {
        user: string;
        pass: string;
    };
    from: string;
}

export interface EmailOptions {
    to: string | string[];
    subject: string;
    html?: string;
    text?: string;
    attachments?: Array<{
        filename: string;
        content: string | Buffer;
    }>;
}

export interface EmailNotificationResult {
    messageId: string;
    accepted: string[];
    rejected: string[];
    response: string;
}

/**
 * Email service class
 */
export class EmailService {
    private config: EmailConfig;

    constructor(config?: Partial<EmailConfig>) {
        this.config = {
            host: process.env.SMTP_HOST || 'smtp.gmail.com',
            port: parseInt(process.env.SMTP_PORT || '587'),
            secure: process.env.SMTP_SECURE === 'true',
            auth: {
                user: process.env.SMTP_USER || '',
                pass: process.env.SMTP_PASS || '',
            },
            from: process.env.SMTP_FROM || 'alerts@ghatana.com',
            ...config,
        };
    }

    /**
     * Send email notification
     */
    async sendEmail(options: EmailOptions): Promise<EmailNotificationResult> {
        // TODO: Implement actual email sending with nodemailer
        // For now, return mock result
        console.log('[Email Service] Would send email:', {
            to: options.to,
            subject: options.subject,
            config: this.config.host,
        });

        return {
            messageId: `<${Date.now()}@ghatana.com>`,
            accepted: Array.isArray(options.to) ? options.to : [options.to],
            rejected: [],
            response: '250 Message accepted',
        };
    }

    /**
     * Send alert notification email
     */
    async sendAlertNotification(alert: Alert, recipients: string[]): Promise<EmailNotificationResult> {
        const severityEmoji = {
            critical: '🔴',
            high: '🟠',
            medium: '🟡',
            low: '🟢',
        };

        const emoji = severityEmoji[alert.severity as keyof typeof severityEmoji] || '⚪';

        const subject = `${emoji} ${alert.severity.toUpperCase()} Alert: ${alert.title}`;

        const html = `
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .alert-box { border-left: 4px solid ${this.getSeverityColor(alert.severity)}; padding: 15px; margin: 20px 0; background: #f9f9f9; }
                    .severity { font-weight: bold; color: ${this.getSeverityColor(alert.severity)}; }
                    .details { margin-top: 15px; }
                    .label { font-weight: bold; color: #666; }
                    .value { margin-left: 10px; }
                    .footer { margin-top: 30px; padding-top: 15px; border-top: 1px solid #ddd; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <h2>${emoji} Alert Notification</h2>
                
                <div class="alert-box">
                    <div class="details">
                        <div><span class="label">Title:</span><span class="value">${alert.title || 'N/A'}</span></div>
                        <div><span class="label">Severity:</span><span class="value severity">${alert.severity.toUpperCase()}</span></div>
                        <div><span class="label">Status:</span><span class="value">${alert.status}</span></div>
                        <div><span class="label">Source:</span><span class="value">${alert.source || 'Unknown'}</span></div>
                        <div><span class="label">Time:</span><span class="value">${new Date(alert.timestamp).toLocaleString()}</span></div>
                    </div>
                    
                    <div style="margin-top: 15px;">
                        <span class="label">Message:</span>
                        <p>${alert.message}</p>
                    </div>
                    
                    ${alert.metadata
                ? `
                        <div style="margin-top: 15px;">
                            <span class="label">Additional Details:</span>
                            <pre style="background: #fff; padding: 10px; border-radius: 4px;">${JSON.stringify(alert.metadata, null, 2)}</pre>
                        </div>
                    `
                : ''
            }
                </div>
                
                <div class="footer">
                    <p>This is an automated alert from Ghatana Monitoring System.</p>
                    <p>Alert ID: ${alert.id}</p>
                </div>
            </body>
            </html>
        `;

        const text = `
${emoji} ALERT NOTIFICATION

Title: ${alert.title}
Severity: ${alert.severity.toUpperCase()}
Status: ${alert.status}
Source: ${alert.source || 'Unknown'}
Time: ${new Date(alert.timestamp).toLocaleString()}

Message:
${alert.message}

${alert.metadata ? `Additional Details:\n${JSON.stringify(alert.metadata, null, 2)}` : ''}

---
Alert ID: ${alert.id}
        `.trim();

        return this.sendEmail({
            to: recipients,
            subject,
            html,
            text,
        });
    }

    /**
     * Send digest email (multiple alerts)
     */
    async sendAlertDigest(alerts: Alert[], recipients: string[]): Promise<EmailNotificationResult> {
        const criticalCount = alerts.filter((a) => a.severity === 'critical').length;
        const highCount = alerts.filter((a) => a.severity === 'high').length;

        const subject = `Alert Digest: ${alerts.length} alerts (${criticalCount} critical, ${highCount} high)`;

        const html = `
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .summary { background: #f0f0f0; padding: 15px; border-radius: 4px; margin: 20px 0; }
                    .alert-item { border-left: 3px solid #ddd; padding: 10px; margin: 10px 0; background: #fafafa; }
                    .critical { border-left-color: #dc3545; }
                    .high { border-left-color: #fd7e14; }
                    .medium { border-left-color: #ffc107; }
                    .low { border-left-color: #28a745; }
                </style>
            </head>
            <body>
                <h2>📊 Alert Digest</h2>
                
                <div class="summary">
                    <h3>Summary</h3>
                    <ul>
                        <li>Total Alerts: ${alerts.length}</li>
                        <li>🔴 Critical: ${criticalCount}</li>
                        <li>🟠 High: ${highCount}</li>
                        <li>🟡 Medium: ${alerts.filter((a) => a.severity === 'medium').length}</li>
                        <li>🟢 Low: ${alerts.filter((a) => a.severity === 'low').length}</li>
                    </ul>
                </div>
                
                <h3>Recent Alerts</h3>
                ${alerts
                .slice(0, 10)
                .map(
                    (alert) => `
                    <div class="alert-item ${alert.severity}">
                        <strong>${alert.title || 'Untitled Alert'}</strong> - ${alert.severity.toUpperCase()}<br>
                        <small>${alert.source || 'Unknown'} • ${new Date(alert.timestamp).toLocaleString()}</small><br>
                        ${alert.message}
                    </div>
                `,
                )
                .join('')}
                
                ${alerts.length > 10 ? `<p><em>And ${alerts.length - 10} more alerts...</em></p>` : ''}
            </body>
            </html>
        `;

        return this.sendEmail({
            to: recipients,
            subject,
            html,
        });
    }

    private getSeverityColor(severity: string): string {
        const colors = {
            critical: '#dc3545',
            high: '#fd7e14',
            medium: '#ffc107',
            low: '#28a745',
        };
        return colors[severity as keyof typeof colors] || '#6c757d';
    }
}

// Singleton instance
export const emailService = new EmailService();
