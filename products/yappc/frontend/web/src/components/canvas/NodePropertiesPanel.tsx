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
  Divider,
  IconButton,
  Chip,
    MenuItem,
} from '@ghatana/design-system';
import { X as Close, Trash2 as Delete } from 'lucide-react';
import type { Node } from '@xyflow/react';

type EditableNodeData = {
    text?: string;
    color?: string;
    fontSize?: number;
    title?: string;
    width?: number;
    height?: number;
    shape?: string;
    label?: string;
    url?: string;
    alt?: string;
};

interface NodePropertiesPanelProps {
        selectedNode: Node<Record<string, unknown>> | null;
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
    const [localData, setLocalData] = useState<EditableNodeData>({});

    useEffect(() => {
        if (selectedNode) {
            setLocalData(selectedNode.data as EditableNodeData);
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

    const updateLocalData = (updates: Partial<EditableNodeData>) => {
        setLocalData(prev => ({ ...prev, ...updates }));
    };

    const renderLabeledInput = (
        label: string,
        value: string | number,
        onChange: (value: string) => void,
        type: 'text' | 'number' | 'color' | 'url' = 'text',
        placeholder?: string,
    ) => (
        <Box className="mb-4">
            <Typography variant="body2" className="mb-1 font-medium">
                {label}
            </Typography>
            <input
                type={type}
                value={value}
                placeholder={placeholder}
                onChange={(event) => onChange(event.target.value)}
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
        </Box>
    );

    const renderLabeledTextArea = (
        label: string,
        value: string,
        onChange: (value: string) => void,
        rows = 4,
    ) => (
        <Box className="mb-4">
            <Typography variant="body2" className="mb-1 font-medium">
                {label}
            </Typography>
            <textarea
                rows={rows}
                value={value}
                onChange={(event) => onChange(event.target.value)}
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
        </Box>
    );

    const renderRangeInput = (
        label: string,
        value: number,
        min: number,
        max: number,
        onChange: (value: number) => void,
    ) => (
        <Box className="mb-4">
            <Typography variant="body2" className="mb-1 font-medium">
                {label}: {value}
            </Typography>
            <input
                type="range"
                value={value}
                min={min}
                max={max}
                onChange={(event) => onChange(Number(event.target.value))}
                className="w-full"
            />
        </Box>
    );

    const renderStickyNoteControls = () => (
        <>
            {renderLabeledTextArea('Text', localData.text || '', (value) => updateLocalData({ text: value }))}

            <FormControl fullWidth>
                <Typography variant="body2" className="mb-1 font-medium">
                    Color
                </Typography>
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
            {renderLabeledInput('Text', localData.text || '', (value) => updateLocalData({ text: value }))}

            {renderRangeInput('Font Size', localData.fontSize || 16, 10, 48, (value) => updateLocalData({ fontSize: value }))}

            {renderLabeledInput('Color', localData.color || '#333', (value) => updateLocalData({ color: value }), 'color')}
        </>
    );

    const renderFrameControls = () => (
        <>
            {renderLabeledInput('Title', localData.title || '', (value) => updateLocalData({ title: value }))}

            <FormControl fullWidth>
                <Typography variant="body2" className="mb-1 font-medium">
                    Background Color
                </Typography>
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

            {renderLabeledInput('Width', localData.width || 400, (value) => updateLocalData({ width: Number.parseInt(value, 10) || 0 }), 'number')}

            {renderLabeledInput('Height', localData.height || 300, (value) => updateLocalData({ height: Number.parseInt(value, 10) || 0 }), 'number')}
        </>
    );

    const renderShapeControls = () => (
        <>
            <FormControl fullWidth>
                <Typography variant="body2" className="mb-1 font-medium">
                    Shape Type
                </Typography>
                <Select
                    value={localData.shape || 'rectangle'}
                    onChange={(e) => updateLocalData({ shape: e.target.value })}
                >
                    <MenuItem value="rectangle">Rectangle</MenuItem>
                    <MenuItem value="circle">Circle</MenuItem>
                    <MenuItem value="diamond">Diamond</MenuItem>
                </Select>
            </FormControl>

            {renderLabeledInput('Label', localData.label || '', (value) => updateLocalData({ label: value }))}

            <FormControl fullWidth>
                <Typography variant="body2" className="mb-1 font-medium">
                    Fill Color
                </Typography>
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
            {renderLabeledInput('Image URL', localData.url || '', (value) => updateLocalData({ url: value }), 'url', 'https://example.com/image.jpg')}
            {renderLabeledInput('Alt Text', localData.alt || '', (value) => updateLocalData({ alt: value }))}
            {renderLabeledInput('Width', localData.width || 200, (value) => updateLocalData({ width: Number.parseInt(value, 10) || 0 }), 'number')}
            {renderLabeledInput('Height', localData.height || 150, (value) => updateLocalData({ height: Number.parseInt(value, 10) || 0 }), 'number')}
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
                return renderFrameControls();
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
