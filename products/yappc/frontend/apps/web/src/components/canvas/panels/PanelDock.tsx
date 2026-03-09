/**
 * Panel Dock
 * 
 * Bottom toolbar dock for workspace panels. Uses @ghatana/ui exclusively.
 * 
 * @doc.type component
 * @doc.purpose Workspace panel dock control
 * @doc.layer product
 * @doc.pattern Toolbar
 */

import React from 'react';
import { Box, Tooltip, IconButton } from '@ghatana/ui';

interface PanelDockProps {
    panels: Array<{
        id: string;
        icon: React.ReactNode;
        title: string;
        isOpen: boolean;
        description?: string;
    }>;
    onToggle: (id: string) => void;
}

export const PanelDock: React.FC<PanelDockProps> = ({ panels, onToggle }) => {
    return (
        <Box
            className="absolute bottom-6 left-1/2 -translate-x-1/2 z-[1300] flex gap-1 bg-white dark:bg-gray-900 rounded-xl shadow-lg px-2 py-1.5 border border-gray-200 dark:border-gray-700"
            role="toolbar"
            aria-label="Panel controls"
        >
            {panels.map((panel) => (
                <Tooltip
                    key={panel.id}
                    title={panel.description || panel.title}
                >
                    <Box className="relative">
                        <IconButton
                            onClick={() => onToggle(panel.id)}
                            aria-label={`Toggle ${panel.title} panel`}
                            aria-pressed={panel.isOpen}
                            className={`w-[44px] h-[44px] rounded-lg transition-all duration-200 ${
                                panel.isOpen
                                    ? 'bg-indigo-50 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400'
                                    : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800'
                            }`}
                        >
                            {panel.icon}
                        </IconButton>
                        {/* Active Indicator Dot */}
                        {panel.isOpen && (
                            <Box
                                className="absolute rounded-full bottom-[-2px] left-1/2 -translate-x-1/2 w-1 h-1 bg-indigo-500" />
                        )}
                    </Box>
                </Tooltip>
            ))}
        </Box>
    );
};
