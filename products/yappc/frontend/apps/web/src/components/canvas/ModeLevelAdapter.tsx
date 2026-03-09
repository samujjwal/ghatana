/**
 * Mode × Level Content Adapter
 * 
 * Maps 7 modes × 4 levels = 28 canvas states to appropriate content,
 * tools, and empty states per CANVAS_TOOLBAR_UI_UX_SPECIFICATION.md §7.
 * 
 * @doc.type component
 * @doc.purpose Canvas content adaptation based on mode and level
 * @doc.layer product
 * @doc.pattern Adapter/Strategy Pattern
 */

import React from 'react';
import { Box, Stack, Typography } from '@ghatana/ui';
import { Lightbulb, GitBranch as AccountTree, Palette, Code, Bug as BugReport, Rocket as RocketLaunch, Eye as Visibility, Sparkles as AutoAwesome } from 'lucide-react';

import type { CanvasMode } from '../../types/canvasMode';
import type { AbstractionLevel } from '../../types/canvas';

// ============================================================================
// Types
// ============================================================================

export interface ModeLevelState {
    /** Description of what this state shows */
    description: string;
    /** Available tools for this state */
    tools: string[];
    /** Empty state message when no content exists */
    emptyMessage: string;
    /** AI assistant suggestion for this state */
    aiSuggestion: string;
    /** Icon for the state */
    icon: React.ReactNode;
}

export interface ModeLevelAdapterProps {
    /** Current canvas mode */
    mode: CanvasMode;
    /** Current abstraction level */
    level: AbstractionLevel;
    /** Whether canvas has content */
    hasContent?: boolean;
    /** Handler for AI assistance */
    onAskAI?: () => void;
    /** Handler for getting started */
    onGetStarted?: () => void;
    /** Children to render when content exists */
    children?: React.ReactNode;
}

// ============================================================================
// Mode × Level Matrix (28 States)
// Per CANVAS_TOOLBAR_UI_UX_SPECIFICATION.md §7.1
// ============================================================================

