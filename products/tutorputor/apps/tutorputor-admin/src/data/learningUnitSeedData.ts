/**
 * Seed Data for Evidence-Based Learning Units
 *
 * Mock Learning Units following the canonical schema from contracts/v1/learning-unit.ts
 * Used for development and demonstration of the Content Hub and Simulation Builder.
 *
 * @doc.type module
 * @doc.purpose Seed data for Learning Units with Claims, Evidence, Tasks
 * @doc.layer data
 * @doc.pattern Seed
 */

import type { LearningUnit, Claim, Evidence, Task, Intent, AssessmentConfig, CredentialConfig, TelemetryConfig, Artifact } from '@ghatana/tutorputor-contracts/v1/learning-unit';

// ============================================================================
// Physics Learning Units
// ============================================================================

export const physicsFreeFallLU: LearningUnit = {
    id: 'lu-physics-free-fall-001',
    version: 1,
    domain: 'physics',
    level: 'intermediate',
    status: 'published',

    intent: {
        problem: 'Students believe heavier objects fall faster than lighter objects, even in a vacuum',
        motivation: 'Critical for understanding gravity, free fall, and Galilean physics principles',
        targetMisconceptions: ['mass_affects_fall_rate', 'heavier_is_faster'],
    },

    claims: [
        {
            id: 'C1',
            text: 'Predict which object hits the ground first when dropped from the same height in a vacuum',
            bloom: 'apply',
            prerequisites: [],
        },
        {
            id: 'C2',
            text: 'Explain why mass does not affect the rate of fall in a vacuum using force and acceleration concepts',
            bloom: 'understand',
            prerequisites: ['C1'],
        },
    ],

    evidence: [
        {
            id: 'E1',
            claimRef: 'C1',
            type: 'prediction_vs_outcome',
            description: 'Compare predicted vs actual fall behavior to assess prediction accuracy',
            observables: [
                { name: 'predicted_object', type: 'enum', enumValues: ['bowling_ball', 'feather', 'same_time'] },
                { name: 'confidence_level', type: 'number', unit: '1-3' },
                { name: 'actual_outcome', type: 'string' },
                { name: 'prediction_correct', type: 'boolean' },
            ],
        },
        {
            id: 'E2',
            claimRef: 'C2',
            type: 'parameter_targeting',
            description: 'Ability to manipulate air resistance to achieve synchronized falling',
            observables: [
                { name: 'air_resistance_final', type: 'number', unit: 'N' },
                { name: 'rmse_from_target', type: 'number' },
                { name: 'attempts_count', type: 'number' },
                { name: 'time_to_solution', type: 'number', unit: 'seconds' },
            ],
        },
        {
            id: 'E3',
            claimRef: 'C2',
            type: 'explanation_quality',
            description: 'Quality of causal explanation using physics terminology',
            observables: [
                { name: 'explanation_text', type: 'string' },
                { name: 'word_count', type: 'number' },
                { name: 'contains_key_terms', type: 'enum', enumValues: ['force', 'gravity', 'acceleration', 'mass'] },
            ],
        },
    ],

    tasks: [
        {
            id: 'T1',
            type: 'prediction',
            claimRef: 'C1',
            evidenceRef: 'E1',
            prompt: 'A bowling ball (5kg) and a feather (0.01kg) are dropped from the same height in a VACUUM. Which hits the ground first?',
            confidenceRequired: true,
            options: ['Bowling ball', 'Feather', 'Same time'],
            correctAnswer: 'Same time',
        },
        {
            id: 'T2',
            type: 'simulation',
            claimRef: 'C2',
            evidenceRef: 'E2',
            simulationRef: 'sim-falling-objects-vacuum',
            prompt: 'Adjust the air resistance slider until BOTH objects fall at the same rate.',
            goal: 'Achieve synchronized landing',
            successCriteria: {
                rmse: '<= 0.25',
                maxAttempts: 3,
                timeLimit: 180,
            },
        },
        {
            id: 'T3',
            type: 'explanation',
            claimRef: 'C2',
            evidenceRef: 'E3',
            prompt: 'Was your T1 prediction correct? Explain WHY mass does not affect fall rate in a vacuum. Use the words "force", "gravity", and "acceleration" in your answer.',
            minWords: 50,
            expectedTerms: ['force', 'gravity', 'acceleration'],
        },
    ] as Task[],

    artifacts: [
        {
            type: 'simulation',
            ref: 'sim-falling-objects-vacuum',
            claims: ['C1', 'C2'],
            scaffolds: ['T2'],
        },
    ],

    telemetry: {
        events: [
            'simulation.parameter_changed',
            'simulation.reset',
            'prediction.submitted',
            'explanation.text_entered',
        ],
        processFeatures: [
            'time_to_first_attempt',
            'parameter_adjustment_strategy',
            'backtracking_count',
        ],
    },

    assessment: {
        model: 'cbm_plus_process',
        confidenceLevels: ['low', 'medium', 'high'],
        scoring: {
            correctHighConfidence: 3,
            correctMediumConfidence: 2,
            correctLowConfidence: 1,
            incorrectHighConfidence: -6,
            incorrectMediumConfidence: -2,
            incorrectLowConfidence: 0,
        },
        vivaTrigger: {
            conditions: [
                { type: 'overconfident_wrong', threshold: 2, description: '2+ high confidence wrong answers' },
            ],
        },
    },

    credential: {
        skillTags: ['physics.gravity', 'physics.forces', 'misconception.mass_affects_fall'],
        issueOn: 'primary_claim_mastered',
        badgeRef: 'badge-gravity-fundamentals',
    },

    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-03-20T14:30:00Z',
    createdBy: 'author-physics-001',
    tenantId: 'tenant-default',
};

