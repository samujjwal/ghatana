/**
 * CMS Module Editor Page
 * 
 * Provides a full-featured CMS for creating and editing learning modules
 * with support for various content block types including simulations.
 * 
 * @doc.type page
 * @doc.purpose Content authoring UI for educators
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState, useCallback, useEffect, type ChangeEvent, type KeyboardEvent } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Box, Card, Button, Text, Heading } from "@/components/ui";
import { SimulationBlockEditor } from "../components/cms/SimulationBlockEditor";
import { tutorputorClient } from "../api/tutorputorClient";

/** Content block type options */
type ContentBlockType =
    | "text"
    | "rich_text"
    | "video"
    | "image"
    | "simulation"
    | "exercise"
    | "assessment_item_ref"
    | "ai_tutor_prompt";

/** Learning objective taxonomy levels */
type TaxonomyLevel = "REMEMBER" | "UNDERSTAND" | "APPLY" | "ANALYZE" | "EVALUATE" | "CREATE";

/** Learning objective interface */
interface LearningObjective {
    label: string;
    taxonomyLevel: TaxonomyLevel;
}

/** Content block interface */
interface ContentBlock {
    id: string;
    orderIndex: number;
    blockType: ContentBlockType;
    payload: unknown;
}

/** Module draft input for create */
interface ModuleDraftInput {
    slug: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    estimatedTimeMinutes: number;
    tags: string[];
    learningObjectives: LearningObjective[];
    contentBlocks: ContentBlock[];
    prerequisites: string[];
}

/** Module draft patch for update */
interface ModuleDraftPatch {
    title?: string;
    description?: string;
    difficulty?: string;
    estimatedTimeMinutes?: number;
    tags?: string[];
    learningObjectives?: LearningObjective[];
    contentBlocks?: ContentBlock[];
}

const BLOCK_TYPE_OPTIONS: Array<{ value: ContentBlockType; label: string; icon: string }> = [
    { value: "text", label: "Text", icon: "📝" },
    { value: "rich_text", label: "Rich Text", icon: "📄" },
    { value: "video", label: "Video", icon: "🎥" },
    { value: "image", label: "Image", icon: "🖼️" },
    { value: "simulation", label: "Simulation", icon: "🎮" },
    { value: "exercise", label: "Exercise", icon: "✍️" },
    { value: "assessment_item_ref", label: "Assessment", icon: "📋" },
    { value: "ai_tutor_prompt", label: "AI Tutor", icon: "🤖" },
];

const DIFFICULTY_OPTIONS = ["beginner", "intermediate", "advanced", "expert"] as const;
const DOMAIN_OPTIONS = [
    "computer_science",
    "physics",
    "chemistry",
    "biology",
    "mathematics",
    "economics",
    "engineering",
] as const;

interface DraftState {
    slug: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    estimatedTimeMinutes: number;
    tags: string[];
    learningObjectives: Array<{
        label: string;
        taxonomyLevel: TaxonomyLevel;
    }>;
    contentBlocks: ContentBlock[];
}

const INITIAL_DRAFT: DraftState = {
    slug: "",
    title: "",
    description: "",
    domain: "computer_science",
    difficulty: "beginner",
    estimatedTimeMinutes: 30,
    tags: [],
    learningObjectives: [],
    contentBlocks: [],
};

