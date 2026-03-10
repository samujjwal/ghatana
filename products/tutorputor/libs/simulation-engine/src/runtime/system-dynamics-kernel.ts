/**
 * System Dynamics Kernel
 * 
 * @doc.type class
 * @doc.purpose Execute economics and system dynamics simulations
 * @doc.layer product
 * @doc.pattern Kernel
 */

import type {
  SystemDynamicsKernel as ISystemDynamicsKernel,
  SimulationRunRequest,
  SimulationRunResult,
  EconomicsConfig
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type {
  SimulationManifest,
  SimKeyframe,
  SimEntity,
  SimEntityId,
  EconStockEntity,
  EconFlowEntity,
  EconAgentEntity
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Internal stock state.
 */
interface StockState {
  id: SimEntityId;
  value: number;
  minValue?: number;
  maxValue?: number;
  history: number[];
}

/**
 * Internal flow state.
 */
interface FlowState {
  id: SimEntityId;
  sourceId: SimEntityId | "external";
  targetId: SimEntityId | "external";
  rate: number;
  equation?: string;
  delay?: number;
  delayBuffer: number[];
}

/**
 * Chart data accumulator.
 */
interface ChartData {
  id: string;
  type: "line" | "bar" | "area" | "pie";
  dataSourceIds: SimEntityId[];
  title: string;
  position: { x: number; y: number; width: number; height: number };
  data: Array<{ x: number; y: number; label?: string }>;
}

/**
 * Create the system dynamics kernel.
 * 
 * @doc.type function
 * @doc.purpose Factory function for system dynamics kernel
 * @doc.layer product
 * @doc.pattern Factory
 */
export function createSystemDynamicsKernel(config?: EconomicsConfig): ISystemDynamicsKernel {
  return {
    domain: "SYSTEM_DYNAMICS" as any,

    canExecute(manifest: SimulationManifest): boolean {
      return manifest.domain === "SYSTEM_DYNAMICS" as any;
    },

    async run(request: SimulationRunRequest): Promise<SimulationRunResult> {
      return this.runWithIntegration(request, "euler");
    },

    async runWithIntegration(
      request: SimulationRunRequest,
      method: "euler" | "rk4"
    ): Promise<SimulationRunResult> {
      const startTime = Date.now();
      const { manifest, samplingRate = 10 } = request;

      if (!this.canExecute(manifest)) {
        return {
          simulationId: manifest.id,
          keyframes: [],
          totalSteps: 0,
          executionTimeMs: Date.now() - startTime,
          errors: ["Manifest domain is not SYSTEM_DYNAMICS"]
        };
      }

      // Extract simulation parameters from metadata
      let timeStep = 1;
      let simulationDuration = 100;
      let integrationMethod = method;

      if (manifest.domainMetadata && "economics" in manifest.domainMetadata) {
        const economics = manifest.domainMetadata.economics;
        timeStep = economics.timeStep || timeStep;
        simulationDuration = economics.simulationDuration || simulationDuration;
        integrationMethod = economics.integrationMethod || integrationMethod;
      }

      // Initialize state
      const stocks = new Map<SimEntityId, StockState>();
      const flows = new Map<SimEntityId, FlowState>();
      const agents = new Map<SimEntityId, EconAgentEntity>();
      const entities = new Map<SimEntityId, SimEntity>();
      const charts: ChartData[] = [];

      // Initialize from manifest
      for (const entity of manifest.initialEntities) {
        entities.set(entity.id, { ...entity });

        if (entity.type === "stock") {
          const stock = entity as EconStockEntity;
          stocks.set(entity.id, {
            id: entity.id,
            value: stock.value,
            minValue: stock.minValue,
            maxValue: stock.maxValue,
            history: [stock.value]
          });
        } else if (entity.type === "flow") {
          const flow = entity as EconFlowEntity;
          flows.set(entity.id, {
            id: entity.id,
            sourceId: flow.sourceId,
            targetId: flow.targetId,
            rate: flow.rate,
            equation: flow.equation,
            delay: flow.delay,
            delayBuffer: []
          });
        } else if (entity.type === "agent") {
          agents.set(entity.id, entity as EconAgentEntity);
        }
      }

      const keyframes: SimKeyframe[] = [];
      const warnings: string[] = [];

      // Generate initial keyframe
      keyframes.push({
        stepIndex: -1,
        timestamp: 0,
        entities: Array.from(entities.values()),
        annotations: [],
        charts: []
      });

      // Process simulation steps
      const stepsToProcess = manifest.steps.sort((a, b) => a.orderIndex - b.orderIndex);
      let currentTime = 0;

      for (let stepIndex = 0; stepIndex < stepsToProcess.length; stepIndex++) {
        const step = stepsToProcess[stepIndex];

        // Apply step actions
        for (const action of step.actions) {
          applyEconomicsAction(action, stocks, flows, agents, entities, charts);
        }

        // Simulate system dynamics for this step
        const stepsPerFrame = Math.max(1, Math.floor(simulationDuration / samplingRate / stepsToProcess.length));

        for (let t = 0; t < stepsPerFrame; t++) {
          // Calculate flows and update stocks
          if (integrationMethod === "euler") {
            integrateEuler(stocks, flows, timeStep);
          } else {
            integrateRK4(stocks, flows, timeStep);
          }

          currentTime += timeStep;

          // Update entity values from stock states
          for (const [id, stock] of stocks) {
            const entity = entities.get(id);
            if (entity && entity.type === "stock") {
              (entity as EconStockEntity).value = stock.value;
            }
            stock.history.push(stock.value);
          }

          // Generate keyframe
          if (t % Math.ceil(stepsPerFrame / 5) === 0) {
            // Update chart data
            const chartSnapshots = charts.map(chart => {
              const data: Array<{ x: number; y: number; label?: string }> = [];
              for (const sourceId of chart.dataSourceIds) {
                const stock = stocks.get(sourceId);
                if (stock) {
                  data.push({
                    x: currentTime,
                    y: stock.value,
                    label: (entities.get(sourceId) as EconStockEntity)?.label
                  });
                }
              }
              return { ...chart, data };
            });

            keyframes.push({
              stepIndex,
              timestamp: currentTime * 1000, // Convert to ms
              entities: Array.from(entities.values()).map(e => ({ ...e })),
              annotations: step.narration ? [{
                id: `annotation_${stepIndex}`,
                text: step.narration,
                position: { x: 400, y: 30 }
              }] : [],
              charts: chartSnapshots
            });
          }
        }
      }

      return {
        simulationId: manifest.id,
        keyframes,
        totalSteps: stepsToProcess.length,
        executionTimeMs: Date.now() - startTime,
        warnings: warnings.length > 0 ? warnings : undefined
      };
    },

    validateConservation(result: SimulationRunResult): {
      valid: boolean;
      violations: Array<{ law: string; deviation: number }>;
    } {
      const violations: Array<{ law: string; deviation: number }> = [];

      // Check mass conservation across all keyframes
      if (result.keyframes.length >= 2) {
        const first = result.keyframes[0];
        const last = result.keyframes[result.keyframes.length - 1];

        // Sum all stock values
        const firstTotal = first.entities
          .filter(e => e.type === "stock")
          .reduce((sum, e) => sum + ((e as EconStockEntity).value || 0), 0);

        const lastTotal = last.entities
          .filter(e => e.type === "stock")
          .reduce((sum, e) => sum + ((e as EconStockEntity).value || 0), 0);

        // Check for external flows to determine if conservation applies
        const hasExternalFlows = first.entities.some(e => {
          if (e.type !== "flow") return false;
          const flow = e as EconFlowEntity;
          return flow.sourceId === ("external" as SimEntityId) ||
            flow.targetId === ("external" as SimEntityId);
        });

        if (!hasExternalFlows) {
          const deviation = Math.abs(lastTotal - firstTotal) / Math.max(firstTotal, 1);
          if (deviation > 0.01) { // 1% tolerance
            violations.push({
              law: "mass_conservation",
              deviation
            });
          }
        }
      }

      return {
        valid: violations.length === 0,
        violations
      };
    },

    async checkHealth(): Promise<boolean> {
      return true;
    },

    serialize(): string {
      return JSON.stringify({});
    },

    deserialize(state: string): void {
      // No-op for stateless execution
    },

    initialize(manifest: SimulationManifest): void {
      // No-op
    },

    step(): void {
      // No-op
    },

    interpolate(t: number): Partial<SimKeyframe> {
      return {};
    },

    reset(): void {
      // No-op
    },

    getAnalytics(): Record<string, unknown> {
      return {};
    }
  };
}

/**
 * Apply an economics action to the simulation state.
 */
function applyEconomicsAction(
  action: any,
  stocks: Map<SimEntityId, StockState>,
  flows: Map<SimEntityId, FlowState>,
  agents: Map<SimEntityId, EconAgentEntity>,
  entities: Map<SimEntityId, SimEntity>,
  charts: ChartData[]
): void {
  switch (action.action) {
    case "SET_STOCK_VALUE": {
      const targetId = action.targetId as SimEntityId;
      const stock = stocks.get(targetId);
      if (stock) {
        stock.value = action.value as number;
      }
      break;
    }

    case "UPDATE_FLOW_RATE": {
      const targetId = action.targetId as SimEntityId;
      const flow = flows.get(targetId);
      if (flow) {
        flow.rate = action.rate as number;
      }
      break;
    }

    case "SPAWN_AGENT": {
      const entity = action.entity as EconAgentEntity;
      entities.set(entity.id, { ...entity });
      agents.set(entity.id, entity);
      break;
    }

    case "DISPLAY_CHART": {
      charts.push({
        id: `chart_${Date.now()}`,
        type: action.chartType as ChartData["type"],
        dataSourceIds: action.dataSourceIds as SimEntityId[],
        title: (action.title as string) || "Chart",
        position: action.position as ChartData["position"],
        data: []
      });
      break;
    }

    case "CREATE_ENTITY": {
      const entity = action.entity as SimEntity;
      entities.set(entity.id, { ...entity });
      if (entity.type === "stock") {
        const stock = entity as EconStockEntity;
        stocks.set(entity.id, {
          id: entity.id,
          value: stock.value,
          minValue: stock.minValue,
          maxValue: stock.maxValue,
          history: [stock.value]
        });
      } else if (entity.type === "flow") {
        const flow = entity as EconFlowEntity;
        flows.set(entity.id, {
          id: entity.id,
          sourceId: flow.sourceId,
          targetId: flow.targetId,
          rate: flow.rate,
          equation: flow.equation,
          delay: flow.delay,
          delayBuffer: []
        });
      }
      break;
    }

    case "REMOVE_ENTITY": {
      const targetId = action.targetId as SimEntityId;
      entities.delete(targetId);
      stocks.delete(targetId);
      flows.delete(targetId);
      agents.delete(targetId);
      break;
    }
  }
}

/**
 * Euler integration for system dynamics.
 */
function integrateEuler(
  stocks: Map<SimEntityId, StockState>,
  flows: Map<SimEntityId, FlowState>,
  dt: number
): void {
  // Calculate net flow for each stock
  const netFlows = new Map<SimEntityId, number>();

  for (const [, stock] of stocks) {
    netFlows.set(stock.id, 0);
  }

  for (const [, flow] of flows) {
    let flowValue = flow.rate * dt;

    // Evaluate equation if present
    if (flow.equation) {
      flowValue = evaluateFlowEquation(flow.equation, stocks, dt);
    }

    // Handle delay
    if (flow.delay && flow.delay > 0) {
      flow.delayBuffer.push(flowValue);
      if (flow.delayBuffer.length > flow.delay) {
        flowValue = flow.delayBuffer.shift() || 0;
      } else {
        flowValue = 0;
      }
    }

    // Apply flow to source and target
    if (flow.sourceId !== "external") {
      const current = netFlows.get(flow.sourceId) || 0;
      netFlows.set(flow.sourceId, current - flowValue);
    }

    if (flow.targetId !== "external") {
      const current = netFlows.get(flow.targetId) || 0;
      netFlows.set(flow.targetId, current + flowValue);
    }
  }

  // Update stock values
  for (const [id, stock] of stocks) {
    const netFlow = netFlows.get(id) || 0;
    stock.value += netFlow;

    // Apply constraints
    if (stock.minValue !== undefined) {
      stock.value = Math.max(stock.minValue, stock.value);
    }
    if (stock.maxValue !== undefined) {
      stock.value = Math.min(stock.maxValue, stock.value);
    }
  }
}

/**
 * RK4 integration for system dynamics.
 */
function integrateRK4(
  stocks: Map<SimEntityId, StockState>,
  flows: Map<SimEntityId, FlowState>,
  dt: number
): void {
  // Store original values
  const originalValues = new Map<SimEntityId, number>();
  for (const [id, stock] of stocks) {
    originalValues.set(id, stock.value);
  }

  // Calculate k1
  const k1 = calculateDerivatives(stocks, flows, dt);

  // Calculate k2 at midpoint
  for (const [id, stock] of stocks) {
    stock.value = originalValues.get(id)! + (k1.get(id) || 0) * dt / 2;
  }
  const k2 = calculateDerivatives(stocks, flows, dt);

  // Calculate k3 at midpoint
  for (const [id, stock] of stocks) {
    stock.value = originalValues.get(id)! + (k2.get(id) || 0) * dt / 2;
  }
  const k3 = calculateDerivatives(stocks, flows, dt);

  // Calculate k4 at end
  for (const [id, stock] of stocks) {
    stock.value = originalValues.get(id)! + (k3.get(id) || 0) * dt;
  }
  const k4 = calculateDerivatives(stocks, flows, dt);

  // Combine using RK4 formula
  for (const [id, stock] of stocks) {
    const original = originalValues.get(id)!;
    const derivative =
      (k1.get(id) || 0) / 6 +
      (k2.get(id) || 0) / 3 +
      (k3.get(id) || 0) / 3 +
      (k4.get(id) || 0) / 6;

    stock.value = original + derivative * dt;

    // Apply constraints
    if (stock.minValue !== undefined) {
      stock.value = Math.max(stock.minValue, stock.value);
    }
    if (stock.maxValue !== undefined) {
      stock.value = Math.min(stock.maxValue, stock.value);
    }
  }
}

/**
 * Calculate derivatives for all stocks.
 */
function calculateDerivatives(
  stocks: Map<SimEntityId, StockState>,
  flows: Map<SimEntityId, FlowState>,
  dt: number
): Map<SimEntityId, number> {
  const derivatives = new Map<SimEntityId, number>();

  for (const [, stock] of stocks) {
    derivatives.set(stock.id, 0);
  }

  for (const [, flow] of flows) {
    let flowRate = flow.rate;

    if (flow.equation) {
      flowRate = evaluateFlowEquation(flow.equation, stocks, dt) / dt;
    }

    if (flow.sourceId !== "external") {
      const current = derivatives.get(flow.sourceId) || 0;
      derivatives.set(flow.sourceId, current - flowRate);
    }

    if (flow.targetId !== "external") {
      const current = derivatives.get(flow.targetId) || 0;
      derivatives.set(flow.targetId, current + flowRate);
    }
  }

  return derivatives;
}

/**
 * Evaluate a flow equation with stock values.
 */
function evaluateFlowEquation(
  equation: string,
  stocks: Map<SimEntityId, StockState>,
  dt: number
): number {
  // Simple equation parser - replace stock names with values
  let expr = equation;

  for (const [id, stock] of stocks) {
    const entity = id as string;
    expr = expr.replace(new RegExp(entity, "g"), stock.value.toString());
  }

  // Replace dt
  expr = expr.replace(/dt/g, dt.toString());

  try {
    // Safe evaluation using Function constructor
    // In production, use a proper expression parser
    const fn = new Function(`return ${expr}`);
    return fn();
  } catch {
    return 0;
  }
}
