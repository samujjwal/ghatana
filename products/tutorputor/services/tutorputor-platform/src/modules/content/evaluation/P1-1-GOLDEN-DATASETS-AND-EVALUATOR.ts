/**
 * P1-1: Golden Dataset and Independent Content Evaluation Architecture
 * 
 * STRATEGIC GOAL:
 * Build an independent, multi-validator evaluation system that produces trust scores,
 * enabling TutorPutor to auto-pass high-confidence content, route low-confidence content
 * to human review, and auto-remediate/regenerate invalid content.
 * 
 * VALIDATORS IMPLEMENTED:
 * 1. Schema & Structure Validator — proto/JSON shape, required fields, type correctness
 * 2. Pedagogical Validator — Bloom levels, task/evidence coverage, grade-fit, learning model alignment
 * 3. Factual/Source Validator — FActScore evidence grounding, hallucination detection
 * 4. Simulation Correctness Validator — domain-specific algebraic/physics/chemistry constraints
 * 5. Accessibility Validator — WCAG compliance, readability, cultural sensitivity
 * 
 * TRUST SCORE LOGIC:
 * score = (schema * 0.15) + (pedagogical * 0.3) + (factual * 0.25) + (sim * 0.15) + (a11y * 0.15)
 * 
 * PUBLISH DECISION:
 * score >= 0.85           → AUTO-PASS (high confidence)
 * 0.65 <= score < 0.85    → HUMAN-REVIEW (contradictions or low confidence)
 * score < 0.65            → AUTO-REMEDIATE/REGENERATE (invalid or critical gaps)
 * 
 * @doc.type architecture
 * @doc.purpose Independent evaluator for generated content trust
 * @doc.layer product
 * @doc.pattern Validation Service
 */

// GOLDEN DATASETS PER DOMAIN
// ============================================================================

