/**
 * Simulation Manifest Editor Component
 *
 * Schema-backed component for editing simulation manifests.
 * Provides UI for managing simulation configuration, parameters,
 * success criteria, and accessibility metadata.
 *
 * @doc.type component
 * @doc.purpose Edit simulation manifests
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { Plus, Trash2, ChevronUp, ChevronDown, Settings, Play } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface SimulationParameter {
  id: string;
  name: string;
  type: "number" | "string" | "boolean" | "enum";
  defaultValue: unknown;
  minValue?: number;
  maxValue?: number;
  allowedValues?: string[];
  description: string;
  editable: boolean;
}

export interface SimulationSuccessCriterion {
  id: string;
  type: "rmse" | "max_attempts" | "time_limit" | "custom";
  threshold?: number;
  maxAttempts?: number;
  timeLimit?: number;
  customRule?: string;
  description: string;
}

export interface SimulationManifest {
  schemaVersion: string;
  manifestType: "Simulation";
  claimRef: string;
  evidenceRefs: string[];
  objectiveRefs: string[];
  domain: string;
  gradeBand: string;
  title: string;
  description: string;
  interactionType: "parameter_exploration" | "prediction" | "construction";
  goal: string;
  successCriteria: SimulationSuccessCriterion[];
  estimatedMinutes: number;
  parameters: SimulationParameter[];
  telemetryProfile?: {
    collectSnapshots: boolean;
    collectControlChanges: boolean;
    collectFinalState: boolean;
  };
  accessibilityMetadata?: {
    reducedMotionSupport: boolean;
    keyboardNavigable: boolean;
    screenReaderCompatible: boolean;
    highContrastMode: boolean;
  };
}

interface SimulationManifestEditorProps {
  manifest: SimulationManifest;
  onChange: (manifest: SimulationManifest) => void;
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

const INTERACTION_TYPES = [
  "parameter_exploration",
  "prediction",
  "construction",
] as const;

const PARAMETER_TYPES = ["number", "string", "boolean", "enum"] as const;

const SUCCESS_CRITERION_TYPES = ["rmse", "max_attempts", "time_limit", "custom"] as const;

export function SimulationManifestEditor({
  manifest,
  onChange,
  readonly = false,
}: SimulationManifestEditorProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(["basic", "parameters", "success"]),
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

  const updateManifest = (updates: Partial<SimulationManifest>) => {
    onChange({ ...manifest, ...updates });
  };

  const addParameter = () => {
    const newParameter: SimulationParameter = {
      id: `param-${Date.now()}`,
      name: "",
      type: "number",
      defaultValue: 0,
      description: "",
      editable: true,
    };
    updateManifest({
      parameters: [...manifest.parameters, newParameter],
    });
  };

  const updateParameter = (paramId: string, updates: Partial<SimulationParameter>) => {
    updateManifest({
      parameters: manifest.parameters.map((param) =>
        param.id === paramId ? { ...param, ...updates } : param,
      ),
    });
  };

  const deleteParameter = (paramId: string) => {
    updateManifest({
      parameters: manifest.parameters.filter((param) => param.id !== paramId),
    });
  };

  const addSuccessCriterion = () => {
    const newCriterion: SimulationSuccessCriterion = {
      id: `criterion-${Date.now()}`,
      type: "rmse",
      threshold: 0.1,
      description: "",
    };
    updateManifest({
      successCriteria: [...manifest.successCriteria, newCriterion],
    });
  };

  const updateSuccessCriterion = (
    criterionId: string,
    updates: Partial<SimulationSuccessCriterion>,
  ) => {
    updateManifest({
      successCriteria: manifest.successCriteria.map((criterion) =>
        criterion.id === criterionId ? { ...criterion, ...updates } : criterion,
      ),
    });
  };

  const deleteSuccessCriterion = (criterionId: string) => {
    updateManifest({
      successCriteria: manifest.successCriteria.filter(
        (criterion) => criterion.id !== criterionId,
      ),
    });
  };

  return (
    <div className="space-y-4">
      {/* Basic Information */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("basic")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Basic Information</span>
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

            <div>
              <label className="block text-sm font-medium mb-1">Interaction Type</label>
              <select
                value={manifest.interactionType}
                onChange={(e) =>
                  updateManifest({
                    interactionType: e.target.value as SimulationManifest["interactionType"],
                  })
                }
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
              >
                {INTERACTION_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type.replace("_", " ").toUpperCase()}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Goal</label>
              <textarea
                value={manifest.goal}
                onChange={(e) => updateManifest({ goal: e.target.value })}
                rows={2}
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
                placeholder="What should learners achieve through this simulation?"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">
                Estimated Time (minutes)
              </label>
              <input
                type="number"
                min="1"
                value={manifest.estimatedMinutes}
                onChange={(e) =>
                  updateManifest({ estimatedMinutes: parseInt(e.target.value, 10) })
                }
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
              />
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

      {/* Parameters */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("parameters")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <div className="flex items-center gap-2">
            <Settings className="w-4 h-4" />
            <span className="font-semibold">Parameters ({manifest.parameters.length})</span>
          </div>
          {expandedSections.has("parameters") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("parameters") && (
          <div className="p-4 space-y-3">
            {!readonly && (
              <Button onClick={addParameter} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Parameter
              </Button>
            )}

            {manifest.parameters.length === 0 ? (
              <div className="text-center py-4 text-gray-500 italic">
                No parameters defined yet
              </div>
            ) : (
              <div className="space-y-3">
                {manifest.parameters.map((param) => (
                  <div key={param.id} className="border rounded-lg p-3 bg-gray-50">
                    <div className="flex items-center justify-between mb-2">
                      <input
                        type="text"
                        value={param.name}
                        onChange={(e) =>
                          updateParameter(param.id, { name: e.target.value })
                        }
                        placeholder="Parameter name"
                        className="flex-1 px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      />
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteParameter(param.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      )}
                    </div>

                    <div className="grid grid-cols-2 gap-2 mb-2">
                      <select
                        value={param.type}
                        onChange={(e) =>
                          updateParameter(param.id, {
                            type: e.target.value as SimulationParameter["type"],
                          })
                        }
                        className="px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      >
                        {PARAMETER_TYPES.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>

                      <label className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={param.editable}
                          onChange={(e) =>
                            updateParameter(param.id, { editable: e.target.checked })
                          }
                          disabled={readonly}
                        />
                        Editable
                      </label>
                    </div>

                    <div className="space-y-2">
                      {param.type === "number" && (
                        <div className="grid grid-cols-2 gap-2">
                          <input
                            type="number"
                            value={param.minValue ?? ""}
                            onChange={(e) =>
                              updateParameter(param.id, {
                                minValue: e.target.value ? parseFloat(e.target.value) : undefined,
                              })
                            }
                            placeholder="Min value"
                            className="px-2 py-1 border rounded text-sm"
                            disabled={readonly}
                          />
                          <input
                            type="number"
                            value={param.maxValue ?? ""}
                            onChange={(e) =>
                              updateParameter(param.id, {
                                maxValue: e.target.value ? parseFloat(e.target.value) : undefined,
                              })
                            }
                            placeholder="Max value"
                            className="px-2 py-1 border rounded text-sm"
                            disabled={readonly}
                          />
                        </div>
                      )}

                      {param.type === "enum" && (
                        <input
                          type="text"
                          value={param.allowedValues?.join(", ") ?? ""}
                          onChange={(e) =>
                            updateParameter(param.id, {
                              allowedValues: e.target.value.split(",").map((s) => s.trim()),
                            })
                          }
                          placeholder="Allowed values (comma-separated)"
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                        />
                      )}

                      <input
                        type="text"
                        value={param.defaultValue?.toString() ?? ""}
                        onChange={(e) => {
                          let defaultValue: unknown = e.target.value;
                          if (param.type === "number") {
                            defaultValue = parseFloat(e.target.value);
                          } else if (param.type === "boolean") {
                            defaultValue = e.target.value === "true";
                          }
                          updateParameter(param.id, { defaultValue });
                        }}
                        placeholder="Default value"
                        className="w-full px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      />

                      <textarea
                        value={param.description}
                        onChange={(e) =>
                          updateParameter(param.id, { description: e.target.value })
                        }
                        rows={1}
                        className="w-full px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                        placeholder="Description"
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Success Criteria */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("success")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <div className="flex items-center gap-2">
            <Play className="w-4 h-4" />
            <span className="font-semibold">Success Criteria ({manifest.successCriteria.length})</span>
          </div>
          {expandedSections.has("success") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("success") && (
          <div className="p-4 space-y-3">
            {!readonly && (
              <Button onClick={addSuccessCriterion} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Criterion
              </Button>
            )}

            {manifest.successCriteria.length === 0 ? (
              <div className="text-center py-4 text-gray-500 italic">
                No success criteria defined yet
              </div>
            ) : (
              <div className="space-y-3">
                {manifest.successCriteria.map((criterion) => (
                  <div key={criterion.id} className="border rounded-lg p-3 bg-gray-50">
                    <div className="flex items-center justify-between mb-2">
                      <select
                        value={criterion.type}
                        onChange={(e) =>
                          updateSuccessCriterion(criterion.id, {
                            type: e.target.value as SimulationSuccessCriterion["type"],
                          })
                        }
                        className="px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      >
                        {SUCCESS_CRITERION_TYPES.map((type) => (
                          <option key={type} value={type}>
                            {type.toUpperCase()}
                          </option>
                        ))}
                      </select>
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteSuccessCriterion(criterion.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      )}
                    </div>

                    <div className="space-y-2">
                      {criterion.type === "rmse" && (
                        <div>
                          <label className="block text-xs font-medium mb-1">
                            RMSE Threshold: {criterion.threshold}
                          </label>
                          <input
                            type="range"
                            min="0"
                            max="1"
                            step="0.01"
                            value={criterion.threshold ?? 0.1}
                            onChange={(e) =>
                              updateSuccessCriterion(criterion.id, {
                                threshold: parseFloat(e.target.value),
                              })
                            }
                            className="w-full"
                            disabled={readonly}
                          />
                        </div>
                      )}

                      {criterion.type === "max_attempts" && (
                        <div>
                          <label className="block text-xs font-medium mb-1">
                            Max Attempts
                          </label>
                          <input
                            type="number"
                            min="1"
                            value={criterion.maxAttempts ?? 3}
                            onChange={(e) =>
                              updateSuccessCriterion(criterion.id, {
                                maxAttempts: parseInt(e.target.value, 10),
                              })
                            }
                            className="w-full px-2 py-1 border rounded text-sm"
                            disabled={readonly}
                          />
                        </div>
                      )}

                      {criterion.type === "time_limit" && (
                        <div>
                          <label className="block text-xs font-medium mb-1">
                            Time Limit (seconds)
                          </label>
                          <input
                            type="number"
                            min="1"
                            value={criterion.timeLimit ?? 60}
                            onChange={(e) =>
                              updateSuccessCriterion(criterion.id, {
                                timeLimit: parseInt(e.target.value, 10),
                              })
                            }
                            className="w-full px-2 py-1 border rounded text-sm"
                            disabled={readonly}
                          />
                        </div>
                      )}

                      {criterion.type === "custom" && (
                        <div>
                          <label className="block text-xs font-medium mb-1">
                            Custom Rule
                          </label>
                          <textarea
                            value={criterion.customRule ?? ""}
                            onChange={(e) =>
                              updateSuccessCriterion(criterion.id, {
                                customRule: e.target.value,
                              })
                            }
                            rows={2}
                            className="w-full px-2 py-1 border rounded text-sm"
                            disabled={readonly}
                            placeholder="Enter custom validation rule..."
                          />
                        </div>
                      )}

                      <textarea
                        value={criterion.description}
                        onChange={(e) =>
                          updateSuccessCriterion(criterion.id, { description: e.target.value })
                        }
                        rows={1}
                        className="w-full px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                        placeholder="Description"
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Telemetry Profile */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("telemetry")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Telemetry Profile</span>
          {expandedSections.has("telemetry") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("telemetry") && (
          <div className="p-4 space-y-2">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.telemetryProfile?.collectSnapshots ?? false}
                onChange={(e) =>
                  updateManifest({
                    telemetryProfile: {
                      collectSnapshots: e.target.checked,
                      collectControlChanges: manifest.telemetryProfile?.collectControlChanges ?? false,
                      collectFinalState: manifest.telemetryProfile?.collectFinalState ?? false,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Collect Snapshots</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.telemetryProfile?.collectControlChanges ?? false}
                onChange={(e) =>
                  updateManifest({
                    telemetryProfile: {
                      collectSnapshots: manifest.telemetryProfile?.collectSnapshots ?? false,
                      collectControlChanges: e.target.checked,
                      collectFinalState: manifest.telemetryProfile?.collectFinalState ?? false,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Collect Control Changes</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.telemetryProfile?.collectFinalState ?? false}
                onChange={(e) =>
                  updateManifest({
                    telemetryProfile: {
                      collectSnapshots: manifest.telemetryProfile?.collectSnapshots ?? false,
                      collectControlChanges: manifest.telemetryProfile?.collectControlChanges ?? false,
                      collectFinalState: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Collect Final State</span>
            </label>
          </div>
        )}
      </div>

      {/* Accessibility Metadata */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("accessibility")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Accessibility Metadata</span>
          {expandedSections.has("accessibility") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("accessibility") && (
          <div className="p-4 space-y-2">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.accessibilityMetadata?.reducedMotionSupport ?? false}
                onChange={(e) =>
                  updateManifest({
                    accessibilityMetadata: {
                      reducedMotionSupport: e.target.checked,
                      keyboardNavigable: manifest.accessibilityMetadata?.keyboardNavigable ?? false,
                      screenReaderCompatible: manifest.accessibilityMetadata?.screenReaderCompatible ?? false,
                      highContrastMode: manifest.accessibilityMetadata?.highContrastMode ?? false,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Reduced Motion Support</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.accessibilityMetadata?.keyboardNavigable ?? false}
                onChange={(e) =>
                  updateManifest({
                    accessibilityMetadata: {
                      reducedMotionSupport: manifest.accessibilityMetadata?.reducedMotionSupport ?? false,
                      keyboardNavigable: e.target.checked,
                      screenReaderCompatible: manifest.accessibilityMetadata?.screenReaderCompatible ?? false,
                      highContrastMode: manifest.accessibilityMetadata?.highContrastMode ?? false,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Keyboard Navigable</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.accessibilityMetadata?.screenReaderCompatible ?? false}
                onChange={(e) =>
                  updateManifest({
                    accessibilityMetadata: {
                      reducedMotionSupport: manifest.accessibilityMetadata?.reducedMotionSupport ?? false,
                      keyboardNavigable: manifest.accessibilityMetadata?.keyboardNavigable ?? false,
                      screenReaderCompatible: e.target.checked,
                      highContrastMode: manifest.accessibilityMetadata?.highContrastMode ?? false,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Screen Reader Compatible</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.accessibilityMetadata?.highContrastMode ?? false}
                onChange={(e) =>
                  updateManifest({
                    accessibilityMetadata: {
                      reducedMotionSupport: manifest.accessibilityMetadata?.reducedMotionSupport ?? false,
                      keyboardNavigable: manifest.accessibilityMetadata?.keyboardNavigable ?? false,
                      screenReaderCompatible: manifest.accessibilityMetadata?.screenReaderCompatible ?? false,
                      highContrastMode: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">High Contrast Mode</span>
            </label>
          </div>
        )}
      </div>
    </div>
  );
}
