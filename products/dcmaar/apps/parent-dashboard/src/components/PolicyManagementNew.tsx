import { useState, useEffect, memo, useCallback } from 'react';
import { DataGrid, type DataGridColumnConfig, type DataGridStatConfig, type DataGridFilterConfig, type CrudConfig } from '@ghatana/ui';
import { PolicyForm } from './PolicyForm';

export interface Policy {
  id: string;
  name: string;
  type: 'time-limit' | 'content-filter' | 'app-block' | 'schedule';
  restrictions: {
    maxUsageMinutes?: number;
    blockedCategories?: string[];
    blockedApps?: string[];
    allowedHours?: { start: string; end: string };
  };
  deviceIds: string[];
  createdAt: string;
  updatedAt: string;
}

interface PolicyManagementNewProps {
  onPolicyCreated?: (policy: Policy) => void;
  onPolicyUpdated?: (policy: Policy) => void;
  onPolicyDeleted?: (policyId: string) => void;
}

/**
 * PolicyManagementNew - Policy management using DataGrid
 * 
 * Manages device policies using the generic DataGrid component configured in table mode.
 * 
 * Features:
 * - CRUD operations for policies
 * - Search and filtering by type
 * - Statistics cards
 * - Type badges with colors
 * - Device count display
 * 
 * @example
 * ```tsx
 * <PolicyManagementNew
 *   onPolicyCreated={(policy) => console.log('Created:', policy)}
 *   onPolicyUpdated={(policy) => console.log('Updated:', policy)}
 *   onPolicyDeleted={(id) => console.log('Deleted:', id)}
 * />
 * ```
 */