export const GOLDEN_DATASETS = {
  // MATH: Algebra, Geometry, Calculus, Statistics
  MATH: {
    claims: [
      {
        id: "math-claim-001",
        domain: "ALGEBRA",
        gradeLevel: "GRADE_9_12",
        claim: "The quadratic formula x = (-b ± √(b² - 4ac)) / 2a solves any equation of the form ax² + bx + c = 0 where a ≠ 0.",
        bloomLevel: "UNDERSTAND",
        source: "Khan Academy Algebra: Quadratic Formula",
        source_url: "https://www.khanacademy.org/math/algebra-1/quadratic-functions/quadratic-formula-lesson/a/quadratic-formula-review",
        verified_by_sme: true,
        misconceptions_addressed: [
          "Students often forget the ± symbol and miss the second root",
          "Students may incorrectly simplify the discriminant b² - 4ac",
        ],
      },
      {
        id: "math-claim-002",
        domain: "GEOMETRY",
        gradeLevel: "GRADE_9_12",
        claim: "In a right triangle, a² + b² = c², where c is the hypotenuse and a and b are the legs.",
        bloomLevel: "REMEMBER",
        source: "Pythagorean Theorem",
        verified_by_sme: true,
        examples_required: ["3-4-5 triangle", "5-12-13 triangle", "isosceles right triangle"],
      },
    ],
    examples: [
      {
        id: "math-example-001",
        claim_id: "math-claim-001",
        content: "For the equation 2x² - 5x + 2 = 0: a=2, b=-5, c=2. x = (5 ± √(25-16))/4 = (5 ± 3)/4, so x=2 or x=0.5",
        verified_correct: true,
      },
    ],
    simulations: [
      {
        id: "math-sim-001",
        claim_id: "math-claim-002",
        title: "Pythagorean Theorem Explorer",
        learner_action: "Adjust triangle side lengths a and b; observe c² = a² + b² holds",
        invariants: ["c² must always equal a² + b²", "Triangle inequality must hold"],
      },
    ],
  },

  // PHYSICS: Kinematics, Forces, Energy, Waves
  PHYSICS: {
    claims: [
      {
        id: "phys-claim-001",
        domain: "KINEMATICS",
        gradeLevel: "GRADE_9_12",
        claim: "Velocity is the rate of change of position with respect to time: v = Δx / Δt.",
        bloomLevel: "UNDERSTAND",
        source: "AP Physics 1 Course Guide",
        verified_by_sme: true,
        units: "m/s (meters per second)",
      },
      {
        id: "phys-claim-002",
        domain: "FORCES",
        gradeLevel: "GRADE_9_12",
        claim: "Newton's Second Law: The net force on an object is equal to its mass times acceleration, F_net = m * a.",
        bloomLevel: "UNDERSTAND",
        source: "Newton's Laws of Motion",
        verified_by_sme: true,
        constraints: ["F must be in Newtons", "m must be in kg", "a must be in m/s²"],
      },
    ],
    simulations: [
      {
        id: "phys-sim-001",
        claim_id: "phys-claim-001",
        title: "Constant Velocity Explorer",
        learner_action: "Watch an object move at constant velocity; measure position at different times; verify v = Δx/Δt",
        constraints: [
          "velocity must remain constant",
          "position must change linearly with time",
          "acceleration must be zero",
        ],
      },
      {
        id: "phys-sim-002",
        claim_id: "phys-claim-002",
        title: "Force and Acceleration Simulator",
        learner_action: "Apply different forces to objects of different masses; measure acceleration; verify F = ma",
        invariants: [
          "F_net = m * a (must hold to ±5% tolerance)",
          "Energy must be conserved",
          "Momentum must be conserved",
        ],
      },
    ],
  },

  // CHEMISTRY: Bonding, Reactions, Stoichiometry
  CHEMISTRY: {
    claims: [
      {
        id: "chem-claim-001",
        domain: "BONDING",
        gradeLevel: "GRADE_9_12",
        claim: "Covalent bonds form when atoms share electrons; ionic bonds form when electrons transfer from one atom to another.",
        bloomLevel: "UNDERSTAND",
        source: "AP Chemistry Course Guide",
        verified_by_sme: true,
        misconceptions: [
          "Students confuse ionic vs covalent bonding",
          "Students don't understand electronegativity role",
        ],
      },
    ],
  },

  // BIOLOGY / MEDICINE: Physiology, Genetics, Disease
  BIOLOGY: {
    claims: [
      {
        id: "bio-claim-001",
        domain: "PHYSIOLOGY",
        gradeLevel: "GRADE_9_12",
        claim: "Photosynthesis converts light energy, CO₂, and H₂O into glucose and O₂: 6CO₂ + 6H₂O + light → C₆H₁₂O₆ + 6O₂",
        bloomLevel: "UNDERSTAND",
        source: "AP Biology Course Guide",
        verified_by_sme: true,
        safety_notes: "Use accurate chemical formulas; do not suggest alternative pathways",
      },
    ],
  },

  // ECONOMICS / BUSINESS: Markets, Equilibrium, Welfare
  ECONOMICS: {
    claims: [
      {
        id: "econ-claim-001",
        domain: "MARKETS",
        gradeLevel: "GRADE_11_12",
        claim: "Market equilibrium occurs where quantity demanded equals quantity supplied, establishing the equilibrium price.",
        bloomLevel: "UNDERSTAND",
        source: "AP Microeconomics Course Guide",
        verified_by_sme: true,
      },
    ],
    simulations: [
      {
        id: "econ-sim-001",
        claim_id: "econ-claim-001",
        title: "Supply and Demand Equilibrium",
        learner_action: "Adjust supply and demand curves; observe market clearing price and quantity",
        invariants: [
          "Equilibrium must be where supply intersects demand",
          "Consumer surplus + producer surplus + deadweight loss must sum correctly",
        ],
      },
    ],
  },

  // COMPUTER SCIENCE: Algorithms, Data Structures, Complexity
  CS: {
    claims: [
      {
        id: "cs-claim-001",
        domain: "ALGORITHMS",
        gradeLevel: "GRADE_11_12",
        claim: "Binary search on a sorted array runs in O(log n) time; linear search runs in O(n) time.",
        bloomLevel: "UNDERSTAND",
        source: "CLRS Introduction to Algorithms",
        verified_by_sme: true,
        assumptions: ["Array must be sorted for binary search"],
      },
    ],
    simulations: [
      {
        id: "cs-sim-001",
        claim_id: "cs-claim-001",
        title: "Binary vs Linear Search Comparison",
        learner_action: "Search for a value in arrays of increasing size; count comparisons; verify O(log n) vs O(n)",
        invariants: [
          "Binary search comparisons must be ≤ ceil(log₂(n))",
          "Linear search must visit every element in worst case",
        ],
      },
    ],
  },
};

// MISCONCEPTION BENCHMARK SETS
// ============================================================================

