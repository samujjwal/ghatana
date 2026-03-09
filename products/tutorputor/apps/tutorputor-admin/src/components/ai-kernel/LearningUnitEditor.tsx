import { useState, useEffect } from 'react';
import { Card, Button, Input, Badge } from '@ghatana/ui';
import { LearningUnit, LearningUnitStatus } from '@ghatana/tutorputor-contracts/v1/learning-unit';
import { LearningUnitValidator, ValidationResult } from '@ghatana/tutorputor-learning-kernel';

interface LearningUnitEditorProps {
    initialUnit?: Partial<LearningUnit>;
    onSave: (unit: LearningUnit) => Promise<void>;
}

export function LearningUnitEditor({ initialUnit, onSave }: LearningUnitEditorProps) {
    const [unit, setUnit] = useState<Partial<LearningUnit>>(initialUnit || {
        id: crypto.randomUUID(),
        version: 1,
        status: 'draft' as LearningUnitStatus,
        intent: { problem: '', motivation: '' },
        claims: [],
        evidence: [],
        tasks: [],
        artifacts: [],
    });

    const [validation, setValidation] = useState<ValidationResult | null>(null);
    const [validator] = useState(() => new LearningUnitValidator());

    // Real-time validation
    useEffect(() => {
        const validate = async () => {
            // Cast to LearningUnit for validation (might be incomplete)
            const result = await validator.validate(unit as LearningUnit);
            setValidation(result);
        };

        const timer = setTimeout(validate, 500); // Debounce
        return () => clearTimeout(timer);
    }, [unit, validator]);

    const handleChange = (field: string, value: unknown) => {
        setUnit(prev => ({ ...prev, [field]: value }));
    };

    const handleIntentChange = (field: string, value: string) => {
        setUnit(prev => ({
            ...prev,
            intent: { ...prev.intent!, [field]: value }
        }));
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Editor Column */}
            <div className="lg:col-span-2 space-y-6">
                <Card className="p-6 space-y-4">
                    <h2 className="text-lg font-semibold">Basic Information</h2>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium mb-1">ID</label>
                            <Input value={unit.id} disabled />
                        </div>
                        <div>
                            <label className="block text-sm font-medium mb-1">Domain</label>
                            <Input
                                value={unit.domain || ''}
                                onChange={e => handleChange('domain', e.target.value)}
                                placeholder="e.g. physics"
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium mb-1">Status</label>
                        <select
                            className="w-full rounded-md border border-gray-300 p-2 dark:bg-gray-800 dark:border-gray-700"
                            value={unit.status}
                            onChange={e => handleChange('status', e.target.value)}
                        >
                            <option value="draft">Draft</option>
                            <option value="review">Review</option>
                            <option value="published">Published</option>
                            <option value="archived">Archived</option>
                        </select>
                    </div>
                </Card>

                <Card className="p-6 space-y-4">
                    <h2 className="text-lg font-semibold">Intent (Why learn this?)</h2>

                    <div>
                        <label className="block text-sm font-medium mb-1">Problem / Misconception</label>
                        <textarea
                            className="w-full rounded-md border border-gray-300 p-2 h-24 dark:bg-gray-800 dark:border-gray-700"
                            value={unit.intent?.problem || ''}
                            onChange={e => handleIntentChange('problem', e.target.value)}
                            placeholder="What gap or misconception does this address?"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium mb-1">Motivation</label>
                        <textarea
                            className="w-full rounded-md border border-gray-300 p-2 h-24 dark:bg-gray-800 dark:border-gray-700"
                            value={unit.intent?.motivation || ''}
                            onChange={e => handleIntentChange('motivation', e.target.value)}
                            placeholder="Why is this important in the real world?"
                        />
                    </div>
                </Card>

                {/* Placeholder for Claims, Evidence, Tasks editors */}
                <Card className="p-6 text-center text-gray-500 border-dashed">
                    Claims, Evidence, and Tasks editors would go here.
                </Card>
            </div>

            {/* AI Assistant / Validation Column */}
            <div className="space-y-6">
                <Card className="p-6 sticky top-6">
                    <div className="flex justify-between items-center mb-4">
                        <h2 className="text-lg font-semibold flex items-center gap-2">
                            <span className="text-xl">🤖</span> AI Assistant
                        </h2>
                        {validation && (
                            <Badge className={validation.valid ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'}>
                                Score: {validation.score}/100
                            </Badge>
                        )}
                    </div>

                    {!validation ? (
                        <div className="text-gray-500 text-sm">Analyzing content...</div>
                    ) : (
                        <div className="space-y-4">
                            {validation.issues.length === 0 && (
                                <div className="text-green-600 text-sm flex items-center gap-2">
                                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                    </svg>
                                    All checks passed!
                                </div>
                            )}

                            {validation.issues.map((issue, i) => (
                                <div key={i} className={`p-3 rounded-lg text-sm ${issue.severity === 'error' ? 'bg-red-50 text-red-800 border border-red-100' :
                                        issue.severity === 'warning' ? 'bg-amber-50 text-amber-800 border border-amber-100' :
                                            'bg-blue-50 text-blue-800 border border-blue-100'
                                    }`}>
                                    <div className="font-medium capitalize mb-1 flex items-center gap-2">
                                        {issue.severity === 'error' && '🔴'}
                                        {issue.severity === 'warning' && '⚠️'}
                                        {issue.severity === 'info' && 'ℹ️'}
                                        {issue.field}
                                    </div>
                                    <div>{issue.message}</div>
                                    {issue.suggestion && (
                                        <div className="mt-2 text-xs opacity-90 bg-white/50 p-2 rounded">
                                            💡 {issue.suggestion}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}

                    <div className="mt-6 pt-6 border-t border-gray-200 dark:border-gray-700">
                        <Button className="w-full" disabled={!validation?.valid} onClick={() => onSave(unit as LearningUnit)}>
                            Save Learning Unit
                        </Button>
                    </div>
                </Card>
            </div>
        </div>
    );
}
