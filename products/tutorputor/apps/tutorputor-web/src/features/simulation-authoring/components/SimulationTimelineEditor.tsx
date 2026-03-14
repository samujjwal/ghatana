/**
 * Simulation Timeline Editor Component
 * 
 * Visual drag-and-drop timeline for editing simulation steps.
 * Supports step ordering, editing, durations, and real-time preview.
 * 
 * @doc.type component
 * @doc.purpose Visual timeline editor for simulation steps
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback, useRef, useMemo } from "react";
import { Button, Tooltip, Badge, Slider, Chip } from "@ghatana/design-system";
import type { 
  SimStepId, 
  SimAction, 
  SimulationStep 
} from "../hooks/useSimulationTimeline";

// Local type for easing functions
type EasingFunction = "linear" | "easeIn" | "easeOut" | "easeInOut" | "bounce";

// =============================================================================
// Types
// =============================================================================

export interface SimulationTimelineEditorProps {
  steps: SimulationStep[];
  onStepsChange: (steps: SimulationStep[]) => void;
  onStepSelect: (stepId: SimStepId | null) => void;
  selectedStepId: SimStepId | null;
  currentPlaybackStep?: number;
  isPlaying?: boolean;
  onPlayPause?: () => void;
  onSeek?: (stepIndex: number) => void;
  readOnly?: boolean;
  domain?: string;
}

interface DragState {
  isDragging: boolean;
  draggedId: SimStepId | null;
  dragOverId: SimStepId | null;
  insertPosition: "before" | "after" | null;
}

interface StepPalette {
  category: string;
  actions: Array<{
    action: string;
    label: string;
    icon: string;
    defaultDuration: number;
  }>;
}

// =============================================================================
// Action Palettes by Domain
// =============================================================================

const COMMON_ACTIONS = [
  { action: "ANNOTATE", label: "Annotate", icon: "💬", defaultDuration: 1000 },
  { action: "HIGHLIGHT", label: "Highlight", icon: "✨", defaultDuration: 500 },
];

const ACTION_PALETTES: Record<string, StepPalette[]> = {
  CS_DISCRETE: [
    {
      category: "Basic",
      actions: [
        { action: "CREATE_ENTITY", label: "Create Node", icon: "➕", defaultDuration: 300 },
        { action: "REMOVE_ENTITY", label: "Remove", icon: "🗑️", defaultDuration: 300 },
        { action: "MOVE", label: "Move", icon: "↔️", defaultDuration: 500 },
      ],
    },
    {
      category: "Algorithm",
      actions: [
        { action: "COMPARE", label: "Compare", icon: "⚖️", defaultDuration: 600 },
        { action: "SWAP", label: "Swap", icon: "🔄", defaultDuration: 500 },
        { action: "SET_VALUE", label: "Set Value", icon: "✏️", defaultDuration: 300 },
      ],
    },
    {
      category: "Visual",
      actions: COMMON_ACTIONS,
    },
  ],
  PHYSICS: [
    {
      category: "Forces",
      actions: [
        { action: "APPLY_FORCE", label: "Apply Force", icon: "💨", defaultDuration: 100 },
        { action: "SET_GRAVITY", label: "Set Gravity", icon: "⬇️", defaultDuration: 100 },
        { action: "RELEASE", label: "Release", icon: "🎈", defaultDuration: 100 },
      ],
    },
    {
      category: "Motion",
      actions: [
        { action: "SET_INITIAL_VELOCITY", label: "Set Velocity", icon: "🚀", defaultDuration: 100 },
        { action: "CONNECT_SPRING", label: "Connect Spring", icon: "🔗", defaultDuration: 300 },
      ],
    },
    {
      category: "Visual",
      actions: COMMON_ACTIONS,
    },
  ],
  CHEMISTRY: [
    {
      category: "Bonds",
      actions: [
        { action: "CREATE_BOND", label: "Create Bond", icon: "🔗", defaultDuration: 500 },
        { action: "BREAK_BOND", label: "Break Bond", icon: "✂️", defaultDuration: 500 },
        { action: "REARRANGE", label: "Rearrange", icon: "🔀", defaultDuration: 800 },
      ],
    },
    {
      category: "Reactions",
      actions: [
        { action: "SET_REACTION_CONDITIONS", label: "Set Conditions", icon: "🌡️", defaultDuration: 300 },
        { action: "SHOW_ENERGY_PROFILE", label: "Energy Profile", icon: "📈", defaultDuration: 500 },
      ],
    },
    {
      category: "Visual",
      actions: [
        ...COMMON_ACTIONS,
        { action: "HIGHLIGHT_ATOMS", label: "Highlight Atoms", icon: "⚛️", defaultDuration: 400 },
        { action: "DISPLAY_FORMULA", label: "Show Formula", icon: "🧪", defaultDuration: 300 },
      ],
    },
  ],
  BIOLOGY: [
    {
      category: "Transport",
      actions: [
        { action: "DIFFUSE", label: "Diffuse", icon: "〰️", defaultDuration: 1000 },
        { action: "TRANSPORT", label: "Transport", icon: "🚚", defaultDuration: 800 },
      ],
    },
    {
      category: "Gene Expression",
      actions: [
        { action: "TRANSCRIBE", label: "Transcribe", icon: "📝", defaultDuration: 1500 },
        { action: "TRANSLATE", label: "Translate", icon: "🔤", defaultDuration: 2000 },
        { action: "METABOLISE", label: "Metabolise", icon: "⚗️", defaultDuration: 1000 },
      ],
    },
    {
      category: "Cell",
      actions: [
        { action: "GROW_DIVIDE", label: "Grow/Divide", icon: "🧫", defaultDuration: 2000 },
      ],
    },
  ],
  MEDICINE: [
    {
      category: "Pharmacokinetics",
      actions: [
        { action: "ABSORB", label: "Absorb", icon: "💊", defaultDuration: 500 },
        { action: "ELIMINATE", label: "Eliminate", icon: "🚿", defaultDuration: 500 },
      ],
    },
    {
      category: "Epidemiology",
      actions: [
        { action: "SPREAD_DISEASE", label: "Spread", icon: "🦠", defaultDuration: 1000 },
        { action: "SIGNAL", label: "Signal", icon: "📡", defaultDuration: 400 },
      ],
    },
  ],
  ECONOMICS: [
    {
      category: "Stocks & Flows",
      actions: [
        { action: "SET_STOCK_VALUE", label: "Set Stock", icon: "📦", defaultDuration: 200 },
        { action: "UPDATE_FLOW_RATE", label: "Update Flow", icon: "🌊", defaultDuration: 200 },
      ],
    },
    {
      category: "Agents",
      actions: [
        { action: "SPAWN_AGENT", label: "Spawn Agent", icon: "👤", defaultDuration: 300 },
      ],
    },
    {
      category: "Analytics",
      actions: [
        { action: "DISPLAY_CHART", label: "Show Chart", icon: "📊", defaultDuration: 500 },
      ],
    },
  ],
};

// Default palette for unknown domains
const DEFAULT_PALETTE: StepPalette[] = [
  {
    category: "Basic",
    actions: [
      { action: "CREATE_ENTITY", label: "Create", icon: "➕", defaultDuration: 300 },
      { action: "REMOVE_ENTITY", label: "Remove", icon: "🗑️", defaultDuration: 300 },
      { action: "MOVE", label: "Move", icon: "↔️", defaultDuration: 500 },
      { action: "SET_VALUE", label: "Set Value", icon: "✏️", defaultDuration: 300 },
    ],
  },
  {
    category: "Visual",
    actions: COMMON_ACTIONS,
  },
];

// =============================================================================
// Utility Functions
// =============================================================================

function generateStepId(): SimStepId {
  return `step_${Date.now()}_${Math.random().toString(36).slice(2, 8)}` as SimStepId;
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function getTotalDuration(steps: SimulationStep[]): number {
  return steps.reduce((sum, step) => {
    const stepDuration = step.actions.reduce((d: number, a: SimAction) => Math.max(d, (a.duration || 0) + (a.delay || 0)), 0);
    return sum + (stepDuration || 500); // default 500ms per step
  }, 0);
}

// =============================================================================
// Sub-Components
// =============================================================================

interface StepTileProps {
  step: SimulationStep;
  index: number;
  isSelected: boolean;
  isCurrent: boolean;
  isDragOver: boolean;
  insertPosition: "before" | "after" | null;
  onSelect: () => void;
  onDelete: () => void;
  onDragStart: (e: React.DragEvent) => void;
  onDragOver: (e: React.DragEvent) => void;
  onDragLeave: () => void;
  onDrop: (e: React.DragEvent) => void;
  readOnly: boolean;
}

const StepTile = ({
  step,
  index,
  isSelected,
  isCurrent,
  isDragOver,
  insertPosition,
  onSelect,
  onDelete,
  onDragStart,
  onDragOver,
  onDragLeave,
  onDrop,
  readOnly,
}: StepTileProps) => {
  const primaryAction = step.actions[0];
  const actionLabel = primaryAction?.action || "Empty";
  
  return (
    <div
      className={`
        relative group
        ${isDragOver && insertPosition === "before" ? "pl-4 border-l-4 border-blue-500" : ""}
        ${isDragOver && insertPosition === "after" ? "pr-4 border-r-4 border-blue-500" : ""}
      `}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      <div
        draggable={!readOnly}
        onDragStart={onDragStart}
        onClick={onSelect}
        className={`
          flex flex-col p-3 rounded-lg border-2 cursor-pointer
          transition-all duration-200 min-w-[120px]
          ${isSelected
            ? "border-blue-500 bg-blue-50 dark:bg-blue-900/20"
            : "border-gray-200 bg-white dark:bg-gray-800 hover:border-gray-300"}
          ${isCurrent ? "ring-2 ring-green-400 ring-offset-2" : ""}
          ${!readOnly ? "hover:shadow-md" : ""}
        `}
      >
        {/* Step number badge */}
        <div className="flex items-center justify-between mb-2">
          <span className={`
            inline-flex items-center justify-center w-6 h-6 text-xs font-bold rounded-full
            ${isSelected ? "bg-blue-500 text-white" : "bg-gray-200 text-gray-600"}
          `}>
            {index + 1}
          </span>
          
          {step.checkpoint && (
            <Tooltip content="Checkpoint">
              <Badge variant="outline">🎯</Badge>
            </Tooltip>
          )}
          
          {!readOnly && (
            <button
              onClick={(e) => { e.stopPropagation(); onDelete(); }}
              className="opacity-0 group-hover:opacity-100 p-1 text-red-500 hover:text-red-700 transition-opacity"
            >
              ✕
            </button>
          )}
        </div>
        
        {/* Title */}
        <span className="text-sm font-medium text-gray-900 dark:text-white truncate mb-1">
          {step.title || actionLabel}
        </span>
        
        {/* Action chips */}
        <div className="flex flex-wrap gap-1 mt-1">
          {step.actions.slice(0, 2).map((action: SimAction, i: number) => (
            <Chip key={i} size="sm" variant="filled" label={action.action.replace(/_/g, " ").toLowerCase()} />
          ))}
          {step.actions.length > 2 && (
            <Chip size="sm" variant="filled" label={`+${step.actions.length - 2}`} />
          )}
        </div>
        
        {/* Duration indicator */}
        <div className="mt-2 text-xs text-gray-500">
          {formatDuration(step.actions.reduce((d: number, a: SimAction) => Math.max(d, (a.duration || 500)), 0))}
        </div>
      </div>
    </div>
  );
};

