/**
 * Ghost Nodes Component
 * 
 * Renders semi-transparent placeholder nodes in the Zero State (empty canvas).
 * These nodes suggest what artifacts should be created in each phase,
 * reducing "white board paralysis" and guiding users to first action.
 * 
 * From CANVAS_UX_DESIGN_SPEC.md Section 3, State 1:
 * - Ghost Nodes: Semi-transparent nodes appear in the "Intent" zone
 * - Pulsing Action: The "Start Here" button pulses gently
 * - Locked Zones: Future zones are visually dimmed/locked
 * 
 * @doc.type component
 * @doc.purpose Zero state guidance for empty canvas
 * @doc.layer product
 * @doc.pattern Onboarding
 */

import React, { useMemo, useState, useEffect } from 'react';
import { Box, Typography, Button, Card, CardContent, CardActions, IconButton } from '@ghatana/ui';
import { keyframes } from '@emotion/react';
import { Flag as IntentIcon, Building2 as ShapeIcon, CheckCircle as ValidateIcon, Hammer as GenerateIcon, Play as RunIcon, Eye as ObserveIcon, TrendingUp as ImproveIcon, Plus as AddIcon, Sparkles as AIIcon, X as CloseIcon } from 'lucide-react';
import { LifecyclePhase } from '@/types/lifecycle';
import { PHASE_COLORS } from '@/styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface GhostNodeTemplate {
    id: string;
    phase: LifecyclePhase;
    title: string;
    description: string;
    icon: React.ReactNode;
    isPrimary: boolean; // Primary ghost nodes have "Start Here" styling
}

export interface GhostNodesProps {
    /** Current lifecycle phase of the project */
    currentPhase: LifecyclePhase;
    /** Total artifact count (if 0, show zero state) */
    artifactCount: number;
    /** Callback when user clicks to create an artifact */
    onCreateArtifact: (template: GhostNodeTemplate) => void;
    /** Callback when user clicks "Get AI Suggestion" */
    onAISuggestion?: () => void;
}

// ============================================================================
// Animations
// ============================================================================

const pulse = keyframes`
    0% {
        box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.4);
    }
    70% {
        box-shadow: 0 0 0 10px rgba(99, 102, 241, 0);
    }
    100% {
        box-shadow: 0 0 0 0 rgba(99, 102, 241, 0);
    }
`;

const fadeIn = keyframes`
    from {
        opacity: 0;
        transform: translateY(10px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
`;

// ============================================================================
// Ghost Node Templates by Phase
// ============================================================================

const GHOST_TEMPLATES: Record<LifecyclePhase, GhostNodeTemplate[]> = {
    [LifecyclePhase.INTENT]: [
        {
            id: 'ghost-mission',
            phase: LifecyclePhase.INTENT,
            title: 'Project Mission',
            description: 'Define the core problem you\'re solving',
            icon: <IntentIcon />,
            isPrimary: true,
        },
        {
            id: 'ghost-goals',
            phase: LifecyclePhase.INTENT,
            title: 'Success Metrics',
            description: 'Set measurable KPIs and goals',
            icon: <IntentIcon />,
            isPrimary: false,
        },
        {
            id: 'ghost-stakeholders',
            phase: LifecyclePhase.INTENT,
            title: 'Stakeholders',
            description: 'Identify key personas and users',
            icon: <IntentIcon />,
            isPrimary: false,
        },
    ],
    [LifecyclePhase.SHAPE]: [
        {
            id: 'ghost-architecture',
            phase: LifecyclePhase.SHAPE,
            title: 'System Architecture',
            description: 'Define high-level system design',
            icon: <ShapeIcon />,
            isPrimary: true,
        },
        {
            id: 'ghost-requirements',
            phase: LifecyclePhase.SHAPE,
            title: 'Requirements',
            description: 'Document functional requirements',
            icon: <ShapeIcon />,
            isPrimary: false,
        },
    ],
    [LifecyclePhase.VALIDATE]: [
        {
            id: 'ghost-prototype',
            phase: LifecyclePhase.VALIDATE,
            title: 'Prototype',
            description: 'Create a proof of concept',
            icon: <ValidateIcon />,
            isPrimary: true,
        },
    ],
    [LifecyclePhase.GENERATE]: [
        {
            id: 'ghost-epic',
            phase: LifecyclePhase.GENERATE,
            title: 'First Epic',
            description: 'Break down into implementable stories',
            icon: <GenerateIcon />,
            isPrimary: true,
        },
    ],
    [LifecyclePhase.RUN]: [
        {
            id: 'ghost-deploy',
            phase: LifecyclePhase.RUN,
            title: 'Deployment Pipeline',
            description: 'Set up CI/CD configuration',
            icon: <RunIcon />,
            isPrimary: true,
        },
    ],
    [LifecyclePhase.OBSERVE]: [
        {
            id: 'ghost-monitoring',
            phase: LifecyclePhase.OBSERVE,
            title: 'Monitoring Dashboard',
            description: 'Configure observability',
            icon: <ObserveIcon />,
            isPrimary: true,
        },
    ],
    [LifecyclePhase.IMPROVE]: [
        {
            id: 'ghost-feedback',
            phase: LifecyclePhase.IMPROVE,
            title: 'Feedback Collection',
            description: 'Set up user feedback loops',
            icon: <ImproveIcon />,
            isPrimary: true,
        },
    ],
};

