import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider as PlatformThemeProvider } from '@ghatana/theme';
import { ForensicsDrillDown } from '@/components/audit/ForensicsDrillDown';
import { RelatedEventsPanel } from '@/components/audit/RelatedEventsPanel';
import { PermissionEditor } from '@/components/admin/PermissionEditor';
import { SSOConfigWizard } from '@/components/admin/SSOConfigWizard';
import { IncidentDashboard } from '@/components/admin/IncidentDashboard';
import { IncidentDetailPanel } from '@/components/admin/IncidentDetailPanel';
import type { AuditEntry } from '@/types/org.types';
import type { Incident } from '@/components/admin/IncidentDashboard';

const { mockSimulatePermission } = vi.hoisted(() => ({
    mockSimulatePermission: vi.fn(),
}));

vi.mock('@/hooks', async () => {
    const actual = await vi.importActual<typeof import('@/hooks')>('@/hooks');
    return {
        ...actual,
        useSimulatePermission: () => ({
            mutateAsync: mockSimulatePermission,
        }),
    };
});

/**
 * Admin Integration Tests
 *
 * Comprehensive tests for all admin features:
 * - Audit Forensics (ForensicsDrillDown, RelatedEventsPanel)
 * - Permission Management (PermissionEditor)
 * - SSO Configuration (SSOConfigWizard)
 * - Incident Management (IncidentDashboard, IncidentDetailPanel)
 */

// ============================================================================
// Test Data
// ============================================================================

const mockAuditEntry: AuditEntry = {
    id: '1',
    timestamp: new Date(Date.now() - 3600000),
    actor: 'user-123',
    action: 'org:restructure',
    target: {
        type: 'team',
        id: 'team-456',
        name: 'Engineering Team',
    },
    changes: {
        division: {
            before: 'div-old',
            after: 'div-new',
        },
    },
    metadata: {
        from: 'div-old',
        to: 'div-new',
    },
};

const mockAuditEntries: AuditEntry[] = [
    mockAuditEntry,
    {
        id: '2',
        timestamp: new Date(Date.now() - 1800000),
        actor: 'user-123',
        action: 'role:update',
        target: {
            type: 'role',
            id: 'role-admin',
            name: 'Admin Role',
        },
        changes: {
            permissions: {
                before: ['users.read'],
                after: ['users.read', 'users.write'],
            },
        },
        metadata: {
            relatedTo: '1',
        },
    },
    {
        id: '3',
        timestamp: new Date(Date.now() - 900000),
        actor: 'user-456',
        action: 'team:create',
        target: {
            type: 'team',
            id: 'team-789',
            name: 'Mobile Team',
        },
        changes: {
            created: {
                before: null,
                after: { name: 'Mobile Team' },
            },
        },
    },
];

const mockRoles = [
    {
        id: 'role-admin',
        name: 'Admin',
        description: 'Full system access',
        permissions: ['tenants.read', 'tenants.write', 'users.read', 'users.write'],
        isSystemRole: true,
    },
    {
        id: 'role-manager',
        name: 'Manager',
        description: 'Team management',
        permissions: ['teams.read', 'teams.write'],
        inheritsFrom: 'role-admin',
    },
    {
        id: 'role-user',
        name: 'User',
        description: 'Basic access',
        permissions: ['tenants.read'],
    },
];

const mockUsers = [
    { id: 'user-1', name: 'Alice Admin', email: 'alice@example.com', roles: ['role-admin'] },
    { id: 'user-2', name: 'Bob Manager', email: 'bob@example.com', roles: ['role-manager'] },
];

const mockPermissions = [
    { id: 'tenants.read', resource: 'tenants', action: 'read', description: 'View tenants' },
    { id: 'tenants.write', resource: 'tenants', action: 'write', description: 'Update tenants' },
    { id: 'users.read', resource: 'users', action: 'read', description: 'View users' },
    { id: 'users.write', resource: 'users', action: 'write', description: 'Update users' },
];

