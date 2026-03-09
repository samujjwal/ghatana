/**
 * Unified Canvas Node
 * 
 * A single node type that can contain different content types:
 * - Artifact nodes (current functionality)
 * - Sketch/drawing content
 * - Diagram content  
 * - Code content
 * 
 * The node automatically handles its own rendering and interaction
 * based on its content type, eliminating the need for mode switching.
 * 
 * @doc.type component
 * @doc.purpose Unified node for all canvas content types
 * @doc.layer product
 * @doc.pattern Polymorphic Component
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Box, Surface as Paper, Typography, IconButton, Tooltip } from '@ghatana/ui';
import { Pencil as Edit, Trash2 as Delete, GripVertical as DragIndicator } from 'lucide-react';

// Content type imports
import { ArtifactNodeContent } from './content/ArtifactNodeContent';
import { SketchNodeContent } from './content/SketchNodeContent';
import { DiagramNodeContent } from './content/DiagramNodeContent';
import { CodeNodeContent } from './content/CodeNodeContent';

export type UnifiedContentType = 'artifact' | 'sketch' | 'diagram' | 'code';

export interface UnifiedNodeData {
    id: string;
    type: UnifiedContentType;
    title: string;
    status: 'draft' | 'review' | 'approved';
    phase: string;

    // Content-specific data
    artifactData?: unknown; // Current ArtifactNodeData
    sketchData?: {
        strokes: unknown[];
        canvas?: HTMLCanvasElement;
    };
    diagramData?: {
        type: 'mermaid' | 'excalidraw';
        content: string;
    };
    codeData?: {
        language: string;
        content: string;
    };

    // Layout
    width?: number;
    height?: number;
}

const NodeContainer = styled(Paper)<{ contentType: UnifiedContentType }>(({ theme, contentType }) => ({
    minWidth: 200,
    minHeight: 120,
    border: `2px solid`,
    borderColor:
        contentType === 'artifact' ? theme.palette.primary.main :
            contentType === 'sketch' ? theme.palette.secondary.main :
                contentType === 'diagram' ? theme.palette.success.main :
                    theme.palette.warning.main,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: theme.palette.background.paper,
    cursor: 'move',
    transition: 'all 0.2s ease',
    '&:hover': {
        boxShadow: theme.shadows[8],
        transform: 'translateY(-2px)',
    },
}));

const NodeHeader = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: theme.spacing(1, 2),
    backgroundColor: theme.palette.mode === 'dark'
        ? 'rgba(255,255,255,0.05)'
        : 'rgba(0,0,0,0.02)',
    borderBottom: `1px solid ${theme.palette.divider}`,
}));

const NodeContent = styled(Box)(({ theme }) => ({
    padding: theme.spacing(2),
    minHeight: 80,
    position: 'relative',
}));

const ContentTypeIndicator = styled(Box)<{ contentType: UnifiedContentType }>(({ theme, contentType }) => ({
    width: 8,
    height: 8,
    borderRadius: '50%',
    backgroundColor:
        contentType === 'artifact' ? theme.palette.primary.main :
            contentType === 'sketch' ? theme.palette.secondary.main :
                contentType === 'diagram' ? theme.palette.success.main :
                    theme.palette.warning.main,
    marginRight: theme.spacing(1),
}));

export const UnifiedCanvasNode: React.FC<NodeProps<UnifiedNodeData>> = memo(({
    data,
    selected,
    dragging
}) => {
    const [isEditing, setIsEditing] = useState(false);
    const [isHovered, setIsHovered] = useState(false);

    const handleEdit = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setIsEditing(true);
    }, []);

    const handleDelete = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        // Handle deletion - this will be handled by the parent component
        console.log('Delete node:', data.id);
    }, [data.id]);

    const handleContentChange = useCallback((newContent: unknown) => {
        // Handle content updates
        console.log('Content changed:', data.id, newContent);
    }, [data.id]);

    const renderContent = () => {
        switch (data.type) {
            case 'artifact':
                return (
                    <ArtifactNodeContent
                        data={data.artifactData}
                        onChange={handleContentChange}
                        readonly={!isEditing}
                    />
                );
            case 'sketch':
                return (
                    <SketchNodeContent
                        data={data.sketchData}
                        onChange={handleContentChange}
                        readonly={!isEditing}
                    />
                );
            case 'diagram':
                return (
                    <DiagramNodeContent
                        data={data.diagramData}
                        onChange={handleContentChange}
                        readonly={!isEditing}
                    />
                );
            case 'code':
                return (
                    <CodeNodeContent
                        data={data.codeData}
                        onChange={handleContentChange}
                        readonly={!isEditing}
                    />
                );
            default:
                return <Typography>Unknown content type</Typography>;
        }
    };

    return (
        <>
            {/* Connection Handles */}
            <Handle type="target" position={Position.Top} />
            <Handle type="source" position={Position.Bottom} />

            <NodeContainer
                contentType={data.type}
                elevation={selected ? 8 : 2}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
            >
                {/* Node Header */}
                <NodeHeader>
                    <Box className="flex items-center flex-1">
                        <ContentTypeIndicator contentType={data.type} />
                        <Typography as="p" className="text-sm font-medium" fontWeight="bold" noWrap>
                            {data.title}
                        </Typography>
                    </Box>

                    {(isHovered || selected) && (
                        <Box className="flex items-center">
                            <Tooltip title="Edit">
                                <IconButton
                                    size="sm"
                                    onClick={handleEdit}
                                    className="p-1"
                                >
                                    <Edit size={16} />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete">
                                <IconButton
                                    size="sm"
                                    onClick={handleDelete}
                                    className="p-1"
                                >
                                    <Delete size={16} />
                                </IconButton>
                            </Tooltip>
                            <DragIndicator className="ml-2 text-base text-gray-500 dark:text-gray-400" />
                        </Box>
                    )}
                </NodeHeader>

                {/* Node Content */}
                <NodeContent>
                    {renderContent()}
                </NodeContent>
            </NodeContainer>
        </>
    );
});

UnifiedCanvasNode.displayName = 'UnifiedCanvasNode';
