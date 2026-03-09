/**
 * Phase Guided Flow - Step-by-step phase completion with persona guidance
 * 
 * Provides a structured flow for completing each phase with:
 * - Persona-specific task ordering
 * - Intelligent task validation
 * - Progress tracking
 * - AI assistance integration
 * 
 * @doc.type component
 * @doc.purpose Phase completion flow
 * @doc.layer product
 * @doc.pattern Flow Component
 */

import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { Box, Card, CardContent, Typography, Button, Stepper, Step, StepLabel, StepLabel as StepContent, Chip, LinearProgress, Alert } from '@ghatana/ui';
import { CheckCircle, Play as PlayArrow, ArrowLeft as ArrowBack, ArrowRight as ArrowForward, Lightbulb, Sparkles as AutoAwesome } from 'lucide-react';

interface PhaseGuidedFlowProps {
    projectId: string;
    phase: string;
    persona: string;
    onComplete: () => void;
    onTaskChange: () => void;
}

export function PhaseGuidedFlow({
    projectId,
    phase,
    persona,
    onComplete,
    onTaskChange
}: PhaseGuidedFlowProps) {
    const navigate = useNavigate();
    const [currentStep, setCurrentStep] = useState(0);
    const [completedSteps, setCompletedSteps] = useState<number[]>([]);

    // Mock phase data based on phase ID
    const getPhaseData = () => {
        const phaseMap = {
            'plan': {
                name: 'Planning Phase',
                description: 'Define requirements and project scope',
                tasks: [
                    { id: 'requirements', title: 'Define Requirements', description: 'Gather and document user requirements' },
                    { id: 'user-stories', title: 'Create User Stories', description: 'Write detailed user stories with acceptance criteria' },
                    { id: 'tech-stack', title: 'Select Tech Stack', description: 'Choose appropriate technologies and frameworks' },
                    { id: 'milestones', title: 'Set Milestones', description: 'Define project milestones and timeline' }
                ]
            },
            'design': {
                name: 'Design Phase',
                description: 'Create user interface and experience design',
                tasks: [
                    { id: 'wireframes', title: 'Create Wireframes', description: 'Design basic layout and structure' },
                    { id: 'ui-design', title: 'UI Design', description: 'Create detailed visual designs' },
                    { id: 'prototype', title: 'Build Prototype', description: 'Create interactive prototype' },
                    { id: 'design-system', title: 'Design System', description: 'Establish design tokens and components' }
                ]
            },
            'implement': {
                name: 'Implementation Phase',
                description: 'Build the application functionality',
                tasks: [
                    { id: 'setup', title: 'Project Setup', description: 'Initialize project structure and dependencies' },
                    { id: 'backend', title: 'Backend Development', description: 'Implement API endpoints and business logic' },
                    { id: 'frontend', title: 'Frontend Development', description: 'Build user interface components' },
                    { id: 'integration', title: 'Integration', description: 'Connect frontend and backend' }
                ]
            },
            'test': {
                name: 'Testing Phase',
                description: 'Ensure quality and reliability',
                tasks: [
                    { id: 'unit-tests', title: 'Unit Tests', description: 'Write unit tests for core functionality' },
                    { id: 'integration-tests', title: 'Integration Tests', description: 'Test component interactions' },
                    { id: 'e2e-tests', title: 'E2E Tests', description: 'Test complete user flows' },
                    { id: 'performance', title: 'Performance Testing', description: 'Optimize application performance' }
                ]
            },
            'deploy': {
                name: 'Deployment Phase',
                description: 'Deploy to production environment',
                tasks: [
                    { id: 'build', title: 'Build Application', description: 'Create production build' },
                    { id: 'infrastructure', title: 'Setup Infrastructure', description: 'Configure servers and databases' },
                    { id: 'deployment', title: 'Deploy', description: 'Deploy application to production' },
                    { id: 'monitoring', title: 'Setup Monitoring', description: 'Configure logging and monitoring' }
                ]
            }
        };

        return phaseMap[phase as keyof typeof phaseMap] || phaseMap.plan;
    };

    const phaseData = getPhaseData();
    const tasks = phaseData.tasks;

    const handleStepNext = () => {
        if (currentStep < tasks.length - 1) {
            setCurrentStep(currentStep + 1);
            if (!completedSteps.includes(currentStep)) {
                setCompletedSteps([...completedSteps, currentStep]);
                onTaskChange();
            }
        } else {
            // Phase completed
            onComplete();
        }
    };

    const handleStepBack = () => {
        if (currentStep > 0) {
            setCurrentStep(currentStep - 1);
        }
    };

    const handleStepComplete = () => {
        if (!completedSteps.includes(currentStep)) {
            setCompletedSteps([...completedSteps, currentStep]);
            onTaskChange();
        }
    };

    const progressPercentage = (completedSteps.length / tasks.length) * 100;

    return (
        <Box className="h-full overflow-auto bg-bg-default p-6">
            {/* Header */}
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Box>
                    <Typography as="h4" fontWeight="bold" mb={1}>
                        {phaseData.name}
                    </Typography>
                    <Typography as="p" color="text.secondary">
                        {phaseData.description}
                    </Typography>
                </Box>
                <Button
                    variant="outlined"
                    startIcon={<ArrowBack />}
                    onClick={() => navigate(`/journey/p/${projectId}`)}
                >
                    Back to Project
                </Button>
            </Box>

            {/* Progress Overview */}
            <Card variant="flat" className="mb-8 border border-solid border-gray-200 dark:border-gray-700">
                <CardContent>
                    <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                        <Typography as="h6" fontWeight="medium">
                            Phase Progress
                        </Typography>
                        <Chip
                            label={`${completedSteps.length}/${tasks.length} tasks`}
                            tone="primary"
                            variant="outlined"
                        />
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={progressPercentage}
                        className="h-[8px] rounded-2xl"
                    />
                </CardContent>
            </Card>

            {/* Stepper */}
            <Card variant="flat" className="border border-solid border-gray-200 dark:border-gray-700">
                <CardContent>
                    <Stepper activeStep={currentStep} orientation="vertical">
                        {tasks.map((task, index) => (
                            <Step key={task.id} completed={completedSteps.includes(index)}>
                                <StepLabel
                                    icon={
                                        completedSteps.includes(index) ? (
                                            <CheckCircle tone="success" />
                                        ) : currentStep === index ? (
                                            <PlayArrow tone="primary" />
                                        ) : (
                                            <div className="w-6 h-6 rounded-full border-2 border-gray-300" />
                                        )
                                    }
                                >
                                    <Box display="flex" alignItems="center" gap={2}>
                                        <Typography as="h6" fontWeight="medium">
                                            {task.title}
                                        </Typography>
                                        {currentStep === index && (
                                            <Chip
                                                label="Current"
                                                size="sm"
                                                tone="primary"
                                                variant="outlined"
                                            />
                                        )}
                                    </Box>
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        {task.description}
                                    </Typography>
                                </StepLabel>
                                <StepContent>
                                    <Box className="bg-bg-paper-secondary rounded-lg p-4 mb-2">
                                        <Typography as="p" className="text-sm" mb={2}>
                                            Task content and instructions would go here. This is where the user would
                                            complete the actual work for this task.
                                        </Typography>

                                        {/* AI Assistance */}
                                        <Alert severity="info" icon={<AutoAwesome />} className="mb-4">
                                            <Typography as="p" className="text-sm">
                                                AI assistance available for this task. Get intelligent suggestions
                                                and automated completion based on your persona ({persona}).
                                            </Typography>
                                        </Alert>

                                        {/* Task Actions */}
                                        <Box display="flex" gap={2}>
                                            {currentStep === index && (
                                                <>
                                                    <Button
                                                        variant="solid"
                                                        onClick={handleStepComplete}
                                                        startIcon={<CheckCircle />}
                                                    >
                                                        Mark Complete
                                                    </Button>
                                                    <Button
                                                        variant="outlined"
                                                        onClick={handleStepNext}
                                                        endIcon={<ArrowForward />}
                                                    >
                                                        Next Task
                                                    </Button>
                                                </>
                                            )}
                                            {index > 0 && currentStep === index && (
                                                <Button
                                                    variant="ghost"
                                                    onClick={handleStepBack}
                                                    startIcon={<ArrowBack />}
                                                >
                                                    Previous
                                                </Button>
                                            )}
                                        </Box>
                                    </Box>
                                </StepContent>
                            </Step>
                        ))}
                    </Stepper>

                    {/* Phase Completion */}
                    {completedSteps.length === tasks.length && (
                        <Box className="text-center py-4">
                            <Alert severity="success" icon={<CheckCircle />}>
                                <Typography as="h6" fontWeight="bold" mb={1}>
                                    Phase Completed!
                                </Typography>
                                <Typography as="p" className="text-sm" mb={2}>
                                    You've successfully completed all tasks in the {phaseData.name}.
                                </Typography>
                                <Button
                                    variant="solid"
                                    size="lg"
                                    onClick={onComplete}
                                >
                                    Continue to Next Phase
                                </Button>
                            </Alert>
                        </Box>
                    )}
                </CardContent>
            </Card>
        </Box>
    );
}
