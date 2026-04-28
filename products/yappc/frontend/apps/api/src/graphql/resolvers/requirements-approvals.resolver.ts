/**
 * Requirements and Approvals Resolvers
 *
 * Adds requirement lifecycle APIs, approval actions, and agent-run visibility.
 *
 * @doc.type module
 * @doc.purpose GraphQL resolvers for requirement lifecycle and governance
 * @doc.layer product
 * @doc.pattern GraphQL Resolvers
 */

import prisma from '../../db';

interface ResolverContext {
  userId?: string;
}

type RequirementStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'REJECTED';

type ApprovalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'EXPIRED';

type AgentRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED';

interface SubmitRequirementArgs {
  projectId: string;
  title: string;
  description: string;
  type?: 'FUNCTIONAL' | 'NON_FUNCTIONAL' | 'BUSINESS' | 'TECHNICAL';
  priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  requiresApproval?: boolean;
}

interface UpdateAgentRunArgs {
  runId: string;
  status: AgentRunStatus;
  stage?: string;
  output?: unknown;
  errorMessage?: string;
}

interface EnrichRequirementArgs {
  requirementId: string;
}

interface AiEnrichmentSuggestion {
  normalizedTitle: string;
  acceptanceCriteria: string[];
  storyTrace: string;
  confidence: number;
  rationale: string;
  source: 'RULE' | 'MODEL';
}

const toISOString = (date: Date | null | undefined): string | null =>
  date?.toISOString() ?? null;

function requireUserId(context: ResolverContext): string {
  if (!context.userId) {
    throw new Error('Authentication required: no userId in context');
  }
  return context.userId;
}

function isTerminalRunStatus(status: AgentRunStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED';
}

// ── Enrichment: rule engine ────────────────────────────────────────────────────

/**
 * Deterministic rule-based enrichment. Always succeeds; used as the fallback
 * when the LLM path is unavailable or returns an unparseable response.
 */
function buildRuleBasedSuggestion(
  title: string,
  description: string
): AiEnrichmentSuggestion {
  const words = description.toLowerCase().split(/\s+/).filter(Boolean);
  const isAuth = words.some((w) => ['auth', 'login', 'password', 'token', 'jwt'].includes(w));
  const isApi = words.some((w) => ['api', 'endpoint', 'rest', 'graphql', 'http'].includes(w));

  const normalizedTitle = title
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/^[a-z]/, (c) => c.toUpperCase());

  const acceptanceCriteria = [
    `Given a valid input, the system should fulfil: "${normalizedTitle}"`,
    `Error states are surfaced to the caller with a structured error message`,
    `The operation is auditable and emits a structured log entry`,
  ];

  if (isAuth) {
    acceptanceCriteria.push(
      'Authentication tokens are validated before any action is taken'
    );
  }

  if (isApi) {
    acceptanceCriteria.push(
      'API response conforms to the documented contract schema'
    );
  }

  const storyTrace = `As a user, I want to ${normalizedTitle.toLowerCase()} so that the system delivers the intended outcome reliably and observably.`;

  // Confidence is a simple heuristic: longer descriptions → higher confidence
  const confidence = Math.min(0.95, 0.55 + Math.min(words.length / 50, 0.4));

  const rationale = `Normalised based on description word count (${words.length} words). ${
    isAuth ? 'Auth-related criteria added. ' : ''
  }${isApi ? 'API contract criteria added. ' : ''}Standard observability criterion always included.`;

  return { normalizedTitle, acceptanceCriteria, storyTrace, confidence, rationale, source: 'RULE' as const };
}

// ── Enrichment: LLM path ───────────────────────────────────────────────────────

interface OpenAIChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

interface OpenAIChatResponse {
  choices: Array<{
    message: { role: string; content: string };
    finish_reason: string;
  }>;
}

/** Parsed shape we expect the LLM to return as JSON. */
interface LlmEnrichmentJson {
  normalizedTitle: string;
  acceptanceCriteria: string[];
  storyTrace: string;
  confidence: number;
  rationale: string;
}

const ENRICHMENT_SYSTEM_PROMPT = `You are a requirements-engineering assistant.
Given a requirement title and description, return a JSON object with exactly these fields:
- normalizedTitle: string — sentence-cased, concise restatement of the requirement.
- acceptanceCriteria: string[] — 3–5 testable Given/When/Then or bullet criteria.
- storyTrace: string — user story in "As a … I want … so that …" format.
- confidence: number — your confidence in the enrichment (0.0–1.0).
- rationale: string — brief explanation of how you interpreted the requirement.

Respond ONLY with the JSON object. No markdown, no prose.`;

