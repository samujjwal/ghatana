import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type SettingsTab = 'General' | 'Notifications' | 'Security' | 'Integrations' | 'Danger Zone';

interface WorkspaceSettings {
  general: {
    name: string;
    description: string;
    timezone: string;
    language: string;
  };
  notifications: {
    email: boolean;
    push: boolean;
    weeklyDigest: boolean;
    mentionsOnly: boolean;
    deployAlerts: boolean;
    prReviews: boolean;
  };
  security: {
    twoFactorEnabled: boolean;
    ssoEnabled: boolean;
    ipAllowlist: string[];
    sessionTimeout: number;
    lastPasswordChange: string;
  };
  integrations: Array<{
    id: string;
    name: string;
    icon: string;
    connected: boolean;
    description: string;
    lastSync?: string;
  }>;
}

// ============================================================================
// Constants
// ============================================================================

const TABS: SettingsTab[] = ['General', 'Notifications', 'Security', 'Integrations', 'Danger Zone'];

const TAB_ICONS: Record<SettingsTab, React.ReactNode> = {
  General: (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  ),
  Notifications: (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
    </svg>
  ),
  Security: (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
    </svg>
  ),
  Integrations: (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
    </svg>
  ),
  'Danger Zone': (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>
  ),
};

// ============================================================================
// API
// ============================================================================