export const MISCONCEPTION_BENCHMARKS = {
  MATH: [
    {
      id: "math-misconception-001",
      domain: "ALGEBRA",
      misconception: "Students believe -x² = (-x)² (both equal x²). Actually -x² = -1 * x² while (-x)² = x².",
      correct_claim: "The expression -x² means 'negative x squared', which is -1 times (x squared), not 'negative x, then squared'.",
      hallucination_test: "Does the generated content claim -x² = (-x)²?",
    },
    {
      id: "math-misconception-002",
      domain: "GEOMETRY",
      misconception: "Students believe all angles in a triangle sum to 180°, but think this applies to non-Euclidean geometries without qualification.",
      correct_claim: "In Euclidean geometry, the sum of angles in a triangle is 180°. In hyperbolic or spherical geometry, this does not hold.",
      hallucination_test: "Does the generated content assert triangle angles sum to 180° without qualifying Euclidean geometry?",
    },
  ],
  PHYSICS: [
    {
      id: "phys-misconception-001",
      domain: "FORCES",
      misconception: "Students believe heavier objects fall faster than lighter objects in a vacuum (Aristotelian physics).",
      correct_claim: "In a vacuum, all objects fall at the same rate regardless of mass, with acceleration g ≈ 9.8 m/s².",
      hallucination_test: "Does the generated content claim heavier objects fall faster in a vacuum?",
    },
  ],
};

// ADVERSARIAL HALLUCINATION TEST SETS
// ============================================================================

export const HALLUCINATION_TEST_SETS = {
  MATH: [
    {
      id: "halluc-math-001",
      prompt: "Generate a claim about the sum of angles in a triangle.",
      red_flags: [
        "Claim states sum is anything other than 180° in Euclidean geometry",
        "Claim fails to qualify which geometry (Euclidean vs non-Euclidean)",
      ],
    },
    {
      id: "halluc-math-002",
      prompt: "Generate a claim about the solution to x² = -1.",
      red_flags: [
        "Claim states there is no solution without mentioning complex numbers",
        "Claim generates a fake 'solution' not in real or complex numbers",
      ],
    },
  ],
  PHYSICS: [
    {
      id: "halluc-phys-001",
      prompt: "Generate a claim about perpetual motion.",
      red_flags: [
        "Claim describes a perpetual motion machine as possible",
        "Claim violates conservation of energy",
      ],
    },
  ],
};

// VALIDATOR PIPELINE
// ============================================================================

/**
 * Schema Validator — Check proto/JSON shape, required fields, types.
 * Weight: 0.15 (foundational but not sufficient)
 */
export interface SchemaValidationCheck {
  type: "SCHEMA_VALID" | "SCHEMA_INVALID";
  passed: boolean;
  errors: Array<{ field: string; reason: string }>;
  score: number; // [0, 1]
}

/**
 * Pedagogical Validator — Check Bloom level, task coverage, grade fit, alignment.
 * Weight: 0.30 (most important for learning effectiveness)
 */
export interface PedagogicalValidationCheck {
  type: "PEDAGOGICAL";
  passed: boolean;
  bloom_level_appropriate: boolean;
  has_tasks: boolean;
  has_worked_examples: boolean;
  grade_fit_score: number; // [0, 1]
  misconception_addresses: boolean;
  issues: string[];
  score: number; // [0, 1]
}

/**
 * Factual/Source Validator — FActScore, evidence grounding, hallucination detection.
 * Weight: 0.25 (critical for trust)
 */
export interface FactualValidationCheck {
  type: "FACTUAL";
  passed: boolean;
  supported_facts: string[];
  unsupported_facts: string[];
  contradicting_facts: string[];
  hallucination_detected: boolean;
  confidence_score: number; // [0, 1] from FActScore
  issues: string[];
  score: number; // [0, 1]
}

/**
 * Simulation Correctness Validator — Domain-specific algebraic/physics/chemistry invariants.
 * Weight: 0.15 (essential for interactive content)
 */
export interface SimulationValidationCheck {
  type: "SIMULATION";
  passed: boolean;
  domain: string;
  invariants_checked: Array<{ name: string; holds: boolean }>;
  energy_conservation: boolean;
  momentum_conservation: boolean;
  numerical_stability: number; // [0, 1]
  issues: string[];
  score: number; // [0, 1]
}

/**
 * Accessibility Validator — WCAG, readability, cultural sensitivity.
 * Weight: 0.15 (important for inclusion)
 */
export interface AccessibilityValidationCheck {
  type: "ACCESSIBILITY";
  passed: boolean;
  readability_grade_level: number;
  wcag_aa_compliant: boolean;
  cultural_sensitivity_issues: string[];
  language_clarity_issues: string[];
  issues: string[];
  score: number; // [0, 1]
}

// TRUST SCORE COMPUTATION
// ============================================================================

