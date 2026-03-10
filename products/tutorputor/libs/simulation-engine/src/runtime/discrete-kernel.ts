/**
 * Discrete Algorithm Kernel
 * 
 * @doc.type class
 * @doc.purpose Execute discrete algorithm simulations (sorting, searching, graphs)
 * @doc.layer product
 * @doc.pattern Kernel
 */

import type {
  DiscreteKernel as IDiscreteKernel,
  SimulationRunRequest,
  SimulationRunResult
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type {
  SimulationManifest,
  SimKeyframe,
  SimEntity,
  SimAction,
  SimEntityId,
  EasingFunction
} from "@ghatana/tutorputor-contracts/v1/simulation";
import { interpolate, interpolatePoint, getEasingFunction } from "./easing";

/**
 * Discrete kernel configuration.
 */
export interface DiscreteKernelConfig {
  defaultDuration?: number;
  defaultEasing?: EasingFunction;
  samplingRate?: number; // keyframes per step
}

/**
 * Create the discrete algorithm kernel.
 * 
 * @doc.type function
 * @doc.purpose Factory function for discrete kernel
 * @doc.layer product
 * @doc.pattern Factory
 */
export function createDiscreteKernel(
  config: DiscreteKernelConfig = {}
): IDiscreteKernel {
  const defaultDuration = config.defaultDuration || 500;
  const defaultEasing = config.defaultEasing || "easeInOut";
  const samplingRate = config.samplingRate || 30;

  // State for stateful execution
  let currentManifest: SimulationManifest | null = null;
  let currentStepIndex = -1;
  let currentEntities = new Map<SimEntityId, SimEntity>();
  let currentAnnotations: SimKeyframe["annotations"] = [];

  return {
    domain: "CS_DISCRETE" as const,

    canExecute(manifest: SimulationManifest): boolean {
      return manifest.domain === "CS_DISCRETE";
    },

    async run(request: SimulationRunRequest): Promise<SimulationRunResult> {
      const startTime = Date.now();
      const { manifest, startStep = 0, endStep } = request;

      if (!this.canExecute(manifest)) {
        return {
          simulationId: manifest.id,
          keyframes: [],
          totalSteps: 0,
          executionTimeMs: Date.now() - startTime,
          errors: ["Manifest domain is not CS_DISCRETE"]
        };
      }

      const keyframes: SimKeyframe[] = [];
      const warnings: string[] = [];

      // Initialize entity state from initial entities
      const entityState = new Map<SimEntityId, SimEntity>();
      for (const entity of manifest.initialEntities) {
        entityState.set(entity.id, { ...entity });
      }

      // Generate initial keyframe
      keyframes.push({
        stepIndex: -1,
        timestamp: 0,
        entities: Array.from(entityState.values()),
        annotations: []
      });

      // Process each step
      const stepsToProcess = manifest.steps
        .filter(s => s.orderIndex >= startStep)
        .filter(s => endStep === undefined || s.orderIndex <= endStep)
        .sort((a, b) => a.orderIndex - b.orderIndex);

      let timestamp = 0;

      for (const step of stepsToProcess) {
        const stepKeyframes = processStep(
          step,
          entityState,
          timestamp,
          defaultDuration,
          defaultEasing,
          samplingRate
        );

        keyframes.push(...stepKeyframes);

        // Update timestamp for next step
        const maxDuration = Math.max(
          ...step.actions.map(a => (a.duration || defaultDuration) + (a.delay || 0))
        );
        timestamp += maxDuration;
      }

      return {
        simulationId: manifest.id,
        keyframes,
        totalSteps: stepsToProcess.length,
        executionTimeMs: Date.now() - startTime,
        warnings: warnings.length > 0 ? warnings : undefined
      };
    },

    interpolateKeyframes(
      keyframes: SimKeyframe[],
      targetFps: number
    ): SimKeyframe[] {
      if (keyframes.length < 2) return keyframes;

      const interpolated: SimKeyframe[] = [];
      const frameInterval = 1000 / targetFps;

      for (let i = 0; i < keyframes.length - 1; i++) {
        const current = keyframes[i];
        const next = keyframes[i + 1];
        const duration = next.timestamp - current.timestamp;
        const frameCount = Math.ceil(duration / frameInterval);

        for (let f = 0; f < frameCount; f++) {
          const progress = f / frameCount;
          const timestamp = current.timestamp + progress * duration;

          const entities = current.entities.map(entity => {
            const nextEntity = next.entities.find(e => e.id === entity.id);
            if (!nextEntity) return entity;

            return interpolateEntity(entity, nextEntity, progress);
          });

          interpolated.push({
            stepIndex: current.stepIndex,
            timestamp,
            entities,
            annotations: current.annotations
          });
        }
      }

      // Add final keyframe
      interpolated.push(keyframes[keyframes.length - 1]);

      return interpolated;
    },

    async checkHealth(): Promise<boolean> {
      return true;
    },

    serialize(): string {
      return JSON.stringify({
        stepIndex: currentStepIndex,
        entities: Array.from(currentEntities.entries()),
        annotations: currentAnnotations
      });
    },

    deserialize(state: string): void {
      try {
        const data = JSON.parse(state);
        currentStepIndex = data.stepIndex;
        currentEntities = new Map(data.entities);
        currentAnnotations = data.annotations || [];
      } catch (e) {
        // console.error("Failed to deserialize discrete kernel state", e);
      }
    },

    initialize(manifest: SimulationManifest): void {
      currentManifest = manifest;
      currentStepIndex = -1;
      currentEntities.clear();
      for (const entity of manifest.initialEntities) {
        currentEntities.set(entity.id, { ...entity });
      }
      currentAnnotations = [];
    },

    step(): void {
      if (!currentManifest) return;
      if (currentStepIndex >= currentManifest.steps.length - 1) return;

      currentStepIndex++;
      const step = currentManifest.steps[currentStepIndex];

      for (const action of step.actions) {
        processAction(action, currentEntities, currentAnnotations);
      }
    },

    interpolate(t: number): Partial<SimKeyframe> {
      return {
        entities: Array.from(currentEntities.values()),
        annotations: [...currentAnnotations]
      };
    },

    reset(): void {
      if (currentManifest) {
        this.initialize(currentManifest);
      }
    },

    getAnalytics(): Record<string, unknown> {
      return {};
    },


  };
}

/**
 * Process a single step and generate keyframes.
 */
function processStep(
  step: { orderIndex: number; actions: SimAction[] },
  entityState: Map<SimEntityId, SimEntity>,
  startTimestamp: number,
  defaultDuration: number,
  defaultEasing: EasingFunction,
  samplingRate: number
): SimKeyframe[] {
  const keyframes: SimKeyframe[] = [];
  const annotations: SimKeyframe["annotations"] = [];

  // Group actions by their delay for proper sequencing
  const actionsByDelay = new Map<number, SimAction[]>();
  for (const action of step.actions) {
    const delay = action.delay || 0;
    if (!actionsByDelay.has(delay)) {
      actionsByDelay.set(delay, []);
    }
    actionsByDelay.get(delay)!.push(action);
  }

  // Sort delays
  const sortedDelays = Array.from(actionsByDelay.keys()).sort((a, b) => a - b);

  for (const delay of sortedDelays) {
    const actions = actionsByDelay.get(delay)!;
    const timestamp = startTimestamp + delay;

    // Process each action at this delay
    for (const action of actions) {
      processAction(action, entityState, annotations);
    }

    // Generate keyframe at this point
    keyframes.push({
      stepIndex: step.orderIndex,
      timestamp,
      entities: Array.from(entityState.values()),
      annotations: [...annotations]
    });
  }

  // If no actions had delays, generate a single keyframe
  if (keyframes.length === 0) {
    keyframes.push({
      stepIndex: step.orderIndex,
      timestamp: startTimestamp,
      entities: Array.from(entityState.values()),
      annotations
    });
  }

  return keyframes;
}

/**
 * Process a single action and update entity state.
 */
function processAction(
  action: SimAction,
  entityState: Map<SimEntityId, SimEntity>,
  annotations: SimKeyframe["annotations"]
): void {
  switch (action.action) {
    case "CREATE_ENTITY": {
      const createAction = action as { action: "CREATE_ENTITY"; entity: SimEntity };
      entityState.set(createAction.entity.id, { ...createAction.entity });
      break;
    }

    case "REMOVE_ENTITY": {
      const removeAction = action as { action: "REMOVE_ENTITY"; targetId: SimEntityId };
      entityState.delete(removeAction.targetId);
      break;
    }

    case "MOVE": {
      const moveAction = action as {
        action: "MOVE";
        targetId: SimEntityId;
        toX: number;
        toY: number;
        toZ?: number;
      };
      const entity = entityState.get(moveAction.targetId);
      if (entity) {
        entity.x = moveAction.toX;
        entity.y = moveAction.toY;
        if (moveAction.toZ !== undefined) {
          entity.z = moveAction.toZ;
        }
      }
      break;
    }

    case "HIGHLIGHT": {
      const highlightAction = action as {
        action: "HIGHLIGHT";
        targetIds: SimEntityId[];
        style?: string;
      };
      for (const targetId of highlightAction.targetIds) {
        const entity = entityState.get(targetId);
        if (entity && "highlighted" in entity) {
          (entity as { highlighted: boolean }).highlighted = true;
        }
      }
      break;
    }

    case "COMPARE": {
      const compareAction = action as {
        action: "COMPARE";
        leftId: SimEntityId;
        rightId: SimEntityId;
        result?: "less" | "equal" | "greater";
      };
      const left = entityState.get(compareAction.leftId);
      const right = entityState.get(compareAction.rightId);
      if (left && "comparing" in left) {
        (left as { comparing: boolean }).comparing = true;
      }
      if (right && "comparing" in right) {
        (right as { comparing: boolean }).comparing = true;
      }
      break;
    }

    case "SWAP": {
      const swapAction = action as {
        action: "SWAP";
        id1: SimEntityId;
        id2: SimEntityId;
      };
      const entity1 = entityState.get(swapAction.id1);
      const entity2 = entityState.get(swapAction.id2);
      if (entity1 && entity2) {
        const tempX = entity1.x;
        const tempY = entity1.y;
        entity1.x = entity2.x;
        entity1.y = entity2.y;
        entity2.x = tempX;
        entity2.y = tempY;

        // Also swap values if they're node entities
        if ("value" in entity1 && "value" in entity2) {
          const tempValue = (entity1 as { value: unknown }).value;
          (entity1 as { value: unknown }).value = (entity2 as { value: unknown }).value;
          (entity2 as { value: unknown }).value = tempValue;
        }
      }
      break;
    }

    case "SET_VALUE": {
      const setValueAction = action as {
        action: "SET_VALUE";
        targetId: SimEntityId;
        value: unknown;
        property?: string;
      };
      const entity = entityState.get(setValueAction.targetId);
      if (entity) {
        const prop = setValueAction.property || "value";
        (entity as unknown as Record<string, unknown>)[prop] = setValueAction.value;
      }
      break;
    }

    case "ANNOTATE": {
      const annotateAction = action as {
        action: "ANNOTATE";
        targetId?: SimEntityId;
        text: string;
        position?: string;
      };
      const target = annotateAction.targetId
        ? entityState.get(annotateAction.targetId)
        : null;
      annotations.push({
        id: `annotation_${Date.now()}_${Math.random().toString(36).slice(2)}`,
        text: annotateAction.text,
        position: target
          ? { x: target.x, y: target.y - 30 }
          : { x: 400, y: 50 },
        targetId: annotateAction.targetId
      });
      break;
    }
  }
}

/**
 * Interpolate between two entity states.
 */
function interpolateEntity(
  from: SimEntity,
  to: SimEntity,
  progress: number
): SimEntity {
  const interpolated = { ...from };

  // Interpolate position
  if (from.x !== to.x) {
    interpolated.x = interpolate(from.x, to.x, progress);
  }
  if (from.y !== to.y) {
    interpolated.y = interpolate(from.y, to.y, progress);
  }
  if (from.z !== undefined && to.z !== undefined && from.z !== to.z) {
    interpolated.z = interpolate(from.z, to.z, progress);
  }

  // Interpolate visual properties
  if (from.opacity !== undefined && to.opacity !== undefined) {
    interpolated.opacity = interpolate(from.opacity, to.opacity, progress);
  }
  if (from.scale !== undefined && to.scale !== undefined) {
    interpolated.scale = interpolate(from.scale, to.scale, progress);
  }
  if (from.rotation !== undefined && to.rotation !== undefined) {
    interpolated.rotation = interpolate(from.rotation, to.rotation, progress);
  }

  return interpolated;
}
