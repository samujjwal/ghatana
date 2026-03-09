/**
 * Chemistry AI Tutor Context Pipeline
 *
 * Provides chemistry-specific context to the AI tutor during simulation
 * playback, enabling intelligent explanations and guidance.
 *
 * @doc.type module
 * @doc.purpose Chemistry-specific tutor context for AI guidance
 * @doc.layer product
 * @doc.pattern TutorContext
 */

import type {
    SimulationManifest,
    SimKeyframe,
    SimEntity,
    SimulationStep,
    SimEntityId,
} from '@ghatana/tutorputor-contracts/v1/simulation/types';

// =============================================================================
// Types
// =============================================================================

/**
 * Chemistry-specific context for the AI tutor
 */
export interface ChemistryTutorContext {
    /** Current reaction type (substitution, addition, elimination, etc.) */
    reactionType: string;
    /** Reaction mechanism (SN1, SN2, E1, E2, etc.) */
    mechanism?: string;
    /** Current reaction phase */
    phase: ChemistryPhase;
    /** Active molecules in the scene */
    molecules: MoleculeContext[];
    /** Current bonds being formed/broken */
    bondChanges: BondChange[];
    /** Energy profile state */
    energyState?: EnergyContext;
    /** Stereochemistry information */
    stereochemistry?: StereochemistryContext;
    /** Relevant concepts for this step */
    relevantConcepts: string[];
    /** Common misconceptions to address */
    misconceptions: string[];
    /** Suggested questions for the student */
    suggestedQuestions: string[];
}

/**
 * Phases of a chemistry simulation
 */
export type ChemistryPhase =
    | 'setup'
    | 'reactant_approach'
    | 'transition_state'
    | 'bond_breaking'
    | 'bond_forming'
    | 'product_formation'
    | 'equilibrium'
    | 'complete';

/**
 * Context for a molecule in the simulation
 */
export interface MoleculeContext {
    id: SimEntityId;
    name: string;
    formula: string;
    role: 'reactant' | 'product' | 'intermediate' | 'catalyst' | 'spectator';
    atoms: AtomContext[];
    functionalGroups: string[];
    charge: number;
    isHighlighted: boolean;
}

/**
 * Context for an atom
 */
export interface AtomContext {
    id: SimEntityId;
    element: string;
    formalCharge: number;
    hybridization?: string;
    bondedTo: string[];
    lonePairs?: number;
    isReactiveCenter: boolean;
}

/**
 * Bond change event
 */
export interface BondChange {
    type: 'forming' | 'breaking';
    atom1: string;
    atom2: string;
    bondOrder: number;
    electronFlow?: 'heterolytic' | 'homolytic';
}

/**
 * Energy profile context
 */
export interface EnergyContext {
    activationEnergy?: number;
    deltaH?: number;
    isExothermic: boolean;
    currentEnergy: number;
    transitionStateReached: boolean;
}

/**
 * Stereochemistry context
 */
export interface StereochemistryContext {
    inversionOccurring: boolean;
    racemization: boolean;
    chiralCenters: string[];
    configuration?: 'R' | 'S' | 'racemic';
}

// =============================================================================
// Chemistry Tutor Context Builder
// =============================================================================

/**
 * Build chemistry-specific tutor context from simulation state
 *
 * @doc.type class
 * @doc.purpose Generate chemistry context for AI tutor
 * @doc.layer product
 * @doc.pattern Builder
 */
export class ChemistryTutorContextBuilder {
    private manifest: SimulationManifest;
    private currentStep: SimulationStep | null = null;
    private currentKeyframe: SimKeyframe | null = null;
    private previousKeyframe: SimKeyframe | null = null;

    constructor(manifest: SimulationManifest) {
        this.manifest = manifest;
    }

    /**
     * Update the current simulation state
     */
    setCurrentState(
        step: SimulationStep,
        keyframe: SimKeyframe,
        previousKeyframe?: SimKeyframe
    ): this {
        this.currentStep = step;
        this.currentKeyframe = keyframe;
        this.previousKeyframe = previousKeyframe || null;
        return this;
    }

    /**
     * Build the complete tutor context
     */
    build(): ChemistryTutorContext {
        const reactionType = this.extractReactionType();
        const mechanism = this.extractMechanism();
        const phase = this.determinePhase();
        const molecules = this.extractMolecules();
        const bondChanges = this.detectBondChanges();
        const energyState = this.extractEnergyState();
        const stereochemistry = this.extractStereochemistry();
        const relevantConcepts = this.identifyRelevantConcepts(reactionType, mechanism, phase);
        const misconceptions = this.identifyMisconceptions(reactionType, mechanism);
        const suggestedQuestions = this.generateQuestions(phase, bondChanges);

        return {
            reactionType,
            mechanism,
            phase,
            molecules,
            bondChanges,
            energyState,
            stereochemistry,
            relevantConcepts,
            misconceptions,
            suggestedQuestions,
        };
    }

