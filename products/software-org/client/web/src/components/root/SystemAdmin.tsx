/**
 * System Admin Component
 *
 * Root-level system administration component with user management, role configuration,
 * system settings, and audit logs.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    Box,
    Chip,
    Tabs,
    Tab,
    Alert,
    Button,
    Typography,
    Stack,
    Table,
    TableHead,
    TableBody,
    TableRow,
    TableCell,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

/**
 * System metrics
 */
export interface SystemMetrics {
    totalUsers: number;
    activeUsers: number;
    totalRoles: number;
    totalPermissions: number;
    systemHealth: 'healthy' | 'degraded' | 'critical';
    lastBackup: string;
    storageUsed: number; // GB
    storageTotal: number; // GB
}

/**
 * User account
 */
export interface UserAccount {
    id: string;
    email: string;
    name: string;
    role: string;
    status: 'active' | 'inactive' | 'suspended';
    lastLogin: string;
    createdAt: string;
    permissions: string[];
}

/**
 * Role definition
 */
export interface RoleDefinition {
    id: string;
    name: string;
    description: string;
    userCount: number;
    permissions: string[];
    isSystem: boolean;
}

/**
 * Audit log entry
 */
export interface AuditLogEntry {
    id: string;
    timestamp: string;
    userId: string;
    userName: string;
    action: string;
    resource: string;
    status: 'success' | 'failure';
    ipAddress: string;
    details: string;
}

/**
 * System setting
 */
export interface SystemSetting {
    id: string;
    category: 'security' | 'integration' | 'notifications' | 'performance';
    name: string;
    value: string;
    description: string;
    lastModified: string;
    modifiedBy: string;
}

/**
 * System Admin Props
 */
export interface SystemAdminProps {
    /** System metrics */
    systemMetrics: SystemMetrics;
    /** User accounts */
    users: UserAccount[];
    /** Role definitions */
    roles: RoleDefinition[];
    /** Audit log entries */
    auditLogs: AuditLogEntry[];
    /** System settings */
    settings: SystemSetting[];
    /** Callback when user is clicked */
    onUserClick?: (userId: string) => void;
    /** Callback when role is clicked */
    onRoleClick?: (roleId: string) => void;
    /** Callback when setting is clicked */
    onSettingClick?: (settingId: string) => void;
    /** Callback when create user is clicked */
    onCreateUser?: () => void;
    /** Callback when create role is clicked */
    onCreateRole?: () => void;
    /** Callback when export logs is clicked */
    onExportLogs?: () => void;
}

/**
 * System Admin Component
 *
 * Provides Root-level system administration with:
 * - System health monitoring (users, roles, storage, backups)
 * - User account management
 * - Role and permission configuration
 * - Audit log tracking
 * - System settings management
 * - Tab-based navigation (Users, Roles, Audit Logs, Settings)
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (system metrics)
 * - Grid (responsive layouts)
 * - Card (user cards, role cards, setting cards)
 * - Table (audit logs)
 * - Chip (status, role, permission indicators)
 * - Tabs (navigation)
 * - Alert (system health warnings)
 *
 * @example
 * ```tsx
 * <SystemAdmin
 *   systemMetrics={metrics}
 *   users={userList}
 *   roles={roleList}
 *   auditLogs={logs}
 *   settings={settingsList}
 *   onUserClick={(id) => navigate(`/admin/users/${id}`)}
 * />
 * ```
 */