function PolicyManagementNewComponent({
  onPolicyCreated,
  onPolicyUpdated,
  onPolicyDeleted,
}: PolicyManagementNewProps) {
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<Policy | null>(null);

  // Fetch policies
  const fetchPolicies = useCallback(async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem('token');
      const response = await fetch(apiUrl('/policies'), {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      
      if (response.ok) {
        const data = await response.json();
        setPolicies(data);
      }
    } catch (error) {
      console.error('Failed to fetch policies:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPolicies();
  }, [fetchPolicies]);

  // Handle CRUD operations
  const handleCreate = async (policyData: Omit<Policy, 'createdAt' | 'updatedAt'>) => {
    try {
      const token = localStorage.getItem('token');
      const response = await fetch(apiUrl('/policies'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(policyData),
      });

      if (response.ok) {
        const newPolicy = await response.json();
        setPolicies([...policies, newPolicy]);
        setShowCreateForm(false);
        onPolicyCreated?.(newPolicy);
      }
    } catch (error) {
      console.error('Failed to create policy:', error);
    }
  };

  const handleUpdate = async (policyData: Omit<Policy, 'createdAt' | 'updatedAt'>) => {
    try {
      const token = localStorage.getItem('token');
      const response = await fetch(apiUrl(`/policies/${policyData.id}`), {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(policyData),
      });

      if (response.ok) {
        const updatedPolicy = await response.json();
        setPolicies(policies.map(p => p.id === updatedPolicy.id ? updatedPolicy : p));
        setEditingPolicy(null);
        onPolicyUpdated?.(updatedPolicy);
      }
    } catch (error) {
      console.error('Failed to update policy:', error);
    }
  };

  const handleDelete = async (policyId: string) => {
    try {
      const token = localStorage.getItem('token');
      const response = await fetch(apiUrl(`/policies/${policyId}`), {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (response.ok) {
        setPolicies(policies.filter(p => p.id !== policyId));
        onPolicyDeleted?.(policyId);
      }
    } catch (error) {
      console.error('Failed to delete policy:', error);
    }
  };

  // Helper functions
  const getPolicyTypeColor = (type: string) => {
    switch (type) {
      case 'time-limit':
        return 'bg-blue-100 text-blue-800';
      case 'content-filter':
        return 'bg-red-100 text-red-800';
      case 'app-block':
        return 'bg-yellow-100 text-yellow-800';
      case 'schedule':
        return 'bg-green-100 text-green-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getPolicyTypeLabel = (type: string) => {
    switch (type) {
      case 'time-limit':
        return 'Time Limit';
      case 'content-filter':
        return 'Content Filter';
      case 'app-block':
        return 'App Block';
      case 'schedule':
        return 'Schedule';
      default:
        return type;
    }
  };

  // Show create/edit forms
  if (showCreateForm) {
    return (
      <div className="space-y-6">
        <div className="bg-white shadow rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-2xl font-bold text-gray-900">Create New Policy</h2>
            <button
              onClick={() => setShowCreateForm(false)}
              className="text-gray-500 hover:text-gray-700"
            >
              Cancel
            </button>
          </div>
          <PolicyForm onSubmit={handleCreate} onCancel={() => setShowCreateForm(false)} />
        </div>
      </div>
    );
  }

  if (editingPolicy) {
    return (
      <div className="space-y-6">
        <div className="bg-white shadow rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-2xl font-bold text-gray-900">Edit Policy</h2>
            <button
              onClick={() => setEditingPolicy(null)}
              className="text-gray-500 hover:text-gray-700"
            >
              Cancel
            </button>
          </div>
          <PolicyForm
            policy={editingPolicy}
            onSubmit={handleUpdate}
            onCancel={() => setEditingPolicy(null)}
          />
        </div>
      </div>
    );
  }

  // DataGrid configuration
  const columns: DataGridColumnConfig<Policy>[] = [
    {
      header: 'Policy Name',
      render: (policy: Policy) => <span>{policy.name}</span>,
    },
    {
      header: 'Type',
      render: (policy: Policy) => (
        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getPolicyTypeColor(policy.type)}`}>
          {getPolicyTypeLabel(policy.type)}
        </span>
      ),
    },
    {
      header: 'Devices',
      render: (policy: Policy) => `${policy.deviceIds.length} device(s)`,
    },
    {
      header: 'Restrictions',
      render: (policy: Policy) => {
        const parts: string[] = [];
        if (policy.restrictions.maxUsageMinutes) {
          parts.push(`${policy.restrictions.maxUsageMinutes}min limit`);
        }
        if (policy.restrictions.blockedCategories?.length) {
          parts.push(`${policy.restrictions.blockedCategories.length} categories`);
        }
        if (policy.restrictions.blockedApps?.length) {
          parts.push(`${policy.restrictions.blockedApps.length} apps`);
        }
        if (policy.restrictions.allowedHours) {
          parts.push(`${policy.restrictions.allowedHours.start}-${policy.restrictions.allowedHours.end}`);
        }
        return parts.join(', ') || 'None';
      },
    },
    {
      header: 'Created',
      render: (policy: Policy) => new Date(policy.createdAt).toLocaleDateString(),
    },
  ];

  const statsCards: DataGridStatConfig<Policy>[] = [
    {
      title: 'Total Policies',
      calculate: (items: Policy[]) => items.length,
      variant: 'blue',
    },
    {
      title: 'Time Limits',
      calculate: (items: Policy[]) => items.filter(p => p.type === 'time-limit').length,
      variant: 'green',
    },
    {
      title: 'Content Filters',
      calculate: (items: Policy[]) => items.filter(p => p.type === 'content-filter').length,
      variant: 'red',
    },
    {
      title: 'App Blocks',
      calculate: (items: Policy[]) => items.filter(p => p.type === 'app-block').length,
      variant: 'yellow',
    },
  ];

  const filters: DataGridFilterConfig[] = [
    {
      name: 'type',
      placeholder: 'Filter by type',
      type: 'select',
      label: 'Policy Type',
      options: [
        { label: 'All Types', value: 'all' },
        { label: 'Time Limit', value: 'time-limit' },
        { label: 'Content Filter', value: 'content-filter' },
        { label: 'App Block', value: 'app-block' },
        { label: 'Schedule', value: 'schedule' },
      ],
    },
  ];

  // Custom filter function
  const handleFilter = (items: Policy[], filterValues: Record<string, string>) => {
    const typeFilter = filterValues.type;
    if (!typeFilter || typeFilter === 'all') return items;
    return items.filter(policy => policy.type === typeFilter);
  };

  const crudConfig: CrudConfig<Policy> = {
    onEdit: (policy: Policy) => setEditingPolicy(policy),
    onDelete: (policy: Policy) => {
      if (confirm(`Delete policy "${policy.name}"?`)) {
        handleDelete(policy.id);
      }
    },
    onCreate: () => setShowCreateForm(true),
    createButtonText: 'Create Policy',
  };

  return (
    <DataGrid
      items={policies}
      columns={columns}
      title="Policy Management"
      displayMode="table"
      loading={loading}
      loadingMessage="Loading policies..."
      emptyMessage="No policies found. Create your first policy to get started."
      statsCards={statsCards}
      filters={filters}
      onFilter={handleFilter}
      crudConfig={crudConfig}
    />
  );
}

// Export memoized component
export const PolicyManagementNew = memo(PolicyManagementNewComponent);
