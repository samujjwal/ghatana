import { buildApp } from './app.js';

// ── Configuration ──────────────────────────────────────────────────────────────
const PORT = parseInt(process.env['PORT'] ?? '3002', 10);
const AEP_BACKEND_URL = process.env['AEP_BACKEND_URL'] ?? 'http://localhost:8090';
const JWT_SECRET = process.env['JWT_SECRET'];
const ALLOWED_ORIGINS = process.env['ALLOWED_ORIGINS'];
const REQUEST_BODY_LIMIT_BYTES = parseInt(process.env['REQUEST_BODY_LIMIT_BYTES'] ?? '1048576', 10);
const RATE_LIMIT_WINDOW_MS = parseInt(process.env['RATE_LIMIT_WINDOW_MS'] ?? '60000', 10);
const RATE_LIMIT_MAX_REQUESTS = parseInt(process.env['RATE_LIMIT_MAX_REQUESTS'] ?? '300', 10);
const BACKEND_TIMEOUT_MS = parseInt(process.env['BACKEND_TIMEOUT_MS'] ?? '10000', 10);
const BACKEND_RETRY_ATTEMPTS = parseInt(process.env['BACKEND_RETRY_ATTEMPTS'] ?? '3', 10);
const BACKEND_RETRY_INITIAL_BACKOFF_MS = parseInt(
  process.env['BACKEND_RETRY_INITIAL_BACKOFF_MS'] ?? '150',
  10,
);
const BACKEND_RETRY_MAX_BACKOFF_MS = parseInt(
  process.env['BACKEND_RETRY_MAX_BACKOFF_MS'] ?? '2000',
  10,
);
const BACKEND_BREAKER_FAILURE_THRESHOLD = parseInt(
  process.env['BACKEND_BREAKER_FAILURE_THRESHOLD'] ?? '5',
  10,
);
const BACKEND_BREAKER_OPEN_MS = parseInt(process.env['BACKEND_BREAKER_OPEN_MS'] ?? '30000', 10);

if (!JWT_SECRET) {
  console.error('FATAL: JWT_SECRET environment variable must be set');
  process.exit(1);
}

const allowedOrigins: string[] = ALLOWED_ORIGINS
  ? ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : ['http://localhost:5173'];

// ── Start ──────────────────────────────────────────────────────────────────────
const start = async () => {
  try {
    const fastify = await buildApp({
      jwtSecret: JWT_SECRET,
      backendUrl: AEP_BACKEND_URL,
      allowedOrigins,
      logger: true,
      requestBodyLimitBytes: REQUEST_BODY_LIMIT_BYTES,
      rateLimitWindowMs: RATE_LIMIT_WINDOW_MS,
      rateLimitMaxRequests: RATE_LIMIT_MAX_REQUESTS,
      backendTimeoutMs: BACKEND_TIMEOUT_MS,
      backendRetryAttempts: BACKEND_RETRY_ATTEMPTS,
      backendRetryInitialBackoffMs: BACKEND_RETRY_INITIAL_BACKOFF_MS,
      backendRetryMaxBackoffMs: BACKEND_RETRY_MAX_BACKOFF_MS,
      backendBreakerFailureThreshold: BACKEND_BREAKER_FAILURE_THRESHOLD,
      backendBreakerOpenMs: BACKEND_BREAKER_OPEN_MS,
    });
    await fastify.listen({ port: PORT, host: '0.0.0.0' });
  } catch (err) {
    console.error(err);
    process.exit(1);
  }
};

start();