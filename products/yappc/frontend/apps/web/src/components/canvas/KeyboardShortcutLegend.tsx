/**
 * Keyboard Shortcut Legend
 * 
 * Shows all available keyboard shortcuts in an organized panel
 * 
 * @doc.type component
 * @doc.purpose Shortcut discovery
 * @doc.layer presentation
 */

import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  Box,
  Typography,
  Chip,
  Grid,
  IconButton,
} from '@ghatana/ui';
import { X as Close, Apple, Settings } from 'lucide-react';

interface ShortcutGroup {
    category: string;
    shortcuts: Array<{
        keys: string[];
        description: string;
    }>;
}

const SHORTCUT_GROUPS: ShortcutGroup[] = [
    {
        category: 'Essential',
        shortcuts: [
            { keys: ['⌘', 'Z'], description: 'Undo' },
            { keys: ['⌘', '⇧', 'Z'], description: 'Redo' },
            { keys: ['⌘', 'C'], description: 'Copy' },
            { keys: ['⌘', 'V'], description: 'Paste' },
            { keys: ['⌘', 'X'], description: 'Cut' },
            { keys: ['Del'], description: 'Delete' },
        ],
    },
    {
        category: 'Selection',
        shortcuts: [
            { keys: ['⌘', 'A'], description: 'Select all' },
            { keys: ['⌘', 'D'], description: 'Deselect all' },
            { keys: ['⌘', '⇧', 'D'], description: 'Duplicate' },
        ],
    },
    {
        category: 'Zoom & View',
        shortcuts: [
            { keys: ['⌘', '+'], description: 'Zoom in' },
            { keys: ['⌘', '-'], description: 'Zoom out' },
            { keys: ['⌘', '0'], description: 'Reset zoom' },
        ],
    },
    {
        category: 'Tools',
        shortcuts: [
            { keys: ['V'], description: 'Select tool' },
            { keys: ['P'], description: 'Pen tool' },
            { keys: ['B'], description: 'Pencil tool' },
            { keys: ['F'], description: 'Create frame' },
            { keys: ['N'], description: 'Create sticky note' },
            { keys: ['T'], description: 'Create text' },
        ],
    },
    {
        category: 'Layout',
        shortcuts: [
            { keys: ['⌘', 'G'], description: 'Group' },
            { keys: ['⌘', '⇧', 'G'], description: 'Ungroup' },
            { keys: [']'], description: 'Bring forward' },
            { keys: ['['], description: 'Send backward' },
            { keys: ['⌘', ']'], description: 'Bring to front' },
            { keys: ['⌘', '['], description: 'Send to back' },
        ],
    },
    {
        category: 'Alignment',
        shortcuts: [
            { keys: ['⌘', '⇧', '←'], description: 'Align left' },
            { keys: ['⌘', '⇧', '→'], description: 'Align right' },
            { keys: ['⌘', '⇧', '↑'], description: 'Align top' },
            { keys: ['⌘', '⇧', '↓'], description: 'Align bottom' },
        ],
    },
    {
        category: 'UI Controls',
        shortcuts: [
            { keys: ['⌘', '⇧', 'C'], description: 'Toggle calm mode' },
            { keys: ['⌘', '⇧', 'L'], description: 'Toggle left rail' },
            { keys: ['⌘', '⇧', 'M'], description: 'Toggle minimap' },
            { keys: ['⌘', '⇧', 'P'], description: 'Toggle properties' },
            { keys: ['Esc'], description: 'Cancel / Deselect' },
        ],
    },
    {
        category: 'Export',
        shortcuts: [
            { keys: ['⌘', 'E'], description: 'Export JSON' },
            { keys: ['⌘', '⇧', 'E'], description: 'Export SVG' },
        ],
    },
];

interface KeyboardShortcutLegendProps {
    open: boolean;
    onClose: () => void;
}

export const KeyboardShortcutLegend: React.FC<KeyboardShortcutLegendProps> = ({
    open,
    onClose,
}) => {
    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle className="flex items-center justify-between">
                <Box className="flex items-center gap-2">
                    <Settings />
                    <Typography variant="h6">Keyboard Shortcuts</Typography>
                </Box>
                <IconButton onClick={onClose} size="small">
                    <Close />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Box className="mb-4">
                    <Box className="flex items-center gap-2">
                        <Typography variant="body2" color="text.secondary">
                            Press
                        </Typography>
                        <Chip label="?" size="small" />
                        <Typography variant="body2" color="text.secondary">
                            anywhere to open this panel
                        </Typography>
                    </Box>
                </Box>
                <Grid container spacing={3}>
                    {SHORTCUT_GROUPS.map((group) => (
                        <Grid size={{ xs: 12, sm: 6 }} key={group.category}>
                            <Typography variant="subtitle2" className="font-semibold mb-3">
                                {group.category}
                            </Typography>
                            <Box className="flex flex-col gap-2">
                                {group.shortcuts.map((shortcut, idx) => (
                                    <Box
                                        key={idx}
                                        className="flex justify-between items-center"
                                    >
                                        <Typography variant="body2" color="text.secondary">
                                            {shortcut.description}
                                        </Typography>
                                        <Box className="flex gap-1">
                                            {shortcut.keys.map((key, keyIdx) => (
                                                <Chip
                                                    key={keyIdx}
                                                    label={key}
                                                    size="small"
                                                    className="text-xs h-[24px] bg-gray-100 dark:bg-gray-800 font-mono"
                                                />
                                            ))}
                                        </Box>
                                    </Box>
                                ))}
                            </Box>
                        </Grid>
                    ))}
                </Grid>
            </DialogContent>
        </Dialog>
    );
};
