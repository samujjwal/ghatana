/**
 * Biology Kernel Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test biology simulation kernel execution
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createBiologyKernel } from '../biology-kernel';
import type { SimulationManifest, SimEntity, SimAction, SimulationStep } from '@ghatana/tutorputor-contracts/v1/simulation/types';

/**
 * Helper to create a minimal biology manifest
 */
function createBiologyManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: 'bio-sim-001' as any,
    version: '1.0',
    domain: 'BIOLOGY' as any,
    title: 'Test Biology Simulation',
    description: 'A test biology simulation',
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
      domain: 'BIOLOGY' as any,
      biology: {
        timeScale: 'hours',
        ecosystemType: 'cellular',
      } as any,
    },
    ...overrides,
  };
}

/**
 * Helper to create a cell entity
 */
function createCell(
  id: string,
  options: {
    x?: number;
    y?: number;
    phase?: 'G1' | 'S' | 'G2' | 'M';
    organelles?: string[];
  } = {}
): SimEntity {
  return {
    id,
    type: 'cell',
    label: `Cell ${id}`,
    x: options.x ?? 0,
    y: options.y ?? 0,
    phase: options.phase ?? 'G1',
    organelles: options.organelles ?? ['nucleus', 'mitochondria', 'ribosome'],
    visual: {
      shape: 'circle',
      fill: '#FFEFD5',
      stroke: '#CD853F',
      strokeWidth: 3,
    },
    data: {
      phase: options.phase ?? 'G1',
    },
  } as unknown as SimEntity;
}

/**
 * Helper to create a gene entity
 */
function createGene(
  id: string,
  name: string,
  options: {
    x?: number;
    y?: number;
    expression?: number;
    active?: boolean;
  } = {}
): SimEntity {
  return {
    id,
    type: 'gene',
    label: name,
    x: options.x ?? 0,
    y: options.y ?? 0,
    expression: options.expression ?? 0,
    active: options.active ?? false,
    visual: {
      shape: 'rectangle',
      fill: options.active ? '#48BB78' : '#A0AEC0',
      stroke: '#2D3748',
      strokeWidth: 2,
    },
    data: { name, expression: options.expression ?? 0 },
  } as unknown as SimEntity;
}

/**
 * Helper to create a population entity (for ecology)
 */
function createPopulation(
  id: string,
  species: string,
  count: number,
  options: {
    x?: number;
    y?: number;
    role?: 'producer' | 'consumer' | 'predator' | 'decomposer';
  } = {}
): SimEntity {
  return {
    id,
    type: 'population',
    label: species,
    x: options.x ?? 0,
    y: options.y ?? 0,
    count,
    species,
    role: options.role ?? 'consumer',
    visual: {
      shape: 'circle',
      fill: getRoleColor(options.role ?? 'consumer'),
      size: Math.log(count + 1) * 10,
    },
    data: { species, count, role: options.role },
  } as unknown as SimEntity;
}

function getRoleColor(role: string): string {
  const colors: Record<string, string> = {
    producer: '#48BB78',
    consumer: '#4299E1',
    predator: '#F56565',
    decomposer: '#9F7AEA',
  };
  return colors[role] ?? '#A0AEC0';
}

