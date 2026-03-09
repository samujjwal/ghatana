import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForensicsDrillDown } from '@/components/audit/ForensicsDrillDown';
import { RelatedEventsPanel } from '@/components/audit/RelatedEventsPanel';
import { PermissionEditor } from '@/components/admin/PermissionEditor';
import { SSOConfigWizard } from '@/components/admin/SSOConfigWizard';
import { IncidentDashboard } from '@/components/admin/IncidentDashboard';
import { IncidentDetailPanel } from '@/components/admin/IncidentDetailPanel';
import type { AuditEntry } from '@/types/audit';
import type { Role, User, Permission } from '@/types/permissions';
import type { Incident } from '@/components/admin/IncidentDashboard';

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
    timestamp: new Date(Date.now() - 3600000).toISOString(), // 1 hour ago
    userId: 'user-123',
    action: 'org.restructure',
    details: 'Moved Engineering Team to Product Division',
    targetType: 'team',
    targetId: 'team-456',
    metadata: {
        from: 'div-old',
        to: 'div-new',
    },
};

const mockAuditEntries: AuditEntry[] = [
    mockAuditEntry,
    {
        id: '2',
        timestamp: new Date(Date.now() - 1800000).toISOString(), // 30 min ago
        userId: 'user-123',
        action: 'role.update',
        details: 'Updated Admin role permissions',
        targetType: 'role',
        targetId: 'role-admin',
    },
    {
        id: '3',
        timestamp: new Date(Date.now() - 900000).toISOString(), // 15 min ago
        userId: 'user-456',
        action: 'team.create',
        details: 'Created Mobile Team',
        targetType: 'team',
        targetId: 'team-789',
    },
];

const mockRoles: Role[] = [
    {
        id: 'role-admin',
        name: 'Admin',
        description: 'Full system access',
        permissions: ['tenants.read', 'tenants.write', 'users.read', 'users.write'],
        isSystem: true,
    },
    {
        id: 'role-manager',
        name: 'Manager',
        description: 'Team management',
        permissions: ['teams.read', 'teams.write'],
        parentRoleId: 'role-admin',
    },
    {
        id: 'role-user',
        name: 'User',
        description: 'Basic access',
        permissions: ['tenants.read'],
    },
];

const mockUsers: User[] = [
    { id: 'user-1', name: 'Alice Admin', email: 'alice@example.com' },
    { id: 'user-2', name: 'Bob Manager', email: 'bob@example.com' },
];