const mockIncident: Incident = {
    id: 'INC-001',
    title: 'Unauthorized Access Attempt',
    description: 'Multiple failed login attempts',
    priority: 'critical',
    status: 'investigating',
    category: 'security',
    assignedTo: 'admin-001',
    reportedBy: 'system',
    reportedAt: new Date(Date.now() - 3600000).toISOString(),
    updatedAt: new Date(Date.now() - 1800000).toISOString(),
    affectedUsers: 1,
    tags: ['security', 'auth'],
    timeline: [
        {
            id: 't1',
            timestamp: new Date(Date.now() - 3600000).toISOString(),
            user: 'system',
            action: 'Incident Created',
        },
    ],
    comments: [
        {
            id: 'c1',
            user: 'admin-001',
            timestamp: new Date(Date.now() - 1800000).toISOString(),
            content: 'Investigating source IP',
        },
    ],
};

const createTestQueryClient = () =>
    new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });

const renderWithProviders = (component: React.ReactElement) => {
    const queryClient = createTestQueryClient();
    return render(
        <QueryClientProvider client={queryClient}>
            <PlatformThemeProvider
                defaultTheme="light"
                enableStorage={false}
                enableSystem={false}
                attribute="class"
            >
                {component}
            </PlatformThemeProvider>
        </QueryClientProvider>
    );
};

const getActionButton = (name: RegExp) => {
    const button = screen
        .getAllByRole('button', { name })
        .find((element) => element.tagName === 'BUTTON');

    if (!button) {
        throw new Error(`Could not find button matching ${name}`);
    }

    return button as HTMLButtonElement;
};

// ============================================================================
// ForensicsDrillDown Tests
// ============================================================================

describe('ForensicsDrillDown', () => {
    const mockHandlers = {
        onClose: vi.fn(),
        onRevert: vi.fn(),
        onLockUser: vi.fn(),
        onEscalate: vi.fn(),
        onMarkReviewed: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render anomaly detection panel', () => {
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        expect(screen.getByText(/Anomaly Detection/i)).toBeInTheDocument();
        expect(screen.getByText(/LOW RISK/i)).toBeInTheDocument();
    });

    it('should calculate anomaly score', () => {
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        expect(screen.getByText(/Anomaly Score/i)).toBeInTheDocument();
    });

    it('should display user context information', () => {
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        expect(screen.getByText(/User Context/i)).toBeInTheDocument();
        expect(screen.getByText(/user-123/i)).toBeInTheDocument();
    });

    it('should toggle between visual and JSON view', async () => {
        const user = userEvent.setup();
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const jsonButton = screen.getByRole('button', { name: /JSON/i });
        await user.click(jsonButton);

        expect(screen.getByText(/"actor"/i)).toBeInTheDocument();
    });

    it('should call onRevert when revert button clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const revertButton = screen.getByRole('button', { name: /Revert Change/i });
        await user.click(revertButton);

        expect(mockHandlers.onRevert).toHaveBeenCalledWith(mockAuditEntry.id);
    });

    it('should call onLockUser for high-risk entries', async () => {
        const user = userEvent.setup();
        const highRiskEntry = {
            ...mockAuditEntry,
            timestamp: new Date(new Date().setHours(2, 0, 0, 0)),
            action: 'role:update',
            metadata: {
                rapidChanges: true,
                firstTimeAccess: true,
            },
        };

        renderWithProviders(<ForensicsDrillDown entry={highRiskEntry} {...mockHandlers} />);

        const lockButton = screen.getByRole('button', { name: /Lock User/i });
        await user.click(lockButton);

        expect(mockHandlers.onLockUser).toHaveBeenCalledWith(highRiskEntry.actor);
    });

    it('should call onEscalate for high-risk incidents', async () => {
        const user = userEvent.setup();
        const highRiskEntry = {
            ...mockAuditEntry,
            timestamp: new Date(new Date().setHours(3, 0, 0, 0)),
            action: 'role:update',
            metadata: {
                rapidChanges: true,
                firstTimeAccess: true,
            },
        };

        renderWithProviders(<ForensicsDrillDown entry={highRiskEntry} {...mockHandlers} />);

        const escalateButton = screen.getByRole('button', { name: /Escalate/i });
        await user.click(escalateButton);

        expect(mockHandlers.onEscalate).toHaveBeenCalledWith(highRiskEntry.id);
    });

    it('should call onMarkReviewed when reviewed', async () => {
        const user = userEvent.setup();
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const reviewedButton = screen.getByRole('button', { name: /Mark as Reviewed/i });
        await user.click(reviewedButton);

        expect(mockHandlers.onMarkReviewed).toHaveBeenCalledWith(mockAuditEntry.id);
    });

    it('should call onClose when close button clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const closeButton = screen.getByRole('button', { name: /Close/i });
        await user.click(closeButton);

        expect(mockHandlers.onClose).toHaveBeenCalled();
    });
});

