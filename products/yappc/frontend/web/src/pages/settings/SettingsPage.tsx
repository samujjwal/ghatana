import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { yappcApi } from '@/lib/api/client';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Textarea } from '../../components/ui/Textarea';
import { useI18n } from '../../i18n/I18nProvider';

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
  return yappcApi.settings.getWorkspaceSettings<WorkspaceSettings>();
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
        <p className="text-sm font-medium text-fg-muted">{label}</p>
        <p className="text-xs text-fg-muted">{description}</p>
      </div>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => onChange(!checked)}
        aria-pressed={checked}
        aria-label={`${checked ? 'Disable' : 'Enable'} ${label}`}
        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors [&>span]:inline-flex ${
          checked ? 'bg-primary' : 'bg-surface-muted'
        }`}
      >
        <span
          className={`inline-block h-4 w-4 rounded-full bg-white transition-transform ${
            checked ? 'translate-x-6' : 'translate-x-1'
          }`}
        />
      </Button>
    </div>
  );
}

function GeneralPanel({ settings }: { settings: WorkspaceSettings }) {
  const [name, setName] = useState(settings.general.name);
  const [description, setDescription] = useState(settings.general.description);
  const [timezone, setTimezone] = useState(settings.general.timezone);
  const [language, setLanguage] = useState(settings.general.language);
  const { t } = useI18n();

  return (
    <div className="space-y-6">
      <div>
        <label className="block text-sm font-medium text-fg-muted mb-1.5">{t('settings.general.workspaceName')}</label>
        <Input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg-muted placeholder-zinc-500 focus:border-info-border focus:ring-blue-500"
          placeholder="My Workspace"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-fg-muted mb-1.5">{t('settings.general.description')}</label>
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          fullWidth
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg-muted placeholder-zinc-500 focus:border-info-border focus:ring-blue-500"
          placeholder="What this workspace is about..."
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-fg-muted mb-1.5">{t('settings.general.timezone')}</label>
        <Input
          type="text"
          value={timezone}
          onChange={(e) => setTimezone(e.target.value)}
          fullWidth
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg-muted placeholder-zinc-500 focus:border-info-border focus:ring-blue-500"
          placeholder="America/New_York"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-fg-muted mb-1.5">{t('settings.general.language')}</label>
        <Select
          value={language}
          onChange={(e) => setLanguage(e.target.value)}
          fullWidth
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg-muted focus:border-info-border focus:ring-blue-500"
        >
          <option value="en">{t('settings.general.lang.en')}</option>
          <option value="es">{t('settings.general.lang.es')}</option>
          <option value="fr">{t('settings.general.lang.fr')}</option>
          <option value="de">{t('settings.general.lang.de')}</option>
          <option value="ja">{t('settings.general.lang.ja')}</option>
        </Select>
      </div>
      <div className="flex justify-end">
        <Button className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-info-bg">
          {t('settings.general.saveChanges')}
        </Button>
      </div>
    </div>
  );
}

function NotificationsPanel({ settings }: { settings: WorkspaceSettings }) {
  const [prefs, setPrefs] = useState(settings.notifications);
  const toggle = (key: keyof typeof prefs) => setPrefs((p) => ({ ...p, [key]: !p[key] }));
  const { t } = useI18n();

  return (
    <div className="divide-y divide-zinc-800">
      <ToggleSwitch
        label={t('settings.notif.email')}
        description={t('settings.notif.emailDesc')}
        checked={prefs.email}
        onChange={() => toggle('email')}
      />
      <ToggleSwitch
        label={t('settings.notif.push')}
        description={t('settings.notif.pushDesc')}
        checked={prefs.push}
        onChange={() => toggle('push')}
      />
      <ToggleSwitch
        label={t('settings.notif.weeklyDigest')}
        description={t('settings.notif.weeklyDigestDesc')}
        checked={prefs.weeklyDigest}
        onChange={() => toggle('weeklyDigest')}
      />
      <ToggleSwitch
        label={t('settings.notif.mentionsOnly')}
        description={t('settings.notif.mentionsOnlyDesc')}
        checked={prefs.mentionsOnly}
        onChange={() => toggle('mentionsOnly')}
      />
      <ToggleSwitch
        label={t('settings.notif.deployAlerts')}
        description={t('settings.notif.deployAlertsDesc')}
        checked={prefs.deployAlerts}
        onChange={() => toggle('deployAlerts')}
      />
      <ToggleSwitch
        label={t('settings.notif.prReviews')}
        description={t('settings.notif.prReviewsDesc')}
        checked={prefs.prReviews}
        onChange={() => toggle('prReviews')}
      />
      <div className="pt-4 flex justify-end">
        <Button className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-info-bg">
          {t('settings.notif.savePreferences')}
        </Button>
      </div>
    </div>
  );
}

function SecurityPanel({ settings }: { settings: WorkspaceSettings }) {
  const sec = settings.security;
  const { t } = useI18n();

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="bg-surface/50 rounded-lg p-4">
          <div className="flex items-center justify-between mb-1">
            <p className="text-sm font-medium text-fg-muted">{t('settings.security.twoFactor')}</p>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                sec.twoFactorEnabled ? 'bg-success-bg/20 text-success-color' : 'bg-surface-muted text-fg-muted'
              }`}
            >
              {sec.twoFactorEnabled ? t('settings.security.enabled') : t('settings.security.disabled')}
            </span>
          </div>
          <p className="text-xs text-fg-muted">{t('settings.security.twoFactorDesc')}</p>
          <Button variant="link" size="sm" className="mt-3 min-h-0 px-0 py-0 text-xs text-info-color hover:text-info-color">
            {sec.twoFactorEnabled ? t('settings.security.manage2fa') : t('settings.security.enable2fa')}
          </Button>
        </div>

        <div className="bg-surface/50 rounded-lg p-4">
          <div className="flex items-center justify-between mb-1">
            <p className="text-sm font-medium text-fg-muted">{t('settings.security.sso')}</p>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                sec.ssoEnabled ? 'bg-success-bg/20 text-success-color' : 'bg-surface-muted text-fg-muted'
              }`}
            >
              {sec.ssoEnabled ? t('settings.security.enabled') : t('settings.security.disabled')}
            </span>
          </div>
          <p className="text-xs text-fg-muted">{t('settings.security.ssoDesc')}</p>
          <Button variant="link" size="sm" className="mt-3 min-h-0 px-0 py-0 text-xs text-info-color hover:text-info-color">
            {t('settings.security.configureSso')}
          </Button>
        </div>
      </div>

      <div>
        <p className="text-sm font-medium text-fg-muted mb-1">{t('settings.security.sessionTimeout')}</p>
        <p className="text-xs text-fg-muted mb-2">{t('settings.security.sessionTimeoutDesc')}</p>
        <span className="text-sm text-fg-muted">{t('settings.security.sessionTimeoutValue', { minutes: String(sec.sessionTimeout) })}</span>
      </div>

      <div>
        <p className="text-sm font-medium text-fg-muted mb-1">{t('settings.security.lastPasswordChange')}</p>
        <p className="text-sm text-fg-muted">
          {sec.lastPasswordChange ? new Date(sec.lastPasswordChange).toLocaleDateString() : t('settings.security.never')}
        </p>
        <Button variant="link" size="sm" className="mt-2 min-h-0 px-0 py-0 text-xs text-info-color hover:text-info-color">
          {t('settings.security.changePassword')}
        </Button>
      </div>

      <div>
        <p className="text-sm font-medium text-fg-muted mb-1">{t('settings.security.ipAllowlist')}</p>
        <p className="text-xs text-fg-muted mb-2">{t('settings.security.ipAllowlistDesc')}</p>
        {sec.ipAllowlist.length === 0 ? (
          <p className="text-sm text-fg-muted">{t('settings.security.noIpRestrictions')}</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {sec.ipAllowlist.map((ip) => (
              <span key={ip} className="rounded bg-surface px-2 py-1 text-xs text-fg-muted font-mono">
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
  const { t } = useI18n();
  return (
    <div className="grid gap-3">
      {settings.integrations.length === 0 ? (
        <p className="text-sm text-fg-muted py-4">{t('settings.integrations.noIntegrations')}</p>
      ) : (
        settings.integrations.map((integration) => (
          <div
            key={integration.id}
            className="flex items-center justify-between rounded-lg border border-border bg-surface/30 p-4"
          >
            <div className="flex items-center gap-3">
              <span className="text-2xl">{integration.icon}</span>
              <div>
                <p className="text-sm font-medium text-fg-muted">{integration.name}</p>
                <p className="text-xs text-fg-muted">{integration.description}</p>
                {integration.lastSync && (
                  <p className="text-xs text-fg-muted mt-0.5">
                    {t('settings.integrations.lastSynced', { date: new Date(integration.lastSync).toLocaleDateString() })}
                  </p>
                )}
              </div>
            </div>
            <Button
              variant={integration.connected ? 'outline' : 'solid'}
              size="sm"
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                integration.connected
                  ? 'border border-border text-fg-muted hover:bg-surface-muted'
                  : 'bg-primary text-white hover:bg-info-bg'
              }`}
            >
              {integration.connected ? t('settings.integrations.disconnect') : t('settings.integrations.connect')}
            </Button>
          </div>
        ))
      )}
    </div>
  );
}