const MODE_LEVEL_MATRIX: Record<CanvasMode, Record<AbstractionLevel, ModeLevelState>> = {
    // ========================================================================
    // BRAINSTORM MODE
    // ========================================================================
    brainstorm: {
        system: {
            description: 'Mind map of systems',
            tools: ['Freehand draw', 'Text', 'Shapes'],
            emptyMessage: 'Start with a big idea',
            aiSuggestion: 'Suggest Analogies: "Like Uber for X..."',
            icon: <Lightbulb className="text-[#FFB300]" />,
        },
        component: {
            description: 'Sticky notes grid',
            tools: ['Sticky notes', 'Grouping'],
            emptyMessage: 'Add components to explore',
            aiSuggestion: 'Break down into components',
            icon: <Lightbulb className="text-[#FFB300]" />,
        },
        file: {
            description: 'File-specific notes',
            tools: ['Annotations', 'Comments'],
            emptyMessage: 'Select a file to annotate',
            aiSuggestion: 'Add notes to files',
            icon: <Lightbulb className="text-[#FFB300]" />,
        },
        code: {
            description: 'Pseudocode blocks',
            tools: ['Text editor', 'Formatting'],
            emptyMessage: 'Write pseudocode here',
            aiSuggestion: 'Convert to real code',
            icon: <Lightbulb className="text-[#FFB300]" />,
        },
    },

    // ========================================================================
    // DIAGRAM MODE
    // ========================================================================
    diagram: {
        system: {
            description: 'System architecture',
            tools: ['Boxes', 'Arrows', 'Databases', 'Clouds'],
            emptyMessage: 'Add your first service',
            aiSuggestion: 'Auto-Connect: "Connect Auth to DB?"',
            icon: <AccountTree className="text-[#4CAF50]" />,
        },
        component: {
            description: 'Component relationships',
            tools: ['Component nodes', 'Data flows'],
            emptyMessage: 'Drag components to canvas',
            aiSuggestion: 'Suggest relationships',
            icon: <AccountTree className="text-[#4CAF50]" />,
        },
        file: {
            description: 'Class/module diagram',
            tools: ['Classes', 'Interfaces', 'Inheritance'],
            emptyMessage: 'Select components to see structure',
            aiSuggestion: 'Generate class diagram',
            icon: <AccountTree className="text-[#4CAF50]" />,
        },
        code: {
            description: 'Sequence diagram',
            tools: ['Actors', 'Messages', 'Lifelines'],
            emptyMessage: 'Drill into a function to see flow',
            aiSuggestion: 'Generate sequence from code',
            icon: <AccountTree className="text-[#4CAF50]" />,
        },
    },

    // ========================================================================
    // DESIGN MODE
    // ========================================================================
    design: {
        system: {
            description: 'Design system overview',
            tools: ['Theme', 'Colors', 'Typography'],
            emptyMessage: 'Define your design language',
            aiSuggestion: 'Generative UI: "Generate Dashboard layout"',
            icon: <Palette className="text-[#E91E63]" />,
        },
        component: {
            description: 'Page wireframes',
            tools: ['Page templates', 'Layouts'],
            emptyMessage: 'Create your first page',
            aiSuggestion: 'Generate page layout',
            icon: <Palette className="text-[#E91E63]" />,
        },
        file: {
            description: 'Component specifications',
            tools: ['Props', 'Variants', 'States'],
            emptyMessage: 'Design component details',
            aiSuggestion: 'Generate component variants',
            icon: <Palette className="text-[#E91E63]" />,
        },
        code: {
            description: 'Style tokens editor',
            tools: ['CSS/tokens editor'],
            emptyMessage: 'Edit component styles',
            aiSuggestion: 'Generate CSS from design',
            icon: <Palette className="text-[#E91E63]" />,
        },
    },

    // ========================================================================
    // CODE MODE
    // ========================================================================
    code: {
        system: {
            description: 'Service topology',
            tools: ['API endpoints', 'Connections'],
            emptyMessage: 'Map your services',
            aiSuggestion: 'Copilot: Complete full functions',
            icon: <Code className="text-[#2196F3]" />,
        },
        component: {
            description: 'Module dependency graph',
            tools: ['Imports', 'Exports visual'],
            emptyMessage: 'View module structure',
            aiSuggestion: 'Suggest module organization',
            icon: <Code className="text-[#2196F3]" />,
        },
        file: {
            description: 'File explorer + preview',
            tools: ['File tree', 'Quick preview'],
            emptyMessage: 'Browse project files',
            aiSuggestion: 'Generate file structure',
            icon: <Code className="text-[#2196F3]" />,
        },
        code: {
            description: 'Monaco code editor',
            tools: ['Full IDE features'],
            emptyMessage: 'Select a file to edit',
            aiSuggestion: 'Complete code with AI',
            icon: <Code className="text-[#2196F3]" />,
        },
    },

    // ========================================================================
    // TEST MODE (🐛)
    // ========================================================================
    test: {
        system: {
            description: 'E2E test coverage map',
            tools: ['Test suites', 'Scenarios'],
            emptyMessage: 'Plan integration tests',
            aiSuggestion: 'Gen-Test: Write missing test cases',
            icon: <BugReport className="text-[#FF5722]" />,
        },
        component: {
            description: 'Unit test coverage',
            tools: ['Coverage badges', 'Gaps'],
            emptyMessage: 'View test coverage',
            aiSuggestion: 'Generate unit tests',
            icon: <BugReport className="text-[#FF5722]" />,
        },
        file: {
            description: 'Test file list',
            tools: ['Test results', 'Status'],
            emptyMessage: 'See test files',
            aiSuggestion: 'Generate test file',
            icon: <BugReport className="text-[#FF5722]" />,
        },
        code: {
            description: 'Test code editor',
            tools: ['Test runner', 'Assertions'],
            emptyMessage: 'Write test cases',
            aiSuggestion: 'Complete test assertions',
            icon: <BugReport className="text-[#FF5722]" />,
        },
    },

    // ========================================================================
    // DEPLOY MODE (🚀)
    // ========================================================================
    deploy: {
        system: {
            description: 'Infrastructure diagram',
            tools: ['AWS/GCP/Azure resources'],
            emptyMessage: 'Design infrastructure',
            aiSuggestion: 'Auto-Examine: Find security holes',
            icon: <RocketLaunch className="text-[#9C27B0]" />,
        },
        component: {
            description: 'Container orchestration',
            tools: ['Pods', 'Services', 'Volumes'],
            emptyMessage: 'Configure containers',
            aiSuggestion: 'Optimize container config',
            icon: <RocketLaunch className="text-[#9C27B0]" />,
        },
        file: {
            description: 'Config file browser',
            tools: ['Dockerfile', 'k8s YAML'],
            emptyMessage: 'View config files',
            aiSuggestion: 'Generate Dockerfile',
            icon: <RocketLaunch className="text-[#9C27B0]" />,
        },
        code: {
            description: 'Config editor',
            tools: ['YAML/JSON validation'],
            emptyMessage: 'Edit deployment config',
            aiSuggestion: 'Validate YAML syntax',
            icon: <RocketLaunch className="text-[#9C27B0]" />,
        },
    },

    // ========================================================================
    // OBSERVE MODE (👁️)
    // ========================================================================
    observe: {
        system: {
            description: 'Health dashboard',
            tools: ['Status', 'Uptime', 'Alerts'],
            emptyMessage: 'Connect monitoring',
            aiSuggestion: 'Root Cause: "Why 500 error?"',
            icon: <Visibility className="text-[#00BCD4]" />,
        },
        component: {
            description: 'Component metrics',
            tools: ['Latency', 'Throughput'],
            emptyMessage: 'View component health',
            aiSuggestion: 'Analyze performance',
            icon: <Visibility className="text-[#00BCD4]" />,
        },
        file: {
            description: 'Log streams',
            tools: ['Log viewer', 'Filters'],
            emptyMessage: 'View log files',
            aiSuggestion: 'Search logs for errors',
            icon: <Visibility className="text-[#00BCD4]" />,
        },
        code: {
            description: 'Trace details',
            tools: ['Span viewer', 'Timeline'],
            emptyMessage: 'View execution traces',
            aiSuggestion: 'Find bottlenecks',
            icon: <Visibility className="text-[#00BCD4]" />,
        },
    },
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get the state configuration for a mode/level combination
 */
export function getModeLevelState(mode: CanvasMode, level: AbstractionLevel): ModeLevelState {
    return MODE_LEVEL_MATRIX[mode]?.[level] ?? {
        description: 'Unknown state',
        tools: [],
        emptyMessage: 'Select a mode and level',
        aiSuggestion: 'Ask AI for help',
        icon: <AutoAwesome />,
    };
}

/**
 * Get all available tools for a mode/level combination
 */
export function getAvailableTools(mode: CanvasMode, level: AbstractionLevel): string[] {
    return getModeLevelState(mode, level).tools;
}

/**
 * Get the empty state message for a mode/level combination
 */
export function getEmptyStateMessage(mode: CanvasMode, level: AbstractionLevel): string {
    return getModeLevelState(mode, level).emptyMessage;
}

// ============================================================================
// Components
// ============================================================================

/**
 * Mode×Level Empty State Component
 * 
 * Displays contextual empty state based on current mode and level
 */
export const ModeLevelEmptyState: React.FC<{
    mode: CanvasMode;
    level: AbstractionLevel;
    onAskAI?: () => void;
    onGetStarted?: () => void;
}> = ({ mode, level, onAskAI, onGetStarted }) => {
    const state = getModeLevelState(mode, level);

    return (
        <Box
            className="flex flex-col items-center justify-center h-full p-8 min-h-[300px]"
        >
            <Stack spacing={3} alignItems="center" className="text-center max-w-[400px]">
                {/* Icon */}
                <Box
                    className="w-[80px] h-[80px] rounded-full flex items-center justify-center bg-gray-100" >
                    {state.icon}
                </Box>

                {/* Message */}
                <Box>
                    <Typography variant="h6" gutterBottom>
                        {state.emptyMessage}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {state.description}
                    </Typography>
                </Box>

                {/* Available Tools */}
                <Box className="flex flex-wrap gap-2 justify-center">
                    {state.tools.map((tool) => (
                        <Box
                            key={tool}
                            className="px-4 py-1 rounded text-xs bg-gray-200" >
                            {tool}
                        </Box>
                    ))}
                </Box>

                {/* AI Suggestion */}
                {onAskAI && (
                    <Box
                        onClick={onAskAI}
                        className="flex items-center gap-2 px-6 py-3 rounded-lg bg-blue-600 text-white cursor-pointer transition-all duration-200 translate-y-[-1px] bg-blue-800" >
                        <AutoAwesome className="text-xl" />
                        <Typography variant="body2" fontWeight={500}>
                            {state.aiSuggestion}
                        </Typography>
                    </Box>
                )}

                {/* Get Started Button */}
                {onGetStarted && (
                    <Box
                        onClick={onGetStarted}
                        className="px-6 py-2 rounded-lg border border-solid border-gray-200 dark:border-gray-700 cursor-pointer transition-all duration-200 hover:border-blue-600"
                    >
                        <Typography variant="body2">Get Started</Typography>
                    </Box>
                )}
            </Stack>
        </Box>
    );
};

/**
 * Mode×Level Content Adapter
 * 
 * Wraps canvas content and provides appropriate empty states
 * based on the current mode/level combination.
 * 
 * @example
 * ```tsx
 * <ModeLevelAdapter
 *   mode={currentMode}
 *   level={currentLevel}
 *   hasContent={nodes.length > 0}
 *   onAskAI={handleAskAI}
 * >
 *   <CanvasContent />
 * </ModeLevelAdapter>
 * ```
 */
export const ModeLevelAdapter: React.FC<ModeLevelAdapterProps> = ({
    mode,
    level,
    hasContent = false,
    onAskAI,
    onGetStarted,
    children,
}) => {
    // If there's content, render children
    if (hasContent) {
        return <>{children}</>;
    }

    // Otherwise, show contextual empty state
    return (
        <ModeLevelEmptyState
            mode={mode}
            level={level}
            onAskAI={onAskAI}
            onGetStarted={onGetStarted}
        />
    );
};

export default ModeLevelAdapter;
