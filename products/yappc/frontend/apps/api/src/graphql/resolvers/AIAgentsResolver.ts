/**
 * AI Agents GraphQL Resolver
 *
 * GraphQL resolver that delegates AI agent requests to the Java backend.
 * This follows the Hybrid Backend Strategy:
 * - Node.js/GraphQL: User API layer (authorization, validation, routing)
 * - Java/ActiveJ: Core Domain (AI processing, ML inference)
 *
 * @module graphql/resolvers/AIAgentsResolver
 * @doc.type class
 * @doc.purpose AI agent GraphQL resolver
 * @doc.layer product
 * @doc.pattern Resolver
 */

import type { CopilotInput, QueryParserInput, PredictionInput, AnomalyInput } from '../../stubs/ai/agents/api-client';
import {
  createCopilotClient,
  createQueryParserClient,
  createPredictionClient,
  createAnomalyDetectorClient,
  fetchAgentRegistry,
  type JavaAIClientConfig,
} from '../../services/ai/java-ai-agent-client';

// Initialize Prisma client
import { getPrismaClient, type PrismaClient } from '../../database/client';

interface GraphQLContext {
  userId?: string;
  workspaceId?: string;
  permissions?: string[];
}

const prisma: PrismaClient = new Proxy({} as PrismaClient, {
  get(_target, property) {
    return (getPrismaClient() as Record<string | symbol, unknown>)[property];
  },
});

// Java AI backend configuration
const javaAIClientConfig: JavaAIClientConfig = {
  baseUrl: process.env.JAVA_AI_BACKEND_URL ?? 'http://localhost:7003',
  timeoutMs: 30_000,
  apiKey: process.env.JAVA_AI_API_KEY,
};

// Create production HTTP clients backed by the Java AI backend
const copilotClient = createCopilotClient(javaAIClientConfig);
const queryParserClient = createQueryParserClient(javaAIClientConfig);
const predictionClient = createPredictionClient(javaAIClientConfig);
const anomalyClient = createAnomalyDetectorClient(javaAIClientConfig);

/**
 * Agent context from GraphQL input
 */
interface AgentContextInput {
  userId: string;
  workspaceId: string;
  requestId?: string;
  timeout?: number;
  metadata?: Record<string, unknown>;
}

/**
 * Convert GraphQL context to agent context
 */
function toAgentContext(input: AgentContextInput) {
  return {
    userId: input.userId,
    workspaceId: input.workspaceId,
    requestId: input.requestId || crypto.randomUUID(),
    permissions: [],
    timeout: input.timeout,
    metadata: input.metadata,
  };
}

/**
 * Log agent execution to database
 */
async function logAgentExecution(
  agentName: string,
  requestId: string,
  userId: string,
  workspaceId: string | undefined,
  input: unknown,
  output: unknown,
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT',
  latencyMs: number,
  tokensUsed?: number,
  errorMessage?: string
) {
  try {
    if (!process.env.DATABASE_URL) return;
    await prisma.agentExecution.create({
      data: {
        agentName,
        requestId,
        userId,
        workspaceId,
        input: input as object,
        output: output as object,
        status,
        latencyMs,
        tokensUsed,
        errorMessage,
        completedAt: new Date(),
      },
    });
  } catch (err) {
    // Logging failure must not surface to callers — emit to stderr only
    process.stderr.write(
      `[AIAgentsResolver] Failed to log agent execution: ${err instanceof Error ? err.message : String(err)}\n`
    );
  }
}

/**
 * AI Agents Resolver
 */
