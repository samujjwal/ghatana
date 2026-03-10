import { createLogger } from '../utils/logger.js';
const logger = createLogger('manifest-generator');

/**
 * Simulation Manifest Generator
 *
 * Generates SimulationManifest templates from domain concept metadata.
 * Uses existing seed manifests as templates and parameterizes them.
 *
 * @doc.type module
 * @doc.purpose Generate simulation manifests from domain concepts
 * @doc.layer product
 * @doc.pattern Generator
 */

import type { DomainConcept, SimulationRequirement } from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import type {
    SimulationManifest,
    SimulationId,
    SimEntityId,
    SimStepId,
    SimEntity,
    SimulationStep,
    SimulationDomain,
    DomainMetadata,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";
import type { UserId, TenantId } from "@ghatana/tutorputor-contracts/v1/types";

// =============================================================================
// Types
// =============================================================================

/**
 * Options for manifest generation.
 */
export interface ManifestGeneratorOptions {
    /** Tenant ID */
    tenantId: string;

    /** Author ID */
    authorId: string;

    /** Base version for manifests */
    version?: string;

    /** Use placeholder content for steps */
    placeholderSteps?: boolean;

    /** Verbose logging */
    verbose?: boolean;
}

/**
 * Result of manifest generation.
 */
export interface ManifestGeneratorResult {
    /** Generated manifest */
    manifest: SimulationManifest;

    /** Template used */
    templateType: string;

    /** Whether placeholder content was used */
    isPlaceholder: boolean;

    /** Warnings */
    warnings: string[];
}

// =============================================================================
// Helper Functions for Branded Types
// =============================================================================

const simId = (id: string): SimulationId => id as unknown as SimulationId;
const entityId = (id: string): SimEntityId => id as unknown as SimEntityId;
const stepId = (id: string): SimStepId => id as unknown as SimStepId;
const userId = (id: string): UserId => id as unknown as UserId;
const tenantIdFn = (id: string): TenantId => id as unknown as TenantId;

// =============================================================================
// Main Generator
// =============================================================================

/**
 * Generate a SimulationManifest template for a domain concept.
 *
 * Strategy:
 * 1. Identify the simulation type from concept metadata
 * 2. Select an appropriate template based on domain and type
 * 3. Parameterize the template with concept-specific data
 * 4. Generate placeholder steps if full steps not available
 */
export function generateManifestFromConcept(
    concept: DomainConcept,
    options: ManifestGeneratorOptions
): ManifestGeneratorResult {
    const warnings: string[] = [];
    const domain = concept.domain as SimulationDomain;
    const simType = concept.simulationMetadata.simulationType;

    // Determine template type and generate entities/steps
    const templateInfo = selectTemplate(domain, simType);
    const { entities, steps } = generateTemplateContent(concept, templateInfo);

    // Generate unique manifest ID
    const manifestId = generateManifestId(concept);

    const manifest: SimulationManifest = {
        id: simId(manifestId),
        version: options.version || "1.0.0",
        schemaVersion: "1.0.0",
        domain,
        title: concept.name,
        description: `${concept.simulationMetadata.purpose}\n\n${concept.description}`,
        authorId: userId(options.authorId),
        tenantId: tenantIdFn(options.tenantId),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),

        domainMetadata: generateDomainMetadata(concept),

        canvas: {
            width: templateInfo.canvasWidth,
            height: templateInfo.canvasHeight,
            backgroundColor: templateInfo.backgroundColor,
        },

        playback: {
            defaultSpeed: getDefaultSpeed(concept),
            allowScrubbing: true,
            loop: false,
        },

        initialEntities: entities,
        steps,
    };

    if (options.verbose) {
        logger.info({}, `Generated manifest ${manifestId} for concept ${concept.id} using template ${templateInfo.type}`);
    }

    return {
        manifest,
        templateType: templateInfo.type,
        isPlaceholder: options.placeholderSteps ?? true,
        warnings,
    };
}

/**
 * Generate manifests for multiple concepts.
 */
export function generateManifestsFromConcepts(
    concepts: DomainConcept[],
    options: ManifestGeneratorOptions
): Map<string, ManifestGeneratorResult> {
    const results = new Map<string, ManifestGeneratorResult>();

    for (const concept of concepts) {
        if (!concept.simulationMetadata.simulationType) continue;

        const reqs = concept.simulationMetadata.requirements ?? [];
        if (reqs.length === 0) {
            const result = generateManifestFromConcept(concept, options);
            results.set(concept.id, result);
            continue;
        }

        for (const req of reqs) {
            const result = generateManifestFromConceptRequirement(concept, req, options);
            results.set(`${concept.id}::${req.id}`, result);
        }
    }

    return results;
}

