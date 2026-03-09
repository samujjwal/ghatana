/**
 * KeyboardShortcutsOverlay Component
 * 
 * Interactive keyboard shortcuts help overlay (⌘/)
 * 
 * Features:
 * - Categorized shortcuts (Tools, Navigation, Editing, View, Panels)
 * - Search filter
 * - Accessible with ⌘/ or ?
 * - Visual key representations
 * - Printable format
 * 
 * @doc.type component
 * @doc.purpose Keyboard shortcuts reference
 * @doc.layer components
 */

import { Search as SearchIcon, X as CloseIcon, Printer as PrintIcon } from 'lucide-react';
import {
  Dialog,
  Box,
  Typography,
  IconButton,
  InputAdornment,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import React, { useState, useMemo } from 'react';

import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS, SHADOWS } = CANVAS_TOKENS;

export interface KeyboardShortcutsOverlayProps {
    /** Whether overlay is open */
    open: boolean;

    /** Callback when closed */
    onClose: () => void;
}

interface ShortcutItem {
    action: string;
    keys: string | string[];
    category: 'Tools' | 'Navigation' | 'Editing' | 'Grouping' | 'View' | 'Panels';
    description?: string;
}

// Define all shortcuts in categorized structure
const ALL_SHORTCUTS: ShortcutItem[] = [
    // Tools
    { action: 'Select Tool', keys: 'V', category: 'Tools' },
    { action: 'Frame Tool', keys: 'F', category: 'Tools' },
    { action: 'Shape Tool', keys: 'R', category: 'Tools' },
    { action: 'Connector Tool', keys: 'C', category: 'Tools' },
    { action: 'Comment Tool', keys: 'M', category: 'Tools' },
    { action: 'Text Tool', keys: 'T', category: 'Tools' },

    // Navigation
    { action: 'Command Palette', keys: '⌘K', category: 'Navigation' },
    { action: 'Zoom In', keys: '⌘+', category: 'Navigation' },
    { action: 'Zoom Out', keys: '⌘−', category: 'Navigation' },
    { action: 'Reset Zoom', keys: '⌘0', category: 'Navigation' },
    { action: 'Fit to View', keys: '⌘1', category: 'Navigation' },
    { action: 'Fit Selection', keys: '⌘2', category: 'Navigation' },
    { action: 'Overview Mode', keys: '⌘⇧O', category: 'Navigation' },
    { action: 'Navigate Up', keys: 'ESC', category: 'Navigation', description: 'Exit drill-down mode' },
    { action: 'Previous Sibling', keys: '⌘[', category: 'Navigation' },
    { action: 'Next Sibling', keys: '⌘]', category: 'Navigation' },

    // Editing
    { action: 'Undo', keys: '⌘Z', category: 'Editing' },
    { action: 'Redo', keys: ['⌘⇧Z', '⌘Y'], category: 'Editing' },
    { action: 'Copy', keys: '⌘C', category: 'Editing' },
    { action: 'Paste', keys: '⌘V', category: 'Editing' },
    { action: 'Cut', keys: '⌘X', category: 'Editing' },
    { action: 'Duplicate', keys: '⌘D', category: 'Editing' },
    { action: 'Delete', keys: ['Delete', 'Backspace'], category: 'Editing' },
    { action: 'Select All', keys: '⌘A', category: 'Editing' },

    // Grouping & Alignment
    { action: 'Group', keys: '⌘G', category: 'Grouping' },
    { action: 'Ungroup', keys: '⌘⇧G', category: 'Grouping' },
    { action: 'Bring Forward', keys: '⌘]', category: 'Grouping' },
    { action: 'Send Backward', keys: '⌘[', category: 'Grouping' },
    { action: 'Bring to Front', keys: '⌘⇧]', category: 'Grouping' },
    { action: 'Send to Back', keys: '⌘⇧[', category: 'Grouping' },

    // View
    { action: 'Focus Mode', keys: '⌘⇧F', category: 'View' },
    { action: 'Toggle Header', keys: '⌘⇧H', category: 'View' },
    { action: 'Toggle Grid', keys: '⌘\'', category: 'View' },
    { action: 'Toggle Rulers', keys: '⌘R', category: 'View' },
    { action: 'Distraction Free', keys: '⌘⇧D', category: 'View' },

    // Panels
    { action: 'Toggle Left Rail', keys: '⌘⇧L', category: 'Panels' },
    { action: 'Inspector Panel', keys: '⌘⇧I', category: 'Panels' },
    { action: 'Outline Panel', keys: 'O', category: 'Panels' },
    { action: 'Layers Panel', keys: 'L', category: 'Panels' },
    { action: 'Palette Panel', keys: 'P', category: 'Panels' },
    { action: 'Show Templates', keys: '⌘⇧A', category: 'Panels' },
];

