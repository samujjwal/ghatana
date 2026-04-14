/**
 * @group contract
 * @tier C
 *
 * Contract tests for YAPPC event schemas.
 *
 * Validates that every JSON Schema in the event-schemas directory:
 *   1. Loads without errors and has required structural fields ($schema, $id).
 *   2. Accepts well-formed golden payloads (positive cases).
 *   3. Rejects clearly invalid payloads (negative cases).
 *
 * These tests use AJV (draft-07) to validate against the canonical schemas.
 * They run in every CI pass and act as a schema compatibility gate — a
 * breaking change to a schema (e.g. adding a new required field without a
 * migration) will fail this suite.
 *
 * @doc.type test-suite
 * @doc.purpose Event schema contract validation for YAPPC event bus
 * @doc.layer application
 * @doc.pattern Contract Test
 */
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import { describe, it, expect, beforeAll } from 'vitest';
import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname, resolve, basename } from 'path';
import { readdirSync } from 'fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCHEMAS_DIR = resolve(__dirname, '../');

const require = createRequire(import.meta.url);

// ─── AJV Setup ────────────────────────────────────────────────────────────────

const ajv = new Ajv({ strict: false, allErrors: true });
addFormats(ajv);

// ─── Load all schemas ─────────────────────────────────────────────────────────

const schemaFiles = readdirSync(SCHEMAS_DIR).filter((f) =>
  f.endsWith('.json'),
);

const schemas = Object.fromEntries(
  schemaFiles.map((file) => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires, @typescript-eslint/no-explicit-any
    const schema: any = require(resolve(SCHEMAS_DIR, file));
    return [file, schema];
  }),
);

// ─── Helpers ─────────────────────────────────────────────────────────────────

function validate(schemaFile: string, payload: unknown): { valid: boolean; errors: string } {
  const schema = schemas[schemaFile];
  if (!schema) throw new Error(`Schema not found: ${schemaFile}`);
  const validateFn = ajv.compile(schema);
  const valid = validateFn(payload) as boolean;
  const errors = ajv.errorsText(validateFn.errors ?? null);
  return { valid, errors };
}

const NOW = new Date().toISOString();
const UUID = '00000000-0000-4000-8000-000000000001';

// ─── Structural requirements ──────────────────────────────────────────────────

describe('Event schemas — structural requirements', () => {
  it.each(schemaFiles)(
    '%s has a $schema field pointing to draft-07',
    (file) => {
      const schema = schemas[file] as Record<string, unknown>;
      expect(typeof schema.$schema).toBe('string');
      expect(schema.$schema).toContain('draft-07');
    },
  );

  it.each(schemaFiles)(
    '%s has a $id field',
    (file) => {
      const schema = schemas[file] as Record<string, unknown>;
      expect(typeof schema.$id).toBe('string');
      expect((schema.$id as string).length).toBeGreaterThan(0);
    },
  );

  it.each(schemaFiles)(
    '%s compiles without AJV errors',
    (file) => {
      const schema = schemas[file] as Record<string, unknown>;
      expect(() => ajv.compile(schema)).not.toThrow();
    },
  );
});

// ─── agent-dispatch-v1 ────────────────────────────────────────────────────────

describe('agent-dispatch-v1.json', () => {
  const FILE = 'agent-dispatch-v1.json';

  it('accepts a valid dispatch event', () => {
    const { valid, errors } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.dispatch.requested',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      context: { stage: 'execute' },
    });
    expect(valid, errors).toBe(true);
  });

  it('rejects when eventId is missing', () => {
    const { valid } = validate(FILE, {
      eventType: 'agent.dispatch.requested',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      context: {},
    });
    expect(valid).toBe(false);
  });

  it('rejects when agentId does not match the required pattern', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.dispatch.requested',
      timestamp: NOW,
      agentId: 'bad-agent-id',
      context: {},
    });
    expect(valid).toBe(false);
  });

  it('rejects when eventType does not match the const', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.dispatch.other',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      context: {},
    });
    expect(valid).toBe(false);
  });

  it('rejects when timestamp is not a date-time string', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.dispatch.requested',
      timestamp: 'not-a-date',
      agentId: 'agent.yappc.code-review',
      context: {},
    });
    expect(valid).toBe(false);
  });

  it('rejects when context is missing', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.dispatch.requested',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
    });
    expect(valid).toBe(false);
  });
});

// ─── agent-result-v1 ─────────────────────────────────────────────────────────

describe('agent-result-v1.json', () => {
  const FILE = 'agent-result-v1.json';

  it('accepts a valid SUCCESS result event', () => {
    const { valid, errors } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.result.produced',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      status: 'SUCCESS',
      executionTimeMs: 1234,
    });
    expect(valid, errors).toBe(true);
  });

  it('accepts all valid status enum values', () => {
    for (const status of ['SUCCESS', 'FAILURE', 'TIMEOUT', 'CANCELLED'] as const) {
      const { valid, errors } = validate(FILE, {
        eventId: UUID,
        eventType: 'agent.result.produced',
        timestamp: NOW,
        agentId: 'agent.yappc.code-review',
        status,
        executionTimeMs: 0,
      });
      expect(valid, `${status}: ${errors}`).toBe(true);
    }
  });

  it('rejects an invalid status value', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.result.produced',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      status: 'PENDING',
      executionTimeMs: 0,
    });
    expect(valid).toBe(false);
  });

  it('rejects a negative executionTimeMs', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.result.produced',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      status: 'SUCCESS',
      executionTimeMs: -1,
    });
    expect(valid).toBe(false);
  });

  it('rejects when executionTimeMs is missing', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'agent.result.produced',
      timestamp: NOW,
      agentId: 'agent.yappc.code-review',
      status: 'SUCCESS',
    });
    expect(valid).toBe(false);
  });
});

