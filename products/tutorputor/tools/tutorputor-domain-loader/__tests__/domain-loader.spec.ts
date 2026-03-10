/**
 * Domain Loader Tests
 *
 * @doc.type test
 * @doc.purpose Test domain content parsing and loading
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import { parseTimeToMinutes, mapDifficultyString, mapLevelString, generateSlug } from "../src/utils/mappers";
import { parseRawConcept } from "../src/parsers/physics-parser";
import type { RawConceptJSON } from "@ghatana/tutorputor-contracts/v1/curriculum/types";

describe("Mappers", () => {
  describe("parseTimeToMinutes", () => {
    it("should parse minute strings", () => {
      expect(parseTimeToMinutes("20 min")).toBe(20);
      expect(parseTimeToMinutes("15 minutes")).toBe(15);
      expect(parseTimeToMinutes("30min")).toBe(30);
    });

    it("should parse hour strings", () => {
      expect(parseTimeToMinutes("1 hr")).toBe(60);
      expect(parseTimeToMinutes("1.5 hours")).toBe(90);
      expect(parseTimeToMinutes("2 hour")).toBe(120);
    });

    it("should parse range strings", () => {
      expect(parseTimeToMinutes("2–3 hours")).toBe(150); // avg of 2.5 hours
      expect(parseTimeToMinutes("1-2 hr")).toBe(90); // avg of 1.5 hours
    });

    it("should return default for invalid strings", () => {
      expect(parseTimeToMinutes("")).toBe(15);
      expect(parseTimeToMinutes("unknown")).toBe(15);
    });
  });

  describe("mapDifficultyString", () => {
    it("should map easy/beginner to INTRO", () => {
      expect(mapDifficultyString("easy")).toBe("INTRO");
      expect(mapDifficultyString("beginner")).toBe("INTRO");
    });

    it("should map intermediate/medium to INTERMEDIATE", () => {
      expect(mapDifficultyString("intermediate")).toBe("INTERMEDIATE");
      expect(mapDifficultyString("medium")).toBe("INTERMEDIATE");
    });

    it("should map hard/advanced to ADVANCED", () => {
      expect(mapDifficultyString("hard")).toBe("ADVANCED");
      expect(mapDifficultyString("advanced")).toBe("ADVANCED");
      expect(mapDifficultyString("very_hard")).toBe("ADVANCED");
    });
  });

  describe("mapLevelString", () => {
    it("should map foundational variants", () => {
      expect(mapLevelString("Foundational")).toBe("FOUNDATIONAL");
      expect(mapLevelString("foundation")).toBe("FOUNDATIONAL");
      expect(mapLevelString("basic")).toBe("FOUNDATIONAL");
    });

    it("should map research variants", () => {
      expect(mapLevelString("Research")).toBe("RESEARCH");
      expect(mapLevelString("Research_Independent")).toBe("RESEARCH");
    });
  });

  describe("generateSlug", () => {
    it("should generate valid slugs", () => {
      expect(generateSlug("Scalars and Vectors", "phy_F_1")).toBe("scalars-and-vectors-phy-f-1");
      expect(generateSlug("Newton's Laws of Motion", "physics_I_2")).toBe(
        "newton-s-laws-of-motion-physics-i-2"
      );
    });

    it("should handle special characters", () => {
      expect(generateSlug("2D Motion: Velocity & Acceleration", "physics_I_1")).toContain(
        "2d-motion"
      );
    });
  });
});

describe("Physics Parser", () => {
  const sampleRawConcept: RawConceptJSON = {
    id: "phy_F_1",
    name: "Scalars and Vectors",
    description: "Understand what physical quantities have only magnitude vs magnitude + direction.",
    prerequisites: [],
    audience_tags: ["K-12", "Independent_Study"],
    keywords: ["scalar", "vector", "magnitude", "direction"],
    simulation_metadata: {
      simulation_type: "interactive_visualization",
      recommended_interactivity: "low",
      purpose: "Visualize difference between scalar vs vector",
      estimated_time: "10 min",
    },
    cross_domain_links: [],
    learning_object_metadata: {
      author: "TutorPutor Team",
      version: "1.0.0",
      status: "published",
      intended_end_user_role: ["student"],
      context: ["self-study"],
      difficulty: "easy",
      typical_learning_time: "15 min",
      learning_object_type: "conceptual_explanation",
      creation_date: "2025-12-05",
      last_modified: "2025-12-05",
    },
    pedagogical_metadata: {
      learning_objectives: [
        "Distinguish between scalar and vector quantities.",
        "Explain why direction matters for vector quantities.",
      ],
      competencies: ["quantitative reasoning", "visualization"],
      scaffolding_level: "standalone",
      accessibility_notes: "Use high-contrast arrows for visibility",
    },
  };

  describe("parseRawConcept", () => {
    it("should parse a valid concept", () => {
      const result = parseRawConcept(sampleRawConcept, "PHYSICS", "FOUNDATIONAL");

      expect(result.id).toBe("phy_F_1");
      expect(result.name).toBe("Scalars and Vectors");
      expect(result.domain).toBe("PHYSICS");
      expect(result.level).toBe("FOUNDATIONAL");
      expect(result.keywords).toEqual(["scalar", "vector", "magnitude", "direction"]);
    });

    it("should parse simulation metadata", () => {
      const result = parseRawConcept(sampleRawConcept, "PHYSICS", "FOUNDATIONAL");

      expect(result.simulationMetadata.simulationType).toBe("interactive_visualization");
      expect(result.simulationMetadata.recommendedInteractivity).toBe("low");
      expect(result.simulationMetadata.estimatedTimeMinutes).toBe(10);
    });

    it("should parse learning object metadata", () => {
      const result = parseRawConcept(sampleRawConcept, "PHYSICS", "FOUNDATIONAL");

      expect(result.learningObjectMetadata.author).toBe("TutorPutor Team");
      expect(result.learningObjectMetadata.status).toBe("published");
      expect(result.learningObjectMetadata.difficulty).toBe("INTRO"); // easy -> INTRO
    });

    it("should parse pedagogical metadata", () => {
      const result = parseRawConcept(sampleRawConcept, "PHYSICS", "FOUNDATIONAL");

      expect(result.pedagogicalMetadata.learningObjectives).toHaveLength(2);
      expect(result.pedagogicalMetadata.scaffoldingLevel).toBe("standalone");
    });

    it("should map audience tags", () => {
      const result = parseRawConcept(sampleRawConcept, "PHYSICS", "FOUNDATIONAL");

      expect(result.audienceTags).toContain("K-12");
      expect(result.audienceTags).toContain("Independent_Study");
    });
  });
});
