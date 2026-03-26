/**
 * YappcCore Resolver Tests
 *
 * Tests the YAPPC Core resolver implementation for Query and Mutation handlers.
 * Uses mocked services and Prisma client.
 */

import { YappcCoreResolver } from '../YappcCoreResolver';
import { ConfigService } from '../../../services/ConfigService';
import { DashboardService } from '../../../services/DashboardService';
import { FlowService } from '../../../services/FlowService';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

jest.mock('../../../services/ConfigService');
jest.mock('../../../services/DashboardService');
jest.mock('../../../services/FlowService');

const mockConfigService = ConfigService.getInstance as jest.Mock;
const mockDashboardService = DashboardService.getInstance as jest.Mock;
const mockFlowService = FlowService.getInstance as jest.Mock;

// Test context with user identity
const testContext = {
  userId: 'user-123',
  email: 'user@example.com',
  role: 'EDITOR',
};

// ---------------------------------------------------------------------------
// Query - Personas
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.personas', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns list of personas from config service', async () => {
    const personas = [
      { id: 'p1', name: 'Developer', description: 'Software developer' },
      { id: 'p2', name: 'Product Manager', description: 'Product manager' },
    ];

    const mockConfig = { getPersonas: jest.fn().mockReturnValue(personas) };
    mockConfigService.mockReturnValue(mockConfig);

    const configService = ConfigService.getInstance();
    const result = YappcCoreResolver.Query.personas();

    expect(result).toEqual(personas);
    expect(mockConfig.getPersonas).toHaveBeenCalled();
  });

  it('handles empty personas list', async () => {
    const mockConfig = { getPersonas: jest.fn().mockReturnValue([]) };
    mockConfigService.mockReturnValue(mockConfig);

    const result = YappcCoreResolver.Query.personas();

    expect(result).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Query - Persona by ID
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.persona', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns persona by id', async () => {
    const persona = { id: 'p1', name: 'Developer', description: 'Dev persona' };

    const mockConfig = { getPersona: jest.fn().mockReturnValue(persona) };
    mockConfigService.mockReturnValue(mockConfig);

    const result = YappcCoreResolver.Query.persona(undefined, { id: 'p1' });

    expect(result).toEqual(persona);
    expect(mockConfig.getPersona).toHaveBeenCalledWith('p1');
  });

  it('returns null for non-existent persona', async () => {
    const mockConfig = { getPersona: jest.fn().mockReturnValue(null) };
    mockConfigService.mockReturnValue(mockConfig);

    const result = YappcCoreResolver.Query.persona(undefined, {
      id: 'nonexistent',
    });

    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Query - Domains
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.domains', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns list of domains from config service', async () => {
    const domains = [
      { id: 'd1', name: 'Content', description: 'Content management' },
      { id: 'd2', name: 'Analytics', description: 'Analytics domain' },
    ];

    const mockConfig = { getDomains: jest.fn().mockReturnValue(domains) };
    mockConfigService.mockReturnValue(mockConfig);

    const result = YappcCoreResolver.Query.domains();

    expect(result).toEqual(domains);
  });
});

// ---------------------------------------------------------------------------
// Query - Domain by ID
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.domain', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns domain by id', async () => {
    const domain = {
      id: 'd1',
      name: 'Content',
      description: 'Content management',
    };

    const mockConfig = { getDomain: jest.fn().mockReturnValue(domain) };
    mockConfigService.mockReturnValue(mockConfig);

    const result = YappcCoreResolver.Query.domain(undefined, { id: 'd1' });

    expect(result).toEqual(domain);
  });
});

// ---------------------------------------------------------------------------
// Query - Dashboards
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.dashboard', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns dashboard by id', async () => {
    const dashboard = {
      id: 'dash-1',
      domainId: 'd1',
      name: 'Analytics Dashboard',
      widgets: [],
    };

    const mockDashboard = {
      getDashboard: jest.fn().mockResolvedValue(dashboard),
    };
    mockDashboardService.mockReturnValue(mockDashboard);

    const result = await YappcCoreResolver.Query.dashboard(undefined, {
      id: 'dash-1',
    });

    expect(result).toEqual(dashboard);
    expect(mockDashboard.getDashboard).toHaveBeenCalledWith('dash-1');
  });

  it('returns null when dashboard not found', async () => {
    const mockDashboard = { getDashboard: jest.fn().mockResolvedValue(null) };
    mockDashboardService.mockReturnValue(mockDashboard);

    const result = await YappcCoreResolver.Query.dashboard(undefined, {
      id: 'nonexistent',
    });

    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Query - Dashboards by Domain
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.dashboards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns all dashboards when no domain filter provided', async () => {
    const dashboards = [
      { id: 'dash-1', domainId: 'd1', name: 'Dashboard 1' },
      { id: 'dash-2', domainId: 'd2', name: 'Dashboard 2' },
    ];

    const mockDashboard = {
      getDashboards: jest.fn().mockResolvedValue(dashboards),
    };
    mockDashboardService.mockReturnValue(mockDashboard);

    const result = await YappcCoreResolver.Query.dashboards(undefined, {});

    expect(result).toEqual(dashboards);
    expect(mockDashboard.getDashboards).toHaveBeenCalled();
  });

  it('returns dashboards for specific domain', async () => {
    const dashboards = [{ id: 'dash-1', domainId: 'd1', name: 'Dashboard 1' }];

    const mockDashboard = {
      getDashboardsByDomain: jest.fn().mockResolvedValue(dashboards),
    };
    mockDashboardService.mockReturnValue(mockDashboard);

    const result = await YappcCoreResolver.Query.dashboards(undefined, {
      domainId: 'd1',
    });

    expect(result).toEqual(dashboards);
    expect(mockDashboard.getDashboardsByDomain).toHaveBeenCalledWith('d1');
  });
});

