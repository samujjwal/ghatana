/**
 * Root Features Integration Tests
 *
 * Comprehensive test suite for all Root-level components:
 * - SystemAdmin (user/role management, audit logs, system settings)
 * - OperationsCenter (infrastructure monitoring, deployments, incidents)
 * - PlatformInsights (usage analytics, cost optimization, capacity planning)
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SystemAdmin, mockSystemAdminData } from './SystemAdmin';
import { OperationsCenter, mockOperationsCenterData } from './OperationsCenter';
import { PlatformInsights, mockPlatformInsightsData } from './PlatformInsights';

/**
 * SystemAdmin Component Tests
 */
describe('SystemAdmin Component', () => {
    const mockCallbacks = {
        onUserClick: vi.fn(),
        onRoleClick: vi.fn(),
        onSettingClick: vi.fn(),
        onCreateUser: vi.fn(),
        onCreateRole: vi.fn(),
        onExportLogs: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render the component with all sections', () => {
            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('System Administration')).toBeInTheDocument();
            expect(screen.getByText('User and role management, audit logs, and system configuration')).toBeInTheDocument();
        });

        it('should render system metrics KPI cards', () => {
            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('Total Users')).toBeInTheDocument();
            expect(screen.getByText('Roles')).toBeInTheDocument();
            expect(screen.getByText('Storage')).toBeInTheDocument();
            expect(screen.getByText('Last Backup')).toBeInTheDocument();
        });

        it('should display system health alert when degraded', () => {
            const degradedMetrics = {
                ...mockSystemAdminData.systemMetrics,
                systemHealth: 'degraded' as const,
            };

            render(
                <SystemAdmin
                    systemMetrics={degradedMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText(/system health is degraded/i)).toBeInTheDocument();
        });
    });

    describe('User Management', () => {
        it('should render user table with all users', () => {
            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            mockSystemAdminData.users.forEach((user) => {
                expect(screen.getByText(user.name)).toBeInTheDocument();
                expect(screen.getByText(user.email)).toBeInTheDocument();
            });
        });

        it('should filter users by status', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Click "Active" filter
            const activeFilter = screen.getByText(/Active \(\d+\)/);
            await user.click(activeFilter);

            // Should only show active users
            const activeUsers = mockSystemAdminData.users.filter((u) => u.status === 'active');
            const inactiveUsers = mockSystemAdminData.users.filter((u) => u.status !== 'active');

            activeUsers.forEach((u) => {
                expect(screen.getByText(u.name)).toBeInTheDocument();
            });

            inactiveUsers.forEach((u) => {
                expect(screen.queryByText(u.name)).not.toBeInTheDocument();
            });
        });

        it('should call onUserClick when user row is clicked', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            const userRow = screen.getByText(mockSystemAdminData.users[0].name).closest('tr');
            await user.click(userRow!);

            expect(mockCallbacks.onUserClick).toHaveBeenCalledWith(mockSystemAdminData.users[0].id);
        });

        it('should call onCreateUser when Create User button is clicked', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            const createButton = screen.getByText('Create User');
            await user.click(createButton);

            expect(mockCallbacks.onCreateUser).toHaveBeenCalled();
        });
    });

    describe('Role Management', () => {
        it('should render role cards with permissions', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Navigate to Roles tab
            const rolesTab = screen.getByText(/Roles \(\d+\)/);
            await user.click(rolesTab);

            mockSystemAdminData.roles.forEach((role) => {
                expect(screen.getByText(role.name)).toBeInTheDocument();
                expect(screen.getByText(role.description)).toBeInTheDocument();
            });
        });

        it('should call onRoleClick when role card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Navigate to Roles tab
            const rolesTab = screen.getByText(/Roles \(\d+\)/);
            await user.click(rolesTab);

            const roleCard = screen.getByText(mockSystemAdminData.roles[0].name).closest('[class*="MuiCard"]');
            await user.click(roleCard!);

            expect(mockCallbacks.onRoleClick).toHaveBeenCalledWith(mockSystemAdminData.roles[0].id);
        });
    });

    describe('Audit Logs', () => {
        it('should render audit log table', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Navigate to Audit Logs tab
            const logsTab = screen.getByText(/Audit Logs \(\d+\)/);
            await user.click(logsTab);

            mockSystemAdminData.auditLogs.forEach((log) => {
                expect(screen.getByText(log.userName)).toBeInTheDocument();
                expect(screen.getByText(log.action)).toBeInTheDocument();
            });
        });

        it('should call onExportLogs when Export Logs button is clicked', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Navigate to Audit Logs tab
            const logsTab = screen.getByText(/Audit Logs \(\d+\)/);
            await user.click(logsTab);

            const exportButton = screen.getByText('Export Logs');
            await user.click(exportButton);

            expect(mockCallbacks.onExportLogs).toHaveBeenCalled();
        });
    });

    describe('System Settings', () => {
        it('should filter settings by category', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Navigate to Settings tab
            const settingsTab = screen.getByText(/Settings \(\d+\)/);
            await user.click(settingsTab);

            // Click "Security" filter
            const securityFilter = screen.getByText('Security');
            await user.click(securityFilter);

            // Should only show security settings
            const securitySettings = mockSystemAdminData.settings.filter((s) => s.category === 'security');
            const otherSettings = mockSystemAdminData.settings.filter((s) => s.category !== 'security');

            securitySettings.forEach((s) => {
                expect(screen.getByText(s.name)).toBeInTheDocument();
            });

            otherSettings.forEach((s) => {
                expect(screen.queryByText(s.name)).not.toBeInTheDocument();
            });
        });

        it('should call onSettingClick when setting card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <SystemAdmin
                    systemMetrics={mockSystemAdminData.systemMetrics}
                    users={mockSystemAdminData.users}
                    roles={mockSystemAdminData.roles}
                    auditLogs={mockSystemAdminData.auditLogs}
                    settings={mockSystemAdminData.settings}
                    {...mockCallbacks}
                />
            );

            // Navigate to Settings tab
            const settingsTab = screen.getByText(/Settings \(\d+\)/);
            await user.click(settingsTab);

            const settingCard = screen.getByText(mockSystemAdminData.settings[0].name).closest('[class*="MuiCard"]');
            await user.click(settingCard!);

            expect(mockCallbacks.onSettingClick).toHaveBeenCalledWith(mockSystemAdminData.settings[0].id);
        });
    });
});