/**
 * Calls the OpenAI chat completions API to enrich a requirement.
 * Throws if the API key is absent, the request fails, or the response is invalid.
 */
async function callLlmForEnrichment(
  title: string,
  description: string
): Promise<AiEnrichmentSuggestion> {
  const apiKey = process.env['OPENAI_API_KEY'];
  if (!apiKey) {
    throw new Error('OPENAI_API_KEY not configured — LLM path unavailable');
  }

  const model = process.env['ENRICHMENT_LLM_MODEL'] ?? 'gpt-4o-mini';

  const messages: OpenAIChatMessage[] = [
    { role: 'system', content: ENRICHMENT_SYSTEM_PROMPT },
    {
      role: 'user',
      content: `Title: ${title}\nDescription: ${description || '(no description provided)'}`,
    },
  ];

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10_000);

  let response: Response;
  try {
    response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({ model, messages, temperature: 0.2, max_tokens: 512 }),
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timer);
  }

  if (!response.ok) {
    throw new Error(`OpenAI API error ${response.status}: ${response.statusText}`);
  }

  const data = (await response.json()) as OpenAIChatResponse;
  const raw = data.choices[0]?.message?.content;
  if (typeof raw !== 'string' || !raw.trim()) {
    throw new Error('LLM returned empty content');
  }

  let parsed: LlmEnrichmentJson;
  try {
    parsed = JSON.parse(raw) as LlmEnrichmentJson;
  } catch {
    throw new Error(`LLM response was not valid JSON: ${raw.slice(0, 200)}`);
  }

  // Validate required fields — don't trust LLM output without boundary checks
  if (
    typeof parsed.normalizedTitle !== 'string' ||
    !Array.isArray(parsed.acceptanceCriteria) ||
    typeof parsed.storyTrace !== 'string' ||
    typeof parsed.confidence !== 'number' ||
    typeof parsed.rationale !== 'string'
  ) {
    throw new Error('LLM response missing required enrichment fields');
  }

  return {
    normalizedTitle: parsed.normalizedTitle,
    acceptanceCriteria: parsed.acceptanceCriteria.map(String),
    storyTrace: parsed.storyTrace,
    confidence: Math.min(1, Math.max(0, parsed.confidence)),
    rationale: parsed.rationale,
    source: 'MODEL' as const,
  };
}

// ── Enrichment: hybrid entry point ─────────────────────────────────────────────

/**
 * Hybrid enrichment: attempts the LLM path first, falls back to deterministic
 * rule engine on any failure (missing API key, timeout, network error, bad JSON).
 *
 * The returned suggestion carries `source: 'MODEL'` or `source: 'RULE'` so the
 * UI can display the correct {@link AISourceChip} provenance label.
 */
async function buildEnrichmentSuggestion(
  title: string,
  description: string
): Promise<AiEnrichmentSuggestion> {
  try {
    return await callLlmForEnrichment(title, description);
  } catch (err: unknown) {
    const reason = err instanceof Error ? err.message : String(err);
    console.warn(`[enrichRequirement] LLM path failed (${reason}), using rule fallback`);
    return buildRuleBasedSuggestion(title, description);
  }
}

