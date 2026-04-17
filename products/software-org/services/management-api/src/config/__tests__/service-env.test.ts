import { afterEach, describe, expect, it, vi } from 'vitest';

const baseEnv: NodeJS.ProcessEnv = {
    NODE_ENV: 'development',
    LOG_LEVEL: 'info',
    JAVA_SERVICE_URL: 'http://localhost:8080',
    JAVA_API_BASE_URL: 'http://localhost:8080',
    JAVA_API_MOCK: 'false',
    SMTP_HOST: 'smtp.gmail.com',
    SMTP_PORT: '587',
    SMTP_SECURE: 'false',
    SMTP_FROM: 'alerts@ghatana.com',
    SLACK_CHANNEL: '#alerts',
    SLACK_USERNAME: 'Ghatana Alerts',
    SLACK_ICON_EMOJI: ':warning:',
    ENABLE_EMAIL_NOTIFICATIONS: 'false',
    ENABLE_SLACK_NOTIFICATIONS: 'false',
    CRITICAL_THRESHOLD: 'high',
};

describe('loadServiceEnvironment', () => {
    afterEach(() => {
        vi.resetModules();
        vi.unstubAllEnvs();
    });


    async function importConfigModule() {
        for (const [key, value] of Object.entries(baseEnv)) {
            vi.stubEnv(key, value ?? '');
        }
        return import('../service-env.js');
    }

    it('loads typed defaults for service-level environment values', async () => {
        const { loadServiceEnvironment } = await importConfigModule();

        const config = loadServiceEnvironment(baseEnv);

        expect(config.javaServiceUrl).toBe('http://localhost:8080');
        expect(config.useJavaApiMock).toBe(false);
        expect(config.smtp.port).toBe(587);
        expect(config.notifications.emailRecipients).toEqual([]);
    });

    it('rejects invalid Java service URLs', async () => {
        const { loadServiceEnvironment } = await importConfigModule();

        expect(() => loadServiceEnvironment({
            ...baseEnv,
            JAVA_SERVICE_URL: 'not-a-url',
        })).toThrow('JAVA_SERVICE_URL must be a valid URL');
    });

    it('requires email recipients when email notifications are enabled', async () => {
        const { loadServiceEnvironment } = await importConfigModule();

        expect(() => loadServiceEnvironment({
            ...baseEnv,
            ENABLE_EMAIL_NOTIFICATIONS: 'true',
            ALERT_EMAIL_RECIPIENTS: undefined,
        })).toThrow('ALERT_EMAIL_RECIPIENTS is required when ENABLE_EMAIL_NOTIFICATIONS=true');
    });

    it('requires a valid Slack webhook when Slack notifications are enabled', async () => {
        const { loadServiceEnvironment } = await importConfigModule();

        expect(() => loadServiceEnvironment({
            ...baseEnv,
            ENABLE_SLACK_NOTIFICATIONS: 'true',
            SLACK_WEBHOOK_URL: undefined,
        })).toThrow('SLACK_WEBHOOK_URL is required when ENABLE_SLACK_NOTIFICATIONS=true');
    });

    it('parses and validates notification recipients', async () => {
        const { loadServiceEnvironment } = await importConfigModule();

        const config = loadServiceEnvironment({
            ...baseEnv,
            ENABLE_EMAIL_NOTIFICATIONS: 'true',
            ALERT_EMAIL_RECIPIENTS: 'ops@ghatana.com,alerts@ghatana.com',
            ENABLE_SLACK_NOTIFICATIONS: 'true',
            SLACK_WEBHOOK_URL: 'https://hooks.slack.com/services/T000/B000/XXXX',
            JAVA_API_MOCK: 'true',
            CRITICAL_THRESHOLD: 'critical',
        });

        expect(config.useJavaApiMock).toBe(true);
        expect(config.notifications.emailRecipients).toEqual(['ops@ghatana.com', 'alerts@ghatana.com']);
        expect(config.notifications.criticalThreshold).toBe('critical');
    });
});