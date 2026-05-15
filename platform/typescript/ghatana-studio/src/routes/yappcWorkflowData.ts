import type {
  ArtifactGraphSummary,
  DependencyGraphEvidence,
  GeneratedChangeSetSummary,
  ProductShapeEvidence,
  ProductUnitIntent,
  ResidualIslandReport,
  RiskHotspotReport,
  SemanticArtifactReference,
} from '@ghatana/kernel-product-contracts';

const createdAt = '2026-05-14T00:00:00.000Z';

export const yappcProductUnitIntentCandidate: ProductUnitIntent = {
  schemaVersion: '1.0.0',
  intentId: 'intent:yappc:commerce-studio:corr-yappc-1',
  intentType: 'promote-candidate',
  scope: {
    tenantId: 'tenant-demo',
    workspaceId: 'workspace-commerce',
    projectId: 'commerce-studio',
  },
  producer: {
    id: 'yappc-creator-ui',
    type: 'yappc',
    correlationId: 'corr-yappc-1',
  },
  target: {
    registryProvider: 'kernel-product-registry',
    sourceProvider: 'yappc-creator',
  },
  productUnit: {
    id: 'commerce-studio',
    name: 'Commerce Studio',
    kind: 'business-product',
    surfaces: [
      {
        id: 'commerce-studio-web',
        type: 'web',
        implementationStatus: 'experimental',
        sourceRef: 'yappc:artifact:commerce-page',
      },
    ],
  },
  requestedLifecycle: {
    profile: 'standard-web-product',
    enableExecution: false,
    phases: ['validate', 'build'],
  },
  provenance: {
    sourceSystem: 'yappc',
    sourceArtifactRefs: ['yappc:artifact:commerce-page'],
    createdBy: 'user:builder',
    createdAt,
    evidenceRefs: ['evidence:semantic-commerce-page'],
  },
};

export const semanticArtifactReferences: readonly SemanticArtifactReference[] = [
  {
    schemaVersion: '1.0.0',
    evidenceId: 'evidence:semantic-commerce-page',
    evidenceType: 'semantic-artifact-reference',
    source: 'yappc-creator-ui',
    confidence: 0.91,
    provenanceRefs: ['prov:yappc:scan-commerce'],
    createdAt,
    correlationId: 'corr-yappc-1',
    productUnitId: 'commerce-studio',
    artifactId: 'route-commerce-home',
    artifactKind: 'ui-route',
    displayName: 'Commerce home route',
    artifactRef: 'yappc:artifact:commerce-page',
    path: 'src/routes/CommerceHome.tsx',
    semanticTags: ['checkout', 'catalog'],
    riskLevel: 'low',
  },
];

export const artifactGraphSummary: ArtifactGraphSummary = {
  schemaVersion: '1.0.0',
  evidenceId: 'evidence:graph-commerce',
  evidenceType: 'artifact-graph-summary',
  source: 'yappc-creator-ui',
  confidence: 0.88,
  provenanceRefs: ['prov:yappc:scan-commerce'],
  createdAt,
  correlationId: 'corr-yappc-1',
  productUnitId: 'commerce-studio',
  nodeCount: 4,
  edgeCount: 3,
  nodes: [
    { artifactId: 'commerce-studio', artifactKind: 'service', label: 'Commerce Studio' },
    { artifactId: 'route-commerce-home', artifactKind: 'ui-route', label: 'Commerce home route' },
    { artifactId: 'component-cart-summary', artifactKind: 'source-file', label: 'Cart summary' },
    { artifactId: 'legacy-promo-widget', artifactKind: 'configuration', label: 'Legacy promo widget' },
  ],
  edges: [
    { fromArtifactId: 'commerce-studio', toArtifactId: 'route-commerce-home', relationship: 'contains' },
    { fromArtifactId: 'route-commerce-home', toArtifactId: 'component-cart-summary', relationship: 'contains' },
    { fromArtifactId: 'legacy-promo-widget', toArtifactId: 'route-commerce-home', relationship: 'residual-of' },
  ],
  rootArtifactIds: ['commerce-studio', 'route-commerce-home'],
  orphanArtifactIds: ['legacy-promo-widget'],
};

export const productShapeEvidence: ProductShapeEvidence = {
  schemaVersion: '1.0.0',
  evidenceId: 'evidence:shape-commerce',
  evidenceType: 'product-shape-evidence',
  source: 'yappc-creator-ui',
  confidence: 0.86,
  provenanceRefs: ['prov:yappc:scan-commerce'],
  createdAt,
  correlationId: 'corr-yappc-1',
  productUnitId: 'commerce-studio',
  shapeKind: 'web-app',
  lifecycleReadiness: 'partial',
  detectedSurfaces: ['web'],
  requiredAdapters: ['pnpm-vite-react'],
  missingEvidenceRefs: ['deployment-manifest'],
};

export const dependencyGraphEvidence: DependencyGraphEvidence = {
  schemaVersion: '1.0.0',
  evidenceId: 'evidence:dependency-commerce',
  evidenceType: 'dependency-graph-evidence',
  source: 'yappc-creator-ui',
  confidence: 0.84,
  provenanceRefs: ['prov:yappc:scan-commerce'],
  createdAt,
  correlationId: 'corr-yappc-1',
  productUnitId: 'commerce-studio',
  dependencyCount: 12,
  internalDependencyCount: 5,
  externalDependencyCount: 7,
  cycleCount: 0,
  cycleRefs: [],
  vulnerableDependencyRefs: [],
};

export const residualIslandReport: ResidualIslandReport = {
  schemaVersion: '1.0.0',
  evidenceId: 'evidence:residual-commerce',
  evidenceType: 'residual-island-report',
  source: 'yappc-creator-ui',
  confidence: 0.82,
  provenanceRefs: ['prov:yappc:scan-commerce'],
  createdAt,
  correlationId: 'corr-yappc-1',
  productUnitId: 'commerce-studio',
  islandCount: 1,
  residualArtifactRefs: ['legacy-promo-widget'],
  recommendedActions: ['Promote legacy-promo-widget to a reviewed registry candidate before lifecycle promotion'],
};

export const riskHotspotReport: RiskHotspotReport = {
  schemaVersion: '1.0.0',
  evidenceId: 'evidence:risk-commerce',
  evidenceType: 'risk-hotspot-report',
  source: 'yappc-creator-ui',
  confidence: 0.79,
  provenanceRefs: ['prov:yappc:scan-commerce'],
  createdAt,
  correlationId: 'corr-yappc-1',
  productUnitId: 'commerce-studio',
  hotspotCount: 1,
  highestRiskLevel: 'high',
  hotspots: [
    {
      artifactId: 'legacy-promo-widget',
      riskLevel: 'high',
      reason: 'Residual island still needs operator review',
      evidenceRefs: ['evidence:residual-commerce'],
    },
  ],
};

export const generatedChangeSetSummary: GeneratedChangeSetSummary = {
  schemaVersion: '1.0.0',
  evidenceId: 'evidence:changes-commerce',
  evidenceType: 'generated-change-set-summary',
  source: 'yappc-creator-ui',
  confidence: 0.87,
  provenanceRefs: ['prov:yappc:scan-commerce'],
  createdAt,
  correlationId: 'corr-yappc-1',
  productUnitId: 'commerce-studio',
  changeSetId: 'changeset:commerce-studio:1',
  changeCount: 3,
  affectedArtifactRefs: ['route-commerce-home', 'component-cart-summary'],
  generatedArtifactRefs: ['evidence:graph-commerce'],
  validationEvidenceRefs: ['evidence:residual-commerce', 'evidence:risk-commerce'],
};
