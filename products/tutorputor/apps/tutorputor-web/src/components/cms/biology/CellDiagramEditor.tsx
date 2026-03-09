/**
 * Cell Diagram Editor Component
 *
 * Visual editor for creating cell biology simulation content
 * with organelle placement, gene expression, and metabolic pathways.
 *
 * @doc.type component
 * @doc.purpose Biology cell diagram authoring UI
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback, useEffect, useRef } from "react";

// =============================================================================
// Types
// =============================================================================

/** Cell type */
type CellType = "prokaryote" | "eukaryote-animal" | "eukaryote-plant";

/** Organelle type */
type OrganelleType =
    | "nucleus"
    | "mitochondria"
    | "ribosome"
    | "endoplasmic-reticulum-rough"
    | "endoplasmic-reticulum-smooth"
    | "golgi"
    | "lysosome"
    | "chloroplast"
    | "vacuole"
    | "cell-membrane"
    | "cell-wall";

/** Process type for gene expression / metabolism */
type BiologicalProcess =
    | "transcription"
    | "translation"
    | "replication"
    | "glycolysis"
    | "krebs-cycle"
    | "oxidative-phosphorylation"
    | "photosynthesis"
    | "protein-folding";

/** Organelle instance */
interface Organelle {
    id: string;
    type: OrganelleType;
    x: number;
    y: number;
    scale: number;
    label?: string;
    active?: boolean;
}

/** Gene definition */
interface Gene {
    id: string;
    name: string;
    sequence?: string;
    product?: string;
    expressionLevel: number; // 0-1
}

/** Process step in a biological pathway */
interface ProcessStep {
    id: string;
    process: BiologicalProcess;
    location: string; // organelle id or "cytoplasm"
    description: string;
    duration: number; // seconds
}

/** Complete cell model */
interface CellModelDefinition {
    cellType: CellType;
    name: string;
    description?: string;
    organelles: Organelle[];
    genes: Gene[];
    processSteps: ProcessStep[];
    canvasSize: { width: number; height: number };
    needsReview?: boolean;
}

interface CellDiagramEditorProps {
    initialModel?: Partial<CellModelDefinition>;
    onChange?: (model: CellModelDefinition) => void;
    onSave?: (model: CellModelDefinition) => void;
    readOnly?: boolean;
}

// =============================================================================
// Constants
// =============================================================================

const CELL_TYPES: Array<{ value: CellType; label: string; description: string }> = [
    { value: "prokaryote", label: "Prokaryote", description: "Bacteria/Archaea - no membrane-bound organelles" },
    { value: "eukaryote-animal", label: "Animal Cell", description: "Eukaryotic cell with typical animal organelles" },
    { value: "eukaryote-plant", label: "Plant Cell", description: "Eukaryotic cell with chloroplasts and cell wall" },
];

const ORGANELLE_DEFS: Record<OrganelleType, { label: string; color: string; icon: string; availableIn: CellType[] }> = {
    "nucleus": { label: "Nucleus", color: "#6366f1", icon: "⬭", availableIn: ["eukaryote-animal", "eukaryote-plant"] },
    "mitochondria": { label: "Mitochondria", color: "#ef4444", icon: "⬬", availableIn: ["eukaryote-animal", "eukaryote-plant"] },
    "ribosome": { label: "Ribosome", color: "#f59e0b", icon: "●", availableIn: ["prokaryote", "eukaryote-animal", "eukaryote-plant"] },
    "endoplasmic-reticulum-rough": { label: "Rough ER", color: "#3b82f6", icon: "〰", availableIn: ["eukaryote-animal", "eukaryote-plant"] },
    "endoplasmic-reticulum-smooth": { label: "Smooth ER", color: "#60a5fa", icon: "〰", availableIn: ["eukaryote-animal", "eukaryote-plant"] },
    "golgi": { label: "Golgi Apparatus", color: "#a855f7", icon: "⌓", availableIn: ["eukaryote-animal", "eukaryote-plant"] },
    "lysosome": { label: "Lysosome", color: "#84cc16", icon: "○", availableIn: ["eukaryote-animal"] },
    "chloroplast": { label: "Chloroplast", color: "#22c55e", icon: "◉", availableIn: ["eukaryote-plant"] },
    "vacuole": { label: "Vacuole", color: "#06b6d4", icon: "◯", availableIn: ["eukaryote-animal", "eukaryote-plant"] },
    "cell-membrane": { label: "Cell Membrane", color: "#f97316", icon: "◌", availableIn: ["prokaryote", "eukaryote-animal", "eukaryote-plant"] },
    "cell-wall": { label: "Cell Wall", color: "#78716c", icon: "▢", availableIn: ["prokaryote", "eukaryote-plant"] },
};

