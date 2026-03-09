/**
 * Cross-Functional Features Integration Tests
 *
 * Comprehensive test suite for all cross-functional components:
 * - CrossFunctionalDashboard
 * - NotificationCenter
 * - SharedWorkflows
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
    CrossFunctionalDashboard,
    mockCrossFunctionalDashboardData,
    type OrganizationMetrics,
    type LayerMetrics,
    type TeamPerformance,
    type Initiative,
    type CollaborationMetric,
} from './CrossFunctionalDashboard';
import {
    NotificationCenter,
    mockNotificationCenterData,
    type NotificationMetrics,
    type Notification,
    type AlertNotification,
    type CollaborationActivity,
    type ApprovalRequest,
} from './NotificationCenter';
import {
    SharedWorkflows,
    mockSharedWorkflowsData,
    type WorkflowMetrics,
    type WorkflowProcess,
    type WorkflowStage,
    type WorkflowTemplate,
    type WorkflowActivity,
} from './SharedWorkflows';

/**
 * CrossFunctionalDashboard Tests
 */
describe('CrossFunctionalDashboard', () => {
    describe('Component Rendering', () => {
        it('renders dashboard with organization metrics', () => {
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                />
            );

            expect(screen.getByText('Cross-Functional Dashboard')).toBeInTheDocument();
            expect(screen.getByText('Total Employees')).toBeInTheDocument();
            expect(screen.getByText('1,250')).toBeInTheDocument();
            expect(screen.getByText('Active Projects')).toBeInTheDocument();
            expect(screen.getByText('48')).toBeInTheDocument();
        });

        it('renders all 4 tabs', () => {
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                />
            );

            expect(screen.getByRole('tab', { name: /Layers/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Teams/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Initiatives/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Collaboration/i })).toBeInTheDocument();
        });

        it('displays export button', () => {
            const onExportDashboard = vi.fn();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                    onExportDashboard={onExportDashboard}
                />
            );

            expect(screen.getByRole('button', { name: /Export Dashboard/i })).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to Teams tab and shows team status filter', async () => {
            const user = userEvent.setup();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Teams/i }));

            await waitFor(() => {
                expect(screen.getByText(/All \(4\)/i)).toBeInTheDocument();
                expect(screen.getByText(/Excellent \(2\)/i)).toBeInTheDocument();
            });
        });

        it('switches to Initiatives tab and displays initiatives', async () => {
            const user = userEvent.setup();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Initiatives/i }));

            await waitFor(() => {
                expect(screen.getByText('AI Transformation Initiative')).toBeInTheDocument();
            });
        });

        it('switches to Collaboration tab and displays metrics', async () => {
            const user = userEvent.setup();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Collaboration/i }));

            await waitFor(() => {
                expect(screen.getByText('Communication')).toBeInTheDocument();
                expect(screen.getByText('Knowledge-sharing')).toBeInTheDocument();
            });
        });
    });

    describe('Team Status Filtering', () => {
        it('filters teams by status', async () => {
            const user = userEvent.setup();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Teams/i }));

            await waitFor(async () => {
                const excellentFilter = screen.getByText(/Excellent \(2\)/i);
                await user.click(excellentFilter);

                await waitFor(() => {
                    expect(screen.getByText('Platform Engineering')).toBeInTheDocument();
                    expect(screen.getByText('Product Design')).toBeInTheDocument();
                });
            });
        });
    });

    describe('Callback Interactions', () => {
        it('calls onLayerClick when layer is clicked', async () => {
            const onLayerClick = vi.fn();
            const user = userEvent.setup();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                    onLayerClick={onLayerClick}
                />
            );

            const layerCard = screen.getByText('Individual Contributors').closest('div[class*="cursor-pointer"]');
            await user.click(layerCard!);

            expect(onLayerClick).toHaveBeenCalledWith('ic');
        });

        it('calls onExportDashboard when export button is clicked', async () => {
            const onExportDashboard = vi.fn();
            const user = userEvent.setup();
            render(
                <CrossFunctionalDashboard
                    organizationMetrics={mockCrossFunctionalDashboardData.organizationMetrics}
                    layerMetrics={mockCrossFunctionalDashboardData.layerMetrics}
                    teamPerformance={mockCrossFunctionalDashboardData.teamPerformance}
                    initiatives={mockCrossFunctionalDashboardData.initiatives}
                    collaborationMetrics={mockCrossFunctionalDashboardData.collaborationMetrics}
                    onExportDashboard={onExportDashboard}
                />
            );

            await user.click(screen.getByRole('button', { name: /Export Dashboard/i }));

            expect(onExportDashboard).toHaveBeenCalled();
        });
    });
});

