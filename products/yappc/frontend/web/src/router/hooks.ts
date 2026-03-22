/**
 * Navigation Hooks
 *
 * @description Custom navigation hooks for type-safe routing,
 * route guards, and navigation state management.
 */

import { useCallback, useMemo } from 'react';
import { useNavigate, useLocation, useParams, useSearchParams } from 'react-router';
import { useAtomValue, useSetAtom } from 'jotai';

import {
  activeProjectAtom,
  navigationHistoryAtom,
  breadcrumbsAtom,
} from '../state/atoms';
import { ROUTES, generateBreadcrumbs, buildUrl, getPhaseFromPath } from './paths';

// =============================================================================
// Core Navigation Hook
// =============================================================================

/**
 * Type-safe project navigation hook
 */
export function useProjectNavigation() {
  const navigate = useNavigate();
  const activeProject = useAtomValue(activeProjectAtom);
  const projectId = activeProject?.id || '';

  return useMemo(
    () => ({
      // Bootstrapping
      toBootstrap: () => navigate(ROUTES.bootstrap.root(projectId)),
      toBootstrapSession: (sessionId: string) =>
        navigate(ROUTES.bootstrap.session(projectId, sessionId)),
      toBootstrapPreview: () => navigate(ROUTES.bootstrap.preview(projectId)),

      // Setup
      toSetup: () => navigate(ROUTES.setup.root(projectId)),
      toSetupInfrastructure: () => navigate(ROUTES.setup.infrastructure(projectId)),
      toSetupEnvironments: () => navigate(ROUTES.setup.environments(projectId)),
      toSetupTeam: () => navigate(ROUTES.setup.team(projectId)),
      toSetupProgress: () => navigate(ROUTES.setup.progress(projectId)),

      // Development
      toDevelopment: () => navigate(ROUTES.development.root(projectId)),
      toSprintBoard: (sprintId?: string) =>
        navigate(ROUTES.development.board(projectId, sprintId)),
      toBacklog: () => navigate(ROUTES.development.backlog(projectId)),
      toStory: (storyId: string) => navigate(ROUTES.development.story(projectId, storyId)),
      toEpics: () => navigate(ROUTES.development.epics(projectId)),
      toPullRequests: (prId?: string) => navigate(ROUTES.development.prs(projectId, prId)),
      toCodeReview: (prId: string) => navigate(ROUTES.development.review(projectId, prId)),
      toFeatureFlags: () => navigate(ROUTES.development.flags(projectId)),
      toDeployments: () => navigate(ROUTES.development.deployments(projectId)),
      toVelocity: () => navigate(ROUTES.development.velocity(projectId)),

      // Operations
      toOperations: () => navigate(ROUTES.operations.root(projectId)),
      toIncidents: (incidentId?: string) =>
        navigate(ROUTES.operations.incidents(projectId, incidentId)),
      toWarRoom: (incidentId: string) =>
        navigate(ROUTES.operations.warroom(projectId, incidentId)),
      toAlerts: () => navigate(ROUTES.operations.alerts(projectId)),
      toDashboards: (dashboardId?: string) =>
        navigate(ROUTES.operations.dashboards(projectId, dashboardId)),
      toLogExplorer: () => navigate(ROUTES.operations.logs(projectId)),
      toMetrics: () => navigate(ROUTES.operations.metrics(projectId)),
      toRunbooks: (runbookId?: string) =>
        navigate(ROUTES.operations.runbooks(projectId, runbookId)),
      toOnCall: () => navigate(ROUTES.operations.oncall(projectId)),
      toServiceMap: () => navigate(ROUTES.operations.services(projectId)),
      toPostmortems: () => navigate(ROUTES.operations.postmortems(projectId)),

      // Collaboration
      toTeamHub: () => navigate(ROUTES.team.root(projectId)),
      toCalendar: () => navigate(ROUTES.team.calendar(projectId)),
      toKnowledgeBase: () => navigate(ROUTES.team.knowledge(projectId)),
      toArticle: (articleId: string) => navigate(ROUTES.team.article(projectId, articleId)),
      toArticleEdit: (articleId: string) =>
        navigate(ROUTES.team.articleEdit(projectId, articleId)),
      toNewArticle: () => navigate(ROUTES.team.articleNew(projectId)),
      toStandups: () => navigate(ROUTES.team.standups(projectId)),
      toRetros: () => navigate(ROUTES.team.retros(projectId)),
      toMessages: () => navigate(ROUTES.team.messages(projectId)),
      toChannel: (channelId: string) => navigate(ROUTES.team.channel(projectId, channelId)),
      toDM: (userId: string) => navigate(ROUTES.team.dm(projectId, userId)),
      toGoals: () => navigate(ROUTES.team.goals(projectId)),
      toActivity: () => navigate(ROUTES.team.activity(projectId)),

      // Security
      toSecurity: () => navigate(ROUTES.security.root(projectId)),
      toVulnerabilities: (vulnId?: string) =>
        navigate(ROUTES.security.vulnerabilities(projectId, vulnId)),
      toSecurityScans: (scanId?: string) =>
        navigate(ROUTES.security.scans(projectId, scanId)),
      toCompliance: (frameworkId?: string) =>
        navigate(ROUTES.security.compliance(projectId, frameworkId)),
      toSecrets: () => navigate(ROUTES.security.secrets(projectId)),
      toPolicies: (policyId?: string) =>
        navigate(ROUTES.security.policies(projectId, policyId)),
      toSecurityAlerts: () => navigate(ROUTES.security.alerts(projectId)),
      toAuditLogs: () => navigate(ROUTES.security.audit(projectId)),
      toThreatModel: () => navigate(ROUTES.security.threatModel(projectId)),
    }),
    [navigate, projectId]
  );
}

