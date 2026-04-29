/**
 * Tests for aepRunLineageApi (F-Y009).
 *
 * Covers the data-transform from the raw AEP response shape into the
 * `RunLineageData` expected by <RunLineage>.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchAepRunLineage,
  fetchAepRunLineageRaw,
  type AepRunLineageResponse,
} from '../aepRunLineageApi';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeRawResponse(
  overrides: Partial<AepRunLineageResponse> = {}
): AepRunLineageResponse {
  return {
    runId: 'run-abc123',
    status: 'SUCCEEDED',
    pipelineVersion: '2.4.1',
    agentVersions: [
      { agentId: 'agent-req', agentName: 'RequirementAgent', version: '1.0.0' },
      { agentId: 'agent-code', agentName: 'CodeGenAgent', version: '3.1.0' },
    ],
    policyBundleRef: 'policy-bundle-v7',
    evaluationGate: { passed: true, score: 0.97 },
    aepRunDetailUrl: '/aep/runs/run-abc123',
    ...overrides,
  };
}

function mockFetch(body: unknown, status = 200): void {
  const text = JSON.stringify(body);
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      text: () => Promise.resolve(text),
    })
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
});

// ─── fetchAepRunLineageRaw ────────────────────────────────────────────────────

describe('fetchAepRunLineageRaw', () => {
  it('calls the correct endpoint with credentials included', async () => {
    mockFetch(makeRawResponse());
    await fetchAepRunLineageRaw('run-xyz');
    // eslint-disable-next-line @typescript-eslint/unbound-method
    const fetchMock = vi.mocked(fetch);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/runs/run-xyz/lineage');
    expect(init?.credentials).toBe('include');
  });

  it('encodes special characters in runId', async () => {
    mockFetch(makeRawResponse({ runId: 'run/123' }));
    await fetchAepRunLineageRaw('run/123');
    // eslint-disable-next-line @typescript-eslint/unbound-method
    const fetchMock = vi.mocked(fetch);
    const [url] = fetchMock.mock.calls[0] as [string];
    expect(url).toContain('run%2F123');
  });

  it('returns the parsed JSON body', async () => {
    const raw = makeRawResponse();
    mockFetch(raw);
    const result = await fetchAepRunLineageRaw('run-abc123');
    expect(result.runId).toBe('run-abc123');
    expect(result.pipelineVersion).toBe('2.4.1');
    expect(result.agentVersions).toHaveLength(2);
  });
});

// ─── fetchAepRunLineage (transform) ──────────────────────────────────────────

describe('fetchAepRunLineage', () => {
  it('returns the correct runId', async () => {
    mockFetch(makeRawResponse());
    const data = await fetchAepRunLineage('run-abc123');
    expect(data.runId).toBe('run-abc123');
  });

  it('first node is run with href to AEP detail page', async () => {
    mockFetch(makeRawResponse());
    const { nodes } = await fetchAepRunLineage('run-abc123');
    const runNode = nodes[0];
    expect(runNode?.type).toBe('run');
    expect(runNode?.id).toBe('run-abc123');
    expect(runNode?.label).toMatch(/run-abc/i);
    expect(runNode?.href).toBe('/aep/runs/run-abc123');
  });

  it('second node is pipeline version', async () => {
    mockFetch(makeRawResponse());
    const { nodes } = await fetchAepRunLineage('run-abc123');
    const pipelineNode = nodes[1];
    expect(pipelineNode?.type).toBe('plan');
    expect(pipelineNode?.label).toContain('2.4.1');
  });

  it('third node is policy bundle', async () => {
    mockFetch(makeRawResponse());
    const { nodes } = await fetchAepRunLineage('run-abc123');
    const policyNode = nodes[2];
    expect(policyNode?.type).toBe('step');
    expect(policyNode?.label).toContain('policy-bundle-v7');
  });

  it('agent nodes follow the policy node in input order', async () => {
    mockFetch(makeRawResponse());
    const { nodes } = await fetchAepRunLineage('run-abc123');
    // indices 3 and 4 are the two agents
    expect(nodes[3]?.type).toBe('agent');
    expect(nodes[3]?.id).toBe('agent-req');
    expect(nodes[3]?.label).toContain('RequirementAgent');
    expect(nodes[3]?.label).toContain('1.0.0');
    expect(nodes[3]?.href).toBe('/aep/agents/agent-req');

    expect(nodes[4]?.type).toBe('agent');
    expect(nodes[4]?.id).toBe('agent-code');
    expect(nodes[4]?.label).toContain('3.1.0');
  });

  it('last node is eval gate with pass indicator', async () => {
    mockFetch(makeRawResponse({ evaluationGate: { passed: true, score: 0.9 } }));
    const { nodes } = await fetchAepRunLineage('run-abc123');
    const evalNode = nodes[nodes.length - 1];
    expect(evalNode?.id).toBe('eval-gate');
    expect(evalNode?.label).toContain('✓');
  });

  it('eval gate label shows ✗ on failure', async () => {
    mockFetch(makeRawResponse({ evaluationGate: { passed: false, reason: 'Low score' } }));
    const { nodes } = await fetchAepRunLineage('run-abc123');
    const evalNode = nodes[nodes.length - 1];
    expect(evalNode?.label).toContain('✗');
  });

  it('produces correct total node count for two agents', async () => {
    mockFetch(makeRawResponse());
    const { nodes } = await fetchAepRunLineage('run-abc123');
    // run + pipeline + policy + 2 agents + eval = 6
    expect(nodes).toHaveLength(6);
  });

  it('produces correct total node count for zero agents', async () => {
    mockFetch(makeRawResponse({ agentVersions: [] }));
    const { nodes } = await fetchAepRunLineage('run-abc123');
    // run + pipeline + policy + 0 agents + eval = 4
    expect(nodes).toHaveLength(4);
  });
});
