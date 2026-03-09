/**
 * AI-Enhanced Content Generation Service
 *
 * Provides AI-assisted content creation for concepts and simulations.
 * Reduces manual authoring effort and enables non-technical educators.
 *
 * @doc.type class
 * @doc.purpose AI-powered content generation for authoring workflow
 * @doc.layer platform
 * @doc.pattern Service
 */

import type { AIProxyService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
  TenantId,
  UserId,
  TutorResponsePayload,
} from "@ghatana/tutorputor-contracts/v1/types";

interface GeneratedConcept {
  name: string;
  description: string;
  learningObjectives: Array<{
    text: string;
    example?: {
      title: string;
      description: string;
      type: string;
    };
    animation?: {
      title: string;
      description: string;
      type: string;
    };
  }>;
  prerequisites: string[];
  competencies: string[];
  keywords: string[];
  level: "FOUNDATIONAL" | "INTERMEDIATE" | "ADVANCED" | "RESEARCH";
}

interface GeneratedSimulationManifest {
  type: string;
  manifest: {
    title: string;
    description: string;
    entities: Array<{
      id: string;
      type: string;
      properties: Record<string, unknown>;
    }>;
    interactions: Array<{
      type: string;
      config: Record<string, unknown>;
    }>;
  };
  estimatedTimeMinutes: number;
  purpose: string;
}

export class AIContentGenerationService {
  constructor(private readonly aiProxyService: AIProxyService) {}

  /**
   * Generate concept metadata from a concept name using AI.
   */
  async generateConceptFromName(
    conceptName: string,
    domain: string,
    tenantId: string,
  ): Promise<GeneratedConcept> {
    const prompt = this.buildConceptGenerationPrompt(conceptName, domain);

    let lastError: unknown;
    const maxAttempts = 3;

    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        const response = await this.aiProxyService.handleTutorQuery({
          tenantId: tenantId as TenantId,
          userId: "system" as UserId,
          question: prompt,
          locale: "en",
        });

        const responseText = this.extractResponseText(response);
        const generated = this.parseConceptResponse(responseText);

        if (
          generated.description?.trim() ||
          generated.learningObjectives.length > 0
        ) {
          return {
            name: conceptName,
            ...generated,
          };
        }

        lastError = new Error("AI concept generation returned empty content");
      } catch (error) {
        lastError = error;
        console.warn("[AI] Concept generation error", {
          conceptName,
          attempt,
          error: error instanceof Error ? error.message : String(error),
        });
      }

