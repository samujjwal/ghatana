/**
 * OnboardingFlow Component
 *
 * AI-driven first-login experience that automatically sets up
 * a default workspace and project based on user context.
 * Minimal friction - just a few optional customizations.
 *
 * @doc.type component
 * @doc.purpose Smart user onboarding with AI suggestions
 * @doc.layer product
 * @doc.pattern Wizard
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Sparkles as AutoAwesome, Folder, Globe as Public, Smartphone as PhoneIphone, Component as Widgets } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useSetAtom } from 'jotai';
import { useCreateWorkspace, useNameSuggestions } from '@/hooks/useWorkspaceData';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { PERSONA_DEFINITIONS, ALL_PERSONA_TYPES, type PersonaType } from '../../context/PersonaContext';

// ============================================================================
// Types
// ============================================================================

interface OnboardingStep {
    id: 'welcome' | 'workspace' | 'project' | 'complete';
    title: string;
    subtitle: string;
}

interface ProjectType {
    id: string;
    name: string;
    description: string;
    icon: React.ReactNode;
    color: string;
}

const STEPS: OnboardingStep[] = [
    { id: 'welcome', title: 'Welcome to Yappc', subtitle: "Let's set up your workspace in seconds" },
    { id: 'workspace', title: 'Your Workspace', subtitle: 'A home for all your projects' },
    { id: 'project', title: 'First Project', subtitle: 'What are you building?' },
    { id: 'complete', title: 'All Set!', subtitle: "You're ready to create amazing things" },
];

const PROJECT_TYPES: ProjectType[] = [
    {
        id: 'webapp',
        name: 'Web Application',
        description: 'Full-stack web app with React',
        icon: <Public size={16} />,
        color: 'from-blue-500 to-cyan-500',
    },
    {
        id: 'api',
        name: 'API Service',
        description: 'RESTful or GraphQL backend',
        icon: <AutoAwesome size={16} />,
        color: 'from-purple-500 to-pink-500',
    },
    {
        id: 'mobile',
        name: 'Mobile App',
        description: 'React Native cross-platform',
        icon: <PhoneIphone size={16} />,
        color: 'from-orange-500 to-red-500',
    },
    {
        id: 'library',
        name: 'Shared Library',
        description: 'Reusable components & utils',
        icon: <Widgets size={16} />,
        color: 'from-green-500 to-emerald-500',
    },
];

// ============================================================================
// Sub-Components
// ============================================================================

function StepIndicator({ currentStep, steps }: { currentStep: number; steps: OnboardingStep[] }) {
    return (
        <div className="flex items-center justify-center gap-2 mb-8">
            {steps.map((step, index) => (
                <React.Fragment key={step.id}>
                    <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-all duration-300 ${index < currentStep
                            ? 'bg-green-500 text-white'
                            : index === currentStep
                                ? 'bg-blue-500 text-white ring-4 ring-blue-500/30'
                                : 'bg-gray-200 dark:bg-gray-700 text-gray-500'
                            }`}
                    >
                        {index < currentStep ? '✓' : index + 1}
                    </div>
                    {index < steps.length - 1 && (
                        <div
                            className={`w-12 h-0.5 transition-colors duration-300 ${index < currentStep ? 'bg-green-500' : 'bg-gray-200 dark:bg-gray-700'
                                }`}
                        />
                    )}
                </React.Fragment>
            ))}
        </div>
    );
}

function WelcomeStep({ onNext }: { onNext: () => void }) {
    return (
        <div className="text-center animate-fadeIn">
            <div className="relative mb-8">
                <div className="absolute inset-0 bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 rounded-full blur-3xl opacity-30 animate-pulse" />
                <div className="relative bg-gradient-to-br from-blue-500 to-purple-600 w-24 h-24 rounded-2xl flex items-center justify-center mx-auto shadow-xl">
                    <AutoAwesome className="text-white" size={32} />
                </div>
            </div>

            <h1 className="text-3xl font-bold text-text-primary mb-4">
                Welcome to Yappc
            </h1>

            <p className="text-lg text-text-secondary mb-8 max-w-md mx-auto">
                We'll have you up and running in under 30 seconds. Our AI will handle the heavy lifting.
            </p>

            <div className="flex flex-col items-center gap-4">
                <button
                    onClick={onNext}
                    className="group px-8 py-4 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium text-lg hover:shadow-lg hover:shadow-purple-500/30 transition-all duration-300 flex items-center gap-2"
                >
                    Let's Go
                    <span className="group-hover:translate-x-1 transition-transform inline-block">→</span>
                </button>

                <p className="text-sm text-text-secondary">
                    AI will suggest names based on your context
                </p>
            </div>
        </div>
    );
}

function WorkspaceStep({
    workspaceName,
    setWorkspaceName,
    selectedPersonas,
    setSelectedPersonas,
    suggestedName,
    isLoadingSuggestion,
    errorMessage,
    onNext,
    onBack,
}: {
    workspaceName: string;
    setWorkspaceName: (name: string) => void;
    selectedPersonas: PersonaType[];
    setSelectedPersonas: (personas: PersonaType[]) => void;
    suggestedName: string | null;
    isLoadingSuggestion: boolean;
    errorMessage?: string | null;
    onNext: () => void;
    onBack: () => void;
}) {
    const useSuggestion = useCallback(() => {
        if (suggestedName) {
            setWorkspaceName(suggestedName);
        }
    }, [suggestedName, setWorkspaceName]);

    const togglePersona = useCallback((personaId: PersonaType) => {
        setSelectedPersonas(
            selectedPersonas.includes(personaId)
                ? selectedPersonas.filter(p => p !== personaId)
                : [...selectedPersonas, personaId]
        );
    }, [selectedPersonas, setSelectedPersonas]);

    return (
        <div className="max-w-lg mx-auto animate-fadeIn">
            <div className="bg-gradient-to-br from-blue-500 to-purple-600 w-16 h-16 rounded-xl flex items-center justify-center mx-auto mb-6 shadow-lg">
                <span className="text-2xl">📁</span>
            </div>

            <h2 className="text-2xl font-bold text-text-primary text-center mb-2">
                Name Your Workspace
            </h2>

            <p className="text-text-secondary text-center mb-6">
                This will contain all your projects.
            </p>

            <div className="space-y-6">
                {errorMessage && (
                    <div className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-sm text-red-700 dark:text-red-300">
                        {errorMessage}
                    </div>
                )}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-1">
                        Workspace Name
                    </label>
                    <input
                        type="text"
                        value={workspaceName}
                        onChange={(e) => setWorkspaceName(e.target.value)}
                        placeholder="My Awesome Workspace"
                        className="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-800 text-text-primary focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                        autoFocus
                    />
                </div>

                {(isLoadingSuggestion || suggestedName) && (
                    <div className="p-3 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-lg border border-purple-100 dark:border-purple-800 animate-fadeIn">
                        <div className="flex items-center gap-2">
                            {isLoadingSuggestion ? (
                                <span className="text-sm text-purple-600 dark:text-purple-400">
                                    AI is thinking...
                                </span>
                            ) : (
                                <span className="text-sm text-purple-600 dark:text-purple-400">
                                    AI suggests:{' '}
                                    <button
                                        onClick={useSuggestion}
                                        className="font-medium underline hover:no-underline"
                                    >
                                        {suggestedName}
                                    </button>
                                </span>
                            )}
                        </div>
                    </div>
                )}

                {/* Persona Selection */}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-3 text-center">
                        What's your primary role? (Select all that apply)
                    </label>
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                        {ALL_PERSONA_TYPES.map((personaId) => {
                            const persona = PERSONA_DEFINITIONS[personaId];
                            const isSelected = selectedPersonas.includes(personaId);
                            return (
                                <button
                                    key={personaId}
                                    onClick={() => togglePersona(personaId)}
                                    className={`p-3 rounded-lg border-2 transition-all text-center ${isSelected
                                            ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                            : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                                        }`}
                                >
                                    <div className="text-2xl mb-1">{persona.icon}</div>
                                    <p className="text-xs font-medium text-text-primary">
                                        {persona.shortName}
                                    </p>
                                    <div className={`w-4 h-4 mx-auto mt-2 rounded border-2 flex items-center justify-center ${isSelected
                                            ? 'border-blue-500 bg-blue-500'
                                            : 'border-gray-300 dark:border-gray-600'
                                        }`}>
                                        {isSelected && (
                                            <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                            </svg>
                                        )}
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                    <p className="text-xs text-text-secondary text-center mt-2">
                        Unselected roles will be filled by AI agents
                    </p>
                </div>
            </div>

            <div className="flex gap-3 mt-8">
                <button
                    onClick={onBack}
                    className="flex-1 px-4 py-3 border border-gray-300 dark:border-gray-600 text-text-primary rounded-xl font-medium hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                    ← Back
                </button>
                <button
                    onClick={onNext}
                    disabled={!workspaceName.trim() || selectedPersonas.length === 0}
                    className="flex-1 px-4 py-3 bg-blue-500 text-white rounded-xl font-medium hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
                >
                    Continue →
                </button>
            </div>
        </div>
    );
}

function ProjectStep({
    projectName,
    setProjectName,
    projectType,
    setProjectType,
    suggestedName,
    isLoadingSuggestion,
    onNext,
    onBack,
}: {
    projectName: string;
    setProjectName: (name: string) => void;
    projectType: string;
    setProjectType: (type: string) => void;
    suggestedName: string | null;
    isLoadingSuggestion: boolean;
    onNext: () => void;
    onBack: () => void;
}) {
    const useSuggestion = useCallback(() => {
        if (suggestedName) {
            setProjectName(suggestedName);
        }
    }, [suggestedName, setProjectName]);

    return (
        <div className="max-w-lg mx-auto animate-fadeIn">
            <div className="bg-gradient-to-br from-green-500 to-emerald-600 w-16 h-16 rounded-xl flex items-center justify-center mx-auto mb-6 shadow-lg">
                <span className="text-2xl">🚀</span>
            </div>

            <h2 className="text-2xl font-bold text-text-primary text-center mb-2">
                Create Your First Project
            </h2>

            <p className="text-text-secondary text-center mb-6">
                What type of project are you building?
            </p>

            <div className="grid grid-cols-2 gap-3 mb-6">
                {PROJECT_TYPES.map((type) => {
                    const isSelected = projectType === type.id;
                    return (
                        <button
                            key={type.id}
                            onClick={() => setProjectType(type.id)}
                            className={`p-4 rounded-xl border-2 transition-all text-left ${isSelected
                                ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                                }`}
                        >
                            <div
                                className={`w-10 h-10 rounded-lg bg-gradient-to-br ${type.color} flex items-center justify-center mb-2`}
                            >
                                <span className="text-white">{type.icon}</span>
                            </div>
                            <p className="font-medium text-text-primary text-sm">{type.name}</p>
                            <p className="text-xs text-text-secondary">{type.description}</p>
                        </button>
                    );
                })}
            </div>

            <div className="space-y-4">
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-1">
                        Project Name
                    </label>
                    <input
                        type="text"
                        value={projectName}
                        onChange={(e) => setProjectName(e.target.value)}
                        placeholder="My First Project"
                        className="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-800 text-text-primary focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                    />
                </div>

                {(isLoadingSuggestion || suggestedName) && (
                    <div className="p-3 bg-gradient-to-r from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 rounded-lg border border-green-100 dark:border-green-800 animate-fadeIn">
                        <div className="flex items-center gap-2">
                            {isLoadingSuggestion ? (
                                <span className="text-sm text-green-600 dark:text-green-400">
                                    AI is thinking...
                                </span>
                            ) : (
                                <span className="text-sm text-green-600 dark:text-green-400">
                                    AI suggests:{' '}
                                    <button
                                        onClick={useSuggestion}
                                        className="font-medium underline hover:no-underline"
                                    >
                                        {suggestedName}
                                    </button>
                                </span>
                            )}
                        </div>
                    </div>
                )}
            </div>

            <div className="flex gap-3 mt-8">
                <button
                    onClick={onBack}
                    className="flex-1 px-4 py-3 border border-gray-300 dark:border-gray-600 text-text-primary rounded-xl font-medium hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                    Back
                </button>
                <button
                    onClick={onNext}
                    disabled={!projectName.trim() || !projectType}
                    className="flex-1 px-4 py-3 bg-green-500 text-white rounded-xl font-medium hover:bg-green-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
                >
                    Create & Finish
                    <span>✓</span>
                </button>
            </div>
        </div>
    );
}

function CompleteStep({ onFinish, isCreating }: { onFinish: () => void; isCreating: boolean }) {
    useEffect(() => {
        if (!isCreating) {
            // Auto-navigate after a brief delay to show success
            const timer = setTimeout(onFinish, 2000);
            return () => clearTimeout(timer);
        }
    }, [isCreating, onFinish]);

    return (
        <div className="text-center animate-fadeIn">
            {isCreating ? (
                <>
                    <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
                        <span className="text-3xl animate-spin">...</span>
                    </div>
                    <h2 className="text-2xl font-bold text-text-primary mb-2">
                        Setting Up Your Workspace...
                    </h2>
                    <p className="text-text-secondary">
                        AI is configuring everything for you
                    </p>
                </>
            ) : (
                <>
                    <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center animate-bounce">
                        <span className="text-3xl">✓</span>
                    </div>

                    <div>
                        <h2 className="text-2xl font-bold text-text-primary mb-2">
                            You're All Set
                        </h2>
                        <p className="text-text-secondary mb-6">
                            Redirecting you to your new workspace...
                        </p>

                        <button
                            onClick={onFinish}
                            className="px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium hover:shadow-lg transition-shadow"
                        >
                            Go to Dashboard
                        </button>
                    </div>
                </>
            )}
        </div>
    );
}

// ============================================================================
// Main Component
// ============================================================================

export interface OnboardingFlowProps {
    /** Called when onboarding is complete */
    onComplete?: () => void;
    /** Redirect path after completion */
    redirectTo?: string;
}

export function OnboardingFlow({ onComplete, redirectTo = '/' }: OnboardingFlowProps) {
    const navigate = useNavigate();
    const { suggestWorkspace, suggestProject } = useNameSuggestions();
    const createWorkspace = useCreateWorkspace();

    const [currentStep, setCurrentStep] = useState(0);
    const [workspaceName, setWorkspaceName] = useState('');
    const [projectName, setProjectName] = useState('');
    const [projectType, setProjectType] = useState('webapp');
    const [selectedPersonas, setSelectedPersonas] = useState<PersonaType[]>(['developer']);
    const [suggestedWorkspaceName, setSuggestedWorkspaceName] = useState<string | null>(null);
    const [suggestedProjectName, setSuggestedProjectName] = useState<string | null>(null);
    const [isLoadingWorkspaceSuggestion, setIsLoadingWorkspaceSuggestion] = useState(false);
    const [isLoadingProjectSuggestion, setIsLoadingProjectSuggestion] = useState(false);
    const [isCreating, setIsCreating] = useState(false);
    const [workspaceError, setWorkspaceError] = useState<string | null>(null);

    // Fetch AI suggestions when entering workspace step
    useEffect(() => {
        if (currentStep === 1 && !suggestedWorkspaceName) {
            setIsLoadingWorkspaceSuggestion(true);
            suggestWorkspace().then((name) => {
                setSuggestedWorkspaceName(name);
                setIsLoadingWorkspaceSuggestion(false);
                // Auto-fill if empty
                if (!workspaceName) {
                    setWorkspaceName(name);
                }
            });
        }
    }, [currentStep, suggestedWorkspaceName, suggestWorkspace, workspaceName]);

    // Fetch AI suggestions when entering project step or changing type
    useEffect(() => {
        if (currentStep === 2) {
            setIsLoadingProjectSuggestion(true);
            suggestProject('temp-workspace', projectType).then((name) => {
                setSuggestedProjectName(name);
                setIsLoadingProjectSuggestion(false);
                // Auto-fill if empty
                if (!projectName) {
                    setProjectName(name);
                }
            });
        }
    }, [currentStep, projectType, suggestProject, projectName]);

    const handleNext = useCallback(() => {
        setWorkspaceError(null);
        setCurrentStep((prev) => Math.min(prev + 1, STEPS.length - 1));
    }, []);

    const handleBack = useCallback(() => {
        setWorkspaceError(null);
        setCurrentStep((prev) => Math.max(prev - 1, 0));
    }, []);

    const setCurrentWorkspaceId = useSetAtom(currentWorkspaceIdAtom);

    const handleCreate = useCallback(async () => {
        setCurrentStep(3);
        setIsCreating(true);

        try {
            // Save selected personas to localStorage for PersonaContext
            localStorage.setItem('yappc_active_personas', JSON.stringify(selectedPersonas));
            localStorage.setItem('yappc_primary_persona', selectedPersonas[0] || 'developer');

            // Create workspace with default project
            const workspace = await createWorkspace.mutateAsync({
                name: workspaceName,
                createDefaultProject: true,
            });

            // Set the newly created workspace as current
            setCurrentWorkspaceId(workspace.id);

            // Mark onboarding as complete
            localStorage.setItem('onboarding_complete', 'true');

            setIsCreating(false);
        } catch (error: unknown) {
            console.error('Failed to create workspace:', error);

            // Check if it's a duplicate name error (P2002)
            const errorMessage = error?.message || '';
            if (errorMessage.includes('P2002') || errorMessage.includes('unique')) {
                // Go back to workspace step with inline error message
                setWorkspaceError(`A workspace named "${workspaceName}" already exists. Please try a different name.`);
                setCurrentStep(1);
                setIsCreating(false);
                return;
            }

            // For other errors, still mark as complete to not block user
            localStorage.setItem('onboarding_complete', 'true');
            setIsCreating(false);
        }
    }, [workspaceName, selectedPersonas, createWorkspace, setCurrentWorkspaceId]);

    const handleFinish = useCallback(() => {
        onComplete?.();
        navigate(redirectTo);
    }, [onComplete, navigate, redirectTo]);

    const step = STEPS[currentStep];

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800 flex items-center justify-center p-4">
            <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .animate-fadeIn {
          animation: fadeIn 0.3s ease-out forwards;
        }
      `}</style>

            <div className="w-full max-w-2xl">
                <StepIndicator currentStep={currentStep} steps={STEPS} />

                <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8 min-h-[400px] flex flex-col justify-center">
                    {step.id === 'welcome' && <WelcomeStep key="welcome" onNext={handleNext} />}

                    {step.id === 'workspace' && (
                        <WorkspaceStep
                            key="workspace"
                            workspaceName={workspaceName}
                            setWorkspaceName={setWorkspaceName}
                            selectedPersonas={selectedPersonas}
                            setSelectedPersonas={setSelectedPersonas}
                            suggestedName={suggestedWorkspaceName}
                            isLoadingSuggestion={isLoadingWorkspaceSuggestion}
                            errorMessage={workspaceError}
                            onNext={handleNext}
                            onBack={handleBack}
                        />
                    )}

                    {step.id === 'project' && (
                        <ProjectStep
                            key="project"
                            projectName={projectName}
                            setProjectName={setProjectName}
                            projectType={projectType}
                            setProjectType={setProjectType}
                            suggestedName={suggestedProjectName}
                            isLoadingSuggestion={isLoadingProjectSuggestion}
                            onNext={handleCreate}
                            onBack={handleBack}
                        />
                    )}

                    {step.id === 'complete' && (
                        <CompleteStep key="complete" onFinish={handleFinish} isCreating={isCreating} />
                    )}
                </div>

                <p className="text-center text-sm text-text-secondary mt-6">
                    You can always customize these settings later in your workspace settings.
                </p>
            </div>
        </div>
    );
}

export default OnboardingFlow;
