import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RootDashboard, type TenantInfo } from '../RootDashboard';
import { PlatformHealthDashboard, type HealthMetric, type SystemAlert, type ServiceStatus } from '../PlatformHealthDashboard';
import { CrossTenantUserSearch, type CrossTenantUser, type UserActivity, type SecurityEvent } from '../CrossTenantUserSearch';

// ==================== ROOT DASHBOARD TESTS ====================

describe('RootDashboard', () => {
    const mockTenants: TenantInfo[] = [
        {
            id: 'tenant-1',
            name: 'Acme Corporation',
            slug: 'acme-corp',
            createdAt: '2024-01-15T10:00:00Z',
            status: 'active',
            plan: 'enterprise',
            userCount: 450,
            activeUsers: 380,
            storageUsed: 850,
            storageLimit: 1000,
            apiCallsToday: 125000,
            apiCallLimit: 500000,
            healthScore: 95,
            lastActivity: '2025-12-11T11:30:00Z',
            owner: {
                name: 'John Smith',
                email: 'john.smith@acme.com',
            },
        },
        {
            id: 'tenant-2',
            name: 'TechStart Inc',
            slug: 'techstart',
            createdAt: '2024-06-20T14:00:00Z',
            status: 'trial',
            plan: 'professional',
            userCount: 85,
            activeUsers: 72,
            storageUsed: 120,
            storageLimit: 250,
            apiCallsToday: 18000,
            apiCallLimit: 100000,
            healthScore: 88,
            lastActivity: '2025-12-11T10:15:00Z',
            owner: {
                name: 'Sarah Johnson',
                email: 'sarah.johnson@techstart.io',
            },
        },
        {
            id: 'tenant-3',
            name: 'Legacy Systems Co',
            slug: 'legacy-systems',
            createdAt: '2022-03-15T12:00:00Z',
            status: 'suspended',
            plan: 'professional',
            userCount: 150,
            activeUsers: 0,
            storageUsed: 380,
            storageLimit: 500,
            apiCallsToday: 0,
            apiCallLimit: 200000,
            healthScore: 0,
            lastActivity: '2025-11-20T14:30:00Z',
            owner: {
                name: 'Robert Wilson',
                email: 'robert.wilson@legacysystems.com',
            },
        },
    ];

    const mockPlatformStats = {
        totalTenants: 5,
        activeTenants: 4,
        suspendedTenants: 1,
        trialTenants: 1,
        totalUsers: 1897,
        activeUsers: 1440,
        totalStorage: 3800,
        apiCallsToday: 628000,
        averageHealthScore: 76,
    };

    const mockCallbacks = {
        onTenantInspect: vi.fn(),
        onTenantSuspend: vi.fn(),
        onTenantReactivate: vi.fn(),
        onAdjustLimits: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render platform statistics correctly', () => {
        render(<RootDashboard tenants={mockTenants} platformStats={mockPlatformStats} />);

        expect(screen.getByText('5')).toBeInTheDocument();
        expect(screen.getByText('4 active')).toBeInTheDocument();
        expect(screen.getByText('1,897')).toBeInTheDocument();
        expect(screen.getByText('1,440 active')).toBeInTheDocument();
        expect(screen.getByText('3.8 TB')).toBeInTheDocument();
        expect(screen.getByText('628K')).toBeInTheDocument();
    });

    it('should display all tenants in the list', () => {
        render(<RootDashboard tenants={mockTenants} />);

        expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
        expect(screen.getByText('TechStart Inc')).toBeInTheDocument();
        expect(screen.getByText('Legacy Systems Co')).toBeInTheDocument();
    });

    it('should filter tenants by search query', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} />);

        const searchInput = screen.getByPlaceholderText(/search/i);
        await user.type(searchInput, 'acme');

        await waitFor(() => {
            expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
            expect(screen.queryByText('TechStart Inc')).not.toBeInTheDocument();
            expect(screen.queryByText('Legacy Systems Co')).not.toBeInTheDocument();
        });
    });

    it('should filter tenants by status', async () => {
        render(<RootDashboard tenants={mockTenants} />);

        const statusSelect = screen.getByLabelText(/status/i);
        fireEvent.change(statusSelect, { target: { value: 'suspended' } });

        await waitFor(() => {
            expect(screen.getByText('Legacy Systems Co')).toBeInTheDocument();
            expect(screen.queryByText('Acme Corporation')).not.toBeInTheDocument();
            expect(screen.queryByText('TechStart Inc')).not.toBeInTheDocument();
        });
    });

    it('should filter tenants by plan', async () => {
        render(<RootDashboard tenants={mockTenants} />);

        const planSelect = screen.getByLabelText(/plan/i);
        fireEvent.change(planSelect, { target: { value: 'enterprise' } });

        await waitFor(() => {
            expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
            expect(screen.queryByText('TechStart Inc')).not.toBeInTheDocument();
        });
    });

    it('should open tenant inspection dialog when tenant is clicked', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} {...mockCallbacks} />);

        const tenantCard = screen.getByText('Acme Corporation');
        await user.click(tenantCard);

        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Tenant Details')).toBeInTheDocument();
            expect(mockCallbacks.onTenantInspect).toHaveBeenCalledWith('tenant-1');
        });
    });

    it('should display all tabs in tenant inspection dialog', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} />);

        const tenantCard = screen.getByText('Acme Corporation');
        await user.click(tenantCard);

        await waitFor(() => {
            expect(screen.getByRole('tab', { name: /overview/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /resource usage/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /limits/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /actions/i })).toBeInTheDocument();
        });
    });

    it('should display tenant resource usage on overview tab', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} />);

        const tenantCard = screen.getByText('Acme Corporation');
        await user.click(tenantCard);

        await waitFor(() => {
            const dialog = screen.getByRole('dialog');
            expect(within(dialog).getByText(/john.smith@acme.com/i)).toBeInTheDocument();
            expect(within(dialog).getByText(/health score/i)).toBeInTheDocument();
        });
    });

    it('should allow suspending an active tenant', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} {...mockCallbacks} />);

        // Open inspection dialog
        await user.click(screen.getByText('Acme Corporation'));

        // Navigate to Actions tab
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const actionsTab = screen.getByRole('tab', { name: /actions/i });
        await user.click(actionsTab);

        // Click suspend button
        const suspendButton = await screen.findByRole('button', { name: /suspend tenant/i });
        await user.click(suspendButton);

        expect(mockCallbacks.onTenantSuspend).toHaveBeenCalledWith('tenant-1');
    });

    it('should allow reactivating a suspended tenant', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} {...mockCallbacks} />);

        // Open inspection dialog for suspended tenant
        await user.click(screen.getByText('Legacy Systems Co'));

        // Navigate to Actions tab
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const actionsTab = screen.getByRole('tab', { name: /actions/i });
        await user.click(actionsTab);

        // Click reactivate button
        const reactivateButton = await screen.findByRole('button', { name: /reactivate tenant/i });
        await user.click(reactivateButton);

        expect(mockCallbacks.onTenantReactivate).toHaveBeenCalledWith('tenant-3');
    });

    it('should allow adjusting tenant limits', async () => {
        const user = userEvent.setup();
        render(<RootDashboard tenants={mockTenants} {...mockCallbacks} />);

        // Open inspection dialog
        await user.click(screen.getByText('Acme Corporation'));

        // Navigate to Limits tab
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const limitsTab = screen.getByRole('tab', { name: /limits/i });
        await user.click(limitsTab);

        // Click adjust limits button
        const adjustButton = await screen.findByRole('button', { name: /adjust limits/i });
        await user.click(adjustButton);

        // Change storage limit
        const storageInput = screen.getByLabelText(/storage limit/i);
        await user.clear(storageInput);
        await user.type(storageInput, '2000');

        // Save changes
        const saveButton = screen.getByRole('button', { name: /save changes/i });
        await user.click(saveButton);

        expect(mockCallbacks.onAdjustLimits).toHaveBeenCalledWith('tenant-1', {
            storageLimit: 2000,
            apiCallLimit: 500000,
        });
    });

    it('should display correct health score colors', () => {
        render(<RootDashboard tenants={mockTenants} />);

        // Excellent health (95)
        const acmeChip = screen.getByText(/excellent/i);
        expect(acmeChip).toHaveClass(/MuiChip-colorSuccess/);

        // Good health (88)
        const techStartChip = screen.getByText(/good/i);
        expect(techStartChip).toHaveClass(/MuiChip-colorInfo/);

        // Poor health (0)
        const legacyChip = screen.getByText(/poor/i);
        expect(legacyChip).toHaveClass(/MuiChip-colorError/);
    });

    it('should display correct status colors', () => {
        render(<RootDashboard tenants={mockTenants} />);

        const activeChips = screen.getAllByText(/ACTIVE/i);
        expect(activeChips[0]).toHaveClass(/MuiChip-colorSuccess/);

        const trialChip = screen.getByText(/TRIAL/i);
        expect(trialChip).toHaveClass(/MuiChip-colorInfo/);

        const suspendedChip = screen.getByText(/SUSPENDED/i);
        expect(suspendedChip).toHaveClass(/MuiChip-colorError/);
    });
});

