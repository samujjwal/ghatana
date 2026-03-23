/**
 * Module Editor Hooks
 * 
 * Custom hooks for the CMS Module Editor
 * 
 * @doc.type hooks
 * @doc.purpose Custom hooks for module editor
 * @doc.layer product
 * @doc.pattern Hooks
 */

import { useState, useCallback, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useLocation } from "react-router-dom";
import { tutorputorClient } from "../../api/tutorputorClient";
import type { 
    DraftState, 
    ContentBlock, 
    ContentBlockType, 
    LearningObjective,
    ModuleDraftInput,
    ModuleDraftPatch 
} from "./types";
import { INITIAL_DRAFT } from "./constants";

/** Hook for module editor state and operations */
export function useModuleEditor() {
    const { moduleId } = useParams<{ moduleId?: string }>();
    const location = useLocation();
    const searchParams = new URLSearchParams(location.search);
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

    // Initialize draft from existing module or template
    useEffect(() => {
        if (existingModule) {
            setDraft({
                slug: existingModule.slug,
                title: existingModule.title,
                description: existingModule.description || "",
                domain: existingModule.domain,
                difficulty: existingModule.difficulty,
                estimatedTimeMinutes: existingModule.estimatedTimeMinutes,
                tags: existingModule.tags,
                learningObjectives: existingModule.learningObjectives || [],
                contentBlocks: existingModule.contentBlocks || [],
            });
        } else if (templateData) {
            setDraft((prev) => ({
                ...prev,
                title: templateData.title || prev.title,
                description: templateData.description || prev.description,
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
    }, [existingModule]);

    // Create mutation
    const createMutation = useMutation({
        mutationFn: async (input: ModuleDraftInput) => {
            const response = await tutorputorClient.post<{ id: string }>("/cms/modules", input);
            return response.data;
        },
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ["cms", "modules"] });
            window.location.href = `/cms/modules/${data.id}`;
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
            window.location.href = "/cms/modules";
        },
    });

    const handleSave = useCallback(() => {
        if (isEditing) {
            updateMutation.mutate({
                title: draft.title,
                description: draft.description,
                difficulty: draft.difficulty,
                estimatedTimeMinutes: draft.estimatedTimeMinutes,
                tags: draft.tags,
                learningObjectives: draft.learningObjectives,
                contentBlocks: draft.contentBlocks,
            });
        } else {
            createMutation.mutate(draft);
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
                    description: "",
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
                contentBlocks: prev.contentBlocks.filter((_, i) => i !== index),
            }));
        }
    }, []);

    const handleMoveBlock = useCallback((index: number, direction: "up" | "down") => {
        const newIndex = direction === "up" ? index - 1 : index + 1;
        if (newIndex < 0 || newIndex >= draft.contentBlocks.length) return;

        setDraft((prev) => {
            const newBlocks = [...prev.contentBlocks];
            [newBlocks[index], newBlocks[newIndex]] = [newBlocks[newIndex], newBlocks[index]];
            
            // Update order indices
            return {
                ...prev,
                contentBlocks: newBlocks.map((block, i) => ({ ...block, orderIndex: i })),
            };
        });
    }, [draft.contentBlocks.length]);

    return {
        // State
        draft,
        setDraft,
        editingBlockIndex,
        setEditingBlockIndex,
        showBlockPicker,
        setShowBlockPicker,
        tagInput,
        setTagInput,
        
        // Queries
        existingModule,
        isLoadingModule,
        templateData,
        
        // Mutations
        createMutation,
        updateMutation,
        publishMutation,
        
        // Handlers
        handleSave,
        handlePublish,
        handleAddBlock,
        handleUpdateBlock,
        handleRemoveBlock,
        handleMoveBlock,
        
        // Flags
        isEditing,
    };
}
