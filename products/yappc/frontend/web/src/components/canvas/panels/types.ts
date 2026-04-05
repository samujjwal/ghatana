import React from 'react';

export interface WorkspacePanelConfig {
    id: string;
    title: string;
    icon: React.ReactNode;
    content: React.ReactNode;
    defaultPosition?: { x: number; y: number };
    defaultWidth?: number;
    defaultOpen?: boolean;
    description?: string;
}
