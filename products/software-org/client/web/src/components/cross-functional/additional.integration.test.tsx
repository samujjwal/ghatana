/**
 * Additional Cross-Functional Features Integration Tests
 *
 * Comprehensive test suite for additional cross-functional components:
 * - ResourceAllocation
 * - DependencyTracker
 * - GoalTracker
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
    ResourceAllocation,
    mockResourceAllocationData,
    type ResourceMetrics,
    type TeamResource,
    type IndividualResource,
    type SkillDistribution,
    type ResourceRequest,
} from './ResourceAllocation';
import {
    DependencyTracker,
    mockDependencyTrackerData,
    type DependencyMetrics,
    type CrossTeamDependency,
    type Blocker,
    type TeamCoordination,
    type DependencyActivity,
} from './DependencyTracker';
import {
    GoalTracker,
    mockGoalTrackerData,
    type GoalMetrics,
    type OrganizationalGoal,
    type KeyResult,
    type TeamContribution,
    type GoalActivity,
} from './GoalTracker';

/**
 * ResourceAllocation Tests
 */
describe('ResourceAllocation', () => {
    describe('Component Rendering', () => {
        it('renders resource allocation with metrics', () => {
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                />
            );

            expect(screen.getByText('Resource Allocation')).toBeInTheDocument();
            expect(screen.getByText('Total Resources')).toBeInTheDocument();
            expect(screen.getByText('145')).toBeInTheDocument();
            expect(screen.getByText('Utilization Rate')).toBeInTheDocument();
        });

        it('renders all 4 tabs', () => {
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                />
            );

            expect(screen.getByRole('tab', { name: /Teams/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /People/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Skills/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Requests/i })).toBeInTheDocument();
        });

        it('displays allocate resource button', () => {
            const onAllocateResource = vi.fn();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                    onAllocateResource={onAllocateResource}
                />
            );

            expect(screen.getByRole('button', { name: /Allocate Resource/i })).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to People tab and shows availability filter', async () => {
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                />
            );

            await user.click(screen.getByRole('tab', { name: /People/i }));

            await waitFor(() => {
                expect(screen.getByText(/Available \(/i)).toBeInTheDocument();
            });
        });

        it('switches to Skills tab and displays skill distribution', async () => {
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Skills/i }));

            await waitFor(() => {
                expect(screen.getByText('Java Development')).toBeInTheDocument();
            });
        });

        it('switches to Requests tab and displays resource requests', async () => {
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Requests/i }));

            await waitFor(() => {
                expect(screen.getByText('AI Analytics Platform')).toBeInTheDocument();
            });
        });
    });

    describe('Team Capacity Filtering', () => {
        it('filters teams by capacity', async () => {
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                />
            );

            await waitFor(async () => {
                const overCapacityFilter = screen.getByText(/Over Capacity \(/i);
                await user.click(overCapacityFilter);

                await waitFor(() => {
                    expect(screen.getByText('Data Science')).toBeInTheDocument();
                });
            });
        });
    });

    describe('Callback Interactions', () => {
        it('calls onTeamClick when team is clicked', async () => {
            const onTeamClick = vi.fn();
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                    onTeamClick={onTeamClick}
                />
            );

            const teamCard = screen.getByText('Platform Engineering').closest('div[class*="cursor-pointer"]');
            await user.click(teamCard!);

            expect(onTeamClick).toHaveBeenCalledWith('team-1');
        });

        it('calls onAllocateResource when allocate button is clicked', async () => {
            const onAllocateResource = vi.fn();
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                    onAllocateResource={onAllocateResource}
                />
            );

            await user.click(screen.getByRole('button', { name: /Allocate Resource/i }));

            expect(onAllocateResource).toHaveBeenCalled();
        });

        it('calls onResourceClick when person is clicked', async () => {
            const onResourceClick = vi.fn();
            const user = userEvent.setup();
            render(
                <ResourceAllocation
                    metrics={mockResourceAllocationData.metrics}
                    teamResources={mockResourceAllocationData.teamResources}
                    individualResources={mockResourceAllocationData.individualResources}
                    skillDistribution={mockResourceAllocationData.skillDistribution}
                    resourceRequests={mockResourceAllocationData.resourceRequests}
                    onResourceClick={onResourceClick}
                />
            );

            await user.click(screen.getByRole('tab', { name: /People/i }));

            await waitFor(async () => {
                const personCard = screen.getByText('Sarah Chen').closest('div[class*="cursor-pointer"]');
                await user.click(personCard!);

                expect(onResourceClick).toHaveBeenCalledWith('res-1');
            });
        });
    });
});

/**
 * DependencyTracker Tests
 */