export const physicsProjectileMotionLU: LearningUnit = {
    id: 'lu-physics-projectile-001',
    version: 1,
    domain: 'physics',
    level: 'intermediate',
    status: 'published',

    intent: {
        problem: 'Students believe projectile motion follows a V-shaped or linear path instead of a parabola',
        motivation: 'Understanding parabolic motion is essential for physics, engineering, and sports science',
        targetMisconceptions: ['linear_projectile_path', 'v_shaped_trajectory'],
    },

    claims: [
        {
            id: 'C1',
            text: 'Predict the shape of a projectile trajectory when launched at an angle',
            bloom: 'apply',
            prerequisites: [],
        },
        {
            id: 'C2',
            text: 'Explain why projectiles follow a parabolic path using horizontal and vertical motion independence',
            bloom: 'understand',
            prerequisites: ['C1'],
        },
        {
            id: 'C3',
            text: 'Construct a trajectory to hit a target by adjusting launch angle and velocity',
            bloom: 'create',
            prerequisites: ['C1', 'C2'],
        },
    ],

    evidence: [
        {
            id: 'E1',
            claimRef: 'C1',
            type: 'prediction_vs_outcome',
            description: 'Compare predicted trajectory shape vs actual simulated path',
            observables: [
                { name: 'predicted_shape', type: 'enum', enumValues: ['parabola', 'straight_line', 'v_shape', 'other'] },
                { name: 'confidence_level', type: 'number', unit: '1-3' },
                { name: 'prediction_correct', type: 'boolean' },
            ],
        },
        {
            id: 'E2',
            claimRef: 'C2',
            type: 'explanation_quality',
            description: 'Quality of explanation for parabolic motion',
            observables: [
                { name: 'explanation_text', type: 'string' },
                { name: 'mentions_horizontal_motion', type: 'boolean' },
                { name: 'mentions_vertical_motion', type: 'boolean' },
                { name: 'mentions_gravity', type: 'boolean' },
            ],
        },
        {
            id: 'E3',
            claimRef: 'C3',
            type: 'parameter_targeting',
            description: 'Successfully hitting the target with projectile',
            observables: [
                { name: 'launch_angle', type: 'number', unit: 'degrees' },
                { name: 'launch_velocity', type: 'number', unit: 'm/s' },
                { name: 'distance_to_target', type: 'number', unit: 'm' },
                { name: 'attempts_count', type: 'number' },
            ],
        },
    ],

    tasks: [
        {
            id: 'T1',
            type: 'prediction',
            claimRef: 'C1',
            evidenceRef: 'E1',
            prompt: 'A ball is launched at 45° angle. What shape will its path trace in the air?',
            confidenceRequired: true,
            options: ['Parabola (curved arc)', 'Straight diagonal line', 'V-shape (up then down)', 'Horizontal line'],
            correctAnswer: 'Parabola (curved arc)',
        },
        {
            id: 'T2',
            type: 'simulation',
            claimRef: 'C3',
            evidenceRef: 'E3',
            simulationRef: 'sim-projectile-motion',
            prompt: 'Adjust the launch angle and velocity to hit the target at X=50m',
            goal: 'Hit the target within 2m accuracy',
            successCriteria: {
                rmse: '<= 2.0',
                maxAttempts: 5,
                timeLimit: 300,
            },
        },
        {
            id: 'T3',
            type: 'explanation',
            claimRef: 'C2',
            evidenceRef: 'E2',
            prompt: 'Explain why a projectile follows a curved (parabolic) path. Mention what happens to horizontal and vertical motion separately.',
            minWords: 60,
            expectedTerms: ['horizontal', 'vertical', 'gravity', 'constant'],
        },
    ] as Task[],

    artifacts: [
        {
            type: 'simulation',
            ref: 'sim-projectile-motion',
            claims: ['C1', 'C3'],
            scaffolds: ['T2'],
        },
    ],

    telemetry: {
        events: [
            'simulation.launched',
            'simulation.parameter_changed',
            'simulation.target_hit',
            'prediction.submitted',
        ],
        processFeatures: [
            'angle_adjustment_pattern',
            'velocity_adjustment_pattern',
            'time_between_attempts',
        ],
    },

    assessment: {
        model: 'cbm_plus_process',
        confidenceLevels: ['low', 'medium', 'high'],
        scoring: {
            correctHighConfidence: 3,
            correctMediumConfidence: 2,
            correctLowConfidence: 1,
            incorrectHighConfidence: -6,
            incorrectMediumConfidence: -2,
            incorrectLowConfidence: 0,
        },
        vivaTrigger: {
            conditions: [
                { type: 'overconfident_wrong', threshold: 2, description: '2+ high confidence wrong answers' },
                { type: 'speed_anomaly', description: 'Completed too quickly' },
            ],
        },
    },

    credential: {
        skillTags: ['physics.projectile_motion', 'physics.kinematics', 'physics.vectors'],
        issueOn: 'all_claims_mastered',
        badgeRef: 'badge-projectile-master',
    },

    createdAt: '2024-02-10T09:00:00Z',
    updatedAt: '2024-04-05T11:20:00Z',
    createdBy: 'author-physics-001',
    tenantId: 'tenant-default',
};

