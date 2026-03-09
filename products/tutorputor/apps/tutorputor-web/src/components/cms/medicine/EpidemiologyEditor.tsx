/**
 * Epidemiology Model Editor Component
 *
 * Visual editor for creating infectious disease simulation content
 * with SIR/SEIR models, population parameters, and intervention scenarios.
 *
 * @doc.type component
 * @doc.purpose Medicine epidemiology model authoring UI
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback, useEffect, useRef, useMemo } from "react";

// =============================================================================
// Types
// =============================================================================

/** Epidemiology model type */
type EpiModelType = "SIR" | "SEIR" | "SEIRS" | "SIS";

/** Compartment in epidemiology model */
interface EpiCompartment {
    id: string;
    name: string;
    type: "S" | "E" | "I" | "R" | "D";
    initialPopulation: number;
    color: string;
}

/** Transition between compartments */
interface EpiTransition {
    id: string;
    fromId: string;
    toId: string;
    rate: number;
    rateLabel: string; // e.g., "β", "γ", "σ"
}

/** Intervention type */
type InterventionType = "vaccination" | "quarantine" | "social-distancing" | "treatment" | "mask-mandate";

/** Intervention definition */
interface Intervention {
    id: string;
    type: InterventionType;
    name: string;
    startDay: number;
    endDay?: number;
    effectiveness: number; // 0-1
    coveragePercent: number; // 0-100
}

/** Population parameters */
interface PopulationParams {
    totalPopulation: number;
    initialInfected: number;
    initialRecovered: number;
    initialExposed?: number;
    birthRate?: number;
    deathRate?: number;
}

/** Disease parameters */
interface DiseaseParams {
    basicReproductionNumber: number; // R₀
    infectiousPeriod: number; // days
    latentPeriod?: number; // days (for SEIR)
    immunityDuration?: number; // days (for SEIRS)
    caseFatalityRate?: number; // fraction
}

/** Complete epidemiology model */
interface EpiModelDefinition {
    modelType: EpiModelType;
    diseaseName: string;
    description?: string;
    compartments: EpiCompartment[];
    transitions: EpiTransition[];
    population: PopulationParams;
    disease: DiseaseParams;
    interventions: Intervention[];
    simulationDays: number;
    needsReview?: boolean;
}

interface EpidemiologyEditorProps {
    initialModel?: Partial<EpiModelDefinition>;
    onChange?: (model: EpiModelDefinition) => void;
    onSave?: (model: EpiModelDefinition) => void;
    readOnly?: boolean;
}

// =============================================================================
// Constants
// =============================================================================

const MODEL_TYPES: Array<{ value: EpiModelType; label: string; description: string; compartments: string[] }> = [
    { value: "SIR", label: "SIR", description: "Susceptible → Infected → Recovered", compartments: ["S", "I", "R"] },
    { value: "SEIR", label: "SEIR", description: "With Exposed (latent) period", compartments: ["S", "E", "I", "R"] },
    { value: "SEIRS", label: "SEIRS", description: "With waning immunity", compartments: ["S", "E", "I", "R"] },
    { value: "SIS", label: "SIS", description: "No lasting immunity", compartments: ["S", "I"] },
];

const COMPARTMENT_COLORS: Record<string, string> = {
    S: "#3b82f6", // blue - susceptible
    E: "#f59e0b", // amber - exposed
    I: "#ef4444", // red - infected
    R: "#10b981", // green - recovered
    D: "#6b7280", // gray - deceased
};

const COMPARTMENT_LABELS: Record<string, string> = {
    S: "Susceptible",
    E: "Exposed",
    I: "Infected",
    R: "Recovered",
    D: "Deceased",
};

const INTERVENTION_TYPES: Array<{ value: InterventionType; label: string; icon: string }> = [
    { value: "vaccination", label: "Vaccination", icon: "💉" },
    { value: "quarantine", label: "Quarantine", icon: "🏠" },
    { value: "social-distancing", label: "Social Distancing", icon: "↔️" },
    { value: "treatment", label: "Treatment", icon: "💊" },
    { value: "mask-mandate", label: "Mask Mandate", icon: "😷" },
];

