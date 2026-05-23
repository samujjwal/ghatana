/**
 * Polyglot Fixture Node Service
 * 
 * Demonstrates Node.js service surface integration with the Ghatana platform.
 * 
 * @doc.type module
 * @doc.purpose Node.js service surface for polyglot fixture product
 * @doc.layer product
 * @doc.pattern Service
 */

import express from 'express';

const app = express();
const PORT = process.env.PORT || 3004;

app.get('/health', (_req, res) => {
  const response = {
    status: 'UP',
    service: 'node-service',
    version: '1.0.0'
  };
  res.json(response);
});

app.get('/api/ping', (_req, res) => {
  const response = {
    message: 'pong',
    timestamp: Date.now()
  };
  res.json(response);
});

export function startServer() {
  app.listen(PORT, () => {
    console.log(`Node service listening on port ${PORT}`);
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  startServer();
}
