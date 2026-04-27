/**
 * TutorPutor Platform Events — Consolidated Event Contract
 *
 * Defines the canonical `TutorPutorPlatformEvent` envelope that wraps all
 * event types emitted by the TutorPutor platform. Aligned with the
 * `@ghatana/events` `PlatformEvent` pattern.
 *
 * Every event bus message, audit log entry, and analytics pipeline record
 * must conform to this envelope. Do NOT emit raw domain event payloads onto
 * shared buses without this wrapper.
 *
 * @doc.type module
 * @doc.purpose Canonical TutorPutor platform event envelope
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

import type { TelemetryEvent } from "./telemetry-events.js";

// ============================================================================
// Event categories
// ============================================================================

/**
 * High-level category for routing / filtering events on the bus.
 */
export type PlatformEventCategory =
  | "telemetry"        // xAPI learning telemetry events
  | "content"          // Content lifecycle: created, published, rejected, archived
  | "user"             // User lifecycle: registered, deactivated, role-changed
  | "auth"             // Auth events: login, logout, token-refresh, failed-auth
  | "billing"          // Payment / subscription events
  | "compliance"       // Consent, data export, deletion requests
  | "system";          // System events: startup, shutdown, health degradation

// ============================================================================
// Platform event envelope
// ============================================================================

/**
 * Canonical envelope for all events emitted by the TutorPutor platform.
 *
 * Rules:
 * - `id` must be a UUID v4.
 * - `occurredAt` is always ISO 8601 UTC.
 * - `schemaVersion` must match the contract version that defines `payload`.
 * - `correlationId` must be propagated from the originating HTTP request where available.
 * - `source` identifies the emitting service.
 */
export interface TutorPutorPlatformEvent<
  TPayload = TelemetryEvent | ContentLifecycleEvent | UserLifecycleEvent | AuthEvent | BillingEvent | ComplianceEvent | SystemEvent,
> {
  /** UUID v4 — unique event identifier */
  readonly id: string;
  /** Event category for bus routing */
  readonly category: PlatformEventCategory;
  /** Event type discriminant (e.g. "sim.start", "content.published", "user.registered") */
  readonly type: string;
  /** ISO 8601 UTC timestamp */
  readonly occurredAt: string;
  /** Schema version, e.g. "1.0" */
  readonly schemaVersion: "1.0";
  /** Emitting service name */
  readonly source: "tutorputor-platform" | "tutorputor-content-generation" | "tutorputor-gateway";
  /** Tenant ID for multi-tenant routing */
  readonly tenantId: string;
  /**
   * Correlation ID propagated from the originating HTTP request.
   * Used for distributed tracing across service boundaries.
   */
  readonly correlationId?: string;
  /** The domain-specific payload */
  readonly payload: TPayload;
}

// ============================================================================
// Content lifecycle events
// ============================================================================

export type ContentLifecycleEventType =
  | "content.experience.created"
  | "content.experience.published"
  | "content.experience.rejected"
  | "content.experience.archived"
  | "content.claim.validated"
  | "content.artifact.generated"
  | "content.artifact.failed";

export interface ContentLifecycleEvent {
  readonly type: ContentLifecycleEventType;
  readonly experienceId: string;
  readonly authorId: string;
  readonly domain: string;
  readonly trustScore?: number;
  readonly reason?: string;
}

// ============================================================================
// User lifecycle events
// ============================================================================

export type UserLifecycleEventType =
  | "user.registered"
  | "user.deactivated"
  | "user.role.changed"
  | "user.profile.updated";

export interface UserLifecycleEvent {
  readonly type: UserLifecycleEventType;
  readonly userId: string;
  readonly tenantId: string;
  readonly role?: string;
  readonly previousRole?: string;
}

// ============================================================================
// Auth events
// ============================================================================

export type AuthEventType =
  | "auth.login.success"
  | "auth.login.failed"
  | "auth.logout"
  | "auth.token.refreshed"
  | "auth.token.expired";

export interface AuthEvent {
  readonly type: AuthEventType;
  readonly userId?: string;
  readonly tenantId: string;
  readonly ipAddress?: string;
  readonly userAgent?: string;
  /** Whether the event is security-sensitive (affects audit log retention) */
  readonly isSensitive: true;
}

// ============================================================================
// Billing events
// ============================================================================

export type BillingEventType =
  | "billing.subscription.created"
  | "billing.subscription.cancelled"
  | "billing.subscription.renewed"
  | "billing.payment.succeeded"
  | "billing.payment.failed";

export interface BillingEvent {
  readonly type: BillingEventType;
  readonly tenantId: string;
  readonly planId: string;
  readonly externalSubscriptionId?: string;
  readonly amountCents?: number;
  readonly currency?: string;
}

// ============================================================================
// Compliance events
// ============================================================================

export type ComplianceEventType =
  | "compliance.consent.granted"
  | "compliance.consent.revoked"
  | "compliance.data.export.requested"
  | "compliance.data.deletion.requested"
  | "compliance.data.deletion.completed";

export interface ComplianceEvent {
  readonly type: ComplianceEventType;
  readonly userId: string;
  readonly tenantId: string;
  /** Consent types affected, e.g. ["analytics", "ai_processing"] */
  readonly consentTypes?: string[];
}

// ============================================================================
// System events
// ============================================================================

export type SystemEventType =
  | "system.startup"
  | "system.shutdown"
  | "system.health.degraded"
  | "system.health.recovered"
  | "system.worker.started"
  | "system.worker.stopped";

export interface SystemEvent {
  readonly type: SystemEventType;
  readonly service: string;
  readonly version?: string;
  readonly details?: Record<string, unknown>;
}

// ============================================================================
// Typed event constructors
// ============================================================================

/**
 * Creates a canonical platform event envelope.
 * Callers must supply a UUID v4 `id` — use `crypto.randomUUID()`.
 */
export function createPlatformEvent<TPayload>(
  fields: Omit<TutorPutorPlatformEvent<TPayload>, "occurredAt" | "schemaVersion">,
): TutorPutorPlatformEvent<TPayload> {
  return {
    ...fields,
    occurredAt: new Date().toISOString(),
    schemaVersion: "1.0",
  };
}

// ============================================================================
// Type guards
// ============================================================================

export function isTelemetryEvent(
  event: TutorPutorPlatformEvent,
): event is TutorPutorPlatformEvent<TelemetryEvent> {
  return event.category === "telemetry";
}

export function isContentLifecycleEvent(
  event: TutorPutorPlatformEvent,
): event is TutorPutorPlatformEvent<ContentLifecycleEvent> {
  return event.category === "content";
}

export function isAuthEvent(
  event: TutorPutorPlatformEvent,
): event is TutorPutorPlatformEvent<AuthEvent> {
  return event.category === "auth";
}

export function isComplianceEvent(
  event: TutorPutorPlatformEvent,
): event is TutorPutorPlatformEvent<ComplianceEvent> {
  return event.category === "compliance";
}
