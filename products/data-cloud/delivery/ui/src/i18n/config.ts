import i18n from "i18next";
import { initReactI18next } from "react-i18next";

type TranslationTree = {
  [key: string]: string | TranslationTree;
};

/**
 * i18n configuration for Data Cloud Platform UI.
 *
 * Supports multiple languages with locale-specific translations.
 * Default locale is 'en' (English).
 *
 * @doc.type configuration
 * @doc.purpose Internationalization setup
 * @doc.layer frontend
 */

const enTranslations: TranslationTree = {
  // Common
  common: {
    loading: "Loading...",
    error: "Error",
    success: "Success",
    cancel: "Cancel",
    save: "Save",
    delete: "Delete",
    edit: "Edit",
    search: "Search",
    filter: "Filter",
    export: "Export",
    import: "Import",
    refresh: "Refresh",
    apply: "Apply",
    back: "Back",
    home: "Home",
    retry: "Retry",
    close: "Close",
    confirm: "Confirm",
    submit: "Submit",
  },
  // Navigation
  nav: {
    dashboard: "Dashboard",
    data: "Data",
    events: "Events",
    query: "Query",
    pipelines: "Pipelines",
    trust: "Trust",
    settings: "Settings",
    insights: "Insights",
    operations: "Operations",
  },
  // Pipelines (formerly Workflows)
  pipelines: {
    title: "Pipelines",
    description: "Manage and execute data pipelines",
    create: "Create Pipeline",
    edit: "Edit Pipeline",
    delete: "Delete Pipeline",
    execute: "Execute Pipeline",
    cancel: "Cancel Execution",
    retry: "Retry Execution",
    rollback: "Rollback Execution",
    checkpoint: "Create Checkpoint",
    restore: "Restore from Checkpoint",
    status: "Status",
    lastRun: "Last Run",
    failureReason: "Failure Reason",
    retryable: "Retryable",
    approvalState: "Approval State",
    empty: "No pipelines found",
    emptyDescription: "Create your first pipeline to get started",
  },
  // Layout shell strings
  layout: {
    searchPlaceholder: "Search...",
    collapse: "Collapse",
    sectionCore: "Core",
    sectionManage: "Manage",
    sectionAdvanced: "Advanced",
    sectionPreview: "Preview",
    viewModePresetTitle: "View mode preset",
    viewModePresetDescription:
      "View mode presets tune UI focus only. Backend permissions are always enforced independently.",
    devRoleSwitcherTitle: "Dev: Shell role override",
    devRoleSwitcherNote: "Dev-only. Not visible in production.",
    footerProduct: "Data Cloud • Ghatana Platform",
    productName: "Data Cloud",
  },
  // Media Artifacts
  mediaArtifacts: {
    title: "Media Artifacts",
    description:
      "Manage audio and video media artifacts with transcription and vision analysis",
    tabs: {
      all: "All",
      audio: "Audio",
      video: "Video",
    },
    actions: {
      upload: "Upload",
    },
    empty: "No media artifacts found",
    stats: {
      pendingJobs: "Pending Jobs",
      retentionAlerts: "Retention Alerts",
      expiringSoon: "Expiring Soon",
    },
  },
  routes: {
    loading: "Loading page...",
    loadFailed: "Failed to load page",
    unknownError: "An unknown error occurred",
    reload: "Reload",
    home: {
      label: "Home",
      description: "Dashboard overview and quick actions",
    },
    data: {
      label: "Data",
      description: "Explore and manage data collections",
    },
    connectors: {
      label: "Connectors",
      description: "Manage external data source connectors",
    },
    pipelines: {
      label: "Pipelines",
      description: "Manage data-local plugin workflow execution and history",
    },
    query: {
      label: "Query",
      description: "SQL query editor and execution",
    },
    insights: {
      label: "Insights",
      description: "Analytics, automation insights, and cost optimization",
    },
    trust: {
      label: "Trust",
      description: "Governance, compliance, and audit logs",
    },
    events: {
      label: "Events",
      description: "Real-time event stream explorer",
    },
    alerts: {
      label: "Alerts",
      description: "Operator alert triage console",
    },
    memory: {
      label: "Memory",
      description: "Memory plane viewer",
    },
    entities: {
      label: "Entities",
      description: "Entity browser",
    },
    context: {
      label: "Context",
      description: "Context explorer",
    },
    fabric: {
      label: "Data Fabric",
      description: "Data fabric topology and tier management",
    },
    agents: {
      label: "Agents",
      description: "Agent catalog and manager",
    },
    operations: {
      label: "Operations",
      description: "Operations console and diagnostics",
    },
    operationsJobs: {
      label: "Job Center",
      description: "Background operations timeline and status center",
    },
    operationsReleaseTruth: {
      label: "Release Truth",
      description: "Runtime truth, release gates, and evidence dashboard",
    },
    plugins: {
      label: "Plugins",
      description: "Plugin management surfaces",
    },
    settings: {
      label: "Settings",
      description: "System settings (surface under development)",
    },
  },
  // Connectors
  connectors: {
    title: "Data Sources",
    add: "Add Data Source",
    name: "Name",
    type: "Type",
    status: "Status",
    enabled: "Enabled",
    disabled: "Disabled",
    testConnection: "Test Connection",
    enable: "Enable",
    disable: "Disable",
    rotateCredentials: "Rotate Credentials",
    sync: "Sync",
    health: "Health",
    healthy: "Healthy",
    unhealthy: "Unhealthy",
    pending: "Pending",
  },
  // Loading states
  loading: {
    pipelines: "Loading pipelines...",
    policies: "Loading policies...",
    auditLogs: "Loading audit logs...",
    recommendations: "Loading recommendations...",
    governanceSnapshot: "Loading tenant governance snapshot...",
    governanceLifecycle: "Loading governance lifecycle truth...",
    runtimeSurfaces: "Loading runtime surfaces...",
    data: "Loading data...",
  },
  // Error messages
  errors: {
    failedToLoad: "Failed to load",
    failedToSave: "Failed to save",
    failedToDelete: "Failed to delete",
    failedToCreate: "Failed to create",
    failedToUpdate: "Failed to update",
    classifyRetentionPolicy: "Failed to classify retention policy",
    redactPIIFields: "Failed to redact PII fields",
    refreshComplianceSummary: "Failed to refresh compliance summary",
    runRetentionPurgeDryRun: "Failed to run retention purge dry run",
    executeRetentionPurge: "Failed to execute retention purge",
    createPolicy: "Failed to create policy",
    updatePolicy: "Failed to update policy",
    deletePolicy: "Failed to delete policy",
    togglePolicy: "Failed to toggle policy",
    loadTenantGovernanceView: "Failed to load tenant governance view.",
  },
  // Disabled surface
  disabledSurface: {
    disabled: "is not available",
    degraded: "is degraded",
    unavailable: "is unavailable",
    misconfigured: "is misconfigured",
    targetOnly: "is target-only",
    previewNotAllowed: "is preview-only",
    disabledMessage:
      "This capability is not enabled in your current Data Cloud configuration.",
    degradedMessage:
      "This capability is currently degraded due to dependency issues.",
    unavailableMessage:
      "This capability is currently unavailable due to missing or failed dependencies.",
    misconfiguredMessage:
      "This capability is misconfigured and requires configuration updates.",
    targetOnlyMessage:
      "This capability is currently reserved for target-state workflows and is not user-actionable.",
    previewNotAllowedMessage:
      "This capability is currently in preview and is not available for your audience.",
    affectedDependencies: "Affected Dependencies",
    nextAction: "Next Action",
    viewRemediation: "View remediation documentation",
    goBack: "Go back",
    goToHome: "Go to Home",
    contactAdmin: "Contact your administrator to enable this capability.",
  },
  // Workflows
  workflows: {
    title: "Workflows",
    subtitle:
      "Data-local plugin execution surface: focus on what needs a run, what is stalled, and what should happen next.",
    refreshWorkflows: "Refresh workflows",
    newPipeline: "New Pipeline",
    outcomeFirstView: "Outcome-First View",
    outcomeFirstHeading: "Keep the list about outcomes, not pipeline internals",
    outcomeFirstDescription:
      "Review the most important next action for each workflow here, then open the advanced editor only when the flow itself needs deeper changes.",
    startNewPipeline: "Start a new pipeline",
    statsTotal: "Total",
    statsNeedsAttention: "Needs Attention",
    statsRecentlyRun: "Recently Run",
    statsScheduled: "Scheduled",
    statsDraftArchived: "Draft / Archived",
    statusFilter: "Status",
    statusAll: "All Workflows",
    statusActive: "Active",
    statusPaused: "Paused",
    statusDraft: "Draft",
    statusArchived: "Archived",
    searchPlaceholder: "Search workflows by outcome, schedule, or owner...",
    noSummaryYet: "No pipeline summary provided yet.",
    lastRun: "Last run",
    schedule: "Schedule",
    flowSize: "Flow size",
    owner: "Owner",
    manual: "Manual",
    stepsLinks: "{{steps}} steps / {{links}} links",
    noTagsYet: "No tags yet",
    reviewPipeline: "Review pipeline",
    showingPagination: "Showing {{start}}–{{end}} of {{count}} workflows",
    previous: "Previous",
    next: "Next",
    pageOf: "Page {{page}} of {{total}}",
    closeWorkflowDetail: "Close workflow detail",
    outcomeSummary: "Outcome summary",
    policyImpact: "Policy impact:",
    trustTenantScopedMovement: "Tenant-scoped data movement",
    trustExternalSink: "External sink detected — review required",
    trustComplexFlow: "Complex flow ({{steps}} steps) — approval recommended",
    latestRun: "Latest run",
    executionCompleted: "Completed",
    executionFailed: "Failed",
    executionRunning: "Running",
    executionCancelled: "Cancelled",
    executionPending: "Pending",
    outcomeNotAvailable: "Outcome not available",
    showPipelineDetails: "Show pipeline details",
    hidePipelineDetails: "Hide pipeline details",
    created: "Created",
    tags: "Tags",
    noRunsYet: "No runs yet",
    noWorkflowsFound: "No workflows found",
    tryAdjustingFilters: "Try adjusting your filters",
    createFirstWorkflow: "Create your first workflow to get started",
    workflowActions: "Workflow actions",
    deleteWorkflowConfirm:
      'Delete workflow "{{name}}"? This action cannot be undone.',
    advancedEditor: "Advanced editor",
    viewLogs: "View Logs",
    delete: "Delete",
    runNow: "Run Now",
    edit: "Edit",
    resumeOrArchive: "Resume or archive",
    resumeOrArchiveDesc:
      "This pipeline is paused. Decide whether it should resume or leave the active queue.",
    finishFirstRun: "Finish the first run",
    finishFirstRunDescExecuted:
      "Review the draft and promote it when the current flow is ready.",
    finishFirstRunDescNotExecuted:
      "This draft has not been run yet. Validate the outcome and schedule before promotion.",
    referenceOnly: "Reference only",
    referenceOnlyDesc:
      "Keep this pipeline archived unless a previous flow needs to be restored or compared.",
    checkLatestOutcome: "Check the latest outcome",
    checkLatestOutcomeDesc:
      "Active pipeline. Confirm the most recent run completed the intended outcome.",
    runFirstExecution: "Run the first execution",
    runFirstExecutionDesc:
      "Active pipeline with no recorded execution yet. Trigger an initial run before broad rollout.",
    inlineAIRecommendations: "Inline AI Recommendations",
    estimated: "estimated",
    operationalAdvisories: "Operational Advisories",
    analyzingPipeline: "Analysing pipeline with AI…",
    highImpact: "high impact",
    mediumImpact: "medium impact",
    lowImpact: "low impact",
    confidencePercent: "{{value}}% confidence",
    priorityCritical: "critical",
    priorityHigh: "high",
    priorityMedium: "medium",
    priorityLow: "low",
    aiUnavailable: "AI assistance is unavailable",
    aiUnavailableDesc:
      "The AI service is not configured or is disabled in your deployment.",
    aiDegraded: "AI assistance is degraded",
    aiDegradedDesc:
      "The AI service is experiencing issues. Recommendations may be incomplete or delayed.",
  },
  // Trust Center / Governance
  trustCenter: {
    title: "Trust Center",
    loadingGovernanceLifecycle: "Loading governance lifecycle truth...",
    loadingRecommendations: "Loading recommendations...",
    loadingPolicies: "Loading policies...",
    loadingAuditLogs: "Loading audit logs...",
    outcomeSummary: "Outcome summary",
    closeWorkflowDetail: "Close workflow detail",
    classifyRetentionPolicy: "Failed to classify retention policy",
    redactPIIFields: "Failed to redact PII fields",
    refreshComplianceSummary: "Failed to refresh compliance summary",
    runRetentionPurgeDryRun: "Failed to run retention purge dry run",
    executeRetentionPurge: "Failed to execute retention purge",
    createPolicy: "Failed to create policy",
    updatePolicy: "Failed to update policy",
    deletePolicy: "Failed to delete policy",
    togglePolicy: "Failed to toggle policy",
    loadTenantGovernanceView: "Failed to load tenant governance view.",
  },
  // Data Explorer
  dataExplorer: {
    title: "Data Explorer",
    subtitle: "Browse and manage your data collections",
    searchPlaceholder: "Search collections...",
    noCollections: "No collections found",
    noCollectionsDesc: "Create your first collection to get started",
    loading: "Loading collections...",
    failedToLoad: "Failed to load collections",
    retry: "Retry",
    viewMode: "View Mode",
    viewModeTable: "Table",
    viewModeLineage: "Lineage",
    viewModeQuality: "Quality",
    viewModeSchema: "Schema",
    collectionDetail: "Collection Details",
    overview: "Overview",
    metadata: "Metadata",
    schema: "Schema",
    quality: "Quality",
    lineage: "Lineage",
    records: "Records",
    retentionPolicy: "Retention Policy",
    retentionPolicyDesc: "Data retention and archival configuration",
    lineageInfo: "Lineage",
    lineageDesc: "Data lineage and provenance information",
    actions: {
      view: "View",
      edit: "Edit",
      quality: "Quality",
      lineage: "Lineage",
      schema: "Schema",
      archive: "Archive",
      delete: "Delete",
    },
  },
};

function pseudoLocalize(text: string): string {
  return `[!! ${text.replace(/[aeiouAEIOU]/g, "$&$&")} !!]`;
}

function toPseudoLocale(tree: TranslationTree): TranslationTree {
  const entries = Object.entries(tree).map(([key, value]) => {
    if (typeof value === "string") {
      return [key, pseudoLocalize(value)];
    }
    return [key, toPseudoLocale(value)];
  });

  return Object.fromEntries(entries);
}

const resources = {
  en: {
    translation: enTranslations,
  },
  "en-XA": {
    translation: toPseudoLocale(enTranslations),
  },
};

i18n.use(initReactI18next).init({
  resources,
  lng: "en", // default language
  fallbackLng: "en",
  supportedLngs: ["en", "en-XA"],
  nonExplicitSupportedLngs: true,
  interpolation: {
    escapeValue: false, // React already escapes values
  },
});

export default i18n;
