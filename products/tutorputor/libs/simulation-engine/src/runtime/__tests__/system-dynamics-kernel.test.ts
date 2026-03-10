/**
 * System Dynamics Kernel Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test system dynamics simulation kernel for economics/stock-flow models
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createSystemDynamicsKernel } from '../system-dynamics-kernel';
import type { SimulationManifest, SimEntity, SimAction, SimulationStep } from '@ghatana/tutorputor-contracts/v1/simulation/types';

/**
 * Helper to create a minimal system dynamics manifest
 */
function createSDManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: 'sd-sim-001' as any,
    version: '1.0',
    domain: 'SYSTEM_DYNAMICS' as any,
    title: 'Test System Dynamics Simulation',
    description: 'A test system dynamics simulation',
    authorId: 'test-user' as any,
    tenantId: 'test-tenant' as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: '1.0',
    initialEntities: [],
    steps: [],
    domainMetadata: {
      domain: 'SYSTEM_DYNAMICS' as any,
      systemDynamics: {
        timeUnit: 'years',
        simulationDuration: 10,
        timeStep: 0.1,
      } as any,
    } as any,
    ...overrides,
  };
}

/**
 * Helper to create a stock entity
 */
function createStock(
  id: string,
  name: string,
  initialValue: number,
  options: { x?: number; y?: number; unit?: string } = {}
): SimEntity {
  return {
    id,
    type: 'stock',
    label: name,
    x: options.x ?? 0,
    y: options.y ?? 0,
    value: initialValue,
    unit: options.unit ?? 'units',
    visual: {
      shape: 'rectangle',
      fill: '#4A90D9',
      stroke: '#2C5282',
      strokeWidth: 2,
    },
    data: { name, value: initialValue },
  } as unknown as SimEntity;
}

/**
 * Helper to create a flow entity
 */
function createFlow(
  id: string,
  name: string,
  rate: number,
  options: {
    x?: number;
    y?: number;
    fromStock?: string;
    toStock?: string;
    unit?: string;
  } = {}
): SimEntity {
  return {
    id,
    type: 'flow',
    label: name,
    x: options.x ?? 0,
    y: options.y ?? 0,
    rate,
    fromStock: options.fromStock,
    toStock: options.toStock,
    unit: options.unit ?? 'units/time',
    visual: {
      shape: 'arrow',
      stroke: '#48BB78',
      strokeWidth: 2,
    },
    data: { name, rate },
  } as unknown as SimEntity;
}

/**
 * Helper to create a variable/auxiliary entity
 */
function createVariable(
  id: string,
  name: string,
  value: number,
  options: { x?: number; y?: number; formula?: string } = {}
): SimEntity {
  return {
    id,
    type: 'variable',
    label: name,
    x: options.x ?? 0,
    y: options.y ?? 0,
    value,
    formula: options.formula,
    visual: {
      shape: 'circle',
      fill: '#9F7AEA',
      stroke: '#6B46C1',
    },
    data: { name, value, formula: options.formula },
  } as unknown as SimEntity;
}

/**
 * Helper to create a feedback loop entity
 */
function createFeedbackLoop(
  id: string,
  type: 'positive' | 'negative',
  elements: string[],
  options: { x?: number; y?: number } = {}
): SimEntity {
  return {
    id,
    type: 'feedback_loop',
    x: options.x ?? 0,
    y: options.y ?? 0,
    loopType: type,
    elements,
    visual: {
      shape: 'loop',
      stroke: type === 'positive' ? '#48BB78' : '#F56565',
    },
    data: { type, elements },
  } as unknown as SimEntity;
}

