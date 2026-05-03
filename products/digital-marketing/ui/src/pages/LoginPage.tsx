/**
 * Login page.
 *
 * @doc.type page
 * @doc.purpose Entry point for unauthenticated users
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '@/context/AuthContext';

export function LoginPage(): React.ReactElement {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [workspaceId, setWorkspaceId] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent): void {
    e.preventDefault();
    if (!token.trim() || !workspaceId.trim() || !tenantId.trim()) {
      setError('All fields are required.');
      return;
    }
    login(token.trim(), workspaceId.trim(), tenantId.trim());
    void navigate(`/workspaces/${workspaceId.trim()}/dashboard`);
  }

  return (
    <main
      data-testid="login-page"
      className="min-h-screen flex items-center justify-center bg-gray-50"
    >
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-sm">
        <h1 className="text-xl font-bold mb-6">DMOS — Sign In</h1>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="token" className="block text-sm font-medium mb-1">
              Bearer Token
            </label>
            <input
              id="token"
              type="password"
              data-testid="login-token"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label
              htmlFor="workspaceId"
              className="block text-sm font-medium mb-1"
            >
              Workspace ID
            </label>
            <input
              id="workspaceId"
              type="text"
              data-testid="login-workspace-id"
              value={workspaceId}
              onChange={(e) => setWorkspaceId(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label
              htmlFor="tenantId"
              className="block text-sm font-medium mb-1"
            >
              Tenant ID
            </label>
            <input
              id="tenantId"
              type="text"
              data-testid="login-tenant-id"
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm"
            />
          </div>

          {error && (
            <p role="alert" className="text-red-600 text-sm">
              {error}
            </p>
          )}

          <button
            type="submit"
            data-testid="login-submit"
            className="w-full bg-blue-600 text-white rounded px-4 py-2 text-sm hover:bg-blue-700"
          >
            Sign In
          </button>
        </form>
      </div>
    </main>
  );
}
