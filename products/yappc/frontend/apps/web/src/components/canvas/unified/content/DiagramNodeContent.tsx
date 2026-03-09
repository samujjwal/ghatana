/**
 * Diagram Node Content
 * 
 * Renders diagram content (Mermaid, Excalidraw) within a canvas node.
 * Provides inline diagram editing without mode switching.
 * 
 * @doc.type component
 * @doc.purpose Inline diagram content for canvas nodes
 * @doc.layer product
 * @doc.pattern Content Renderer
 */

import React, { useState, useCallback, useRef } from 'react';
import { Box, IconButton, Tooltip, TextField, Select, MenuItem, FormControl } from '@ghatana/ui';
import { Pencil as Edit, Maximize2 as Fullscreen, Play as PlayArrow } from 'lucide-react';
import { MermaidDiagram } from '../../diagram/MermaidDiagram';

interface DiagramNodeContentProps {
    data?: {
        type: 'mermaid' | 'excalidraw';
        content: string;
    };
    onChange?: (newData: unknown) => void;
    readonly?: boolean;
}

const DEFAULT_DIAGRAM = {
    type: 'mermaid' as const,
    content: 'graph TD\n  A[Start] --> B[Process]\n  B --> C[End]',
};

export const DiagramNodeContent: React.FC<DiagramNodeContentProps> = ({
    data,
    onChange,
    readonly = false
}) => {
    const [isEditing, setIsEditing] = useState(false);
    const [editContent, setEditContent] = useState(data?.content || DEFAULT_DIAGRAM.content);
    const [diagramType, setDiagramType] = useState(data?.type || DEFAULT_DIAGRAM.type);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Sync with parent data
    React.useEffect(() => {
        if (data) {
            setEditContent(data.content);
            setDiagramType(data.type);
        }
    }, [data]);

    const notifyChange = useCallback((newContent: string, newType: string) => {
        const newData = {
            type: newType,
            content: newContent,
        };
        onChange?.(newData);
    }, [onChange]);

    const handleEdit = useCallback(() => {
        setIsEditing(true);
        // Focus textarea after state update
        setTimeout(() => {
            textareaRef.current?.focus();
        }, 0);
    }, []);

    const handleSave = useCallback(() => {
        setIsEditing(false);
        notifyChange(editContent, diagramType);
    }, [editContent, diagramType, notifyChange]);

    const handleCancel = useCallback(() => {
        setIsEditing(false);
        // Revert to original content
        if (data) {
            setEditContent(data.content);
            setDiagramType(data.type);
        }
    }, [data]);

    const handleTypeChange = useCallback((event: unknown) => {
        const newType = event.target.value;
        setDiagramType(newType);

        // Provide default content for different diagram types
        const defaultContent =
            newType === 'mermaid'
                ? 'graph TD\n  A[Start] --> B[Process]\n  B --> C[End]'
                : newType === 'excalidraw'
                    ? 'excalidraw-diagram-content'
                    : editContent;

        setEditContent(defaultContent);
    }, [editContent]);

    if (isEditing && !readonly) {
        return (
            <Box className="flex flex-col gap-2 p-2 min-w-[300px] min-h-[200px]">
                {/* Diagram Type Selector */}
                <FormControl size="sm" className="min-w-[120px]">
                    <Select
                        value={diagramType}
                        onChange={handleTypeChange}
                        size="sm"
                    >
                        <MenuItem value="mermaid">Mermaid</MenuItem>
                        <MenuItem value="excalidraw">Excalidraw</MenuItem>
                    </Select>
                </FormControl>

                {/* Content Editor */}
                <TextField
                    multiline
                    rows={6}
                    value={editContent}
                    onChange={(e) => setEditContent(e.target.value)}
                    placeholder="Enter diagram content..."
                    variant="outlined"
                    size="sm"
                    inputRef={textareaRef}
                    InputProps={{
                        style: { fontFamily: 'monospace', fontSize: '0.875rem' },
                    }}
                />

                {/* Action Buttons */}
                <Box className="flex gap-2 justify-end">
                    <Tooltip title="Cancel">
                        <IconButton size="sm" onClick={handleCancel}>
                            ×
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Save">
                        <IconButton size="sm" onClick={handleSave} tone="primary">
                            ✓
                        </IconButton>
                    </Tooltip>
                </Box>
            </Box>
        );
    }

    // Read-only view
    return (
        <Box className="relative w-full h-[200px]">
            {/* Diagram Preview */}
            <Box className="w-full h-full rounded overflow-hidden border border-solid border-[#ccc] bg-white dark:bg-gray-900">
                {diagramType === 'mermaid' ? (
                    <MermaidDiagram
                        content={editContent}
                        zoom={1}
                    />
                ) : (
                    <Box className="p-4 flex items-center justify-center h-full text-gray-500 dark:text-gray-400">
                        <Box className="text-center">
                            <Typography as="p" className="text-sm">
                                Excalidraw diagrams
                            </Typography>
                            <Typography as="span" className="text-xs text-gray-500">
                                Click edit to modify
                            </Typography>
                        </Box>
                    </Box>
                )}
            </Box>

            {/* Toolbar */}
            <Box className="absolute flex gap-1 rounded p-1 top-[8px] right-[8px] bg-white dark:bg-gray-900 shadow-sm">
                {!readonly && (
                    <Tooltip title="Edit">
                        <IconButton size="sm" onClick={handleEdit}>
                            <Edit size={16} />
                        </IconButton>
                    </Tooltip>
                )}

                <Tooltip title="Fullscreen">
                    <IconButton size="sm">
                        <Fullscreen size={16} />
                    </IconButton>
                </Tooltip>
            </Box>
        </Box>
    );
};