    /**
     * Extract reaction type from manifest metadata
     */
    private extractReactionType(): string {
        const metadata = this.manifest.domainMetadata as any;
        const chemistry = metadata?.chemistry;
        if (chemistry && 'reactionType' in chemistry) {
            return chemistry.reactionType as string;
        }
        return 'unknown';
    }

    /**
     * Extract mechanism from manifest metadata
     */
    private extractMechanism(): string | undefined {
        const metadata = this.manifest.domainMetadata as any;
        const chemistry = metadata?.chemistry;
        if (chemistry && 'mechanism' in chemistry) {
            return chemistry.mechanism as string;
        }
        return undefined;
    }

    /**
     * Determine the current phase of the reaction
     */
    private determinePhase(): ChemistryPhase {
        if (!this.currentStep) return 'setup';

        const stepTitle = this.currentStep.title?.toLowerCase() || '';
        const stepDesc = this.currentStep.description?.toLowerCase() || '';

        if (stepTitle.includes('setup') || stepTitle.includes('initial')) {
            return 'setup';
        }
        if (stepTitle.includes('approach') || stepTitle.includes('attack')) {
            return 'reactant_approach';
        }
        if (stepTitle.includes('transition') || stepDesc.includes('transition state')) {
            return 'transition_state';
        }
        if (stepTitle.includes('breaking') || stepDesc.includes('bond break')) {
            return 'bond_breaking';
        }
        if (stepTitle.includes('forming') || stepDesc.includes('bond form')) {
            return 'bond_forming';
        }
        if (stepTitle.includes('product') || stepTitle.includes('complete')) {
            return 'product_formation';
        }
        if (stepTitle.includes('equilibrium')) {
            return 'equilibrium';
        }

        // Infer from step position
        const totalSteps = this.manifest.steps.length;
        const currentIndex = this.currentStep.orderIndex;
        const progress = currentIndex / totalSteps;

        if (progress < 0.2) return 'setup';
        if (progress < 0.4) return 'reactant_approach';
        if (progress < 0.6) return 'transition_state';
        if (progress < 0.8) return 'bond_forming';
        return 'product_formation';
    }

    /**
     * Extract molecule information from current entities
     */
    private extractMolecules(): MoleculeContext[] {
        if (!this.currentKeyframe) return [];

        const molecules: MoleculeContext[] = [];
        const atomsByMolecule = new Map<string, SimEntity[]>();

        // Group atoms by molecule
        for (const entity of this.currentKeyframe.entities) {
            if (entity.type === 'atom') {
                const moleculeId = (entity as any).moleculeId || 'standalone';
                const atoms = atomsByMolecule.get(moleculeId) || [];
                atoms.push(entity);
                atomsByMolecule.set(moleculeId, atoms);
            }

            if (entity.type === 'molecule') {
                molecules.push({
                    id: entity.id,
                    name: (entity as any).name || entity.label || 'Unknown',
                    formula: (entity as any).formula || '',
                    role: this.determineRole(entity),
                    atoms: [],
                    functionalGroups: (entity as any).functionalGroups || [],
                    charge: (entity as any).charge || 0,
                    isHighlighted: (entity as any).highlighted || false,
                });
            }
        }

        return molecules;
    }

    /**
     * Determine the role of a molecule in the reaction
     */
    private determineRole(entity: SimEntity): MoleculeContext['role'] {
        const label = entity.label?.toLowerCase() || '';
        const data = (entity as any).data || {};

        if (label.includes('catalyst')) return 'catalyst';
        if (label.includes('intermediate')) return 'intermediate';
        if ((data as any).role) return (data as any).role;

        // Infer from step
        if (!this.currentStep) return 'reactant';

        const stepIndex = this.currentStep.orderIndex;
        const totalSteps = this.manifest.steps.length;

        if (stepIndex < totalSteps * 0.3) return 'reactant';
        if (stepIndex > totalSteps * 0.7) return 'product';
        return 'intermediate';
    }

