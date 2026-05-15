import { useTranslation } from '@ghatana/i18n';

export const STUDIO_TRANSLATIONS = {
  'studio.navigation.home': 'Home',
  'studio.navigation.ideas': 'Ideas',
  'studio.navigation.blueprints': 'Blueprints',
  'studio.navigation.canvas': 'Canvas',
  'studio.navigation.develop': 'Develop',
  'studio.navigation.lifecycle': 'Lifecycle',
  'studio.navigation.agents': 'Agents',
  'studio.navigation.artifacts': 'Artifacts',
  'studio.navigation.deployments': 'Deployments',
  'studio.navigation.health': 'Health',
  'studio.navigation.learn': 'Learn',
  'studio.navigation.settings': 'Settings',
  'studio.status.ready': 'Ready',
  'studio.status.empty': 'Empty',
  'studio.status.degraded': 'Degraded',
  'studio.status.blocked': 'Blocked',
  'studio.brand.title': 'Ghatana Studio',
  'studio.brand.subtitle': 'Unified product development',
  'studio.header.documentation': 'Documentation',
  'studio.header.unknownRoute': 'Unknown Studio route',
  'studio.header.ownershipSuffix': 'owned',
  'studio.route.notFound.title': 'Page not found',
  'studio.route.notFound.description':
    'The requested Studio route is not registered in the canonical navigation.',
  'studio.route.settings.title': 'Settings',
  'studio.route.settings.description':
    'Workspace, provider mode, approvals, and capability settings appear here when their contracts are available.',
  'studio.route.agents.badge.fallback': 'requires approval',
  'studio.route.agents.title': 'Agents',
  'studio.route.agents.description':
    'Governed agentic lifecycle proposals with evidence, risk, approval, and rollback context.',
  'studio.route.agents.proposalTitle': 'Lifecycle proposal',
  'studio.route.agents.evidenceRequired': 'Evidence required before execution.',
  'studio.route.agents.noRawExecution':
    'Raw command execution is intentionally absent; proposals must resolve through Kernel contracts.',
  'studio.route.agents.approve': 'approve proposal',
  'studio.route.agents.reject': 'reject proposal',
  'studio.route.artifacts.title': 'Artifacts',
  'studio.route.artifacts.description':
    'Manifest-backed artifact evidence with fingerprints, packaging, size, and expected/found state.',
  'studio.route.artifacts.emptyTitle': 'Artifact manifest unavailable',
  'studio.route.artifacts.emptyDescription':
    'Artifact rows are loaded from the Kernel artifact manifest for the selected lifecycle run.',
  'studio.route.develop.title': 'Develop',
  'studio.route.develop.description':
    'ProductUnit shape, surfaces, conformance, and safe lifecycle planning actions.',
  'studio.route.develop.productUnitLabel': 'ProductUnit',
  'studio.route.develop.productShapeTitle': 'Product shape',
  'studio.route.develop.safeActionsTitle': 'Safe actions',
  'studio.route.develop.safeActionNote':
    'Deploy actions stay hidden here until approval, policy, and verification models are wired.',
  'studio.route.develop.surfacesTitle': 'Surfaces',
  'studio.route.develop.surfacesEmpty':
    'ProductUnit surfaces load from the Kernel lifecycle client when Studio is connected.',
  'studio.route.deployments.title': 'Deployments',
  'studio.route.deployments.description':
    'Deployment manifests, environment targets, services, health checks, rollback plan, and verifier results.',
  'studio.route.deployments.localEnvironment': 'Local environment',
  'studio.route.deployments.rollbackPlan': 'Rollback plan',
  'studio.route.deployments.rollbackFallback':
    'Rollback evidence is required before production controls appear.',
  'studio.route.deployments.noProductionButton': 'No production deploy button is rendered.',
  'studio.route.health.title': 'Health',
  'studio.route.health.description':
    'Kernel lifecycle, provider mode, ProductUnit, plugin, and toolchain health using canonical statuses.',
  'studio.route.health.statusTextVisible':
    'Status text is shown directly, not only through color.',
  'studio.route.home.badge': 'Unified Studio',
  'studio.route.home.title': 'Product development journey',
  'studio.route.home.description':
    'Ghatana Studio brings ideation, blueprinting, lifecycle execution, deployments, health, learning, and evolution into one customer-facing workspace.',
  'studio.route.home.pilotTitle': 'Closest executable pilot',
  'studio.route.home.pilotDescription':
    'Digital Marketing is the first lifecycle proof target for validate, build, package, deploy, verify, and health reporting through Kernel.',
  'studio.route.home.providerModeTitle': 'Provider mode',
  'studio.route.home.providerModeDescription':
    'Studio keeps bootstrap mode explicit while platform mode providers are wired through Data Cloud-backed runtime truth, events, artifacts, provenance, memory, and health.',
  'studio.route.home.healthSummaryTitle': 'Health summary',
  'studio.route.home.healthSummaryEmpty':
    'Kernel, Data Cloud, and product health signals appear here once the typed lifecycle client and provider-backed contracts are connected.',
  'studio.route.lifecycle.title': 'Lifecycle',
  'studio.route.lifecycle.description':
    'Run status, gates, artifacts, deployments, verification, approvals, and diagnostics.',
  'studio.route.lifecycle.planTitle': 'Lifecycle plan',
  'studio.route.lifecycle.stepGraphTitle': 'Step graph',
  'studio.route.lifecycle.validationCommandTitle': 'Validation command',
  'studio.route.ideas.title': 'Ideas',
  'studio.route.ideas.description':
    'YAPPC-owned ideation candidates ready for Kernel handoff review.',
  'studio.route.ideas.intentTitle': 'ProductUnitIntent',
  'studio.route.ideas.statusPrefix': 'Status:',
  'studio.route.ideas.statusValue': 'candidate drafted',
  'studio.route.ideas.targetProvidersTitle': 'Target providers',
  'studio.route.ideas.providerStateTitle': 'Provider state',
  'studio.route.ideas.providerState': 'Data Cloud evidence provider degraded',
  'studio.route.ideas.bootstrapHandoff': 'Bootstrap handoff remains available.',
  'studio.route.ideas.sendToKernel': 'Send to Kernel',
  'studio.route.blueprints.title': 'Blueprints',
  'studio.route.blueprints.description':
    'YAPPC blueprint evidence prepared for ProductUnit promotion.',
  'studio.route.blueprints.productShapeTitle': 'Product shape',
  'studio.route.blueprints.readinessPrefix': 'Readiness:',
  'studio.route.blueprints.dependenciesTitle': 'Dependencies',
  'studio.route.blueprints.dependenciesSuffix': 'dependencies',
  'studio.route.blueprints.cyclesSuffix': 'cycles',
  'studio.route.blueprints.generatedChangesTitle': 'Generated changes',
  'studio.route.blueprints.changesSuffix': 'changes',
  'studio.route.canvas.title': 'Canvas',
  'studio.route.canvas.description':
    'Artifact graph and residual review evidence from the YAPPC canvas.',
  'studio.route.canvas.artifactGraphTitle': 'Artifact graph',
  'studio.route.canvas.nodesSuffix': 'nodes',
  'studio.route.canvas.edgesSuffix': 'edges',
  'studio.route.canvas.residualIslandsTitle': 'Residual islands',
  'studio.route.canvas.reviewRequired': 'review required',
  'studio.route.canvas.riskHotspotsTitle': 'Risk hotspots',
  'studio.route.learn.title': 'Learn',
  'studio.route.learn.description':
    'YAPPC recommendations and learning signals from artifact intelligence.',
  'studio.route.learn.recommendationTitle': 'Recommendation',
  'studio.route.learn.learningEvidenceTitle': 'Learning evidence',
  'studio.route.learn.highestRiskPrefix': 'Highest risk:',
} as const satisfies Record<string, string>;

export type StudioTranslationKey = keyof typeof STUDIO_TRANSLATIONS;

export const STUDIO_I18N_RESOURCES = {
  en: {
    studio: STUDIO_TRANSLATIONS,
  },
} as const;

export function useStudioTranslation(): (key: StudioTranslationKey) => string {
  const { t } = useTranslation('studio');

  return (key: StudioTranslationKey): string => {
    const translated = t(key);
    return translated === key ? STUDIO_TRANSLATIONS[key] : translated;
  };
}