/**
 * Format key for display (replace mod with ⌘/Ctrl)
 */
function formatKey(key: string): string {
    const isMac = typeof navigator !== 'undefined' && /Mac/.test(navigator.platform);
    return key
        .replace('mod', isMac ? '⌘' : 'Ctrl')
        .replace('shift', '⇧')
        .replace('alt', '⌥')
        .replace('ctrl', isMac ? '⌃' : 'Ctrl');
}

/**
 * Render keyboard key
 */
function KeyboardKey({ children }: { children: React.ReactNode }) {
    return (
        <Box
            component="kbd"
            className="inline-flex items-center justify-center min-w-[24px] h-[24px] font-mono" style={{ paddingLeft: SPACING.XS, paddingRight: SPACING.XS, fontSize: TYPOGRAPHY.XS, fontWeight: FONT_WEIGHT.SEMIBOLD, backgroundColor: COLORS.NEUTRAL_100, border: `1px solid ${COLORS.BORDER_LIGHT}` }}
        >
            {children}
        </Box>
    );
}

/**
 * Render multiple keys (for combinations or alternatives)
 */
function KeyCombination({ keys }: { keys: string | string[] }) {
    const keyArray = Array.isArray(keys) ? keys : [keys];

    return (
        <Box className="flex flex-wrap gap-1" >
            {keyArray.map((keyCombo, index) => (
                <Box key={index} className="flex items-center gap-0.5" >
                    {keyCombo.split(/(\+|⌘|⇧|⌥|⌃)/).filter(Boolean).map((key, i) => (
                        key === '+' ? (
                            <Box key={i} style={{ marginLeft: SPACING.XXS, marginRight: SPACING.XXS, color: COLORS.TEXT_DISABLED }}>
                                +
                            </Box>
                        ) : (
                            <KeyboardKey key={i}>{formatKey(key)}</KeyboardKey>
                        )
                    ))}
                    {index < keyArray.length - 1 && (
                        <Box style={{ marginLeft: SPACING.XS, marginRight: SPACING.XS, color: COLORS.TEXT_SECONDARY, fontSize: TYPOGRAPHY.XS }}>
                            or
                        </Box>
                    )}
                </Box>
            ))}
        </Box>
    );
}

/**
 * Keyboard Shortcuts Overlay
 * 
 * Comprehensive keyboard shortcuts reference with search and categories
 */
