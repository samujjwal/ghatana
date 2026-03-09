/**
 * Canvas Showcase Demo
 * 
 * Interactive demonstration of all canvas features and utilities.
 * Shows persistence, history, export, and keyboard shortcuts in action.
 * 
 * @doc.type component
 * @doc.purpose Demo page for canvas system features
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useRef } from 'react';
import {
  Box,
  Typography,
  Button,
  Tabs,
  Tab,
  Alert,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';
import { CanvasToolbar, useCanvasToolbar } from '../components/canvas/CanvasToolbar';
import { useKeyboardShortcuts, ShortcutBadge } from '../utils/keyboardShortcuts';
import { createCommand } from '../utils/canvasHistory';

interface DemoItem {
    id: string;
    text: string;
    color: string;
    position: { x: number; y: number };
}

const INITIAL_ITEMS: DemoItem[] = [
    { id: '1', text: 'Welcome to Canvas Demo', color: '#6366F1', position: { x: 20, y: 20 } },
    { id: '2', text: 'Try adding items', color: '#10B981', position: { x: 20, y: 50 } },
    { id: '3', text: 'Use Undo/Redo', color: '#F59E0B', position: { x: 60, y: 20 } },
];

export const CanvasShowcaseDemo = () => {
    const canvasRef = useRef<HTMLDivElement>(null);
    const [items, setItems] = useState<DemoItem[]>(INITIAL_ITEMS);
    const [selectedTab, setSelectedTab] = useState(0);
    const [message, setMessage] = useState<string>('');

    // Setup toolbar with history and export
    const { history, handleExport } = useCanvasToolbar(
        items,
        canvasRef,
        'brainstorm',
        'system'
    );

    // Setup keyboard shortcuts
    useKeyboardShortcuts(history, {
        onUndo: (newState) => {
            if (newState) {
                setItems(newState);
                showMessage('Undo successful (⌘Z)');
            }
        },
        onRedo: (newState) => {
            if (newState) {
                setItems(newState);
                showMessage('Redo successful (⌘⇧Z)');
            }
        },
        onSave: () => {
            showMessage('State saved (⌘S)');
        },
        onExport: async () => {
            try {
                await handleExport('png');
                showMessage('Exported as PNG (⌘E)');
            } catch (error) {
                showMessage('Export requires: npm install html2canvas jspdf');
            }
        },
    });

    const showMessage = (msg: string) => {
        setMessage(msg);
        setTimeout(() => setMessage(''), 3000);
    };

    // Operations with history
    const addItem = () => {
        const newItem: DemoItem = {
            id: Date.now().toString(),
            text: `Item ${items.length + 1}`,
            color: ['#6366F1', '#10B981', '#F59E0B', '#EF4444'][Math.floor(Math.random() * 4)],
            position: { x: Math.random() * 70 + 10, y: Math.random() * 60 + 20 },
        };

        const newItems = history.execute(
            createCommand(items, [...items, newItem], 'Add item')
        );
        setItems(newItems);
        showMessage('Item added');
    };

    const deleteItem = (id: string) => {
        const newItems = history.execute(
            createCommand(
                items,
                items.filter(item => item.id !== id),
                'Delete item'
            )
        );
        setItems(newItems);
        showMessage('Item deleted');
    };

    const clearAll = () => {
        const newItems = history.execute(
            createCommand(items, [], 'Clear all')
        );
        setItems(newItems);
        showMessage('All items cleared');
    };

    return (
        <Box className="h-screen flex flex-col">
            {/* Header */}
            <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-[#6366F1] text-white">
                <Typography variant="h5" gutterBottom>
                    Canvas System Showcase
                </Typography>
                <Typography variant="body2">
                    Interactive demo of persistence, history, export, and keyboard shortcuts
                </Typography>
            </Box>

            {/* Toolbar with all features */}
            <CanvasToolbar
                history={history}
                onUndo={(newState) => newState && setItems(newState)}
                onRedo={(newState) => newState && setItems(newState)}
                onExport={handleExport}
                showHistory={true}
                showExport={true}
            />

            {/* Message banner */}
            {message && (
                <Alert severity="success" className="m-4 mb-0">
                    {message}
                </Alert>
            )}

            {/* Main content */}
            <Box className="flex flex-1 overflow-hidden">
                {/* Canvas area */}
                <Box className="flex-1 overflow-auto p-4">
                    <Paper
                        ref={canvasRef}
                        className="relative min-h-[500px] bg-[#F9FAFB] p-4"
                    >
                        {items.length === 0 ? (
                            <Box
                                className="flex items-center justify-center h-full min-h-[400px]"
                            >
                                <Typography variant="h6" color="text.secondary">
                                    Canvas is empty. Add some items!
                                </Typography>
                            </Box>
                        ) : (
                            items.map(item => (
                                <Paper
                                    key={item.id}
                                    elevation={3}
                                    className="absolute" style={{ left: `${item.position.x }}
                                >
                                    <Typography variant="body2" gutterBottom>
                                        {item.text}
                                    </Typography>
                                    <Button
                                        size="small"
                                        onClick={() => deleteItem(item.id)}
                                        className="text-white mt-2"
                                    >
                                        Delete
                                    </Button>
                                </Paper>
                            ))
                        )}
                    </Paper>
                </Box>

                {/* Sidebar with info and controls */}
                <Paper className="overflow-auto w-[300px] p-4">
                    <Tabs value={selectedTab} onChange={(_, v) => setSelectedTab(v)}>
                        <Tab label="Actions" />
                        <Tab label="Features" />
                        <Tab label="Shortcuts" />
                    </Tabs>

                    <Box className="mt-4">
                        {selectedTab === 0 && (
                            <Box className="flex flex-col gap-4">
                                <Typography variant="h6">Actions</Typography>
                                <Button variant="contained" fullWidth onClick={addItem}>
                                    Add Item
                                </Button>
                                <Button variant="outlined" fullWidth onClick={clearAll} disabled={items.length === 0}>
                                    Clear All
                                </Button>
                                <Box className="mt-4">
                                    <Typography variant="subtitle2" gutterBottom>
                                        History:
                                    </Typography>
                                    <Chip label={`Undo: ${history.canUndo() ? 'Available' : 'None'}`} size="small" />
                                    <Chip label={`Redo: ${history.canRedo() ? 'Available' : 'None'}`} size="small" className="ml-2" />
                                </Box>
                                <Box className="mt-4">
                                    <Typography variant="subtitle2" gutterBottom>
                                        State:
                                    </Typography>
                                    <Typography variant="caption">
                                        {items.length} items on canvas
                                    </Typography>
                                </Box>
                            </Box>
                        )}

                        {selectedTab === 1 && (
                            <Box>
                                <Typography variant="h6" gutterBottom>
                                    Features Demonstrated
                                </Typography>
                                <Box className="flex flex-col gap-4 mt-4">
                                    <Box>
                                        <Typography variant="subtitle2" color="primary">
                                            ✓ State Persistence
                                        </Typography>
                                        <Typography variant="caption">
                                            State automatically saves to localStorage and restores on page reload
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="subtitle2" color="primary">
                                            ✓ Undo/Redo
                                        </Typography>
                                        <Typography variant="caption">
                                            Command pattern tracks all changes with full history
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="subtitle2" color="primary">
                                            ✓ Export
                                        </Typography>
                                        <Typography variant="caption">
                                            Export canvas as PNG, SVG, or PDF (requires dependencies)
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="subtitle2" color="primary">
                                            ✓ Keyboard Shortcuts
                                        </Typography>
                                        <Typography variant="caption">
                                            Full keyboard support for all operations
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography variant="subtitle2" color="primary">
                                            ✓ Reusable Toolbar
                                        </Typography>
                                        <Typography variant="caption">
                                            Drop-in component for any canvas
                                        </Typography>
                                    </Box>
                                </Box>
                            </Box>
                        )}

                        {selectedTab === 2 && (
                            <Box>
                                <Typography variant="h6" gutterBottom>
                                    Keyboard Shortcuts
                                </Typography>
                                <Box className="flex flex-col gap-2 mt-4">
                                    <Box className="flex justify-between items-center">
                                        <Typography variant="body2">Undo</Typography>
                                        <ShortcutBadge shortcut="undo" />
                                    </Box>
                                    <Box className="flex justify-between items-center">
                                        <Typography variant="body2">Redo</Typography>
                                        <ShortcutBadge shortcut="redo" />
                                    </Box>
                                    <Box className="flex justify-between items-center">
                                        <Typography variant="body2">Save</Typography>
                                        <ShortcutBadge shortcut="save" />
                                    </Box>
                                    <Box className="flex justify-between items-center">
                                        <Typography variant="body2">Export</Typography>
                                        <ShortcutBadge shortcut="export" />
                                    </Box>
                                </Box>
                                <Alert severity="info" className="mt-4">
                                    Try the keyboard shortcuts! They work throughout the canvas.
                                </Alert>
                            </Box>
                        )}
                    </Box>
                </Paper>
            </Box>
        </Box>
    );
};

export default CanvasShowcaseDemo;
