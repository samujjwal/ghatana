/**
 * Test Utilities and Factories
 * 
 * Shared mocks and test data factories for content pipeline tests.
 * 
 * @doc.type test-utility
 * @doc.purpose Provide consistent test data and mocks
 * @doc.layer test
 * @doc.pattern Factory Pattern
 */

import { vi } from 'vitest';

// ============================================================================
// Logger Mock
// ============================================================================

export interface MockLogger {
  info: ReturnType<typeof vi.fn>;
  warn: ReturnType<typeof vi.fn>;
  error: ReturnType<typeof vi.fn>;
  debug: ReturnType<typeof vi.fn>;
}

export function createMockLogger(): MockLogger {
  return {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  };
}

// ============================================================================
// Prisma Mock
// ============================================================================

export interface MockPrisma {
  learningExperience: {
    findMany: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
    findUnique: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
  };
  learningClaim: {
    findMany: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
    upsert: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    count: ReturnType<typeof vi.fn>;
  };
  claimExample: {
    findMany: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    upsert: ReturnType<typeof vi.fn>;
    deleteMany: ReturnType<typeof vi.fn>;
    count: ReturnType<typeof vi.fn>;
  };
  claimSimulation: {
    findMany: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    upsert: ReturnType<typeof vi.fn>;
    count: ReturnType<typeof vi.fn>;
  };
  claimAnimation: {
    findMany: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    upsert: ReturnType<typeof vi.fn>;
    count: ReturnType<typeof vi.fn>;
  };
  simulationManifest: {
    upsert: ReturnType<typeof vi.fn>;
  };
  validationRecord: {
    create: ReturnType<typeof vi.fn>;
  };
  module: {
    findMany: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
  };
  enrollment: {
    findUnique: ReturnType<typeof vi.fn>;
  };
}

export function createMockPrisma(): MockPrisma {
  return {
    learningExperience: {
      findMany: vi.fn(),
      findFirst: vi.fn(),
      findUnique: vi.fn(),
      update: vi.fn(),
      create: vi.fn(),
    },
    learningClaim: {
      findMany: vi.fn(),
      findFirst: vi.fn(),
      upsert: vi.fn(),
      create: vi.fn(),
      count: vi.fn(),
    },
    claimExample: {
      findMany: vi.fn(),
      create: vi.fn(),
      upsert: vi.fn(),
      deleteMany: vi.fn(),
      count: vi.fn(),
    },
    claimSimulation: {
      findMany: vi.fn(),
      create: vi.fn(),
      upsert: vi.fn(),
      count: vi.fn(),
    },
    claimAnimation: {
      findMany: vi.fn(),
      create: vi.fn(),
      upsert: vi.fn(),
      count: vi.fn(),
    },
    simulationManifest: {
      upsert: vi.fn(),
    },
    validationRecord: {
      create: vi.fn(),
    },
    module: {
      findMany: vi.fn(),
      findFirst: vi.fn(),
    },
    enrollment: {
      findUnique: vi.fn(),
    },
  };
}

// ============================================================================
// gRPC Mock
// ============================================================================

export interface MockGrpcClient {
  generateClaims: ReturnType<typeof vi.fn>;
  generateExamples: ReturnType<typeof vi.fn>;
  generateSimulation: ReturnType<typeof vi.fn>;
  generateAnimation: ReturnType<typeof vi.fn>;
  validateContent: ReturnType<typeof vi.fn>;
}

export function createMockGrpcClient(): MockGrpcClient {
  return {
    generateClaims: vi.fn(),
    generateExamples: vi.fn(),
    generateSimulation: vi.fn(),
    generateAnimation: vi.fn(),
    validateContent: vi.fn(),
  };
}

// ============================================================================
// BullMQ Mock
// ============================================================================

export interface MockJob {
  id: string;
  name: string;
  data: any;
  waitUntilFinished: ReturnType<typeof vi.fn>;
}

export interface MockQueue {
  add: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
}

export interface MockQueueEvents {
  close: ReturnType<typeof vi.fn>;
}

export function createMockQueue(): MockQueue {
  return {
    add: vi.fn(),
    close: vi.fn(),
  };
}

export function createMockQueueEvents(): MockQueueEvents {
  return {
    close: vi.fn(),
  };
}

export function createMockJob(overrides?: Partial<MockJob>): MockJob {
  return {
    id: 'job-123',
    name: 'test-job',
    data: {},
    waitUntilFinished: vi.fn(),
    ...overrides,
  };
}

// ============================================================================
// Test Data Factories
// ============================================================================

export const createExperience = (overrides?: Partial<any>) => ({
  id: 'exp-test-001',
  tenantId: 'tenant-1',
  title: 'Test Learning Experience',
  description: 'A test experience for unit testing',
  domain: 'PHYSICS',
  status: 'DRAFT',
  concept: 'test-concept',
  targetGrades: ['GRADE_9_12'],
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides,
});