const PRESET_DISEASES: Array<{ name: string; R0: number; infectiousPeriod: number; latentPeriod?: number }> = [
    { name: "Influenza", R0: 1.5, infectiousPeriod: 5, latentPeriod: 2 },
    { name: "COVID-19 (Original)", R0: 2.5, infectiousPeriod: 10, latentPeriod: 5 },
    { name: "Measles", R0: 15, infectiousPeriod: 8, latentPeriod: 10 },
    { name: "Chickenpox", R0: 10, infectiousPeriod: 7, latentPeriod: 14 },
    { name: "Common Cold", R0: 2, infectiousPeriod: 7 },
];

// =============================================================================
// Utility
// =============================================================================

function generateId(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

function calculateBeta(R0: number, gamma: number): number {
    return R0 * gamma;
}

function calculateGamma(infectiousPeriod: number): number {
    return 1 / infectiousPeriod;
}

// =============================================================================
// Sub-Components
// =============================================================================

interface CompartmentFlowDiagramProps {
    compartments: EpiCompartment[];
    transitions: EpiTransition[];
}

function CompartmentFlowDiagram({ compartments, transitions }: CompartmentFlowDiagramProps) {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        const width = 500;
        const height = 150;

        // Clear
        ctx.fillStyle = "#f8fafc";
        ctx.fillRect(0, 0, width, height);

        // Position compartments horizontally
        const spacing = width / (compartments.length + 1);
        const positions: Record<string, { x: number; y: number }> = {};

        compartments.forEach((c, i) => {
            positions[c.id] = { x: spacing * (i + 1), y: 75 };
        });

        // Draw transitions (arrows)
        transitions.forEach(t => {
            const from = positions[t.fromId];
            const to = positions[t.toId];
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

            // Curved arrow for SEIRS waning immunity
            if (t.fromId.includes("R") && t.toId.includes("S")) {
                ctx.beginPath();
                ctx.strokeStyle = "#94a3b8";
                ctx.lineWidth = 2;
                const cpY = 140;
                ctx.moveTo(from.x, from.y + 30);
                ctx.quadraticCurveTo((from.x + to.x) / 2, cpY, to.x, to.y + 30);
                ctx.stroke();

                // Label
                ctx.fillStyle = "#64748b";
                ctx.font = "12px Arial";
                ctx.textAlign = "center";
                ctx.fillText(t.rateLabel, (from.x + to.x) / 2, cpY - 5);
            } else {
                ctx.strokeStyle = "#94a3b8";
                ctx.lineWidth = 2;
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

                // Rate label
                ctx.fillStyle = "#64748b";
                ctx.font = "12px Arial";
                ctx.textAlign = "center";
                ctx.fillText(t.rateLabel, (startX + endX) / 2, (startY + endY) / 2 - 12);
            }
        });

        // Draw compartments
        compartments.forEach(c => {
            const pos = positions[c.id];
            if (!pos) return;

            // Box
            ctx.beginPath();
            ctx.roundRect(pos.x - 30, pos.y - 25, 60, 50, 8);
            ctx.fillStyle = c.color;
            ctx.fill();
            ctx.strokeStyle = "#374151";
            ctx.lineWidth = 2;
            ctx.stroke();

            // Label
            ctx.fillStyle = "#ffffff";
            ctx.font = "bold 18px Arial";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText(c.type, pos.x, pos.y - 5);

            // Population
            ctx.fillStyle = "#ffffff";
            ctx.font = "10px Arial";
            ctx.fillText(c.initialPopulation.toLocaleString(), pos.x, pos.y + 12);
        });

    }, [compartments, transitions]);

    return (
        <canvas
            ref={canvasRef}
            width={500}
            height={150}
            className="border rounded-lg"
        />
    );
}

interface R0SliderProps {
    value: number;
    onChange: (value: number) => void;
    disabled?: boolean;
}

