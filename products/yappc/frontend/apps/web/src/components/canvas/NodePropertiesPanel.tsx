/**
 * Node Properties Panel
 * Side panel for editing selected node properties
 */

import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  Select,
  FormControl,
  InputLabel,
  Slider,
  Divider,
  IconButton,
  Chip,
} from '@ghatana/ui';
import { TextField, MenuItem } from '@ghatana/ui';
import { X as Close, Trash2 as Delete, Palette as ColorLens } from 'lucide-react';
import type { Node } from '@xyflow/react';

interface NodePropertiesPanelProps {
    selectedNode: Node | null;
    onNodeUpdate: (nodeId: string, updates: unknown) => void;
    onNodeDelete: (nodeId: string) => void;
    onClose: () => void;
}

export const NodePropertiesPanel: React.FC<NodePropertiesPanelProps> = ({
    selectedNode,
    onNodeUpdate,
    onNodeDelete,
    onClose,
}) => {
    const [localData, setLocalData] = useState<unknown>({});

    useEffect(() => {
        if (selectedNode) {
            setLocalData({ ...selectedNode.data });
        }
    }, [selectedNode]);

    if (!selectedNode) {
        return null;
    }

    const handleSave = () => {
        onNodeUpdate(selectedNode.id, localData);
    };

    const handleDelete = () => {
        onNodeDelete(selectedNode.id);
        onClose();
    };

    const handleColorChange = (color: string) => {
        setLocalData(prev => ({ ...prev, color }));
    };

    const renderStickyNoteControls = () => (
        <>
            <TextField
                label="Text"
                multiline
                rows={4}
                value={localData.text || ''}
                onChange={(e) => setLocalData(prev => ({ ...prev, text: e.target.value }))}
                fullWidth
                margin="normal"
            />

            <FormControl fullWidth margin="normal">
                <InputLabel>Color</InputLabel>
                <Select
                    value={localData.color || '#fff9c4'}
                    onChange={(e) => handleColorChange(e.target.value)}
                >
                    <MenuItem value="#fff9c4">Yellow</MenuItem>
                    <MenuItem value="#f8bbd0">Pink</MenuItem>
                    <MenuItem value="#bbdefb">Blue</MenuItem>
                    <MenuItem value="#c8e6c9">Green</MenuItem>
                    <MenuItem value="#e1bee7">Purple</MenuItem>
                </Select>
            </FormControl>
        </>
    );

    const renderTextControls = () => (
        <>
            <TextField
                label="Text"
                value={localData.text || ''}
                onChange={(e) => setLocalData(prev => ({ ...prev, text: e.target.value }))}
                fullWidth
                margin="normal"
            />

            <FormControl fullWidth margin="normal">
                <InputLabel>Font Size</InputLabel>
                <Slider
                    value={localData.fontSize || 16}
                    onChange={(e) => setLocalData(prev => ({ ...prev, fontSize: e.target.value }))}
                    min={10}
                    max={48}
                    marks={[
                        { value: 12, label: '12' },
                        { value: 16, label: '16' },
                        { value: 20, label: '20' },
                        { value: 24, label: '24' },
                        { value: 32, label: '32' },
                    ]}
                />
            </FormControl>

            <TextField
                label="Color"
                type="color"
                value={localData.color || '#333'}
                onChange={(e) => setLocalData(prev => ({ ...prev, color: e.target.value }))}
                fullWidth
                margin="normal"
            />
        </>
    );

    const renderFrameControls = () => (
        <>
            <TextField
                label="Title"
                value={localData.title || ''}
                onChange={(e) => setLocalData(prev => ({ ...prev, title: e.target.value }))}
                fullWidth
                margin="normal"
            />

            <FormControl fullWidth margin="normal">
                <InputLabel>Background Color</InputLabel>
                <Select
                    value={localData.color || '#e3f2fd'}
                    onChange={(e) => handleColorChange(e.target.value)}
                >
                    <MenuItem value="#e3f2fd">Blue</MenuItem>
                    <MenuItem value="#f3e5f5">Purple</MenuItem>
                    <MenuItem value="#e8f5e8">Green</MenuItem>
                    <MenuItem value="#fff3e0">Orange</MenuItem>
                    <MenuItem value="#fce4ec">Red</MenuItem>
                </Select>
            </FormControl>

            <TextField
                label="Width"
                type="number"
                value={localData.width || 400}
                onChange={(e) => setLocalData(prev => ({ ...prev, width: parseInt(e.target.value) }))}
                fullWidth
                margin="normal"
            />

            <TextField
                label="Height"
                type="number"
                value={localData.height || 300}
                onChange={(e) => setLocalData(prev => ({ ...prev, height: parseInt(e.target.value) }))}
                fullWidth
                margin="normal"
            />
        </>
    );

    const renderShapeControls = () => (
        <>
            <FormControl fullWidth margin="normal">
                <InputLabel>Shape Type</InputLabel>
                <Select
                    value={localData.shape || 'rectangle'}
                    onChange={(e) => setLocalData(prev => ({ ...prev, shape: e.target.value }))}
                >
                    <MenuItem value="rectangle">Rectangle</MenuItem>
                    <MenuItem value="circle">Circle</MenuItem>
                    <MenuItem value="diamond">Diamond</MenuItem>
                </Select>
            </FormControl>

            <TextField
                label="Label"
                value={localData.label || ''}
                onChange={(e) => setLocalData(prev => ({ ...prev, label: e.target.value }))}
                fullWidth
                margin="normal"
            />

            <FormControl fullWidth margin="normal">
                <InputLabel>Fill Color</InputLabel>
                <Select
                    value={localData.color || '#e3f2fd'}
                    onChange={(e) => handleColorChange(e.target.value)}
                >
                    <MenuItem value="#e3f2fd">Blue</MenuItem>
                    <MenuItem value="#fff3e0">Orange</MenuItem>
                    <MenuItem value="#f3e5f5">Purple</MenuItem>
                    <MenuItem value="#e8f5e8">Green</MenuItem>
                    <MenuItem value="#fce4ec">Red</MenuItem>
                </Select>
            </FormControl>
        </>
    );

    const renderImageControls = () => (
        <>
            <TextField
                label="Image URL"
                value={localData.url || ''}
                onChange={(e) => setLocalData(prev => ({ ...prev, url: e.target.value }))}
                fullWidth
                margin="normal"
                placeholder="https://example.com/image.jpg"
            />

            <TextField
                label="Alt Text"
                value={localData.alt || ''}
                onChange={(e) => setLocalData(prev => ({ ...prev, alt: e.target.value }))}
                fullWidth
                margin="normal"
            />

            <TextField
                label="Width"
                type="number"
                value={localData.width || 200}
                onChange={(e) => setLocalData(prev => ({ ...prev, width: parseInt(e.target.value) }))}
                fullWidth
                margin="normal"
            />

            <TextField
                label="Height"
                type="number"
                value={localData.height || 150}
                onChange={(e) => setLocalData(prev => ({ ...prev, height: parseInt(e.target.value) }))}
                fullWidth
                margin="normal"
            />
        </>
    );

    const renderControls = () => {
        switch (selectedNode.type) {
            case 'sticky-note':
            case 'sticky':
                return renderStickyNoteControls();
            case 'text':
                return renderTextControls();
            case 'frame':
                return renderControls();
            case 'rectangle':
            case 'circle':
            case 'diamond':
                return renderShapeControls();
            case 'image':
                return renderImageControls();
            default:
                return (
                    <Typography variant="body2" color="text.secondary">
                        No properties available for this node type
                    </Typography>
                );
        }
    };

    return (
        <Box
            className="h-full flex flex-col w-[300px] bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-700" style={{ borderLeft: '1px solid' }} >
            {/* Header */}
            <Box
                className="flex items-center justify-between p-4 border-b border-solid border-gray-200 dark:border-gray-700"
            >
                <Typography variant="h6" className="font-semibold">
                    Node Properties
                </Typography>
                <IconButton size="small" onClick={onClose}>
                    <Close />
                </IconButton>
            </Box>

            {/* Node Type */}
            <Box className="p-4">
                <Chip
                    label={`Type: ${selectedNode.type}`}
                    size="small"
                    className="mb-4"
                />
                <Chip
                    label={`ID: ${selectedNode.id.slice(-8)}`}
                    size="small"
                    variant="outlined"
                />
            </Box>

            <Divider />

            {/* Properties Form */}
            <Box className="flex-1 overflow-y-auto p-4">
                {renderControls()}
            </Box>

            {/* Actions */}
            <Box className="border-gray-200 dark:border-gray-700 p-4 border-t border-solid">
                <Button
                    variant="contained"
                    onClick={handleSave}
                    fullWidth
                    className="mb-2"
                >
                    Save Changes
                </Button>
                <Button
                    variant="outlined"
                    color="error"
                    onClick={handleDelete}
                    startIcon={<Delete />}
                    fullWidth
                >
                    Delete Node
                </Button>
            </Box>
        </Box>
    );
};

NodePropertiesPanel.displayName = 'NodePropertiesPanel';
