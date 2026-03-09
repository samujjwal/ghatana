/**
 * @fileoverview HTTP Ingest Sink Preset
 *
 * Standard configuration for HTTP ingestion endpoints.
 */

import type { SinkPreset } from '../types';

/**
 * Standard HTTP ingest preset
 *
 * Balanced configuration for HTTP ingestion.
 * Suitable for most API ingestion scenarios.
 */
export const httpIngestStandardPreset: SinkPreset = {
  id: 'http-ingest-standard',
  name: 'Standard HTTP Ingest',
  description: 'Balanced HTTP ingestion with batching',
  config: {
    type: 'http',
    url: '${INGEST_URL}',
    method: 'POST',
    batchSize: 50,
    flushIntervalMs: 5000,
    timeout: 10000,
    retries: 3,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ${AUTH_TOKEN}',
    },
  },
  tags: ['http', 'ingest', 'standard'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Batch HTTP ingest preset
 *
 * Optimized for high-throughput with large batches.
 * Suitable for bulk data ingestion.
 */
export const httpIngestBatchPreset: SinkPreset = {
  id: 'http-ingest-batch',
  name: 'Batch HTTP Ingest',
  description: 'Optimized batching for high-throughput',
  config: {
    type: 'http',
    url: '${INGEST_URL}',
    method: 'POST',
    batchSize: 200,
    flushIntervalMs: 10000,
    timeout: 30000,
    retries: 5,
    compression: true,
    headers: {
      'Content-Type': 'application/json',
      'Content-Encoding': 'gzip',
      'Authorization': 'Bearer ${AUTH_TOKEN}',
    },
  },
  tags: ['http', 'ingest', 'batch', 'high-throughput'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Secure HTTP ingest preset
 *
 * Enhanced security with TLS and authentication.
 * Suitable for production environments.
 */
export const httpIngestSecurePreset: SinkPreset = {
  id: 'http-ingest-secure',
  name: 'Secure HTTP Ingest',
  description: 'Enhanced security with TLS and authentication',
  config: {
    type: 'http',
    url: 'https://${INGEST_HOST}/api/v1/ingest',
    method: 'POST',
    batchSize: 50,
    flushIntervalMs: 5000,
    timeout: 15000,
    retries: 3,
    tls: {
      rejectUnauthorized: true,
      minVersion: 'TLSv1.3',
    },
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ${AUTH_TOKEN}',
      'X-Client-ID': '${CLIENT_ID}',
      'X-Request-ID': '${REQUEST_ID}',
    },
  },
  tags: ['http', 'ingest', 'secure', 'production'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
