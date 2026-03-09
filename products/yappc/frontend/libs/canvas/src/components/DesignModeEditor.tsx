/**
 * @doc.type component
 * @doc.purpose Design mode editor for Journey 5.1 (UX Designer - High-Fidelity Mockups)
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Surface as Paper, Box, Typography, IconButton, Button, Tooltip, Divider, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemButton, Collapse, TextField, Slider, ToggleButton, ToggleButtonGroup, Chip } from '@ghatana/ui';
import { X as CloseIcon, ZoomIn as ZoomInIcon, ZoomOut as ZoomOutIcon, Undo2 as UndoIcon, Redo2 as RedoIcon, Save as SaveIcon, Grid3x3 as GridIcon, Link as LinkIcon, ChevronDown as ExpandIcon, ChevronUp as CollapseIcon, UserCircle as AvatarIcon, Type as TextFieldIcon, SquareCheck as CheckBoxIcon, CircleDot as RadioIcon, Image as ImageIcon, RectangleHorizontal as ButtonIcon, List as ListIcon, Columns as ColumnIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';

/**
 * Design component type
 */
export type DesignComponentType =
    | 'avatar'
    | 'textfield'
    | 'button'
    | 'checkbox'
    | 'radio'
    | 'image'
    | 'list'
    | 'container'
    | 'text';

/**
 * Design component definition
 */
export interface DesignComponent {
    id: string;
    type: DesignComponentType;
    x: number;
    y: number;
    width: number;
    height: number;
    props: Record<string, unknown>;
    label?: string;
}

/**
 * Prototype link
 */
export interface PrototypeLink {
    id: string;
    from: string; // component ID
    to: string; // target node ID
    event: 'click' | 'hover' | 'submit';
}

/**
 * Component props
 */
export interface DesignModeEditorProps {
    /**
     * Node being edited
     */
    node: Node;

    /**
     * Close handler
     */
    onClose: () => void;

    /**
     * Save handler
     */
    onSave?: (components: DesignComponent[], links: PrototypeLink[]) => void;

    /**
     * Create link handler
     */
    onCreateLink?: (from: string, to: string) => void;
}

/**
 * Component library item
 */
const COMPONENT_LIBRARY: Array<{
    type: DesignComponentType;
    label: string;
    icon: React.ReactElement;
    defaultWidth: number;
    defaultHeight: number;
}> = [
        { type: 'avatar', label: 'Avatar', icon: <AvatarIcon />, defaultWidth: 48, defaultHeight: 48 },
        { type: 'textfield', label: 'Text Field', icon: <TextFieldIcon />, defaultWidth: 200, defaultHeight: 40 },
        { type: 'button', label: 'Button', icon: <ButtonIcon />, defaultWidth: 120, defaultHeight: 36 },
        { type: 'checkbox', label: 'Checkbox', icon: <CheckBoxIcon />, defaultWidth: 24, defaultHeight: 24 },
        { type: 'radio', label: 'Radio', icon: <RadioIcon />, defaultWidth: 24, defaultHeight: 24 },
        { type: 'image', label: 'Image', icon: <ImageIcon />, defaultWidth: 200, defaultHeight: 150 },
        { type: 'list', label: 'List', icon: <ListIcon />, defaultWidth: 250, defaultHeight: 200 },
        { type: 'container', label: 'Container', icon: <ColumnIcon />, defaultWidth: 300, defaultHeight: 200 },
    ];

/**
 * Design Mode Editor Component
 * 
 * Mini Figma-like editor for creating high-fidelity UI mockups within canvas nodes.
 * Supports drag-and-drop components, prototype linking, and grid snapping.
 * 
 * @example
 * ```tsx
 * <DesignModeEditor
 *   node={selectedNode}
 *   onClose={handleClose}
 *   onSave={(components, links) => console.log('Saved:', components, links)}
 * />
 * ```
 */