// ============================================================================
// RelatedEventsPanel Tests
// ============================================================================

describe('RelatedEventsPanel', () => {
    const mockOnEventClick = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render all event groups', () => {
        renderWithProviders(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        expect(screen.getByRole('button', { name: /Timeline/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Same User/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Same Resource/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Correlated/i })).toBeInTheDocument();
    });

    it('should show correct count for temporal events', () => {
        renderWithProviders(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        const temporalTab = screen.getByRole('button', { name: /Timeline/i });
        expect(temporalTab).toHaveTextContent(/\d+/);
    });

    it('should filter events by same actor', () => {
        renderWithProviders(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        const actorTab = screen.getByRole('button', { name: /Same User/i });
        fireEvent.click(actorTab);

        expect(screen.getByText(/role update/i)).toBeInTheDocument();
    });

    it('should call onEventClick when event clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        const actorTab = screen.getByRole('button', { name: /Same User/i });
        await user.click(actorTab);

        const relatedEvent = screen.getByText(/role update/i);
        await user.click(relatedEvent);

        expect(mockOnEventClick).toHaveBeenCalled();
    });

    it('should show pattern detection warning', () => {
        const manyEntries = Array.from({ length: 5 }, (_, i) => ({
            ...mockAuditEntry,
            id: `entry-${i}`,
            timestamp: new Date(Date.now() - i * 600000),
        }));

        renderWithProviders(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={manyEntries}
                onEventClick={mockOnEventClick}
            />
        );

        expect(screen.getByText(/Pattern Detected/i)).toBeInTheDocument();
    });

    it('should display summary stats', () => {
        renderWithProviders(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        expect(screen.getByText(/Related Events Summary/i)).toBeInTheDocument();
    });
});

// ============================================================================
// PermissionEditor Tests
// ============================================================================

describe('PermissionEditor', () => {
    const mockHandlers = {
        onRoleUpdate: vi.fn(),
        onRoleCreate: vi.fn(),
        onRoleDelete: vi.fn(),
        onRoleAssign: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        mockSimulatePermission.mockResolvedValue({
            userId: 'user-1',
            permissionId: 'tenants.read',
            granted: true,
            matchedRoles: [
                {
                    roleId: 'role-admin',
                    roleName: 'Admin',
                    roleSlug: 'admin',
                },
            ],
            allRoles: [],
        });
    });

    it('should render permission matrix tab', () => {
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        expect(screen.getByText(/Permission Matrix/i)).toBeInTheDocument();
    });

    it('should display all roles', () => {
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        expect(screen.getByText('Admin')).toBeInTheDocument();
        expect(screen.getByText('Manager')).toBeInTheDocument();
        expect(screen.getByText('User')).toBeInTheDocument();
    });

    it('should select a role and show permissions', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        await user.click(screen.getByText('Admin'));

        expect(screen.getByRole('checkbox', { name: /View tenants/i })).toBeInTheDocument();
    });

    it('should toggle permission checkbox', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const checkbox = screen.getByRole('checkbox', { name: /View tenants/i });
        await user.click(checkbox);

        expect(checkbox).not.toBeChecked();
    });

    it('should call onRoleUpdate when save clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const saveButton = screen.getByRole('button', { name: /Save/i });
        await user.click(saveButton);

        expect(mockHandlers.onRoleUpdate).toHaveBeenCalled();
    });

    it('should create new role', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const nameInput = screen.getByPlaceholderText(/New role name/i);
        await user.type(nameInput, 'Developer');

        const createButton = screen.getByRole('button', { name: /Create Role/i });
        await user.click(createButton);

        expect(mockHandlers.onRoleCreate).toHaveBeenCalledWith(
            expect.objectContaining({
                name: 'Developer',
            })
        );
    });

    it('should switch to testing tab', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const testingTab = screen.getByText(/Testing Panel/i);
        await user.click(testingTab);

        expect(screen.getByText(/Permission Testing/i)).toBeInTheDocument();
    });

    it('should test permission and show result', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const testingTab = screen.getByText(/Testing Panel/i);
        await user.click(testingTab);

        const userIdInput = screen.getByLabelText(/User ID/i);
        await user.type(userIdInput, 'user-1');

        const permissionSelect = screen.getByLabelText(/Permission ID/i);
        await user.selectOptions(permissionSelect, 'tenants.read');

        const testButton = screen.getByRole('button', { name: /Test Permission/i });
        await user.click(testButton);

        await waitFor(() => {
            expect(mockSimulatePermission).toHaveBeenCalledWith({
                userId: 'user-1',
                permissionId: 'tenants.read',
            });
        });

        expect(screen.getByText(/Permission Granted/i)).toBeInTheDocument();
    });

    it('should switch to assignment tab', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const assignmentTab = screen.getByText(/Role Assignment/i);
        await user.click(assignmentTab);

        expect(screen.getByText(/Assign Roles to Users/i)).toBeInTheDocument();
    });

    it('should assign roles to user', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const assignmentTab = screen.getByText(/Role Assignment/i);
        await user.click(assignmentTab);

        const userSelect = screen.getByRole('combobox');
        await user.selectOptions(userSelect, 'user-1');

        const adminCheckbox = screen.getByLabelText(/Admin/i);
        await user.click(adminCheckbox);

        const assignButton = screen.getByRole('button', { name: /Assign Roles/i });
        await user.click(assignButton);

        expect(mockHandlers.onRoleAssign).toHaveBeenCalledWith('user-1', expect.arrayContaining(['role-admin']));
    });
});

