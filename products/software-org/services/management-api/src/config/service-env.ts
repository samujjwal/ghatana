import { config } from 'dotenv';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { z } from 'zod';

const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '../../.env') });

const logLevelSchema = z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']);
const notificationThresholdSchema = z.enum(['critical', 'high', 'medium', 'low']);
const emailAddressSchema = z.string().email('ALERT_EMAIL_RECIPIENTS must contain valid email addresses');

const serviceEnvSchema = z.object({
    NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
    LOG_LEVEL: logLevelSchema.default('info'),
    CONFIG_PATH: z.string().min(1).optional(),
    JAVA_SERVICE_URL: z.string().url('JAVA_SERVICE_URL must be a valid URL').default('http://localhost:8080'),
    JAVA_API_BASE_URL: z.string().url('JAVA_API_BASE_URL must be a valid URL').default('http://localhost:8080'),
    JAVA_API_MOCK: z.enum(['true', 'false']).default('false'),
    SMTP_HOST: z.string().min(1, 'SMTP_HOST must not be empty').default('smtp.gmail.com'),
    SMTP_PORT: z.coerce.number().int().positive('SMTP_PORT must be a positive integer').default(587),
    SMTP_SECURE: z.enum(['true', 'false']).default('false'),
    SMTP_USER: z.string().min(1).optional(),
    SMTP_PASS: z.string().min(1).optional(),
    SMTP_FROM: z.string().min(1, 'SMTP_FROM must not be empty').default('alerts@ghatana.com'),
    SLACK_WEBHOOK_URL: z.string().url('SLACK_WEBHOOK_URL must be a valid URL').optional(),
    SLACK_CHANNEL: z.string().min(1, 'SLACK_CHANNEL must not be empty').default('#alerts'),
    SLACK_USERNAME: z.string().min(1, 'SLACK_USERNAME must not be empty').default('Ghatana Alerts'),
    SLACK_ICON_EMOJI: z.string().min(1, 'SLACK_ICON_EMOJI must not be empty').default(':warning:'),
    ENABLE_EMAIL_NOTIFICATIONS: z.enum(['true', 'false']).default('false'),
    ENABLE_SLACK_NOTIFICATIONS: z.enum(['true', 'false']).default('false'),
    ALERT_EMAIL_RECIPIENTS: z.string().optional(),
    CRITICAL_THRESHOLD: notificationThresholdSchema.default('high'),
}).superRefine((value, ctx) => {
    if (value.ENABLE_EMAIL_NOTIFICATIONS === 'true' && !value.ALERT_EMAIL_RECIPIENTS?.trim()) {
        ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['ALERT_EMAIL_RECIPIENTS'],
            message: 'ALERT_EMAIL_RECIPIENTS is required when ENABLE_EMAIL_NOTIFICATIONS=true',
        });
    }

    if (value.ENABLE_SLACK_NOTIFICATIONS === 'true' && !value.SLACK_WEBHOOK_URL) {
        ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['SLACK_WEBHOOK_URL'],
            message: 'SLACK_WEBHOOK_URL is required when ENABLE_SLACK_NOTIFICATIONS=true',
        });
    }
});

export interface ServiceEnvironment {
    nodeEnv: 'development' | 'production' | 'test';
    isDevelopment: boolean;
    logLevel: z.infer<typeof logLevelSchema>;
    configPath?: string;
    javaServiceUrl: string;
    javaApiBaseUrl: string;
    useJavaApiMock: boolean;
    smtp: {
        host: string;
        port: number;
        secure: boolean;
        auth: {
            user?: string;
            pass?: string;
        };
        from: string;
    };
    slack: {
        webhookUrl?: string;
        channel: string;
        username: string;
        iconEmoji: string;
    };
    notifications: {
        enableEmail: boolean;
        enableSlack: boolean;
        emailRecipients: string[];
        criticalThreshold: z.infer<typeof notificationThresholdSchema>;
    };
}

function parseRecipients(rawRecipients?: string): string[] {
    if (!rawRecipients?.trim()) {
        return [];
    }

    return rawRecipients
        .split(',')
        .map((recipient) => recipient.trim())
        .filter((recipient) => recipient.length > 0)
        .map((recipient) => emailAddressSchema.parse(recipient));
}

export function loadServiceEnvironment(env: NodeJS.ProcessEnv = process.env): ServiceEnvironment {
    const parsed = serviceEnvSchema.safeParse(env);

    if (!parsed.success) {
        const message = parsed.error.issues
            .map((issue) => `${issue.path.join('.') || 'env'}: ${issue.message}`)
            .join('; ');
        throw new Error(`Invalid Software-Org service environment: ${message}`);
    }

    const configValues = parsed.data;

    return {
        nodeEnv: configValues.NODE_ENV,
        isDevelopment: configValues.NODE_ENV === 'development',
        logLevel: configValues.LOG_LEVEL,
        configPath: configValues.CONFIG_PATH,
        javaServiceUrl: configValues.JAVA_SERVICE_URL,
        javaApiBaseUrl: configValues.JAVA_API_BASE_URL,
        useJavaApiMock: configValues.JAVA_API_MOCK === 'true',
        smtp: {
            host: configValues.SMTP_HOST,
            port: configValues.SMTP_PORT,
            secure: configValues.SMTP_SECURE === 'true',
            auth: {
                user: configValues.SMTP_USER,
                pass: configValues.SMTP_PASS,
            },
            from: configValues.SMTP_FROM,
        },
        slack: {
            webhookUrl: configValues.SLACK_WEBHOOK_URL,
            channel: configValues.SLACK_CHANNEL,
            username: configValues.SLACK_USERNAME,
            iconEmoji: configValues.SLACK_ICON_EMOJI,
        },
        notifications: {
            enableEmail: configValues.ENABLE_EMAIL_NOTIFICATIONS === 'true',
            enableSlack: configValues.ENABLE_SLACK_NOTIFICATIONS === 'true',
            emailRecipients: parseRecipients(configValues.ALERT_EMAIL_RECIPIENTS),
            criticalThreshold: configValues.CRITICAL_THRESHOLD,
        },
    };
}