/**
 * Settings - Platform Settings and Integrations
 *
 * Configure platform settings, integrations, and AI guardrails.
 * Connected to real Admin APIs via useAdminApi hooks.
 *
 * @doc.type route
 * @doc.section ADMIN
 */

import { useState } from 'react';
import { MainLayout } from '@/app/Layout';
import {
    Settings,
    Bell,
    Palette,
    Globe,
    Database,
    Plug,
    Bot,
    ArrowLeft,
    Check,
    X,
    Loader2,
    AlertCircle,
    RefreshCw,
    TestTube2,
} from 'lucide-react';
import {
    usePlatformSettings,
    useUpdatePlatformSettings,
    useIntegrations,
    useTestIntegration,
    useCreateIntegration,
    useUpdateIntegration,
    useAIAgentSettings,
    useUpdateAIAgentSettings,
    type PlatformSettingsResponse,
    type IntegrationResponse,
    type AIAgentSettingsResponse,
} from '@/hooks';
import { Drawer, FormField, Input, Textarea, Select, Checkbox } from '@/components/admin';

type SettingsView = 'grid' | 'general' | 'notifications' | 'appearance' | 'integrations' | 'data' | 'localization' | 'ai-agents';

const settingsSections = [
    { id: 'general' as const, name: 'General', description: 'Basic application settings', icon: Settings },
    { id: 'notifications' as const, name: 'Notifications', description: 'Email and alert preferences', icon: Bell },
    { id: 'appearance' as const, name: 'Appearance', description: 'Theme and display settings', icon: Palette },
    { id: 'integrations' as const, name: 'Integrations', description: 'Third-party connections', icon: Plug },
    { id: 'data' as const, name: 'Data & Storage', description: 'Database and backup settings', icon: Database },
    { id: 'localization' as const, name: 'Localization', description: 'Language and timezone', icon: Globe },
    { id: 'ai-agents' as const, name: 'AI & Agents', description: 'AI guardrails and agent settings', icon: Bot },
];

interface LoadingStateProps {
    message?: string;
}

function LoadingState({ message = 'Loading...' }: LoadingStateProps) {
    return (
        <div className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-blue-600 mr-2" />
            <span className="text-gray-600 dark:text-gray-400">{message}</span>
        </div>
    );
}

interface ErrorStateProps {
    message: string;
    onRetry?: () => void;
}

function ErrorState({ message, onRetry }: ErrorStateProps) {
    return (
        <div className="flex flex-col items-center justify-center py-12">
            <AlertCircle className="h-8 w-8 text-red-500 mb-2" />
            <p className="text-gray-600 dark:text-gray-400 mb-4">{message}</p>
            {onRetry && (
                <button
                    onClick={onRetry}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
                >
                    Retry
                </button>
            )}
        </div>
    );
}

interface SectionHeaderProps {
    title: string;
    description: string;
    onBack: () => void;
}

function SectionHeader({ title, description, onBack }: SectionHeaderProps) {
    return (
        <div className="flex items-center gap-4 mb-6">
            <button
                onClick={onBack}
                className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-800 transition-colors"
            >
                <ArrowLeft className="h-5 w-5 text-gray-500" />
            </button>
            <div>
                <h2 className="text-xl font-bold text-gray-900 dark:text-white">{title}</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400">{description}</p>
            </div>
        </div>
    );
}

interface GeneralSettingsProps {
    settings: PlatformSettingsResponse | undefined;
    isLoading: boolean;
    error: Error | null;
    onBack: () => void;
    onSave: (settings: Partial<PlatformSettingsResponse>) => void;
    isSaving: boolean;
}