// ============================================================================
// Phase Icon Map
// ============================================================================

const PHASE_ICONS: Record<LifecyclePhase, React.ReactNode> = {
    [LifecyclePhase.INTENT]: <IntentIcon />,
    [LifecyclePhase.SHAPE]: <ShapeIcon />,
    [LifecyclePhase.VALIDATE]: <ValidateIcon />,
    [LifecyclePhase.GENERATE]: <GenerateIcon />,
    [LifecyclePhase.RUN]: <RunIcon />,
    [LifecyclePhase.OBSERVE]: <ObserveIcon />,
    [LifecyclePhase.IMPROVE]: <ImproveIcon />,
};

// ============================================================================
// Components
// ============================================================================

/**
 * Individual Ghost Node Card
 */
const GhostNodeCard: React.FC<{
    template: GhostNodeTemplate;
    onClick: () => void;
    isLocked: boolean;
    delay: number;
}> = ({ template, onClick, isLocked, delay }) => {
    const phaseColor = PHASE_COLORS[template.phase]?.primary || '#6366F1';

    const handleClick = (e?: React.MouseEvent) => {
        e?.stopPropagation(); // Prevent backdrop dismiss
        if (isLocked) {
            const ok = window.confirm(`This template is in a future phase (${template.phase}). Create it anyway?`);
            if (!ok) return;
        }
        onClick();
    };

    return (
        <Card
            className="w-[220px]"
            style={{
                opacity: isLocked ? 0.6 : 0.85,
                borderColor: isLocked ? '#9ca3af' : phaseColor,
                borderStyle: 'dashed',
                borderWidth: 2,
                animation: `${fadeIn} 0.4s ease-out ${delay}s both`,
            }}
            onClick={handleClick}
        >
            <CardContent className="pb-2">
                <Box className="flex items-center gap-2 mb-2">
                    <Box style={{ color: phaseColor, border: '2px dashed' }}>
                        {template.icon}
                    </Box>
                    <Typography as="p" className="text-sm font-medium" fontWeight={600}>
                        {template.title}
                    </Typography>
                </Box>
                <Typography as="p" className="text-sm" color="text.secondary" fontSize={12}>
                    {template.description}
                </Typography>
            </CardContent>
            <CardActions className="pt-0">
                <Button
                    size="sm"
                    startIcon={<AddIcon />}
                    onClick={handleClick}
                    className="font-medium" >
                    {template.isPrimary ? 'Start Here' : 'Create'}{isLocked ? ' (locked)' : ''}
                </Button>
            </CardActions>
        </Card>
    );
};

/**
 * Main Ghost Nodes Component
 */