// ─── task-status-changed-v1 ───────────────────────────────────────────────────

describe('task-status-changed-v1.json', () => {
  const FILE = 'task-status-changed-v1.json';

  const VALID_STATUS = 'IN_PROGRESS';

  it('accepts a valid task status changed event', () => {
    const { valid, errors } = validate(FILE, {
      eventId: UUID,
      eventType: 'task.status.changed',
      timestamp: NOW,
      taskId: UUID,
      projectId: UUID,
      previousStatus: 'PENDING',
      newStatus: VALID_STATUS,
    });
    expect(valid, errors).toBe(true);
  });

  it('accepts all valid status enum values for previousStatus and newStatus', () => {
    const statuses = ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'];
    for (const status of statuses) {
      const { valid, errors } = validate(FILE, {
        eventId: UUID,
        eventType: 'task.status.changed',
        timestamp: NOW,
        taskId: UUID,
        projectId: UUID,
        previousStatus: status,
        newStatus: status,
      });
      expect(valid, `status=${status}: ${errors}`).toBe(true);
    }
  });

  it('rejects an invalid previousStatus value', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'task.status.changed',
      timestamp: NOW,
      taskId: UUID,
      projectId: UUID,
      previousStatus: 'UNKNOWN',
      newStatus: 'IN_PROGRESS',
    });
    expect(valid).toBe(false);
  });

  it('rejects when taskId is missing', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'task.status.changed',
      timestamp: NOW,
      projectId: UUID,
      previousStatus: 'PENDING',
      newStatus: 'IN_PROGRESS',
    });
    expect(valid).toBe(false);
  });
});

// ─── shape-created-v1 ─────────────────────────────────────────────────────────

describe('shape-created-v1.json', () => {
  const FILE = 'shape-created-v1.json';

  it('accepts a valid shape-created event', () => {
    const { valid, errors } = validate(FILE, {
      eventId: UUID,
      eventType: 'shape.created',
      timestamp: NOW,
      shape: {
        id: UUID,
        type: 'rectangle',
        position: { x: 100, y: 200 },
      },
    });
    expect(valid, errors).toBe(true);
  });

  it('rejects when shape.type is an unknown value', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'shape.created',
      timestamp: NOW,
      shape: {
        id: UUID,
        type: 'triangle', // not in enum
        position: { x: 0, y: 0 },
      },
    });
    expect(valid).toBe(false);
  });

  it('rejects when shape.position is missing', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'shape.created',
      timestamp: NOW,
      shape: { id: UUID, type: 'rectangle' },
    });
    expect(valid).toBe(false);
  });
});

// ─── phase-transition-v1 ──────────────────────────────────────────────────────

describe('phase-transition-v1.json', () => {
  const FILE = 'phase-transition-v1.json';

  it('accepts a valid phase-transition event', () => {
    const { valid, errors } = validate(FILE, {
      eventId: UUID,
      eventType: 'phase.transition',
      timestamp: NOW,
      projectId: UUID,
      fromStage: 'plan',
      toStage: 'execute',
      triggerEvent: 'user.approved',
    });
    expect(valid, errors).toBe(true);
  });

  it('rejects when fromStage is not a valid lifecycle stage', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'phase.transition',
      timestamp: NOW,
      projectId: UUID,
      fromStage: 'unknown-stage',
      toStage: 'execute',
      triggerEvent: 'user.approved',
    });
    expect(valid).toBe(false);
  });

  it('rejects when triggerEvent is missing', () => {
    const { valid } = validate(FILE, {
      eventId: UUID,
      eventType: 'phase.transition',
      timestamp: NOW,
      projectId: UUID,
      fromStage: 'plan',
      toStage: 'execute',
    });
    expect(valid).toBe(false);
  });
});

// ─── Compatibility: all schemas have required metadata ────────────────────────

describe('Schema compatibility contract', () => {
  it.each(schemaFiles)(
    '%s has a version field',
    (file) => {
      const schema = schemas[file] as Record<string, unknown>;
      expect(schema).toHaveProperty('version');
    },
  );

  it.each(schemaFiles)(
    '%s $id points to the ghatana.ai schema registry',
    (file) => {
      const schema = schemas[file] as Record<string, unknown>;
      expect(schema.$id as string).toContain('ghatana.ai');
    },
  );

  it.each(schemaFiles)(
    '%s defines required fields at the top level',
    (file) => {
      const schema = schemas[file] as Record<string, string[]>;
      expect(Array.isArray(schema.required)).toBe(true);
      expect(schema.required).toContain('eventId');
      expect(schema.required).toContain('eventType');
      expect(schema.required).toContain('timestamp');
    },
  );
});
