/**
 * Copy-Paste Ready API Test Examples
 * 
 * All the example data you need to test the simulation and visualization APIs.
 * Simply copy the JSON payloads and paste them into curl commands or Postman.
 * 
 * @doc.type API Examples
 * @doc.purpose Ready-to-use API test payloads
 * @doc.layer product
 */

// ============================================================================
// DOMAIN CREATION EXAMPLES
// ============================================================================

/**
 * Create Physics Domain
 * 
 * POST /admin/api/v1/content/db/domains
 */
export const createPhysicsDomain = {
    domain: 'PHYSICS',
    title: 'Physics 101',
    description: 'Introduction to Physics - Mechanics, Waves, and Forces',
    author: 'Physics Department'
};

/**
 * Create Chemistry Domain
 * 
 * POST /admin/api/v1/content/db/domains
 */
export const createChemistryDomain = {
    domain: 'CHEMISTRY',
    title: 'Chemistry Fundamentals',
    description: 'Basic concepts in chemistry including molecular structures and reactions',
    author: 'Chemistry Department'
};

/**
 * Create Biology Domain
 * 
 * POST /admin/api/v1/content/db/domains
 */
export const createBiologyDomain = {
    domain: 'BIOLOGY',
    title: 'Biology Essentials',
    description: 'Fundamental biological processes and cellular mechanisms',
    author: 'Biology Department'
};

/**
 * Create Mathematics Domain
 * 
 * POST /admin/api/v1/content/db/domains
 */
export const createMathematicsDomain = {
    domain: 'MATHEMATICS',
    title: 'Mathematics Fundamentals',
    description: 'Core mathematical concepts including functions, algebra, and geometry',
    author: 'Mathematics Department'
};

/**
 * Create Economics Domain
 * 
 * POST /admin/api/v1/content/db/domains
 */
export const createEconomicsDomain = {
    domain: 'ECONOMICS',
    title: 'Economics 101',
    description: 'Introduction to economics and market principles',
    author: 'Economics Department'
};

// ============================================================================
// CONCEPT CREATION EXAMPLES
// ============================================================================

/**
 * Create Kinematics Concept (Physics)
 * 
 * POST /admin/api/v1/content/db/domains/{PHYSICS_DOMAIN_ID}/concepts
 */
export const createKinematicsConcept = {
    name: 'Kinematics',
    description: 'Study of motion without considering forces',
    level: 'FOUNDATIONAL',
    learningObjectives: JSON.stringify([
        'Understand velocity and acceleration',
        'Analyze projectile motion',
        'Solve kinematics problems'
    ]),
    prerequisites: JSON.stringify([]),
    competencies: JSON.stringify(['critical-thinking', 'problem-solving', 'mathematical-reasoning']),
    keywords: JSON.stringify(['motion', 'velocity', 'acceleration', 'projectile', 'physics'])
};

/**
 * Create Molecular Structure Concept (Chemistry)
 * 
 * POST /admin/api/v1/content/db/domains/{CHEMISTRY_DOMAIN_ID}/concepts
 */
export const createMolecularStructureConcept = {
    name: 'Molecular Structure',
    description: 'Understanding how molecules are formed and bonded',
    level: 'FOUNDATIONAL',
    learningObjectives: JSON.stringify([
        'Understand atomic bonding',
        'Recognize molecular structures',
        'Analyze chemical bonds'
    ]),
    prerequisites: JSON.stringify([]),
    competencies: JSON.stringify(['visualization', 'molecular-reasoning', 'chemistry-concepts']),
    keywords: JSON.stringify(['molecule', 'bonding', 'covalent', 'ionic', 'structure'])
};

/**
 * Create Mitosis Concept (Biology)
 * 
 * POST /admin/api/v1/content/db/domains/{BIOLOGY_DOMAIN_ID}/concepts
 */
export const createMitosisConcept = {
    name: 'Mitosis',
    description: 'The process of cell division in eukaryotic cells',
    level: 'FOUNDATIONAL',
    learningObjectives: JSON.stringify([
        'Identify stages of mitosis',
        'Understand cell division mechanism',
        'Apply mitosis concepts'
    ]),
    prerequisites: JSON.stringify([]),
    competencies: JSON.stringify(['biological-understanding', 'visualization', 'observation']),
    keywords: JSON.stringify(['mitosis', 'cell-division', 'chromosome', 'prophase', 'metaphase'])
};

