/**
 * Governance Page
 * 
 * Data governance management including roles, permissions, policies, and audit logs.
 * 
 * @doc.type page
 * @doc.purpose Data governance management
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import React, { useState } from 'react';
import { useLocation, Link } from 'react-router';
import {
    cn,
    cardStyles,
    textStyles,
    bgStyles,
    buttonStyles,
    tableStyles,
    badgeStyles,
    policyTypeStyles,
} from '../lib/theme';

/**
 * Mock data for governance
 */
const mockRoles = [
    { id: 'role-1', name: 'Admin', description: 'Full system access', userCount: 3, permissions: 42 },
    { id: 'role-2', name: 'Data Steward', description: 'Manage data quality and metadata', userCount: 8, permissions: 28 },
    { id: 'role-3', name: 'Analyst', description: 'Read access to datasets', userCount: 24, permissions: 12 },
    { id: 'role-4', name: 'Developer', description: 'Create and manage workflows', userCount: 15, permissions: 18 },
];

const mockPolicies = [
    { id: 'pol-1', name: 'PII Data Masking', type: 'data-protection' as const, status: 'active', appliedTo: 12, lastUpdated: '2024-01-10' },
    { id: 'pol-2', name: 'Retention Policy - 90 Days', type: 'retention' as const, status: 'active', appliedTo: 24, lastUpdated: '2024-01-08' },
    { id: 'pol-3', name: 'GDPR Compliance', type: 'compliance' as const, status: 'active', appliedTo: 18, lastUpdated: '2024-01-05' },
    { id: 'pol-4', name: 'Access Control - Finance', type: 'access' as const, status: 'draft', appliedTo: 0, lastUpdated: '2024-01-12' },
];

const mockAuditLogs = [
    { id: 'log-1', action: 'Collection Created', resource: 'user-events', user: 'alice@example.com', timestamp: '2024-01-12T10:30:00Z', status: 'success' },
    { id: 'log-2', action: 'Policy Updated', resource: 'pii-masking', user: 'admin@example.com', timestamp: '2024-01-12T09:15:00Z', status: 'success' },
    { id: 'log-3', action: 'Role Assigned', resource: 'Data Steward', user: 'bob@example.com', timestamp: '2024-01-11T16:45:00Z', status: 'success' },
    { id: 'log-4', action: 'Access Denied', resource: 'finance-data', user: 'guest@example.com', timestamp: '2024-01-11T14:20:00Z', status: 'failed' },
    { id: 'log-5', action: 'Workflow Executed', resource: 'etl-pipeline', user: 'system', timestamp: '2024-01-11T12:00:00Z', status: 'success' },
];

type TabType = 'roles' | 'policies' | 'audit';

/**
 * Governance Page Component
 */
export function GovernancePage(): React.ReactElement {
    const location = useLocation();
    const [activeTab, setActiveTab] = useState<TabType>(() => {
        if (location.pathname.includes('/policies')) return 'policies';
        if (location.pathname.includes('/audit')) return 'audit';
        return 'roles';
    });

    const tabs: { id: TabType; label: string; count?: number }[] = [
        { id: 'roles', label: 'Roles & Permissions', count: mockRoles.length },
        { id: 'policies', label: 'Policies', count: mockPolicies.length },
        { id: 'audit', label: 'Audit Logs' },
    ];

    return (
        <div className={cn('min-h-screen', bgStyles.page)}>
            {/* Header */}
            <div className={cn('border-b', bgStyles.surface, 'border-gray-200 dark:border-gray-700')}>
                <div className="px-6 py-4">
                    <h1 className={textStyles.h1}>Governance</h1>
                    <p className={textStyles.muted}>Manage roles, permissions, policies, and audit logs</p>
                </div>

                {/* Tabs */}
                <div className="px-6 flex gap-4">
                    {tabs.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className={cn(
                                'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                                activeTab === tab.id
                                    ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                    : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
                            )}
                        >
                            {tab.label}
                            {tab.count !== undefined && (
                                <span className="ml-2 px-2 py-0.5 text-xs rounded-full bg-gray-100 dark:bg-gray-700">
                                    {tab.count}
                                </span>
                            )}
                        </button>
                    ))}
                </div>
            </div>

            {/* Content */}
            <div className="p-6">
                {activeTab === 'roles' && <RolesTab />}
                {activeTab === 'policies' && <PoliciesTab />}
                {activeTab === 'audit' && <AuditTab />}
            </div>
        </div>
    );
}

/**
 * Roles Tab Component
 */
