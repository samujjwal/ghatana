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

  it('maps a single launcher agent catalog entry from the detail route', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      id: 'catalog-2',
      name: 'Detail Agent',
      description: 'Launcher-exposed detail payload',
      version: '2.0.0',
      tenantId: 'tenant-a',
      status: 'REGISTERING',
      capabilities: [
        {
          id: 'cap-1',
          name: 'Search',
          description: 'Searches catalog data',
          version: '1.0.0',
        },
      ],
      registeredAt: '2026-04-15T10:00:00Z',
      updatedAt: '2026-04-15T10:05:00Z',
    });

    const agent = await agentRegistryService.getAgent('catalog-2');

    expect(mockApiClient.get).toHaveBeenCalledWith('/agents/catalog/catalog-2');
    expect(agent).toMatchObject({
      agentId: 'catalog-2',
      name: 'Detail Agent',
      status: 'REGISTERING',
      tenantId: 'tenant-a',
    });
    expect(agent.capabilities).toHaveLength(1);
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