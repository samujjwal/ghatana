/**
 * Artifact Palette Component
 * 
 * Draggable palette of artifact types for quick canvas creation.
 * 
 * @doc.type component
 * @doc.purpose Artifact creation palette
 * @doc.layer product
 * @doc.pattern Palette
 */

import * as React from 'react';
import { Surface as Paper, Box, Typography, IconButton, Tooltip, Collapse, Divider, Stack, Chip } from '@ghatana/ui';
import { ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, GripVertical as DragIcon, Info as InfoIcon, Lightbulb as LightbulbIcon } from 'lucide-react';
import { LifecyclePhase } from '@/types/lifecycle';
import { INTERACTIONS, TIMING } from '@/styles/design-tokens';
import {
    ARTIFACT_TEMPLATES,
    PHASE_GROUPS,
    type ArtifactTemplate,
    type ArtifactType,
} from './artifact-templates';

// Re-export for backward compatibility
export type { ArtifactTemplate, ArtifactType };

export interface ArtifactPaletteProps {
    onDragStart: (template: ArtifactTemplate) => void;
    onQuickCreate: (template: ArtifactTemplate) => void;
}

export const ArtifactPalette: React.FC<ArtifactPaletteProps> = ({ onDragStart, onQuickCreate }) => {
    const [expandedPhases, setExpandedPhases] = React.useState<Set<LifecyclePhase>>(
        new Set([LifecyclePhase.INTENT, LifecyclePhase.SHAPE])
    );

    const togglePhase = (phase: LifecyclePhase) => {
        setExpandedPhases((prev) => {
            const next = new Set(prev);
            if (next.has(phase)) {
                next.delete(phase);
            } else {
                next.add(phase);
            }
            return next;
        });
    };

    const handleDragStart = (e: React.DragEvent, template: ArtifactTemplate) => {
        e.dataTransfer.effectAllowed = 'copy';
        e.dataTransfer.setData('application/artifact-template', JSON.stringify(template));
        onDragStart(template);
    };

    return (
        <Box
            className="h-full flex flex-col"
        >
            <Box className="flex items-center justify-between px-5 py-4 border-b border-solid border-gray-200 dark:border-gray-700">
                <Typography as="p" className="text-sm uppercase text-xs tracking-[0.5px]" fontWeight="600" color="text.secondary">
                    Artifacts Library
                </Typography>
                <Tooltip title="Drag to canvas or click to quick create" enterDelay={TIMING.tooltipDelay}>
                    <IconButton size="sm" aria-label="Artifact palette info" className="p-1">
                        <InfoIcon className="text-base" />
                    </IconButton>
                </Tooltip>
            </Box>

            <Stack spacing={0} className="flex-1 overflow-auto px-4 py-3">
                {PHASE_GROUPS.map((group) => {
                    const templates = ARTIFACT_TEMPLATES.filter((t) => t.phase === group.phase);
                    if (!templates.length) return null;

                    const isExpanded = expandedPhases.has(group.phase);

                    return (
                        <Box key={group.phase}>
                            <Box
                                onClick={() => togglePhase(group.phase)}
                                className="flex items-center justify-between px-3 py-[10px] rounded-lg cursor-pointer border border-solid border-gray-200 dark:border-gray-700 transition-all duration-200 hover:shadow-sm"
                                style={{ backgroundColor: `${group.color}20` }}
                            >
                                <Box className="flex items-center gap-2">
                                    <Typography as="p" className="text-sm text-[0.8125rem]" fontWeight="600">
                                        {group.label}
                                    </Typography>
                                    <Chip label={templates.length} size="sm" className="font-semibold h-[20px] text-[0.7rem]" />
                                </Box>
                                <IconButton size="sm" className="p-0">
                                    {isExpanded ? <ExpandLessIcon size={16} /> : <ExpandMoreIcon size={16} />}
                                </IconButton>
                            </Box>

                            <Collapse in={isExpanded} timeout={200}>
                                <Stack spacing={1} className="mt-3">
                                    {templates.map((template) => (
                                        <Paper
                                            key={template.type}
                                            variant="flat"
                                            draggable
                                            onDragStart={(e) => handleDragStart(e, template)}
                                            onClick={() => onQuickCreate(template)}
                                            className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg cursor-grab transition-all duration-200 bg-white dark:bg-gray-900 active:cursor-grabbing shadow translate-x-[4px]"
                                            style={{ borderColor: `${group.color}55` }}
                                        >
                                            <Box className="flex items-start gap-2">
                                                <Box className="flex items-center gap-1">
                                                    <DragIcon className="text-gray-400 dark:text-gray-600 text-sm" />
                                                    <Typography className="text-lg">{template.icon}</Typography>
                                                </Box>
                                                <Box className="flex-1 min-w-0">
                                                    <Typography as="p" className="text-sm" fontWeight="medium" noWrap>
                                                        {template.label}
                                                    </Typography>
                                                    <Typography as="span" className="text-xs text-gray-500 block" color="text.secondary">
                                                        {template.description}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </Paper>
                                    ))}
                                </Stack>
                            </Collapse>
                        </Box>
                    );
                })}
            </Stack>

            <Box
                className="px-5 py-3 border-t border-solid border-gray-200 dark:border-gray-700 flex items-center gap-2 justify-center"
                style={{ backgroundColor: 'rgba(148, 163, 184, 0.08)' }}
            >
                <LightbulbIcon className="text-gray-500 dark:text-gray-400 text-base" />
                <Typography as="span" className="text-xs text-gray-500 text-[0.7rem]" color="text.secondary">
                    Drag to canvas or double-click canvas for quick menu
                </Typography>
            </Box>
        </Box>
    );
};