/**
 * NotificationCenter Tests
 */
describe('NotificationCenter', () => {
    describe('Component Rendering', () => {
        it('renders notification center with metrics', () => {
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            expect(screen.getByText('Notification Center')).toBeInTheDocument();
            expect(screen.getByText('Total Notifications')).toBeInTheDocument();
            expect(screen.getByText('48')).toBeInTheDocument();
        });

        it('renders all 4 tabs', () => {
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            expect(screen.getByRole('tab', { name: /All/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Alerts/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Collaboration/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Approvals/i })).toBeInTheDocument();
        });

        it('displays action buttons', () => {
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            expect(screen.getByRole('button', { name: /Mark All Read/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /Clear All/i })).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to Alerts tab and displays alerts', async () => {
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Alerts/i }));

            await waitFor(() => {
                expect(screen.getByText('System Performance Alert')).toBeInTheDocument();
            });
        });

        it('switches to Collaboration tab and displays activities', async () => {
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Collaboration/i }));

            await waitFor(() => {
                expect(screen.getByText('You were mentioned in a comment')).toBeInTheDocument();
            });
        });

        it('switches to Approvals tab and displays requests', async () => {
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Approvals/i }));

            await waitFor(() => {
                expect(screen.getByText('Infrastructure Budget Approval')).toBeInTheDocument();
            });
        });
    });

    describe('Notification Filtering', () => {
        it('filters notifications by unread status', async () => {
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            const unreadFilter = screen.getByText(/Unread \(12\)/i);
            await user.click(unreadFilter);

            await waitFor(() => {
                expect(screen.getByText('Q1 Budget Approval Required')).toBeInTheDocument();
            });
        });

        it('filters notifications by priority', async () => {
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            const urgentFilter = screen.getAllByText('Urgent')[0]; // First occurrence in filter chips
            await user.click(urgentFilter);

            await waitFor(() => {
                expect(screen.getByText('Q1 Budget Approval Required')).toBeInTheDocument();
            });
        });
    });

    describe('Callback Interactions', () => {
        it('calls onNotificationClick when notification is clicked', async () => {
            const onNotificationClick = vi.fn();
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                    onNotificationClick={onNotificationClick}
                />
            );

            const notificationCard = screen.getByText('Q1 Budget Approval Required').closest('div[class*="cursor-pointer"]');
            await user.click(notificationCard!);

            expect(onNotificationClick).toHaveBeenCalledWith('notif-1');
        });

        it('calls onMarkAllRead when button is clicked', async () => {
            const onMarkAllRead = vi.fn();
            const user = userEvent.setup();
            render(
                <NotificationCenter
                    metrics={mockNotificationCenterData.metrics}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                    onMarkAllRead={onMarkAllRead}
                />
            );

            await user.click(screen.getByRole('button', { name: /Mark All Read/i }));

            expect(onMarkAllRead).toHaveBeenCalled();
        });

        it('disables Mark All Read button when no unread notifications', () => {
            const metricsNoUnread = { ...mockNotificationCenterData.metrics, unreadCount: 0 };
            render(
                <NotificationCenter
                    metrics={metricsNoUnread}
                    notifications={mockNotificationCenterData.notifications}
                    alerts={mockNotificationCenterData.alerts}
                    collaborationActivities={mockNotificationCenterData.collaborationActivities}
                    approvalRequests={mockNotificationCenterData.approvalRequests}
                />
            );

            expect(screen.getByRole('button', { name: /Mark All Read/i })).toBeDisabled();
        });
    });
});