/**
 * Create Functions Concept (Mathematics)
 * 
 * POST /admin/api/v1/content/db/domains/{MATHEMATICS_DOMAIN_ID}/concepts
 */
export const createFunctionsConcept = {
    name: 'Functions',
    description: 'Understanding functions and their graphical representations',
    level: 'FOUNDATIONAL',
    learningObjectives: JSON.stringify([
        'Understand function notation',
        'Graph quadratic functions',
        'Analyze function properties'
    ]),
    prerequisites: JSON.stringify([]),
    competencies: JSON.stringify(['algebraic-thinking', 'visualization', 'mathematical-reasoning']),
    keywords: JSON.stringify(['function', 'quadratic', 'graph', 'parabola', 'equation'])
};

/**
 * Create Supply and Demand Concept (Economics)
 * 
 * POST /admin/api/v1/content/db/domains/{ECONOMICS_DOMAIN_ID}/concepts
 */
export const createSupplyDemandConcept = {
    name: 'Supply and Demand',
    description: 'Core principles of market equilibrium',
    level: 'FOUNDATIONAL',
    learningObjectives: JSON.stringify([
        'Understand supply and demand curves',
        'Find market equilibrium',
        'Analyze price changes'
    ]),
    prerequisites: JSON.stringify([]),
    competencies: JSON.stringify(['economic-thinking', 'data-analysis', 'graphing']),
    keywords: JSON.stringify(['supply', 'demand', 'equilibrium', 'price', 'economics'])
};

// ============================================================================
// SIMULATION CREATION EXAMPLES (Copy-Paste Ready)
// ============================================================================

/**
 * Create Projectile Motion Simulation
 * 
 * POST /admin/api/v1/content/db/domains/{PHYSICS_DOMAIN_ID}/concepts/{KINEMATICS_CONCEPT_ID}/simulation
 */
export const createProjectileMotionSimulation = {
    type: 'physics-2D',
    manifest: {
        title: 'Projectile Motion',
        description: 'Explore how objects move through the air',
        initialVelocity: 50,
        angle: 45,
        gravity: 9.81,
        friction: 0.01,
        gridSize: 10,
        timeStep: 0.01,
        maxTime: 10
    },
    estimatedTimeMinutes: 20,
    interactivityLevel: 'high',
    purpose: 'Students will understand the relationship between initial velocity, angle, and trajectory in projectile motion.',
    previewConfig: {
        width: 800,
        height: 600,
        scale: 1.0
    }
};

/**
 * Create Water Molecule Simulation
 * 
 * POST /admin/api/v1/content/db/domains/{CHEMISTRY_DOMAIN_ID}/concepts/{MOLECULAR_STRUCTURE_CONCEPT_ID}/simulation
 */
export const createWaterMoleculeSimulation = {
    type: 'chemistry-interactive',
    manifest: {
        title: 'Water Molecule Formation',
        description: 'Observe H2O formation and bonding',
        molecule: 'H2O',
        atoms: [
            { element: 'O', count: 1, mass: 16 },
            { element: 'H', count: 2, mass: 1 }
        ],
        bonds: [
            { type: 'covalent', atoms: [0, 1] },
            { type: 'covalent', atoms: [0, 2] }
        ],
        electronegativity: {
            O: 3.44,
            H: 2.20
        },
        polarityRating: 'Highly Polar'
    },
    estimatedTimeMinutes: 15,
    interactivityLevel: 'medium',
    purpose: 'Understand molecular bonding and the structure of water molecules.',
    previewConfig: {
        width: 600,
        height: 600
    }
};

/**
 * Create Cell Division Simulation
 * 
 * POST /admin/api/v1/content/db/domains/{BIOLOGY_DOMAIN_ID}/concepts/{MITOSIS_CONCEPT_ID}/simulation
 */
