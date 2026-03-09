import { validator } from '@/services/validation';
import eventSchema from '@/schemas/EventEnvelope.schema.json';
import requestSchema from '@/schemas/IngestRequest.schema.json';

describe('Schema Validation', () => {
  describe('EventEnvelope', () => {
    it('should validate a complete event envelope', () => {
      const validEvent = {
        meta: {
          tenant_id: 'test-tenant',
          device_id: 'test-device',
          session_id: 'test-session',
          timestamp: Date.now(),
          schema_version: '1.0.0'
        },
        events: [{
          event: {
            id: 'test-id',
            type: 'EVENT_TYPE_USER',
            source: 'test-source',
            timestamp: { seconds: Date.now() / 1000, nanos: 0 }
          },
          schema_version: '1.0.0',
          idempotency_key: 'test-key',
          source_os: 'linux',
          source_arch: 'x64'
        }]
      };
      
      const result = validator.validateEvent(validEvent);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject invalid event envelopes', () => {
      const invalidEvent = {
        meta: {},
        events: [{
          event: {}
        }]
      };
      
      const result = validator.validateEvent(invalidEvent);
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });

  describe('IngestRequest', () => {
    it('should validate a complete ingest request', () => {
      const validRequest = {
        batch: {
          envelopes: [{
            meta: {
              tenant_id: 'test-tenant',
              device_id: 'test-device',
              session_id: 'test-session',
              timestamp: Date.now(),
              schema_version: '1.0.0'
            },
            events: []
          }]
        },
        schema_version: '1.0.0',
        idempotency_key: 'test-key',
        source_os: 'linux',
        source_arch: 'x64'
      };
      
      const result = validator.validateIngestRequest(validRequest);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });
  });
});
