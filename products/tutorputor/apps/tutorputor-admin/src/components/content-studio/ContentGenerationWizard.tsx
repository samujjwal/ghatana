/**
 * Content Generation Wizard Component
 *
 * AI-powered content generation with simplified topic-centric flow.
 * AI infers audience, objectives, and content types from topic input.
 *
 * @doc.type component
 * @doc.purpose Simplified content generation with AI inference
 * @doc.layer product
 * @doc.pattern Wizard
 */

import { useState, useEffect } from 'react';
import { Sparkles, Globe, PenTool, HelpCircle, Settings, Check, Loader2, X, Edit2, Wand2 } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { useContentStudioApi } from '../../services/contentStudioApi';

interface ContentGenerationWizardProps {
    onContentGenerated: (content: unknown) => void;
    onCancel: () => void;
}

interface InferredParams {
    topic: string;
    gradeLevel: string;
    subject: string;
    contentTypes: string[];
    learningObjectives: string[];
    confidence: number;
}

const CONTENT_TYPE_OPTIONS = [
    { id: 'realWorldUseCases', label: 'Real-World Use Cases', icon: Globe, description: 'Industry applications and case studies', default: true },
    { id: 'practiceWorksheets', label: 'Practice Worksheets', icon: PenTool, description: 'Hands-on exercises and problems', default: true },
    { id: 'quizzes', label: 'Interactive Quizzes', icon: HelpCircle, description: 'Assessment and evaluation tools', default: true },
    { id: 'simulations', label: 'Simulations', icon: Settings, description: 'Interactive learning experiences', default: false },
];

const GRADE_LEVELS = [
    'Grade 1', 'Grade 2', 'Grade 3', 'Grade 4', 'Grade 5',
    'Grade 6', 'Grade 7', 'Grade 8', 'Grade 9', 'Grade 10',
    'Grade 11', 'Grade 12', 'College', 'Professional'
];

const SUBJECTS = [
    'Mathematics', 'Science', 'English', 'History', 'Geography',
    'Physics', 'Chemistry', 'Biology', 'Computer Science', 'Art',
    'Music', 'Physical Education', 'Economics', 'Psychology', 'Sociology'
];

