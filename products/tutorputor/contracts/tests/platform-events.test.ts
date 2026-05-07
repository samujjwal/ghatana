/**
 * Tests for the canonical TutorPutor platform event contracts.
 * Verifies the envelope structure, type guards, and constructor utility.
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function uuid(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

// ---------------------------------------------------------------------------
// createPlatformEvent
// ---------------------------------------------------------------------------

describe("createPlatformEvent", () => {
  it("populates occurredAt and schemaVersion automatically", () => {
    const base = createBaseEvent("sim.start", "user-1", "tenant-1", "session-1");
    const event = createPlatformEvent<TelemetryEvent>({
      id: uuid(),
      category: "telemetry",
      type: "sim.start",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      payload: { ...base, type: "sim.start", object: { id: "sim-1", name: "Physics Lab", blueprintId: "bp-1" } },
    });

    expect(event.schemaVersion).toBe("1.0");
    expect(event.occurredAt).toBeDefined();
    expect(new Date(event.occurredAt).toISOString()).toBe(event.occurredAt);
  });

  it("includes correlationId when provided", () => {
    const correlationId = uuid();
    const event = createPlatformEvent<ContentLifecycleEvent>({
      id: uuid(),
      category: "content",
      type: "content.experience.published",
      source: "tutorputor-platform",
      tenantId: "tenant-1",
      correlationId,
      payload: {
        type: "content.experience.published",
        experienceId: "exp-1",
        authorId: "author-1",
        domain: "MATH",
        trustScore: 0.87,
      },
    });

    expect(event.correlationId).toBe(correlationId);
    expect(event.payload.trustScore).toBe(0.87);
  });
});

// ---------------------------------------------------------------------------
// Type guards
// ---------------------------------------------------------------------------

describe("isTelemetryEvent", () => {
  it("returns true for telemetry category events", () => {
    const base = createBaseEvent("sim.start", "u1", "t1", "s1");
    const event = createPlatformEvent<TelemetryEvent>({
      id: uuid(),
      category: "telemetry",
      type: "sim.start",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: { ...base, type: "sim.start", object: { id: "sim-1", name: "Lab", blueprintId: "bp-1" } },
    });
    expect(isTelemetryEvent(event as TutorPutorPlatformEvent)).toBe(true);
  });

  it("returns false for non-telemetry events", () => {
    const event = createPlatformEvent<AuthEvent>({
      id: uuid(),
      category: "auth",
      type: "auth.login.success",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: { type: "auth.login.success", tenantId: "t1", isSensitive: true },
    });
    expect(isTelemetryEvent(event as TutorPutorPlatformEvent)).toBe(false);
  });
});

describe("isContentLifecycleEvent", () => {
  it("returns true for content category events", () => {
    const event = createPlatformEvent<ContentLifecycleEvent>({
      id: uuid(),
      category: "content",
      type: "content.experience.published",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: { type: "content.experience.published", experienceId: "e1", authorId: "a1", domain: "MATH" },
    });
    expect(isContentLifecycleEvent(event as TutorPutorPlatformEvent)).toBe(true);
  });
});

describe("isAuthEvent", () => {
  it("returns true for auth category events", () => {
    const event = createPlatformEvent<AuthEvent>({
      id: uuid(),
      category: "auth",
      type: "auth.login.failed",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: { type: "auth.login.failed", tenantId: "t1", isSensitive: true, ipAddress: "1.2.3.4" },
    });
    expect(isAuthEvent(event as TutorPutorPlatformEvent)).toBe(true);
  });
});

describe("isComplianceEvent", () => {
  it("returns true for compliance category events", () => {
    const event = createPlatformEvent<ComplianceEvent>({
      id: uuid(),
      category: "compliance",
      type: "compliance.consent.revoked",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: { type: "compliance.consent.revoked", userId: "u1", tenantId: "t1", consentTypes: ["ai_processing"] },
    });
    expect(isComplianceEvent(event as TutorPutorPlatformEvent)).toBe(true);
  });

  it("keeps privacy export and deletion as platform compliance events", () => {
    const exportEvent = createPlatformEvent<ComplianceEvent>({
      id: uuid(),
      category: "compliance",
      type: "compliance.telemetry.exported",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: {
        type: "compliance.telemetry.exported",
        userId: "u1",
        tenantId: "t1",
        requestId: "privacy-export-1",
        recordCounts: {
          telemetryEvents: 10,
          aiAuditLogs: 2,
        },
        evidenceUri: "audit://privacy-export-1",
      },
    });
    const deletionEvent = createPlatformEvent<ComplianceEvent>({
      id: uuid(),
      category: "compliance",
      type: "compliance.telemetry.deleted",
      source: "tutorputor-platform",
      tenantId: "t1",
      payload: {
        type: "compliance.telemetry.deleted",
        userId: "u1",
        tenantId: "t1",
        requestId: "privacy-delete-1",
        recordCounts: {
          telemetryEvents: 10,
          aiAuditLogs: 2,
        },
        evidenceUri: "audit://privacy-delete-1",
      },
    });

    expect(isTelemetryEvent(exportEvent as TutorPutorPlatformEvent)).toBe(false);
    expect(isTelemetryEvent(deletionEvent as TutorPutorPlatformEvent)).toBe(false);
    expect(isComplianceEvent(exportEvent as TutorPutorPlatformEvent)).toBe(true);
    expect(isComplianceEvent(deletionEvent as TutorPutorPlatformEvent)).toBe(true);
  });
});
