import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { LifecycleProgressRail } from '../LifecycleProgressRail';
import { WorkflowContextPanel } from '../WorkflowContextPanel';

// ─── Shared fixtures ──────────────────────────────────────────────────────────

type LifecycleStage = 'intent' | 'context' | 'plan' | 'execute' | 'verify' | 'observe' | 'learn' | 'institutionalize';

function makeWorkflow(overrides: {
    status?: 'pending' | 'running' | 'paused' | 'completed' | 'cancelled';
    phaseName?: string;
    completedStepCount?: number;
    totalStepCount?: number;
} = {}) {
    const { status = 'running', phaseName = 'Phase One', completedStepCount = 2, totalStepCount = 4 } = overrides;
    const steps = Array.from({ length: totalStepCount }, (_, i) => ({
        id: `step-${i}`,
        taskId: `task-${i}`,
        status: i < completedStepCount ? 'completed' : 'pending',
        completedAt: i < completedStepCount ? '2024-01-15T10:00:00Z' : undefined,
    }));

    return {
        id: 'wf-1',
        templateId: 'tmpl-1',
        status,
        createdAt: '2024-01-15T08:00:00Z',
        phases: [{ id: 'phase-1', name: phaseName, status: 'running', steps }],
        currentPhaseIndex: 0,
    };
}

const completedTask = {
    id: 'ct-1',
    taskId: 'design-system-review',
    status: 'completed',
    completedAt: '2024-01-15T09:00:00Z',
};

// ─── WorkflowContextPanel ─────────────────────────────────────────────────────

describe('WorkflowContextPanel', () => {
    describe('null state', () => {
        it('shows "No workflow selected" when workflow is null', () => {
            render(<WorkflowContextPanel workflow={null} completedTasks={[]} />);
            expect(screen.getByText(/no workflow selected/i)).toBeInTheDocument();
        });

        it('does not render header when workflow is null', () => {
            render(<WorkflowContextPanel workflow={null} completedTasks={[]} />);
            expect(screen.queryByText(/workflow context/i)).not.toBeInTheDocument();
        });
    });

    describe('header', () => {
        it('shows "Workflow Context" heading', () => {
            render(<WorkflowContextPanel workflow={makeWorkflow()} completedTasks={[]} />);
            expect(screen.getByText('Workflow Context')).toBeInTheDocument();
        });

        it('shows close button when onClose is provided', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow()}
                    completedTasks={[]}
                    onClose={vi.fn()}
                />,
            );
            // Close button exists (IconButton renders a button)
            const buttons = screen.getAllByRole('button');
            expect(buttons.length).toBeGreaterThan(0);
        });

        it('calls onClose when close button is clicked', () => {
            const onClose = vi.fn();
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow()}
                    completedTasks={[]}
                    onClose={onClose}
                />,
            );
            // Close button is the first button in the header (IconButton)
            const buttons = screen.getAllByRole('button');
            fireEvent.click(buttons[0]);
            expect(onClose).toHaveBeenCalledTimes(1);
        });
    });

    describe('progress', () => {
        it('shows task completion count', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow({ completedStepCount: 2, totalStepCount: 4 })}
                    completedTasks={[]}
                />,
            );
            expect(screen.getByText(/2 of 4 tasks completed/i)).toBeInTheDocument();
        });

        it('shows 0% progress when no steps completed', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow({ completedStepCount: 0, totalStepCount: 3 })}
                    completedTasks={[]}
                />,
            );
            expect(screen.getByText(/0 of 3 tasks completed/i)).toBeInTheDocument();
        });

        it('shows 100% progress when all steps completed', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow({ completedStepCount: 3, totalStepCount: 3 })}
                    completedTasks={[]}
                />,
            );
            expect(screen.getByText(/3 of 3 tasks completed/i)).toBeInTheDocument();
        });

        it('shows progress percentage chip', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow({ completedStepCount: 2, totalStepCount: 4 })}
                    completedTasks={[]}
                />,
            );
            expect(screen.getByText('50%')).toBeInTheDocument();
        });
    });

    describe('tabs', () => {
        it('shows Tasks tab', () => {
            render(<WorkflowContextPanel workflow={makeWorkflow()} completedTasks={[]} />);
            expect(screen.getByRole('tab', { name: /tasks/i })).toBeInTheDocument();
        });

        it('shows Artifacts tab', () => {
            render(<WorkflowContextPanel workflow={makeWorkflow()} completedTasks={[]} />);
            expect(screen.getByRole('tab', { name: /artifacts/i })).toBeInTheDocument();
        });
    });

    describe('completed tasks', () => {
        it('shows "No tasks completed yet" when completedTasks is empty', () => {
            render(<WorkflowContextPanel workflow={makeWorkflow()} completedTasks={[]} />);
            expect(screen.getByText(/no tasks completed yet/i)).toBeInTheDocument();
        });

        it('shows task name when tasks are completed', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow()}
                    completedTasks={[completedTask]}
                />,
            );
            // Task name is formatted from taskId: 'design-system-review' → 'Design System Review'
            expect(screen.getByText('Design System Review')).toBeInTheDocument();
        });

        it('groups tasks by domain prefix', () => {
            const tasks = [
                { ...completedTask, id: 'ct-1', taskId: 'design-review' },
                { ...completedTask, id: 'ct-2', taskId: 'design-spec' },
            ];
            render(<WorkflowContextPanel workflow={makeWorkflow()} completedTasks={tasks} />);
            // Both tasks share 'design' domain
            expect(screen.getByText('Design')).toBeInTheDocument();
        });
    });

    describe('footer', () => {
        it('shows started date in footer', () => {
            render(<WorkflowContextPanel workflow={makeWorkflow()} completedTasks={[]} />);
            // createdAt is '2024-01-15T08:00:00Z' → localeDate depends on locale but 'Started' label is shown
            expect(screen.getByText(/started/i)).toBeInTheDocument();
        });

        it('shows current phase name in footer', () => {
            render(
                <WorkflowContextPanel
                    workflow={makeWorkflow({ phaseName: 'Implementation' })}
                    completedTasks={[]}
                />,
            );
            expect(screen.getByText(/implementation/i)).toBeInTheDocument();
        });
    });
});