function GeneralSettings({ settings, isLoading, error, onBack, onSave, isSaving }: GeneralSettingsProps) {
    const [displayName, setDisplayName] = useState(settings?.displayName || '');

    if (isLoading) return <LoadingState message="Loading settings..." />;
    if (error) return <ErrorState message="Failed to load settings" />;

    const handleSave = () => {
        onSave({ displayName });
    };

    return (
        <div>
            <SectionHeader title="General Settings" description="Basic application settings" onBack={onBack} />

            <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-6 space-y-6">
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Display Name
                    </label>
                    <input
                        type="text"
                        value={displayName}
                        onChange={(e) => setDisplayName(e.target.value)}
                        placeholder="Organization name"
                        className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                    <button
                        onClick={onBack}
                        className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={isSaving}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
                    >
                        {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                        Save Changes
                    </button>
                </div>
            </div>
        </div>
    );
}

interface AppearanceSettingsProps {
    settings: PlatformSettingsResponse | undefined;
    isLoading: boolean;
    error: Error | null;
    onBack: () => void;
    onSave: (settings: Partial<PlatformSettingsResponse>) => void;
    isSaving: boolean;
}

function AppearanceSettings({ settings, isLoading, error, onBack, onSave, isSaving }: AppearanceSettingsProps) {
    const [theme, setTheme] = useState(settings?.defaultTheme || 'system');

    if (isLoading) return <LoadingState message="Loading settings..." />;
    if (error) return <ErrorState message="Failed to load settings" />;

    const handleSave = () => {
        onSave({ defaultTheme: theme });
    };

    return (
        <div>
            <SectionHeader title="Appearance" description="Theme and display settings" onBack={onBack} />

            <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-6 space-y-6">
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-4">
                        Default Theme
                    </label>
                    <div className="grid grid-cols-3 gap-3">
                        {['light', 'dark', 'system'].map((t) => (
                            <button
                                key={t}
                                onClick={() => setTheme(t)}
                                className={`p-4 rounded-lg border-2 transition-all ${
                                    theme === t
                                        ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-gray-200 dark:border-slate-700 hover:border-gray-300 dark:hover:border-slate-600'
                                }`}
                            >
                                <Palette
                                    className={`h-6 w-6 mx-auto mb-2 ${
                                        theme === t ? 'text-blue-600' : 'text-gray-400'
                                    }`}
                                />
                                <span
                                    className={`text-sm font-medium capitalize ${
                                        theme === t ? 'text-blue-600' : 'text-gray-600 dark:text-gray-400'
                                    }`}
                                >
                                    {t}
                                </span>
                            </button>
                        ))}
                    </div>
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                    <button
                        onClick={onBack}
                        className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={isSaving}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
                    >
                        {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                        Save Changes
                    </button>
                </div>
            </div>
        </div>
    );
}

interface LocalizationSettingsProps {
    settings: PlatformSettingsResponse | undefined;
    isLoading: boolean;
    error: Error | null;
    onBack: () => void;
    onSave: (settings: Partial<PlatformSettingsResponse>) => void;
    isSaving: boolean;
}

function LocalizationSettings({ settings, isLoading, error, onBack, onSave, isSaving }: LocalizationSettingsProps) {
    const [locale, setLocale] = useState(settings?.defaultLocale || 'en-US');
    const [timezone, setTimezone] = useState(settings?.defaultTimezone || 'UTC');

    if (isLoading) return <LoadingState message="Loading settings..." />;
    if (error) return <ErrorState message="Failed to load settings" />;

    const handleSave = () => {
        onSave({ defaultLocale: locale, defaultTimezone: timezone });
    };

    return (
        <div>
            <SectionHeader title="Localization" description="Language and timezone settings" onBack={onBack} />

            <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-6 space-y-6">
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Default Locale
                    </label>
                    <select
                        value={locale}
                        onChange={(e) => setLocale(e.target.value)}
                        className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100"
                    >
                        <option value="en-US">English (US)</option>
                        <option value="en-GB">English (UK)</option>
                        <option value="es-ES">Spanish</option>
                        <option value="fr-FR">French</option>
                        <option value="de-DE">German</option>
                        <option value="ja-JP">Japanese</option>
                    </select>
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Default Timezone
                    </label>
                    <select
                        value={timezone}
                        onChange={(e) => setTimezone(e.target.value)}
                        className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100"
                    >
                        <option value="UTC">UTC</option>
                        <option value="America/New_York">Eastern Time</option>
                        <option value="America/Los_Angeles">Pacific Time</option>
                        <option value="Europe/London">London</option>
                        <option value="Europe/Paris">Paris</option>
                        <option value="Asia/Tokyo">Tokyo</option>
                    </select>
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                    <button
                        onClick={onBack}
                        className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={isSaving}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
                    >
                        {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                        Save Changes
                    </button>
                </div>
            </div>
        </div>
    );
}

interface IntegrationsViewProps {
    integrations: IntegrationResponse[];
    isLoading: boolean;
    error: Error | null;
    onBack: () => void;
    onTest: (id: string) => void;
    testingId: string | null;
    onConfigure: (integration: IntegrationResponse) => void;
    onAddNew: () => void;
}

function IntegrationsView({ 
    integrations, 
    isLoading, 
    error, 
    onBack, 
    onTest, 
    testingId, 
    onConfigure,
    onAddNew 
}: IntegrationsViewProps) {
    if (isLoading) return <LoadingState message="Loading integrations..." />;
    if (error) return <ErrorState message="Failed to load integrations" />;

    const getStatusBadge = (status: string) => {
        switch (status) {
            case 'connected':
                return (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300">
                        <Check className="h-3 w-3" /> Connected
                    </span>
                );
            case 'disconnected':
                return (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300">
                        <X className="h-3 w-3" /> Disconnected
                    </span>
                );
            case 'degraded':
                return (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300">
                        <AlertCircle className="h-3 w-3" /> Degraded
                    </span>
                );
            default:
                return (
                    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                        {status}
                    </span>
                );
        }
    };

    const getIntegrationIcon = (type: string) => {
        switch (type) {
            case 'github':
            case 'gitlab':
                return '🔗';
            case 'datadog':
            case 'prometheus':
                return '📊';
            case 'pagerduty':
            case 'opsgenie':
                return '🔔';
            case 'jira':
            case 'servicenow':
                return '🎫';
            default:
                return '🔌';
        }
    };

    return (
        <div>
            <SectionHeader title="Integrations" description="Third-party connections and services" onBack={onBack} />

            <div className="space-y-4">
                {integrations.map((integration) => (
                    <div
                        key={integration.id}
                        className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-4"
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-4">
                                <div className="text-2xl">{getIntegrationIcon(integration.type)}</div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <h3 className="font-semibold text-gray-900 dark:text-white">
                                            {integration.name}
                                        </h3>
                                        {getStatusBadge(integration.status)}
                                    </div>
                                    <p className="text-sm text-gray-500 dark:text-gray-400 capitalize">
                                        {integration.type}
                                        {integration.lastCheckedAt && (
                                            <span className="ml-2">
                                                • Last checked: {new Date(integration.lastCheckedAt).toLocaleString()}
                                            </span>
                                        )}
                                    </p>
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={() => onTest(integration.id)}
                                    disabled={testingId === integration.id}
                                    className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-slate-700 rounded-lg hover:bg-gray-200 dark:hover:bg-slate-600 disabled:opacity-50"
                                >
                                    {testingId === integration.id ? (
                                        <Loader2 className="h-3 w-3 animate-spin" />
                                    ) : (
                                        <TestTube2 className="h-3 w-3" />
                                    )}
                                    Test
                                </button>
                                <button 
                                    onClick={() => onConfigure(integration)}
                                    className="px-3 py-1.5 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg"
                                >
                                    Configure
                                </button>
                            </div>
                        </div>
                    </div>
                ))}

                {integrations.length === 0 && (
                    <div className="text-center py-12 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700">
                        <Plug className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                            No integrations configured
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                            Connect third-party services to enhance your workflows.
                        </p>
                        <button 
                            onClick={onAddNew}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
                        >
                            <Plug className="h-4 w-4" />
                            Add Integration
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

interface AIAgentSettingsViewProps {
    settings: AIAgentSettingsResponse | undefined;
    isLoading: boolean;
    error: Error | null;
    onBack: () => void;
    onSave: (settings: Partial<AIAgentSettingsResponse>) => void;
    isSaving: boolean;
}

function AIAgentSettingsView({ settings, isLoading, error, onBack, onSave, isSaving }: AIAgentSettingsViewProps) {
    const [auditEnabled, setAuditEnabled] = useState(settings?.auditLogging?.enabled ?? true);
    const [requireApproval, setRequireApproval] = useState(
        settings?.guardrails?.requireHumanApprovalInProd ?? true
    );
    const [autoRemediationEnvs, setAutoRemediationEnvs] = useState(
        settings?.guardrails?.autoRemediationEnvironments ?? ['dev', 'staging']
    );

    if (isLoading) return <LoadingState message="Loading AI settings..." />;
    if (error) return <ErrorState message="Failed to load AI settings" />;

    const handleSave = () => {
        onSave({
            auditLogging: { enabled: auditEnabled },
            guardrails: {
                requireHumanApprovalInProd: requireApproval,
                autoRemediationEnvironments: autoRemediationEnvs,
            },
        });
    };

    const toggleEnv = (env: string) => {
        if (autoRemediationEnvs.includes(env)) {
            setAutoRemediationEnvs(autoRemediationEnvs.filter((e) => e !== env));
        } else {
            setAutoRemediationEnvs([...autoRemediationEnvs, env]);
        }
    };

    return (
        <div>
            <SectionHeader title="AI & Agents" description="Configure AI guardrails and agent behavior" onBack={onBack} />

            <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-6 space-y-6">
                {/* Audit Logging */}
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="font-medium text-gray-900 dark:text-white">AI Audit Logging</h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Log all AI agent decisions and actions
                        </p>
                    </div>
                    <button
                        onClick={() => setAuditEnabled(!auditEnabled)}
                        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                            auditEnabled ? 'bg-blue-600' : 'bg-gray-200 dark:bg-slate-700'
                        }`}
                    >
                        <span
                            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                                auditEnabled ? 'translate-x-6' : 'translate-x-1'
                            }`}
                        />
                    </button>
                </div>

                {/* Human Approval */}
                <div className="flex items-center justify-between pt-4 border-t border-gray-200 dark:border-slate-700">
                    <div>
                        <h3 className="font-medium text-gray-900 dark:text-white">
                            Require Human Approval in Prod
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            AI agents must get approval for production actions
                        </p>
                    </div>
                    <button
                        onClick={() => setRequireApproval(!requireApproval)}
                        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                            requireApproval ? 'bg-blue-600' : 'bg-gray-200 dark:bg-slate-700'
                        }`}
                    >
                        <span
                            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                                requireApproval ? 'translate-x-6' : 'translate-x-1'
                            }`}
                        />
                    </button>
                </div>

                {/* Auto-remediation environments */}
                <div className="pt-4 border-t border-gray-200 dark:border-slate-700">
                    <h3 className="font-medium text-gray-900 dark:text-white mb-2">
                        Auto-remediation Environments
                    </h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                        Environments where AI agents can auto-remediate without approval
                    </p>
                    <div className="flex flex-wrap gap-2">
                        {['dev', 'staging', 'prod'].map((env) => (
                            <button
                                key={env}
                                onClick={() => toggleEnv(env)}
                                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                                    autoRemediationEnvs.includes(env)
                                        ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                                        : 'bg-gray-100 text-gray-700 dark:bg-slate-700 dark:text-gray-300'
                                }`}
                            >
                                {env}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                    <button
                        onClick={onBack}
                        className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={isSaving}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
                    >
                        {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                        Save Changes
                    </button>
                </div>
            </div>
        </div>
    );
}

export default function SettingsPage() {
    const [currentView, setCurrentView] = useState<SettingsView>('grid');
    const [testingIntegrationId, setTestingIntegrationId] = useState<string | null>(null);

    // Integration drawer state
    const [isIntegrationDrawerOpen, setIsIntegrationDrawerOpen] = useState(false);
    const [editingIntegration, setEditingIntegration] = useState<IntegrationResponse | null>(null);
    const [integrationForm, setIntegrationForm] = useState({
        name: '',
        type: 'github' as string,
        configuration: {} as Record<string, any>,
    });

    // Fetch data using Admin API hooks
    const {
        data: platformSettings,
        isLoading: isSettingsLoading,
        error: settingsError,
    } = usePlatformSettings();

    const {
        data: integrationsData,
        isLoading: isIntegrationsLoading,
        error: integrationsError,
        refetch: refetchIntegrations,
    } = useIntegrations();

    const {
        data: aiSettings,
        isLoading: isAISettingsLoading,
        error: aiSettingsError,
    } = useAIAgentSettings();

    const updateSettingsMutation = useUpdatePlatformSettings();
    const testIntegrationMutation = useTestIntegration();
    const updateAISettingsMutation = useUpdateAIAgentSettings();
    const createIntegrationMutation = useCreateIntegration();
    const updateIntegrationMutation = useUpdateIntegration();

    const integrations = integrationsData?.data ?? [];

    const handleOpenConfigDrawer = (integration: IntegrationResponse) => {
        setEditingIntegration(integration);
        setIntegrationForm({
            name: integration.name,
            type: integration.type,
            configuration: integration.configuration || {},
        });
        setIsIntegrationDrawerOpen(true);
    };

    const handleOpenNewIntegrationDrawer = () => {
        setEditingIntegration(null);
        setIntegrationForm({
            name: '',
            type: 'github',
            configuration: {},
        });
        setIsIntegrationDrawerOpen(true);
    };

    const handleSaveIntegration = async () => {
        try {
            if (editingIntegration) {
                await updateIntegrationMutation.mutateAsync({
                    id: editingIntegration.id,
                    name: integrationForm.name,
                    configuration: integrationForm.configuration,
                });
            } else {
                await createIntegrationMutation.mutateAsync({
                    name: integrationForm.name,
                    type: integrationForm.type,
                    configuration: integrationForm.configuration,
                });
            }
            setIsIntegrationDrawerOpen(false);
            refetchIntegrations();
        } catch (error) {
            console.error('Failed to save integration:', error);
        }
    };

    const handleSaveSettings = (settings: Partial<PlatformSettingsResponse>) => {
        updateSettingsMutation.mutate(settings, {
            onSuccess: () => {
                setCurrentView('grid');
            },
        });
    };

    const handleTestIntegration = (id: string) => {
        setTestingIntegrationId(id);
        testIntegrationMutation.mutate(id, {
            onSettled: () => {
                setTestingIntegrationId(null);
            },
        });
    };

    const handleSaveAISettings = (settings: Partial<AIAgentSettingsResponse>) => {
        updateAISettingsMutation.mutate(settings, {
            onSuccess: () => {
                setCurrentView('grid');
            },
        });
    };

    // Render the current view
    if (currentView === 'general') {
        return (
            <MainLayout>
                <div className="space-y-6">
                    <GeneralSettings
                        settings={platformSettings}
                        isLoading={isSettingsLoading}
                        error={settingsError as Error | null}
                        onBack={() => setCurrentView('grid')}
                        onSave={handleSaveSettings}
                        isSaving={updateSettingsMutation.isPending}
                    />
                </div>
            </MainLayout>
        );
    }

    if (currentView === 'appearance') {
        return (
            <MainLayout>
                <div className="space-y-6">
                    <AppearanceSettings
                        settings={platformSettings}
                        isLoading={isSettingsLoading}
                        error={settingsError as Error | null}
                        onBack={() => setCurrentView('grid')}
                        onSave={handleSaveSettings}
                        isSaving={updateSettingsMutation.isPending}
                    />
                </div>
            </MainLayout>
        );
    }

    if (currentView === 'localization') {
        return (
            <MainLayout>
                <div className="space-y-6">
                    <LocalizationSettings
                        settings={platformSettings}
                        isLoading={isSettingsLoading}
                        error={settingsError as Error | null}
                        onBack={() => setCurrentView('grid')}
                        onSave={handleSaveSettings}
                        isSaving={updateSettingsMutation.isPending}
                    />
                </div>
            </MainLayout>
        );
    }

    if (currentView === 'integrations') {
        return (
            <MainLayout>
                <div className="space-y-6">
                    <IntegrationsView
                        integrations={integrations}
                        isLoading={isIntegrationsLoading}
                        error={integrationsError as Error | null}
                        onBack={() => setCurrentView('grid')}
                        onTest={handleTestIntegration}
                        testingId={testingIntegrationId}
                        onConfigure={handleOpenConfigDrawer}
                        onAddNew={handleOpenNewIntegrationDrawer}
                    />
                </div>
            </MainLayout>
        );
    }

    if (currentView === 'ai-agents') {
        return (
            <MainLayout>
                <div className="space-y-6">
                    <AIAgentSettingsView
                        settings={aiSettings}
                        isLoading={isAISettingsLoading}
                        error={aiSettingsError as Error | null}
                        onBack={() => setCurrentView('grid')}
                        onSave={handleSaveAISettings}
                        isSaving={updateAISettingsMutation.isPending}
                    />
                </div>
            </MainLayout>
        );
    }

    // Default: Settings Grid
    return (
        <MainLayout>
            <div className="space-y-6">
                {/* Header */}
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Settings</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                        Configure platform settings and integrations
                    </p>
                </div>

                {/* Settings Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {settingsSections.map((section) => {
                        const Icon = section.icon;
                        return (
                            <button
                                key={section.id}
                                onClick={() => setCurrentView(section.id)}
                                className="flex items-start gap-4 p-5 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 hover:shadow-md hover:border-gray-300 dark:hover:border-slate-600 transition-all text-left"
                            >
                                <div className="p-3 bg-gray-100 dark:bg-slate-700 rounded-xl">
                                    <Icon className="h-6 w-6 text-gray-600 dark:text-gray-400" />
                                </div>
                                <div>
                                    <h3 className="font-semibold text-gray-900 dark:text-white">{section.name}</h3>
                                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                                        {section.description}
                                    </p>
                                </div>
                            </button>
                        );
                    })}
                </div>
            </div>

            {/* Integration Configuration Drawer */}
            <Drawer
                isOpen={isIntegrationDrawerOpen}
                onClose={() => setIsIntegrationDrawerOpen(false)}
                title={editingIntegration ? 'Configure Integration' : 'Add Integration'}
                size="md"
            >
                <div className="space-y-4">
                    {!editingIntegration && (
                        <FormField label="Integration Type" name="type" required>
                            <Select
                                value={integrationForm.type}
                                onChange={(e) => setIntegrationForm({ ...integrationForm, type: e.target.value })}
                            >
                                <option value="github">GitHub</option>
                                <option value="gitlab">GitLab</option>
                                <option value="datadog">Datadog</option>
                                <option value="prometheus">Prometheus</option>
                                <option value="pagerduty">PagerDuty</option>
                                <option value="opsgenie">Opsgenie</option>
                                <option value="jira">Jira</option>
                                <option value="servicenow">ServiceNow</option>
                            </Select>
                        </FormField>
                    )}

                    <FormField label="Integration Name" name="name" required>
                        <Input
                            value={integrationForm.name}
                            onChange={(e) => setIntegrationForm({ ...integrationForm, name: e.target.value })}
                            placeholder="Production GitHub, Main Datadog..."
                        />
                    </FormField>

                    {/* GitHub/GitLab Configuration */}
                    {(integrationForm.type === 'github' || integrationForm.type === 'gitlab') && (
                        <>
                            <FormField label="Access Token" name="token" required>
                                <Input
                                    type="password"
                                    value={integrationForm.configuration.token || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, token: e.target.value }
                                    })}
                                    placeholder="ghp_xxxxxxxxxxxxx"
                                />
                            </FormField>
                            <FormField label="Organization/Group" name="org">
                                <Input
                                    value={integrationForm.configuration.organization || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, organization: e.target.value }
                                    })}
                                    placeholder="your-organization"
                                />
                            </FormField>
                        </>
                    )}

                    {/* Datadog/Prometheus Configuration */}
                    {(integrationForm.type === 'datadog' || integrationForm.type === 'prometheus') && (
                        <>
                            <FormField label="API Key" name="apiKey" required>
                                <Input
                                    type="password"
                                    value={integrationForm.configuration.apiKey || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, apiKey: e.target.value }
                                    })}
                                    placeholder="Enter API key"
                                />
                            </FormField>
                            <FormField label="API URL" name="apiUrl" required>
                                <Input
                                    value={integrationForm.configuration.apiUrl || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, apiUrl: e.target.value }
                                    })}
                                    placeholder="https://api.datadoghq.com"
                                />
                            </FormField>
                        </>
                    )}

                    {/* Jira/ServiceNow Configuration */}
                    {(integrationForm.type === 'jira' || integrationForm.type === 'servicenow') && (
                        <>
                            <FormField label="Instance URL" name="instanceUrl" required>
                                <Input
                                    value={integrationForm.configuration.instanceUrl || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, instanceUrl: e.target.value }
                                    })}
                                    placeholder="https://your-instance.atlassian.net"
                                />
                            </FormField>
                            <FormField label="Username" name="username" required>
                                <Input
                                    value={integrationForm.configuration.username || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, username: e.target.value }
                                    })}
                                    placeholder="user@example.com"
                                />
                            </FormField>
                            <FormField label="API Token" name="apiToken" required>
                                <Input
                                    type="password"
                                    value={integrationForm.configuration.apiToken || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, apiToken: e.target.value }
                                    })}
                                    placeholder="Enter API token"
                                />
                            </FormField>
                        </>
                    )}

                    {/* PagerDuty/Opsgenie Configuration */}
                    {(integrationForm.type === 'pagerduty' || integrationForm.type === 'opsgenie') && (
                        <>
                            <FormField label="API Key" name="apiKey" required>
                                <Input
                                    type="password"
                                    value={integrationForm.configuration.apiKey || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, apiKey: e.target.value }
                                    })}
                                    placeholder="Enter API key"
                                />
                            </FormField>
                            <FormField label="Service ID" name="serviceId">
                                <Input
                                    value={integrationForm.configuration.serviceId || ''}
                                    onChange={(e) => setIntegrationForm({
                                        ...integrationForm,
                                        configuration: { ...integrationForm.configuration, serviceId: e.target.value }
                                    })}
                                    placeholder="Service or Team ID"
                                />
                            </FormField>
                        </>
                    )}

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsIntegrationDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSaveIntegration}
                            disabled={!integrationForm.name || (createIntegrationMutation.isPending || updateIntegrationMutation.isPending)}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {(createIntegrationMutation.isPending || updateIntegrationMutation.isPending) 
                                ? 'Saving...' 
                                : editingIntegration ? 'Update Integration' : 'Add Integration'}
                        </button>
                    </div>
                </div>
            </Drawer>
        </MainLayout>
    );
}
