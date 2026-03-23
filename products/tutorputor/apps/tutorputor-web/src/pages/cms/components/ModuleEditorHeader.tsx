/**
 * Module Editor Header Component
 * 
 * Header section with title, actions, and basic metadata
 * 
 * @doc.type component
 * @doc.purpose Module editor header
 * @doc.layer product
 * @doc.pattern Component
 */

import { Button, Heading, Text } from "@/components/ui";

interface ModuleEditorHeaderProps {
    title: string;
    isEditing: boolean;
    isLoading: boolean;
    onSave: () => void;
    onPublish: () => void;
    saveLoading: boolean;
    publishLoading: boolean;
}

export function ModuleEditorHeader({
    title,
    isEditing,
    isLoading,
    onSave,
    onPublish,
    saveLoading,
    publishLoading,
}: ModuleEditorHeaderProps) {
    return (
        <div className="border-b border-gray-200 pb-4 mb-6">
            <div className="flex items-center justify-between">
                <div>
                    <Heading level={1} className="text-2xl font-bold text-gray-900">
                        {isEditing ? "Edit Module" : "Create Module"}
                    </Heading>
                    <Text className="text-gray-600 mt-1">
                        {title || "Untitled Module"}
                    </Text>
                </div>
                <div className="flex items-center gap-3">
                    <Button
                        onClick={onSave}
                        disabled={isLoading || saveLoading}
                        variant="solid"
                        tone="primary"
                    >
                        {saveLoading ? "Saving..." : "Save"}
                    </Button>
                    {isEditing && (
                        <Button
                            onClick={onPublish}
                            disabled={isLoading || publishLoading}
                            variant="solid"
                            tone="success"
                        >
                            {publishLoading ? "Publishing..." : "Publish"}
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
