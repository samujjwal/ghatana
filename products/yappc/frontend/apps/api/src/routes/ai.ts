/**
 * AI Routes
 *
 * Server-side suggestion endpoints for the web app.
 *
 * These endpoints use deterministic, rule-based heuristics. They are not
 * model inference results and expose explicit scoring metadata.
 *
 * @doc.type router
 * @doc.purpose AI suggestion APIs
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance } from 'fastify';

type LifecyclePhaseId =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'IMPROVE';

interface ExistingArtifact {
  kind: string;
  payload?: Record<string, unknown>;
}

interface SuggestionContext {
  projectId: string;
  currentPhase: LifecyclePhaseId;
  existingArtifacts: ExistingArtifact[];
  projectDescription?: string;
}

interface SuggestArtifactsBody {
  context: SuggestionContext;
  targetKinds?: string[];
}

interface ArtifactSuggestion {
  id: string;
  kind: string;
  title: string;
  summary: string;
  reasoning: string;
  confidence: number;
  confidenceType: 'rule_based_heuristic';
  confidenceReason: string;
  suggestedPayload: Record<string, unknown>;
}

interface SuggestionDefaults {
  defaultOwnerRole: string;
  defaultPriority: 'high' | 'medium' | 'low';
  defaultTargetDays: number;
  defaultReasoning: string;
}

const phaseArtifactDefaults: Record<LifecyclePhaseId, readonly string[]> = {
  INTENT: ['IDEA_BRIEF', 'RESEARCH_PACK', 'PROBLEM_STATEMENT'],
  SHAPE: ['REQUIREMENTS', 'ADR', 'UX_SPEC', 'THREAT_MODEL'],
  VALIDATE: ['VALIDATION_REPORT', 'SIMULATION_RESULTS'],
  GENERATE: ['DELIVERY_PLAN', 'RELEASE_STRATEGY'],
  RUN: ['EVIDENCE_PACK', 'RELEASE_PACKET'],
  OBSERVE: ['OPS_BASELINE', 'INCIDENT_REPORT'],
  IMPROVE: ['ENHANCEMENT_REQUESTS', 'LEARNING_RECORD'],
} as const;

function isLifecyclePhase(value: string): value is LifecyclePhaseId {
  return Object.prototype.hasOwnProperty.call(phaseArtifactDefaults, value);
}

function createSuggestion(
  context: SuggestionContext,
  kind: string,
  index: number
): ArtifactSuggestion {
  const phase = context.currentPhase;
  const artifactTitle = kind
    .toLowerCase()
    .split('_')
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join(' ');

  const hasProjectDescription =
    typeof context.projectDescription === 'string' &&
    context.projectDescription.trim().length > 0;
  const hasExistingEvidence = context.existingArtifacts.length > 0;

  // Deterministic heuristic score: bounded and explainable, not model confidence.
  const confidence = Math.min(
    95,
    55 + (hasProjectDescription ? 20 : 0) + (hasExistingEvidence ? 15 : 0)
  );
  const defaults = inferSuggestionDefaults(context);

  return {
    id: `${context.projectId}-${kind.toLowerCase()}-${index + 1}`,
    kind,
    title: `${artifactTitle} draft`,
    summary: `Server-generated starter ${artifactTitle.toLowerCase()} for ${phase} phase.`,
    reasoning:
      `This artifact aligns with ${phase} lifecycle expectations and fills a missing gap ` +
      `based on existing project evidence.`,
    confidence,
    confidenceType: 'rule_based_heuristic',
    confidenceReason:
      'Score is computed from deterministic evidence coverage (project description and existing artifacts).',
    suggestedPayload: {
      title: artifactTitle,
      phase,
      status: 'draft',
      generatedBy: 'api-rule-based-suggest-artifacts',
      generatedAt: new Date().toISOString(),
      projectDescription: context.projectDescription ?? '',
      defaultOwnerRole: defaults.defaultOwnerRole,
      defaultPriority: defaults.defaultPriority,
      defaultTargetDays: defaults.defaultTargetDays,
      defaultReasoning: defaults.defaultReasoning,
      checklist: [
        `Define scope for ${artifactTitle.toLowerCase()}`,
        'Capture assumptions and constraints',
        'Attach supporting evidence links',
      ],
    },
  };
}

function inferSuggestionDefaults(context: SuggestionContext): SuggestionDefaults {
  const description = (context.projectDescription ?? '').toLowerCase();

  const phaseDefaults: Record<LifecyclePhaseId, SuggestionDefaults> = {
    INTENT: {
      defaultOwnerRole: 'Product Team',
      defaultPriority: 'medium',
      defaultTargetDays: 7,
      defaultReasoning: 'Intent artifacts need quick alignment before architecture work begins.',
    },
    SHAPE: {
      defaultOwnerRole: 'Architecture Team',
      defaultPriority: 'medium',
      defaultTargetDays: 5,
      defaultReasoning: 'Shape artifacts are required to unblock validation and generation phases.',
    },
    VALIDATE: {
      defaultOwnerRole: 'QA Team',
      defaultPriority: 'high',
      defaultTargetDays: 3,
      defaultReasoning: 'Validation outputs gate downstream delivery and should be prioritized.',
    },
    GENERATE: {
      defaultOwnerRole: 'Backend Team',
      defaultPriority: 'medium',
      defaultTargetDays: 4,
      defaultReasoning: 'Generated delivery artifacts should stay synchronized with current requirements.',
    },
    RUN: {
      defaultOwnerRole: 'DevOps Team',
      defaultPriority: 'high',
      defaultTargetDays: 2,
      defaultReasoning: 'Run-phase artifacts are operationally urgent and close to deployment windows.',
    },
    OBSERVE: {
      defaultOwnerRole: 'SRE Team',
      defaultPriority: 'high',
      defaultTargetDays: 2,
      defaultReasoning: 'Observation artifacts reduce incident MTTR and should be captured rapidly.',
    },
    IMPROVE: {
      defaultOwnerRole: 'Product Team',
      defaultPriority: 'medium',
      defaultTargetDays: 7,
      defaultReasoning: 'Improvement records support planned iterative changes across releases.',
    },
  };

  if (hasAnyKeyword(description, ['security', 'compliance', 'audit', 'threat', 'auth'])) {
    return {
      defaultOwnerRole: 'Security Team',
      defaultPriority: 'high',
      defaultTargetDays: Math.min(phaseDefaults[context.currentPhase].defaultTargetDays, 3),
      defaultReasoning: 'Security-related context requires tighter ownership and accelerated delivery targets.',
    };
  }

  if (hasAnyKeyword(description, ['latency', 'performance', 'throughput', 'scale', 'scalability'])) {
    return {
      defaultOwnerRole: 'Platform Team',
      defaultPriority: 'high',
      defaultTargetDays: Math.min(phaseDefaults[context.currentPhase].defaultTargetDays, 4),
      defaultReasoning: 'Performance-sensitive context prioritizes platform ownership and tighter timelines.',
    };
  }

  if (hasAnyKeyword(description, ['ux', 'design', 'frontend', 'accessibility'])) {
    return {
      defaultOwnerRole: 'Frontend Team',
      defaultPriority: 'medium',
      defaultTargetDays: phaseDefaults[context.currentPhase].defaultTargetDays,
      defaultReasoning: 'User-experience context maps to frontend ownership for artifact follow-through.',
    };
  }

  return phaseDefaults[context.currentPhase];
}

function hasAnyKeyword(input: string, keywords: readonly string[]): boolean {
  return keywords.some((keyword) => input.includes(keyword));
}

export default async function aiRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: SuggestArtifactsBody }>(
    '/ai/suggest-artifacts',
    async (request, reply) => {
      const payload = request.body;

      if (!payload || !payload.context) {
        return reply.status(400).send({
          error: 'Invalid payload: context is required.',
          correlationId: request.correlationId,
        });
      }

      const { context, targetKinds } = payload;

      if (!context.projectId || context.projectId.trim().length === 0) {
        return reply.status(400).send({
          error: 'Invalid payload: context.projectId is required.',
          correlationId: request.correlationId,
        });
      }

      if (!isLifecyclePhase(context.currentPhase)) {
        return reply.status(400).send({
          error: 'Invalid payload: context.currentPhase is invalid.',
          correlationId: request.correlationId,
        });
      }

      const existingKinds = new Set(context.existingArtifacts.map((artifact) => artifact.kind));
      const requestedKinds =
        targetKinds && targetKinds.length > 0
          ? targetKinds
          : [...phaseArtifactDefaults[context.currentPhase]];

      const missingKinds = requestedKinds.filter((kind) => !existingKinds.has(kind));
      const suggestions = missingKinds.map((kind, index) =>
        createSuggestion(context, kind, index)
      );

      return {
        suggestions,
        correlationId: request.correlationId,
      };
    }
  );
}