function generateManifestFromConceptRequirement(
    concept: DomainConcept,
    req: SimulationRequirement,
    options: ManifestGeneratorOptions
): ManifestGeneratorResult {
    const base = generateManifestFromConcept(concept, options);
    const manifest = base.manifest;

    const suffix = sanitizeIdPart(req.id);
    const baseId = generateManifestId(concept);

    manifest.id = simId(`${baseId}-${suffix}`);
    manifest.title = `${concept.name} (${req.role})`;

    return {
        ...base,
        manifest,
    };
}

function sanitizeIdPart(id: string): string {
    return id
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 40);
}

// =============================================================================
// Template Selection
// =============================================================================

interface TemplateInfo {
    type: string;
    canvasWidth: number;
    canvasHeight: number;
    backgroundColor: string;
    entityTypes: string[];
}

/**
 * Select appropriate template based on domain and simulation type.
 */
function selectTemplate(domain: SimulationDomain, simType: string): TemplateInfo {
    const simTypeLower = simType.toLowerCase();

    // Physics templates
    if (domain === "PHYSICS") {
        if (simTypeLower.includes("motion") || simTypeLower.includes("kinematic") || simTypeLower.includes("projectile")) {
            return {
                type: "projectile_motion",
                canvasWidth: 1000,
                canvasHeight: 600,
                backgroundColor: "#e0f2fe",
                entityTypes: ["rigidBody", "vector"],
            };
        }
        if (simTypeLower.includes("harmonic") || simTypeLower.includes("oscillat") || simTypeLower.includes("spring")) {
            return {
                type: "harmonic_oscillator",
                canvasWidth: 900,
                canvasHeight: 600,
                backgroundColor: "#f0fdf4",
                entityTypes: ["rigidBody", "spring", "vector"],
            };
        }
        if (simTypeLower.includes("incline") || simTypeLower.includes("force") || simTypeLower.includes("friction")) {
            return {
                type: "inclined_plane",
                canvasWidth: 900,
                canvasHeight: 600,
                backgroundColor: "#f0fdf4",
                entityTypes: ["rigidBody", "vector"],
            };
        }
        if (simTypeLower.includes("wave") || simTypeLower.includes("optic")) {
            return {
                type: "wave_physics",
                canvasWidth: 1000,
                canvasHeight: 500,
                backgroundColor: "#fef3c7",
                entityTypes: ["particle", "vector"],
            };
        }
        if (simTypeLower.includes("electric") || simTypeLower.includes("magnetic") || simTypeLower.includes("circuit")) {
            return {
                type: "electromagnetic",
                canvasWidth: 900,
                canvasHeight: 600,
                backgroundColor: "#fce7f3",
                entityTypes: ["particle", "vector", "field"],
            };
        }
        if (simTypeLower.includes("thermo") || simTypeLower.includes("heat") || simTypeLower.includes("energy")) {
            return {
                type: "thermodynamics",
                canvasWidth: 900,
                canvasHeight: 600,
                backgroundColor: "#fef2f2",
                entityTypes: ["particle", "container"],
            };
        }
        // Default physics template
        return {
            type: "physics_generic",
            canvasWidth: 900,
            canvasHeight: 600,
            backgroundColor: "#e0f2fe",
            entityTypes: ["rigidBody", "vector"],
        };
    }

    // Chemistry templates
    if (domain === "CHEMISTRY") {
        if (simTypeLower.includes("reaction") || simTypeLower.includes("substitution")) {
            return {
                type: "chemical_reaction",
                canvasWidth: 900,
                canvasHeight: 500,
                backgroundColor: "#fefce8",
                entityTypes: ["atom", "bond", "molecule"],
            };
        }
        if (simTypeLower.includes("equilibrium") || simTypeLower.includes("dynamic")) {
            return {
                type: "chemical_equilibrium",
                canvasWidth: 900,
                canvasHeight: 500,
                backgroundColor: "#ecfdf5",
                entityTypes: ["molecule", "arrow"],
            };
        }
        if (simTypeLower.includes("orbital") || simTypeLower.includes("electron") || simTypeLower.includes("quantum")) {
            return {
                type: "atomic_orbital",
                canvasWidth: 800,
                canvasHeight: 600,
                backgroundColor: "#f0f9ff",
                entityTypes: ["atom", "orbital"],
            };
        }
        if (simTypeLower.includes("bond") || simTypeLower.includes("structure") || simTypeLower.includes("molecular")) {
            return {
                type: "molecular_structure",
                canvasWidth: 800,
                canvasHeight: 600,
                backgroundColor: "#fffbeb",
                entityTypes: ["atom", "bond"],
            };
        }
        // Default chemistry template
        return {
            type: "chemistry_generic",
            canvasWidth: 900,
            canvasHeight: 500,
            backgroundColor: "#fefce8",
            entityTypes: ["atom", "bond", "molecule"],
        };
    }

    // Biology templates
    if (domain === "BIOLOGY") {
        return {
            type: "biology_generic",
            canvasWidth: 900,
            canvasHeight: 600,
            backgroundColor: "#ecfdf5",
            entityTypes: ["cell", "organelle"],
        };
    }

    // Default template
    return {
        type: "generic",
        canvasWidth: 900,
        canvasHeight: 600,
        backgroundColor: "#f8fafc",
        entityTypes: ["node"],
    };
}