// ─── LifecycleProgressRail ────────────────────────────────────────────────────

describe('LifecycleProgressRail', () => {
    const ALL_STAGES: LifecycleStage[] = [
        'intent', 'context', 'plan', 'execute', 'verify', 'observe', 'learn', 'institutionalize',
    ];

    describe('group labels', () => {
        it('shows Planning group label', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            expect(screen.getByText('Planning')).toBeInTheDocument();
        });

        it('shows Building group label', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            expect(screen.getByText('Building')).toBeInTheDocument();
        });

        it('shows Operating group label', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            expect(screen.getByText('Operating')).toBeInTheDocument();
        });

        it('shows Learning group label', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            expect(screen.getByText('Learning')).toBeInTheDocument();
        });
    });

    describe('stage names', () => {
        it('renders all 8 lifecycle stage names', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            const stageNames = ['Intent', 'Context', 'Plan', 'Execute', 'Verify', 'Observe', 'Learn', 'Institutionalize'];
            stageNames.forEach((name) => {
                expect(screen.getByText(name)).toBeInTheDocument();
            });
        });
    });

    describe('overall progress', () => {
        it('shows "0 of 8 stages complete" initially', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            expect(screen.getByText(/0 of 8 stages complete/i)).toBeInTheDocument();
        });

        it('shows completed stage count', () => {
            render(
                <LifecycleProgressRail
                    currentStage="plan"
                    completedStages={['intent', 'context']}
                />,
            );
            expect(screen.getByText(/2 of 8 stages complete/i)).toBeInTheDocument();
        });

        it('shows "Overall Progress" label', () => {
            render(<LifecycleProgressRail currentStage={null} completedStages={[]} />);
            expect(screen.getByText(/overall progress/i)).toBeInTheDocument();
        });
    });

    describe('stage selection', () => {
        it('calls onStageSelect when an available stage is clicked', () => {
            const onStageSelect = vi.fn();
            render(
                <LifecycleProgressRail
                    currentStage={null}
                    completedStages={[]}
                    onStageSelect={onStageSelect}
                />,
            );
            // Intent is always available (first stage, no prior required)
            fireEvent.click(screen.getByText('Intent'));
            expect(onStageSelect).toHaveBeenCalledWith('intent');
        });

        it('does not call onStageSelect when global disabled is true', () => {
            const onStageSelect = vi.fn();
            render(
                <LifecycleProgressRail
                    currentStage={null}
                    completedStages={[]}
                    onStageSelect={onStageSelect}
                    disabled
                />,
            );
            fireEvent.click(screen.getByText('Intent'));
            expect(onStageSelect).not.toHaveBeenCalled();
        });

        it('calls onStageSelect with correct stage id when context stage clicked (after intent completed)', () => {
            const onStageSelect = vi.fn();
            render(
                <LifecycleProgressRail
                    currentStage="context"
                    completedStages={['intent']}
                    onStageSelect={onStageSelect}
                />,
            );
            fireEvent.click(screen.getByText('Context'));
            expect(onStageSelect).toHaveBeenCalledWith('context');
        });
    });

    describe('stage task counts', () => {
        it('shows task count for a stage when stageTasks is provided', () => {
            render(
                <LifecycleProgressRail
                    currentStage="intent"
                    completedStages={[]}
                    stageTasks={{ intent: { total: 5, completed: 3 } }}
                />,
            );
            expect(screen.getByText('3/5 tasks')).toBeInTheDocument();
        });
    });

    describe('stage progress chip', () => {
        it('shows progress chip for stages with partial progress', () => {
            render(
                <LifecycleProgressRail
                    currentStage="intent"
                    completedStages={[]}
                    stageProgress={{ intent: 60 }}
                />,
            );
            expect(screen.getByText('60%')).toBeInTheDocument();
        });

        it('does not show progress chip when progress is 0', () => {
            render(
                <LifecycleProgressRail
                    currentStage="intent"
                    completedStages={[]}
                    stageProgress={{ intent: 0 }}
                />,
            );
            expect(screen.queryByText('0%')).not.toBeInTheDocument();
        });

        it('does not show progress chip when progress is 100', () => {
            render(
                <LifecycleProgressRail
                    currentStage="intent"
                    completedStages={[]}
                    stageProgress={{ intent: 100 }}
                />,
            );
            expect(screen.queryByText('100%')).not.toBeInTheDocument();
        });
    });
});
