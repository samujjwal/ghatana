/**
 * Artifact Node Component
 *
 * Canvas node representing a lifecycle artifact with persona badges and status.
 * Used in Canvas-First UX to show requirements, designs, code, tests, etc.
 *
 * @doc.type component
 * @doc.purpose Lifecycle artifact visualization
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useState } from 'react';
import { Handle, Position, NodeResizer, type NodeProps } from '@xyflow/react';
import { Box, Card, CardContent, Typography, Chip, IconButton, Tooltip, Badge, Menu, MenuItem, ListItemIcon, ListItemText, Button } from '@ghatana/ui';
import { MoreVertical as MoreVert, Link as LinkIcon, Check as CheckIcon, X as CloseIcon, Code as CodeIcon, Bug as TestIcon, FileText as DocIcon, Drama as MockIcon, ArrowLeftRight as SwapIcon } from 'lucide-react';
import { useAtomValue } from 'jotai';
import { PersonaBadge, StatusBadge } from '../workspace/PersonaBadge';
import { ArtifactType } from '@/types/fow-stages';
import { LifecyclePhase } from '@/types/lifecycle';
import { codeAssociationsAtom, canvasInteractionModeAtom, cameraZoomAtom } from '../workspace';
import { CodePreviewPopover } from './CodePreviewPopover';
import { TypeSelectorModal } from '../TypeSelectorModal';
import type { CodeRelationshipType } from '../../../hooks/useCodeAssociations';

// Artifact type icons
const ARTIFACT_ICONS: Record<string, string> = {
    'brief': 'BR',
    'requirement': 'REQ',
    'design': 'DES',
    'architecture': 'ARC',
    'code': 'CODE',
    'test': 'TEST',
    'deployment': 'DEP',
    'documentation': 'DOC',
    'adr': 'ADR',
    'evidence': 'EVD',
};

export interface ArtifactNodeData {
    id: string;
    type: ArtifactType;
    title: string;
    description?: string;
    status: 'complete' | 'in-progress' | 'pending' | 'blocked';
    persona?: string;
    phase: LifecyclePhase;
    linkedCount?: number;
    blockerCount?: number;
    isGhost?: boolean;
    /** Space-separated target node IDs for SR graph traversal (populated by CanvasWorkspace) */
    outgoingEdgeTargets?: string;
    onEdit?: (id: string) => void;
    onLink?: (id: string) => void;
    onAccept?: (id: string) => void;
    onReject?: (id: string) => void;
    onLinkCode?: (artifactId: string, relationship: CodeRelationshipType) => void;
    onOpenCode?: (codeArtifactId: string) => void;
    onTypeChange?: (artifactId: string, newType: ArtifactType) => void;
}

/**
 * ArtifactNode - ReactFlow node for lifecycle artifacts
 *
 * Wrapped in React.memo with a data-aware comparator to prevent unnecessary
 * re-renders when unrelated canvas state changes (e.g. another node is selected).
 */