export const AIAgentsResolver = {
  Query: {
    /**
     * Get all registered AI agents from the AEP Java registry.
     * Throws if the registry is unreachable so callers receive a meaningful error
     * rather than stale hardcoded data.
     */
    async aiAgents() {
      return fetchAgentRegistry(javaAIClientConfig);
    },

    /**
     * Get a specific agent by name
     */
    async aiAgent(_: unknown, { name }: { name: string }) {
      const agents = await AIAgentsResolver.Query.aiAgents();
      return agents.find((a) => a.name === name);
    },

    /**
     * Get agent health status
     */
    async aiAgentHealth(_: unknown, { name }: { name: string }) {
      try {
        // Get appropriate client based on agent name
        let health;
        switch (name) {
          case 'COPILOT_AGENT':
            health = await copilotClient.healthCheck();
            break;
          case 'QUERY_PARSER_AGENT':
            health = await queryParserClient.healthCheck();
            break;
          case 'PREDICTION_AGENT':
            health = await predictionClient.healthCheck();
            break;
          case 'ANOMALY_DETECTOR_AGENT':
            health = await anomalyClient.healthCheck();
            break;
          default:
            throw new Error(`Unknown agent: ${name}`);
        }
        return health;
      } catch (error) {
        return {
          healthy: false,
          latency: 0,
          lastCheck: new Date(),
          dependencies: {},
          errorMessage:
            error instanceof Error ? error.message : 'Health check failed',
        };
      }
    },

    /**
     * Get all agent health statuses
     */
    async aiAgentsHealthCheck() {
      const agents = [
        'COPILOT_AGENT',
        'QUERY_PARSER_AGENT',
        'PREDICTION_AGENT',
        'ANOMALY_DETECTOR_AGENT',
      ];
      const results = await Promise.all(
        agents.map((name) =>
          AIAgentsResolver.Query.aiAgentHealth(null, { name })
        )
      );
      return results;
    },

    /**
     * Get user AI preferences
     */
    async userAIPreferences(_: unknown, { userId }: { userId: string }) {
      return prisma.userAIPreferences.findUnique({
        where: { userId },
      });
    },

    /**
     * Get copilot sessions for a user
     */
    async copilotSessions(
      _: unknown,
      {
        userId,
        status,
        limit = 20,
        offset = 0,
      }: { userId: string; status?: string; limit?: number; offset?: number }
    ) {
      return prisma.copilotSession.findMany({
        where: {
          userId,
          ...(status ? { status: status as unknown } : {}),
        },
        take: limit,
        skip: offset,
        orderBy: { createdAt: 'desc' },
      });
    },

    /**
     * Get a specific copilot session
     */
    async copilotSession(
      _: unknown,
      { id }: { id: string },
      context: GraphQLContext
    ) {
      if (!context.userId) {
        throw new Error('Unauthorized');
      }

      const session = await prisma.copilotSession.findUnique({
        where: { id },
      });

      // Verify session belongs to user
      if (session && session.userId !== context.userId) {
        // Emit structured security event via stderr to preserve correlation context
        process.stderr.write(
          JSON.stringify({
            level: 'warn',
            event: 'security.unauthorized_session_access',
            requestingUserId: context.userId,
            sessionId: id,
            sessionOwner: session.userId,
          }) + '\n'
        );
        throw new Error('Forbidden: Session does not belong to you');
      }

      return session;
    },

    /**
     * Get agent execution history
     */
    async agentExecutions(
      _: unknown,
      {
        agentName,
        userId,
        status,
        limit = 50,
        offset = 0,
      }: {
        agentName?: string;
        userId?: string;
        status?: string;
        limit?: number;
        offset?: number;
      }
    ) {
      return prisma.agentExecution.findMany({
        where: {
          ...(agentName ? { agentName } : {}),
          ...(userId ? { userId } : {}),
          ...(status ? { status: status as unknown } : {}),
        },
        take: limit,
        skip: offset,
        orderBy: { startedAt: 'desc' },
      });
    },
  },

  Mutation: {
    /**
     * Execute the Copilot agent
     */
    async executeCopilot(
      _: unknown,
      { input, context }: { input: CopilotInput; context: AgentContextInput }
    ) {
      const agentContext = toAgentContext(context);
      const startTime = Date.now();

      try {
        const result = await copilotClient.execute(input, agentContext);
        const latencyMs = Date.now() - startTime;

        // Log execution
        await logAgentExecution(
          'COPILOT_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          result.data,
          result.success ? 'SUCCESS' : 'FAILED',
          latencyMs,
          result.metrics.tokensUsed,
          result.error?.message
        );

        return result;
      } catch (error) {
        const latencyMs = Date.now() - startTime;
        const errorMessage =
          error instanceof Error ? error.message : 'Unknown error';

        await logAgentExecution(
          'COPILOT_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          null,
          'FAILED',
          latencyMs,
          undefined,
          errorMessage
        );

        throw error;
      }
    },

    /**
     * Execute the Query Parser agent
     */
    async executeQueryParser(
      _: unknown,
      {
        input,
        context,
      }: { input: QueryParserInput; context: AgentContextInput }
    ) {
      const agentContext = toAgentContext(context);
      const startTime = Date.now();

      try {
        const result = await queryParserClient.execute(input, agentContext);
        const latencyMs = Date.now() - startTime;

        await logAgentExecution(
          'QUERY_PARSER_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          result.data,
          result.success ? 'SUCCESS' : 'FAILED',
          latencyMs,
          result.metrics.tokensUsed,
          result.error?.message
        );

        return result;
      } catch (error) {
        const latencyMs = Date.now() - startTime;
        const errorMessage =
          error instanceof Error ? error.message : 'Unknown error';

        await logAgentExecution(
          'QUERY_PARSER_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          null,
          'FAILED',
          latencyMs,
          undefined,
          errorMessage
        );

        throw error;
      }
    },

    /**
     * Execute the Prediction agent
     */
    async executePrediction(
      _: unknown,
      { input, context }: { input: PredictionInput; context: AgentContextInput }
    ) {
      const agentContext = toAgentContext(context);
      const startTime = Date.now();

      try {
        const result = await predictionClient.execute(input, agentContext);
        const latencyMs = Date.now() - startTime;

        await logAgentExecution(
          'PREDICTION_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          result.data,
          result.success ? 'SUCCESS' : 'FAILED',
          latencyMs,
          result.metrics.tokensUsed,
          result.error?.message
        );

        return result;
      } catch (error) {
        const latencyMs = Date.now() - startTime;
        const errorMessage =
          error instanceof Error ? error.message : 'Unknown error';

        await logAgentExecution(
          'PREDICTION_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          null,
          'FAILED',
          latencyMs,
          undefined,
          errorMessage
        );

        throw error;
      }
    },

    /**
     * Execute the Anomaly Detector agent
     */
    async executeAnomalyDetector(
      _: unknown,
      { input, context }: { input: AnomalyInput; context: AgentContextInput }
    ) {
      const agentContext = toAgentContext(context);
      const startTime = Date.now();

      try {
        const result = await anomalyClient.execute(input, agentContext);
        const latencyMs = Date.now() - startTime;

        await logAgentExecution(
          'ANOMALY_DETECTOR_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          result.data,
          result.success ? 'SUCCESS' : 'FAILED',
          latencyMs,
          result.metrics.tokensUsed,
          result.error?.message
        );

        return result;
      } catch (error) {
        const latencyMs = Date.now() - startTime;
        const errorMessage =
          error instanceof Error ? error.message : 'Unknown error';

        await logAgentExecution(
          'ANOMALY_DETECTOR_AGENT',
          agentContext.requestId,
          context.userId,
          context.workspaceId,
          input,
          null,
          'FAILED',
          latencyMs,
          undefined,
          errorMessage
        );

        throw error;
      }
    },

    /**
     * Update user AI preferences
     */
    async updateUserAIPreferences(
      _: unknown,
      {
        userId,
        input,
      }: { userId: string; input: Partial<Record<string, unknown>> }
    ) {
      return prisma.userAIPreferences.upsert({
        where: { userId },
        create: {
          userId,
          ...input,
        },
        update: input,
      });
    },

    /**
     * Create a new copilot session
     */
    async createCopilotSession(
      _: unknown,
      { userId, modelUsed }: { userId: string; modelUsed: string }
    ) {
      return prisma.copilotSession.create({
        data: {
          userId,
          modelUsed,
          status: 'ACTIVE',
        },
      });
    },

    /**
     * End a copilot session
     */
    async endCopilotSession(
      _: unknown,
      { id }: { id: string },
      context: GraphQLContext
    ) {
      if (!context.userId) {
        throw new Error('Unauthorized');
      }

      const session = await prisma.copilotSession.findUnique({
        where: { id },
      });

      // Verify session belongs to user
      if (session && session.userId !== context.userId) {
        process.stderr.write(
          JSON.stringify({
            level: 'warn',
            event: 'security.unauthorized_session_end_attempt',
            requestingUserId: context.userId,
            sessionId: id,
            sessionOwner: session.userId,
          }) + '\n'
        );
        throw new Error('Forbidden: Session does not belong to you');
      }

      return prisma.copilotSession.update({
        where: { id },
        data: {
          status: 'COMPLETED',
          endedAt: new Date(),
        },
      });
    },

    /**
     * Provide feedback on a copilot session
     */
    async feedbackCopilotSession(
      _: unknown,
      { id, feedback }: { id: string; feedback: string },
      context: GraphQLContext
    ) {
      if (!context.userId) {
        throw new Error('Unauthorized');
      }

      const session = await prisma.copilotSession.findUnique({
        where: { id },
      });

      // Verify session belongs to user
      if (session && session.userId !== context.userId) {
        process.stderr.write(
          JSON.stringify({
            level: 'warn',
            event: 'security.unauthorized_session_feedback_attempt',
            requestingUserId: context.userId,
            sessionId: id,
            sessionOwner: session.userId,
          }) + '\n'
        );
        throw new Error('Forbidden: Session does not belong to you');
      }

      return prisma.copilotSession.update({
        where: { id },
        data: {
          feedback: feedback as 'HELPFUL' | 'NOT_HELPFUL' | 'NEUTRAL',
        },
      });
    },
  },

  Subscription: {
    /**
     * Subscribe to copilot streaming responses
     * Note: Real implementation would use WebSocket/SSE
     */
    copilotStream: {
      subscribe: async function* (
        _: unknown,
        { sessionId }: { sessionId: string }
      ) {
        // Placeholder - real implementation would stream from Java backend
        yield `Session ${sessionId} connected`;
      },
    },

    /**
     * Subscribe to anomaly alerts
     * Note: Real implementation would use WebSocket/SSE
     */
    anomalyAlerts: {
      subscribe: async function* (
        _: unknown,
        { workspaceId }: { workspaceId: string }
      ) {
        // Placeholder - real implementation would stream from Java backend
        yield {
          success: true,
          data: null,
          metrics: { latencyMs: 0 },
          trace: {
            agentName: 'ANOMALY_DETECTOR_AGENT',
            requestId: 'subscription',
            startTime: Date.now(),
            endTime: Date.now(),
            records: [],
          },
        };
      },
    },

    /**
     * Subscribe to prediction updates
     * Note: Real implementation would use WebSocket/SSE
     */
    predictionUpdates: {
      subscribe: async function* (_: unknown, { itemId }: { itemId: string }) {
        // Placeholder - real implementation would stream from Java backend
        yield {
          success: true,
          data: null,
          metrics: { latencyMs: 0 },
          trace: {
            agentName: 'PREDICTION_AGENT',
            requestId: 'subscription',
            startTime: Date.now(),
            endTime: Date.now(),
            records: [],
          },
        };
      },
    },
  },
};

export default AIAgentsResolver;