/**
 * OperationsCenter Component Tests
 */
describe('OperationsCenter Component', () => {
    const mockCallbacks = {
        onServiceClick: vi.fn(),
        onDeploymentClick: vi.fn(),
        onIncidentClick: vi.fn(),
        onCreateIncident: vi.fn(),
        onTriggerDeployment: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render the component with all sections', () => {
            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('Operations Center')).toBeInTheDocument();
            expect(screen.getByText('Infrastructure monitoring and incident response')).toBeInTheDocument();
        });

        it('should render infrastructure metrics KPI cards', () => {
            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('Service Health')).toBeInTheDocument();
            expect(screen.getByText('Average Uptime')).toBeInTheDocument();
            expect(screen.getByText('Active Incidents')).toBeInTheDocument();
            expect(screen.getByText('Recent Deployments')).toBeInTheDocument();
        });

        it('should display critical incident alert', () => {
            const criticalIncident = {
                ...mockOperationsCenterData.incidents[0],
                severity: 'critical' as const,
                status: 'active' as const,
            };

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={[criticalIncident, ...mockOperationsCenterData.incidents.slice(1)]}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText(/critical incident.*require immediate attention/i)).toBeInTheDocument();
        });
    });

    describe('Service Monitoring', () => {
        it('should render service cards with status', () => {
            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            mockOperationsCenterData.services.forEach((service) => {
                expect(screen.getByText(service.name)).toBeInTheDocument();
            });
        });

        it('should filter services by status', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            // Click "Healthy" filter
            const healthyFilter = screen.getByText(/Healthy \(\d+\)/);
            await user.click(healthyFilter);

            // Should only show healthy services
            const healthyServices = mockOperationsCenterData.services.filter((s) => s.status === 'healthy');
            const degradedServices = mockOperationsCenterData.services.filter((s) => s.status !== 'healthy');

            healthyServices.forEach((s) => {
                expect(screen.getByText(s.name)).toBeInTheDocument();
            });

            degradedServices.forEach((s) => {
                expect(screen.queryByText(s.name)).not.toBeInTheDocument();
            });
        });

        it('should call onServiceClick when service card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            const serviceCard = screen.getByText(mockOperationsCenterData.services[0].name).closest('[class*="MuiCard"]');
            await user.click(serviceCard!);

            expect(mockCallbacks.onServiceClick).toHaveBeenCalledWith(mockOperationsCenterData.services[0].id);
        });
    });

    describe('Deployment Tracking', () => {
        it('should render deployment table', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            // Navigate to Deployments tab
            const deploymentsTab = screen.getByText(/Deployments \(\d+\)/);
            await user.click(deploymentsTab);

            mockOperationsCenterData.deployments.forEach((deployment) => {
                expect(screen.getByText(deployment.service)).toBeInTheDocument();
                expect(screen.getByText(deployment.version)).toBeInTheDocument();
            });
        });

        it('should call onDeploymentClick when deployment row is clicked', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            // Navigate to Deployments tab
            const deploymentsTab = screen.getByText(/Deployments \(\d+\)/);
            await user.click(deploymentsTab);

            const deploymentRow = screen.getByText(mockOperationsCenterData.deployments[0].service).closest('tr');
            await user.click(deploymentRow!);

            expect(mockCallbacks.onDeploymentClick).toHaveBeenCalledWith(mockOperationsCenterData.deployments[0].id);
        });
    });

    describe('Incident Management', () => {
        it('should render incident cards', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            // Navigate to Incidents tab
            const incidentsTab = screen.getByText(/Incidents \(\d+\)/);
            await user.click(incidentsTab);

            mockOperationsCenterData.incidents.forEach((incident) => {
                expect(screen.getByText(incident.title)).toBeInTheDocument();
                expect(screen.getByText(incident.description)).toBeInTheDocument();
            });
        });

        it('should call onIncidentClick when incident card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            // Navigate to Incidents tab
            const incidentsTab = screen.getByText(/Incidents \(\d+\)/);
            await user.click(incidentsTab);

            const incidentCard = screen.getByText(mockOperationsCenterData.incidents[0].title).closest('[class*="MuiCard"]');
            await user.click(incidentCard!);

            expect(mockCallbacks.onIncidentClick).toHaveBeenCalledWith(mockOperationsCenterData.incidents[0].id);
        });
    });

    describe('Resource Utilization', () => {
        it('should render resource cards with utilization', async () => {
            const user = userEvent.setup();

            render(
                <OperationsCenter
                    infraMetrics={mockOperationsCenterData.infraMetrics}
                    services={mockOperationsCenterData.services}
                    deployments={mockOperationsCenterData.deployments}
                    incidents={mockOperationsCenterData.incidents}
                    resources={mockOperationsCenterData.resources}
                    {...mockCallbacks}
                />
            );

            // Navigate to Resources tab
            const resourcesTab = screen.getByText(/Resources \(\d+\)/);
            await user.click(resourcesTab);

            mockOperationsCenterData.resources.forEach((resource) => {
                expect(screen.getByText(new RegExp(resource.resourceType, 'i'))).toBeInTheDocument();
            });
        });
    });
});

