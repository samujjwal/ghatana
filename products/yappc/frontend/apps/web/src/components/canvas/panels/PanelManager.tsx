import React, { useState, useEffect } from 'react';
import { WorkspacePanelConfig } from './types';
import { PanelDock } from './PanelDock';
import { DraggablePanel } from '@ghatana/ui';
import { Box } from '@ghatana/ui';

interface PanelManagerProps {
    panels: WorkspacePanelConfig[];
}

export const PanelManager: React.FC<PanelManagerProps> = ({ panels }) => {
    // Determine initial state based on defaultOpen config or local storage preference could go here
    const [openPanels, setOpenPanels] = useState<Set<string>>(() => {
        const initial = new Set<string>();
        panels.forEach(p => {
            if (p.defaultOpen) initial.add(p.id);
        });
        return initial;
    });

    const handleToggle = (id: string) => {
        setOpenPanels(prev => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    };

    const handleClose = (id: string) => {
        setOpenPanels(prev => {
            const next = new Set(prev);
            next.delete(id);
            return next;
        });
    };

    return (
        <>
            {/* Render Active Panels */}
            {panels.map(panel => {
                if (!openPanels.has(panel.id)) return null;

                return (
                    <DraggablePanel
                        key={panel.id}
                        id={panel.id}
                        title={panel.title}
                        onClose={() => handleClose(panel.id)}
                        defaultPosition={panel.defaultPosition}
                        width={panel.defaultWidth}
                    >
                        {panel.content}
                    </DraggablePanel>
                );
            })}

            {/* Dock for control */}
            <PanelDock
                panels={panels.map(p => ({
                    id: p.id,
                    title: p.title,
                    icon: p.icon,
                    isOpen: openPanels.has(p.id),
                    description: p.description
                }))}
                onToggle={handleToggle}
            />
        </>
    );
};
