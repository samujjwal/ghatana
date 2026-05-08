/**
 * Claim Planner Component
 *
 * Schema-backed component for planning and managing learning claims.
 * Provides UI for creating, editing, and organizing claims with their
 * evidence requirements and tasks.
 *
 * @doc.type component
 * @doc.purpose Plan and manage learning claims
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { Plus, Trash2, ChevronUp, ChevronDown } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface LearningClaim {
  id: string;
  text: string;
  bloom: string;
  masteryThreshold: number;
  orderIndex: number;
  evidenceRequirements: LearningEvidence[];
  tasks: ExperienceTask[];
}

export interface LearningEvidence {
  id: string;
  claimId: string;
  type: string;
  description: string;
  minimumScore: number;
  weight: number;
}

export interface ExperienceTask {
  id: string;
  claimId: string;
  type: string;
  title: string;
  instructions: string;
  evidenceIds: string[];
  estimatedMinutes: number;
  orderIndex: number;
}

interface ClaimPlannerProps {
  claims: LearningClaim[];
  onChange: (claims: LearningClaim[]) => void;
  readonly?: boolean;
}

const BLOOM_LEVELS = [
  "REMEMBER",
  "UNDERSTAND",
  "APPLY",
  "ANALYZE",
  "EVALUATE",
  "CREATE",
] as const;

const EVIDENCE_TYPES = [
  "ASSESSMENT",
  "SIMULATION",
  "EXAMPLE",
  "EXPLANATION",
  "ANIMATION",
] as const;

const TASK_TYPES = [
  "PRACTICE",
  "SIMULATION",
  "ASSESSMENT",
  "READING",
  "VIDEO",
  "INTERACTIVE",
] as const;

export function ClaimPlanner({ claims, onChange, readonly = false }: ClaimPlannerProps) {
  const [expandedClaims, setExpandedClaims] = useState<Set<string>>(new Set());

  const toggleClaimExpansion = (claimId: string) => {
    const newExpanded = new Set(expandedClaims);
    if (newExpanded.has(claimId)) {
      newExpanded.delete(claimId);
    } else {
      newExpanded.add(claimId);
    }
    setExpandedClaims(newExpanded);
  };

  const addClaim = () => {
    const newClaim: LearningClaim = {
      id: `claim-${Date.now()}`,
      text: "",
      bloom: "UNDERSTAND",
      masteryThreshold: 0.8,
      orderIndex: claims.length,
      evidenceRequirements: [],
      tasks: [],
    };
    onChange([...claims, newClaim]);
    setExpandedClaims(new Set([...expandedClaims, newClaim.id]));
  };

  const updateClaim = (claimId: string, updates: Partial<LearningClaim>) => {
    onChange(
      claims.map((claim) =>
        claim.id === claimId ? { ...claim, ...updates } : claim,
      ),
    );
  };

  const deleteClaim = (claimId: string) => {
    onChange(claims.filter((claim) => claim.id !== claimId));
    const newExpanded = new Set(expandedClaims);
    newExpanded.delete(claimId);
    setExpandedClaims(newExpanded);
  };

  const moveClaim = (claimId: string, direction: "up" | "down") => {
    const index = claims.findIndex((c) => c.id === claimId);
    if (index < 0) return;

    const newIndex = direction === "up" ? index - 1 : index + 1;
    if (newIndex < 0 || newIndex >= claims.length) return;

    const newClaims = [...claims];
    [newClaims[index], newClaims[newIndex]] = [newClaims[newIndex], newClaims[index]];
    
    // Update order indices
    newClaims.forEach((claim, idx) => {
      claim.orderIndex = idx;
    });

    onChange(newClaims);
  };

  const addEvidence = (claimId: string) => {
    const claim = claims.find((c) => c.id === claimId);
    if (!claim) return;

    const newEvidence: LearningEvidence = {
      id: `evidence-${Date.now()}`,
      claimId,
      type: "ASSESSMENT",
      description: "",
      minimumScore: 0.7,
      weight: 1.0,
    };

    updateClaim(claimId, {
      evidenceRequirements: [...claim.evidenceRequirements, newEvidence],
    });
  };

  const updateEvidence = (
    claimId: string,
    evidenceId: string,
    updates: Partial<LearningEvidence>,
  ) => {
    const claim = claims.find((c) => c.id === claimId);
    if (!claim) return;

    updateClaim(claimId, {
      evidenceRequirements: claim.evidenceRequirements.map((ev) =>
        ev.id === evidenceId ? { ...ev, ...updates } : ev,
      ),
    });
  };

  const deleteEvidence = (claimId: string, evidenceId: string) => {
    const claim = claims.find((c) => c.id === claimId);
    if (!claim) return;

    updateClaim(claimId, {
      evidenceRequirements: claim.evidenceRequirements.filter(
        (ev) => ev.id !== evidenceId,
      ),
    });
  };

  const addTask = (claimId: string) => {
    const claim = claims.find((c) => c.id === claimId);
    if (!claim) return;

    const newTask: ExperienceTask = {
      id: `task-${Date.now()}`,
      claimId,
      type: "PRACTICE",
      title: "",
      instructions: "",
      evidenceIds: [],
      estimatedMinutes: 10,
      orderIndex: claim.tasks.length,
    };

    updateClaim(claimId, {
      tasks: [...claim.tasks, newTask],
    });
  };

  const updateTask = (
    claimId: string,
    taskId: string,
    updates: Partial<ExperienceTask>,
  ) => {
    const claim = claims.find((c) => c.id === claimId);
    if (!claim) return;

    updateClaim(claimId, {
      tasks: claim.tasks.map((task) =>
        task.id === taskId ? { ...task, ...updates } : task,
      ),
    });
  };

  const deleteTask = (claimId: string, taskId: string) => {
    const claim = claims.find((c) => c.id === claimId);
    if (!claim) return;

    updateClaim(claimId, {
      tasks: claim.tasks.filter((task) => task.id !== taskId),
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Learning Claims</h3>
        {!readonly && (
          <Button onClick={addClaim} size="sm">
            <Plus className="w-4 h-4 mr-2" />
            Add Claim
          </Button>
        )}
      </div>

      {claims.length === 0 ? (
        <div className="text-center py-8 text-gray-500 border-2 border-dashed rounded-lg">
          No claims defined yet. Add your first claim to get started.
        </div>
      ) : (
        <div className="space-y-3">
          {claims.map((claim, index) => (
            <div
              key={claim.id}
              className="border rounded-lg overflow-hidden"
            >
              <div className="flex items-center gap-2 p-3 bg-gray-50">
                <span className="text-sm text-gray-500 font-mono">
                  #{index + 1}
                </span>
                {!readonly && (
                  <div className="flex gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => moveClaim(claim.id, "up")}
                      disabled={index === 0}
                    >
                      <ChevronUp className="w-4 h-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => moveClaim(claim.id, "down")}
                      disabled={index === claims.length - 1}
                    >
                      <ChevronDown className="w-4 h-4" />
                    </Button>
                  </div>
                )}
                <input
                  type="text"
                  value={claim.text}
                  onChange={(e) => updateClaim(claim.id, { text: e.target.value })}
                  placeholder="Enter claim text..."
                  className="flex-1 px-3 py-1 border rounded"
                  disabled={readonly}
                />
                <select
                  value={claim.bloom}
                  onChange={(e) => updateClaim(claim.id, { bloom: e.target.value })}
                  className="px-3 py-1 border rounded text-sm"
                  disabled={readonly}
                >
                  {BLOOM_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
                {!readonly && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => deleteClaim(claim.id)}
                  >
                    <Trash2 className="w-4 h-4 text-red-500" />
                  </Button>
                )}
              </div>

              {expandedClaims.has(claim.id) && (
                <div className="p-4 space-y-4">
                  {/* Mastery Threshold */}
                  <div>
                    <label className="block text-sm font-medium mb-1">
                      Mastery Threshold: {claim.masteryThreshold.toFixed(2)}
                    </label>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.05"
                      value={claim.masteryThreshold}
                      onChange={(e) =>
                        updateClaim(claim.id, {
                          masteryThreshold: parseFloat(e.target.value),
                        })
                      }
                      className="w-full"
                      disabled={readonly}
                    />
                  </div>

                  {/* Evidence Requirements */}
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <h4 className="text-sm font-medium">Evidence Requirements</h4>
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => addEvidence(claim.id)}
                        >
                          <Plus className="w-4 h-4 mr-1" />
                          Add Evidence
                        </Button>
                      )}
                    </div>
                    {claim.evidenceRequirements.length === 0 ? (
                      <div className="text-sm text-gray-500 italic">
                        No evidence requirements defined
                      </div>
                    ) : (
                      <div className="space-y-2">
                        {claim.evidenceRequirements.map((evidence) => (
                          <div
                            key={evidence.id}
                            className="flex gap-2 items-start p-2 border rounded bg-gray-50"
                          >
                            <select
                              value={evidence.type}
                              onChange={(e) =>
                                updateEvidence(claim.id, evidence.id, {
                                  type: e.target.value,
                                })
                              }
                              className="px-2 py-1 border rounded text-sm"
                              disabled={readonly}
                            >
                              {EVIDENCE_TYPES.map((type) => (
                                <option key={type} value={type}>
                                  {type}
                                </option>
                              ))}
                            </select>
                            <input
                              type="text"
                              value={evidence.description}
                              onChange={(e) =>
                                updateEvidence(claim.id, evidence.id, {
                                  description: e.target.value,
                                })
                              }
                              placeholder="Description"
                              className="flex-1 px-2 py-1 border rounded text-sm"
                              disabled={readonly}
                            />
                            <input
                              type="number"
                              min="0"
                              max="1"
                              step="0.1"
                              value={evidence.minimumScore}
                              onChange={(e) =>
                                updateEvidence(claim.id, evidence.id, {
                                  minimumScore: parseFloat(e.target.value),
                                })
                              }
                              placeholder="Min Score"
                              className="w-20 px-2 py-1 border rounded text-sm"
                              disabled={readonly}
                            />
                            {!readonly && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => deleteEvidence(claim.id, evidence.id)}
                              >
                                <Trash2 className="w-4 h-4 text-red-500" />
                              </Button>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Tasks */}
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <h4 className="text-sm font-medium">Tasks</h4>
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => addTask(claim.id)}
                        >
                          <Plus className="w-4 h-4 mr-1" />
                          Add Task
                        </Button>
                      )}
                    </div>
                    {claim.tasks.length === 0 ? (
                      <div className="text-sm text-gray-500 italic">
                        No tasks defined
                      </div>
                    ) : (
                      <div className="space-y-2">
                        {claim.tasks.map((task) => (
                          <div
                            key={task.id}
                            className="flex gap-2 items-start p-2 border rounded bg-gray-50"
                          >
                            <select
                              value={task.type}
                              onChange={(e) =>
                                updateTask(claim.id, task.id, {
                                  type: e.target.value,
                                })
                              }
                              className="px-2 py-1 border rounded text-sm"
                              disabled={readonly}
                            >
                              {TASK_TYPES.map((type) => (
                                <option key={type} value={type}>
                                  {type}
                                </option>
                              ))}
                            </select>
                            <input
                              type="text"
                              value={task.title}
                              onChange={(e) =>
                                updateTask(claim.id, task.id, {
                                  title: e.target.value,
                                })
                              }
                              placeholder="Task title"
                              className="flex-1 px-2 py-1 border rounded text-sm"
                              disabled={readonly}
                            />
                            <input
                              type="number"
                              min="1"
                              value={task.estimatedMinutes}
                              onChange={(e) =>
                                updateTask(claim.id, task.id, {
                                  estimatedMinutes: parseInt(e.target.value, 10),
                                })
                              }
                              placeholder="Min"
                              className="w-16 px-2 py-1 border rounded text-sm"
                              disabled={readonly}
                            />
                            {!readonly && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => deleteTask(claim.id, task.id)}
                              >
                                <Trash2 className="w-4 h-4 text-red-500" />
                              </Button>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