const PROCESS_DEFS: Array<{ value: BiologicalProcess; label: string; description: string; location: string }> = [
    { value: "transcription", label: "Transcription", description: "DNA → mRNA", location: "nucleus" },
    { value: "translation", label: "Translation", description: "mRNA → Protein", location: "ribosome" },
    { value: "replication", label: "Replication", description: "DNA → DNA", location: "nucleus" },
    { value: "glycolysis", label: "Glycolysis", description: "Glucose → Pyruvate", location: "cytoplasm" },
    { value: "krebs-cycle", label: "Krebs Cycle", description: "Citric acid cycle", location: "mitochondria" },
    { value: "oxidative-phosphorylation", label: "Oxidative Phosphorylation", description: "ATP synthesis", location: "mitochondria" },
    { value: "photosynthesis", label: "Photosynthesis", description: "Light → Chemical energy", location: "chloroplast" },
    { value: "protein-folding", label: "Protein Folding", description: "Polypeptide → Functional protein", location: "endoplasmic-reticulum-rough" },
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

interface CellCanvasProps {
    cellType: CellType;
    organelles: Organelle[];
    selectedOrganelleId: string | null;
    onOrganelleClick: (id: string) => void;
    onOrganelleMove: (id: string, x: number, y: number) => void;
    width: number;
    height: number;
    readOnly?: boolean;
}

function CellCanvas({
    cellType,
    organelles,
    selectedOrganelleId,
    onOrganelleClick,
    onOrganelleMove,
    width,
    height,
    readOnly,
}: CellCanvasProps) {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [dragging, setDragging] = useState<string | null>(null);
    const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        // Background
        ctx.fillStyle = "#fef3c7"; // pale yellow cytoplasm
        ctx.fillRect(0, 0, width, height);

        // Cell membrane / wall
        ctx.strokeStyle = cellType.includes("plant") ? "#78716c" : "#f97316";
        ctx.lineWidth = cellType.includes("plant") ? 8 : 4;

        if (cellType === "prokaryote") {
            // Capsule shape for prokaryote
            ctx.beginPath();
            ctx.ellipse(width / 2, height / 2, width / 2 - 20, height / 2 - 20, 0, 0, Math.PI * 2);
            ctx.stroke();
        } else {
            // Rounded rectangle for eukaryotes
            ctx.beginPath();
            ctx.roundRect(20, 20, width - 40, height - 40, 30);
            ctx.stroke();
        }

        // Draw organelles
        organelles.forEach(org => {
            const def = ORGANELLE_DEFS[org.type];
            const isSelected = org.id === selectedOrganelleId;
            const size = 40 * (org.scale ?? 1);

            // Glow for selected
            if (isSelected) {
                ctx.shadowColor = def.color;
                ctx.shadowBlur = 15;
            }

            // Organelle shape
            ctx.beginPath();
            if (org.type === "nucleus") {
                ctx.ellipse(org.x, org.y, size, size * 0.8, 0, 0, Math.PI * 2);
            } else if (org.type === "mitochondria") {
                ctx.ellipse(org.x, org.y, size * 1.5, size * 0.5, 0, 0, Math.PI * 2);
            } else if (org.type === "chloroplast") {
                ctx.ellipse(org.x, org.y, size * 1.3, size * 0.6, 0.3, 0, Math.PI * 2);
            } else if (org.type.includes("endoplasmic")) {
                // Wavy lines for ER
                ctx.moveTo(org.x - size, org.y);
                for (let i = 0; i < 5; i++) {
                    ctx.quadraticCurveTo(
                        org.x - size + (i * 2 + 1) * (size / 5), org.y + (i % 2 === 0 ? 15 : -15),
                        org.x - size + (i * 2 + 2) * (size / 5), org.y
                    );
                }
            } else if (org.type === "golgi") {
                // Stacked curves for Golgi
                for (let i = 0; i < 4; i++) {
                    ctx.moveTo(org.x - size * 0.8, org.y - 20 + i * 15);
                    ctx.quadraticCurveTo(org.x, org.y - 30 + i * 15, org.x + size * 0.8, org.y - 20 + i * 15);
                }
            } else if (org.type === "vacuole") {
                ctx.ellipse(org.x, org.y, size * 1.5, size * 1.2, 0, 0, Math.PI * 2);
            } else {
                // Default circle for ribosomes, lysosomes
                ctx.arc(org.x, org.y, size * (org.type === "ribosome" ? 0.3 : 0.5), 0, Math.PI * 2);
            }

            ctx.fillStyle = def.color + (org.active ? "ff" : "99");
            ctx.fill();
            ctx.strokeStyle = isSelected ? "#1d4ed8" : "#333";
            ctx.lineWidth = isSelected ? 3 : 1;
            ctx.stroke();

            ctx.shadowBlur = 0;

            // Label
            if (org.label || !readOnly) {
                ctx.fillStyle = "#333";
                ctx.font = "11px Arial";
                ctx.textAlign = "center";
                ctx.fillText(org.label || def.label, org.x, org.y + size + 15);
            }
        });

    }, [cellType, organelles, selectedOrganelleId, width, height, readOnly]);

    const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
        if (readOnly) return;
        const rect = canvasRef.current!.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        // Find clicked organelle
        const clicked = [...organelles].reverse().find(org => {
            const size = 40 * (org.scale ?? 1);
            const dx = x - org.x;
            const dy = y - org.y;
            return dx * dx + dy * dy <= size * size;
        });

        if (clicked) {
            onOrganelleClick(clicked.id);
            setDragging(clicked.id);
            setDragOffset({ x: x - clicked.x, y: y - clicked.y });
        }
    };

    const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
        if (!dragging || readOnly) return;
        const rect = canvasRef.current!.getBoundingClientRect();
        const x = e.clientX - rect.left - dragOffset.x;
        const y = e.clientY - rect.top - dragOffset.y;
        onOrganelleMove(dragging, x, y);
    };

    const handleMouseUp = () => {
        setDragging(null);
    };

    return (
        <canvas
            ref={canvasRef}
            width={width}
            height={height}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            className={`border rounded-lg ${readOnly ? "cursor-default" : "cursor-move"}`}
        />
    );
}

