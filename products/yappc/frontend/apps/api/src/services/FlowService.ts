import { getPrismaClient, Prisma, type PrismaClient } from '../database/client';
import { GoldenFlows } from '../config/flows';
import { getArray, isRecord } from '../utils/type-guards';

const prisma: PrismaClient = new Proxy({} as PrismaClient, {
  get(_target, property, receiver) {
    return Reflect.get(getPrismaClient() as unknown as object, property, receiver);
  },
}) as PrismaClient;

// State hooks that trigger AI processing
type StateHook = (
  flowId: string,
  instanceId: string,
  context: unknown
) => Promise<void>;
const stateHooks: Map<string, StateHook[]> = new Map();

async function parseJsonResponse<T>(
  response: Response,
  context: string
): Promise<T> {
  const raw = await response.text();

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`);
  }
}

async function readErrorResponse(
  response: Response,
  context: string
): Promise<string> {
  const raw = await response.text();

  if (!raw) {
    return `${context} failed with HTTP ${response.status}`;
  }

  try {
    const payload = JSON.parse(raw) as { message?: unknown; error?: unknown };
    if (typeof payload.message === 'string' && payload.message.length > 0) {
      return payload.message;
    }
    if (typeof payload.error === 'string' && payload.error.length > 0) {
      return payload.error;
    }
  } catch {
    if (raw.trim().length > 0) {
      return raw.trim();
    }
  }

  return `${context} failed with HTTP ${response.status}`;
}

export interface FlowInstance {
  instanceId: string;
  flowId: string;
  currentState: string;
  context: unknown;
  history: FlowHistoryItem[];
  artifacts: unknown[];
}

type FlowStateRecord = {
  id: string;
  flowId: string;
  currentState: string;
  context: unknown;
  history: unknown;
  artifacts: unknown;
};

function toJsonValue(value: unknown): Prisma.InputJsonValue {
  return value as Prisma.InputJsonValue;
}

export interface FlowHistoryItem {
  state: string;
  event: string;
  timestamp: string;
  actorId?: string;
}

/**
 * Register a hook to be called when entering a specific state
 */
export function onEnterState(state: string, hook: StateHook): void {
  const hooks = stateHooks.get(state) || [];
  hooks.push(hook);
  stateHooks.set(state, hooks);
}

/**
 * Trigger AI generation for "Suggesting" states
 * This is registered as a hook and will call Java backend AI endpoints
 */
async function triggerAISuggestion(
  flowId: string,
  instanceId: string,
  context: unknown
): Promise<void> {
  console.log(
    `[FlowService] Triggering AI suggestion for ${flowId}:${instanceId}`
  );

  const javaBackendUrl =
    process.env.JAVA_BACKEND_URL || 'http://localhost:7003';

  try {
    // Call Java backend AI suggestion endpoint
    const response = await fetch(
      `${javaBackendUrl}/api/ai/suggestions/generate`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          flowId,
          instanceId,
          type:
            flowId === 'product.requirement'
              ? 'REQUIREMENT_REFINEMENT'
              : 'GENERAL',
          context: context,
        }),
      }
    );

    if (response.ok) {
      const result = await parseJsonResponse<Record<string, unknown>>(
        response,
        'AI suggestion endpoint'
      );
      console.log(
        `[FlowService] AI suggestion generated: ${result.suggestionId || 'pending'}`
      );

      // Store the suggestion reference in the flow context
      await prisma.flowState.update({
        where: { id: instanceId },
        data: {
          context: toJsonValue({
            ...(isRecord(context) ? context : {}),
            aiSuggestionId: result.suggestionId,
            aiStatus: 'pending',
          }),
        },
      });
    } else {
      console.warn(
        `[FlowService] AI suggestion request failed: ${await readErrorResponse(response, 'AI suggestion endpoint')}`
      );
    }
  } catch (error) {
    console.error('[FlowService] Failed to trigger AI suggestion:', error);
    // Don't fail the transition - AI is optional enhancement
  }
}

// Register AI hook for "Suggesting" states
onEnterState('Suggesting', triggerAISuggestion);

export class FlowService {
  private static instance: FlowService;

  private constructor() {}

  public static getInstance(): FlowService {
    if (!FlowService.instance) {
      FlowService.instance = new FlowService();
    }
    return FlowService.instance;
  }

  public async startFlow(
    flowId: string,
    input: unknown,
    actorId?: string
  ): Promise<FlowInstance> {
    const definition = GoldenFlows[flowId];
    if (!definition) {
      throw new Error(`Flow definition not found: ${flowId}`);
    }

    const historyItem: FlowHistoryItem = {
      state: 'INITIAL',
      event: 'START',
      timestamp: new Date().toISOString(),
      actorId,
    };

    const flowState = await prisma.flowState.create({
      data: {
        flowId,
        currentState: definition.initialState,
        context: toJsonValue(isRecord(input) ? input : {}),
        history: toJsonValue([historyItem]),
        artifacts: toJsonValue([]),
        activeTasks:
          definition.states[definition.initialState]?.associatedTasks || [],
      },
    });

    return this.mapToInstance(flowState);
  }

  public async transition(
    instanceId: string,
    event: string,
    payload: unknown,
    actorId?: string
  ): Promise<FlowInstance> {
    const flowState = await prisma.flowState.findUnique({
      where: { id: instanceId },
    });
    if (!flowState) {
      throw new Error(`Flow instance not found: ${instanceId}`);
    }

    const definition = GoldenFlows[flowState.flowId];
    const currentStateDef = definition.states[flowState.currentState];

    if (!currentStateDef) {
      throw new Error(`Invalid state configuration: ${flowState.currentState}`);
    }

    const nextState = currentStateDef.on[event];
    if (!nextState) {
      throw new Error(
        `Invalid transition: ${flowState.currentState} -> ${event}`
      );
    }

    const nextStateDef = definition.states[nextState];

    const historyItem: FlowHistoryItem = {
      state: flowState.currentState,
      event,
      timestamp: new Date().toISOString(),
      actorId,
    };

    const history = getArray<FlowHistoryItem>(flowState.history);
    history.push(historyItem);

    const currentContext = isRecord(flowState.context) ? flowState.context : {};
    const payloadRecord = isRecord(payload) ? payload : {};

    const updatedFlowState = await prisma.flowState.update({
      where: { id: instanceId },
      data: {
        currentState: nextState,
        context: toJsonValue({ ...currentContext, ...payloadRecord }),
        history: toJsonValue(history),
        activeTasks: nextStateDef?.associatedTasks || [],
      },
    });

    // Trigger state hooks (e.g., AI generation for Suggesting state)
    const hooks = stateHooks.get(nextState) || [];
    for (const hook of hooks) {
      // Run hooks asynchronously - don't block the transition
      void (async () => {
        try {
          await hook(flowState.flowId, instanceId, updatedFlowState.context);
        } catch (err) {
          console.error(
            `[FlowService] Hook failed for state ${nextState}:`,
            err
          );
        }
      })();
    }

    return this.mapToInstance(updatedFlowState);
  }

  public async getFlow(instanceId: string): Promise<FlowInstance | null> {
    const flowState = await prisma.flowState.findUnique({
      where: { id: instanceId },
    });
    if (!flowState) return null;
    return this.mapToInstance(flowState);
  }

  public async getAvailableTransitions(instanceId: string): Promise<string[]> {
    const flowState = await prisma.flowState.findUnique({
      where: { id: instanceId },
    });
    if (!flowState) return [];

    const definition = GoldenFlows[flowState.flowId];
    const currentStateDef = definition.states[flowState.currentState];
    return currentStateDef ? Object.keys(currentStateDef.on) : [];
  }

  public async getActiveTasks(instanceId: string): Promise<string[]> {
    const flowState = await prisma.flowState.findUnique({
      where: { id: instanceId },
    });
    if (!flowState) return [];
    return (flowState.activeTasks as string[]) || [];
  }

  public async cancel(
    instanceId: string,
    reason: string,
    actorId?: string
  ): Promise<FlowInstance> {
    const flowState = await prisma.flowState.findUnique({
      where: { id: instanceId },
    });
    if (!flowState) {
      throw new Error(`Flow instance not found: ${instanceId}`);
    }

    const historyItem: FlowHistoryItem = {
      state: flowState.currentState,
      event: 'CANCELLED',
      timestamp: new Date().toISOString(),
      actorId,
    };

    const history = getArray<FlowHistoryItem>(flowState.history);
    history.push(historyItem);

    const currentContext = isRecord(flowState.context) ? flowState.context : {};

    const updatedFlowState = await prisma.flowState.update({
      where: { id: instanceId },
      data: {
        currentState: 'Cancelled',
        context: toJsonValue({ ...currentContext, cancelReason: reason }),
        history: toJsonValue(history),
        activeTasks: [],
      },
    });

    return this.mapToInstance(updatedFlowState);
  }

  private mapToInstance(flowState: FlowStateRecord): FlowInstance {
    return {
      instanceId: flowState.id,
      flowId: flowState.flowId,
      currentState: flowState.currentState,
      context: flowState.context,
      history: getArray<FlowHistoryItem>(flowState.history),
      artifacts: getArray<unknown>(flowState.artifacts),
    };
  }
}
