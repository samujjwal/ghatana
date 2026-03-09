/**
 * Artifact Node Content
 * 
 * Renders artifact content within a unified canvas node.
 * Maintains compatibility with existing artifact functionality.
 * 
 * @doc.type component
 * @doc.purpose Artifact content for unified canvas nodes
 * @doc.layer product
 * @doc.pattern Content Renderer
 */

import React from 'react';
import { Box, Typography, Chip } from '@ghatana/ui';
import { ArtifactNodeData } from '../../nodes/ArtifactNode';

interface ArtifactNodeContentProps {
    data?: ArtifactNodeData;
    onChange?: (newData: unknown) => void;
    readonly?: boolean;
}

export const ArtifactNodeContent: React.FC<ArtifactNodeContentProps> = ({
    data,
    readonly = false
}) => {
    if (!data) {
        return (
            <Box className="p-4">
                <Typography as="p" className="text-sm" color="text.secondary">
                    No artifact data
                </Typography>
            </Box>
        );
    }

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'draft': return 'default';
            case 'review': return 'warning';
            case 'approved': return 'success';
            default: return 'default';
        }
    };

    return (
        <Box className="p-4">
            <Box className="flex items-center gap-2 mb-2">
                <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                    {data.title}
                </Typography>
                <Chip
                    size="sm"
                    label={data.status}
                    color={getStatusColor(data.status) as unknown}
                />
            </Box>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                Phase: {data.phase}
            </Typography>

            {data.description && (
                <Typography as="p" className="text-sm" className="mb-2">
                    {data.description}
                </Typography>
            )}

            {/* Additional artifact content can be rendered here */}
            <Box className="mt-2">
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    Type: {data.type}
                </Typography>
            </Box>
        </Box>
    );
};
