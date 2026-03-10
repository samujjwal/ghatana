/**
 * Chemistry Kernel Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test chemistry simulation kernel execution
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createChemistryKernel } from '../chemistry-kernel';
import type { SimulationManifest, SimEntity, SimAction, SimulationStep } from '@ghatana/tutorputor-contracts/v1/simulation/types';

/**
 * Helper to create a minimal chemistry manifest
 */
function createChemistryManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: 'chem-sim-001' as any,
    version: '1.0',
    domain: 'CHEMISTRY' as any,
    title: 'Test Chemistry Simulation',
    description: 'A test chemistry simulation',
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
      domain: 'CHEMISTRY' as any,
      chemistry: {
        temperature: 298, // Room temperature in Kelvin
        pressure: 1, // atm
      } as any,
    },
    ...overrides,
  };
}

/**
 * Helper to create an atom entity
 */
function createAtom(
  id: string,
  element: string,
  options: {
    x?: number;
    y?: number;
    charge?: number;
  } = {}
): SimEntity {
  return {
    id,
    type: 'atom',
    label: element,
    x: options.x ?? 0,
    y: options.y ?? 0,
    element,
    charge: options.charge ?? 0,
    visual: {
      shape: 'circle',
      fill: getElementColor(element),
      stroke: '#333333',
      strokeWidth: 2,
    },
    data: { element, charge: options.charge ?? 0 },
  } as unknown as SimEntity;
}

/**
 * Helper to create a bond entity
 */
function createBond(
  id: string,
  atom1Id: string,
  atom2Id: string,
  bondOrder: number = 1
): SimEntity {
  return {
    id,
    type: 'bond',
    label: `${atom1Id}-${atom2Id}`,
    x: 0,
    y: 0,
    atom1Id,
    atom2Id,
    bondOrder,
    visual: {
      shape: 'line',
      stroke: '#333333',
      strokeWidth: bondOrder * 2,
    },
    data: { atom1Id, atom2Id, bondOrder },
  } as unknown as SimEntity;
}

/**
 * Helper to create a molecule entity
 */
function createMolecule(
  id: string,
  formula: string,
  options: {
    x?: number;
    y?: number;
    atomIds?: string[];
    bondIds?: string[];
  } = {}
): SimEntity {
  return {
    id,
    type: 'molecule',
    label: formula,
    x: options.x ?? 0,
    y: options.y ?? 0,
    formula,
    atomIds: options.atomIds ?? [],
    bondIds: options.bondIds ?? [],
    visual: {
      shape: 'group',
      stroke: '#666666',
    },
    data: { formula },
  } as unknown as SimEntity;
}

/**
 * Get element color (simplified)
 */
function getElementColor(element: string): string {
  const colors: Record<string, string> = {
    H: '#FFFFFF',
    C: '#909090',
    N: '#3050F8',
    O: '#FF0D0D',
    S: '#FFFF30',
    Cl: '#1FF01F',
    Na: '#AB5CF2',
    Fe: '#E06633',
  };
  return colors[element] ?? '#808080';
}