// =============================================================================
// App Navigation Hook
// =============================================================================

/**
 * App-level navigation hook
 */
export function useAppNavigation() {
  const navigate = useNavigate();

  return useMemo(
    () => ({
      toHome: () => navigate(ROUTES.HOME),
      toSSO: () => navigate(ROUTES.SSO_CALLBACK),
      toDashboard: () => navigate(ROUTES.DASHBOARD),
      toProjects: () => navigate(ROUTES.PROJECTS),
      toTemplates: () => navigate(ROUTES.TEMPLATES),
      toSettings: () => navigate(ROUTES.SETTINGS),
      toProfile: () => navigate(ROUTES.PROFILE),
      toProject: (projectId: string) => navigate(ROUTES.project(projectId)),
      toAdmin: () => navigate(ROUTES.admin.root),
      toAdminTeams: () => navigate(ROUTES.admin.teams),
      toAdminBilling: () => navigate(ROUTES.admin.billing),
    }),
    [navigate]
  );
}

// =============================================================================
// Route State Hook
// =============================================================================

/**
 * Get current route state with params and query
 */
export function useRouteState<
  TParams extends Record<string, string> = Record<string, string>
>() {
  const location = useLocation();
  const params = useParams() as TParams;
  const [searchParams, setSearchParams] = useSearchParams();

  const queryParams = useMemo(() => {
    const result: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      result[key] = value;
    });
    return result;
  }, [searchParams]);

  const setQueryParam = useCallback(
    (key: string, value: string | null) => {
      setSearchParams((prev) => {
        if (value === null) {
          prev.delete(key);
        } else {
          prev.set(key, value);
        }
        return prev;
      });
    },
    [setSearchParams]
  );

  const setQueryParams = useCallback(
    (params: Record<string, string | null>) => {
      setSearchParams((prev) => {
        Object.entries(params).forEach(([key, value]) => {
          if (value === null) {
            prev.delete(key);
          } else {
            prev.set(key, value);
          }
        });
        return prev;
      });
    },
    [setSearchParams]
  );

  return {
    pathname: location.pathname,
    hash: location.hash,
    search: location.search,
    state: location.state,
    params,
    queryParams,
    setQueryParam,
    setQueryParams,
    phase: getPhaseFromPath(location.pathname),
  };
}

// =============================================================================
// Breadcrumbs Hook
// =============================================================================

/**
 * Generate and manage breadcrumbs
 */
export function useBreadcrumbs() {
  const location = useLocation();
  const activeProject = useAtomValue(activeProjectAtom);
  const setBreadcrumbs = useSetAtom(breadcrumbsAtom);

  const crumbs = useMemo(() => {
    const generated = generateBreadcrumbs(
      location.pathname,
      activeProject?.id,
      activeProject?.name
    );
    
    // Convert BreadcrumbItem[] to Breadcrumb[] format expected by atom
    const breadcrumbs = generated.map((item, index) => ({
      id: `breadcrumb-${index}`,
      label: item.label,
      href: item.path || '#',
      path: item.path,
      icon: item.icon,
    }));
    
    setBreadcrumbs(breadcrumbs);
    return generated;
  }, [location.pathname, activeProject, setBreadcrumbs]);

  return crumbs;
}

// =============================================================================
// Navigation History Hook
// =============================================================================

/**
 * Track and manage navigation history
 */
export function useNavigationHistory() {
  const location = useLocation();
  const navigate = useNavigate();
  const setHistory = useSetAtom(navigationHistoryAtom);
  const history = useAtomValue(navigationHistoryAtom);

  // Add current location to history
  useMemo(() => {
    setHistory((prev) => {
      const newEntry = {
        path: location.pathname,
        timestamp: Date.now(),
      };

      // Don't add duplicates
      if (prev.length > 0 && prev[prev.length - 1].path === location.pathname) {
        return prev;
      }

      // Keep last 50 entries
      const newHistory = [...prev, newEntry];
      return newHistory.slice(-50);
    });
  }, [location.pathname, setHistory]);

  const goBack = useCallback(() => {
    if (history.length > 1) {
      navigate(-1);
    } else {
      navigate(ROUTES.DASHBOARD);
    }
  }, [navigate, history.length]);

  const goForward = useCallback(() => {
    navigate(1);
  }, [navigate]);

  const canGoBack = history.length > 1;

  return {
    history,
    goBack,
    goForward,
    canGoBack,
    currentPath: location.pathname,
  };
}