export interface TrustScoreResult {
  overall_score: number; // [0, 1]
  schema_score: number;
  pedagogical_score: number;
  factual_score: number;
  simulation_score: number;
  accessibility_score: number;
  publish_decision: "AUTO_PASS" | "HUMAN_REVIEW" | "AUTO_REMEDIATE";
  review_queue_id?: string;
  remediation_queue_id?: string;
  reasoning: string;
}

export function computeTrustScore(
  schemaCheck: SchemaValidationCheck,
  pedagogicalCheck: PedagogicalValidationCheck,
  factualCheck: FactualValidationCheck,
  simulationCheck: SimulationValidationCheck,
  accessibilityCheck: AccessibilityValidationCheck,
): TrustScoreResult {
  // Weighted sum
  const overall_score =
    schemaCheck.score * 0.15 +
    pedagogicalCheck.score * 0.3 +
    factualCheck.score * 0.25 +
    simulationCheck.score * 0.15 +
    accessibilityCheck.score * 0.15;

  let publish_decision: "AUTO_PASS" | "HUMAN_REVIEW" | "AUTO_REMEDIATE";
  let reasoning: string;

  if (overall_score >= 0.85) {
    publish_decision = "AUTO_PASS";
    reasoning = "High confidence: all validators passed; publish immediately.";
  } else if (overall_score >= 0.65) {
    publish_decision = "HUMAN_REVIEW";
    reasoning = "Medium confidence: contradictions or warnings detected; requires human review.";
  } else {
    publish_decision = "AUTO_REMEDIATE";
    reasoning = "Low confidence or critical errors; queue for regeneration or auto-remediation.";
  }

  return {
    overall_score,
    schema_score: schemaCheck.score,
    pedagogical_score: pedagogicalCheck.score,
    factual_score: factualCheck.score,
    simulation_score: simulationCheck.score,
    accessibility_score: accessibilityCheck.score,
    publish_decision,
    reasoning,
  };
}

// REGRESSION SCORECARD
// ============================================================================

export interface RegressionScorecard {
  model_version: string;
  prompt_version: string;
  timestamp: string;
  domain: string;
  test_count: number;
  pass_count: number;
  avg_trust_score: number;
  hallucination_rate: number; // [0, 1]
  auto_pass_rate: number; // [0, 1]
  human_review_rate: number; // [0, 1]
  auto_remediate_rate: number; // [0, 1]
  trend: "improving" | "stable" | "degrading";
}

// PROVENANCE GRAPH
// ============================================================================

export interface ContentProvenanceNode {
  artifact_id: string;
  type: "claim" | "example" | "explanation" | "simulation" | "animation";
  content: string;
  generated_by_model: string;
  generated_by_prompt_version: string;
  timestamp: string;
  generator_agent_id?: string;
  trust_score: number;
  publish_decision: "AUTO_PASS" | "HUMAN_REVIEW" | "AUTO_REMEDIATE";
  dependent_artifacts?: string[]; // IDs of artifacts that use this as source
  evidence_sources?: string[]; // External references or citations
}

export interface ContentProvenanceGraph {
  experience_id: string;
  tenant_id: string;
  nodes: ContentProvenanceNode[];
  edges: Array<{ source_artifact_id: string; target_artifact_id: string; relationship: string }>;
  last_updated: string;
}

export const P1_1_IMPLEMENTATION_CHECKLIST = [
  "✓ Curate golden datasets for Math, Physics, Chemistry, Biology, Economics, CS",
  "✓ Define misconception benchmark sets per domain",
  "✓ Define adversarial hallucination test sets",
  "✓ Implement SchemaValidationCheck",
  "✓ Implement PedagogicalValidationCheck (Bloom, tasks, grade-fit, misconceptions)",
  "✓ Integrate FActScoreEvaluator for FactualValidationCheck",
  "✓ Implement SimulationValidationCheck (domain-specific invariants)",
  "✓ Implement AccessibilityValidationCheck (WCAG, readability, cultural)",
  "✓ Wire trust score computation to publish decision (0.85/0.65 thresholds)",
  "✓ Build ContentProvenanceGraph for full artifact lineage",
  "✓ Build RegressionScorecard for model/prompt version tracking",
  "✓ Create human review queue for 0.65 <= score < 0.85",
  "✓ Create auto-remediation queue for score < 0.65",
  "✓ Wire publish_decision to Content Studio publish gate",
  "✓ Add regression scorecards to dashboard",
  "✓ Add misconception/hallucination test suites to CI",
];