/**
 * PlatformInsights Component Tests
 */
describe('PlatformInsights Component', () => {
    const mockCallbacks = {
        onCostClick: vi.fn(),
        onCapacityClick: vi.fn(),
        onHealthClick: vi.fn(),
        onRecommendationClick: vi.fn(),
        onExportReport: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render the component with all sections', () => {
            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('Platform Insights')).toBeInTheDocument();
            expect(screen.getByText('Usage analytics, cost optimization, and capacity planning')).toBeInTheDocument();
        });

        it('should render usage metrics KPI cards', () => {
            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('Active Users')).toBeInTheDocument();
            expect(screen.getByText('API Calls')).toBeInTheDocument();
            expect(screen.getByText('Storage Used')).toBeInTheDocument();
            expect(screen.getByText('Error Rate')).toBeInTheDocument();
        });
    });

    describe('Usage Analytics', () => {
        it('should render usage trend cards', () => {
            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            expect(screen.getByText('User Activity')).toBeInTheDocument();
            expect(screen.getByText('API Performance')).toBeInTheDocument();
            expect(screen.getByText('Storage & Bandwidth')).toBeInTheDocument();
            expect(screen.getByText('Reliability Metrics')).toBeInTheDocument();
        });
    });

    describe('Cost Analysis', () => {
        it('should render cost breakdown cards', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Costs tab
            const costsTab = screen.getByText(/Costs \(\$[\d.]+k\)/);
            await user.click(costsTab);

            mockPlatformInsightsData.serviceCosts.forEach((cost) => {
                expect(screen.getByText(cost.serviceName)).toBeInTheDocument();
            });
        });

        it('should filter costs by category', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Costs tab
            const costsTab = screen.getByText(/Costs \(\$[\d.]+k\)/);
            await user.click(costsTab);

            // Click "Compute" filter
            const computeFilter = screen.getByText('Compute');
            await user.click(computeFilter);

            // Should only show compute costs
            const computeCosts = mockPlatformInsightsData.serviceCosts.filter((c) => c.category === 'compute');
            const otherCosts = mockPlatformInsightsData.serviceCosts.filter((c) => c.category !== 'compute');

            computeCosts.forEach((c) => {
                expect(screen.getByText(c.serviceName)).toBeInTheDocument();
            });

            otherCosts.forEach((c) => {
                expect(screen.queryByText(c.serviceName)).not.toBeInTheDocument();
            });
        });

        it('should call onCostClick when cost card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Costs tab
            const costsTab = screen.getByText(/Costs \(\$[\d.]+k\)/);
            await user.click(costsTab);

            const costCard = screen.getByText(mockPlatformInsightsData.serviceCosts[0].serviceName).closest('[class*="MuiCard"]');
            await user.click(costCard!);

            expect(mockCallbacks.onCostClick).toHaveBeenCalledWith(mockPlatformInsightsData.serviceCosts[0].id);
        });
    });

    describe('Capacity Planning', () => {
        it('should render capacity metric cards', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Capacity tab
            const capacityTab = screen.getByText(/Capacity \(\d+\)/);
            await user.click(capacityTab);

            mockPlatformInsightsData.capacityMetrics.forEach((capacity) => {
                expect(screen.getByText(capacity.resourceName)).toBeInTheDocument();
            });
        });

        it('should call onCapacityClick when capacity card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Capacity tab
            const capacityTab = screen.getByText(/Capacity \(\d+\)/);
            await user.click(capacityTab);

            const capacityCard = screen.getByText(mockPlatformInsightsData.capacityMetrics[0].resourceName).closest('[class*="MuiCard"]');
            await user.click(capacityCard!);

            expect(mockCallbacks.onCapacityClick).toHaveBeenCalledWith(mockPlatformInsightsData.capacityMetrics[0].id);
        });
    });

    describe('Platform Health', () => {
        it('should render health indicator table', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Health tab
            const healthTab = screen.getByText(/Health \(\d+\)/);
            await user.click(healthTab);

            mockPlatformInsightsData.healthIndicators.forEach((indicator) => {
                expect(screen.getByText(indicator.metric)).toBeInTheDocument();
            });
        });

        it('should render optimization recommendations', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Health tab
            const healthTab = screen.getByText(/Health \(\d+\)/);
            await user.click(healthTab);

            mockPlatformInsightsData.recommendations.forEach((rec) => {
                expect(screen.getByText(rec.title)).toBeInTheDocument();
                expect(screen.getByText(rec.description)).toBeInTheDocument();
            });
        });

        it('should call onRecommendationClick when recommendation card is clicked', async () => {
            const user = userEvent.setup();

            render(
                <PlatformInsights
                    usageMetrics={mockPlatformInsightsData.usageMetrics}
                    serviceCosts={mockPlatformInsightsData.serviceCosts}
                    capacityMetrics={mockPlatformInsightsData.capacityMetrics}
                    healthIndicators={mockPlatformInsightsData.healthIndicators}
                    recommendations={mockPlatformInsightsData.recommendations}
                    {...mockCallbacks}
                />
            );

            // Navigate to Health tab
            const healthTab = screen.getByText(/Health \(\d+\)/);
            await user.click(healthTab);

            const recCard = screen.getByText(mockPlatformInsightsData.recommendations[0].title).closest('[class*="MuiCard"]');
            await user.click(recCard!);

            expect(mockCallbacks.onRecommendationClick).toHaveBeenCalledWith(mockPlatformInsightsData.recommendations[0].id);
        });
    });
});
