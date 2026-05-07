/**
 * Content Blocks List Component
 * 
 * List view of content blocks with drag-and-drop and actions
 * 
 * @doc.type component
 * @doc.purpose Content blocks list
 * @doc.layer product
 * @doc.pattern Component
 */

import { Card, Text, Button } from "@/components/ui";
import type { ContentBlock } from "../types";
import { BLOCK_TYPE_OPTIONS } from "../constants";

interface ContentBlocksListProps {
    blocks: ContentBlock[];
    onEditBlock: (index: number) => void;
    onRemoveBlock: (index: number) => void;
    onMoveBlock: (index: number, direction: "up" | "down") => void;
    onAddBlock: () => void;
}

export function ContentBlocksList({
    blocks,
    onEditBlock,
    onRemoveBlock,
    onMoveBlock,
    onAddBlock,
}: ContentBlocksListProps) {
    const getBlockTypeInfo = (blockType: string) => {
        return BLOCK_TYPE_OPTIONS.find(option => option.value === blockType) || {
            value: "text" as const,
            label: "Unknown",
            icon: "❓"
        };
    };

    return (
        <Card className="p-6 mb-6">
            <div className="flex items-center justify-between mb-4">
                <Text className="text-lg font-medium text-gray-900">Content Blocks</Text>
                <Button onClick={onAddBlock} variant="solid" tone="primary">
                    Add Block
                </Button>
            </div>
            
            <div className="space-y-3">
                {blocks.map((block, index) => {
                    const blockType = getBlockTypeInfo(block.blockType);
                    return (
                        <div
                            key={block.id}
                            className="flex items-center gap-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50"
                        >
                            <div className="flex flex-col gap-1">
                                <Button
                                    onClick={() => onMoveBlock(index, "up")}
                                    variant="ghost"
                                    size="sm"
                                    disabled={index === 0}
                                    className="p-1 h-6 w-6"
                                >
                                    ↑
                                </Button>
                                <Button
                                    onClick={() => onMoveBlock(index, "down")}
                                    variant="ghost"
                                    size="sm"
                                    disabled={index === blocks.length - 1}
                                    className="p-1 h-6 w-6"
                                >
                                    ↓
                                </Button>
                            </div>
                            
                            <div className="text-2xl">{blockType.icon}</div>
                            
                            <div className="flex-1">
                                <Text className="font-medium text-gray-900">{blockType.label}</Text>
                                <Text className="text-sm text-gray-500">
                                    {block.blockType === "simulation" && 
                                        (typeof block.payload === 'object' && block.payload !== null && 'inlineManifest' in block.payload && 
                                            typeof (block.payload as { inlineManifest?: { title?: string } }).inlineManifest?.title === 'string'
                                            ? (block.payload as { inlineManifest?: { title?: string } }).inlineManifest?.title ?? "Untitled Simulation"
                                            : "Untitled Simulation"
                                        )
                                    }
                                    {block.blockType === "text" && 
                                        (typeof block.payload === 'object' && block.payload !== null && 'markdown' in block.payload &&
                                            typeof (block.payload as { markdown?: string }).markdown === 'string'
                                            ? ((block.payload as { markdown?: string }).markdown?.substring(0, 50) || "Empty text block") + "..."
                                            : "Empty text block..."
                                        )
                                    }
                                    {block.blockType !== "simulation" && block.blockType !== "text" && 
                                        "Click to edit"
                                    }
                                </Text>
                            </div>
                            
                            <div className="flex items-center gap-2">
                                <Button
                                    onClick={() => onEditBlock(index)}
                                    variant="solid"
                                    tone="primary"
                                    size="sm"
                                >
                                    Edit
                                </Button>
                                <Button
                                    onClick={() => onRemoveBlock(index)}
                                    variant="ghost"
                                    tone="danger"
                                    size="sm"
                                >
                                    Remove
                                </Button>
                            </div>
                        </div>
                    );
                })}
                
                {blocks.length === 0 && (
                    <div className="text-center py-8">
                        <Text className="text-gray-500 mb-4">No content blocks yet</Text>
                        <Button onClick={onAddBlock} variant="solid" tone="primary">
                            Add Your First Block
                        </Button>
                    </div>
                )}
            </div>
        </Card>
    );
}
