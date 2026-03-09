/**
 * Simulation Block Editor Component
 * 
 * Provides a UI for creating and editing simulation content blocks
 * with JSON editor, preview panel, and domain-specific form fields.
 * 
 * @doc.type component
 * @doc.purpose CMS simulation block authoring
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback, useMemo } from "react";

/** Simulation domain types */
type SimulationDomain =
    | "CS_DISCRETE"
    | "PHYSICS"
    | "CHEMISTRY"
    | "BIOLOGY"
    | "MEDICINE"
    | "ECONOMICS"
    | "ENGINEERING"
    | "MATHEMATICS";

/** Simulation entity interface */
interface SimEntity {
    id: string;
    type: string;
    label?: string;
    position?: { x: number; y: number };
    properties?: Record<string, unknown>;
}

/** Simulation step interface */
interface SimulationStep {
    id: string;
    action: string;
    targetEntityId?: string;
    params?: Record<string, unknown>;
    duration?: number;
    narrative?: string;
}

/** Simulation manifest interface */
interface SimulationManifest {
    domain: SimulationDomain;
    title: string;
    description?: string;
    initialEntities: SimEntity[];
    steps: SimulationStep[];
    canvas?: {
        width: number;
        height: number;
        backgroundColor?: string;
    };
    playback?: {
        defaultSpeed: number;
        allowScrubbing: boolean;
        autoPlay: boolean;
    };
}

/** Simulation block payload */
interface SimulationBlockPayload {
    manifestId?: string;
    inlineManifest?: SimulationManifest;
    display?: {
        showControls?: boolean;
        showTimeline?: boolean;
        showNarration?: boolean;
        aspectRatio?: string;
    };
    tutorContext?: {
        enabled?: boolean;
    };
}

interface SimulationBlockEditorProps {
    initialPayload?: SimulationBlockPayload;
    onSave: (payload: SimulationBlockPayload) => void;
    onCancel: () => void;
    readOnly?: boolean;
}

const DOMAIN_OPTIONS: Array<{ value: SimulationDomain; label: string; description: string }> = [
    { value: "CS_DISCRETE", label: "Algorithms & Data Structures", description: "Sorting, graphs, trees, linked lists" },
    { value: "PHYSICS", label: "Physics", description: "Mechanics, waves, thermodynamics" },
    { value: "CHEMISTRY", label: "Chemistry", description: "Reactions, molecular structures" },
    { value: "BIOLOGY", label: "Biology", description: "Cellular, molecular biology" },
    { value: "MEDICINE", label: "Medicine & Pharmacology", description: "PK/PD, epidemiology" },
    { value: "ECONOMICS", label: "Economics", description: "System dynamics, markets" },
    { value: "ENGINEERING", label: "Engineering", description: "Circuits, mechanics" },
    { value: "MATHEMATICS", label: "Mathematics", description: "Geometry, calculus" },
];

const DEFAULT_MANIFEST: SimulationBlockPayload["inlineManifest"] = {
    domain: "CS_DISCRETE",
    title: "New Simulation",
    description: "",
    initialEntities: [],
    steps: [],
    canvas: {
        width: 800,
        height: 600,
        backgroundColor: "#f8fafc",
    },
    playback: {
        defaultSpeed: 1,
        allowScrubbing: true,
        autoPlay: false,
    },
};

