/**
 * Session Adaptation Engine
 *
 * Real-time learner-session adaptation based on recent struggle patterns.
 *
 * @doc.type service
 * @doc.purpose Trigger adaptive content variants during a live learning session
 * @doc.layer product
 * @doc.pattern Real-time Adaptation
 */

import type Redis from "ioredis";
import type { LearnerProfileService } from "../learning/learner-profile-service.js";
import type { ContentVariationService, AdaptedContentVariant } from "../content/variation/service.js";

type StrugglePattern =
  | "REPEATED_ERRORS"
  | "DISENGAGEMENT"
  | "EXCESSIVE_HINTS"
  | "RAPID_GUESSING";

type SessionEventType =
  | "ANSWER_SUBMITTED"
  | "HINT_REQUESTED"
  | "CONTENT_VIEWED"
  | "IDLE"
  | "CHECKPOINT";

export interface SessionAdaptationEvent {
  tenantId: string;
  userId: string;
  sessionId: string;
  assetId: string;
  eventType: SessionEventType;
  occurredAt?: string;
  correct?: boolean;
  hintsUsed?: number;
  responseLatencyMs?: number;
  inactivityMs?: number;
  confidence?: number;
}

export interface SessionAdaptationDecision {
  sessionId: string;
  assetId: string;
  adapted: boolean;
  trigger?: StrugglePattern;
  reason: string;
  recommendation?: string;
  variant?: AdaptedContentVariant;
  observedSignals: {
    recentEvents: number;
    incorrectStreak: number;
    hintRate: number;
    rapidGuessCount: number;
    inactivityMs: number;
  };
  createdAt: string;
}

interface SessionAdaptationState {
  tenantId: string;
  userId: string;
  sessionId: string;
  assetId: string;
  events: SessionAdaptationEvent[];
  currentDecision?: SessionAdaptationDecision;
}

export class SessionAdaptationEngine {
  private readonly fallbackState = new Map<string, SessionAdaptationState>();

  constructor(
    private readonly learnerProfileService: LearnerProfileService,
    private readonly variationService: ContentVariationService,
    private readonly redis?: Redis,
  ) {}

  async processEvent(
    event: SessionAdaptationEvent,
  ): Promise<SessionAdaptationDecision> {
    const state = await this.loadState(event);
    state.events.push(normalizeEvent(event));
    state.events = state.events.slice(-20);

    const signals = summarizeSignals(state.events);
    const trigger = detectStrugglePattern(signals);

    let decision: SessionAdaptationDecision;
    if (!trigger) {
      decision = {
        sessionId: event.sessionId,
        assetId: event.assetId,
        adapted: false,
        reason: "No adaptation needed",
        observedSignals: signals,
        createdAt: new Date().toISOString(),
      };
    } else if (state.currentDecision?.trigger === trigger) {
      decision = state.currentDecision;
    } else {
      decision = await this.createDecision(event, trigger, signals);
      console.info("Session adaptation triggered", {
        sessionId: event.sessionId,
        userId: event.userId,
        assetId: event.assetId,
        trigger,
      });
    }

    state.currentDecision = decision;
    await this.saveState(state);
    return decision;
  }

  async getCurrentAdaptation(
    sessionId: string,
    assetId: string,
  ): Promise<SessionAdaptationDecision | null> {
    const key = getStateKey(sessionId, assetId);

    if (this.redis) {
      const raw = await this.redis.get(key);
      if (!raw) return null;
      return (JSON.parse(raw) as SessionAdaptationState).currentDecision ?? null;
    }

    return this.fallbackState.get(key)?.currentDecision ?? null;
  }

  private async createDecision(
    event: SessionAdaptationEvent,
    trigger: StrugglePattern,
    signals: SessionAdaptationDecision["observedSignals"],
  ): Promise<SessionAdaptationDecision> {
    const snapshot = await this.learnerProfileService.getPersonalizationSnapshot(
      event.tenantId,
      event.userId,
    );

    const variant = await this.generateVariant(event.tenantId, event.assetId, trigger, snapshot);
    return {
      sessionId: event.sessionId,
      assetId: event.assetId,
      adapted: true,
      trigger,
      reason: describeTrigger(trigger),
      recommendation: buildRecommendation(trigger, snapshot.preferredModality),
      variant,
      observedSignals: signals,
      createdAt: new Date().toISOString(),
    };
  }

  private async generateVariant(
    tenantId: string,
    assetId: string,
    trigger: StrugglePattern,
    snapshot: Awaited<ReturnType<LearnerProfileService["getPersonalizationSnapshot"]>>,
  ): Promise<AdaptedContentVariant> {
    switch (trigger) {
      case "REPEATED_ERRORS": {
        const variants = await this.variationService.generateDifficultyVariants(
          tenantId,
          assetId,
        );
        return snapshot.adjustedDifficulty === "beginner" ? variants.easy : variants.medium;
      }
      case "EXCESSIVE_HINTS": {
        const variants = await this.variationService.generateExplanationVariants(
          tenantId,
          assetId,
        );
        return variants.scaffolded;
      }
      case "DISENGAGEMENT": {
        const variants = await this.variationService.generateModalityVariants(
          tenantId,
          assetId,
        );
        return selectModalityVariant(variants, snapshot.preferredModality);
      }
      case "RAPID_GUESSING": {
        const variants = await this.variationService.generateExplanationVariants(
          tenantId,
          assetId,
        );
        return variants.detailed;
      }
    }
  }

