import { useAtom, useSetAtom } from 'jotai';
import { useNavigate, useLocation, useParams } from 'react-router';
import { useEffect, useState, useMemo } from 'react';
import {
    PersonaHero,
    QuickActionsGrid,
    PersonaMetricsGrid,
    RecentActivitiesTimeline,
    PinnedFeaturesGrid,
    PersonaDashboardSkeleton,
    HeroSection,
    StatsGrid,
    FeatureGrid,
    CallToActionSection,
    InfoBanner,
    MyStoriesCard,
    PersonaFlowStrip,
    PersonaWorkspaceGrid,
    WorkspaceOnboardingBanner,
    WorkspaceTipCard,
} from '@/shared/components';
import type { PersonaId, DevSecOpsPhaseId } from '@/shared/types/org';
import {
    userProfileAtom,
    personaConfigAtom,
    personaOverrideAtom,
} from '@/state/jotai/atoms';
import { useToast } from '@/lib/toast';
import { usePersonaKeyboardShortcuts } from '@/lib/keyboardShortcuts';
import {
    usePendingTasks,
    useRecentActivities,
    useMetrics,
    usePinnedFeatures,
} from '@/hooks';
import { useAllWorkItems } from '@/hooks/useMyWorkItems';
import { usePersonaComposition } from '@/hooks/usePersonaComposition';
import { DashboardGrid, useLayoutPersistence } from '@/components/DashboardGrid';
import { PluginSlot } from '@/components/PluginSlot';
import { pluginRegistry } from '@/lib/persona/PluginRegistry';
import { customMetricWidgetManifest } from '@/plugins/CustomMetricWidget';
import type { WidgetConfig } from '@/schemas/persona.schema';

/**
 * HomePage - Persona-driven landing page with generic fallback
 *
 * <p><b>Purpose</b><br>
 * Dynamic landing page that adapts to user role (admin, lead, engineer, viewer)
 * with persona-specific quick actions, metrics, and workflows. Falls back to
 * generic landing page for unauthenticated users.
 *
 * <p><b>Features (Enhanced v2.0)</b><br>
 * - Multi-role persona composition (supports multiple roles simultaneously)
 * - Plugin-based extensibility
 * - Role-specific quick actions with badge counts
 * - Real-time metrics with threshold indicators
 * - Recent activity timeline
 * - Pinned features for quick access
 * - Generic landing page fallback (when not authenticated)
 * - Fully responsive design
 * - Dark mode support
 *
 * <p><b>Persona Types</b><br>
 * - Admin: Security, compliance, user management
 * - Lead: Team KPIs, approvals, workflow oversight
 * - Engineer: Workflow creation, testing, deployments
 * - Viewer: Dashboards, reports, read-only access
 *
 * <p><b>Component Hierarchy (Authenticated)</b><br>
 * HomePage (PersonaDashboard)
 * ├── PersonaHero (greeting, role badge, pending tasks)
 * ├── QuickActionsGrid (4-6 persona-specific actions)
 * ├── PersonaMetricsGrid (3-4 key metrics)
 * ├── RecentActivitiesTimeline (activity history)
 * └── PinnedFeaturesGrid (user's pinned features)
 *
 * <p><b>Component Hierarchy (Unauthenticated)</b><br>
 * HomePage (GenericLanding)
 * ├── HeroSection (hero title + subtitle)
 * ├── StatsGrid (key metrics)
 * ├── FeatureGrid (primary features)
 * ├── CallToActionSection (primary CTA)
 * ├── FeatureGrid (secondary features)
 * └── InfoBanner (navigation tips)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import HomePage from '@/pages/HomePage';
 * <Route path="/" element={<HomePage />} />
 * }</pre>
 *
 * @doc.type page
 * @doc.purpose Adaptive landing page with persona-driven content and multi-role composition
 * @doc.layer product
 * @doc.pattern Adaptive Composition (Persona-Driven v2.0)
 */