interface StepEditorPanelProps {
  step: SimulationStep;
  onUpdate: (updates: Partial<SimulationStep>) => void;
  onActionUpdate: (actionIndex: number, updates: Partial<SimAction>) => void;
  onAddAction: (action: Partial<SimAction>) => void;
  onRemoveAction: (actionIndex: number) => void;
  domain: string;
  readOnly: boolean;
}

const StepEditorPanel = ({
  step,
  onUpdate,
  onActionUpdate,
  onAddAction,
  onRemoveAction,
  domain,
  readOnly,
}: StepEditorPanelProps) => {
  const palettes = ACTION_PALETTES[domain] || DEFAULT_PALETTE;
  
  return (
    <div className="p-4 space-y-4 overflow-y-auto max-h-[calc(100vh-300px)]">
      {/* Step Metadata */}
      <div className="space-y-3">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Title
          </label>
          <input
            type="text"
            value={step.title || ""}
            onChange={(e) => onUpdate({ title: e.target.value })}
            disabled={readOnly}
            placeholder="Step title..."
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
          />
        </div>
        
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Narration
          </label>
          <textarea
            value={step.narration || ""}
            onChange={(e) => onUpdate({ narration: e.target.value })}
            disabled={readOnly}
            placeholder="Explain what happens in this step..."
            rows={2}
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
          />
        </div>
        
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={step.checkpoint || false}
              onChange={(e) => onUpdate({ checkpoint: e.target.checked })}
              disabled={readOnly}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700 dark:text-gray-300">Checkpoint</span>
          </label>
          
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={step.breakpoint || false}
              onChange={(e) => onUpdate({ breakpoint: e.target.checked })}
              disabled={readOnly}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700 dark:text-gray-300">Breakpoint</span>
          </label>
        </div>
      </div>
      
      {/* Divider */}
      <hr className="border-gray-200 dark:border-gray-700" />
      
      {/* Actions List */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Actions ({step.actions.length})
        </h4>
        
        <div className="space-y-2">
          {step.actions.map((action: SimAction, idx: number) => (
            <div
              key={idx}
              className="flex items-center gap-2 p-2 bg-gray-50 dark:bg-gray-900 rounded-md"
            >
              <span className="flex-1 text-sm font-mono truncate">
                {action.action}
              </span>
              
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  value={action.duration || 500}
                  onChange={(e) => onActionUpdate(idx, { duration: Number(e.target.value) })}
                  disabled={readOnly}
                  min={0}
                  step={100}
                  className="w-20 px-2 py-1 text-xs border border-gray-300 rounded"
                  title="Duration (ms)"
                />
                
                <select
                  value={action.easing || "easeInOut"}
                  onChange={(e) => onActionUpdate(idx, { easing: e.target.value as EasingFunction })}
                  disabled={readOnly}
                  className="text-xs border border-gray-300 rounded px-1 py-1"
                >
                  <option value="linear">linear</option>
                  <option value="easeIn">easeIn</option>
                  <option value="easeOut">easeOut</option>
                  <option value="easeInOut">easeInOut</option>
                  <option value="spring">spring</option>
                </select>
                
                {!readOnly && (
                  <button
                    onClick={() => onRemoveAction(idx)}
                    className="p-1 text-red-500 hover:text-red-700"
                  >
                    ✕
                  </button>
                )}
              </div>
            </div>
          ))}
          
          {step.actions.length === 0 && (
            <p className="text-sm text-gray-500 italic">
              No actions yet. Add actions from the palette below.
            </p>
          )}
        </div>
      </div>
      
      {/* Divider */}
      <hr className="border-gray-200 dark:border-gray-700" />
      
      {/* Action Palette */}
      {!readOnly && (
        <div>
          <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Add Action
          </h4>
          
          <div className="space-y-3">
            {palettes.map((palette) => (
              <div key={palette.category}>
                <p className="text-xs text-gray-500 uppercase mb-1">{palette.category}</p>
                <div className="flex flex-wrap gap-1">
                  {palette.actions.map((actionDef) => (
                    <button
                      key={actionDef.action}
                      onClick={() => onAddAction({
                        action: actionDef.action,
                        duration: actionDef.defaultDuration,
                        easing: "easeInOut" as EasingFunction,
                      })}
                      className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded hover:border-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                    >
                      <span>{actionDef.icon}</span>
                      <span>{actionDef.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
      
      {/* Assessment Hook */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Assessment Hook (optional)
        </label>
        <input
          type="text"
          value={step.assessmentHook?.prompt || ""}
          onChange={(e) => onUpdate({
            assessmentHook: e.target.value
              ? { ...step.assessmentHook, prompt: e.target.value }
              : undefined
          })}
          disabled={readOnly}
          placeholder="Question to ask learner at this step..."
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
        />
      </div>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const SimulationTimelineEditor = ({
  steps,
  onStepsChange,
  onStepSelect,
  selectedStepId,
  currentPlaybackStep = 0,
  isPlaying = false,
  onPlayPause,
  onSeek,
  readOnly = false,
  domain = "CS_DISCRETE",
}: SimulationTimelineEditorProps) => {
  const timelineRef = useRef<HTMLDivElement>(null);
  const [dragState, setDragState] = useState<DragState>({
    isDragging: false,
    draggedId: null,
    dragOverId: null,
    insertPosition: null,
  });
  
  const selectedStep = useMemo(
    () => steps.find((s) => s.id === selectedStepId),
    [steps, selectedStepId]
  );
  
  const totalDuration = useMemo(() => getTotalDuration(steps), [steps]);
  
  // --- Handlers ---
  
  const handleAddStep = useCallback(() => {
    const newStep: SimulationStep = {
      id: generateStepId(),
      orderIndex: steps.length,
      title: `Step ${steps.length + 1}`,
      duration: 1000, // default 1 second
      actions: [],
    };
    onStepsChange([...steps, newStep]);
    onStepSelect(newStep.id);
  }, [steps, onStepsChange, onStepSelect]);
  
  const handleDeleteStep = useCallback((stepId: SimStepId) => {
    const newSteps = steps
      .filter((s) => s.id !== stepId)
      .map((s, i) => ({ ...s, orderIndex: i }));
    onStepsChange(newSteps);
    if (selectedStepId === stepId) {
      onStepSelect(null);
    }
  }, [steps, selectedStepId, onStepsChange, onStepSelect]);
  
  const handleUpdateStep = useCallback((stepId: SimStepId, updates: Partial<SimulationStep>) => {
    const newSteps = steps.map((s) =>
      s.id === stepId ? { ...s, ...updates } : s
    );
    onStepsChange(newSteps);
  }, [steps, onStepsChange]);
  
  const handleActionUpdate = useCallback(
    (stepId: SimStepId, actionIndex: number, updates: Partial<SimAction>) => {
      const newSteps = steps.map((s) => {
        if (s.id !== stepId) return s;
        const newActions = [...s.actions];
        newActions[actionIndex] = { ...newActions[actionIndex], ...updates } as SimAction;
        return { ...s, actions: newActions };
      });
      onStepsChange(newSteps);
    },
    [steps, onStepsChange]
  );
  
  const handleAddAction = useCallback((stepId: SimStepId, action: Partial<SimAction>) => {
    const newSteps = steps.map((s) => {
      if (s.id !== stepId) return s;
      return { ...s, actions: [...s.actions, action as SimAction] };
    });
    onStepsChange(newSteps);
  }, [steps, onStepsChange]);
  
  const handleRemoveAction = useCallback((stepId: SimStepId, actionIndex: number) => {
    const newSteps = steps.map((s) => {
      if (s.id !== stepId) return s;
      const newActions = s.actions.filter((_: SimAction, i: number) => i !== actionIndex);
      return { ...s, actions: newActions };
    });
    onStepsChange(newSteps);
  }, [steps, onStepsChange]);
  
  // --- Drag & Drop ---
  
  const handleDragStart = useCallback((e: React.DragEvent, stepId: SimStepId) => {
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", stepId);
    setDragState((prev) => ({ ...prev, isDragging: true, draggedId: stepId }));
  }, []);
  
  const handleDragOver = useCallback((e: React.DragEvent, stepId: SimStepId) => {
    e.preventDefault();
    if (!dragState.isDragging || dragState.draggedId === stepId) return;
    
    const rect = (e.target as HTMLElement).getBoundingClientRect();
    const midpoint = rect.left + rect.width / 2;
    const position = e.clientX < midpoint ? "before" : "after";
    
    setDragState((prev) => ({
      ...prev,
      dragOverId: stepId,
      insertPosition: position,
    }));
  }, [dragState.isDragging, dragState.draggedId]);
  
  const handleDragLeave = useCallback(() => {
    setDragState((prev) => ({ ...prev, dragOverId: null, insertPosition: null }));
  }, []);
  
  const handleDrop = useCallback((e: React.DragEvent, targetId: SimStepId) => {
    e.preventDefault();
    const draggedId = e.dataTransfer.getData("text/plain") as SimStepId;
    
    if (!draggedId || draggedId === targetId) {
      setDragState({ isDragging: false, draggedId: null, dragOverId: null, insertPosition: null });
      return;
    }
    
    const draggedIndex = steps.findIndex((s) => s.id === draggedId);
    const targetIndex = steps.findIndex((s) => s.id === targetId);
    
    const newSteps = [...steps];
    const [removed] = newSteps.splice(draggedIndex, 1);
    
    let insertIndex = targetIndex;
    if (dragState.insertPosition === "after") {
      insertIndex = draggedIndex < targetIndex ? targetIndex : targetIndex + 1;
    } else {
      insertIndex = draggedIndex < targetIndex ? targetIndex - 1 : targetIndex;
    }
    
    newSteps.splice(insertIndex, 0, removed);
    
    // Update orderIndex
    const reindexed = newSteps.map((s, i) => ({ ...s, orderIndex: i }));
    onStepsChange(reindexed);
    
    setDragState({ isDragging: false, draggedId: null, dragOverId: null, insertPosition: null });
  }, [steps, dragState.insertPosition, onStepsChange]);
  
  const handleDragEnd = useCallback(() => {
    setDragState({ isDragging: false, draggedId: null, dragOverId: null, insertPosition: null });
  }, []);
  
  return (
    <div className="flex flex-col h-full bg-gray-50 dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700">
      {/* Header / Controls */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="flex items-center gap-3">
          <h3 className="font-semibold text-gray-900 dark:text-white">Timeline</h3>
          <Badge variant="outline">{steps.length} steps</Badge>
          <span className="text-xs text-gray-500">{formatDuration(totalDuration)} total</span>
        </div>
        
        <div className="flex items-center gap-2">
          {onPlayPause && (
            <Button size="sm" variant="outline" onClick={onPlayPause}>
              {isPlaying ? "⏸ Pause" : "▶ Play"}
            </Button>
          )}
          
          {!readOnly && (
            <Button size="sm" variant="solid" tone="primary" onClick={handleAddStep}>
              + Add Step
            </Button>
          )}
        </div>
      </div>
      
      {/* Timeline Strip */}
      <div
        ref={timelineRef}
        className="flex items-center gap-2 px-4 py-4 overflow-x-auto min-h-[140px] bg-gradient-to-b from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800"
        onDragEnd={handleDragEnd}
      >
        {steps.length === 0 ? (
          <div className="flex-1 flex items-center justify-center text-gray-500">
            <div className="text-center">
              <p className="text-lg mb-2">No steps yet</p>
              {!readOnly && (
                <Button variant="outline" onClick={handleAddStep}>
                  + Add First Step
                </Button>
              )}
            </div>
          </div>
        ) : (
          <>
            {steps.map((step, index) => (
              <StepTile
                key={step.id}
                step={step}
                index={index}
                isSelected={step.id === selectedStepId}
                isCurrent={index === currentPlaybackStep}
                isDragOver={dragState.dragOverId === step.id}
                insertPosition={dragState.dragOverId === step.id ? dragState.insertPosition : null}
                onSelect={() => onStepSelect(step.id)}
                onDelete={() => handleDeleteStep(step.id)}
                onDragStart={(e) => handleDragStart(e, step.id)}
                onDragOver={(e) => handleDragOver(e, step.id)}
                onDragLeave={handleDragLeave}
                onDrop={(e) => handleDrop(e, step.id)}
                readOnly={readOnly}
              />
            ))}
          </>
        )}
      </div>
      
      {/* Scrubber */}
      {onSeek && steps.length > 0 && (
        <div className="px-4 py-2 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <Slider
            min={0}
            max={steps.length - 1}
            value={currentPlaybackStep}
            onChange={(e) => onSeek(Number(e.target.value))}
            label="Step"
          />
        </div>
      )}
      
      {/* Step Editor Panel */}
      {selectedStep && (
        <div className="flex-1 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
          <StepEditorPanel
            step={selectedStep}
            onUpdate={(updates) => handleUpdateStep(selectedStep.id, updates)}
            onActionUpdate={(idx, updates) => handleActionUpdate(selectedStep.id, idx, updates)}
            onAddAction={(action) => handleAddAction(selectedStep.id, action)}
            onRemoveAction={(idx) => handleRemoveAction(selectedStep.id, idx)}
            domain={domain}
            readOnly={readOnly}
          />
        </div>
      )}
    </div>
  );
};

export default SimulationTimelineEditor;
