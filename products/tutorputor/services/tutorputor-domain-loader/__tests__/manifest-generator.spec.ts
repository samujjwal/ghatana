/**
 * Manifest Generator Tests
 *
 * @doc.type test
 * @doc.purpose Test simulation manifest generation from domain concepts
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import { generateManifestFromConcept, generateManifestsFromConcepts } from "../src/generators/manifest-generator";
import type { DomainConcept, ConceptId } from "@ghatana/tutorputor-contracts/v1/curriculum/types";

// =============================================================================
// Mock Data
// =============================================================================

const createMockConcept = (overrides: Partial<DomainConcept> = {}): DomainConcept => ({
    id: "phy_F_1" as ConceptId,
    name: "Scalars and Vectors",
    description: "Understand what physical quantities have only magnitude vs magnitude + direction.",
    domain: "PHYSICS",
    level: "FOUNDATIONAL",
    prerequisites: [] as ConceptId[],
    audienceTags: ["K-12", "Independent_Study"],
    keywords: ["scalar", "vector", "magnitude", "direction"],
    simulationMetadata: {
        simulationType: "interactive_visualization",
        recommendedInteractivity: "low",
        purpose: "Visualize difference between scalar vs vector",
        estimatedTimeMinutes: 10,
    },
    crossDomainLinks: [],
    learningObjectMetadata: {
        author: "TutorPutor",
        version: "1.0.0",
        status: "published",
        intendedRoles: ["student"],
        contexts: ["K-12", "Independent_Study"],
        difficulty: "INTRO",
        typicalLearningTimeMinutes: 20,
        learningObjectType: "lesson",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    pedagogicalMetadata: {
        learningObjectives: [
            "Define scalar and vector quantities",
            "Identify examples of scalars and vectors",
        ],
        competencies: ["scalar_analysis", "vector_analysis"],
        scaffoldingLevel: "standalone",
        accessibilityNotes: "Visual simulation with text descriptions",
    },
    ...overrides,
});

const defaultOptions = {
    tenantId: "tenant-123",
    authorId: "author-456",
};

// =============================================================================
// Tests
// =============================================================================

describe("Manifest Generator", () => {
    describe("generateManifestFromConcept", () => {
        it("should generate a valid simulation manifest", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest).toBeDefined();
            expect(result.manifest.id).toBeDefined();
            expect(result.manifest.title).toBe(concept.name);
            expect(result.manifest.domain).toBe("PHYSICS");
            expect(result.templateType).toBeDefined();
            expect(result.warnings).toEqual([]);
        });

        it("should include concept description in manifest description", () => {
            const concept = createMockConcept({
                description: "Test description for manifest",
                simulationMetadata: {
                    ...createMockConcept().simulationMetadata,
                    purpose: "Test purpose",
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.description).toContain("Test description for manifest");
            expect(result.manifest.description).toContain("Test purpose");
        });

        it("should set correct author and tenant IDs", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, {
                tenantId: "my-tenant",
                authorId: "my-author",
            });

            expect(String(result.manifest.authorId)).toBe("my-author");
            expect(String(result.manifest.tenantId)).toBe("my-tenant");
        });

        it("should use provided version", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, {
                ...defaultOptions,
                version: "2.0.0",
            });

            expect(result.manifest.version).toBe("2.0.0");
        });

        it("should default version to 1.0.0", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.version).toBe("1.0.0");
        });

        it("should select projectile motion template for kinematic concepts", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "projectile_motion",
                    recommendedInteractivity: "high",
                    purpose: "Simulate projectile trajectory",
                    estimatedTimeMinutes: 15,
                },
                keywords: ["projectile", "trajectory", "motion"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.templateType).toBe("projectile_motion");
        });

        it("should select harmonic oscillator template for spring/oscillation concepts", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "harmonic_oscillation",
                    recommendedInteractivity: "medium",
                    purpose: "Simulate spring mass system",
                    estimatedTimeMinutes: 20,
                },
                keywords: ["spring", "oscillation", "harmonic"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.templateType).toBe("harmonic_oscillator");
        });

        it("should select chemical_reaction template for chemistry substitution concepts", () => {
            const concept = createMockConcept({
                domain: "CHEMISTRY",
                simulationMetadata: {
                    simulationType: "sn2_reaction",
                    recommendedInteractivity: "high",
                    purpose: "Visualize nucleophilic substitution",
                    estimatedTimeMinutes: 25,
                },
                keywords: ["SN2", "nucleophilic", "substitution"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            // SN2 substitution maps to chemical_reaction template
            expect(result.templateType).toBe("chemical_reaction");
        });

        it("should select chemistry_generic template for combustion concepts", () => {
            const concept = createMockConcept({
                domain: "CHEMISTRY",
                simulationMetadata: {
                    simulationType: "combustion",
                    recommendedInteractivity: "medium",
                    purpose: "Visualize combustion reaction",
                    estimatedTimeMinutes: 15,
                },
                keywords: ["combustion", "oxidation", "fire"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            // Combustion falls back to chemistry_generic since no specific match
            expect(result.templateType).toBe("chemistry_generic");
        });

        it("should use domain-specific generic template for unknown simulation types", () => {
            const concept = createMockConcept({
                domain: "BIOLOGY",
                simulationMetadata: {
                    simulationType: "cell_division",
                    recommendedInteractivity: "low",
                    purpose: "Visualize mitosis",
                    estimatedTimeMinutes: 30,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            // Unknown biology simulation uses biology_generic template
            expect(result.templateType).toBe("biology_generic");
        });

        it("should have canvas configuration", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.canvas).toBeDefined();
            expect(result.manifest.canvas.width).toBeGreaterThan(0);
            expect(result.manifest.canvas.height).toBeGreaterThan(0);
            expect(result.manifest.canvas.backgroundColor).toBeDefined();
        });

        it("should have playback configuration", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.playback).toBeDefined();
            expect(result.manifest.playback.defaultSpeed).toBeGreaterThan(0);
            expect(result.manifest.playback.allowScrubbing).toBe(true);
        });

        it("should have initial entities", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "projectile_motion",
                    recommendedInteractivity: "high",
                    purpose: "Simulate projectile trajectory",
                    estimatedTimeMinutes: 15,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.initialEntities).toBeDefined();
            expect(Array.isArray(result.manifest.initialEntities)).toBe(true);
        });

        it("should have simulation steps", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "projectile_motion",
                    recommendedInteractivity: "high",
                    purpose: "Simulate projectile trajectory",
                    estimatedTimeMinutes: 15,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.steps).toBeDefined();
            expect(Array.isArray(result.manifest.steps)).toBe(true);
        });

        it("should include domain metadata", () => {
            const concept = createMockConcept({
                keywords: ["test", "physics", "motion"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.domainMetadata).toBeDefined();
        });

        it("should mark placeholder content correctly", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, {
                ...defaultOptions,
                placeholderSteps: true,
            });

            expect(result.isPlaceholder).toBe(true);
        });

        it("should generate unique manifest IDs for different concepts", () => {
            const concept1 = createMockConcept({ id: "phy_F_1" as ConceptId });
            const concept2 = createMockConcept({ id: "phy_F_2" as ConceptId });

            const result1 = generateManifestFromConcept(concept1, defaultOptions);
            const result2 = generateManifestFromConcept(concept2, defaultOptions);

            expect(String(result1.manifest.id)).not.toBe(String(result2.manifest.id));
        });

        it("should set timestamps", () => {
            const concept = createMockConcept();

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.createdAt).toBeDefined();
            expect(result.manifest.updatedAt).toBeDefined();
        });
    });

    describe("generateManifestsFromConcepts", () => {
        it("should generate manifests for multiple concepts", () => {
            const concepts = [
                createMockConcept({ id: "phy_F_1" as ConceptId }),
                createMockConcept({ id: "phy_F_2" as ConceptId }),
                createMockConcept({ id: "phy_F_3" as ConceptId }),
            ];

            const results = generateManifestsFromConcepts(concepts, defaultOptions);

            expect(results.size).toBe(3);
            expect(results.has("phy_F_1")).toBe(true);
            expect(results.has("phy_F_2")).toBe(true);
            expect(results.has("phy_F_3")).toBe(true);
        });

        it("should skip concepts without simulation type", () => {
            const concepts = [
                createMockConcept({ id: "phy_F_1" as ConceptId }),
                createMockConcept({
                    id: "phy_F_2" as ConceptId,
                    simulationMetadata: {
                        simulationType: "", // No simulation type
                        recommendedInteractivity: "low",
                        purpose: "",
                        estimatedTimeMinutes: 0,
                    },
                }),
            ];

            const results = generateManifestsFromConcepts(concepts, defaultOptions);

            expect(results.size).toBe(1);
            expect(results.has("phy_F_1")).toBe(true);
            expect(results.has("phy_F_2")).toBe(false);
        });

        it("should handle empty concepts array", () => {
            const results = generateManifestsFromConcepts([], defaultOptions);

            expect(results.size).toBe(0);
        });

        it("should preserve concept ID as key in results map", () => {
            const concepts = [
                createMockConcept({ id: "chem_I_1" as ConceptId, domain: "CHEMISTRY" }),
            ];

            const results = generateManifestsFromConcepts(concepts, defaultOptions);

            expect(results.get("chem_I_1")).toBeDefined();
            expect(results.get("chem_I_1")!.manifest.domain).toBe("CHEMISTRY");
        });
    });

    describe("Template Selection Edge Cases", () => {
        it("should handle mixed case simulation types", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "PROJECTILE_MOTION",
                    recommendedInteractivity: "high",
                    purpose: "Test",
                    estimatedTimeMinutes: 10,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            // Should still match projectile motion template
            expect(result.templateType).toBe("projectile_motion");
        });

        it("should handle partial keyword matches for template selection", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "kinematic_analysis",
                    recommendedInteractivity: "high",
                    purpose: "Study kinematics",
                    estimatedTimeMinutes: 20,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            // kinematic should match projectile motion
            expect(result.templateType).toBe("projectile_motion");
        });
    });

    describe("Domain Metadata Generation", () => {
        it("should generate physics domain metadata", () => {
            const concept = createMockConcept({
                domain: "PHYSICS",
                level: "INTERMEDIATE",
                keywords: ["force", "acceleration", "mass"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.domainMetadata).toBeDefined();
        });

        it("should generate chemistry domain metadata", () => {
            const concept = createMockConcept({
                domain: "CHEMISTRY",
                level: "ADVANCED",
                keywords: ["reaction", "catalyst", "equilibrium"],
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.domainMetadata).toBeDefined();
        });
    });

    describe("Playback Speed", () => {
        it("should set appropriate speed for low interactivity", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "visualization",
                    recommendedInteractivity: "low",
                    purpose: "Passive visualization",
                    estimatedTimeMinutes: 5,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.playback.defaultSpeed).toBeGreaterThanOrEqual(0.5);
            expect(result.manifest.playback.defaultSpeed).toBeLessThanOrEqual(2);
        });

        it("should set appropriate speed for high interactivity", () => {
            const concept = createMockConcept({
                simulationMetadata: {
                    simulationType: "interactive_experiment",
                    recommendedInteractivity: "high",
                    purpose: "Interactive exploration",
                    estimatedTimeMinutes: 30,
                },
            });

            const result = generateManifestFromConcept(concept, defaultOptions);

            expect(result.manifest.playback.defaultSpeed).toBeGreaterThanOrEqual(0.5);
            expect(result.manifest.playback.defaultSpeed).toBeLessThanOrEqual(2);
        });
    });
});
