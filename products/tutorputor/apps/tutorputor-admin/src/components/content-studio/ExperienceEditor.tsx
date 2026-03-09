/**
 * Experience Editor Component
 * 
 * Editor for refining Learning Experiences.
 * Implements the "Refine" step of the 3-step flow.
 * 
 * @doc.type component
 * @doc.purpose Experience refinement editor
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback } from 'react';
import {
    Plus,
    CheckCircle,
    AlertTriangle,
    Sparkles,
    Target,
    FileText,
    Clock,
    GraduationCap,
    ChevronDown,
    ChevronRight,
    Send
} from 'lucide-react';
import { SimulationBuilder } from './SimulationBuilder';

// Types (shared across Content Studio components)
interface GradeAdaptation {
    gradeRange: string;
    mathLevel: string;
    rigorLevel: string;
    scaffoldingLevel: string;
    vocabularyComplexity: number;
    readingLevel: number;
    prerequisiteConcepts: string[];
}

interface LearningEvidence {
    id: string;
    claimId: string;
    type: string;
    description: string;
    minimumScore: number;
    weight: number;
}

interface ExperienceTask {
    id: string;
    claimId: string;
    type: string;
    title: string;
    instructions: string;
    evidenceIds: string[];
    estimatedMinutes: number;
    orderIndex: number;
}

interface LearningClaim {
    id: string;
    text: string;
    bloom: string;
    masteryThreshold: number;
    orderIndex: number;
    evidenceRequirements: LearningEvidence[];
    tasks: ExperienceTask[];
}

interface LearningExperience {
    id: string;
    tenantId: string;
    slug: string;
    title: string;
    description: string;
    status: string;
    version: number;
    gradeAdaptation: GradeAdaptation;
    claims: LearningClaim[];
    estimatedTimeMinutes: number;
    keywords: string[];
    moduleId?: string;
    simulationId?: string;
    authorId?: string;
    createdAt: Date;
    updatedAt: Date;
}

interface ValidationCheck {
    checkId: string;
    pillar: string;
    name: string;
    passed: boolean;
    severity: 'error' | 'warning' | 'info';
    message: string;
    suggestion?: string;
}

interface ValidationResult {
    status: string;
    canPublish: boolean;
    checks: ValidationCheck[];
    score: number;
    pillarScores: Record<string, number>;
    validatedAt: Date;
}

interface ExperienceEditorProps {
    experience: LearningExperience;
    validation: ValidationResult | null;
    onExperienceUpdated: (experience: LearningExperience, validation?: ValidationResult) => void;
    onValidate: (experience: LearningExperience, validation: ValidationResult) => void;
}

const BLOOM_LEVELS = [
    { value: 'remember', label: 'Remember', color: 'bg-gray-100 text-gray-700' },
    { value: 'understand', label: 'Understand', color: 'bg-blue-100 text-blue-700' },
    { value: 'apply', label: 'Apply', color: 'bg-green-100 text-green-700' },
    { value: 'analyze', label: 'Analyze', color: 'bg-yellow-100 text-yellow-700' },
    { value: 'evaluate', label: 'Evaluate', color: 'bg-orange-100 text-orange-700' },
    { value: 'create', label: 'Create', color: 'bg-purple-100 text-purple-700' },
];

const TASK_TYPES = [
    { value: 'prediction', label: 'Prediction', icon: '🔮' },
    { value: 'simulation', label: 'Simulation', icon: '🎮' },
    { value: 'explanation', label: 'Explanation', icon: '💬' },
    { value: 'construction', label: 'Construction', icon: '🔧' },
];

export function ExperienceEditor({ experience, validation, onExperienceUpdated, onValidate }: ExperienceEditorProps) {
    const [expandedClaims, setExpandedClaims] = useState<Set<string>>(new Set((experience.claims || []).map(c => c.id)));
    // Removed unused state
    const [refinementPrompt, setRefinementPrompt] = useState('');
    const [isRefining, setIsRefining] = useState(false);
    // Track linked simulation
    const [linkedSimulation, setLinkedSimulation] = useState<{
        id: string;
        version: string;
    } | null>(
        experience.simulationId && experience.simulationVersion
            ? { id: experience.simulationId, version: experience.simulationVersion }
            : null
    );
    const [showSimulationBuilder, setShowSimulationBuilder] = useState(false);

    // Toggle claim expansion
    const toggleClaim = useCallback((claimId: string) => {
        setExpandedClaims(prev => {
            const next = new Set(prev);
            if (next.has(claimId)) {
                next.delete(claimId);
            } else {
                next.add(claimId);
            }
            return next;
        });
    }, []);

    // Handle AI refinement
    const handleRefine = useCallback(async () => {
        if (!refinementPrompt.trim()) return;

        setIsRefining(true);
        try {
            const response = await fetch(`/api/content-studio/experiences/${experience.id}/refine`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    refinementPrompt,
                    // Include simulation data to preserve it
                    simulationManifestId: linkedSimulation?.id,
                    simulationVersion: linkedSimulation?.version,
                }),
            });

            if (!response.ok) throw new Error('Refinement failed');

            const result = await response.json();
            if (result.success) {
                onExperienceUpdated(result.experience, result.validation);
                setRefinementPrompt('');
            }
        } catch (error) {
            console.error('Refinement error:', error);
        } finally {
            setIsRefining(false);
        }
    }, [experience.id, refinementPrompt, linkedSimulation, onExperienceUpdated]);

    // Handle validation
    const handleValidate = useCallback(async () => {
        try {
            const response = await fetch(`/api/content-studio/experiences/${experience.id}/validate`, {
                method: 'POST',
            });

            if (!response.ok) throw new Error('Validation failed');

            const validationResult = await response.json();
            onValidate(experience, validationResult);
        } catch (error) {
            console.error('Validation error:', error);
        }
    }, [experience, onValidate]);

    // Get bloom level styling
    const getBloomStyle = (bloom: string) => {
        const level = BLOOM_LEVELS.find(l => l.value === bloom);
        return level?.color || 'bg-gray-100 text-gray-700';
    };

    // Get task type info
    const getTaskType = (type: string) => {
        return TASK_TYPES.find(t => t.value === type) || { value: type, label: type, icon: '📝' };
    };

    // Get validation status color
    const getValidationColor = () => {
        if (!validation) return 'text-gray-400';
        if (validation.status === 'valid') return 'text-green-500';
        if (validation.status === 'warnings') return 'text-yellow-500';
        return 'text-red-500';
    };

    return (
        <div className="grid grid-cols-1 xl:grid-cols-4 gap-6">
            {/* Main Editor Panel - Takes 3 columns */}
            <div className="xl:col-span-3 space-y-6">
                {/* Header Card - Improved */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                    {/* Status Bar */}
                    <div className={`h-1 ${experience.status === 'draft' ? 'bg-yellow-400' :
                        experience.status === 'published' ? 'bg-green-500' :
                            experience.status === 'review' ? 'bg-blue-500' :
                                'bg-gray-300'
                        }`} />
                    
                    <div className="p-6">
                        <div className="flex items-start justify-between mb-4">
                            <div className="flex-1">
                                <div className="flex items-center gap-2 mb-2">
                                    <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
                                        {experience.title}
                                    </h2>
                                    <span className={`px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wide
                                        ${experience.status === 'draft' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300' :
                                        experience.status === 'published' ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300' :
                                            experience.status === 'review' ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300' :
                                                'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
                                        }`}>
                                        {experience.status}
                                    </span>
                                </div>
                                <p className="text-gray-600 dark:text-gray-400 text-sm leading-relaxed">
                                    {experience.description}
                                </p>
                            </div>
                        </div>

                        {/* Quick Stats Grid */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                            <div className="flex items-center gap-3 p-3 bg-purple-50 dark:bg-purple-900/20 rounded-lg">
                                <Target className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                                <div>
                                    <p className="text-xs text-gray-600 dark:text-gray-400">Claims</p>
                                    <p className="text-lg font-bold text-gray-900 dark:text-white">{(experience.claims || []).length}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                                <Clock className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                                <div>
                                    <p className="text-xs text-gray-600 dark:text-gray-400">Duration</p>
                                    <p className="text-lg font-bold text-gray-900 dark:text-white">{experience.estimatedTimeMinutes}m</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 bg-green-50 dark:bg-green-900/20 rounded-lg">
                                <GraduationCap className="h-5 w-5 text-green-600 dark:text-green-400" />
                                <div>
                                    <p className="text-xs text-gray-600 dark:text-gray-400">Grade</p>
                                    <p className="text-sm font-bold text-gray-900 dark:text-white">
                                        {(experience.gradeAdaptation?.gradeRange || 'N/A').replace(/_/g, '-').toUpperCase()}
                                    </p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 bg-gradient-to-br from-yellow-50 to-orange-50 dark:from-yellow-900/20 dark:to-orange-900/20 rounded-lg">
                                {validation?.status === 'valid' ? (
                                    <CheckCircle className="h-5 w-5 text-green-600 dark:text-green-400" />
                                ) : (
                                    <AlertTriangle className="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
                                )}
                                <div>
                                    <p className="text-xs text-gray-600 dark:text-gray-400">Quality</p>
                                    <p className={`text-lg font-bold ${validation?.score >= 80 ? 'text-green-600 dark:text-green-400' :
                                        validation?.score >= 60 ? 'text-yellow-600 dark:text-yellow-400' :
                                            'text-red-600 dark:text-red-400'
                                        }`}>
                                        {validation?.score || '--'}%
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Claims Section - Enhanced */}
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <h3 className="text-xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
                            <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                                <Target className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                            </div>
                            Learning Claims & Objectives
                        </h3>
                        <Button size="sm" variant="outline">
                            <Plus className="h-4 w-4 mr-1" />
                            Add Claim
                        </Button>
                    </div>

                    {(experience.claims || []).length === 0 ? (
                        <div className="bg-gray-50 dark:bg-gray-800 rounded-xl p-12 text-center border-2 border-dashed border-gray-300 dark:border-gray-600">
                            <Target className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                            <h4 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">No Claims Yet</h4>
                            <p className="text-gray-600 dark:text-gray-400 mb-4">Add learning claims to define what students will learn</p>
                            <Button size="sm">
                                <Plus className="h-4 w-4 mr-1" />
                                Add Your First Claim
                            </Button>
                        </div>
                    ) : (
                        (experience.claims || []).map((claim, index) => (
                            <div
                                key={claim.id}
                                className="bg-white dark:bg-gray-800 rounded-xl shadow-md border-2 border-gray-200 dark:border-gray-700 hover:border-purple-300 dark:hover:border-purple-700 overflow-hidden transition-all"
                            >
                                {/* Claim Header */}
                                <div
                                    className="flex items-center gap-3 p-4 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-750"
                                    onClick={() => toggleClaim(claim.id)}
                                >
                                    <div className="flex-shrink-0">
                                        {expandedClaims.has(claim.id) ? (
                                            <ChevronDown className="h-5 w-5 text-gray-400" />
                                        ) : (
                                            <ChevronRight className="h-5 w-5 text-gray-400" />
                                        )}
                                    </div>
                                    <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-gradient-to-br from-purple-100 to-blue-100 dark:from-purple-900/30 dark:to-blue-900/30 flex items-center justify-center">
                                        <span className="text-sm font-bold text-purple-700 dark:text-purple-300">
                                            {index + 1}
                                        </span>
                                    </div>
                                    <span className={`flex-shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold ${getBloomStyle(claim.bloom)}`}>
                                        {claim.bloom.toUpperCase()}
                                    </span>
                                    <p className="flex-1 text-gray-900 dark:text-gray-100 font-medium">{claim.text}</p>
                                    <div className="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
                                        <div className="flex items-center gap-1">
                                            <FileText className="h-4 w-4" />
                                            <span>{(claim.evidenceRequirements || []).length} evidence</span>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <Target className="h-4 w-4" />
                                            <span>{(claim.tasks || []).length} tasks</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Claim Details */}
                                {expandedClaims.has(claim.id) && (
                                    <div className="border-t-2 dark:border-gray-700 p-6 space-y-6 bg-gradient-to-br from-gray-50 to-white dark:from-gray-800 dark:to-gray-800/50">
                                        {/* Evidence Requirements */}
                                        <div>
                                            <div className="flex items-center justify-between mb-3">
                                                <h4 className="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                                                    <FileText className="h-4 w-4 text-blue-600" />
                                                    Evidence Requirements
                                                </h4>
                                                <Button size="sm" variant="ghost">
                                                    <Plus className="h-3 w-3 mr-1" />
                                                    Add Evidence
                                                </Button>
                                            </div>
                                            {(claim.evidenceRequirements || []).length === 0 ? (
                                                <p className="text-sm text-gray-500 dark:text-gray-400 italic">No evidence requirements defined</p>
                                            ) : (
                                                <div className="space-y-2">
                                                    {(claim.evidenceRequirements || []).map((evidence, eIdx) => (
                                                        <div
                                                            key={evidence.id}
                                                            className="flex items-start gap-3 p-3 bg-white dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600 hover:border-blue-400 dark:hover:border-blue-600 transition-colors"
                                                        >
                                                            <span className="flex-shrink-0 w-6 h-6 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 flex items-center justify-center text-xs font-bold">
                                                                {eIdx + 1}
                                                            </span>
                                                            <div className="flex-1">
                                                                <div className="flex items-center gap-2 mb-1">
                                                                    <span className="px-2 py-0.5 text-xs font-medium bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded">
                                                                        {evidence.type.replace(/_/g, ' ').toUpperCase()}
                                                                    </span>
                                                                    <span className="text-xs text-gray-500">
                                                                        Min: {evidence.minimumScore}% • Weight: {evidence.weight}
                                                                    </span>
                                                                </div>
                                                                <p className="text-sm text-gray-700 dark:text-gray-300">
                                                                    {evidence.description}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>

                                        {/* Tasks */}
                                        <div>
                                            <div className="flex items-center justify-between mb-3">
                                                <h4 className="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                                                    <Target className="h-4 w-4 text-green-600" />
                                                    Learning Tasks
                                                </h4>
                                                <Button size="sm" variant="ghost">
                                                    <Plus className="h-3 w-3 mr-1" />
                                                    Add Task
                                                </Button>
                                            </div>
                                            {(claim.tasks || []).length === 0 ? (
                                                <p className="text-sm text-gray-500 dark:text-gray-400 italic">No tasks defined</p>
                                            ) : (
                                                <div className="space-y-3">
                                                    {(claim.tasks || []).map((task) => {
                                                        const taskType = getTaskType(task.type);
                                                        return (
                                                            <div
                                                                key={task.id}
                                                                className="p-4 bg-white dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600 hover:border-green-400 dark:hover:border-green-600 hover:shadow-md transition-all"
                                                            >
                                                                <div className="flex items-start gap-3 mb-2">
                                                                    <span className="text-2xl">{taskType.icon}</span>
                                                                    <div className="flex-1">
                                                                        <div className="flex items-center gap-2 mb-1">
                                                                            <span className="font-semibold text-gray-900 dark:text-gray-100">
                                                                                {task.title}
                                                                            </span>
                                                                            <span className="px-2 py-0.5 text-xs bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded">
                                                                                {taskType.label}
                                                                            </span>
                                                                            <span className="text-xs text-gray-500 flex items-center gap-1">
                                                                                <Clock className="h-3 w-3" />
                                                                                {task.estimatedMinutes} min
                                                                            </span>
                                                                        </div>
                                                                        <p className="text-sm text-gray-600 dark:text-gray-400">
                                                                            {task.instructions}
                                                                        </p>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Sidebar - Takes 1 column */}
            <div className="xl:col-span-1 space-y-6">
                {/* AI Refinement Panel */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center gap-2 mb-4">
                        <div className="p-2 bg-gradient-to-br from-purple-500 to-blue-500 rounded-lg">
                            <Sparkles className="h-4 w-4 text-white" />
                        </div>
                        <h3 className="font-semibold text-gray-900 dark:text-white">AI Refinement</h3>
                    </div>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                        Describe changes you'd like to make and AI will help refine the experience
                    </p>
                    <div className="space-y-3">
                        <textarea
                            value={refinementPrompt}
                            onChange={(e) => setRefinementPrompt(e.target.value)}
                            placeholder="e.g., Add a claim about applying Newton's third law to real-world examples"
                            rows={3}
                            className="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm placeholder-gray-400 focus:ring-2 focus:ring-purple-500 focus:border-transparent resize-none"
                        />
                        <button
                            onClick={handleRefine}
                            disabled={isRefining || !refinementPrompt.trim()}
                            className="w-full px-4 py-2 bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600 text-white rounded-lg font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                        >
                            {isRefining ? (
                                <>
                                    <Sparkles className="h-4 w-4 animate-spin" />
                                    Refining...
                                </>
                            ) : (
                                <>
                                    <Send className="h-4 w-4" />
                                    Refine Experience
                                </>
                            )}
                        </button>
                    </div>
                </div>

                {/* Validation Summary */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h3 className="font-semibold text-gray-900 dark:text-white">Validation</h3>
                        {validation && (
                            <span className={`text-2xl font-bold ${validation.score >= 80 ? 'text-green-500' :
                                validation.score >= 60 ? 'text-yellow-500' :
                                    'text-red-500'
                                }`}>
                                {validation.score}%
                            </span>
                        )}
                    </div>

                    {validation && (
                        <div className="space-y-3 mb-4">
                            {Object.entries(validation.pillarScores).map(([pillar, score]) => (
                                <div key={pillar}>
                                    <div className="flex items-center justify-between text-sm mb-1">
                                        <span className="text-gray-600 dark:text-gray-400 capitalize">
                                            {pillar}
                                        </span>
                                        <span className={`font-medium ${score >= 80 ? 'text-green-500' :
                                            score >= 60 ? 'text-yellow-500' :
                                                'text-red-500'
                                            }`}>
                                            {score}%
                                        </span>
                                    </div>
                                    <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                                        <div
                                            className={`h-full rounded-full ${score >= 80 ? 'bg-green-500' :
                                                score >= 60 ? 'bg-yellow-500' :
                                                    'bg-red-500'
                                                }`}
                                            style={{ width: `${score}%` }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    <button
                        onClick={handleValidate}
                        className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg font-medium text-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center justify-center gap-2"
                    >
                        <CheckCircle className="h-4 w-4" />
                        Run Validation
                    </button>
                </div>

                {/* Quick Actions */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
                    <h3 className="font-semibold text-gray-900 dark:text-white mb-4">Quick Actions</h3>
                    <div className="space-y-2">
                        <button className="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 rounded-lg flex items-center gap-2">
                            <GraduationCap className="h-4 w-4" />
                            Adapt Grade Level
                        </button>
                        <button className="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 rounded-lg flex items-center gap-2">
                            <Plus className="h-4 w-4" />
                            Add Claim
                        </button>
                        <button 
                            onClick={() => setShowSimulationBuilder(!showSimulationBuilder)}
                            className="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 rounded-lg flex items-center gap-2"
                        >
                            <FileText className="h-4 w-4" />
                            {linkedSimulation ? 'Update Simulation' : 'Link Simulation'}
                        </button>
                    </div>
                </div>

                {/* Simulation Builder */}
                {showSimulationBuilder && (
                    <SimulationBuilder
                        experienceId={experience.id}
                        experienceTitle={experience.title}
                        domain={experience.domain || 'MATH'}
                        currentSimulation={undefined}
                        onSimulationGenerated={(simulation) => {
                            console.log('Simulation generated:', simulation);
                        }}
                        onSimulationLinked={async (id, version) => {
                            setLinkedSimulation({ id, version });
                            setShowSimulationBuilder(false);
                            
                            // Save to backend immediately
                            try {
                                const response = await fetch(`/api/content-studio/experiences/${experience.id}`, {
                                    method: 'PUT',
                                    headers: { 'Content-Type': 'application/json', 'x-tenant-id': 'default' },
                                    body: JSON.stringify({
                                        title: experience.title,
                                        description: experience.description,
                                        simulationManifestId: id,
                                        simulationVersion: version,
                                    }),
                                });
                                
                                if (response.ok) {
                                    const updated = await response.json();
                                    onExperienceUpdated(updated, validation || undefined);
                                }
                            } catch (error) {
                                console.error('Failed to save simulation link:', error);
                            }
                        }}
                    />
                )}
            </div>
        </div>
    );
}
