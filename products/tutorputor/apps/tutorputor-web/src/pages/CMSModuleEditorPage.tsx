/**
 * Refactored CMS Module Editor Page
 * 
 * Clean, modular version of the module editor with separated concerns
 * 
 * @doc.type page
 * @doc.purpose Content authoring UI for educators (refactored)
 * @doc.layer product
 * @doc.pattern Page
 */

import { Box } from "@/components/ui";
import { useModuleEditor } from "./cms/useModuleEditor";
import { ModuleEditorHeader } from "./cms/components/ModuleEditorHeader";
import { ModuleMetadataForm } from "./cms/components/ModuleMetadataForm";
import { LearningObjectivesEditor } from "./cms/components/LearningObjectivesEditor";
import { ContentBlocksList } from "./cms/components/ContentBlocksList";
import { BlockTypePickerModal } from "./cms/components/BlockTypePickerModal";
import { SimulationBlockEditor } from "../components/cms/SimulationBlockEditor";

export function CMSModuleEditorPage() {
    const {
        draft,
        setDraft,
        editingBlockIndex,
        setEditingBlockIndex,
        showBlockPicker,
        setShowBlockPicker,
        existingModule,
        isLoadingModule,
        createMutation,
        updateMutation,
        publishMutation,
        handleSave,
        handlePublish,
        handleAddBlock,
        handleUpdateBlock,
        handleRemoveBlock,
        handleMoveBlock,
        isEditing,
    } = useModuleEditor();

    // Handle metadata changes
    const handleMetadataChange = (updates: Partial<typeof draft>) => {
        setDraft(prev => ({ ...prev, ...updates }));
    };

    // Handle objectives changes
    const handleObjectivesChange = (objectives: any[]) => {
        setDraft(prev => ({ ...prev, learningObjectives: objectives }));
    };

    // Handle block editing
    const handleEditBlock = (index: number) => {
        setEditingBlockIndex(index);
    };

    // Handle block selection from picker
    const handleBlockTypeSelect = (blockType: unknown) => {
        handleAddBlock(blockType as import('./cms/types').ContentBlockType);
    };

    return (
        <Box className="max-w-6xl mx-auto p-6">
            <ModuleEditorHeader
                title={draft.title}
                isEditing={isEditing}
                isLoading={isLoadingModule}
                onSave={handleSave}
                onPublish={handlePublish}
                saveLoading={createMutation.isPending || updateMutation.isPending}
                publishLoading={publishMutation.isPending}
            />

            <ModuleMetadataForm
                draft={draft}
                onChange={handleMetadataChange}
            />

            <LearningObjectivesEditor
                objectives={draft.learningObjectives}
                onChange={handleObjectivesChange}
            />

            <ContentBlocksList
                blocks={draft.contentBlocks}
                onEditBlock={handleEditBlock}
                onRemoveBlock={handleRemoveBlock}
                onMoveBlock={handleMoveBlock}
                onAddBlock={() => setShowBlockPicker(true)}
            />

            {/* Block Type Picker Modal */}
            <BlockTypePickerModal
                isOpen={showBlockPicker}
                onClose={() => setShowBlockPicker(false)}
                onSelect={handleBlockTypeSelect}
            />

            {/* Simulation Block Editor Modal */}
            {editingBlockIndex !== null && 
                draft.contentBlocks[editingBlockIndex]?.blockType === "simulation" && (
                <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
                    <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
                        <SimulationBlockEditor
                            initialPayload={
                                (draft.contentBlocks[editingBlockIndex].payload as any) ?? undefined
                            }
                            onSave={(payload) => {
                                handleUpdateBlock(editingBlockIndex, payload as any);
                            }}
                            onCancel={() => setEditingBlockIndex(null)}
                        />
                    </div>
                </div>
            )}
        </Box>
    );
}

export default CMSModuleEditorPage;