    /**
     * Detect bond changes between frames
     */
    private detectBondChanges(): BondChange[] {
        if (!this.currentKeyframe || !this.previousKeyframe) return [];

        const changes: BondChange[] = [];
        const currentBonds = new Set<string>();
        const previousBonds = new Set<string>();

        // Collect current bonds
        for (const entity of this.currentKeyframe.entities) {
            if (entity.type === 'bond') {
                const bond = entity as any;
                currentBonds.add(`${bond.atom1Id}-${bond.atom2Id}`);
            }
        }

        // Collect previous bonds
        for (const entity of this.previousKeyframe.entities) {
            if (entity.type === 'bond') {
                const bond = entity as any;
                previousBonds.add(`${bond.atom1Id}-${bond.atom2Id}`);
            }
        }

        // Find new bonds (forming)
        for (const bondKey of currentBonds) {
            if (!previousBonds.has(bondKey)) {
                const [atom1, atom2] = bondKey.split('-');
                changes.push({
                    type: 'forming',
                    atom1,
                    atom2,
                    bondOrder: 1,
                });
            }
        }

        // Find removed bonds (breaking)
        for (const bondKey of previousBonds) {
            if (!currentBonds.has(bondKey)) {
                const [atom1, atom2] = bondKey.split('-');
                changes.push({
                    type: 'breaking',
                    atom1,
                    atom2,
                    bondOrder: 1,
                });
            }
        }

        return changes;
    }

    /**
     * Extract energy state from current context
     */
    private extractEnergyState(): EnergyContext | undefined {
        if (!this.currentKeyframe) return undefined;

        const energyProfile = this.currentKeyframe.entities.find(
            (e) => e.type === 'energyProfile'
        );

        if (!energyProfile) return undefined;

        const data = (energyProfile as any).data || {};
        return {
            activationEnergy: (data as any).activationEnergy,
            deltaH: (data as any).deltaH,
            isExothermic: ((data as any).deltaH || 0) < 0,
            currentEnergy: (data as any).currentEnergy || 0,
            transitionStateReached: this.determinePhase() === 'transition_state',
        };
    }

    /**
     * Extract stereochemistry information
     */
    private extractStereochemistry(): StereochemistryContext | undefined {
        const mechanism = this.extractMechanism();

        if (mechanism === 'SN2') {
            return {
                inversionOccurring: this.determinePhase() === 'transition_state',
                racemization: false,
                chiralCenters: [],
                configuration: undefined,
            };
        }

        if (mechanism === 'SN1') {
            return {
                inversionOccurring: false,
                racemization: true,
                chiralCenters: [],
                configuration: 'racemic',
            };
        }

        return undefined;
    }

    /**
     * Identify relevant concepts for the current state
     */
    private identifyRelevantConcepts(
        reactionType: string,
        mechanism: string | undefined,
        phase: ChemistryPhase
    ): string[] {
        const concepts: string[] = [];

        // Reaction-type concepts
        if (reactionType === 'substitution') {
            concepts.push('nucleophilic substitution', 'leaving group', 'nucleophile');
        }
        if (reactionType === 'addition') {
            concepts.push('addition reaction', 'electrophilic addition');
        }
        if (reactionType === 'elimination') {
            concepts.push('elimination reaction', 'Zaitsev rule');
        }
        if (reactionType === 'combustion') {
            concepts.push('oxidation', 'exothermic reaction', 'balancing equations');
        }
        if (reactionType === 'condensation') {
            concepts.push('condensation reaction', 'dehydration synthesis', 'peptide bond');
        }

        // Mechanism concepts
        if (mechanism === 'SN2') {
            concepts.push('backside attack', 'Walden inversion', 'concerted mechanism');
        }
        if (mechanism === 'SN1') {
            concepts.push('carbocation intermediate', 'racemization', 'rate-determining step');
        }

        // Phase-specific concepts
        if (phase === 'transition_state') {
            concepts.push('transition state theory', 'activation energy', 'reaction coordinate');
        }
        if (phase === 'bond_forming' || phase === 'bond_breaking') {
            concepts.push('bond energy', 'electron pair movement');
        }

        return concepts;
    }

    /**
     * Identify common misconceptions to address
     */
    private identifyMisconceptions(
        reactionType: string,
        mechanism: string | undefined
    ): string[] {
        const misconceptions: string[] = [];

        if (mechanism === 'SN2') {
            misconceptions.push(
                'Students often confuse SN2 with SN1 - remember SN2 is one step!',
                'The nucleophile attacks from the BACKSIDE, not the front',
                'Inversion always occurs - the configuration flips'
            );
        }

        if (mechanism === 'SN1') {
            misconceptions.push(
                'The carbocation is NOT a transition state - it is an intermediate',
                'Racemization does not mean 50/50 - solvent effects matter'
            );
        }

        if (reactionType === 'combustion') {
            misconceptions.push(
                'Combustion requires oxygen - it cannot happen without it',
                'Complete vs incomplete combustion produce different products'
            );
        }

        return misconceptions;
    }

