/**
 * Validation Panel Component
 * 
 * Displays detailed validation results and publishing controls.
 * Implements the "Publish" step of the 3-step flow.
 * 
 * @doc.type component
 * @doc.purpose Validation review and publishing
 * @doc.layer product
 * @doc.pattern Panel
 */

import { useState, useCallback } from 'react';
import {
    CheckCircle,
    XCircle,
    AlertTriangle,
    Info,
    Shield,
    BookOpen,
    Sparkles,
    Eye,
    Layers,
    ArrowLeft,
    Send,
    FileCheck
} from 'lucide-react';

// Types
interface ValidationCheck {
    checkId: string;
    pillar: string;
    name: string;
    passed: boolean;
    severity: 'error' | 'warning' | 'info';
    message: string;
    suggestion?: string;
    location?: string;
}

interface ValidationResult {
    status: string;
    canPublish: boolean;
    checks: ValidationCheck[];
    score: number;
    pillarScores: Record<string, number>;
    validatedAt: Date;
}

interface LearningExperience {
    id: string;
    title: string;
    description: string;
    status: string;
    claims: any[];
    estimatedTimeMinutes: number;
    gradeAdaptation: {
        gradeRange: string;
    };
}

interface ValidationPanelProps {
    experience: LearningExperience;
    validation: ValidationResult;
    onBack: () => void;
    onPublish: () => void;
}

const PILLAR_INFO: Record<string, { icon: React.ElementType; label: string; description: string }> = {
    educational: {
        icon: BookOpen,
        label: 'Educational',
        description: 'Learning design and pedagogical soundness',
    },
    experiential: {
        icon: Sparkles,
        label: 'Experiential',
        description: 'Interactive learning activities and evidence collection',
    },
    safety: {
        icon: Shield,
        label: 'Safety',
        description: 'Content appropriateness and data privacy',
    },
    technical: {
        icon: Layers,
        label: 'Technical',
        description: 'Data integrity and system compatibility',
    },
    accessibility: {
        icon: Eye,
        label: 'Accessibility',
        description: 'Inclusive design and usability',
    },
};