describe('DependencyTracker', () => {
    describe('Component Rendering', () => {
        it('renders dependency tracker with metrics', () => {
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                />
            );

            expect(screen.getByText('Dependency Tracker')).toBeInTheDocument();
            expect(screen.getByText('Total Dependencies')).toBeInTheDocument();
            expect(screen.getByText('24')).toBeInTheDocument();
        });

        it('renders all 4 tabs', () => {
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                />
            );

            expect(screen.getByRole('tab', { name: /Dependencies/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Blockers/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Coordination/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Activity/i })).toBeInTheDocument();
        });

        it('displays create dependency button', () => {
            const onCreateDependency = vi.fn();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                    onCreateDependency={onCreateDependency}
                />
            );

            expect(screen.getByRole('button', { name: /Create Dependency/i })).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to Blockers tab and displays blockers', async () => {
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Blockers/i }));

            await waitFor(() => {
                expect(screen.getByText('Mobile App Release')).toBeInTheDocument();
            });
        });

        it('switches to Coordination tab and displays team coordination', async () => {
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Coordination/i }));

            await waitFor(() => {
                expect(screen.getByText('Sync Meeting')).toBeInTheDocument();
            });
        });

        it('switches to Activity tab and displays activities', async () => {
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Activity/i }));

            await waitFor(() => {
                expect(screen.getByText('Dependency blocked due to resource unavailability')).toBeInTheDocument();
            });
        });
    });

    describe('Dependency Status Filtering', () => {
        it('filters dependencies by status', async () => {
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                />
            );

            const blockedFilter = screen.getByText(/Blocked \(/i);
            await user.click(blockedFilter);

            await waitFor(() => {
                expect(screen.getByText('App Redesign Mockups')).toBeInTheDocument();
            });
        });
    });

    describe('Callback Interactions', () => {
        it('calls onDependencyClick when dependency is clicked', async () => {
            const onDependencyClick = vi.fn();
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                    onDependencyClick={onDependencyClick}
                />
            );

            const dependencyRow = screen.getByText('User Authentication API').closest('tr');
            await user.click(dependencyRow!);

            expect(onDependencyClick).toHaveBeenCalledWith('dep-1');
        });

        it('calls onCreateDependency when create button is clicked', async () => {
            const onCreateDependency = vi.fn();
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                    onCreateDependency={onCreateDependency}
                />
            );

            await user.click(screen.getByRole('button', { name: /Create Dependency/i }));

            expect(onCreateDependency).toHaveBeenCalled();
        });

        it('calls onBlockerClick when blocker is clicked', async () => {
            const onBlockerClick = vi.fn();
            const user = userEvent.setup();
            render(
                <DependencyTracker
                    metrics={mockDependencyTrackerData.metrics}
                    dependencies={mockDependencyTrackerData.dependencies}
                    blockers={mockDependencyTrackerData.blockers}
                    coordination={mockDependencyTrackerData.coordination}
                    activities={mockDependencyTrackerData.activities}
                    onBlockerClick={onBlockerClick}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Blockers/i }));

            await waitFor(async () => {
                const blockerCard = screen.getByText('Mobile App Release').closest('div[class*="cursor-pointer"]');
                await user.click(blockerCard!);

                expect(onBlockerClick).toHaveBeenCalledWith('blocker-1');
            });
        });
    });
});

/**
 * GoalTracker Tests
 */
describe('GoalTracker', () => {
    describe('Component Rendering', () => {
        it('renders goal tracker with metrics', () => {
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            expect(screen.getByText('Goal Tracker')).toBeInTheDocument();
            expect(screen.getByText('Total Goals')).toBeInTheDocument();
            expect(screen.getByText('18')).toBeInTheDocument();
        });

        it('renders all 4 tabs', () => {
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            expect(screen.getByRole('tab', { name: /Goals/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Key Results/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Teams/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Activity/i })).toBeInTheDocument();
        });

        it('displays create goal button', () => {
            const onCreateGoal = vi.fn();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                    onCreateGoal={onCreateGoal}
                />
            );

            expect(screen.getByRole('button', { name: /Create Goal/i })).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to Key Results tab and displays key results', async () => {
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Key Results/i }));

            await waitFor(() => {
                expect(screen.getByText('Reach 50,000 active users')).toBeInTheDocument();
            });
        });

        it('switches to Teams tab and displays team contributions', async () => {
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Teams/i }));

            await waitFor(() => {
                expect(screen.getByText('Platform Engineering')).toBeInTheDocument();
            });
        });

        it('switches to Activity tab and displays activities', async () => {
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Activity/i }));

            await waitFor(() => {
                expect(screen.getByText('Progress updated for Q4 goal')).toBeInTheDocument();
            });
        });
    });

    describe('Goal Filtering', () => {
        it('filters goals by status', async () => {
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            const atRiskFilter = screen.getByText(/At Risk \(/i);
            await user.click(atRiskFilter);

            await waitFor(() => {
                expect(screen.getByText('Improve System Performance')).toBeInTheDocument();
            });
        });

        it('filters goals by level', async () => {
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                />
            );

            const companyFilter = screen.getByText('Company');
            await user.click(companyFilter);

            await waitFor(() => {
                expect(screen.getByText('Increase Platform Adoption')).toBeInTheDocument();
            });
        });
    });

    describe('Callback Interactions', () => {
        it('calls onGoalClick when goal is clicked', async () => {
            const onGoalClick = vi.fn();
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                    onGoalClick={onGoalClick}
                />
            );

            const goalCard = screen.getByText('Increase Platform Adoption').closest('div[class*="cursor-pointer"]');
            await user.click(goalCard!);

            expect(onGoalClick).toHaveBeenCalledWith('goal-1');
        });

        it('calls onCreateGoal when create button is clicked', async () => {
            const onCreateGoal = vi.fn();
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                    onCreateGoal={onCreateGoal}
                />
            );

            await user.click(screen.getByRole('button', { name: /Create Goal/i }));

            expect(onCreateGoal).toHaveBeenCalled();
        });

        it('calls onKeyResultClick when key result is clicked', async () => {
            const onKeyResultClick = vi.fn();
            const user = userEvent.setup();
            render(
                <GoalTracker
                    metrics={mockGoalTrackerData.metrics}
                    goals={mockGoalTrackerData.goals}
                    keyResults={mockGoalTrackerData.keyResults}
                    teamContributions={mockGoalTrackerData.teamContributions}
                    activities={mockGoalTrackerData.activities}
                    onKeyResultClick={onKeyResultClick}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Key Results/i }));

            await waitFor(async () => {
                const krCard = screen.getByText('Reach 50,000 active users').closest('div[class*="cursor-pointer"]');
                await user.click(krCard!);

                expect(onKeyResultClick).toHaveBeenCalledWith('kr-1');
            });
        });
    });
});