export const SystemAdmin: React.FC<SystemAdminProps> = ({
    systemMetrics,
    users,
    roles,
    auditLogs,
    settings,
    onUserClick,
    onRoleClick,
    onSettingClick,
    onCreateUser,
    onCreateRole,
    onExportLogs,
}) => {
    const [selectedTab, setSelectedTab] = useState<'users' | 'roles' | 'logs' | 'settings'>('users');
    const [userFilter, setUserFilter] = useState<'all' | 'active' | 'inactive' | 'suspended'>('all');
    const [settingCategory, setSettingCategory] = useState<'all' | 'security' | 'integration' | 'notifications' | 'performance'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'active':
            case 'success':
            case 'healthy':
                return 'success';
            case 'inactive':
            case 'degraded':
                return 'warning';
            case 'suspended':
            case 'failure':
            case 'critical':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'default' | 'warning' | 'error' => {
        switch (category) {
            case 'security':
                return 'error';
            case 'integration':
                return 'warning';
            case 'notifications':
                return 'default';
            case 'performance':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    };

    // Filter users
    const filteredUsers = userFilter === 'all' ? users : users.filter((u) => u.status === userFilter);

    // Filter settings
    const filteredSettings = settingCategory === 'all' ? settings : settings.filter((s) => s.category === settingCategory);

    // Count users by status
    const userCounts = {
        active: users.filter((u) => u.status === 'active').length,
        inactive: users.filter((u) => u.status === 'inactive').length,
        suspended: users.filter((u) => u.status === 'suspended').length,
    };

    // Calculate storage usage percentage
    const storageUsagePercent = Math.round((systemMetrics.storageUsed / systemMetrics.storageTotal) * 100);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        System Administration
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        User management, roles, and system configuration
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onCreateUser && selectedTab === 'users' && (
                        <Button variant="primary" size="md" onClick={onCreateUser}>
                            Create User
                        </Button>
                    )}
                    {onCreateRole && selectedTab === 'roles' && (
                        <Button variant="primary" size="md" onClick={onCreateRole}>
                            Create Role
                        </Button>
                    )}
                    {onExportLogs && selectedTab === 'logs' && (
                        <Button variant="outline" size="md" onClick={onExportLogs}>
                            Export Logs
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* System Health Alert */}
            {systemMetrics.systemHealth !== 'healthy' && (
                <Alert severity={systemMetrics.systemHealth === 'critical' ? 'error' : 'warning'}>
                    System health is {systemMetrics.systemHealth} - please review system status
                </Alert>
            )}

            {/* System Metrics */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Users"
                    value={systemMetrics.totalUsers}
                    description={`${systemMetrics.activeUsers} active`}
                    status={systemMetrics.activeUsers >= systemMetrics.totalUsers * 0.7 ? 'healthy' : 'warning'}
                />

                <KpiCard label="Roles" value={systemMetrics.totalRoles} description={`${systemMetrics.totalPermissions} permissions`} status="healthy" />

                <KpiCard
                    label="Storage"
                    value={`${storageUsagePercent}%`}
                    description={`${systemMetrics.storageUsed} GB / ${systemMetrics.storageTotal} GB`}
                    status={storageUsagePercent < 80 ? 'healthy' : storageUsagePercent < 90 ? 'warning' : 'error'}
                />

                <KpiCard label="Last Backup" value={new Date(systemMetrics.lastBackup).toLocaleDateString()} description="System backup" status="healthy" />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Users (${users.length})`} value="users" />
                    <Tab label={`Roles (${roles.length})`} value="roles" />
                    <Tab label={`Audit Logs (${auditLogs.length})`} value="logs" />
                    <Tab label={`Settings (${settings.length})`} value="settings" />
                </Tabs>

                {/* Users Tab */}
                {selectedTab === 'users' && (
                    <Box className="p-4">
                        {/* User Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${users.length})`} color={userFilter === 'all' ? 'error' : 'default'} onClick={() => setUserFilter('all')} />
                            <Chip label={`Active (${userCounts.active})`} color={userFilter === 'active' ? 'success' : 'default'} onClick={() => setUserFilter('active')} />
                            <Chip
                                label={`Inactive (${userCounts.inactive})`}
                                color={userFilter === 'inactive' ? 'warning' : 'default'}
                                onClick={() => setUserFilter('inactive')}
                            />
                            <Chip
                                label={`Suspended (${userCounts.suspended})`}
                                color={userFilter === 'suspended' ? 'error' : 'default'}
                                onClick={() => setUserFilter('suspended')}
                            />
                        </Stack>

                        {/* User Table */}
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Name</TableCell>
                                    <TableCell>Email</TableCell>
                                    <TableCell>Role</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Last Login</TableCell>
                                    <TableCell>Created</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {filteredUsers.map((user) => (
                                    <TableRow key={user.id} className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800" onClick={() => onUserClick?.(user.id)}>
                                        <TableCell>
                                            <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                {user.name}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {user.email}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={user.role} color="default" size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={user.status} color={getStatusColor(user.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {formatDate(user.lastLogin)}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {formatDate(user.createdAt)}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Roles Tab */}
                {selectedTab === 'roles' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Role Definitions
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {roles.map((role) => (
                                <Card key={role.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onRoleClick?.(role.id)}>
                                    <Box className="p-4">
                                        {/* Role Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box>
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {role.name}
                                                    </Typography>
                                                    {role.isSystem && <Chip label="System" color="warning" size="small" />}
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {role.description}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Role Stats */}
                                        <Box className="mb-3">
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {role.userCount} user{role.userCount !== 1 ? 's' : ''} • {role.permissions.length} permission
                                                {role.permissions.length !== 1 ? 's' : ''}
                                            </Typography>
                                        </Box>

                                        {/* Permissions */}
                                        <Box>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Permissions
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap gap-1">
                                                {role.permissions.slice(0, 5).map((permission, index) => (
                                                    <Chip key={index} label={permission} color="default" size="small" />
                                                ))}
                                                {role.permissions.length > 5 && <Chip label={`+${role.permissions.length - 5} more`} color="default" size="small" />}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Audit Logs Tab */}
                {selectedTab === 'logs' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Audit Log Entries
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Timestamp</TableCell>
                                    <TableCell>User</TableCell>
                                    <TableCell>Action</TableCell>
                                    <TableCell>Resource</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>IP Address</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {auditLogs.map((log) => (
                                    <TableRow key={log.id}>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {formatDate(log.timestamp)}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                {log.userName}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {log.action}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {log.resource}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={log.status} color={getStatusColor(log.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {log.ipAddress}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Settings Tab */}
                {selectedTab === 'settings' && (
                    <Box className="p-4">
                        {/* Category Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label="All" color={settingCategory === 'all' ? 'error' : 'default'} onClick={() => setSettingCategory('all')} />
                            <Chip label="Security" color={settingCategory === 'security' ? 'error' : 'default'} onClick={() => setSettingCategory('security')} />
                            <Chip label="Integration" color={settingCategory === 'integration' ? 'default' : 'default'} onClick={() => setSettingCategory('integration')} />
                            <Chip
                                label="Notifications"
                                color={settingCategory === 'notifications' ? 'default' : 'default'}
                                onClick={() => setSettingCategory('notifications')}
                            />
                            <Chip label="Performance" color={settingCategory === 'performance' ? 'default' : 'default'} onClick={() => setSettingCategory('performance')} />
                        </Stack>

                        {/* Settings List */}
                        <Stack spacing={3}>
                            {filteredSettings.map((setting) => (
                                <Card key={setting.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onSettingClick?.(setting.id)}>
                                    <Box className="p-4">
                                        <Box className="flex items-start justify-between mb-2">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {setting.name}
                                                    </Typography>
                                                    <Chip label={setting.category} color={getCategoryColor(setting.category)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {setting.description}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Current Value
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {setting.value}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Last Modified
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {formatDate(setting.lastModified)}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Modified By
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {setting.modifiedBy}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockSystemAdminData = {
    systemMetrics: {
        totalUsers: 850,
        activeUsers: 720,
        totalRoles: 12,
        totalPermissions: 48,
        systemHealth: 'healthy',
        lastBackup: '2025-12-11T02:00:00Z',
        storageUsed: 680,
        storageTotal: 1000,
    } as SystemMetrics,

    users: [
        {
            id: 'user-1',
            email: 'john.doe@company.com',
            name: 'John Doe',
            role: 'Admin',
            status: 'active',
            lastLogin: '2025-12-11T10:30:00Z',
            createdAt: '2024-01-15T08:00:00Z',
            permissions: ['users.read', 'users.write', 'roles.read', 'roles.write'],
        },
        {
            id: 'user-2',
            email: 'jane.smith@company.com',
            name: 'Jane Smith',
            role: 'Manager',
            status: 'active',
            lastLogin: '2025-12-11T09:15:00Z',
            createdAt: '2024-02-20T08:00:00Z',
            permissions: ['users.read', 'reports.read'],
        },
        {
            id: 'user-3',
            email: 'bob.wilson@company.com',
            name: 'Bob Wilson',
            role: 'Developer',
            status: 'inactive',
            lastLogin: '2025-12-01T14:20:00Z',
            createdAt: '2024-03-10T08:00:00Z',
            permissions: ['projects.read', 'projects.write'],
        },
    ] as UserAccount[],

    roles: [
        {
            id: 'role-1',
            name: 'Admin',
            description: 'Full system access with all permissions',
            userCount: 15,
            permissions: ['users.read', 'users.write', 'roles.read', 'roles.write', 'settings.read', 'settings.write'],
            isSystem: true,
        },
        {
            id: 'role-2',
            name: 'Manager',
            description: 'Team management and reporting access',
            userCount: 45,
            permissions: ['users.read', 'reports.read', 'reports.write', 'teams.read', 'teams.write'],
            isSystem: false,
        },
        {
            id: 'role-3',
            name: 'Developer',
            description: 'Development and project access',
            userCount: 320,
            permissions: ['projects.read', 'projects.write', 'deployments.read'],
            isSystem: false,
        },
    ] as RoleDefinition[],

    auditLogs: [
        {
            id: 'log-1',
            timestamp: '2025-12-11T10:45:00Z',
            userId: 'user-1',
            userName: 'John Doe',
            action: 'CREATE_USER',
            resource: 'users/user-123',
            status: 'success',
            ipAddress: '192.168.1.100',
            details: 'Created new user account',
        },
        {
            id: 'log-2',
            timestamp: '2025-12-11T10:30:00Z',
            userId: 'user-2',
            userName: 'Jane Smith',
            action: 'UPDATE_ROLE',
            resource: 'roles/role-5',
            status: 'success',
            ipAddress: '192.168.1.101',
            details: 'Updated role permissions',
        },
        {
            id: 'log-3',
            timestamp: '2025-12-11T10:15:00Z',
            userId: 'user-3',
            userName: 'Bob Wilson',
            action: 'LOGIN_FAILED',
            resource: 'auth/login',
            status: 'failure',
            ipAddress: '192.168.1.102',
            details: 'Invalid credentials',
        },
    ] as AuditLogEntry[],

    settings: [
        {
            id: 'setting-1',
            category: 'security',
            name: 'Password Policy',
            value: 'Minimum 12 characters, requires special char',
            description: 'User password complexity requirements',
            lastModified: '2025-11-15T14:00:00Z',
            modifiedBy: 'admin@company.com',
        },
        {
            id: 'setting-2',
            category: 'integration',
            name: 'SSO Provider',
            value: 'Okta',
            description: 'Single sign-on authentication provider',
            lastModified: '2025-10-01T10:00:00Z',
            modifiedBy: 'admin@company.com',
        },
        {
            id: 'setting-3',
            category: 'notifications',
            name: 'Email Notifications',
            value: 'Enabled',
            description: 'System email notification settings',
            lastModified: '2025-09-20T12:00:00Z',
            modifiedBy: 'admin@company.com',
        },
    ] as SystemSetting[],
};
