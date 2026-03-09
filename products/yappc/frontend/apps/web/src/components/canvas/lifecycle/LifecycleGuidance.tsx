/**
 * Lifecycle Guidance Component
 * 
 * Provides clear, contextual guidance for what to do at each lifecycle phase
 * Shows available actions, next steps, and tips
 * 
 * @doc.type component
 * @doc.purpose Display phase-specific guidance and next actions
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@ghatana/ui';
import { Lightbulb as LightbulbIcon, Pencil as EditIcon, BadgeCheck as VerifiedIcon, Code as CodeIcon, Rocket as RocketIcon, TrendingUp as TrendingUpIcon, Settings as SettingsIcon } from 'lucide-react';
import {
    LifecyclePhase,
    getOperationsForPhase,
    canTransitionToPhase,
} from '@/types/lifecycle';

export interface LifecycleGuidanceProps {
    currentPhase: LifecyclePhase;
    onPhaseTransition: (toPhase: LifecyclePhase) => void;
    projectName?: string;
    hasElements?: boolean;
    showTips?: boolean;
}

/**
 * Get icon for a lifecycle phase
 */
function getPhaseIcon(phase: LifecyclePhase) {
    switch (phase) {
        case LifecyclePhase.INTENT:
            return LightbulbIcon;
        case LifecyclePhase.SHAPE:
            return EditIcon;
        case LifecyclePhase.VALIDATE:
            return VerifiedIcon;
        case LifecyclePhase.GENERATE:
            return CodeIcon;
        case LifecyclePhase.RUN:
            return RocketIcon;
        case LifecyclePhase.OBSERVE:
            return TrendingUpIcon;
        case LifecyclePhase.IMPROVE:
            return SettingsIcon;
        default:
            return LightbulbIcon;
    }
}

/**
 * Get phase-specific guidance content
 */
function getPhaseGuidance(phase: LifecyclePhase) {
    const baseGuidance = {
        [LifecyclePhase.INTENT]: {
            title: 'Express Your Intent',
            description: 'Start by describing what you want to build. Use the AI chat to express your ideas.',
            tips: [
                'Describe the purpose and main features',
                'Mention technologies or frameworks you prefer',
                'List key integrations or requirements',
                'Sketch out user flows or requirements',
            ],
            nextStep: 'When ready, move to SHAPE phase to design your architecture',
            nextPhase: LifecyclePhase.SHAPE,
        },
        [LifecyclePhase.SHAPE]: {
            title: 'Shape Your Architecture',
            description: 'Design your application components, flows, and connections.',
            tips: [
                'Drag components from the palette (Frontend, API, Database)',
                'Connect components to show data flow',
                'Add labels and descriptions to each component',
                'Use the AI suggestions panel for architecture patterns',
                'Position components for clarity',
            ],
            nextStep: 'When your design is complete, validate it for gaps and risks',
            nextPhase: LifecyclePhase.VALIDATE,
        },
        [LifecyclePhase.VALIDATE]: {
            title: 'Validate Design',
            description: 'AI checks your design for gaps, risks, and best practices.',
            tips: [
                'Review validation results for any issues',
                'Check for missing components (API, Auth, Logging)',
                'Verify data flow connections are complete',
                'Address any risks or warnings',
                'Return to SHAPE phase to fix issues if needed',
            ],
            nextStep: 'When validation passes, generate code and resources',
            nextPhase: LifecyclePhase.GENERATE,
        },
        [LifecyclePhase.GENERATE]: {
            title: 'Generate Code',
            description: 'AI generates code, configs, and infrastructure from your design.',
            tips: [
                'Review generated code and configuration',
                'Check project structure and file organization',
                'Verify dependencies and package.json',
                'Review environment configuration',
                'Download or save generated artifacts',
            ],
            nextStep: 'Deploy and run your application',
            nextPhase: LifecyclePhase.RUN,
        },
        [LifecyclePhase.RUN]: {
            title: 'Run Application',
            description: 'Deploy and execute your generated code in a runtime environment.',
            tips: [
                'Monitor deployment progress',
                'Check application logs for errors',
                'Test basic functionality',
                'Verify all services are running',
                'Set up monitoring and alerts',
            ],
            nextStep: 'Move to OBSERVE phase to monitor performance',
            nextPhase: LifecyclePhase.OBSERVE,
        },
        [LifecyclePhase.OBSERVE]: {
            title: 'Observe & Monitor',
            description: 'Monitor your application performance, logs, and user behavior.',
            tips: [
                'Check performance metrics and dashboards',
                'Review error logs and warnings',
                'Analyze user behavior and usage patterns',
                'Identify bottlenecks or issues',
                'Gather insights for improvements',
            ],
            nextStep: 'Use insights to improve your application',
            nextPhase: LifecyclePhase.IMPROVE,
        },
        [LifecyclePhase.IMPROVE]: {
            title: 'Iterate & Improve',
            description: 'Update your design and code based on observations and learnings.',
            tips: [
                'Update canvas design with improvements',
                'Add new components or features',
                'Refactor problematic areas',
                'Optimize performance bottlenecks',
                'Cycle back to VALIDATE or SHAPE for changes',
            ],
            nextStep: 'Continue improving or start a new cycle',
            nextPhase: LifecyclePhase.SHAPE,
        },
    };

    return baseGuidance[phase] || baseGuidance[LifecyclePhase.SHAPE];
}

