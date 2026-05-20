import type { ReactElement } from 'react';
import { Badge } from '@ghatana/design-system';
import { HybridCanvas } from '@ghatana/canvas/hybrid';
import { createBuilderDocument } from '@ghatana/ui-builder';
import type { BuilderDocument, ComponentInstance } from '@ghatana/ui-builder';
import type { CanvasNode, CanvasEdge } from '@ghatana/canvas/hybrid';
import { insertNode } from '@ghatana/ui-builder';
import { getStudioCapabilityState } from '../api/kernelLifecycleClient';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import type { StudioTranslationKey } from '../i18n/studioTranslations';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { artifactGraphSummary, residualIslandReport, riskHotspotReport, semanticArtifactReferences } from './yappcWorkflowData';
import IdeationRouteStatusPanel from './IdeationRouteStatusPanel';

type TranslateFn = (key: StudioTranslationKey) => string;

function canvasRiskLevelLabel(riskLevel: string, t: TranslateFn): string {
  if (riskLevel === 'critical' || riskLevel === 'high' || riskLevel === 'medium' || riskLevel === 'low') {
    return t(`studio.route.canvas.riskLevel.${riskLevel}`);
  }
  return t('studio.route.canvas.riskLevel.unknown');
}

/**
 * Convert artifact graph data to BuilderDocument for canvas visualization.
 * Creates nodes for artifacts and edges for relationships using canonical BuilderDocument.
 */
function createArtifactCanvasDocument(): BuilderDocument {
  // Create a canonical BuilderDocument using the factory function
  const document = createBuilderDocument('canvas-workspace', {
    designSystemId: 'default-design-system',
    designSystemName: 'Default Design System',
  });

  // Insert artifact nodes using canonical operations
  let updatedDoc = document;
  semanticArtifactReferences.forEach((artifact) => {
    updatedDoc = insertNode(updatedDoc, {
      contractName: 'ArtifactNode',
      props: {
        displayName: artifact.displayName,
        artifactKind: artifact.artifactKind,
        riskLevel: artifact.riskLevel,
        confidence: artifact.confidence,
        semanticTags: artifact.semanticTags,
      },
      slots: {},
      bindings: [],
      metadata: {
        name: artifact.displayName,
        locked: false,
        hidden: false,
      },
    });
  });

  return updatedDoc;
}

