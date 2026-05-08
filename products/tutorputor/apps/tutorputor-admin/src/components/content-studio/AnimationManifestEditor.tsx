/**
 * Animation Manifest Editor Component
 *
 * Schema-backed component for editing animation manifests.
 * Provides UI for managing animation configuration, scene graph,
 * timeline segments, cueing rules, and accessibility metadata.
 *
 * @doc.type component
 * @doc.purpose Edit animation manifests
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { Plus, Trash2, ChevronUp, ChevronDown, Play, Film, Clock } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface TimelineSegment {
  id: string;
  startTime: number; // seconds
  endTime: number; // seconds
  description: string;
  keyframes: Array<{
    time: number;
    properties: Record<string, unknown>;
  }>;
}

export interface CueingRule {
  id: string;
  trigger: "time" | "user_action" | "animation_complete";
  condition: string;
  action: "pause" | "highlight" | "show_caption" | "advance";
  parameters: Record<string, unknown>;
}

export interface AnimationManifest {
  schemaVersion: string;
  manifestType: "Animation";
  claimRef: string;
  evidenceRefs: string[];
  objectiveRefs: string[];
  domain: string;
  gradeBand: string;
  title: string;
  description: string;
  type: "2d" | "3d" | "timeline";
  duration: number; // seconds
  sceneGraph: Record<string, unknown>;
  timeline: TimelineSegment[];
  cueingRules: CueingRule[];
  narrationHooks: string[];
  pacingMetadata: {
    averagePace: "slow" | "medium" | "fast";
    pausePoints: number[];
  };
  learnerControls: {
    playPause: boolean;
    scrubbing: boolean;
    speedControl: boolean;
    captions: boolean;
  };
  reducedMotionBehavior: "respect" | "fallback" | "disable";
  captionsTextAlternatives: string[];
  accessibilityMetadata?: {
    screenReaderDescriptions: string[];
    audioDescriptions: string[];
  };
}

interface AnimationManifestEditorProps {
  manifest: AnimationManifest;
  onChange: (manifest: AnimationManifest) => void;
  readonly?: boolean;
}

const DOMAINS = [
  "MATHEMATICS",
  "PHYSICS",
  "CHEMISTRY",
  "BIOLOGY",
  "COMPUTER_SCIENCE",
  "ECONOMICS",
  "ENGINEERING",
  "MEDICINE",
] as const;

const GRADE_BANDS = [
  "GRADE_K_2",
  "GRADE_3_5",
  "GRADE_6_8",
  "GRADE_9_12",
  "UNDERGRADUATE",
  "GRADUATE",
] as const;

const ANIMATION_TYPES = ["2d", "3d", "timeline"] as const;

const PACE_OPTIONS = ["slow", "medium", "fast"] as const;

const REDUCED_MOTION_OPTIONS = ["respect", "fallback", "disable"] as const;

export function AnimationManifestEditor({
  manifest,
  onChange,
  readonly = false,
}: AnimationManifestEditorProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(["basic", "timeline", "cueing"]),
  );

  const toggleSection = (section: string) => {
    const newExpanded = new Set(expandedSections);
    if (newExpanded.has(section)) {
      newExpanded.delete(section);
    } else {
      newExpanded.add(section);
    }
    setExpandedSections(newExpanded);
  };

  const updateManifest = (updates: Partial<AnimationManifest>) => {
    onChange({ ...manifest, ...updates });
  };

  const addTimelineSegment = () => {
    const newSegment: TimelineSegment = {
      id: `segment-${Date.now()}`,
      startTime: manifest.timeline.length > 0 ? manifest.timeline[manifest.timeline.length - 1].endTime : 0,
      endTime: manifest.timeline.length > 0 ? manifest.timeline[manifest.timeline.length - 1].endTime + 5 : 5,
      description: "",
      keyframes: [],
    };
    updateManifest({
      timeline: [...manifest.timeline, newSegment],
    });
  };

  const updateTimelineSegment = (segmentId: string, updates: Partial<TimelineSegment>) => {
    updateManifest({
      timeline: manifest.timeline.map((segment) =>
        segment.id === segmentId ? { ...segment, ...updates } : segment,
      ),
    });
  };

  const deleteTimelineSegment = (segmentId: string) => {
    updateManifest({
      timeline: manifest.timeline.filter((segment) => segment.id !== segmentId),
    });
  };

  const addCueingRule = () => {
    const newRule: CueingRule = {
      id: `rule-${Date.now()}`,
      trigger: "time",
      condition: "",
      action: "pause",
      parameters: {},
    };
    updateManifest({
      cueingRules: [...manifest.cueingRules, newRule],
    });
  };

  const updateCueingRule = (ruleId: string, updates: Partial<CueingRule>) => {
    updateManifest({
      cueingRules: manifest.cueingRules.map((rule) =>
        rule.id === ruleId ? { ...rule, ...updates } : rule,
      ),
    });
  };

  const deleteCueingRule = (ruleId: string) => {
    updateManifest({
      cueingRules: manifest.cueingRules.filter((rule) => rule.id !== ruleId),
    });
  };

  const addNarrationHook = () => {
    updateManifest({
      narrationHooks: [...manifest.narrationHooks, ""],
    });
  };

  const updateNarrationHook = (index: number, value: string) => {
    const hooks = [...manifest.narrationHooks];
    hooks[index] = value;
    updateManifest({ narrationHooks: hooks });
  };

  const deleteNarrationHook = (index: number) => {
    const hooks = [...manifest.narrationHooks];
    hooks.splice(index, 1);
    updateManifest({ narrationHooks: hooks });
  };

  return (
    <div className="space-y-4">
      {/* Basic Information */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("basic")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <div className="flex items-center gap-2">
            <Film className="w-4 h-4" />
            <span className="font-semibold">Basic Information</span>
          </div>
          {expandedSections.has("basic") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("basic") && (
          <div className="p-4 space-y-3">
            <div>
              <label className="block text-sm font-medium mb-1">Title</label>
              <input
                type="text"
                value={manifest.title}
                onChange={(e) => updateManifest({ title: e.target.value })}
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Description</label>
              <textarea
                value={manifest.description}
                onChange={(e) => updateManifest({ description: e.target.value })}
                rows={2}
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium mb-1">Domain</label>
                <select
                  value={manifest.domain}
                  onChange={(e) => updateManifest({ domain: e.target.value })}
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                >
                  {DOMAINS.map((domain) => (
                    <option key={domain} value={domain}>
                      {domain}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">Grade Band</label>
                <select
                  value={manifest.gradeBand}
                  onChange={(e) => updateManifest({ gradeBand: e.target.value })}
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                >
                  {GRADE_BANDS.map((band) => (
                    <option key={band} value={band}>
                      {band}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium mb-1">Type</label>
                <select
                  value={manifest.type}
                  onChange={(e) =>
                    updateManifest({ type: e.target.value as AnimationManifest["type"] })
                  }
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                >
                  {ANIMATION_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type.toUpperCase()}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">
                  Duration (seconds)
                </label>
                <input
                  type="number"
                  min="1"
                  value={manifest.duration}
                  onChange={(e) =>
                    updateManifest({ duration: parseInt(e.target.value, 10) })
                  }
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Claim Reference</label>
              <input
                type="text"
                value={manifest.claimRef}
                onChange={(e) => updateManifest({ claimRef: e.target.value })}
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
                placeholder="claim-123"
              />
            </div>
          </div>
        )}
      </div>

      {/* Timeline */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("timeline")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4" />
            <span className="font-semibold">Timeline ({manifest.timeline.length} segments)</span>
          </div>
          {expandedSections.has("timeline") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("timeline") && (
          <div className="p-4 space-y-3">
            {!readonly && (
              <Button onClick={addTimelineSegment} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Segment
              </Button>
            )}

            {manifest.timeline.length === 0 ? (
              <div className="text-center py-4 text-gray-500 italic">
                No timeline segments defined yet
              </div>
            ) : (
              <div className="space-y-3">
                {manifest.timeline.map((segment, index) => (
                  <div key={segment.id} className="border rounded-lg p-3 bg-gray-50">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-mono text-gray-500">
                        Segment {index + 1}
                      </span>
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteTimelineSegment(segment.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      )}
                    </div>

                    <div className="grid grid-cols-2 gap-2 mb-2">
                      <div>
                        <label className="block text-xs font-medium mb-1">
                          Start Time (s)
                        </label>
                        <input
                          type="number"
                          min="0"
                          value={segment.startTime}
                          onChange={(e) =>
                            updateTimelineSegment(segment.id, {
                              startTime: parseFloat(e.target.value),
                            })
                          }
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-medium mb-1">
                          End Time (s)
                        </label>
                        <input
                          type="number"
                          min="0"
                          value={segment.endTime}
                          onChange={(e) =>
                            updateTimelineSegment(segment.id, {
                              endTime: parseFloat(e.target.value),
                            })
                          }
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                        />
                      </div>
                    </div>

                    <textarea
                      value={segment.description}
                      onChange={(e) =>
                        updateTimelineSegment(segment.id, { description: e.target.value })
                      }
                      rows={1}
                      className="w-full px-2 py-1 border rounded text-sm"
                      disabled={readonly}
                      placeholder="Description of this segment..."
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Cueing Rules */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("cueing")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <div className="flex items-center gap-2">
            <Play className="w-4 h-4" />
            <span className="font-semibold">Cueing Rules ({manifest.cueingRules.length})</span>
          </div>
          {expandedSections.has("cueing") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("cueing") && (
          <div className="p-4 space-y-3">
            {!readonly && (
              <Button onClick={addCueingRule} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Rule
              </Button>
            )}

            {manifest.cueingRules.length === 0 ? (
              <div className="text-center py-4 text-gray-500 italic">
                No cueing rules defined yet
              </div>
            ) : (
              <div className="space-y-3">
                {manifest.cueingRules.map((rule) => (
                  <div key={rule.id} className="border rounded-lg p-3 bg-gray-50">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium">{rule.action.toUpperCase()}</span>
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteCueingRule(rule.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      )}
                    </div>

                    <div className="grid grid-cols-2 gap-2 mb-2">
                      <select
                        value={rule.trigger}
                        onChange={(e) =>
                          updateCueingRule(rule.id, {
                            trigger: e.target.value as CueingRule["trigger"],
                          })
                        }
                        className="px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      >
                        <option value="time">Time</option>
                        <option value="user_action">User Action</option>
                        <option value="animation_complete">Animation Complete</option>
                      </select>

                      <select
                        value={rule.action}
                        onChange={(e) =>
                          updateCueingRule(rule.id, {
                            action: e.target.value as CueingRule["action"],
                          })
                        }
                        className="px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      >
                        <option value="pause">Pause</option>
                        <option value="highlight">Highlight</option>
                        <option value="show_caption">Show Caption</option>
                        <option value="advance">Advance</option>
                      </select>
                    </div>

                    <input
                      type="text"
                      value={rule.condition}
                      onChange={(e) =>
                        updateCueingRule(rule.id, { condition: e.target.value })
                      }
                      placeholder="Condition (e.g., time > 5)"
                      className="w-full px-2 py-1 border rounded text-sm"
                      disabled={readonly}
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Narration Hooks */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("narration")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Narration Hooks ({manifest.narrationHooks.length})</span>
          {expandedSections.has("narration") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("narration") && (
          <div className="p-4 space-y-2">
            {!readonly && (
              <Button onClick={addNarrationHook} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Hook
              </Button>
            )}

            {manifest.narrationHooks.map((hook, index) => (
              <div key={index} className="flex gap-2">
                <input
                  type="text"
                  value={hook}
                  onChange={(e) => updateNarrationHook(index, e.target.value)}
                  placeholder="Narration hook point..."
                  className="flex-1 px-3 py-2 border rounded"
                  disabled={readonly}
                />
                {!readonly && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => deleteNarrationHook(index)}
                  >
                    <Trash2 className="w-4 h-4 text-red-500" />
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Learner Controls */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("controls")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Learner Controls</span>
          {expandedSections.has("controls") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("controls") && (
          <div className="p-4 space-y-2">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.learnerControls.playPause}
                onChange={(e) =>
                  updateManifest({
                    learnerControls: {
                      ...manifest.learnerControls,
                      playPause: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Play/Pause</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.learnerControls.scrubbing}
                onChange={(e) =>
                  updateManifest({
                    learnerControls: {
                      ...manifest.learnerControls,
                      scrubbing: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Scrubbing</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.learnerControls.speedControl}
                onChange={(e) =>
                  updateManifest({
                    learnerControls: {
                      ...manifest.learnerControls,
                      speedControl: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Speed Control</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.learnerControls.captions}
                onChange={(e) =>
                  updateManifest({
                    learnerControls: {
                      ...manifest.learnerControls,
                      captions: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Captions</span>
            </label>
          </div>
        )}
      </div>

      {/* Accessibility */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("accessibility")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Accessibility</span>
          {expandedSections.has("accessibility") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("accessibility") && (
          <div className="p-4 space-y-3">
            <div>
              <label className="block text-sm font-medium mb-1">
                Reduced Motion Behavior
              </label>
              <select
                value={manifest.reducedMotionBehavior}
                onChange={(e) =>
                  updateManifest({
                    reducedMotionBehavior: e.target.value as AnimationManifest["reducedMotionBehavior"],
                  })
                }
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
              >
                {REDUCED_MOTION_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option.toUpperCase()}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">
                Captions Text Alternatives
              </label>
              <textarea
                value={manifest.captionsTextAlternatives.join("\n")}
                onChange={(e) =>
                  updateManifest({
                    captionsTextAlternatives: e.target.value.split("\n").filter(Boolean),
                  })
                }
                rows={3}
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
                placeholder="One alternative per line..."
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
