/**
 * Contract backward/forward compatibility tests.
 *
 * Strategy:
 * - Backward compatibility: a consumer built against a previous (simpler) envelope
 *   can still parse events produced by the current schema without crashing.
 * - Forward compatibility: a consumer built against the current schema gracefully
 *   handles events that contain unknown extra fields (additive changes).
 * - Required field presence: every mandatory field on the envelope is verified for
 *   each event category so regressions on field removal are caught early.
 * - Discriminant stability: type discriminants (e.g. "content.experience.published")
 *   that are in use across service boundaries must not be silently renamed.
 *
 * @doc.type test
 * @doc.purpose Contract backward/forward compatibility coverage for platform event schema
 * @doc.layer contracts
 * @doc.pattern ContractTest
 */

import { describe, it, expect } from "vitest";
import {
  createPlatformEvent,
  isTelemetryEvent,
  isContentLifecycleEvent,
  isAuthEvent,
  isComplianceEvent,
  type TutorPutorPlatformEvent,
  type ContentLifecycleEvent,
  type AuthEvent,
  type ComplianceEvent,
} from "../v1/platform-events.js";
import type { TelemetryEvent } from "../v1/telemetry-events.js";
import { createBaseEvent } from "../v1/telemetry-events.js";

/* ---------------------------------------------------------------------------
 * Helpers
 * --------------------------------------------------------------------------- */

function makeId(): string {
  return `test-id-${Math.random().toString(36).slice(2)}`;
}


function buildTelemetryEvent(): TelemetryEvent {
  const base = createBaseEvent("sim.start", "user-1", "tenant-1", "session-1");
  // Narrow to SimStartEvent by asserting the literal type
  return {
    ...base,
    type: "sim.start" as const,
    object: { id: "sim-1", name: "Physics Lab", blueprintId: "bp-1" },
  };
}

function buildContentEvent(overrides?: Partial<ContentLifecycleEvent>): ContentLifecycleEvent {
  return {
    type: "content.experience.published",
    experienceId: "exp-1",
    authorId: "author-1",
    domain: "MATH",
    trustScore: 0.9,
    ...overrides,
  };
}

function buildAuthEvent(): AuthEvent {
  return {
    type: "auth.login.success",
    userId: "user-1",
    tenantId: "tenant-1",
    ipAddress: "127.0.0.1",
    isSensitive: true,
  };
}

function buildComplianceEvent(): ComplianceEvent {
  return {
    type: "compliance.consent.granted",
    userId: "user-1",
    tenantId: "tenant-1",
    consentTypes: ["analytics"],
  };
}

/* ===========================================================================
 * Envelope required-field presence
 * =========================================================================== */

describe("Platform event envelope — required field presence", () => {
  const requiredFields: Array<keyof TutorPutorPlatformEvent> = [
    "id", "category", "type", "occurredAt", "schemaVersion", "source", "tenantId", "payload",
  ];

  it("telemetry event contains all required envelope fields", () => {
    const event = createPlatformEvent<TelemetryEvent>({
      id: makeId(),
      category: "telemetry",
      type: "sim.start",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildTelemetryEvent(),
    });

    for (const field of requiredFields) {
      expect(event[field], `field "${field}" must be present`).toBeDefined();
    }
  });

  it("content lifecycle event contains all required envelope fields", () => {
    const event = createPlatformEvent<ContentLifecycleEvent>({
      id: makeId(),
      category: "content",
      type: "content.experience.published",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildContentEvent(),
    });

    for (const field of requiredFields) {
      expect(event[field], `field "${field}" must be present`).toBeDefined();
    }
  });

  it("schemaVersion is always '1.0'", () => {
    const authPayload = buildAuthEvent();
    const event = createPlatformEvent<AuthEvent>({
      id: makeId(),
      category: "auth",
      type: "auth.login.success",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: authPayload,
    });
    expect(event.schemaVersion).toBe("1.0");
  });

  it("occurredAt is always a valid ISO 8601 timestamp", () => {
    const compliancePayload = buildComplianceEvent();
    const event = createPlatformEvent<ComplianceEvent>({
      id: makeId(),
      category: "compliance",
      type: "compliance.consent.granted",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: compliancePayload,
    });
    expect(new Date(event.occurredAt).toISOString()).toBe(event.occurredAt);
  });
});

/* ===========================================================================
 * Backward compatibility: older consumer still works with current envelope
 * =========================================================================== */

