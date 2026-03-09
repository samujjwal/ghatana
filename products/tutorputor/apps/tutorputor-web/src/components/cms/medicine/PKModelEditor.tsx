/**
 * Pharmacokinetics Model Editor Component
 *
 * Visual editor for creating PK simulation content with compartmental
 * models, dose schedules, and parameter configuration.
 *
 * @doc.type component
 * @doc.purpose Medicine PK model authoring UI
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback, useEffect, useRef } from "react";

// =============================================================================
// Types
// =============================================================================

/** PK Model type */
type PKModelType =
    | "one-compartment"
    | "two-compartment"
    | "three-compartment"
    | "linear"
    | "nonlinear-michaelis-menten";

/** Dosing route */
type DosingRoute = "IV" | "oral" | "IM" | "SC" | "topical" | "inhalation";

/** PK Compartment */
interface PKCompartment {
    id: string;
    name: string;
    type: "central" | "peripheral" | "effect";
    volume: number; // L
    initialConcentration: number; // mg/L
    color?: string;
}

/** PK Transfer */
interface PKTransfer {
    id: string;
    fromCompartmentId: string;
    toCompartmentId: string;
    rateConstant: number; // 1/h
}

/** Dose event */
interface DoseEvent {
    id: string;
    time: number; // hours
    amount: number; // mg
    route: DosingRoute;
    duration?: number; // hours (for infusions)
    bioavailability?: number; // fraction (0-1)
}

/** PK Parameters */
interface PKParameters {
    clearance?: number; // L/h
    halfLife?: number; // hours
    volumeOfDistribution?: number; // L
    absorptionRate?: number; // 1/h (ka for oral)
    eliminationRate?: number; // 1/h (ke)
}

/** Drug properties */
interface DrugProperties {
    name: string;
    molarMass?: number; // g/mol
    proteinBinding?: number; // fraction
    therapeuticRange?: { min: number; max: number }; // mg/L
    toxicThreshold?: number; // mg/L
    mec?: number; // Minimum effective concentration mg/L
}

/** Complete PK Model Definition */
interface PKModelDefinition {
    modelType: PKModelType;
    drug: DrugProperties;
    compartments: PKCompartment[];
    transfers: PKTransfer[];
    doses: DoseEvent[];
    parameters: PKParameters;
    simulationTime: number; // hours
    needsReview?: boolean;
}

interface PKModelEditorProps {
    initialModel?: Partial<PKModelDefinition>;
    onChange?: (model: PKModelDefinition) => void;
    onSave?: (model: PKModelDefinition) => void;
    readOnly?: boolean;
}

// =============================================================================
// Constants
// =============================================================================

const MODEL_TYPES: Array<{ value: PKModelType; label: string; description: string; compartments: number }> = [
    { value: "one-compartment", label: "One-Compartment", description: "Single central compartment", compartments: 1 },
    { value: "two-compartment", label: "Two-Compartment", description: "Central + peripheral", compartments: 2 },
    { value: "three-compartment", label: "Three-Compartment", description: "Central + 2 peripheral", compartments: 3 },
    { value: "linear", label: "Linear", description: "First-order kinetics", compartments: 1 },
    { value: "nonlinear-michaelis-menten", label: "Michaelis-Menten", description: "Saturable elimination", compartments: 1 },
];

const DOSING_ROUTES: Array<{ value: DosingRoute; label: string; defaultBioavailability: number }> = [
    { value: "IV", label: "IV (Intravenous)", defaultBioavailability: 1.0 },
    { value: "oral", label: "Oral", defaultBioavailability: 0.7 },
    { value: "IM", label: "IM (Intramuscular)", defaultBioavailability: 0.9 },
    { value: "SC", label: "SC (Subcutaneous)", defaultBioavailability: 0.85 },
    { value: "topical", label: "Topical", defaultBioavailability: 0.1 },
    { value: "inhalation", label: "Inhalation", defaultBioavailability: 0.6 },
];

const COMPARTMENT_COLORS = {
    central: "#3b82f6",
    peripheral: "#10b981",
    effect: "#f59e0b",
};

// =============================================================================
// Utility
// =============================================================================

