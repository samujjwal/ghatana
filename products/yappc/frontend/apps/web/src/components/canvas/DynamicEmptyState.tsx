/**
 * Dynamic Empty State Component
 * 
 * Provides persona and phase-aware empty state suggestions
 * to prevent "blank canvas paralysis"
 * 
 * @doc.type component
 * @doc.purpose Smart empty state
 * @doc.layer presentation
 */

import React from 'react';
import { Box, Button, Typography } from '@ghatana/ui';
import { Lightbulb, Code, Palette, GitBranch as AccountTree, FileText as Description } from 'lucide-react';
import type { CanvasMode } from '../../../types/canvas';
import type { LifecyclePhase } from '../../../theme/phaseTheme';

interface EmptyStateConfig {
    title: string;
    subtitle: string;
    icon: React.ReactNode;
    suggestions: Array<{
        label: string;
        action: string;
        icon: React.ReactNode;
    }>;
}

const EMPTY_STATE_CONFIG: Record<LifecyclePhase, Record<CanvasMode, EmptyStateConfig>> = {
    intent: {
        plan: {
            title: 'What problem are we solving?',
            subtitle: 'Start by defining the user need or business goal',
            icon: <Description className="text-5xl" />,
            suggestions: [
                { label: 'Write User Story', action: 'user-story', icon: <Description /> },
                { label: 'Sketch User Flow', action: 'user-flow', icon: <AccountTree /> },
                { label: 'Add Requirements', action: 'requirements', icon: <Lightbulb /> },
            ],
        },
        code: {
            title: 'Let\'s define the system',
            subtitle: 'Outline the technical architecture and components',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Create Architecture Diagram', action: 'architecture', icon: <AccountTree /> },
                { label: 'Add Tech Stack Notes', action: 'tech-stack', icon: <Code /> },
                { label: 'Define API Endpoints', action: 'api-spec', icon: <Description /> },
            ],
        },
        design: {
            title: 'Visualize the experience',
            subtitle: 'Start with wireframes or user interface concepts',
            icon: <Palette className="text-5xl" />,
            suggestions: [
                { label: 'Paste Wireframe', action: 'wireframe', icon: <Palette /> },
                { label: 'Create Frame', action: 'frame', icon: <AccountTree /> },
                { label: 'Add Design Notes', action: 'design-notes', icon: <Lightbulb /> },
            ],
        },
        brainstorm: {
            title: 'Capture your ideas',
            subtitle: 'No structure needed yet—just let creativity flow',
            icon: <Lightbulb className="text-5xl" />,
            suggestions: [
                { label: 'Add Sticky Note', action: 'sticky', icon: <Description /> },
                { label: 'Start Mind Map', action: 'mindmap', icon: <AccountTree /> },
                { label: 'Free Draw', action: 'draw', icon: <Palette /> },
            ],
        },
        diagram: {
            title: 'Map out the system',
            subtitle: 'Create visual representations of ideas and relationships',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Shape', action: 'shape', icon: <AccountTree /> },
                { label: 'Create Connector', action: 'connector', icon: <Code /> },
                { label: 'Insert Frame', action: 'frame', icon: <Palette /> },
            ],
        },
        test: {
            title: 'Plan your tests',
            subtitle: 'Define test cases and scenarios',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Add Test Case', action: 'test-case', icon: <Description /> },
                { label: 'Create Test Flow', action: 'test-flow', icon: <AccountTree /> },
                { label: 'Define Scenarios', action: 'scenarios', icon: <Lightbulb /> },
            ],
        },
        deploy: {
            title: 'Plan deployment',
            subtitle: 'Define deployment strategy and environments',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Environment', action: 'environment', icon: <AccountTree /> },
                { label: 'Define Pipeline', action: 'pipeline', icon: <Code /> },
                { label: 'Setup Config', action: 'config', icon: <Description /> },
            ],
        },
        observe: {
            title: 'Set up monitoring',
            subtitle: 'Define what metrics and logs to track',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Add Metric', action: 'metric', icon: <Code /> },
                { label: 'Define Alert', action: 'alert', icon: <Description /> },
                { label: 'Create Dashboard', action: 'dashboard', icon: <AccountTree /> },
            ],
        },
    },
    shape: {
        plan: {
            title: 'Structure your plan',
            subtitle: 'Organize requirements into actionable tasks',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Create Epic', action: 'epic', icon: <Description /> },
                { label: 'Add Roadmap', action: 'roadmap', icon: <AccountTree /> },
                { label: 'Define Milestones', action: 'milestone', icon: <Lightbulb /> },
            ],
        },
        code: {
            title: 'Design the architecture',
            subtitle: 'Define components, services, and data flow',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Service', action: 'service', icon: <Code /> },
                { label: 'Define Database', action: 'database', icon: <Description /> },
                { label: 'Create Diagram', action: 'diagram', icon: <AccountTree /> },
            ],
        },
        design: {
            title: 'Refine the design',
            subtitle: 'Add detailed layouts and component specifications',
            icon: <Palette className="text-5xl" />,
            suggestions: [
                { label: 'Create Component', action: 'component', icon: <Palette /> },
                { label: 'Add Layout', action: 'layout', icon: <AccountTree /> },
                { label: 'Define Styles', action: 'styles', icon: <Code /> },
            ],
        },
        brainstorm: {
            title: 'Organize ideas',
            subtitle: 'Group and structure your brainstorming results',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Create Frame', action: 'frame', icon: <AccountTree /> },
                { label: 'Group Notes', action: 'group', icon: <Description /> },
                { label: 'Add Categories', action: 'categories', icon: <Lightbulb /> },
            ],
        },
        diagram: {
            title: 'Build the diagram',
            subtitle: 'Create structured visual representations',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Node', action: 'node', icon: <AccountTree /> },
                { label: 'Create Connection', action: 'connection', icon: <Code /> },
                { label: 'Insert Container', action: 'container', icon: <Palette /> },
            ],
        },
        test: {
            title: 'Structure tests',
            subtitle: 'Organize test suites and coverage',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Create Test Suite', action: 'test-suite', icon: <AccountTree /> },
                { label: 'Add Test Group', action: 'test-group', icon: <Description /> },
                { label: 'Define Coverage', action: 'coverage', icon: <Code /> },
            ],
        },
        deploy: {
            title: 'Build deployment flow',
            subtitle: 'Design the deployment pipeline structure',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Stage', action: 'stage', icon: <AccountTree /> },
                { label: 'Define Gate', action: 'gate', icon: <Code /> },
                { label: 'Create Flow', action: 'flow', icon: <Description /> },
            ],
        },
        observe: {
            title: 'Design monitoring',
            subtitle: 'Structure observability architecture',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Service', action: 'service', icon: <AccountTree /> },
                { label: 'Define Metrics', action: 'metrics', icon: <Code /> },
                { label: 'Create View', action: 'view', icon: <Palette /> },
            ],
        },
    },
    // Add other phases...
    build: {
        code: {
            title: 'Time to implement',
            subtitle: 'Generate components and write code',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Generate Component', action: 'generate-component', icon: <Code /> },
                { label: 'Add Service Node', action: 'service-node', icon: <AccountTree /> },
                { label: 'Scaffold Database', action: 'database-schema', icon: <Description /> },
            ],
        },
        plan: {
            title: 'Track implementation',
            subtitle: 'Monitor build progress and tasks',
            icon: <Description className="text-5xl" />,
            suggestions: [
                { label: 'Add Task', action: 'task', icon: <Description /> },
                { label: 'Create Sprint', action: 'sprint', icon: <AccountTree /> },
                { label: 'Define Blocker', action: 'blocker', icon: <Lightbulb /> },
            ],
        },
        design: {
            title: 'Implement designs',
            subtitle: 'Build UI components from specifications',
            icon: <Palette className="text-5xl" />,
            suggestions: [
                { label: 'Create Component', action: 'component', icon: <Palette /> },
                { label: 'Add Assets', action: 'assets', icon: <Code /> },
                { label: 'Generate Code', action: 'generate', icon: <AccountTree /> },
            ],
        },
        brainstorm: {
            title: 'Quick prototypes',
            subtitle: 'Rapid experimentation during development',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Add Snippet', action: 'snippet', icon: <Code /> },
                { label: 'Test Idea', action: 'test-idea', icon: <Lightbulb /> },
                { label: 'Create Prototype', action: 'prototype', icon: <Palette /> },
            ],
        },
        diagram: {
            title: 'Implementation view',
            subtitle: 'Visualize code structure as you build',
            icon: <AccountTree className="text-5xl" />,
            suggestions: [
                { label: 'Add Module', action: 'module', icon: <AccountTree /> },
                { label: 'Show Dependencies', action: 'dependencies', icon: <Code /> },
                { label: 'Create Graph', action: 'graph', icon: <Description /> },
            ],
        },
        test: {
            title: 'Write tests',
            subtitle: 'Create test cases as you develop',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Add Unit Test', action: 'unit-test', icon: <Code /> },
                { label: 'Create Integration Test', action: 'integration-test', icon: <AccountTree /> },
                { label: 'Generate Test', action: 'generate-test', icon: <Lightbulb /> },
            ],
        },
        deploy: {
            title: 'Build artifacts',
            subtitle: 'Prepare deployment packages',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Create Build', action: 'build', icon: <Code /> },
                { label: 'Package App', action: 'package', icon: <AccountTree /> },
                { label: 'Generate Config', action: 'config', icon: <Description /> },
            ],
        },
        observe: {
            title: 'Add instrumentation',
            subtitle: 'Implement logging and monitoring',
            icon: <Code className="text-5xl" />,
            suggestions: [
                { label: 'Add Logger', action: 'logger', icon: <Code /> },
                { label: 'Implement Trace', action: 'trace', icon: <AccountTree /> },
                { label: 'Create Metric', action: 'metric', icon: <Description /> },
            ],
        },
    },
    // Minimal configs for other phases
    validate: {} as unknown,
    generate: {} as unknown,
    run: {} as unknown,
    observe: {} as unknown,
    improve: {} as unknown,
};

