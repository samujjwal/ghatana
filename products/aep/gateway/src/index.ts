import { buildApp } from './app.js';

// ── Configuration ──────────────────────────────────────────────────────────────
const PORT = parseInt(process.env['PORT'] ?? '3002', 10);
const AEP_BACKEND_URL = process.env['AEP_BACKEND_URL'] ?? 'http://localhost:8090';
const JWT_SECRET = process.env['JWT_SECRET'];
const ALLOWED_ORIGINS = process.env['ALLOWED_ORIGINS'];

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
    });
    await fastify.listen({ port: PORT, host: '0.0.0.0' });
  } catch (err) {
    console.error(err);
    process.exit(1);
  }
};

start();