/**
 * Medicine Kernel Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test medicine simulation kernel execution
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createMedicineKernel } from '../medicine-kernel';
import type { SimulationManifest, SimEntity, SimAction, SimulationStep } from '@ghatana/tutorputor-contracts/v1/simulation/types';

/**
 * Helper to create a minimal medicine manifest
 */
function createMedicineManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
  return {
    id: 'med-sim-001' as any,
    version: '1.0',
    domain: 'MEDICINE' as any,
    title: 'Test Medicine Simulation',
    description: 'A test medicine simulation',
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
      domain: 'MEDICINE' as any,
      medicine: {
        patientAge: 35,
        patientWeight: 70, // kg
        timeUnit: 'hours',
      } as any,
    },
    ...overrides,
  };
}

/**
 * Helper to create a drug entity
 */
function createDrug(
  id: string,
  name: string,
  options: {
    x?: number;
    y?: number;
    dose?: number;
    halfLife?: number;
    bioavailability?: number;
    route?: 'oral' | 'iv' | 'im' | 'sc';
  } = {}
): SimEntity {
  return {
    id,
    type: 'drug',
    label: name,
    x: options.x ?? 0,
    y: options.y ?? 0,
    dose: options.dose ?? 100,
    halfLife: options.halfLife ?? 4,
    bioavailability: options.bioavailability ?? 1,
    route: options.route ?? 'oral',
    visual: {
      shape: 'circle',
      fill: '#4A90D9',
      stroke: '#2C5282',
    },
    data: {
      name,
      dose: options.dose ?? 100,
      halfLife: options.halfLife ?? 4,
    },
  } as unknown as SimEntity;
}

/**
 * Helper to create a compartment entity
 */
function createCompartment(
  id: string,
  name: string,
  options: {
    x?: number;
    y?: number;
    volume?: number;
    concentration?: number;
  } = {}
): SimEntity {
  return {
    id,
    type: 'compartment',
    label: name,
    x: options.x ?? 0,
    y: options.y ?? 0,
    volume: options.volume ?? 5, // liters (blood volume)
    concentration: options.concentration ?? 0,
    visual: {
      shape: 'rectangle',
      fill: '#FED7D7',
      stroke: '#C53030',
    },
    data: { name, volume: options.volume ?? 5 },
  } as unknown as SimEntity;
}

/**
 * Helper to create a population entity (for epidemiology)
 */
function createEpidemicPopulation(
  id: string,
  compartment: 'S' | 'E' | 'I' | 'R' | 'D',
  count: number,
  options: { x?: number; y?: number } = {}
): SimEntity {
  const colors: Record<string, string> = {
    S: '#48BB78', // Susceptible - green
    E: '#ECC94B', // Exposed - yellow
    I: '#F56565', // Infected - red
    R: '#4299E1', // Recovered - blue
    D: '#718096', // Deceased - gray
  };

  const labels: Record<string, string> = {
    S: 'Susceptible',
    E: 'Exposed',
    I: 'Infected',
    R: 'Recovered',
    D: 'Deceased',
  };

  return {
    id,
    type: 'epidemic_compartment',
    label: labels[compartment],
    x: options.x ?? 0,
    y: options.y ?? 0,
    compartment,
    count,
    visual: {
      shape: 'circle',
      fill: colors[compartment],
      size: Math.log(count + 1) * 5,
    },
    data: { compartment, count },
  } as unknown as SimEntity;
}