// =============================================================================
// Content Generation
// =============================================================================

/**
 * Generate template-specific entities and steps.
 */
function generateTemplateContent(
    concept: DomainConcept,
    template: TemplateInfo,
): { entities: SimEntity[]; steps: SimulationStep[] } {
    // Generate domain-specific content
    switch (template.type) {
        case "projectile_motion":
            return generateProjectileMotionContent(concept);
        case "harmonic_oscillator":
            return generateHarmonicOscillatorContent(concept);
        case "inclined_plane":
            return generateInclinedPlaneContent(concept);
        case "chemical_reaction":
            return generateChemicalReactionContent(concept);
        case "molecular_structure":
            return generateMolecularStructureContent(concept);
        default:
            return generateGenericContent(concept, template);
    }
}

/**
 * Generate projectile motion entities and steps.
 */
function generateProjectileMotionContent(concept: DomainConcept): { entities: SimEntity[]; steps: SimulationStep[] } {
    const entities: SimEntity[] = [
        {
            id: entityId("projectile"),
            type: "rigidBody",
            x: 50,
            y: 500,
            mass: 1,
            velocityX: 20,
            velocityY: -15,
            shape: "circle",
            color: "#ef4444",
            label: "Object",
        } as unknown as SimEntity,
        {
            id: entityId("velocity-vector"),
            type: "vector",
            x: 50,
            y: 500,
            magnitude: 25,
            angle: -36.87,
            vectorType: "velocity",
            color: "#3b82f6",
            label: "v",
        } as unknown as SimEntity,
        {
            id: entityId("ground"),
            type: "rigidBody",
            x: 0,
            y: 550,
            mass: 0,
            width: 1000,
            height: 50,
            fixed: true,
            shape: "rect",
            color: "#84cc16",
        } as unknown as SimEntity,
    ];

    const steps = generateConceptSteps(concept, [
        "Initial Setup",
        "Velocity Components",
        "Launch",
        "Rising Phase",
        "Maximum Height",
        "Falling Phase",
        "Landing",
    ]);

    return { entities, steps };
}

/**
 * Generate chemical reaction entities and steps.
 */
function generateChemicalReactionContent(concept: DomainConcept): { entities: SimEntity[]; steps: SimulationStep[] } {
    const entities: SimEntity[] = [
        {
            id: entityId("reactant-1"),
            type: "molecule",
            x: 250,
            y: 250,
            color: "#3b82f6",
            label: "Reactant A",
        } as unknown as SimEntity,
        {
            id: entityId("reactant-2"),
            type: "molecule",
            x: 400,
            y: 250,
            color: "#22c55e",
            label: "Reactant B",
        } as unknown as SimEntity,
        {
            id: entityId("product"),
            type: "molecule",
            x: 650,
            y: 250,
            color: "#f59e0b",
            label: "Product",
            visible: false,
        } as unknown as SimEntity,
        {
            id: entityId("reaction-arrow"),
            type: "arrow",
            x: 500,
            y: 250,
            width: 100,
            color: "#64748b",
            label: "→",
        } as unknown as SimEntity,
    ];

    const steps = generateConceptSteps(concept, [
        "Identify reactants",
        "Reaction conditions",
        "Bond rearrangement",
        "Product formation",
        "Check conservation",
    ]);

    return { entities, steps };
}

