/**
 * Chemistry Simulation Kernel
 * 
 * @doc.type class
 * @doc.purpose Execute chemistry simulations for reactions and molecular structures
 * @doc.layer product
 * @doc.pattern Kernel
 */

import type {
  ChemistryKernel as IChemistryKernel,
  SimulationRunRequest,
  SimulationRunResult,
  ChemistryConfig
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type {
  SimulationManifest,
  SimKeyframe,
  SimEntity,
  SimEntityId,
  ChemAtomEntity,
  ChemBondEntity,
  ChemMoleculeEntity,
  ChemEnergyProfileEntity
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Element properties for validation and rendering.
 */
const ELEMENT_DATA: Record<string, {
  atomicNumber: number;
  valence: number[];
  color: string;
  radius: number;
}> = {
  H: { atomicNumber: 1, valence: [1], color: "#FFFFFF", radius: 25 },
  C: { atomicNumber: 6, valence: [4], color: "#909090", radius: 40 },
  N: { atomicNumber: 7, valence: [3], color: "#3050F8", radius: 38 },
  O: { atomicNumber: 8, valence: [2], color: "#FF0D0D", radius: 35 },
  F: { atomicNumber: 9, valence: [1], color: "#90E050", radius: 32 },
  P: { atomicNumber: 15, valence: [3, 5], color: "#FF8000", radius: 45 },
  S: { atomicNumber: 16, valence: [2, 4, 6], color: "#FFFF30", radius: 45 },
  Cl: { atomicNumber: 17, valence: [1], color: "#1FF01F", radius: 40 },
  Br: { atomicNumber: 35, valence: [1], color: "#A62929", radius: 45 },
  I: { atomicNumber: 53, valence: [1], color: "#940094", radius: 50 }
};

/**
 * Internal atom state.
 */
interface AtomState {
  id: SimEntityId;
  element: string;
  x: number;
  y: number;
  charge: number;
  bonds: SimEntityId[];
  totalBondOrder: number;
}

/**
 * Internal bond state.
 */
interface BondState {
  id: SimEntityId;
  atom1Id: SimEntityId;
  atom2Id: SimEntityId;
  bondOrder: number;
  breaking?: boolean;
  forming?: boolean;
}

/**
 * Create the chemistry kernel.
 * 
 * @doc.type function
 * @doc.purpose Factory function for chemistry kernel
 * @doc.layer product
 * @doc.pattern Factory
 */
export function createChemistryKernel(config?: ChemistryConfig): IChemistryKernel {
  return {
    domain: "CHEMISTRY" as const,

    canExecute(manifest: SimulationManifest): boolean {
      return manifest.domain === "CHEMISTRY";
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
          errors: ["Manifest domain is not CHEMISTRY"]
        };
      }

      // Initialize state
      const atoms = new Map<SimEntityId, AtomState>();
      const bonds = new Map<SimEntityId, BondState>();
      const molecules = new Map<SimEntityId, ChemMoleculeEntity>();
      const entities = new Map<SimEntityId, SimEntity>();

      // Initialize from manifest
      for (const entity of manifest.initialEntities) {
        entities.set(entity.id, { ...entity });

        if (entity.type === "atom") {
          const atom = entity as ChemAtomEntity;
          atoms.set(entity.id, {
            id: entity.id,
            element: atom.element,
            x: entity.x,
            y: entity.y,
            charge: atom.charge || 0,
            bonds: [],
            totalBondOrder: 0
          });
        } else if (entity.type === "bond") {
          const bond = entity as ChemBondEntity;
          bonds.set(entity.id, {
            id: entity.id,
            atom1Id: bond.atom1Id,
            atom2Id: bond.atom2Id,
            bondOrder: bond.bondOrder
          });

          // Update atom bond lists
          const atom1 = atoms.get(bond.atom1Id);
          const atom2 = atoms.get(bond.atom2Id);
          if (atom1) {
            atom1.bonds.push(entity.id);
            atom1.totalBondOrder += bond.bondOrder;
          }
          if (atom2) {
            atom2.bonds.push(entity.id);
            atom2.totalBondOrder += bond.bondOrder;
          }
        } else if (entity.type === "molecule") {
          molecules.set(entity.id, entity as ChemMoleculeEntity);
        }
      }

      const keyframes: SimKeyframe[] = [];
      const warnings: string[] = [];

      // Validate initial state
      const valenceErrors = validateValence(atoms, bonds);
      if (valenceErrors.length > 0) {
        warnings.push(...valenceErrors);
      }

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

      for (let stepIndex = 0; stepIndex < stepsToProcess.length; stepIndex++) {
        const step = stepsToProcess[stepIndex];

        // Apply step actions
        for (const action of step.actions) {
          applyChemistryAction(action, atoms, bonds, molecules, entities);
        }

        // Update entity positions from atom states
        for (const [id, atom] of atoms) {
          const entity = entities.get(id);
          if (entity) {
            entity.x = atom.x;
            entity.y = atom.y;
          }
        }

        // Generate keyframes for this step
        const stepDuration = 1000; // 1 second per step
        const framesPerStep = Math.ceil(stepDuration / (1000 / samplingRate));

        for (let frame = 0; frame < framesPerStep; frame++) {
          currentTime += 1000 / samplingRate;

          keyframes.push({
            stepIndex,
            timestamp: currentTime,
            entities: Array.from(entities.values()).map(e => ({ ...e })),
            annotations: step.narration ? [{
              id: `annotation_${stepIndex}`,
              text: step.narration,
              position: { x: 450, y: 30 }
            }] : []
          });
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

    async parseSMILES(smiles: string): Promise<{
      atoms: Array<{ element: string; x: number; y: number; charge?: number }>;
      bonds: Array<{ from: number; to: number; order: number }>;
    }> {
      // Simple SMILES parser for basic molecules
      // In production, use RDKit or OpenBabel WASM
      const atoms: Array<{ element: string; x: number; y: number; charge?: number }> = [];
      const bonds: Array<{ from: number; to: number; order: number }> = [];

      const elementRegex = /([A-Z][a-z]?)/g;
      const bondRegex = /[=\-#]/g;

      let match;
      let atomIndex = 0;
      const spacing = 80;
      let x = 100;
      let y = 200;
      let direction = 1;

      // Parse atoms
      while ((match = elementRegex.exec(smiles)) !== null) {
        const element = match[1];
        if (ELEMENT_DATA[element]) {
          atoms.push({
            element,
            x,
            y: y + (atomIndex % 2) * 30 * direction
          });

          // Create bond to previous atom
          if (atomIndex > 0) {
            // Check for double or triple bond
            const prevChar = smiles.charAt(match.index - 1);
            let bondOrder = 1;
            if (prevChar === "=") bondOrder = 2;
            else if (prevChar === "#") bondOrder = 3;

            bonds.push({
              from: atomIndex - 1,
              to: atomIndex,
              order: bondOrder
            });
          }

          x += spacing;
          atomIndex++;
        }
      }

      return { atoms, bonds };
    },

    validateReaction(
      reactants: string[],
      products: string[]
    ): {
      valid: boolean;
      massBalanced: boolean;
      chargeBalanced: boolean;
      errors: string[];
    } {
      const errors: string[] = [];

      // Count atoms in reactants
      const reactantAtoms = new Map<string, number>();
      for (const smiles of reactants) {
        const atomCounts = countAtomsInSMILES(smiles);
        for (const [element, count] of atomCounts) {
          reactantAtoms.set(element, (reactantAtoms.get(element) || 0) + count);
        }
      }

      // Count atoms in products
      const productAtoms = new Map<string, number>();
      for (const smiles of products) {
        const atomCounts = countAtomsInSMILES(smiles);
        for (const [element, count] of atomCounts) {
          productAtoms.set(element, (productAtoms.get(element) || 0) + count);
        }
      }

      // Check mass balance
      let massBalanced = true;
      for (const [element, count] of reactantAtoms) {
        if (productAtoms.get(element) !== count) {
          massBalanced = false;
          errors.push(`Element ${element}: ${count} in reactants, ${productAtoms.get(element) || 0} in products`);
        }
      }
      for (const [element, count] of productAtoms) {
        if (!reactantAtoms.has(element)) {
          massBalanced = false;
          errors.push(`Element ${element} appears only in products`);
        }
      }

      // Charge balance (simplified - would need proper SMILES parsing)
      const chargeBalanced = true; // Placeholder

      return {
        valid: massBalanced && chargeBalanced,
        massBalanced,
        chargeBalanced,
        errors
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
 * Apply a chemistry action to the simulation state.
 */
function applyChemistryAction(
  action: any,
  atoms: Map<SimEntityId, AtomState>,
  bonds: Map<SimEntityId, BondState>,
  molecules: Map<SimEntityId, ChemMoleculeEntity>,
  entities: Map<SimEntityId, SimEntity>
): void {
  switch (action.action) {
    case "CREATE_BOND": {
      const atom1Id = action.atom1Id as SimEntityId;
      const atom2Id = action.atom2Id as SimEntityId;
      const bondOrder = action.bondOrder as number;

      const bondId = `bond_${atom1Id}_${atom2Id}` as SimEntityId;

      // Create bond state
      bonds.set(bondId, {
        id: bondId,
        atom1Id,
        atom2Id,
        bondOrder,
        forming: true
      });

      // Update atom bond lists
      const atom1 = atoms.get(atom1Id);
      const atom2 = atoms.get(atom2Id);
      if (atom1) {
        atom1.bonds.push(bondId);
        atom1.totalBondOrder += bondOrder;
      }
      if (atom2) {
        atom2.bonds.push(bondId);
        atom2.totalBondOrder += bondOrder;
      }

      // Create entity
      const atom1Entity = entities.get(atom1Id);
      const atom2Entity = entities.get(atom2Id);
      if (atom1Entity && atom2Entity) {
        entities.set(bondId, {
          id: bondId,
          type: "bond",
          x: (atom1Entity.x + atom2Entity.x) / 2,
          y: (atom1Entity.y + atom2Entity.y) / 2,
          atom1Id,
          atom2Id,
          bondOrder
        } as ChemBondEntity);
      }
      break;
    }

    case "BREAK_BOND": {
      const bondId = action.bondId as SimEntityId;
      const bond = bonds.get(bondId);

      if (bond) {
        // Mark bond as breaking
        bond.breaking = true;

        // Update atom bond lists
        const atom1 = atoms.get(bond.atom1Id);
        const atom2 = atoms.get(bond.atom2Id);
        if (atom1) {
          atom1.bonds = atom1.bonds.filter(b => b !== bondId);
          atom1.totalBondOrder -= bond.bondOrder;
        }
        if (atom2) {
          atom2.bonds = atom2.bonds.filter(b => b !== bondId);
          atom2.totalBondOrder -= bond.bondOrder;
        }

        // Remove bond
        bonds.delete(bondId);
        entities.delete(bondId);

        // If homolytic cleavage, update charges
        if (action.homolytic) {
          if (atom1) atom1.charge = 0;
          if (atom2) atom2.charge = 0;
        }
      }
      break;
    }

    case "REARRANGE": {
      const atomIds = action.atomIds as SimEntityId[];
      const newPositions = action.newPositions as Array<{ id: SimEntityId; x: number; y: number }>;

      for (const pos of newPositions) {
        const atom = atoms.get(pos.id);
        if (atom) {
          atom.x = pos.x;
          atom.y = pos.y;
        }
        const entity = entities.get(pos.id);
        if (entity) {
          entity.x = pos.x;
          entity.y = pos.y;
        }
      }
      break;
    }

    case "HIGHLIGHT_ATOMS": {
      const atomIds = action.atomIds as SimEntityId[];
      const style = action.style as string;

      for (const atomId of atomIds) {
        const entity = entities.get(atomId);
        if (entity && entity.type === "atom") {
          // Apply highlight color based on style
          switch (style) {
            case "nucleophile":
              (entity as ChemAtomEntity).color = "#00FF00";
              break;
            case "electrophile":
              (entity as ChemAtomEntity).color = "#FF0000";
              break;
            case "leaving_group":
              (entity as ChemAtomEntity).color = "#FFFF00";
              break;
            case "active_site":
              (entity as ChemAtomEntity).color = "#00FFFF";
              break;
          }
        }
      }
      break;
    }

    case "SET_REACTION_CONDITIONS": {
      // Store conditions in metadata (would affect reaction rates in advanced simulation)
      // For now, just update annotations
      break;
    }

    case "DISPLAY_FORMULA": {
      // Would render formula at specified position
      // Handled by renderer
      break;
    }

    case "SHOW_ENERGY_PROFILE": {
      // Would animate energy profile
      // Handled by renderer
      break;
    }

    case "CREATE_ENTITY": {
      const entity = action.entity as SimEntity;
      entities.set(entity.id, { ...entity });

      if (entity.type === "atom") {
        const atom = entity as ChemAtomEntity;
        atoms.set(entity.id, {
          id: entity.id,
          element: atom.element,
          x: entity.x,
          y: entity.y,
          charge: atom.charge || 0,
          bonds: [],
          totalBondOrder: 0
        });
      }
      break;
    }

    case "REMOVE_ENTITY": {
      const targetId = action.targetId as SimEntityId;
      entities.delete(targetId);
      atoms.delete(targetId);
      bonds.delete(targetId);
      break;
    }
  }
}

/**
 * Validate valence rules for all atoms.
 */
function validateValence(
  atoms: Map<SimEntityId, AtomState>,
  bonds: Map<SimEntityId, BondState>
): string[] {
  const errors: string[] = [];

  for (const [id, atom] of atoms) {
    const elementData = ELEMENT_DATA[atom.element];
    if (!elementData) continue;

    const expectedValence = elementData.valence;
    const actualValence = atom.totalBondOrder - atom.charge;

    if (!expectedValence.includes(actualValence)) {
      errors.push(
        `Atom ${atom.element} (${id}) has valence ${actualValence}, expected one of ${expectedValence.join(", ")}`
      );
    }
  }

  return errors;
}

/**
 * Count atoms in a SMILES string.
 */
function countAtomsInSMILES(smiles: string): Map<string, number> {
  const counts = new Map<string, number>();
  const elementRegex = /([A-Z][a-z]?)(\d*)/g;

  let match;
  while ((match = elementRegex.exec(smiles)) !== null) {
    const element = match[1];
    const count = match[2] ? parseInt(match[2], 10) : 1;

    if (ELEMENT_DATA[element]) {
      counts.set(element, (counts.get(element) || 0) + count);
    }
  }

  // Count implicit hydrogens (simplified)
  // In production, use a proper SMILES parser

  return counts;
}