// =============================================================================
// URL Builder Hook
// =============================================================================

/**
 * Build URLs with query params
 */
export function useUrlBuilder() {
  const activeProject = useAtomValue(activeProjectAtom);
  const projectId = activeProject?.id || '';

  return useMemo(
    () => ({
      buildUrl,

      // Development URLs with filters
      sprintBoardUrl: (options?: { sprintId?: string; filter?: string }) =>
        buildUrl(ROUTES.development.board(projectId, options?.sprintId), {
          filter: options?.filter,
        }),

      backlogUrl: (options?: { epic?: string; status?: string }) =>
        buildUrl(ROUTES.development.backlog(projectId), options),

      // Operations URLs with filters
      incidentsUrl: (options?: { severity?: string; status?: string }) =>
        buildUrl(ROUTES.operations.incidents(projectId), options),

      alertsUrl: (options?: { severity?: string; acknowledged?: boolean }) =>
        buildUrl(ROUTES.operations.alerts(projectId), options),

      logsUrl: (options?: { service?: string; level?: string; timeRange?: string }) =>
        buildUrl(ROUTES.operations.logs(projectId), options),

      metricsUrl: (options?: { dashboard?: string; timeRange?: string }) =>
        buildUrl(ROUTES.operations.metrics(projectId), options),

      // Security URLs with filters
      vulnerabilitiesUrl: (options?: {
        severity?: string;
        status?: string;
        scanType?: string;
      }) => buildUrl(ROUTES.security.vulnerabilities(projectId), options),

      complianceUrl: (options?: { frameworkId?: string }) =>
        buildUrl(ROUTES.security.compliance(projectId, options?.frameworkId)),

      auditUrl: (options?: { action?: string; user?: string; from?: string; to?: string }) =>
        buildUrl(ROUTES.security.audit(projectId), options),
    }),
    [projectId]
  );
}

// =============================================================================
// Phase Navigation Hook
// =============================================================================

/**
 * Navigate between phases
 */
export function usePhaseNavigation() {
  const location = useLocation();
  const projectNav = useProjectNavigation();
  const currentPhase = getPhaseFromPath(location.pathname);

  const phases = [
    { id: 'bootstrap', label: 'Bootstrap', nav: projectNav.toBootstrap },
    { id: 'setup', label: 'Setup', nav: projectNav.toSetup },
    { id: 'development', label: 'Development', nav: projectNav.toDevelopment },
    { id: 'operations', label: 'Operations', nav: projectNav.toOperations },
    { id: 'team', label: 'Team', nav: projectNav.toTeamHub },
    { id: 'security', label: 'Security', nav: projectNav.toSecurity },
  ];

  const currentPhaseIndex = phases.findIndex((p) => p.id === currentPhase);

  const goToNextPhase = useCallback(() => {
    if (currentPhaseIndex < phases.length - 1) {
      phases[currentPhaseIndex + 1].nav();
    }
  }, [currentPhaseIndex, phases]);

  const goToPreviousPhase = useCallback(() => {
    if (currentPhaseIndex > 0) {
      phases[currentPhaseIndex - 1].nav();
    }
  }, [currentPhaseIndex, phases]);

  const goToPhase = useCallback(
    (phaseId: string) => {
      const phase = phases.find((p) => p.id === phaseId);
      if (phase) {
        phase.nav();
      }
    },
    [phases]
  );

  return {
    currentPhase,
    phases,
    currentPhaseIndex,
    goToNextPhase,
    goToPreviousPhase,
    goToPhase,
    hasNextPhase: currentPhaseIndex < phases.length - 1,
    hasPreviousPhase: currentPhaseIndex > 0,
  };
}

// =============================================================================
// Deep Link Hook
// =============================================================================

/**
 * Generate shareable deep links
 */
export function useDeepLink() {
  const location = useLocation();

  const generateLink = useCallback(
    (includeQuery = true): string => {
      const base = window.location.origin;
      const path = location.pathname;
      const query = includeQuery ? location.search : '';
      return `${base}${path}${query}`;
    },
    [location]
  );

  const copyToClipboard = useCallback(
    async (includeQuery = true): Promise<boolean> => {
      try {
        const link = generateLink(includeQuery);
        await navigator.clipboard.writeText(link);
        return true;
      } catch {
        return false;
      }
    },
    [generateLink]
  );

  return {
    generateLink,
    copyToClipboard,
    currentUrl: generateLink(true),
  };
}

export default {
  useProjectNavigation,
  useAppNavigation,
  useRouteState,
  useBreadcrumbs,
  useNavigationHistory,
  useUrlBuilder,
  usePhaseNavigation,
  useDeepLink,
};
