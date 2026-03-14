import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, Badge } from '../components/ui';
import { Button, Input, Spinner } from '@ghatana/design-system';
import { useAuth } from '../hooks/useAuth';
import type {
  IdentityProviderConfig,
} from '@ghatana/tutorputor-contracts/v1/types';

type ProviderType = 'oidc' | 'saml';

export function SsoConfigPage() {
  const { tenantId } = useAuth();
  const queryClient = useQueryClient();
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingProvider, setEditingProvider] = useState<IdentityProviderConfig | null>(
    null
  );
  const [testingProviderId, setTestingProviderId] = useState<string | null>(null);

  const { data: providers, isLoading } = useQuery({
    queryKey: ['sso-providers', tenantId],
    queryFn: async () => {
      // Use existing SSO service routes (reuse-first principle)
      const res = await fetch('/admin/sso/providers');
      if (!res.ok) throw new Error('Failed to fetch providers');
      return res.json() as Promise<{ providers: IdentityProviderConfig[] }>;
    },
  });

  const testMutation = useMutation({
    mutationFn: async (providerId: string) => {
      setTestingProviderId(providerId);
      // Use existing SSO service routes
      const res = await fetch(`/admin/sso/providers/${providerId}/test`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error('Test failed');
      return res.json();
    },
    onSettled: () => {
      setTestingProviderId(null);
      queryClient.invalidateQueries({ queryKey: ['sso-providers'] });
    },
  });

  const toggleMutation = useMutation({
    mutationFn: async ({ providerId, enabled }: { providerId: string; enabled: boolean }) => {
      // Use existing SSO service routes (PUT for updates)
      const res = await fetch(`/admin/sso/providers/${providerId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled }),
      });
      if (!res.ok) throw new Error('Update failed');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sso-providers'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (providerId: string) => {
      // Use existing SSO service routes
      const res = await fetch(`/admin/sso/providers/${providerId}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error('Delete failed');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sso-providers'] });
    },
  });

  const providerStatusColors: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    active: 'default',
    pending_verification: 'secondary',
    error: 'destructive',
    disabled: 'outline',
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
            SSO Configuration
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Configure identity providers for Single Sign-On
          </p>
        </div>
        <Button onClick={() => setShowAddModal(true)}>
          Add Identity Provider
        </Button>
      </div>

      {/* Info Card */}
      <Card className="p-4 bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800">
        <div className="flex gap-3">
          <svg
            className="w-5 h-5 text-blue-600 mt-0.5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          <div className="text-sm text-blue-800 dark:text-blue-200">
            <p className="font-medium">Enterprise SSO</p>
            <p className="mt-1 text-blue-600 dark:text-blue-300">
              Configure OIDC (Google, Microsoft, Okta) or SAML providers to allow users
              to sign in with their organization credentials.
            </p>
          </div>
        </div>
      </Card>

      {/* Providers List */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner size="lg" />
        </div>
      ) : providers?.providers.length === 0 ? (
        <Card className="p-12 text-center">
          <svg
            className="w-12 h-12 mx-auto text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z"
            />
          </svg>
          <h3 className="mt-4 text-lg font-medium text-gray-900 dark:text-white">
            No identity providers configured
          </h3>
          <p className="mt-2 text-gray-500">
            Add an identity provider to enable SSO for your organization.
          </p>
          <Button className="mt-4" onClick={() => setShowAddModal(true)}>
            Add Identity Provider
          </Button>
        </Card>
      ) : (
        <div className="space-y-4">
          {providers?.providers.map((provider) => (
            <Card key={provider.id} className="p-6">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center">
                    {getProviderIcon(provider.type, provider.displayName)}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold text-gray-900 dark:text-white">
                        {provider.displayName}
                      </h3>
                      <Badge
                        variant={providerStatusColors[provider.status || 'pending_verification']}
                      >
                        {provider.status?.replace('_', ' ') || 'pending'}
                      </Badge>
                      {!provider.enabled && (
                        <Badge variant="outline">Disabled</Badge>
                      )}
                    </div>
                    <p className="text-sm text-gray-500 mt-1">
                      {provider.type.toUpperCase()} • {provider.clientId}
                    </p>
                    {provider.allowedDomains && provider.allowedDomains.length > 0 && (
                      <p className="text-sm text-gray-500 mt-1">
                        Allowed domains: {provider.allowedDomains.join(', ')}
                      </p>
                    )}
                    {provider.lastSuccessfulAuthAt && (
                      <p className="text-xs text-gray-400 mt-1">
                        Last successful auth:{' '}
                        {new Date(provider.lastSuccessfulAuthAt).toLocaleString()}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => testMutation.mutate(provider.id)}
                    disabled={testingProviderId === provider.id}
                  >
                    {testingProviderId === provider.id ? 'Testing...' : 'Test'}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      toggleMutation.mutate({
                        providerId: provider.id,
                        enabled: !provider.enabled,
                      })
                    }
                  >
                    {provider.enabled ? 'Disable' : 'Enable'}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setEditingProvider(provider)}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => {
                      if (confirm('Are you sure you want to delete this provider?')) {
                        deleteMutation.mutate(provider.id);
                      }
                    }}
                  >
                    Delete
                  </Button>
                </div>
              </div>

              {/* Role Mapping Summary */}
              {provider.roleMapping && (
                <div className="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                  <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
                    Role Mapping
                  </p>
                  <p className="text-sm text-gray-500 mt-1">
                    Default role: {provider.roleMapping.defaultRole}
                    {provider.roleMapping.rules.length > 0 &&
                      ` • ${provider.roleMapping.rules.length} custom rules`}
                  </p>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      {/* Add/Edit Modal */}
      {(showAddModal || editingProvider) && (
        <ProviderModal
          provider={editingProvider}
          onClose={() => {
            setShowAddModal(false);
            setEditingProvider(null);
          }}
        />
      )}
    </div>
  );
}

// Provider Modal for Add/Edit
function ProviderModal({
  provider,
  onClose,
}: {
  provider: IdentityProviderConfig | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const isEditing = !!provider;

  const [formData, setFormData] = useState({
    type: provider?.type || ('oidc' as ProviderType),
    displayName: provider?.displayName || '',
    discoveryEndpoint: provider?.discoveryEndpoint || '',
    clientId: provider?.clientId || '',
    clientSecret: '',
    allowedDomains: provider?.allowedDomains?.join(', ') || '',
    defaultRole: provider?.roleMapping?.defaultRole || 'student',
  });

  const createMutation = useMutation({
    mutationFn: async (data: typeof formData) => {
      const res = await fetch('/admin/api/v1/sso/providers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...data,
          allowedDomains: data.allowedDomains
            ? data.allowedDomains.split(',').map((d) => d.trim())
            : [],
          roleMapping: {
            defaultRole: data.defaultRole,
            rules: [],
          },
        }),
      });
      if (!res.ok) throw new Error('Failed to create provider');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sso-providers'] });
      onClose();
    },
  });

  const updateMutation = useMutation({
    mutationFn: async (data: typeof formData) => {
      const res = await fetch(`/admin/api/v1/sso/providers/${provider!.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: data.displayName,
          discoveryEndpoint: data.discoveryEndpoint,
          clientId: data.clientId,
          allowedDomains: data.allowedDomains
            ? data.allowedDomains.split(',').map((d) => d.trim())
            : [],
        }),
      });
      if (!res.ok) throw new Error('Failed to update provider');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sso-providers'] });
      onClose();
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isEditing) {
      updateMutation.mutate(formData);
    } else {
      createMutation.mutate(formData);
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/50" onClick={onClose}></div>
      <Card className="relative z-10 w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <h2 className="text-xl font-bold mb-4">
          {isEditing ? 'Edit Identity Provider' : 'Add Identity Provider'}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          {!isEditing && (
            <div>
              <label className="block text-sm font-medium mb-2">Type</label>
              <select
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800"
                value={formData.type}
                onChange={(e) =>
                  setFormData({ ...formData, type: e.target.value as ProviderType })
                }
              >
                <option value="oidc">OIDC (Google, Microsoft, Okta, etc.)</option>
                <option value="saml">SAML 2.0</option>
              </select>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium mb-2">Display Name</label>
            <Input
              value={formData.displayName}
              onChange={(e) =>
                setFormData({ ...formData, displayName: e.target.value })
              }
              placeholder="e.g., Google Workspace, Okta, Azure AD"
              required
            />
          </div>

          {formData.type === 'oidc' && (
            <>
              <div>
                <label className="block text-sm font-medium mb-2">
                  Discovery Endpoint
                </label>
                <Input
                  value={formData.discoveryEndpoint}
                  onChange={(e) =>
                    setFormData({ ...formData, discoveryEndpoint: e.target.value })
                  }
                  placeholder="https://accounts.google.com/.well-known/openid-configuration"
                  required
                />
                <p className="mt-1 text-xs text-gray-500">
                  The OIDC .well-known/openid-configuration URL
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">Client ID</label>
                <Input
                  value={formData.clientId}
                  onChange={(e) =>
                    setFormData({ ...formData, clientId: e.target.value })
                  }
                  placeholder="your-client-id"
                  required
                />
              </div>

              {!isEditing && (
                <div>
                  <label className="block text-sm font-medium mb-2">
                    Client Secret
                  </label>
                  <Input
                    type="password"
                    value={formData.clientSecret}
                    onChange={(e) =>
                      setFormData({ ...formData, clientSecret: e.target.value })
                    }
                    placeholder="your-client-secret"
                    required
                  />
                </div>
              )}
            </>
          )}

          <div>
            <label className="block text-sm font-medium mb-2">
              Allowed Email Domains (optional)
            </label>
            <Input
              value={formData.allowedDomains}
              onChange={(e) =>
                setFormData({ ...formData, allowedDomains: e.target.value })
              }
              placeholder="example.com, school.edu"
            />
            <p className="mt-1 text-xs text-gray-500">
              Comma-separated list of allowed email domains
            </p>
          </div>

          <div>
            <label className="block text-sm font-medium mb-2">Default Role</label>
            <select
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800"
              value={formData.defaultRole}
              onChange={(e) =>
                setFormData({ ...formData, defaultRole: e.target.value as any })
              }
            >
              <option value="student">Student</option>
              <option value="teacher">Teacher</option>
              <option value="creator">Creator</option>
              <option value="admin">Admin</option>
            </select>
            <p className="mt-1 text-xs text-gray-500">
              Role assigned to new users. Configure advanced role mapping after creation.
            </p>
          </div>

          {(createMutation.error || updateMutation.error) && (
            <p className="text-sm text-red-600">
              Failed to save provider. Please check your configuration.
            </p>
          )}

          <div className="pt-4 flex justify-end gap-3">
            <Button variant="outline" type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Provider'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

// Helper
function getProviderIcon(_type: string, displayName: string) {
  const name = displayName.toLowerCase();

  if (name.includes('google')) {
    return (
      <svg className="w-6 h-6" viewBox="0 0 24 24" fill="none">
        <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
        <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
        <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
        <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
      </svg>
    );
  }

  if (name.includes('microsoft') || name.includes('azure')) {
    return (
      <svg className="w-6 h-6" viewBox="0 0 24 24" fill="none">
        <path d="M11.4 11.4H2V2h9.4v9.4z" fill="#F35325" />
        <path d="M22 11.4h-9.4V2H22v9.4z" fill="#81BC06" />
        <path d="M11.4 22H2v-9.4h9.4V22z" fill="#05A6F0" />
        <path d="M22 22h-9.4v-9.4H22V22z" fill="#FFBA08" />
      </svg>
    );
  }

  if (name.includes('okta')) {
    return (
      <svg className="w-6 h-6" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="10" fill="#007DC1" />
        <circle cx="12" cy="12" r="5" fill="white" />
      </svg>
    );
  }

  // Default SSO icon
  return (
    <svg className="w-6 h-6 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
    </svg>
  );
}