const mockPermissions: Permission[] = [
    { id: 'tenants.read', name: 'Read Tenants', resource: 'tenants' },
    { id: 'tenants.write', name: 'Write Tenants', resource: 'tenants' },
    { id: 'users.read', name: 'Read Users', resource: 'users' },
    { id: 'users.write', name: 'Write Users', resource: 'users' },
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
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        expect(screen.getByText(/Anomaly Detection/i)).toBeInTheDocument();
        expect(screen.getByText(/Risk Level/i)).toBeInTheDocument();
    });

    it('should calculate anomaly score', () => {
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        // Score should be visible
        expect(screen.getByText(/Score:/i)).toBeInTheDocument();
    });

    it('should display user context information', () => {
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        expect(screen.getByText(/User Context/i)).toBeInTheDocument();
        expect(screen.getByText(/user-123/i)).toBeInTheDocument();
    });

    it('should toggle between visual and JSON view', async () => {
        const user = userEvent.setup();
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const jsonButton = screen.getByRole('button', { name: /JSON/i });
        await user.click(jsonButton);

        // JSON view should be visible
        expect(screen.getByText(/"userId"/i)).toBeInTheDocument();
    });

    it('should call onRevert when revert button clicked', async () => {
        const user = userEvent.setup();
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const revertButton = screen.getByRole('button', { name: /Revert Change/i });
        await user.click(revertButton);

        expect(mockHandlers.onRevert).toHaveBeenCalledWith(mockAuditEntry.id);
    });

    it('should call onLockUser for high-risk entries', async () => {
        const user = userEvent.setup();
        const highRiskEntry = {
            ...mockAuditEntry,
            timestamp: new Date(new Date().setHours(2)).toISOString(), // 2 AM - off hours
        };

        render(<ForensicsDrillDown entry={highRiskEntry} {...mockHandlers} />);

        const lockButton = screen.getByRole('button', { name: /Lock User/i });
        await user.click(lockButton);

        expect(mockHandlers.onLockUser).toHaveBeenCalledWith(highRiskEntry.userId);
    });

    it('should call onEscalate for critical incidents', async () => {
        const user = userEvent.setup();
        const criticalEntry = {
            ...mockAuditEntry,
            timestamp: new Date(new Date().setHours(3)).toISOString(), // Off hours
            action: 'role.update', // Sensitive operation
        };

        render(<ForensicsDrillDown entry={criticalEntry} {...mockHandlers} />);

        const escalateButton = screen.getByRole('button', { name: /Escalate/i });
        await user.click(escalateButton);

        expect(mockHandlers.onEscalate).toHaveBeenCalledWith(criticalEntry.id);
    });

    it('should call onMarkReviewed when reviewed', async () => {
        const user = userEvent.setup();
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

        const reviewedButton = screen.getByRole('button', { name: /Mark as Reviewed/i });
        await user.click(reviewedButton);

        expect(mockHandlers.onMarkReviewed).toHaveBeenCalledWith(mockAuditEntry.id);
    });

    it('should call onClose when close button clicked', async () => {
        const user = userEvent.setup();
        render(<ForensicsDrillDown entry={mockAuditEntry} {...mockHandlers} />);

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
        render(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        expect(screen.getByText(/Temporal/i)).toBeInTheDocument();
        expect(screen.getByText(/Same Actor/i)).toBeInTheDocument();
        expect(screen.getByText(/Same Resource/i)).toBeInTheDocument();
        expect(screen.getByText(/Correlated/i)).toBeInTheDocument();
    });

    it('should show correct count for temporal events', () => {
        render(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        // Should find events within 1 hour
        const temporalTab = screen.getByText(/Temporal/i).closest('button');
        expect(temporalTab).toHaveTextContent(/\d+/); // Should show count
    });

    it('should filter events by same actor', () => {
        render(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        // Click Same Actor tab
        const actorTab = screen.getByText(/Same Actor/i);
        fireEvent.click(actorTab);

        // Should show events by user-123
        expect(screen.getByText(/role\.update/i)).toBeInTheDocument();
    });

    it('should call onEventClick when event clicked', async () => {
        const user = userEvent.setup();
        render(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        // Click Same Actor tab
        const actorTab = screen.getByText(/Same Actor/i);
        await user.click(actorTab);

        // Click on related event
        const relatedEvent = screen.getByText(/role\.update/i);
        await user.click(relatedEvent);

        expect(mockOnEventClick).toHaveBeenCalled();
    });

    it('should show pattern detection warning', () => {
        const manyEntries = Array.from({ length: 5 }, (_, i) => ({
            ...mockAuditEntry,
            id: `entry-${i}`,
            timestamp: new Date(Date.now() - i * 600000).toISOString(),
        }));

        render(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={manyEntries}
                onEventClick={mockOnEventClick}
            />
        );

        // Should show pattern warning if 3+ events
        expect(screen.getByText(/Pattern Detected/i)).toBeInTheDocument();
    });

    it('should display summary stats', () => {
        render(
            <RelatedEventsPanel
                currentEntry={mockAuditEntry}
                allEntries={mockAuditEntries}
                onEventClick={mockOnEventClick}
            />
        );

        expect(screen.getByText(/Summary/i)).toBeInTheDocument();
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
        onTestPermission: vi.fn().mockResolvedValue(true),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render permission matrix tab', () => {
        render(
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
        render(
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
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const adminRole = screen.getByText('Admin').closest('div');
        await user.click(adminRole!);

        // Permission checkboxes should be visible
        expect(screen.getByLabelText(/Read Tenants/i)).toBeInTheDocument();
    });

    it('should toggle permission checkbox', async () => {
        const user = userEvent.setup();
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        // Select Admin role
        const adminRole = screen.getByText('Admin').closest('div');
        await user.click(adminRole!);

        // Toggle a permission
        const checkbox = screen.getByLabelText(/Read Tenants/i);
        await user.click(checkbox);

        // Should update selected permissions state
        expect(checkbox).not.toBeChecked();
    });

    it('should call onRoleUpdate when save clicked', async () => {
        const user = userEvent.setup();
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        // Select Admin role
        const adminRole = screen.getByText('Admin').closest('div');
        await user.click(adminRole!);

        // Click Save
        const saveButton = screen.getByRole('button', { name: /Save/i });
        await user.click(saveButton);

        expect(mockHandlers.onRoleUpdate).toHaveBeenCalled();
    });

    it('should create new role', async () => {
        const user = userEvent.setup();
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        // Enter new role name
        const nameInput = screen.getByPlaceholderText(/Role name/i);
        await user.type(nameInput, 'Developer');

        // Click Create
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
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        const testingTab = screen.getByText(/Testing Panel/i);
        await user.click(testingTab);

        expect(screen.getByText(/Test User Permissions/i)).toBeInTheDocument();
    });

    it('should test permission and show result', async () => {
        const user = userEvent.setup();
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        // Switch to testing tab
        const testingTab = screen.getByText(/Testing Panel/i);
        await user.click(testingTab);

        // Enter user ID
        const userIdInput = screen.getByLabelText(/User ID/i);
        await user.type(userIdInput, 'user-1');

        // Select permission
        const permissionSelect = screen.getByLabelText(/Permission/i);
        await user.click(permissionSelect);
        const permission = screen.getByText('Read Tenants');
        await user.click(permission);

        // Click Test
        const testButton = screen.getByRole('button', { name: /Test Permission/i });
        await user.click(testButton);

        await waitFor(() => {
            expect(mockHandlers.onTestPermission).toHaveBeenCalledWith('user-1', 'tenants.read');
        });
    });

    it('should switch to assignment tab', async () => {
        const user = userEvent.setup();
        render(
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
        render(
            <PermissionEditor
                roles={mockRoles}
                users={mockUsers}
                permissions={mockPermissions}
                {...mockHandlers}
            />
        );

        // Switch to assignment tab
        const assignmentTab = screen.getByText(/Role Assignment/i);
        await user.click(assignmentTab);

        // Select user
        const userSelect = screen.getByLabelText(/Select User/i);
        await user.click(userSelect);
        const userOption = screen.getByText('Alice Admin');
        await user.click(userOption);

        // Select roles
        const adminCheckbox = screen.getByLabelText(/Admin/i);
        await user.click(adminCheckbox);

        // Click Assign
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
        render(<SSOConfigWizard {...mockHandlers} />);

        expect(screen.getByText(/Select Your SSO Provider/i)).toBeInTheDocument();
        expect(screen.getByText('Okta')).toBeInTheDocument();
        expect(screen.getByText('Azure Active Directory')).toBeInTheDocument();
        expect(screen.getByText('Google Workspace')).toBeInTheDocument();
    });

    it('should select provider and enable next button', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

        const oktaCard = screen.getByText('Okta').closest('div');
        await user.click(oktaCard!);

        const nextButton = screen.getByRole('button', { name: /Next/i });
        expect(nextButton).not.toBeDisabled();
    });

    it('should navigate to configuration step', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

        // Select Okta
        const oktaCard = screen.getByText('Okta').closest('div');
        await user.click(oktaCard!);

        // Click Next
        const nextButton = screen.getByRole('button', { name: /Next/i });
        await user.click(nextButton);

        expect(screen.getByText(/Configure Okta SSO/i)).toBeInTheDocument();
    });

    it('should fill Okta configuration', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

        // Select Okta and proceed
        const oktaCard = screen.getByText('Okta').closest('div');
        await user.click(oktaCard!);
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Fill configuration
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');

        const nextButton = screen.getByRole('button', { name: /Next/i });
        expect(nextButton).not.toBeDisabled();
    });

    it('should test connection', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

        // Navigate to testing step
        await user.click(screen.getByText('Okta').closest('div')!);
        await user.click(screen.getByRole('button', { name: /Next/i }));

        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Test connection
        const testButton = screen.getByRole('button', { name: /Test Connection/i });
        await user.click(testButton);

        await waitFor(() => {
            expect(mockHandlers.onTestConnection).toHaveBeenCalled();
        });
    });

    it('should test login after connection success', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

        // Navigate and configure
        await user.click(screen.getByText('Okta').closest('div')!);
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Test connection first
        await user.click(screen.getByRole('button', { name: /Test Connection/i }));
        await waitFor(() => expect(mockHandlers.onTestConnection).toHaveBeenCalled());

        // Test login
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

        render(<SSOConfigWizard {...mockHandlers} availableGroups={groups} />);

        // Navigate to rollout step
        await user.click(screen.getByText('Okta').closest('div')!);
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.click(screen.getByRole('button', { name: /Test Connection/i }));
        await waitFor(() => expect(mockHandlers.onTestConnection).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Configure rollout
        expect(screen.getByText(/Configure Rollout Settings/i)).toBeInTheDocument();
    });

    it('should complete wizard and call onComplete', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

        // Full workflow
        await user.click(screen.getByText('Okta').closest('div')!);
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.type(screen.getByLabelText(/Okta Domain/i), 'example.okta.com');
        await user.type(screen.getByLabelText(/Client ID/i), 'client-123');
        await user.type(screen.getByLabelText(/Client Secret/i), 'secret-456');
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.click(screen.getByRole('button', { name: /Test Connection/i }));
        await waitFor(() => expect(mockHandlers.onTestConnection).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /Next/i }));
        await user.click(screen.getByRole('button', { name: /Next/i }));

        // Final step - enable SSO
        const enableButton = screen.getByRole('button', { name: /Enable SSO/i });
        await user.click(enableButton);

        expect(mockHandlers.onComplete).toHaveBeenCalled();
    });

    it('should call onCancel when cancel clicked', async () => {
        const user = userEvent.setup();
        render(<SSOConfigWizard {...mockHandlers} />);

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
        render(<IncidentDashboard {...mockHandlers} />);

        expect(screen.getByText(/Incident Management/i)).toBeInTheDocument();
        expect(screen.getByText(/Total Incidents/i)).toBeInTheDocument();
        expect(screen.getByText(/Critical/i)).toBeInTheDocument();
        expect(screen.getByText(/Active/i)).toBeInTheDocument();
    });

    it('should display create incident button', () => {
        render(<IncidentDashboard {...mockHandlers} />);

        const createButton = screen.getByRole('button', { name: /Create Incident/i });
        expect(createButton).toBeInTheDocument();
    });

    it('should call onCreateIncident when button clicked', async () => {
        const user = userEvent.setup();
        render(<IncidentDashboard {...mockHandlers} />);

        const createButton = screen.getByRole('button', { name: /Create Incident/i });
        await user.click(createButton);

        expect(mockHandlers.onCreateIncident).toHaveBeenCalled();
    });

    it('should filter incidents by search term', async () => {
        const user = userEvent.setup();
        render(<IncidentDashboard incidents={[mockIncident]} {...mockHandlers} />);

        const searchInput = screen.getByPlaceholderText(/Search incidents/i);
        await user.type(searchInput, 'Unauthorized');

        expect(screen.getByText(/Unauthorized Access Attempt/i)).toBeInTheDocument();
    });

    it('should filter incidents by status', async () => {
        const user = userEvent.setup();
        render(<IncidentDashboard incidents={[mockIncident]} {...mockHandlers} />);

        const statusSelect = screen.getByLabelText(/Status/i);
        await user.click(statusSelect);
        await user.click(screen.getByText('Investigating'));

        expect(screen.getByText(/Unauthorized Access Attempt/i)).toBeInTheDocument();
    });

    it('should select incident and show detail panel', async () => {
        const user = userEvent.setup();
        render(<IncidentDashboard incidents={[mockIncident]} {...mockHandlers} />);

        const incidentCard = screen.getByText(/Unauthorized Access Attempt/i).closest('div');
        await user.click(incidentCard!);

        // Detail panel should be visible
        expect(screen.getByText(/INC-001/i)).toBeInTheDocument();
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
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        expect(screen.getByText('INC-001')).toBeInTheDocument();
        expect(screen.getByText(/Unauthorized Access Attempt/i)).toBeInTheDocument();
        expect(screen.getByText(/Multiple failed login attempts/i)).toBeInTheDocument();
    });

    it('should display incident metadata', () => {
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        expect(screen.getByText(/Category/i)).toBeInTheDocument();
        expect(screen.getByText(/security/i)).toBeInTheDocument();
        expect(screen.getByText(/Reported By/i)).toBeInTheDocument();
    });

    it('should call onUpdateStatus when status changed', async () => {
        const user = userEvent.setup();
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const statusSelect = screen.getByLabelText(/Status/i);
        await user.click(statusSelect);
        await user.click(screen.getByText('Resolved'));

        expect(mockHandlers.onUpdateStatus).toHaveBeenCalledWith('resolved');
    });

    it('should call onAssign when user assigned', async () => {
        const user = userEvent.setup();
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const assignSelect = screen.getByLabelText(/Assign To/i);
        await user.click(assignSelect);
        await user.click(screen.getByText(/Alice Admin/i));

        expect(mockHandlers.onAssign).toHaveBeenCalledWith('admin-001');
    });

    it('should add comment', async () => {
        const user = userEvent.setup();
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        // Switch to comments tab
        const commentsTab = screen.getByText(/Comments/i);
        await user.click(commentsTab);

        // Add comment
        const commentInput = screen.getByPlaceholderText(/Add a comment/i);
        await user.type(commentInput, 'This is a test comment');

        const addButton = screen.getByRole('button', { name: /Add Comment/i });
        await user.click(addButton);

        expect(mockHandlers.onAddComment).toHaveBeenCalledWith('This is a test comment');
    });

    it('should resolve incident', async () => {
        const user = userEvent.setup();
        const openIncident = { ...mockIncident, status: 'open' as const };
        render(<IncidentDetailPanel incident={openIncident} {...mockHandlers} />);

        const resolutionInput = screen.getByPlaceholderText(/Resolution notes/i);
        await user.type(resolutionInput, 'Issue resolved by blocking IP');

        const resolveButton = screen.getByRole('button', { name: /Mark as Resolved/i });
        await user.click(resolveButton);

        expect(mockHandlers.onResolve).toHaveBeenCalledWith('Issue resolved by blocking IP');
    });

    it('should display timeline', async () => {
        const user = userEvent.setup();
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        // Timeline should be default tab
        expect(screen.getByText('Incident Created')).toBeInTheDocument();
    });

    it('should call onClose when close clicked', async () => {
        const user = userEvent.setup();
        render(<IncidentDetailPanel incident={mockIncident} {...mockHandlers} />);

        const closeButton = screen.getByRole('button', { name: /✕/i });
        await user.click(closeButton);

        expect(mockHandlers.onClose).toHaveBeenCalled();
    });
});