// ============================================================================
// SSOConfigWizard Tests
// ============================================================================

describe('SSOConfigWizard', () => {
    const mockHandlers = {
        onComplete: vi.fn(),
        onCancel: vi.fn(),
        onTestConnection: vi.fn().mockResolvedValue({ success: true }),
        onTestLogin: vi.fn().mockResolvedValue({ success: true, user: { email: 'test@example.com' } }),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render provider selection step', () => {
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        expect(screen.getByText(/Select Your SSO Provider/i)).toBeInTheDocument();
        expect(screen.getByText('Okta')).toBeInTheDocument();
        expect(screen.getByText('Azure Active Directory')).toBeInTheDocument();
        expect(screen.getByText('Google Workspace')).toBeInTheDocument();
    });

    it('should select provider and enable next button', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        await user.click(screen.getByText('Okta'));

        const nextButton = screen.getByRole('button', { name: /Next/i });
        expect(nextButton).not.toBeDisabled();
    });

    it('should navigate to configuration step', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        await user.click(screen.getByText('Okta'));
        const nextButton = screen.getByRole('button', { name: /Next/i });
        await user.click(nextButton);

        expect(screen.getByText(/Configure Okta SSO/i)).toBeInTheDocument();
    });

    it('should fill Okta configuration', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        await user.click(screen.getByText('Okta'));
        await user.click(screen.getByRole('button', { name: /Next/i }));

        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');

        const nextButton = screen.getByRole('button', { name: /Next/i });
        expect(nextButton).not.toBeDisabled();
    });

    it('should test connection', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        await user.click(screen.getByText('Okta'));
        await user.click(screen.getByRole('button', { name: /Next/i }));

        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Test connection
        const testButton = getActionButton(/Test Connection/i);
        await user.click(testButton);

        await waitFor(() => {
            expect(mockHandlers.onTestConnection).toHaveBeenCalled();
        });
    });

    it('should test login after connection success', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        await user.click(screen.getByText('Okta'));
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));

        await user.click(getActionButton(/Test Connection/i));
        await waitFor(() => expect(mockHandlers.onTestConnection).toHaveBeenCalled());

        const loginButton = screen.getByRole('button', { name: /Test Login/i });
        await user.click(loginButton);

        await waitFor(() => {
            expect(mockHandlers.onTestLogin).toHaveBeenCalled();
        });
    });

    it('should configure rollout settings', async () => {
        const user = userEvent.setup();
        const groups = [
            { id: 'g1', name: 'Engineering' },
            { id: 'g2', name: 'Product' },
        ];

        renderWithProviders(<SSOConfigWizard {...mockHandlers} availableGroups={groups} />);

        await user.click(screen.getByText('Okta'));
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.click(getActionButton(/Test Connection/i));
        await waitFor(() => expect(mockHandlers.onTestConnection).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Configure rollout
        expect(screen.getByText(/Configure Rollout Settings/i)).toBeInTheDocument();
    });

    it('should complete wizard and call onComplete', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        await user.click(screen.getByText('Okta'));
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.click(getActionButton(/Test Connection/i));
        await waitFor(() => expect(mockHandlers.onTestConnection).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.click(screen.getByLabelText(/Enable for all users/i));
        await user.click(screen.getByRole('button', { name: /Next/i }));

        const enableButton = screen.getByRole('button', { name: /Enable SSO/i });
        await user.click(enableButton);

        expect(mockHandlers.onComplete).toHaveBeenCalled();
    });

    it('should call onCancel when cancel clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(<SSOConfigWizard {...mockHandlers} />);

        const cancelButton = screen.getByRole('button', { name: /Cancel/i });
        await user.click(cancelButton);

        expect(mockHandlers.onCancel).toHaveBeenCalled();
    });
});