// ---------------------------------------------------------------------------
// Query - Flow State
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.flowState', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns flow state with available transitions and active tasks', async () => {
    const flow = { id: 'flow-1', state: 'PENDING', instanceId: 'instance-1' };
    const transitions = ['APPROVE', 'REJECT'];
    const tasks = [
      { id: 'task-1', name: 'Review', status: 'ACTIVE' },
      { id: 'task-2', name: 'Validate', status: 'PENDING' },
    ];

    const mockFlow = {
      getFlow: jest.fn().mockResolvedValue(flow),
      getAvailableTransitions: jest.fn().mockResolvedValue(transitions),
      getActiveTasks: jest.fn().mockResolvedValue(tasks),
    };
    mockFlowService.mockReturnValue(mockFlow);

    const result = await YappcCoreResolver.Query.flowState(undefined, {
      instanceId: 'instance-1',
    });

    expect(result).toMatchObject({
      id: 'flow-1',
      availableTransitions: transitions,
      activeTasks: tasks,
    });
  });

  it('returns null when flow not found', async () => {
    const mockFlow = {
      getFlow: jest.fn().mockResolvedValue(null),
    };
    mockFlowService.mockReturnValue(mockFlow);

    const result = await YappcCoreResolver.Query.flowState(undefined, {
      instanceId: 'nonexistent',
    });

    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Query - Policies
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.policies', () => {
  it('returns list of policies', () => {
    const result = YappcCoreResolver.Query.policies();

    expect(result).toBeInstanceOf(Array);
    expect(result.length).toBeGreaterThan(0);
    expect(result[0]).toHaveProperty('id');
    expect(result[0]).toHaveProperty('name');
    expect(result[0]).toHaveProperty('enabled');
  });

  it('includes prod-deploy-approval policy', () => {
    const result = YappcCoreResolver.Query.policies();
    const prodPolicy = result.find((p) => p.id === 'prod-deploy-approval');

    expect(prodPolicy).toBeDefined();
    expect(prodPolicy?.approval.required).toBe(true);
    expect(prodPolicy?.approval.minApprovers).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// Query - Policy by ID
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.policy', () => {
  it('returns policy by id', () => {
    const result = YappcCoreResolver.Query.policy(undefined, {
      id: 'prod-deploy-approval',
    });

    expect(result).toBeDefined();
    expect(result?.id).toBe('prod-deploy-approval');
    expect(result?.approval.required).toBe(true);
  });

  it('returns null for non-existent policy', () => {
    const result = YappcCoreResolver.Query.policy(undefined, {
      id: 'nonexistent',
    });

    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Query - Flow Definitions
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.flowDefinitions', () => {
  it('returns array of flow definitions', () => {
    const result = YappcCoreResolver.Query.flowDefinitions();

    expect(result).toBeInstanceOf(Array);
    expect(result.length).toBeGreaterThan(0);

    result.forEach((flow) => {
      expect(flow).toHaveProperty('id');
      expect(flow).toHaveProperty('initialState');
      expect(flow).toHaveProperty('states');
    });
  });

  it('each flow has valid structure', () => {
    const result = YappcCoreResolver.Query.flowDefinitions();

    result.forEach((flow) => {
      expect(typeof flow.id).toBe('string');
      expect(typeof flow.initialState).toBe('string');
      expect(flow.states).toBeDefined();
    });
  });
});

// ---------------------------------------------------------------------------
// Query - Flow Instances
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Query.flowInstances', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns flow instances', async () => {
    const instances = [
      { id: 'instance-1', flowId: 'flow-1', state: 'ACTIVE' },
      { id: 'instance-2', flowId: 'flow-1', state: 'COMPLETED' },
    ];

    const mockFlow = {
      getFlowInstances: jest.fn().mockResolvedValue(instances),
    };
    mockFlowService.mockReturnValue(mockFlow);

    const result = await YappcCoreResolver.Query.flowInstances(undefined, {
      flowId: 'flow-1',
    });

    expect(result).toEqual(instances);
  });
});

// ---------------------------------------------------------------------------
// Mutation - Create Flow Instance
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Mutation.createFlowInstance', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('creates and returns flow instance', async () => {
    const newInstance = {
      id: 'instance-new',
      flowId: 'flow-1',
      state: 'INITIATED',
      context: {},
    };

    const mockFlow = {
      createFlowInstance: jest.fn().mockResolvedValue(newInstance),
    };
    mockFlowService.mockReturnValue(mockFlow);

    const result = await YappcCoreResolver.Mutation.createFlowInstance(
      undefined,
      { flowId: 'flow-1', context: {} },
      testContext
    );

    expect(result).toEqual(newInstance);
    expect(mockFlow.createFlowInstance).toHaveBeenCalledWith(
      'flow-1',
      {},
      'user-123'
    );
  });

  it('throws when userId missing', async () => {
    const noUserContext = { email: 'test@example.com' };

    expect(() => {
      YappcCoreResolver.Mutation.createFlowInstance(
        undefined,
        { flowId: 'flow-1', context: {} },
        noUserContext as any
      );
    }).toThrow('no userId in resolver context');
  });
});

