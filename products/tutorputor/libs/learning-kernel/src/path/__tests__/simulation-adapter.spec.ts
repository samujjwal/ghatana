/**
 * Tests for Simulation Adapter
 *
 * @doc.type test
 * @doc.purpose Unit tests for manifest -> learning step mapping
 * @doc.layer libs
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import {
  toSimulationStep,
  toSimulationSteps,
  inferDifficulty,
  inferSkills,
  estimateCompletionTime,
  validateSimulationStep,
} from "../simulation-adapter";
import type { SimulationManifest, SimulationDomain } from "@ghatana/tutorputor-contracts/v1/simulation";

// =============================================================================
// Test Fixtures
// =============================================================================

function createBasicManifest(
  overrides: Partial<SimulationManifest> = {}
): SimulationManifest {
  return {
    id: "sim_test_001" as any,
    version: "1.0.0",
    title: "Test Simulation",
    description: "A test simulation for unit testing",
    domain: "PHYSICS" as SimulationDomain,
    authorId: "user_001" as any,
    tenantId: "tenant_001" as any,
    canvas: {
      width: 800,
      height: 600,
    },
    playback: {
      defaultSpeed: 1,
    },
    initialEntities: [
      {
        id: "entity_001" as any,
        type: "rigidBody",
        x: 100,
        y: 100,
        mass: 1,
      },
      {
        id: "entity_002" as any,
        type: "rigidBody",
        x: 200,
        y: 100,
        mass: 2,
      },
    ],
    steps: [
      {
        id: "step_001" as any,
        orderIndex: 0,
        title: "Step 1",
        actions: [
          { action: "APPLY_FORCE", targetId: "entity_001" as any, fx: 10, fy: 0 },
        ],
      },
      {
        id: "step_002" as any,
        orderIndex: 1,
        title: "Step 2",
        actions: [
          { action: "MOVE", targetId: "entity_001" as any, toX: 300, toY: 100 },
        ],
      },
    ],
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    schemaVersion: "1.0.0",
    ...overrides,
  } as SimulationManifest;
}

function createComplexManifest(): SimulationManifest {
  const entities = Array.from({ length: 20 }, (_, i) => ({
    id: `entity_${i}` as any,
    type: "rigidBody",
    x: i * 50,
    y: 100,
    mass: 1,
  }));

  const steps = Array.from({ length: 25 }, (_, i) => ({
    id: `step_${i}` as any,
    orderIndex: i,
    title: `Step ${i + 1}`,
    actions: Array.from({ length: 3 }, (_, j) => ({
      action: "MOVE",
      targetId: `entity_${j}` as any,
      toX: i * 10,
      toY: j * 10,
    })),
  }));

  return createBasicManifest({
    id: "sim_complex_001" as any,
    title: "Complex Physics Simulation",
    domain: "PHYSICS",
    initialEntities: entities,
    steps,
  });
}

function createChemistryManifest(): SimulationManifest {
  return createBasicManifest({
    id: "sim_chem_001" as any,
    title: "SN2 Reaction Mechanism",
    domain: "CHEMISTRY",
    initialEntities: [
      { id: "atom_c" as any, type: "atom", x: 100, y: 100, element: "C" },
      { id: "atom_br" as any, type: "atom", x: 150, y: 100, element: "Br" },
      { id: "atom_oh" as any, type: "atom", x: 50, y: 100, element: "O" },
      { id: "bond_1" as any, type: "bond", x: 125, y: 100, atom1Id: "atom_c" as any, atom2Id: "atom_br" as any, bondOrder: 1 },
    ],
    steps: [
      {
        id: "step_001" as any,
        orderIndex: 0,
        title: "Nucleophilic Attack",
        actions: [
          { action: "HIGHLIGHT_ATOMS", atomIds: ["atom_oh" as any], style: "nucleophile" },
          { action: "CREATE_BOND", atom1Id: "atom_c" as any, atom2Id: "atom_oh" as any, bondOrder: 1 },
        ],
      },
      {
        id: "step_002" as any,
        orderIndex: 1,
        title: "Leaving Group Departure",
        actions: [
          { action: "BREAK_BOND", bondId: "bond_1" as any },
          { action: "HIGHLIGHT_ATOMS", atomIds: ["atom_br" as any], style: "leaving_group" },
        ],
      },
    ],
  });
}

function createMedicineManifest(): SimulationManifest {
  return createBasicManifest({
    id: "sim_med_001" as any,
    title: "Drug Absorption and Elimination",
    domain: "MEDICINE",
    initialEntities: [
      { id: "comp_central" as any, type: "pkCompartment", x: 200, y: 200, compartmentType: "central", volume: 5, concentration: 0 },
      { id: "comp_periph" as any, type: "pkCompartment", x: 400, y: 200, compartmentType: "peripheral", volume: 10, concentration: 0 },
      { id: "dose_1" as any, type: "dose", x: 50, y: 200, amount: 100, route: "oral" },
    ],
    steps: [
      {
        id: "step_001" as any,
        orderIndex: 0,
        title: "Drug Administration",
        actions: [
          { action: "ABSORB", doseId: "dose_1" as any, compartmentId: "comp_central" as any, rate: 0.5 },
        ],
      },
      {
        id: "step_002" as any,
        orderIndex: 1,
        title: "Distribution",
        actions: [
          { action: "SIGNAL", signalId: "dose_1" as any, targetId: "comp_periph" as any, response: "activate" },
        ],
      },
      {
        id: "step_003" as any,
        orderIndex: 2,
        title: "Elimination",
        actions: [
          { action: "ELIMINATE", compartmentId: "comp_central" as any, rate: 0.1, route: "renal" },
        ],
      },
    ],
  });
}

// =============================================================================
// Difficulty Inference Tests
// =============================================================================

describe("inferDifficulty", () => {
  it("should infer INTRO for simple simulations", () => {
    const manifest = createBasicManifest({
      initialEntities: [{ id: "e1" as any, type: "node", x: 0, y: 0 }],
      steps: [
        {
          id: "s1" as any,
          orderIndex: 0,
          actions: [{ action: "HIGHLIGHT", targetIds: ["e1" as any] }],
        },
      ],
    });

    const difficulty = inferDifficulty(manifest);
    expect(difficulty).toBe("INTRO");
  });

  it("should infer INTERMEDIATE for moderately complex simulations", () => {
    const manifest = createBasicManifest();
    const difficulty = inferDifficulty(manifest);
    expect(["INTRO", "INTERMEDIATE"]).toContain(difficulty);
  });

  it("should infer ADVANCED for complex simulations", () => {
    const manifest = createComplexManifest();
    const difficulty = inferDifficulty(manifest);
    expect(difficulty).toBe("ADVANCED");
  });

  it("should consider domain complexity for MEDICINE", () => {
    const manifest = createMedicineManifest();
    const difficulty = inferDifficulty(manifest);
    // Medicine simulations get a complexity bonus
    expect(["INTERMEDIATE", "ADVANCED"]).toContain(difficulty);
  });

  it("should consider domain complexity for CHEMISTRY", () => {
    const manifest = createChemistryManifest();
    const difficulty = inferDifficulty(manifest);
    expect(["INTERMEDIATE", "ADVANCED"]).toContain(difficulty);
  });
});

// =============================================================================
// Skills Inference Tests
// =============================================================================

describe("inferSkills", () => {
  it("should infer domain default skills", () => {
    const manifest = createBasicManifest({ domain: "PHYSICS" });
    const skills = inferSkills(manifest);

    expect(skills.length).toBeGreaterThan(0);
    const skillNames = skills.map((s) => s.name);
    expect(skillNames).toContain("Mechanics");
  });

  it("should infer skills from entity types", () => {
    const manifest = createBasicManifest({
      initialEntities: [
        { id: "body" as any, type: "rigidBody", x: 0, y: 0, mass: 1 },
        { id: "spring" as any, type: "spring", x: 0, y: 0, anchorId: "body" as any, attachId: "body" as any, stiffness: 1, damping: 0.1, restLength: 100 },
      ],
    });

    const skills = inferSkills(manifest);
    const skillNames = skills.map((s) => s.name);

    expect(skillNames).toContain("Physics Simulation");
    expect(skillNames).toContain("Harmonic Motion");
  });

  it("should infer skills from chemistry entities", () => {
    const manifest = createChemistryManifest();
    const skills = inferSkills(manifest);
    const skillNames = skills.map((s) => s.name);

    expect(skillNames).toContain("Atomic Structure");
    expect(skillNames).toContain("Chemical Bonding");
  });

  it("should infer skills from PK/PD entities", () => {
    const manifest = createMedicineManifest();
    const skills = inferSkills(manifest);
    const skillNames = skills.map((s) => s.name);

    expect(skillNames).toContain("Pharmacokinetics");
    expect(skillNames).toContain("Drug Dosing");
  });

  it("should infer skills from actions", () => {
    const manifest = createBasicManifest({
      steps: [
        {
          id: "s1" as any,
          orderIndex: 0,
          actions: [
            { action: "APPLY_FORCE", targetId: "e1" as any, fx: 10, fy: 0 },
            { action: "SET_GRAVITY", gx: 0, gy: -9.8 },
          ],
        },
      ],
    });

    const skills = inferSkills(manifest);
    const skillNames = skills.map((s) => s.name);

    expect(skillNames).toContain("Force Analysis");
    expect(skillNames).toContain("Gravitational Physics");
  });

  it("should assign weights to skills", () => {
    const manifest = createBasicManifest();
    const skills = inferSkills(manifest);

    for (const skill of skills) {
      expect(skill.weight).toBeGreaterThan(0);
      expect(skill.weight).toBeLessThanOrEqual(1);
    }
  });

  it("should not duplicate skills", () => {
    const manifest = createBasicManifest();
    const skills = inferSkills(manifest);
    const skillNames = skills.map((s) => s.name);
    const uniqueNames = [...new Set(skillNames)];

    expect(skillNames.length).toBe(uniqueNames.length);
  });
});

// =============================================================================
// Time Estimation Tests
// =============================================================================

describe("estimateCompletionTime", () => {
  it("should return at least 1 minute", () => {
    const manifest = createBasicManifest({
      initialEntities: [],
      steps: [],
    });

    const time = estimateCompletionTime(manifest);
    expect(time).toBeGreaterThanOrEqual(1);
  });

  it("should increase time with more steps", () => {
    const simpleManifest = createBasicManifest();
    const complexManifest = createComplexManifest();

    const simpleTime = estimateCompletionTime(simpleManifest);
    const complexTime = estimateCompletionTime(complexManifest);

    expect(complexTime).toBeGreaterThan(simpleTime);
  });

  it("should apply domain multipliers", () => {
    const physicsManifest = createBasicManifest({ domain: "PHYSICS" });
    const medicineManifest = createBasicManifest({ domain: "MEDICINE" });

    const physicsTime = estimateCompletionTime(physicsManifest);
    const medicineTime = estimateCompletionTime(medicineManifest);

    // Medicine has a higher multiplier than physics
    expect(medicineTime).toBeGreaterThanOrEqual(physicsTime);
  });
});

// =============================================================================
// Manifest to Step Conversion Tests
// =============================================================================

describe("toSimulationStep", () => {
  it("should convert a manifest to a learning step", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);

    expect(step.id).toBeDefined();
    expect(step.type).toBe("simulation");
    expect(step.simulationId).toBe(manifest.id);
    expect(step.manifestId).toBe(manifest.id);
    expect(step.domain).toBe(manifest.domain);
  });

  it("should include inferred difficulty", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);

    expect(["INTRO", "INTERMEDIATE", "ADVANCED"]).toContain(step.difficulty);
  });

  it("should allow overriding difficulty", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest, { overrideDifficulty: "ADVANCED" });

    expect(step.difficulty).toBe("ADVANCED");
  });

  it("should include skills", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);

    expect(step.skills.length).toBeGreaterThan(0);
    for (const skill of step.skills) {
      expect(skill.skillId).toBeDefined();
      expect(skill.name).toBeDefined();
      expect(skill.weight).toBeDefined();
    }
  });

  it("should allow adding additional skills", () => {
    const manifest = createBasicManifest();
    const additionalSkill = {
      skillId: "skill_custom" as any,
      name: "Custom Skill",
      weight: 0.8,
    };

    const step = toSimulationStep(manifest, {
      additionalSkills: [additionalSkill],
    });

    const skillNames = step.skills.map((s) => s.name);
    expect(skillNames).toContain("Custom Skill");
  });

  it("should include estimated time", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);

    expect(step.estimatedTimeMinutes).toBeGreaterThanOrEqual(1);
  });

  it("should include metadata from manifest", () => {
    const manifest = createBasicManifest({
      title: "Test Title",
      description: "Test Description",
    });

    const step = toSimulationStep(manifest);

    expect(step.metadata.title).toBe("Test Title");
    expect(step.metadata.description).toBe("Test Description");
  });

  it("should extract tags", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);

    expect(step.metadata.tags).toBeDefined();
    expect(step.metadata.tags!.length).toBeGreaterThan(0);
    expect(step.metadata.tags).toContain("physics");
  });

  it("should include completion criteria", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);

    expect(step.completionCriteria).toBeDefined();
    expect(step.completionCriteria!.minTimeSpentSeconds).toBeGreaterThan(0);
    expect(step.completionCriteria!.minInteractions).toBeGreaterThanOrEqual(1);
  });

  it("should include prerequisites when provided", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest, {
      prerequisites: [
        { stepId: "step_prereq_001" as any, type: "required" },
      ],
    });

    expect(step.prerequisites.length).toBe(1);
    expect(step.prerequisites[0].stepId).toBe("step_prereq_001");
    expect(step.prerequisites[0].type).toBe("required");
  });

  it("should include assessment refs when provided", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest, {
      assessmentRefs: [
        { assessmentId: "assess_001", position: "post", required: true },
      ],
    });

    expect(step.assessmentRefs.length).toBe(1);
    expect(step.assessmentRefs[0].position).toBe("post");
    expect(step.assessmentRefs[0].required).toBe(true);
  });
});

// =============================================================================
// Batch Conversion Tests
// =============================================================================

describe("toSimulationSteps", () => {
  it("should convert multiple manifests", () => {
    const manifests = [
      createBasicManifest({ id: "sim_001" as any }),
      createBasicManifest({ id: "sim_002" as any }),
      createBasicManifest({ id: "sim_003" as any }),
    ];

    const steps = toSimulationSteps(manifests);

    expect(steps.length).toBe(3);
    for (const step of steps) {
      expect(step.type).toBe("simulation");
    }
  });

  it("should sort by difficulty when requested", () => {
    const manifests = [
      createComplexManifest(), // ADVANCED
      createBasicManifest({ initialEntities: [], steps: [] }), // INTRO
      createBasicManifest(), // INTERMEDIATE
    ];

    const steps = toSimulationSteps(manifests, { sortByDifficulty: true });

    // First should be easiest
    expect(steps[0].difficulty).toBe("INTRO");
  });
});

// =============================================================================
// Validation Tests
// =============================================================================

describe("validateSimulationStep", () => {
  it("should validate a correct step", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);
    const result = validateSimulationStep(step);

    expect(result.valid).toBe(true);
    expect(result.errors.length).toBe(0);
  });

  it("should detect missing step ID", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);
    (step as any).id = "";

    const result = validateSimulationStep(step);

    expect(result.valid).toBe(false);
    expect(result.errors).toContain("Step ID is required");
  });

  it("should detect missing simulation ID", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);
    (step as any).simulationId = "";

    const result = validateSimulationStep(step);

    expect(result.valid).toBe(false);
    expect(result.errors).toContain("Simulation ID is required");
  });

  it("should detect missing title", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);
    step.metadata.title = "";

    const result = validateSimulationStep(step);

    expect(result.valid).toBe(false);
    expect(result.errors).toContain("Step title is required in metadata");
  });

  it("should detect invalid estimated time", () => {
    const manifest = createBasicManifest();
    const step = toSimulationStep(manifest);
    step.estimatedTimeMinutes = 0;

    const result = validateSimulationStep(step);

    expect(result.valid).toBe(false);
    expect(result.errors).toContain("Estimated time must be at least 1 minute");
  });
});