export default function CanvasPage(): ReactElement {
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const capabilityState = getStudioCapabilityState({
    runtimeConfigured: lifecycleData.authenticatedUserId !== undefined,
    lifecycleStatus: lifecycleData.snapshot.status,
    selectedProviderMode: lifecycleData.selectedProviderMode,
    productUnit: lifecycleData.snapshot.productUnit,
    selectedRun: lifecycleData.snapshot.selectedRun,
    manifestLoadState: lifecycleData.snapshot.manifestLoadState,
  });
  const handoffReady = lifecycleData.previewProductUnitIntent !== undefined && lifecycleData.applyProductUnitIntent !== undefined;
  const currentStatusLabel = capabilityState.runtimeConfigured
    ? capabilityState.lifecycleConfigured
      ? capabilityState.dataCloudEvidenceReady
        ? t('studio.route.canvas.status.ready')
        : t('studio.route.canvas.status.evidencePending')
      : t('studio.route.canvas.status.lifecycleNotConfigured')
    : t('studio.route.canvas.status.runtimeNotConfigured');
  const requiredNextActionLabel = handoffReady
    ? capabilityState.dataCloudEvidenceReady
      ? t('studio.route.canvas.action.exportCandidates')
      : t('studio.route.canvas.action.completeEvidence')
    : t('studio.route.canvas.action.configureHandoff');

  // Create canvas document from artifact data
  const canvasDocument = createArtifactCanvasDocument();

  // Convert BuilderDocument to canvas format using deterministic grid layout.
  // Positions are computed from node index so they are stable across renders.
  const nodeEntries = Object.values(canvasDocument.nodes) as ComponentInstance[];
  const GRID_COLUMNS = 4;
  const CELL_WIDTH = 220;
  const CELL_HEIGHT = 120;
  const MARGIN_X = 40;
  const MARGIN_Y = 40;

  const canvasNodes: CanvasNode<Record<string, unknown>>[] = nodeEntries.map((node: ComponentInstance, index: number) => {
    const col = index % GRID_COLUMNS;
    const row = Math.floor(index / GRID_COLUMNS);
    return {
      id: node.id,
      type: 'artifactNode',
      position: {
        x: MARGIN_X + col * CELL_WIDTH,
        y: MARGIN_Y + row * CELL_HEIGHT,
      },
      data: {
        contractName: node.contractName,
        props: node.props,
        metadata: node.metadata,
      },
    };
  });
  const canvasEdges: CanvasEdge<Record<string, unknown>>[] = [];

  const getConfidenceTone = (confidence: number): 'success' | 'warning' | 'danger' => {
    if (confidence >= 0.9) return 'success';
    if (confidence >= 0.7) return 'warning';
    return 'danger';
  };

  return (
    <section className="space-y-6" aria-labelledby="canvas-title">
      <div className="space-y-2">
        <h2 id="canvas-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.canvas.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">{t('studio.route.canvas.description')}</p>
      </div>

      <IdeationRouteStatusPanel
        ownershipLabel={t('studio.route.canvas.ownershipLabel.yappc')}
        currentStatusLabel={currentStatusLabel}
        requiredNextActionLabel={requiredNextActionLabel}
        handoffReady={handoffReady}
        handoffReadinessLabel={handoffReady ? t('studio.route.canvas.handoffStatus.ready') : t('studio.route.canvas.handoffStatus.unavailable')}
        evidenceRefs={[artifactGraphSummary.evidenceId, residualIslandReport.evidenceId, riskHotspotReport.evidenceId]}
      />

      {/* Live Canvas Workspace */}
      <div className="border rounded-lg overflow-hidden">
        <div className="bg-gray-100 p-2 text-sm text-gray-600 flex items-center justify-between">
          <span>Artifact Graph Canvas</span>
          <Badge tone="success" variant="soft" className="text-xs">Live</Badge>
        </div>
        <HybridCanvas
          nodes={canvasNodes}
          edges={canvasEdges}
          mode="hybrid-graph"
          width="100%"
          height="600px"
          readOnly={false}
          onElementsChange={() => {}}
          onNodesChange={() => {}}
          onEdgesChange={() => {}}
          onSelectionChange={() => {}}
          onViewportChange={() => {}}
          onModeChange={() => {}}
          onConnect={() => {}}
        />
      </div>

      {/* Evidence/Risk Overlays */}
      <div className="grid gap-4 lg:grid-cols-2">
        {/* Risk Hotspots */}
        <article className="studio-card space-y-3 border-red-200 bg-red-50" aria-labelledby="risk-hotspots-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="risk-hotspots-title" className="text-base font-semibold text-red-900">
              {t('studio.route.canvas.riskHotspotsTitle')}
            </h3>
            <Badge tone={getConfidenceTone(riskHotspotReport.confidence)} variant="soft" className="text-xs">
              {Math.round(riskHotspotReport.confidence * 100)}{t('studio.route.canvas.confidenceSuffix')}
            </Badge>
          </div>
          <p className="text-sm text-red-800">
            {t('studio.route.canvas.highestRiskPrefix')} {canvasRiskLevelLabel(riskHotspotReport.highestRiskLevel, t)}
          </p>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-red-900">{t('studio.route.canvas.hotspotsTitle')}</h4>
            {riskHotspotReport.hotspots.map((hotspot) => (
              <div key={hotspot.artifactId} className="rounded-md border border-red-300 bg-red-100 p-3">
                <div className="flex justify-between text-sm">
                  <span className="font-medium text-red-900">{hotspot.artifactId}</span>
                  <Badge tone="danger" variant="soft" className="text-xs">
                    {canvasRiskLevelLabel(hotspot.riskLevel, t)}
                  </Badge>
                </div>
                <p className="mt-1 text-xs text-red-800">{hotspot.reason}</p>
              </div>
            ))}
          </div>
        </article>

        {/* Residual Islands */}
        <article className="studio-card space-y-3 border-amber-200 bg-amber-50" aria-labelledby="residual-islands-title">
          <div className="flex items-center justify-between gap-3">
            <h3 id="residual-islands-title" className="text-base font-semibold text-amber-900">
              {t('studio.route.canvas.residualIslandsTitle')}
            </h3>
            <Badge tone={getConfidenceTone(residualIslandReport.confidence)} variant="soft" className="text-xs">
              {Math.round(residualIslandReport.confidence * 100)}{t('studio.route.canvas.confidenceSuffix')}
            </Badge>
          </div>
          <p className="text-sm text-amber-800">
            {residualIslandReport.islandCount} {t('studio.route.canvas.reviewRequired')}
          </p>
        </article>
      </div>

      {/* Semantic Artifacts */}
      {semanticArtifactReferences.length > 0 && (
        <section aria-labelledby="semantic-artifacts-title">
          <h3 id="semantic-artifacts-title" className="text-base font-semibold text-gray-900 mb-3">
            {t('studio.route.canvas.semanticArtifactsTitle')}
          </h3>
          <ul className="space-y-2">
            {semanticArtifactReferences.map((artifact) => (
              <li key={artifact.artifactId} className="flex items-center justify-between rounded-md border border-gray-200 p-3 text-sm">
                <div>
                  <span className="font-medium text-gray-900">{artifact.displayName}</span>
                  <span className="ml-2 text-gray-500 text-xs">{t('studio.route.canvas.artifactKindLabel')}: {artifact.artifactKind}</span>
                </div>
                <Badge
                  tone={artifact.riskLevel === 'high' || artifact.riskLevel === 'critical' ? 'danger' : artifact.riskLevel === 'medium' ? 'warning' : 'success'}
                  variant="soft"
                  className="text-xs"
                >
                  {canvasRiskLevelLabel(artifact.riskLevel ?? 'unknown', t)}
                </Badge>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  );
}