// ============================================================================
// Chemistry Learning Units
// ============================================================================

export const chemistryEnzymeInhibitionLU: LearningUnit = {
    id: 'lu-chemistry-enzyme-001',
    version: 1,
    domain: 'chemistry',
    level: 'advanced',
    status: 'published',

    intent: {
        problem: 'Students confuse competitive and non-competitive enzyme inhibition mechanisms',
        motivation: 'Critical for understanding drug design, metabolism, and biochemical regulation',
        targetMisconceptions: ['inhibition_type_confusion', 'km_vmax_misunderstanding'],
    },

    claims: [
        {
            id: 'C1',
            text: 'Predict inhibition type from Lineweaver-Burk plot characteristics',
            bloom: 'analyze',
            prerequisites: [],
        },
        {
            id: 'C2',
            text: 'Explain the difference between competitive and non-competitive inhibition at the molecular level',
            bloom: 'understand',
            prerequisites: ['C1'],
        },
    ],

    evidence: [
        {
            id: 'E1',
            claimRef: 'C1',
            type: 'prediction_vs_outcome',
            description: 'Graph interpretation accuracy with confidence tracking',
            observables: [
                { name: 'predicted_inhibition_type', type: 'enum', enumValues: ['competitive', 'non_competitive', 'uncompetitive', 'mixed'] },
                { name: 'confidence_level', type: 'number', unit: '1-3' },
                { name: 'graph_features_identified', type: 'string' },
                { name: 'prediction_correct', type: 'boolean' },
            ],
        },
        {
            id: 'E2',
            claimRef: 'C2',
            type: 'explanation_quality',
            description: 'Quality of mechanism explanation',
            observables: [
                { name: 'explanation_text', type: 'string' },
                { name: 'mentions_active_site', type: 'boolean' },
                { name: 'mentions_allosteric_site', type: 'boolean' },
                { name: 'mentions_km_change', type: 'boolean' },
                { name: 'mentions_vmax_change', type: 'boolean' },
            ],
        },
    ],

    tasks: [
        {
            id: 'T1',
            type: 'prediction',
            claimRef: 'C1',
            evidenceRef: 'E1',
            prompt: 'Given this Lineweaver-Burk plot showing: same y-intercept (1/Vmax) but different x-intercept (-1/Km). What type of inhibition is this?',
            confidenceRequired: true,
            options: ['Competitive inhibition', 'Non-competitive inhibition', 'Uncompetitive inhibition', 'No inhibition'],
            correctAnswer: 'Competitive inhibition',
        },
        {
            id: 'T2',
            type: 'simulation',
            claimRef: 'C1',
            evidenceRef: 'E1',
            simulationRef: 'sim-enzyme-kinetics',
            prompt: 'Adjust the inhibitor concentration and type to match the target Lineweaver-Burk plot',
            goal: 'Match the target plot within tolerance',
            successCriteria: {
                rmse: '<= 0.15',
                maxAttempts: 4,
                timeLimit: 240,
            },
        },
        {
            id: 'T3',
            type: 'explanation',
            claimRef: 'C2',
            evidenceRef: 'E2',
            prompt: 'Explain the molecular difference between competitive and non-competitive inhibition. Include where each inhibitor binds and how it affects Km and Vmax.',
            minWords: 80,
            expectedTerms: ['active site', 'allosteric', 'Km', 'Vmax', 'substrate'],
        },
    ] as Task[],

    artifacts: [
        {
            type: 'simulation',
            ref: 'sim-enzyme-kinetics',
            claims: ['C1'],
            scaffolds: ['T2'],
        },
    ],

    telemetry: {
        events: [
            'simulation.inhibitor_changed',
            'simulation.concentration_changed',
            'graph.point_selected',
            'prediction.submitted',
        ],
        processFeatures: [
            'graph_analysis_time',
            'parameter_exploration_pattern',
            'error_correction_rate',
        ],
    },

    assessment: {
        model: 'cbm_plus_process',
        confidenceLevels: ['low', 'medium', 'high'],
        scoring: {
            correctHighConfidence: 3,
            correctMediumConfidence: 2,
            correctLowConfidence: 1,
            incorrectHighConfidence: -8, // Higher penalty for advanced content
            incorrectMediumConfidence: -3,
            incorrectLowConfidence: 0,
        },
        vivaTrigger: {
            conditions: [
                { type: 'overconfident_wrong', threshold: 2, description: '2+ high confidence wrong answers' },
                { type: 'speed_anomaly', description: 'Completed graph analysis too quickly' },
            ],
        },
    },

    credential: {
        skillTags: ['biochemistry.enzymes', 'biochemistry.inhibition', 'biochemistry.kinetics'],
        issueOn: 'all_claims_mastered',
        badgeRef: 'badge-enzyme-kinetics-master',
    },

    createdAt: '2024-03-01T08:00:00Z',
    updatedAt: '2024-05-10T16:45:00Z',
    createdBy: 'author-biochem-001',
    tenantId: 'tenant-default',
};

