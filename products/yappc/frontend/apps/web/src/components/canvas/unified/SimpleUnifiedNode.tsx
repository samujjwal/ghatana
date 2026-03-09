/**
 * Simple Unified Node
 * 
 * A simplified unified node that works with the existing ReactFlow system.
 * This bridges the gap between our unified approach and the current artifact nodes.
 * 
 * @doc.type component
 * @doc.purpose Simple unified node for ReactFlow integration
 * @doc.layer product
 * @doc.pattern Bridge Component
 */

import React, { useState, useCallback } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Box, Surface as Paper, Typography, IconButton, Tooltip, TextField, Button } from '@ghatana/ui';
import { Pencil as Edit, Trash2 as Delete, GripVertical as DragIndicator, X as Close, Check } from 'lucide-react';

// Simple data interface that extends Record<string, unknown>
interface SimpleUnifiedNodeData extends Record<string, unknown> {
    id: string;
    title: string;
    contentType: 'sketch' | 'diagram' | 'code' | 'artifact';
    content: unknown;
    isEditing?: boolean;
}

const NodeContainer = styled(Paper)<{ contentType: string }>(({ theme, contentType }) => ({
    minWidth: 250,
    minHeight: 150,
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
    minHeight: 100,
    position: 'relative',
}));

const ContentTypeIndicator = styled(Box)<{ contentType: string }>(({ theme, contentType }) => ({
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

export const SimpleUnifiedNode: React.FC<NodeProps<SimpleUnifiedNodeData>> = ({
    data,
    selected,
    dragging
}) => {
    const [isEditing, setIsEditing] = useState(data.isEditing || false);
    const [editContent, setEditContent] = useState(
        typeof data.content === 'string' ? data.content : JSON.stringify(data.content, null, 2)
    );

    const handleEdit = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setIsEditing(true);
    }, []);

    const handleSave = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setIsEditing(false);
        // In a real implementation, this would save to the data store
        console.log('Saving content:', data.contentType, editContent);
    }, [data.contentType, editContent]);

    const handleCancel = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setIsEditing(false);
        // Reset to original content
        setEditContent(
            typeof data.content === 'string' ? data.content : JSON.stringify(data.content, null, 2)
        );
    }, [data.content]);

    const handleDelete = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        // In a real implementation, this would delete the node
        console.log('Delete node:', data.id);
    }, [data.id]);

    const renderContent = () => {
        if (isEditing) {
            return (
                <Box className="flex flex-col gap-2 h-full">
                    <TextField
                        multiline
                        rows={6}
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        placeholder={`Edit ${data.contentType} content...`}
                        variant="outlined"
                        size="sm"
                        InputProps={{
                            style: { fontFamily: 'monospace', fontSize: '0.875rem' },
                        }}
                    />
                    <Box className="flex gap-2 justify-end">
                        <Button size="sm" onClick={handleCancel} startIcon={<Close />}>
                            Cancel
                        </Button>
                        <Button size="sm" onClick={handleSave} variant="solid" startIcon={<Check />}>
                            Save
                        </Button>
                    </Box>
                </Box>
            );
        }

        // Read-only view based on content type
        switch (data.contentType) {
            case 'sketch':
                return (
                    <Box className="rounded flex items-center justify-center h-[120px] text-gray-500 dark:text-gray-400" style={{ border: '1px dashed #ccc' }} >
                        <Box className="text-center">
                            <Typography as="p" className="text-sm">Sketch Canvas</Typography>
                            <Typography as="span" className="text-xs text-gray-500">Click edit to draw</Typography>
                        </Box>
                    </Box>
                );

            case 'diagram':
                return (
                    <Box className="rounded p-2 overflow-hidden h-[120px] border border-solid border-[#ccc] bg-[#f9f9f9]">
                        <pre style={{
                            margin: 0,
                            fontSize: '11px',
                            lineHeight: '1.2',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                        }}>
                            {editContent}
                        </pre>
                    </Box>
                );

            case 'code':
                return (
                    <Box className="rounded overflow-hidden h-[120px] border border-solid border-[#ccc]">
                        <Box className="px-2 py-1 border-b border-solid border-b-[#ccc] bg-gray-100" >
                            <Typography as="span" className="text-xs text-gray-500 font-mono">
                                {data.content?.language || 'javascript'}
                            </Typography>
                        </Box>
                        <Box className="p-2 overflow-auto h-[calc(100% - 30px)]">
                            <pre style={{
                                margin: 0,
                                fontSize: '11px',
                                lineHeight: '1.3',
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-word',
                            }}>
                                {editContent}
                            </pre>
                        </Box>
                    </Box>
                );

            case 'artifact':
            default:
                return (
                    <Box>
                        <Typography as="p" className="text-sm" className="mb-2">
                            {data.title}
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {data.content?.description || 'No description'}
                        </Typography>
                    </Box>
                );
        }
    };

    return (
        <>
            {/* Connection Handles */}
            <Handle type="target" position={Position.Top} />
            <Handle type="source" position={Position.Bottom} />

            <NodeContainer contentType={data.contentType} elevation={selected ? 8 : 2}>
                {/* Node Header */}
                <NodeHeader>
                    <Box className="flex items-center flex-1">
                        <ContentTypeIndicator contentType={data.contentType} />
                        <Typography as="p" className="text-sm font-medium" fontWeight="bold" noWrap>
                            {data.title}
                        </Typography>
                    </Box>

                    <Box className="flex items-center">
                        {!isEditing && (
                            <Tooltip title="Edit">
                                <IconButton size="sm" onClick={handleEdit} className="p-1">
                                    <Edit size={16} />
                                </IconButton>
                            </Tooltip>
                        )}
                        <Tooltip title="Delete">
                            <IconButton size="sm" onClick={handleDelete} className="p-1">
                                <Delete size={16} />
                            </IconButton>
                        </Tooltip>
                        <DragIndicator className="ml-2 text-base text-gray-500 dark:text-gray-400" />
                    </Box>
                </NodeHeader>

                {/* Node Content */}
                <NodeContent>
                    {renderContent()}
                </NodeContent>
            </NodeContainer>
        </>
    );
};