function R0Slider({ value, onChange, disabled }: R0SliderProps) {
    const getR0Color = (r0: number): string => {
        if (r0 < 1) return "#10b981";
        if (r0 < 2) return "#f59e0b";
        if (r0 < 5) return "#f97316";
        return "#ef4444";
    };

    const getR0Label = (r0: number): string => {
        if (r0 < 1) return "Declining";
        if (r0 === 1) return "Endemic";
        if (r0 < 2) return "Spreading";
        if (r0 < 5) return "Highly Contagious";
        return "Extremely Contagious";
    };

    return (
        <div className="space-y-2">
            <div className="flex items-center justify-between">
                <label className="text-sm font-medium text-gray-700">
                    Basic Reproduction Number (R₀)
                </label>
                <span
                    className="text-lg font-bold px-2 py-0.5 rounded"
                    style={{ backgroundColor: getR0Color(value) + "20", color: getR0Color(value) }}
                >
                    {value.toFixed(1)}
                </span>
            </div>
            <input
                type="range"
                min={0}
                max={20}
                step={0.1}
                value={value}
                onChange={(e) => onChange(parseFloat(e.target.value))}
                disabled={disabled}
                className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
                style={{
                    background: `linear-gradient(to right, #10b981 0%, #f59e0b 10%, #f97316 25%, #ef4444 100%)`
                }}
            />
            <div className="flex justify-between text-xs text-gray-500">
                <span>0</span>
                <span className="font-medium" style={{ color: getR0Color(value) }}>
                    {getR0Label(value)}
                </span>
                <span>20</span>
            </div>
        </div>
    );
}

// =============================================================================
// Main Component
// =============================================================================