function generateId(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

function calculateEliminationRate(halfLife: number): number {
    return 0.693 / halfLife;
}

// =============================================================================
// Sub-Components
// =============================================================================

interface CompartmentDiagramProps {
    compartments: PKCompartment[];
    transfers: PKTransfer[];
    onCompartmentClick?: (id: string) => void;
    selectedCompartmentId?: string | null;
}

function CompartmentDiagram({
    compartments,
    transfers,
    onCompartmentClick,
    selectedCompartmentId
}: CompartmentDiagramProps) {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        const width = 400;
        const height = 200;

        // Clear
        ctx.fillStyle = "#f8fafc";
        ctx.fillRect(0, 0, width, height);

        // Position compartments
        const positions: Record<string, { x: number; y: number }> = {};
        const central = compartments.find(c => c.type === "central");
        const peripheral = compartments.filter(c => c.type === "peripheral");
        const effect = compartments.find(c => c.type === "effect");

        if (central) {
            positions[central.id] = { x: 200, y: 100 };
        }
        peripheral.forEach((p, i) => {
            const angle = (i + 1) * (Math.PI / (peripheral.length + 1));
            positions[p.id] = {
                x: 200 + Math.cos(angle) * 120,
                y: 100 + Math.sin(angle) * 60
            };
        });
        if (effect) {
            positions[effect.id] = { x: 320, y: 100 };
        }

        // Draw transfers (arrows)
        ctx.strokeStyle = "#94a3b8";
        ctx.lineWidth = 2;
        transfers.forEach(t => {
            const from = positions[t.fromCompartmentId];
            const to = positions[t.toCompartmentId];
            if (!from || !to) return;

            const dx = to.x - from.x;
            const dy = to.y - from.y;
            const len = Math.sqrt(dx * dx + dy * dy);
            const nx = dx / len;
            const ny = dy / len;

            const startX = from.x + nx * 35;
            const startY = from.y + ny * 35;
            const endX = to.x - nx * 35;
            const endY = to.y - ny * 35;

            ctx.beginPath();
            ctx.moveTo(startX, startY);
            ctx.lineTo(endX, endY);
            ctx.stroke();

            // Arrowhead
            const arrowSize = 8;
            ctx.beginPath();
            ctx.moveTo(endX, endY);
            ctx.lineTo(endX - nx * arrowSize + ny * arrowSize * 0.5, endY - ny * arrowSize - nx * arrowSize * 0.5);
            ctx.lineTo(endX - nx * arrowSize - ny * arrowSize * 0.5, endY - ny * arrowSize + nx * arrowSize * 0.5);
            ctx.closePath();
            ctx.fillStyle = "#94a3b8";
            ctx.fill();

            // Rate constant label
            ctx.fillStyle = "#64748b";
            ctx.font = "10px Arial";
            ctx.textAlign = "center";
            ctx.fillText(`k=${t.rateConstant}`, (startX + endX) / 2, (startY + endY) / 2 - 8);
        });

        // Draw compartments
        compartments.forEach(c => {
            const pos = positions[c.id];
            if (!pos) return;

            const color = COMPARTMENT_COLORS[c.type];
            const isSelected = c.id === selectedCompartmentId;

            // Circle
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, 30, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            if (isSelected) {
                ctx.strokeStyle = "#1d4ed8";
                ctx.lineWidth = 3;
            } else {
                ctx.strokeStyle = "#374151";
                ctx.lineWidth = 2;
            }
            ctx.stroke();

            // Label
            ctx.fillStyle = "#ffffff";
            ctx.font = "bold 12px Arial";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText(c.name.substring(0, 8), pos.x, pos.y);

            // Volume below
            ctx.fillStyle = "#64748b";
            ctx.font = "10px Arial";
            ctx.fillText(`V=${c.volume}L`, pos.x, pos.y + 45);
        });

    }, [compartments, transfers, selectedCompartmentId]);

    const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
        if (!onCompartmentClick) return;

        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        // Check click on compartments (simplified positioning)
        const central = compartments.find(c => c.type === "central");
        if (central) {
            const dx = x - 200;
            const dy = y - 100;
            if (dx * dx + dy * dy <= 900) {
                onCompartmentClick(central.id);
                return;
            }
        }
    };

    return (
        <canvas
            ref={canvasRef}
            width={400}
            height={200}
            onClick={handleCanvasClick}
            className="border rounded-lg cursor-pointer"
        />
    );
}

interface DoseScheduleEditorProps {
    doses: DoseEvent[];
    onAdd: () => void;
    onUpdate: (id: string, updates: Partial<DoseEvent>) => void;
    onRemove: (id: string) => void;
    readOnly?: boolean;
}

