/**
 * Enhanced Unified Node
 * 
 * An advanced unified node with enhanced features like:
 * - Real-time collaboration indicators
 * - Quick actions menu
 * - Content preview
 * - Drag and drop support
 * - Keyboard shortcuts
 * 
 * @doc.type component
 * @doc.purpose Enhanced unified node with advanced features
 * @doc.layer product
 * @doc.pattern Advanced Component
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Box, Surface as Paper, Typography, IconButton, Tooltip, TextField, Button, Menu, MenuItem, Chip, Avatar, Badge } from '@ghatana/ui';
import { Pencil as Edit, Trash2 as Delete, GripVertical as DragIndicator, X as Close, Check, MoreVertical as MoreVert, Copy as ContentCopy, Share2 as Share, Heart as Favorite, Heart as FavoriteBorder, User as Person, Clock as Schedule, Tag } from 'lucide-react';

// Enhanced data interface
interface EnhancedUnifiedNodeData extends Record<string, unknown> {
    id: string;
    title: string;
    contentType: 'sketch' | 'diagram' | 'code' | 'artifact';
    content: unknown;
    isEditing?: boolean;
    isFavorite?: boolean;
    tags?: string[];
    collaborators?: Array<{ id: string; name: string; avatar?: string }>;
    lastModified?: string;
    version?: number;
}

const NodeContainer = styled(Paper)<{ contentType: string; isFavorite?: boolean }>(({ theme, contentType, isFavorite }) => ({
    minWidth: 280,
    minHeight: 180,
    border: `2px solid`,
    borderColor:
        contentType === 'artifact' ? theme.palette.primary.main :
            contentType === 'sketch' ? theme.palette.secondary.main :
                contentType === 'diagram' ? theme.palette.success.main :
                    theme.palette.warning.main,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: theme.palette.background.paper,
    cursor: 'move',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    position: 'relative',
    '&:hover': {
        boxShadow: theme.shadows[12],
        transform: 'translateY(-4px) scale(1.02)',
    },
    ...(isFavorite && {
        '&::before': {
            content: '""',
            position: 'absolute',
            top: -2,
            right: -2,
            width: 24,
            height: 24,
            backgroundColor: theme.palette.error.main,
            borderRadius: '50%',
            zIndex: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
        },
    }),
}));

const NodeHeader = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: theme.spacing(1.5, 2),
    backgroundColor: theme.palette.mode === 'dark'
        ? 'rgba(255,255,255,0.08)'
        : 'rgba(0,0,0,0.04)',
    borderBottom: `1px solid ${theme.palette.divider}`,
}));

const NodeContent = styled(Box)(({ theme }) => ({
    padding: theme.spacing(2),
    minHeight: 120,
    position: 'relative',
}));

const ContentTypeIndicator = styled(Box)<{ contentType: string }>(({ theme, contentType }) => ({
    width: 10,
    height: 10,
    borderRadius: '50%',
    backgroundColor:
        contentType === 'artifact' ? theme.palette.primary.main :
            contentType === 'sketch' ? theme.palette.secondary.main :
                contentType === 'diagram' ? theme.palette.success.main :
                    theme.palette.warning.main,
    marginRight: theme.spacing(1.5),
    boxShadow: `0 2px 4px ${alpha(theme.palette.common.black, 0.2)}`,
}));

const CollaboratorsAvatars = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: -8,
    '& .MuiAvatar-root': {
        width: 24,
        height: 24,
        border: `2px solid ${theme.palette.background.paper}`,
    },
}));

export const EnhancedUnifiedNode: React.FC<NodeProps<EnhancedUnifiedNodeData>> = ({
    data,
    selected,
    dragging
}) => {
    const [isEditing, setIsEditing] = useState(data.isEditing || false);
    const [editContent, setEditContent] = useState(
        typeof data.content === 'string' ? data.content : JSON.stringify(data.content, null, 2)
    );
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const [isFavorite, setIsFavorite] = useState(data.isFavorite || false);
    const menuRef = useRef<HTMLButtonElement>(null);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
                return;
            }

            if (e.key === 'e' && !e.ctrlKey && !e.metaKey) {
                handleEdit(e as unknown);
            } else if (e.key === 'Delete' || e.key === 'Backspace') {
                handleDelete(e as unknown);
            } else if ((e.ctrlKey || e.metaKey) && e.key === 'c') {
                handleCopy(e as unknown);
            } else if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
                e.preventDefault();
                setIsFavorite(prev => !prev);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, []);

    const handleEdit = useCallback((e: React.MouseEvent) => {
        e?.stopPropagation();
        setIsEditing(true);
    }, []);

    const handleSave = useCallback((e: React.MouseEvent) => {
        e?.stopPropagation();
        setIsEditing(false);
        console.log('Saving enhanced content:', data.contentType, editContent);
    }, [data.contentType, editContent]);

    const handleCancel = useCallback((e: React.MouseEvent) => {
        e?.stopPropagation();
        setIsEditing(false);
        setEditContent(
            typeof data.content === 'string' ? data.content : JSON.stringify(data.content, null, 2)
        );
    }, [data.content]);

    const handleDelete = useCallback((e: React.MouseEvent) => {
        e?.stopPropagation();
        console.log('Delete enhanced node:', data.id);
    }, [data.id]);

    const handleCopy = useCallback((e: React.MouseEvent) => {
        e?.stopPropagation();
        navigator.clipboard.writeText(editContent);
        console.log('Content copied to clipboard');
    }, [editContent]);

    const handleShare = useCallback(() => {
        console.log('Share node:', data.id);
        setAnchorEl(null);
    }, [data.id]);

    const handleFavoriteToggle = useCallback(() => {
        setIsFavorite(prev => !prev);
    }, []);

    const handleMenuOpen = useCallback((e: React.MouseEvent<HTMLElement>) => {
        e.stopPropagation();
        setAnchorEl(e.currentTarget);
    }, []);

    const handleMenuClose = useCallback(() => {
        setAnchorEl(null);
    }, []);

    const renderContent = () => {
        if (isEditing) {
            return (
                <Box className="flex flex-col gap-3 h-full">
                    <TextField
                        multiline
                        rows={6}
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        placeholder={`Edit ${data.contentType} content...`}
                        variant="outlined"
                        size="sm"
                        fullWidth
                        autoFocus
                        InputProps={{
                            style: { fontFamily: 'monospace', fontSize: '0.875rem' },
                        }}
                    />
                    <Box className="flex gap-2 justify-between items-center">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Press Ctrl+S to save, Esc to cancel
                        </Typography>
                        <Box className="flex gap-2">
                            <Button size="sm" onClick={handleCancel} startIcon={<Close />}>
                                Cancel
                            </Button>
                            <Button size="sm" onClick={handleSave} variant="solid" startIcon={<Check />}>
                                Save
                            </Button>
                        </Box>
                    </Box>
                </Box>
            );
        }

        // Enhanced read-only views
        switch (data.contentType) {
            case 'sketch':
                return (
                    <Box className="rounded-lg flex items-center justify-center relative h-[140px] text-gray-500 dark:text-gray-400" style={{ border: '2px dashed #ccc', background: 'linear-gradient(45deg, backgroundSize: '20px 20px', backgroundPosition: '0 0' }} >
                        <Box className="text-center">
                            <Typography as="p" className="text-sm" fontWeight="medium">Sketch Canvas</Typography>
                            <Typography as="span" className="text-xs text-gray-500">Press E to edit</Typography>
                        </Box>
                    </Box>
                );

            case 'diagram':
                return (
                    <Box className="rounded-lg p-3 overflow-hidden relative h-[140px] border border-solid border-[#ccc] bg-[#fafafa]">
                        <Box className="absolute px-2 py-0.5 rounded text-[10px] font-bold top-[4px] right-[4px] bg-green-600 text-white">
                            Mermaid
                        </Box>
                        <pre style={{
                            margin: 0,
                            fontSize: '11px',
                            lineHeight: '1.3',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                            maxHeight: '100%',
                            overflow: 'hidden',
                        }}>
                            {editContent}
                        </pre>
                    </Box>
                );

            case 'code':
                return (
                    <Box className="rounded-lg overflow-hidden flex flex-col h-[140px] border border-solid border-[#ccc]">
                        <Box className="px-3 py-1.5 flex justify-between items-center bg-[#1e1e1e] text-[#d4d4d4] border-b border-solid border-b-[#ccc]">
                            <Typography as="span" className="text-xs text-gray-500 font-mono text-[#569cd6]">
                                {data.content?.language || 'javascript'}
                            </Typography>
                            <Box className="flex gap-2">
                                <Typography as="span" className="text-xs text-gray-500 text-[#608b4e]">●</Typography>
                                <Typography as="span" className="text-xs text-gray-500 text-[#dcdcaa]">●</Typography>
                                <Typography as="span" className="text-xs text-gray-500 text-[#569cd6]">●</Typography>
                            </Box>
                        </Box>
                        <Box className="p-3 overflow-auto h-[calc(100% - 32px)] bg-[#1e1e1e] text-[#d4d4d4]">
                            <pre style={{
                                margin: 0,
                                fontSize: '11px',
                                lineHeight: '1.4',
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
                        <Typography as="p" className="text-sm" className="mb-3 font-medium" >
                            {data.title}
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-3 block">
                            {data.content?.description || 'No description'}
                        </Typography>
                        {data.tags && data.tags.length > 0 && (
                            <Box className="flex gap-1 flex-wrap mt-2">
                                {data.tags.slice(0, 3).map((tag, index) => (
                                    <Chip key={index} label={tag} size="sm" variant="outlined" />
                                ))}
                                {data.tags.length > 3 && (
                                    <Chip label={`+${data.tags.length - 3}`} size="sm" variant="outlined" />
                                )}
                            </Box>
                        )}
                    </Box>
                );
        }
    };

    return (
        <>
            {/* Connection Handles */}
            <Handle type="target" position={Position.Top} />
            <Handle type="source" position={Position.Bottom} />

            <NodeContainer contentType={data.contentType} isFavorite={isFavorite} elevation={selected ? 12 : 6}>
                {/* Node Header */}
                <NodeHeader>
                    <Box className="flex items-center flex-1 min-w-0">
                        <ContentTypeIndicator contentType={data.contentType} />
                        <Box className="min-w-0 flex-1">
                            <Typography as="p" className="text-sm font-medium" fontWeight="bold" noWrap>
                                {data.title}
                            </Typography>
                            {data.lastModified && (
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="flex items-center gap-1">
                                    <Schedule size={undefined} />
                                    {new Date(data.lastModified).toLocaleDateString()}
                                </Typography>
                            )}
                        </Box>
                    </Box>

                    <Box className="flex items-center gap-1">
                        {/* Collaborators */}
                        {data.collaborators && data.collaborators.length > 0 && (
                            <CollaboratorsAvatars>
                                {data.collaborators.slice(0, 3).map((collaborator, index) => (
                                    <Avatar
                                        key={collaborator.id}
                                        src={collaborator.avatar}
                                        className="text-[10px] w-[24px] h-[24px]"
                                    >
                                        {collaborator.name.charAt(0).toUpperCase()}
                                    </Avatar>
                                ))}
                                {data.collaborators.length > 3 && (
                                    <Avatar className="text-[10px] w-[24px] h-[24px] bg-gray-500" >
                                        +{data.collaborators.length - 3}
                                    </Avatar>
                                )}
                            </CollaboratorsAvatars>
                        )}

                        {/* Quick Actions */}
                        <Tooltip title="Toggle Favorite (Ctrl+F)">
                            <IconButton size="sm" onClick={handleFavoriteToggle} className="p-1">
                                {isFavorite ? (
                                    <Favorite size={16} tone="danger" />
                                ) : (
                                    <FavoriteBorder size={16} />
                                )}
                            </IconButton>
                        </Tooltip>

                        <Tooltip title="More actions">
                            <IconButton
                                ref={menuRef}
                                size="sm"
                                onClick={handleMenuOpen}
                                className="p-1"
                            >
                                <MoreVert size={16} />
                            </IconButton>
                        </Tooltip>

                        <DragIndicator className="ml-1 text-base text-gray-500 dark:text-gray-400" />
                    </Box>
                </NodeHeader>

                {/* Node Content */}
                <NodeContent>
                    {renderContent()}
                </NodeContent>

                {/* Context Menu */}
                <Menu
                    anchorEl={anchorEl}
                    open={Boolean(anchorEl)}
                    onClose={handleMenuClose}
                    anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'right',
                    }}
                    transformOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                    }}
                >
                    <MenuItem onClick={handleEdit}>
                        <Edit size={16} className="mr-2" />
                        Edit (E)
                    </MenuItem>
                    <MenuItem onClick={handleCopy}>
                        <ContentCopy size={16} className="mr-2" />
                        Copy (Ctrl+C)
                    </MenuItem>
                    <MenuItem onClick={handleShare}>
                        <Share size={16} className="mr-2" />
                        Share
                    </MenuItem>
                    <MenuItem onClick={handleDelete} className="text-red-600">
                        <Delete size={16} className="mr-2" />
                        Delete
                    </MenuItem>
                </Menu>
            </NodeContainer>
        </>
    );
};
