/**
 * Block Type Picker Modal Component
 * 
 * Modal for selecting content block types
 * 
 * @doc.type component
 * @doc.purpose Block type picker modal
 * @doc.layer product
 * @doc.pattern Component
 */

import { Heading, Button } from "@/components/ui";
import type { ContentBlockType } from "../types";
import { BLOCK_TYPE_OPTIONS } from "../constants";

interface BlockTypePickerModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (blockType: ContentBlockType) => void;
}

export function BlockTypePickerModal({ isOpen, onClose, onSelect }: BlockTypePickerModalProps) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
            <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                <Heading level={2} className="text-lg font-medium text-gray-900 mb-4">
                    Add Content Block
                </Heading>
                
                <div className="grid grid-cols-3 gap-3 mb-4">
                    {BLOCK_TYPE_OPTIONS.map((option) => (
                        <button
                            key={option.value}
                            onClick={() => onSelect(option.value)}
                            className="p-4 text-center border rounded-lg hover:bg-gray-50 transition-colors"
                        >
                            <div className="text-2xl mb-1">{option.icon}</div>
                            <div className="text-sm text-gray-700">{option.label}</div>
                        </button>
                    ))}
                </div>
                
                <div className="flex justify-end">
                    <Button onClick={onClose} variant="ghost" tone="neutral">
                        Cancel
                    </Button>
                </div>
            </div>
        </div>
    );
}
