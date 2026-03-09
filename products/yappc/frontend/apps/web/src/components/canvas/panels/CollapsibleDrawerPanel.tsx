/**
 * Collapsible Drawer Panel Component
 * 
 * Enhanced drawer that supports collapsing to a thin strip with just an icon
 * 
 * @doc.type component
 * @doc.purpose Collapsible drawer with state persistence
 * @doc.layer presentation
 * @doc.pattern Wrapper Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  IconButton,
  Tooltip,
  Typography,
} from '@ghatana/ui';
import { Drawer } from '@ghatana/ui';
import { ChevronLeft as CollapseIcon, ChevronRight as ExpandIcon } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface CollapsibleDrawerPanelProps {
    /** Panel ID for localStorage persistence */
    panelId: string;

    /** Panel title */
    title: string;

    /** Panel icon */
    icon: React.ReactNode;

    /** Whether drawer is open */
    open: boolean;

    /** Close handler */
    onClose: () => void;

    /** Panel content */
    children: React.ReactNode;

    /** Width when expanded (in pixels) */
    width?: number;

    /** Collapsed width (in pixels) */
    collapsedWidth?: number;

    /** Keyboard shortcut hint */
    keyboardShortcut?: string;

    /** Badge count */
    badgeCount?: number;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get stored collapse state from localStorage
 */
const getStoredCollapseState = (panelId: string): boolean | null => {
    if (typeof window === 'undefined') return null;
    const stored = localStorage.getItem(`yappc:drawer-collapsed:${panelId}`);
    return stored ? JSON.parse(stored) : null;
};

/**
 * Store collapse state in localStorage
 */
const setStoredCollapseState = (panelId: string, collapsed: boolean): void => {
    if (typeof window === 'undefined') return;
    localStorage.setItem(`yappc:drawer-collapsed:${panelId}`, JSON.stringify(collapsed));
};

// ============================================================================
// Component
// ============================================================================

/**
 * Collapsible Drawer Panel Component
 * 
 * A right-side drawer that can collapse to a minimal width while keeping the drawer open.
 * Useful for panels that users want to keep visible but minimize to save space.
 */
export const CollapsibleDrawerPanel: React.FC<CollapsibleDrawerPanelProps> = ({
    panelId,
    title,
    icon,
    open,
    onClose,
    children,
    width = 450,
    collapsedWidth = 48,
    keyboardShortcut,
    badgeCount,
}) => {
    // Initialize collapsed state from localStorage
    const [isCollapsed, setIsCollapsed] = useState(() => {
        const stored = getStoredCollapseState(panelId);
        return stored !== null ? stored : false;
    });

    // Persist state changes to localStorage
    useEffect(() => {
        setStoredCollapseState(panelId, isCollapsed);
    }, [isCollapsed, panelId]);

    // Toggle collapse state
    const handleToggle = useCallback(() => {
        setIsCollapsed((prev) => !prev);
    }, []);

    // Keyboard shortcut handler
    useEffect(() => {
        if (!keyboardShortcut || !open) return;

        const handleKeyDown = (e: KeyboardEvent) => {
            const parts = keyboardShortcut.toLowerCase().split('+');
            const key = parts[parts.length - 1];
            const needsCmd = parts.includes('cmd') || parts.includes('meta');
            const needsCtrl = parts.includes('ctrl');
            const needsShift = parts.includes('shift');
            const needsAlt = parts.includes('alt');

            const matches =
                e.key.toLowerCase() === key &&
                e.metaKey === needsCmd &&
                e.ctrlKey === needsCtrl &&
                e.shiftKey === needsShift &&
                e.altKey === needsAlt;

            if (matches) {
                e.preventDefault();
                handleToggle();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [keyboardShortcut, open, handleToggle]);

    const drawerWidth = isCollapsed ? collapsedWidth : width;

    return (
        <Drawer
            anchor="right"
            open={open}
            onClose={onClose}
            variant="persistent"
            className="shrink-0 transition-all duration-300" style={{ width: drawerWidth }}
        >
            <Box
                className="w-full h-full flex flex-col overflow-hidden"
            >
                {/* Header */}
                <Box
                    className="flex items-center border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 min-h-[56px] border-b" style={{ justifyContent: isCollapsed ? 'center' : 'space-between', padding: isCollapsed ? 8 : 16 }} >
                    {!isCollapsed && (
                        <Box className="flex items-center gap-2">
                            {icon}
                            <Typography variant="h6" className="text-base font-semibold">
                                {title}
                            </Typography>
                            {badgeCount !== undefined && badgeCount > 0 && (
                                <Box
                                    className="px-2 py-0.5 text-xs font-semibold bg-blue-600 text-white rounded-[12px]"
                                >
                                    {badgeCount}
                                </Box>
                            )}
                        </Box>
                    )}

                    <Tooltip
                        title={`${isCollapsed ? 'Expand' : 'Collapse'} ${title}${keyboardShortcut ? ` (${keyboardShortcut})` : ''}`}
                        placement="left"
                    >
                        <IconButton
                            size="small"
                            onClick={handleToggle}
                            aria-label={isCollapsed ? `Expand ${title}` : `Collapse ${title}`}
                            className="hover:bg-gray-100 hover:dark:bg-gray-800"
                        >
                            {isCollapsed ? <ExpandIcon size={16} /> : <CollapseIcon size={16} />}
                        </IconButton>
                    </Tooltip>
                </Box>

                {/* Content */}
                {!isCollapsed && (
                    <Box
                        className="flex-1 overflow-auto flex flex-col"
                    >
                        {children}
                    </Box>
                )}

                {/* Collapsed State - Show icon vertically */}
                {isCollapsed && (
                    <Box
                        className="flex-1 flex flex-col items-center py-6 gap-4"
                    >
                        <Box className="text-blue-600">{icon}</Box>

                        {badgeCount !== undefined && badgeCount > 0 && (
                            <Box
                                className="rounded-full flex items-center justify-center text-xs font-semibold bg-blue-600 text-white w-[24px] h-[24px]"
                            >
                                {badgeCount}
                            </Box>
                        )}

                        <Typography
                            variant="caption"
                            className="font-semibold text-xs text-gray-500 dark:text-gray-400 tracking-[0.1em] rotate-[180deg]" style={{ writingMode: 'vertical-rl' }} >
                            {title.toUpperCase()}
                        </Typography>
                    </Box>
                )}
            </Box>
        </Drawer>
    );
};
