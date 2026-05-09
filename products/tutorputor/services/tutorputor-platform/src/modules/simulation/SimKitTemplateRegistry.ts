/**
 * SimKit Template Registry
 *
 * Registry for domain-specific simulation templates with deterministic seeds.
 * Provides consistent, reproducible simulation generation across domains.
 *
 * @doc.type class
 * @doc.purpose Domain-specific simulation template registry with deterministic seeds
 * @doc.layer platform
 * @doc.pattern Registry
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import { z } from "zod";

const logger = createStandaloneLogger({ component: "SimKitTemplateRegistry" });

// ============================================================================
// Template Types
// ============================================================================

export enum SimulationDomain {
  MATH = "math",
  SCIENCE = "science",
  PHYSICS = "physics",
  CHEMISTRY = "chemistry",
  BIOLOGY = "biology",
  ENGINEERING = "engineering",
  COMPUTER_SCIENCE = "computer_science",
  INTERDISCIPLINARY = "interdisciplinary",
}

export interface SimulationTemplate {
  id: string;
  domain: SimulationDomain;
  name: string;
  description: string;
  seed: string;
  parameters: Record<string, unknown>;
  version: string;
  gradeRange: string[];
  estimatedDurationMinutes: number;
}

export interface TemplateRequest {
  domain: SimulationDomain;
  topic?: string;
  gradeLevel?: string;
  difficulty?: "beginner" | "intermediate" | "advanced";
  durationMinutes?: number;
}

// ============================================================================
// SimKit Template Registry
// ============================================================================

export class SimKitTemplateRegistry {
  private static instance: SimKitTemplateRegistry;
  private templates: Map<string, SimulationTemplate>;
  private domainTemplates: Map<SimulationDomain, SimulationTemplate[]>;

  private constructor() {
    this.templates = new Map();
    this.domainTemplates = new Map();
    this.initializeDefaultTemplates();
  }

  static getInstance(): SimKitTemplateRegistry {
    if (!SimKitTemplateRegistry.instance) {
      SimKitTemplateRegistry.instance = new SimKitTemplateRegistry();
    }
    return SimKitTemplateRegistry.instance;
  }

  /**
   * Initialize default domain templates
   */
  private initializeDefaultTemplates(): void {
    // Math templates
    this.registerTemplate({
      id: "math-quadratic-equation-v1",
      domain: SimulationDomain.MATH,
      name: "Quadratic Equation Explorer",
      description: "Interactive exploration of quadratic equations and their graphs",
      seed: "math-quadratic-2024",
      parameters: {
        equationType: "quadratic",
        visualization: "graph",
        interaction: "parameter-sliders",
      },
      version: "1.0.0",
      gradeRange: ["9", "10", "11", "12"],
      estimatedDurationMinutes: 15,
    });

    this.registerTemplate({
      id: "math-calculus-derivative-v1",
      domain: SimulationDomain.MATH,
      name: "Calculus Derivative Visualizer",
      description: "Visual understanding of derivatives through interactive graphs",
      seed: "math-calculus-2024",
      parameters: {
        calculusType: "derivative",
        visualization: "slope-field",
        interaction: "function-input",
      },
      version: "1.0.0",
      gradeRange: ["11", "12"],
      estimatedDurationMinutes: 20,
    });

    // Physics templates
    this.registerTemplate({
      id: "physics-projectile-motion-v1",
      domain: SimulationDomain.PHYSICS,
      name: "Projectile Motion Simulator",
      description: "Simulate and analyze projectile motion with adjustable parameters",
      seed: "physics-projectile-2024",
      parameters: {
        physicsType: "kinematics",
        forces: ["gravity", "air-resistance"],
        visualization: "trajectory",
      },
      version: "1.0.0",
      gradeRange: ["9", "10", "11", "12"],
      estimatedDurationMinutes: 25,
    });

    this.registerTemplate({
      id: "physics-circuit-analysis-v1",
      domain: SimulationDomain.PHYSICS,
      name: "Circuit Analysis Lab",
      description: "Build and analyze electrical circuits with Ohm's law",
      seed: "physics-circuit-2024",
      parameters: {
        physicsType: "electricity",
        components: ["resistor", "capacitor", "inductor"],
        visualization: "schematic",
      },
      version: "1.0.0",
      gradeRange: ["10", "11", "12"],
      estimatedDurationMinutes: 30,
    });

    // Chemistry templates
    this.registerTemplate({
      id: "chemistry-reaction-balancer-v1",
      domain: SimulationDomain.CHEMISTRY,
      name: "Chemical Reaction Balancer",
      description: "Balance chemical equations through interactive molecular visualization",
      seed: "chemistry-reaction-2024",
      parameters: {
        chemistryType: "stoichiometry",
        visualization: "molecular-model",
        interaction: "drag-drop",
      },
      version: "1.0.0",
      gradeRange: ["10", "11", "12"],
      estimatedDurationMinutes: 20,
    });

    // Biology templates
    this.registerTemplate({
      id: "biology-cell-division-v1",
      domain: SimulationDomain.BIOLOGY,
      name: "Cell Division Visualizer",
      description: "Interactive visualization of mitosis and meiosis processes",
      seed: "biology-cell-2024",
      parameters: {
        biologyType: "cell-division",
        phases: ["mitosis", "meiosis"],
        visualization: "time-lapse",
      },
      version: "1.0.0",
      gradeRange: ["9", "10", "11", "12"],
      estimatedDurationMinutes: 25,
    });

    // Engineering templates
    this.registerTemplate({
      id: "engineering-bridge-design-v1",
      domain: SimulationDomain.ENGINEERING,
      name: "Bridge Design Challenge",
      description: "Design and test bridges under various load conditions",
      seed: "engineering-bridge-2024",
      parameters: {
        engineeringType: "structural",
        materials: ["steel", "concrete", "wood"],
        simulation: "stress-analysis",
      },
      version: "1.0.0",
      gradeRange: ["9", "10", "11", "12"],
      estimatedDurationMinutes: 35,
    });

    // Computer Science templates
    this.registerTemplate({
      id: "cs-algorithm-visualizer-v1",
      domain: SimulationDomain.COMPUTER_SCIENCE,
      name: "Algorithm Visualizer",
      description: "Visualize sorting and search algorithms step-by-step",
      seed: "cs-algorithm-2024",
      parameters: {
        csType: "algorithms",
        algorithms: ["bubble-sort", "quick-sort", "binary-search"],
        visualization: "step-by-step",
      },
      version: "1.0.0",
      gradeRange: ["9", "10", "11", "12"],
      estimatedDurationMinutes: 20,
    });

    logger.info({
      message: "SimKit template registry initialized",
      templateCount: this.templates.size,
    }, "SimKitTemplateRegistry");
  }

  /**
   * Register a new template
   */
  registerTemplate(template: SimulationTemplate): void {
    this.templates.set(template.id, template);

    if (!this.domainTemplates.has(template.domain)) {
      this.domainTemplates.set(template.domain, []);
    }
    this.domainTemplates.get(template.domain)!.push(template);

    logger.info({
      message: "Template registered",
      templateId: template.id,
      domain: template.domain,
    }, "SimKitTemplateRegistry");
  }

  /**
   * Get template by ID
   */
  getTemplate(templateId: string): SimulationTemplate | null {
    return this.templates.get(templateId) || null;
  }

  /**
   * Get templates by domain
   */
  getTemplatesByDomain(domain: SimulationDomain): SimulationTemplate[] {
    return this.domainTemplates.get(domain) || [];
  }

  /**
   * Find matching template based on request
   */
  findTemplate(request: TemplateRequest): SimulationTemplate | null {
    const domainTemplates = this.getTemplatesByDomain(request.domain);

    if (domainTemplates.length === 0) {
      logger.warn({
        message: "No templates found for domain",
        domain: request.domain,
      }, "SimKitTemplateRegistry");
      return null;
    }

    // Filter by grade level if specified
    let filtered = domainTemplates;
    if (request.gradeLevel) {
      filtered = filtered.filter((t) =>
        t.gradeRange.includes(request.gradeLevel!)
      );
    }

    // Filter by duration if specified
    if (request.durationMinutes) {
      filtered = filtered.filter((t) =>
        Math.abs(t.estimatedDurationMinutes - request.durationMinutes) <= 10
      );
    }

    // Return first match or default to first template in domain
    return filtered.length > 0 ? filtered[0] : (domainTemplates[0] ?? null);
  }

  /**
   * Generate deterministic seed for a template
   */
  generateSeed(templateId: string, context: Record<string, unknown>): string {
    const contextString = JSON.stringify(context);
    const combined = `${templateId}-${contextString}`;
    
    // Simple hash function for deterministic seed generation
    let hash = 0;
    for (let i = 0; i < combined.length; i++) {
      const char = combined.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }

    return `seed-${Math.abs(hash)}`;
  }

  /**
   * Get all templates
   */
  getAllTemplates(): SimulationTemplate[] {
    return Array.from(this.templates.values());
  }

  /**
   * Validate template structure
   */
  validateTemplate(template: SimulationTemplate): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!template.id) errors.push("Template ID is required");
    if (!template.domain) errors.push("Domain is required");
    if (!template.name) errors.push("Name is required");
    if (!template.seed) errors.push("Seed is required");
    if (!template.version) errors.push("Version is required");
    if (!template.gradeRange || template.gradeRange.length === 0) {
      errors.push("Grade range is required");
    }
    if (!template.estimatedDurationMinutes || template.estimatedDurationMinutes <= 0) {
      errors.push("Estimated duration must be positive");
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Remove template
   */
  removeTemplate(templateId: string): boolean {
    const template = this.templates.get(templateId);
    if (!template) return false;

    this.templates.delete(templateId);

    const domainTemplates = this.domainTemplates.get(template.domain);
    if (domainTemplates) {
      const index = domainTemplates.findIndex((t) => t.id === templateId);
      if (index >= 0) {
        domainTemplates.splice(index, 1);
      }
    }

    logger.info({
      message: "Template removed",
      templateId,
    }, "SimKitTemplateRegistry");

    return true;
  }
}

// Singleton instance
export function getSimKitTemplateRegistry(): SimKitTemplateRegistry {
  return SimKitTemplateRegistry.getInstance();
}

// ============================================================================
// Zod Schemas
// ============================================================================

export const SimulationTemplateSchema = z.object({
  id: z.string().min(1),
  domain: z.nativeEnum(SimulationDomain),
  name: z.string().min(1),
  description: z.string().min(1),
  seed: z.string().min(1),
  parameters: z.record(z.unknown()),
  version: z.string().min(1),
  gradeRange: z.array(z.string()),
  estimatedDurationMinutes: z.number().positive(),
});

export const TemplateRequestSchema = z.object({
  domain: z.nativeEnum(SimulationDomain),
  topic: z.string().optional(),
  gradeLevel: z.string().optional(),
  difficulty: z.enum(["beginner", "intermediate", "advanced"]).optional(),
  durationMinutes: z.number().positive().optional(),
});