function RolesTab(): React.ReactElement {
    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h2 className={textStyles.h2}>Roles & Permissions</h2>
                <button className={buttonStyles.primary}>+ Create Role</button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                {mockRoles.map((role) => (
                    <div key={role.id} className={cn(cardStyles.interactive, cardStyles.padded)}>
                        <h3 className={textStyles.h3}>{role.name}</h3>
                        <p className={cn(textStyles.small, 'mt-1')}>{role.description}</p>
                        <div className="mt-4 flex justify-between">
                            <div>
                                <p className={textStyles.xs}>Users</p>
                                <p className={textStyles.h4}>{role.userCount}</p>
                            </div>
                            <div>
                                <p className={textStyles.xs}>Permissions</p>
                                <p className={textStyles.h4}>{role.permissions}</p>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

/**
 * Policies Tab Component
 */
function PoliciesTab(): React.ReactElement {
    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h2 className={textStyles.h2}>Data Policies</h2>
                <button className={buttonStyles.primary}>+ Create Policy</button>
            </div>

            <div className={cn(cardStyles.base, 'overflow-hidden')}>
                <table className={tableStyles.table}>
                    <thead className={tableStyles.thead}>
                        <tr>
                            <th className={tableStyles.th}>Policy</th>
                            <th className={tableStyles.th}>Type</th>
                            <th className={tableStyles.th}>Status</th>
                            <th className={tableStyles.th}>Applied To</th>
                            <th className={tableStyles.th}>Last Updated</th>
                            <th className={tableStyles.th}>Actions</th>
                        </tr>
                    </thead>
                    <tbody className={tableStyles.tbody}>
                        {mockPolicies.map((policy) => (
                            <tr key={policy.id} className={tableStyles.tr}>
                                <td className={tableStyles.td}>
                                    <p className={textStyles.h4}>{policy.name}</p>
                                    <p className={textStyles.xs}>{policy.id}</p>
                                </td>
                                <td className={tableStyles.td}>
                                    <span className={cn('px-2 py-1 rounded text-xs font-medium', policyTypeStyles[policy.type])}>
                                        {policy.type}
                                    </span>
                                </td>
                                <td className={tableStyles.td}>
                                    <span className={cn(
                                        'px-2 py-1 rounded text-xs font-medium',
                                        policy.status === 'active' ? badgeStyles.success : badgeStyles.warning
                                    )}>
                                        {policy.status}
                                    </span>
                                </td>
                                <td className={tableStyles.td}>{policy.appliedTo} collections</td>
                                <td className={tableStyles.td}>{policy.lastUpdated}</td>
                                <td className={tableStyles.td}>
                                    <button className={cn(buttonStyles.ghost, buttonStyles.sm)}>Edit</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

/**
 * Audit Tab Component
 */
function AuditTab(): React.ReactElement {
    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h2 className={textStyles.h2}>Audit Logs</h2>
                <div className="flex gap-2">
                    <select className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm">
                        <option>All Actions</option>
                        <option>Create</option>
                        <option>Update</option>
                        <option>Delete</option>
                        <option>Access</option>
                    </select>
                    <button className={buttonStyles.secondary}>Export</button>
                </div>
            </div>

            <div className={cn(cardStyles.base, 'overflow-hidden')}>
                <table className={tableStyles.table}>
                    <thead className={tableStyles.thead}>
                        <tr>
                            <th className={tableStyles.th}>Timestamp</th>
                            <th className={tableStyles.th}>Action</th>
                            <th className={tableStyles.th}>Resource</th>
                            <th className={tableStyles.th}>User</th>
                            <th className={tableStyles.th}>Status</th>
                        </tr>
                    </thead>
                    <tbody className={tableStyles.tbody}>
                        {mockAuditLogs.map((log) => (
                            <tr key={log.id} className={tableStyles.tr}>
                                <td className={tableStyles.td}>
                                    <p className={textStyles.small}>
                                        {new Date(log.timestamp).toLocaleString()}
                                    </p>
                                </td>
                                <td className={tableStyles.td}>
                                    <p className={textStyles.h4}>{log.action}</p>
                                </td>
                                <td className={tableStyles.td}>
                                    <p className={textStyles.body}>{log.resource}</p>
                                </td>
                                <td className={tableStyles.td}>
                                    <p className={textStyles.small}>{log.user}</p>
                                </td>
                                <td className={tableStyles.td}>
                                    <span className={cn(
                                        'px-2 py-1 rounded text-xs font-medium',
                                        log.status === 'success' ? badgeStyles.success : badgeStyles.danger
                                    )}>
                                        {log.status}
                                    </span>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default GovernancePage;