// ============================================================================
// Biology Learning Units
// ============================================================================

export const biologyMitosisLU: LearningUnit = {
    id: 'lu-biology-mitosis-001',
    version: 1,
    domain: 'biology',
    level: 'foundational',
    status: 'published',

    intent: {
        problem: 'Students confuse the stages of mitosis and their distinguishing characteristics',
        motivation: 'Understanding cell division is fundamental to biology, medicine, and cancer research',
        targetMisconceptions: ['phase_order_confusion', 'chromosome_vs_chromatid'],
    },

    claims: [
        {
            id: 'C1',
            text: 'Identify the stage of mitosis from visual characteristics of a cell',
            bloom: 'apply',
            prerequisites: [],
        },
        {
            id: 'C2',
            text: 'Sequence the stages of mitosis in the correct order',
            bloom: 'remember',
            prerequisites: [],
        },
        {
            id: 'C3',
            text: 'Explain what happens to chromosomes during each stage of mitosis',
            bloom: 'understand',
            prerequisites: ['C1', 'C2'],
        },
    ],

    evidence: [
        {
            id: 'E1',
            claimRef: 'C1',
            type: 'prediction_vs_outcome',
            description: 'Stage identification accuracy',
            observables: [
                { name: 'identified_stage', type: 'enum', enumValues: ['prophase', 'metaphase', 'anaphase', 'telophase', 'interphase'] },
                { name: 'confidence_level', type: 'number', unit: '1-3' },
                { name: 'identification_correct', type: 'boolean' },
                { name: 'time_to_identify', type: 'number', unit: 'seconds' },
            ],
        },
        {
            id: 'E2',
            claimRef: 'C2',
            type: 'prediction_vs_outcome',
            description: 'Correct sequencing of mitosis stages',
            observables: [
                { name: 'sequence_submitted', type: 'string' },
                { name: 'sequence_correct', type: 'boolean' },
                { name: 'positions_correct', type: 'number' },
            ],
        },
        {
            id: 'E3',
            claimRef: 'C3',
            type: 'explanation_quality',
            description: 'Explanation of chromosome behavior',
            observables: [
                { name: 'explanation_text', type: 'string' },
                { name: 'mentions_condensation', type: 'boolean' },
                { name: 'mentions_alignment', type: 'boolean' },
                { name: 'mentions_separation', type: 'boolean' },
            ],
        },
    ],

    tasks: [
        {
            id: 'T1',
            type: 'prediction',
            claimRef: 'C1',
            evidenceRef: 'E1',
            prompt: 'Look at this cell image. Chromosomes are lined up at the center of the cell. What stage is this?',
            confidenceRequired: true,
            options: ['Prophase', 'Metaphase', 'Anaphase', 'Telophase'],
            correctAnswer: 'Metaphase',
        },
        {
            id: 'T2',
            type: 'simulation',
            claimRef: 'C2',
            evidenceRef: 'E2',
            simulationRef: 'sim-mitosis-stages',
            prompt: 'Drag the stages into the correct order: Prophase, Metaphase, Anaphase, Telophase',
            goal: 'Arrange all stages correctly',
            successCriteria: {
                maxAttempts: 3,
                timeLimit: 120,
            },
        },
        {
            id: 'T3',
            type: 'explanation',
            claimRef: 'C3',
            evidenceRef: 'E3',
            prompt: 'Describe what happens to chromosomes as a cell moves from Prophase through Telophase.',
            minWords: 40,
            expectedTerms: ['condense', 'align', 'separate', 'nuclear envelope'],
        },
    ] as Task[],

    artifacts: [
        {
            type: 'simulation',
            ref: 'sim-mitosis-stages',
            claims: ['C1', 'C2'],
            scaffolds: ['T2'],
        },
    ],

    telemetry: {
        events: [
            'simulation.stage_selected',
            'simulation.stage_reordered',
            'image.zoomed',
            'prediction.submitted',
        ],
        processFeatures: [
            'time_per_stage_identification',
            'reordering_attempts',
            'help_requests',
        ],
    },

    assessment: {
        model: 'cbm',
        confidenceLevels: ['low', 'medium', 'high'],
        scoring: {
            correctHighConfidence: 3,
            correctMediumConfidence: 2,
            correctLowConfidence: 1,
            incorrectHighConfidence: -4,
            incorrectMediumConfidence: -1,
            incorrectLowConfidence: 0,
        },
    },

    credential: {
        skillTags: ['biology.cell_division', 'biology.mitosis', 'biology.chromosomes'],
        issueOn: 'primary_claim_mastered',
        badgeRef: 'badge-cell-division-basics',
    },

    createdAt: '2024-01-20T11:00:00Z',
    updatedAt: '2024-04-15T09:30:00Z',
    createdBy: 'author-bio-001',
    tenantId: 'tenant-default',
};

