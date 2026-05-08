/**
 * Assessment Editor Component
 *
 * Schema-backed component for editing assessments.
 * Provides UI for managing assessment items, scoring, feedback,
 * and confidence-based marking configuration.
 *
 * @doc.type component
 * @doc.purpose Edit assessments for learning experiences
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { Plus, Trash2, ChevronUp, ChevronDown, CheckCircle, XCircle } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface AssessmentItem {
  id: string;
  orderIndex: number;
  itemType: "multiple_choice" | "free_response" | "numeric" | "matching" | "ordering";
  prompt: string;
  stimulus: string | null;
  points: number;
  modelAnswer: string | null;
  rubric: string | null;
  choices?: Array<{
    id: string;
    text: string;
    isCorrect: boolean;
    explanation?: string;
  }>;
  metadata: Record<string, unknown>;
  requiresConfidence: boolean;
  confidenceWeight: number; // 0.0 - 1.0
}

export interface AssessmentManifest {
  schemaVersion: string;
  manifestType: "Assessment";
  claimRef: string;
  evidenceRefs: string[];
  objectiveRefs: string[];
  domain: string;
  gradeBand: string;
  title: string;
  description: string;
  items: AssessmentItem[];
  objectives: Array<{
    id: string;
    description: string;
    bloomLevel: string;
  }>;
  scoring: {
    totalPoints: number;
    passingScore: number;
    allowPartialCredit: boolean;
    showFeedback: boolean;
  };
  cbmEnabled: boolean; // Confidence-Based Marking
  cbmConfiguration: {
    requireConfidence: boolean;
    minConfidenceThreshold: number;
    calibrationIndexWeight: number;
    vivaTriggerThreshold: number;
  };
  timeLimit?: number; // seconds
  shuffleItems: boolean;
  randomizeChoices: boolean;
}

interface AssessmentEditorProps {
  manifest: AssessmentManifest;
  onChange: (manifest: AssessmentManifest) => void;
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

const ITEM_TYPES = [
  "multiple_choice",
  "free_response",
  "numeric",
  "matching",
  "ordering",
] as const;

const BLOOM_LEVELS = [
  "REMEMBER",
  "UNDERSTAND",
  "APPLY",
  "ANALYZE",
  "EVALUATE",
  "CREATE",
] as const;

export function AssessmentEditor({
  manifest,
  onChange,
  readonly = false,
}: AssessmentEditorProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(["basic", "items", "scoring"]),
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

  const updateManifest = (updates: Partial<AssessmentManifest>) => {
    onChange({ ...manifest, ...updates });
  };

  const addItem = () => {
    const newItem: AssessmentItem = {
      id: `item-${Date.now()}`,
      orderIndex: manifest.items.length,
      itemType: "multiple_choice",
      prompt: "",
      stimulus: null,
      points: 10,
      modelAnswer: null,
      rubric: null,
      choices: [
        { id: "choice-1", text: "", isCorrect: false },
        { id: "choice-2", text: "", isCorrect: false },
        { id: "choice-3", text: "", isCorrect: false },
        { id: "choice-4", text: "", isCorrect: false },
      ],
      metadata: {},
      requiresConfidence: manifest.cbmEnabled,
      confidenceWeight: 0.5,
    };
    updateManifest({
      items: [...manifest.items, newItem],
      scoring: {
        ...manifest.scoring,
        totalPoints: manifest.scoring.totalPoints + newItem.points,
      },
    });
  };

  const updateItem = (itemId: string, updates: Partial<AssessmentItem>) => {
    updateManifest({
      items: manifest.items.map((item) =>
        item.id === itemId ? { ...item, ...updates } : item,
      ),
    });
  };

  const deleteItem = (itemId: string) => {
    const item = manifest.items.find((i) => i.id === itemId);
    updateManifest({
      items: manifest.items.filter((item) => item.id !== itemId),
      scoring: {
        ...manifest.scoring,
        totalPoints: manifest.scoring.totalPoints - (item?.points || 0),
      },
    });
  };

  const moveItem = (itemId: string, direction: "up" | "down") => {
    const index = manifest.items.findIndex((i) => i.id === itemId);
    if (index < 0) return;

    const newIndex = direction === "up" ? index - 1 : index + 1;
    if (newIndex < 0 || newIndex >= manifest.items.length) return;

    const newItems = [...manifest.items];
    [newItems[index], newItems[newIndex]] = [newItems[newIndex], newItems[index]];
    
    newItems.forEach((item, idx) => {
      item.orderIndex = idx;
    });

    updateManifest({ items: newItems });
  };

  const updateChoice = (itemId: string, choiceId: string, updates: Partial<{ text: string; isCorrect: boolean; explanation: string }>) => {
    const item = manifest.items.find((i) => i.id === itemId);
    if (!item || !item.choices) return;

    updateItem(itemId, {
      choices: item.choices.map((choice) =>
        choice.id === choiceId ? { ...choice, ...updates } : choice,
      ),
    });
  };

  const addObjective = () => {
    updateManifest({
      objectives: [
        ...manifest.objectives,
        {
          id: `objective-${Date.now()}`,
          description: "",
          bloomLevel: "UNDERSTAND",
        },
      ],
    });
  };

  const updateObjective = (objectiveId: string, updates: Partial<{ description: string; bloomLevel: string }>) => {
    updateManifest({
      objectives: manifest.objectives.map((obj) =>
        obj.id === objectiveId ? { ...obj, ...updates } : obj,
      ),
    });
  };

  const deleteObjective = (objectiveId: string) => {
    updateManifest({
      objectives: manifest.objectives.filter((obj) => obj.id !== objectiveId),
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

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={manifest.shuffleItems}
                    onChange={(e) => updateManifest({ shuffleItems: e.target.checked })}
                    disabled={readonly}
                  />
                  <span className="text-sm">Shuffle Items</span>
                </label>
              </div>

              <div>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={manifest.randomizeChoices}
                    onChange={(e) => updateManifest({ randomizeChoices: e.target.checked })}
                    disabled={readonly}
                  />
                  <span className="text-sm">Randomize Choices</span>
                </label>
              </div>
            </div>

            {manifest.timeLimit !== undefined && (
              <div>
                <label className="block text-sm font-medium mb-1">
                  Time Limit (seconds)
                </label>
                <input
                  type="number"
                  min="1"
                  value={manifest.timeLimit}
                  onChange={(e) =>
                    updateManifest({ timeLimit: parseInt(e.target.value, 10) })
                  }
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                />
              </div>
            )}
          </div>
        )}
      </div>

      {/* Objectives */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("objectives")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Learning Objectives ({manifest.objectives.length})</span>
          {expandedSections.has("objectives") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("objectives") && (
          <div className="p-4 space-y-2">
            {!readonly && (
              <Button onClick={addObjective} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Objective
              </Button>
            )}

            {manifest.objectives.map((objective) => (
              <div key={objective.id} className="flex gap-2 items-start border rounded p-2 bg-gray-50">
                <div className="flex-1 space-y-2">
                  <input
                    type="text"
                    value={objective.description}
                    onChange={(e) =>
                      updateObjective(objective.id, { description: e.target.value })
                    }
                    placeholder="Objective description..."
                    className="w-full px-2 py-1 border rounded text-sm"
                    disabled={readonly}
                  />
                  <select
                    value={objective.bloomLevel}
                    onChange={(e) =>
                      updateObjective(objective.id, { bloomLevel: e.target.value })
                    }
                    className="w-full px-2 py-1 border rounded text-sm"
                    disabled={readonly}
                  >
                    {BLOOM_LEVELS.map((level) => (
                      <option key={level} value={level}>
                        {level}
                      </option>
                    ))}
                  </select>
                </div>
                {!readonly && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => deleteObjective(objective.id)}
                  >
                    <Trash2 className="w-4 h-4 text-red-500" />
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Items */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("items")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Assessment Items ({manifest.items.length})</span>
          {expandedSections.has("items") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("items") && (
          <div className="p-4 space-y-3">
            {!readonly && (
              <Button onClick={addItem} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Item
              </Button>
            )}

            {manifest.items.length === 0 ? (
              <div className="text-center py-4 text-gray-500 italic">
                No assessment items defined yet
              </div>
            ) : (
              <div className="space-y-3">
                {manifest.items.map((item, index) => (
                  <div key={item.id} className="border rounded-lg p-3 bg-gray-50">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="text-sm font-mono text-gray-500">
                        #{index + 1}
                      </span>
                      {!readonly && (
                        <div className="flex gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => moveItem(item.id, "up")}
                            disabled={index === 0}
                          >
                            <ChevronUp className="w-4 h-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => moveItem(item.id, "down")}
                            disabled={index === manifest.items.length - 1}
                          >
                            <ChevronDown className="w-4 h-4" />
                          </Button>
                        </div>
                      )}
                      <select
                        value={item.itemType}
                        onChange={(e) =>
                          updateItem(item.id, {
                            itemType: e.target.value as AssessmentItem["itemType"],
                          })
                        }
                        className="px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      >
                        {ITEM_TYPES.map((type) => (
                          <option key={type} value={type}>
                            {type.replace("_", " ").toUpperCase()}
                          </option>
                        ))}
                      </select>
                      <input
                        type="number"
                        min="0"
                        value={item.points}
                        onChange={(e) =>
                          updateItem(item.id, {
                            points: parseInt(e.target.value, 10),
                          })
                        }
                        className="w-16 px-2 py-1 border rounded text-sm"
                        disabled={readonly}
                      />
                      <span className="text-sm text-gray-500">pts</span>
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteItem(item.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      )}
                    </div>

                    <div className="space-y-2">
                      <div>
                        <label className="block text-xs font-medium mb-1">Prompt</label>
                        <textarea
                          value={item.prompt}
                          onChange={(e) => updateItem(item.id, { prompt: e.target.value })}
                          rows={2}
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                        />
                      </div>

                      {item.itemType === "multiple_choice" && item.choices && (
                        <div>
                          <label className="block text-xs font-medium mb-1">Choices</label>
                          <div className="space-y-1">
                            {item.choices.map((choice) => (
                              <div key={choice.id} className="flex gap-2 items-center">
                                <input
                                  type="checkbox"
                                  checked={choice.isCorrect}
                                  onChange={(e) =>
                                    updateChoice(item.id, choice.id, {
                                      isCorrect: e.target.checked,
                                    })
                                  }
                                  disabled={readonly}
                                />
                                <input
                                  type="text"
                                  value={choice.text}
                                  onChange={(e) =>
                                    updateChoice(item.id, choice.id, { text: e.target.value })
                                  }
                                  className="flex-1 px-2 py-1 border rounded text-sm"
                                  disabled={readonly}
                                  placeholder="Choice text..."
                                />
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      <div>
                        <label className="block text-xs font-medium mb-1">Model Answer</label>
                        <textarea
                          value={item.modelAnswer || ""}
                          onChange={(e) => updateItem(item.id, { modelAnswer: e.target.value })}
                          rows={1}
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                        />
                      </div>

                      {manifest.cbmEnabled && (
                        <div className="flex gap-4 items-center">
                          <label className="flex items-center gap-2">
                            <input
                              type="checkbox"
                              checked={item.requiresConfidence}
                              onChange={(e) =>
                                updateItem(item.id, {
                                  requiresConfidence: e.target.checked,
                                })
                              }
                              disabled={readonly}
                            />
                            <span className="text-xs">Require Confidence</span>
                          </label>
                          <div className="flex items-center gap-2">
                            <span className="text-xs">Confidence Weight:</span>
                            <input
                              type="number"
                              min="0"
                              max="1"
                              step="0.1"
                              value={item.confidenceWeight}
                              onChange={(e) =>
                                updateItem(item.id, {
                                  confidenceWeight: parseFloat(e.target.value),
                                })
                              }
                              className="w-16 px-2 py-1 border rounded text-xs"
                              disabled={readonly}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Scoring */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("scoring")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Scoring Configuration</span>
          {expandedSections.has("scoring") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("scoring") && (
          <div className="p-4 space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium mb-1">
                  Total Points: {manifest.scoring.totalPoints}
                </label>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Passing Score</label>
                <input
                  type="number"
                  min="0"
                  max={manifest.scoring.totalPoints}
                  value={manifest.scoring.passingScore}
                  onChange={(e) =>
                    updateManifest({
                      scoring: {
                        ...manifest.scoring,
                        passingScore: parseInt(e.target.value, 10),
                      },
                    })
                  }
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                />
              </div>
            </div>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.scoring.allowPartialCredit}
                onChange={(e) =>
                  updateManifest({
                    scoring: {
                      ...manifest.scoring,
                      allowPartialCredit: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Allow Partial Credit</span>
            </label>

            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.scoring.showFeedback}
                onChange={(e) =>
                  updateManifest({
                    scoring: {
                      ...manifest.scoring,
                      showFeedback: e.target.checked,
                    },
                  })
                }
                disabled={readonly}
              />
              <span className="text-sm">Show Feedback</span>
            </label>
          </div>
        )}
      </div>

      {/* CBM Configuration */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("cbm")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Confidence-Based Marking</span>
          {expandedSections.has("cbm") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("cbm") && (
          <div className="p-4 space-y-3">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={manifest.cbmEnabled}
                onChange={(e) =>
                  updateManifest({ cbmEnabled: e.target.checked })
                }
                disabled={readonly}
              />
              <span className="text-sm font-medium">Enable CBM</span>
            </label>

            {manifest.cbmEnabled && (
              <div className="space-y-3 ml-6 border-l-2 border-gray-200 pl-4">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={manifest.cbmConfiguration.requireConfidence}
                    onChange={(e) =>
                      updateManifest({
                        cbmConfiguration: {
                          ...manifest.cbmConfiguration,
                          requireConfidence: e.target.checked,
                        },
                      })
                    }
                    disabled={readonly}
                  />
                  <span className="text-sm">Require Confidence for All Items</span>
                </label>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    Min Confidence Threshold: {manifest.cbmConfiguration.minConfidenceThreshold.toFixed(2)}
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={manifest.cbmConfiguration.minConfidenceThreshold}
                    onChange={(e) =>
                      updateManifest({
                        cbmConfiguration: {
                          ...manifest.cbmConfiguration,
                          minConfidenceThreshold: parseFloat(e.target.value),
                        },
                      })
                    }
                    className="w-full"
                    disabled={readonly}
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    Calibration Index Weight: {manifest.cbmConfiguration.calibrationIndexWeight.toFixed(2)}
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={manifest.cbmConfiguration.calibrationIndexWeight}
                    onChange={(e) =>
                      updateManifest({
                        cbmConfiguration: {
                          ...manifest.cbmConfiguration,
                          calibrationIndexWeight: parseFloat(e.target.value),
                        },
                      })
                    }
                    className="w-full"
                    disabled={readonly}
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    Viva Trigger Threshold: {manifest.cbmConfiguration.vivaTriggerThreshold.toFixed(2)}
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={manifest.cbmConfiguration.vivaTriggerThreshold}
                    onChange={(e) =>
                      updateManifest({
                        cbmConfiguration: {
                          ...manifest.cbmConfiguration,
                          vivaTriggerThreshold: parseFloat(e.target.value),
                        },
                      })
                    }
                    className="w-full"
                    disabled={readonly}
                  />
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