export function CMSModuleEditorPage() {
    const { moduleId } = useParams<{ moduleId?: string }>();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const templateId = searchParams.get("template");
    const queryClient = useQueryClient();
    const isEditing = Boolean(moduleId);

    const [draft, setDraft] = useState<DraftState>(INITIAL_DRAFT);
    const [editingBlockIndex, setEditingBlockIndex] = useState<number | null>(null);
    const [showBlockPicker, setShowBlockPicker] = useState(false);
    const [tagInput, setTagInput] = useState("");

    // Fetch existing module if editing
    const { data: existingModule, isLoading: isLoadingModule } = useQuery({
        queryKey: ["cms", "module", moduleId],
        queryFn: async () => {
            if (!moduleId) return null;
            const response = await tutorputorClient.get<{ items: Array<{ id: string; slug: string; title: string; description?: string; domain: string; difficulty: string; estimatedTimeMinutes: number; tags: string[]; learningObjectives?: LearningObjective[]; contentBlocks?: ContentBlock[]; }>; nextCursor: string | null }>(`/cms/modules?status=DRAFT`);
            const modules = response.data.items || [];
            return modules.find((m) => m.id === moduleId) || null;
        },
        enabled: isEditing,
    });

    // Fetch template if provided
    const { data: templateData } = useQuery({
        queryKey: ["template", templateId],
        queryFn: async () => {
            if (!templateId) return null;
            const response = await tutorputorClient.get<any>(`/templates/${templateId}`);
            return response.data;
        },
        enabled: !!templateId && !isEditing,
    });

    // Initialize draft from template
    useEffect(() => {
        if (templateData && !isEditing) {
            setDraft((prev) => ({
                ...prev,
                title: `Simulation: ${templateData.title}`,
                description: templateData.description || "",
                domain: templateData.domain || "computer_science",
                difficulty: templateData.difficulty?.toLowerCase() || "beginner",
                contentBlocks: [
                    {
                        id: crypto.randomUUID(),
                        orderIndex: 0,
                        blockType: "simulation",
                        payload: {
                            templateId: templateData.id,
                            config: templateData.defaultConfig || {},
                        },
                    },
                ],
            }));
        }
    }, [templateData, isEditing]);

    // Initialize draft from existing module
    useEffect(() => {
        if (existingModule) {
            setDraft({
                slug: existingModule.slug,
                title: existingModule.title,
                description: existingModule.description || "",
                domain: existingModule.domain,
                difficulty: existingModule.difficulty,
                estimatedTimeMinutes: existingModule.estimatedTimeMinutes,
                tags: existingModule.tags || [],
                learningObjectives: existingModule.learningObjectives || [],
                contentBlocks: existingModule.contentBlocks || [],
            });
        }
    }, [existingModule]);

    // Create mutation
    const createMutation = useMutation({
        mutationFn: async (input: ModuleDraftInput) => {
            const response = await tutorputorClient.post<{ id: string }>("/cms/modules", input);
            return response.data;
        },
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ["cms", "modules"] });
            navigate(`/cms/modules/${data.id}/edit`);
        },
    });

    // Update mutation
    const updateMutation = useMutation({
        mutationFn: async (patch: ModuleDraftPatch) => {
            const response = await tutorputorClient.patch<{ id: string }>(`/cms/modules/${moduleId}`, patch);
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["cms", "modules"] });
        },
    });

    // Publish mutation
    const publishMutation = useMutation({
        mutationFn: async () => {
            const response = await tutorputorClient.post<{ id: string }>(`/cms/modules/${moduleId}/publish`);
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["cms", "modules"] });
            navigate("/cms/modules");
        },
    });

    const handleSave = useCallback(() => {
        if (isEditing) {
            updateMutation.mutate({
                title: draft.title,
                description: draft.description,
                difficulty: draft.difficulty as any,
                estimatedTimeMinutes: draft.estimatedTimeMinutes,
                tags: draft.tags,
                learningObjectives: draft.learningObjectives,
                contentBlocks: draft.contentBlocks,
            });
        } else {
            createMutation.mutate({
                slug: draft.slug,
                title: draft.title,
                description: draft.description,
                domain: draft.domain as any,
                difficulty: draft.difficulty as any,
                estimatedTimeMinutes: draft.estimatedTimeMinutes,
                tags: draft.tags,
                learningObjectives: draft.learningObjectives,
                contentBlocks: draft.contentBlocks,
                prerequisites: [],
            });
        }
    }, [draft, isEditing, createMutation, updateMutation]);

    const handlePublish = useCallback(() => {
        if (confirm("Are you sure you want to publish this module?")) {
            publishMutation.mutate();
        }
    }, [publishMutation]);

    const handleAddBlock = useCallback((blockType: ContentBlockType) => {
        const newBlock: ContentBlock = {
            id: `block-${Date.now()}`,
            orderIndex: draft.contentBlocks.length,
            blockType,
            payload: blockType === "simulation" ? {
                inlineManifest: {
                    domain: "CS_DISCRETE",
                    title: "New Simulation",
                    initialEntities: [],
                    steps: [],
                },
                display: { showControls: true, showTimeline: true },
            } : blockType === "text" ? { markdown: "" } : {},
        };

        setDraft((prev) => ({
            ...prev,
            contentBlocks: [...prev.contentBlocks, newBlock],
        }));
        setShowBlockPicker(false);

        if (blockType === "simulation") {
            setEditingBlockIndex(draft.contentBlocks.length);
        }
    }, [draft.contentBlocks.length]);

    const handleUpdateBlock = useCallback((index: number, payload: unknown) => {
        setDraft((prev) => ({
            ...prev,
            contentBlocks: prev.contentBlocks.map((block, i) =>
                i === index ? { ...block, payload } : block
            ),
        }));
        setEditingBlockIndex(null);
    }, []);

    const handleRemoveBlock = useCallback((index: number) => {
        if (confirm("Remove this block?")) {
            setDraft((prev) => ({
                ...prev,
                contentBlocks: prev.contentBlocks
                    .filter((_, i) => i !== index)
                    .map((block, i) => ({ ...block, orderIndex: i })),
            }));
        }
    }, []);

    const handleMoveBlock = useCallback((index: number, direction: "up" | "down") => {
        const newIndex = direction === "up" ? index - 1 : index + 1;
        if (newIndex < 0 || newIndex >= draft.contentBlocks.length) return;

        setDraft((prev) => {
            const blocks = [...prev.contentBlocks];
            [blocks[index], blocks[newIndex]] = [blocks[newIndex], blocks[index]];
            return {
                ...prev,
                contentBlocks: blocks.map((block, i) => ({ ...block, orderIndex: i })),
            };
        });
    }, [draft.contentBlocks.length]);

    const handleAddTag = useCallback(() => {
        const tag = tagInput.trim();
        if (tag && !draft.tags.includes(tag)) {
            setDraft((prev) => ({
                ...prev,
                tags: [...prev.tags, tag],
            }));
            setTagInput("");
        }
    }, [tagInput, draft.tags]);

    const handleRemoveTag = useCallback((tag: string) => {
        setDraft((prev) => ({
            ...prev,
            tags: prev.tags.filter((t) => t !== tag),
        }));
    }, []);

    const handleAddObjective = useCallback(() => {
        setDraft((prev) => ({
            ...prev,
            learningObjectives: [
                ...prev.learningObjectives,
                { label: "", taxonomyLevel: "UNDERSTAND" as TaxonomyLevel },
            ],
        }));
    }, []);

    const handleUpdateObjective = useCallback((
        index: number,
        field: "label" | "taxonomyLevel",
        value: string
    ) => {
        setDraft((prev) => ({
            ...prev,
            learningObjectives: prev.learningObjectives.map((obj, i) =>
                i === index ? { ...obj, [field]: value } : obj
            ),
        }));
    }, []);

    const handleRemoveObjective = useCallback((index: number) => {
        setDraft((prev) => ({
            ...prev,
            learningObjectives: prev.learningObjectives.filter((_, i) => i !== index),
        }));
    }, []);

    if (isLoadingModule) {
        return (
            <Box className="p-8">
                <Text>Loading module...</Text>
            </Box>
        );
    }

    // Show simulation block editor in modal
    if (editingBlockIndex !== null && draft.contentBlocks[editingBlockIndex]?.blockType === "simulation") {
        return (
            <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
                <div className="w-full max-w-6xl h-[90vh] bg-white dark:bg-gray-900 rounded-lg overflow-hidden">
                    <SimulationBlockEditor
                        initialPayload={draft.contentBlocks[editingBlockIndex].payload as any}
                        onSave={(payload) => handleUpdateBlock(editingBlockIndex, payload)}
                        onCancel={() => setEditingBlockIndex(null)}
                    />
                </div>
            </div>
        );
    }

    return (
        <Box>
            {/* Header */}
            <div className="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 sticky top-0 z-10">
                <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
                    <div>
                        <Heading level={1} className="text-xl font-semibold text-gray-900 dark:text-white">
                            {isEditing ? "Edit Module" : "Create New Module"}
                        </Heading>
                        <Text className="text-sm text-gray-500 dark:text-gray-400">
                            {isEditing ? `Editing: ${draft.title || "Untitled"}` : "Draft a new learning module"}
                        </Text>
                    </div>
                    <div className="flex items-center gap-3">
                        <Button
                            onClick={() => navigate("/cms/modules")}
                            variant="outline"
                            tone="neutral"
                        >
                            Cancel
                        </Button>
                        <Button
                            onClick={handleSave}
                            disabled={createMutation.isPending || updateMutation.isPending}
                            tone="primary"
                        >
                            {createMutation.isPending || updateMutation.isPending ? "Saving..." : "Save Draft"}
                        </Button>
                        {isEditing && (
                            <Button
                                onClick={handlePublish}
                                disabled={publishMutation.isPending}
                                tone="success"
                            >
                                {publishMutation.isPending ? "Publishing..." : "Publish"}
                            </Button>
                        )}
                    </div>
                </div>
            </div>

            {/* Content */}
            <div className="max-w-6xl mx-auto px-6 py-8">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    {/* Main Content */}
                    <div className="lg:col-span-2 space-y-6">
                        {/* Basic Info */}
                        <Card className="p-6">
                            <Heading level={2} className="text-lg font-medium text-gray-900 mb-4">
                                Basic Information
                            </Heading>
                            <div className="space-y-4">
                                {!isEditing && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            Slug (URL identifier)
                                        </label>
                                        <input
                                            value={draft.slug}
                                            onChange={(e: ChangeEvent<HTMLInputElement>) => setDraft((prev) => ({ ...prev, slug: e.target.value }))}
                                            placeholder="my-module-slug"
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                        />
                                    </div>
                                )}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Title
                                    </label>
                                    <input
                                        value={draft.title}
                                        onChange={(e: ChangeEvent<HTMLInputElement>) => setDraft((prev) => ({ ...prev, title: e.target.value }))}
                                        placeholder="Module Title"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Description
                                    </label>
                                    <textarea
                                        value={draft.description}
                                        onChange={(e) => setDraft((prev) => ({ ...prev, description: e.target.value }))}
                                        placeholder="Brief description of the module..."
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                        rows={3}
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            Domain
                                        </label>
                                        <select
                                            value={draft.domain}
                                            onChange={(e) => setDraft((prev) => ({ ...prev, domain: e.target.value }))}
                                            disabled={isEditing}
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                        >
                                            {DOMAIN_OPTIONS.map((domain) => (
                                                <option key={domain} value={domain}>
                                                    {domain.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            Difficulty
                                        </label>
                                        <select
                                            value={draft.difficulty}
                                            onChange={(e) => setDraft((prev) => ({ ...prev, difficulty: e.target.value }))}
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                        >
                                            {DIFFICULTY_OPTIONS.map((diff) => (
                                                <option key={diff} value={diff}>
                                                    {diff.charAt(0).toUpperCase() + diff.slice(1)}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Estimated Time (minutes)
                                    </label>
                                    <input
                                        type="number"
                                        value={draft.estimatedTimeMinutes}
                                        onChange={(e: ChangeEvent<HTMLInputElement>) => setDraft((prev) => ({ ...prev, estimatedTimeMinutes: parseInt(e.target.value) || 0 }))}
                                        min={1}
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                    />
                                </div>
                            </div>
                        </Card>

                        {/* Content Blocks */}
                        <Card className="p-6">
                            <div className="flex items-center justify-between mb-4">
                                <Heading level={2} className="text-lg font-medium text-gray-900">
                                    Content Blocks
                                </Heading>
                                <Button
                                    onClick={() => setShowBlockPicker(true)}
                                    variant="outline"
                                    tone="neutral"
                                    size="sm"
                                >
                                    + Add Block
                                </Button>
                            </div>

                            {draft.contentBlocks.length === 0 ? (
                                <div className="text-center py-12 bg-gray-50 rounded-lg border-2 border-dashed border-gray-200">
                                    <Text className="text-gray-500 mb-3">
                                        No content blocks yet
                                    </Text>
                                    <Button
                                        onClick={() => setShowBlockPicker(true)}
                                        variant="outline"
                                        tone="primary"
                                        size="sm"
                                    >
                                        Add Your First Block
                                    </Button>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {draft.contentBlocks.map((block, index) => (
                                        <div
                                            key={block.id}
                                            className="flex items-center gap-3 p-4 bg-gray-50 rounded-lg border border-gray-200"
                                        >
                                            <div className="flex flex-col gap-1">
                                                <button
                                                    onClick={() => handleMoveBlock(index, "up")}
                                                    disabled={index === 0}
                                                    className="text-gray-400 hover:text-gray-600 disabled:opacity-30"
                                                >
                                                    ▲
                                                </button>
                                                <button
                                                    onClick={() => handleMoveBlock(index, "down")}
                                                    disabled={index === draft.contentBlocks.length - 1}
                                                    className="text-gray-400 hover:text-gray-600 disabled:opacity-30"
                                                >
                                                    ▼
                                                </button>
                                            </div>
                                            <div className="flex-1">
                                                <div className="flex items-center gap-2">
                                                    <span className="text-lg">
                                                        {BLOCK_TYPE_OPTIONS.find((o) => o.value === block.blockType)?.icon || "📦"}
                                                    </span>
                                                    <span className="font-medium text-gray-900">
                                                        {BLOCK_TYPE_OPTIONS.find((o) => o.value === block.blockType)?.label || block.blockType}
                                                    </span>
                                                    <span className="text-xs text-gray-400">
                                                        #{index + 1}
                                                    </span>
                                                </div>
                                                {block.blockType === "simulation" && (
                                                    <Text className="text-sm text-gray-500 mt-1">
                                                        {(block.payload as any)?.inlineManifest?.title || "Untitled simulation"}
                                                    </Text>
                                                )}
                                                {block.blockType === "text" && (
                                                    <Text className="text-sm text-gray-500 mt-1 truncate">
                                                        {((block.payload as any)?.markdown || "").slice(0, 50)}...
                                                    </Text>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-2">
                                                {block.blockType === "simulation" && (
                                                    <button
                                                        onClick={() => setEditingBlockIndex(index)}
                                                        className="px-3 py-1 text-sm text-blue-600 hover:text-blue-700"
                                                    >
                                                        Edit
                                                    </button>
                                                )}
                                                <button
                                                    onClick={() => handleRemoveBlock(index)}
                                                    className="px-3 py-1 text-sm text-red-600 hover:text-red-700"
                                                >
                                                    Remove
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </Card>
                    </div>

                    {/* Sidebar */}
                    <div className="space-y-6">
                        {/* Tags */}
                        <Card className="p-6">
                            <Heading level={2} className="text-lg font-medium text-gray-900 mb-4">
                                Tags
                            </Heading>
                            <div className="flex gap-2 mb-3">
                                <input
                                    value={tagInput}
                                    onChange={(e: ChangeEvent<HTMLInputElement>) => setTagInput(e.target.value)}
                                    placeholder="Add tag..."
                                    onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => e.key === "Enter" && handleAddTag()}
                                    className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                />
                                <Button onClick={handleAddTag} variant="outline" tone="neutral" size="sm">
                                    Add
                                </Button>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {draft.tags.map((tag) => (
                                    <span
                                        key={tag}
                                        className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-700 text-sm rounded-full"
                                    >
                                        {tag}
                                        <button
                                            onClick={() => handleRemoveTag(tag)}
                                            className="text-blue-500 hover:text-blue-700"
                                        >
                                            ×
                                        </button>
                                    </span>
                                ))}
                            </div>
                        </Card>

                        {/* Learning Objectives */}
                        <Card className="p-6">
                            <div className="flex items-center justify-between mb-4">
                                <Heading level={2} className="text-lg font-medium text-gray-900">
                                    Learning Objectives
                                </Heading>
                                <button
                                    onClick={handleAddObjective}
                                    className="text-sm text-blue-600 hover:text-blue-700"
                                >
                                    + Add
                                </button>
                            </div>
                            <div className="space-y-3">
                                {draft.learningObjectives.map((obj, index) => (
                                    <div key={index} className="flex items-start gap-2">
                                        <div className="flex-1 space-y-2">
                                            <input
                                                value={obj.label}
                                                onChange={(e: ChangeEvent<HTMLInputElement>) => handleUpdateObjective(index, "label", e.target.value)}
                                                placeholder="Objective description..."
                                                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                                            />
                                            <select
                                                value={obj.taxonomyLevel}
                                                onChange={(e) => handleUpdateObjective(index, "taxonomyLevel", e.target.value)}
                                                className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md"
                                            >
                                                <option value="REMEMBER">Remember</option>
                                                <option value="UNDERSTAND">Understand</option>
                                                <option value="APPLY">Apply</option>
                                                <option value="ANALYZE">Analyze</option>
                                                <option value="EVALUATE">Evaluate</option>
                                                <option value="CREATE">Create</option>
                                            </select>
                                        </div>
                                        <button
                                            onClick={() => handleRemoveObjective(index)}
                                            className="text-red-500 hover:text-red-700 mt-2"
                                        >
                                            ×
                                        </button>
                                    </div>
                                ))}
                                {draft.learningObjectives.length === 0 && (
                                    <Text className="text-sm text-gray-500 italic">
                                        No objectives yet
                                    </Text>
                                )}
                            </div>
                        </Card>
                    </div>
                </div>
            </div>
            {/* Block Type Picker Modal */}
            {showBlockPicker && (
                <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                        <Heading level={2} className="text-lg font-medium text-gray-900 mb-4">
                            Add Content Block
                        </Heading>
                        <div className="grid grid-cols-3 gap-3">
                            {BLOCK_TYPE_OPTIONS.map((option) => (
                                <button
                                    key={option.value}
                                    onClick={() => handleAddBlock(option.value)}
                                    className="p-4 text-center border rounded-lg hover:bg-gray-50 transition-colors"
                                >
                                    <div className="text-2xl mb-1">{option.icon}</div>
                                    <div className="text-sm text-gray-700">{option.label}</div>
                                </button>
                            ))}
                        </div>
                        <div className="mt-4 flex justify-end">
                            <Button
                                onClick={() => setShowBlockPicker(false)}
                                variant="ghost"
                                tone="neutral"
                            >
                                Cancel
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </Box>
    );
}

export default CMSModuleEditorPage;