export default function HomePage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { personaId: routePersonaId } = useParams<{ personaId?: string }>();
    const [userProfile] = useAtom(userProfileAtom);
    const [personaConfig] = useAtom(personaConfigAtom);
    const setPersonaOverride = useSetAtom(personaOverrideAtom);
    const { showSuccess, showError } = useToast();

    // Handle persona override from /workspace/:personaId route
    useEffect(() => {
        if (routePersonaId) {
            const validPersonas = ['engineer', 'lead', 'sre', 'security', 'admin', 'viewer'];
            if (validPersonas.includes(routePersonaId)) {
                setPersonaOverride(routePersonaId as 'engineer' | 'lead' | 'admin' | 'viewer');
            }
        } else {
            // Clear override when not on a workspace route
            setPersonaOverride(null);
        }
        // Cleanup on unmount
        return () => {
            setPersonaOverride(null);
        };
    }, [routePersonaId, setPersonaOverride]);

    // NEW: Use composition hooks for multi-role support
    const { merged: composedConfig, roles: activeRoles, hasPermission } = usePersonaComposition();

    // Dashboard customization (Phase 2)
    const [isEditMode, setIsEditMode] = useState(false);
    const [savedLayouts, saveLayout, clearLayout] = useLayoutPersistence(
        `dashboard-layout-${userProfile?.role ?? 'default'}`
    );
    const [showPersonaOnboarding, setShowPersonaOnboarding] = useState(() => {
        if (typeof window === 'undefined') {
            return true;
        }
        const stored = window.localStorage.getItem('softwareOrg.personaDashboard.onboarding.dismissed');
        return stored !== 'true';
    });

    // Register example plugin on mount
    useEffect(() => {
        if (!pluginRegistry.get('custom-metric-widget')) {
            pluginRegistry.register(
                customMetricWidgetManifest,
                () => import('@/plugins/CustomMetricWidget')
            );
        }
    }, []);

    // Convert metrics to widgets for DashboardGrid
    const widgets = useMemo<WidgetConfig[]>(() => {
        // Use composed widgets if available, otherwise convert metrics to widgets
        if (composedConfig?.widgets && composedConfig.widgets.length > 0) {
            return composedConfig.widgets;
        }

        // Fallback: Convert metrics to widget format
        const metrics = composedConfig?.metrics ?? personaConfig?.metrics ?? [];
        return metrics.map((metric, index) => ({
            id: metric.id,
            title: metric.title,
            type: 'metric' as const,
            slot: 'dashboard.metrics',
            pluginId: 'custom-metric-widget',
            config: {
                metricKey: metric.dataKey,
                title: metric.title,
                format: metric.format === 'percentage' ? 'percentage' : 'number',
                icon: metric.icon,
                color: metric.color,
                threshold: metric.threshold,
            },
            layout: {
                lg: { x: (index % 3) * 4, y: Math.floor(index / 3) * 4, w: 4, h: 4 },
            },
            enabled: true,
            permissions: [],
        }));
    }, [composedConfig, personaConfig]);

    // Enable keyboard shortcuts
    usePersonaKeyboardShortcuts();

    // React Query hooks for real-time data
    const { tasks: pendingTasks, isLoading: tasksLoading, error: tasksError } = usePendingTasks();
    const { activities: recentActivities, isLoading: activitiesLoading, error: activitiesError } = useRecentActivities({ maxItems: 5 });
    const { metrics: metricData, isLoading: metricsLoading, error: metricsError } = useMetrics();
    const { features: pinnedFeatures, unpin, isMutating: unpinning } = usePinnedFeatures();
    const { workItems: devSecOpsWorkItems, isLoading: devSecOpsLoading } = useAllWorkItems();

    // Handle unpin with toast notification
    const handleUnpin = (featureTitle: string) => {
        if (unpinning) return;

        unpin(featureTitle);
        showSuccess(`"${featureTitle}" unpinned successfully`);
    };

    // Show error toasts for failed data fetches
    useEffect(() => {
        if (tasksError) {
            showError('Failed to load pending tasks. Retrying automatically...', 4000);
        }
    }, [tasksError, showError]);

    useEffect(() => {
        if (metricsError) {
            showError('Failed to load metrics. Retrying automatically...', 4000);
        }
    }, [metricsError, showError]);

    useEffect(() => {
        if (activitiesError) {
            showError('Failed to load activities. Retrying automatically...', 4000);
        }
    }, [activitiesError, showError]);

    useEffect(() => {
        // Debug: log personaConfig to verify it updates when persona changes (dev only)
        if (import.meta.env.DEV) {
            console.debug('[HomePage] Persona config changed:', personaConfig?.role, personaConfig?.displayName);
        }
    }, [personaConfig]);

    useEffect(() => {
        if (!userProfile) return;
        if (location.hash === '#my-stories') {
            const element = document.getElementById('my-stories');
            if (element) {
                element.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }
    }, [location.hash, userProfile]);

    // If user is authenticated, show persona dashboard
    if (userProfile) {
        // Show skeleton loader on first load
        const isInitialLoad = tasksLoading || activitiesLoading || metricsLoading;
        if (isInitialLoad) {
            return <PersonaDashboardSkeleton />;
        }

        // Use composed config if available (v2.0), fallback to legacy config (v1.0)
        const effectiveQuickActions = composedConfig?.quickActions ?? personaConfig?.quickActions ?? [];
        const effectiveMetrics = composedConfig?.metrics ?? personaConfig?.metrics ?? [];

        const totalDevSecOpsItems = devSecOpsWorkItems.length;
        const blockedDevSecOpsItems = devSecOpsWorkItems.filter(item => item.status === 'blocked').length;
        const inReviewDevSecOpsItems = devSecOpsWorkItems.filter(item => item.status === 'in-review').length;
        const completedDevSecOpsItems = devSecOpsWorkItems.filter(item =>
            item.status === 'done' || item.status === 'deployed',
        ).length;
        const devSecOpsCompletionRate = totalDevSecOpsItems === 0
            ? 0
            : Math.round((completedDevSecOpsItems / totalDevSecOpsItems) * 100);

        const primaryRole = activeRoles[0] ?? userProfile.role;
        let devSecOpsPersonaFilter: 'all' | 'engineer' | 'lead' | 'sre' | 'security' = 'all';
        if (primaryRole === 'engineer') {
            devSecOpsPersonaFilter = 'engineer';
        } else if (primaryRole === 'lead') {
            devSecOpsPersonaFilter = 'lead';
        } else if (primaryRole === 'admin') {
            devSecOpsPersonaFilter = 'security';
        }

        let devSecOpsStatusFilter: 'blocked' | 'in-review' | undefined;
        if (blockedDevSecOpsItems > 0) {
            devSecOpsStatusFilter = 'blocked';
        } else if (inReviewDevSecOpsItems > 0) {
            devSecOpsStatusFilter = 'in-review';
        }

        const devSecOpsParams = new URLSearchParams();
        if (devSecOpsPersonaFilter !== 'all') {
            devSecOpsParams.set('persona', devSecOpsPersonaFilter);
        }
        if (devSecOpsStatusFilter) {
            devSecOpsParams.set('status', devSecOpsStatusFilter);
        }

        const devSecOpsBoardHref = devSecOpsParams.toString()
            ? `/devsecops/board?${devSecOpsParams.toString()}`
            : '/devsecops/board';

        return (
            <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-6">

                <div className="max-w-7xl mx-auto">
                    {/* Header with Edit Toggle */}
                    <div className="flex justify-between items-center mb-6">
                        <div className="flex-1" />
                        <button
                            onClick={() => setIsEditMode(!isEditMode)}
                            className={`px-4 py-2 rounded-lg font-medium transition-colors ${isEditMode
                                ? 'bg-blue-600 text-white hover:bg-blue-700'
                                : 'bg-white dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700 border border-slate-200 dark:border-neutral-600'
                                }`}
                        >
                            {isEditMode ? '✓ Save Layout' : '✏️ Customize'}
                        </button>
                    </div>

                    {/* Persona Hero - Enhanced with multi-role support */}
                    <PersonaHero
                        user={userProfile}
                        pendingTasks={pendingTasks}
                        // Pass active roles for multi-role badge display (future enhancement)
                        {...(activeRoles.length > 0 && { activeRoles })}
                    />

                    {/* Workspace-specific onboarding banner (when on /workspace/:personaId route) */}
                    {routePersonaId && (
                        <WorkspaceOnboardingBanner
                            personaId={routePersonaId as PersonaId}
                            className="mt-6 mb-6"
                        />
                    )}

                    {/* Generic onboarding (when not on a specific workspace route) */}
                    {!routePersonaId && showPersonaOnboarding && (
                        <div className="mt-6 mb-6 rounded-lg border border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 px-4 py-3 text-xs text-slate-700 dark:text-neutral-300 flex flex-col md:flex-row md:items-start md:justify-between gap-3">
                            <div className="space-y-1">
                                <div className="font-semibold text-slate-900 dark:text-neutral-100 text-sm">
                                    Welcome to your Software Org workspace
                                </div>
                                <div>
                                    Use quick actions to jump into common tasks, the DevSecOps summary to see delivery health, and the widgets below to monitor KPIs for your persona.
                                </div>
                                <div>
                                    You can click DevSecOps phases on story pages to move through the engineer flow, or open the DevSecOps board from the summary card to see all work by phase.
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={() => {
                                    setShowPersonaOnboarding(false);
                                    if (typeof window !== 'undefined') {
                                        window.localStorage.setItem('softwareOrg.personaDashboard.onboarding.dismissed', 'true');
                                    }
                                }}
                                className="self-start md:self-center px-3 py-1.5 rounded-md text-xs font-medium bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700"
                            >
                                Hide help
                            </button>
                        </div>
                    )}

                    {/* Quick Actions - Now using composed config with permission filtering */}
                    <QuickActionsGrid
                        actions={effectiveQuickActions}
                        pendingTasks={pendingTasks}
                        columns={3}
                        onActionClick={(action) => {
                            // Check permissions before navigation (enhanced security)
                            if (action.permissions && action.permissions.length > 0) {
                                const canAccess = action.permissions.some(p => hasPermission(p));
                                if (!canAccess) {
                                    showError('You do not have permission to access this feature');
                                    return;
                                }
                            }
                            navigate(action.href);
                        }}
                    />

                    {/* Engineer Flow: My Stories Card */}
                    {(personaConfig?.role === 'engineer' || userProfile?.role === 'engineer') && (
                        <div className="mb-8" id="my-stories">
                            <MyStoriesCard
                                maxItems={5}
                                onItemClick={(id) => {
                                    console.log('[HomePage] MyStoriesCard item clicked:', id);
                                    if (id === 'all') {
                                        // Future: navigate to full work items list
                                        navigate('/work-items');
                                    } else {
                                        navigate(`/work-items/${id}`);
                                    }
                                }}
                            />
                        </div>
                    )}

                    {/* DevSecOps Summary Widget */}
                    <div className="mb-8">
                        <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-3">
                            <div>
                                <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                    DevSecOps summary
                                </h2>
                                <p className="text-xs text-slate-600 dark:text-neutral-400">
                                    Snapshot of work items across the DevSecOps lifecycle.
                                </p>
                            </div>
                            <button
                                onClick={() => navigate(devSecOpsBoardHref)}
                                className="mt-3 md:mt-0 px-4 py-2 rounded-lg text-sm bg-blue-600 text-white hover:bg-blue-700 transition"
                            >
                                Open DevSecOps Board
                            </button>
                        </div>

                        {/* Persona Flow Strip - Visual phase navigation */}
                        <div className="mb-4">
                            <PersonaFlowStrip
                                personaId={(primaryRole as PersonaId) || 'engineer'}
                                currentPhaseId={null}
                                onPhaseClick={(phaseId: DevSecOpsPhaseId) => {
                                    navigate(`/devsecops/board?persona=${primaryRole}&phase=${phaseId}`);
                                }}
                                size="md"
                            />
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            <div className="rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 px-4 py-3 flex flex-col gap-1">
                                <div className="text-[11px] uppercase tracking-wide text-slate-500 dark:text-neutral-400">
                                    Total items
                                </div>
                                <div className="flex items-baseline gap-2">
                                    <span className="text-xl font-semibold text-slate-900 dark:text-neutral-100">
                                        {devSecOpsLoading ? '–' : totalDevSecOpsItems}
                                    </span>
                                </div>
                            </div>
                            <div className="rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 px-4 py-3 flex flex-col gap-1">
                                <div className="text-[11px] uppercase tracking-wide text-slate-500 dark:text-neutral-400">
                                    Completion rate
                                </div>
                                <div className="flex items-baseline gap-2">
                                    <span className="text-xl font-semibold text-emerald-600 dark:text-green-400">
                                        {devSecOpsLoading ? '–' : `${devSecOpsCompletionRate}%`}
                                    </span>
                                    <span className="text-[11px] text-slate-500 dark:text-neutral-400">
                                        {!devSecOpsLoading && `${completedDevSecOpsItems} completed`}
                                    </span>
                                </div>
                            </div>
                            <div className="rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 px-4 py-3 flex flex-col gap-1">
                                <div className="text-[11px] uppercase tracking-wide text-slate-500 dark:text-neutral-400">
                                    Blocked
                                </div>
                                <div className="flex items-baseline gap-2">
                                    <span className="text-xl font-semibold text-amber-600 dark:text-amber-400">
                                        {devSecOpsLoading ? '–' : blockedDevSecOpsItems}
                                    </span>
                                </div>
                            </div>
                            <div className="rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 px-4 py-3 flex flex-col gap-1">
                                <div className="text-[11px] uppercase tracking-wide text-slate-500 dark:text-neutral-400">
                                    In review
                                </div>
                                <div className="flex items-baseline gap-2">
                                    <span className="text-xl font-semibold text-purple-600 dark:text-violet-400">
                                        {devSecOpsLoading ? '–' : inReviewDevSecOpsItems}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Dashboard Grid - Customizable widget layout (Phase 2) */}
                    {widgets.length > 0 ? (
                        <div className="mb-8">
                            <DashboardGrid
                                widgets={widgets}
                                editable={isEditMode}
                                savedLayouts={savedLayouts}
                                onLayoutChange={(layout, updatedWidgets) => {
                                    saveLayout(layout, updatedWidgets);
                                    if (isEditMode) {
                                        showSuccess('Layout saved!');
                                    }
                                }}
                                rowHeight={80}
                                className="mb-8"
                            />
                        </div>
                    ) : (
                        /* Fallback to static metrics grid if no widgets */
                        <PersonaMetricsGrid metrics={effectiveMetrics} data={metricData} />
                    )}

                    {/* Recent Activity */}
                    <RecentActivitiesTimeline
                        activities={recentActivities}
                        maxItems={5}
                        onActivityClick={(activity) => {
                            if (activity.href) navigate(activity.href);
                        }}
                    />

                    {/* Pinned Features */}
                    <PinnedFeaturesGrid
                        features={pinnedFeatures}
                        onFeatureClick={(feature) => navigate(feature.href)}
                        onUnpin={handleUnpin}
                    />

                    {/* Other Persona Workspaces - for multi-role users */}
                    {activeRoles.length > 1 && (
                        <div className="mb-8">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                Other Workspaces
                            </h2>
                            <p className="text-xs text-slate-600 dark:text-neutral-400 mb-4">
                                Quick access to your other persona workspaces based on your active roles.
                            </p>
                            <PersonaWorkspaceGrid
                                personas={activeRoles.filter(r => r !== primaryRole) as PersonaId[]}
                                columns={2}
                                compact
                            />
                        </div>
                    )}

                    {/* Workspace Tip Card (when on /workspace/:personaId route) */}
                    {routePersonaId && (
                        <WorkspaceTipCard
                            personaId={routePersonaId as PersonaId}
                            className="mb-8"
                        />
                    )}

                    {/* Plugin Slots for extensibility (Phase 2) */}
                    <div className="mb-8">
                        <PluginSlot
                            slot="dashboard.footer"
                            userPermissions={composedConfig?.permissions}
                            context={{ userId: userProfile?.id, role: userProfile?.role }}
                        />
                    </div>

                    {/* Footer */}
                    <div className="mt-16 text-center text-sm text-slate-500 dark:text-slate-500 border-t border-slate-200 dark:border-slate-800 pt-8">
                        <p>
                            Software-Org Platform © 2025. An AI-first DevSecOps control center for modern software
                            organizations.
                        </p>
                        {/* Show active roles for debugging (dev only) */}
                        {import.meta.env.DEV && activeRoles.length > 0 && (
                            <p className="mt-2 text-xs text-slate-400">
                                Active roles: {activeRoles.join(', ')} | Permissions: {composedConfig?.permissions.length ?? 0} |
                                Widgets: {widgets.length} | Edit mode: {isEditMode ? 'ON' : 'OFF'}
                            </p>
                        )}
                    </div>
                </div>
            </div>
        );
    }

    // Generic landing page for unauthenticated users
    return (
        <>
            <GenericLandingPage />
        </>
    );
}