export function ValidationPanel({ experience, validation, onBack, onPublish }: ValidationPanelProps) {
    const [isPublishing, setIsPublishing] = useState(false);
    const [selectedPillar, setSelectedPillar] = useState<string | null>(null);

    // Get checks for a specific pillar
    const getChecksForPillar = (pillar: string) => {
        return validation.checks.filter(c => c.pillar === pillar);
    };

    // Get severity icon
    const getSeverityIcon = (severity: string, passed: boolean) => {
        if (passed) {
            return <CheckCircle className="h-5 w-5 text-green-500" />;
        }
        switch (severity) {
            case 'error':
                return <XCircle className="h-5 w-5 text-red-500" />;
            case 'warning':
                return <AlertTriangle className="h-5 w-5 text-yellow-500" />;
            default:
                return <Info className="h-5 w-5 text-blue-500" />;
        }
    };

    // Handle publish
    const handlePublish = useCallback(async () => {
        setIsPublishing(true);
        try {
            const response = await fetch(`/api/content-studio/experiences/${experience.id}/publish`, {
                method: 'POST',
            });

            if (!response.ok) throw new Error('Publish failed');

            onPublish();
        } catch (error) {
            console.error('Publish error:', error);
        } finally {
            setIsPublishing(false);
        }
    }, [experience.id, onPublish]);

    // Count issues by severity
    const errorCount = validation.checks.filter(c => !c.passed && c.severity === 'error').length;
    const warningCount = validation.checks.filter(c => !c.passed && c.severity === 'warning').length;
    const infoCount = validation.checks.filter(c => !c.passed && c.severity === 'info').length;

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
                <div className="flex items-center justify-between mb-6">
                    <button
                        onClick={onBack}
                        className="flex items-center gap-2 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Back to Editor
                    </button>
                    <div className="flex items-center gap-3">
                        <FileCheck className="h-6 w-6 text-purple-500" />
                        <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                            Validation Results
                        </h2>
                    </div>
                    <div className="w-24" /> {/* Spacer for centering */}
                </div>

                {/* Score Summary */}
                <div className="flex items-center justify-center gap-8">
                    <div className="text-center">
                        <div className={`text-6xl font-bold ${validation.score >= 80 ? 'text-green-500' :
                                validation.score >= 60 ? 'text-yellow-500' :
                                    'text-red-500'
                            }`}>
                            {validation.score}%
                        </div>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            Overall Score
                        </p>
                    </div>

                    <div className="h-24 w-px bg-gray-200 dark:bg-gray-700" />

                    <div className="flex gap-6">
                        <div className="text-center">
                            <div className="text-2xl font-bold text-red-500">{errorCount}</div>
                            <p className="text-xs text-gray-500">Errors</p>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-bold text-yellow-500">{warningCount}</div>
                            <p className="text-xs text-gray-500">Warnings</p>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-bold text-blue-500">{infoCount}</div>
                            <p className="text-xs text-gray-500">Info</p>
                        </div>
                    </div>
                </div>

                {/* Publish Status */}
                <div className={`mt-6 p-4 rounded-lg ${validation.canPublish
                        ? 'bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800'
                        : 'bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800'
                    }`}>
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            {validation.canPublish ? (
                                <CheckCircle className="h-6 w-6 text-green-500" />
                            ) : (
                                <XCircle className="h-6 w-6 text-red-500" />
                            )}
                            <div>
                                <p className={`font-medium ${validation.canPublish ? 'text-green-700 dark:text-green-300' : 'text-red-700 dark:text-red-300'
                                    }`}>
                                    {validation.canPublish
                                        ? 'Ready to Publish'
                                        : 'Cannot Publish - Fix Errors First'}
                                </p>
                                <p className="text-sm text-gray-600 dark:text-gray-400">
                                    {validation.canPublish
                                        ? 'All required checks passed'
                                        : `${errorCount} error(s) must be resolved`}
                                </p>
                            </div>
                        </div>
                        {validation.canPublish && (
                            <button
                                onClick={handlePublish}
                                disabled={isPublishing}
                                className="px-6 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium flex items-center gap-2 disabled:opacity-50"
                            >
                                {isPublishing ? (
                                    <>
                                        <Sparkles className="h-4 w-4 animate-spin" />
                                        Publishing...
                                    </>
                                ) : (
                                    <>
                                        <Send className="h-4 w-4" />
                                        Publish Experience
                                    </>
                                )}
                            </button>
                        )}
                    </div>
                </div>
            </div>

            {/* Pillar Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
                {Object.entries(PILLAR_INFO).map(([pillar, info]) => {
                    const Icon = info.icon;
                    const pillarScore = validation.pillarScores[pillar] || 0;
                    const pillarChecks = getChecksForPillar(pillar);
                    const failures = pillarChecks.filter(c => !c.passed);

                    return (
                        <button
                            key={pillar}
                            onClick={() => setSelectedPillar(selectedPillar === pillar ? null : pillar)}
                            className={`bg-white dark:bg-gray-800 rounded-xl shadow border p-4 text-left transition-all ${selectedPillar === pillar
                                    ? 'border-purple-500 ring-2 ring-purple-500/20'
                                    : 'border-gray-200 dark:border-gray-700 hover:border-purple-300'
                                }`}
                        >
                            <div className="flex items-center justify-between mb-3">
                                <Icon className="h-5 w-5 text-purple-500" />
                                <span className={`text-lg font-bold ${pillarScore >= 80 ? 'text-green-500' :
                                        pillarScore >= 60 ? 'text-yellow-500' :
                                            'text-red-500'
                                    }`}>
                                    {pillarScore}%
                                </span>
                            </div>
                            <h3 className="font-medium text-gray-900 dark:text-white mb-1">
                                {info.label}
                            </h3>
                            <p className="text-xs text-gray-500 dark:text-gray-400">
                                {failures.length > 0
                                    ? `${failures.length} issue${failures.length > 1 ? 's' : ''}`
                                    : 'All checks passed'}
                            </p>
                        </button>
                    );
                })}
            </div>

            {/* Detailed Checks */}
            {selectedPillar && (
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                    <div className="px-6 py-4 bg-gray-50 dark:bg-gray-800/50 border-b dark:border-gray-700">
                        <h3 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                            {(() => {
                                const Icon = PILLAR_INFO[selectedPillar]?.icon || Info;
                                return <Icon className="h-5 w-5 text-purple-500" />;
                            })()}
                            {PILLAR_INFO[selectedPillar]?.label} Checks
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            {PILLAR_INFO[selectedPillar]?.description}
                        </p>
                    </div>
                    <div className="divide-y dark:divide-gray-700">
                        {getChecksForPillar(selectedPillar).map((check) => (
                            <div
                                key={check.checkId}
                                className={`p-4 ${!check.passed && check.severity === 'error' ? 'bg-red-50 dark:bg-red-900/10' : ''}`}
                            >
                                <div className="flex items-start gap-3">
                                    {getSeverityIcon(check.severity, check.passed)}
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2">
                                            <h4 className="font-medium text-gray-900 dark:text-white">
                                                {check.name}
                                            </h4>
                                            <span className={`px-2 py-0.5 text-xs rounded-full ${check.passed
                                                    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                                                    : check.severity === 'error'
                                                        ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
                                                        : check.severity === 'warning'
                                                            ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300'
                                                            : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                                                }`}>
                                                {check.passed ? 'Passed' : check.severity}
                                            </span>
                                        </div>
                                        <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                                            {check.message}
                                        </p>
                                        {check.suggestion && !check.passed && (
                                            <p className="text-sm text-purple-600 dark:text-purple-400 mt-2 flex items-start gap-1">
                                                <Sparkles className="h-4 w-4 mt-0.5 flex-shrink-0" />
                                                {check.suggestion}
                                            </p>
                                        )}
                                        {check.location && (
                                            <p className="text-xs text-gray-500 mt-1">
                                                Location: {check.location}
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* All Checks (when no pillar selected) */}
            {!selectedPillar && (
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                    <div className="px-6 py-4 bg-gray-50 dark:bg-gray-800/50 border-b dark:border-gray-700">
                        <h3 className="font-semibold text-gray-900 dark:text-white">
                            All Validation Checks
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Click a pillar card above to filter by category
                        </p>
                    </div>
                    <div className="divide-y dark:divide-gray-700 max-h-96 overflow-y-auto">
                        {validation.checks
                            .filter(c => !c.passed)
                            .sort((a, b) => {
                                const order = { error: 0, warning: 1, info: 2 };
                                return order[a.severity] - order[b.severity];
                            })
                            .map((check) => (
                                <div
                                    key={check.checkId}
                                    className="p-4 flex items-start gap-3"
                                >
                                    {getSeverityIcon(check.severity, check.passed)}
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs px-2 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 rounded">
                                                {check.pillar}
                                            </span>
                                            <h4 className="font-medium text-gray-900 dark:text-white">
                                                {check.name}
                                            </h4>
                                        </div>
                                        <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                                            {check.message}
                                        </p>
                                        {check.suggestion && (
                                            <p className="text-sm text-purple-600 dark:text-purple-400 mt-1">
                                                💡 {check.suggestion}
                                            </p>
                                        )}
                                    </div>
                                </div>
                            ))}
                        {validation.checks.filter(c => !c.passed).length === 0 && (
                            <div className="p-8 text-center">
                                <CheckCircle className="h-12 w-12 text-green-500 mx-auto mb-3" />
                                <p className="text-gray-600 dark:text-gray-400">
                                    All validation checks passed!
                                </p>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
