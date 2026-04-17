import { config } from 'dotenv';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { z } from 'zod';

// Load .env file
const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '../../.env') });

const envSchema = z.object({
    NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
    PORT: z.coerce.number().int().positive().default(3001),
    HOST: z.string().min(1).default('0.0.0.0'),
    DATABASE_URL: z.string().url('DATABASE_URL must be a valid URL'),
    JWT_SECRET: z.string().min(1, 'JWT_SECRET is required'),
    JWT_EXPIRES_IN: z.string().min(1).default('7d'),
    CORS_ORIGIN: z.string().url('CORS_ORIGIN must be a valid URL').default('http://localhost:3000'),
    LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']).default('info'),
    ENABLE_TELEMETRY: z.enum(['true', 'false']).optional(),
    PROMETHEUS_PORT: z.coerce.number().int().positive().default(9090),
    JAEGER_ENDPOINT: z.string().url('JAEGER_ENDPOINT must be a valid URL').optional(),
    SENTRY_DSN: z.string().url('SENTRY_DSN must be a valid URL').optional(),
});

export type AppConfig = ReturnType<typeof loadAppConfig>;

export function loadAppConfig(env: NodeJS.ProcessEnv = process.env) {
    const parsed = envSchema.safeParse(env);

    if (!parsed.success) {
        const message = parsed.error.issues
            .map(issue => `${issue.path.join('.') || 'env'}: ${issue.message}`)
            .join('; ');
        throw new Error(`Invalid Software-Org management-api environment: ${message}`);
    }

    const configValues = parsed.data;
    const isProduction = configValues.NODE_ENV === 'production';

    if (isProduction && configValues.JWT_SECRET === 'dev-secret-change-in-production') {
        throw new Error('Invalid Software-Org management-api environment: JWT_SECRET must not use the development default in production');
    }

    return {
        env: configValues.NODE_ENV,
        isDevelopment: configValues.NODE_ENV !== 'production',
        isProduction,

        server: {
            port: configValues.PORT,
            host: configValues.HOST,
        },

        database: {
            url: configValues.DATABASE_URL,
        },

        jwt: {
            secret: configValues.JWT_SECRET,
            expiresIn: configValues.JWT_EXPIRES_IN,
        },

        cors: {
            origin: configValues.CORS_ORIGIN,
            credentials: true,
        },

        logging: {
            level: configValues.LOG_LEVEL,
        },

        observability: {
            enableTelemetry: configValues.ENABLE_TELEMETRY === 'true',
            prometheusPort: configValues.PROMETHEUS_PORT,
            jaegerEndpoint: configValues.JAEGER_ENDPOINT,
        },

        sentry: {
            dsn: configValues.SENTRY_DSN,
        },
    };
}

export const appConfig = loadAppConfig();