const ArtifactNodeInner: React.FC<NodeProps<ArtifactNodeData>> = ({ id, data, selected }) => {
    const icon = ARTIFACT_ICONS[data.type] || 'ART';
    const hasBlockers = data.status === 'blocked' || (data.blockerCount && data.blockerCount > 0);

    // State for menus
    const [contextMenuAnchor, setContextMenuAnchor] = useState<null | HTMLElement>(null);
    const [codePreviewAnchor, setCodePreviewAnchor] = useState<null | HTMLElement>(null);
    const [showTypeSelector, setShowTypeSelector] = useState(false);

    // Read code associations from the batch-loaded atom — no per-node HTTP call.
    const allCodeAssociations = useAtomValue(codeAssociationsAtom);
    const codeAssociations = allCodeAssociations.get(data.id) ?? [];

    // Gate resize handles to navigate mode only (not sketch / code / diagram)
    const interactionMode = useAtomValue(canvasInteractionModeAtom);
    const zoom = useAtomValue(cameraZoomAtom);

    // Determine border color based on status (with accessible pattern)
    const getStatusColor = (status: string) => {
        switch (status) {
            case 'complete': return 'var(--color-success, #4caf50)';
            case 'in-progress': return 'var(--color-info, #2196f3)';
            case 'blocked': return 'var(--color-danger, #f44336)';
            default: return 'var(--color-muted, #bdbdbd)';
        }
    };

    const getStatusIcon = (status: string) => {
        switch (status) {
            case 'complete': return '✓';
            case 'in-progress': return '◐';
            case 'blocked': return '✕';
            default: return '○';
        }
    };

    const statusColor = getStatusColor(data.status);

    const nodeAriaLabel = `${data.type}: ${data.title}`;
    const statusDescId = `node-status-${id}`;

    return (
        <>
            {/* Allow resize while preserving aspect ratio */}
            <NodeResizer
                minWidth={220}
                minHeight={80}
                isVisible={selected && interactionMode === 'navigate'}
                handleStyle={{ width: 10 / zoom, height: 10 / zoom }}
            />

            <Card
                elevation={selected ? 8 : 1}
                className="min-w-[220px] max-w-[300px] rounded-xl"
                // Accessibility — tabIndex managed by ReactFlow's nodesFocusable prop
                // on the outer wrapper; a second tabIndex here creates double Tab stops
                role="article"
                aria-label={nodeAriaLabel}
                aria-selected={selected}
                aria-describedby={statusDescId}
                aria-flowto={data.outgoingEdgeTargets || undefined}
            >
                {/* SR-only status descriptor */}
                <span id={statusDescId} className="sr-only">
                    Status: {data.status}. Phase: {data.phase}.
                    {codeAssociations.length > 0 ? ` ${codeAssociations.length} code associations.` : ''}
                    {hasBlockers && data.blockerCount ? ` ${data.blockerCount} blockers.` : ''}
                </span>

                {/* Handles for connections */}
                <Handle
                    type="target"
                    position={Position.Top}
                    style={{ background: 'var(--color-primary, #1976d2)', width: 10, height: 10 }}
                />
                <Handle
                    type="source"
                    position={Position.Bottom}
                    style={{ background: 'var(--color-primary, #1976d2)', width: 10, height: 10 }}
                />
                <Handle
                    type="target"
                    position={Position.Left}
                    style={{ background: 'var(--color-primary, #1976d2)', width: 10, height: 10 }}
                />
                <Handle
                    type="source"
                    position={Position.Right}
                    style={{ background: 'var(--color-primary, #1976d2)', width: 10, height: 10 }}
                />

            <CardContent className="p-3 pb-3">
                {/* Header Row */}
                <Box className="flex items-start gap-2 mb-2">
                    <Typography as="h6" className="text-2xl">
                        {icon}
                    </Typography>
                    <Box className="flex-1 min-w-0 nodrag">
                        <Typography
                            as="p"
                            className="text-sm font-medium overflow-hidden text-ellipsis whitespace-nowrap"
                        >
                            {data.title}
                        </Typography>
                        <Typography
                            as="span"
                            className="text-xs text-gray-500 block overflow-hidden text-ellipsis whitespace-nowrap"
                        >
                            {data.type}
                        </Typography>
                    </Box>
                    <IconButton
                        size="sm"
                        onClick={(e) => setContextMenuAnchor(e.currentTarget)}
                        className="p-1 nodrag nopan"
                    >
                        <MoreVert className="text-base" />
                    </IconButton>

                    {/* Code Badge — populated from the workspace-level batch query */}
                    {codeAssociations.length > 0 && (
                        <Tooltip title={`${codeAssociations.length} code associations`}>
                            <IconButton
                                size="sm"
                                onClick={(e) => setCodePreviewAnchor(e.currentTarget)}
                                className="p-1 relative"
                                data-testid="code-badge"
                            >
                                <Badge
                                    badgeContent={codeAssociations.length}
                                    tone="primary"
                                    className="[&_.MuiBadge-badge]:text-[0.6rem] [&_.MuiBadge-badge]:h-[14px] [&_.MuiBadge-badge]:min-w-[14px]"
                                >
                                    <CodeIcon className="text-base" />
                                </Badge>
                            </IconButton>
                        </Tooltip>
                    )}
                </Box>

                {/* Context Menu */}
                <Menu
                    anchorEl={contextMenuAnchor}
                    open={Boolean(contextMenuAnchor)}
                    onClose={() => setContextMenuAnchor(null)}
                >
                    <MenuItem onClick={() => {
                        data.onEdit?.(data.id);
                        setContextMenuAnchor(null);
                    }}>
                        <ListItemText>Edit Artifact</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => {
                        setShowTypeSelector(true);
                        setContextMenuAnchor(null);
                    }}>
                        <ListItemIcon><SwapIcon size={16} /></ListItemIcon>
                        <ListItemText>Change Content Type</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => {
                        data.onLinkCode?.(data.id, 'IMPLEMENTATION');
                        setContextMenuAnchor(null);
                    }}>
                        <ListItemIcon><CodeIcon size={16} /></ListItemIcon>
                        <ListItemText>Link Code Implementation</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => {
                        data.onLinkCode?.(data.id, 'TEST');
                        setContextMenuAnchor(null);
                    }}>
                        <ListItemIcon><TestIcon size={16} /></ListItemIcon>
                        <ListItemText>Link Test Case</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => {
                        data.onLinkCode?.(data.id, 'DOCUMENTATION');
                        setContextMenuAnchor(null);
                    }}>
                        <ListItemIcon><DocIcon size={16} /></ListItemIcon>
                        <ListItemText>Link Documentation</ListItemText>
                    </MenuItem>
                    <MenuItem onClick={() => {
                        data.onLinkCode?.(data.id, 'MOCK');
                        setContextMenuAnchor(null);
                    }}>
                        <ListItemIcon><MockIcon size={16} /></ListItemIcon>
                        <ListItemText>Link Mock</ListItemText>
                    </MenuItem>
                </Menu>

                {/* Code Preview Popover */}
                <CodePreviewPopover
                    anchorEl={codePreviewAnchor}
                    associations={codeAssociations}
                    onClose={() => setCodePreviewAnchor(null)}
                    onOpenCode={data.onOpenCode}
                />

                {/* Type Selector Modal */}
                <TypeSelectorModal
                    open={showTypeSelector}
                    currentType={data.type}
                    artifactId={data.id}
                    onClose={() => setShowTypeSelector(false)}
                    onTypeChange={(artifactId, newType) => {
                        data.onTypeChange?.(artifactId, newType);
                        setShowTypeSelector(false);
                    }}
                />

                {/* Description */}
                {data.description && (
                    <Typography
                        as="p" className="text-xs mb-2 overflow-hidden line-clamp-2 nodrag nopan text-gray-500"
                    >
                        {data.description}
                    </Typography>
                )}

                {/* Badges Row */}
                <Box className="flex gap-1 flex-wrap items-center">
                    {/* Status Badge */}
                    <StatusBadge status={data.status} size="sm" />

                    {/* Persona Badge */}
                    {data.persona && (
                        <PersonaBadge persona={data.persona} size="sm" variant="outlined" />
                    )}

                    {/* Linked Count */}
                    {data.linkedCount && data.linkedCount > 0 && (
                        <Tooltip title={`${data.linkedCount} linked artifacts`}>
                            <Chip
                                icon={<LinkIcon className="text-xs" />}
                                label={data.linkedCount}
                                size="sm"
                                variant="outlined"
                                className="h-[20px] text-[0.7rem] [&_.MuiChip-label]:px-1"
                            />
                        </Tooltip>
                    )}

                    {/* Blocker Indicator */}
                    {hasBlockers && data.blockerCount && data.blockerCount > 0 && (
                        <Tooltip title={`${data.blockerCount} blockers`}>
                            <Chip
                                label={`! ${data.blockerCount}`}
                                size="sm"
                                tone="danger"
                                className="font-bold h-[20px] text-[0.7rem]"
                            />
                        </Tooltip>
                    )}
                </Box>

                {/* Ghost Node Actions */}
                {data.isGhost && (
                    <Box className="mt-3 pt-3 flex justify-end gap-2" style={{ borderTop: '1px dashed rgba(0, 0, 0, 0.2)' }}>
                        <Tooltip title="Reject suggestion">
                            <IconButton
                                size="sm"
                                tone="danger"
                                className="p-1"
                                onClick={(e) => {
                                    e.stopPropagation();
                                    data.onReject?.(data.id);
                                }}
                            >
                                <CloseIcon className="text-sm" />
                            </IconButton>
                        </Tooltip>
                        <Button
                            size="sm"
                            variant="solid"
                            tone="primary"
                            startIcon={<CheckIcon className="text-sm" />}
                            onClick={(e) => {
                                e.stopPropagation();
                                data.onAccept?.(data.id);
                            }}
                            className="rounded-lg py-0.5 px-2 text-[0.65rem] h-[24px]"
                        >
                            Accept
                        </Button>
                    </Box>
                )}
            </CardContent>
        </Card>
        </>
    );
};

/**
 * Memoized ArtifactNode — custom comparator prevents re-renders when only
 * unrelated canvas state (e.g. viewport, other node positions) changes.
 */
export const ArtifactNode = memo(ArtifactNodeInner, (prev, next) =>
    prev.id === next.id &&
    prev.selected === next.selected &&
    prev.data.id === next.data.id &&
    prev.data.status === next.data.status &&
    prev.data.title === next.data.title &&
    prev.data.phase === next.data.phase &&
    prev.data.isGhost === next.data.isGhost &&
    prev.data.linkedCount === next.data.linkedCount &&
    prev.data.blockerCount === next.data.blockerCount
);

// Export for node types registry
export default ArtifactNode;
