/**
 * Code Preview Popover Component
 * 
 * Displays linked code implementations in a popover with syntax highlighting
 * 
 * @doc.type component
 * @doc.purpose Show code preview for artifact associations
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React, { useState } from 'react';
import { Popper as Popover, Box, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemButton, Divider, Button, Typography, Chip, IconButton, Surface as Paper, Spinner as CircularProgress, Alert } from '@ghatana/ui';
import { Code as CodeIcon, Bug as TestIcon, FileText as DocIcon, Drama as MockIcon, X as CloseIcon, ExternalLink as OpenIcon, Trash2 as DeleteIcon } from 'lucide-react';
import type { CodeAssociation, CodeRelationshipType } from '../../../hooks/useCodeAssociations';
import { getAssociationColor } from '../../../hooks/useCodeAssociations';

export interface CodePreviewPopoverProps {
    /** Anchor element for popover */
    anchorEl: HTMLElement | null;

    /** Code associations to display */
    associations: CodeAssociation[];

    /** Callback when popover closes */
    onClose: () => void;

    /** Callback to navigate to full code editor */
    onOpenCode?: (codeArtifactId: string) => void;

    /** Callback to delete association */
    onDeleteAssociation?: (associationId: string) => void;

    /** Loading state */
    isLoading?: boolean;

    /** Error state */
    error?: Error | null;
}

/**
 * Get icon component for relationship type
 */
function getRelationshipIcon(relationship: CodeRelationshipType): React.ReactElement {
    switch (relationship) {
        case 'IMPLEMENTATION':
            return <CodeIcon />;
        case 'TEST':
            return <TestIcon />;
        case 'DOCUMENTATION':
            return <DocIcon />;
        case 'MOCK':
            return <MockIcon />;
        default:
            return <CodeIcon />;
    }
}

/**
 * Simple syntax highlighter for code preview
 */
function CodePreview({ code, language }: { code: string; language?: string }) {
    return (
        <Paper
            className="p-4 text-sm overflow-auto max-h-[200px] bg-gray-900 text-gray-100" >
            <pre style={{ margin: 0, fontFamily: '"Fira Code' }}>
                <code>{code}</code>
            </pre>
        </Paper>
    );
}

/**
 * Code Preview Popover Component
 */
export const CodePreviewPopover: React.FC<CodePreviewPopoverProps> = ({
    anchorEl,
    associations,
    onClose,
    onOpenCode,
    onDeleteAssociation,
    isLoading = false,
    error = null,
}) => {
    const [selectedAssociation, setSelectedAssociation] = useState<CodeAssociation | null>(
        associations[0] || null
    );

    if (associations.length === 0 && !isLoading && !error) {
        return null;
    }

    return (
        <Popover
            open={Boolean(anchorEl)}
            anchorEl={anchorEl}
            onClose={onClose}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'left',
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'left',
            }}
            slotProps={{
                paper: {
                    sx: { width: 600, maxHeight: 500 },
                },
            }}
        >
            <Box className="flex flex-col h-full">
                {/* Header */}
                <Box
                    className="p-4 flex items-center justify-between border-gray-200 dark:border-gray-700 border-b" >
                    <Typography as="h6">Linked Code</Typography>
                    <IconButton size="sm" onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                </Box>

                {/* Loading State */}
                {isLoading && (
                    <Box className="flex justify-center items-center p-8">
                        <CircularProgress />
                    </Box>
                )}

                {/* Error State */}
                {error && (
                    <Box className="p-4">
                        <Alert severity="error">
                            Failed to load code associations: {error.message}
                        </Alert>
                    </Box>
                )}

                {/* Content */}
                {!isLoading && !error && (
                    <Box className="flex flex-1 overflow-hidden">
                        {/* Association List */}
                        <List
                            className="overflow-auto w-[200px] border-r border-gray-200 dark:border-gray-700"
                        >
                            {associations.map((assoc) => (
                                <ListItemButton
                                    key={assoc.id}
                                    selected={assoc.id === selectedAssociation?.id}
                                    onClick={() => setSelectedAssociation(assoc)}
                                >
                                    <ListItemIcon>
                                        {getRelationshipIcon(assoc.relationship)}
                                    </ListItemIcon>
                                    <ListItemText
                                        primary={
                                            <Box className="flex items-center gap-1">
                                                <Typography as="p" className="text-sm" noWrap>
                                                    {assoc.codeArtifact?.title || 'Untitled'}
                                                </Typography>
                                            </Box>
                                        }
                                        secondary={
                                            <Chip
                                                label={assoc.relationship}
                                                size="sm"
                                                color={getAssociationColor(assoc.relationship) as unknown}
                                                className="mt-1"
                                            />
                                        }
                                    />
                                </ListItemButton>
                            ))}
                        </List>

                        {/* Code Preview */}
                        <Box className="flex-1 flex flex-col overflow-hidden">
                            {selectedAssociation && (
                                <>
                                    {/* Code Details */}
                                    <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                                        <Typography as="p" className="text-lg font-medium" gutterBottom>
                                            {selectedAssociation.codeArtifact?.title}
                                        </Typography>
                                        {selectedAssociation.codeArtifact?.description && (
                                            <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                                {selectedAssociation.codeArtifact.description}
                                            </Typography>
                                        )}
                                        <Box className="flex gap-2 mt-2">
                                            <Chip
                                                label={selectedAssociation.codeArtifact?.type || 'CODE'}
                                                size="sm"
                                                variant="outlined"
                                            />
                                            {selectedAssociation.codeArtifact?.format && (
                                                <Chip
                                                    label={selectedAssociation.codeArtifact.format}
                                                    size="sm"
                                                    variant="outlined"
                                                />
                                            )}
                                        </Box>
                                    </Box>

                                    {/* Code Content */}
                                    <Box className="flex-1 overflow-auto p-4">
                                        {selectedAssociation.codeArtifact?.content ? (
                                            <CodePreview
                                                code={selectedAssociation.codeArtifact.content}
                                                language={selectedAssociation.codeArtifact.format}
                                            />
                                        ) : (
                                            <Typography color="text.secondary" align="center" className="py-8">
                                                No code content available
                                            </Typography>
                                        )}
                                    </Box>

                                    {/* Actions */}
                                    <Box
                                        className="p-4 flex gap-2 justify-between border-gray-200 dark:border-gray-700 border-t" >
                                        <Box className="flex gap-2">
                                            {onOpenCode && (
                                                <Button
                                                    variant="solid"
                                                    startIcon={<OpenIcon />}
                                                    onClick={() => onOpenCode(selectedAssociation.codeArtifactId)}
                                                >
                                                    Open Full Editor
                                                </Button>
                                            )}
                                        </Box>
                                        {onDeleteAssociation && (
                                            <IconButton
                                                tone="danger"
                                                onClick={() => {
                                                    onDeleteAssociation(selectedAssociation.id);
                                                    // Select next association or close if last one
                                                    const remainingAssocs = associations.filter(
                                                        (a) => a.id !== selectedAssociation.id
                                                    );
                                                    if (remainingAssocs.length > 0) {
                                                        setSelectedAssociation(remainingAssocs[0]);
                                                    } else {
                                                        onClose();
                                                    }
                                                }}
                                                title="Remove association"
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        )}
                                    </Box>
                                </>
                            )}
                        </Box>
                    </Box>
                )}
            </Box>
        </Popover>
    );
};