// ============================================================================
// IncidentDashboard Tests
// ============================================================================

describe('IncidentDashboard', () => {
    const mockHandlers = {
        onCreateIncident: vi.fn(),
        onUpdateIncident: vi.fn(),
        onAssignIncident: vi.fn(),
        onResolveIncident: vi.fn(),
        onAddComment: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render dashboard with statistics', () => {
        renderWithProviders(<IncidentDashboard {...mockHandlers} />);

        expect(screen.getByText(/Incident Management/i)).toBeInTheDocument();
        expect(screen.getByText(/Total Incidents/i)).toBeInTheDocument();
        expect(screen.getAllByText(/^Critical$/i).length).toBeGreaterThan(0);
        expect(screen.getByText(/Active/i)).toBeInTheDocument();
    });

    it('should display create incident button', () => {
        renderWithProviders(<IncidentDashboard {...mockHandlers} />);

        const createButton = screen.getByRole('button', { name: /Create Incident/i });
        expect(createButton).toBeInTheDocument();
    });

    it('should call onCreateIncident when button clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDashboard {...mockHandlers} />);

        const createButton = screen.getByRole('button', { name: /Create Incident/i });
        await user.click(createButton);

        expect(mockHandlers.onCreateIncident).toHaveBeenCalled();
    });

    it('should filter incidents by search term', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDashboard incidents={[mockIncident]} {...mockHandlers} />);

        const searchInput = screen.getByPlaceholderText(/Search incidents/i);
        await user.type(searchInput, 'Unauthorized');

        expect(screen.getByText(/Unauthorized Access Attempt/i)).toBeInTheDocument();
    });

    it('should filter incidents by status', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDashboard incidents={[mockIncident]} {...mockHandlers} />);

        const statusSelect = screen.getByLabelText(/Status/i);
        await user.selectOptions(statusSelect, 'investigating');

        expect(screen.getByText(/Unauthorized Access Attempt/i)).toBeInTheDocument();
    });

    it('should select incident and show detail panel', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDashboard incidents={[mockIncident]} {...mockHandlers} />);

        await user.click(screen.getByText(/Unauthorized Access Attempt/i));

        expect(screen.getByLabelText(/Assign To/i)).toBeInTheDocument();
    });
});

