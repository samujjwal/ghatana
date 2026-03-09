/**
 * Collapsible Panel Component
 * 
 * Reusable wrapper for panels with collapse/expand functionality and state persistence
 * 
 * @doc.type component
 * @doc.purpose Collapsible panel wrapper with state persistence
 * @doc.layer presentation
 * @doc.pattern Wrapper Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  IconButton,
  Tooltip,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import { ChevronLeft as CollapseLeftIcon, ChevronRight as ExpandLeftIcon, X as CloseIcon } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface CollapsiblePanelProps {
    /** Panel ID for localStorage persistence */
    panelId: string;

    /** Panel title */
    title: string;

    /** Panel position */
    position: 'left' | 'right';

    /** Panel content */
    children: React.ReactNode;

    /** Width when expanded (in pixels) */
    width?: number;

    /** Collapsed width (in pixels) */
    collapsedWidth?: number;

    /** Default collapsed state */
    defaultCollapsed?: boolean;

    /** Show close button */
    showClose?: boolean;

    /** Close handler */
    onClose?: () => void;

    /** Collapse state change handler */
    onCollapseChange?: (collapsed: boolean) => void;

    /** Additional styles */
    sx?: unknown;

    /** Keyboard shortcut hint */
    keyboardShortcut?: string;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get stored collapse state from localStorage
 */
const getStoredCollapseState = (panelId: string): boolean | null => {
    if (typeof window === 'undefined') return null;
    const stored = localStorage.getItem(`yappc:panel-collapsed:${panelId}`);
    return stored ? JSON.parse(stored) : null;
};

/**
 * Store collapse state in localStorage
 */
const setStoredCollapseState = (panelId: string, collapsed: boolean): void => {
    if (typeof window === 'undefined') return;
    localStorage.setItem(`yappc:panel-collapsed:${panelId}`, JSON.stringify(collapsed));
};

/**
 * Reset all stored panel states
 */
export const resetPanelStates = (): void => {
    if (typeof window === 'undefined') return;
    const keys = Object.keys(localStorage);
    keys.forEach((key) => {
        if (key.startsWith('yappc:panel-collapsed:')) {
            localStorage.removeItem(key);
        }
    });
};

// ============================================================================
// Component
// ============================================================================

/**
 * Collapsible Panel Component
 * 
 * Provides a panel that can be collapsed/expanded with state persistence.
 * Ideal for side panels that users want to show/hide to reclaim canvas space.
 */
export const CollapsiblePanel: React.FC<CollapsiblePanelProps> = ({
    panelId,
    title,
    position,
    children,
    width = 280,
    collapsedWidth = 48,
    defaultCollapsed = false,
    showClose = false,
    onClose,
    onCollapseChange,
    sx = {},
    keyboardShortcut,
}) => {
    // Initialize collapsed state from localStorage or default
    const [isCollapsed, setIsCollapsed] = useState(() => {
        const stored = getStoredCollapseState(panelId);
        return stored !== null ? stored : defaultCollapsed;
    });

    // Persist state changes to localStorage
    useEffect(() => {
        setStoredCollapseState(panelId, isCollapsed);
        onCollapseChange?.(isCollapsed);
    }, [isCollapsed, panelId, onCollapseChange]);

    // Toggle collapse state
    const handleToggle = useCallback(() => {
        setIsCollapsed((prev) => !prev);
    }, []);

    // Keyboard shortcut handler
    useEffect(() => {
        if (!keyboardShortcut) return;

        const handleKeyDown = (e: KeyboardEvent) => {
            // Parse shortcut (e.g., "Cmd+B" or "Ctrl+B")
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
    }, [keyboardShortcut, handleToggle]);

    // Icon and tooltip based on position and state
    const CollapseIcon = position === 'left' ? CollapseLeftIcon : CloseIcon;
    const ExpandIcon = position === 'left' ? ExpandLeftIcon : CollapseLeftIcon;
    const icon = isCollapsed ? ExpandIcon : CollapseIcon;
    const tooltipText = isCollapsed ? `Expand ${title}` : `Collapse ${title}`;

    return (
        <Paper
            elevation={2}
            className="h-full flex flex-col overflow-hidden transition-all duration-300 rounded-none border-gray-200 dark:border-gray-700 relative" style={{ width: isCollapsed ? collapsedWidth : width, borderRight: position === 'left' ? 1 : 0, borderLeft: position === 'right' ? 1 : 0 }}
        >
            {/* Header */}
            <Box
                className="flex items-center justify-between p-3 border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 min-h-[48px] border-b" >
                {!isCollapsed && (
                    <Typography variant="subtitle2" className="font-semibold text-sm">
                        {title}
                    </Typography>
                )}

                <Box className="flex gap-1 ml-auto">
                    {/* Collapse/Expand button */}
                    <Tooltip
                        title={`${tooltipText}${keyboardShortcut ? ` (${keyboardShortcut})` : ''}`}
                        placement={position === 'left' ? 'right' : 'left'}
                    >
                        <IconButton
                            size="small"
                            onClick={handleToggle}
                            aria-label={tooltipText}
                            className="hover:bg-gray-100 hover:dark:bg-gray-800"
                        >
                            {React.createElement(icon, { fontSize: 'small' })}
                        </IconButton>
                    </Tooltip>

                    {/* Close button */}
                    {showClose && onClose && !isCollapsed && (
                        <Tooltip title="Close" placement={position === 'left' ? 'right' : 'left'}>
                            <IconButton
                                size="small"
                                onClick={onClose}
                                aria-label="Close panel"
                                className="hover:bg-gray-100 hover:dark:bg-gray-800"
                            >
                                <CloseIcon size={16} />
                            </IconButton>
                        </Tooltip>
                    )}
                </Box>
            </Box>

            {/* Content */}
            <Box
                className="flex-1 overflow-hidden flex-col" style={{ display: isCollapsed ? 'none' : 'flex', writingMode: 'vertical-rl' }}
            >
                {children}
            </Box>

            {/* Collapsed State - Show icon vertically */}
            {isCollapsed && (
                <Box
                    className="flex-1 flex flex-col items-center py-4 gap-4"
                >
                    <Typography
                        variant="caption"
                        className="font-semibold text-xs text-gray-500 dark:text-gray-400 tracking-[0.1em] rotate-[180deg]" >
                        {title.toUpperCase()}
                    </Typography>
                </Box>
            )}
        </Paper>
    );
};