/**
 * Lifecycle Guidance Component
 */
export const LifecycleGuidance: React.FC<LifecycleGuidanceProps> = ({
    currentPhase,
    onPhaseTransition,
    projectName = 'Your Project',
    hasElements = false,
    showTips = true,
}) => {
    const [showWelcome, setShowWelcome] = React.useState(
        currentPhase === LifecyclePhase.SHAPE && !hasElements
    );
    const [expandedTips, setExpandedTips] = React.useState(false);

    const guidance = getPhaseGuidance(currentPhase);
    const operations = getOperationsForPhase(currentPhase);
    const PhaseIcon = getPhaseIcon(currentPhase);

    // Get all valid next phases
    const validNextPhases = (Object.values(LifecyclePhase) as LifecyclePhase[]).filter(
        (phase) => canTransitionToPhase(currentPhase, phase)
    );

    // Main action (primary next phase)
    const mainNextPhase = validNextPhases[0];
    const canAdvance = mainNextPhase && (hasElements || currentPhase !== LifecyclePhase.SHAPE);

    return (
        <>
            {/* Welcome Dialog for New Projects in SHAPE Phase */}
            <Dialog open={showWelcome} maxWidth="sm" fullWidth>
                <DialogTitle>
                    <Stack direction="row" spacing={1} alignItems="center">
                        <PhaseIcon color="primary" />
                        <Typography variant="h6">Welcome to {projectName}!</Typography>
                    </Stack>
                </DialogTitle>
                <DialogContent>
                    <Stack spacing={3}>
                        <Typography variant="body2" color="textSecondary">
                            You're now in the <strong>SHAPE phase</strong> where you'll design your application architecture.
                        </Typography>

                        <Typography variant="subtitle2" fontWeight="bold">
                            Here's what you can do:
                        </Typography>
                        <ul style={{ marginTop: 8, paddingLeft: 20 }}>
                            <li>Drag components from the palette on the right</li>
                            <li>Connect components to show data flow</li>
                            <li>Add labels and descriptions</li>
                            <li>Use AI suggestions for patterns</li>
                        </ul>

                        <Alert severity="info">
                            Tip: Start by adding Frontend, API, and Database components to your canvas.
                        </Alert>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowWelcome(false)}>Got it!</Button>
                </DialogActions>
            </Dialog>

            {/* Phase Header with Icon and Description */}
            <Card
                style={{ background: `linear-gradient(135deg }}
            >
                <CardContent>
                    <Stack direction="row" spacing={2} alignItems="flex-start">
                        <Box className="pt-1">
                            <PhaseIcon className="text-[32px] text-[#2196f3]" />
                        </Box>

                        <Stack spacing={1} flex={1}>
                            <Typography variant="h6" fontWeight="bold">
                                {guidance.title}
                            </Typography>

                            <Typography variant="body2" color="textSecondary">
                                {guidance.description}
                            </Typography>

                            {/* Quick Actions */}
                            <Stack direction="row" spacing={1} className="mt-4 flex-wrap">
                                {operations.canEdit && (
                                    <Chip
                                        label="Edit"
                                        size="small"
                                        variant="outlined"
                                        color="primary"
                                    />
                                )}
                                {operations.canValidate && (
                                    <Chip
                                        label="Validate"
                                        size="small"
                                        variant="outlined"
                                        color="warning"
                                    />
                                )}
                                {operations.canGenerate && (
                                    <Chip
                                        label="Generate"
                                        size="small"
                                        variant="outlined"
                                        color="success"
                                    />
                                )}
                                {operations.canDeploy && (
                                    <Chip
                                        label="Deploy"
                                        size="small"
                                        variant="outlined"
                                        color="success"
                                    />
                                )}
                                {operations.canObserve && (
                                    <Chip
                                        label="Monitor"
                                        size="small"
                                        variant="outlined"
                                        color="info"
                                    />
                                )}
                            </Stack>
                        </Stack>
                    </Stack>
                </CardContent>
            </Card>

            {/* Tips Section */}
            {showTips && (
                <Card className="mt-4">
                    <CardContent>
                        <Stack spacing={2}>
                            <Box
                                onClick={() => setExpandedTips(!expandedTips)}
                                className="cursor-pointer flex items-center gap-2"
                            >
                                <LightbulbIcon className="text-[#FFC107]" />
                                <Typography variant="subtitle2" fontWeight="bold">
                                    Tips for {guidance.title.split(' ').pop()}
                                </Typography>
                            </Box>

                            {expandedTips && (
                                <Stack component="ul" spacing={1} className="ml-4 mb-2">
                                    {guidance.tips.map((tip, idx) => (
                                        <Typography key={idx} component="li" variant="body2">
                                            {tip}
                                        </Typography>
                                    ))}
                                </Stack>
                            )}
                        </Stack>
                    </CardContent>
                </Card>
            )}

            {/* Next Steps */}
            <Card className="mt-4" style={{ backgroundColor: 'rgba(76' }} >
                <CardContent>
                    <Stack spacing={2}>
                        <Typography variant="subtitle2" fontWeight="bold">
                            Next Step
                        </Typography>

                        <Typography variant="body2" color="textSecondary">
                            {guidance.nextStep}
                        </Typography>

                        {/* Transition Buttons */}
                        <Stack direction="row" spacing={1} className="mt-4 flex-wrap">
                            {validNextPhases.map((phase) => (
                                <Button
                                    key={phase}
                                    variant={phase === mainNextPhase ? 'contained' : 'outlined'}
                                    onClick={() => onPhaseTransition(phase)}
                                    disabled={phase === LifecyclePhase.SHAPE && !canAdvance}
                                    size="small"
                                >
                                    {phase}
                                </Button>
                            ))}
                        </Stack>

                        {!canAdvance && currentPhase === LifecyclePhase.SHAPE && (
                            <Alert severity="warning" className="mt-2">
                                Add some components to your canvas before validating.
                            </Alert>
                        )}
                    </Stack>
                </CardContent>
            </Card>

            {/* Workflow Overview */}
            <Card className="mt-4">
                <CardContent>
                    <Typography variant="subtitle2" fontWeight="bold" className="mb-4">
                        Full Workflow
                    </Typography>

                    <Stack direction="row" spacing={1} className="overflow-x-auto pb-2">
                        {Object.values(LifecyclePhase).map((phase) => (
                            <Chip
                                key={phase}
                                label={phase}
                                variant={phase === currentPhase ? 'filled' : 'outlined'}
                                color={phase === currentPhase ? 'primary' : 'default'}
                                size="small"
                                onClick={() => {
                                    if (canTransitionToPhase(currentPhase, phase)) {
                                        onPhaseTransition(phase);
                                    }
                                }}
                                style={{ cursor: canTransitionToPhase(currentPhase }}
                            />
                        ))}
                    </Stack>
                </CardContent>
            </Card>
        </>
    );
};

export default LifecycleGuidance;
