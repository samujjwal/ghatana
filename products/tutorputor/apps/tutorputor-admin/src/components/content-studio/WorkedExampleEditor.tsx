/**
 * Worked Example Editor Component
 *
 * Schema-backed component for editing worked examples.
 * Provides UI for managing example content, steps, solutions, and
 * alignment with learning objectives and evidence.
 *
 * @doc.type component
 * @doc.purpose Edit worked examples for claims
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { Plus, Trash2, ChevronUp, ChevronDown } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface WorkedExampleStep {
  id: string;
  orderIndex: number;
  instruction: string;
  explanation: string;
  expectedOutput?: string;
  hint?: string;
}

export interface WorkedExampleManifest {
  schemaVersion: string;
  manifestType: "WorkedExample";
  claimRef: string;
  evidenceRefs: string[];
  objectiveRefs: string[];
  domain: string;
  gradeBand: string;
  pedagogicalIntent: string;
  title: string;
  description: string;
  problemStatement: string;
  steps: WorkedExampleStep[];
  solution: string;
  alternativeApproaches?: string[];
  commonMistakes?: string[];
  misconceptions?: string[];
  provenance: {
    generatedBy: string;
    modelVersion?: string;
    timestamp: string;
  };
  validators?: string[];
  telemetryProfile?: {
    difficulty: "easy" | "medium" | "hard";
    estimatedTimeMinutes: number;
    requiredSkills: string[];
  };
}

interface WorkedExampleEditorProps {
  manifest: WorkedExampleManifest;
  onChange: (manifest: WorkedExampleManifest) => void;
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

const PEDAGOGICAL_INTENTS = [
  "illustrate_concept",
  "demonstrate_procedure",
  "practice_skill",
  "connect_concepts",
  "address_misconception",
] as const;

const DIFFICULTY_LEVELS = ["easy", "medium", "hard"] as const;

export function WorkedExampleEditor({
  manifest,
  onChange,
  readonly = false,
}: WorkedExampleEditorProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(["basic", "steps"]));

  const toggleSection = (section: string) => {
    const newExpanded = new Set(expandedSections);
    if (newExpanded.has(section)) {
      newExpanded.delete(section);
    } else {
      newExpanded.add(section);
    }
    setExpandedSections(newExpanded);
  };

  const updateManifest = (updates: Partial<WorkedExampleManifest>) => {
    onChange({ ...manifest, ...updates });
  };

  const addStep = () => {
    const newStep: WorkedExampleStep = {
      id: `step-${Date.now()}`,
      orderIndex: manifest.steps.length,
      instruction: "",
      explanation: "",
    };
    updateManifest({
      steps: [...manifest.steps, newStep],
    });
  };

  const updateStep = (stepId: string, updates: Partial<WorkedExampleStep>) => {
    updateManifest({
      steps: manifest.steps.map((step) =>
        step.id === stepId ? { ...step, ...updates } : step,
      ),
    });
  };

  const deleteStep = (stepId: string) => {
    updateManifest({
      steps: manifest.steps.filter((step) => step.id !== stepId),
    });
  };

  const moveStep = (stepId: string, direction: "up" | "down") => {
    const index = manifest.steps.findIndex((s) => s.id === stepId);
    if (index < 0) return;

    const newIndex = direction === "up" ? index - 1 : index + 1;
    if (newIndex < 0 || newIndex >= manifest.steps.length) return;

    const newSteps = [...manifest.steps];
    [newSteps[index], newSteps[newIndex]] = [newSteps[newIndex], newSteps[index]];
    
    // Update order indices
    newSteps.forEach((step, idx) => {
      step.orderIndex = idx;
    });

    updateManifest({ steps: newSteps });
  };

  const addMisconception = () => {
    updateManifest({
      misconceptions: [...(manifest.misconceptions || []), ""],
    });
  };

  const updateMisconception = (index: number, value: string) => {
    const misconceptions = [...(manifest.misconceptions || [])];
    misconceptions[index] = value;
    updateManifest({ misconceptions });
  };

  const deleteMisconception = (index: number) => {
    const misconceptions = [...(manifest.misconceptions || [])];
    misconceptions.splice(index, 1);
    updateManifest({ misconceptions });
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
              <label className="block text-sm font-medium mb-1">Pedagogical Intent</label>
              <select
                value={manifest.pedagogicalIntent}
                onChange={(e) => updateManifest({ pedagogicalIntent: e.target.value })}
                className="w-full px-3 py-2 border rounded"
                disabled={readonly}
              >
                {PEDAGOGICAL_INTENTS.map((intent) => (
                  <option key={intent} value={intent}>
                    {intent}
                  </option>
                ))}
              </select>
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

      {/* Problem Statement */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("problem")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Problem Statement</span>
          {expandedSections.has("problem") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("problem") && (
          <div className="p-4">
            <textarea
              value={manifest.problemStatement}
              onChange={(e) => updateManifest({ problemStatement: e.target.value })}
              rows={4}
              className="w-full px-3 py-2 border rounded"
              disabled={readonly}
              placeholder="Describe the problem or scenario..."
            />
          </div>
        )}
      </div>

      {/* Steps */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("steps")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Solution Steps ({manifest.steps.length})</span>
          {expandedSections.has("steps") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("steps") && (
          <div className="p-4 space-y-3">
            {!readonly && (
              <Button onClick={addStep} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Step
              </Button>
            )}

            {manifest.steps.length === 0 ? (
              <div className="text-center py-4 text-gray-500 italic">
                No steps defined yet
              </div>
            ) : (
              <div className="space-y-3">
                {manifest.steps.map((step, index) => (
                  <div key={step.id} className="border rounded-lg p-3 bg-gray-50">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="text-sm font-mono text-gray-500">
                        Step {index + 1}
                      </span>
                      {!readonly && (
                        <div className="flex gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => moveStep(step.id, "up")}
                            disabled={index === 0}
                          >
                            <ChevronUp className="w-4 h-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => moveStep(step.id, "down")}
                            disabled={index === manifest.steps.length - 1}
                          >
                            <ChevronDown className="w-4 h-4" />
                          </Button>
                        </div>
                      )}
                      {!readonly && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => deleteStep(step.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      )}
                    </div>

                    <div className="space-y-2">
                      <div>
                        <label className="block text-xs font-medium mb-1">Instruction</label>
                        <textarea
                          value={step.instruction}
                          onChange={(e) => updateStep(step.id, { instruction: e.target.value })}
                          rows={2}
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                          placeholder="What should the learner do?"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-medium mb-1">Explanation</label>
                        <textarea
                          value={step.explanation}
                          onChange={(e) => updateStep(step.id, { explanation: e.target.value })}
                          rows={2}
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                          placeholder="Explain why this step works..."
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-medium mb-1">Expected Output (optional)</label>
                        <input
                          type="text"
                          value={step.expectedOutput || ""}
                          onChange={(e) => updateStep(step.id, { expectedOutput: e.target.value })}
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                          placeholder="e.g., x = 5"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-medium mb-1">Hint (optional)</label>
                        <input
                          type="text"
                          value={step.hint || ""}
                          onChange={(e) => updateStep(step.id, { hint: e.target.value })}
                          className="w-full px-2 py-1 border rounded text-sm"
                          disabled={readonly}
                          placeholder="Hint for learners who get stuck"
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Solution */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("solution")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Final Solution</span>
          {expandedSections.has("solution") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("solution") && (
          <div className="p-4">
            <textarea
              value={manifest.solution}
              onChange={(e) => updateManifest({ solution: e.target.value })}
              rows={4}
              className="w-full px-3 py-2 border rounded"
              disabled={readonly}
              placeholder="Provide the final answer or conclusion..."
            />
          </div>
        )}
      </div>

      {/* Misconceptions */}
      <div className="border rounded-lg">
        <button
          onClick={() => toggleSection("misconceptions")}
          className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100"
        >
          <span className="font-semibold">Misconceptions Addressed ({manifest.misconceptions?.length || 0})</span>
          {expandedSections.has("misconceptions") ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>

        {expandedSections.has("misconceptions") && (
          <div className="p-4 space-y-2">
            {!readonly && (
              <Button onClick={addMisconception} size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Add Misconception
              </Button>
            )}

            {(manifest.misconceptions || []).map((misconception, index) => (
              <div key={index} className="flex gap-2">
                <input
                  type="text"
                  value={misconception}
                  onChange={(e) => updateMisconception(index, e.target.value)}
                  className="flex-1 px-3 py-2 border rounded"
                  disabled={readonly}
                  placeholder="Common misconception this example addresses..."
                />
                {!readonly && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => deleteMisconception(index)}
                  >
                    <Trash2 className="w-4 h-4 text-red-500" />
                  </Button>
                )}
              </div>
            ))}
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
          <div className="p-4 space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium mb-1">Difficulty</label>
                <select
                  value={manifest.telemetryProfile?.difficulty || "medium"}
                  onChange={(e) =>
                    updateManifest({
                      telemetryProfile: {
                        difficulty: e.target.value as "easy" | "medium" | "hard",
                        estimatedTimeMinutes: manifest.telemetryProfile?.estimatedTimeMinutes ?? 10,
                        requiredSkills: manifest.telemetryProfile?.requiredSkills ?? [],
                      },
                    })
                  }
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                >
                  {DIFFICULTY_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">
                  Estimated Time (minutes)
                </label>
                <input
                  type="number"
                  min="1"
                  value={manifest.telemetryProfile?.estimatedTimeMinutes || 10}
                  onChange={(e) =>
                    updateManifest({
                      telemetryProfile: {
                        difficulty: manifest.telemetryProfile?.difficulty ?? "medium",
                        estimatedTimeMinutes: parseInt(e.target.value, 10),
                        requiredSkills: manifest.telemetryProfile?.requiredSkills ?? [],
                      },
                    })
                  }
                  className="w-full px-3 py-2 border rounded"
                  disabled={readonly}
                />
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
