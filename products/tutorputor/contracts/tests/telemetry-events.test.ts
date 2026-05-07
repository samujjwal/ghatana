import { describe, expect, it } from "vitest";
import {
  XAPI_VERBS,
  createBaseEvent,
  type AIGovernanceBlockedEvent,
  type AITutorResponseEvent,
  type AssessAnswerEvent,
  type AssistHintEvent,
  type LearningTelemetryEvent,
  type SimCaptureEvent,
  type SimSnapshotEvent,
  type SimulationTelemetryEvent,
} from "../v1/telemetry-events.js";

describe("learning telemetry event contract separation", () => {
  it("defines required simulation telemetry event names", () => {
    expect(XAPI_VERBS["sim.start"]).toBeDefined();
    expect(XAPI_VERBS["sim.control.change"]).toBeDefined();
    expect(XAPI_VERBS["sim.snapshot"]).toBeDefined();
    expect(XAPI_VERBS["sim.capture"]).toBeDefined();
  });

  it("defines required assessment, assist, and AI telemetry event names", () => {
    expect(XAPI_VERBS["assess.answer"]).toBeDefined();
    expect(XAPI_VERBS["assist.hint"]).toBeDefined();
    expect(XAPI_VERBS["ai.tutor.response"]).toBeDefined();
    expect(XAPI_VERBS["ai.governance.blocked"]).toBeDefined();
  });

  it("types simulation snapshots and captures as learning telemetry", () => {
    const base = createBaseEvent("sim.snapshot", "learner-1", "tenant-1", "session-1");
    const snapshot: SimSnapshotEvent = {
      ...base,
      type: "sim.snapshot",
      object: {
        simulationId: "sim-1",
        runId: "run-1",
        seed: "seed-1",
      },
      result: {
        state: { velocity: 4 },
        elapsedTimeMs: 1200,
        deterministicHash: "hash-1",
      },
    };
    const capture: SimCaptureEvent = {
      ...createBaseEvent("sim.capture", "learner-1", "tenant-1", "session-1"),
      type: "sim.capture",
      object: {
        simulationId: "sim-1",
        runId: "run-1",
        captureId: "capture-1",
        claimId: "claim-1",
        evidenceId: "evidence-1",
        taskId: "task-1",
      },
      result: {
        processFeatures: { attempts: 2 },
        outputState: { distance: 12 },
        validEvidence: true,
      },
    };

    const simulationEvents: SimulationTelemetryEvent[] = [snapshot, capture];
    const learningEvents: LearningTelemetryEvent[] = simulationEvents;

    expect(learningEvents.map((event) => event.type)).toEqual([
      "sim.snapshot",
      "sim.capture",
    ]);
  });

  it("types assessment answers, hints, and AI interactions without platform events", () => {
    const answer: AssessAnswerEvent = {
      ...createBaseEvent("assess.answer", "learner-1", "tenant-1", "session-1"),
      type: "assess.answer",
      object: {
        assessmentId: "assessment-1",
        attemptId: "attempt-1",
        itemId: "item-1",
        taskId: "task-1",
        claimId: "claim-1",
        evidenceId: "evidence-1",
      },
      result: {
        response: "12",
        correct: true,
        score: 1,
        maxScore: 1,
        confidence: "medium",
        durationMs: 3000,
      },
    };
    const hint: AssistHintEvent = {
      ...createBaseEvent("assist.hint", "learner-1", "tenant-1", "session-1"),
      type: "assist.hint",
      object: {
        moduleId: "module-1",
        claimId: "claim-1",
        taskId: "task-1",
        hintId: "hint-1",
      },
      result: {
        source: "ai_tutor",
        level: "nudge",
        accepted: true,
      },
    };
    const tutor: AITutorResponseEvent = {
      ...createBaseEvent("ai.tutor.response", "learner-1", "tenant-1", "session-1"),
      type: "ai.tutor.response",
      object: {
        moduleId: "module-1",
        claimIds: ["claim-1"],
        responseId: "response-1",
      },
      result: {
        consentState: "granted",
        learnerContextScope: "module",
        promptVersion: "tutor-v1",
        modelVersion: "model-v1",
        retrievedContentIds: ["content-1"],
        safetyFilterResult: "passed",
        latencyMs: 50,
        humanReviewRequired: false,
        containsDirectPii: false,
        blocked: false,
      },
    };
    const blocked: AIGovernanceBlockedEvent = {
      ...createBaseEvent("ai.governance.blocked", "learner-1", "tenant-1", "session-1"),
      type: "ai.governance.blocked",
      object: { useCase: "tutor" },
      result: {
        consentState: "missing",
        learnerContextScope: "module",
        promptVersion: "tutor-v1",
        modelVersion: "model-v1",
        retrievedContentIds: [],
        safetyFilterResult: "blocked",
        latencyMs: 0,
        humanReviewRequired: false,
        containsDirectPii: false,
        blocked: true,
        reason: "consent_missing",
      },
    };

    const learningEvents: LearningTelemetryEvent[] = [answer, hint, tutor, blocked];
    expect(learningEvents.map((event) => event.type)).toEqual([
      "assess.answer",
      "assist.hint",
      "ai.tutor.response",
      "ai.governance.blocked",
    ]);
  });
});
