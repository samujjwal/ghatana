// @ts-nocheck
import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import DeleteMyDataSection from '@/components/admin/DeleteMyDataSection';

// ============================================================================
// Types
// ============================================================================

type ThemePreference = 'dark' | 'light' | 'system';

const NativeButton = React.forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement>>((props, ref) =>
  React.createElement('button', { ...props, ref }),
);
NativeButton.displayName = 'NativeButton';

const NativeInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>((props, ref) =>
  React.createElement('input', { ...props, ref }),
);
NativeInput.displayName = 'NativeInput';

const NativeTextarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>((props, ref) =>
  React.createElement('textarea', { ...props, ref }),
);
NativeTextarea.displayName = 'NativeTextarea';

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
  if (!res.ok) {
    throw new Error(await readErrorResponse(res, 'Failed to load profile'));
  }
  return parseJsonResponse<UserProfile>(res, 'settings profile');
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
  if (!res.ok) {
    throw new Error(await readErrorResponse(res, 'Failed to update profile'));
  }
  return parseJsonResponse<UserProfile>(res, 'settings profile update');
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
      <p className="text-sm font-medium text-fg-muted">{label}</p>
      {description && <p className="text-xs text-fg-muted mt-0.5">{description}</p>}
    </div>
    <NativeButton
      type="button"
      role="switch"
      aria-checked={enabled}
      onClick={() => onChange(!enabled)}
      className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
        enabled ? 'bg-primary' : 'bg-surface-muted'
      }`}
    >
      <span
        className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
          enabled ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </NativeButton>
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
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
          Failed to load profile: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (isLoading || !merged) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-fg-muted">Profile Settings</h1>
        <p className="text-sm text-fg-muted mt-1">Manage your account details and preferences</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-8">
        {/* Avatar & Identity */}
        <section className="bg-surface border border-border rounded-xl p-6 space-y-5">
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wider">Account</h2>

          <div className="flex items-center gap-5">
            <div className="w-16 h-16 rounded-full bg-primary/20 flex items-center justify-center text-2xl font-bold text-info-color shrink-0">
              {merged.name.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 space-y-1">
              <p className="text-sm font-medium text-fg-muted">{merged.name}</p>
              <p className="text-xs text-fg-muted">{merged.role}</p>
              <NativeButton
                type="button"
                className="text-xs text-info-color hover:text-info-color transition-colors"
              >
                Change avatar
              </NativeButton>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <label className="block">
              <span className="text-xs font-medium text-fg-muted">Full Name</span>
              <NativeInput
                type="text"
                value={merged.name}
                onChange={(e) => handleFieldChange('name', e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-surface border border-border rounded-lg text-fg-muted text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-info-border"
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-fg-muted">Email</span>
              <NativeInput
                type="email"
                value={merged.email}
                onChange={(e) => handleFieldChange('email', e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-surface border border-border rounded-lg text-fg-muted text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-info-border"
              />
            </label>
          </div>

          <label className="block">
            <span className="text-xs font-medium text-fg-muted">Bio</span>
            <NativeTextarea
              value={merged.bio}
              onChange={(e) => handleFieldChange('bio', e.target.value)}
              rows={3}
              className="mt-1 block w-full px-3 py-2 bg-surface border border-border rounded-lg text-fg-muted text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-info-border"
            />
          </label>

          <label className="block">
            <span className="text-xs font-medium text-fg-muted">Timezone</span>
            <NativeInput
              type="text"
              value={merged.timezone}
              onChange={(e) => handleFieldChange('timezone', e.target.value)}
              className="mt-1 block w-full px-3 py-2 bg-surface border border-border rounded-lg text-fg-muted text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-info-border"
            />
          </label>
        </section>

        {/* Theme */}
        <section className="bg-surface border border-border rounded-xl p-6 space-y-4">
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wider">Appearance</h2>
          <div className="flex gap-3">
            {(['dark', 'light', 'system'] as ThemePreference[]).map((t) => (
              <NativeButton
                key={t}
                type="button"
                onClick={() => setFormData((prev) => ({ ...prev, theme: t }))}
                className={`flex-1 px-4 py-3 rounded-lg border text-sm font-medium capitalize transition-colors ${
                  merged.theme === t
                    ? 'border-info-border bg-primary/10 text-info-color'
                    : 'border-border bg-surface/50 text-fg-muted hover:text-fg-muted'
                }`}
              >
                {t}
              </NativeButton>
            ))}
          </div>
        </section>

        {/* Notifications */}
        <section className="bg-surface border border-border rounded-xl p-6 space-y-1">
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wider mb-3">Notifications</h2>
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
            <p className="text-sm text-destructive">
              {mutation.error instanceof Error ? mutation.error.message : 'Save failed'}
            </p>
          )}
          <div className="ml-auto flex gap-3">
            <NativeButton
              type="button"
              onClick={() => setFormData({})}
              className="px-4 py-2 text-sm font-medium text-fg-muted hover:text-fg-muted transition-colors"
            >
              Reset
            </NativeButton>
            <NativeButton
              type="submit"
              disabled={mutation.isPending || Object.keys(formData).length === 0}
              className="px-5 py-2 bg-primary hover:bg-info-bg disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors"
            >
              {mutation.isPending ? 'Saving...' : 'Save Changes'}
            </NativeButton>
          </div>
        </div>

        {/* Data Privacy — C-Y15 / F-Y058 */}
        <DeleteMyDataSection className="mt-6" />
      </form>
    </div>
  );
};

export default ProfilePage;