describe('BiologyKernel', () => {
  let kernel: ReturnType<typeof createBiologyKernel>;

  beforeEach(() => {
    kernel = createBiologyKernel();
  });

  describe('domain identification', () => {
    it('should have BIOLOGY as domain', () => {
      expect(kernel.domain).toBe('BIOLOGY');
    });

    it('should be able to execute BIOLOGY manifests', () => {
      const manifest = createBiologyManifest();
      expect(kernel.canExecute(manifest)).toBe(true);
    });

    it('should not execute non-BIOLOGY manifests', () => {
      const manifest = createBiologyManifest({ domain: 'CHEMISTRY' as any });
      expect(kernel.canExecute(manifest)).toBe(false);
    });
  });

  describe('run()', () => {
    it('should return error for wrong domain', async () => {
      const manifest = createBiologyManifest({ domain: 'PHYSICS' as any });
      const result = await kernel.run({ manifest });

      expect(result.errors).toBeDefined();
      expect(result.errors).toContain('Manifest domain is not BIOLOGY');
      expect(result.keyframes).toHaveLength(0);
    });

    it('should generate initial keyframe with entities', async () => {
      const entities = [
        createCell('cell-1', { x: 100, y: 100, phase: 'G1' }),
        createCell('cell-2', { x: 200, y: 100, phase: 'S' }),
      ];

      const manifest = createBiologyManifest({
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      expect(result.keyframes).toHaveLength(1);
      expect(result.keyframes[0].entities).toHaveLength(2);
    });
  });

  describe('checkHealth()', () => {
    it('should return true for healthy kernel', async () => {
      const isHealthy = await kernel.checkHealth();
      expect(isHealthy).toBe(true);
    });
  });
});

describe('Cell Division Simulation', () => {
  describe('Mitosis', () => {
    it('should simulate cell cycle phases', async () => {
      const kernel = createBiologyKernel();

      const entities = [
        createCell('cell-1', { x: 200, y: 200, phase: 'G1' }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-g1' as any as any,
          orderIndex: 0,

          actions: [
            {
              action: 'SET_PHASE',
              targetId: 'cell-1',
              phase: 'G1',
            } as unknown as SimAction,
            {
              action: 'SCALE',
              targetId: 'cell-1',
              scaleFactor: 1.2,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-s' as any as any,
          orderIndex: 1,

          actions: [
            {
              action: 'SET_PHASE',
              targetId: 'cell-1',
              phase: 'S',
            } as unknown as SimAction,
            {
              action: 'HIGHLIGHT',
              targetIds: ['cell-1'],
              style: 'replicating',
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-g2' as any as any,
          orderIndex: 2,

          actions: [
            {
              action: 'SET_PHASE',
              targetId: 'cell-1',
              phase: 'G2',
            } as unknown as SimAction,
            {
              action: 'SCALE',
              targetId: 'cell-1',
              scaleFactor: 1.3,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-m' as any as any,
          orderIndex: 3,

          actions: [
            {
              action: 'SET_PHASE',
              targetId: 'cell-1',
              phase: 'M',
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-divide' as any as any,
          orderIndex: 4,

          actions: [
            {
              action: 'DIVIDE',
              targetId: 'cell-1',
              newCellId: 'cell-2',
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createBiologyManifest({
        title: 'Cell Mitosis',
        description: 'Cell cycle and division process',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(5);
    });
  });
});

describe('Gene Expression Simulation', () => {
  it('should simulate gene activation and protein synthesis', async () => {
    const kernel = createBiologyKernel();

    const entities = [
      createGene('gene-insulin', 'Insulin Gene', { x: 100, y: 50, active: false }),
      createCell('beta-cell', { x: 200, y: 200 }),
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-activate' as any as any,
        orderIndex: 0,

        actions: [
          {
            action: 'ACTIVATE_GENE',
            targetId: 'gene-insulin',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-transcription' as any as any,
        orderIndex: 1,

        actions: [
          {
            action: 'TRANSCRIBE',
            geneId: 'gene-insulin',
            mrnaId: 'mrna-insulin',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-translation' as any as any,
        orderIndex: 2,

        actions: [
          {
            action: 'TRANSLATE',
            mrnaId: 'mrna-insulin',
            proteinId: 'protein-insulin',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-secretion' as any as any,
        orderIndex: 3,

        actions: [
          {
            action: 'SECRETE',
            cellId: 'beta-cell',
            proteinId: 'protein-insulin',
          } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createBiologyManifest({
      title: 'Insulin Gene Expression',
      description: 'From gene activation to protein secretion',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(4);
  });
});

describe('Predator-Prey Dynamics', () => {
  it('should simulate Lotka-Volterra population dynamics', async () => {
    const kernel = createBiologyKernel();

    const entities = [
      createPopulation('rabbits', 'Rabbit', 100, { x: 100, y: 100, role: 'consumer' }),
      createPopulation('foxes', 'Fox', 20, { x: 300, y: 100, role: 'predator' }),
    ];

    const steps: SimulationStep[] = [];

    // Simulate 10 time steps of population dynamics
    for (let t = 0; t < 10; t++) {
      steps.push({
        id: `step-${t}` as any,
        orderIndex: t,
        actions: [
          {
            action: 'UPDATE_POPULATION',
            targetId: 'rabbits',
            equation: 'lotka-volterra-prey',
          } as unknown as SimAction,
          {
            action: 'UPDATE_POPULATION',
            targetId: 'foxes',
            equation: 'lotka-volterra-predator',
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createBiologyManifest({
      title: 'Predator-Prey Dynamics',
      description: 'Lotka-Volterra model: Rabbits and Foxes',
      initialEntities: entities,
      steps,
      domainMetadata: {
        domain: 'BIOLOGY' as any,
        biology: {
          timeScale: 'months',
          ecosystemType: 'population',
          parameters: {
            preyGrowthRate: 0.1,
            predationRate: 0.01,
            predatorMortalityRate: 0.05,
            predatorReproductionRate: 0.001,
          },
        } as any,
      },
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(10);
  });
});

describe('Photosynthesis Simulation', () => {
  it('should simulate light-dependent reactions', async () => {
    const kernel = createBiologyKernel();

    const entities = [
      { id: 'chloroplast', type: 'organelle', x: 200, y: 200, visual: { fill: '#48BB78' } } as unknown as SimEntity,
      { id: 'photon', type: 'particle', x: 50, y: 200, visual: { fill: '#FFFF00' } } as unknown as SimEntity,
      { id: 'water', type: 'molecule', x: 200, y: 300, visual: { fill: '#4299E1' } } as unknown as SimEntity,
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-light-absorption' as any as any,
        orderIndex: 0,

        actions: [
          { action: 'MOVE', targetId: 'photon', toX: 200, toY: 200 } as unknown as SimAction,
          { action: 'ABSORB', targetId: 'chloroplast', sourceId: 'photon' } as unknown as SimAction,
        ],
      },
      {
        id: 'step-water-split' as any as any,
        orderIndex: 1,

        actions: [
          { action: 'SPLIT', targetId: 'water', products: ['O2', 'H+', 'electrons'] } as unknown as SimAction,
        ],
      },
      {
        id: 'step-electron-transport' as any as any,
        orderIndex: 2,

        actions: [
          { action: 'TRANSPORT', chain: 'ETC', produces: 'ATP' } as unknown as SimAction,
        ],
      },
      {
        id: 'step-nadph' as any as any,
        orderIndex: 3,

        actions: [
          { action: 'PRODUCE', product: 'NADPH' } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createBiologyManifest({
      title: 'Photosynthesis: Light Reactions',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(4);
  });
});

describe('Ecosystem Energy Flow', () => {
  it('should simulate trophic levels and energy transfer', async () => {
    const kernel = createBiologyKernel();

    const entities = [
      createPopulation('producers', 'Plants', 10000, { x: 100, y: 300, role: 'producer' }),
      createPopulation('herbivores', 'Deer', 1000, { x: 200, y: 200, role: 'consumer' }),
      createPopulation('carnivores', 'Wolves', 100, { x: 300, y: 100, role: 'predator' }),
      createPopulation('decomposers', 'Fungi', 5000, { x: 400, y: 300, role: 'decomposer' }),
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-sun-energy' as any as any,
        orderIndex: 0,

        actions: [
          { action: 'ENERGY_INPUT', targetId: 'producers', amount: 100000 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-primary-consumption' as any as any,
        orderIndex: 1,

        actions: [
          { action: 'ENERGY_TRANSFER', fromId: 'producers', toId: 'herbivores', efficiency: 0.1 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-secondary-consumption' as any as any,
        orderIndex: 2,

        actions: [
          { action: 'ENERGY_TRANSFER', fromId: 'herbivores', toId: 'carnivores', efficiency: 0.1 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-decomposition' as any as any,
        orderIndex: 3,

        actions: [
          { action: 'DECOMPOSE', targetId: 'decomposers', recyclesTo: 'producers' } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createBiologyManifest({
      title: 'Ecosystem Energy Flow',
      description: 'Trophic levels and 10% rule of energy transfer',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(4);
  });
});