export const createCellDivisionSimulation = {
    type: 'biology-interactive',
    manifest: {
        title: 'Mitosis: Cell Division Process',
        description: 'Observe the stages of cell division',
        stages: [
            {
                name: 'Prophase',
                description: 'Chromatin condenses into chromosomes, nuclear envelope breaks down',
                duration: 2.5,
                chromosomeCount: 46
            },
            {
                name: 'Metaphase',
                description: 'Chromosomes align at the metaphase plate',
                duration: 1.5,
                chromosomeCount: 46
            },
            {
                name: 'Anaphase',
                description: 'Sister chromatids separate and move to opposite poles',
                duration: 2.0,
                chromosomeCount: 92
            },
            {
                name: 'Telophase',
                description: 'Nuclear envelopes reform, chromosomes begin to decondense',
                duration: 2.0,
                chromosomeCount: 46
            }
        ],
        totalDuration: 8.0,
        speed: 1.0
    },
    estimatedTimeMinutes: 25,
    interactivityLevel: 'high',
    purpose: 'Students will be able to identify and describe the stages of mitosis and understand cell division.',
    previewConfig: {
        width: 800,
        height: 600,
        animationSpeed: 1.5
    }
};

/**
 * Create Quadratic Function Simulation
 * 
 * POST /admin/api/v1/content/db/domains/{MATHEMATICS_DOMAIN_ID}/concepts/{FUNCTIONS_CONCEPT_ID}/simulation
 */
export const createQuadraticFunctionSimulation = {
    type: 'mathematics-interactive',
    manifest: {
        title: 'Quadratic Function Explorer',
        description: 'Explore quadratic functions and their properties',
        defaultFormula: 'x^2 - 4x + 3',
        parameters: [
            {
                name: 'a',
                description: 'Leading coefficient',
                min: -5,
                max: 5,
                default: 1,
                step: 0.1
            },
            {
                name: 'b',
                description: 'Linear coefficient',
                min: -10,
                max: 10,
                default: -4,
                step: 0.1
            },
            {
                name: 'c',
                description: 'Constant term',
                min: -10,
                max: 10,
                default: 3,
                step: 0.1
            }
        ],
        domainMin: -10,
        domainMax: 10,
        rangeMin: -10,
        rangeMax: 20
    },
    estimatedTimeMinutes: 30,
    interactivityLevel: 'high',
    purpose: 'Students will explore how changes in coefficients affect the shape and position of parabolas.',
    previewConfig: {
        width: 1000,
        height: 700,
        gridSize: 1
    }
};

/**
 * Create Supply & Demand Simulation
 * 
 * POST /admin/api/v1/content/db/domains/{ECONOMICS_DOMAIN_ID}/concepts/{SUPPLY_DEMAND_CONCEPT_ID}/simulation
 */
export const createSupplyDemandSimulation = {
    type: 'mathematics-interactive',
    manifest: {
        title: 'Supply and Demand Market Equilibrium',
        description: 'Explore how supply and demand affect market price',
        supplyFunction: 'P = 0.5Q + 2',
        demandFunction: 'P = 20 - 0.3Q',
        equilibriumPrice: 11,
        equilibriumQuantity: 18,
        elasticity: {
            demand: 0.8,
            supply: 1.2
        }
    },
    estimatedTimeMinutes: 20,
    interactivityLevel: 'medium',
    purpose: 'Understand market equilibrium and how external factors affect supply and demand.',
    previewConfig: {
        width: 800,
        height: 600
    }
};

// ============================================================================
// VISUALIZATION CREATION EXAMPLES (Copy-Paste Ready)
// ============================================================================

/**
 * Create Projectile Trajectory Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{PHYSICS_DOMAIN_ID}/concepts/{KINEMATICS_CONCEPT_ID}/visualization
 */
export const createProjectileTrajectoryVisualization = {
    type: 'graph-2d',
    config: {
        title: 'Projectile Trajectory',
        xAxisLabel: 'Distance (meters)',
        yAxisLabel: 'Height (meters)',
        width: 600,
        height: 400,
        dataPoints: [
            { x: 0, y: 0 },
            { x: 10, y: 14 },
            { x: 20, y: 24 },
            { x: 30, y: 30 },
            { x: 40, y: 32 },
            { x: 50, y: 30 },
            { x: 60, y: 24 },
            { x: 70, y: 14 },
            { x: 80, y: 0 }
        ],
        gridLines: true,
        showLegend: true
    },
    dataSource: 'simulation'
};

/**
 * Create Water Molecule Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{CHEMISTRY_DOMAIN_ID}/concepts/{MOLECULAR_STRUCTURE_CONCEPT_ID}/visualization
 */
