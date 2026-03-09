/**
 * Prototype Link Tool
 * 
 * Tool for creating interactive prototype links between components.
 * Part of Journey 5.1: UX Designer - High-Fidelity Mockups.
 * 
 * @doc.type component
 * @doc.purpose Prototype link tool for design mode
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';
import { Surface as Paper, Box, Typography, Button, IconButton, Select, MenuItem, FormControl, InputLabel, InteractiveList as List, ListItem, ListItemText, ListItemText as ListItemSecondaryAction, Chip, Tooltip, Alert, Divider } from '@ghatana/ui';
import { Link as LinkIcon, Trash2 as DeleteIcon, Pencil as EditIcon, X as CloseIcon, MousePointer as ClickIcon, TouchAppOutlined as HoverIcon, SendOutlined as SubmitIcon } from 'lucide-react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

/**
 * Link event type
 */
export type LinkEventType = 'click' | 'hover' | 'submit';

/**
 * Prototype link
 */
export interface PrototypeLink {
    id: string;
    fromComponentId: string;
    fromComponentLabel?: string;
    toNodeId: string;
    toNodeLabel?: string;
    event: LinkEventType;
    transition?: 'instant' | 'slide' | 'fade' | 'push';
    duration?: number;
}

/**
 * Available target node
 */
export interface LinkTarget {
    id: string;
    label: string;
    type: string;
}

/**
 * Component props
 */
export interface PrototypeLinkToolProps {
    /**
     * Current prototype links
     */
    links: PrototypeLink[];

    /**
     * Available components to link from
     */
    components: Array<{ id: string; label: string; type: string }>;

    /**
     * Available nodes to link to
     */
    nodes: LinkTarget[];

    /**
     * Link creation handler
     */
    onCreateLink: (link: Omit<PrototypeLink, 'id'>) => void;

    /**
     * Link update handler
     */
    onUpdateLink: (linkId: string, updates: Partial<PrototypeLink>) => void;

    /**
     * Link deletion handler
     */
    onDeleteLink: (linkId: string) => void;

    /**
     * Close handler
     */
    onClose?: () => void;

    /**
     * Whether link mode is active
     */
    linkModeActive?: boolean;