export function EpidemiologyEditor({
    initialModel,
    onChange,
    onSave,
    readOnly = false,
}: EpidemiologyEditorProps) {
    const [activeTab, setActiveTab] = useState<"disease" | "population" | "interventions" | "preview">("disease");
    const [model, setModel] = useState<Partial<EpiModelDefinition>>(
        initialModel ?? {
            modelType: "SIR",
            diseaseName: "",
            compartments: [
                { id: "comp-S", name: "Susceptible", type: "S", initialPopulation: 999000, color: COMPARTMENT_COLORS.S },
                { id: "comp-I", name: "Infected", type: "I", initialPopulation: 1000, color: COMPARTMENT_COLORS.I },
                { id: "comp-R", name: "Recovered", type: "R", initialPopulation: 0, color: COMPARTMENT_COLORS.R },
            ],
            transitions: [
                { id: "t-SI", fromId: "comp-S", toId: "comp-I", rate: 0.3, rateLabel: "β" },
                { id: "t-IR", fromId: "comp-I", toId: "comp-R", rate: 0.1, rateLabel: "γ" },
            ],
            population: {
                totalPopulation: 1000000,
                initialInfected: 1000,
                initialRecovered: 0,
            },
            disease: {
                basicReproductionNumber: 2.5,
                infectiousPeriod: 10,
            },
            interventions: [],
            simulationDays: 180,
        }
    );
    const [validationErrors, setValidationErrors] = useState<string[]>([]);

    // Derived values
    const gamma = useMemo(() =>
        calculateGamma(model.disease?.infectiousPeriod ?? 10),
        [model.disease?.infectiousPeriod]
    );

    const beta = useMemo(() =>
        calculateBeta(model.disease?.basicReproductionNumber ?? 2.5, gamma),
        [model.disease?.basicReproductionNumber, gamma]
    );

    // Notify parent
    useEffect(() => {
        onChange?.(model as EpiModelDefinition);
    }, [model, onChange]);

    // Validate
    useEffect(() => {
        const errors: string[] = [];
        if (!model.diseaseName) errors.push("Disease name is required");
        if ((model.population?.totalPopulation ?? 0) <= 0) errors.push("Total population must be positive");
        if ((model.disease?.basicReproductionNumber ?? 0) <= 0) errors.push("R₀ must be positive");
        if ((model.disease?.infectiousPeriod ?? 0) <= 0) errors.push("Infectious period must be positive");
        setValidationErrors(errors);
    }, [model]);

    // Model type change
    const handleModelTypeChange = useCallback((type: EpiModelType) => {
        const config = MODEL_TYPES.find(t => t.value === type);
        if (!config) return;

        const compartments: EpiCompartment[] = config.compartments.map(c => ({
            id: `comp-${c}`,
            name: COMPARTMENT_LABELS[c],
            type: c as EpiCompartment["type"],
            initialPopulation: c === "S" ? (model.population?.totalPopulation ?? 1000000) - (model.population?.initialInfected ?? 1000) :
                c === "I" ? (model.population?.initialInfected ?? 1000) : 0,
            color: COMPARTMENT_COLORS[c],
        }));

        const transitions: EpiTransition[] = [];
        if (type === "SIR") {
            transitions.push({ id: "t-SI", fromId: "comp-S", toId: "comp-I", rate: beta, rateLabel: "β" });
            transitions.push({ id: "t-IR", fromId: "comp-I", toId: "comp-R", rate: gamma, rateLabel: "γ" });
        } else if (type === "SEIR" || type === "SEIRS") {
            transitions.push({ id: "t-SE", fromId: "comp-S", toId: "comp-E", rate: beta, rateLabel: "β" });
            transitions.push({ id: "t-EI", fromId: "comp-E", toId: "comp-I", rate: 1 / (model.disease?.latentPeriod ?? 5), rateLabel: "σ" });
            transitions.push({ id: "t-IR", fromId: "comp-I", toId: "comp-R", rate: gamma, rateLabel: "γ" });
            if (type === "SEIRS") {
                transitions.push({ id: "t-RS", fromId: "comp-R", toId: "comp-S", rate: 1 / (model.disease?.immunityDuration ?? 365), rateLabel: "ω" });
            }
        } else if (type === "SIS") {
            transitions.push({ id: "t-SI", fromId: "comp-S", toId: "comp-I", rate: beta, rateLabel: "β" });
            transitions.push({ id: "t-IS", fromId: "comp-I", toId: "comp-S", rate: gamma, rateLabel: "γ" });
        }

        setModel(prev => ({
            ...prev,
            modelType: type,
            compartments,
            transitions,
        }));
    }, [model.population, model.disease, beta, gamma]);

    // Apply preset disease
    const applyPreset = useCallback((preset: typeof PRESET_DISEASES[0]) => {
        setModel(prev => ({
            ...prev,
            diseaseName: preset.name,
            disease: {
                ...prev.disease,
                basicReproductionNumber: preset.R0,
                infectiousPeriod: preset.infectiousPeriod,
                latentPeriod: preset.latentPeriod,
            }
        }));
    }, []);

    // Intervention handlers
    const addIntervention = useCallback(() => {
        const newIntervention: Intervention = {
            id: generateId("int"),
            type: "social-distancing",
            name: "New Intervention",
            startDay: 30,
            effectiveness: 0.5,
            coveragePercent: 80,
        };
        setModel(prev => ({
            ...prev,
            interventions: [...(prev.interventions ?? []), newIntervention],
        }));
    }, []);

    const updateIntervention = useCallback((id: string, updates: Partial<Intervention>) => {
        setModel(prev => ({
            ...prev,
            interventions: (prev.interventions ?? []).map(i => i.id === id ? { ...i, ...updates } : i),
        }));
    }, []);

    const removeIntervention = useCallback((id: string) => {
        setModel(prev => ({
            ...prev,
            interventions: (prev.interventions ?? []).filter(i => i.id !== id),
        }));
    }, []);

    // Save
    const handleSave = useCallback(() => {
        if (validationErrors.length > 0) {
            alert("Please fix validation errors before saving");
            return;
        }

        // Flag for review if R0 > 10 (highly contagious, may need verification)
        const needsReview = (model.disease?.basicReproductionNumber ?? 0) > 10;

        onSave?.({ ...model, needsReview } as EpiModelDefinition);
    }, [model, validationErrors, onSave]);

    return (
        <div className="flex flex-col h-full bg-white rounded-lg border shadow-sm">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b">
                <div>
                    <h2 className="text-xl font-semibold text-gray-900">
                        Epidemiology Model Editor
                    </h2>
                    <p className="text-sm text-gray-500">
                        Design infectious disease spread simulations
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
                {(["disease", "population", "interventions", "preview"] as const).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${activeTab === tab
                                ? "border-blue-600 text-blue-600"
                                : "border-transparent text-gray-500 hover:text-gray-700"
                            }`}
                    >
                        {tab === "disease" && "Disease Parameters"}
                        {tab === "population" && "Population"}
                        {tab === "interventions" && "Interventions"}
                        {tab === "preview" && "Preview"}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-6">
                {activeTab === "disease" && (
                    <div className="space-y-6">
                        {/* Disease Presets */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Quick Presets
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {PRESET_DISEASES.map(preset => (
                                    <button
                                        key={preset.name}
                                        onClick={() => applyPreset(preset)}
                                        disabled={readOnly}
                                        className={`px-3 py-1.5 text-sm border rounded-full hover:bg-gray-50 ${model.diseaseName === preset.name ? "border-blue-500 bg-blue-50" : "border-gray-300"
                                            }`}
                                    >
                                        {preset.name} (R₀={preset.R0})
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Disease Name *
                                </label>
                                <input
                                    type="text"
                                    value={model.diseaseName ?? ""}
                                    onChange={(e) => setModel(prev => ({ ...prev, diseaseName: e.target.value }))}
                                    placeholder="e.g., COVID-19"
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
                                    onChange={(e) => handleModelTypeChange(e.target.value as EpiModelType)}
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

                        {/* Compartment Flow */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Compartment Model
                            </label>
                            <CompartmentFlowDiagram
                                compartments={model.compartments ?? []}
                                transitions={model.transitions ?? []}
                            />
                        </div>

                        {/* R0 Slider */}
                        <R0Slider
                            value={model.disease?.basicReproductionNumber ?? 2.5}
                            onChange={(value) => setModel(prev => ({
                                ...prev,
                                disease: { ...prev.disease!, basicReproductionNumber: value }
                            }))}
                            disabled={readOnly}
                        />

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Infectious Period (days)
                                </label>
                                <input
                                    type="number"
                                    min={1}
                                    step={1}
                                    value={model.disease?.infectiousPeriod ?? 10}
                                    onChange={(e) => setModel(prev => ({
                                        ...prev,
                                        disease: { ...prev.disease!, infectiousPeriod: parseInt(e.target.value) || 10 }
                                    }))}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            {(model.modelType === "SEIR" || model.modelType === "SEIRS") && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Latent Period (days)
                                    </label>
                                    <input
                                        type="number"
                                        min={1}
                                        step={1}
                                        value={model.disease?.latentPeriod ?? 5}
                                        onChange={(e) => setModel(prev => ({
                                            ...prev,
                                            disease: { ...prev.disease!, latentPeriod: parseInt(e.target.value) || 5 }
                                        }))}
                                        disabled={readOnly}
                                        className="w-full px-3 py-2 border rounded-md"
                                    />
                                </div>
                            )}
                        </div>

                        {/* Derived rates */}
                        <div className="p-4 bg-gray-50 rounded-lg">
                            <h4 className="text-sm font-medium text-gray-700 mb-2">Derived Rates</h4>
                            <div className="grid grid-cols-3 gap-4 text-sm">
                                <div>
                                    <span className="text-gray-500">γ (recovery):</span>
                                    <span className="ml-2 font-mono">{gamma.toFixed(4)} /day</span>
                                </div>
                                <div>
                                    <span className="text-gray-500">β (transmission):</span>
                                    <span className="ml-2 font-mono">{beta.toFixed(4)} /day</span>
                                </div>
                                <div>
                                    <span className="text-gray-500">Doubling time:</span>
                                    <span className="ml-2 font-mono">
                                        {((model.disease?.basicReproductionNumber ?? 2.5) > 1
                                            ? (Math.log(2) / ((model.disease?.basicReproductionNumber ?? 2.5) - 1) / gamma).toFixed(1)
                                            : "∞")} days
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === "population" && (
                    <div className="space-y-6">
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Total Population
                                </label>
                                <input
                                    type="number"
                                    min={100}
                                    step={1000}
                                    value={model.population?.totalPopulation ?? 1000000}
                                    onChange={(e) => {
                                        const total = parseInt(e.target.value) || 1000000;
                                        const infected = model.population?.initialInfected ?? 1000;
                                        const recovered = model.population?.initialRecovered ?? 0;
                                        setModel(prev => ({
                                            ...prev,
                                            population: { ...prev.population!, totalPopulation: total },
                                            compartments: prev.compartments?.map(c => ({
                                                ...c,
                                                initialPopulation: c.type === "S" ? total - infected - recovered :
                                                    c.type === "I" ? infected :
                                                        c.type === "R" ? recovered : c.initialPopulation
                                            }))
                                        }));
                                    }}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Initial Infected
                                </label>
                                <input
                                    type="number"
                                    min={1}
                                    step={100}
                                    value={model.population?.initialInfected ?? 1000}
                                    onChange={(e) => {
                                        const infected = parseInt(e.target.value) || 1000;
                                        setModel(prev => ({
                                            ...prev,
                                            population: { ...prev.population!, initialInfected: infected },
                                            compartments: prev.compartments?.map(c => ({
                                                ...c,
                                                initialPopulation: c.type === "I" ? infected :
                                                    c.type === "S" ? (prev.population?.totalPopulation ?? 1000000) - infected - (prev.population?.initialRecovered ?? 0) :
                                                        c.initialPopulation
                                            }))
                                        }));
                                    }}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border rounded-md"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Simulation Duration (days)
                            </label>
                            <input
                                type="number"
                                min={30}
                                max={730}
                                step={30}
                                value={model.simulationDays ?? 180}
                                onChange={(e) => setModel(prev => ({
                                    ...prev,
                                    simulationDays: parseInt(e.target.value) || 180
                                }))}
                                disabled={readOnly}
                                className="w-32 px-3 py-2 border rounded-md"
                            />
                            <span className="ml-2 text-sm text-gray-500">
                                ({((model.simulationDays ?? 180) / 30).toFixed(1)} months)
                            </span>
                        </div>

                        {/* Population breakdown */}
                        <div className="p-4 bg-gray-50 rounded-lg">
                            <h4 className="text-sm font-medium text-gray-700 mb-3">Initial Distribution</h4>
                            <div className="space-y-2">
                                {(model.compartments ?? []).map(c => (
                                    <div key={c.id} className="flex items-center gap-3">
                                        <div
                                            className="w-8 h-8 rounded flex items-center justify-center text-white font-bold"
                                            style={{ backgroundColor: c.color }}
                                        >
                                            {c.type}
                                        </div>
                                        <span className="w-24 text-sm">{c.name}</span>
                                        <div className="flex-1 h-4 bg-gray-200 rounded-full overflow-hidden">
                                            <div
                                                className="h-full rounded-full"
                                                style={{
                                                    backgroundColor: c.color,
                                                    width: `${(c.initialPopulation / (model.population?.totalPopulation ?? 1)) * 100}%`
                                                }}
                                            />
                                        </div>
                                        <span className="w-24 text-sm text-right">
                                            {c.initialPopulation.toLocaleString()}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === "interventions" && (
                    <div className="space-y-6">
                        <div className="flex items-center justify-between">
                            <h3 className="font-medium text-gray-900">Interventions</h3>
                            {!readOnly && (
                                <button
                                    onClick={addIntervention}
                                    className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    + Add Intervention
                                </button>
                            )}
                        </div>

                        <div className="space-y-4">
                            {(model.interventions ?? []).map(intervention => (
                                <div key={intervention.id} className="p-4 border rounded-lg">
                                    <div className="flex items-start gap-4">
                                        <div className="text-2xl">
                                            {INTERVENTION_TYPES.find(t => t.value === intervention.type)?.icon}
                                        </div>
                                        <div className="flex-1 space-y-3">
                                            <div className="grid grid-cols-2 gap-3">
                                                <input
                                                    type="text"
                                                    value={intervention.name}
                                                    onChange={(e) => updateIntervention(intervention.id, { name: e.target.value })}
                                                    placeholder="Intervention name"
                                                    disabled={readOnly}
                                                    className="px-3 py-1.5 border rounded"
                                                />
                                                <select
                                                    value={intervention.type}
                                                    onChange={(e) => updateIntervention(intervention.id, { type: e.target.value as InterventionType })}
                                                    disabled={readOnly}
                                                    className="px-3 py-1.5 border rounded"
                                                >
                                                    {INTERVENTION_TYPES.map(t => (
                                                        <option key={t.value} value={t.value}>{t.icon} {t.label}</option>
                                                    ))}
                                                </select>
                                            </div>
                                            <div className="grid grid-cols-4 gap-3">
                                                <div>
                                                    <label className="text-xs text-gray-500">Start Day</label>
                                                    <input
                                                        type="number"
                                                        min={0}
                                                        value={intervention.startDay}
                                                        onChange={(e) => updateIntervention(intervention.id, { startDay: parseInt(e.target.value) || 0 })}
                                                        disabled={readOnly}
                                                        className="w-full px-2 py-1 border rounded text-sm"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="text-xs text-gray-500">End Day</label>
                                                    <input
                                                        type="number"
                                                        min={intervention.startDay}
                                                        value={intervention.endDay ?? ""}
                                                        onChange={(e) => updateIntervention(intervention.id, { endDay: parseInt(e.target.value) || undefined })}
                                                        placeholder="∞"
                                                        disabled={readOnly}
                                                        className="w-full px-2 py-1 border rounded text-sm"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="text-xs text-gray-500">Effectiveness</label>
                                                    <input
                                                        type="number"
                                                        min={0}
                                                        max={1}
                                                        step={0.1}
                                                        value={intervention.effectiveness}
                                                        onChange={(e) => updateIntervention(intervention.id, { effectiveness: parseFloat(e.target.value) || 0 })}
                                                        disabled={readOnly}
                                                        className="w-full px-2 py-1 border rounded text-sm"
                                                    />
                                                </div>
                                                <div>
                                                    <label className="text-xs text-gray-500">Coverage %</label>
                                                    <input
                                                        type="number"
                                                        min={0}
                                                        max={100}
                                                        value={intervention.coveragePercent}
                                                        onChange={(e) => updateIntervention(intervention.id, { coveragePercent: parseInt(e.target.value) || 0 })}
                                                        disabled={readOnly}
                                                        className="w-full px-2 py-1 border rounded text-sm"
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                        {!readOnly && (
                                            <button
                                                onClick={() => removeIntervention(intervention.id)}
                                                className="p-1 text-red-500 hover:bg-red-50 rounded"
                                            >
                                                ✕
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>

                        {(model.interventions ?? []).length === 0 && (
                            <div className="text-center py-8 text-gray-500">
                                <p>No interventions configured.</p>
                                <p className="text-sm">Add interventions to model policy responses.</p>
                            </div>
                        )}
                    </div>
                )}

                {activeTab === "preview" && (
                    <div className="space-y-4">
                        <div className="p-4 bg-blue-50 rounded-lg">
                            <h3 className="font-medium text-blue-900">{model.diseaseName || "Unnamed Disease"}</h3>
                            <p className="text-sm text-blue-700">
                                {MODEL_TYPES.find(t => t.value === model.modelType)?.label} Model • R₀ = {model.disease?.basicReproductionNumber}
                            </p>
                        </div>

                        <CompartmentFlowDiagram
                            compartments={model.compartments ?? []}
                            transitions={model.transitions ?? []}
                        />

                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Disease Parameters</h4>
                                <dl className="space-y-1">
                                    <div className="flex"><dt className="w-40 text-gray-500">R₀:</dt><dd>{model.disease?.basicReproductionNumber}</dd></div>
                                    <div className="flex"><dt className="w-40 text-gray-500">Infectious Period:</dt><dd>{model.disease?.infectiousPeriod} days</dd></div>
                                    <div className="flex"><dt className="w-40 text-gray-500">Population:</dt><dd>{(model.population?.totalPopulation ?? 0).toLocaleString()}</dd></div>
                                    <div className="flex"><dt className="w-40 text-gray-500">Simulation:</dt><dd>{model.simulationDays} days</dd></div>
                                </dl>
                            </div>
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Interventions</h4>
                                {(model.interventions ?? []).length > 0 ? (
                                    <ul className="space-y-1">
                                        {(model.interventions ?? []).map(i => (
                                            <li key={i.id} className="text-sm">
                                                {INTERVENTION_TYPES.find(t => t.value === i.type)?.icon} {i.name} (Day {i.startDay})
                                            </li>
                                        ))}
                                    </ul>
                                ) : (
                                    <p className="text-gray-500">None configured</p>
                                )}
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

export default EpidemiologyEditor;