export const SimulationBlockEditor = ({
    initialPayload,
    onSave,
    onCancel,
    readOnly = false,
}: SimulationBlockEditorProps) => {
    const [activeTab, setActiveTab] = useState<"visual" | "json" | "preview">("visual");
    const [payload, setPayload] = useState<SimulationBlockPayload>(
        initialPayload || {
            inlineManifest: { ...DEFAULT_MANIFEST },
            display: {
                showControls: true,
                showTimeline: true,
                showNarration: true,
                aspectRatio: "16:9",
            },
            tutorContext: {
                enabled: true,
            },
        }
    );
    const [jsonError, setJsonError] = useState<string | null>(null);
    const [jsonText, setJsonText] = useState<string>(
        JSON.stringify(payload.inlineManifest, null, 2)
    );

    const manifest = payload.inlineManifest;

    // Convert inline manifest to full manifest for preview
    const previewManifest: SimulationManifest | null = useMemo(() => {
        if (!manifest) return null;

        return {
            domain: manifest.domain,
            title: manifest.title,
            description: manifest.description,
            canvas: manifest.canvas || { width: 800, height: 600 },
            playback: manifest.playback || { defaultSpeed: 1, allowScrubbing: true, autoPlay: false },
            initialEntities: manifest.initialEntities as SimEntity[],
            steps: manifest.steps as SimulationStep[],
        };
    }, [manifest]);

    const handleDomainChange = useCallback((domain: SimulationDomain) => {
        setPayload((prev: SimulationBlockPayload) => ({
            ...prev,
            inlineManifest: {
                ...prev.inlineManifest!,
                domain,
            },
        }));
    }, []);

    const handleTitleChange = useCallback((title: string) => {
        setPayload((prev: SimulationBlockPayload) => ({
            ...prev,
            inlineManifest: {
                ...prev.inlineManifest!,
                title,
            },
        }));
    }, []);

    const handleDescriptionChange = useCallback((description: string) => {
        setPayload((prev: SimulationBlockPayload) => ({
            ...prev,
            inlineManifest: {
                ...prev.inlineManifest!,
                description,
            },
        }));
    }, []);

    const handleJsonChange = useCallback((text: string) => {
        setJsonText(text);
        try {
            const parsed = JSON.parse(text);
            setPayload((prev: SimulationBlockPayload) => ({
                ...prev,
                inlineManifest: parsed,
            }));
            setJsonError(null);
        } catch (e) {
            setJsonError((e as Error).message);
        }
    }, []);

    const handleDisplayChange = useCallback((key: keyof NonNullable<SimulationBlockPayload["display"]>, value: boolean | string) => {
        setPayload((prev: SimulationBlockPayload) => ({
            ...prev,
            display: {
                ...prev.display,
                [key]: value,
            },
        }));
    }, []);

    const handleSave = useCallback(() => {
        if (jsonError) {
            alert("Please fix JSON errors before saving");
            return;
        }
        onSave(payload);
    }, [payload, jsonError, onSave]);

    const addEntity = useCallback(() => {
        const newEntity: SimEntity = {
            id: `entity-${Date.now()}`,
            type: "node",
            position: { x: 100, y: 100 },
            label: "New Entity",
        };

        setPayload((prev: SimulationBlockPayload) => ({
            ...prev,
            inlineManifest: {
                ...prev.inlineManifest!,
                initialEntities: [...(prev.inlineManifest?.initialEntities || []), newEntity],
            },
        }));
    }, []);

    const addStep = useCallback(() => {
        const steps = payload.inlineManifest?.steps || [];
        const newStep: SimulationStep = {
            id: `step-${Date.now()}`,
            action: "HIGHLIGHT",
            duration: 1000,
            narrative: `Step ${steps.length + 1}`,
        };

        setPayload((prev: SimulationBlockPayload) => ({
            ...prev,
            inlineManifest: {
                ...prev.inlineManifest!,
                steps: [...steps, newStep],
            },
        }));
    }, [payload.inlineManifest?.steps]);

    return (
        <div className="flex flex-col h-full bg-white rounded-lg shadow-lg">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b">
                <h2 className="text-xl font-semibold text-gray-900">
                    Simulation Block Editor
                </h2>
                <div className="flex items-center gap-3">
                    <button
                        onClick={onCancel}
                        className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={readOnly || !!jsonError}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        Save Block
                    </button>
                </div>
            </div>

            {/* Tab Navigation */}
            <div className="flex border-b px-6">
                {(["visual", "json", "preview"] as const).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${activeTab === tab
                                ? "border-blue-600 text-blue-600"
                                : "border-transparent text-gray-500 hover:text-gray-700"
                            }`}
                    >
                        {tab === "visual" && "Visual Editor"}
                        {tab === "json" && "JSON Editor"}
                        {tab === "preview" && "Preview"}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto">
                {activeTab === "visual" && (
                    <div className="p-6 space-y-6">
                        {/* Domain Selection */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Simulation Domain
                            </label>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                                {DOMAIN_OPTIONS.map((option) => (
                                    <button
                                        key={option.value}
                                        onClick={() => handleDomainChange(option.value)}
                                        disabled={readOnly}
                                        className={`p-3 text-left border rounded-lg transition-colors ${manifest?.domain === option.value
                                                ? "border-blue-600 bg-blue-50"
                                                : "border-gray-200 hover:border-gray-300"
                                            }`}
                                    >
                                        <div className="font-medium text-sm text-gray-900">
                                            {option.label}
                                        </div>
                                        <div className="text-xs text-gray-500 mt-1">
                                            {option.description}
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Title & Description */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Title
                                </label>
                                <input
                                    type="text"
                                    value={manifest?.title || ""}
                                    onChange={(e) => handleTitleChange(e.target.value)}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                    placeholder="Enter simulation title"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Description
                                </label>
                                <input
                                    type="text"
                                    value={manifest?.description || ""}
                                    onChange={(e) => handleDescriptionChange(e.target.value)}
                                    disabled={readOnly}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                    placeholder="Brief description"
                                />
                            </div>
                        </div>

                        {/* Entities Section */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <label className="block text-sm font-medium text-gray-700">
                                    Initial Entities ({manifest?.initialEntities?.length || 0})
                                </label>
                                <button
                                    onClick={addEntity}
                                    disabled={readOnly}
                                    className="px-3 py-1 text-sm font-medium text-blue-600 hover:text-blue-700"
                                >
                                    + Add Entity
                                </button>
                            </div>
                            <div className="space-y-2 max-h-48 overflow-auto border rounded-lg p-3">
                                {(manifest?.initialEntities || []).length === 0 ? (
                                    <p className="text-sm text-gray-500 italic">
                                        No entities yet. Add entities to define the initial simulation state.
                                    </p>
                                ) : (
                                    (manifest?.initialEntities || []).map((entity) => (
                                        <div
                                            key={entity.id}
                                            className="flex items-center justify-between p-2 bg-gray-50 rounded"
                                        >
                                            <div className="flex items-center gap-2">
                                                <span className="text-xs font-mono text-gray-500">
                                                    {entity.id}
                                                </span>
                                                <span className="text-sm text-gray-700">
                                                    {entity.type}
                                                </span>
                                                <span className="text-xs text-gray-400">
                                                    {entity.position ? `(${entity.position.x}, ${entity.position.y})` : ''}
                                                </span>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>

                        {/* Steps Section */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <label className="block text-sm font-medium text-gray-700">
                                    Simulation Steps ({manifest?.steps?.length || 0})
                                </label>
                                <button
                                    onClick={addStep}
                                    disabled={readOnly}
                                    className="px-3 py-1 text-sm font-medium text-blue-600 hover:text-blue-700"
                                >
                                    + Add Step
                                </button>
                            </div>
                            <div className="space-y-2 max-h-48 overflow-auto border rounded-lg p-3">
                                {(manifest?.steps || []).length === 0 ? (
                                    <p className="text-sm text-gray-500 italic">
                                        No steps yet. Add steps to define the simulation sequence.
                                    </p>
                                ) : (
                                    (manifest?.steps || []).map((step, idx) => (
                                        <div
                                            key={step.id}
                                            className="flex items-center justify-between p-2 bg-gray-50 rounded"
                                        >
                                            <div className="flex items-center gap-2">
                                                <span className="w-6 h-6 flex items-center justify-center bg-blue-100 text-blue-600 text-xs font-medium rounded-full">
                                                    {idx + 1}
                                                </span>
                                                <span className="text-sm text-gray-700">
                                                    {step.narrative || step.action || `Step ${idx + 1}`}
                                                </span>
                                                <span className="text-xs text-gray-400">
                                                    {step.duration ? `${step.duration}ms` : ''}
                                                </span>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>

                        {/* Display Options */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-3">
                                Display Options
                            </label>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <label className="flex items-center gap-2">
                                    <input
                                        type="checkbox"
                                        checked={payload.display?.showControls ?? true}
                                        onChange={(e) => handleDisplayChange("showControls", e.target.checked)}
                                        disabled={readOnly}
                                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-gray-700">Show Controls</span>
                                </label>
                                <label className="flex items-center gap-2">
                                    <input
                                        type="checkbox"
                                        checked={payload.display?.showTimeline ?? true}
                                        onChange={(e) => handleDisplayChange("showTimeline", e.target.checked)}
                                        disabled={readOnly}
                                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-gray-700">Show Timeline</span>
                                </label>
                                <label className="flex items-center gap-2">
                                    <input
                                        type="checkbox"
                                        checked={payload.display?.showNarration ?? true}
                                        onChange={(e) => handleDisplayChange("showNarration", e.target.checked)}
                                        disabled={readOnly}
                                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-gray-700">Show Narration</span>
                                </label>
                                <label className="flex items-center gap-2">
                                    <input
                                        type="checkbox"
                                        checked={payload.tutorContext?.enabled ?? true}
                                        onChange={(e) => setPayload(prev => ({
                                            ...prev,
                                            tutorContext: { ...prev.tutorContext, enabled: e.target.checked }
                                        }))}
                                        disabled={readOnly}
                                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-gray-700">AI Tutor</span>
                                </label>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === "json" && (
                    <div className="p-6">
                        <div className="mb-3 flex items-center justify-between">
                            <label className="block text-sm font-medium text-gray-700">
                                Manifest JSON
                            </label>
                            {jsonError && (
                                <span className="text-sm text-red-600">
                                    Error: {jsonError}
                                </span>
                            )}
                        </div>
                        <textarea
                            value={jsonText}
                            onChange={(e) => handleJsonChange(e.target.value)}
                            disabled={readOnly}
                            className={`w-full h-96 font-mono text-sm p-4 border rounded-lg focus:ring-blue-500 focus:border-blue-500 ${jsonError ? "border-red-300 bg-red-50" : "border-gray-300"
                                }`}
                            spellCheck={false}
                        />
                        <p className="mt-2 text-xs text-gray-500">
                            Edit the raw JSON manifest. Changes will sync with the visual editor.
                        </p>
                    </div>
                )}

                {activeTab === "preview" && (
                    <div className="p-6">
                        <div className="mb-4">
                            <h3 className="text-sm font-medium text-gray-700 mb-2">
                                Preview: {manifest?.title || "Untitled"}
                            </h3>
                            <p className="text-xs text-gray-500">
                                Domain: {manifest?.domain} •
                                {manifest?.initialEntities?.length || 0} entities •
                                {manifest?.steps?.length || 0} steps
                            </p>
                        </div>
                        <div className="border rounded-lg overflow-hidden bg-gray-100">
                            {previewManifest ? (
                                <div className="h-96 flex flex-col items-center justify-center text-gray-600 bg-gradient-to-br from-blue-50 to-indigo-50">
                                    <div className="text-4xl mb-4">🎮</div>
                                    <div className="text-lg font-medium mb-2">Simulation Preview</div>
                                    <div className="text-sm text-gray-500 mb-4">
                                        {manifest?.initialEntities?.length || 0} entities • {manifest?.steps?.length || 0} steps
                                    </div>
                                    <div className="text-xs text-gray-400 max-w-md text-center px-4">
                                        Full simulation preview will render in the module player.
                                        Save this block to test the simulation in context.
                                    </div>
                                </div>
                            ) : (
                                <div className="h-96 flex items-center justify-center text-gray-500">
                                    Add entities and steps to see preview
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default SimulationBlockEditor;
