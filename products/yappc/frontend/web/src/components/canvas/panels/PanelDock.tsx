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
import { Box, Tooltip, IconButton } from '@ghatana/design-system';
import { useI18n } from '../../../i18n/I18nProvider';

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
    const { t } = useI18n();
    return (
        <Box
            className="absolute bottom-6 left-1/2 -translate-x-1/2 z-[1300] flex gap-1 bg-white dark:bg-surface rounded-xl shadow-lg px-2 py-1.5 border border-border dark:border-border"
            role="toolbar"
            aria-label={t('canvas.panelDock.controls')}
        >
            {panels.map((panel) => (
                <Tooltip
                    key={panel.id}
                    title={panel.description || panel.title}
                >
                    <Box className="relative">
                        <IconButton
                            onClick={() => onToggle(panel.id)}
                            aria-label={t('canvas.panelDock.togglePanel', { title: panel.title })}
                            aria-pressed={panel.isOpen}
                            className={`w-[44px] h-[44px] rounded-lg transition-all duration-200 ${
                                panel.isOpen
                                    ? 'bg-info-bg dark:bg-info-bg/20 text-info-color dark:text-info-color'
                                    : 'text-fg-muted hover:bg-surface-muted dark:hover:bg-surface'
                            }`}
                        >
                            {panel.icon}
                        </IconButton>
                        {/* Active Indicator Dot */}
                        {panel.isOpen && (
                            <Box
                                className="absolute rounded-full bottom-[-2px] left-1/2 -translate-x-1/2 w-1 h-1 bg-info-bg" />
                        )}
                    </Box>
                </Tooltip>
            ))}
        </Box>
    );
};
