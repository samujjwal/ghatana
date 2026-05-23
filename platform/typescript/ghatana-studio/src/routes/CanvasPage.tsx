import type { ComponentType, ReactElement } from 'react';
import { useCallback, useMemo } from 'react';
import { Badge } from '@ghatana/design-system';
import { HybridCanvas } from '@ghatana/canvas/hybrid';
import type { CanvasNode } from '@ghatana/canvas/hybrid';
import { createBuilderDocument } from '@ghatana/ui-builder';
import type { BuilderDocument } from '@ghatana/ui-builder';
import { insertNode } from '@ghatana/ui-builder';
import { useAtomValue, useSetAtom } from 'jotai';
import { getStudioCapabilityState } from '../api/kernelLifecycleClient';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import type { StudioTranslationKey } from '../i18n/studioTranslations';
import { useStudioTranslation } from '../i18n/studioTranslations';
import { artifactGraphSummary, residualIslandReport, riskHotspotReport, semanticArtifactReferences } from './yappcWorkflowData';
import IdeationRouteStatusPanel from './IdeationRouteStatusPanel';
import { projectedBuilderDocumentAtom, setArtifactWorkflowAtom } from '../state/artifactWorkflowStore.js';
import {
  builderToCanvas,
  canvasToBuilder,
  filterValidBuilderCanvasNodes,
} from '../adapters/BuilderCanvasProjectionAdapter.js';

type TranslateFn = (key: StudioTranslationKey) => string;

function canvasRiskLevelLabel(riskLevel: string, t: TranslateFn): string {
  if (riskLevel === 'critical' || riskLevel === 'high' || riskLevel === 'medium' || riskLevel === 'low') {
    return t(`studio.route.canvas.riskLevel.${riskLevel}`);
  }
  return t('studio.route.canvas.riskLevel.unknown');
}

function formatCanvasDocumentPositionState(document: BuilderDocument): string {
  return Object.entries(document.nodes)
    .map(([nodeId, node]) => {
      const position = node.metadata.position;
      const x = typeof position?.x === 'number' ? Math.round(position.x) : 0;
      const y = typeof position?.y === 'number' ? Math.round(position.y) : 0;
      return `${nodeId}:${x},${y}`;
    })
    .sort((left, right) => left.localeCompare(right))
    .join('|');
}

function formatCanvasNodeTestId(contractName: string): string {
  return `artifact-canvas-node-${contractName.replace(/[^A-Za-z0-9_-]/gu, '-')}`;
}

function hasRecordProperty<Key extends string>(
  value: unknown,
  key: Key,
): value is Record<Key, Record<string, unknown>> {
  return typeof value === 'object' && value !== null && key in value && typeof value[key] === 'object' && value[key] !== null;
}

function ArtifactCanvasNode(props: unknown): ReactElement {
  const data = hasRecordProperty(props, 'data') ? props.data : {};
  const label = typeof data.label === 'string' ? data.label : 'Artifact node';
  const testId =
    typeof data.testId === 'string'
      ? data.testId
      : typeof data.contractName === 'string'
        ? formatCanvasNodeTestId(data.contractName)
        : 'artifact-canvas-node';

  return (
    <div
      className="rounded-md border border-blue-200 bg-white px-3 py-2 text-sm font-medium text-gray-900 shadow-sm"
      data-testid={testId}
      role="group"
      aria-label={label}
    >
      {label}
    </div>
  );
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

  // Read workflow store: use imported document if available, fall back to static demo data.
  const workflowDocument = useAtomValue(projectedBuilderDocumentAtom);
  const setWorkflow = useSetAtom(setArtifactWorkflowAtom);

  // Create canvas document from artifact data (static fallback)
  const staticCanvasDocument = createArtifactCanvasDocument();
  const canvasDocument = workflowDocument ?? staticCanvasDocument;
  const canvasDocumentPositionState = useMemo(
    () => formatCanvasDocumentPositionState(canvasDocument),
    [canvasDocument],
  );

  // Project BuilderDocument → canvas nodes + edges using the typed adapter.
  // This replaces the manual node-building code and correctly emits slot edges.
  const { nodes: canvasNodes, edges: canvasEdges } = builderToCanvas(canvasDocument);
  const artifactNodeTypes = useMemo<Record<string, ComponentType<unknown>>>(
    () => ({ artifact: ArtifactCanvasNode }),
    [],
  );
  const canvasNodesWithTestHooks = useMemo(
    () =>
      canvasNodes.map((node) => ({
        ...node,
        type: 'artifact',
        data: {
          ...node.data,
          testId: formatCanvasNodeTestId(node.data.contractName),
        },
      })),
    [canvasNodes],
  );

  // Write position changes back to the workflow store.
  // The `onNodesChange` callback receives the full node list after any drag/resize.
  // We validate nodes against the document to ensure type safety without unsafe casts.
  const handleNodesChange = useCallback(
    (updatedNodes: CanvasNode[]) => {
      const builderNodes = filterValidBuilderCanvasNodes(canvasDocument, updatedNodes);
      const updatedDoc = canvasToBuilder({ baseDocument: canvasDocument, canvasNodes: builderNodes });
      setWorkflow({ projectedBuilderDocument: updatedDoc });
    },
    [canvasDocument, setWorkflow],
  );

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
      <div className="border rounded-lg overflow-hidden" data-testid="artifact-graph-canvas">
        <div className="bg-gray-100 p-2 text-sm text-gray-600 flex items-center justify-between">
          <span>Artifact Graph Canvas</span>
          <span
            data-testid="artifact-canvas-state-position"
            className="sr-only"
            aria-live="polite"
          >
            {canvasDocumentPositionState}
          </span>
          <Badge tone="success" variant="soft" className="text-xs">Live</Badge>
        </div>
        <HybridCanvas
          nodes={canvasNodesWithTestHooks}
          edges={[...canvasEdges]}
          mode="hybrid-graph"
          nodeTypes={artifactNodeTypes}
          width="100%"
          height="600px"
          readOnly={false}
          onElementsChange={() => {}}
          onNodesChange={handleNodesChange}
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
