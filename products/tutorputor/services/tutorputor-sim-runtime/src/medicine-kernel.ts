/**
 * Medicine Simulation Kernel
 * 
 * @doc.type class
 * @doc.purpose Execute pharmacokinetic/pharmacodynamic and epidemiology simulations
 * @doc.layer product
 * @doc.pattern Kernel
 */

import type {
  MedicineKernel as IMedicineKernel,
  SimulationRunRequest,
  SimulationRunResult,
  MedicineConfig
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type {
  SimulationManifest,
  SimKeyframe,
  SimEntity,
  SimEntityId,
  MedCompartmentEntity,
  MedDoseEntity,
  MedInfectionAgentEntity
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * PK compartment state.
 */
interface PKCompartmentState {
  id: SimEntityId;
  type: "central" | "peripheral" | "effect";
  volume: number;
  concentration: number;
  ke?: number;
  k12?: number;
  k21?: number;
}

/**
 * Create the medicine kernel.
 * 
 * @doc.type function
 * @doc.purpose Factory function for medicine kernel
 * @doc.layer product
 * @doc.pattern Factory
 */
export function createMedicineKernel(config?: MedicineConfig): IMedicineKernel {
  return {
    domain: "MEDICINE" as const,

    canExecute(manifest: SimulationManifest): boolean {
      return manifest.domain === "MEDICINE";
    },

    async run(request: SimulationRunRequest): Promise<SimulationRunResult> {
      const startTime = Date.now();
      const { manifest, samplingRate = 60 } = request;

      if (!this.canExecute(manifest)) {
        return {
          simulationId: manifest.id,
          keyframes: [],
          totalSteps: 0,
          executionTimeMs: Date.now() - startTime,
          errors: ["Manifest domain is not MEDICINE"]
        };
      }

      // Extract model type from metadata
      let modelType = "one_compartment";
      let therapeuticRange: { min: number; max: number } | undefined;

      if (manifest.domainMetadata && "medicine" in manifest.domainMetadata) {
        const medicine = manifest.domainMetadata.medicine;
        modelType = medicine.modelType;
        therapeuticRange = medicine.therapeuticRange;
      }

      // Initialize state
      const compartments = new Map<SimEntityId, PKCompartmentState>();
      const doses: MedDoseEntity[] = [];
      const infectionAgents = new Map<SimEntityId, MedInfectionAgentEntity>();
      const entities = new Map<SimEntityId, SimEntity>();

      // Initialize from manifest
      for (const entity of manifest.initialEntities) {
        entities.set(entity.id, { ...entity });

        if (entity.type === "pkCompartment") {
          const comp = entity as MedCompartmentEntity;
          compartments.set(entity.id, {
            id: entity.id,
            type: comp.compartmentType,
            volume: comp.volume,
            concentration: comp.concentration,
            ke: comp.ke,
            k12: comp.k12,
            k21: comp.k21
          });
        } else if (entity.type === "dose") {
          doses.push(entity as MedDoseEntity);
        } else if (entity.type === "infectionAgent") {
          infectionAgents.set(entity.id, entity as MedInfectionAgentEntity);
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
      const dt = 0.1; // Time step in hours

      for (let stepIndex = 0; stepIndex < stepsToProcess.length; stepIndex++) {
        const step = stepsToProcess[stepIndex];

        // Apply step actions
        for (const action of step.actions) {
          applyMedicineAction(action, compartments, doses, infectionAgents, entities);
        }

        // Simulate for step duration (default 1 hour per step)
        const stepDuration = 1; // hours
        const iterations = Math.ceil(stepDuration / dt);

        for (let i = 0; i < iterations; i++) {
          currentTime += dt;

          // Process absorption from doses
          for (const dose of doses) {
            if (dose.route === "oral") {
              processOralAbsorption(dose, compartments, dt);
            } else if (dose.route === "iv") {
              processIVBolus(dose, compartments);
              doses.splice(doses.indexOf(dose), 1); // Remove after bolus
            }
          }

          // Simulate PK based on model type
          switch (modelType) {
            case "one_compartment":
              simulateOneCompartmentPK(compartments, dt);
              break;
            case "two_compartment":
              simulateTwoCompartmentPK(compartments, dt);
              break;
            case "sir":
            case "seir":
              simulateEpidemiology(infectionAgents, entities, modelType as "sir" | "seir", dt);
              break;
          }

          // Update entity states
          for (const [id, comp] of compartments) {
            const entity = entities.get(id);
            if (entity && entity.type === "pkCompartment") {
              (entity as MedCompartmentEntity).concentration = comp.concentration;
            }
          }

          // Check therapeutic range and add warnings
          if (therapeuticRange) {
            for (const [, comp] of compartments) {
              if (comp.type === "central") {
                if (comp.concentration > therapeuticRange.max) {
                  warnings.push(`Concentration ${comp.concentration.toFixed(2)} exceeds therapeutic range`);
                }
              }
            }
          }

          // Sample keyframe
          if (i % Math.ceil(iterations / 10) === 0) {
            const annotations: SimKeyframe["annotations"] = [];

            if (step.narration) {
              annotations.push({
                id: `annotation_${stepIndex}`,
                text: step.narration,
                position: { x: 450, y: 30 }
              });
            }

            // Add concentration annotation
            for (const [, comp] of compartments) {
              if (comp.type === "central") {
                annotations.push({
                  id: `conc_${currentTime}`,
                  text: `C = ${comp.concentration.toFixed(2)} mg/L`,
                  position: { x: 450, y: 60 }
                });
              }
            }

            keyframes.push({
              stepIndex,
              timestamp: currentTime * 3600 * 1000, // Convert hours to ms
              entities: Array.from(entities.values()).map(e => ({ ...e })),
              annotations
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

    runOneCompartmentPK(
      dose: number,
      volume: number,
      ke: number,
      timePoints: number[]
    ): Array<{ time: number; concentration: number }> {
      const c0 = dose / volume;
      return timePoints.map(t => ({
        time: t,
        concentration: c0 * Math.exp(-ke * t)
      }));
    },

    runTwoCompartmentPK(
      dose: number,
      v1: number,
      v2: number,
      k12: number,
      k21: number,
      ke: number,
      timePoints: number[]
    ): Array<{ time: number; c1: number; c2: number }> {
      // Solve two-compartment model using matrix exponential
      // Simplified: use numerical integration
      const dt = 0.01;
      const results: Array<{ time: number; c1: number; c2: number }> = [];

      let c1 = dose / v1;
      let c2 = 0;

      for (const targetTime of timePoints) {
        while (results.length === 0 || results[results.length - 1].time < targetTime) {
          const currentTime = results.length > 0 ? results[results.length - 1].time + dt : 0;

          // Rate equations
          const dc1 = -k12 * c1 + k21 * c2 * (v2 / v1) - ke * c1;
          const dc2 = k12 * c1 * (v1 / v2) - k21 * c2;

          c1 += dc1 * dt;
          c2 += dc2 * dt;

          c1 = Math.max(0, c1);
          c2 = Math.max(0, c2);

          if (Math.abs(currentTime - targetTime) < dt) {
            results.push({ time: targetTime, c1, c2 });
          }
        }
      }

      return results;
    },

    runEmaxPD(
      concentrations: number[],
      emax: number,
      ec50: number,
      hill: number = 1
    ): Array<{ concentration: number; effect: number }> {
      return concentrations.map(c => ({
        concentration: c,
        effect: (emax * Math.pow(c, hill)) / (Math.pow(ec50, hill) + Math.pow(c, hill))
      }));
    },

    runEpidemiologyModel(
      model: "sir" | "seir",
      population: number,
      initialInfected: number,
      beta: number,
      gamma: number,
      days: number,
      sigma?: number
    ): Array<{
      day: number;
      susceptible: number;
      exposed?: number;
      infected: number;
      recovered: number;
    }> {
      const results: Array<{
        day: number;
        susceptible: number;
        exposed?: number;
        infected: number;
        recovered: number;
      }> = [];

      const dt = 0.1;
      let S = population - initialInfected;
      let E = model === "seir" ? initialInfected * 0.5 : 0;
      let I = model === "seir" ? initialInfected * 0.5 : initialInfected;
      let R = 0;

      for (let day = 0; day <= days; day++) {
        results.push({
          day,
          susceptible: S,
          exposed: model === "seir" ? E : undefined,
          infected: I,
          recovered: R
        });

        // Simulate one day
        for (let t = 0; t < 1; t += dt) {
          if (model === "sir") {
            const dS = -beta * S * I / population;
            const dI = beta * S * I / population - gamma * I;
            const dR = gamma * I;

            S += dS * dt;
            I += dI * dt;
            R += dR * dt;
          } else {
            // SEIR model
            const sigmaVal = sigma || 0.2;
            const dS = -beta * S * I / population;
            const dE = beta * S * I / population - sigmaVal * E;
            const dI = sigmaVal * E - gamma * I;
            const dR = gamma * I;

            S += dS * dt;
            E += dE * dt;
            I += dI * dt;
            R += dR * dt;
          }

          // Clamp values
          S = Math.max(0, S);
          E = Math.max(0, E);
          I = Math.max(0, I);
          R = Math.max(0, R);
        }
      }

      return results;
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
 * Apply a medicine action to the simulation state.
 */
function applyMedicineAction(
  action: any,
  compartments: Map<SimEntityId, PKCompartmentState>,
  doses: MedDoseEntity[],
  infectionAgents: Map<SimEntityId, MedInfectionAgentEntity>,
  entities: Map<SimEntityId, SimEntity>
): void {
  switch (action.action) {
    case "ABSORB": {
      const doseId = action.doseId as SimEntityId;
      const compartmentId = action.compartmentId as SimEntityId;
      const rate = action.rate as number;

      const dose = doses.find(d => d.id === doseId);
      const comp = compartments.get(compartmentId);

      if (dose && comp) {
        const amount = dose.amount * rate * (dose.bioavailability || 1);
        comp.concentration += amount / comp.volume;
        dose.amount = Math.max(0, dose.amount - amount);
      }
      break;
    }

    case "ELIMINATE": {
      const compartmentId = action.compartmentId as SimEntityId;
      const rate = action.rate as number;

      const comp = compartments.get(compartmentId);
      if (comp) {
        comp.concentration *= (1 - rate);
      }
      break;
    }

    case "SPREAD_DISEASE": {
      const agentId = action.agentId as SimEntityId;
      const beta = action.beta as number;
      const gamma = action.gamma as number;

      const agent = infectionAgents.get(agentId);
      if (agent) {
        (agent as MedInfectionAgentEntity).infectivity = beta;
        agent.reproductionRate = beta / gamma; // R0
      }
      break;
    }

    case "CREATE_ENTITY": {
      const entity = action.entity as SimEntity;
      entities.set(entity.id, { ...entity });

      if (entity.type === "pkCompartment") {
        const comp = entity as MedCompartmentEntity;
        compartments.set(entity.id, {
          id: entity.id,
          type: comp.compartmentType,
          volume: comp.volume,
          concentration: comp.concentration,
          ke: comp.ke,
          k12: comp.k12,
          k21: comp.k21
        });
      } else if (entity.type === "dose") {
        doses.push(entity as MedDoseEntity);
      }
      break;
    }

    case "REMOVE_ENTITY": {
      const targetId = action.targetId as SimEntityId;
      entities.delete(targetId);
      compartments.delete(targetId);
      break;
    }
  }
}

/**
 * Process oral absorption.
 */
function processOralAbsorption(
  dose: MedDoseEntity,
  compartments: Map<SimEntityId, PKCompartmentState>,
  dt: number
): void {
  // Find central compartment
  for (const [, comp] of compartments) {
    if (comp.type === "central" && dose.amount > 0) {
      const ka = dose.absorptionRate || 1; // Absorption rate constant
      const amount = dose.amount * (1 - Math.exp(-ka * dt)) * (dose.bioavailability || 1);
      comp.concentration += amount / comp.volume;
      dose.amount = Math.max(0, dose.amount - amount);
    }
  }
}

/**
 * Process IV bolus.
 */
function processIVBolus(
  dose: MedDoseEntity,
  compartments: Map<SimEntityId, PKCompartmentState>
): void {
  for (const [, comp] of compartments) {
    if (comp.type === "central") {
      comp.concentration += dose.amount / comp.volume;
    }
  }
}

/**
 * Simulate one-compartment PK.
 */
function simulateOneCompartmentPK(
  compartments: Map<SimEntityId, PKCompartmentState>,
  dt: number
): void {
  for (const [, comp] of compartments) {
    if (comp.type === "central" && comp.ke) {
      comp.concentration *= Math.exp(-comp.ke * dt);
    }
  }
}

/**
 * Simulate two-compartment PK.
 */
function simulateTwoCompartmentPK(
  compartments: Map<SimEntityId, PKCompartmentState>,
  dt: number
): void {
  const central = Array.from(compartments.values()).find(c => c.type === "central");
  const peripheral = Array.from(compartments.values()).find(c => c.type === "peripheral");

  if (central && peripheral) {
    const k12 = central.k12 || 0;
    const k21 = central.k21 || peripheral.k21 || 0;
    const ke = central.ke || 0;

    const dc1 = -k12 * central.concentration +
      k21 * peripheral.concentration * (peripheral.volume / central.volume) -
      ke * central.concentration;
    const dc2 = k12 * central.concentration * (central.volume / peripheral.volume) -
      k21 * peripheral.concentration;

    central.concentration += dc1 * dt;
    peripheral.concentration += dc2 * dt;

    central.concentration = Math.max(0, central.concentration);
    peripheral.concentration = Math.max(0, peripheral.concentration);
  }
}

/**
 * Simulate epidemiology models (SIR/SEIR).
 */
function simulateEpidemiology(
  infectionAgents: Map<SimEntityId, MedInfectionAgentEntity>,
  entities: Map<SimEntityId, SimEntity>,
  model: "sir" | "seir",
  dt: number
): void {
  for (const [id, agent] of infectionAgents) {
    const population = agent.population;
    const beta = agent.infectivity || 0.3;
    const gamma = 1 / 14; // Recovery rate (14 days)

    // Update population in entity
    const entity = entities.get(id);
    if (entity && entity.type === "infectionAgent") {
      // Simplified: just update population based on reproduction
      const infected = agent.population;
      const newInfected = infected * beta * dt;
      const recovered = infected * gamma * dt;

      (entity as MedInfectionAgentEntity).population =
        Math.max(0, infected + newInfected - recovered);
    }
  }
}