/**
 * Generate molecular structure entities and steps.
 */
function generateMolecularStructureContent(concept: DomainConcept): { entities: SimEntity[]; steps: SimulationStep[] } {
    const entities: SimEntity[] = [
        {
            id: entityId("central-atom"),
            type: "atom",
            x: 500,
            y: 250,
            element: "C",
            color: "#374151",
            label: "Central Atom",
        } as unknown as SimEntity,
        {
            id: entityId("atom-1"),
            type: "atom",
            x: 420,
            y: 180,
            element: "H",
            color: "#e5e7eb",
            label: "H",
        } as unknown as SimEntity,
        {
            id: entityId("atom-2"),
            type: "atom",
            x: 580,
            y: 180,
            element: "H",
            color: "#e5e7eb",
            label: "H",
        } as unknown as SimEntity,
        {
            id: entityId("bond-1"),
            type: "bond",
            atom1Id: entityId("central-atom"),
            atom2Id: entityId("atom-1"),
            bondOrder: 1,
        } as unknown as SimEntity,
        {
            id: entityId("bond-2"),
            type: "bond",
            atom1Id: entityId("central-atom"),
            atom2Id: entityId("atom-2"),
            bondOrder: 1,
        } as unknown as SimEntity,
    ];

    const steps = generateConceptSteps(concept, [
        "Choose central atom",
        "Add surrounding atoms",
        "Create bonds",
        "Check geometry",
        "Review polarity",
    ]);

    return { entities, steps };
}

/**
 * Generate harmonic oscillator entities and steps.
 */
function generateHarmonicOscillatorContent(concept: DomainConcept): { entities: SimEntity[]; steps: SimulationStep[] } {
    const entities: SimEntity[] = [
        {
            id: entityId("anchor"),
            type: "rigidBody",
            x: 450,
            y: 100,
            mass: 0,
            fixed: true,
            shape: "rect",
            width: 100,
            height: 20,
            color: "#374151",
            label: "Fixed Support",
        } as unknown as SimEntity,
        {
            id: entityId("mass"),
            type: "rigidBody",
            x: 450,
            y: 300,
            mass: 2,
            shape: "circle",
            color: "#3b82f6",
            label: "m",
        } as unknown as SimEntity,
        {
            id: entityId("spring"),
            type: "spring",
            x: 450,
            y: 200,
            anchorId: entityId("anchor"),
            attachId: entityId("mass"),
            stiffness: 50,
            damping: 0,
            restLength: 150,
            color: "#6b7280",
        } as unknown as SimEntity,
    ];

    const steps = generateConceptSteps(concept, [
        "System at Equilibrium",
        "Displacing the Mass",
        "Release and Acceleration",
        "Passing Through Equilibrium",
        "Maximum Displacement (Opposite)",
        "Oscillation Complete",
    ]);

    return { entities, steps };
}

/**
 * Generate inclined plane entities and steps.
 */
function generateInclinedPlaneContent(concept: DomainConcept): { entities: SimEntity[]; steps: SimulationStep[] } {
    const entities: SimEntity[] = [
        {
            id: entityId("block"),
            type: "rigidBody",
            x: 200,
            y: 300,
            mass: 5,
            shape: "rect",
            width: 60,
            height: 40,
            color: "#3b82f6",
            label: "Block",
        },
        {
            id: entityId("concept-node"),
            type: "rigidBody",
            x: 100,
            y: 400,
            mass: 0,
            fixed: true,
            shape: "polygon",
            color: "#64748b",
            label: "Incline",
        },
        {
            id: entityId("weight-vector"),
            type: "vector",
            x: 230,
            y: 320,
            magnitude: 49,
            angle: 90,
            vectorType: "force",
            color: "#ef4444",
            label: "W = mg",
        },
        {
            id: entityId("normal-vector"),
            type: "vector",
            x: 230,
            y: 320,
            magnitude: 42.4,
            angle: -60,
            vectorType: "force",
            color: "#22c55e",
            label: "N",
        },
    ];

    const steps = generateConceptSteps(concept, [
        "Problem Setup",
        "Weight Force",
        "Decompose Weight",
        "Normal Force",
        "Without Friction",
        "With Friction",
        "Net Force Analysis",
    ]);

    return { entities, steps };
}

/**
 * Generate generic content for any domain.
 */
