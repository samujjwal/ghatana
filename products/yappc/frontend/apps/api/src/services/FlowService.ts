import { getPrismaClient, type PrismaClient } from '../database/client';
import { GoldenFlows } from '../config/flows';

const prisma: PrismaClient = new Proxy({} as PrismaClient, {
  get(_target, property) {
    return (getPrismaClient() as unknown)[property];
  },
});

// State hooks that trigger AI processing
type StateHook = (
  flowId: string,
  instanceId: string,
  context: unknown
) => Promise<void>;
const stateHooks: Map<string, StateHook[]> = new Map();

export interface FlowInstance {
  instanceId: string;
  flowId: string;
  currentState: string;
  context: unknown;
  history: FlowHistoryItem[];
  artifacts: unknown[];
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
      const result = await response.json();
      console.log(
        `[FlowService] AI suggestion generated: ${result.suggestionId || 'pending'}`
      );

      // Store the suggestion reference in the flow context
      await prisma.flowState.update({
        where: { id: instanceId },
        data: {
          context: {
            ...context,
            aiSuggestionId: result.suggestionId,
            aiStatus: 'pending',
          },
        },
      });
    } else {
      console.warn(
        `[FlowService] AI suggestion request failed: ${response.status}`
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
        context: input || {},
        history: [historyItem] as unknown,
        artifacts: [],
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

    const history = (flowState.history as unknown as FlowHistoryItem[]) || [];
    history.push(historyItem);

    const updatedFlowState = await prisma.flowState.update({
      where: { id: instanceId },
      data: {
        currentState: nextState,
        context: { ...(flowState.context as unknown), ...payload },
        history: history as unknown,
        activeTasks: nextStateDef?.associatedTasks || [],
      },
    });

    // Trigger state hooks (e.g., AI generation for Suggesting state)
    const hooks = stateHooks.get(nextState) || [];
    for (const hook of hooks) {
      // Run hooks asynchronously - don't block the transition
      hook(flowState.flowId, instanceId, updatedFlowState.context).catch(
        (err) => {
          console.error(
            `[FlowService] Hook failed for state ${nextState}:`,
            err
          );
        }
      );
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

    const history = (flowState.history as unknown as FlowHistoryItem[]) || [];
    history.push(historyItem);

    const updatedFlowState = await prisma.flowState.update({
      where: { id: instanceId },
      data: {
        currentState: 'Cancelled',
        context: { ...(flowState.context as unknown), cancelReason: reason },
        history: history as unknown,
        activeTasks: [],
      },
    });

    return this.mapToInstance(updatedFlowState);
  }

  private mapToInstance(flowState: unknown): FlowInstance {
    return {
      instanceId: flowState.id,
      flowId: flowState.flowId,
      currentState: flowState.currentState,
      context: flowState.context,
      history: flowState.history,
      artifacts: flowState.artifacts,
    };
  }
}
