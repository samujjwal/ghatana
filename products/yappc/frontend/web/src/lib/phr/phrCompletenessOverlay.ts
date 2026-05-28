import { z } from 'zod';

const RouteLifecycleSchema = z.enum(['stable', 'hidden', 'blocked', 'preview']);
const UseCaseStatusSchema = z.enum([
  'implemented',
  'partial',
  'feature_flagged',
  'backend_only',
  'ui_only',
  'missing',
  'deferred',
  'removed',
]);
const OptionalStringSchema = z.string().nullable().optional().transform((value) => value ?? undefined);

const PhrRouteSchema = z.object({
  path: z.string().min(1),
  label: z.string().min(1),
  group: z.string().min(1),
  stability: RouteLifecycleSchema,
  actions: z.array(z.string()).default([]),
  cards: z.array(z.string()).default([]),
  apiEndpoint: z.string().optional(),
  policyId: z.string().optional(),
  testId: z.string().optional(),
});

const PhrRouteContractSchema = z.object({
  product: z.literal('phr'),
  routes: z.array(PhrRouteSchema),
});

const PhrUseCaseSchema = z.object({
  id: z.string().min(1),
  persona: z.string().min(1),
  iaRoute: z.string().min(1),
  webRoute: OptionalStringSchema,
  mobileScreen: OptionalStringSchema,
  backendApis: z.array(z.string()).default([]),
  status: UseCaseStatusSchema,
});

const PhrUseCaseBaselineSchema = z.object({
  product: z.literal('phr'),
  usecases: z.array(PhrUseCaseSchema),
});

export type PhrRouteLifecycle = z.infer<typeof RouteLifecycleSchema>;
export type PhrUseCaseStatus = z.infer<typeof UseCaseStatusSchema>;

export interface PhrCompletenessRouteRow {
  path: string;
  label: string;
  group: string;
  lifecycle: PhrRouteLifecycle;
  useCaseIds: string[];
  webCovered: boolean;
  mobileCovered: boolean;
  backendCovered: boolean;
  testCovered: boolean;
  directLinkAllowed: boolean;
  score: number;
}

export interface PhrCompletenessGap {
  routePath: string;
  category: 'web' | 'mobile' | 'backend' | 'test' | 'route-state';
  message: string;
}

export interface PhrCompletenessOverlayModel {
  product: 'phr';
  generatedAt: string;
  totals: {
    routes: number;
    stableRoutes: number;
    hiddenRoutes: number;
    blockedRoutes: number;
    previewRoutes: number;
    stableCoveragePercent: number;
    gapCount: number;
  };
  routes: PhrCompletenessRouteRow[];
  gaps: PhrCompletenessGap[];
}

type PhrRoute = z.infer<typeof PhrRouteSchema>;
type PhrUseCase = z.infer<typeof PhrUseCaseSchema>;

const IMPLEMENTED_STATUSES: ReadonlySet<PhrUseCaseStatus> = new Set([
  'implemented',
  'partial',
  'feature_flagged',
  'backend_only',
  'ui_only',
]);

function isStableCovered(useCase: PhrUseCase): boolean {
  return IMPLEMENTED_STATUSES.has(useCase.status);
}

function routeUseCases(route: PhrRoute, useCases: PhrUseCase[]): PhrUseCase[] {
  return useCases.filter((useCase) => useCase.iaRoute === route.path || useCase.webRoute === route.path);
}

function computeScore(row: Omit<PhrCompletenessRouteRow, 'score'>): number {
  if (row.lifecycle !== 'stable') {
    return row.directLinkAllowed ? 0 : 100;
  }

  const checks = [row.webCovered, row.mobileCovered, row.backendCovered, row.testCovered];
  return Math.round((checks.filter(Boolean).length / checks.length) * 100);
}

function buildRouteGaps(row: PhrCompletenessRouteRow): PhrCompletenessGap[] {
  if (row.lifecycle === 'hidden' || row.lifecycle === 'blocked') {
    return row.directLinkAllowed
      ? [{
          routePath: row.path,
          category: 'route-state',
          message: `${row.lifecycle} route must not allow direct-link access`,
        }]
      : [];
  }

  if (row.lifecycle !== 'stable') {
    return [];
  }

  const gaps: PhrCompletenessGap[] = [];
  if (!row.webCovered) {
    gaps.push({ routePath: row.path, category: 'web', message: 'stable route has no web use-case coverage' });
  }
  if (!row.mobileCovered) {
    gaps.push({ routePath: row.path, category: 'mobile', message: 'stable route has no mobile use-case coverage' });
  }
  if (!row.backendCovered) {
    gaps.push({ routePath: row.path, category: 'backend', message: 'stable route has no backend API coverage' });
  }
  if (!row.testCovered) {
    gaps.push({ routePath: row.path, category: 'test', message: 'stable route has no route contract test id' });
  }
  return gaps;
}

export function buildPhrCompletenessOverlay(
  routeContractInput: unknown,
  useCaseBaselineInput: unknown,
  generatedAt = new Date().toISOString(),
): PhrCompletenessOverlayModel {
  const routeContract = PhrRouteContractSchema.parse(routeContractInput);
  const useCaseBaseline = PhrUseCaseBaselineSchema.parse(useCaseBaselineInput);

  const routes = routeContract.routes.map((route): PhrCompletenessRouteRow => {
    const relatedUseCases = routeUseCases(route, useCaseBaseline.usecases);
    const coveredUseCases = relatedUseCases.filter(isStableCovered);
    const contractOnlyGovernanceRoute = route.group === 'governance'
      && route.actions.length === 0
      && route.cards.length === 0
      && relatedUseCases.length === 0;
    const directLinkAllowed = route.stability === 'stable' || route.stability === 'preview';
    const rowWithoutScore: Omit<PhrCompletenessRouteRow, 'score'> = {
      path: route.path,
      label: route.label,
      group: route.group,
      lifecycle: route.stability,
      useCaseIds: relatedUseCases.map((useCase) => useCase.id),
      webCovered: contractOnlyGovernanceRoute || coveredUseCases.some((useCase) => useCase.webRoute === route.path),
      mobileCovered: contractOnlyGovernanceRoute || coveredUseCases.some((useCase) => Boolean(useCase.mobileScreen)),
      backendCovered: Boolean(route.apiEndpoint)
        && (contractOnlyGovernanceRoute || coveredUseCases.some((useCase) => useCase.backendApis.length > 0)),
      testCovered: Boolean(route.testId),
      directLinkAllowed,
    };

    return {
      ...rowWithoutScore,
      score: computeScore(rowWithoutScore),
    };
  });

  const gaps = routes.flatMap(buildRouteGaps);
  const stableRows = routes.filter((route) => route.lifecycle === 'stable');
  const stableCoveragePercent = stableRows.length === 0
    ? 100
    : Math.round(stableRows.reduce((sum, route) => sum + route.score, 0) / stableRows.length);

  return {
    product: 'phr',
    generatedAt,
    totals: {
      routes: routes.length,
      stableRoutes: stableRows.length,
      hiddenRoutes: routes.filter((route) => route.lifecycle === 'hidden').length,
      blockedRoutes: routes.filter((route) => route.lifecycle === 'blocked').length,
      previewRoutes: routes.filter((route) => route.lifecycle === 'preview').length,
      stableCoveragePercent,
      gapCount: gaps.length,
    },
    routes,
    gaps,
  };
}