// ============================================================================
// CS/Algorithms Learning Units
// ============================================================================

export const csSortingAlgorithmsLU: LearningUnit = {
    id: 'lu-cs-sorting-001',
    version: 1,
    domain: 'cs',
    level: 'intermediate',
    status: 'published',

    intent: {
        problem: 'Students struggle to understand time complexity differences between sorting algorithms',
        motivation: 'Algorithm analysis is fundamental to writing efficient software and passing technical interviews',
        targetMisconceptions: ['all_sorts_equal', 'bubble_sort_efficient', 'big_o_confusion'],
    },

    claims: [
        {
            id: 'C1',
            text: 'Predict which sorting algorithm will complete faster for a given input',
            bloom: 'analyze',
            prerequisites: [],
        },
        {
            id: 'C2',
            text: 'Explain why certain sorting algorithms are faster using Big-O notation',
            bloom: 'understand',
            prerequisites: ['C1'],
        },
        {
            id: 'C3',
            text: 'Trace through a sorting algorithm step-by-step on a small array',
            bloom: 'apply',
            prerequisites: [],
        },
    ],

    evidence: [
        {
            id: 'E1',
            claimRef: 'C1',
            type: 'prediction_vs_outcome',
            description: 'Algorithm speed prediction accuracy',
            observables: [
                { name: 'predicted_faster', type: 'enum', enumValues: ['bubble_sort', 'merge_sort', 'quick_sort', 'insertion_sort'] },
                { name: 'confidence_level', type: 'number', unit: '1-3' },
                { name: 'prediction_correct', type: 'boolean' },
                { name: 'input_size', type: 'number' },
            ],
        },
        {
            id: 'E2',
            claimRef: 'C2',
            type: 'explanation_quality',
            description: 'Big-O explanation quality',
            observables: [
                { name: 'explanation_text', type: 'string' },
                { name: 'uses_big_o_notation', type: 'boolean' },
                { name: 'mentions_comparisons', type: 'boolean' },
                { name: 'mentions_nested_loops', type: 'boolean' },
            ],
        },
        {
            id: 'E3',
            claimRef: 'C3',
            type: 'parameter_targeting',
            description: 'Correct step-by-step tracing',
            observables: [
                { name: 'steps_correct', type: 'number' },
                { name: 'total_steps', type: 'number' },
                { name: 'errors_made', type: 'number' },
                { name: 'time_to_complete', type: 'number', unit: 'seconds' },
            ],
        },
    ],

    tasks: [
        {
            id: 'T1',
            type: 'prediction',
            claimRef: 'C1',
            evidenceRef: 'E1',
            prompt: 'For an array of 10,000 random elements, which algorithm will finish first?',
            confidenceRequired: true,
            options: ['Bubble Sort O(n²)', 'Merge Sort O(n log n)', 'Insertion Sort O(n²)', 'They are about the same'],
            correctAnswer: 'Merge Sort O(n log n)',
        },
        {
            id: 'T2',
            type: 'simulation',
            claimRef: 'C3',
            evidenceRef: 'E3',
            simulationRef: 'sim-sorting-visualizer',
            prompt: 'Step through bubble sort on [5, 2, 8, 1, 9]. Click to advance each comparison and swap.',
            goal: 'Complete the sort with no more than 2 errors',
            successCriteria: {
                maxAttempts: 3,
                timeLimit: 300,
            },
        },
        {
            id: 'T3',
            type: 'explanation',
            claimRef: 'C2',
            evidenceRef: 'E2',
            prompt: 'Explain why Merge Sort is faster than Bubble Sort for large arrays. Use Big-O notation in your answer.',
            minWords: 50,
            expectedTerms: ['O(n²)', 'O(n log n)', 'comparisons', 'divide'],
        },
    ] as Task[],

    artifacts: [
        {
            type: 'simulation',
            ref: 'sim-sorting-visualizer',
            claims: ['C1', 'C3'],
            scaffolds: ['T2'],
        },
    ],

    telemetry: {
        events: [
            'simulation.step_advanced',
            'simulation.swap_made',
            'simulation.error_corrected',
            'algorithm.selected',
        ],
        processFeatures: [
            'hesitation_before_swap',
            'error_recovery_time',
            'algorithm_switch_count',
        ],
    },

    assessment: {
        model: 'cbm_plus_process',
        confidenceLevels: ['low', 'medium', 'high'],
        scoring: {
            correctHighConfidence: 3,
            correctMediumConfidence: 2,
            correctLowConfidence: 1,
            incorrectHighConfidence: -6,
            incorrectMediumConfidence: -2,
            incorrectLowConfidence: 0,
        },
        vivaTrigger: {
            conditions: [
                { type: 'overconfident_wrong', threshold: 2 },
                { type: 'pattern_mismatch', description: 'Fast prediction but slow tracing' },
            ],
        },
    },

    credential: {
        skillTags: ['cs.algorithms', 'cs.sorting', 'cs.complexity_analysis'],
        issueOn: 'all_claims_mastered',
        badgeRef: 'badge-algorithm-analyst',
    },

    createdAt: '2024-02-25T14:00:00Z',
    updatedAt: '2024-05-01T10:15:00Z',
    createdBy: 'author-cs-001',
    tenantId: 'tenant-default',
};