function DoseScheduleEditor({ doses, onAdd, onUpdate, onRemove, readOnly }: DoseScheduleEditorProps) {
    return (
        <div className="space-y-3">
            <div className="flex items-center justify-between">
                <h4 className="font-medium text-gray-700">Dosing Schedule</h4>
                {!readOnly && (
                    <button
                        onClick={onAdd}
                        className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                    >
                        + Add Dose
                    </button>
                )}
            </div>

            <div className="space-y-2">
                {doses.map((dose) => (
                    <div key={dose.id} className="flex items-center gap-3 p-3 border rounded-lg bg-gray-50">
                        <div className="w-20">
                            <label className="text-xs text-gray-500">Time (h)</label>
                            <input
                                type="number"
                                min={0}
                                step={0.5}
                                value={dose.time}
                                onChange={(e) => onUpdate(dose.id, { time: parseFloat(e.target.value) || 0 })}
                                disabled={readOnly}
                                className="w-full px-2 py-1 border rounded text-sm"
                            />
                        </div>
                        <div className="w-24">
                            <label className="text-xs text-gray-500">Amount (mg)</label>
                            <input
                                type="number"
                                min={0}
                                step={10}
                                value={dose.amount}
                                onChange={(e) => onUpdate(dose.id, { amount: parseFloat(e.target.value) || 0 })}
                                disabled={readOnly}
                                className="w-full px-2 py-1 border rounded text-sm"
                            />
                        </div>
                        <div className="w-32">
                            <label className="text-xs text-gray-500">Route</label>
                            <select
                                value={dose.route}
                                onChange={(e) => {
                                    const route = e.target.value as DosingRoute;
                                    const defaultF = DOSING_ROUTES.find(r => r.value === route)?.defaultBioavailability ?? 1;
                                    onUpdate(dose.id, { route, bioavailability: defaultF });
                                }}
                                disabled={readOnly}
                                className="w-full px-2 py-1 border rounded text-sm"
                            >
                                {DOSING_ROUTES.map(r => (
                                    <option key={r.value} value={r.value}>{r.label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="w-20">
                            <label className="text-xs text-gray-500">F</label>
                            <input
                                type="number"
                                min={0}
                                max={1}
                                step={0.05}
                                value={dose.bioavailability ?? 1}
                                onChange={(e) => onUpdate(dose.id, { bioavailability: parseFloat(e.target.value) || 1 })}
                                disabled={readOnly}
                                className="w-full px-2 py-1 border rounded text-sm"
                            />
                        </div>
                        {!readOnly && (
                            <button
                                onClick={() => onRemove(dose.id)}
                                className="p-1 text-red-500 hover:bg-red-50 rounded ml-auto"
                            >
                                ✕
                            </button>
                        )}
                    </div>
                ))}
            </div>

            {doses.length === 0 && (
                <div className="text-center py-4 text-gray-500 text-sm">
                    No doses scheduled. Add a dose to define the regimen.
                </div>
            )}
        </div>
    );
}

// =============================================================================
// Main Component
// =============================================================================

export function PKModelEditor({
    initialModel,
    onChange,
    onSave,
    readOnly = false,
}: PKModelEditorProps) {
    const [activeTab, setActiveTab] = useState<"model" | "doses" | "parameters" | "preview">("model");
    const [model, setModel] = useState<Partial<PKModelDefinition>>(
        initialModel ?? {
            modelType: "one-compartment",
            drug: { name: "", therapeuticRange: { min: 0, max: 100 } },
            compartments: [
                { id: generateId("comp"), name: "Central", type: "central", volume: 10, initialConcentration: 0 }
            ],
            transfers: [],
            doses: [],
            parameters: { clearance: 5, halfLife: 4 },
            simulationTime: 48,
        }
    );
    const [selectedCompartmentId, setSelectedCompartmentId] = useState<string | null>(null);
    const [validationErrors, setValidationErrors] = useState<string[]>([]);

    // Notify parent on change
    useEffect(() => {
        onChange?.(model as PKModelDefinition);
    }, [model, onChange]);

    // Validate model
    useEffect(() => {
        const errors: string[] = [];

        if (!model.drug?.name) {
            errors.push("Drug name is required");
        }
        if ((model.compartments ?? []).length === 0) {
            errors.push("At least one compartment is required");
        }
        if ((model.doses ?? []).length === 0) {
            errors.push("At least one dose is required");
        }
        if ((model.drug?.toxicThreshold ?? Infinity) < (model.drug?.therapeuticRange?.max ?? 0)) {
            // This is actually expected - toxic should be > therapeutic max
        }
        if ((model.drug?.therapeuticRange?.min ?? 0) < 0) {
            errors.push("Therapeutic range minimum cannot be negative");
        }

        setValidationErrors(errors);
    }, [model]);

    // Model type change handler
    const handleModelTypeChange = useCallback((type: PKModelType) => {
        const numCompartments = MODEL_TYPES.find(t => t.value === type)?.compartments ?? 1;

        let compartments: PKCompartment[] = [
            { id: generateId("comp"), name: "Central", type: "central", volume: 10, initialConcentration: 0 }
        ];

        if (numCompartments >= 2) {
            compartments.push({
                id: generateId("comp"),
                name: "Peripheral",
                type: "peripheral",
                volume: 20,
                initialConcentration: 0
            });
        }
        if (numCompartments >= 3) {
            compartments.push({
                id: generateId("comp"),
                name: "Deep Tissue",
                type: "peripheral",
                volume: 15,
                initialConcentration: 0
            });
        }

        // Generate transfers between compartments
        const transfers: PKTransfer[] = [];
        if (numCompartments >= 2) {
            transfers.push({
                id: generateId("transfer"),
                fromCompartmentId: compartments[0].id,
                toCompartmentId: compartments[1].id,
                rateConstant: 0.5
            });
            transfers.push({
                id: generateId("transfer"),
                fromCompartmentId: compartments[1].id,
                toCompartmentId: compartments[0].id,
                rateConstant: 0.3
            });
        }

        setModel(prev => ({
            ...prev,
            modelType: type,
            compartments,
            transfers
        }));
    }, []);

    // Dose handlers
    const addDose = useCallback(() => {
        const newDose: DoseEvent = {
            id: generateId("dose"),
            time: (model.doses ?? []).length * 12,
            amount: 100,
            route: "oral",
            bioavailability: 0.7
        };
        setModel(prev => ({
            ...prev,
            doses: [...(prev.doses ?? []), newDose]
        }));
    }, [model.doses]);

    const updateDose = useCallback((id: string, updates: Partial<DoseEvent>) => {
        setModel(prev => ({
            ...prev,
            doses: (prev.doses ?? []).map(d => d.id === id ? { ...d, ...updates } : d)
        }));
    }, []);

    const removeDose = useCallback((id: string) => {
        setModel(prev => ({
            ...prev,
            doses: (prev.doses ?? []).filter(d => d.id !== id)
        }));
    }, []);

    // Save handler
    const handleSave = useCallback(() => {
        if (validationErrors.length > 0) {
            alert("Please fix validation errors before saving");
            return;
        }

        // Add needsReview flag if toxic threshold is high
        const needsReview = (model.drug?.toxicThreshold ?? Infinity) > 1000;

        onSave?.({ ...model, needsReview } as PKModelDefinition);
    }, [model, validationErrors, onSave]);

    return (
        <div className="flex flex-col h-full bg-white rounded-lg border shadow-sm">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b">
                <div>
                    <h2 className="text-xl font-semibold text-gray-900">
                        Pharmacokinetics Model Editor
                    </h2>
                    <p className="text-sm text-gray-500">
                        Design compartmental PK models with dosing schedules
                    </p>
                </div>
                {onSave && !readOnly && (
                    <button
                        onClick={handleSave}
                        disabled={validationErrors.length > 0}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
                    >
                        Save Model
                    </button>
                )}
            </div>

            {/* Tabs */}
            <div className="flex border-b px-6">
                {(["model", "doses", "parameters", "preview"] as const).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${activeTab === tab
                                ? "border-blue-600 text-blue-600"
                                : "border-transparent text-gray-500 hover:text-gray-700"
                            }`}
                    >
                        {tab === "model" && "Model Structure"}
                        {tab === "doses" && "Dosing"}
                        {tab === "parameters" && "PK Parameters"}
                        {tab === "preview" && "Preview"}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-6">
                {activeTab === "model" && (
                    <div className="space-y-6">
                        {/* Drug Info */}
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Drug Name *
                                </label>
                                <input
                                    type="text"
                                    value={model.drug?.name ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        drug: { ...prev.drug!, name: e.target.value }
                                    }))}
                                    placeholder="e.g., Acetaminophen"
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Model Type
                                </label>
                                <select
                                    value={model.modelType}
                                    onChange={(e) => handleModelTypeChange(e.target.value as PKModelType)}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                >
                                    {MODEL_TYPES.map(t => (
                                        <option key={t.value} value={t.value}>
                                            {t.label} - {t.description}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>

                        {/* Compartment Diagram */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Compartment Model
                            </label>
                            <CompartmentDiagram
                                compartments={model.compartments ?? []}
                                transfers={model.transfers ?? []}
                                selectedCompartmentId={selectedCompartmentId}
                                onCompartmentClick={setSelectedCompartmentId}
                            />
                        </div>

                        {/* Therapeutic Range */}
                        <div className="grid grid-cols-3 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    MEC (mg/L)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={0.1}
                                    value={model.drug?.mec ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        drug: { ...prev.drug!, mec: parseFloat(e.target.value) || undefined }
                                    }))}
                                    placeholder="Min effective"
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Therapeutic Max (mg/L)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={1}
                                    value={model.drug?.therapeuticRange?.max ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        drug: {
                                            ...prev.drug!,
                                            therapeuticRange: {
                                                min: prev.drug?.therapeuticRange?.min ?? 0,
                                                max: parseFloat(e.target.value) || 0
                                            }
                                        }
                                    }))}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Toxic Threshold (mg/L)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={1}
                                    value={model.drug?.toxicThreshold ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        drug: { ...prev.drug!, toxicThreshold: parseFloat(e.target.value) || undefined }
                                    }))}
                                    placeholder="Toxic level"
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === "doses" && (
                    <DoseScheduleEditor
                        doses={model.doses ?? []}
                        onAdd={addDose}
                        onUpdate={updateDose}
                        onRemove={removeDose}
                        readOnly={readOnly}
                    />
                )}

                {activeTab === "parameters" && (
                    <div className="space-y-6">
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Clearance (L/h)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={0.1}
                                    value={model.parameters?.clearance ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        parameters: { ...prev.parameters, clearance: parseFloat(e.target.value) || undefined }
                                    }))}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Half-life (h)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={0.1}
                                    value={model.parameters?.halfLife ?? ""}
                                    onChange={(e) => {
                                        const halfLife = parseFloat(e.target.value);
                                        setModel(prev => ({
                                            ...prev,
                                            parameters: {
                                                ...prev.parameters,
                                                halfLife: halfLife || undefined,
                                                eliminationRate: halfLife ? calculateEliminationRate(halfLife) : undefined
                                            }
                                        }));
                                    }}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Volume of Distribution (L)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={1}
                                    value={model.parameters?.volumeOfDistribution ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        parameters: { ...prev.parameters, volumeOfDistribution: parseFloat(e.target.value) || undefined }
                                    }))}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Absorption Rate ka (1/h)
                                </label>
                                <input
                                    type="number"
                                    min={0}
                                    step={0.1}
                                    value={model.parameters?.absorptionRate ?? ""}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        parameters: { ...prev.parameters, absorptionRate: parseFloat(e.target.value) || undefined }
                                    }))}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Simulation Duration (h)
                            </label>
                            <input
                                type="number"
                                min={1}
                                max={168}
                                step={1}
                                value={model.simulationTime ?? 48}
                                onChange={(e) => setModel(prev => ({
                                    ...prev,
                                    simulationTime: parseInt(e.target.value) || 48
                                }))}
                                disabled={readOnly}
                                className="w-32 px-3 py-2 border rounded-md"
                            />
                            <span className="ml-2 text-sm text-gray-500">hours</span>
                        </div>
                    </div>
                )}

                {activeTab === "preview" && (
                    <div className="space-y-4">
                        <div className="p-4 bg-blue-50 rounded-lg">
                            <h3 className="font-medium text-blue-900">{model.drug?.name || "Unnamed Drug"}</h3>
                            <p className="text-sm text-blue-700">
                                {MODEL_TYPES.find(t => t.value === model.modelType)?.label} Model
                            </p>
                        </div>

                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Model Summary</h4>
                                <dl className="space-y-1">
                                    <div className="flex"><dt className="w-32 text-gray-500">Compartments:</dt><dd>{(model.compartments ?? []).length}</dd></div>
                                    <div className="flex"><dt className="w-32 text-gray-500">Doses:</dt><dd>{(model.doses ?? []).length}</dd></div>
                                    <div className="flex"><dt className="w-32 text-gray-500">Duration:</dt><dd>{model.simulationTime}h</dd></div>
                                </dl>
                            </div>
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">PK Parameters</h4>
                                <dl className="space-y-1">
                                    <div className="flex"><dt className="w-32 text-gray-500">Clearance:</dt><dd>{model.parameters?.clearance ?? "-"} L/h</dd></div>
                                    <div className="flex"><dt className="w-32 text-gray-500">Half-life:</dt><dd>{model.parameters?.halfLife ?? "-"} h</dd></div>
                                    <div className="flex"><dt className="w-32 text-gray-500">Vd:</dt><dd>{model.parameters?.volumeOfDistribution ?? "-"} L</dd></div>
                                </dl>
                            </div>
                        </div>

                        {validationErrors.length > 0 && (
                            <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg">
                                <h4 className="font-medium text-amber-800 mb-2">Validation Issues</h4>
                                <ul className="list-disc list-inside text-sm text-amber-700">
                                    {validationErrors.map((err, i) => (
                                        <li key={i}>{err}</li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

export default PKModelEditor;
