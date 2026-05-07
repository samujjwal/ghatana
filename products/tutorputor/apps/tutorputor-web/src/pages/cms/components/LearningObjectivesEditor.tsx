/**
 * Learning Objectives Editor Component
 * 
 * Component for editing learning objectives with taxonomy levels
 * 
 * @doc.type component
 * @doc.purpose Learning objectives editor
 * @doc.layer product
 * @doc.pattern Component
 */

import { Card, Text, Input, Select, Button } from "@/components/ui";
import type { LearningObjective, TaxonomyLevel } from "../types";

interface LearningObjectivesEditorProps {
    objectives: LearningObjective[];
    onChange: (objectives: LearningObjective[]) => void;
}

export function LearningObjectivesEditor({ objectives, onChange }: LearningObjectivesEditorProps) {
    const handleAddObjective = () => {
        const newObjective: LearningObjective = {
            label: "",
            taxonomyLevel: "UNDERSTAND",
        };
        onChange([...objectives, newObjective]);
    };

    const handleUpdateObjective = (index: number, updates: Partial<LearningObjective>) => {
        const newObjectives = [...objectives];
        newObjectives[index] = { ...newObjectives[index], ...updates };
        onChange(newObjectives);
    };

    const handleRemoveObjective = (index: number) => {
        onChange(objectives.filter((_, i) => i !== index));
    };

    return (
        <Card className="p-6 mb-6">
            <div className="flex items-center justify-between mb-4">
                <Text className="text-lg font-medium text-gray-900">Learning Objectives</Text>
                <Button onClick={handleAddObjective} variant="solid" tone="primary" size="sm">
                    Add Objective
                </Button>
            </div>
            
            <div className="space-y-3">
                {objectives.map((objective, index) => (
                    <div key={index} className="flex items-center gap-3 p-3 border border-gray-200 rounded-lg">
                        <Input
                            value={objective.label}
                            onChange={(e) => handleUpdateObjective(index, { label: e.target.value })}
                            placeholder="Enter learning objective..."
                            className="flex-1"
                        />
                        <Select
                            value={objective.taxonomyLevel}
                            onChange={(e) => handleUpdateObjective(index, { taxonomyLevel: e.target.value as TaxonomyLevel })}
                            className="w-40"
                        >
                            <option value="REMEMBER">Remember</option>
                            <option value="UNDERSTAND">Understand</option>
                            <option value="APPLY">Apply</option>
                            <option value="ANALYZE">Analyze</option>
                            <option value="EVALUATE">Evaluate</option>
                            <option value="CREATE">Create</option>
                        </Select>
                        <Button
                            onClick={() => handleRemoveObjective(index)}
                            variant="ghost"
                            tone="danger"
                            size="sm"
                        >
                            ×
                        </Button>
                    </div>
                ))}
                {objectives.length === 0 && (
                    <Text className="text-sm text-gray-500 italic">
                        No objectives yet. Click "Add Objective" to get started.
                    </Text>
                )}
            </div>
        </Card>
    );
}
