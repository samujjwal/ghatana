/**
 * SimulationStudio - Authoring interface for creating/editing simulations.
 *
 * @doc.type component
 * @doc.purpose Full-featured simulation authoring environment with NL support
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import type {
  SimulationManifest,
  SimKeyframe,
  SimEntityBase,
  SimulationDomain,
} from '@ghatana/tutorputor-contracts/v1/simulation';
import { SimulationCanvas } from './SimulationCanvas';
import { SimulationPlayer } from './SimulationPlayer';

/**
 * Studio props.
 */
interface SimulationStudioProps {
  /** Initial manifest (for editing) */
  initialManifest?: SimulationManifest;
  /** Save callback */
  onSave?: (manifest: SimulationManifest) => void;
  /** Cancel callback */
  onCancel?: () => void;
  /** NL refinement callback */
  onNLRefine?: (input: string) => Promise<NLRefinementResult>;
  /** AI generation callback */
  onAIGenerate?: (prompt: string, domain: SimulationDomain) => Promise<SimulationManifest>;
}

/**
 * NL refinement result.
 */
interface NLRefinementResult {
  success: boolean;
  manifest?: SimulationManifest;
  response: string;
  suggestions: string[];
}

/**
 * Panel tabs.
 */
type PanelTab = 'entities' | 'steps' | 'settings' | 'ai';

/**
 * SimulationStudio component.
 */
