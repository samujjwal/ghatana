import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import {
  AGENT_REGISTRY_BOUNDARY_MESSAGE,
  agentRegistryService,
} from '../../api/agent-registry.service';

describe('agentRegistryService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('maps the launcher agent catalog to AgentDefinition entries', async () => {
    mockApiClient.get.mockResolvedValueOnce([
      {
        id: 'catalog-1',
        name: 'Catalog Agent',
        description: 'Launcher-exposed agent',
        version: '1.0.0',
        status: 'ACTIVE',
        capabilities: [],
      },
    ]);

    const agents = await agentRegistryService.listAgents();

    expect(mockApiClient.get).toHaveBeenCalledWith('/agents/catalog', { params: {} });
    expect(agents[0]).toMatchObject({
      agentId: 'catalog-1',
      name: 'Catalog Agent',
      status: 'ACTIVE',
    });
  });

  it('fails explicitly for unsupported registry mutations and live event streaming', async () => {
    await expect(
      agentRegistryService.registerAgent({ name: 'A', description: '', version: '1.0.0', capabilities: [] }),
    ).rejects.toThrow(AGENT_REGISTRY_BOUNDARY_MESSAGE);

    await expect(agentRegistryService.deregisterAgent('agent-1')).rejects.toThrow(
      AGENT_REGISTRY_BOUNDARY_MESSAGE,
    );

    expect(() => agentRegistryService.streamRegistryEvents(undefined, () => undefined)).toThrow(
      AGENT_REGISTRY_BOUNDARY_MESSAGE,
    );
  });
});