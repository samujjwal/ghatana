import { apiUrl } from '../config/api';
import { useState, useEffect, useMemo, memo, useCallback } from 'react';
import { PolicyForm } from './PolicyForm';
import { usePoliciesData } from '@ghatana/dcmaar-dashboard-core';

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

interface PolicyManagementProps {
  onPolicyCreated?: (policy: Policy) => void;
  onPolicyUpdated?: (policy: Policy) => void;
  onPolicyDeleted?: (policyId: string) => void;
}

function PolicyManagementComponent({
  onPolicyCreated,
  onPolicyUpdated,
  onPolicyDeleted,
}: PolicyManagementProps) {
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<Policy | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<string>('all');
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const { data: corePolicies, isLoading: coreLoading } = usePoliciesData<Policy[]>();

  useEffect(() => {
    if (corePolicies) {
      setPolicies(corePolicies);
    }
    setLoading(coreLoading);
  }, [corePolicies, coreLoading]);

  const handleCreatePolicy = async (policyData: Omit<Policy, 'createdAt' | 'updatedAt'>) => {
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

  const handleUpdatePolicy = async (policyData: Omit<Policy, 'createdAt' | 'updatedAt'>) => {
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

  const handleDeletePolicy = async (policyId: string) => {
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
        setDeleteConfirmId(null);
        onPolicyDeleted?.(policyId);
      }
    } catch (error) {
      console.error('Failed to delete policy:', error);
    }
  };

  // Memoize filtered policies to avoid recalculation on every render
  const filteredPolicies = useMemo(() => {
    return policies.filter(policy => {
      const matchesSearch = policy.name.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesType = filterType === 'all' || policy.type === filterType;
      return matchesSearch && matchesType;
    });
  }, [policies, searchTerm, filterType]);

  // Memoize helper functions to avoid recreation on every render
  const getPolicyTypeColor = useCallback((type: string) => {
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
  }, []);

  const getPolicyTypeLabel = useCallback((type: string) => {
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
  }, []);

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
          <PolicyForm onSubmit={handleCreatePolicy} onCancel={() => setShowCreateForm(false)} />
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
            onSubmit={handleUpdatePolicy}
            onCancel={() => setEditingPolicy(null)}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">Policy Management</h2>
          <button
            onClick={() => setShowCreateForm(true)}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            Create Policy
          </button>
        </div>

        {/* Search and Filter */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
          <input
            type="text"
            placeholder="Search policies..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">All Types</option>
            <option value="time-limit">Time Limit</option>
            <option value="content-filter">Content Filter</option>
            <option value="app-block">App Block</option>
            <option value="schedule">Schedule</option>
          </select>
        </div>

        {/* Statistics */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-blue-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-blue-600">Total Policies</h3>
            <p className="text-3xl font-bold text-blue-900">{policies.length}</p>
          </div>
          <div className="bg-green-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-green-600">Time Limits</h3>
            <p className="text-3xl font-bold text-green-900">
              {policies.filter(p => p.type === 'time-limit').length}
            </p>
          </div>
          <div className="bg-red-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-red-600">Content Filters</h3>
            <p className="text-3xl font-bold text-red-900">
              {policies.filter(p => p.type === 'content-filter').length}
            </p>
          </div>
          <div className="bg-yellow-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-yellow-600">App Blocks</h3>
            <p className="text-3xl font-bold text-yellow-900">
              {policies.filter(p => p.type === 'app-block').length}
            </p>
          </div>
        </div>

        {/* Policy List */}
        {loading ? (
          <p className="text-gray-500 text-center py-8">Loading policies...</p>
        ) : filteredPolicies.length === 0 ? (
          <p className="text-gray-500 text-center py-8">
            No policies found. Create your first policy to get started.
          </p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" role="list">
            {filteredPolicies.map((policy) => (
              <div key={policy.id} className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow" role="listitem">
                <div className="flex items-start justify-between mb-2">
                  <h3 className="text-lg font-semibold text-gray-900">{policy.name}</h3>
                  <span className={`px-2 py-1 rounded-full text-xs font-semibold ${getPolicyTypeColor(policy.type)}`}>
                    {getPolicyTypeLabel(policy.type)}
                  </span>
                </div>

                <div className="space-y-2 mb-4">
                  {policy.type === 'time-limit' && policy.restrictions.maxUsageMinutes && (
                    <p className="text-sm text-gray-600">
                      Max Usage: {policy.restrictions.maxUsageMinutes} minutes
                    </p>
                  )}
                  {policy.type === 'content-filter' && policy.restrictions.blockedCategories && (
                    <p className="text-sm text-gray-600">
                      Blocked Categories: {policy.restrictions.blockedCategories.join(', ')}
                    </p>
                  )}
                  {policy.type === 'app-block' && policy.restrictions.blockedApps && (
                    <p className="text-sm text-gray-600">
                      Blocked Apps: {policy.restrictions.blockedApps.join(', ')}
                    </p>
                  )}
                  {policy.type === 'schedule' && policy.restrictions.allowedHours && (
                    <p className="text-sm text-gray-600">
                      Allowed: {policy.restrictions.allowedHours.start} - {policy.restrictions.allowedHours.end}
                    </p>
                  )}
                  <p className="text-sm text-gray-600">
                    Devices: {policy.deviceIds.length}
                  </p>
                </div>

                <div className="flex gap-2">
                  <button
                    onClick={() => setEditingPolicy(policy)}
                    className="flex-1 px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
                  >
                    Edit
                  </button>
                  {deleteConfirmId === policy.id ? (
                    <div className="flex-1 flex gap-1">
                      <button
                        onClick={() => handleDeletePolicy(policy.id)}
                        className="flex-1 px-3 py-1 bg-red-600 text-white text-sm rounded hover:bg-red-700"
                      >
                        Confirm
                      </button>
                      <button
                        onClick={() => setDeleteConfirmId(null)}
                        className="flex-1 px-3 py-1 bg-gray-300 text-gray-700 text-sm rounded hover:bg-gray-400"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => setDeleteConfirmId(policy.id)}
                      className="flex-1 px-3 py-1 bg-red-600 text-white text-sm rounded hover:bg-red-700"
                    >
                      Delete
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Export memoized component to prevent unnecessary re-renders
export const PolicyManagement = memo(PolicyManagementComponent);