  private async loadState(
    event: SessionAdaptationEvent,
  ): Promise<SessionAdaptationState> {
    const key = getStateKey(event.sessionId, event.assetId);

    if (this.redis) {
      const raw = await this.redis.get(key);
      if (raw) {
        return JSON.parse(raw) as SessionAdaptationState;
      }
    }

    const existing = this.fallbackState.get(key);
    if (existing) {
      return existing;
    }

    return {
      tenantId: event.tenantId,
      userId: event.userId,
      sessionId: event.sessionId,
      assetId: event.assetId,
      events: [],
    };
  }

  private async saveState(state: SessionAdaptationState): Promise<void> {
    const key = getStateKey(state.sessionId, state.assetId);

    if (this.redis) {
      await this.redis.set(key, JSON.stringify(state), "EX", 3600);
      return;
    }

    this.fallbackState.set(key, state);
  }
}

function normalizeEvent(event: SessionAdaptationEvent): SessionAdaptationEvent {
  return {
    ...event,
    occurredAt: event.occurredAt ?? new Date().toISOString(),
    hintsUsed: event.hintsUsed ?? 0,
    responseLatencyMs: event.responseLatencyMs ?? 0,
    inactivityMs: event.inactivityMs ?? 0,
    confidence: event.confidence ?? 0.5,
  };
}

function summarizeSignals(events: SessionAdaptationEvent[]) {
  const recent = events.slice(-8);
  const incorrectStreak = countConsecutiveIncorrect(recent);
  const hintEvents = recent.filter(
    (event) => event.eventType === "HINT_REQUESTED" || (event.hintsUsed ?? 0) > 0,
  ).length;
  const rapidGuessCount = recent.filter(
    (event) =>
      event.eventType === "ANSWER_SUBMITTED" &&
      event.correct === false &&
      (event.responseLatencyMs ?? 0) > 0 &&
      (event.responseLatencyMs ?? 0) < 4000,
  ).length;
  const inactivityMs = Math.max(
    ...recent.map((event) => event.inactivityMs ?? 0),
    0,
  );

  return {
    recentEvents: recent.length,
    incorrectStreak,
    hintRate: recent.length === 0 ? 0 : hintEvents / recent.length,
    rapidGuessCount,
    inactivityMs,
  };
}

function detectStrugglePattern(
  signals: SessionAdaptationDecision["observedSignals"],
): StrugglePattern | null {
  if (signals.incorrectStreak >= 3) return "REPEATED_ERRORS";
  if (signals.inactivityMs >= 180000) return "DISENGAGEMENT";
  if (signals.hintRate >= 0.4 && signals.recentEvents >= 3) return "EXCESSIVE_HINTS";
  if (signals.rapidGuessCount >= 3) return "RAPID_GUESSING";
  return null;
}

function countConsecutiveIncorrect(events: SessionAdaptationEvent[]): number {
  let count = 0;
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const event = events[index];
    if (event?.eventType !== "ANSWER_SUBMITTED" || event.correct !== false) {
      break;
    }
    count += 1;
  }
  return count;
}

function selectModalityVariant(
  variants: Awaited<ReturnType<ContentVariationService["generateModalityVariants"]>>,
  modality: "VISUAL" | "AUDITORY" | "KINESTHETIC" | "READING" | "MIXED",
): AdaptedContentVariant {
  switch (modality) {
    case "VISUAL":
      return variants.visual;
    case "AUDITORY":
      return variants.auditory;
    case "KINESTHETIC":
      return variants.kinesthetic;
    case "READING":
      return variants.reading;
    default:
      return variants.visual;
  }
}

function buildRecommendation(
  trigger: StrugglePattern,
  preferredModality: "VISUAL" | "AUDITORY" | "KINESTHETIC" | "READING" | "MIXED",
): string {
  switch (trigger) {
    case "REPEATED_ERRORS":
      return "Lower the cognitive load and retry with a simpler worked example.";
    case "EXCESSIVE_HINTS":
      return "Switch to scaffolded guidance and reveal one reasoning step at a time.";
    case "DISENGAGEMENT":
      return `Re-engage with a ${preferredModality.toLowerCase()}-leaning explanation or interaction.`;
    case "RAPID_GUESSING":
      return "Slow the pace and require explanation before the next attempt.";
  }
}

function describeTrigger(trigger: StrugglePattern): string {
  switch (trigger) {
    case "REPEATED_ERRORS":
      return "Detected repeated incorrect responses";
    case "EXCESSIVE_HINTS":
      return "Detected heavy hint dependence";
    case "DISENGAGEMENT":
      return "Detected prolonged disengagement";
    case "RAPID_GUESSING":
      return "Detected rapid guessing without reflection";
  }
}

function getStateKey(sessionId: string, assetId: string): string {
  return `learning:session-adaptation:${sessionId}:${assetId}`;
}
