import nodemailer from 'nodemailer';
import { SESClient, SendEmailCommand } from '@aws-sdk/client-ses';

export type EmailPayload = {
    to: string;
    subject: string;
    body: string;
    html?: string;
    from?: string;
};

export type EmailConfig = {
    provider: 'smtp' | 'ses' | 'stub';
    smtp?: {
        host: string;
        port: number;
        secure: boolean;
        auth?: {
            user: string;
            pass: string;
        };
    };
    ses?: {
        region: string;
        accessKeyId?: string;
        secretAccessKey?: string;
    };
    from: string;
};

// Load config from environment
const emailConfig: EmailConfig = {
    provider: (process.env.EMAIL_PROVIDER as EmailConfig['provider']) || 'stub',
    from: process.env.EMAIL_FROM || 'noreply@flashit.app',
    smtp: process.env.EMAIL_PROVIDER === 'smtp' ? {
        host: process.env.SMTP_HOST || 'localhost',
        port: parseInt(process.env.SMTP_PORT || '587'),
        secure: process.env.SMTP_SECURE === 'true',
        auth: process.env.SMTP_USER && process.env.SMTP_PASS ? {
            user: process.env.SMTP_USER,
            pass: process.env.SMTP_PASS,
        } : undefined,
    } : undefined,
    ses: process.env.EMAIL_PROVIDER === 'ses' ? {
        region: process.env.AWS_REGION || 'us-east-1',
        accessKeyId: process.env.AWS_ACCESS_KEY_ID,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
    } : undefined,
};

// Lazy initialization
let smtpTransporter: nodemailer.Transporter | null = null;
let sesClient: SESClient | null = null;

function getSmtpTransporter(): nodemailer.Transporter {
    if (!smtpTransporter) {
        if (!emailConfig.smtp) {
            throw new Error('SMTP configuration not provided');
        }
        smtpTransporter = nodemailer.createTransport({
            host: emailConfig.smtp.host,
            port: emailConfig.smtp.port,
            secure: emailConfig.smtp.secure,
            auth: emailConfig.smtp.auth,
        });
    }
    return smtpTransporter;
}

function getSesClient(): SESClient {
    if (!sesClient) {
        if (!emailConfig.ses) {
            throw new Error('SES configuration not provided');
        }
        sesClient = new SESClient({
            region: emailConfig.ses.region,
            credentials: emailConfig.ses.accessKeyId && emailConfig.ses.secretAccessKey ? {
                accessKeyId: emailConfig.ses.accessKeyId,
                secretAccessKey: emailConfig.ses.secretAccessKey,
            } : undefined,
        });
    }
    return sesClient;
}

/**
 * Send email using configured provider
 */
export async function sendEmail(payload: EmailPayload): Promise<{ messageId: string }> {
    const from = payload.from || emailConfig.from;

    // Stub provider for development
    if (emailConfig.provider === 'stub') {
        if (process.env.NODE_ENV === 'production') {
            console.error('🚨 CRITICAL WARNING: Using STUB email provider in PRODUCTION environment! Emails will NOT be sent.');
        }
        const messageId = `stub-email-${Date.now()}`;
        console.log(`[email stub] from=${from} to=${payload.to} subject="${payload.subject}" id=${messageId}`);
        return { messageId };
    }

    // SMTP provider
    if (emailConfig.provider === 'smtp') {
        try {
            const transporter = getSmtpTransporter();
            const info = await transporter.sendMail({
                from,
                to: payload.to,
                subject: payload.subject,
                text: payload.body,
                html: payload.html,
            });
            return { messageId: info.messageId };
        } catch (error) {
            console.error('[email smtp] Failed to send email:', error);
            throw new Error(`Failed to send email via SMTP: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    // AWS SES provider
    if (emailConfig.provider === 'ses') {
        try {
            const client = getSesClient();
            const command = new SendEmailCommand({
                Source: from,
                Destination: {
                    ToAddresses: [payload.to],
                },
                Message: {
                    Subject: {
                        Data: payload.subject,
                        Charset: 'UTF-8',
                    },
                    Body: {
                        Text: payload.html ? undefined : {
                            Data: payload.body,
                            Charset: 'UTF-8',
                        },
                        Html: payload.html ? {
                            Data: payload.html,
                            Charset: 'UTF-8',
                        } : undefined,
                    },
                },
            });
            const response = await client.send(command);
            return { messageId: response.MessageId || `ses-${Date.now()}` };
        } catch (error) {
            console.error('[email ses] Failed to send email:', error);
            throw new Error(`Failed to send email via SES: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    throw new Error(`Unknown email provider: ${emailConfig.provider}`);
}

/**
 * Verify email configuration is valid
 */
export async function verifyEmailConfig(): Promise<boolean> {
    if (emailConfig.provider === 'stub') {
        return true;
    }

    if (emailConfig.provider === 'smtp') {
        try {
            const transporter = getSmtpTransporter();
            await transporter.verify();
            return true;
        } catch (error) {
            console.error('[email smtp] Configuration verification failed:', error);
            return false;
        }
    }

    if (emailConfig.provider === 'ses') {
        // SES client will fail on actual send if misconfigured
        return true;
    }

    return false;
}