export function ContentGenerationWizard({ onContentGenerated, onCancel }: ContentGenerationWizardProps) {
    const [step, setStep] = useState<'input' | 'preview' | 'generating'>('input');
    const [topic, setTopic] = useState('');
    const [isInferring, setIsInferring] = useState(false);
    const [inferred, setInferred] = useState<InferredParams | null>(null);
    const [editedParams, setEditedParams] = useState<Partial<InferredParams>>({});
    const [isGenerating, setIsGenerating] = useState(false);
    const [generationProgress, setGenerationProgress] = useState(0);

    const contentStudioApi = useContentStudioApi();

    // Simulate AI inference from topic
    const inferParams = async (topicText: string): Promise<InferredParams> => {
        // Simulate API delay for inference
        await new Promise(resolve => setTimeout(resolve, 800));

        // Simple keyword-based inference logic
        const lowerTopic = topicText.toLowerCase();

        // Infer subject from keywords
        const subjectKeywords: Record<string, string[]> = {
            'Mathematics': ['math', 'algebra', 'geometry', 'calculus', 'equation', 'fraction', 'number', 'arithmetic'],
            'Physics': ['physics', 'force', 'motion', 'energy', 'gravity', 'electricity', 'magnetism'],
            'Chemistry': ['chemistry', 'element', 'molecule', 'atom', 'reaction', 'compound'],
            'Biology': ['biology', 'cell', 'organism', 'ecosystem', 'photosynthesis', 'dna', 'gene'],
            'Computer Science': ['programming', 'code', 'algorithm', 'software', 'computer', 'data', 'ai', 'machine learning'],
            'History': ['history', 'war', 'civilization', 'empire', 'revolution', 'ancient'],
            'English': ['grammar', 'literature', 'writing', 'essay', 'poem', 'novel'],
            'Geography': ['geography', 'climate', 'continent', 'country', 'map', 'landscape']
        };

        let inferredSubject = 'General';
        for (const [subject, keywords] of Object.entries(subjectKeywords)) {
            if (keywords.some(kw => lowerTopic.includes(kw))) {
                inferredSubject = subject;
                break;
            }
        }

        // Infer grade level from complexity keywords
        const advancedTerms = ['advanced', 'complex', 'calculus', 'differential', 'quantum', 'molecular'];
        const elementaryTerms = ['basic', 'introduction', 'elementary', 'primary', 'simple'];

        let inferredGrade = 'Grade 9';
        if (advancedTerms.some(term => lowerTopic.includes(term))) {
            inferredGrade = 'College';
        } else if (elementaryTerms.some(term => lowerTopic.includes(term))) {
            inferredGrade = 'Grade 5';
        }

        // Infer content types based on subject
        const contentTypes = ['realWorldUseCases', 'practiceWorksheets', 'quizzes'];
        if (['Physics', 'Chemistry', 'Biology'].includes(inferredSubject)) {
            contentTypes.push('simulations');
        }

        // Generate learning objectives
        const objectives = [
            `Understand the fundamentals of ${topicText}`,
            `Apply ${topicText} concepts to solve problems`,
            `Analyze real-world scenarios involving ${topicText}`,
        ];

        return {
            topic: topicText,
            gradeLevel: inferredGrade,
            subject: inferredSubject,
            contentTypes,
            learningObjectives: objectives,
            confidence: 0.85
        };
    };

    const handleTopicSubmit = async () => {
        if (!topic.trim()) return;

        setIsInferring(true);
        try {
            const params = await inferParams(topic.trim());
            setInferred(params);
            setEditedParams(params);
            setStep('preview');
        } catch (error) {
            console.error('Inference failed:', error);
        } finally {
            setIsInferring(false);
        }
    };

    const handleGenerate = async () => {
        setIsGenerating(true);
        setStep('generating');

        // Simulate progress updates
        const progressInterval = setInterval(() => {
            setGenerationProgress(prev => {
                if (prev >= 90) return prev;
                return prev + Math.random() * 15;
            });
        }, 1000);

        try {
            const formData = {
                topic: editedParams.topic || topic,
                gradeLevel: editedParams.gradeLevel || inferred?.gradeLevel || '',
                subject: editedParams.subject || inferred?.subject || '',
                includeRealWorldUseCases: editedParams.contentTypes?.includes('realWorldUseCases') ?? true,
                includePracticeWorksheets: editedParams.contentTypes?.includes('practiceWorksheets') ?? true,
                includeQuizzes: editedParams.contentTypes?.includes('quizzes') ?? true,
                includeSimulations: editedParams.contentTypes?.includes('simulations') ?? false,
                includeAnimations: false,
                includeProjects: false,
                learningObjectives: editedParams.learningObjectives || inferred?.learningObjectives || []
            };

            const result = await contentStudioApi.generateContent(formData as unknown as Parameters<typeof contentStudioApi.generateContent>[0]);

            clearInterval(progressInterval);
            setGenerationProgress(100);

            // Small delay to show completion
            setTimeout(() => {
                onContentGenerated(result.experience);
            }, 500);
        } catch (error) {
            console.error('Generation failed:', error);
            clearInterval(progressInterval);
        }
    };

    const toggleContentType = (typeId: string) => {
        const current = editedParams.contentTypes || inferred?.contentTypes || [];
        const updated = current.includes(typeId)
            ? current.filter(t => t !== typeId)
            : [...current, typeId];
        setEditedParams({ ...editedParams, contentTypes: updated });
    };

    const updateLearningObjective = (index: number, value: string) => {
        const current = [...(editedParams.learningObjectives || inferred?.learningObjectives || [])];
        current[index] = value;
        setEditedParams({ ...editedParams, learningObjectives: current });
    };

    const renderInputStep = () => (
        <div className="space-y-8">
            <div className="text-center">
                <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-purple-100 dark:bg-purple-900/30 mb-4">
                    <Sparkles className="w-8 h-8 text-purple-600 dark:text-purple-400" />
                </div>
                <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                    What do you want to teach?
                </h2>
                <p className="text-gray-600 dark:text-gray-400 max-w-md mx-auto">
                    Just enter a topic and our AI will infer the best approach for your learners.
                </p>
            </div>

            <div className="max-w-lg mx-auto space-y-4">
                <div className="relative">
                    <input
                        type="text"
                        value={topic}
                        onChange={(e) => setTopic(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && topic.trim() && handleTopicSubmit()}
                        placeholder="e.g., Photosynthesis, Fractions, World War II, Machine Learning..."
                        className="w-full px-4 py-4 text-lg border border-gray-300 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white placeholder:text-gray-400"
                    />
                    {topic && (
                        <button
                            onClick={() => setTopic('')}
                            className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                        >
                            <X className="w-5 h-5" />
                        </button>
                    )}
                </div>

                <Button
                    onClick={handleTopicSubmit}
                    disabled={!topic.trim() || isInferring}
                    className="w-full py-4 text-lg bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white font-medium"
                >
                    {isInferring ? (
                        <>
                            <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                            Analyzing topic...
                        </>
                    ) : (
                        <>
                            <Wand2 className="w-5 h-5 mr-2" />
                            Generate Content
                        </>
                    )}
                </Button>

                <p className="text-center text-sm text-gray-500 dark:text-gray-400">
                    Our AI will automatically determine the audience, learning objectives, and content types.
                </p>
            </div>
        </div>
    );

    const renderPreviewStep = () => {
        if (!inferred) return null;

        const params = { ...inferred, ...editedParams };

        return (
            <div className="space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                            Review AI Suggestions
                        </h2>
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            AI confidence: {Math.round(inferred.confidence * 100)}% — Everything looks good?
                        </p>
                    </div>
                    <div className="flex gap-2">
                        <Button variant="outline" onClick={() => setStep('input')}>
                            <Edit2 className="w-4 h-4 mr-2" />
                            Change Topic
                        </Button>
                    </div>
                </div>

                {/* Topic Summary */}
                <div className="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-xl p-6">
                    <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                        {params.topic}
                    </h3>
                    <div className="flex flex-wrap gap-2">
                        <span className="px-3 py-1 bg-white dark:bg-gray-800 rounded-full text-sm font-medium text-purple-700 dark:text-purple-300 shadow-sm">
                            {params.gradeLevel}
                        </span>
                        <span className="px-3 py-1 bg-white dark:bg-gray-800 rounded-full text-sm font-medium text-blue-700 dark:text-blue-300 shadow-sm">
                            {params.subject}
                        </span>
                    </div>
                </div>

                {/* Editable Parameters */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Target Audience
                        </label>
                        <select
                            value={params.gradeLevel}
                            onChange={(e) => setEditedParams({ ...editedParams, gradeLevel: e.target.value })}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
                        >
                            {GRADE_LEVELS.map(level => (
                                <option key={level} value={level}>{level}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Subject Area
                        </label>
                        <select
                            value={params.subject}
                            onChange={(e) => setEditedParams({ ...editedParams, subject: e.target.value })}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
                        >
                            {SUBJECTS.map(subject => (
                                <option key={subject} value={subject}>{subject}</option>
                            ))}
                        </select>
                    </div>
                </div>

                {/* Learning Objectives */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                        Learning Objectives (AI-generated, editable)
                    </label>
                    <div className="space-y-2">
                        {(params.learningObjectives || []).map((objective, index) => (
                            <div key={index} className="flex items-center gap-2">
                                <span className="w-6 h-6 rounded-full bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center text-xs font-medium text-purple-600 dark:text-purple-400">
                                    {index + 1}
                                </span>
                                <input
                                    type="text"
                                    value={objective}
                                    onChange={(e) => updateLearningObjective(index, e.target.value)}
                                    className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white text-sm"
                                />
                            </div>
                        ))}
                    </div>
                </div>

                {/* Content Types */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                        Content Types to Include
                    </label>
                    <div className="grid grid-cols-2 gap-3">
                        {CONTENT_TYPE_OPTIONS.map((type) => {
                            const isSelected = params.contentTypes?.includes(type.id);
                            const Icon = type.icon;
                            return (
                                <div
                                    key={type.id}
                                    onClick={() => toggleContentType(type.id)}
                                    className={`cursor-pointer rounded-lg border p-3 transition-all ${
                                        isSelected
                                            ? 'border-purple-500 bg-purple-50 dark:bg-purple-900/20'
                                            : 'border-gray-300 dark:border-gray-600 hover:border-gray-400'
                                    }`}
                                >
                                    <div className="flex items-center gap-2">
                                        <Icon className={`w-4 h-4 ${isSelected ? 'text-purple-500' : 'text-gray-400'}`} />
                                        <span className={`text-sm font-medium ${isSelected ? 'text-gray-900 dark:text-white' : 'text-gray-600 dark:text-gray-400'}`}>
                                            {type.label}
                                        </span>
                                        {isSelected && <Check className="w-4 h-4 text-purple-500 ml-auto" />}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* Actions */}
                <div className="flex gap-3 pt-4">
                    <Button variant="outline" onClick={onCancel} className="flex-1">
                        Cancel
                    </Button>
                    <Button
                        onClick={handleGenerate}
                        disabled={isGenerating}
                        className="flex-1 bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white"
                    >
                        {isGenerating ? (
                            <>
                                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                Starting...
                            </>
                        ) : (
                            <>
                                <Sparkles className="w-4 h-4 mr-2" />
                                Confirm & Generate
                            </>
                        )}
                    </Button>
                </div>
            </div>
        );
    };

    const renderGeneratingStep = () => (
        <div className="text-center py-12 space-y-6">
            <div className="relative">
                <div className="w-24 h-24 rounded-full bg-gradient-to-r from-purple-500 to-blue-500 flex items-center justify-center mx-auto animate-pulse">
                    <Sparkles className="w-12 h-12 text-white" />
                </div>
                <div className="absolute inset-0 w-24 h-24 rounded-full border-4 border-purple-200 dark:border-purple-800 mx-auto animate-ping opacity-20" />
            </div>

            <div>
                <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-2">
                    Creating your learning experience
                </h3>
                <p className="text-gray-600 dark:text-gray-400">
                    AI is generating lessons, exercises, and assessments...
                </p>
            </div>

            {/* Progress Bar */}
            <div className="max-w-md mx-auto">
                <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                    <div
                        className="h-full bg-gradient-to-r from-purple-500 to-blue-500 transition-all duration-500"
                        style={{ width: `${Math.min(generationProgress, 100)}%` }}
                    />
                </div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
                    {Math.round(generationProgress)}% complete
                </p>
            </div>

            {/* Generation Steps */}
            <div className="max-w-md mx-auto space-y-2 text-sm">
                {[
                    { label: 'Analyzing learning objectives', threshold: 20 },
                    { label: 'Generating lesson content', threshold: 45 },
                    { label: 'Creating practice exercises', threshold: 70 },
                    { label: 'Building assessments', threshold: 85 },
                    { label: 'Finalizing experience', threshold: 100 }
                ].map((step, index) => {
                    const isComplete = generationProgress >= step.threshold;
                    const isActive = generationProgress >= step.threshold - 10 && generationProgress < step.threshold;

                    return (
                        <div key={index} className="flex items-center gap-3">
                            <div className={`w-5 h-5 rounded-full flex items-center justify-center ${
                                isComplete
                                    ? 'bg-green-500 text-white'
                                    : isActive
                                        ? 'bg-purple-500 text-white animate-pulse'
                                        : 'bg-gray-200 dark:bg-gray-700 text-gray-400'
                            }`}>
                                {isComplete && <Check className="w-3 h-3" />}
                                {isActive && <Loader2 className="w-3 h-3 animate-spin" />}
                            </div>
                            <span className={`${
                                isComplete || isActive
                                    ? 'text-gray-900 dark:text-white'
                                    : 'text-gray-400 dark:text-gray-500'
                            }`}>
                                {step.label}
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );

    return (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-6 max-w-2xl mx-auto">
            {step === 'input' && renderInputStep()}
            {step === 'preview' && renderPreviewStep()}
            {step === 'generating' && renderGeneratingStep()}
        </div>
    );
}