export const GhostNodes: React.FC<GhostNodesProps> = ({
    currentPhase,
    artifactCount,
    onCreateArtifact,
    onAISuggestion,
}) => {
    // Only show ghost nodes when canvas is empty (strict) and user hasn't dismissed them
    const [dismissed, setDismissed] = useState<boolean>(() => {
        try {
            return localStorage.getItem('canvas:ghostDismissed_v1') === '1';
        } catch (e) {
            return false;
        }
    });

    const showGhostNodes = !dismissed && artifactCount === 0; // Strict empty-canvas condition

    // Get templates for current phase and next phases
    const visibleTemplates = useMemo(() => {
        if (!showGhostNodes) return [];

        const phaseOrder: LifecyclePhase[] = [
            LifecyclePhase.INTENT,
            LifecyclePhase.SHAPE,
            LifecyclePhase.VALIDATE,
            LifecyclePhase.GENERATE,
            LifecyclePhase.RUN,
            LifecyclePhase.OBSERVE,
            LifecyclePhase.IMPROVE,
        ];

        const currentIndex = phaseOrder.indexOf(currentPhase);
        const templates: Array<{ template: GhostNodeTemplate; isLocked: boolean }> = [];

        // Show templates for current phase and next 2 phases
        for (let i = currentIndex; i < Math.min(currentIndex + 3, phaseOrder.length); i++) {
            const phase = phaseOrder[i];
            const phaseTemplates = GHOST_TEMPLATES[phase] || [];

            phaseTemplates.forEach(template => {
                templates.push({
                    template,
                    isLocked: i > currentIndex, // Lock future phases
                });
            });
        }

        return templates;
    }, [currentPhase, showGhostNodes]);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setDismissed(true);
        };

        if (showGhostNodes) {
            window.addEventListener('keydown', onKey);
        }

        return () => window.removeEventListener('keydown', onKey);
    }, [showGhostNodes]);

    if (!showGhostNodes) {
        return null;
    }

    return (
        <>
            {/* Semi-transparent backdrop - click to dismiss */}
            <Box
                onClick={() => {
                    setDismissed(true);
                    try {
                        localStorage.setItem('canvas:ghostDismissed_v1', '1');
                    } catch { }
                }}
                className="absolute cursor-pointer top-[0px] left-[0px] right-[0px] bottom-[0px] z-[1]" />

            {/* Ghost content - floating above backdrop */}
            <Box
                className="absolute flex flex-col items-center justify-center pointer-events-none top-[0px] left-[0px] right-[0px] bottom-[0px] z-[2]"
            >
                {/* Welcome Message */}
                <Box
                    className="text-center mb-8"
                    style={{ animation: `${fadeIn} 0.45s ease-out both` }}
                >
                    {/* Dismiss Button */}
                    <Box className="absolute right-[0px] top-[-8px]">
                        <IconButton size="sm" onClick={() => { setDismissed(true); try { localStorage.setItem('canvas:ghostDismissed_v1', '1'); } catch { } }}>
                            <CloseIcon className="text-base" />
                        </IconButton>
                    </Box>

                    <Typography as="h5" fontWeight={600} gutterBottom>
                        Let's Build Something Amazing
                    </Typography>
                    <Typography as="p" color="text.secondary" className="mb-4 max-w-[400px] mx-auto">
                        Start by defining your project's mission and goals.
                        Each card below represents a key artifact to create.
                    </Typography>
                    {onAISuggestion && (
                        <Button
                            variant="outlined"
                            startIcon={<AIIcon />}
                            onClick={onAISuggestion}
                            className="rounded-lg normal-case"
                        >
                            Get AI Suggestion
                        </Button>
                    )}
                </Box>

                {/* Ghost Node Grid */}
                <Box
                    className="flex flex-wrap gap-6 justify-center max-w-[800px] pointer-events-auto"
                >
                    {visibleTemplates.map(({ template, isLocked }, index) => (
                        <GhostNodeCard
                            key={template.id}
                            template={template}
                            onClick={() => {
                                if (isLocked) {
                                    const ok = window.confirm(`This template is in a future phase (${template.phase}). Create it anyway?`);
                                    if (!ok) return;
                                }
                                onCreateArtifact(template);
                            }}
                            isLocked={isLocked}
                            delay={index * 0.1}
                        />
                    ))}
                </Box>

                {/* Phase Indicator */}
                <Box
                    className="mt-8 flex items-center gap-2 text-gray-500 dark:text-gray-400"
                    style={{ animation: `${fadeIn} 0.5s ease-out 0.2s both` }}
                >
                    {PHASE_ICONS[currentPhase]}
                    <Typography as="p" className="text-sm">
                        You are in the <strong>{currentPhase}</strong> phase
                    </Typography>
                </Box>
            </Box>
        </>
    );
};

export default GhostNodes;