      // Exponential backoff
      const delay = Math.pow(2, attempt) * 250;
      await new Promise((res) => setTimeout(res, delay));
    }

    // Return fallback after retries
    const message =
      lastError instanceof Error ? lastError.message : "Unknown error";
    return {
      name: conceptName,
      description: `AI concept generation incomplete: ${message}`,
      learningObjectives: [],
      prerequisites: [],
      competencies: [],
      keywords: [],
      level: "INTERMEDIATE",
    };
  }

  /**
   * Generate simulation manifest from natural language description.
   */
  async generateSimulationManifest(
    description: string,
    conceptName: string,
    domain: string,
    tenantId: string,
  ): Promise<GeneratedSimulationManifest> {
    const prompt = this.buildSimulationGenerationPrompt(
      description,
      conceptName,
      domain,
    );
    try {
      const response = await this.aiProxyService.handleTutorQuery({
        tenantId: tenantId as TenantId,
        userId: "system" as UserId,
        question: prompt,
        locale: "en",
      });

      const responseText = this.extractResponseText(response);
      return this.parseSimulationResponse(responseText);
    } catch (error) {
      throw new Error(
        `AI simulation generation failed: ${error instanceof Error ? error.message : "Unknown error"}`,
      );
    }
  }

  /**
   * Extract text content from TutorResponsePayload
   */
  private extractResponseText(response: TutorResponsePayload): string {
    return response.answer;
  }

  private buildConceptGenerationPrompt(
    conceptName: string,
    domain: string,
  ): string {
    return `Generate educational content metadata for the concept "${conceptName}" in the domain of ${domain}.

Provide a JSON response with the following structure:
{
  "description": "2-3 sentence explanation suitable for students",
  "learningObjectives": [
    {
      "text": "Specific, measurable learning outcome",
      "example": {
        "title": "Example title",
        "description": "Brief description of a real-world example",
        "type": "real_world|problem_solving|analogy"
      }
    }
  ],
  "prerequisites": ["List of prerequisite concepts/topics"],
  "competencies": ["Key skills or competencies developed"],
  "keywords": ["5-7 relevant keywords for search/tagging"],
  "level": "FOUNDATIONAL|INTERMEDIATE|ADVANCED|RESEARCH"
}

Ensure the response is valid JSON only, with no additional text.`;
  }

  private buildSimulationGenerationPrompt(
    description: string,
    conceptName: string,
    domain: string,
  ): string {
    return `Generate a simulation manifest for: "${description}"
    
Context:
- Concept: ${conceptName}
- Domain: ${domain}

Provide a JSON response with the following structure:
{
  "type": "physics-2D|graph-3d|interactive-visualization",
  "manifest": {
    "title": "Simulation title",
    "description": "What the simulation demonstrates",
    "entities": [
      {
        "id": "unique-id",
        "type": "object|particle|graph|node",
        "properties": {
          "position": [0, 0],
          "mass": 1.0,
          "color": "#3b82f6"
        }
      }
    ],
    "interactions": [
      {
        "type": "drag|click|parameter-adjust",
        "config": {
          "targetEntity": "entity-id",
          "parameter": "property-name"
        }
      }
    ]
  },
  "estimatedTimeMinutes": 15,
  "purpose": "Pedagogical purpose of this simulation"
}

Ensure the response is valid JSON only, with no additional text.`;
  }

  private parseConceptResponse(
    response: string,
  ): Omit<GeneratedConcept, "name"> {
    try {
      const jsonMatch = response.match(/\{[\s\S]*\}/);
      if (!jsonMatch) {
        throw new Error("No JSON found in AI response");
      }

      const parsed = JSON.parse(jsonMatch[0]);

      return {
        description: parsed.description ?? "No description provided",
        learningObjectives: Array.isArray(parsed.learningObjectives)
          ? parsed.learningObjectives.map((obj: unknown) => {
              if (typeof obj === "string") return { text: obj };
              const item = obj as Record<string, unknown>;
              return {
                text: (item.text as string) || "Objective",
                example:
                  item.example as GeneratedConcept["learningObjectives"][0]["example"],
                animation:
                  item.animation as GeneratedConcept["learningObjectives"][0]["animation"],
              };
            })
          : [],
        prerequisites: Array.isArray(parsed.prerequisites)
          ? parsed.prerequisites
          : [],
        competencies: Array.isArray(parsed.competencies)
          ? parsed.competencies
          : [],
        keywords: Array.isArray(parsed.keywords) ? parsed.keywords : [],
        level: this.validateLevel(parsed.level),
      };
    } catch (error) {
      throw new Error(
        `Failed to parse AI concept response: ${error instanceof Error ? error.message : "Unknown error"}`,
      );
    }
  }

  private parseSimulationResponse(
    response: string,
  ): GeneratedSimulationManifest {
    try {
      const jsonMatch = response.match(/\{[\s\S]*\}/);
      if (!jsonMatch) {
        throw new Error("No JSON found in AI response");
      }

      const parsed = JSON.parse(jsonMatch[0]);

      return {
        type: parsed.type ?? "physics-2D",
        manifest: parsed.manifest ?? {
          title: "",
          description: "",
          entities: [],
          interactions: [],
        },
        estimatedTimeMinutes: parsed.estimatedTimeMinutes ?? 15,
        purpose: parsed.purpose ?? "Interactive learning simulation",
      };
    } catch (error) {
      throw new Error(
        `Failed to parse AI simulation response: ${error instanceof Error ? error.message : "Unknown error"}`,
      );
    }
  }

  private validateLevel(
    level: string,
  ): "FOUNDATIONAL" | "INTERMEDIATE" | "ADVANCED" | "RESEARCH" {
    const validLevels = [
      "FOUNDATIONAL",
      "INTERMEDIATE",
      "ADVANCED",
      "RESEARCH",
    ];
    if (validLevels.includes(level)) {
      return level as "FOUNDATIONAL" | "INTERMEDIATE" | "ADVANCED" | "RESEARCH";
    }
    return "INTERMEDIATE";
  }
}