// ==================== PLATFORM HEALTH DASHBOARD TESTS ====================

describe('PlatformHealthDashboard', () => {
    const mockHealthMetrics: HealthMetric[] = [
        {
            id: 'metric-1',
            name: 'API Response Time',
            value: 145,
            unit: 'ms',
            status: 'healthy',
            threshold: { warning: 200, critical: 500 },
            trend: 'stable',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-2',
            name: 'CPU Utilization',
            value: 72,
            unit: '%',
            status: 'warning',
            threshold: { warning: 70, critical: 90 },
            trend: 'up',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-3',
            name: 'Storage Capacity',
            value: 92,
            unit: '%',
            status: 'critical',
            threshold: { warning: 80, critical: 90 },
            trend: 'up',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
    ];

    const mockAlerts: SystemAlert[] = [
        {
            id: 'alert-1',
            severity: 'critical',
            title: 'Storage Capacity Critical',
            description: 'Storage utilization has exceeded 90% threshold',
            affectedServices: ['File Storage'],
            affectedTenants: 45,
            triggeredAt: '2025-12-11T08:00:00Z',
        },
        {
            id: 'alert-2',
            severity: 'warning',
            title: 'High CPU Usage',
            description: 'CPU utilization above 70% for extended period',
            affectedServices: ['API Gateway', 'Background Jobs'],
            affectedTenants: 12,
            triggeredAt: '2025-12-11T09:30:00Z',
            acknowledgedAt: '2025-12-11T09:45:00Z',
            assignedTo: 'DevOps Team',
        },
        {
            id: 'alert-3',
            severity: 'info',
            title: 'Maintenance Completed',
            description: 'Scheduled database maintenance completed successfully',
            affectedServices: ['Database Cluster'],
            affectedTenants: 0,
            triggeredAt: '2025-12-11T06:00:00Z',
            resolvedAt: '2025-12-11T07:00:00Z',
        },
    ];

    const mockServices: ServiceStatus[] = [
        {
            id: 'service-1',
            name: 'API Gateway',
            status: 'operational',
            uptime: 99.98,
            responseTime: 145,
            errorRate: 0.8,
        },
        {
            id: 'service-2',
            name: 'File Storage',
            status: 'degraded',
            uptime: 98.5,
            responseTime: 320,
            errorRate: 2.1,
            lastIncident: '2025-12-11T08:00:00Z',
        },
        {
            id: 'service-3',
            name: 'Database Cluster',
            status: 'operational',
            uptime: 99.99,
            responseTime: 38,
            errorRate: 0.1,
        },
    ];

    const mockCallbacks = {
        onAcknowledgeAlert: vi.fn(),
        onResolveAlert: vi.fn(),
        onInvestigateAlert: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render system overview metrics', () => {
        render(<PlatformHealthDashboard healthMetrics={mockHealthMetrics} serviceStatuses={mockServices} />);

        expect(screen.getByText(/cpu usage/i)).toBeInTheDocument();
        expect(screen.getByText(/memory/i)).toBeInTheDocument();
        expect(screen.getByText(/storage/i)).toBeInTheDocument();
        expect(screen.getByText(/network/i)).toBeInTheDocument();
    });

    it('should display all health metrics with correct status', () => {
        render(<PlatformHealthDashboard healthMetrics={mockHealthMetrics} />);

        expect(screen.getByText('API Response Time')).toBeInTheDocument();
        expect(screen.getByText('HEALTHY')).toBeInTheDocument();

        expect(screen.getByText('CPU Utilization')).toBeInTheDocument();
        expect(screen.getByText('WARNING')).toBeInTheDocument();

        expect(screen.getByText('Storage Capacity')).toBeInTheDocument();
        expect(screen.getByText('CRITICAL')).toBeInTheDocument();
    });

    it('should display all service statuses', () => {
        render(<PlatformHealthDashboard serviceStatuses={mockServices} />);

        expect(screen.getByText('API Gateway')).toBeInTheDocument();
        expect(screen.getByText('File Storage')).toBeInTheDocument();
        expect(screen.getByText('Database Cluster')).toBeInTheDocument();
    });

    it('should show degraded service with warning indicator', () => {
        render(<PlatformHealthDashboard serviceStatuses={mockServices} />);

        const fileStorageRow = screen.getByText('File Storage').closest('tr');
        expect(fileStorageRow).toBeInTheDocument();
        expect(within(fileStorageRow!).getByText(/degraded/i)).toBeInTheDocument();
    });

    it('should display all active alerts', () => {
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        expect(screen.getByText('Storage Capacity Critical')).toBeInTheDocument();
        expect(screen.getByText('High CPU Usage')).toBeInTheDocument();
        expect(screen.getByText('Maintenance Completed')).toBeInTheDocument();
    });

    it('should filter alerts by severity', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        const criticalFilter = screen.getByRole('button', { name: /critical/i });
        await user.click(criticalFilter);

        await waitFor(() => {
            expect(screen.getByText('Storage Capacity Critical')).toBeInTheDocument();
            expect(screen.queryByText('High CPU Usage')).not.toBeInTheDocument();
            expect(screen.queryByText('Maintenance Completed')).not.toBeInTheDocument();
        });
    });

    it('should display alert counts correctly', () => {
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        expect(screen.getByText(/1 critical/i)).toBeInTheDocument();
        expect(screen.getByText(/1 warning/i)).toBeInTheDocument();
    });

    it('should open alert detail dialog when alert is clicked', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} {...mockCallbacks} />);

        const alert = screen.getByText('Storage Capacity Critical');
        await user.click(alert);

        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(mockCallbacks.onInvestigateAlert).toHaveBeenCalledWith('alert-1');
        });
    });

    it('should display all tabs in alert detail dialog', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        const alert = screen.getByText('Storage Capacity Critical');
        await user.click(alert);

        await waitFor(() => {
            expect(screen.getByRole('tab', { name: /details/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /impact/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /timeline/i })).toBeInTheDocument();
        });
    });

    it('should allow acknowledging an alert', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} {...mockCallbacks} />);

        // Open alert detail
        await user.click(screen.getByText('Storage Capacity Critical'));

        // Click acknowledge button
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const acknowledgeButton = screen.getByRole('button', { name: /acknowledge/i });
        await user.click(acknowledgeButton);

        expect(mockCallbacks.onAcknowledgeAlert).toHaveBeenCalledWith('alert-1');
    });

    it('should allow resolving an alert', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} {...mockCallbacks} />);

        // Open alert detail
        await user.click(screen.getByText('Storage Capacity Critical'));

        // Click resolve button
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const resolveButton = screen.getByRole('button', { name: /mark resolved/i });
        await user.click(resolveButton);

        expect(mockCallbacks.onResolveAlert).toHaveBeenCalledWith('alert-1');
    });

    it('should not show acknowledge button for already acknowledged alerts', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        await user.click(screen.getByText('High CPU Usage'));

        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.queryByRole('button', { name: /acknowledge/i })).not.toBeInTheDocument();
        });
    });

    it('should show resolved alerts with reduced opacity', () => {
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        const resolvedAlert = screen.getByText('Maintenance Completed').closest('div');
        expect(resolvedAlert).toHaveStyle({ opacity: 0.6 });
    });

    it('should display affected services and tenants in alert detail', async () => {
        const user = userEvent.setup();
        render(<PlatformHealthDashboard systemAlerts={mockAlerts} />);

        await user.click(screen.getByText('Storage Capacity Critical'));

        await waitFor(() => {
            const dialog = screen.getByRole('dialog');
            expect(within(dialog).getByText(/file storage/i)).toBeInTheDocument();
            expect(within(dialog).getByText(/45 tenants/i)).toBeInTheDocument();
        });
    });
});