// ============================================================================
// Draft Learning Units (for authoring demonstration)
// ============================================================================

export const draftPendulumLU: LearningUnit = {
    id: 'lu-physics-pendulum-draft',
    version: 1,
    domain: 'physics',
    level: 'intermediate',
    status: 'draft',

    intent: {
        problem: 'Students believe pendulum mass affects its period',
        motivation: 'Understanding period independence is key for timekeeping and oscillation concepts',
        targetMisconceptions: ['mass_affects_period'],
    },

    claims: [
        {
            id: 'C1',
            text: 'Predict how changing mass affects pendulum period',
            bloom: 'apply',
            prerequisites: [],
        },
    ],

    evidence: [
        {
            id: 'E1',
            claimRef: 'C1',
            type: 'prediction_vs_outcome',
            description: 'Mass-period prediction accuracy',
            observables: [
                { name: 'predicted_effect', type: 'enum', enumValues: ['increases', 'decreases', 'no_change'] },
                { name: 'confidence_level', type: 'number', unit: '1-3' },
            ],
        },
    ],

    tasks: [
        {
            id: 'T1',
            type: 'prediction',
            claimRef: 'C1',
            evidenceRef: 'E1',
            prompt: 'If you double the mass of a pendulum bob, what happens to the period?',
            confidenceRequired: true,
            options: ['Period increases', 'Period decreases', 'Period stays the same'],
            correctAnswer: 'Period stays the same',
        },
    ] as Task[],

    artifacts: [],

    telemetry: {
        events: ['prediction.submitted'],
        processFeatures: [],
    },

    assessment: {
        model: 'cbm',
        confidenceLevels: ['low', 'medium', 'high'],
        scoring: {
            correctHighConfidence: 3,
            correctMediumConfidence: 2,
            correctLowConfidence: 1,
            incorrectHighConfidence: -6,
            incorrectMediumConfidence: -2,
            incorrectLowConfidence: 0,
        },
    },

    createdAt: '2024-06-01T09:00:00Z',
    updatedAt: '2024-06-01T09:00:00Z',
    createdBy: 'author-physics-001',
    tenantId: 'tenant-default',
};

