import React from 'react';
import { Alert, Box, Drawer, Snackbar, useMediaQuery, useTheme } from '@ghatana/design-system';

import { CommentsPanel } from '@/components/canvas/collaboration/CommentsPanel';
import {
  CanvasPerformancePanel,
  CanvasAccessibilityPanel,
  LayoutDialog,
  TemplateSaveDialog,
} from '../components';
import type { AutoLayoutPreview } from '../hooks/useCanvasLayout';
import { VersionHistoryPanel } from '@/components/canvas/versioning/VersionHistoryPanel';
import { PageDesigner } from '@/components/canvas/page/PageDesigner';
import type { BuilderDocument } from '@ghatana/ui-builder';
import {
  UnifiedRightPanel,
  type GuidanceItem,
  type Suggestion as UnifiedSuggestion,
  type ValidationIssue,
} from '@/components/canvas/UnifiedRightPanel';
import { TemplateGallery } from '@/components/canvas/templates/TemplateGallery';
import { CanvasWelcomeDialog } from '@/components/canvas/onboarding/CanvasWelcomeDialog';
import { AINotificationToast } from '@/components/canvas/ai/AINotificationToast';
import { ContextualHelpManager } from '@/components/canvas/help/ContextualHelpManager';
import type { LifecyclePhase } from 'yappc-core/types/lifecycle';
import type { CanvasElement } from '@/components/canvas/workspace/canvasAtoms';

interface CanvasPersistence {
  restoreSnapshot: (snapshotId: string) => Promise<unknown>;
  deleteSnapshot: (snapshotId: string) => Promise<void>;
  save: (
    projectId: string,
    canvasId: string,
    canvasData: { elements: unknown[]; connections: unknown[] },
    metadata: { label: string; description?: string }
  ) => Promise<void>;
}

interface GenerationArtifact {
  path?: string;
  name?: string;
}

interface GenerationResult {
  artifacts?: GenerationArtifact[];
}

export interface CanvasPanelsProps {
  unifiedPanelOpen: boolean;
  setUnifiedPanelOpen: (open: boolean) => void;
  unifiedPanelTab?: number;
  setUnifiedPanelTab?: (tab: number) => void;

  performancePanelOpen: boolean;
  setPerformancePanelOpen: (open: boolean) => void;

  accessibilityPanelOpen: boolean;
  closeAccessibilityPanel: () => void;

  commentsOpen: boolean;
  setCommentsOpen: (open: boolean) => void;

  designerOpen: boolean;
  setDesignerOpen: (open: boolean) => void;

  showVersionHistory: boolean;
  setShowVersionHistory: (open: boolean) => void;

  layoutDialogOpen: boolean;
  setLayoutDialogOpen: (open: boolean) => void;
  layoutPreview: AutoLayoutPreview | null;
  previewAutoLayout: () => void;

  templateDialogOpen: boolean;
  setTemplateDialogOpen: (open: boolean) => void;

  templateGalleryOpen: boolean;
  setTemplateGalleryOpen: (open: boolean) => void;

  welcomeDialogOpen: boolean;
  setWelcomeDialogOpen: (open: boolean) => void;

  notification: { type?: 'success' | 'info' | 'warning' | 'error'; message: string } | null;
  closeNotification: () => void;

  showAINotification: boolean;
  setShowAINotification: (show: boolean) => void;

  currentPhase: LifecyclePhase;
  canvasState: { elements: CanvasElement[] };

  performanceMetrics: unknown;
  performanceEnabled: boolean;
  handleEnablePerformance: () => void;

  accessibilityIssues: unknown[];

  selectedElementId?: string;
  currentUser: unknown;

  designerNodeId: string | null;
  /** Per-node BuilderDocument state keyed by ReactFlow node id */
  pageDesignerDocuments: Record<string, BuilderDocument>;
  setPageDesignerDocuments: React.Dispatch<
    React.SetStateAction<Record<string, BuilderDocument>>
  >;

  persistenceRef: React.MutableRefObject<unknown>;
  params: { projectId?: string; canvasId?: string };
  setGlobalCanvas: (state: unknown) => void;
  nodes: unknown[];
  edges: unknown[];