    /**
     * Active component ID (when in link mode)
     */
    activeComponentId?: string;
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

const eventIcons: Record<LinkEventType, React.ReactElement> = {
    click: <ClickIcon size={16} />,
    hover: <HoverIcon size={16} />,
    submit: <SubmitIcon size={16} />,
};

const eventLabels: Record<LinkEventType, string> = {
    click: 'Click',
    hover: 'Hover',
    submit: 'Submit',
};

// ============================================================================
// COMPONENT IMPLEMENTATION
// ============================================================================

/**
 * Prototype Link Tool
 * 
 * Usage:
 * ```tsx
 * <PrototypeLinkTool
 *   links={prototypeLinks}
 *   components={designComponents}
 *   nodes={canvasNodes}
 *   onCreateLink={(link) => setLinks([...links, { ...link, id: generateId() }])}
 *   onDeleteLink={(id) => setLinks(links.filter(l => l.id !== id))}
 * />
 * ```
 */
export const PrototypeLinkTool: React.FC<PrototypeLinkToolProps> = ({
    links,
    components,
    nodes,
    onCreateLink,
    onUpdateLink,
    onDeleteLink,
    onClose,
    linkModeActive = false,
    activeComponentId,
}) => {
    const [isCreating, setIsCreating] = useState(false);
    const [editingLinkId, setEditingLinkId] = useState<string | null>(null);

    // Form state
    const [fromComponentId, setFromComponentId] = useState<string>('');
    const [toNodeId, setToNodeId] = useState<string>('');
    const [event, setEvent] = useState<LinkEventType>('click');
    const [transition, setTransition] = useState<'instant' | 'slide' | 'fade' | 'push'>('instant');
    const [duration, setDuration] = useState<number>(300);

    // Reset form
    const resetForm = useCallback(() => {
        setFromComponentId('');
        setToNodeId('');
        setEvent('click');
        setTransition('instant');
        setDuration(300);
        setIsCreating(false);
        setEditingLinkId(null);
    }, []);

    // Handle create link
    const handleCreateLink = useCallback(() => {
        if (!fromComponentId || !toNodeId) return;

        const fromComponent = components.find(c => c.id === fromComponentId);
        const toNode = nodes.find(n => n.id === toNodeId);

        onCreateLink({
            fromComponentId,
            fromComponentLabel: fromComponent?.label,
            toNodeId,
            toNodeLabel: toNode?.label,
            event,
            transition,
            duration,
        });

        resetForm();
    }, [fromComponentId, toNodeId, event, transition, duration, components, nodes, onCreateLink, resetForm]);

    // Handle edit link
    const handleEditLink = useCallback((link: PrototypeLink) => {
        setEditingLinkId(link.id);
        setFromComponentId(link.fromComponentId);
        setToNodeId(link.toNodeId);
        setEvent(link.event);
        setTransition(link.transition || 'instant');
        setDuration(link.duration || 300);
        setIsCreating(true);
    }, []);

    // Handle update link
    const handleUpdateLink = useCallback(() => {
        if (!editingLinkId) return;

        const fromComponent = components.find(c => c.id === fromComponentId);
        const toNode = nodes.find(n => n.id === toNodeId);

        onUpdateLink(editingLinkId, {
            fromComponentId,
            fromComponentLabel: fromComponent?.label,
            toNodeId,
            toNodeLabel: toNode?.label,
            event,
            transition,
            duration,
        });

        resetForm();
    }, [editingLinkId, fromComponentId, toNodeId, event, transition, duration, components, nodes, onUpdateLink, resetForm]);

    // Auto-populate from component when in link mode
    React.useEffect(() => {
        if (linkModeActive && activeComponentId && !isCreating) {
            setFromComponentId(activeComponentId);
            setIsCreating(true);
        }
    }, [linkModeActive, activeComponentId, isCreating]);

    return (
        <Paper
            elevation={2}
            className="h-full flex flex-col overflow-hidden w-[320px]"
        >
            {/* Header */}
            <Box
                className="p-4 flex items-center justify-between border-gray-200 dark:border-gray-700 border-b" >
                <Box className="flex items-center gap-2">
                    <LinkIcon />
                    <Typography as="h6">Prototype Links</Typography>
                </Box>
                {onClose && (
                    <IconButton size="sm" onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                )}
            </Box>

            {/* Content */}
            <Box className="flex-1 overflow-auto p-4">
                {/* Link Mode Alert */}
                {linkModeActive && (
                    <Alert severity="info" className="mb-4">
                        Click a target node to create a link
                    </Alert>
                )}

                {/* Create/Edit Form */}
                {isCreating ? (
                    <Box className="mb-6">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            {editingLinkId ? 'Edit Link' : 'Create Link'}
                        </Typography>

                        {/* From Component */}
                        <FormControl fullWidth className="mb-4" size="sm">
                            <InputLabel>From Component</InputLabel>
                            <Select
                                value={fromComponentId}
                                onChange={e => setFromComponentId(e.target.value)}
                                label="From Component"
                                disabled={linkModeActive}
                            >
                                {components.map(component => (
                                    <MenuItem key={component.id} value={component.id}>
                                        {component.label || component.type}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {/* To Node */}
                        <FormControl fullWidth className="mb-4" size="sm">
                            <InputLabel>To Screen/Node</InputLabel>
                            <Select
                                value={toNodeId}
                                onChange={e => setToNodeId(e.target.value)}
                                label="To Screen/Node"
                            >
                                {nodes.map(node => (
                                    <MenuItem key={node.id} value={node.id}>
                                        {node.label || node.type}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {/* Event Type */}
                        <FormControl fullWidth className="mb-4" size="sm">
                            <InputLabel>Event</InputLabel>
                            <Select
                                value={event}
                                onChange={e => setEvent(e.target.value as LinkEventType)}
                                label="Event"
                            >
                                <MenuItem value="click">
                                    <Box className="flex items-center gap-2">
                                        <ClickIcon size={16} />
                                        Click
                                    </Box>
                                </MenuItem>
                                <MenuItem value="hover">
                                    <Box className="flex items-center gap-2">
                                        <HoverIcon size={16} />
                                        Hover
                                    </Box>
                                </MenuItem>
                                <MenuItem value="submit">
                                    <Box className="flex items-center gap-2">
                                        <SubmitIcon size={16} />
                                        Submit
                                    </Box>
                                </MenuItem>
                            </Select>
                        </FormControl>

                        {/* Transition */}
                        <FormControl fullWidth className="mb-4" size="sm">
                            <InputLabel>Transition</InputLabel>
                            <Select
                                value={transition}
                                onChange={e => setTransition(e.target.value as unknown)}
                                label="Transition"
                            >
                                <MenuItem value="instant">Instant</MenuItem>
                                <MenuItem value="slide">Slide</MenuItem>
                                <MenuItem value="fade">Fade</MenuItem>
                                <MenuItem value="push">Push</MenuItem>
                            </Select>
                        </FormControl>

                        {/* Duration (if not instant) */}
                        {transition !== 'instant' && (
                            <FormControl fullWidth className="mb-4" size="sm">
                                <InputLabel>Duration (ms)</InputLabel>
                                <Select
                                    value={duration}
                                    onChange={e => setDuration(Number(e.target.value))}
                                    label="Duration (ms)"
                                >
                                    <MenuItem value={150}>150ms (Fast)</MenuItem>
                                    <MenuItem value={300}>300ms (Normal)</MenuItem>
                                    <MenuItem value={500}>500ms (Slow)</MenuItem>
                                    <MenuItem value={1000}>1000ms (Very Slow)</MenuItem>
                                </Select>
                            </FormControl>
                        )}

                        {/* Actions */}
                        <Box className="flex gap-2">
                            <Button
                                fullWidth
                                variant="solid"
                                onClick={editingLinkId ? handleUpdateLink : handleCreateLink}
                                disabled={!fromComponentId || !toNodeId}
                            >
                                {editingLinkId ? 'Update' : 'Create'}
                            </Button>
                            <Button fullWidth variant="outlined" onClick={resetForm}>
                                Cancel
                            </Button>
                        </Box>
                    </Box>
                ) : (
                    <Button
                        fullWidth
                        variant="outlined"
                        startIcon={<LinkIcon />}
                        onClick={() => setIsCreating(true)}
                    >
                        New Link
                    </Button>
                )}

                <Divider className="my-4" />

                {/* Link List */}
                <Typography as="p" className="text-sm font-medium" gutterBottom>
                    Existing Links ({links.length})
                </Typography>

                {links.length === 0 ? (
                    <Typography as="p" className="text-sm" color="text.secondary" className="text-center py-6">
                        No prototype links yet
                    </Typography>
                ) : (
                    <List dense>
                        {links.map(link => (
                            <ListItem
                                key={link.id}
                                className="rounded mb-2 border border-gray-200 dark:border-gray-700"
                            >
                                <ListItemText
                                    primary={
                                        <Box className="flex items-center gap-2">
                                            <Chip
                                                icon={eventIcons[link.event]}
                                                label={eventLabels[link.event]}
                                                size="sm"
                                                tone="primary"
                                            />
                                            {link.transition !== 'instant' && (
                                                <Chip
                                                    label={`${link.transition} (${link.duration}ms)`}
                                                    size="sm"
                                                    variant="outlined"
                                                />
                                            )}
                                        </Box>
                                    }
                                    secondary={
                                        <Typography as="span" className="text-xs text-gray-500" component="div">
                                            {link.fromComponentLabel || link.fromComponentId} →{' '}
                                            {link.toNodeLabel || link.toNodeId}
                                        </Typography>
                                    }
                                />
                                <ListItemSecondaryAction>
                                    <Tooltip title="Edit">
                                        <IconButton edge="end" size="sm" onClick={() => handleEditLink(link)} className="mr-1">
                                            <EditIcon size={16} />
                                        </IconButton>
                                    </Tooltip>
                                    <Tooltip title="Delete">
                                        <IconButton edge="end" size="sm" onClick={() => onDeleteLink(link.id)}>
                                            <DeleteIcon size={16} />
                                        </IconButton>
                                    </Tooltip>
                                </ListItemSecondaryAction>
                            </ListItem>
                        ))}
                    </List>
                )}
            </Box>

            {/* Footer */}
            <Box className="p-2 border-gray-200 dark:border-gray-700 border-t" >
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" align="center" display="block">
                    Create interactive prototype flows
                </Typography>
            </Box>
        </Paper>
    );
};