describe('MedicineKernel', () => {
  let kernel: ReturnType<typeof createMedicineKernel>;

  beforeEach(() => {
    kernel = createMedicineKernel();
  });

  describe('domain identification', () => {
    it('should have MEDICINE as domain', () => {
      expect(kernel.domain).toBe('MEDICINE');
    });

    it('should be able to execute MEDICINE manifests', () => {
      const manifest = createMedicineManifest();
      expect(kernel.canExecute(manifest)).toBe(true);
    });

    it('should not execute non-MEDICINE manifests', () => {
      const manifest = createMedicineManifest({ domain: 'BIOLOGY' as any });
      expect(kernel.canExecute(manifest)).toBe(false);
    });
  });

  describe('run()', () => {
    it('should return error for wrong domain', async () => {
      const manifest = createMedicineManifest({ domain: 'CHEMISTRY' as any });
      const result = await kernel.run({ manifest });

      expect(result.errors).toBeDefined();
      expect(result.errors).toContain('Manifest domain is not MEDICINE');
      expect(result.keyframes).toHaveLength(0);
    });

    it('should generate initial keyframe with entities', async () => {
      const entities = [
        createDrug('drug-aspirin', 'Aspirin', { dose: 500 }),
        createCompartment('plasma', 'Blood Plasma'),
      ];

      const manifest = createMedicineManifest({
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

describe('Pharmacokinetics Simulation', () => {
  describe('Drug Absorption', () => {
    it('should simulate oral drug absorption', async () => {
      const kernel = createMedicineKernel();

      const entities = [
        createDrug('drug-ibuprofen', 'Ibuprofen', {
          dose: 400,
          halfLife: 2,
          bioavailability: 0.95,
          route: 'oral',
        }),
        createCompartment('gi-tract', 'GI Tract', { volume: 1 }),
        createCompartment('plasma', 'Blood Plasma', { volume: 5 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-administer' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'ADMINISTER',
              drugId: 'drug-ibuprofen',
              compartmentId: 'gi-tract',
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-absorb' as any,
          orderIndex: 1,

          actions: [
            {
              action: 'ABSORB',
              fromCompartment: 'gi-tract',
              toCompartment: 'plasma',
              rate: 0.5,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-peak' as any,
          orderIndex: 2,

          actions: [
            {
              action: 'UPDATE_CONCENTRATION',
              compartmentId: 'plasma',
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createMedicineManifest({
        title: 'Ibuprofen Pharmacokinetics',
        description: 'Oral absorption and plasma concentration',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(3);
    });
  });

  describe('Drug Distribution', () => {
    it('should simulate two-compartment model', async () => {
      const kernel = createMedicineKernel();

      const entities = [
        createDrug('drug-lidocaine', 'Lidocaine', {
          dose: 100,
          halfLife: 1.5,
          route: 'iv',
        }),
        createCompartment('central', 'Central Compartment', { volume: 6 }),
        createCompartment('peripheral', 'Peripheral Compartment', { volume: 20 }),
      ];

      const steps: SimulationStep[] = [
        {
          id: 'step-iv-admin' as any,
          orderIndex: 0,

          actions: [
            {
              action: 'ADMINISTER',
              drugId: 'drug-lidocaine',
              compartmentId: 'central',
              instant: true,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-distribute' as any,
          orderIndex: 1,

          actions: [
            {
              action: 'DISTRIBUTE',
              fromCompartment: 'central',
              toCompartment: 'peripheral',
              rate: 0.3,
            } as unknown as SimAction,
          ],
        },
        {
          id: 'step-equilibrium' as any,
          orderIndex: 2,

          actions: [
            {
              action: 'EQUILIBRATE',
              compartments: ['central', 'peripheral'],
            } as unknown as SimAction,
          ],
        },
      ];

      const manifest = createMedicineManifest({
        title: 'Two-Compartment PK Model',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(3);
    });
  });

  describe('Drug Elimination', () => {
    it('should simulate first-order elimination', async () => {
      const kernel = createMedicineKernel();

      const entities = [
        createDrug('drug-acetaminophen', 'Acetaminophen', {
          dose: 1000,
          halfLife: 2.5,
        }),
        createCompartment('plasma', 'Plasma', { volume: 5, concentration: 200 }),
        createCompartment('liver', 'Liver (Metabolism)', { volume: 1.5 }),
        createCompartment('kidney', 'Kidney (Excretion)', { volume: 0.3 }),
      ];

      const steps: SimulationStep[] = [];

      // Simulate 10 hours of elimination
      for (let t = 0; t <= 10; t += 2) {
        steps.push({
          id: `step-${t}h` as any,
          orderIndex: t / 2,
          actions: [
            {
              action: 'METABOLIZE',
              drugId: 'drug-acetaminophen',
              compartmentId: 'liver',
              rate: 0.28, // ke based on t1/2 = 2.5h
            } as unknown as SimAction,
            {
              action: 'EXCRETE',
              drugId: 'drug-acetaminophen',
              compartmentId: 'kidney',
            } as unknown as SimAction,
          ],
        });
      }

      const manifest = createMedicineManifest({
        title: 'Acetaminophen Elimination',
        description: 'First-order hepatic metabolism and renal excretion',
        initialEntities: entities,
        steps,
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(6);
    });
  });
});

describe('Epidemiology Simulation', () => {
  describe('SIR Model', () => {
    it('should simulate basic SIR epidemic dynamics', async () => {
      const kernel = createMedicineKernel();

      const population = 1000000;
      const entities = [
        createEpidemicPopulation('S', 'S', population - 100, { x: 100, y: 200 }),
        createEpidemicPopulation('I', 'I', 100, { x: 300, y: 200 }),
        createEpidemicPopulation('R', 'R', 0, { x: 500, y: 200 }),
      ];

      const steps: SimulationStep[] = [];

      // Simulate 30 days
      for (let day = 0; day < 30; day++) {
        steps.push({
          id: `day-${day}` as any,
          orderIndex: day,
          actions: [
            {
              action: 'SIR_UPDATE',
              beta: 0.3, // Transmission rate
              gamma: 0.1, // Recovery rate
            } as unknown as SimAction,
          ],
        });
      }

      const manifest = createMedicineManifest({
        title: 'SIR Epidemic Model',
        description: 'Basic Susceptible-Infected-Recovered model',
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'MEDICINE' as any,
          medicine: {
            epidemiologyModel: 'SIR',
            R0: 3.0, // Basic reproduction number
          } as any,
        },
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(30);
    });
  });

  describe('SEIR Model', () => {
    it('should simulate SEIR with exposed compartment', async () => {
      const kernel = createMedicineKernel();

      const population = 100000;
      const entities = [
        createEpidemicPopulation('S', 'S', population - 10, { x: 100, y: 200 }),
        createEpidemicPopulation('E', 'E', 10, { x: 233, y: 200 }),
        createEpidemicPopulation('I', 'I', 0, { x: 366, y: 200 }),
        createEpidemicPopulation('R', 'R', 0, { x: 500, y: 200 }),
      ];

      const steps: SimulationStep[] = [];

      // Simulate 60 days
      for (let day = 0; day < 60; day += 5) {
        steps.push({
          id: `day-${day}` as any,
          orderIndex: day / 5,
          actions: [
            {
              action: 'SEIR_UPDATE',
              beta: 0.5, // Transmission rate
              sigma: 0.2, // Incubation rate (1/5 days)
              gamma: 0.1, // Recovery rate (1/10 days)
            } as unknown as SimAction,
          ],
        });
      }

      const manifest = createMedicineManifest({
        title: 'SEIR Epidemic Model',
        description: 'Susceptible-Exposed-Infected-Recovered with incubation',
        initialEntities: entities,
        steps,
        domainMetadata: {
          domain: 'MEDICINE' as any,
          medicine: {
            epidemiologyModel: 'SEIR',
            incubationPeriod: 5,
            infectiousPeriod: 10,
          } as any,
        },
      });

      const result = await kernel.run({ manifest });

      expect(result.errors).toBeUndefined();
      expect(result.totalSteps).toBe(12);
    });
  });
});

describe('Cardiac Physiology Simulation', () => {
  it('should simulate ECG waveform', async () => {
    const kernel = createMedicineKernel();

    const entities = [
      { id: 'heart', type: 'organ', x: 200, y: 200, visual: { fill: '#C53030' } } as unknown as SimEntity,
      { id: 'ecg-trace', type: 'waveform', x: 400, y: 200, visual: { stroke: '#48BB78' } } as unknown as SimEntity,
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-p-wave' as any,
        orderIndex: 0,

        actions: [
          { action: 'ECG_PHASE', phase: 'P', duration: 80 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-pr-interval' as any,
        orderIndex: 1,

        actions: [
          { action: 'ECG_PHASE', phase: 'PR', duration: 120 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-qrs' as any,
        orderIndex: 2,

        actions: [
          { action: 'ECG_PHASE', phase: 'QRS', duration: 100 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-st' as any,
        orderIndex: 3,

        actions: [
          { action: 'ECG_PHASE', phase: 'ST', duration: 80 } as unknown as SimAction,
        ],
      },
      {
        id: 'step-t-wave' as any,
        orderIndex: 4,

        actions: [
          { action: 'ECG_PHASE', phase: 'T', duration: 160 } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createMedicineManifest({
      title: 'Electrocardiogram (ECG)',
      description: 'Normal cardiac electrical activity',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(5);
  });
});

describe('Respiratory Physiology Simulation', () => {
  it('should simulate oxygen-hemoglobin dissociation curve', async () => {
    const kernel = createMedicineKernel();

    const entities = [
      { id: 'hemoglobin', type: 'protein', x: 100, y: 200 } as unknown as SimEntity,
      { id: 'oxygen', type: 'molecule', x: 200, y: 200 } as unknown as SimEntity,
      { id: 'curve', type: 'graph', x: 400, y: 200 } as unknown as SimEntity,
    ];

    const steps: SimulationStep[] = [];

    // Simulate binding at different pO2 levels
    const pO2Levels = [10, 20, 30, 40, 60, 80, 100];

    pO2Levels.forEach((pO2, index) => {
      steps.push({
        id: `step-pO2-${pO2}` as any,
        orderIndex: index,
        actions: [
          {
            action: 'CALCULATE_SATURATION',
            pO2,
            // Hill equation parameters
            p50: 26.6,
            hillCoefficient: 2.7,
          } as unknown as SimAction,
        ],
      });
    });

    const manifest = createMedicineManifest({
      title: 'Oxygen-Hemoglobin Dissociation',
      description: 'Sigmoid binding curve with cooperative binding',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(7);
  });
});

describe('Drug-Drug Interaction Simulation', () => {
  it('should simulate competitive enzyme inhibition', async () => {
    const kernel = createMedicineKernel();

    const entities = [
      createDrug('warfarin', 'Warfarin', { dose: 5, halfLife: 40 }),
      createDrug('fluconazole', 'Fluconazole', { dose: 200, halfLife: 30 }),
      createCompartment('plasma', 'Plasma', { volume: 5 }),
      { id: 'cyp2c9', type: 'enzyme', x: 300, y: 150 } as unknown as SimEntity,
    ];

    const steps: SimulationStep[] = [
      {
        id: 'step-warfarin-alone' as any,
        orderIndex: 0,

        actions: [
          {
            action: 'METABOLIZE',
            drugId: 'warfarin',
            enzymeId: 'cyp2c9',
            rate: 0.0173, // Normal rate
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-add-fluconazole' as any,
        orderIndex: 1,

        actions: [
          {
            action: 'INHIBIT_ENZYME',
            inhibitorId: 'fluconazole',
            enzymeId: 'cyp2c9',
            inhibitionConstant: 0.5, // Ki
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-warfarin-inhibited' as any,
        orderIndex: 2,

        actions: [
          {
            action: 'METABOLIZE',
            drugId: 'warfarin',
            enzymeId: 'cyp2c9',
            rate: 0.0086, // Reduced rate due to inhibition
          } as unknown as SimAction,
        ],
      },
      {
        id: 'step-increased-inr' as any,
        orderIndex: 3,

        actions: [
          {
            action: 'CLINICAL_EFFECT',
            parameter: 'INR',
            change: 'increase',
            magnitude: 2,
          } as unknown as SimAction,
        ],
      },
    ];

    const manifest = createMedicineManifest({
      title: 'Warfarin-Fluconazole Interaction',
      description: 'CYP2C9 inhibition leading to increased warfarin effect',
      initialEntities: entities,
      steps,
    });

    const result = await kernel.run({ manifest });

    expect(result.errors).toBeUndefined();
    expect(result.totalSteps).toBe(4);
  });
});
