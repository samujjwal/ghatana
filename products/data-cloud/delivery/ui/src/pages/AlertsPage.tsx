/**
 * Alerts Page
 *
 * Operator-facing alerts triage console.
 *
 * Uses the canonical launcher-backed alerts routes when available and
 * falls back to the shared unsupported boundary when the deployment does
 * not expose the live alerts surface.
 *
 * @doc.type page
 * @doc.purpose Operator alerts triage page
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import { Button, IconButton, Select } from "@ghatana/design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  EyeOff,
  Lightbulb,
  Link2,
  Play,
  ShieldCheck,
  Sparkles,
  X,
  Zap,
} from "lucide-react";
import React, { useEffect, useState } from "react";
import type { AiCrossCorrelation } from "../api/ai-operations.service";
import { aiOperationsService } from "../api/ai-operations.service";
import type {
  Alert,
  AlertGroup,
  AlertSeverity,
  AlertStatus,
  ResolutionSuggestion,
} from "../api/alerts.service";
import {
  ALERTS_UNSUPPORTED_MESSAGE,
  alertsService,
} from "../api/alerts.service";
import { getSurfaceSignal, useSurfaceRegistry } from "../api/surfaces.service";
import {
  AlertRuleForm,
  type AlertRule,
} from "../components/alerts/AlertRuleForm";
import { GuardedAction } from "../components/common/GuardedAction";
import { SearchFilterBar } from "../components/common/SearchFilterBar";
import { UnsupportedSurfaceBoundary } from "../components/common/UnsupportedSurfaceBoundary";
import { alertsSurfaceBoundary } from "../components/common/unsupportedSurfaceRegistry";
import { UnsupportedRuntimeBoundaryError } from "../lib/runtime-boundaries";
import {
  bgStyles,
  cardStyles,
  cn,
  metricCardStyles,
  textStyles,
} from "../lib/theme";

function isAlertsUnsupportedError(error: unknown): boolean {
  return (
    error instanceof Error && error.message.includes(ALERTS_UNSUPPORTED_MESSAGE)
  );
}

/**
 * Severity styles
 */
const severityStyles: Record<AlertSeverity, string> = {
  critical: "bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200",
  warning:
    "bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200",
  info: "bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200",
};

/**
 * Status styles
 */
const statusStyles: Record<AlertStatus, string> = {
  active: "bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200",
  acknowledged:
    "bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200",
  resolved: "bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200",
};

/**
 * AI Alert Group Card
 */
