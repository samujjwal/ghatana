/**
 * Sample Simulation & Visualization Test Data
 * 
 * This file contains example data for testing the simulation and visualization endpoints.
 * Use these examples to populate test data or validate the API responses.
 * 
 * @doc.type data
 * @doc.purpose Test data for simulations and visualizations
 * @doc.layer product
 */

// ============================================================================
// EXAMPLE 1: Physics 2D Simulation - Projectile Motion
// ============================================================================

export const projectileMotionSimulation = {
    type: "physics-2D",
    manifest: {
        title: "Projectile Motion",
        description: "Explore how objects move through the air",
        initialVelocity: 50,
        angle: 45,
        gravity: 9.81,
        friction: 0.01,
        gridSize: 10,
        timeStep: 0.01,
        maxTime: 10
    },
    estimatedTimeMinutes: 20,
    interactivityLevel: "high",
    purpose: "Students will understand the relationship between initial velocity, angle, and trajectory in projectile motion.",
    previewConfig: {
        width: 800,
        height: 600,
        scale: 1.0
    }
};

export const projectileMotionVisualization = {
    type: "graph-2d",
    config: {
        title: "Projectile Trajectory",
        xAxisLabel: "Distance (meters)",
        yAxisLabel: "Height (meters)",
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
    dataSource: "simulation"
};

// ============================================================================
// EXAMPLE 2: Chemistry Interactive - Molecular Structure
// ============================================================================

export const waterMoleculeSimulation = {
    type: "chemistry-interactive",
    manifest: {
        title: "Water Molecule Formation",
        description: "Observe H2O formation and bonding",
        molecule: "H2O",
        atoms: [
            { element: "O", count: 1, mass: 16 },
            { element: "H", count: 2, mass: 1 }
        ],
        bonds: [
            { type: "covalent", atoms: [0, 1] },
            { type: "covalent", atoms: [0, 2] }
        ],
        electronegativity: {
            O: 3.44,
            H: 2.20
        },
        polarityRating: "Highly Polar"
    },
    estimatedTimeMinutes: 15,
    interactivityLevel: "medium",
    purpose: "Understand molecular bonding and the structure of water molecules.",
    previewConfig: {
        width: 600,
        height: 600
    }
};

export const waterMoleculeVisualization = {
    type: "molecule",
    config: {
        title: "H2O Molecular Structure",
        atoms: [
            { symbol: "O", x: 150, y: 150, color: "#FF6B6B", radius: 20 },
            { symbol: "H", x: 100, y: 100, color: "#4ECDC4", radius: 15 },
            { symbol: "H", x: 200, y: 100, color: "#4ECDC4", radius: 15 }
        ],
        bonds: [
            { from: 0, to: 1, type: "covalent", length: 80 },
            { from: 0, to: 2, type: "covalent", length: 80 }
        ]
    },
    dataSource: "static"
};

// ============================================================================
// EXAMPLE 3: Biology Interactive - Cell Division
// ============================================================================

export const cellDivisionSimulation = {
    type: "biology-interactive",
    manifest: {
        title: "Mitosis: Cell Division Process",
        description: "Observe the stages of cell division",
        stages: [
            {
                name: "Prophase",
                description: "Chromatin condenses into chromosomes, nuclear envelope breaks down",
                duration: 2.5,
                chromosomeCount: 46
            },
            {
                name: "Metaphase",
                description: "Chromosomes align at the metaphase plate",
                duration: 1.5,
                chromosomeCount: 46
            },
            {
                name: "Anaphase",
                description: "Sister chromatids separate and move to opposite poles",
                duration: 2.0,
                chromosomeCount: 92
            },
            {
                name: "Telophase",
                description: "Nuclear envelopes reform, chromosomes begin to decondense",
                duration: 2.0,
                chromosomeCount: 46
            }
        ],
        totalDuration: 8.0,
        speed: 1.0
    },
    estimatedTimeMinutes: 25,
    interactivityLevel: "high",
    purpose: "Students will be able to identify and describe the stages of mitosis and understand cell division.",
    previewConfig: {
        width: 800,
        height: 600,
        animationSpeed: 1.5
    }
};

export const cellDivisionVisualization = {
    type: "diagram",
    config: {
        title: "Mitosis Stages",
        stages: [
            {
                name: "Prophase",
                image: "prophase",
                description: "Chromosomes become visible, centrioles move to poles"
            },
            {
                name: "Metaphase",
                image: "metaphase",
                description: "Chromosomes align at the cell's equator"
            },
            {
                name: "Anaphase",
                image: "anaphase",
                description: "Chromatids separate and move to opposite poles"
            },
            {
                name: "Telophase",
                image: "telophase",
                description: "Nuclear envelopes form around separated chromosomes"
            }
        ],
        layout: "vertical",
        showLabels: true
    },
    dataSource: "static"
};

// ============================================================================
// EXAMPLE 4: Mathematics Interactive - Function Grapher
// ============================================================================

export const quadraticFunctionSimulation = {
    type: "mathematics-interactive",
    manifest: {
        title: "Quadratic Function Explorer",
        description: "Explore quadratic functions and their properties",
        defaultFormula: "x^2 - 4x + 3",
        parameters: [
            {
                name: "a",
                description: "Leading coefficient",
                min: -5,
                max: 5,
                default: 1,
                step: 0.1
            },
            {
                name: "b",
                description: "Linear coefficient",
                min: -10,
                max: 10,
                default: -4,
                step: 0.1
            },
            {
                name: "c",
                description: "Constant term",
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
    interactivityLevel: "high",
    purpose: "Students will explore how changes in coefficients affect the shape and position of parabolas.",
    previewConfig: {
        width: 1000,
        height: 700,
        gridSize: 1
    }
};

export const quadraticFunctionVisualization = {
    type: "graph-2d",
    config: {
        title: "Quadratic Function: f(x) = x² - 4x + 3",
        xAxisLabel: "x",
        yAxisLabel: "f(x)",
        width: 700,
        height: 500,
        domain: [-10, 10],
        range: [-10, 20],
        functions: [
            {
                formula: "x^2 - 4x + 3",
                color: "#3B82F6",
                lineWidth: 2
            }
        ],
        points: [
            { x: 1, y: 0, label: "Root 1" },
            { x: 3, y: 0, label: "Root 2" },
            { x: 2, y: -1, label: "Vertex" }
        ],
        gridLines: true,
        showLegend: true
    },
    dataSource: "simulation"
};

// ============================================================================
// EXAMPLE 5: Economics - Supply & Demand Curves
// ============================================================================

export const supplyDemandSimulation = {
    type: "mathematics-interactive",
    manifest: {
        title: "Supply and Demand Market Equilibrium",
        description: "Explore how supply and demand affect market price",
        supplyFunction: "P = 0.5Q + 2",
        demandFunction: "P = 20 - 0.3Q",
        equilibriumPrice: 11,
        equilibriumQuantity: 18,
        elasticity: {
            demand: 0.8,
            supply: 1.2
        }
    },
    estimatedTimeMinutes: 20,
    interactivityLevel: "medium",
    purpose: "Understand market equilibrium and how external factors affect supply and demand.",
    previewConfig: {
        width: 800,
        height: 600
    }
};

export const supplyDemandVisualization = {
    type: "graph-2d",
    config: {
        title: "Supply and Demand Equilibrium",
        xAxisLabel: "Quantity",
        yAxisLabel: "Price ($)",
        width: 700,
        height: 500,
        domain: [0, 40],
        range: [0, 25],
        functions: [
            {
                name: "Supply",
                formula: "0.5Q + 2",
                color: "#10B981",
                lineWidth: 2
            },
            {
                name: "Demand",
                formula: "20 - 0.3Q",
                color: "#EF4444",
                lineWidth: 2
            }
        ],
        equilibriumPoint: {
            x: 18,
            y: 11,
            label: "Equilibrium",
            color: "#8B5CF6"
        },
        gridLines: true,
        showLegend: true
    },
    dataSource: "simulation"
};

// ============================================================================
// EXAMPLE 6: Bar Chart - Student Performance
// ============================================================================

export const studentPerformanceVisualization = {
    type: "chart",
    config: {
        title: "Student Performance by Unit",
        labels: ["Unit 1", "Unit 2", "Unit 3", "Unit 4", "Unit 5"],
        dataPoints: [78, 85, 92, 88, 95],
        colors: ["#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6"],
        yAxisLabel: "Score (%)",
        yMax: 100,
        showValues: true,
        showAverage: true
    },
    dataSource: "static"
};

// ============================================================================
// EXAMPLE 7: 3D Graph Visualization
// ============================================================================

export const threeDGraphVisualization = {
    type: "graph-3d",
    config: {
        title: "3D Surface: z = x² + y²",
        xAxisLabel: "X",
        yAxisLabel: "Y",
        zAxisLabel: "Z",
        width: 700,
        height: 500,
        domain: [-5, 5],
        range: [-5, 5],
        zMin: 0,
        zMax: 50,
        formula: "x^2 + y^2",
        gridSize: 0.5,
        rotation: {
            x: 45,
            y: 45,
            z: 0
        }
    },
    dataSource: "simulation"
};

// ============================================================================
// HELPER FUNCTIONS FOR API TESTING
// ============================================================================

/**
 * Creates the complete request payload for creating a simulation
 */
export function createSimulationPayload(
    simulation: any,
    previewConfig?: any
) {
    return {
        ...simulation,
        previewConfig: previewConfig || simulation.previewConfig
    };
}

/**
 * Creates the complete request payload for creating a visualization
 */
export function createVisualizationPayload(visualization: any) {
    return visualization;
}

/**
 * Sample curl commands for testing endpoints
 */
export const curlExamples = {
    createSimulation: `
curl -X POST http://localhost:3200/admin/api/v1/content/db/domains/DOMAIN_ID/concepts/CONCEPT_ID/simulation \\
  -H "Content-Type: application/json" \\
  -d '{
    "type": "physics-2D",
    "manifest": ${JSON.stringify(projectileMotionSimulation.manifest)},
    "estimatedTimeMinutes": 20,
    "interactivityLevel": "high",
    "purpose": "Students will understand projectile motion",
    "previewConfig": ${JSON.stringify(projectileMotionSimulation.previewConfig)}
  }'
  `,

    getSimulation: `
curl http://localhost:3200/admin/api/v1/content/db/domains/DOMAIN_ID/concepts/CONCEPT_ID/simulation
  `,

    createVisualization: `
curl -X POST http://localhost:3200/admin/api/v1/content/db/domains/DOMAIN_ID/concepts/CONCEPT_ID/visualization \\
  -H "Content-Type: application/json" \\
  -d '{
    "type": "graph-2d",
    "config": ${JSON.stringify(projectileMotionVisualization.config)},
    "dataSource": "simulation"
  }'
  `,

    getVisualization: `
curl http://localhost:3200/admin/api/v1/content/db/domains/DOMAIN_ID/concepts/CONCEPT_ID/visualization
  `,

    deleteSimulation: `
curl -X DELETE http://localhost:3200/admin/api/v1/content/db/domains/DOMAIN_ID/concepts/CONCEPT_ID/simulation
  `,

    deleteVisualization: `
curl -X DELETE http://localhost:3200/admin/api/v1/content/db/domains/DOMAIN_ID/concepts/CONCEPT_ID/visualization
  `
};

// ============================================================================
// EXPORT ALL EXAMPLES
// ============================================================================

export const allExamples = [
    {
        name: "Projectile Motion",
        domain: "PHYSICS",
        concept: "Kinematics",
        simulation: projectileMotionSimulation,
        visualization: projectileMotionVisualization
    },
    {
        name: "Water Molecule",
        domain: "CHEMISTRY",
        concept: "Molecular Structure",
        simulation: waterMoleculeSimulation,
        visualization: waterMoleculeVisualization
    },
    {
        name: "Cell Division",
        domain: "BIOLOGY",
        concept: "Mitosis",
        simulation: cellDivisionSimulation,
        visualization: cellDivisionVisualization
    },
    {
        name: "Quadratic Functions",
        domain: "MATHEMATICS",
        concept: "Functions",
        simulation: quadraticFunctionSimulation,
        visualization: quadraticFunctionVisualization
    },
    {
        name: "Supply & Demand",
        domain: "ECONOMICS",
        concept: "Market Equilibrium",
        simulation: supplyDemandSimulation,
        visualization: supplyDemandVisualization
    }
];
