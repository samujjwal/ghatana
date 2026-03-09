/**
 * Reaction Wizard Component
 *
 * Step-by-step wizard for creating chemical reaction simulations.
 * Guides educators through defining reactants, products, mechanism steps,
 * and energy profiles.
 *
 * @doc.type component
 * @doc.purpose Chemistry reaction authoring wizard
 * @doc.layer product
 * @doc.pattern Wizard
 */

import { useState, useCallback } from "react";
import { MoleculeDrawer } from "./MoleculeDrawer";

// =============================================================================
// Types
// =============================================================================

/** Reaction type classification */
type ReactionType =
    | "substitution-sn1"
    | "substitution-sn2"
    | "elimination-e1"
    | "elimination-e2"
    | "addition"
    | "oxidation"
    | "reduction"
    | "acid-base"
    | "condensation"
    | "hydrolysis"
    | "other";

/** Molecule in a reaction */
interface ReactionMolecule {
    id: string;
    name: string;
    smiles?: string;
    role: "reactant" | "product" | "catalyst" | "intermediate" | "transition-state";
    coefficient?: number;
}

/** Mechanism step */
interface MechanismStep {
    id: string;
    stepNumber: number;
    description: string;
    electronFlow?: string; // Description of electron movement
    energyChange?: "uphill" | "downhill" | "neutral";
    intermediateId?: string;
}

/** Energy profile point */
interface EnergyPoint {
    label: string;
    relativeEnergy: number; // kJ/mol relative to reactants
    species: string[];
}

/** Complete reaction definition */
interface ReactionDefinition {
    reactionType: ReactionType;
    name: string;
    description: string;
    molecules: ReactionMolecule[];
    mechanismSteps: MechanismStep[];
    energyProfile: EnergyPoint[];
    conditions?: {
        temperature?: string;
        solvent?: string;
        catalyst?: string;
        notes?: string;
    };
}

/** Wizard step */
type WizardStep = "type" | "molecules" | "mechanism" | "energy" | "review";

interface ReactionWizardProps {
    initialReaction?: Partial<ReactionDefinition>;
    onComplete: (reaction: ReactionDefinition) => void;
    onCancel: () => void;
}

// =============================================================================
// Constants
// =============================================================================

const REACTION_TYPES: Array<{ value: ReactionType; label: string; description: string }> = [
    { value: "substitution-sn2", label: "SN2 Substitution", description: "Bimolecular nucleophilic substitution" },
    { value: "substitution-sn1", label: "SN1 Substitution", description: "Unimolecular nucleophilic substitution" },
    { value: "elimination-e2", label: "E2 Elimination", description: "Bimolecular elimination" },
    { value: "elimination-e1", label: "E1 Elimination", description: "Unimolecular elimination" },
    { value: "addition", label: "Addition", description: "Addition to unsaturated systems" },
    { value: "oxidation", label: "Oxidation", description: "Electron loss / O gain" },
    { value: "reduction", label: "Reduction", description: "Electron gain / H gain" },
    { value: "acid-base", label: "Acid-Base", description: "Proton transfer reactions" },
    { value: "condensation", label: "Condensation", description: "Bond formation with small molecule loss" },
    { value: "hydrolysis", label: "Hydrolysis", description: "Bond cleavage with water" },
    { value: "other", label: "Other", description: "Custom reaction type" },
];

const WIZARD_STEPS: Array<{ id: WizardStep; label: string }> = [
    { id: "type", label: "Reaction Type" },
    { id: "molecules", label: "Molecules" },
    { id: "mechanism", label: "Mechanism" },
    { id: "energy", label: "Energy Profile" },
    { id: "review", label: "Review" },
];

// =============================================================================
// Utility
// =============================================================================