export const createWaterMoleculeVisualization = {
    type: 'molecule',
    config: {
        title: 'H2O Molecular Structure',
        atoms: [
            { symbol: 'O', x: 150, y: 150, color: '#FF6B6B', radius: 20 },
            { symbol: 'H', x: 100, y: 100, color: '#4ECDC4', radius: 15 },
            { symbol: 'H', x: 200, y: 100, color: '#4ECDC4', radius: 15 }
        ],
        bonds: [
            { from: 0, to: 1, type: 'covalent', length: 80 },
            { from: 0, to: 2, type: 'covalent', length: 80 }
        ]
    },
    dataSource: 'static'
};

/**
 * Create Mitosis Stages Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{BIOLOGY_DOMAIN_ID}/concepts/{MITOSIS_CONCEPT_ID}/visualization
 */
export const createMitosisStagesVisualization = {
    type: 'diagram',
    config: {
        title: 'Mitosis Stages',
        stages: [
            {
                name: 'Prophase',
                image: 'prophase',
                description: 'Chromosomes become visible, centrioles move to poles'
            },
            {
                name: 'Metaphase',
                image: 'metaphase',
                description: 'Chromosomes align at the cell\'s equator'
            },
            {
                name: 'Anaphase',
                image: 'anaphase',
                description: 'Chromatids separate and move to opposite poles'
            },
            {
                name: 'Telophase',
                image: 'telophase',
                description: 'Nuclear envelopes form around separated chromosomes'
            }
        ],
        layout: 'vertical',
        showLabels: true
    },
    dataSource: 'static'
};

/**
 * Create Quadratic Function Graph Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{MATHEMATICS_DOMAIN_ID}/concepts/{FUNCTIONS_CONCEPT_ID}/visualization
 */
export const createQuadraticFunctionVisualization = {
    type: 'graph-2d',
    config: {
        title: 'Quadratic Function: f(x) = x² - 4x + 3',
        xAxisLabel: 'x',
        yAxisLabel: 'f(x)',
        width: 700,
        height: 500,
        domain: [-10, 10],
        range: [-10, 20],
        functions: [
            {
                formula: 'x^2 - 4x + 3',
                color: '#3B82F6',
                lineWidth: 2
            }
        ],
        points: [
            { x: 1, y: 0, label: 'Root 1' },
            { x: 3, y: 0, label: 'Root 2' },
            { x: 2, y: -1, label: 'Vertex' }
        ],
        gridLines: true,
        showLegend: true
    },
    dataSource: 'simulation'
};

/**
 * Create Supply & Demand Equilibrium Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{ECONOMICS_DOMAIN_ID}/concepts/{SUPPLY_DEMAND_CONCEPT_ID}/visualization
 */
export const createSupplyDemandVisualization = {
    type: 'graph-2d',
    config: {
        title: 'Supply and Demand Equilibrium',
        xAxisLabel: 'Quantity',
        yAxisLabel: 'Price ($)',
        width: 700,
        height: 500,
        domain: [0, 40],
        range: [0, 25],
        functions: [
            {
                name: 'Supply',
                formula: '0.5Q + 2',
                color: '#10B981',
                lineWidth: 2
            },
            {
                name: 'Demand',
                formula: '20 - 0.3Q',
                color: '#EF4444',
                lineWidth: 2
            }
        ],
        equilibriumPoint: {
            x: 18,
            y: 11,
            label: 'Equilibrium',
            color: '#8B5CF6'
        },
        gridLines: true,
        showLegend: true
    },
    dataSource: 'simulation'
};

/**
 * Create 3D Graph Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{MATHEMATICS_DOMAIN_ID}/concepts/{FUNCTIONS_CONCEPT_ID}/visualization
 */
export const create3DGraphVisualization = {
    type: 'graph-3d',
    config: {
        title: '3D Surface: z = x² + y²',
        xAxisLabel: 'X',
        yAxisLabel: 'Y',
        zAxisLabel: 'Z',
        width: 700,
        height: 500,
        domain: [-5, 5],
        range: [-5, 5],
        zMin: 0,
        zMax: 50,
        formula: 'x^2 + y^2',
        gridSize: 0.5,
        rotation: {
            x: 45,
            y: 45,
            z: 0
        }
    },
    dataSource: 'simulation'
};

/**
 * Create Bar Chart Visualization
 * 
 * POST /admin/api/v1/content/db/domains/{MATHEMATICS_DOMAIN_ID}/concepts/ANY_CONCEPT_ID/visualization
 */