// ============================================================================
// Export All Learning Units
// ============================================================================

export const allLearningUnits: LearningUnit[] = [
    physicsFreeFallLU,
    physicsProjectileMotionLU,
    chemistryEnzymeInhibitionLU,
    biologyMitosisLU,
    csSortingAlgorithmsLU,
    draftPendulumLU,
];

export const publishedLearningUnits = allLearningUnits.filter(lu => lu.status === 'published');
export const draftLearningUnits = allLearningUnits.filter(lu => lu.status === 'draft');

// ============================================================================
// Learning Unit Statistics (Mock Analytics)
// ============================================================================

export interface LearningUnitStats {
    luId: string;
    totalAttempts: number;
    completionRate: number;
    avgCalibration: number; // -1 to 1, 0 = well calibrated
    avgTimeMinutes: number;
    overconfidenceRate: number;
    vivaTriggeredRate: number;
    rating: number;
    ratingCount: number;
}

export const learningUnitStats: Record<string, LearningUnitStats> = {
    'lu-physics-free-fall-001': {
        luId: 'lu-physics-free-fall-001',
        totalAttempts: 1247,
        completionRate: 0.89,
        avgCalibration: 0.12, // Slightly overconfident
        avgTimeMinutes: 22,
        overconfidenceRate: 0.32,
        vivaTriggeredRate: 0.18,
        rating: 4.7,
        ratingCount: 312,
    },
    'lu-physics-projectile-001': {
        luId: 'lu-physics-projectile-001',
        totalAttempts: 892,
        completionRate: 0.85,
        avgCalibration: 0.08,
        avgTimeMinutes: 28,
        overconfidenceRate: 0.25,
        vivaTriggeredRate: 0.15,
        rating: 4.6,
        ratingCount: 198,
    },
    'lu-chemistry-enzyme-001': {
        luId: 'lu-chemistry-enzyme-001',
        totalAttempts: 456,
        completionRate: 0.72,
        avgCalibration: 0.35, // More overconfident (harder topic)
        avgTimeMinutes: 35,
        overconfidenceRate: 0.45,
        vivaTriggeredRate: 0.28,
        rating: 4.4,
        ratingCount: 87,
    },
    'lu-biology-mitosis-001': {
        luId: 'lu-biology-mitosis-001',
        totalAttempts: 2103,
        completionRate: 0.94,
        avgCalibration: -0.05, // Slightly underconfident
        avgTimeMinutes: 18,
        overconfidenceRate: 0.15,
        vivaTriggeredRate: 0.08,
        rating: 4.9,
        ratingCount: 521,
    },
    'lu-cs-sorting-001': {
        luId: 'lu-cs-sorting-001',
        totalAttempts: 678,
        completionRate: 0.81,
        avgCalibration: 0.22,
        avgTimeMinutes: 32,
        overconfidenceRate: 0.38,
        vivaTriggeredRate: 0.22,
        rating: 4.5,
        ratingCount: 156,
    },
};
