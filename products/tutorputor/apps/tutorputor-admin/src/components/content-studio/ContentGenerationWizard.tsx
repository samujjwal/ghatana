/**
 * Content Generation Wizard Component
 * 
 * Step-by-step wizard for generating comprehensive educational content
 */

import { useState } from 'react';
import { Sparkles, Globe, PenTool, QuizIcon, Settings, ArrowRight, ArrowLeft, Check } from 'lucide-react';
import { Button } from '@ghatana/ui';
import { useContentStudioApi } from '../../services/contentStudioApi';

interface ContentGenerationWizardProps {
    onContentGenerated: (content: any) => void;
    onCancel: () => void;
}

export function ContentGenerationWizard({ onContentGenerated, onCancel }: ContentGenerationWizardProps) {
    const [currentStep, setCurrentStep] = useState(1);
    const [isGenerating, setIsGenerating] = useState(false);
    const [formData, setFormData] = useState({
        topic: '',
        gradeLevel: '',
        subject: '',
        includeRealWorldUseCases: true,
        includePracticeWorksheets: true,
        includeQuizzes: true,
        includeSimulations: false,
        includeAnimations: false,
        includeProjects: false
    });

    const contentStudioApi = useContentStudioApi();

    const gradeLevels = [
        'Grade 1', 'Grade 2', 'Grade 3', 'Grade 4', 'Grade 5',
        'Grade 6', 'Grade 7', 'Grade 8', 'Grade 9', 'Grade 10',
        'Grade 11', 'Grade 12', 'College', 'Professional'
    ];

    const subjects = [
        'Mathematics', 'Science', 'English', 'History', 'Geography',
        'Physics', 'Chemistry', 'Biology', 'Computer Science', 'Art',
        'Music', 'Physical Education', 'Economics', 'Psychology', 'Sociology'
    ];

    const contentTypes = [
        { id: 'includeRealWorldUseCases', label: 'Real-World Use Cases', icon: Globe, description: 'Industry applications and case studies' },
        { id: 'includePracticeWorksheets', label: 'Practice Worksheets', icon: PenTool, description: 'Hands-on exercises and problems' },
        { id: 'includeQuizzes', label: 'Interactive Quizzes', icon: QuizIcon, description: 'Assessment and evaluation tools' },
        { id: 'includeSimulations', label: 'Simulations', icon: Settings, description: 'Interactive learning experiences' },
        { id: 'includeAnimations', label: 'Animations', icon: Settings, description: 'Visual learning aids' },
        { id: 'includeProjects', label: 'Projects', icon: Settings, description: 'Hands-on project-based learning' }
    ];

    const handleNext = () => {
        if (currentStep < 3) {
            setCurrentStep(currentStep + 1);
        }
    };

    const handlePrevious = () => {
        if (currentStep > 1) {
            setCurrentStep(currentStep - 1);
        }
    };

    const handleGenerate = async () => {
        setIsGenerating(true);
        try {
            const result = await contentStudioApi.generateContent(formData);
            onContentGenerated(result.experience);
        } catch (error) {
            console.error('Failed to generate content:', error);
        } finally {
            setIsGenerating(false);
        }
    };

    const renderStep = () => {
        switch (currentStep) {
            case 1:
                return (
                    <div className="space-y-6">
                        <div>
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                                What topic do you want to teach?
                            </h3>
                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                Enter the main topic or concept you want to create content for.
                            </p>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Topic
                                </label>
                                <input
                                    type="text"
                                    value={formData.topic}
                                    onChange={(e) => setFormData({ ...formData, topic: e.target.value })}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
                                    placeholder="e.g., Photosynthesis, Fractions, World War II"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Grade Level
                                </label>
                                <select
                                    value={formData.gradeLevel}
                                    onChange={(e) => setFormData({ ...formData, gradeLevel: e.target.value })}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">Select grade level</option>
                                    {gradeLevels.map(level => (
                                        <option key={level} value={level}>{level}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Subject
                                </label>
                                <select
                                    value={formData.subject}
                                    onChange={(e) => setFormData({ ...formData, subject: e.target.value })}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">Select subject</option>
                                    {subjects.map(subject => (
                                        <option key={subject} value={subject}>{subject}</option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>
                );

            case 2:
                return (
                    <div className="space-y-6">
                        <div>
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                                What types of content would you like to include?
                            </h3>
                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                Select the types of educational content you want to generate.
                            </p>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {contentTypes.map((type) => (
                                <div
                                    key={type.id}
                                    className={`border rounded-lg p-4 cursor-pointer transition-colors ${formData[type.id as keyof typeof formData]
                                            ? 'border-purple-500 bg-purple-50 dark:bg-purple-900/20'
                                            : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                                        }`}
                                    onClick={() => setFormData({ ...formData, [type.id]: !formData[type.id as keyof typeof formData] })}
                                >
                                    <div className="flex items-start gap-3">
                                        <type.icon className="h-5 w-5 text-purple-500 mt-1" />
                                        <div className="flex-1">
                                            <h4 className="font-medium text-gray-900 dark:text-white">{type.label}</h4>
                                            <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{type.description}</p>
                                        </div>
                                        <div className={`w-5 h-5 rounded border-2 flex items-center justify-center ${formData[type.id as keyof typeof formData]
                                                ? 'border-purple-500 bg-purple-500'
                                                : 'border-gray-300 dark:border-gray-600'
                                            }`}>
                                            {formData[type.id as keyof typeof formData] && (
                                                <Check className="h-3 w-3 text-white" />
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                );

            case 3:
                return (
                    <div className="space-y-6">
                        <div>
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                                Review and Generate
                            </h3>
                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                Review your selections and generate comprehensive content.
                            </p>
                        </div>

                        <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-6 space-y-4">
                            <div>
                                <h4 className="font-medium text-gray-900 dark:text-white">Topic</h4>
                                <p className="text-gray-600 dark:text-gray-400">{formData.topic}</p>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <h4 className="font-medium text-gray-900 dark:text-white">Grade Level</h4>
                                    <p className="text-gray-600 dark:text-gray-400">{formData.gradeLevel}</p>
                                </div>
                                <div>
                                    <h4 className="font-medium text-gray-900 dark:text-white">Subject</h4>
                                    <p className="text-gray-600 dark:text-gray-400">{formData.subject}</p>
                                </div>
                            </div>

                            <div>
                                <h4 className="font-medium text-gray-900 dark:text-white mb-2">Content Types</h4>
                                <div className="flex flex-wrap gap-2">
                                    {contentTypes
                                        .filter(type => formData[type.id as keyof typeof formData])
                                        .map(type => (
                                            <span
                                                key={type.id}
                                                className="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 rounded-full text-sm"
                                            >
                                                {type.label}
                                            </span>
                                        ))}
                                </div>
                            </div>
                        </div>
                    </div>
                );

            default:
                return null;
        }
    };

    return (
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            {/* Progress Indicator */}
            <div className="flex items-center justify-between mb-8">
                {[1, 2, 3].map((step) => (
                    <div key={step} className="flex items-center">
                        <div
                            className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${step === currentStep
                                    ? 'bg-purple-500 text-white'
                                    : step < currentStep
                                        ? 'bg-green-500 text-white'
                                        : 'bg-gray-200 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                                }`}
                        >
                            {step < currentStep ? <Check className="h-4 w-4" /> : step}
                        </div>
                        {step < 3 && (
                            <div className={`w-12 h-1 mx-2 ${step < currentStep ? 'bg-green-500' : 'bg-gray-200 dark:bg-gray-700'
                                }`} />
                        )}
                    </div>
                ))}
            </div>

            {/* Step Content */}
            {renderStep()}

            {/* Navigation */}
            <div className="flex justify-between mt-8">
                <div>
                    {currentStep > 1 && (
                        <Button variant="outline" onClick={handlePrevious}>
                            <ArrowLeft className="h-4 w-4 mr-2" />
                            Previous
                        </Button>
                    )}
                </div>

                <div className="flex gap-2">
                    <Button variant="outline" onClick={onCancel}>
                        Cancel
                    </Button>

                    {currentStep < 3 ? (
                        <Button onClick={handleNext} disabled={!formData.topic || !formData.gradeLevel || !formData.subject}>
                            Next
                            <ArrowRight className="h-4 w-4 ml-2" />
                        </Button>
                    ) : (
                        <Button
                            onClick={handleGenerate}
                            disabled={isGenerating || !formData.topic || !formData.gradeLevel || !formData.subject}
                            className="bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600"
                        >
                            {isGenerating ? (
                                <>
                                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                                    Generating...
                                </>
                            ) : (
                                <>
                                    <Sparkles className="h-4 w-4 mr-2" />
                                    Generate Content
                                </>
                            )}
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