interface DynamicEmptyStateProps {
    phase: LifecyclePhase;
    mode: CanvasMode;
    onAction: (action: string) => void;
}

export const DynamicEmptyState: React.FC<DynamicEmptyStateProps> = ({
    phase,
    mode,
    onAction,
}) => {
    const config = EMPTY_STATE_CONFIG[phase]?.[mode] || {
        title: 'Start Your Journey',
        subtitle: 'Select a tool from the toolbar or drop a file here',
        icon: <Lightbulb className="text-5xl" />,
        suggestions: [
            { label: 'Create Frame', action: 'frame', icon: <AccountTree /> },
            { label: 'Add Text', action: 'text', icon: <Description /> },
        ],
    };

    return (
        <Box
            className="absolute text-center pointer-events-none top-[50%] left-[50%] z-[5] max-w-[500px]" style={{ transform: 'translate(-50%' }} >
            <Box className="mb-4 opacity-[0.3]">
                {config.icon}
            </Box>
            <Typography variant="h4" gutterBottom color="text.disabled" className="font-light">
                {config.title}
            </Typography>
            <Typography variant="body1" color="text.disabled" className="mb-6">
                {config.subtitle}
            </Typography>
            <Box className="flex gap-4 justify-center flex-wrap pointer-events-auto">
                {config.suggestions.map((suggestion) => (
                    <Button
                        key={suggestion.action}
                        variant="outlined"
                        startIcon={suggestion.icon}
                        onClick={() => onAction(suggestion.action)}
                        className="min-w-[140px]"
                    >
                        {suggestion.label}
                    </Button>
                ))}
            </Box>
        </Box>
    );
};
