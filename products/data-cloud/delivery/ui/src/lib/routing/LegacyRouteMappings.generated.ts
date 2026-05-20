// Auto-generated from route-compatibility-registry.yaml by generate-route-security-metadata.mjs
// DC-P1-07: Legacy route compatibility metadata - do not edit manually

export interface LegacyRouteMapping {
  path: string;
  canonical: string;
  methods: string[];
  deprecatedSince: string;
  retirementTarget: string;
  featureFlag: string;
}

export const legacyRouteMappings: LegacyRouteMapping[] = [
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['GET'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['GET'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['POST'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['POST'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['POST'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['POST'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['GET'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/executions/',
    canonical: '/api/v1/action/executions/',
    methods: ['POST'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
  {
    path: '/api/v1/queries/explain',
    canonical: '/api/v1/analytics/explain',
    methods: ['POST'],
    deprecatedSince: '2026-03-27',
    retirementTarget: '2026-12-31',
    featureFlag: 'DataCloudFeature.LEGACY_ACTION_ROUTES',
  },
];