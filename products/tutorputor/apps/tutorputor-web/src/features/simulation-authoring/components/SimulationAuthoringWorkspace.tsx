/**
 * Simulation Authoring Workspace
 * 
 * Full-featured workspace for creating and editing simulation manifests.
 * Combines timeline editor, step palette, entity inspector, and preview.
 * 
 * @doc.type component
 * @doc.purpose Unified simulation authoring workspace
 * @doc.layer product
 * @doc.pattern Workspace
 */

import { useState, useCallback } from "react";
import { Button, Badge, Modal } from "@ghatana/design-system";
import { SimulationTimelineEditor } from "./SimulationTimelineEditor";
import { StepPalette } from "./StepPalette";
import { useSimulationTimeline } from "../hooks/useSimulationTimeline";
import type {
  SimulationManifest,
  SimEntity,
  SimAction,
} from "../hooks/useSimulationTimeline";

// =============================================================================
// Types
// =============================================================================

export interface SimulationAuthoringWorkspaceProps {
  manifest: SimulationManifest | null;
  onSave: (manifest: SimulationManifest) => void;
  onClose?: () => void;
  readOnly?: boolean;
}

type ViewMode = "timeline" | "entities" | "settings" | "preview";

// =============================================================================
// Sub-Components
// =============================================================================

interface EntityListPanelProps {
  entities: SimEntity[];
  onEntitiesChange: (entities: SimEntity[]) => void;
  selectedEntityId: string | null;
  onSelectEntity: (id: string | null) => void;
  readOnly: boolean;
}