  guidanceItems: GuidanceItem[];
  unifiedSuggestions: UnifiedSuggestion[];
  dismissSuggestion: (id: string) => void;

  unifiedValidationIssues: ValidationIssue[];
  score?: number;
  validate?: () => void;
  isValidating?: boolean;
  errorCount: number;
  warningCount: number;

  isGenerating: boolean;
  generationResult: GenerationResult | null;
  generateCode: () => void;

  applyAutoLayout: () => void;
  templateName: string;
  setTemplateName: (name: string) => void;
  handleSaveTemplateConfirm: () => void;
  handleSelectTemplate: (templateId: string) => void;
  recentTemplateIds: string[];

  suggestionsLength: number;

  handleAddComponent: (_component: unknown) => void;
}

export function CanvasPanels({
  unifiedPanelOpen,
  setUnifiedPanelOpen,
  unifiedPanelTab,
  setUnifiedPanelTab,
  performancePanelOpen,
  performanceMetrics,
  performanceEnabled,
  handleEnablePerformance,
  accessibilityPanelOpen,
  closeAccessibilityPanel,
  accessibilityIssues,
  handleAddComponent: _handleAddComponent,
  commentsOpen,
  setCommentsOpen,
  selectedElementId,
  currentUser,
  designerOpen,
  setDesignerOpen,
  designerNodeId,
  pageDesignerDocuments,
  setPageDesignerDocuments,
  showVersionHistory,
  setShowVersionHistory,
  persistenceRef,
  params,
  setGlobalCanvas,
  nodes,
  edges,
  currentPhase,
  guidanceItems,
  unifiedSuggestions,
  dismissSuggestion,
  unifiedValidationIssues,
  score,
  validate,
  isValidating,
  isGenerating,
  generationResult,
  generateCode,
  layoutDialogOpen,
  setLayoutDialogOpen,
  layoutPreview,
  previewAutoLayout,
  applyAutoLayout,
  templateDialogOpen,
  templateName,
  setTemplateName,
  setTemplateDialogOpen,
  handleSaveTemplateConfirm,
  notification,
  closeNotification,
  templateGalleryOpen,
  setTemplateGalleryOpen,
  handleSelectTemplate,
  recentTemplateIds,
  welcomeDialogOpen,
  setWelcomeDialogOpen,
  showAINotification,
  setShowAINotification,
  suggestionsLength,
  canvasState,
  errorCount,
  warningCount,
}: CanvasPanelsProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const panelAnchor = isMobile ? 'bottom' : 'right';
  const panelWidth = isMobile ? '100%' : 320;
  const widePanelWidth = isMobile ? '100%' : 420;

  return (
    <>
      <CanvasPerformancePanel
        open={performancePanelOpen}
        metrics={performanceMetrics}
        enabled={performanceEnabled}
        onEnableMonitoring={handleEnablePerformance}
      />

      <CanvasAccessibilityPanel
        open={accessibilityPanelOpen}
        issues={accessibilityIssues}
        onClose={closeAccessibilityPanel}
      />

      <Drawer anchor={panelAnchor} open={commentsOpen} onClose={() => setCommentsOpen(false)}>
        <Box className="p-4" style={{ width: panelWidth, height: isMobile ? '80vh' : '100%' }}>
          <CommentsPanel selectedElementId={selectedElementId} currentUser={currentUser} />
        </Box>
      </Drawer>

      <Drawer anchor={panelAnchor} open={designerOpen} onClose={() => setDesignerOpen(false)}>
        <Box style={{ width: widePanelWidth, height: isMobile ? '80vh' : '100%', overflow: 'hidden' }}>
          {designerNodeId && (
            <PageDesigner
              key={designerNodeId}
              initialComponents={pageDesignerDocuments[designerNodeId]}
              onDocumentChange={(doc) =>
                setPageDesignerDocuments((prev) => ({
                  ...prev,
                  [designerNodeId]: doc,
                }))
              }
            />
          )}
        </Box>
      </Drawer>

      <Drawer
        anchor={panelAnchor}
        open={showVersionHistory}
        onClose={() => setShowVersionHistory(false)}
      >
        <Box className="p-4" style={{ width: widePanelWidth, height: isMobile ? '80vh' : '100%' }}>
          <VersionHistoryPanel
            snapshots={[]}
            open={showVersionHistory}
            onClose={() => setShowVersionHistory(false)}
            onRestore={async (snapshotId) => {
              const snapshot = await (
                persistenceRef.current as CanvasPersistence | null
              )?.restoreSnapshot(snapshotId);
              if (snapshot) {
                setGlobalCanvas(snapshot);
                setShowVersionHistory(false);
              }
            }}
            onDelete={async (snapshotId) => {
              await (persistenceRef.current as CanvasPersistence | null)?.deleteSnapshot(snapshotId);
            }}
            onCreateSnapshot={async (label, description) => {
              if (params.projectId && params.canvasId) {
                await (persistenceRef.current as CanvasPersistence | null)?.save(
                  params.projectId,
                  params.canvasId,
                  { elements: nodes, connections: edges },
                  { label, description }
                );
              }
            }}
          />
        </Box>
      </Drawer>

      <Drawer
        anchor={panelAnchor}
        open={unifiedPanelOpen}
        onClose={() => setUnifiedPanelOpen(false)}
        PaperProps={{
          sx: { width: panelWidth, height: isMobile ? '80vh' : '100%' },
        }}
      >
        <UnifiedRightPanel
          open={true}
          onClose={() => setUnifiedPanelOpen(false)}
          initialTab={unifiedPanelTab}
          currentPhase={currentPhase}
          guidanceItems={guidanceItems}
          suggestions={unifiedSuggestions}
          onDismissSuggestion={dismissSuggestion}
          validationIssues={unifiedValidationIssues}
          validationScore={score}
          onValidate={validate}
          isValidating={isValidating}
          generationStatus={{
            isGenerating,
            progress: isGenerating ? 50 : 0,
            files:
              generationResult?.artifacts?.map((artifact) => ({
                name: artifact.path || artifact.name || 'file',
                status: 'complete' as const,
              })) || [],
          }}
          onGenerate={generateCode}
        />
      </Drawer>

      <LayoutDialog
        open={layoutDialogOpen}
        onClose={() => setLayoutDialogOpen(false)}
        onPreview={previewAutoLayout}
        onApply={applyAutoLayout}
        preview={layoutPreview}
      />

      <TemplateSaveDialog
        open={templateDialogOpen}
        templateName={templateName}
        onTemplateNameChange={setTemplateName}
        onClose={() => setTemplateDialogOpen(false)}
        onSave={handleSaveTemplateConfirm}
      />

      <Snackbar
        open={!!notification}
        autoHideDuration={3000}
        onClose={closeNotification}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert onClose={closeNotification} severity={notification?.type || 'info'} className="w-full">
          {notification?.message}
        </Alert>
      </Snackbar>

      <TemplateGallery
        open={templateGalleryOpen}
        onClose={() => setTemplateGalleryOpen(false)}
        onSelectTemplate={handleSelectTemplate}
        recentTemplates={recentTemplateIds}
      />

      <CanvasWelcomeDialog
        open={welcomeDialogOpen}
        onClose={() => setWelcomeDialogOpen(false)}
        onComplete={() => {
          setWelcomeDialogOpen(false);
        }}
        onSkip={() => setWelcomeDialogOpen(false)}
      />

      <AINotificationToast
        show={showAINotification}
        suggestionCount={suggestionsLength}
        onView={() => {
          setShowAINotification(false);
          setUnifiedPanelOpen(true);
          if (setUnifiedPanelTab) {
            setUnifiedPanelTab(1);
          }
        }}
        onDismiss={() => setShowAINotification(false)}
        position="bottom-right"
        autoDismiss={8000}
      />

      <ContextualHelpManager
        currentPhase={currentPhase}
        canvasElementCount={canvasState.elements.length}
        hasValidationErrors={errorCount > 0}
        aiSuggestionCount={suggestionsLength}
        isCanvasEmpty={canvasState.elements.length === 0}
      />
    </>
  );
}
