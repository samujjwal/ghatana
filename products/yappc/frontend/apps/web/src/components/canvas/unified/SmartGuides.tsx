/**
 * SmartGuides - Visual Alignment Guides
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import type { Guide } from '../../../lib/canvas/AlignmentEngine';

interface SmartGuidesProps {
    guides: Guide[];
    visible: boolean;
}

export function SmartGuides({ guides, visible }: SmartGuidesProps) {
    if (!visible || guides.length === 0) return null;

    return (
        <Box className="absolute pointer-events-none inset-0" >
            {guides.map(guide => (
                <Box
                    key={guide.id}
                    className="absolute"
                />
            ))}
        </Box>
    );
}