function DangerZonePanel() {
  const [confirmText, setConfirmText] = useState('');
  const { t } = useI18n();

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-destructive-border/50 p-4">
        <h3 className="text-sm font-semibold text-destructive mb-1">{t('settings.danger.transferTitle')}</h3>
        <p className="text-xs text-fg-muted mb-3">
          {t('settings.danger.transferDesc')}
        </p>
        <Button variant="outline" tone="danger" size="sm" className="rounded-lg border border-destructive-border px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive-bg/20">
          {t('settings.danger.transferAction')}
        </Button>
      </div>

      <div className="rounded-lg border border-destructive-border/50 p-4">
        <h3 className="text-sm font-semibold text-destructive mb-1">{t('settings.danger.deleteTitle')}</h3>
        <p className="text-xs text-fg-muted mb-3">
          {t('settings.danger.deleteDesc')}
        </p>
        <div className="flex items-center gap-3">
          <Input
            type="text"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder={t('settings.danger.deleteConfirmPlaceholder')}
            size="sm"
            className="rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-fg-muted placeholder-zinc-500 focus:border-destructive-border focus:ring-red-500"
          />
          <Button
            tone="danger"
            size="sm"
            disabled={confirmText !== 'DELETE'}
            className="rounded-lg bg-destructive-bg px-3 py-1.5 text-xs font-medium text-white hover:bg-destructive-bg disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {t('settings.danger.deleteAction')}
          </Button>
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
  const { t } = useI18n();

  const { data, isLoading, error } = useQuery<WorkspaceSettings>({
    queryKey: ['workspace-settings'],
    queryFn: fetchSettings,
  });

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-fg-muted">{t('settings.title')}</h1>
        <p className="mt-1 text-sm text-fg-muted">{t('settings.subtitle')}</p>
      </div>

      <div className="flex gap-8">
        {/* Sidebar Navigation */}
        <nav className="w-52 shrink-0 space-y-1">
          {TABS.map((tab) => (
            <Button
              key={tab}
              variant="ghost"
              fullWidth
              onClick={() => setActiveTab(tab)}
              aria-pressed={activeTab === tab}
              className={`justify-start gap-2.5 text-left px-3 py-2 text-sm rounded-lg transition-colors [&>span]:flex [&>span]:items-center [&>span]:gap-2.5 ${
                activeTab === tab
                  ? 'bg-surface text-fg-muted'
                  : tab === 'Danger Zone'
                    ? 'text-destructive/70 hover:text-destructive hover:bg-destructive-bg/10'
                    : 'text-fg-muted hover:text-fg-muted hover:bg-surface/50'
              }`}
            >
              {TAB_ICONS[tab]}
              {tab}
            </Button>
          ))}
        </nav>

        {/* Content Panel */}
        <main className="flex-1 bg-surface border border-border rounded-lg p-6 min-h-[400px]">
          <h2 className="text-lg font-semibold text-fg-muted mb-6">{activeTab}</h2>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
            </div>
          ) : error || !data ? (
            <div className="rounded-lg border border-destructive-border bg-destructive-bg/20 p-4">
              <p className="text-sm text-destructive">{t('settings.loadError')}</p>
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