const EntityListPanel = ({
  entities,
  onEntitiesChange,
  selectedEntityId,
  onSelectEntity,
  readOnly,
}: EntityListPanelProps) => {
  const handleAddEntity = useCallback(() => {
    const newEntity: SimEntity = {
      id: `entity_${Date.now()}` as SimEntity["id"],
      type: "node",
      x: 100,
      y: 100,
      label: `Entity ${entities.length + 1}`,
    };
    onEntitiesChange([...entities, newEntity]);
    onSelectEntity(newEntity.id);
  }, [entities, onEntitiesChange, onSelectEntity]);

  const handleDeleteEntity = useCallback(
    (entityId: string) => {
      onEntitiesChange(entities.filter((e) => e.id !== entityId));
      if (selectedEntityId === entityId) {
        onSelectEntity(null);
      }
    },
    [entities, selectedEntityId, onEntitiesChange, onSelectEntity]
  );

  return (
    <div className="p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-gray-900 dark:text-white">
          Initial Entities
        </h3>
        {!readOnly && (
          <Button size="sm" variant="outline" onClick={handleAddEntity}>
            + Add Entity
          </Button>
        )}
      </div>

      <div className="space-y-2 max-h-96 overflow-y-auto">
        {entities.length === 0 ? (
          <p className="text-sm text-gray-500 italic py-4 text-center">
            No entities yet. Add entities to define the initial state.
          </p>
        ) : (
          entities.map((entity) => (
            <div
              key={entity.id}
              onClick={() => onSelectEntity(entity.id)}
              className={`
                p-3 rounded-lg border cursor-pointer transition-colors
                ${
                  selectedEntityId === entity.id
                    ? "border-blue-500 bg-blue-50 dark:bg-blue-900/20"
                    : "border-gray-200 dark:border-gray-700 hover:border-gray-300"
                }
              `}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Badge variant="soft">
                    {entity.type}
                  </Badge>
                  <span className="text-sm font-medium text-gray-900 dark:text-white">
                    {entity.label || entity.id}
                  </span>
                </div>
                {!readOnly && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteEntity(entity.id);
                    }}
                    className="p-1 text-red-500 hover:text-red-700"
                  >
                    ✕
                  </button>
                )}
              </div>
              <div className="mt-1 text-xs text-gray-500">
                Position: ({entity.x}, {entity.y})
                {entity.z !== undefined && `, ${entity.z}`}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

interface SettingsPanelProps {
  manifest: SimulationManifest;
  onUpdate: (updates: Partial<SimulationManifest>) => void;
  readOnly: boolean;
}

const SettingsPanel = ({ manifest, onUpdate, readOnly }: SettingsPanelProps) => {
  return (
    <div className="p-4 space-y-6">
      {/* General */}
      <div className="space-y-3">
        <h4 className="font-medium text-gray-900 dark:text-white">General</h4>
        
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Title
          </label>
          <input
            type="text"
            value={manifest.title}
            onChange={(e) => onUpdate({ title: e.target.value })}
            disabled={readOnly}
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Description
          </label>
          <textarea
            value={manifest.description || ""}
            onChange={(e) => onUpdate({ description: e.target.value })}
            disabled={readOnly}
            rows={3}
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
        </div>
      </div>

      {/* Canvas */}
      <div className="space-y-3">
        <h4 className="font-medium text-gray-900 dark:text-white">Canvas</h4>
        
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">Width</label>
            <input
              type="number"
              value={manifest.canvas?.width ?? 800}
              onChange={(e) =>
                onUpdate({
                  canvas: { ...manifest.canvas, width: Number(e.target.value) },
                })
              }
              disabled={readOnly}
              className="w-full px-2 py-1 border border-gray-300 rounded text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">Height</label>
            <input
              type="number"
              value={manifest.canvas?.height ?? 600}
              onChange={(e) =>
                onUpdate({
                  canvas: { ...manifest.canvas, height: Number(e.target.value) },
                })
              }
              disabled={readOnly}
              className="w-full px-2 py-1 border border-gray-300 rounded text-sm"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs text-gray-500 mb-1">
            Background Color
          </label>
          <input
            type="color"
            value={manifest.canvas?.backgroundColor ?? "#ffffff"}
            onChange={(e) =>
              onUpdate({
                canvas: { ...manifest.canvas, backgroundColor: e.target.value },
              })
            }
            disabled={readOnly}
            className="w-full h-8 rounded border border-gray-300"
          />
        </div>
      </div>

      {/* Playback */}
      <div className="space-y-3">
        <h4 className="font-medium text-gray-900 dark:text-white">Playback</h4>
        
        <div>
          <label className="block text-xs text-gray-500 mb-1">Default Speed</label>
          <input
            type="number"
            value={manifest.playback?.defaultSpeed ?? 1}
            onChange={(e) =>
              onUpdate({
                playback: {
                  ...manifest.playback,
                  defaultSpeed: Number(e.target.value),
                },
              })
            }
            disabled={readOnly}
            min={0.1}
            max={5}
            step={0.1}
            className="w-full px-2 py-1 border border-gray-300 rounded text-sm"
          />
        </div>

        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={manifest.playback?.autoPlay ?? false}
              onChange={(e) =>
                onUpdate({
                  playback: { ...manifest.playback, autoPlay: e.target.checked },
                })
              }
              disabled={readOnly}
              className="rounded border-gray-300"
            />
            <span className="text-sm">Auto-play</span>
          </label>

          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={manifest.playback?.loop ?? false}
              onChange={(e) =>
                onUpdate({
                  playback: { ...manifest.playback, loop: e.target.checked },
                })
              }
              disabled={readOnly}
              className="rounded border-gray-300"
            />
            <span className="text-sm">Loop</span>
          </label>
        </div>
      </div>

      {/* Accessibility */}
      <div className="space-y-3">
        <h4 className="font-medium text-gray-900 dark:text-white">
          Accessibility
        </h4>
        
        <div>
          <label className="block text-xs text-gray-500 mb-1">Alt Text</label>
          <textarea
            value={manifest.accessibility?.altText || ""}
            onChange={(e) =>
              onUpdate({
                accessibility: {
                  ...manifest.accessibility,
                  altText: e.target.value,
                },
              })
            }
            disabled={readOnly}
            rows={2}
            placeholder="Describe this simulation for screen readers..."
            className="w-full px-2 py-1 border border-gray-300 rounded text-sm"
          />
        </div>

        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={manifest.accessibility?.screenReaderNarration || false}
              onChange={(e) =>
                onUpdate({
                  accessibility: {
                    ...manifest.accessibility,
                    screenReaderNarration: e.target.checked,
                  },
                })
              }
              disabled={readOnly}
              className="rounded border-gray-300"
            />
            <span className="text-sm">Narration</span>
          </label>

          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={manifest.accessibility?.reducedMotion || false}
              onChange={(e) =>
                onUpdate({
                  accessibility: {
                    ...manifest.accessibility,
                    reducedMotion: e.target.checked,
                  },
                })
              }
              disabled={readOnly}
              className="rounded border-gray-300"
            />
            <span className="text-sm">Reduced Motion</span>
          </label>
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const SimulationAuthoringWorkspace = ({
  manifest: initialManifest,
  onSave,
  onClose,
  readOnly = false,
}: SimulationAuthoringWorkspaceProps) => {
  const [manifest, setManifest] = useState<SimulationManifest | null>(
    initialManifest
  );
  const [viewMode, setViewMode] = useState<ViewMode>("timeline");
  const [paletteCollapsed, setPaletteCollapsed] = useState(false);
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null);
  const [showJsonModal, setShowJsonModal] = useState(false);

  // Timeline hook
  const timeline = useSimulationTimeline({
    manifest,
    onManifestChange: setManifest,
  });

  // Update manifest steps when timeline changes
  const handleStepsChange = useCallback(
    (steps: typeof timeline.steps) => {
      if (!manifest) return;
      setManifest({ ...manifest, steps });
      timeline.setSteps(steps);
    },
    [manifest, timeline]
  );

  // Update manifest entities
  const handleEntitiesChange = useCallback(
    (entities: SimEntity[]) => {
      if (!manifest) return;
      setManifest({ ...manifest, initialEntities: entities });
    },
    [manifest]
  );

  // Update manifest settings
  const handleManifestUpdate = useCallback(
    (updates: Partial<SimulationManifest>) => {
      if (!manifest) return;
      setManifest({ ...manifest, ...updates, updatedAt: new Date().toISOString() });
    },
    [manifest]
  );

  // Save handler
  const handleSave = useCallback(() => {
    if (!manifest) return;
    onSave({
      ...manifest,
      steps: timeline.steps,
      updatedAt: new Date().toISOString(),
    });
  }, [manifest, timeline.steps, onSave]);

  // Add action to selected step
  const handleAddActionToStep = useCallback(
    (action: Partial<SimAction>) => {
      // Ensure action has required fields
      const fullAction: SimAction = {
        action: action.action ?? "ANNOTATE",
        ...action,
      };
      
      if (!timeline.selectedStepId) {
        // If no step selected, create one first
        const newStepId = timeline.addStep();
        // Action will be added after step is created
        setTimeout(() => {
          timeline.setSteps(
            timeline.steps.map((s) =>
              s.id === newStepId
                ? { ...s, actions: [...s.actions, fullAction] }
                : s
            )
          );
        }, 0);
      } else {
        timeline.setSteps(
          timeline.steps.map((s) =>
            s.id === timeline.selectedStepId
              ? { ...s, actions: [...s.actions, fullAction] }
              : s
          )
        );
      }
    },
    [timeline]
  );

  if (!manifest) {
    return (
      <div className="flex items-center justify-center h-full text-gray-500">
        No manifest loaded
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-gray-100 dark:bg-gray-900">
      {/* Header */}
      <header className="flex items-center justify-between px-4 py-3 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-3">
          <h1 className="text-lg font-semibold text-gray-900 dark:text-white">
            {manifest.title}
          </h1>
          <Badge variant="outline">{manifest.domain}</Badge>
          {timeline.isDirty && (
            <Badge variant="soft" tone="warning">
              Unsaved
            </Badge>
          )}
        </div>

        <div className="flex items-center gap-2">
          {/* Undo/Redo */}
          <Button
            size="sm"
            variant="ghost"
            disabled={!timeline.canUndo}
            onClick={timeline.undo}
            title="Undo"
          >
            ↩️
          </Button>
          <Button
            size="sm"
            variant="ghost"
            disabled={!timeline.canRedo}
            onClick={timeline.redo}
            title="Redo"
          >
            ↪️
          </Button>

          <div className="w-px h-6 bg-gray-200 dark:bg-gray-700 mx-2" />

          {/* View JSON */}
          <Button
            size="sm"
            variant="outline"
            onClick={() => setShowJsonModal(true)}
          >
            { } JSON
          </Button>

          {/* Save */}
          {!readOnly && (
            <Button size="sm" variant="solid" tone="primary" onClick={handleSave}>
              💾 Save
            </Button>
          )}

          {/* Close */}
          {onClose && (
            <Button size="sm" variant="ghost" onClick={onClose}>
              ✕
            </Button>
          )}
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Panel - Palette */}
        {viewMode === "timeline" && (
          <StepPalette
            domain={manifest.domain}
            onActionAdd={handleAddActionToStep}
            collapsed={paletteCollapsed}
            onToggleCollapse={() => setPaletteCollapsed(!paletteCollapsed)}
          />
        )}

        {/* Center Content */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* View Tabs */}
          <div className="flex border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
            {(
              [
                { key: "timeline", label: "Timeline", icon: "📊" },
                { key: "entities", label: "Entities", icon: "🎯" },
                { key: "settings", label: "Settings", icon: "⚙️" },
                { key: "preview", label: "Preview", icon: "▶️" },
              ] as const
            ).map((tab) => (
              <button
                key={tab.key}
                onClick={() => setViewMode(tab.key)}
                className={`
                  px-4 py-2 text-sm font-medium border-b-2 transition-colors
                  ${
                    viewMode === tab.key
                      ? "border-blue-500 text-blue-600"
                      : "border-transparent text-gray-500 hover:text-gray-700"
                  }
                `}
              >
                {tab.icon} {tab.label}
              </button>
            ))}
          </div>

          {/* View Content */}
          <div className="flex-1 overflow-auto">
            {viewMode === "timeline" && (
              <SimulationTimelineEditor
                steps={timeline.steps}
                onStepsChange={handleStepsChange}
                onStepSelect={timeline.selectStep}
                selectedStepId={timeline.selectedStepId}
                currentPlaybackStep={timeline.currentPlaybackStep}
                isPlaying={timeline.isPlaying}
                onPlayPause={timeline.togglePlayPause}
                onSeek={timeline.seekTo}
                readOnly={readOnly}
                domain={manifest.domain}
              />
            )}

            {viewMode === "entities" && (
              <EntityListPanel
                entities={manifest.initialEntities}
                onEntitiesChange={handleEntitiesChange}
                selectedEntityId={selectedEntityId}
                onSelectEntity={setSelectedEntityId}
                readOnly={readOnly}
              />
            )}

            {viewMode === "settings" && (
              <SettingsPanel
                manifest={manifest}
                onUpdate={handleManifestUpdate}
                readOnly={readOnly}
              />
            )}

            {viewMode === "preview" && (
              <div className="flex items-center justify-center h-full bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-gray-800 dark:to-gray-900">
                <div className="text-center">
                  <div className="text-6xl mb-4">🎮</div>
                  <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
                    Simulation Preview
                  </h3>
                  <p className="text-gray-500 dark:text-gray-400 max-w-md">
                    {manifest.initialEntities.length} entities •{" "}
                    {timeline.steps.length} steps
                  </p>
                  <p className="text-sm text-gray-400 mt-4">
                    Full preview available in the module player.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* JSON Modal */}
      <Modal
        isOpen={showJsonModal}
        onClose={() => setShowJsonModal(false)}
        title="Manifest JSON"
        size="lg"
      >
        <pre className="bg-gray-900 text-green-400 p-4 rounded-lg overflow-auto max-h-[60vh] text-xs font-mono">
          {JSON.stringify({ ...manifest, steps: timeline.steps }, null, 2)}
        </pre>
        <div className="mt-4 flex justify-end">
          <Button
            variant="outline"
            onClick={() => {
              navigator.clipboard.writeText(
                JSON.stringify({ ...manifest, steps: timeline.steps }, null, 2)
              );
            }}
          >
            📋 Copy to Clipboard
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default SimulationAuthoringWorkspace;