interface OrganellePaletteProps {
    cellType: CellType;
    onAddOrganelle: (type: OrganelleType) => void;
    disabled?: boolean;
}

function OrganellePalette({ cellType, onAddOrganelle, disabled }: OrganellePaletteProps) {
    const availableOrganelles = Object.entries(ORGANELLE_DEFS).filter(
        ([_, def]) => def.availableIn.includes(cellType)
    );

    return (
        <div className="p-3 bg-gray-50 rounded-lg border">
            <h4 className="text-sm font-medium text-gray-700 mb-2">Organelles</h4>
            <div className="grid grid-cols-2 gap-2">
                {availableOrganelles.map(([type, def]) => (
                    <button
                        key={type}
                        onClick={() => onAddOrganelle(type as OrganelleType)}
                        disabled={disabled}
                        className="flex items-center gap-2 px-2 py-1.5 text-xs border rounded hover:bg-white disabled:opacity-50"
                    >
                        <span
                            className="w-4 h-4 rounded"
                            style={{ backgroundColor: def.color }}
                        />
                        {def.label}
                    </button>
                ))}
            </div>
        </div>
    );
}

// =============================================================================
// Main Component
// =============================================================================

export function CellDiagramEditor({
    initialModel,
    onChange,
    onSave,
    readOnly = false,
}: CellDiagramEditorProps) {
    const [activeTab, setActiveTab] = useState<"diagram" | "genes" | "processes" | "preview">("diagram");
    const [model, setModel] = useState<Partial<CellModelDefinition>>(
        initialModel ?? {
            cellType: "eukaryote-animal",
            name: "",
            organelles: [],
            genes: [],
            processSteps: [],
            canvasSize: { width: 600, height: 400 },
        }
    );
    const [selectedOrganelleId, setSelectedOrganelleId] = useState<string | null>(null);
    const [validationErrors, setValidationErrors] = useState<string[]>([]);

    // Notify parent
    useEffect(() => {
        onChange?.(model as CellModelDefinition);
    }, [model, onChange]);

    // Validate
    useEffect(() => {
        const errors: string[] = [];
        if (!model.name) errors.push("Cell name is required");
        if ((model.organelles ?? []).length === 0) errors.push("Add at least one organelle");
        setValidationErrors(errors);
    }, [model]);

    // Organelle handlers
    const addOrganelle = useCallback((type: OrganelleType) => {
        const canvas = model.canvasSize ?? { width: 600, height: 400 };
        const newOrg: Organelle = {
            id: generateId("org"),
            type,
            x: canvas.width / 2 + (Math.random() - 0.5) * 100,
            y: canvas.height / 2 + (Math.random() - 0.5) * 100,
            scale: 1,
        };
        setModel(prev => ({
            ...prev,
            organelles: [...(prev.organelles ?? []), newOrg],
        }));
        setSelectedOrganelleId(newOrg.id);
    }, [model.canvasSize]);

    const moveOrganelle = useCallback((id: string, x: number, y: number) => {
        setModel(prev => ({
            ...prev,
            organelles: (prev.organelles ?? []).map(o =>
                o.id === id ? { ...o, x, y } : o
            ),
        }));
    }, []);

    const updateOrganelle = useCallback((id: string, updates: Partial<Organelle>) => {
        setModel(prev => ({
            ...prev,
            organelles: (prev.organelles ?? []).map(o =>
                o.id === id ? { ...o, ...updates } : o
            ),
        }));
    }, []);

    const removeOrganelle = useCallback((id: string) => {
        setModel(prev => ({
            ...prev,
            organelles: (prev.organelles ?? []).filter(o => o.id !== id),
        }));
        setSelectedOrganelleId(null);
    }, []);

    // Gene handlers
    const addGene = useCallback(() => {
        const newGene: Gene = {
            id: generateId("gene"),
            name: "New Gene",
            expressionLevel: 0.5,
        };
        setModel(prev => ({
            ...prev,
            genes: [...(prev.genes ?? []), newGene],
        }));
    }, []);

    const updateGene = useCallback((id: string, updates: Partial<Gene>) => {
        setModel(prev => ({
            ...prev,
            genes: (prev.genes ?? []).map(g =>
                g.id === id ? { ...g, ...updates } : g
            ),
        }));
    }, []);

    const removeGene = useCallback((id: string) => {
        setModel(prev => ({
            ...prev,
            genes: (prev.genes ?? []).filter(g => g.id !== id),
        }));
    }, []);

    // Process step handlers
    const addProcessStep = useCallback(() => {
        const steps = model.processSteps ?? [];
        const newStep: ProcessStep = {
            id: generateId("step"),
            process: "transcription",
            location: "nucleus",
            description: "",
            duration: 5,
        };
        setModel(prev => ({
            ...prev,
            processSteps: [...steps, newStep],
        }));
    }, [model.processSteps]);

    const updateProcessStep = useCallback((id: string, updates: Partial<ProcessStep>) => {
        setModel(prev => ({
            ...prev,
            processSteps: (prev.processSteps ?? []).map(s =>
                s.id === id ? { ...s, ...updates } : s
            ),
        }));
    }, []);

    const removeProcessStep = useCallback((id: string) => {
        setModel(prev => ({
            ...prev,
            processSteps: (prev.processSteps ?? []).filter(s => s.id !== id),
        }));
    }, []);

    // Save
    const handleSave = useCallback(() => {
        if (validationErrors.length > 0) {
            alert("Please fix validation errors before saving");
            return;
        }
        onSave?.(model as CellModelDefinition);
    }, [model, validationErrors, onSave]);

    const selectedOrganelle = (model.organelles ?? []).find(o => o.id === selectedOrganelleId);

    return (
        <div className="flex flex-col h-full bg-white rounded-lg border shadow-sm">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b">
                <div>
                    <h2 className="text-xl font-semibold text-gray-900">
                        Cell Diagram Editor
                    </h2>
                    <p className="text-sm text-gray-500">
                        Design cell biology simulations
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
                {(["diagram", "genes", "processes", "preview"] as const).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${activeTab === tab
                                ? "border-blue-600 text-blue-600"
                                : "border-transparent text-gray-500 hover:text-gray-700"
                            }`}
                    >
                        {tab === "diagram" && "Cell Diagram"}
                        {tab === "genes" && "Genes"}
                        {tab === "processes" && "Processes"}
                        {tab === "preview" && "Preview"}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-6">
                {activeTab === "diagram" && (
                    <div className="flex gap-6">
                        {/* Canvas */}
                        <div className="flex-1">
                            <div className="mb-4 flex items-center gap-4">
                                <div className="flex-1">
                                    <input
                                        type="text"
                                        value={model.name ?? ""}
                                        onChange={(e) => setModel(prev => ({ ...prev, name: e.target.value }))}
                                        placeholder="Cell name (e.g., Hepatocyte)"
                                        disabled={readOnly}
                                        className="w-full px-3 py-2 border rounded-md"
                                    />
                                </div>
                                <select
                                    value={model.cellType}
                                    onChange={(e) => setModel(prev => ({ ...prev, cellType: e.target.value as CellType, organelles: [] }))}
                                    disabled={readOnly}
                                    className="px-3 py-2 border rounded-md"
                                >
                                    {CELL_TYPES.map(t => (
                                        <option key={t.value} value={t.value}>{t.label}</option>
                                    ))}
                                </select>
                            </div>

                            <CellCanvas
                                cellType={model.cellType ?? "eukaryote-animal"}
                                organelles={model.organelles ?? []}
                                selectedOrganelleId={selectedOrganelleId}
                                onOrganelleClick={setSelectedOrganelleId}
                                onOrganelleMove={moveOrganelle}
                                width={model.canvasSize?.width ?? 600}
                                height={model.canvasSize?.height ?? 400}
                                readOnly={readOnly}
                            />
                        </div>

                        {/* Side Panel */}
                        <div className="w-64 space-y-4">
                            <OrganellePalette
                                cellType={model.cellType ?? "eukaryote-animal"}
                                onAddOrganelle={addOrganelle}
                                disabled={readOnly}
                            />

                            {selectedOrganelle && !readOnly && (
                                <div className="p-3 bg-blue-50 rounded-lg border border-blue-200">
                                    <h4 className="text-sm font-medium text-blue-900 mb-2">
                                        {ORGANELLE_DEFS[selectedOrganelle.type].label}
                                    </h4>
                                    <div className="space-y-2">
                                        <div>
                                            <label className="text-xs text-blue-700">Label</label>
                                            <input
                                                type="text"
                                                value={selectedOrganelle.label ?? ""}
                                                onChange={(e) => updateOrganelle(selectedOrganelle.id, { label: e.target.value })}
                                                placeholder="Custom label"
                                                className="w-full px-2 py-1 text-sm border rounded"
                                            />
                                        </div>
                                        <div>
                                            <label className="text-xs text-blue-700">Scale</label>
                                            <input
                                                type="range"
                                                min={0.5}
                                                max={2}
                                                step={0.1}
                                                value={selectedOrganelle.scale}
                                                onChange={(e) => updateOrganelle(selectedOrganelle.id, { scale: parseFloat(e.target.value) })}
                                                className="w-full"
                                            />
                                        </div>
                                        <label className="flex items-center gap-2 text-xs">
                                            <input
                                                type="checkbox"
                                                checked={selectedOrganelle.active ?? false}
                                                onChange={(e) => updateOrganelle(selectedOrganelle.id, { active: e.target.checked })}
                                            />
                                            Active (highlighted)
                                        </label>
                                        <button
                                            onClick={() => removeOrganelle(selectedOrganelle.id)}
                                            className="w-full px-2 py-1 text-xs text-red-600 border border-red-300 rounded hover:bg-red-50"
                                        >
                                            Remove
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {activeTab === "genes" && (
                    <div className="space-y-4">
                        <div className="flex items-center justify-between">
                            <h3 className="font-medium text-gray-900">Gene Expression</h3>
                            {!readOnly && (
                                <button
                                    onClick={addGene}
                                    className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    + Add Gene
                                </button>
                            )}
                        </div>

                        <div className="space-y-3">
                            {(model.genes ?? []).map(gene => (
                                <div key={gene.id} className="p-4 border rounded-lg">
                                    <div className="flex items-center gap-4">
                                        <div className="flex-1">
                                            <input
                                                type="text"
                                                value={gene.name}
                                                onChange={(e) => updateGene(gene.id, { name: e.target.value })}
                                                placeholder="Gene name"
                                                disabled={readOnly}
                                                className="w-full px-3 py-1 border rounded font-mono"
                                            />
                                        </div>
                                        <div className="w-48">
                                            <label className="text-xs text-gray-500">Expression Level</label>
                                            <input
                                                type="range"
                                                min={0}
                                                max={1}
                                                step={0.1}
                                                value={gene.expressionLevel}
                                                onChange={(e) => updateGene(gene.id, { expressionLevel: parseFloat(e.target.value) })}
                                                disabled={readOnly}
                                                className="w-full"
                                            />
                                        </div>
                                        <span className="text-sm font-medium w-12">
                                            {Math.round(gene.expressionLevel * 100)}%
                                        </span>
                                        {!readOnly && (
                                            <button
                                                onClick={() => removeGene(gene.id)}
                                                className="p-1 text-red-500 hover:bg-red-50 rounded"
                                            >
                                                ✕
                                            </button>
                                        )}
                                    </div>
                                    <div className="mt-2">
                                        <input
                                            type="text"
                                            value={gene.product ?? ""}
                                            onChange={(e) => updateGene(gene.id, { product: e.target.value })}
                                            placeholder="Gene product (e.g., Insulin)"
                                            disabled={readOnly}
                                            className="w-full px-3 py-1 text-sm border rounded"
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>

                        {(model.genes ?? []).length === 0 && (
                            <div className="text-center py-8 text-gray-500">
                                <p>No genes defined yet.</p>
                                <p className="text-sm">Add genes to model expression levels.</p>
                            </div>
                        )}
                    </div>
                )}

                {activeTab === "processes" && (
                    <div className="space-y-4">
                        <div className="flex items-center justify-between">
                            <h3 className="font-medium text-gray-900">Biological Processes</h3>
                            {!readOnly && (
                                <button
                                    onClick={addProcessStep}
                                    className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    + Add Step
                                </button>
                            )}
                        </div>

                        <div className="space-y-3">
                            {(model.processSteps ?? []).map((step, index) => (
                                <div key={step.id} className="p-4 border rounded-lg">
                                    <div className="flex items-start gap-4">
                                        <div className="flex items-center justify-center w-8 h-8 bg-blue-100 text-blue-600 font-bold rounded-full">
                                            {index + 1}
                                        </div>
                                        <div className="flex-1 space-y-3">
                                            <div className="grid grid-cols-2 gap-3">
                                                <select
                                                    value={step.process}
                                                    onChange={(e) => {
                                                        const process = e.target.value as BiologicalProcess;
                                                        const def = PROCESS_DEFS.find(p => p.value === process);
                                                        updateProcessStep(step.id, {
                                                            process,
                                                            location: def?.location ?? "cytoplasm"
                                                        });
                                                    }}
                                                    disabled={readOnly}
                                                    className="px-3 py-1.5 border rounded"
                                                >
                                                    {PROCESS_DEFS.map(p => (
                                                        <option key={p.value} value={p.value}>
                                                            {p.label} - {p.description}
                                                        </option>
                                                    ))}
                                                </select>
                                                <div className="flex items-center gap-2">
                                                    <label className="text-sm text-gray-500">Duration:</label>
                                                    <input
                                                        type="number"
                                                        min={1}
                                                        value={step.duration}
                                                        onChange={(e) => updateProcessStep(step.id, { duration: parseInt(e.target.value) || 5 })}
                                                        disabled={readOnly}
                                                        className="w-20 px-2 py-1 border rounded"
                                                    />
                                                    <span className="text-sm text-gray-500">sec</span>
                                                </div>
                                            </div>
                                            <textarea
                                                value={step.description}
                                                onChange={(e) => updateProcessStep(step.id, { description: e.target.value })}
                                                placeholder="Describe this step..."
                                                disabled={readOnly}
                                                rows={2}
                                                className="w-full px-3 py-2 text-sm border rounded"
                                            />
                                        </div>
                                        {!readOnly && (
                                            <button
                                                onClick={() => removeProcessStep(step.id)}
                                                className="p-1 text-red-500 hover:bg-red-50 rounded"
                                            >
                                                ✕
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>

                        {(model.processSteps ?? []).length === 0 && (
                            <div className="text-center py-8 text-gray-500">
                                <p>No process steps defined.</p>
                                <p className="text-sm">Add steps to create a biological pathway.</p>
                            </div>
                        )}
                    </div>
                )}

                {activeTab === "preview" && (
                    <div className="space-y-4">
                        <div className="p-4 bg-green-50 rounded-lg">
                            <h3 className="font-medium text-green-900">{model.name || "Unnamed Cell"}</h3>
                            <p className="text-sm text-green-700">
                                {CELL_TYPES.find(t => t.value === model.cellType)?.label}
                            </p>
                        </div>

                        <CellCanvas
                            cellType={model.cellType ?? "eukaryote-animal"}
                            organelles={model.organelles ?? []}
                            selectedOrganelleId={null}
                            onOrganelleClick={() => { }}
                            onOrganelleMove={() => { }}
                            width={model.canvasSize?.width ?? 600}
                            height={model.canvasSize?.height ?? 400}
                            readOnly={true}
                        />

                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Summary</h4>
                                <dl className="space-y-1">
                                    <div className="flex"><dt className="w-24 text-gray-500">Organelles:</dt><dd>{(model.organelles ?? []).length}</dd></div>
                                    <div className="flex"><dt className="w-24 text-gray-500">Genes:</dt><dd>{(model.genes ?? []).length}</dd></div>
                                    <div className="flex"><dt className="w-24 text-gray-500">Steps:</dt><dd>{(model.processSteps ?? []).length}</dd></div>
                                </dl>
                            </div>
                            <div>
                                <h4 className="font-medium text-gray-700 mb-2">Organelles</h4>
                                <ul className="space-y-1 text-sm">
                                    {(model.organelles ?? []).map(o => (
                                        <li key={o.id} className="flex items-center gap-2">
                                            <span
                                                className="w-3 h-3 rounded"
                                                style={{ backgroundColor: ORGANELLE_DEFS[o.type].color }}
                                            />
                                            {o.label || ORGANELLE_DEFS[o.type].label}
                                        </li>
                                    ))}
                                </ul>
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

export default CellDiagramEditor;
