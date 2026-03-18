import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type ThemePreference = 'dark' | 'light' | 'system';

interface NotificationSettings {
  email: boolean;
  push: boolean;
  slack: boolean;
  weeklyDigest: boolean;
}

interface UserProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  avatarUrl?: string;
  bio: string;
  timezone: string;
  theme: ThemePreference;
  notifications: NotificationSettings;
}

// ============================================================================
// API
// ============================================================================

async function fetchProfile(): Promise<UserProfile> {
  const res = await fetch('/api/user/profile', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load profile');
  return res.json();
}

async function updateProfile(profile: Partial<UserProfile>): Promise<UserProfile> {
  const res = await fetch('/api/user/profile', {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
    },
    body: JSON.stringify(profile),
  });
  if (!res.ok) throw new Error('Failed to update profile');
  return res.json();
}

// ============================================================================
// Sub-components
// ============================================================================

interface ToggleProps {
  enabled: boolean;
  onChange: (value: boolean) => void;
  label: string;
  description?: string;
}

const Toggle: React.FC<ToggleProps> = ({ enabled, onChange, label, description }) => (
  <div className="flex items-center justify-between py-3">
    <div>
      <p className="text-sm font-medium text-zinc-200">{label}</p>
      {description && <p className="text-xs text-zinc-500 mt-0.5">{description}</p>}
    </div>
    <button
      type="button"
      role="switch"
      aria-checked={enabled}
      onClick={() => onChange(!enabled)}
      className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
        enabled ? 'bg-blue-600' : 'bg-zinc-700'
      }`}
    >
      <span
        className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
          enabled ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  </div>
);

// ============================================================================
// Component
// ============================================================================

/**
 * ProfilePage — User profile settings.
 *
 * @doc.type component
 * @doc.purpose User profile, notifications, and theme settings
 * @doc.layer product
 */
const ProfilePage: React.FC = () => {
  const queryClient = useQueryClient();
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  const { data: profile, isLoading, error } = useQuery<UserProfile>({
    queryKey: ['user-profile'],
    queryFn: fetchProfile,
  });

  const [formData, setFormData] = useState<Partial<UserProfile>>({});

  // Merge fetched profile with local edits
  const merged: UserProfile | undefined = profile
    ? { ...profile, ...formData, notifications: { ...profile.notifications, ...formData.notifications } }
    : undefined;

  const mutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-profile'] });
      setFormData({});
      setSaveMessage('Profile updated successfully');
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  const handleFieldChange = useCallback(
    (field: keyof UserProfile, value: string) => {
      setFormData((prev) => ({ ...prev, [field]: value }));
    },
    [],
  );

  const handleNotifChange = useCallback(
    (field: keyof NotificationSettings, value: boolean) => {
      setFormData((prev) => ({
        ...prev,
        notifications: { ...profile?.notifications, ...prev.notifications, [field]: value },
      }));
    },
    [profile],
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (Object.keys(formData).length > 0) {
      mutation.mutate(formData);
    }
  };

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          Failed to load profile: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (isLoading || !merged) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Profile Settings</h1>
        <p className="text-sm text-zinc-400 mt-1">Manage your account details and preferences</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-8">
        {/* Avatar & Identity */}
        <section className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 space-y-5">
          <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wider">Account</h2>

          <div className="flex items-center gap-5">
            <div className="w-16 h-16 rounded-full bg-blue-600/20 flex items-center justify-center text-2xl font-bold text-blue-400 shrink-0">
              {merged.name.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 space-y-1">
              <p className="text-sm font-medium text-zinc-200">{merged.name}</p>
              <p className="text-xs text-zinc-500">{merged.role}</p>
              <button
                type="button"
                className="text-xs text-blue-400 hover:text-blue-300 transition-colors"
              >
                Change avatar
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <label className="block">
              <span className="text-xs font-medium text-zinc-400">Full Name</span>
              <input
                type="text"
                value={merged.name}
                onChange={(e) => handleFieldChange('name', e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-zinc-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-zinc-400">Email</span>
              <input
                type="email"
                value={merged.email}
                onChange={(e) => handleFieldChange('email', e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-zinc-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
              />
            </label>
          </div>

          <label className="block">
            <span className="text-xs font-medium text-zinc-400">Bio</span>
            <textarea
              value={merged.bio}
              onChange={(e) => handleFieldChange('bio', e.target.value)}
              rows={3}
              className="mt-1 block w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-zinc-100 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
            />
          </label>

          <label className="block">
            <span className="text-xs font-medium text-zinc-400">Timezone</span>
            <input
              type="text"
              value={merged.timezone}
              onChange={(e) => handleFieldChange('timezone', e.target.value)}
              className="mt-1 block w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-zinc-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
            />
          </label>
        </section>

        {/* Theme */}
        <section className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 space-y-4">
          <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wider">Appearance</h2>
          <div className="flex gap-3">
            {(['dark', 'light', 'system'] as ThemePreference[]).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setFormData((prev) => ({ ...prev, theme: t }))}
                className={`flex-1 px-4 py-3 rounded-lg border text-sm font-medium capitalize transition-colors ${
                  merged.theme === t
                    ? 'border-blue-500 bg-blue-600/10 text-blue-400'
                    : 'border-zinc-800 bg-zinc-800/50 text-zinc-400 hover:text-zinc-200'
                }`}
              >
                {t}
              </button>
            ))}
          </div>
        </section>

        {/* Notifications */}
        <section className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 space-y-1">
          <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wider mb-3">Notifications</h2>
          <Toggle
            label="Email notifications"
            description="Receive project updates via email"
            enabled={merged.notifications.email}
            onChange={(v) => handleNotifChange('email', v)}
          />
          <Toggle
            label="Push notifications"
            description="Browser push notifications"
            enabled={merged.notifications.push}
            onChange={(v) => handleNotifChange('push', v)}
          />
          <Toggle
            label="Slack notifications"
            description="Receive alerts in your Slack workspace"
            enabled={merged.notifications.slack}
            onChange={(v) => handleNotifChange('slack', v)}
          />
          <Toggle
            label="Weekly digest"
            description="Summary of activity sent every Monday"
            enabled={merged.notifications.weeklyDigest}
            onChange={(v) => handleNotifChange('weeklyDigest', v)}
          />
        </section>

        {/* Actions */}
        <div className="flex items-center justify-between">
          {saveMessage && (
            <p className="text-sm text-emerald-400">{saveMessage}</p>
          )}
          {mutation.error && (
            <p className="text-sm text-red-400">
              {mutation.error instanceof Error ? mutation.error.message : 'Save failed'}
            </p>
          )}
          <div className="ml-auto flex gap-3">
            <button
              type="button"
              onClick={() => setFormData({})}
              className="px-4 py-2 text-sm font-medium text-zinc-400 hover:text-zinc-200 transition-colors"
            >
              Reset
            </button>
            <button
              type="submit"
              disabled={mutation.isPending || Object.keys(formData).length === 0}
              className="px-5 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors"
            >
              {mutation.isPending ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
};

export default ProfilePage;