// ============================================================================
// IncidentDetailPanel Tests
// ============================================================================

describe('IncidentDetailPanel', () => {
    const mockHandlers = {
        onClose: vi.fn(),
        onUpdateStatus: vi.fn(),
        onAssign: vi.fn(),
        onResolve: vi.fn(),
        onAddComment: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render incident details', () => {
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        expect(screen.getByText('INC-001')).toBeInTheDocument();
        expect(screen.getByText(/Unauthorized Access Attempt/i)).toBeInTheDocument();
        expect(screen.getByText(/Multiple failed login attempts/i)).toBeInTheDocument();
    });

    it('should display incident metadata', () => {
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        expect(screen.getByText(/Category/i)).toBeInTheDocument();
        expect(screen.getAllByText(/security/i).length).toBeGreaterThan(0);
        expect(screen.getByText(/Reported By/i)).toBeInTheDocument();
    });

    it('should call onUpdateStatus when status changed', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const statusSelect = screen.getByLabelText(/Status/i);
        await user.selectOptions(statusSelect, 'resolved');

        expect(mockHandlers.onUpdateStatus).toHaveBeenCalledWith('resolved');
    });

    it('should call onAssign when user assigned', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const assignSelect = screen.getByLabelText(/Assign To/i);
        await user.selectOptions(assignSelect, 'admin-002');

        expect(mockHandlers.onAssign).toHaveBeenCalledWith('admin-002');
    });

    it('should add comment', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const commentsTab = screen.getByText(/Comments/i);
        await user.click(commentsTab);

        const commentInput = screen.getByPlaceholderText(/Add a comment/i);
        await user.type(commentInput, 'This is a test comment');

        const addButton = screen.getByRole('button', { name: /Add Comment/i });
        await user.click(addButton);

        expect(mockHandlers.onAddComment).toHaveBeenCalledWith('This is a test comment');
    });

    it('should resolve incident', async () => {
        const user = userEvent.setup();
        const openIncident = { ...mockIncident, status: 'open' as const };
        renderWithProviders(<IncidentDetailPanel incident={openIncident} {...mockHandlers} />);

        const resolutionInput = screen.getByPlaceholderText(/Resolution notes/i);
        await user.type(resolutionInput, 'Issue resolved by blocking IP');

        const resolveButton = screen.getByRole('button', { name: /Mark as Resolved/i });
        await user.click(resolveButton);

        expect(mockHandlers.onResolve).toHaveBeenCalledWith('Issue resolved by blocking IP');
    });

    it('should display timeline', async () => {
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        expect(screen.getByText('Incident Created')).toBeInTheDocument();
    });

    it('should call onClose when close clicked', async () => {
        const user = userEvent.setup();
        renderWithProviders(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const closeButton = screen.getByRole('button', { name: /Close incident/i });
        await user.click(closeButton);

        expect(mockHandlers.onClose).toHaveBeenCalled();
    });
});