export const DesignModeEditor: React.FC<DesignModeEditorProps> = ({
    node,
    onClose,
    onSave,
    onCreateLink,
}) => {
    const [components, setComponents] = useState<DesignComponent[]>(
        node.data.designComponents || []
    );
    const [links, setLinks] = useState<PrototypeLink[]>(
        node.data.prototypeLinks || []
    );
    const [selectedComponent, setSelectedComponent] = useState<string | null>(null);
    const [zoom, setZoom] = useState(100);
    const [showGrid, setShowGrid] = useState(true);
    const [libraryOpen, setLibraryOpen] = useState(true);
    const [linkMode, setLinkMode] = useState(false);
    const [linkFrom, setLinkFrom] = useState<string | null>(null);
    const [history, setHistory] = useState<DesignComponent[][]>([]);
    const [historyIndex, setHistoryIndex] = useState(-1);

    // Canvas size
    const canvasWidth = 800;
    const canvasHeight = 600;

    // Add component to canvas
    const handleAddComponent = useCallback((type: DesignComponentType) => {
        const libraryItem = COMPONENT_LIBRARY.find(item => item.type === type);
        if (!libraryItem) return;

        const newComponent: DesignComponent = {
            id: `${type}-${Date.now()}`,
            type,
            x: 50,
            y: 50,
            width: libraryItem.defaultWidth,
            height: libraryItem.defaultHeight,
            props: {},
            label: libraryItem.label,
        };

        setComponents(prev => {
            const newState = [...prev, newComponent];
            // Add to history
            setHistory(h => [...h.slice(0, historyIndex + 1), newState]);
            setHistoryIndex(i => i + 1);
            return newState;
        });
    }, [historyIndex]);

    // Select component
    const handleSelectComponent = useCallback((id: string) => {
        if (linkMode && linkFrom) {
            // Complete link
            setLinks(prev => [
                ...prev,
                {
                    id: `link-${Date.now()}`,
                    from: linkFrom,
                    to: id,
                    event: 'click',
                },
            ]);
            setLinkMode(false);
            setLinkFrom(null);
        } else {
            setSelectedComponent(id);
        }
    }, [linkMode, linkFrom]);

    // Delete component
    const handleDeleteComponent = useCallback((id: string) => {
        setComponents(prev => {
            const newState = prev.filter(c => c.id !== id);
            setHistory(h => [...h.slice(0, historyIndex + 1), newState]);
            setHistoryIndex(i => i + 1);
            return newState;
        });
        setSelectedComponent(null);
    }, [historyIndex]);

    // Update component position
    const handleUpdatePosition = useCallback((id: string, x: number, y: number) => {
        setComponents(prev =>
            prev.map(c => (c.id === id ? { ...c, x, y } : c))
        );
    }, []);

    // Update component size
    const handleUpdateSize = useCallback((id: string, width: number, height: number) => {
        setComponents(prev =>
            prev.map(c => (c.id === id ? { ...c, width, height } : c))
        );
    }, []);

    // Start link creation
    const handleStartLink = useCallback((fromId: string) => {
        setLinkMode(true);
        setLinkFrom(fromId);
    }, []);

    // Undo
    const handleUndo = useCallback(() => {
        if (historyIndex > 0) {
            setHistoryIndex(i => i - 1);
            setComponents(history[historyIndex - 1]);
        }
    }, [history, historyIndex]);

    // Redo
    const handleRedo = useCallback(() => {
        if (historyIndex < history.length - 1) {
            setHistoryIndex(i => i + 1);
            setComponents(history[historyIndex + 1]);
        }
    }, [history, historyIndex]);

    // Save
    const handleSave = useCallback(() => {
        if (onSave) {
            onSave(components, links);
        }
    }, [components, links, onSave]);

    // Zoom controls
    const handleZoomIn = () => setZoom(z => Math.min(200, z + 10));
    const handleZoomOut = () => setZoom(z => Math.max(25, z - 10));

    // Selected component details
    const selectedComponentData = useMemo(() => {
        return components.find(c => c.id === selectedComponent);
    }, [components, selectedComponent]);

    return (
        <Paper
            className="fixed flex flex-col top-[50%] left-[50%] w-[90vw] h-[90vh] max-w-[1400px] z-[2000]" style={{ transform: 'translate(-50%' }} >
            {/* Header */}
            <Box
                className="p-4 flex justify-between items-center border-gray-200 dark:border-gray-700 border-b" >
                <Box className="flex gap-4 items-center">
                    <Typography as="h6">Design Mode</Typography>
                    <Chip label={node.data.label || node.data.name} tone="primary" />
                </Box>

                <Box className="flex gap-2">
                    <Tooltip title="Undo">
                        <IconButton onClick={handleUndo} disabled={historyIndex <= 0} size="sm">
                            <UndoIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Redo">
                        <IconButton onClick={handleRedo} disabled={historyIndex >= history.length - 1} size="sm">
                            <RedoIcon />
                        </IconButton>
                    </Tooltip>
                    <Divider orientation="vertical" flexItem />
                    <Tooltip title="Zoom In">
                        <IconButton onClick={handleZoomIn} size="sm">
                            <ZoomInIcon />
                        </IconButton>
                    </Tooltip>
                    <Typography as="p" className="text-sm px-2 flex items-center">
                        {zoom}%
                    </Typography>
                    <Tooltip title="Zoom Out">
                        <IconButton onClick={handleZoomOut} size="sm">
                            <ZoomOutIcon />
                        </IconButton>
                    </Tooltip>
                    <Divider orientation="vertical" flexItem />
                    <Tooltip title="Toggle Grid">
                        <IconButton onClick={() => setShowGrid(!showGrid)} size="sm" color={showGrid ? 'primary' : 'default'}>
                            <GridIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Link Mode">
                        <IconButton onClick={() => setLinkMode(!linkMode)} size="sm" color={linkMode ? 'primary' : 'default'}>
                            <LinkIcon />
                        </IconButton>
                    </Tooltip>
                    <Divider orientation="vertical" flexItem />
                    <Button startIcon={<SaveIcon />} onClick={handleSave} variant="solid" size="sm">
                        Save
                    </Button>
                    <IconButton onClick={onClose} size="sm">
                        <CloseIcon />
                    </IconButton>
                </Box>
            </Box>

            {/* Main Content */}
            <Box className="flex-1 flex overflow-hidden">
                {/* Component Library */}
                <Paper
                    className="flex flex-col overflow-auto w-[250px] border-r border-gray-200 dark:border-gray-700"
                    variant="flat"
                >
                    <Box
                        className="p-2 flex justify-between items-center cursor-pointer"
                        onClick={() => setLibraryOpen(!libraryOpen)}
                    >
                        <Typography as="p" className="text-sm font-medium">Component Library</Typography>
                        {libraryOpen ? <CollapseIcon /> : <ExpandIcon />}
                    </Box>
                    <Collapse in={libraryOpen}>
                        <List dense>
                            {COMPONENT_LIBRARY.map(item => (
                                <ListItemButton key={item.type} onClick={() => handleAddComponent(item.type)}>
                                    <ListItemIcon>{item.icon}</ListItemIcon>
                                    <ListItemText primary={item.label} />
                                </ListItemButton>
                            ))}
                        </List>
                    </Collapse>

                    <Divider />

                    {/* Component Stats */}
                    <Box className="p-4">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Components: {components.length}
                        </Typography>
                        <br />
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Links: {links.length}
                        </Typography>
                    </Box>
                </Paper>

                {/* Canvas */}
                <Box className="flex-1 overflow-auto relative bg-[#f5f5f5]">
                    <Box
                        className="bg-white shadow relative" style={{ width: canvasWidth * (zoom / 100), height: canvasHeight * (zoom / 100), backgroundImage: showGrid
                                ? 'repeating-linear-gradient(0deg, #e0e0e0, #e0e0e0 1px, transparent 1px, transparent 20px), repeating-linear-gradient(90deg, #e0e0e0, #e0e0e0 1px, transparent 1px, transparent 20px)'
                                : 'none', backgroundSize: showGrid ? '20px 20px' : 'auto', margin: '20px auto' }} >
                        {/* Render components */}
                        {components.map(component => (
                            <Box
                                key={component.id}
                                onClick={() => handleSelectComponent(component.id)}
                                className="absolute cursor-pointer flex items-center justify-center bg-white dark:bg-gray-900 border-dashed" style={{ left: component.x * (zoom / 100), top: component.y * (zoom / 100), width: component.width * (zoom / 100), height: component.height * (zoom / 100), border: selectedComponent === component.id ? 2 : 1, borderColor: selectedComponent === component.id ? 'primary.main' : 'divider' }} >
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {component.label}
                                </Typography>
                            </Box>
                        ))}

                        {/* Render prototype links */}
                        {links.map(link => {
                            const fromComp = components.find(c => c.id === link.from);
                            if (!fromComp) return null;

                            return (
                                <svg
                                    key={link.id}
                                    style={{
                                        position: 'absolute',
                                        top: 0,
                                        left: 0,
                                        width: '100%',
                                        height: '100%',
                                        pointerEvents: 'none',
                                    }}
                                >
                                    <line
                                        x1={(fromComp.x + fromComp.width) * (zoom / 100)}
                                        y1={(fromComp.y + fromComp.height / 2) * (zoom / 100)}
                                        x2={(fromComp.x + fromComp.width + 50) * (zoom / 100)}
                                        y2={(fromComp.y + fromComp.height / 2) * (zoom / 100)}
                                        stroke="#2196f3"
                                        strokeWidth="2"
                                        strokeDasharray="5,5"
                                    />
                                </svg>
                            );
                        })}
                    </Box>
                </Box>

                {/* Properties Panel */}
                {selectedComponentData && (
                    <Paper
                        className="p-4 overflow-auto w-[300px] border-gray-200 dark:border-gray-700 border-l" variant="flat"
                    >
                        <Box className="mb-4">
                            <Box className="flex justify-between items-center mb-4">
                                <Typography as="p" className="text-sm font-medium">Properties</Typography>
                                <IconButton size="sm" onClick={() => handleDeleteComponent(selectedComponentData.id)}>
                                    <CloseIcon />
                                </IconButton>
                            </Box>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {selectedComponentData.type}
                            </Typography>
                        </Box>

                        <Divider className="my-4" />

                        {/* Position */}
                        <Box className="mb-4">
                            <Typography as="p" className="text-sm" gutterBottom>
                                Position
                            </Typography>
                            <Box className="grid gap-2 grid-cols-2">
                                <TextField
                                    label="X"
                                    type="number"
                                    value={selectedComponentData.x}
                                    onChange={e =>
                                        handleUpdatePosition(selectedComponentData.id, Number(e.target.value), selectedComponentData.y)
                                    }
                                    size="sm"
                                />
                                <TextField
                                    label="Y"
                                    type="number"
                                    value={selectedComponentData.y}
                                    onChange={e =>
                                        handleUpdatePosition(selectedComponentData.id, selectedComponentData.x, Number(e.target.value))
                                    }
                                    size="sm"
                                />
                            </Box>
                        </Box>

                        {/* Size */}
                        <Box className="mb-4">
                            <Typography as="p" className="text-sm" gutterBottom>
                                Size
                            </Typography>
                            <Box className="grid gap-2 grid-cols-2">
                                <TextField
                                    label="Width"
                                    type="number"
                                    value={selectedComponentData.width}
                                    onChange={e =>
                                        handleUpdateSize(selectedComponentData.id, Number(e.target.value), selectedComponentData.height)
                                    }
                                    size="sm"
                                />
                                <TextField
                                    label="Height"
                                    type="number"
                                    value={selectedComponentData.height}
                                    onChange={e =>
                                        handleUpdateSize(selectedComponentData.id, selectedComponentData.width, Number(e.target.value))
                                    }
                                    size="sm"
                                />
                            </Box>
                        </Box>

                        <Divider className="my-4" />

                        {/* Create Link */}
                        <Button
                            fullWidth
                            variant="outlined"
                            startIcon={<LinkIcon />}
                            onClick={() => handleStartLink(selectedComponentData.id)}
                            disabled={linkMode}
                        >
                            Create Prototype Link
                        </Button>
                    </Paper>
                )}
            </Box>
        </Paper>
    );
};