function generateId(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

// =============================================================================
// Sub-Components
// =============================================================================

interface StepIndicatorProps {
    steps: Array<{ id: WizardStep; label: string }>;
    currentStep: WizardStep;
    onStepClick?: (step: WizardStep) => void;
}

function StepIndicator({ steps, currentStep, onStepClick }: StepIndicatorProps) {
    const currentIndex = steps.findIndex((s) => s.id === currentStep);

    return (
        <div className="flex items-center justify-between mb-8">
            {steps.map((step, index) => {
                const isCompleted = index < currentIndex;
                const isCurrent = step.id === currentStep;

                return (
                    <div key={step.id} className="flex items-center flex-1">
                        <button
                            onClick={() => onStepClick?.(step.id)}
                            disabled={!onStepClick || index > currentIndex}
                            className={`
                                flex items-center justify-center w-8 h-8 rounded-full
                                text-sm font-medium transition-colors
                                ${isCompleted
                                    ? "bg-green-500 text-white"
                                    : isCurrent
                                        ? "bg-blue-600 text-white"
                                        : "bg-gray-200 text-gray-500"
                                }
                                ${onStepClick && index <= currentIndex ? "cursor-pointer" : "cursor-default"}
                            `}
                        >
                            {isCompleted ? "✓" : index + 1}
                        </button>
                        <span
                            className={`
                                ml-2 text-sm font-medium
                                ${isCurrent ? "text-blue-600" : "text-gray-500"}
                            `}
                        >
                            {step.label}
                        </span>
                        {index < steps.length - 1 && (
                            <div
                                className={`
                                    flex-1 h-0.5 mx-4
                                    ${isCompleted ? "bg-green-500" : "bg-gray-200"}
                                `}
                            />
                        )}
                    </div>
                );
            })}
        </div>
    );
}

// =============================================================================
// Main Component
// =============================================================================

export function ReactionWizard({
    initialReaction,
    onComplete,
    onCancel,
}: ReactionWizardProps) {
    const [step, setStep] = useState<WizardStep>("type");
    const [reaction, setReaction] = useState<Partial<ReactionDefinition>>(
        initialReaction ?? {
            reactionType: "substitution-sn2",
            name: "",
            description: "",
            molecules: [],
            mechanismSteps: [],
            energyProfile: [],
            conditions: {},
        }
    );
    const [editingMoleculeId, setEditingMoleculeId] = useState<string | null>(null);

    // Navigation
    const goNext = useCallback(() => {
        const currentIndex = WIZARD_STEPS.findIndex((s) => s.id === step);
        if (currentIndex < WIZARD_STEPS.length - 1) {
            setStep(WIZARD_STEPS[currentIndex + 1].id);
        }
    }, [step]);

    const goPrev = useCallback(() => {
        const currentIndex = WIZARD_STEPS.findIndex((s) => s.id === step);
        if (currentIndex > 0) {
            setStep(WIZARD_STEPS[currentIndex - 1].id);
        }
    }, [step]);

    // Molecule management
    const addMolecule = useCallback((role: ReactionMolecule["role"]) => {
        const newMolecule: ReactionMolecule = {
            id: generateId("mol"),
            name: "",
            role,
            coefficient: 1,
        };
        setReaction((prev) => ({
            ...prev,
            molecules: [...(prev.molecules ?? []), newMolecule],
        }));
        setEditingMoleculeId(newMolecule.id);
    }, []);

    const updateMolecule = useCallback((id: string, updates: Partial<ReactionMolecule>) => {
        setReaction((prev) => ({
            ...prev,
            molecules: (prev.molecules ?? []).map((m) =>
                m.id === id ? { ...m, ...updates } : m
            ),
        }));
    }, []);

    const removeMolecule = useCallback((id: string) => {
        setReaction((prev) => ({
            ...prev,
            molecules: (prev.molecules ?? []).filter((m) => m.id !== id),
        }));
    }, []);

    // Mechanism step management
    const addMechanismStep = useCallback(() => {
        const steps = reaction.mechanismSteps ?? [];
        const newStep: MechanismStep = {
            id: generateId("step"),
            stepNumber: steps.length + 1,
            description: "",
            energyChange: "neutral",
        };
        setReaction((prev) => ({
            ...prev,
            mechanismSteps: [...steps, newStep],
        }));
    }, [reaction.mechanismSteps]);

    const updateMechanismStep = useCallback((id: string, updates: Partial<MechanismStep>) => {
        setReaction((prev) => ({
            ...prev,
            mechanismSteps: (prev.mechanismSteps ?? []).map((s) =>
                s.id === id ? { ...s, ...updates } : s
            ),
        }));
    }, []);

    const removeMechanismStep = useCallback((id: string) => {
        setReaction((prev) => ({
            ...prev,
            mechanismSteps: (prev.mechanismSteps ?? [])
                .filter((s) => s.id !== id)
                .map((s, i) => ({ ...s, stepNumber: i + 1 })),
        }));
    }, []);

    // Energy profile management
    const addEnergyPoint = useCallback(() => {
        const points = reaction.energyProfile ?? [];
        const newPoint: EnergyPoint = {
            label: `Point ${points.length + 1}`,
            relativeEnergy: 0,
            species: [],
        };
        setReaction((prev) => ({
            ...prev,
            energyProfile: [...points, newPoint],
        }));
    }, [reaction.energyProfile]);

    const updateEnergyPoint = useCallback((index: number, updates: Partial<EnergyPoint>) => {
        setReaction((prev) => ({
            ...prev,
            energyProfile: (prev.energyProfile ?? []).map((p, i) =>
                i === index ? { ...p, ...updates } : p
            ),
        }));
    }, []);

    const removeEnergyPoint = useCallback((index: number) => {
        setReaction((prev) => ({
            ...prev,
            energyProfile: (prev.energyProfile ?? []).filter((_, i) => i !== index),
        }));
    }, []);

    // Complete handler
    const handleComplete = useCallback(() => {
        if (!reaction.name || !reaction.reactionType) {
            alert("Please fill in all required fields");
            return;
        }
        onComplete(reaction as ReactionDefinition);
    }, [reaction, onComplete]);

    // Validation
    const canProceed = useCallback((): boolean => {
        switch (step) {
            case "type":
                return !!reaction.reactionType && !!reaction.name;
            case "molecules":
                return (reaction.molecules ?? []).length >= 2;
            case "mechanism":
                return (reaction.mechanismSteps ?? []).length >= 1;
            case "energy":
                return (reaction.energyProfile ?? []).length >= 2;
            case "review":
                return true;
            default:
                return false;
        }
    }, [step, reaction]);

    // Render step content
    const renderStepContent = () => {
        switch (step) {
            case "type":
                return (
                    <div className="space-y-6">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Reaction Name *
                            </label>
                            <input
                                type="text"
                                value={reaction.name ?? ""}
                                onChange={(e) =>
                                    setReaction((prev) => ({ ...prev, name: e.target.value }))
                                }
                                placeholder="e.g., SN2 of Bromomethane with Hydroxide"
                                className="w-full px-3 py-2 border rounded-md"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Reaction Type *
                            </label>
                            <div className="grid grid-cols-2 gap-3">
                                {REACTION_TYPES.map((type) => (
                                    <button
                                        key={type.value}
                                        onClick={() =>
                                            setReaction((prev) => ({
                                                ...prev,
                                                reactionType: type.value,
                                            }))
                                        }
                                        className={`
                                            p-3 text-left border rounded-lg transition-colors
                                            ${reaction.reactionType === type.value
                                                ? "border-blue-500 bg-blue-50"
                                                : "border-gray-200 hover:border-gray-300"
                                            }
                                        `}
                                    >
                                        <div className="font-medium text-sm">{type.label}</div>
                                        <div className="text-xs text-gray-500">{type.description}</div>
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Description
                            </label>
                            <textarea
                                value={reaction.description ?? ""}
                                onChange={(e) =>
                                    setReaction((prev) => ({ ...prev, description: e.target.value }))
                                }
                                rows={3}
                                placeholder="Brief description of the reaction..."
                                className="w-full px-3 py-2 border rounded-md"
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Temperature
                                </label>
                                <input
                                    type="text"
                                    value={reaction.conditions?.temperature ?? ""}
                                    onChange={(e) =>
                                        setReaction((prev) => ({
                                            ...prev,
                                            conditions: {
                                                ...prev.conditions,
                                                temperature: e.target.value,
                                            },
                                        }))
                                    }
                                    placeholder="e.g., Room temperature, 25°C"
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Solvent
                                </label>
                                <input
                                    type="text"
                                    value={reaction.conditions?.solvent ?? ""}
                                    onChange={(e) =>
                                        setReaction((prev) => ({
                                            ...prev,
                                            conditions: {
                                                ...prev.conditions,
                                                solvent: e.target.value,
                                            },
                                        }))
                                    }
                                    placeholder="e.g., Water, DMSO, Acetone"
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                        </div>
                    </div>
                );

            case "molecules":
                return (
                    <div className="space-y-6">
                        {/* Reactants */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-medium text-gray-900">Reactants</h3>
                                <button
                                    onClick={() => addMolecule("reactant")}
                                    className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    + Add Reactant
                                </button>
                            </div>
                            <div className="space-y-3">
                                {(reaction.molecules ?? [])
                                    .filter((m) => m.role === "reactant")
                                    .map((mol) => (
                                        <div key={mol.id} className="p-3 border rounded-lg bg-gray-50">
                                            <div className="flex items-center gap-3 mb-2">
                                                <input
                                                    type="number"
                                                    min={1}
                                                    value={mol.coefficient ?? 1}
                                                    onChange={(e) =>
                                                        updateMolecule(mol.id, {
                                                            coefficient: parseInt(e.target.value) || 1,
                                                        })
                                                    }
                                                    className="w-16 px-2 py-1 border rounded text-center"
                                                />
                                                <input
                                                    type="text"
                                                    value={mol.name}
                                                    onChange={(e) =>
                                                        updateMolecule(mol.id, { name: e.target.value })
                                                    }
                                                    placeholder="Molecule name"
                                                    className="flex-1 px-3 py-1 border rounded"
                                                />
                                                <button
                                                    onClick={() => removeMolecule(mol.id)}
                                                    className="p-1 text-red-500 hover:bg-red-50 rounded"
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                            {editingMoleculeId === mol.id && (
                                                <div className="mt-3">
                                                    <MoleculeDrawer
                                                        initialSmiles={mol.smiles}
                                                        onSmilesChange={(smiles) =>
                                                            updateMolecule(mol.id, { smiles })
                                                        }
                                                        width={400}
                                                        height={250}
                                                        showElementPicker={false}
                                                    />
                                                </div>
                                            )}
                                            <button
                                                onClick={() =>
                                                    setEditingMoleculeId(
                                                        editingMoleculeId === mol.id ? null : mol.id
                                                    )
                                                }
                                                className="mt-2 text-xs text-blue-600 hover:underline"
                                            >
                                                {editingMoleculeId === mol.id
                                                    ? "Hide drawer"
                                                    : "Draw structure"}
                                            </button>
                                        </div>
                                    ))}
                            </div>
                        </div>

                        {/* Arrow */}
                        <div className="flex items-center justify-center text-3xl text-gray-400">
                            →
                        </div>

                        {/* Products */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-medium text-gray-900">Products</h3>
                                <button
                                    onClick={() => addMolecule("product")}
                                    className="px-3 py-1.5 text-sm bg-green-600 text-white rounded-md hover:bg-green-700"
                                >
                                    + Add Product
                                </button>
                            </div>
                            <div className="space-y-3">
                                {(reaction.molecules ?? [])
                                    .filter((m) => m.role === "product")
                                    .map((mol) => (
                                        <div key={mol.id} className="p-3 border rounded-lg bg-gray-50">
                                            <div className="flex items-center gap-3 mb-2">
                                                <input
                                                    type="number"
                                                    min={1}
                                                    value={mol.coefficient ?? 1}
                                                    onChange={(e) =>
                                                        updateMolecule(mol.id, {
                                                            coefficient: parseInt(e.target.value) || 1,
                                                        })
                                                    }
                                                    className="w-16 px-2 py-1 border rounded text-center"
                                                />
                                                <input
                                                    type="text"
                                                    value={mol.name}
                                                    onChange={(e) =>
                                                        updateMolecule(mol.id, { name: e.target.value })
                                                    }
                                                    placeholder="Molecule name"
                                                    className="flex-1 px-3 py-1 border rounded"
                                                />
                                                <button
                                                    onClick={() => removeMolecule(mol.id)}
                                                    className="p-1 text-red-500 hover:bg-red-50 rounded"
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                            {editingMoleculeId === mol.id && (
                                                <div className="mt-3">
                                                    <MoleculeDrawer
                                                        initialSmiles={mol.smiles}
                                                        onSmilesChange={(smiles) =>
                                                            updateMolecule(mol.id, { smiles })
                                                        }
                                                        width={400}
                                                        height={250}
                                                        showElementPicker={false}
                                                    />
                                                </div>
                                            )}
                                            <button
                                                onClick={() =>
                                                    setEditingMoleculeId(
                                                        editingMoleculeId === mol.id ? null : mol.id
                                                    )
                                                }
                                                className="mt-2 text-xs text-blue-600 hover:underline"
                                            >
                                                {editingMoleculeId === mol.id
                                                    ? "Hide drawer"
                                                    : "Draw structure"}
                                            </button>
                                        </div>
                                    ))}
                            </div>
                        </div>

                        {/* Optional: Catalyst */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-medium text-gray-500">Catalyst (optional)</h3>
                                <button
                                    onClick={() => addMolecule("catalyst")}
                                    className="px-3 py-1.5 text-sm border border-gray-300 rounded-md hover:bg-gray-50"
                                >
                                    + Add Catalyst
                                </button>
                            </div>
                            <div className="space-y-3">
                                {(reaction.molecules ?? [])
                                    .filter((m) => m.role === "catalyst")
                                    .map((mol) => (
                                        <div key={mol.id} className="p-3 border rounded-lg bg-yellow-50">
                                            <div className="flex items-center gap-3">
                                                <input
                                                    type="text"
                                                    value={mol.name}
                                                    onChange={(e) =>
                                                        updateMolecule(mol.id, { name: e.target.value })
                                                    }
                                                    placeholder="Catalyst name"
                                                    className="flex-1 px-3 py-1 border rounded"
                                                />
                                                <button
                                                    onClick={() => removeMolecule(mol.id)}
                                                    className="p-1 text-red-500 hover:bg-red-50 rounded"
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                            </div>
                        </div>
                    </div>
                );

            case "mechanism":
                return (
                    <div className="space-y-6">
                        <div className="flex items-center justify-between">
                            <h3 className="font-medium text-gray-900">Mechanism Steps</h3>
                            <button
                                onClick={addMechanismStep}
                                className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                + Add Step
                            </button>
                        </div>

                        <div className="space-y-4">
                            {(reaction.mechanismSteps ?? []).map((mechStep) => (
                                <div key={mechStep.id} className="p-4 border rounded-lg">
                                    <div className="flex items-start gap-4">
                                        <div className="flex items-center justify-center w-8 h-8 bg-blue-100 text-blue-600 font-bold rounded-full">
                                            {mechStep.stepNumber}
                                        </div>
                                        <div className="flex-1 space-y-3">
                                            <textarea
                                                value={mechStep.description}
                                                onChange={(e) =>
                                                    updateMechanismStep(mechStep.id, {
                                                        description: e.target.value,
                                                    })
                                                }
                                                rows={2}
                                                placeholder="Describe this mechanism step..."
                                                className="w-full px-3 py-2 border rounded-md text-sm"
                                            />
                                            <div className="flex items-center gap-4">
                                                <label className="text-sm text-gray-500">Energy:</label>
                                                <select
                                                    value={mechStep.energyChange ?? "neutral"}
                                                    onChange={(e) =>
                                                        updateMechanismStep(mechStep.id, {
                                                            energyChange: e.target.value as MechanismStep["energyChange"],
                                                        })
                                                    }
                                                    className="px-2 py-1 border rounded text-sm"
                                                >
                                                    <option value="uphill">↗ Uphill (Ea)</option>
                                                    <option value="downhill">↘ Downhill</option>
                                                    <option value="neutral">→ Neutral</option>
                                                </select>
                                                <input
                                                    type="text"
                                                    value={mechStep.electronFlow ?? ""}
                                                    onChange={(e) =>
                                                        updateMechanismStep(mechStep.id, {
                                                            electronFlow: e.target.value,
                                                        })
                                                    }
                                                    placeholder="Electron flow (e.g., O → C)"
                                                    className="flex-1 px-3 py-1 border rounded text-sm"
                                                />
                                            </div>
                                        </div>
                                        <button
                                            onClick={() => removeMechanismStep(mechStep.id)}
                                            className="p-1 text-red-500 hover:bg-red-50 rounded"
                                        >
                                            ✕
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {(reaction.mechanismSteps ?? []).length === 0 && (
                            <div className="text-center py-8 text-gray-500">
                                <p>No mechanism steps defined yet.</p>
                                <p className="text-sm">Add steps to describe the reaction mechanism.</p>
                            </div>
                        )}
                    </div>
                );

            case "energy":
                return (
                    <div className="space-y-6">
                        <div className="flex items-center justify-between">
                            <h3 className="font-medium text-gray-900">Energy Profile</h3>
                            <button
                                onClick={addEnergyPoint}
                                className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                + Add Point
                            </button>
                        </div>

                        {/* Simple energy diagram preview */}
                        <div className="h-48 border rounded-lg bg-gray-50 p-4">
                            <svg width="100%" height="100%" viewBox="0 0 400 150">
                                {/* Axis */}
                                <line x1="40" y1="130" x2="380" y2="130" stroke="#ccc" strokeWidth="2" />
                                <line x1="40" y1="130" x2="40" y2="20" stroke="#ccc" strokeWidth="2" />
                                <text x="210" y="145" textAnchor="middle" fontSize="10" fill="#666">
                                    Reaction Progress
                                </text>
                                <text x="15" y="75" textAnchor="middle" fontSize="10" fill="#666" transform="rotate(-90, 15, 75)">
                                    Energy
                                </text>

                                {/* Plot points */}
                                {(reaction.energyProfile ?? []).length > 1 && (
                                    <polyline
                                        points={(reaction.energyProfile ?? [])
                                            .map((p, i, arr) => {
                                                const x = 60 + (i / (arr.length - 1)) * 300;
                                                const y = 120 - (p.relativeEnergy / 100) * 80;
                                                return `${x},${y}`;
                                            })
                                            .join(" ")}
                                        fill="none"
                                        stroke="#3b82f6"
                                        strokeWidth="2"
                                    />
                                )}
                                {(reaction.energyProfile ?? []).map((p, i, arr) => {
                                    const x = 60 + (arr.length > 1 ? (i / (arr.length - 1)) * 300 : 150);
                                    const y = 120 - (p.relativeEnergy / 100) * 80;
                                    return (
                                        <g key={i}>
                                            <circle cx={x} cy={y} r="5" fill="#3b82f6" />
                                            <text x={x} y={y - 10} textAnchor="middle" fontSize="9" fill="#333">
                                                {p.label}
                                            </text>
                                        </g>
                                    );
                                })}
                            </svg>
                        </div>

                        {/* Energy points editor */}
                        <div className="space-y-3">
                            {(reaction.energyProfile ?? []).map((point, index) => (
                                <div key={index} className="flex items-center gap-3 p-3 border rounded-lg">
                                    <input
                                        type="text"
                                        value={point.label}
                                        onChange={(e) =>
                                            updateEnergyPoint(index, { label: e.target.value })
                                        }
                                        placeholder="Label"
                                        className="w-40 px-3 py-1 border rounded"
                                    />
                                    <div className="flex items-center gap-2">
                                        <label className="text-sm text-gray-500">Energy:</label>
                                        <input
                                            type="number"
                                            value={point.relativeEnergy}
                                            onChange={(e) =>
                                                updateEnergyPoint(index, {
                                                    relativeEnergy: parseFloat(e.target.value) || 0,
                                                })
                                            }
                                            className="w-24 px-3 py-1 border rounded"
                                        />
                                        <span className="text-sm text-gray-500">kJ/mol</span>
                                    </div>
                                    <button
                                        onClick={() => removeEnergyPoint(index)}
                                        className="ml-auto p-1 text-red-500 hover:bg-red-50 rounded"
                                    >
                                        ✕
                                    </button>
                                </div>
                            ))}
                        </div>

                        <p className="text-sm text-gray-500">
                            Tip: Start with 0 kJ/mol for reactants. Add transition states with positive values,
                            and products with their relative energy (negative for exothermic).
                        </p>
                    </div>
                );

            case "review":
                return (
                    <div className="space-y-6">
                        <div className="p-4 bg-blue-50 rounded-lg">
                            <h3 className="font-medium text-blue-900">{reaction.name}</h3>
                            <p className="text-sm text-blue-700">{reaction.description}</p>
                        </div>

                        <div className="grid grid-cols-2 gap-6">
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Reaction Details</h4>
                                <dl className="space-y-1 text-sm">
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Type:</dt>
                                        <dd>{REACTION_TYPES.find((t) => t.value === reaction.reactionType)?.label}</dd>
                                    </div>
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Temp:</dt>
                                        <dd>{reaction.conditions?.temperature || "Not specified"}</dd>
                                    </div>
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Solvent:</dt>
                                        <dd>{reaction.conditions?.solvent || "Not specified"}</dd>
                                    </div>
                                </dl>
                            </div>

                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Summary</h4>
                                <dl className="space-y-1 text-sm">
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Reactants:</dt>
                                        <dd>{(reaction.molecules ?? []).filter((m) => m.role === "reactant").length}</dd>
                                    </div>
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Products:</dt>
                                        <dd>{(reaction.molecules ?? []).filter((m) => m.role === "product").length}</dd>
                                    </div>
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Mech steps:</dt>
                                        <dd>{(reaction.mechanismSteps ?? []).length}</dd>
                                    </div>
                                    <div className="flex">
                                        <dt className="w-24 text-gray-500">Energy pts:</dt>
                                        <dd>{(reaction.energyProfile ?? []).length}</dd>
                                    </div>
                                </dl>
                            </div>
                        </div>

                        {/* Validation warnings */}
                        <div className="p-4 border rounded-lg">
                            <h4 className="font-medium text-gray-700 mb-2">Validation</h4>
                            {(reaction.molecules ?? []).filter((m) => m.role === "reactant").length === 0 && (
                                <p className="text-sm text-amber-600">⚠ No reactants defined</p>
                            )}
                            {(reaction.molecules ?? []).filter((m) => m.role === "product").length === 0 && (
                                <p className="text-sm text-amber-600">⚠ No products defined</p>
                            )}
                            {(reaction.mechanismSteps ?? []).length === 0 && (
                                <p className="text-sm text-amber-600">⚠ No mechanism steps defined</p>
                            )}
                            {(reaction.energyProfile ?? []).length < 2 && (
                                <p className="text-sm text-amber-600">⚠ Energy profile needs at least 2 points</p>
                            )}
                            {(reaction.molecules ?? []).filter((m) => m.role === "reactant").length > 0 &&
                                (reaction.molecules ?? []).filter((m) => m.role === "product").length > 0 &&
                                (reaction.mechanismSteps ?? []).length > 0 && (
                                    <p className="text-sm text-green-600">✓ Reaction is ready to save</p>
                                )}
                        </div>
                    </div>
                );
        }
    };

    return (
        <div className="flex flex-col h-full bg-white">
            {/* Header */}
            <div className="px-6 py-4 border-b">
                <h2 className="text-xl font-semibold text-gray-900">Reaction Wizard</h2>
                <p className="text-sm text-gray-500">Create a chemical reaction simulation step by step</p>
            </div>

            {/* Progress */}
            <div className="px-6 py-4 border-b bg-gray-50">
                <StepIndicator
                    steps={WIZARD_STEPS}
                    currentStep={step}
                    onStepClick={(s) => {
                        const targetIndex = WIZARD_STEPS.findIndex((ws) => ws.id === s);
                        const currentIndex = WIZARD_STEPS.findIndex((ws) => ws.id === step);
                        if (targetIndex <= currentIndex) {
                            setStep(s);
                        }
                    }}
                />
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-6">{renderStepContent()}</div>

            {/* Footer */}
            <div className="flex items-center justify-between px-6 py-4 border-t bg-gray-50">
                <button
                    onClick={onCancel}
                    className="px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-md"
                >
                    Cancel
                </button>
                <div className="flex items-center gap-3">
                    {step !== "type" && (
                        <button
                            onClick={goPrev}
                            className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            Previous
                        </button>
                    )}
                    {step !== "review" ? (
                        <button
                            onClick={goNext}
                            disabled={!canProceed()}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            Next
                        </button>
                    ) : (
                        <button
                            onClick={handleComplete}
                            className="px-4 py-2 text-sm font-medium text-white bg-green-600 rounded-md hover:bg-green-700"
                        >
                            Create Reaction
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ReactionWizard;