export const createBarChartVisualization = {
    type: 'chart',
    config: {
        title: 'Student Performance by Unit',
        labels: ['Unit 1', 'Unit 2', 'Unit 3', 'Unit 4', 'Unit 5'],
        dataPoints: [78, 85, 92, 88, 95],
        colors: ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6'],
        yAxisLabel: 'Score (%)',
        yMax: 100,
        showValues: true,
        showAverage: true
    },
    dataSource: 'static'
};

// ============================================================================
// EXPORT ALL EXAMPLES
// ============================================================================

export const allDomainExamples = [
    createPhysicsDomain,
    createChemistryDomain,
    createBiologyDomain,
    createMathematicsDomain,
    createEconomicsDomain
];

export const allConceptExamples = [
    createKinematicsConcept,
    createMolecularStructureConcept,
    createMitosisConcept,
    createFunctionsConcept,
    createSupplyDemandConcept
];

export const allSimulationExamples = [
    createProjectileMotionSimulation,
    createWaterMoleculeSimulation,
    createCellDivisionSimulation,
    createQuadraticFunctionSimulation,
    createSupplyDemandSimulation
];

export const allVisualizationExamples = [
    createProjectileTrajectoryVisualization,
    createWaterMoleculeVisualization,
    createMitosisStagesVisualization,
    createQuadraticFunctionVisualization,
    createSupplyDemandVisualization,
    create3DGraphVisualization,
    createBarChartVisualization
];

// ============================================================================
// CURL COMMAND TEMPLATES
// ============================================================================

export const curlCommands = {
    // Domain Creation
    createDomain: (domain: any) => `
curl -X POST http://localhost:3200/admin/api/v1/content/db/domains \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -d '${JSON.stringify(domain, null, 2)}'
  `,

    // Concept Creation
    createConcept: (domainId: string, concept: any) => `
curl -X POST http://localhost:3200/admin/api/v1/content/db/domains/${domainId}/concepts \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -d '${JSON.stringify(concept, null, 2)}'
  `,

    // Simulation Creation
    createSimulation: (domainId: string, conceptId: string, simulation: any) => `
curl -X POST http://localhost:3200/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/simulation \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -d '${JSON.stringify(simulation, null, 2)}'
  `,

    // Visualization Creation
    createVisualization: (domainId: string, conceptId: string, visualization: any) => `
curl -X POST http://localhost:3200/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/visualization \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -d '${JSON.stringify(visualization, null, 2)}'
  `
};

// ============================================================================
// JSON COPY-PASTE BLOCKS
// ============================================================================

export const jsonBlocks = {
    physics: {
        domain: JSON.stringify(createPhysicsDomain, null, 2),
        concept: JSON.stringify(createKinematicsConcept, null, 2),
        simulation: JSON.stringify(createProjectileMotionSimulation, null, 2),
        visualization: JSON.stringify(createProjectileTrajectoryVisualization, null, 2)
    },
    chemistry: {
        domain: JSON.stringify(createChemistryDomain, null, 2),
        concept: JSON.stringify(createMolecularStructureConcept, null, 2),
        simulation: JSON.stringify(createWaterMoleculeSimulation, null, 2),
        visualization: JSON.stringify(createWaterMoleculeVisualization, null, 2)
    },
    biology: {
        domain: JSON.stringify(createBiologyDomain, null, 2),
        concept: JSON.stringify(createMitosisConcept, null, 2),
        simulation: JSON.stringify(createCellDivisionSimulation, null, 2),
        visualization: JSON.stringify(createMitosisStagesVisualization, null, 2)
    },
    mathematics: {
        domain: JSON.stringify(createMathematicsDomain, null, 2),
        concept: JSON.stringify(createFunctionsConcept, null, 2),
        simulation: JSON.stringify(createQuadraticFunctionSimulation, null, 2),
        visualization: JSON.stringify(createQuadraticFunctionVisualization, null, 2)
    },
    economics: {
        domain: JSON.stringify(createEconomicsDomain, null, 2),
        concept: JSON.stringify(createSupplyDemandConcept, null, 2),
        simulation: JSON.stringify(createSupplyDemandSimulation, null, 2),
        visualization: JSON.stringify(createSupplyDemandVisualization, null, 2)
    }
};
