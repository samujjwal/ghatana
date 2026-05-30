/**
 * YAPPC-T02: PHR Contract Status Visualizer
 * 
 * Visualizes route/page/API/policy/test status from the PHR route contract.
 * Provides structured data for rendering coverage gaps and implementation status.
 */

// Use a flexible type for PHR contract to handle actual contract structure
export interface PhrRouteContract {
  product: string;
  version: string;
  schemaVersion?: string;
  routes: PhrRoute[];
}

export interface PhrRoute {
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

export interface PhrContractStatus {
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

/**
 * Parses PHR route contract and generates visualization data structure.
 */
export class PhrContractVisualizer {
  /**
   * Visualizes the PHR contract status.
   */
  visualize(contract: PhrRouteContract): PhrContractStatus {
    const routes = this.analyzeRoutes(contract);
    const summary = this.calculateSummary(routes);

    return {
      contract: {
        product: contract.product,
        version: contract.version,
        schemaVersion: contract.schemaVersion || '1.0.0',
        routeCount: contract.routes.length,
      },
      routes,
      summary,
    };
  }

  private analyzeRoutes(contract: PhrRouteContract): RouteStatus[] {
    return contract.routes.map(route => {
      const pageStatus = this.analyzePageStatus(route);
      const apiStatus = this.analyzeApiStatus(route);
      const policyStatus = this.analyzePolicyStatus(route);
      const testStatus = this.analyzeTestStatus(route);
      const overallStatus = this.determineOverallStatus(pageStatus, apiStatus, policyStatus, testStatus, route.stability);

      return {
        path: route.path,
        label: route.label,
        group: route.group || 'default',
        stability: route.stability,
        minimumRole: route.minimumRole || 'viewer',
        page: pageStatus,
        api: apiStatus,
        policy: policyStatus,
        test: testStatus,
        overallStatus,
      };
    });
  }

  private analyzePageStatus(route: any): PageStatus {
    const surface = route.surface || [];
    const i18nComplete = !!route.i18nKey && !!route.descriptionI18nKey;
    const a11yComplete = !!route.accessibility && Object.keys(route.accessibility).length > 0;

    let status: 'complete' | 'partial' | 'missing';
    if (surface.length === 0) {
      status = 'missing';
    } else if (i18nComplete && a11yComplete) {
      status = 'complete';
    } else {
      status = 'partial';
    }

    return {
      exists: surface.length > 0,
      surface,
      i18nComplete,
      a11yComplete,
      status,
    };
  }

  private analyzeApiStatus(route: any): ApiStatus {
    const endpoint = route.apiEndpoint || route.metadata?.apiEndpoint || null;
    const method = 'GET'; // Default, could be derived from route metadata

    let status: 'complete' | 'partial' | 'missing';
    if (!endpoint) {
      status = 'missing';
    } else {
      status = 'complete';
    }

    return {
      exists: !!endpoint,
      endpoint,
      method,
      status,
    };
  }

  private analyzePolicyStatus(route: any): PolicyStatus {
    const policyId = route.policyId || route.metadata?.policyId || null;

    let status: 'complete' | 'partial' | 'missing';
    if (!policyId) {
      status = 'missing';
    } else {
      status = 'complete';
    }

    return {
      exists: !!policyId,
      policyId,
      status,
    };
  }

  private analyzeTestStatus(route: any): TestStatus {
    const testId = route.testId || route.metadata?.testId || null;

    let coverage: 'full' | 'partial' | 'none';
    let status: 'complete' | 'partial' | 'missing';

    if (!testId) {
      coverage = 'none';
      status = 'missing';
    } else {
      coverage = 'full';
      status = 'complete';
    }

    return {
      exists: !!testId,
      testId,
      coverage,
      status,
    };
  }

  private determineOverallStatus(
    page: PageStatus,
    api: ApiStatus,
    policy: PolicyStatus,
    test: TestStatus,
    stability: string | undefined
  ): 'complete' | 'partial' | 'missing' | 'blocked' {
    if (stability === 'blocked' || stability === 'hidden') {
      return 'blocked';
    }

    const allComplete = page.status === 'complete' && api.status === 'complete' && policy.status === 'complete' && test.status === 'complete';
    const anyMissing = page.status === 'missing' || api.status === 'missing' || policy.status === 'missing' || test.status === 'missing';

    if (allComplete) {
      return 'complete';
    } else if (anyMissing) {
      return 'missing';
    } else {
      return 'partial';
    }
  }

  private calculateSummary(routes: RouteStatus[]): StatusSummary {
    const totalRoutes = routes.length;
    const completeRoutes = routes.filter(r => r.overallStatus === 'complete').length;
    const partialRoutes = routes.filter(r => r.overallStatus === 'partial').length;
    const missingRoutes = routes.filter(r => r.overallStatus === 'missing').length;
    const blockedRoutes = routes.filter(r => r.overallStatus === 'blocked').length;

    const pageComplete = routes.filter(r => r.page.status === 'complete').length;
    const apiComplete = routes.filter(r => r.api.status === 'complete').length;
    const policyComplete = routes.filter(r => r.policy.status === 'complete').length;
    const testComplete = routes.filter(r => r.test.status === 'complete').length;

    return {
      totalRoutes,
      completeRoutes,
      partialRoutes,
      missingRoutes,
      blockedRoutes,
      pageCoverage: totalRoutes > 0 ? (pageComplete / totalRoutes) * 100 : 0,
      apiCoverage: totalRoutes > 0 ? (apiComplete / totalRoutes) * 100 : 0,
      policyCoverage: totalRoutes > 0 ? (policyComplete / totalRoutes) * 100 : 0,
      testCoverage: totalRoutes > 0 ? (testComplete / totalRoutes) * 100 : 0,
    };
  }
}

/**
 * Creates a PHR contract visualizer instance.
 */
export function createPhrContractVisualizer(): PhrContractVisualizer {
  return new PhrContractVisualizer();
}

/**
 * Visualizes PHR contract status from a contract object.
 */
export function visualizePhrContract(contract: PhrRouteContract): PhrContractStatus {
  return createPhrContractVisualizer().visualize(contract);
}
