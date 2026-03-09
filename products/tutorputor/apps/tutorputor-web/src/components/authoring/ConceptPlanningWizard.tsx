/**
 * Concept & Planning Wizard
 * 
 * @doc.type component
 * @doc.purpose Phase 1 of authoring flow - Learning objectives and concept selection
 * @doc.layer product
 * @doc.pattern Wizard
 */

import { useState } from "react";

interface LearningObjectives {
    domain: string;
    topic: string;
    gradeLevel: string;
    duration: string;
    objectives: string[];
    prerequisites: string[];
}

interface ConceptPlanningWizardProps {
    onComplete: (data: LearningObjectives) => void;
    onCancel: () => void;
}

export function ConceptPlanningWizard({ onComplete, onCancel }: ConceptPlanningWizardProps) {
    const [step, setStep] = useState(1);
    const [objectives, setObjectives] = useState<LearningObjectives>({
        domain: "CS_DISCRETE",
        topic: "",
        gradeLevel: "high_school",
        duration: "45_minutes",
        objectives: [""],
        prerequisites: [""]
    });

    const domains = [
        { value: "CS_DISCRETE", label: "Algorithms & Data Structures" },
        { value: "PHYSICS", label: "Physics" },
        { value: "CHEMISTRY", label: "Chemistry" },
        { value: "BIOLOGY", label: "Biology" },
        { value: "MATHEMATICS", label: "Mathematics" },
        { value: "ECONOMICS", label: "Economics" },
        { value: "ENGINEERING", label: "Engineering" },
        { value: "MEDICINE", label: "Medicine" }
    ];

    const gradeLevels = [
        { value: "elementary", label: "Elementary (K-5)" },
        { value: "middle_school", label: "Middle School (6-8)" },
        { value: "high_school", label: "High School (9-12)" },
        { value: "undergraduate", label: "Undergraduate" },
        { value: "graduate", label: "Graduate" }
    ];

    const durations = [
        { value: "15_minutes", label: "15 minutes" },
        { value: "30_minutes", label: "30 minutes" },
        { value: "45_minutes", label: "45 minutes" },
        { value: "60_minutes", label: "60 minutes" },
        { value: "90_minutes", label: "90 minutes" }
    ];

    const handleNext = () => {
        if (step < 3) {
            setStep(step + 1);
        } else {
            onComplete(objectives);
        }
    };

    const handleBack = () => {
        if (step > 1) {
            setStep(step - 1);
        }
    };

    const addObjective = () => {
        setObjectives({
            ...objectives,
            objectives: [...objectives.objectives, ""]
        });
    };

    const updateObjective = (index: number, value: string) => {
        const newObjectives = [...objectives.objectives];
        newObjectives[index] = value;
        setObjectives({ ...objectives, objectives: newObjectives });
    };

    const removeObjective = (index: number) => {
        setObjectives({
            ...objectives,
            objectives: objectives.objectives.filter((_, i) => i !== index)
        });
    };

    const addPrerequisite = () => {
        setObjectives({
            ...objectives,
            prerequisites: [...objectives.prerequisites, ""]
        });
    };

    const updatePrerequisite = (index: number, value: string) => {
        const newPrereqs = [...objectives.prerequisites];
        newPrereqs[index] = value;
        setObjectives({ ...objectives, prerequisites: newPrereqs });
    };

    const removePrerequisite = (index: number) => {
        setObjectives({
            ...objectives,
            prerequisites: objectives.prerequisites.filter((_, i) => i !== index)
        });
    };

    return (
        <div className="concept-planning-wizard max-w-4xl mx-auto p-6">
            {/* Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Create New Simulation</h1>
                <p className="text-gray-600">Step {step} of 3: {
                    step === 1 ? "Basic Information" :
                        step === 2 ? "Learning Objectives" :
                            "Prerequisites & Review"
                }</p>
            </div>

            {/* Progress Bar */}
            <div className="mb-8">
                <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium">Progress</span>
                    <span className="text-sm text-gray-600">{Math.round((step / 3) * 100)}%</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                        className="bg-blue-600 h-2 rounded-full transition-all"
                        style={{ width: `${(step / 3) * 100}%` }}
                    />
                </div>
            </div>

            {/* Step 1: Basic Information */}
            {step === 1 && (
                <div className="space-y-6">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Domain *
                        </label>
                        <select
                            className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                            value={objectives.domain}
                            onChange={(e) => setObjectives({ ...objectives, domain: e.target.value })}
                        >
                            {domains.map(d => (
                                <option key={d.value} value={d.value}>{d.label}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Topic *
                        </label>
                        <input
                            type="text"
                            className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                            placeholder="e.g., Newton's Laws of Motion"
                            value={objectives.topic}
                            onChange={(e) => setObjectives({ ...objectives, topic: e.target.value })}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Grade Level *
                            </label>
                            <select
                                className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                value={objectives.gradeLevel}
                                onChange={(e) => setObjectives({ ...objectives, gradeLevel: e.target.value })}
                            >
                                {gradeLevels.map(g => (
                                    <option key={g.value} value={g.value}>{g.label}</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Estimated Duration *
                            </label>
                            <select
                                className="w-full px-4 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                value={objectives.duration}
                                onChange={(e) => setObjectives({ ...objectives, duration: e.target.value })}
                            >
                                {durations.map(d => (
                                    <option key={d.value} value={d.value}>{d.label}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                </div>
            )}

            {/* Step 2: Learning Objectives */}
            {step === 2 && (
                <div className="space-y-6">
                    <div>
                        <div className="flex items-center justify-between mb-4">
                            <label className="block text-sm font-medium text-gray-700">
                                Learning Objectives *
                            </label>
                            <button
                                onClick={addObjective}
                                className="px-3 py-1 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                + Add Objective
                            </button>
                        </div>
                        <div className="space-y-3">
                            {objectives.objectives.map((obj, index) => (
                                <div key={index} className="flex gap-2">
                                    <input
                                        type="text"
                                        className="flex-1 px-4 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                        placeholder={`Objective ${index + 1}`}
                                        value={obj}
                                        onChange={(e) => updateObjective(index, e.target.value)}
                                    />
                                    {objectives.objectives.length > 1 && (
                                        <button
                                            onClick={() => removeObjective(index)}
                                            className="px-3 py-2 text-red-600 hover:bg-red-50 rounded-md"
                                        >
                                            Remove
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                        <h4 className="font-medium text-blue-900 mb-2">💡 Tips for Writing Learning Objectives</h4>
                        <ul className="text-sm text-blue-800 space-y-1">
                            <li>• Use action verbs (understand, apply, analyze, create)</li>
                            <li>• Be specific and measurable</li>
                            <li>• Focus on student outcomes, not activities</li>
                            <li>• Align with curriculum standards</li>
                        </ul>
                    </div>
                </div>
            )}

            {/* Step 3: Prerequisites & Review */}
            {step === 3 && (
                <div className="space-y-6">
                    <div>
                        <div className="flex items-center justify-between mb-4">
                            <label className="block text-sm font-medium text-gray-700">
                                Prerequisites
                            </label>
                            <button
                                onClick={addPrerequisite}
                                className="px-3 py-1 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                + Add Prerequisite
                            </button>
                        </div>
                        <div className="space-y-3">
                            {objectives.prerequisites.map((prereq, index) => (
                                <div key={index} className="flex gap-2">
                                    <input
                                        type="text"
                                        className="flex-1 px-4 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                        placeholder={`Prerequisite ${index + 1}`}
                                        value={prereq}
                                        onChange={(e) => updatePrerequisite(index, e.target.value)}
                                    />
                                    {objectives.prerequisites.length > 1 && (
                                        <button
                                            onClick={() => removePrerequisite(index)}
                                            className="px-3 py-2 text-red-600 hover:bg-red-50 rounded-md"
                                        >
                                            Remove
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Review Summary */}
                    <div className="bg-gray-50 border rounded-lg p-6">
                        <h3 className="font-semibold text-lg mb-4">Review Your Plan</h3>
                        <dl className="space-y-3">
                            <div>
                                <dt className="text-sm font-medium text-gray-600">Domain</dt>
                                <dd className="text-gray-900">{domains.find(d => d.value === objectives.domain)?.label}</dd>
                            </div>
                            <div>
                                <dt className="text-sm font-medium text-gray-600">Topic</dt>
                                <dd className="text-gray-900">{objectives.topic}</dd>
                            </div>
                            <div>
                                <dt className="text-sm font-medium text-gray-600">Grade Level</dt>
                                <dd className="text-gray-900">{gradeLevels.find(g => g.value === objectives.gradeLevel)?.label}</dd>
                            </div>
                            <div>
                                <dt className="text-sm font-medium text-gray-600">Duration</dt>
                                <dd className="text-gray-900">{durations.find(d => d.value === objectives.duration)?.label}</dd>
                            </div>
                            <div>
                                <dt className="text-sm font-medium text-gray-600">Learning Objectives</dt>
                                <dd className="text-gray-900">
                                    <ul className="list-disc list-inside">
                                        {objectives.objectives.filter(o => o.trim()).map((obj, i) => (
                                            <li key={i}>{obj}</li>
                                        ))}
                                    </ul>
                                </dd>
                            </div>
                            <div>
                                <dt className="text-sm font-medium text-gray-600">Prerequisites</dt>
                                <dd className="text-gray-900">
                                    <ul className="list-disc list-inside">
                                        {objectives.prerequisites.filter(p => p.trim()).map((prereq, i) => (
                                            <li key={i}>{prereq}</li>
                                        ))}
                                    </ul>
                                </dd>
                            </div>
                        </dl>
                    </div>
                </div>
            )}

            {/* Navigation Buttons */}
            <div className="flex items-center justify-between mt-8 pt-6 border-t">
                <button
                    onClick={step === 1 ? onCancel : handleBack}
                    className="px-6 py-2 border rounded-md hover:bg-gray-50"
                >
                    {step === 1 ? "Cancel" : "Back"}
                </button>
                <button
                    onClick={handleNext}
                    disabled={!objectives.topic.trim()}
                    className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {step === 3 ? "Start Creating" : "Next"}
                </button>
            </div>
        </div>
    );
}
