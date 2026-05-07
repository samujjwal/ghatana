/**
 * Pipeline Builder UI — Unit & Integration Tests.
 *
 * Tests cover:
 * 1. Domain types & palette constants
 * 2. Jotai store atoms (initial values, derived atoms)
 * 3. API client export function
 * 4. Node-to-spec conversion logic
 *
 * @doc.type test
 * @doc.purpose Validate pipeline builder types, store, and utilities
 * @doc.layer frontend
 */
import { describe, it, expect } from 'vitest';
import { createStore } from 'jotai';

import {
  STAGE_PALETTE,
  CONNECTOR_PALETTE,
  type PipelineSpec,
  type PipelineStageSpec,
  type AgentSpec,
  type StageKind,
  type ConnectorDirection,
  type PaletteItem,
  type IOSpec,
} from '@/types/pipeline.types';

import {
  pipelineAtom,
  nodesAtom,
  edgesAtom,
  selectedNodeIdAtom,
  selectedNodeAtom,
  stageCountAtom,
  totalAgentCountAtom,
  isDirtyAtom,
  pipelineStatusAtom,
  isValidAtom,
  validationAtom,
  canUndoAtom,
  canRedoAtom,
  historyAtom,
  historyIndexAtom,
} from '@/stores/pipeline.store';

import { exportPipelineSpec } from '@/api/pipeline.api';

// ─────────────────────────────────────────────────────────────────────
// 1. Domain Types & Palette Constants
// ─────────────────────────────────────────────────────────────────────

describe('Pipeline Domain Types', () => {
  it('STAGE_PALETTE contains all 7 stage kinds', () => {
    expect(STAGE_PALETTE).toHaveLength(7);
    const kinds = STAGE_PALETTE.map((p) => p.kind);
    expect(kinds).toContain('ingestion');
    expect(kinds).toContain('validation');
    expect(kinds).toContain('transformation');
    expect(kinds).toContain('enrichment');
    expect(kinds).toContain('analysis');
    expect(kinds).toContain('persistence');
    expect(kinds).toContain('custom');
  });

  it('each stage palette item has required fields', () => {
    for (const item of STAGE_PALETTE) {
      expect(item.id).toBeTruthy();
      expect(item.label).toBeTruthy();
      expect(item.icon).toBeTruthy();
      expect(item.description).toBeTruthy();
    }
  });

  it('non-custom stages have default agents', () => {
    const nonCustom = STAGE_PALETTE.filter((p) => p.kind !== 'custom');
    for (const item of nonCustom) {
      expect(item.defaultAgents).toBeDefined();
      expect(item.defaultAgents!.length).toBeGreaterThan(0);
    }
  });

  it('custom stage has empty default agents', () => {
    const custom = STAGE_PALETTE.find((p) => p.kind === 'custom');
    expect(custom).toBeDefined();
    expect(custom!.defaultAgents).toEqual([]);
  });

  it('CONNECTOR_PALETTE contains 5 connectors', () => {
    expect(CONNECTOR_PALETTE).toHaveLength(5);
  });

  it('connectors have valid directions', () => {
    const validDirs: ConnectorDirection[] = ['INGRESS', 'EGRESS', 'BIDIRECTIONAL'];
    for (const conn of CONNECTOR_PALETTE) {
      expect(validDirs).toContain(conn.direction);
    }
  });

  it('INGRESS and EGRESS connectors both exist', () => {
    const dirs = CONNECTOR_PALETTE.map((c) => c.direction);
    expect(dirs).toContain('INGRESS');
    expect(dirs).toContain('EGRESS');
  });

  it('AgentSpec shape is correct', () => {
    const agent: AgentSpec = {
      id: 'a1',
      agent: 'TestAgent',
      role: 'validator',
      inputsSpec: [{ name: 'input', format: 'json' }],
      outputsSpec: [{ name: 'output', format: 'json' }],
      config: { threshold: 5 },
    };
    expect(agent.id).toBe('a1');
    expect(agent.agent).toBe('TestAgent');
    expect(agent.inputsSpec).toHaveLength(1);
  });

  it('PipelineSpec can be constructed', () => {
    const spec: PipelineSpec = {
      name: 'test-pipeline',
      stages: [
        {
          name: 'ingestion',
          workflow: [{ id: 'a1', agent: 'IngestAgent' }],
        },
      ],
    };
    expect(spec.name).toBe('test-pipeline');
    expect(spec.stages).toHaveLength(1);
    expect(spec.stages[0].workflow[0].agent).toBe('IngestAgent');
  });

  it('IOSpec supports optional fields', () => {
    const io: IOSpec = { name: 'events', format: 'avro', schema: 'v1', description: 'Event stream' };
    expect(io.name).toBe('events');
    expect(io.schema).toBe('v1');
  });
});