// ---------------------------------------------------------------------------
// Mutation - Transition Flow Instance
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Mutation.transitionFlowInstance', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('transitions flow instance to new state', async () => {
    const transitionedInstance = {
      id: 'instance-1',
      flowId: 'flow-1',
      state: 'APPROVED',
    };

    const mockFlow = {
      transitionFlow: jest.fn().mockResolvedValue(transitionedInstance),
    };
    mockFlowService.mockReturnValue(mockFlow);

    const result = await YappcCoreResolver.Mutation.transitionFlowInstance(
      undefined,
      { instanceId: 'instance-1', action: 'APPROVE' },
      testContext
    );

    expect(result.state).toBe('APPROVED');
    expect(mockFlow.transitionFlow).toHaveBeenCalledWith(
      'instance-1',
      'APPROVE',
      'user-123'
    );
  });

  it('throws when userId missing', async () => {
    const noUserContext = { email: 'test@example.com' };

    expect(() => {
      YappcCoreResolver.Mutation.transitionFlowInstance(
        undefined,
        { instanceId: 'instance-1', action: 'APPROVE' },
        noUserContext as any
      );
    }).toThrow('no userId in resolver context');
  });
});

// ---------------------------------------------------------------------------
// Mutation - Create Policy
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Mutation.createPolicy', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('creates and returns new policy', async () => {
    const newPolicy = {
      id: 'policy-new',
      name: 'Custom Policy',
      appliesTo: { domains: ['d1'] },
      enabled: true,
    };

    const mockConfig = {
      createPolicy: jest.fn().mockResolvedValue(newPolicy),
    };
    mockConfigService.mockReturnValue(mockConfig);

    const result = await YappcCoreResolver.Mutation.createPolicy(
      undefined,
      {
        name: 'Custom Policy',
        appliesTo: { domains: ['d1'] },
      },
      testContext
    );

    expect(result).toEqual(newPolicy);
  });

  it('throws when userId missing', async () => {
    const noUserContext = { email: 'test@example.com' };

    expect(() => {
      YappcCoreResolver.Mutation.createPolicy(
        undefined,
        { name: 'Policy', appliesTo: {} },
        noUserContext as any
      );
    }).toThrow('no userId in resolver context');
  });
});

// ---------------------------------------------------------------------------
// Mutation - Update Policy
// ---------------------------------------------------------------------------

describe('YappcCoreResolver.Mutation.updatePolicy', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('updates and returns policy', async () => {
    const updatedPolicy = {
      id: 'policy-1',
      name: 'Updated Policy',
      enabled: false,
    };

    const mockConfig = {
      updatePolicy: jest.fn().mockResolvedValue(updatedPolicy),
    };
    mockConfigService.mockReturnValue(mockConfig);

    const result = await YappcCoreResolver.Mutation.updatePolicy(
      undefined,
      { id: 'policy-1', name: 'Updated Policy', enabled: false },
      testContext
    );

    expect(result).toEqual(updatedPolicy);
  });

  it('throws when userId missing', async () => {
    const noUserContext = { email: 'test@example.com' };

    expect(() => {
      YappcCoreResolver.Mutation.updatePolicy(
        undefined,
        { id: 'policy-1' },
        noUserContext as any
      );
    }).toThrow('no userId in resolver context');
  });
});