export function KeyboardShortcutsOverlay({
    open,
    onClose,
}: KeyboardShortcutsOverlayProps) {
    const [searchQuery, setSearchQuery] = useState('');

    // Filter shortcuts by search query
    const filteredShortcuts = useMemo(() => {
        if (!searchQuery) return ALL_SHORTCUTS;

        const query = searchQuery.toLowerCase();
        return ALL_SHORTCUTS.filter(shortcut =>
            shortcut.action.toLowerCase().includes(query) ||
            (shortcut.description && shortcut.description.toLowerCase().includes(query)) ||
            (typeof shortcut.keys === 'string' ? shortcut.keys.toLowerCase().includes(query) : shortcut.keys.some(k => k.toLowerCase().includes(query)))
        );
    }, [searchQuery]);

    // Group shortcuts by category
    const shortcutsByCategory = useMemo(() => {
        const groups: Record<string, ShortcutItem[]> = {};

        filteredShortcuts.forEach(shortcut => {
            if (!groups[shortcut.category]) {
                groups[shortcut.category] = [];
            }
            groups[shortcut.category].push(shortcut);
        });

        return groups;
    }, [filteredShortcuts]);

    const categories = Object.keys(shortcutsByCategory).sort();

    const handlePrint = () => {
        window.print();
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            maxWidth="lg"
            fullWidth
            PaperProps={{
                style: {
                    borderRadius: RADIUS.LG,
                    boxShadow: SHADOWS.XL,
                    maxHeight: '90vh',
                },
            }}
        >
            {/* Header */}
            <Box
                className="flex items-center justify-between" style={{ padding: SPACING.LG, borderBottom: `1px solid ${COLORS.BORDER_LIGHT}` }}
            >
                <Box>
                    <Typography variant="h5" style={{ fontWeight: FONT_WEIGHT.BOLD, marginBottom: SPACING.XXS }}>
                        ⌨️ Keyboard Shortcuts
                    </Typography>
                    <Typography variant="body2" style={{ color: COLORS.TEXT_SECONDARY }}>
                        Master the canvas with keyboard-first navigation
                    </Typography>
                </Box>

                <Box className="flex gap-2" >
                    <IconButton onClick={handlePrint} aria-label="Print shortcuts">
                        <PrintIcon />
                    </IconButton>
                    <IconButton onClick={onClose} aria-label="Close">
                        <CloseIcon />
                    </IconButton>
                </Box>
            </Box>

            {/* Search */}
            <Box style={{ padding: SPACING.LG, paddingBottom: SPACING.MD }}>
                <TextField
                    fullWidth
                    placeholder="Search shortcuts..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon style={{ color: COLORS.TEXT_SECONDARY }} />
                            </InputAdornment>
                        ),
                    }}
                />
            </Box>

            {/* Shortcuts Grid */}
            <Box
                className="overflow-y-auto max-h-[calc(90vh - 180px)] px-6 pb-6" >
                {categories.length === 0 ? (
                    <Box
                        className="text-center py-12" >
                        <Typography variant="body1">No shortcuts found</Typography>
                        <Typography variant="body2" style={{ marginTop: SPACING.SM, fontSize: TYPOGRAPHY.SM }}>
                            Try a different search term
                        </Typography>
                    </Box>
                ) : (
                    <Box
                        className="grid grid-cols-1 md:grid-cols-2" style={{ gap: SPACING.LG, color: COLORS.TEXT_SECONDARY }}
                    >
                        {categories.map((category) => (
                            <Box key={category}>
                                <Box style={{ marginBottom: SPACING.MD }}>
                                    <Typography
                                        variant="h6"
                                        style={{
                                            fontWeight: FONT_WEIGHT.SEMIBOLD,
                                            marginBottom: SPACING.MD,
                                            color: COLORS.TEXT_PRIMARY,
                                            fontSize: TYPOGRAPHY.LG,
                                        }}
                                    >
                                        {category}
                                    </Typography>

                                    <Box className="flex flex-col gap-2" >
                                        {shortcutsByCategory[category].map((shortcut, index) => (
                                            <Box
                                                key={index}
                                                className="flex items-center justify-between" style={{ gap: SPACING.MD, padding: SPACING.SM, backgroundColor: COLORS.NEUTRAL_50, borderRadius: RADIUS.SM, border: `1px solid ${COLORS.BORDER_LIGHT}` }}
                                            >
                                                <Box className="flex-1">
                                                    <Typography
                                                        style={{
                                                            fontSize: TYPOGRAPHY.SM,
                                                            fontWeight: FONT_WEIGHT.MEDIUM,
                                                            color: COLORS.TEXT_PRIMARY,
                                                        }}
                                                    >
                                                        {shortcut.action}
                                                    </Typography>
                                                    {shortcut.description && (
                                                        <Typography
                                                            style={{
                                                                fontSize: TYPOGRAPHY.XS,
                                                                color: COLORS.TEXT_SECONDARY,
                                                                marginTop: SPACING.XXS,
                                                            }}
                                                        >
                                                            {shortcut.description}
                                                        </Typography>
                                                    )}
                                                </Box>

                                                <KeyCombination keys={shortcut.keys} />
                                            </Box>
                                        ))}
                                    </Box>
                                </Box>
                            </Box>
                        ))}
                    </Box>
                )}
            </Box>

            {/* Footer */}
            <Box
                style={{
                    padding: SPACING.MD,
                    borderTop: `1px solid ${COLORS.BORDER_LIGHT}`,
                    backgroundColor: COLORS.NEUTRAL_50,
                    textAlign: 'center',
                }}
            >
                <Typography variant="body2" style={{ color: COLORS.TEXT_SECONDARY, fontSize: TYPOGRAPHY.XS }}>
                    Press <KeyboardKey>⌘/</KeyboardKey> or <KeyboardKey>?</KeyboardKey> anytime to show this overlay
                </Typography>
            </Box>
        </Dialog>
    );
}

/**
 * Hook to manage keyboard shortcuts overlay
 */
export function useKeyboardShortcutsOverlay() {
    const [open, setOpen] = React.useState(false);

    React.useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // ⌘/ or Ctrl/ or just ?
            if ((e.metaKey || e.ctrlKey) && e.key === '/' || e.key === '?') {
                e.preventDefault();
                setOpen(true);
            }

            // Escape to close
            if (e.key === 'Escape' && open) {
                setOpen(false);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [open]);

    return {
        open,
        setOpen,
        showShortcuts: () => setOpen(true),
        hideShortcuts: () => setOpen(false),
    };
}
