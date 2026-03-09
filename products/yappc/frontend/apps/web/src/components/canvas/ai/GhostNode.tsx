/**
 * Ghost Node Component
 * 
 * Renders semi-transparent "ghost" nodes for AI suggestions
 * 
 * @doc.type component
 * @doc.purpose Visualize AI-suggested nodes on canvas
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Box,
  Button,
  Stack,
  Typography,
} from '@ghatana/ui';
import { CheckCircle, X as Close } from 'lucide-react';
import type { GhostNode as GhostNodeType } from '@/hooks/useAIAssistant';
import { Z_INDEX } from '../../../styles/design-tokens';

export interface GhostNodeProps {
    node: GhostNodeType;
    onAccept: (nodeId: string) => void;
    onDismiss: (nodeId: string) => void;
}

/**
 * Ghost Node Component
 * Renders a semi-transparent node with accept/dismiss actions
 */
export const GhostNode: React.FC<GhostNodeProps> = ({
    node,
    onAccept,
    onDismiss,
}) => {
    const [isHovered, setIsHovered] = React.useState(false);

    // Get color based on node type
    const getTypeColor = (type: string) => {
        switch (type) {
            case 'component':
                return '#1976d2';
            case 'api':
                return '#9c27b0';
            case 'data':
                return '#4caf50';
            case 'flow':
                return '#ff9800';
            case 'page':
                return '#f57c00';
            default:
                return '#666';
        }
    };

    const color = getTypeColor(node.type);

    return (
        <Box
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            className="absolute w-[180px] min-h-[80px] bg-[rgba(255,_255,_255,_0.7)] backdrop-blur-[8px]" style={{ left: node.position.x, top: node.position.y, border: `2px dashed ${color, co: 'blur(8px)' */
            role="button"
            aria-label={`AI Suggestion: ${node.data.label || node.type}`}
            tabIndex={0}
            onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    onAccept(node.id);
                } else if (e.key === 'Escape') {
                    onDismiss(node.id);
                }
            }}
        >
            {/* Node Content */}
            <Stack spacing={1}>
                <Box>
                    <Typography
                        variant="caption"
                        className="font-bold uppercase" >
                        AI Suggestion
                    </Typography>
                    <Typography variant="subtitle2" fontWeight="bold">
                        {node.data.label || node.type}
                    </Typography>
                    {node.data.suggestion?.description && (
                        <Typography variant="caption" color="text.secondary">
                            {node.data.suggestion.description}
                        </Typography>
                    )}
                </Box>

                {/* Actions (show on hover) */}
                {isHovered && (
                    <Stack direction="row" spacing={0.5} className="mt-2">
                        <Button
                            size="small"
                            variant="outlined"
                            onClick={() => onDismiss(node.id)}
                            startIcon={<Close />}
                            className="flex-1"
                        >
                            Dismiss
                        </Button>
                        <Button
                            size="small"
                            variant="contained"
                            onClick={() => onAccept(node.id)}
                            startIcon={<CheckCircle />}
                            className="flex-1"
                        >
                            Accept
                        </Button>
                    </Stack>
                )}
            </Stack>

            {/* Pulsing indicator */}
            <Box
                className="absolute top-[-8px] right-[-8px] w-[16px] h-[16px] rounded-full" style={{ backgroundColor: color }}
            />
        </Box>
    );
};

/**
 * Ghost Node Layer
 * Renders all ghost nodes as an overlay on the canvas
 */
export interface GhostNodeLayerProps {
    nodes: GhostNodeType[];
    onAccept: (nodeId: string) => void;
    onDismiss: (nodeId: string) => void;
}

export const GhostNodeLayer: React.FC<GhostNodeLayerProps> = ({
    nodes,
    onAccept,
    onDismiss,
}) => {
    if (nodes.length === 0) return null;

    return (
        <Box
            aria-live="polite"
            aria-atomic="false"
            className="absolute top-[0px] left-[0px] right-[0px] bottom-[0px] pointer-events-none" style={{ zIndex: Z_INDEX.canvas + 1 }}
        >
            {nodes.map((node) => (
                <GhostNode
                    key={node.id}
                    node={node}
                    onAccept={onAccept}
                    onDismiss={onDismiss}
                />
            ))}
        </Box>
    );
};