describe('SystemDynamicsKernel', () => {
  let kernel: ReturnType<typeof createSystemDynamicsKernel>;

  beforeEach(() => {
    kernel = createSystemDynamicsKernel();
  });

  describe('domain identification', () => {
    it('should have SYSTEM_DYNAMICS as domain', () => {
      expect(kernel.domain).toBe('SYSTEM_DYNAMICS');
    });

    it('should be able to execute SYSTEM_DYNAMICS manifests', () => {
      const manifest = createSDManifest();
      expect(kernel.canExecute(manifest)).toBe(true);
    });

    it('should not execute non-SYSTEM_DYNAMICS manifests', () => {
      const manifest = createSDManifest({ domain: 'PHYSICS' as any });
      expect(kernel.canExecute(manifest)).toBe(false);
    });
  });

  describe('run()', () => {
    it('should return error for wrong domain', async () => {
      const manifest = createSDManifest({ domain: 'CHEMISTRY' as any });
      const result = await kernel.run({ manifest });

      expect(result.errors).toBeDefined();
      expect(result.errors).toContain('Manifest domain is not SYSTEM_DYNAMICS');
      expect(result.keyframes).toHaveLength(0);
    });

    it('should generate initial keyframe with entities', async () => {
      const entities = [
        createStock('population', 'Population', 1000),
        createFlow('births', 'Birth Rate', 0.02),
        createFlow('deaths', 'Death Rate', 0.01),
      ];

      const manifest = createSDManifest({
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      expect(result.keyframes).toHaveLength(1);
      expect(result.keyframes[0].entities).toHaveLength(3);
    });
  });

  describe('checkHealth()', () => {
    it('should return true for healthy kernel', async () => {
      const isHealthy = await kernel.checkHealth();
      expect(isHealthy).toBe(true);
    });
  });
});

describe('Population Growth Model', () => {
  it('should simulate exponential growth', async () => {
    const kernel = createSystemDynamicsKernel();

    const entities = [
      createStock('population', 'Population', 100, { x: 200, y: 200 }),
      createFlow('births', 'Births', 0, { x: 100, y: 200, toStock: 'population' }),
      createVariable('birth_rate', 'Birth Rate', 0.1, { x: 100, y: 100 }),
    ];

    const steps: SimulationStep[] = [];

    // Simulate 10 time periods
    for (let t = 0; t < 10; t++) {
      steps.push({
        id: `step-${t}` as any,
        orderIndex: t,
        actions: [
          {
            action: 'CALCULATE_FLOW',
            flowId: 'births',
            formula: 'population * birth_rate',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'population',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createSDManifest({
      title: 'Exponential Population Growth',
      description: 'P(t) = P0 * e^(rt)',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(10);
  });

  it('should simulate logistic growth with carrying capacity', async () => {
    const kernel = createSystemDynamicsKernel();

    const K = 1000; // Carrying capacity
    const entities = [
      createStock('population', 'Population', 100, { x: 200, y: 200 }),
      createFlow('net_growth', 'Net Growth', 0, { x: 100, y: 200, toStock: 'population' }),
      createVariable('growth_rate', 'Intrinsic Growth Rate', 0.2),
      createVariable('carrying_capacity', 'Carrying Capacity', K),
      createFeedbackLoop('limiting', 'negative', ['population', 'net_growth']),
    ];

    const steps: SimulationStep[] = [];

    for (let t = 0; t < 20; t++) {
      steps.push({
        id: `step-${t}` as any,
        orderIndex: t,
        actions: [
          {
            action: 'CALCULATE_FLOW',
            flowId: 'net_growth',
            formula: 'growth_rate * population * (1 - population / carrying_capacity)',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'population',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createSDManifest({
      title: 'Logistic Population Growth',
      description: 'dP/dt = rP(1 - P/K)',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(20);
  });
});

describe('Supply and Demand Model', () => {
  it('should simulate market equilibrium', async () => {
    const kernel = createSystemDynamicsKernel();

    const entities = [
      createStock('price', 'Price', 50, { x: 300, y: 200 }),
      createStock('quantity', 'Quantity', 100, { x: 300, y: 300 }),
      createVariable('demand', 'Demand', 150, { x: 100, y: 200, formula: '200 - 2 * price' }),
      createVariable('supply', 'Supply', 50, { x: 500, y: 200, formula: '-50 + 3 * price' }),
      createFlow('price_adjustment', 'Price Adjustment', 0, { x: 200, y: 200 }),
      createFeedbackLoop('market', 'negative', ['price', 'demand', 'supply', 'price_adjustment']),
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-init' as any,
        orderIndex: 0,

        actions: [
          { action: 'CALCULATE', variableId: 'demand' } as unknown as SimAction,
          { action: 'CALCULATE', variableId: 'supply' } as unknown as SimAction,
        ],
      },
      {
        id: 'step-excess-demand' as any,
        orderIndex: 1,

        actions: [
          {
            action: 'CALCULATE_FLOW',
            flowId: 'price_adjustment',
            formula: '0.1 * (demand - supply)',
          } as unknown as SimAction,
          { action: 'APPLY_FLOWS', stockId: 'price' } as unknown as SimAction,
        ],
      },
      {
        id: 'step-new-equilibrium' as any,
        orderIndex: 2,

        actions: [
          { action: 'CALCULATE', variableId: 'demand' } as unknown as SimAction,
          { action: 'CALCULATE', variableId: 'supply' } as unknown as SimAction,
        ],
      },
      {
        id: 'step-convergence' as any,
        orderIndex: 3,

        actions: [
          { action: 'CHECK_EQUILIBRIUM', tolerance: 1 } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createSDManifest({
      title: 'Supply and Demand Equilibrium',
      description: 'Price adjustment mechanism',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(4);
  });
});

describe('Investment and Capital Model', () => {
  it('should simulate capital accumulation with depreciation', async () => {
    const kernel = createSystemDynamicsKernel();

    const entities = [
      createStock('capital', 'Capital Stock', 1000, { x: 300, y: 200, unit: 'dollars' }),
      createFlow('investment', 'Investment', 100, { x: 150, y: 200, toStock: 'capital' }),
      createFlow('depreciation', 'Depreciation', 0, { x: 450, y: 200, fromStock: 'capital' }),
      createVariable('depreciation_rate', 'Depreciation Rate', 0.05),
      createVariable('savings_rate', 'Savings Rate', 0.2),
      createVariable('output', 'Output', 0, { formula: '2 * sqrt(capital)' }),
    ];

    const steps: SimulationStep[] = [];

    for (let t = 0; t < 20; t++) {
      steps.push({
        id: `step-${t}` as any,
        orderIndex: t,
        actions: [
          {
            action: 'CALCULATE',
            variableId: 'output',
          } as unknown as SimAction,
          {
            action: 'CALCULATE_FLOW',
            flowId: 'investment',
            formula: 'savings_rate * output',
          } as unknown as SimAction,
          {
            action: 'CALCULATE_FLOW',
            flowId: 'depreciation',
            formula: 'depreciation_rate * capital',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'capital',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createSDManifest({
      title: 'Solow Growth Model',
      description: 'Capital accumulation: dK/dt = sY - δK',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(20);
  });
});

describe('Inventory Management Model', () => {
  it('should simulate inventory with orders and shipments', async () => {
    const kernel = createSystemDynamicsKernel();

    const entities = [
      createStock('inventory', 'Inventory', 100, { x: 300, y: 200, unit: 'units' }),
      createStock('backlog', 'Order Backlog', 0, { x: 300, y: 100, unit: 'units' }),
      createFlow('orders', 'Customer Orders', 20, { x: 100, y: 100, toStock: 'backlog' }),
      createFlow('shipments', 'Shipments', 0, { x: 200, y: 150, fromStock: 'backlog' }),
      createFlow('production', 'Production', 0, { x: 400, y: 200, toStock: 'inventory' }),
      createVariable('desired_inventory', 'Desired Inventory', 50),
      createVariable('adjustment_time', 'Adjustment Time', 4),
      createFeedbackLoop('inventory_control', 'negative', ['inventory', 'production', 'shipments']),
    ];

    const steps: SimulationStep[] = [];

    for (let week = 0; week < 12; week++) {
      steps.push({
        id: `week-${week}` as any,
        orderIndex: week,
        actions: [
          {
            action: 'CALCULATE_FLOW',
            flowId: 'shipments',
            formula: 'MIN(backlog, inventory)',
          } as unknown as SimAction,
          {
            action: 'CALCULATE_FLOW',
            flowId: 'production',
            formula: '(desired_inventory - inventory) / adjustment_time + shipments',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'inventory',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'backlog',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createSDManifest({
      title: 'Inventory Management',
      description: 'Beer Distribution Game dynamics',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(12);
  });
});

describe('Climate Carbon Cycle Model', () => {
  it('should simulate carbon flow between atmosphere and ocean', async () => {
    const kernel = createSystemDynamicsKernel();

    const entities = [
      createStock('atm_carbon', 'Atmospheric Carbon', 850, { x: 200, y: 100, unit: 'GtC' }),
      createStock('ocean_carbon', 'Ocean Carbon', 38000, { x: 200, y: 300, unit: 'GtC' }),
      createStock('land_carbon', 'Land Carbon', 2000, { x: 400, y: 200, unit: 'GtC' }),
      createFlow('emissions', 'Fossil Fuel Emissions', 10, { x: 50, y: 100, toStock: 'atm_carbon' }),
      createFlow('ocean_uptake', 'Ocean Uptake', 0, { x: 200, y: 200, fromStock: 'atm_carbon', toStock: 'ocean_carbon' }),
      createFlow('photosynthesis', 'Photosynthesis', 0, { x: 300, y: 150, fromStock: 'atm_carbon', toStock: 'land_carbon' }),
      createFlow('respiration', 'Respiration', 0, { x: 300, y: 250, fromStock: 'land_carbon', toStock: 'atm_carbon' }),
    ];

    const steps: SimulationStep[] = [];

    for (let year = 0; year < 10; year++) {
      steps.push({
        id: `year-${year}` as any,
        orderIndex: year,
        actions: [
          {
            action: 'CALCULATE_FLOW',
            flowId: 'ocean_uptake',
            formula: '0.02 * (atm_carbon - 280)', // Proportional to excess CO2
          } as unknown as SimAction,
          {
            action: 'CALCULATE_FLOW',
            flowId: 'photosynthesis',
            formula: '120', // Approximate annual GPP
          } as unknown as SimAction,
          {
            action: 'CALCULATE_FLOW',
            flowId: 'respiration',
            formula: '118', // Slightly less than photosynthesis
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'atm_carbon',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'ocean_carbon',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'land_carbon',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createSDManifest({
      title: 'Global Carbon Cycle',
      description: 'Simplified carbon exchange model',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(10);
  });
});

describe('Feedback Loop Analysis', () => {
  it('should identify and annotate positive feedback', async () => {
    const kernel = createSystemDynamicsKernel();

    // Positive feedback: More customers -> More word of mouth -> More customers
    const entities = [
      createStock('customers', 'Customers', 10, { x: 200, y: 200 }),
      createFlow('adoption', 'New Adoptions', 0, { x: 100, y: 200, toStock: 'customers' }),
      createVariable('adoption_rate', 'Adoption per Customer', 0.05),
      createFeedbackLoop('viral_growth', 'positive', ['customers', 'adoption']),
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-identify' as any,
        orderIndex: 0,

        actions: [
          {
            action: 'ANALYZE_FEEDBACK',
            loopId: 'viral_growth',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-simulate' as any,
        orderIndex: 1,

        actions: [
          {
            action: 'CALCULATE_FLOW',
            flowId: 'adoption',
            formula: 'adoption_rate * customers * (1000 - customers) / 1000',
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'customers',
          } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createSDManifest({
      title: 'Viral Adoption Model',
      description: 'Positive feedback in product adoption',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(2);
  });

  it('should identify and annotate negative feedback (goal-seeking)', async () => {
    const kernel = createSystemDynamicsKernel();

    // Negative feedback: Gap between actual and goal drives adjustment
    const entities = [
      createStock('temperature', 'Room Temperature', 20, { x: 200, y: 200, unit: '°C' }),
      createFlow('heating', 'Heating', 0, { x: 100, y: 200, toStock: 'temperature' }),
      createVariable('setpoint', 'Thermostat Setting', 22),
      createVariable('gap', 'Temperature Gap', 0, { formula: 'setpoint - temperature' }),
      createFeedbackLoop('thermostat', 'negative', ['temperature', 'gap', 'heating']),
    ];

    const steps: SimulationStep[] = [];

    for (let t = 0; t < 10; t++) {
      steps.push({
        id: `step-${t}` as any,
        orderIndex: t,
        actions: [
          {
            action: 'CALCULATE',
            variableId: 'gap',
          } as unknown as SimAction,
          {
            action: 'CALCULATE_FLOW',
            flowId: 'heating',
            formula: '0.5 * gap', // Proportional control
          } as unknown as SimAction,
          {
            action: 'APPLY_FLOWS',
            stockId: 'temperature',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createSDManifest({
      title: 'Thermostat Control',
      description: 'Goal-seeking behavior with negative feedback',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(10);
  });
});