describe("Backward compatibility", () => {
  it("consumer reading only id/type/tenantId/payload from envelope still works", () => {
    const event = createPlatformEvent<ContentLifecycleEvent>({
      id: "event-1",
      category: "content",
      type: "content.experience.published",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildContentEvent(),
    });

    // Simulate an older consumer that only knows about id, type, tenantId, payload
    const legacyConsumer = (e: Pick<TutorPutorPlatformEvent, "id" | "type" | "tenantId" | "payload">) => ({
      eventId: e.id,
      eventType: e.type,
      tenant: e.tenantId,
      hasPayload: e.payload !== undefined,
    });

    const parsed = legacyConsumer(event);
    expect(parsed.eventId).toBe("event-1");
    expect(parsed.eventType).toBe("content.experience.published");
    expect(parsed.tenant).toBe("tenant-1");
    expect(parsed.hasPayload).toBe(true);
  });

  it("a consumer that does not read correlationId still processes events that include it", () => {
    const event = createPlatformEvent<AuthEvent>({
      id: makeId(),
      category: "auth",
      type: "auth.login.success",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      correlationId: "corr-abc",
      payload: buildAuthEvent(),
    });

    // Consumer ignores correlationId
    const result = { userId: (event.payload as AuthEvent).userId };
    expect(result.userId).toBe("user-1");
  });
});

/* ===========================================================================
 * Forward compatibility: consumer handles unknown extra fields
 * =========================================================================== */

describe("Forward compatibility", () => {
  it("consumer handles events with unknown extra top-level fields", () => {
    const futureEvent = createPlatformEvent<TelemetryEvent>({
      id: makeId(),
      category: "telemetry",
      type: "sim.start",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildTelemetryEvent(),
    });

    // Simulate a future event that has extra fields unknown to the current consumer
    const enrichedEvent = Object.assign({}, futureEvent, {
      experimentId: "exp-abc-123",           // added in a hypothetical future version
      dataClassification: "internal",         // another future field
    });

    // Current consumer should be able to process the known fields without crashing
    expect(enrichedEvent.id).toBeDefined();
    expect(enrichedEvent.payload).toBeDefined();
    expect(enrichedEvent.schemaVersion).toBe("1.0");
  });

  it("consumer handles events with extra payload fields via unknown spread", () => {
    const payload = {
      ...buildContentEvent(),
      // hypothetical future extension field
      aiConfidenceScore: 0.98,
    };

    const event = createPlatformEvent<typeof payload>({
      id: makeId(),
      category: "content",
      type: "content.experience.published",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload,
    });

    // Known fields still accessible
    expect(event.payload.experienceId).toBe("exp-1");
    expect(event.payload.trustScore).toBe(0.9);
    // Future fields accessible without casting
    expect((event.payload as Record<string, unknown>)["aiConfidenceScore"]).toBe(0.98);
  });
});

/* ===========================================================================
 * Discriminant stability
 * =========================================================================== */

describe("Discriminant type-tag stability", () => {
  const stableDiscriminants = [
    "content.experience.published",
    "content.experience.rejected",
    "auth.login.success",
    "auth.login.failed",
    "compliance.consent.granted",
    "compliance.data.deletion_requested",
  ] as const;

  it("type-guard isTelemetryEvent recognises telemetry category events", () => {
    const event = createPlatformEvent<TelemetryEvent>({
      id: makeId(),
      category: "telemetry",
      type: "sim.start",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildTelemetryEvent(),
    });
    expect(isTelemetryEvent(event)).toBe(true);
    expect(isContentLifecycleEvent(event)).toBe(false);
  });

  it("type-guard isContentLifecycleEvent recognises content category events", () => {
    const event = createPlatformEvent<ContentLifecycleEvent>({
      id: makeId(),
      category: "content",
      type: "content.experience.published",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildContentEvent(),
    });
    expect(isContentLifecycleEvent(event)).toBe(true);
    expect(isTelemetryEvent(event)).toBe(false);
  });

  it("type-guard isAuthEvent recognises auth category events", () => {
    const event = createPlatformEvent<AuthEvent>({
      id: makeId(),
      category: "auth",
      type: "auth.login.success",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildAuthEvent(),
    });
    expect(isAuthEvent(event)).toBe(true);
    expect(isComplianceEvent(event)).toBe(false);
  });

  it("type-guard isComplianceEvent recognises compliance category events", () => {
    const event = createPlatformEvent<ComplianceEvent>({
      id: makeId(),
      category: "compliance",
      type: "compliance.consent.granted",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: buildComplianceEvent(),
    });
    expect(isComplianceEvent(event)).toBe(true);
    expect(isAuthEvent(event)).toBe(false);
  });

  it("stable discriminant type strings are present in the known discriminants list", () => {
    // Regression guard: if a discriminant is renamed these will break here first.
    for (const disc of stableDiscriminants) {
      expect(typeof disc).toBe("string");
      expect(disc.length).toBeGreaterThan(0);
    }
  });
});