export const createClaim = (overrides?: Partial<any>) => ({
  id: 'claim-test-001',
  experienceId: 'exp-test-001',
  claimRef: 'C1',
  text: 'Test learning claim',
  bloomLevel: 'UNDERSTAND',
  contentNeeds: createContentNeeds(),
  orderIndex: 0,
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides,
});

export const createContentNeeds = (overrides?: Partial<any>) => ({
  examples: {
    required: true,
    types: ['REAL_WORLD_APPLICATION', 'STEP_BY_STEP'],
    count: 2,
    complexity: 'MODERATE',
    scaffolding: 'MEDIUM',
  },
  simulation: {
    required: true,
    interactionType: 'INTERACTIVE_EXPLORATION',
    complexity: 'INTERMEDIATE',
    estimatedTimeMinutes: 15,
    entities: ['entity1', 'entity2'],
  },
  animation: {
    required: true,
    type: 'CONCEPT_VISUALIZATION',
    durationSeconds: 120,
    complexity: 'MODERATE',
  },
  ...overrides,
});

export const createExample = (overrides?: Partial<any>) => ({
  id: 'example-test-001',
  experienceId: 'exp-test-001',
  claimRef: 'C1',
  type: 'REAL_WORLD_APPLICATION',
  title: 'Test Example',
  content: {
    text: 'This is a test example',
    media: [],
  },
  orderIndex: 0,
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides,
});

export const createSimulation = (overrides?: Partial<any>) => ({
  id: 'sim-test-001',
  experienceId: 'exp-test-001',
  claimRef: 'C1',
  interactionType: 'INTERACTIVE_EXPLORATION',
  goal: 'Explore the concept',
  entities: ['entity1'],
  simulationManifestId: 'manifest-001',
  createdAt: new Date(),
  updatedAt: new Date(),
  simulationManifest: createSimulationManifest(),
  ...overrides,
});

export const createSimulationManifest = (overrides?: Partial<any>) => ({
  id: 'manifest-001',
  domain: 'PHYSICS',
  title: 'Test Simulation',
  description: 'A test simulation',
  manifest: {
    entities: [],
    systems: [],
    interactions: [],
  },
  version: '1.0.0',
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides,
});

export const createAnimation = (overrides?: Partial<any>) => ({
  id: 'anim-test-001',
  experienceId: 'exp-test-001',
  claimRef: 'C1',
  type: 'CONCEPT_VISUALIZATION',
  title: 'Test Animation',
  duration: 120,
  config: {
    scenes: [],
    transitions: [],
  },
  createdAt: new Date(),
  updatedAt: new Date(),
  ...overrides,
});

export const createValidationRecord = (overrides?: Partial<any>) => ({
  id: 'validation-001',
  experienceId: 'exp-test-001',
  status: 'PASS',
  overallScore: 85,
  details: {
    correctness: { score: 90, passed: true },
    completeness: { score: 85, passed: true },
    concreteness: { score: 80, passed: true },
    conciseness: { score: 85, passed: true },
  },
  createdAt: new Date(),
  ...overrides,
});

// ============================================================================
// gRPC Response Factories
// ============================================================================

export const createGrpcClaimsResponse = (overrides?: Partial<any>) => ({
  claims: [
    {
      claim_ref: 'C1',
      text: 'Test claim',
      bloom_level: 'UNDERSTAND',
      content_needs: createContentNeeds(),
    },
  ],
  ...overrides,
});

export const createGrpcExamplesResponse = (overrides?: Partial<any>) => ({
  examples: [
    {
      type: 'REAL_WORLD_APPLICATION',
      title: 'Test Example',
      content: { text: 'Test content' },
    },
  ],
  ...overrides,
});

export const createGrpcSimulationResponse = (overrides?: Partial<any>) => ({
  manifest: {
    domain: 'PHYSICS',
    title: 'Test Simulation',
    entities: [],
    systems: [],
  },
  interaction_type: 'INTERACTIVE_EXPLORATION',
  goal: 'Test goal',
  entities: ['entity1'],
  ...overrides,
});

export const createGrpcAnimationResponse = (overrides?: Partial<any>) => ({
  animation: {
    type: 'CONCEPT_VISUALIZATION',
    title: 'Test Animation',
    duration_seconds: 120,
    config: {},
  },
  ...overrides,
});

export const createGrpcValidationResponse = (overrides?: Partial<any>) => ({
  status: 'PASS',
  overall_score: 85,
  details: {
    correctness: { score: 90, passed: true },
    completeness: { score: 85, passed: true },
    concreteness: { score: 80, passed: true },
    conciseness: { score: 85, passed: true },
  },
  ...overrides,
});