/**
 * SharedWorkflows Tests
 */
describe('SharedWorkflows', () => {
    describe('Component Rendering', () => {
        it('renders shared workflows with metrics', () => {
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                />
            );

            expect(screen.getByText('Shared Workflows')).toBeInTheDocument();
            expect(screen.getByText('Total Workflows')).toBeInTheDocument();
            expect(screen.getByText('28')).toBeInTheDocument();
        });

        it('renders all 4 tabs', () => {
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                />
            );

            expect(screen.getByRole('tab', { name: /Workflows/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Stages/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Templates/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /Activity/i })).toBeInTheDocument();
        });

        it('displays create workflow button', () => {
            const onCreateWorkflow = vi.fn();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                    onCreateWorkflow={onCreateWorkflow}
                />
            );

            expect(screen.getByRole('button', { name: /Create Workflow/i })).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to Stages tab and displays stages table', async () => {
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Stages/i }));

            await waitFor(() => {
                expect(screen.getByText('Budget Submission')).toBeInTheDocument();
                expect(screen.getByText('Manager Review')).toBeInTheDocument();
            });
        });

        it('switches to Templates tab and displays templates', async () => {
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Templates/i }));

            await waitFor(() => {
                expect(screen.getByText('Standard Approval Workflow')).toBeInTheDocument();
            });
        });

        it('switches to Activity tab and displays activities', async () => {
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Activity/i }));

            await waitFor(() => {
                expect(screen.getByText('Moved to Director Approval stage')).toBeInTheDocument();
            });
        });
    });

    describe('Workflow Status Filtering', () => {
        it('filters workflows by status', async () => {
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                />
            );

            const blockedFilter = screen.getByText(/Blocked \(1\)/i);
            await user.click(blockedFilter);

            await waitFor(() => {
                expect(screen.getByText('New Employee Onboarding')).toBeInTheDocument();
            });
        });
    });

    describe('Callback Interactions', () => {
        it('calls onWorkflowClick when workflow is clicked', async () => {
            const onWorkflowClick = vi.fn();
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                    onWorkflowClick={onWorkflowClick}
                />
            );

            const workflowCard = screen.getByText('Q1 Budget Approval Process').closest('div[class*="cursor-pointer"]');
            await user.click(workflowCard!);

            expect(onWorkflowClick).toHaveBeenCalledWith('wf-1');
        });

        it('calls onTemplateClick when template is clicked', async () => {
            const onTemplateClick = vi.fn();
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                    onTemplateClick={onTemplateClick}
                />
            );

            await user.click(screen.getByRole('tab', { name: /Templates/i }));

            await waitFor(async () => {
                const templateCard = screen.getByText('Standard Approval Workflow').closest('div[class*="cursor-pointer"]');
                await user.click(templateCard!);

                expect(onTemplateClick).toHaveBeenCalledWith('tmpl-1');
            });
        });

        it('calls onCreateWorkflow when create button is clicked', async () => {
            const onCreateWorkflow = vi.fn();
            const user = userEvent.setup();
            render(
                <SharedWorkflows
                    metrics={mockSharedWorkflowsData.metrics}
                    workflows={mockSharedWorkflowsData.workflows}
                    stages={mockSharedWorkflowsData.stages}
                    templates={mockSharedWorkflowsData.templates}
                    activities={mockSharedWorkflowsData.activities}
                    onCreateWorkflow={onCreateWorkflow}
                />
            );

            await user.click(screen.getByRole('button', { name: /Create Workflow/i }));

            expect(onCreateWorkflow).toHaveBeenCalled();
        });
    });
});
