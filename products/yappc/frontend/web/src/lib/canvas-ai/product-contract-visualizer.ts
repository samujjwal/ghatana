export interface ProductRouteContract {
  product: string;
  version: string;
  schemaVersion?: string;
  routes: ProductRoute[];
}

export interface ProductRoute {
  path: string;
  label: string;
  description?: string;
  group?: string;
  stability?: string;
  minimumRole?: string;
  surface?: string[];
  i18nKey?: string;
  descriptionI18nKey?: string;
  accessibility?: Record<string, boolean>;
  apiEndpoint?: string;
  policyId?: string;
  testId?: string;
  metadata?: Record<string, unknown>;
}

export interface ProductContractStatus {
  contract: {
    product: string;
    version: string;
    schemaVersion: string;
    routeCount: number;
  };
  routes: RouteStatus[];
  summary: StatusSummary;
}

export interface RouteStatus {
  path: string;
  label: string;
  group: string;
  stability: string | undefined;
  minimumRole: string;
  page: PageStatus;
  api: ApiStatus;
  policy: PolicyStatus;
  test: TestStatus;
  overallStatus: 'complete' | 'partial' | 'missing' | 'blocked';
}

export interface PageStatus {
  exists: boolean;
  surface: string[];
  i18nComplete: boolean;
  a11yComplete: boolean;
  status: 'complete' | 'partial' | 'missing';
}

export interface ApiStatus {
  exists: boolean;
  endpoint: string | null;
  method: string;
  status: 'complete' | 'partial' | 'missing';
}

export interface PolicyStatus {
  exists: boolean;
  policyId: string | null;
  status: 'complete' | 'partial' | 'missing';
}

export interface TestStatus {
  exists: boolean;
  testId: string | null;
  coverage: 'full' | 'partial' | 'none';
  status: 'complete' | 'partial' | 'missing';
}

export interface StatusSummary {
  totalRoutes: number;
  completeRoutes: number;
  partialRoutes: number;
  missingRoutes: number;
  blockedRoutes: number;
  pageCoverage: number;
  apiCoverage: number;
  policyCoverage: number;
  testCoverage: number;
}

export class ProductContractVisualizer {
  visualize(contract: ProductRouteContract): ProductContractStatus {
    const routes = this.analyzeRoutes(contract);
    const summary = this.calculateSummary(routes);

    return {
      contract: {
        product: contract.product,
        version: contract.version,
        schemaVersion: contract.schemaVersion ?? '1.0.0',
        routeCount: contract.routes.length,
      },
      routes,
      summary,
    };
  }

  private analyzeRoutes(contract: ProductRouteContract): RouteStatus[] {
    return contract.routes.map((route: ProductRoute): RouteStatus => {
      const pageStatus = this.analyzePageStatus(route);
      const apiStatus = this.analyzeApiStatus(route);
      const policyStatus = this.analyzePolicyStatus(route);
      const testStatus = this.analyzeTestStatus(route);
      const overallStatus = this.determineOverallStatus(pageStatus, apiStatus, policyStatus, testStatus, route.stability);

      return {
        path: route.path,
        label: route.label,
        group: route.group ?? 'default',
        stability: route.stability,
        minimumRole: route.minimumRole ?? 'viewer',
        page: pageStatus,
        api: apiStatus,
        policy: policyStatus,
        test: testStatus,
        overallStatus,
      };
    });
  }

  private analyzePageStatus(route: ProductRoute): PageStatus {
    const surface = route.surface ?? [];
    const i18nComplete = Boolean(route.i18nKey) && Boolean(route.descriptionI18nKey);
    const a11yComplete = route.accessibility !== undefined && Object.keys(route.accessibility).length > 0;
    const status = this.pageStatus(surface, i18nComplete, a11yComplete);

    return {
      exists: surface.length > 0,
      surface,
      i18nComplete,
      a11yComplete,
      status,
    };
  }

  private pageStatus(surface: string[], i18nComplete: boolean, a11yComplete: boolean): PageStatus['status'] {
    if (surface.length === 0) {
      return 'missing';
    }
    if (i18nComplete && a11yComplete) {
      return 'complete';
    }
    return 'partial';
  }

  private analyzeApiStatus(route: ProductRoute): ApiStatus {
    const endpoint = metadataString(route, 'apiEndpoint', route.apiEndpoint);

    return {
      exists: endpoint !== null,
      endpoint,
      method: 'GET',
      status: endpoint === null ? 'missing' : 'complete',
    };
  }

  private analyzePolicyStatus(route: ProductRoute): PolicyStatus {
    const policyId = metadataString(route, 'policyId', route.policyId);

    return {
      exists: policyId !== null,
      policyId,
      status: policyId === null ? 'missing' : 'complete',
    };
  }

  private analyzeTestStatus(route: ProductRoute): TestStatus {
    const testId = metadataString(route, 'testId', route.testId);

    return {
      exists: testId !== null,
      testId,
      coverage: testId === null ? 'none' : 'full',
      status: testId === null ? 'missing' : 'complete',
    };
  }

  private determineOverallStatus(
    page: PageStatus,
    api: ApiStatus,
    policy: PolicyStatus,
    test: TestStatus,
    stability: string | undefined,
  ): RouteStatus['overallStatus'] {
    if (stability === 'blocked' || stability === 'hidden') {
      return 'blocked';
    }

    const completeCount = [page.status, api.status, policy.status, test.status]
      .filter((status: PageStatus['status'] | ApiStatus['status'] | PolicyStatus['status'] | TestStatus['status']): boolean => status === 'complete')
      .length;

    if (completeCount === 4) {
      return 'complete';
    }
    if (completeCount === 0) {
      return 'missing';
    }
    return 'partial';
  }

  private calculateSummary(routes: RouteStatus[]): StatusSummary {
    const totalRoutes = routes.length;
    const completeRoutes = routes.filter((route: RouteStatus): boolean => route.overallStatus === 'complete').length;
    const partialRoutes = routes.filter((route: RouteStatus): boolean => route.overallStatus === 'partial').length;
    const missingRoutes = routes.filter((route: RouteStatus): boolean => route.overallStatus === 'missing').length;
    const blockedRoutes = routes.filter((route: RouteStatus): boolean => route.overallStatus === 'blocked').length;
    const pageComplete = routes.filter((route: RouteStatus): boolean => route.page.exists).length;
    const apiComplete = routes.filter((route: RouteStatus): boolean => route.api.status === 'complete').length;
    const policyComplete = routes.filter((route: RouteStatus): boolean => route.policy.status === 'complete').length;
    const testComplete = routes.filter((route: RouteStatus): boolean => route.test.status === 'complete').length;

    return {
      totalRoutes,
      completeRoutes,
      partialRoutes,
      missingRoutes,
      blockedRoutes,
      pageCoverage: percentage(pageComplete, totalRoutes),
      apiCoverage: percentage(apiComplete, totalRoutes),
      policyCoverage: percentage(policyComplete, totalRoutes),
      testCoverage: percentage(testComplete, totalRoutes),
    };
  }
}

function metadataString(route: ProductRoute, key: string, directValue: string | undefined): string | null {
  if (directValue !== undefined && directValue.trim().length > 0) {
    return directValue;
  }
  const value = route.metadata?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

function percentage(value: number, total: number): number {
  return total > 0 ? (value / total) * 100 : 0;
}

export function createProductContractVisualizer(): ProductContractVisualizer {
  return new ProductContractVisualizer();
}

export function visualizeProductContract(contract: ProductRouteContract): ProductContractStatus {
  return createProductContractVisualizer().visualize(contract);
}