async function fetchSettings(): Promise<WorkspaceSettings> {
  const res = await fetch('/api/settings', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load settings');
  return res.json();
}

// ============================================================================
// Sub-components
// ============================================================================

function ToggleSwitch({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between py-3">
      <div>
        <p className="text-sm font-medium text-zinc-200">{label}</p>
        <p className="text-xs text-zinc-500">{description}</p>
      </div>
      <button
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
          checked ? 'bg-blue-600' : 'bg-zinc-700'
        }`}
      >
        <span
          className={`inline-block h-4 w-4 rounded-full bg-white transition-transform ${
            checked ? 'translate-x-6' : 'translate-x-1'
          }`}
        />
      </button>
    </div>
  );
}

function GeneralPanel({ settings }: { settings: WorkspaceSettings }) {
  const [name, setName] = useState(settings.general.name);
  const [description, setDescription] = useState(settings.general.description);
  const [timezone, setTimezone] = useState(settings.general.timezone);

  return (
    <div className="space-y-6">
      <div>
        <label className="block text-sm font-medium text-zinc-300 mb-1.5">Workspace Name</label>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-200 placeholder-zinc-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          placeholder="My Workspace"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-zinc-300 mb-1.5">Description</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-200 placeholder-zinc-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          placeholder="What this workspace is about..."
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-zinc-300 mb-1.5">Timezone</label>
        <input
          type="text"
          value={timezone}
          onChange={(e) => setTimezone(e.target.value)}
          className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-200 placeholder-zinc-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          placeholder="America/New_York"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-zinc-300 mb-1.5">Language</label>
        <select
          value={settings.general.language}
          className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-200 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        >
          <option value="en">English</option>
          <option value="es">Spanish</option>
          <option value="fr">French</option>
          <option value="de">German</option>
          <option value="ja">Japanese</option>
        </select>
      </div>
      <div className="flex justify-end">
        <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 transition-colors">
          Save Changes
        </button>
      </div>
    </div>
  );
}

function NotificationsPanel({ settings }: { settings: WorkspaceSettings }) {
  const [prefs, setPrefs] = useState(settings.notifications);
  const toggle = (key: keyof typeof prefs) => setPrefs((p) => ({ ...p, [key]: !p[key] }));

  return (
    <div className="divide-y divide-zinc-800">
      <ToggleSwitch
        label="Email Notifications"
        description="Receive email for important updates"
        checked={prefs.email}
        onChange={() => toggle('email')}
      />
      <ToggleSwitch
        label="Push Notifications"
        description="Browser push notifications"
        checked={prefs.push}
        onChange={() => toggle('push')}
      />
      <ToggleSwitch
        label="Weekly Digest"
        description="Receive a weekly summary email"
        checked={prefs.weeklyDigest}
        onChange={() => toggle('weeklyDigest')}
      />
      <ToggleSwitch
        label="Mentions Only"
        description="Only notify when you are @mentioned"
        checked={prefs.mentionsOnly}
        onChange={() => toggle('mentionsOnly')}
      />
      <ToggleSwitch
        label="Deploy Alerts"
        description="Get notified on deployment events"
        checked={prefs.deployAlerts}
        onChange={() => toggle('deployAlerts')}
      />
      <ToggleSwitch
        label="PR Reviews"
        description="Notify when you are requested for review"
        checked={prefs.prReviews}
        onChange={() => toggle('prReviews')}
      />
      <div className="pt-4 flex justify-end">
        <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 transition-colors">
          Save Preferences
        </button>
      </div>
    </div>
  );
}

function SecurityPanel({ settings }: { settings: WorkspaceSettings }) {
  const sec = settings.security;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="bg-zinc-800/50 rounded-lg p-4">
          <div className="flex items-center justify-between mb-1">
            <p className="text-sm font-medium text-zinc-200">Two-Factor Auth</p>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                sec.twoFactorEnabled ? 'bg-green-600/20 text-green-400' : 'bg-zinc-700 text-zinc-400'
              }`}
            >
              {sec.twoFactorEnabled ? 'Enabled' : 'Disabled'}
            </span>
          </div>
          <p className="text-xs text-zinc-500">Add an extra layer of security to your account</p>
          <button className="mt-3 text-xs text-blue-400 hover:text-blue-300 transition-colors">
            {sec.twoFactorEnabled ? 'Manage 2FA' : 'Enable 2FA'}
          </button>
        </div>

        <div className="bg-zinc-800/50 rounded-lg p-4">
          <div className="flex items-center justify-between mb-1">
            <p className="text-sm font-medium text-zinc-200">SSO</p>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                sec.ssoEnabled ? 'bg-green-600/20 text-green-400' : 'bg-zinc-700 text-zinc-400'
              }`}
            >
              {sec.ssoEnabled ? 'Enabled' : 'Disabled'}
            </span>
          </div>
          <p className="text-xs text-zinc-500">Single sign-on for enterprise authentication</p>
          <button className="mt-3 text-xs text-blue-400 hover:text-blue-300 transition-colors">
            Configure SSO
          </button>
        </div>
      </div>

      <div>
        <p className="text-sm font-medium text-zinc-200 mb-1">Session Timeout</p>
        <p className="text-xs text-zinc-500 mb-2">Auto-logout after inactivity</p>
        <span className="text-sm text-zinc-300">{sec.sessionTimeout} minutes</span>
      </div>

      <div>
        <p className="text-sm font-medium text-zinc-200 mb-1">Last Password Change</p>
        <p className="text-sm text-zinc-400">
          {sec.lastPasswordChange ? new Date(sec.lastPasswordChange).toLocaleDateString() : 'Never'}
        </p>
        <button className="mt-2 text-xs text-blue-400 hover:text-blue-300 transition-colors">
          Change Password
        </button>
      </div>

      <div>
        <p className="text-sm font-medium text-zinc-200 mb-1">IP Allowlist</p>
        <p className="text-xs text-zinc-500 mb-2">Restrict access to specific IP addresses</p>
        {sec.ipAllowlist.length === 0 ? (
          <p className="text-sm text-zinc-500">No IP restrictions configured.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {sec.ipAllowlist.map((ip) => (
              <span key={ip} className="rounded bg-zinc-800 px-2 py-1 text-xs text-zinc-300 font-mono">
                {ip}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function IntegrationsPanel({ settings }: { settings: WorkspaceSettings }) {
  return (
    <div className="grid gap-3">
      {settings.integrations.length === 0 ? (
        <p className="text-sm text-zinc-500 py-4">No integrations available.</p>
      ) : (
        settings.integrations.map((integration) => (
          <div
            key={integration.id}
            className="flex items-center justify-between rounded-lg border border-zinc-800 bg-zinc-800/30 p-4"
          >
            <div className="flex items-center gap-3">
              <span className="text-2xl">{integration.icon}</span>
              <div>
                <p className="text-sm font-medium text-zinc-200">{integration.name}</p>
                <p className="text-xs text-zinc-500">{integration.description}</p>
                {integration.lastSync && (
                  <p className="text-xs text-zinc-600 mt-0.5">
                    Last synced: {new Date(integration.lastSync).toLocaleDateString()}
                  </p>
                )}
              </div>
            </div>
            <button
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                integration.connected
                  ? 'border border-zinc-700 text-zinc-300 hover:bg-zinc-700'
                  : 'bg-blue-600 text-white hover:bg-blue-500'
              }`}
            >
              {integration.connected ? 'Disconnect' : 'Connect'}
            </button>
          </div>
        ))
      )}
    </div>
  );
}

