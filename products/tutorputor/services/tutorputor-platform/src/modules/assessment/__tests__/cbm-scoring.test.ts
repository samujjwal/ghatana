/**
 * CBM (Confidence-Based Marking) Scoring Tests
 *
 * Validates that CBM scoring works correctly with proper penalty/bonus logic.
 * Ensures scoring is evidence-linked and replayable.
 *
 * @doc.type test
 * @doc.purpose Validate CBM scoring logic and evidence linkage
 * @doc.layer product
 * @doc.pattern GoldenMaster
 */

import { describe, it, expect } from "vitest";
import {
  AssessmentScoringService,
  type AssessmentResponse,
  type AttemptBinding,
  ConfidenceLevel,
} from "../AssessmentScoringService.js";

describe("CBM Scoring Tests", () => {
  const scoringService = AssessmentScoringService.getInstance();

  describe("CBM Penalty and Bonus Logic", () => {
    it("applies low confidence penalty to correct answers", () => {
      const response: AssessmentResponse = {
        itemId: "q1",
        answer: "A",
        confidence: ConfidenceLevel.LOW,
        timeSpentSeconds: 30,
      };

      const result = scoringService.scoreItem(response, true, 10);

      // Default penalty: 0.5x for low confidence
      expect(result.points).toBe(5); // 10 * 0.5
      expect(result.adjustment).toBe(-5); // 5 - 10 = -5
    });

    it("applies medium confidence penalty to correct answers", () => {
      const response: AssessmentResponse = {
        itemId: "q1",
        answer: "A",
        confidence: ConfidenceLevel.MEDIUM,
        timeSpentSeconds: 30,
      };

      const result = scoringService.scoreItem(response, true, 10);

      // Default penalty: 0.75x for medium confidence
      expect(result.points).toBe(7.5); // 10 * 0.75
      expect(result.adjustment).toBe(-2.5); // 7.5 - 10 = -2.5
    });

    it("applies high confidence bonus to correct answers", () => {
      const response: AssessmentResponse = {
        itemId: "q1",
        answer: "A",
        confidence: ConfidenceLevel.HIGH,
        timeSpentSeconds: 30,
      };

      const result = scoringService.scoreItem(response, true, 10);

      // Default bonus: 1.1x for high confidence
      expect(result.points).toBe(11); // 10 * 1.1
      expect(result.adjustment).toBe(1); // 11 - 10 = 1
    });

    it("applies incorrect penalty when configured", () => {
      const response: AssessmentResponse = {
        itemId: "q1",
        answer: "A",
        confidence: ConfidenceLevel.HIGH,
        timeSpentSeconds: 30,
      };

      const result = scoringService.scoreItem(response, false, 10, { incorrectPenalty: 0.25 });

      // Incorrect penalty: 0.25x
      expect(result.points).toBe(7.5); // 10 - (10 * 0.25) = 7.5
      expect(result.adjustment).toBe(-2.5); // 7.5 - 10 = -2.5
    });
  });

  describe("Evidence Linkage", () => {
    it("binds scoring to attempt ID", () => {
      const responses: AssessmentResponse[] = [
        {
          itemId: "q1",
          answer: "A",
          confidence: ConfidenceLevel.HIGH,
          timeSpentSeconds: 30,
        },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        undefined,
        10,
        { enabled: true },
      );

      expect(result.attemptId).toBe("attempt-123");
      expect(result.scoredAt).toBeDefined();
    });

    it("validates attempt binding", () => {
      const validBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      expect(scoringService.validateAttemptBinding(validBinding)).toBe(true);

      const invalidBinding = {
        attemptId: "",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      expect(scoringService.validateAttemptBinding(invalidBinding)).toBe(false);
    });
  });

  describe("Confidence Breakdown", () => {
    it("tracks confidence breakdown by level", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.LOW, timeSpentSeconds: 30 },
        { itemId: "q2", answer: "B", confidence: ConfidenceLevel.LOW, timeSpentSeconds: 30 },
        { itemId: "q3", answer: "C", confidence: ConfidenceLevel.MEDIUM, timeSpentSeconds: 30 },
        { itemId: "q4", answer: "D", confidence: ConfidenceLevel.HIGH, timeSpentSeconds: 30 },
        { itemId: "q5", answer: "E", confidence: ConfidenceLevel.HIGH, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        undefined,
        10,
        { enabled: true },
      );

      expect(result.confidenceBreakdown.low.total).toBe(2);
      expect(result.confidenceBreakdown.low.correct).toBe(2);
      expect(result.confidenceBreakdown.medium.total).toBe(1);
      expect(result.confidenceBreakdown.medium.correct).toBe(1);
      expect(result.confidenceBreakdown.high.total).toBe(2);
      expect(result.confidenceBreakdown.high.correct).toBe(2);
    });

    it("tracks incorrect responses in breakdown", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.LOW, timeSpentSeconds: 30 },
        { itemId: "q2", answer: "B", confidence: ConfidenceLevel.LOW, timeSpentSeconds: 30 },
        { itemId: "q3", answer: "C", confidence: ConfidenceLevel.MEDIUM, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const result = scoringService.scoreAssessment(
        responses,
        (r) => r.itemId !== "q2", // q2 is incorrect
        attemptBinding,
        undefined,
        10,
        { enabled: true },
      );

      expect(result.confidenceBreakdown.low.total).toBe(2);
      expect(result.confidenceBreakdown.low.correct).toBe(1);
    });
  });

  describe("Telemetry Validation", () => {
    it("validates telemetry events when provided", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.HIGH, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const telemetryEvents = [
        {
          eventType: "answer_submitted",
          timestamp: new Date().toISOString(),
          attemptId: "attempt-123",
          itemId: "q1",
          data: { answer: "A" },
        },
      ];

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        telemetryEvents,
        10,
        { enabled: true },
      );

      expect(result.telemetryValidated).toBe(true);
    });

    it("rejects telemetry with mismatched attempt ID", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.HIGH, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const telemetryEvents = [
        {
          eventType: "answer_submitted",
          timestamp: new Date().toISOString(),
          attemptId: "attempt-999", // Mismatched
          itemId: "q1",
          data: { answer: "A" },
        },
      ];

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        telemetryEvents,
        10,
        { enabled: true },
      );

      expect(result.telemetryValidated).toBe(false);
    });

    it("marks telemetry as invalid when not provided", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.HIGH, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        undefined, // No telemetry
        10,
        { enabled: true },
      );

      expect(result.telemetryValidated).toBe(false);
    });
  });

  describe("CBM Configuration", () => {
    it("allows custom CBM configuration", () => {
      const customConfig = {
        enabled: true,
        lowConfidencePenalty: 0.3,
        mediumConfidencePenalty: 0.6,
        highConfidenceBonus: 1.2,
        incorrectPenalty: 0.5,
      };

      scoringService.updateCBMConfig(customConfig);
      const retrievedConfig = scoringService.getCBMConfig();

      expect(retrievedConfig.lowConfidencePenalty).toBe(0.3);
      expect(retrievedConfig.mediumConfidencePenalty).toBe(0.6);
      expect(retrievedConfig.highConfidenceBonus).toBe(1.2);
      expect(retrievedConfig.incorrectPenalty).toBe(0.5);
    });

    it("disables CBM when configured", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.LOW, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        undefined,
        10,
        { enabled: false }, // CBM disabled
      );

      // Without CBM, correct answer gets full points regardless of confidence
      expect(result.score).toBe(100);
      expect(result.confidenceAdjustment).toBe(0);
      expect(result.cbmLinked).toBe(false);
    });

    it("marks CBM as linked when enabled", () => {
      const responses: AssessmentResponse[] = [
        { itemId: "q1", answer: "A", confidence: ConfidenceLevel.HIGH, timeSpentSeconds: 30 },
      ];

      const attemptBinding: AttemptBinding = {
        attemptId: "attempt-123",
        userId: "user-456",
        tenantId: "tenant-789",
        assessmentId: "assessment-abc",
        startedAt: new Date().toISOString(),
      };

      const result = scoringService.scoreAssessment(
        responses,
        () => true,
        attemptBinding,
        undefined,
        10,
        { enabled: true }, // CBM enabled
      );

      expect(result.cbmLinked).toBe(true);
    });
  });

  describe("Mastery and Grade Calculation", () => {
    it("calculates passing status correctly", () => {
      expect(scoringService.isPassing(85, 70)).toBe(true);
      expect(scoringService.isPassing(65, 70)).toBe(false);
    });

    it("calculates grade correctly", () => {
      expect(scoringService.calculateGrade(95)).toBe("A");
      expect(scoringService.calculateGrade(85)).toBe("B");
      expect(scoringService.calculateGrade(75)).toBe("C");
      expect(scoringService.calculateGrade(65)).toBe("D");
      expect(scoringService.calculateGrade(55)).toBe("F");
    });

    it("calculates mastery level correctly", () => {
      expect(scoringService.calculateMasteryLevel(97)).toBe("expert");
      expect(scoringService.calculateMasteryLevel(87)).toBe("advanced");
      expect(scoringService.calculateMasteryLevel(75)).toBe("proficient");
      expect(scoringService.calculateMasteryLevel(55)).toBe("developing");
      expect(scoringService.calculateMasteryLevel(35)).toBe("novice");
    });
  });
});
