import { config } from 'dotenv';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

// Load .env file
const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '../../.env') });

export const appConfig = {
    env: process.env.NODE_ENV || 'development',
    isDevelopment: process.env.NODE_ENV !== 'production',
    isProduction: process.env.NODE_ENV === 'production',

    server: {
        port: parseInt(process.env.PORT || '3001', 10),
        host: process.env.HOST || '0.0.0.0',
    },

    database: {
        url: process.env.DATABASE_URL || 'postgresql://localhost:5432/software_org_dev',
    },

    jwt: {
        secret: process.env.JWT_SECRET || 'dev-secret-change-in-production',
        expiresIn: process.env.JWT_EXPIRES_IN || '7d',
    },

    cors: {
        origin: process.env.CORS_ORIGIN || 'http://localhost:3000',
        credentials: true,
    },

    logging: {
        level: process.env.LOG_LEVEL || 'info',
    },

    observability: {
        enableTelemetry: process.env.ENABLE_TELEMETRY === 'true',
        prometheusPort: parseInt(process.env.PROMETHEUS_PORT || '9090', 10),
        jaegerEndpoint: process.env.JAEGER_ENDPOINT,
    },

    sentry: {
        dsn: process.env.SENTRY_DSN,
    },
};
