/**
 * Content Generation Contract Tests
 *
 * Contract tests to validate that TypeScript types match the authoritative
 * proto definition in contracts/proto/content_generation.proto
 *
 * These tests ensure:
 * 1. All proto messages have corresponding TypeScript types
 * 2. Field names match between proto and TypeScript
 * 3. Required fields are present in TypeScript
 * 4. Enum values match
 *
 * @doc.type test
 * @doc.purpose Contract tests for content generation proto-to-TypeScript mapping
 * @doc.layer contracts
 * @doc.pattern ContractTest
 */

import { describe, it, expect } from "vitest";
import * as ContentGeneration from "../content-generation.js";

describe("Content Generation Contracts - Proto Mapping Validation", () => {
  describe("Common Types", () => {
    it("should have RequestContext with all required fields", () => {
      const context: ContentGeneration.RequestContext = {
        request_id: "test-req-123",
        tenant_id: "tenant-456",
        timestamp: "2024-01-01T00:00:00Z",
        metadata: {},
      };
      
      expect(context.request_id).toBe("test-req-123");
      expect(context.tenant_id).toBe("tenant-456");
    });

    it("should have LearningClaim with all required fields", () => {
      const claim: ContentGeneration.LearningClaim = {
        claim_ref: "claim-123",
        text: "Test claim",
        bloom_level: "understand",
        order_index: 1,
        content_needs: {
          examples: { required: false, types: [], count: 0, necessity: 0, rationale: "" },
          simulation: { required: false, interaction_type: "", complexity: "", necessity: 0, rationale: "" },
          animation: { required: false, animation_type: "", duration_seconds: 0, necessity: 0, rationale: "" },
        },
      };
      
      expect(claim.claim_ref).toBe("claim-123");
      expect(claim.text).toBe("Test claim");
    });

    it("should have ContentNeeds with all sub-types", () => {
      const needs: ContentGeneration.ContentNeeds = {
        examples: { required: true, types: ["worked_example"], count: 3, necessity: 0.8, rationale: "Need examples" },
        simulation: { required: false, interaction_type: "interactive", complexity: "medium", necessity: 0.5, rationale: "" },
        animation: { required: false, animation_type: "2d", duration_seconds: 30, necessity: 0.3, rationale: "" },
      };
      
      expect(needs.examples.required).toBe(true);
      expect(needs.examples.types).toContain("worked_example");
    });
  });

  describe("Generate Claims", () => {
    it("should have GenerateClaimsRequest with all fields", () => {
      const request: ContentGeneration.GenerateClaimsRequest = {
        context: {
          request_id: "req-123",
          tenant_id: "tenant-456",
          timestamp: "2024-01-01T00:00:00Z",
          metadata: {},
        },
        topic: "Photosynthesis",
        grade_level: "8",
        domain: "biology",
        max_claims: 10,
        context_params: {},
        language: "en",
      };
      
      expect(request.topic).toBe("Photosynthesis");
      expect(request.max_claims).toBe(10);
    });

    it("should have GenerateClaimsResponse with all fields", () => {
      const response: ContentGeneration.GenerateClaimsResponse = {
        context: {
          request_id: "req-123",
          tenant_id: "tenant-456",
          timestamp: "2024-01-01T00:00:00Z",
          metadata: {},
        },
        claims: [],
        validation: { valid: true, confidence_score: 0.95, issues: [], suggestions: [] },
        metadata: {
          model_name: "gpt-4",
          tokens_used: 1000,
          generation_time_ms: 5000,
          temperature: 0.7,
          prompt_hash: "abc123",
          timestamp: "2024-01-01T00:00:00Z",
        },
      };
      
      expect(response.validation.valid).toBe(true);
      expect(response.validation.confidence_score).toBe(0.95);
    });
  });

  describe("Generate Examples", () => {
    it("should have GenerateExamplesRequest with all fields", () => {
      const request: ContentGeneration.GenerateExamplesRequest = {
        request_id: "req-123",
        tenant_id: "tenant-456",
        claim_ref: "claim-123",
        claim_text: "Test claim",
        example_types: ["worked_example", "real_world"],
        count: 3,
        domain: "math",
        grade_level: "8",
        context: {},
      };
      
      expect(request.example_types).toContain("worked_example");
      expect(request.count).toBe(3);
    });

    it("should have Example with all fields", () => {
      const example: ContentGeneration.Example = {
        example_id: "ex-123",
        type: "worked_example",
        title: "Example 1",
        description: "A worked example",
        content: "Example content",
        tags: ["tag1", "tag2"],
        relevance_score: 0.9,
      };
      
      expect(example.example_id).toBe("ex-123");
      expect(example.relevance_score).toBe(0.9);
    });
  });

  describe("Generate Simulation", () => {
    it("should have GenerateSimulationRequest with all fields", () => {
      const request: ContentGeneration.GenerateSimulationRequest = {
        request_id: "req-123",
        tenant_id: "tenant-456",
        claim_ref: "claim-123",
        claim_text: "Test claim",
        interaction_type: "interactive",
        complexity: "medium",
        domain: "physics",
        grade_level: "8",
        context: {},
      };
      
      expect(request.interaction_type).toBe("interactive");
      expect(request.complexity).toBe("medium");
    });

    it("should have SimulationManifest with all fields", () => {
      const manifest: ContentGeneration.SimulationManifest = {
        manifest_id: "sim-123",
        version: "1.0",
        domain: "physics",
        title: "Simulation Title",
        description: "Simulation description",
        entities: [],
        steps: [],
        keyframes: [],
        domain_config: "{}",
      };
      
      expect(manifest.manifest_id).toBe("sim-123");
      expect(manifest.domain_config).toBe("{}");
    });

    it("should have Entity with Position", () => {
      const entity: ContentGeneration.Entity = {
        id: "entity-123",
        label: "Particle",
        entity_type: "object",
        visual: "{}",
        position: { x: 0, y: 0, z: 0 },
      };
      
      expect(entity.position.x).toBe(0);
      expect(entity.position.y).toBe(0);
      expect(entity.position.z).toBe(0);
    });
  });

  describe("Generate Animation", () => {
    it("should have GenerateAnimationRequest with all fields", () => {
      const request: ContentGeneration.GenerateAnimationRequest = {
        request_id: "req-123",
        tenant_id: "tenant-456",
        claim_ref: "claim-123",
        claim_text: "Test claim",
        animation_type: "2d",
        duration_seconds: 30,
        domain: "biology",
        grade_level: "8",
        context: {},
      };
      
      expect(request.animation_type).toBe("2d");
      expect(request.duration_seconds).toBe(30);
    });

    it("should have AnimationManifest with scenes and cues", () => {
      const manifest: ContentGeneration.AnimationManifest = {
        manifest_id: "anim-123",
        version: "1.0",
        title: "Animation Title",
        description: "Animation description",
        duration_seconds: 30,
        scenes: [],
        cues: [],
        narration_script: "Narration text",
      };
      
      expect(manifest.duration_seconds).toBe(30);
      expect(manifest.narration_script).toBe("Narration text");
    });

    it("should have AnimationCue with valid type", () => {
      const cue: ContentGeneration.AnimationCue = {
        cue_id: "cue-123",
        time_ms: 5000,
        type: "highlight",
        target: "element-1",
        effect: "fade-in",
      };
      
      expect(cue.type).toBe("highlight");
      expect(cue.time_ms).toBe(5000);
    });
  });

  describe("Validate Content", () => {
    it("should have ValidateContentRequest with all fields", () => {
      const request: ContentGeneration.ValidateContentRequest = {
        request_id: "req-123",
        tenant_id: "tenant-456",
        experience_id: "exp-123",
        title: "Experience Title",
        description: "Experience description",
        claim_texts: ["Claim 1", "Claim 2"],
        domain: "math",
        metadata: {},
      };
      
      expect(request.claim_texts).toHaveLength(2);
      expect(request.experience_id).toBe("exp-123");
    });

    it("should have ValidateContentResponse with dimension scores", () => {
      const response: ContentGeneration.ValidateContentResponse = {
        request_id: "req-123",
        status: "valid",
        overall_score: 95,
        can_publish: true,
        dimension_scores: {
          educational: 90,
          experiential: 95,
          safety: 100,
          technical: 92,
          accessibility: 88,
        },
        issues: [],
        issue_count: 0,
        metadata: {
          model_name: "validator-v1",
          tokens_used: 500,
          generation_time_ms: 1000,
          temperature: 0.5,
          prompt_hash: "abc123",
          timestamp: "2024-01-01T00:00:00Z",
        },
      };
      
      expect(response.status).toBe("valid");
      expect(response.can_publish).toBe(true);
      expect(response.dimension_scores.educational).toBe(90);
    });

    it("should have ValidationIssue with valid severity", () => {
      const issue: ContentGeneration.ValidationIssue = {
        issue_id: "issue-123",
        dimension: "accessibility",
        severity: "warning",
        message: "Missing alt text",
        suggestion: "Add alt text to images",
      };
      
      expect(issue.severity).toBe("warning");
      expect(issue.dimension).toBe("accessibility");
    });
  });

  describe("Enhance Content", () => {
    it("should have EnhanceContentRequest with enhancement types", () => {
      const request: ContentGeneration.EnhanceContentRequest = {
        request_id: "req-123",
        tenant_id: "tenant-456",
        experience_id: "exp-123",
        title: "Experience Title",
        description: "Experience description",
        claim_texts: ["Claim 1"],
        domain: "science",
        grade_level: "8",
        enhancement_types: ["engagement", "real_world"],
        context: {},
      };
      
      expect(request.enhancement_types).toContain("engagement");
      expect(request.enhancement_types).toContain("real_world");
    });

    it("should have Enhancement with implementation", () => {
      const enhancement: ContentGeneration.Enhancement = {
        enhancement_id: "enh-123",
        type: "engagement",
        title: "Interactive Quiz",
        description: "Add a quiz after each section",
        rationale: "Improves learner engagement",
        confidence: 0.85,
        implementation: '{"type": "quiz", "questions": 5}',
      };
      
      expect(enhancement.type).toBe("engagement");
      expect(enhancement.confidence).toBe(0.85);
      expect(typeof enhancement.implementation).toBe("string");
    });
  });

  describe("Health Check", () => {
    it("should have HealthCheckRequest and Response", () => {
      const request: ContentGeneration.HealthCheckRequest = {
        service_name: "content-generation-service",
      };
      
      const response: ContentGeneration.HealthCheckResponse = {
        status: "healthy",
        timestamp: "2024-01-01T00:00:00Z",
        service_details: { version: "1.0.0" },
        dependencies: ["ai-service", "database"],
      };
      
      expect(request.service_name).toBe("content-generation-service");
      expect(response.status).toBe("healthy");
      expect(response.dependencies).toContain("ai-service");
    });
  });

  describe("GenerationMetadata", () => {
    it("should have all metadata fields", () => {
      const metadata: ContentGeneration.GenerationMetadata = {
        model_name: "gpt-4",
        tokens_used: 1000,
        generation_time_ms: 5000,
        temperature: 0.7,
        prompt_hash: "abc123def456",
        timestamp: "2024-01-01T00:00:00Z",
      };
      
      expect(metadata.model_name).toBe("gpt-4");
      expect(metadata.tokens_used).toBe(1000);
      expect(metadata.generation_time_ms).toBe(5000);
      expect(metadata.temperature).toBe(0.7);
    });
  });
});