export const SimulationStudio: React.FC<SimulationStudioProps> = ({
  initialManifest,
  onSave,
  onCancel,
  onNLRefine,
  onAIGenerate,
}) => {
  // === State ===
  const [manifest, setManifest] = useState<SimulationManifest>(
    initialManifest ?? createEmptyManifest()
  );
  const [currentKeyframe, setCurrentKeyframe] = useState<SimKeyframe>(
    createKeyframeFromManifest(manifest, 0)
  );
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [selectedEntity, setSelectedEntity] = useState<SimEntityBase | null>(null);
  const [activeTab, setActiveTab] = useState<PanelTab>('entities');
  const [nlInput, setNlInput] = useState('');
  const [nlHistory, setNlHistory] = useState<Array<{ role: 'user' | 'assistant'; content: string }>>([]);
  const [isProcessingNL, setIsProcessingNL] = useState(false);
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiDomain, setAiDomain] = useState<SimulationDomain>('discrete');
  const [isGenerating, setIsGenerating] = useState(false);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  const nlInputRef = useRef<HTMLInputElement>(null);
  const playIntervalRef = useRef<number | null>(null);

  // === Computed State ===
  const totalSteps = manifest.steps.length;
  const totalDuration = manifest.steps.reduce((sum, step) => sum + (step.duration ?? 1000), 0);
  const currentTime = manifest.steps
    .slice(0, currentStepIndex)
    .reduce((sum, step) => sum + (step.duration ?? 1000), 0);

  // === Effects ===
  useEffect(() => {
    setCurrentKeyframe(createKeyframeFromManifest(manifest, currentStepIndex));
  }, [manifest, currentStepIndex]);

  useEffect(() => {
    if (isPlaying && currentStepIndex < totalSteps - 1) {
      const stepDuration = (manifest.steps[currentStepIndex]?.duration ?? 1000) / playbackSpeed;
      playIntervalRef.current = window.setTimeout(() => {
        setCurrentStepIndex((prev) => prev + 1);
      }, stepDuration);
    } else {
      setIsPlaying(false);
    }

    return () => {
      if (playIntervalRef.current) {
        window.clearTimeout(playIntervalRef.current);
      }
    };
  }, [isPlaying, currentStepIndex, totalSteps, manifest.steps, playbackSpeed]);

  // === Handlers ===
  const handlePlay = useCallback(() => setIsPlaying(true), []);
  const handlePause = useCallback(() => setIsPlaying(false), []);

  const handleStepForward = useCallback(() => {
    if (currentStepIndex < totalSteps - 1) {
      setCurrentStepIndex((prev) => prev + 1);
    }
  }, [currentStepIndex, totalSteps]);

  const handleStepBackward = useCallback(() => {
    if (currentStepIndex > 0) {
      setCurrentStepIndex((prev) => prev - 1);
    }
  }, [currentStepIndex]);

  const handleSeek = useCallback(
    (timeMs: number) => {
      let accumulatedTime = 0;
      for (let i = 0; i < manifest.steps.length; i++) {
        const stepDuration = manifest.steps[i].duration ?? 1000;
        if (accumulatedTime + stepDuration > timeMs) {
          setCurrentStepIndex(i);
          return;
        }
        accumulatedTime += stepDuration;
      }
      setCurrentStepIndex(manifest.steps.length - 1);
    },
    [manifest.steps]
  );

  const handleReset = useCallback(() => {
    setCurrentStepIndex(0);
    setIsPlaying(false);
  }, []);

  const handleSpeedChange = useCallback((speed: number) => {
    setPlaybackSpeed(speed);
  }, []);

  const handleEntityClick = useCallback((entity: SimEntityBase) => {
    setSelectedEntity(entity);
    setActiveTab('entities');
  }, []);

  const handleNLSubmit = useCallback(async () => {
    if (!nlInput.trim() || !onNLRefine) return;

    setIsProcessingNL(true);
    setNlHistory((prev) => [...prev, { role: 'user', content: nlInput }]);

    try {
      const result = await onNLRefine(nlInput);

      if (result.success && result.manifest) {
        setManifest(result.manifest);
        setHasUnsavedChanges(true);
      }

      setNlHistory((prev) => [
        ...prev,
        { role: 'assistant', content: result.response },
      ]);
    } catch (error) {
      setNlHistory((prev) => [
        ...prev,
        { role: 'assistant', content: 'Sorry, something went wrong. Please try again.' },
      ]);
    }

    setNlInput('');
    setIsProcessingNL(false);
    nlInputRef.current?.focus();
  }, [nlInput, onNLRefine]);

  const handleAIGenerate = useCallback(async () => {
    if (!aiPrompt.trim() || !onAIGenerate) return;

    setIsGenerating(true);

    try {
      const generatedManifest = await onAIGenerate(aiPrompt, aiDomain);
      setManifest(generatedManifest);
      setHasUnsavedChanges(true);
      setCurrentStepIndex(0);
      setAiPrompt('');
    } catch (error) {
      console.error('Generation failed:', error);
    }

    setIsGenerating(false);
  }, [aiPrompt, aiDomain, onAIGenerate]);

  const handleSave = useCallback(() => {
    onSave?.(manifest);
    setHasUnsavedChanges(false);
  }, [manifest, onSave]);

  const handleManifestChange = useCallback((updates: Partial<SimulationManifest>) => {
    setManifest((prev) => ({ ...prev, ...updates }));
    setHasUnsavedChanges(true);
  }, []);

  // === Render ===
  return (
    <div className="flex flex-col h-full bg-slate-50 dark:bg-slate-900">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-4 bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700">
        <div className="flex items-center gap-4">
          <h1 className="text-xl font-semibold text-slate-900 dark:text-white">
            Simulation Studio
          </h1>
          {hasUnsavedChanges && (
            <span className="text-sm text-amber-600 dark:text-amber-400">
              • Unsaved changes
            </span>
          )}
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={!hasUnsavedChanges}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
          >
            Save
          </button>
        </div>
      </header>

      {/* Main content */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left panel: Editor */}
        <div className="w-80 flex flex-col bg-white dark:bg-slate-800 border-r border-slate-200 dark:border-slate-700">
          {/* Tabs */}
          <div className="flex border-b border-slate-200 dark:border-slate-700">
            {(['entities', 'steps', 'settings', 'ai'] as const).map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`flex-1 px-4 py-3 text-sm font-medium capitalize transition-colors ${
                  activeTab === tab
                    ? 'text-blue-600 border-b-2 border-blue-600'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-y-auto p-4">
            {activeTab === 'entities' && (
              <EntitiesPanel
                entities={manifest.entities}
                selectedEntity={selectedEntity}
                onSelectEntity={setSelectedEntity}
                onUpdateEntities={(entities) => handleManifestChange({ entities })}
              />
            )}

            {activeTab === 'steps' && (
              <StepsPanel
                steps={manifest.steps}
                currentStepIndex={currentStepIndex}
                onSelectStep={setCurrentStepIndex}
                onUpdateSteps={(steps) => handleManifestChange({ steps })}
              />
            )}

            {activeTab === 'settings' && (
              <SettingsPanel
                manifest={manifest}
                onUpdateManifest={handleManifestChange}
              />
            )}

            {activeTab === 'ai' && (
              <AIPanel
                aiPrompt={aiPrompt}
                aiDomain={aiDomain}
                isGenerating={isGenerating}
                onPromptChange={setAiPrompt}
                onDomainChange={setAiDomain}
                onGenerate={handleAIGenerate}
                hasGenerateHandler={!!onAIGenerate}
              />
            )}
          </div>
        </div>

        {/* Center: Canvas */}
        <div className="flex-1 flex flex-col p-6 gap-4">
          <div className="flex-1 flex items-center justify-center bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden">
            <SimulationCanvas
              keyframe={currentKeyframe}
              width={800}
              height={500}
              showGrid={true}
              onEntityClick={handleEntityClick}
              enableControls={true}
            />
          </div>

          {/* Player controls */}
          <SimulationPlayer
            state={{
              sessionId: 'studio-preview' as any,
              manifestId: manifest.id,
              currentStepIndex,
              totalSteps,
              currentTime,
              totalDuration,
              isPlaying,
              playbackSpeed,
              currentKeyframe,
              analytics: {},
            }}
            onPlay={handlePlay}
            onPause={handlePause}
            onStepForward={handleStepForward}
            onStepBackward={handleStepBackward}
            onSeek={handleSeek}
            onSpeedChange={handleSpeedChange}
            onReset={handleReset}
          />
        </div>

        {/* Right panel: NL Chat */}
        {onNLRefine && (
          <div className="w-80 flex flex-col bg-white dark:bg-slate-800 border-l border-slate-200 dark:border-slate-700">
            <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-700">
              <h2 className="font-medium text-slate-900 dark:text-white">
                Refine with AI
              </h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Describe changes in natural language
              </p>
            </div>

            {/* Chat history */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {nlHistory.length === 0 && (
                <div className="text-sm text-slate-500 dark:text-slate-400 text-center py-8">
                  <p className="mb-4">Try saying:</p>
                  <div className="space-y-2">
                    <p className="text-slate-600 dark:text-slate-300">"Add a new element"</p>
                    <p className="text-slate-600 dark:text-slate-300">"Make it blue"</p>
                    <p className="text-slate-600 dark:text-slate-300">"Slow down the animation"</p>
                  </div>
                </div>
              )}

              {nlHistory.map((msg, i) => (
                <div
                  key={i}
                  className={`p-3 rounded-lg text-sm ${
                    msg.role === 'user'
                      ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-900 dark:text-blue-100 ml-4'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-900 dark:text-slate-100 mr-4'
                  }`}
                >
                  {msg.content}
                </div>
              ))}
            </div>

            {/* Input */}
            <div className="p-4 border-t border-slate-200 dark:border-slate-700">
              <div className="flex gap-2">
                <input
                  ref={nlInputRef}
                  type="text"
                  value={nlInput}
                  onChange={(e) => setNlInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleNLSubmit()}
                  placeholder="Type a command..."
                  disabled={isProcessingNL}
                  className="flex-1 px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <button
                  onClick={handleNLSubmit}
                  disabled={!nlInput.trim() || isProcessingNL}
                  className="px-3 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  {isProcessingNL ? (
                    <svg className="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                  ) : (
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                    </svg>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

// === Sub-components ===

interface EntitiesPanelProps {
  entities: SimEntityBase[];
  selectedEntity: SimEntityBase | null;
  onSelectEntity: (entity: SimEntityBase | null) => void;
  onUpdateEntities: (entities: SimEntityBase[]) => void;
}

const EntitiesPanel: React.FC<EntitiesPanelProps> = ({
  entities,
  selectedEntity,
  onSelectEntity,
  onUpdateEntities,
}) => {
  const addEntity = () => {
    const newEntity: SimEntityBase = {
      id: crypto.randomUUID(),
      label: `Element ${entities.length + 1}`,
      entityType: 'element',
      visual: { color: '#4A90D9', size: 1, shape: 'rectangle', opacity: 1 },
      position: { x: Math.random() * 200 - 100, y: Math.random() * 200 - 100 },
    };
    onUpdateEntities([...entities, newEntity]);
  };

  const removeEntity = (id: string) => {
    onUpdateEntities(entities.filter((e) => e.id !== id));
    if (selectedEntity?.id === id) onSelectEntity(null);
  };

  return (
    <div className="space-y-4">
      <button
        onClick={addEntity}
        className="w-full px-4 py-2 text-sm font-medium text-blue-600 border border-blue-600 rounded-lg hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
      >
        + Add Entity
      </button>

      <div className="space-y-2">
        {entities.map((entity) => (
          <div
            key={entity.id}
            onClick={() => onSelectEntity(entity)}
            className={`flex items-center justify-between p-3 rounded-lg cursor-pointer transition-colors ${
              selectedEntity?.id === entity.id
                ? 'bg-blue-100 dark:bg-blue-900/30 border border-blue-300 dark:border-blue-700'
                : 'bg-slate-50 dark:bg-slate-700/50 hover:bg-slate-100 dark:hover:bg-slate-700'
            }`}
          >
            <div className="flex items-center gap-3">
              <div
                className="w-4 h-4 rounded"
                style={{ backgroundColor: entity.visual?.color ?? '#4A90D9' }}
              />
              <span className="text-sm font-medium text-slate-900 dark:text-white">
                {entity.label}
              </span>
            </div>
            <button
              onClick={(e) => {
                e.stopPropagation();
                removeEntity(entity.id);
              }}
              className="p-1 text-slate-400 hover:text-red-500 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

interface StepsPanelProps {
  steps: SimulationManifest['steps'];
  currentStepIndex: number;
  onSelectStep: (index: number) => void;
  onUpdateSteps: (steps: SimulationManifest['steps']) => void;
}

const StepsPanel: React.FC<StepsPanelProps> = ({
  steps,
  currentStepIndex,
  onSelectStep,
  onUpdateSteps,
}) => {
  const addStep = () => {
    const newStep = {
      id: crypto.randomUUID(),
      stepNumber: steps.length + 1,
      description: `Step ${steps.length + 1}`,
      algorithm: 'custom',
      actions: [],
      duration: 1000,
    };
    onUpdateSteps([...steps, newStep]);
  };

  return (
    <div className="space-y-4">
      <button
        onClick={addStep}
        className="w-full px-4 py-2 text-sm font-medium text-blue-600 border border-blue-600 rounded-lg hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
      >
        + Add Step
      </button>

      <div className="space-y-2">
        {steps.map((step, index) => (
          <div
            key={step.id}
            onClick={() => onSelectStep(index)}
            className={`p-3 rounded-lg cursor-pointer transition-colors ${
              currentStepIndex === index
                ? 'bg-blue-100 dark:bg-blue-900/30 border border-blue-300 dark:border-blue-700'
                : 'bg-slate-50 dark:bg-slate-700/50 hover:bg-slate-100 dark:hover:bg-slate-700'
            }`}
          >
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                Step {index + 1}
              </span>
              <span className="text-xs text-slate-400">
                {step.duration ?? 1000}ms
              </span>
            </div>
            <p className="text-sm text-slate-900 dark:text-white line-clamp-2">
              {step.description}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
};

interface SettingsPanelProps {
  manifest: SimulationManifest;
  onUpdateManifest: (updates: Partial<SimulationManifest>) => void;
}

const SettingsPanel: React.FC<SettingsPanelProps> = ({ manifest, onUpdateManifest }) => {
  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
          Title
        </label>
        <input
          type="text"
          value={manifest.title}
          onChange={(e) => onUpdateManifest({ title: e.target.value })}
          className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
          Description
        </label>
        <textarea
          value={manifest.description ?? ''}
          onChange={(e) => onUpdateManifest({ description: e.target.value })}
          rows={3}
          className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
          Domain
        </label>
        <select
          value={manifest.domain}
          onChange={(e) => onUpdateManifest({ domain: e.target.value as SimulationDomain })}
          className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="discrete">Discrete (Algorithms)</option>
          <option value="physics">Physics</option>
          <option value="chemistry">Chemistry</option>
          <option value="biology">Biology</option>
          <option value="economics">Economics</option>
          <option value="medicine">Medicine</option>
        </select>
      </div>
    </div>
  );
};

interface AIPanelProps {
  aiPrompt: string;
  aiDomain: SimulationDomain;
  isGenerating: boolean;
  onPromptChange: (value: string) => void;
  onDomainChange: (value: SimulationDomain) => void;
  onGenerate: () => void;
  hasGenerateHandler: boolean;
}

const AIPanel: React.FC<AIPanelProps> = ({
  aiPrompt,
  aiDomain,
  isGenerating,
  onPromptChange,
  onDomainChange,
  onGenerate,
  hasGenerateHandler,
}) => {
  if (!hasGenerateHandler) {
    return (
      <div className="text-sm text-slate-500 dark:text-slate-400 text-center py-8">
        AI generation is not available.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
          Domain
        </label>
        <select
          value={aiDomain}
          onChange={(e) => onDomainChange(e.target.value as SimulationDomain)}
          className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="discrete">Algorithms</option>
          <option value="physics">Physics</option>
          <option value="chemistry">Chemistry</option>
          <option value="biology">Biology</option>
          <option value="economics">Economics</option>
          <option value="medicine">Medicine</option>
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
          Describe your simulation
        </label>
        <textarea
          value={aiPrompt}
          onChange={(e) => onPromptChange(e.target.value)}
          placeholder="e.g., Show how bubble sort works on a list of 5 numbers"
          rows={4}
          className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        />
      </div>

      <button
        onClick={onGenerate}
        disabled={!aiPrompt.trim() || isGenerating}
        className="w-full px-4 py-2 text-sm font-medium text-white bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
      >
        {isGenerating ? (
          <>
            <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Generating...
          </>
        ) : (
          <>
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            Generate Simulation
          </>
        )}
      </button>
    </div>
  );
};

// === Helpers ===

function createEmptyManifest(): SimulationManifest {
  return {
    id: crypto.randomUUID(),
    version: '1.0',
    domain: 'discrete',
    title: 'New Simulation',
    description: '',
    entities: [],
    steps: [],
    keyframes: [],
  };
}

function createKeyframeFromManifest(manifest: SimulationManifest, stepIndex: number): SimKeyframe {
  return {
    id: crypto.randomUUID(),
    timestamp: 0,
    stepIndex,
    entities: manifest.entities,
    annotations: manifest.steps[stepIndex]?.annotations ?? [],
    camera: { x: 0, y: 0, zoom: 1 },
  };
}

export default SimulationStudio;
