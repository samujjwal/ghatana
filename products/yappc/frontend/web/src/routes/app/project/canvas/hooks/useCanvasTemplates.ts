/**
 * @doc.type hook
 * @doc.purpose Manages template loading, saving, and gallery state
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import type { AppTemplate } from '@/components/canvas/templates/TemplateGallery';
import type { CanvasState } from '@/components/canvas/workspace/canvasAtoms';

interface SavedTemplate {
  id: string;
  name: string;
  data: CanvasState;
}

interface UseCanvasTemplatesOptions {
  canvasState: CanvasState;
  setGlobalCanvas: (updater: (prev: CanvasState) => CanvasState) => void;
}

/**
 * Hook to manage canvas templates
 */
export function useCanvasTemplates({
  canvasState,
  setGlobalCanvas,
}: UseCanvasTemplatesOptions) {
  const [templatesMenuOpen, setTemplatesMenuOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [templateName, setTemplateName] = useState('');
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [savedTemplates, setSavedTemplates] = useState<SavedTemplate[]>([]);
  const [templateGalleryOpen, setTemplateGalleryOpen] = useState(false);
  const [recentTemplateIds, setRecentTemplateIds] = useState<string[]>([]);

  const handleOpenTemplateGallery = useCallback(() => {
    setTemplateGalleryOpen(true);
  }, []);

  const handleSelectTemplate = useCallback((template: AppTemplate) => {
    // Load template into canvas
    setGlobalCanvas(() => template.canvasState);

    // Track recently used templates
    setRecentTemplateIds((prev) => {
      const updated = [template.id, ...prev.filter((id) => id !== template.id)];
      return updated.slice(0, 5); // Keep only 5 most recent
    });

    setTemplateGalleryOpen(false);
  }, [setGlobalCanvas]);

  const handleSaveTemplateConfirm = useCallback(() => {
    if (!templateName.trim()) return;

    const snapshot = JSON.parse(JSON.stringify(canvasState)) as CanvasState;
    setSavedTemplates((prev) => [
      ...prev,
      {
        id: `template-${Date.now()}`,
        name: templateName.trim(),
        data: snapshot,
      },
    ]);

    setTemplateName('');
    setTemplateDialogOpen(false);
    setTemplatesMenuOpen(false);
  }, [canvasState, templateName]);

  const handleLoadTemplate = useCallback(() => {
    if (!selectedTemplateId) return;
    const template = savedTemplates.find((entry) => entry.id === selectedTemplateId);
    if (!template) return;

    const snapshot = JSON.parse(JSON.stringify(template.data)) as CanvasState;
    setGlobalCanvas(() => snapshot);
    setTemplatesMenuOpen(false);
  }, [savedTemplates, selectedTemplateId, setGlobalCanvas]);

  return {
    // State
    templatesMenuOpen,
    setTemplatesMenuOpen,
    templateDialogOpen,
    setTemplateDialogOpen,
    templateName,
    setTemplateName,
    selectedTemplateId,
    setSelectedTemplateId,
    savedTemplates,
    templateGalleryOpen,
    setTemplateGalleryOpen,
    recentTemplateIds,

    // Actions
    handleOpenTemplateGallery,
    handleSelectTemplate,
    handleSaveTemplateConfirm,
    handleLoadTemplate,
  };
}
