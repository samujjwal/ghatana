/**
 * AI Routes
 *
 * Server-side AI suggestion endpoints for the web app.
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
  suggestedPayload: Record<string, unknown>;
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

  return {
    id: `${context.projectId}-${kind.toLowerCase()}-${index + 1}`,
    kind,
    title: `${artifactTitle} draft`,
    summary: `Server-generated starter ${artifactTitle.toLowerCase()} for ${phase} phase.`,
    reasoning:
      `This artifact aligns with ${phase} lifecycle expectations and fills a missing gap ` +
      `based on existing project evidence.`,
    confidence: 82,
    suggestedPayload: {
      title: artifactTitle,
      phase,
      status: 'draft',
      generatedBy: 'api-ai-suggest-artifacts',
      generatedAt: new Date().toISOString(),
      projectDescription: context.projectDescription ?? '',
      checklist: [
        `Define scope for ${artifactTitle.toLowerCase()}`,
        'Capture assumptions and constraints',
        'Attach supporting evidence links',
      ],
    },
  };
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
