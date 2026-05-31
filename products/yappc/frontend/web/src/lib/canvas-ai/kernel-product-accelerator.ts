export type RouteStability = 'stable' | 'hidden' | 'blocked' | 'deferred' | 'removed' | 'preview' | string;

export interface KernelProductRoute {
  readonly path: string;
  readonly label: string;
  readonly stability?: RouteStability;
  readonly surface?: readonly string[];
  readonly apiEndpoint?: string;
  readonly policyId?: string;
  readonly i18nKey?: string;
  readonly descriptionI18nKey?: string;
  readonly metadata?: Record<string, unknown>;
}

export interface KernelProductContract {
  readonly product: string;
  readonly version: string;
  readonly routes: readonly KernelProductRoute[];
  readonly pluginDependencies?: readonly string[];
}

export interface ProductValidationResult {
  readonly checkId: string;
  readonly pass: boolean;
  readonly violations?: readonly string[];
}

export interface LifecycleStagePlan {
  readonly stage:
    | 'ideation'
    | 'requirements'
    | 'design'
    | 'contract'
    | 'scaffold'
    | 'implementation'
    | 'validation'
    | 'deployment'
    | 'enhancement';
  readonly inputs: readonly string[];
  readonly outputs: readonly string[];
}

export interface ProductContractGap {
  readonly category:
    | 'route-missing-screen'
    | 'screen-missing-api'
    | 'api-missing-policy'
    | 'hidden-route-mounted'
    | 'mobile-route-missing-screen'
    | 'raw-i18n'
    | 'unsafe-storage';
  readonly routePath: string;
  readonly message: string;
}

export interface DeploymentPlanStep {
  readonly id: string;
  readonly description: string;
  readonly requiredPluginIds: readonly string[];
}

export interface EnhancementProposal {
  readonly sourceCheckId: string;
  readonly priority: 'high' | 'medium' | 'low';
  readonly title: string;
  readonly nextAction: string;
}

export function createKernelProductLifecyclePlan(contract: KernelProductContract): readonly LifecycleStagePlan[] {
  return [
    stage('ideation', ['product intent'], ['problem framing']),
    stage('requirements', ['problem framing'], ['personas', 'workflows', 'capabilities']),
    stage('design', ['personas', 'workflows'], ['screens', 'accessibility requirements']),
    stage('contract', ['routes', 'screens', 'APIs', 'policies'], [`${contract.product}@${contract.version}`]),
    stage('scaffold', ['Kernel product contract'], ['route skeletons', 'API clients', 'test shells']),
    stage('implementation', ['scaffolded artifacts'], ['product-owned code']),
    stage('validation', ['product-owned code'], ['Kernel validation results']),
    stage('deployment', ['Kernel validation results'], ['deployment plan']),
    stage('enhancement', ['validation results', 'runtime feedback'], ['generic fix-forward proposals']),
  ];
}

export function analyzeKernelProductGaps(contract: KernelProductContract): readonly ProductContractGap[] {
  return contract.routes.flatMap((route: KernelProductRoute): readonly ProductContractGap[] => {
    const gaps: ProductContractGap[] = [];
    const surface = route.surface ?? [];
    const apiEndpoint = metadataString(route, 'apiEndpoint', route.apiEndpoint);
    const policyId = metadataString(route, 'policyId', route.policyId);
    const screenKey = metadataString(route, 'screenKey');
    const cachePolicy = metadataString(route, 'cachePolicy');

    if (route.stability === 'stable' && surface.length === 0) {
      gaps.push(gap('route-missing-screen', route, 'Stable route has no screen surface.'));
    }
    if (surface.length > 0 && apiEndpoint === null) {
      gaps.push(gap('screen-missing-api', route, 'Surfaced route has no API endpoint.'));
    }
    if (apiEndpoint !== null && policyId === null) {
      gaps.push(gap('api-missing-policy', route, 'API-backed route has no policy id.'));
    }
    if (route.stability === 'hidden' && apiEndpoint !== null) {
      gaps.push(gap('hidden-route-mounted', route, 'Hidden route declares a live API endpoint.'));
    }
    if (surface.includes('mobile') && screenKey === null) {
      gaps.push(gap('mobile-route-missing-screen', route, 'Mobile route has no screen key metadata.'));
    }
    if (surface.length > 0 && (isBlank(route.i18nKey) || isBlank(route.descriptionI18nKey))) {
      gaps.push(gap('raw-i18n', route, 'Surfaced route is missing localization keys.'));
    }
    if (cachePolicy === 'unsafe' || cachePolicy === 'phi-unrestricted') {
      gaps.push(gap('unsafe-storage', route, 'Route declares an unsafe cache policy.'));
    }

    return gaps;
  });
}

export function createDeploymentPlan(contract: KernelProductContract): readonly DeploymentPlanStep[] {
  return [
    {
      id: 'validate-contract',
      description: `Validate ${contract.product} Kernel product contract before deployment.`,
      requiredPluginIds: ['route', 'policy'],
    },
    {
      id: 'verify-plugin-dependencies',
      description: 'Verify declared Kernel plugin dependencies are installed and healthy.',
      requiredPluginIds: contract.pluginDependencies ?? [],
    },
    {
      id: 'promote-with-rollback',
      description: 'Deploy with health checks, telemetry, and rollback readiness.',
      requiredPluginIds: ['observability'],
    },
  ];
}

export function proposeEnhancements(validationResults: readonly ProductValidationResult[]): readonly EnhancementProposal[] {
  return validationResults
    .filter((result: ProductValidationResult): boolean => !result.pass)
    .map((result: ProductValidationResult): EnhancementProposal => ({
      sourceCheckId: result.checkId,
      priority: (result.violations?.length ?? 0) > 3 ? 'high' : 'medium',
      title: `Resolve failing Kernel validation: ${result.checkId}`,
      nextAction: (result.violations?.[0] ?? 'Inspect validation output and create a product-owned fix-forward task.'),
    }));
}

function stage(stageName: LifecycleStagePlan['stage'], inputs: readonly string[], outputs: readonly string[]): LifecycleStagePlan {
  return { stage: stageName, inputs, outputs };
}

function gap(category: ProductContractGap['category'], route: KernelProductRoute, message: string): ProductContractGap {
  return { category, routePath: route.path, message };
}

function metadataString(route: KernelProductRoute, key: string, directValue?: string): string | null {
  if (directValue !== undefined && directValue.trim().length > 0) {
    return directValue;
  }
  const value = route.metadata?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

function isBlank(value: string | undefined): boolean {
  return value === undefined || value.trim().length === 0;
}