function DangerZonePanel() {
  const [confirmText, setConfirmText] = useState('');

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-red-800/50 p-4">
        <h3 className="text-sm font-semibold text-red-400 mb-1">Transfer Workspace</h3>
        <p className="text-xs text-zinc-400 mb-3">
          Transfer ownership of this workspace to another user.
        </p>
        <button className="rounded-lg border border-red-700 px-3 py-1.5 text-xs font-medium text-red-400 hover:bg-red-900/20 transition-colors">
          Transfer Ownership
        </button>
      </div>

      <div className="rounded-lg border border-red-800/50 p-4">
        <h3 className="text-sm font-semibold text-red-400 mb-1">Delete Workspace</h3>
        <p className="text-xs text-zinc-400 mb-3">
          Permanently delete this workspace and all its data. This action cannot be undone.
        </p>
        <div className="flex items-center gap-3">
          <input
            type="text"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder='Type "DELETE" to confirm'
            className="rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-1.5 text-sm text-zinc-200 placeholder-zinc-500 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
          />
          <button
            disabled={confirmText !== 'DELETE'}
            className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-red-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Delete Workspace
          </button>
        </div>
      </div>
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * SettingsPage — Workspace settings with tabbed panels.
 *
 * @doc.type component
 * @doc.purpose Workspace settings management with General, Notifications, Security, Integrations, and Danger Zone tabs
 * @doc.layer product
 */
const SettingsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<SettingsTab>('General');

  const { data, isLoading, error } = useQuery<WorkspaceSettings>({
    queryKey: ['workspace-settings'],
    queryFn: fetchSettings,
  });

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-zinc-100">Workspace Settings</h1>
        <p className="mt-1 text-sm text-zinc-400">Manage your workspace configuration and preferences</p>
      </div>

      <div className="flex gap-8">
        {/* Sidebar Navigation */}
        <nav className="w-52 shrink-0 space-y-1">
          {TABS.map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`w-full flex items-center gap-2.5 text-left px-3 py-2 text-sm rounded-lg transition-colors ${
                activeTab === tab
                  ? 'bg-zinc-800 text-zinc-100'
                  : tab === 'Danger Zone'
                    ? 'text-red-400/70 hover:text-red-400 hover:bg-red-900/10'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/50'
              }`}
            >
              {TAB_ICONS[tab]}
              {tab}
            </button>
          ))}
        </nav>

        {/* Content Panel */}
        <main className="flex-1 bg-zinc-900 border border-zinc-800 rounded-lg p-6 min-h-[400px]">
          <h2 className="text-lg font-semibold text-zinc-100 mb-6">{activeTab}</h2>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
            </div>
          ) : error || !data ? (
            <div className="rounded-lg border border-red-800 bg-red-900/20 p-4">
              <p className="text-sm text-red-400">Failed to load settings.</p>
            </div>
          ) : (
            <>
              {activeTab === 'General' && <GeneralPanel settings={data} />}
              {activeTab === 'Notifications' && <NotificationsPanel settings={data} />}
              {activeTab === 'Security' && <SecurityPanel settings={data} />}
              {activeTab === 'Integrations' && <IntegrationsPanel settings={data} />}
              {activeTab === 'Danger Zone' && <DangerZonePanel />}
            </>
          )}
        </main>
      </div>
    </div>
  );
};

export default SettingsPage;