    /**
     * Generate suggested questions for the student
     */
    private generateQuestions(phase: ChemistryPhase, bondChanges: BondChange[]): string[] {
        const questions: string[] = [];

        switch (phase) {
            case 'setup':
                questions.push('What type of reaction is this?');
                questions.push('Can you identify the reactants and their functional groups?');
                break;
            case 'reactant_approach':
                questions.push('Where is the nucleophile attacking?');
                questions.push('Why does the nucleophile approach from this direction?');
                break;
            case 'transition_state':
                questions.push('What makes this a transition state rather than an intermediate?');
                questions.push('How does the energy compare to the reactants?');
                break;
            case 'bond_breaking':
            case 'bond_forming':
                if (bondChanges.length > 0) {
                    questions.push('Which bonds are forming/breaking in this step?');
                    questions.push('Where are the electrons going?');
                }
                break;
            case 'product_formation':
                questions.push('What are the products of this reaction?');
                questions.push('Is this reaction exothermic or endothermic?');
                break;
        }

        return questions;
    }
}

// =============================================================================
// Context Provider Service
// =============================================================================

/**
 * Chemistry tutor context provider service
 *
 * @doc.type class
 * @doc.purpose Provide chemistry context to AI tutor service
 * @doc.layer product
 * @doc.pattern Service
 */
export class ChemistryTutorContextService {
    private contextBuilders: Map<string, ChemistryTutorContextBuilder> = new Map();

    /**
     * Initialize context builder for a simulation
     */
    initializeContext(simulationId: string, manifest: SimulationManifest): void {
        if (manifest.domain !== 'CHEMISTRY') {
            throw new Error('Manifest domain must be CHEMISTRY');
        }
        this.contextBuilders.set(simulationId, new ChemistryTutorContextBuilder(manifest));
    }

    /**
     * Update context with current simulation state
     */
    updateContext(
        simulationId: string,
        step: SimulationStep,
        keyframe: SimKeyframe,
        previousKeyframe?: SimKeyframe
    ): ChemistryTutorContext {
        const builder = this.contextBuilders.get(simulationId);
        if (!builder) {
            throw new Error(`No context builder found for simulation ${simulationId}`);
        }

        return builder.setCurrentState(step, keyframe, previousKeyframe).build();
    }

    /**
     * Get formatted context for AI tutor prompt
     */
    getFormattedPrompt(context: ChemistryTutorContext): string {
        const lines: string[] = [
            '## Current Chemistry Context',
            '',
            `**Reaction Type:** ${context.reactionType}`,
        ];

        if (context.mechanism) {
            lines.push(`**Mechanism:** ${context.mechanism}`);
        }

        lines.push(`**Phase:** ${context.phase.replace('_', ' ')}`);
        lines.push('');

        if (context.bondChanges.length > 0) {
            lines.push('**Bond Changes:**');
            for (const change of context.bondChanges) {
                lines.push(`- ${change.type}: ${change.atom1}–${change.atom2}`);
            }
            lines.push('');
        }

        if (context.stereochemistry) {
            lines.push('**Stereochemistry:**');
            if (context.stereochemistry.inversionOccurring) {
                lines.push('- Inversion is occurring');
            }
            if (context.stereochemistry.racemization) {
                lines.push('- Racemization expected');
            }
            lines.push('');
        }

        if (context.relevantConcepts.length > 0) {
            lines.push('**Key Concepts:**');
            for (const concept of context.relevantConcepts) {
                lines.push(`- ${concept}`);
            }
            lines.push('');
        }

        if (context.suggestedQuestions.length > 0) {
            lines.push('**Consider asking:**');
            for (const q of context.suggestedQuestions) {
                lines.push(`- ${q}`);
            }
        }

        return lines.join('\n');
    }

    /**
     * Clean up context builder for a simulation
     */
    disposeContext(simulationId: string): void {
        this.contextBuilders.delete(simulationId);
    }
}

// =============================================================================
// Exports
// =============================================================================

/**
 * Create a new chemistry tutor context service
 */
export function createChemistryTutorContextService(): ChemistryTutorContextService {
    return new ChemistryTutorContextService();
}
