/**
 * Biology Simulation Kernel
 * 
 * @doc.type class
 * @doc.purpose Execute biology simulations (cellular, molecular, genetic)
 * @doc.layer product
 * @doc.pattern Kernel
 */

import type {
  BiologyKernel as IBiologyKernel,
  SimulationRunRequest,
  SimulationRunResult,
  BiologyConfig
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type {
  SimulationManifest,
  SimKeyframe,
  SimEntity,
  SimEntityId,
  BioCellEntity,
  BioOrganelleEntity,
  BioCompartmentEntity,
  BioEnzymeEntity,
  BioGeneEntity
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Compartment state for concentration tracking.
 */
interface CompartmentState {
  id: SimEntityId;
  volume: number;
  concentrations: Map<string, number>;
  permeability: Map<string, number>;
}

/**
 * Gene expression state.
 */
interface GeneState {
  id: SimEntityId;
  sequence: string;
  promoterActive: boolean;
  expressionLevel: number;
  transcribing: boolean;
  translating: boolean;
}

/**
 * Create the biology kernel.
 * 
 * @doc.type function
 * @doc.purpose Factory function for biology kernel
 * @doc.layer product
 * @doc.pattern Factory
 */
export function createBiologyKernel(config?: BiologyConfig): IBiologyKernel {
  return {
    domain: "BIOLOGY" as const,

    canExecute(manifest: SimulationManifest): boolean {
      return manifest.domain === "BIOLOGY";
    },

    async run(request: SimulationRunRequest): Promise<SimulationRunResult> {
      const startTime = Date.now();
      const { manifest, samplingRate = 30 } = request;

      if (!this.canExecute(manifest)) {
        return {
          simulationId: manifest.id,
          keyframes: [],
          totalSteps: 0,
          executionTimeMs: Date.now() - startTime,
          errors: ["Manifest domain is not BIOLOGY"]
        };
      }

      // Initialize state
      const compartments = new Map<SimEntityId, CompartmentState>();
      const genes = new Map<SimEntityId, GeneState>();
      const entities = new Map<SimEntityId, SimEntity>();

      // Initialize from manifest
      for (const entity of manifest.initialEntities) {
        entities.set(entity.id, { ...entity });

        if (entity.type === "compartment") {
          const comp = entity as BioCompartmentEntity;
          compartments.set(entity.id, {
            id: entity.id,
            volume: comp.volume,
            concentrations: new Map(Object.entries(comp.concentration || {})),
            permeability: new Map(Object.entries(comp.permeability || {}))
          });
        } else if (entity.type === "gene") {
          const gene = entity as BioGeneEntity;
          genes.set(entity.id, {
            id: entity.id,
            sequence: gene.sequence || "",
            promoterActive: gene.promoterActive ?? false,
            expressionLevel: gene.expressionLevel || 0,
            transcribing: false,
            translating: false
          });
        }
      }

      const keyframes: SimKeyframe[] = [];
      const warnings: string[] = [];

      // Generate initial keyframe
      keyframes.push({
        stepIndex: -1,
        timestamp: 0,
        entities: Array.from(entities.values()),
        annotations: []
      });

      // Process simulation steps
      const stepsToProcess = manifest.steps.sort((a, b) => a.orderIndex - b.orderIndex);
      let currentTime = 0;
      const dt = 0.1; // Time step in seconds

      for (let stepIndex = 0; stepIndex < stepsToProcess.length; stepIndex++) {
        const step = stepsToProcess[stepIndex];

        // Apply step actions
        for (const action of step.actions) {
          applyBiologyAction(action, compartments, genes, entities);
        }

        // Simulate for step duration
        const stepDuration = 1000;
        const iterations = Math.ceil(stepDuration / (dt * 1000));

        for (let i = 0; i < iterations; i++) {
          // Simulate diffusion between compartments
          simulateAllDiffusion(compartments, dt);

          // Update gene expression
          updateGeneExpression(genes, dt);

          // Update entity states
          for (const [id, comp] of compartments) {
            const entity = entities.get(id);
            if (entity && entity.type === "compartment") {
              (entity as BioCompartmentEntity).concentration =
                Object.fromEntries(comp.concentrations);
            }
          }

          for (const [id, gene] of genes) {
            const entity = entities.get(id);
            if (entity && entity.type === "gene") {
              (entity as BioGeneEntity).expressionLevel = gene.expressionLevel;
            }
          }

          // Sample keyframe
          if (i % Math.ceil(iterations / 10) === 0) {
            currentTime += dt * Math.ceil(iterations / 10) * 1000;
            keyframes.push({
              stepIndex,
              timestamp: currentTime,
              entities: Array.from(entities.values()).map(e => ({ ...e })),
              annotations: step.narration ? [{
                id: `annotation_${stepIndex}`,
                text: step.narration,
                position: { x: 500, y: 30 }
              }] : []
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

    simulateDiffusion(
      compartments: Array<{ id: string; concentration: number; volume: number }>,
      permeability: number,
      timeStep: number
    ): Array<{ id: string; concentration: number }> {
      // Fick's first law: J = -D * (dC/dx)
      // For two compartments: flux = P * (C1 - C2)
      const result = compartments.map(c => ({
        id: c.id,
        concentration: c.concentration
      }));

      if (compartments.length >= 2) {
        const c1 = compartments[0];
        const c2 = compartments[1];

        // Calculate flux
        const flux = permeability * (c1.concentration - c2.concentration) * timeStep;

        // Update concentrations based on volumes
        result[0].concentration = c1.concentration - flux / c1.volume;
        result[1].concentration = c2.concentration + flux / c2.volume;
      }

      return result;
    },

    simulateEnzymeKinetics(
      substrate: number,
      enzyme: number,
      km: number,
      vmax: number,
      time: number
    ): { substrateRemaining: number; productFormed: number } {
      // Michaelis-Menten kinetics: v = Vmax * [S] / (Km + [S])
      // Numerical integration
      const dt = 0.01;
      let s = substrate;
      let p = 0;

      for (let t = 0; t < time; t += dt) {
        const v = (vmax * enzyme * s) / (km + s);
        const ds = v * dt;
        s = Math.max(0, s - ds);
        p += ds;
      }

      return {
        substrateRemaining: s,
        productFormed: p
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
 * Apply a biology action to the simulation state.
 */
function applyBiologyAction(
  action: any,
  compartments: Map<SimEntityId, CompartmentState>,
  genes: Map<SimEntityId, GeneState>,
  entities: Map<SimEntityId, SimEntity>
): void {
  switch (action.action) {
    case "DIFFUSE": {
      const molecule = action.molecule as string;
      const fromId = action.fromId as SimEntityId;
      const toId = action.toId as SimEntityId;
      const rate = action.rate as number;

      const fromComp = compartments.get(fromId);
      const toComp = compartments.get(toId);

      if (fromComp && toComp) {
        const fromConc = fromComp.concentrations.get(molecule) || 0;
        const toConc = toComp.concentrations.get(molecule) || 0;

        const flux = rate * (fromConc - toConc);
        fromComp.concentrations.set(molecule, fromConc - flux / fromComp.volume);
        toComp.concentrations.set(molecule, toConc + flux / toComp.volume);
      }
      break;
    }

    case "TRANSPORT": {
      const molecule = action.molecule as string;
      const fromId = action.fromId as SimEntityId;
      const toId = action.toId as SimEntityId;
      const transporterType = action.transporterType as string;
      const atpCost = action.atpCost as number | undefined;

      const fromComp = compartments.get(fromId);
      const toComp = compartments.get(toId);

      if (fromComp && toComp) {
        const fromConc = fromComp.concentrations.get(molecule) || 0;
        let amount = 0;

        switch (transporterType) {
          case "passive":
            // Down concentration gradient
            amount = Math.min(fromConc, 0.1);
            break;
          case "facilitated":
            // Saturating kinetics
            amount = Math.min(fromConc * 0.5, 1);
            break;
          case "active":
            // Against gradient (costs ATP)
            amount = Math.min(fromConc, 0.2);
            if (atpCost) {
              const atp = fromComp.concentrations.get("ATP") || 0;
              if (atp >= atpCost) {
                fromComp.concentrations.set("ATP", atp - atpCost);
              } else {
                amount = 0; // Not enough ATP
              }
            }
            break;
        }

        fromComp.concentrations.set(molecule, fromConc - amount);
        toComp.concentrations.set(molecule, (toComp.concentrations.get(molecule) || 0) + amount);
      }
      break;
    }

    case "TRANSCRIBE": {
      const geneId = action.geneId as SimEntityId;
      const gene = genes.get(geneId);

      if (gene && gene.promoterActive) {
        gene.transcribing = true;
        gene.expressionLevel = Math.min(1, gene.expressionLevel + 0.1);
      }
      break;
    }

    case "TRANSLATE": {
      const mRnaId = action.mRnaId as SimEntityId;
      // Would create protein entity
      break;
    }

    case "METABOLISE": {
      const enzymeId = action.enzymeId as SimEntityId;
      const substrateId = action.substrateId as SimEntityId;
      const productId = action.productId as SimEntityId;
      const rate = (action.rate as number) || 1;

      // Would handle enzyme kinetics
      break;
    }

    case "GROW_DIVIDE": {
      const cellId = action.cellId as SimEntityId;
      const phase = action.phase as string;

      const entity = entities.get(cellId);
      if (entity && entity.type === "cell") {
        // Update cell phase
        (entity as BioCellEntity).metadata = {
          ...(entity as BioCellEntity).metadata,
          phase
        };
      }
      break;
    }

    case "CREATE_ENTITY": {
      const entity = action.entity as SimEntity;
      entities.set(entity.id, { ...entity });
      break;
    }

    case "REMOVE_ENTITY": {
      const targetId = action.targetId as SimEntityId;
      entities.delete(targetId);
      compartments.delete(targetId);
      genes.delete(targetId);
      break;
    }
  }
}

/**
 * Simulate diffusion between all compartments.
 */
function simulateAllDiffusion(
  compartments: Map<SimEntityId, CompartmentState>,
  dt: number
): void {
  const compArray = Array.from(compartments.values());

  for (let i = 0; i < compArray.length; i++) {
    for (let j = i + 1; j < compArray.length; j++) {
      const c1 = compArray[i];
      const c2 = compArray[j];

      // Diffuse each molecule
      for (const [molecule, conc1] of c1.concentrations) {
        const conc2 = c2.concentrations.get(molecule) || 0;
        const permeability = Math.min(
          c1.permeability.get(molecule) || 1,
          c2.permeability.get(molecule) || 1
        );

        const flux = permeability * (conc1 - conc2) * dt;
        c1.concentrations.set(molecule, conc1 - flux / c1.volume);
        c2.concentrations.set(molecule, conc2 + flux / c2.volume);
      }
    }
  }
}

/**
 * Update gene expression levels.
 */
function updateGeneExpression(
  genes: Map<SimEntityId, GeneState>,
  dt: number
): void {
  for (const [, gene] of genes) {
    if (gene.transcribing) {
      // Increase expression
      gene.expressionLevel = Math.min(1, gene.expressionLevel + 0.05 * dt);
    } else {
      // Decay
      gene.expressionLevel = Math.max(0, gene.expressionLevel - 0.01 * dt);
    }
  }
}