// ─────────────────────────────────────────────────────────────────────
// 2. Jotai Store Atoms
// ─────────────────────────────────────────────────────────────────────

describe('Pipeline Store', () => {
  it('pipelineAtom starts with default values', () => {
    const store = createStore();
    const pipeline = store.get(pipelineAtom);
    expect(pipeline.name).toBe('Untitled Pipeline');
    expect(pipeline.stages).toEqual([]);
    expect(pipeline.status).toBe('DRAFT');
    expect(pipeline.version).toBe(1);
  });

  it('nodesAtom starts empty', () => {
    const store = createStore();
    expect(store.get(nodesAtom)).toEqual([]);
  });

  it('edgesAtom starts empty', () => {
    const store = createStore();
    expect(store.get(edgesAtom)).toEqual([]);
  });

  it('isDirtyAtom starts false', () => {
    const store = createStore();
    expect(store.get(isDirtyAtom)).toBe(false);
  });

  it('pipelineStatusAtom starts as DRAFT', () => {
    const store = createStore();
    expect(store.get(pipelineStatusAtom)).toBe('DRAFT');
  });

  it('selectedNodeAtom is null when no node selected', () => {
    const store = createStore();
    expect(store.get(selectedNodeAtom)).toBeNull();
  });

  it('selectedNodeAtom resolves to matching node', () => {
    const store = createStore();
    const testNode = {
      id: 'node-1',
      type: 'stage' as const,
      position: { x: 0, y: 0 },
      data: { label: 'Test', kind: 'ingestion' as StageKind, agents: [], agentCount: 0 },
    };
    store.set(nodesAtom, [testNode] as any);
    store.set(selectedNodeIdAtom, 'node-1');
    const selected = store.get(selectedNodeAtom);
    expect(selected).not.toBeNull();
    expect(selected!.id).toBe('node-1');
  });

  it('selectedNodeAtom returns null for non-existent node', () => {
    const store = createStore();
    store.set(selectedNodeIdAtom, 'does-not-exist');
    expect(store.get(selectedNodeAtom)).toBeNull();
  });

  it('stageCountAtom counts stage nodes only', () => {
    const store = createStore();
    store.set(nodesAtom, [
      { id: '1', type: 'stage', position: { x: 0, y: 0 }, data: { agentCount: 1 } },
      { id: '2', type: 'connector', position: { x: 100, y: 0 }, data: {} },
      { id: '3', type: 'stage', position: { x: 200, y: 0 }, data: { agentCount: 2 } },
    ] as any);
    expect(store.get(stageCountAtom)).toBe(2);
  });

  it('totalAgentCountAtom sums agents across stages', () => {
    const store = createStore();
    store.set(nodesAtom, [
      { id: '1', type: 'stage', position: { x: 0, y: 0 }, data: { agentCount: 3 } },
      { id: '2', type: 'stage', position: { x: 100, y: 0 }, data: { agentCount: 2 } },
      { id: '3', type: 'connector', position: { x: 200, y: 0 }, data: {} },
    ] as any);
    expect(store.get(totalAgentCountAtom)).toBe(5);
  });

  it('isValidAtom reflects validation result', () => {
    const store = createStore();
    expect(store.get(isValidAtom)).toBe(false); // null validation → false
    store.set(validationAtom, { isValid: true, errors: [], warnings: [] });
    expect(store.get(isValidAtom)).toBe(true);
    store.set(validationAtom, { isValid: false, errors: [{ code: 'E1', message: 'err', severity: 'error' }], warnings: [] });
    expect(store.get(isValidAtom)).toBe(false);
  });

  it('canUndoAtom and canRedoAtom track history', () => {
    const store = createStore();
    expect(store.get(canUndoAtom)).toBe(false);
    expect(store.get(canRedoAtom)).toBe(false);

    store.set(historyAtom, [
      { nodes: [], edges: [] },
      { nodes: [], edges: [] },
      { nodes: [], edges: [] },
    ]);
    store.set(historyIndexAtom, 1);
    expect(store.get(canUndoAtom)).toBe(true); // index > 0
    expect(store.get(canRedoAtom)).toBe(true); // index < length - 1

    store.set(historyIndexAtom, 0);
    expect(store.get(canUndoAtom)).toBe(false);
    expect(store.get(canRedoAtom)).toBe(true);

    store.set(historyIndexAtom, 2);
    expect(store.get(canUndoAtom)).toBe(true);
    expect(store.get(canRedoAtom)).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────
// 3. API Client — Export
// ─────────────────────────────────────────────────────────────────────

describe('Pipeline API — exportPipelineSpec', () => {
  it('produces valid JSON from PipelineSpec', () => {
    const spec: PipelineSpec = {
      name: 'export-test',
      stages: [
        {
          name: 'stage-1',
          workflow: [
            { id: 'a1', agent: 'AgentA', role: 'ingester' },
            { id: 'a2', agent: 'AgentB' },
          ],
        },
      ],
    };
    const json = exportPipelineSpec(spec);
    const parsed = JSON.parse(json);
    expect(parsed.stages).toHaveLength(1);
    expect(parsed.stages[0].name).toBe('stage-1');
    expect(parsed.stages[0].workflow).toHaveLength(2);
  });

  it('omits undefined optional fields', () => {
    const spec: PipelineSpec = {
      name: 'minimal',
      stages: [
        {
          name: 's1',
          workflow: [{ id: 'x', agent: 'X' }],
        },
      ],
    };
    const json = exportPipelineSpec(spec);
    const parsed = JSON.parse(json);
    expect(parsed.stages[0]).not.toHaveProperty('connectorIds');
    expect(parsed.stages[0]).not.toHaveProperty('connectors');
    expect(parsed.stages[0].workflow[0]).not.toHaveProperty('inputsSpec');
  });

  it('includes connectors and I/O specs when present', () => {
    const spec: PipelineSpec = {
      name: 'full',
      stages: [
        {
          name: 's1',
          workflow: [
            {
              id: 'a1',
              agent: 'Agent',
              inputsSpec: [{ name: 'in', format: 'json' }],
              outputsSpec: [{ name: 'out', format: 'avro' }],
              config: { batchSize: 100 },
            },
          ],
          connectorIds: ['kafka-1'],
          connectors: [
            { id: 'kafka-1', type: 'kafka', direction: 'INGRESS', encoding: 'json' },
          ],
        },
      ],
    };
    const parsed = JSON.parse(exportPipelineSpec(spec));
    expect(parsed.stages[0].connectorIds).toEqual(['kafka-1']);
    expect(parsed.stages[0].connectors[0].type).toBe('kafka');
    expect(parsed.stages[0].workflow[0].inputsSpec[0].name).toBe('in');
    expect(parsed.stages[0].workflow[0].config.batchSize).toBe(100);
  });

  it('handles empty pipeline', () => {
    const spec: PipelineSpec = { name: 'empty', stages: [] };
    const parsed = JSON.parse(exportPipelineSpec(spec));
    expect(parsed.stages).toEqual([]);
  });

  it('handles multi-stage pipeline', () => {
    const spec: PipelineSpec = {
      name: 'multi',
      stages: [
        { name: 'ingest', workflow: [{ id: 'a1', agent: 'A' }] },
        { name: 'validate', workflow: [{ id: 'a2', agent: 'B' }] },
        { name: 'persist', workflow: [{ id: 'a3', agent: 'C' }, { id: 'a4', agent: 'D' }] },
      ],
    };
    const parsed = JSON.parse(exportPipelineSpec(spec));
    expect(parsed.stages).toHaveLength(3);
    expect(parsed.stages[2].workflow).toHaveLength(2);
  });
});

// ─────────────────────────────────────────────────────────────────────
// 4. Palette & Model Integrity
// ─────────────────────────────────────────────────────────────────────

describe('Palette Integrity', () => {
  it('all palette IDs are unique', () => {
    const stageIds = STAGE_PALETTE.map((s) => s.id);
    expect(new Set(stageIds).size).toBe(stageIds.length);

    const connIds = CONNECTOR_PALETTE.map((c) => c.id);
    expect(new Set(connIds).size).toBe(connIds.length);
  });

  it('default agents have unique IDs within their stage', () => {
    for (const item of STAGE_PALETTE) {
      if (item.defaultAgents) {
        const agentIds = item.defaultAgents.map((a) => a.id);
        expect(new Set(agentIds).size).toBe(agentIds.length);
      }
    }
  });

  it('connector palette items match known types', () => {
    const knownTypes = ['kafka', 'http', 'jdbc', 'datacloud'];
    for (const conn of CONNECTOR_PALETTE) {
      expect(knownTypes).toContain(conn.type);
    }
  });
});