function generateGenericContent(concept: DomainConcept, template: TemplateInfo): { entities: SimEntity[]; steps: SimulationStep[] } {
    const entities: SimEntity[] = [
        {
            id: entityId("main-entity"),
            type: template.entityTypes[0] || "node",
            x: template.canvasWidth / 2,
            y: template.canvasHeight / 2,
            color: "#3b82f6",
            label: concept.name,
        } as unknown as SimEntity,
    ];

    const steps = generateConceptSteps(concept, [
        "Introduction",
        "Key Concept",
        "Demonstration",
        "Analysis",
        "Conclusion",
    ]);

    return { entities, steps };
}

/**
 * Generate steps from concept objectives.
 */
function generateConceptSteps(concept: DomainConcept, defaultTitles: string[]): SimulationStep[] {
    const steps: SimulationStep[] = [];
    const objectives = concept.pedagogicalMetadata.learningObjectives;

    // Use objectives as step descriptions if available
    const titles = objectives.length >= 3
        ? objectives.map((obj: string, i: number) => `Step ${i + 1}: ${obj.split(" ").slice(0, 4).join(" ")}...`)
        : defaultTitles;

    for (let i = 0; i < titles.length; i++) {
        steps.push({
            id: stepId(`step-${i}`),
            orderIndex: i,
            title: titles[i],
            description: objectives[i] || `Step ${i + 1} of the ${concept.name} simulation.`,
            actions: [
                { action: "HIGHLIGHT", targetIds: [], style: "primary" },
            ],
            narration: objectives[i] || titles[i],
            checkpoint: i === Math.floor(titles.length / 2),
        });
    }

    return steps;
}

// =============================================================================
// Metadata Generation
// =============================================================================

/**
 * Generate domain-specific metadata.
 */
function generateDomainMetadata(concept: DomainConcept): DomainMetadata {
    const domain = concept.domain;
    if (domain === "PHYSICS") {
        return {
            domain: "PHYSICS",
            physics: {
                gravity: { x: 0, y: 9.81 },
                units: {
                    length: "m",
                    mass: "kg",
                    time: "s",
                },
            },
        };
    }

    if (domain === "CHEMISTRY") {
        return {
            domain: "CHEMISTRY",
            chemistry: {
                reactionType: inferReactionType(concept),
                conditions: {
                    temperature: 298,
                    pressure: 1,
                },
            },
        };
    }

    if (domain === "BIOLOGY") {
        return {
            domain: "BIOLOGY",
            biology: {
                scale: "cellular",
            },
        };
    }

    if (domain === "MEDICINE") {
        return {
            domain: "MEDICINE",
            medicine: {
                modelType: "sir",
            },
        };
    }

    if (domain === "ECONOMICS") {
        return {
            domain: "ECONOMICS",
            economics: {
                timeStep: 1,
                integrationMethod: "euler",
                simulationDuration: 100,
            },
        };
    }

    if (domain === "CS_DISCRETE") return { domain: "CS_DISCRETE" };
    if (domain === "ENGINEERING") return { domain: "ENGINEERING" };
    if (domain === "MATHEMATICS") return { domain: "MATHEMATICS" };

    return { domain: "MATHEMATICS" };
}

/**
 * Infer reaction type from concept keywords.
 */
function inferReactionType(concept: DomainConcept): "substitution" | "elimination" | "addition" | "oxidation" | "reduction" | "acid_base" | "combustion" {
    const keywords = concept.keywords.join(" ").toLowerCase();

    if (keywords.includes("substitution")) return "substitution";
    if (keywords.includes("addition")) return "addition";
    if (keywords.includes("elimination")) return "elimination";
    if (keywords.includes("oxidation") || keywords.includes("redox")) return "oxidation";
    if (keywords.includes("equilibrium")) return "addition";
    if (keywords.includes("acid") || keywords.includes("base")) return "acid_base";

    return "addition";
}

/**
 * Get default playback speed based on concept difficulty.
 */
function getDefaultSpeed(concept: DomainConcept): number {
    const difficulty = concept.learningObjectMetadata.difficulty;

    switch (difficulty) {
        case "INTRO":
            return 1.0;
        case "INTERMEDIATE":
            return 0.75;
        case "ADVANCED":
            return 0.5;
        default:
            return 1.0;
    }
}

/**
 * Generate a unique manifest ID for a concept.
 */
function generateManifestId(concept: DomainConcept): string {
    const slug = concept.name
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-|-$/g, "")
        .slice(0, 50);

    return `${concept.id}-${slug}`;
}