function AlertGroupCard({
  group,
  alerts,
  onResolveGroup,
}: {
  group: AlertGroup;
  alerts: Alert[];
  onResolveGroup: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const groupedAlerts = alerts.filter((a) => group.alertIds.includes(a.id));

  return (
    <div
      className={cn(
        "bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20",
        "border border-purple-200 dark:border-purple-800",
        "rounded-xl overflow-hidden mb-4",
      )}
    >
      <div
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-purple-100/50 dark:hover:bg-purple-900/30 transition-colors cursor-pointer"
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            event.currentTarget.click();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <div className="flex items-center gap-3">
          <div className="p-2 bg-purple-100 dark:bg-purple-900/50 rounded-lg">
            <Link2 className="h-4 w-4 text-purple-600 dark:text-purple-400" />
          </div>
          <div className="text-left">
            <div className="flex items-center gap-2">
              <span className="font-medium text-gray-900 dark:text-gray-100">
                {group.title}
              </span>
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-purple-100 dark:bg-purple-900/50 text-purple-600 dark:text-purple-400 text-xs rounded">
                <Sparkles className="h-3 w-3" />
                {Math.round(group.aiConfidence * 100)}% confidence
              </span>
              <span className="text-xs px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-500 rounded">
                {groupedAlerts.length} related alerts
              </span>
            </div>
            <p className="text-sm text-gray-500 mt-0.5">{group.rootCause}</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {group.suggestedActionType === "auto" && (
            <Button
              variant="solid"
              tone="success"
              size="sm"
              leadingIcon={<Zap className="h-3 w-3" />}
              onClick={(e) => {
                e.stopPropagation();
                onResolveGroup();
              }}
            >
              Auto-resolve
            </Button>
          )}
          {expanded ? (
            <ChevronDown className="h-5 w-5 text-gray-400" />
          ) : (
            <ChevronRight className="h-5 w-5 text-gray-400" />
          )}
        </div>
      </div>

      {expanded && (
        <div className="px-4 pb-4 border-t border-purple-200 dark:border-purple-800">
          <div className="mt-3 p-3 bg-white/50 dark:bg-gray-800/50 rounded-lg">
            <div className="flex items-start gap-2">
              <Lightbulb className="h-4 w-4 text-amber-500 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
                  Suggested Action
                </p>
                <p className="text-sm text-gray-500">{group.suggestedAction}</p>
              </div>
            </div>
          </div>
          <div className="mt-3 space-y-2">
            {groupedAlerts.map((alert) => (
              <div
                key={alert.id}
                className="flex items-center gap-3 p-2 bg-white/50 dark:bg-gray-800/50 rounded-lg"
              >
                <AlertTriangle
                  className={cn(
                    "h-4 w-4",
                    alert.severity === "critical"
                      ? "text-red-500"
                      : alert.severity === "warning"
                        ? "text-amber-500"
                        : "text-blue-500",
                  )}
                />
                <span className="text-sm text-gray-700 dark:text-gray-300 flex-1">
                  {alert.title}
                </span>
                <span className="text-xs text-gray-400">{alert.source}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * AI Resolution Suggestion Card
 */
function ResolutionSuggestionCard({
  suggestion,
  onApply,
  onDismiss,
}: {
  suggestion: ResolutionSuggestion;
  onApply: () => void;
  onDismiss: () => void;
}) {
  const [showSteps, setShowSteps] = useState(false);

  return (
    <div
      className={cn(
        "p-3 rounded-lg",
        "bg-green-50 dark:bg-green-900/20",
        "border border-green-200 dark:border-green-800",
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Lightbulb className="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {suggestion.suggestion}
            </p>
            <div className="flex items-center gap-2 mt-1">
              <span className="text-xs text-gray-500">
                {Math.round(suggestion.confidence * 100)}% confidence
              </span>
              {suggestion.steps && (
                <Button
                  variant="link"
                  size="sm"
                  tone="success"
                  onClick={() => setShowSteps(!showSteps)}
                >
                  {showSteps ? "Hide steps" : "Show steps"}
                </Button>
              )}
            </div>
            {showSteps && suggestion.steps && (
              <ol className="mt-2 ml-4 text-xs text-gray-500 list-decimal space-y-1">
                {suggestion.steps.map((step, i) => (
                  <li key={i}>{step}</li>
                ))}
              </ol>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {suggestion.canAutoResolve && (
            <Button
              variant="solid"
              tone="success"
              size="sm"
              leadingIcon={<Play className="h-3 w-3" />}
              onClick={onApply}
            >
              Apply
            </Button>
          )}
          <IconButton
            variant="ghost"
            tone="neutral"
            size="sm"
            icon={<X className="h-4 w-4" />}
            label="Dismiss suggestion"
            onClick={onDismiss}
          />
        </div>
      </div>
    </div>
  );
}

const SUPPORTED_ALERT_ROUTE_COUNT = 7;

/**
 * AI Cross-Surface Correlations Panel
 *
 * Capability-gated. Renders when the ML platform ships cross-surface
 * correlation data for alerts. Falls back to a pending notice when
 * the backend is unavailable (404/405/501 → boundary).
 */
function AiCorrelationsPanel({
  correlations,
  isBoundary,
}: {
  correlations: AiCrossCorrelation[];
  isBoundary: boolean;
}) {
  if (isBoundary) {
    return (
      <div
        className="flex items-center gap-2 rounded-lg border border-purple-200 bg-purple-50 px-3 py-2 text-sm text-purple-700 dark:border-purple-800 dark:bg-purple-950/30 dark:text-purple-300"
        data-testid="alerts-ai-correlations-boundary"
      >
        <Sparkles className="h-4 w-4 shrink-0" />
        <span>
          Cross-surface AI correlations are pending — ML platform activation
          required. Correlations will appear here automatically once the feature
          scoring service is deployed.
        </span>
      </div>
    );
  }

  if (correlations.length === 0) {
    return null;
  }

  return (
    <div
      className="rounded-lg border border-purple-200 bg-purple-50 dark:border-purple-800 dark:bg-purple-950/30 p-4"
      data-testid="alerts-ai-correlations-panel"
    >
      <div className="flex items-center gap-2 mb-3">
        <Link2 className="h-4 w-4 text-purple-600 dark:text-purple-400" />
        <p className="text-sm font-medium text-purple-900 dark:text-purple-200">
          AI-detected cross-surface correlations
        </p>
        <span className="ml-auto text-xs text-purple-500">
          {correlations.length} found
        </span>
      </div>
      <div className="space-y-2">
        {correlations.slice(0, 5).map((c) => (
          <div key={c.id} className="flex items-start gap-2 text-sm">
            <span className="shrink-0 rounded px-1.5 py-0.5 text-xs bg-purple-100 dark:bg-purple-900 text-purple-700 dark:text-purple-300 capitalize">
              {c.correlationType}
            </span>
            <span className="text-gray-700 dark:text-gray-300 flex-1">
              {c.explanation}
            </span>
            <span className="shrink-0 text-xs text-gray-400">
              {Math.round(c.confidence * 100)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function AlertTruthPanel({
  routeCoverage,
  streamHealth,
  groupedCoverage,
  suggestionCoverage,
}: {
  routeCoverage: number;
  streamHealth: "live" | "degraded";
  groupedCoverage: number;
  suggestionCoverage: number;
}) {
  return (
    <div
      className="grid grid-cols-1 gap-4 md:grid-cols-4 mb-6"
      data-testid="alerts-truth-panel"
    >
      <div className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="flex items-center gap-2 mb-2">
          <ShieldCheck className="h-4 w-4 text-green-500" />
          <p className={textStyles.label}>Route Coverage</p>
        </div>
        <p className={textStyles.h3}>
          {routeCoverage}/{SUPPORTED_ALERT_ROUTE_COUNT} live
        </p>
        <p className={textStyles.xs}>
          List, lifecycle, grouping, suggestions, rules, and stream are all
          launcher-backed in this deployment.
        </p>
      </div>
      <div className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="flex items-center gap-2 mb-2">
          <Activity
            className={cn(
              "h-4 w-4",
              streamHealth === "live" ? "text-green-500" : "text-amber-500",
            )}
          />
          <p className={textStyles.label}>Stream Health</p>
        </div>
        <p className={textStyles.h3}>
          {streamHealth === "live" ? "Connected" : "Degraded"}
        </p>
        <p className={textStyles.xs}>
          {streamHealth === "live"
            ? "Live alert mutations invalidate the triage cache immediately."
            : "The page is serving live data, but the event stream is not yet confirmed open."}
        </p>
      </div>
      <div className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="flex items-center gap-2 mb-2">
          <Sparkles className="h-4 w-4 text-purple-500" />
          <p className={textStyles.label}>Grouped Coverage</p>
        </div>
        <p className={textStyles.h3}>{groupedCoverage}%</p>
        <p className={textStyles.xs}>
          Active incidents currently represented in AI-detected correlation
          groups.
        </p>
      </div>
      <div className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="flex items-center gap-2 mb-2">
          <Lightbulb className="h-4 w-4 text-amber-500" />
          <p className={textStyles.label}>Suggestion Coverage</p>
        </div>
        <p className={textStyles.h3}>{suggestionCoverage}%</p>
        <p className={textStyles.xs}>
          Share of active incidents with a generated resolution suggestion or
          grouped action path.
        </p>
      </div>
    </div>
  );
}

/**
 * Alerts Page Component
 */
export function AlertsPage(): React.ReactElement {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<"all" | AlertSeverity>("all");
  const [statusFilter, setStatusFilter] = useState<"all" | AlertStatus>(
    "active",
  );
  const [searchQuery, setSearchQuery] = useState("");
  const [isRuleFormOpen, setIsRuleFormOpen] = useState(false);
  const [selectedRule, setSelectedRule] = useState<AlertRule | undefined>(
    undefined,
  );
  const [viewMode, setViewMode] = useState<"list" | "grouped">("grouped");
  const [dismissedSuggestions, setDismissedSuggestions] = useState<string[]>(
    [],
  );
  const [streamState, setStreamState] = useState<"idle" | "open" | "error">(
    "idle",
  );
  const [showRuleManagement, setShowRuleManagement] = useState(false);

  const alertsQuery = useQuery({
    queryKey: ["alerts"],
    queryFn: () => alertsService.getAlerts(),
  });
  const allAlerts = alertsQuery.data ?? [];

  const groupsQuery = useQuery({
    queryKey: ["alerts", "groups"],
    queryFn: () => alertsService.getAlertGroups(),
  });
  const alertGroups = groupsQuery.data ?? [];

  const suggestionsQuery = useQuery({
    queryKey: ["alerts", "suggestions"],
    queryFn: () => alertsService.getResolutionSuggestions(),
  });
  const suggestions = suggestionsQuery.data ?? [];

  const rulesQuery = useQuery({
    queryKey: ["alerts", "rules"],
    queryFn: () => alertsService.listAlertRules(),
  });
  const alertRules = rulesQuery.data ?? [];

  const alertsUnsupported = [
    alertsQuery.error,
    groupsQuery.error,
    suggestionsQuery.error,
  ].some(isAlertsUnsupportedError);
  const shouldOpenAlertsStream =
    !alertsUnsupported &&
    alertsQuery.isSuccess &&
    groupsQuery.isSuccess &&
    suggestionsQuery.isSuccess;

  const acknowledgeMutation = useMutation({
    mutationFn: (alertId: string) => alertsService.acknowledgeAlert(alertId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alerts"] }),
  });

  const resolveMutation = useMutation({
    mutationFn: (alertId: string) => alertsService.resolveAlert(alertId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alerts"] }),
  });

  const resolveGroupMutation = useMutation({
    mutationFn: (groupId: string) => alertsService.resolveGroup(groupId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alerts"] }),
  });

  const applySuggestionMutation = useMutation({
    mutationFn: (suggestionId: string) =>
      alertsService.applySuggestion(suggestionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alerts"] }),
  });

  // AI cross-surface correlations — capability-gated; boundary-aware.
  const { data: surfaceRegistry } = useSurfaceRegistry();
  const aiAssistCapability = getSurfaceSignal(surfaceRegistry?.surfaces, [
    "ai_assist",
    "ai.assist",
    "assist",
  ]);
  const correlationsQuery = useQuery({
    queryKey: ["ai", "correlations", "alerts"],
    queryFn: () =>
      aiOperationsService.getCrossCorrelations({
        primarySurface: "alerts",
        limit: 10,
      }),
    staleTime: 5 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
    enabled:
      aiAssistCapability?.status !== "UNAVAILABLE" &&
      aiAssistCapability?.status !== "DISABLED" &&
      aiAssistCapability?.status !== "MISCONFIGURED",
  });
  const aiCorrelations = correlationsQuery.data ?? [];
  const aiCorrelationsBoundary =
    correlationsQuery.error instanceof UnsupportedRuntimeBoundaryError;

  // Only start live alert streaming when the launcher proves the routes exist.
  useEffect(() => {
    if (!shouldOpenAlertsStream) {
      return;
    }

    const es = alertsService.openStream();
    setStreamState("idle");
    es.addEventListener("open", () => {
      setStreamState("open");
    });
    es.addEventListener("error", () => {
      setStreamState("error");
    });
    es.addEventListener("message", () => {
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
    });

    return () => {
      es.close();
      setStreamState("idle");
    };
  }, [queryClient, shouldOpenAlertsStream]);

  const filteredAlerts = allAlerts.filter((alert) => {
    if (filter !== "all" && alert.severity !== filter) return false;
    if (statusFilter !== "all" && alert.status !== statusFilter) return false;
    if (
      searchQuery.trim() &&
      !alert.title.toLowerCase().includes(searchQuery.toLowerCase()) &&
      !alert.description.toLowerCase().includes(searchQuery.toLowerCase())
    )
      return false;
    return true;
  });

  const alertCounts = {
    critical: allAlerts.filter(
      (a) => a.severity === "critical" && a.status === "active",
    ).length,
    warning: allAlerts.filter(
      (a) => a.severity === "warning" && a.status === "active",
    ).length,
    info: allAlerts.filter(
      (a) => a.severity === "info" && a.status === "active",
    ).length,
    total: allAlerts.filter((a) => a.status === "active").length,
  };
  const enabledRuleCount = alertRules.filter((rule) => rule.enabled).length;
  const activeSuggestionCount = suggestions.filter(
    (suggestion) => !dismissedSuggestions.includes(suggestion.id),
  ).length;
  const activeAlerts = allAlerts.filter((alert) => alert.status === "active");
  const groupedAlertIds = new Set(
    alertGroups.flatMap((group) => group.alertIds),
  );
  const suggestionAlertIds = new Set(
    suggestions.map((suggestion) => suggestion.alertId),
  );
  const groupedCoverage =
    activeAlerts.length === 0
      ? 100
      : Math.round(
          (activeAlerts.filter((alert) => groupedAlertIds.has(alert.id))
            .length /
            activeAlerts.length) *
            100,
        );
  const suggestionCoverage =
    activeAlerts.length === 0
      ? 100
      : Math.round(
          (activeAlerts.filter(
            (alert) =>
              groupedAlertIds.has(alert.id) || suggestionAlertIds.has(alert.id),
          ).length /
            activeAlerts.length) *
            100,
        );
  const routeCoverage = alertsUnsupported ? 0 : SUPPORTED_ALERT_ROUTE_COUNT;
  const streamHealth = streamState === "open" ? "live" : "degraded";

  const createRuleMutation = useMutation({
    mutationFn: (rule: Omit<AlertRule, "id">) =>
      alertsService.createAlertRule(rule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alerts", "rules"] });
      setSelectedRule(undefined);
      setIsRuleFormOpen(false);
    },
  });

  const updateRuleMutation = useMutation({
    mutationFn: ({
      ruleId,
      rule,
    }: {
      ruleId: string;
      rule: Partial<AlertRule>;
    }) => alertsService.updateAlertRule(ruleId, rule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alerts", "rules"] });
      setSelectedRule(undefined);
      setIsRuleFormOpen(false);
    },
  });

  const openCreateRuleForm = () => {
    setSelectedRule(undefined);
    setIsRuleFormOpen(true);
  };

  const openEditRuleForm = (rule: AlertRule) => {
    setSelectedRule(rule);
    setIsRuleFormOpen(true);
  };

  const handleSaveRule = (rule: AlertRule) => {
    if (rule.id != null && rule.id !== "") {
      updateRuleMutation.mutate({ ruleId: rule.id, rule });
    } else {
      const { id: _id, ...ruleWithoutId } = rule;
      createRuleMutation.mutate(ruleWithoutId);
    }
  };

  if (alertsUnsupported) {
    return (
      <div className={cn("min-h-screen p-6", bgStyles.page)}>
        <div className="mb-6">
          <h1 className={textStyles.h1}>Alerts</h1>
          <p className={textStyles.muted}>
            Operator-facing alert triage remains unavailable until the launcher
            exposes canonical alert routes.
          </p>
        </div>

        <UnsupportedSurfaceBoundary
          title={alertsSurfaceBoundary.title}
          summary={alertsSurfaceBoundary.summary}
          details={alertsSurfaceBoundary.details}
          state={alertsSurfaceBoundary.state}
        />
      </div>
    );
  }

  return (
    <div
      className={cn("min-h-screen p-6", bgStyles.page)}
      data-testid="alerts-page"
    >
      {/* Alert Rule Form Modal */}
      <AlertRuleForm
        isOpen={isRuleFormOpen}
        rule={selectedRule}
        onClose={() => {
          setIsRuleFormOpen(false);
          setSelectedRule(undefined);
        }}
        onSave={handleSaveRule}
      />

      {/* Header */}
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className={textStyles.h1}>Alerts</h1>
          <p className={textStyles.muted}>
            Triage active incidents first, then step into grouped root-cause
            review or rule maintenance only when needed.
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => {
              setShowRuleManagement((current) => !current);
            }}
            data-testid="alert-rule-management-toggle"
            disabled={alertsUnsupported}
          >
            {showRuleManagement ? "Hide Rule Management" : "Review Rules"}
          </Button>
        </div>
      </div>

      {alertsUnsupported && (
        <UnsupportedSurfaceBoundary
          className="mb-6"
          title={alertsSurfaceBoundary.title}
          summary={alertsSurfaceBoundary.summary}
          details={alertsSurfaceBoundary.details}
          state={alertsSurfaceBoundary.state}
        />
      )}

      <AlertTruthPanel
        routeCoverage={routeCoverage}
        streamHealth={streamHealth}
        groupedCoverage={groupedCoverage}
        suggestionCoverage={suggestionCoverage}
      />

      {/* AI cross-surface correlations — shown when ai_assist capability is active */}
      {aiAssistCapability?.status !== "UNAVAILABLE" &&
        aiAssistCapability?.status !== "DISABLED" &&
        aiAssistCapability?.status !== "MISCONFIGURED" &&
        (aiCorrelations.length > 0 || aiCorrelationsBoundary) && (
          <div className="mb-6">
            <AiCorrelationsPanel
              correlations={aiCorrelations}
              isBoundary={aiCorrelationsBoundary}
            />
          </div>
        )}

      {/* Search — OPS-001 */}
      <div className="mb-6">
        <SearchFilterBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          searchPlaceholder="Search alerts by title or description…"
          hasActiveFilters={!!searchQuery}
          onClear={() => setSearchQuery("")}
        />
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        <div className={metricCardStyles.red}>
          <p className={textStyles.muted}>Critical</p>
          <p className={cn(textStyles.h2, "text-red-600 dark:text-red-400")}>
            {alertCounts.critical}
          </p>
        </div>
        <div className={metricCardStyles.yellow}>
          <p className={textStyles.muted}>Warning</p>
          <p
            className={cn(
              textStyles.h2,
              "text-yellow-600 dark:text-yellow-400",
            )}
          >
            {alertCounts.warning}
          </p>
        </div>
        <div className={metricCardStyles.blue}>
          <p className={textStyles.muted}>Info</p>
          <p className={cn(textStyles.h2, "text-blue-600 dark:text-blue-400")}>
            {alertCounts.info}
          </p>
        </div>
        <div className={metricCardStyles.base}>
          <p className={textStyles.muted}>Total Active</p>
          <p className={textStyles.h2}>{alertCounts.total}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)] gap-6 mb-6">
        <div
          className={cn(cardStyles.base, cardStyles.padded)}
          data-testid="alert-rule-management-panel"
        >
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className={textStyles.label}>Operational Focus</p>
              <h2 className={textStyles.h3}>
                Prioritize active incidents before historical cleanup
              </h2>
              <p className={cn(textStyles.muted, "mt-1")}>
                The alerts view now defaults to active incidents, derived
                correlations, and live suggestions so operators can resolve the
                current problem set first.
              </p>
            </div>
            <div className="text-right">
              <p className={textStyles.label}>Visible Suggestions</p>
              <p className={textStyles.h2}>{activeSuggestionCount}</p>
            </div>
          </div>
        </div>

        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <div className="flex items-center justify-between gap-3 mb-3">
            <div>
              <p className={textStyles.label}>Rule Management</p>
              <p className={textStyles.h3}>{enabledRuleCount} enabled rules</p>
            </div>
            <Button
              variant="outline"
              size="sm"
              leadingIcon={
                showRuleManagement ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )
              }
              onClick={() => setShowRuleManagement((current) => !current)}
            >
              {showRuleManagement ? "Collapse" : "Expand"}
            </Button>
          </div>
          <p className={textStyles.muted}>
            Keep rule editing out of the main triage path unless an alert
            pattern needs to be changed.
          </p>

          {showRuleManagement && (
            <div className="mt-4 space-y-3">
              <Button variant="solid" size="sm" onClick={openCreateRuleForm}>
                + Create Alert Rule
              </Button>
              {alertRules.slice(0, 5).map((rule) => (
                <button
                  key={rule.id ?? rule.name}
                  onClick={() => openEditRuleForm(rule)}
                  className="w-full text-left rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-2 hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="font-medium text-gray-900 dark:text-gray-100">
                        {rule.name}
                      </p>
                      <p className={textStyles.xs}>
                        {rule.metric} {rule.operator} {rule.threshold}
                      </p>
                    </div>
                    <span
                      className={cn(
                        "px-2 py-1 rounded text-xs font-medium",
                        rule.enabled
                          ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300"
                          : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300",
                      )}
                    >
                      {rule.enabled ? "Enabled" : "Paused"}
                    </span>
                  </div>
                </button>
              ))}
              {alertRules.length === 0 && (
                <p className={textStyles.muted}>
                  No alert rules configured yet. Create one to start routing
                  incidents toward the right responders.
                </p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center justify-between gap-4 mb-6">
        <div className="flex gap-4">
          <Select
            value={filter}
            onChange={(e) => setFilter(e.target.value as "all" | AlertSeverity)}
            aria-label="Filter by severity"
            size="sm"
            options={[
              { value: "all", label: "All Severities" },
              { value: "critical", label: "Critical" },
              { value: "warning", label: "Warning" },
              { value: "info", label: "Info" },
            ]}
          />
          <Select
            value={statusFilter}
            onChange={(e) =>
              setStatusFilter(e.target.value as "all" | AlertStatus)
            }
            aria-label="Filter by status"
            size="sm"
            options={[
              { value: "all", label: "All Statuses" },
              { value: "active", label: "Active" },
              { value: "acknowledged", label: "Acknowledged" },
              { value: "resolved", label: "Resolved" },
            ]}
          />
        </div>

        {/* View Mode Toggle */}
        <div
          className="flex items-center gap-2 bg-gray-100 dark:bg-gray-800 p-1 rounded-lg"
          role="group"
          aria-label="Alert view mode"
        >
          <button
            onClick={() => setViewMode("grouped")}
            aria-pressed={viewMode === "grouped"}
            className={cn(
              "flex items-center gap-2 px-3 py-1.5 rounded-md text-sm",
              viewMode === "grouped"
                ? "bg-white dark:bg-gray-700 shadow-sm text-gray-900 dark:text-gray-100"
                : "text-gray-600 dark:text-gray-400",
            )}
          >
            <Sparkles className="h-4 w-4" />
            Grouped by root cause
          </button>
          <button
            onClick={() => setViewMode("list")}
            aria-pressed={viewMode === "list"}
            className={cn(
              "flex items-center gap-2 px-3 py-1.5 rounded-md text-sm",
              viewMode === "list"
                ? "bg-white dark:bg-gray-700 shadow-sm text-gray-900 dark:text-gray-100"
                : "text-gray-600 dark:text-gray-400",
            )}
          >
            List View
          </button>
        </div>
      </div>

      {/* Grouped by root cause view */}
      {viewMode === "grouped" && alertGroups.length > 0 && (
        <div className="mb-6" data-testid="alerts-grouped-section">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="h-5 w-5 text-purple-500" />
            <h2 className={textStyles.h3}>Grouped by root cause</h2>
            <span className="text-xs px-2 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded">
              {alertGroups.length} groups found
            </span>
          </div>
          {alertGroups.map((group) => (
            <AlertGroupCard
              key={group.id}
              group={group}
              alerts={allAlerts}
              onResolveGroup={() => resolveGroupMutation.mutate(group.id)}
            />
          ))}

          {activeSuggestionCount > 0 && (
            <div className="mt-6">
              <div className="flex items-center gap-2 mb-4">
                <Lightbulb className="h-5 w-5 text-amber-500" />
                <h2 className={textStyles.h3}>Review-Needed Suggestions</h2>
              </div>
              <div className="space-y-3">
                {suggestions
                  .filter(
                    (suggestion) =>
                      !dismissedSuggestions.includes(suggestion.id),
                  )
                  .slice(0, 3)
                  .map((suggestion) => (
                    <ResolutionSuggestionCard
                      key={suggestion.id}
                      suggestion={suggestion}
                      onApply={() =>
                        applySuggestionMutation.mutate(suggestion.id)
                      }
                      onDismiss={() =>
                        setDismissedSuggestions((current) => [
                          ...current,
                          suggestion.id,
                        ])
                      }
                    />
                  ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* AI Resolution Suggestions */}
      {viewMode === "list" && activeSuggestionCount > 0 && (
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-4">
            <Lightbulb className="h-5 w-5 text-amber-500" />
            <h2 className={textStyles.h3}>AI Resolution Suggestions</h2>
          </div>
          <div className="space-y-3">
            {suggestions
              .filter((s) => !dismissedSuggestions.includes(s.id))
              .map((suggestion) => (
                <ResolutionSuggestionCard
                  key={suggestion.id}
                  suggestion={suggestion}
                  onApply={() => applySuggestionMutation.mutate(suggestion.id)}
                  onDismiss={() =>
                    setDismissedSuggestions((current) => [
                      ...current,
                      suggestion.id,
                    ])
                  }
                />
              ))}
          </div>
        </div>
      )}

      {/* Alert List */}
      <div className="space-y-4">
        {filteredAlerts.map((alert) => (
          <div
            key={alert.id}
            data-testid="alert-item"
            className={cn(
              cardStyles.base,
              cardStyles.padded,
              alert.severity === "critical" &&
                alert.status === "active" &&
                "border-l-4 border-red-500",
            )}
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-3 mb-2">
                  <span
                    className={cn(
                      "px-2 py-1 rounded text-xs font-medium",
                      severityStyles[alert.severity],
                    )}
                  >
                    {alert.severity.toUpperCase()}
                  </span>
                  <span
                    className={cn(
                      "px-2 py-1 rounded text-xs font-medium",
                      statusStyles[alert.status],
                    )}
                  >
                    {alert.status}
                  </span>
                  <span className={textStyles.xs}>{alert.source}</span>
                </div>
                <h3 className={textStyles.h3}>{alert.title}</h3>
                <p className={cn(textStyles.muted, "mt-1")}>
                  {alert.description}
                </p>
                <p className={cn(textStyles.xs, "mt-2")}>
                  Created: {new Date(alert.createdAt).toLocaleString()}
                  {alert.acknowledgedAt &&
                    ` • Acknowledged: ${new Date(alert.acknowledgedAt).toLocaleString()}`}
                  {alert.resolvedAt &&
                    ` • Resolved: ${new Date(alert.resolvedAt).toLocaleString()}`}
                </p>
              </div>
              <div className="flex gap-2">
                {alert.status === "active" && (
                  <GuardedAction
                    label="Acknowledge alert"
                    impact={`Alert "${alert.title}" will be marked as acknowledged. It will remain visible but suppressed from critical notifications.`}
                    requiresReason
                    reasonPrompt="Why are you acknowledging this alert? (recorded for audit)"
                    confirmLabel="Acknowledge"
                    onConfirm={(_reason) => {
                      acknowledgeMutation.mutate(alert.id);
                    }}
                    isExecuting={acknowledgeMutation.isPending}
                    data-testid={`guarded-acknowledge-${alert.id}`}
                  >
                    {({ open }) => (
                      <Button
                        variant="outline"
                        size="sm"
                        loading={acknowledgeMutation.isPending}
                        onClick={open}
                      >
                        Acknowledge
                      </Button>
                    )}
                  </GuardedAction>
                )}
                {alert.status !== "resolved" && (
                  <GuardedAction
                    label="Resolve alert"
                    impact={`Alert "${alert.title}" will be marked as resolved and closed. This action is final — contact your team lead to reopen if needed.`}
                    requiresReason
                    reasonPrompt="What resolved this alert? (recorded for audit)"
                    confirmLabel="Resolve"
                    onConfirm={(_reason) => {
                      resolveMutation.mutate(alert.id);
                    }}
                    isExecuting={resolveMutation.isPending}
                    data-testid={`guarded-resolve-${alert.id}`}
                  >
                    {({ open }) => (
                      <Button
                        variant="solid"
                        tone="success"
                        size="sm"
                        loading={resolveMutation.isPending}
                        onClick={open}
                      >
                        Resolve
                      </Button>
                    )}
                  </GuardedAction>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {filteredAlerts.length === 0 && (
        <div
          className={cn(
            cardStyles.base,
            cardStyles.padded,
            "text-center py-12",
          )}
        >
          <p className={textStyles.muted}>
            No alerts match the current filters
          </p>
        </div>
      )}
    </div>
  );
}

export default AlertsPage;