// ==================== CROSS-TENANT USER SEARCH TESTS ====================

describe('CrossTenantUserSearch', () => {
    const mockUsers: CrossTenantUser[] = [
        {
            id: 'user-1',
            email: 'john.smith@acme.com',
            name: 'John Smith',
            status: 'active',
            createdAt: '2024-01-15T10:00:00Z',
            lastLoginAt: '2025-12-11T11:30:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-1',
                    tenantName: 'Acme Corporation',
                    tenantSlug: 'acme-corp',
                    roles: ['Owner', 'Admin'],
                    joinedAt: '2024-01-15T10:00:00Z',
                    lastActivityAt: '2025-12-11T11:30:00Z',
                },
            ],
            securityFlags: {},
            globalStats: {
                totalLogins: 450,
                totalApiCalls: 125000,
                dataAccessed: 85,
            },
        },
        {
            id: 'user-2',
            email: 'alex.suspicious@example.com',
            name: 'Alex Suspicious',
            status: 'active',
            createdAt: '2025-11-15T08:00:00Z',
            lastLoginAt: '2025-12-11T09:45:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-1',
                    tenantName: 'Acme Corporation',
                    tenantSlug: 'acme-corp',
                    roles: ['IC'],
                    joinedAt: '2025-11-15T08:00:00Z',
                },
                {
                    tenantId: 'tenant-2',
                    tenantName: 'TechStart Inc',
                    tenantSlug: 'techstart',
                    roles: ['IC'],
                    joinedAt: '2025-11-20T10:00:00Z',
                },
            ],
            securityFlags: {
                multipleFailedLogins: true,
                suspiciousActivity: true,
            },
            globalStats: {
                totalLogins: 85,
                totalApiCalls: 15000,
                dataAccessed: 45,
            },
        },
        {
            id: 'user-3',
            email: 'robert.wilson@legacysystems.com',
            name: 'Robert Wilson',
            status: 'suspended',
            createdAt: '2022-03-15T12:00:00Z',
            lastLoginAt: '2025-11-20T14:30:00Z',
            tenantMemberships: [
                {
                    tenantId: 'tenant-5',
                    tenantName: 'Legacy Systems Co',
                    tenantSlug: 'legacy-systems',
                    roles: ['Owner', 'Admin'],
                    joinedAt: '2022-03-15T12:00:00Z',
                },
            ],
            securityFlags: {
                compromisedCredentials: true,
            },
            globalStats: {
                totalLogins: 890,
                totalApiCalls: 250000,
                dataAccessed: 380,
            },
        },
    ];

    const mockActivities: UserActivity[] = [
        {
            id: 'activity-1',
            userId: 'user-2',
            tenantId: 'tenant-1',
            tenantName: 'Acme Corporation',
            action: 'Login',
            ipAddress: '192.168.1.100',
            location: 'New York, US',
            userAgent: 'Mozilla/5.0',
            timestamp: '2025-12-11T09:45:00Z',
            status: 'success',
        },
        {
            id: 'activity-2',
            userId: 'user-2',
            tenantId: 'tenant-1',
            tenantName: 'Acme Corporation',
            action: 'Export Data',
            resource: '/api/v1/employees',
            ipAddress: '192.168.1.100',
            timestamp: '2025-12-11T09:50:00Z',
            status: 'success',
        },
    ];

    const mockSecurityEvents: SecurityEvent[] = [
        {
            id: 'event-1',
            userId: 'user-2',
            tenantId: 'tenant-1',
            eventType: 'suspicious_activity',
            severity: 'high',
            description: 'Multiple logins from different geographic locations',
            ipAddress: '198.51.100.22',
            timestamp: '2025-12-09T03:15:00Z',
            resolved: false,
        },
    ];

    const mockCallbacks = {
        onUserSelect: vi.fn(),
        onSuspendUser: vi.fn(),
        onUnsuspendUser: vi.fn(),
        onFetchActivities: vi.fn().mockResolvedValue(mockActivities),
        onFetchSecurityEvents: vi.fn().mockResolvedValue(mockSecurityEvents),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render all users in the list', () => {
        render(<CrossTenantUserSearch users={mockUsers} />);

        expect(screen.getByText('John Smith')).toBeInTheDocument();
        expect(screen.getByText('Alex Suspicious')).toBeInTheDocument();
        expect(screen.getByText('Robert Wilson')).toBeInTheDocument();
    });

    it('should display security flag count', () => {
        render(<CrossTenantUserSearch users={mockUsers} />);

        expect(screen.getByText(/2 security flags/i)).toBeInTheDocument();
    });

    it('should display suspended user count', () => {
        render(<CrossTenantUserSearch users={mockUsers} />);

        expect(screen.getByText(/1 suspended/i)).toBeInTheDocument();
    });

    it('should filter users by search query', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} />);

        const searchInput = screen.getByPlaceholderText(/search/i);
        await user.type(searchInput, 'suspicious');

        await waitFor(() => {
            expect(screen.getByText('Alex Suspicious')).toBeInTheDocument();
            expect(screen.queryByText('John Smith')).not.toBeInTheDocument();
            expect(screen.queryByText('Robert Wilson')).not.toBeInTheDocument();
        });
    });

    it('should filter users by status', async () => {
        render(<CrossTenantUserSearch users={mockUsers} />);

        const statusSelect = screen.getByLabelText(/status/i);
        fireEvent.change(statusSelect, { target: { value: 'suspended' } });

        await waitFor(() => {
            expect(screen.getByText('Robert Wilson')).toBeInTheDocument();
            expect(screen.queryByText('John Smith')).not.toBeInTheDocument();
            expect(screen.queryByText('Alex Suspicious')).not.toBeInTheDocument();
        });
    });

    it('should show security flag indicator for flagged users', () => {
        render(<CrossTenantUserSearch users={mockUsers} />);

        const alexCard = screen.getByText('Alex Suspicious').closest('div');
        expect(within(alexCard!).getByText('SECURITY FLAG')).toBeInTheDocument();
    });

    it('should open user detail dialog when user is clicked', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} {...mockCallbacks} />);

        await user.click(screen.getByText('John Smith'));

        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(mockCallbacks.onUserSelect).toHaveBeenCalledWith('user-1');
        });
    });

    it('should display all tabs in user detail dialog', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} {...mockCallbacks} />);

        await user.click(screen.getByText('Alex Suspicious'));

        await waitFor(() => {
            expect(screen.getByRole('tab', { name: /overview/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /tenant memberships/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /activity log/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /security events/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /actions/i })).toBeInTheDocument();
        });
    });

    it('should fetch and display user activities when dialog opens', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} {...mockCallbacks} />);

        await user.click(screen.getByText('Alex Suspicious'));

        await waitFor(() => {
            expect(mockCallbacks.onFetchActivities).toHaveBeenCalledWith('user-2');
        });

        // Navigate to Activity Log tab
        const activityTab = screen.getByRole('tab', { name: /activity log/i });
        await user.click(activityTab);

        await waitFor(() => {
            expect(screen.getByText('Login')).toBeInTheDocument();
            expect(screen.getByText('Export Data')).toBeInTheDocument();
        });
    });

    it('should fetch and display security events when dialog opens', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} {...mockCallbacks} />);

        await user.click(screen.getByText('Alex Suspicious'));

        await waitFor(() => {
            expect(mockCallbacks.onFetchSecurityEvents).toHaveBeenCalledWith('user-2');
        });

        // Navigate to Security Events tab
        const securityTab = screen.getByRole('tab', { name: /security events/i });
        await user.click(securityTab);

        await waitFor(() => {
            expect(screen.getByText(/suspicious_activity/i)).toBeInTheDocument();
        });
    });

    it('should allow suspending an active user', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} {...mockCallbacks} />);

        // Open user detail
        await user.click(screen.getByText('John Smith'));

        // Navigate to Actions tab
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const actionsTab = screen.getByRole('tab', { name: /actions/i });
        await user.click(actionsTab);

        // Click suspend button
        const suspendButton = await screen.findByRole('button', { name: /suspend account/i });
        await user.click(suspendButton);

        // Fill in reason and confirm
        const reasonInput = screen.getByLabelText(/reason/i);
        await user.type(reasonInput, 'Security violation');

        const confirmButton = screen.getByRole('button', { name: /suspend account/i, hidden: true });
        await user.click(confirmButton);

        expect(mockCallbacks.onSuspendUser).toHaveBeenCalledWith('user-1', 'Security violation');
    });

    it('should allow unsuspending a suspended user', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} {...mockCallbacks} />);

        // Open user detail for suspended user
        await user.click(screen.getByText('Robert Wilson'));

        // Navigate to Actions tab
        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
        const actionsTab = screen.getByRole('tab', { name: /actions/i });
        await user.click(actionsTab);

        // Click reactivate button
        const reactivateButton = await screen.findByRole('button', { name: /reactivate account/i });
        await user.click(reactivateButton);

        expect(mockCallbacks.onUnsuspendUser).toHaveBeenCalledWith('user-3');
    });

    it('should display user global statistics', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} />);

        await user.click(screen.getByText('John Smith'));

        await waitFor(() => {
            const dialog = screen.getByRole('dialog');
            expect(within(dialog).getByText('450')).toBeInTheDocument();
            expect(within(dialog).getByText('125.0K')).toBeInTheDocument();
            expect(within(dialog).getByText('85 GB')).toBeInTheDocument();
        });
    });

    it('should display tenant memberships with roles', async () => {
        const user = userEvent.setup();
        render(<CrossTenantUserSearch users={mockUsers} />);

        await user.click(screen.getByText('Alex Suspicious'));

        await waitFor(() => {
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        const membershipsTab = screen.getByRole('tab', { name: /tenant memberships/i });
        await user.click(membershipsTab);

        await waitFor(() => {
            expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
            expect(screen.getByText('TechStart Inc')).toBeInTheDocument();
        });
    });
});
