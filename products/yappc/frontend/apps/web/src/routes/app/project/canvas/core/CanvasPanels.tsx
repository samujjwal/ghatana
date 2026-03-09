import React from 'react';
import { Alert, Box } from '@ghatana/ui';
import { Drawer, Snackbar } from '@ghatana/ui';

import { CommentsPanel } from '@/components/canvas/collaboration/CommentsPanel';
import { CanvasPerformancePanel, CanvasAccessibilityPanel, LayoutDialog, TemplateSaveDialog } from '../components';
import { VersionHistoryPanel } from '@/components/canvas/versioning/VersionHistoryPanel';
import { SimplePageDesigner, type SimplePageComponent } from '@/components/canvas/SimplePageDesigner';
import { UnifiedRightPanel, type GuidanceItem, type Suggestion as UnifiedSuggestion, type ValidationIssue } from '@/components/canvas/UnifiedRightPanel';
import { TemplateGallery } from '@/components/canvas/templates/TemplateGallery';
import { CanvasWelcomeDialog } from '@/components/canvas/onboarding/CanvasWelcomeDialog';
import { AINotificationToast } from '@/components/canvas/ai/AINotificationToast';
import { ContextualHelpManager } from '@/components/canvas/help/ContextualHelpManager';
import type { LifecyclePhase } from '@ghatana/yappc-types/lifecycle';
import type { CanvasElement } from '@/components/canvas/workspace/canvasAtoms';

export interface CanvasPanelsProps {
  // State from useCanvasPanels
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

  // Dialog States
  layoutDialogOpen: boolean;
  setLayoutDialogOpen: (open: boolean) => void;
  
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

  // Data
  currentPhase: LifecyclePhase;
  canvasState: { elements: CanvasElement[] };
  
  performanceMetrics: unknown;
  performanceEnabled: boolean;
  handleEnablePerformance: () => void;
  
  accessibilityIssues: unknown[];
  
  selectedElementId?: string;
  currentUser: unknown;
  
  designerNodeId: string | null;
  pageDesignerComponents: Record<string, SimplePageComponent[]>;
  setPageDesignerComponents: React.Dispatch<React.SetStateAction<Record<string, SimplePageComponent[]>>>;
  
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
  generationResult: unknown;
  generateCode: () => void;
  
  applyAutoLayout: unknown;
  templateName: string;
  setTemplateName: (name: string) => void;
  handleSaveTemplateConfirm: () => void;
  handleSelectTemplate: unknown;
  recentTemplateIds: string[];
  
  suggestionsLength: number;
  
  handleAddComponent: (component: unknown) => void;
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
  handleAddComponent,
  commentsOpen,
  setCommentsOpen,
  selectedElementId,
  currentUser,
  designerOpen,
  setDesignerOpen,
  designerNodeId,
  pageDesignerComponents,
  setPageDesignerComponents,
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
        <Box className="p-4" style={{ width: widePanelWidth, height: isMobile ? '80vh' : '100%' }}>
          {designerNodeId && (
            <SimplePageDesigner
              key={designerNodeId}
              components={pageDesignerComponents[designerNodeId] ?? []}
              onComponentsChange={(components) =>
                setPageDesignerComponents((prev) => ({
                  ...prev,
                  [designerNodeId]: components,
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
              const snapshot = await persistenceRef.current?.restoreSnapshot(snapshotId);
              if (snapshot) {
                setGlobalCanvas(snapshot);
                setShowVersionHistory(false);
              }
            }}
            onDelete={async (snapshotId) => {
              await persistenceRef.current?.deleteSnapshot(snapshotId);
            }}
            onCreateSnapshot={async (label, description) => {
              if (params.projectId && params.canvasId) {
                await persistenceRef.current?.save(
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
          sx: { width: panelWidth, height: isMobile ? '80vh' : '100%' }
        }}
      >
        <UnifiedRightPanel
          open={true} // Always render content if drawer is open
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
            files: generationResult?.artifacts?.map((a: unknown) => ({
              name: a.path || a.name || 'file',
              status: 'complete' as const,
            })) || [],
          }}
          onGenerate={generateCode}
        />
      </Drawer>

      <LayoutDialog
        open={layoutDialogOpen}
        onClose={() => setLayoutDialogOpen(false)}
        onApply={applyAutoLayout}
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
        <Alert
          onClose={closeNotification}
          severity={notification?.type || 'info'}
          className="w-full"
        >
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
          if (setUnifiedPanelTab) setUnifiedPanelTab(1);
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