export const requirementsApprovalsResolvers = {
  Query: {
    requirements: async (
      _parent: unknown,
      args: { projectId: string; status?: RequirementStatus },
      context: ResolverContext
    ) => {
      requireUserId(context);

      return prisma.requirement.findMany({
        where: {
          projectId: args.projectId,
          ...(args.status ? { status: args.status } : {}),
        },
        include: {
          versions: { orderBy: { version: 'desc' } },
          approvalRequests: { orderBy: { createdAt: 'desc' } },
          agentRuns: { orderBy: { createdAt: 'desc' } },
        },
        orderBy: [{ updatedAt: 'desc' }, { createdAt: 'desc' }],
      });
    },

    requirement: async (
      _parent: unknown,
      args: { id: string },
      context: ResolverContext
    ) => {
      requireUserId(context);

      return prisma.requirement.findUnique({
        where: { id: args.id },
        include: {
          versions: { orderBy: { version: 'desc' } },
          approvalRequests: { orderBy: { createdAt: 'desc' } },
          agentRuns: { orderBy: { createdAt: 'desc' } },
        },
      });
    },

    approvalRequests: async (
      _parent: unknown,
      args: { projectId: string; status?: ApprovalStatus },
      context: ResolverContext
    ) => {
      requireUserId(context);

      return prisma.approvalRequest.findMany({
        where: {
          projectId: args.projectId,
          ...(args.status ? { status: args.status } : {}),
        },
        orderBy: [{ createdAt: 'desc' }],
      });
    },

    agentRuns: async (
      _parent: unknown,
      args: {
        projectId: string;
        requirementId?: string;
        status?: AgentRunStatus;
      },
      context: ResolverContext
    ) => {
      requireUserId(context);

      return prisma.agentRun.findMany({
        where: {
          projectId: args.projectId,
          ...(args.requirementId ? { requirementId: args.requirementId } : {}),
          ...(args.status ? { status: args.status } : {}),
        },
        orderBy: [{ createdAt: 'desc' }],
      });
    },
  },

  Mutation: {
    submitRequirement: async (
      _parent: unknown,
      args: SubmitRequirementArgs,
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);
      const requiresApproval = args.requiresApproval ?? true;
      const initialStatus: RequirementStatus = requiresApproval
        ? 'PENDING_APPROVAL'
        : 'APPROVED';

      const requirement = await prisma.$transaction(async (tx) => {
        const createdRequirement = await tx.requirement.create({
          data: {
            projectId: args.projectId,
            createdById: userId,
            title: args.title,
            description: args.description,
            type: args.type ?? 'FUNCTIONAL',
            priority: args.priority ?? 'MEDIUM',
            status: initialStatus,
            acceptanceCriteria: [],
            currentVersion: 1,
          },
        });

        await tx.requirementVersion.create({
          data: {
            requirementId: createdRequirement.id,
            version: 1,
            content: {
              title: args.title,
              description: args.description,
              type: args.type ?? 'FUNCTIONAL',
              priority: args.priority ?? 'MEDIUM',
            },
            acceptanceCriteria: [],
            changedById: userId,
            changeSummary: 'Initial requirement submission',
          },
        });

        if (requiresApproval) {
          await tx.approvalRequest.create({
            data: {
              projectId: args.projectId,
              requirementId: createdRequirement.id,
              requesterId: userId,
              requestedAction: 'REQUIREMENT_APPROVAL',
              status: 'PENDING',
            },
          });
        }

        return createdRequirement.id;
      });

      return prisma.requirement.findUniqueOrThrow({
        where: { id: requirement },
        include: {
          versions: { orderBy: { version: 'desc' } },
          approvalRequests: { orderBy: { createdAt: 'desc' } },
          agentRuns: { orderBy: { createdAt: 'desc' } },
        },
      });
    },

    approveRequirement: async (
      _parent: unknown,
      args: { approvalRequestId: string; reason?: string },
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      return prisma.$transaction(async (tx) => {
        const request = await tx.approvalRequest.findUniqueOrThrow({
          where: { id: args.approvalRequestId },
        });

        if (request.status !== 'PENDING' && request.status !== 'CHANGES_REQUESTED') {
          throw new Error(`Approval request ${request.id} is already finalized`);
        }

        const updated = await tx.approvalRequest.update({
          where: { id: request.id },
          data: {
            status: 'APPROVED',
            reviewerId: userId,
            reviewedAt: new Date(),
            decisionReason: args.reason,
          },
        });

        if (request.requirementId) {
          await tx.requirement.update({
            where: { id: request.requirementId },
            data: { status: 'APPROVED' },
          });
        }

        return updated;
      });
    },

    rejectRequirement: async (
      _parent: unknown,
      args: { approvalRequestId: string; reason: string },
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      return prisma.$transaction(async (tx) => {
        const request = await tx.approvalRequest.findUniqueOrThrow({
          where: { id: args.approvalRequestId },
        });

        if (request.status !== 'PENDING' && request.status !== 'CHANGES_REQUESTED') {
          throw new Error(`Approval request ${request.id} is already finalized`);
        }

        const updated = await tx.approvalRequest.update({
          where: { id: request.id },
          data: {
            status: 'REJECTED',
            reviewerId: userId,
            reviewedAt: new Date(),
            decisionReason: args.reason,
          },
        });

        if (request.requirementId) {
          await tx.requirement.update({
            where: { id: request.requirementId },
            data: { status: 'REJECTED' },
          });
        }

        return updated;
      });
    },

    requestRequirementChanges: async (
      _parent: unknown,
      args: { approvalRequestId: string; reason: string },
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      return prisma.$transaction(async (tx) => {
        const request = await tx.approvalRequest.findUniqueOrThrow({
          where: { id: args.approvalRequestId },
        });

        if (request.status !== 'PENDING') {
          throw new Error(`Approval request ${request.id} cannot transition to CHANGES_REQUESTED`);
        }

        const updated = await tx.approvalRequest.update({
          where: { id: request.id },
          data: {
            status: 'CHANGES_REQUESTED',
            reviewerId: userId,
            reviewedAt: new Date(),
            decisionReason: args.reason,
          },
        });

        if (request.requirementId) {
          await tx.requirement.update({
            where: { id: request.requirementId },
            data: { status: 'CHANGES_REQUESTED' },
          });
        }

        return updated;
      });
    },

    startAgentRun: async (
      _parent: unknown,
      args: {
        projectId: string;
        requirementId?: string;
        agentName: string;
        input?: unknown;
      },
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      return prisma.agentRun.create({
        data: {
          projectId: args.projectId,
          requirementId: args.requirementId,
          triggeredById: userId,
          agentName: args.agentName,
          status: 'QUEUED',
          stage: 'QUEUED',
          input: args.input,
        },
      });
    },

    updateAgentRun: async (
      _parent: unknown,
      args: UpdateAgentRunArgs,
      context: ResolverContext
    ) => {
      requireUserId(context);

      const now = new Date();

      return prisma.agentRun.update({
        where: { id: args.runId },
        data: {
          status: args.status,
          stage: args.stage ?? args.status,
          output: args.output,
          errorMessage: args.errorMessage,
          startedAt: args.status === 'RUNNING' ? now : undefined,
          completedAt: isTerminalRunStatus(args.status) ? now : undefined,
        },
      });
    },

    /**
     * enrichRequirement
     *
     * Triggers AI enrichment for a submitted requirement:
     * - Creates a tracked AgentRun so users can observe progress
     * - Produces a normalised title, acceptance criteria, story trace,
     *   confidence score and rationale via a deterministic stub that
     *   pattern-matches the requirement description
     * - Stores the suggestions as a new RequirementVersion
     * - Transitions the requirement to PENDING_APPROVAL
     *
     * Production: replace the local suggestion builder with a call to
     * the canvas-ai-service LLM adapter once real providers are wired.
     */
    enrichRequirement: async (
      _parent: unknown,
      args: EnrichRequirementArgs,
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      const requirement = await prisma.requirement.findUniqueOrThrow({
        where: { id: args.requirementId },
        include: { versions: { orderBy: { version: 'desc' }, take: 1 } },
      });

      // Create AgentRun record so the run viewer can track this enrichment
      const agentRun = await prisma.agentRun.create({
        data: {
          projectId: requirement.projectId,
          requirementId: requirement.id,
          triggeredById: userId,
          agentName: 'RequirementEnrichmentAgent',
          status: 'RUNNING',
          stage: 'NORMALISING',
        },
      });

      // Build enrichment suggestion: LLM path with rule-based fallback (AI-Y2).
      const desc: string =
        typeof requirement.description === 'string'
          ? requirement.description
          : '';
      const suggestion: AiEnrichmentSuggestion = await buildEnrichmentSuggestion(
        requirement.title,
        desc
      );

      const latestVersion = requirement.versions[0];
      const nextVersion = (latestVersion?.version ?? 0) + 1;

      await prisma.$transaction(async (tx) => {
        // Persist the AI suggestion as a new requirement version
        await tx.requirementVersion.create({
          data: {
            requirementId: requirement.id,
            version: nextVersion,
            title: suggestion.normalizedTitle,
            description: desc,
            metadata: {
              acceptanceCriteria: suggestion.acceptanceCriteria,
              storyTrace: suggestion.storyTrace,
              confidence: suggestion.confidence,
              rationale: suggestion.rationale,
              source: suggestion.source,
              enrichedBy: 'RequirementEnrichmentAgent',
            },
            changedById: userId,
            changeSummary: `AI enrichment v${nextVersion} (confidence ${Math.round(suggestion.confidence * 100)}%)`,
          },
        });

        // Transition the requirement to PENDING_APPROVAL
        await tx.requirement.update({
          where: { id: requirement.id },
          data: { status: 'PENDING_APPROVAL' },
        });

        // Mark the AgentRun as succeeded with the enrichment output
        await tx.agentRun.update({
          where: { id: agentRun.id },
          data: {
            status: 'SUCCEEDED',
            stage: 'COMPLETED',
            completedAt: new Date(),
            output: suggestion,
          },
        });
      });

      return prisma.requirement.findUniqueOrThrow({
        where: { id: requirement.id },
        include: {
          versions: { orderBy: { version: 'desc' } },
          approvalRequests: { orderBy: { createdAt: 'desc' } },
          agentRuns: { orderBy: { createdAt: 'desc' } },
        },
      });
    },

    /**
     * bulkApproveRequirements
     *
     * Atomically approves multiple approval requests.
     * Partial failures are collected and returned alongside successes.
     */
    bulkApproveRequirements: async (
      _parent: unknown,
      args: { approvalRequestIds: string[]; reason?: string },
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      const results: Array<{ id: string; success: boolean; error?: string }> = [];

      for (const approvalRequestId of args.approvalRequestIds) {
        try {
          await prisma.$transaction(async (tx) => {
            const request = await tx.approvalRequest.findUniqueOrThrow({
              where: { id: approvalRequestId },
            });

            if (
              request.status !== 'PENDING' &&
              request.status !== 'CHANGES_REQUESTED'
            ) {
              throw new Error(
                `Approval request ${approvalRequestId} is already finalized`
              );
            }

            await tx.approvalRequest.update({
              where: { id: request.id },
              data: {
                status: 'APPROVED',
                reviewerId: userId,
                reviewedAt: new Date(),
                decisionReason: args.reason,
              },
            });

            if (request.requirementId) {
              await tx.requirement.update({
                where: { id: request.requirementId },
                data: { status: 'APPROVED' },
              });
            }
          });
          results.push({ id: approvalRequestId, success: true });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : String(err);
          results.push({ id: approvalRequestId, success: false, error: message });
        }
      }

      return results;
    },

    /**
     * bulkRejectRequirements
     *
     * Atomically rejects multiple approval requests.
     */
    bulkRejectRequirements: async (
      _parent: unknown,
      args: { approvalRequestIds: string[]; reason: string },
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      const results: Array<{ id: string; success: boolean; error?: string }> = [];

      for (const approvalRequestId of args.approvalRequestIds) {
        try {
          await prisma.$transaction(async (tx) => {
            const request = await tx.approvalRequest.findUniqueOrThrow({
              where: { id: approvalRequestId },
            });

            if (
              request.status !== 'PENDING' &&
              request.status !== 'CHANGES_REQUESTED'
            ) {
              throw new Error(
                `Approval request ${approvalRequestId} is already finalized`
              );
            }

            await tx.approvalRequest.update({
              where: { id: request.id },
              data: {
                status: 'REJECTED',
                reviewerId: userId,
                reviewedAt: new Date(),
                decisionReason: args.reason,
              },
            });

            if (request.requirementId) {
              await tx.requirement.update({
                where: { id: request.requirementId },
                data: { status: 'REJECTED' },
              });
            }
          });
          results.push({ id: approvalRequestId, success: true });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : String(err);
          results.push({ id: approvalRequestId, success: false, error: message });
        }
      }

      return results;
    },
  },

  Requirement: {
    createdAt: (parent: { createdAt: Date }) => toISOString(parent.createdAt),
    updatedAt: (parent: { updatedAt: Date }) => toISOString(parent.updatedAt),
  },

  RequirementVersion: {
    createdAt: (parent: { createdAt: Date }) => toISOString(parent.createdAt),
  },

  ApprovalRequest: {
    createdAt: (parent: { createdAt: Date }) => toISOString(parent.createdAt),
    reviewedAt: (parent: { reviewedAt: Date | null }) =>
      toISOString(parent.reviewedAt),
    updatedAt: (parent: { updatedAt: Date }) => toISOString(parent.updatedAt),
  },

  AgentRun: {
    createdAt: (parent: { createdAt: Date }) => toISOString(parent.createdAt),
    updatedAt: (parent: { updatedAt: Date }) => toISOString(parent.updatedAt),
    startedAt: (parent: { startedAt: Date | null }) => toISOString(parent.startedAt),
    completedAt: (parent: { completedAt: Date | null }) =>
      toISOString(parent.completedAt),
  },
};