describe('ChemistryKernel', () => {
  let kernel: ReturnType<typeof createChemistryKernel>;

  beforeEach(() => {
    kernel = createChemistryKernel();
  });

  describe('domain identification', () => {
    it('should have CHEMISTRY as domain', () => {
      expect(kernel.domain).toBe('CHEMISTRY');
    });

    it('should be able to execute CHEMISTRY manifests', () => {
      const manifest = createChemistryManifest();
      expect(kernel.canExecute(manifest)).toBe(true);
    });

    it('should not execute non-CHEMISTRY manifests', () => {
      const manifest = createChemistryManifest({ domain: 'PHYSICS' as any });
      expect(kernel.canExecute(manifest)).toBe(false);
    });
  });

  describe('run()', () => {
    it('should return error for wrong domain', async () => {
      const manifest = createChemistryManifest({ domain: 'BIOLOGY' as any });
      const result = await kernel.run({ manifest });

      expect(result.errors).toBeDefined();
      expect(result.errors).toContain('Manifest domain is not CHEMISTRY');
      expect(result.keyframes).toHaveLength(0);
    });

    it('should generate initial keyframe with entities', async () => {
      // Water molecule: H2O
      const entities = [
        createAtom('H1', 'H', { x: 0, y: -30 }),
        createAtom('O1', 'O', { x: 0, y: 0 }),
        createAtom('H2', 'H', { x: 30, y: 20 }),
        createBond('bond-H1-O1', 'H1', 'O1', 1),
        createBond('bond-O1-H2', 'O1', 'H2', 1),
      ];

      const manifest = createChemistryManifest({
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      expect(result.keyframes).toHaveLength(1);
      expect(result.keyframes[0].stepIndex).toBe(-1);
      expect(result.keyframes[0].entities).toHaveLength(5);
    });
  });

  describe('checkHealth()', () => {
    it('should return true for healthy kernel', async () => {
      const isHealthy = await kernel.checkHealth();
      expect(isHealthy).toBe(true);
    });
  });
});

describe('Chemical Reaction Simulation', () => {
  describe('Bond Breaking and Formation', () => {
    it('should simulate bond breaking', async () => {
      const kernel = createChemistryKernel();

      const entities = [
        createAtom('H1', 'H', { x: 0, y: 0 }),
        createAtom('Cl1', 'Cl', { x: 50, y: 0 }),
        createBond('bond-HCl', 'H1', 'Cl1', 1),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-break-bond' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'BREAK_BOND',
              bondId: 'bond-HCl',
              duration: 1000,
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createChemistryManifest({
        title: 'HCl Dissociation',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(1);
    });

    it('should simulate bond formation', async () => {
      const kernel = createChemistryKernel();

      const entities = [
        createAtom('H1', 'H', { x: 0, y: 0 }),
        createAtom('H2', 'H', { x: 100, y: 0 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-form-bond' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'FORM_BOND',
              atom1Id: 'H1',
              atom2Id: 'H2',
              bondOrder: 1,
              bondId: 'bond-H2',
              duration: 1000,
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createChemistryManifest({
        title: 'H2 Formation',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(1);
    });
  });

  describe('Acid-Base Reactions', () => {
    it('should simulate acid-base neutralization', async () => {
      const kernel = createChemistryKernel();

      // HCl + NaOH -> NaCl + H2O (simplified representation)
      const entities = [
        // HCl
        createAtom('H1', 'H', { x: 0, y: 0 }),
        createAtom('Cl1', 'Cl', { x: 40, y: 0 }),
        createBond('bond-HCl', 'H1', 'Cl1', 1),
        // NaOH
        createAtom('Na1', 'Na', { x: 200, y: 0, charge: 1 }),
        createAtom('O1', 'O', { x: 240, y: 0 }),
        createAtom('H2', 'H', { x: 280, y: 0 }),
        createBond('bond-NaO', 'Na1', 'O1', 1),
        createBond('bond-OH', 'O1', 'H2', 1),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-proton-transfer' as any,
          orderIndex: 0,

          actions: [
            { action: 'BREAK_BOND', bondId: 'bond-HCl' } as unknown as SimAction,
          ],
        },
        {
          id: 'step-form-water' as any,
          orderIndex: 1,

          actions: [
            { action: 'FORM_BOND', atom1Id: 'H1', atom2Id: 'O1', bondOrder: 1 } as unknown as SimAction,
          ],
        },
        {
          id: 'step-form-salt' as any,
          orderIndex: 2,

          actions: [
            { action: 'FORM_BOND', atom1Id: 'Na1', atom2Id: 'Cl1', bondOrder: 1 } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createChemistryManifest({
        title: 'Neutralization: HCl + NaOH',
        description: 'Acid-base neutralization forming salt and water',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(3);
    });
  });

  describe('Oxidation-Reduction', () => {
    it('should simulate electron transfer', async () => {
      const kernel = createChemistryKernel();

      // Simplified: Fe loses electrons, O gains electrons
      const entities = [
        createAtom('Fe1', 'Fe', { x: 0, y: 0, charge: 0 }),
        createAtom('O1', 'O', { x: 100, y: 0, charge: 0 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-oxidation' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'SET_CHARGE',
              targetId: 'Fe1',
              charge: 2,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-reduction' as any,
          orderIndex: 1,

          actions: [
            {
              action: 'SET_CHARGE',
              targetId: 'O1',
              charge: -2,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-ionic-bond' as any,
          orderIndex: 2,

          actions: [
            {
              action: 'FORM_BOND',
              atom1Id: 'Fe1',
              atom2Id: 'O1',
              bondOrder: 2,
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createChemistryManifest({
        title: 'Iron Oxide Formation',
        description: 'Redox reaction forming FeO',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(3);
    });
  });
});

describe('Molecular Structure Visualization', () => {
  describe('Organic molecules', () => {
    it('should render methane (CH4) structure', async () => {
      const kernel = createChemistryKernel();

      // Methane: tetrahedral structure
      const entities = [
        createAtom('C1', 'C', { x: 100, y: 100 }),
        createAtom('H1', 'H', { x: 100, y: 50 }),
        createAtom('H2', 'H', { x: 50, y: 130 }),
        createAtom('H3', 'H', { x: 150, y: 130 }),
        createAtom('H4', 'H', { x: 100, y: 160 }),
        createBond('bond-CH1', 'C1', 'H1', 1),
        createBond('bond-CH2', 'C1', 'H2', 1),
        createBond('bond-CH3', 'C1', 'H3', 1),
        createBond('bond-CH4', 'C1', 'H4', 1),
        createMolecule('methane', 'CH4', {
          x: 100,
          y: 100,
          atomIds: ['C1', 'H1', 'H2', 'H3', 'H4'],
          bondIds: ['bond-CH1', 'bond-CH2', 'bond-CH3', 'bond-CH4'],
        }),
      ];

      const manifest = createChemistryManifest({
        title: 'Methane Structure',
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      expect(result.keyframes[0].entities).toHaveLength(10);

      // Verify methane molecule entity
      const methane = result.keyframes[0].entities.find(e => e.id === 'methane');
      expect(methane).toBeDefined();
      expect(methane?.type).toBe('molecule');
    });

    it('should render double bond in ethene (C2H4)', async () => {
      const kernel = createChemistryKernel();

      const entities = [
        createAtom('C1', 'C', { x: 50, y: 100 }),
        createAtom('C2', 'C', { x: 150, y: 100 }),
        createAtom('H1', 'H', { x: 0, y: 70 }),
        createAtom('H2', 'H', { x: 0, y: 130 }),
        createAtom('H3', 'H', { x: 200, y: 70 }),
        createAtom('H4', 'H', { x: 200, y: 130 }),
        createBond('bond-CC', 'C1', 'C2', 2), // Double bond
        createBond('bond-C1H1', 'C1', 'H1', 1),
        createBond('bond-C1H2', 'C1', 'H2', 1),
        createBond('bond-C2H3', 'C2', 'H3', 1),
        createBond('bond-C2H4', 'C2', 'H4', 1),
      ];

      const manifest = createChemistryManifest({
        title: 'Ethene Structure (C=C double bond)',
        initialEntities: entities,
        steps: [],
      });

      const result = await kernel.run({ manifest });

      const doubleBond = result.keyframes[0].entities.find(e => e.id === 'bond-CC');
      expect(doubleBond).toBeDefined();
      expect((doubleBond as any)?.data?.bondOrder || (doubleBond as any)?.bondOrder).toBe(2);
    });
  });
});

describe('Titration Simulation', () => {
  it('should simulate pH changes during titration', async () => {
    const kernel = createChemistryKernel();

    // Simplified titration: tracking pH indicator color
    const entities = [
      createAtom('solution', 'indicator', { x: 100, y: 100, charge: 0 }),
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-initial' as any,
        orderIndex: 0,

        actions: [
          {
            action: 'SET_VISUAL',
            targetId: 'solution',
            fill: '#FF0000',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-add-base-1' as any,
        orderIndex: 1,

        actions: [
          {
            action: 'SET_VISUAL',
            targetId: 'solution',
            fill: '#FF6600',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-equivalence' as any,
        orderIndex: 2,

        actions: [
          {
            action: 'SET_VISUAL',
            targetId: 'solution',
            fill: '#00FF00',
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-excess-base' as any,
        orderIndex: 3,

        actions: [
          {
            action: 'SET_VISUAL',
            targetId: 'solution',
            fill: '#0000FF',
          } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createChemistryManifest({
      title: 'Acid-Base Titration',
      description: 'pH indicator color change during titration',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(4);
  });
});

describe('Reaction Rate Simulation', () => {
  it('should simulate concentration changes over time', async () => {
    const kernel = createChemistryKernel();

    // A -> B first-order reaction
    const entities = [
      createMolecule('reactant-A', 'A', { x: 50, y: 100 }),
      createMolecule('product-B', 'B', { x: 250, y: 100 }),
    ];

    const steps: SimulationStep[] = [];

    // Simulate 5 time points showing decrease in A, increase in B
    for (let i = 0; i < 5; i++) {
      steps.push({
        id: `step-${i}` as any,
        orderIndex: i,
        actions: [
          {
            action: 'SET_SCALE',
            targetId: 'reactant-A',
            scale: 1 - (i * 0.2), // Shrinking
          } as unknown as SimAction,
          {
            action: 'SET_SCALE',
            targetId: 'product-B',
            scale: 0.2 + (i * 0.2), // Growing
          } as unknown as SimAction,
        ],
      });
    }

    const manifest = createChemistryManifest({
      title: 'First-Order Reaction Kinetics',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(5);
  });
});