/**
 * GenericLandingPage - Static landing page for unauthenticated users
 *
 * Shows platform overview with feature discovery and clear CTAs.
 */
export function GenericLandingPage() {
    // Primary features (8) - always visible in main grid
    const primaryFeatures: Feature[] = [
        {
            icon: '📊',
            title: 'Control Tower',
            description: 'Real-time KPI metrics, AI insights, and event timeline for organization-wide visibility',
            href: '/dashboard',
            color: 'bg-blue-50 dark:bg-blue-950 border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900',
        },
        {
            icon: '🏢',
            title: 'Organization',
            description: 'Manage departments, teams, structure, and automation status across your organization',
            href: '/departments',
            color: 'bg-purple-50 dark:bg-purple-950 border-purple-200 dark:border-purple-800 hover:bg-purple-100 dark:hover:bg-purple-900',
        },
        {
            icon: '🔄',
            title: 'Workflows',
            description: 'Create, manage, and execute automation workflows with visual orchestration',
            href: '/workflows',
            color: 'bg-amber-50 dark:bg-amber-950 border-amber-200 dark:border-amber-800 hover:bg-amber-100 dark:hover:bg-amber-900',
        },
        {
            icon: '✋',
            title: 'HITL Console',
            description: 'Human-in-the-loop decision management with approval workflows for critical actions',
            href: '/hitl',
            color: 'bg-cyan-50 dark:bg-cyan-950 border-cyan-200 dark:border-cyan-800 hover:bg-cyan-100 dark:hover:bg-cyan-900',
        },
        {
            icon: '⚡',
            title: 'Event Simulator',
            description: 'Test scenarios and simulate event patterns to validate pipelines and workflows',
            href: '/simulator',
            color: 'bg-indigo-50 dark:bg-indigo-950 border-indigo-200 dark:border-indigo-800 hover:bg-indigo-100 dark:hover:bg-indigo-900',
        },
        {
            icon: '📈',
            title: 'Reports',
            description: 'Analytics dashboards, audit trails, and performance metrics for compliance',
            href: '/reports',
            color: 'bg-green-50 dark:bg-green-950 border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900',
        },
        {
            icon: '🤖',
            title: 'AI Intelligence',
            description: 'AI-driven insights, pattern detection, and intelligent recommendations for operations',
            href: '/ai',
            color: 'bg-pink-50 dark:bg-pink-950 border-pink-200 dark:border-pink-800 hover:bg-pink-100 dark:hover:bg-pink-900',
        },
        {
            icon: '🔒',
            title: 'Security',
            description: 'Security posture management, compliance tracking, and vulnerability monitoring',
            href: '/security',
            color: 'bg-red-50 dark:bg-red-950 border-red-200 dark:border-red-800 hover:bg-red-100 dark:hover:bg-red-900',
        },
    ];

    // Secondary features (8) - additional capabilities
    const secondaryFeatures: Feature[] = [
        {
            icon: '⏱️',
            title: 'Real-Time Monitor',
            description: 'Live system metrics, anomaly detection, and real-time alerting for operational health',
            href: '/realtime-monitor',
            color: 'bg-orange-50 dark:bg-orange-950 border-orange-200 dark:border-orange-800 hover:bg-orange-100 dark:hover:bg-orange-900',
            badge: 'Live',
        },
        {
            icon: '⚙️',
            title: 'Automation Engine',
            description: 'Workflow execution engine with scheduling, monitoring, and execution history',
            href: '/automation',
            color: 'bg-teal-50 dark:bg-teal-950 border-teal-200 dark:border-teal-800 hover:bg-teal-100 dark:hover:bg-teal-900',
        },
        {
            icon: '🧠',
            title: 'ML Observatory',
            description: 'Model performance tracking, training metrics, and model lifecycle management',
            href: '/ml-observatory',
            color: 'bg-violet-50 dark:bg-violet-950 border-violet-200 dark:border-violet-800 hover:bg-violet-100 dark:hover:bg-violet-900',
            badge: 'ML',
        },
        {
            icon: '🎓',
            title: 'Model Catalog',
            description: 'Browse, version, and deploy ML models with performance comparisons and metadata',
            href: '/models',
            color: 'bg-blue-50 dark:bg-blue-950 border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900',
        },
        {
            icon: '⚙️',
            title: 'Settings',
            description: 'Configuration, preferences, integrations, and user settings management',
            href: '/settings',
            color: 'bg-slate-50 dark:bg-slate-900 border-slate-200 dark:border-neutral-600 hover:bg-slate-100 dark:hover:bg-slate-800',
        },
        {
            icon: '❓',
            title: 'Help Center',
            description: 'Documentation, tutorials, FAQs, and support resources for all features',
            href: '/help',
            color: 'bg-yellow-50 dark:bg-yellow-950 border-yellow-200 dark:border-yellow-800 hover:bg-yellow-100 dark:hover:bg-yellow-900',
        },
        {
            icon: '📥',
            title: 'Data Export',
            description: 'Export reports, metrics, and data in multiple formats for analysis and sharing',
            href: '/export',
            color: 'bg-emerald-50 dark:bg-emerald-950 border-emerald-200 dark:border-emerald-800 hover:bg-emerald-100 dark:hover:bg-emerald-900',
        },
        {
            icon: '📋',
            title: 'Audit Trail',
            description: 'Comprehensive audit logging and compliance records for all system activities',
            href: '/audit',
            color: 'bg-rose-50 dark:bg-rose-950 border-rose-200 dark:border-rose-800 hover:bg-rose-100 dark:hover:bg-rose-900',
            badge: 'Compliance',
        },
    ];

    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-7xl mx-auto">
                {/* Hero Section */}
                <HeroSection
                    title="Software Organization Platform"
                    subtitle="AI-First DevSecOps Control Center"
                    description="Unified platform for orchestrating software delivery, managing compliance, and leveraging AI for operational excellence across your entire organization."
                />

                {/* Key Statistics */}
                <StatsGrid
                    stats={[
                        {
                            icon: '16',
                            label: 'Feature Areas',
                            color: 'text-blue-600 dark:text-indigo-400',
                        },
                        {
                            icon: '⚡',
                            label: 'Real-time Data Updates',
                            color: 'text-green-600 dark:text-green-400',
                        },
                        {
                            icon: '🤖',
                            label: 'AI-Driven Insights',
                            color: 'text-purple-600 dark:text-violet-400',
                        },
                    ]}
                />

                {/* Primary Features Section */}
                <div className="mb-16">
                    <div className="text-center mb-8">
                        <h2 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                            Core Features
                        </h2>
                        <p className="text-lg text-slate-600 dark:text-neutral-400">
                            Eight primary feature areas for comprehensive software organization management
                        </p>
                    </div>
                    <FeatureGrid features={primaryFeatures} columns={3} />
                </div>

                {/* Main Call-to-Action */}
                <CallToActionSection
                    title="Get Started Now"
                    description="Begin your journey by exploring the Control Tower dashboard to see real-time KPIs, AI insights, and event timelines. Or jump directly to any feature area that interests you."
                    primaryAction={{
                        label: 'View Control Tower',
                        href: '/dashboard',
                        icon: '📊',
                    }}
                    secondaryAction={{
                        label: 'Explore Features',
                        href: '/help',
                        icon: '📚',
                    }}
                />

                {/* Secondary Features Section */}
                <div className="mt-16">
                    <div className="text-center mb-8">
                        <h2 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                            Advanced Capabilities
                        </h2>
                        <p className="text-lg text-slate-600 dark:text-neutral-400">
                            Eight additional features for deeper insights and specialized operations
                        </p>
                    </div>
                    <FeatureGrid features={secondaryFeatures} columns={3} />
                </div>

                {/* Information Banners */}
                <div className="mt-16 space-y-4">
                    <InfoBanner icon="ℹ️" tone="info">
                        <strong>Navigation Tip:</strong> Use the sidebar navigation to switch between feature areas anytime. Each section provides specialized tools for managing your software organization.
                    </InfoBanner>
                    <InfoBanner icon="💡" tone="success">
                        <strong>First-Time User?</strong> Start with the Control Tower dashboard to get an overview of your organization's metrics and AI insights, then explore specific features based on your needs.
                    </InfoBanner>
                    <InfoBanner icon="🔗" tone="info">
                        <strong>Need Help?</strong> Visit the Help Center to find documentation, tutorials, and answers to common questions about any feature.
                    </InfoBanner>
                </div>

                {/* Footer */}
                <div className="mt-16 text-center text-sm text-slate-500 dark:text-slate-500 border-t border-slate-200 dark:border-slate-800 pt-8">
                    <p>
                        Software-Org Platform © 2025. An AI-first DevSecOps control center for modern software organizations.
                    </p>
                </div>
            </div>
        </div>
    );
}
