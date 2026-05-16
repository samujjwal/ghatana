/**
 * OnboardingFlow Component
 *
 * Guided first-login experience that automatically sets up
 * a default workspace and project based on user context.
 * Minimal friction with only the durable inputs the mounted product can back today.
 *
 * @doc.type component
 * @doc.purpose Smart user onboarding with AI suggestions
 * @doc.layer product
 * @doc.pattern Wizard
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Sparkles as AutoAwesome, Folder, Globe as Public, Smartphone as PhoneIphone, Component as Widgets } from 'lucide-react';
import { useNavigate } from 'react-router';
import { useSetAtom } from 'jotai';
import type { ProjectTypeContract } from '@/contracts/workspace-project';
import { useCreateWorkspace, useNameSuggestions } from '@/hooks/useWorkspaceData';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { writeStorage, writeFlag } from '../../services/storage';
import { useOnboardingStatus } from '../../services/onboarding/OnboardingStatusService';
import { PERSONA_DEFINITIONS, ALL_PERSONA_TYPES, type PersonaType } from '../../context/PersonaContext';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { useTranslation } from '@ghatana/i18n';

// ============================================================================
// Types
// ============================================================================

interface OnboardingStep {
    id: 'welcome' | 'workspace' | 'project' | 'complete';
    title: string;
    subtitle: string;
}

interface StarterProjectOption {
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

const PROJECT_TYPES: StarterProjectOption[] = [
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

function mapProjectTypeToApiType(projectType: string): ProjectTypeContract {
    switch (projectType) {
        case 'api':
            return 'BACKEND';
        case 'mobile':
            return 'MOBILE';
        case 'library':
            return 'UI';
        case 'webapp':
        default:
            return 'FULL_STACK';
    }
}

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
                            ? 'bg-success-bg text-white'
                            : index === currentStep
                                ? 'bg-info-bg text-white ring-4 ring-blue-500/30'
                                : 'bg-surface-muted dark:bg-surface-muted text-fg-muted'
                            }`}
                    >
                        {index < currentStep ? '✓' : index + 1}
                    </div>
                    {index < steps.length - 1 && (
                        <div
                            className={`w-12 h-0.5 transition-colors duration-300 ${index < currentStep ? 'bg-success-bg' : 'bg-surface-muted dark:bg-surface-muted'
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
                We'll have you up and running in under 30 seconds with a backed workspace and starter project.
            </p>

            <div className="flex flex-col items-center gap-4">
                <Button
                    onClick={onNext}
                    variant="solid"
                    className="group px-8 py-4 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium text-lg hover:shadow-lg hover:shadow-purple-500/30 transition-all duration-300 flex items-center gap-2"
                >
                    Let's Go
                    <span className="group-hover:translate-x-1 transition-transform inline-block">→</span>
                </Button>

                <p className="text-sm text-text-secondary">
                    The product can suggest a starting name, and you can change it before creation.
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
    const { t } = useTranslation('common');
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
                    <div className="p-3 rounded-lg bg-destructive-bg dark:bg-destructive-bg border border-destructive-border dark:border-destructive-border text-sm text-destructive dark:text-destructive">
                        {errorMessage}
                    </div>
                )}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-1">
                        {t('workspace.onboarding.workspaceName')}
                    </label>
                    <Input
                        type="text"
                        value={workspaceName}
                        onChange={(e) => setWorkspaceName(e.target.value)}
                        placeholder={t('workspace.onboarding.workspacePlaceholder')}
                        className="w-full px-4 py-3 border border-border dark:border-border rounded-xl bg-white dark:bg-surface text-text-primary focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                        autoFocus
                    />
                </div>

                {(isLoadingSuggestion || suggestedName) && (
                    <div className="p-3 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-lg border border-info-border dark:border-info-border animate-fadeIn">
                        <div className="flex items-center gap-2">
                            {isLoadingSuggestion ? (
                                <span className="text-sm text-info-color dark:text-info-color">
                                    AI is thinking...
                                </span>
                            ) : (
                                <span className="text-sm text-info-color dark:text-info-color">
                                    Suggested:{' '}
                                    <Button
                                        onClick={useSuggestion}
                                        variant="link"
                                        size="small"
                                        className="font-medium underline hover:no-underline"
                                    >
                                        {suggestedName}
                                    </Button>
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
                                <Button
                                    key={personaId}
                                    onClick={() => togglePersona(personaId)}
                                    variant="ghost"
                                    className={`p-3 rounded-lg border-2 transition-all text-center ${isSelected
                                            ? 'border-info-border bg-info-bg dark:bg-info-bg'
                                            : 'border-border dark:border-border hover:border-border dark:hover:border-border'
                                        }`}
                                >
                                    <span className="block text-2xl mb-1">{persona.icon}</span>
                                    <span className="block text-xs font-medium text-text-primary">
                                        {persona.shortName}
                                    </span>
                                    <span className={`w-4 h-4 mx-auto mt-2 rounded border-2 flex items-center justify-center ${isSelected
                                            ? 'border-info-border bg-info-bg'
                                            : 'border-border dark:border-border'
                                        }`}>
                                        {isSelected && (
                                            <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                            </svg>
                                        )}
                                    </span>
                                </Button>
                            );
                        })}
                    </div>
                    <p className="text-xs text-text-secondary text-center mt-2">
                        Unselected roles will be filled by AI agents
                    </p>
                </div>
            </div>

            <div className="flex gap-3 mt-8">
                <Button
                    onClick={onBack}
                    variant="outline"
                    className="flex-1 px-4 py-3 border border-border dark:border-border text-text-primary rounded-xl font-medium hover:bg-surface-muted dark:hover:bg-surface transition-colors"
                >
                    ← Back
                </Button>
                <Button
                    onClick={onNext}
                    disabled={!workspaceName.trim() || selectedPersonas.length === 0}
                    variant="solid"
                    className="flex-1 px-4 py-3 bg-info-bg text-white rounded-xl font-medium hover:bg-primary disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
                >
                    Continue →
                </Button>
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
    const { t } = useTranslation('common');
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
                        <Button
                            key={type.id}
                            onClick={() => setProjectType(type.id)}
                            variant="ghost"
                            className={`p-4 rounded-xl border-2 transition-all text-left ${isSelected
                                ? 'border-info-border bg-info-bg dark:bg-info-bg'
                                : 'border-border dark:border-border hover:border-border dark:hover:border-border'
                                }`}
                        >
                            <span
                                className={`w-10 h-10 rounded-lg bg-gradient-to-br ${type.color} flex items-center justify-center mb-2`}
                            >
                                <span className="text-white">{type.icon}</span>
                            </span>
                            <span className="block font-medium text-text-primary text-sm">{type.name}</span>
                            <span className="block text-xs text-text-secondary">{type.description}</span>
                        </Button>
                    );
                })}
            </div>

            <div className="space-y-4">
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-1">
                        {t('workspace.onboarding.projectName')}
                    </label>
                    <Input
                        type="text"
                        value={projectName}
                        onChange={(e) => setProjectName(e.target.value)}
                        placeholder={t('workspace.onboarding.projectPlaceholder')}
                        className="w-full px-4 py-3 border border-border dark:border-border rounded-xl bg-white dark:bg-surface text-text-primary focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                    />
                </div>

                {(isLoadingSuggestion || suggestedName) && (
                    <div className="p-3 bg-gradient-to-r from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 rounded-lg border border-success-border dark:border-success-border animate-fadeIn">
                        <div className="flex items-center gap-2">
                            {isLoadingSuggestion ? (
                                <span className="text-sm text-success-color dark:text-success-color">
                                    AI is thinking...
                                </span>
                            ) : (
                                <span className="text-sm text-success-color dark:text-success-color">
                                    Suggested:{' '}
                                    <Button
                                        onClick={useSuggestion}
                                        variant="link"
                                        size="small"
                                        className="font-medium underline hover:no-underline"
                                    >
                                        {suggestedName}
                                    </Button>
                                </span>
                            )}
                        </div>
                    </div>
                )}
            </div>

            <div className="flex gap-3 mt-8">
                <Button
                    onClick={onBack}
                    variant="outline"
                    className="flex-1 px-4 py-3 border border-border dark:border-border text-text-primary rounded-xl font-medium hover:bg-surface-muted dark:hover:bg-surface transition-colors"
                >
                    Back
                </Button>
                <Button
                    onClick={onNext}
                    disabled={!projectName.trim() || !projectType}
                    variant="solid"
                    className="flex-1 px-4 py-3 bg-success-bg text-white rounded-xl font-medium hover:bg-success-bg disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
                >
                    Create & Finish
                    <span>✓</span>
                </Button>
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
                    <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-info-bg dark:bg-info-bg flex items-center justify-center">
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
                    <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-success-bg dark:bg-success-bg flex items-center justify-center animate-bounce">
                        <span className="text-3xl">✓</span>
                    </div>

                    <div>
                        <h2 className="text-2xl font-bold text-text-primary mb-2">
                            You're All Set
                        </h2>
                        <p className="text-text-secondary mb-6">
                            Redirecting you to your new workspace...
                        </p>

                        <Button
                            onClick={onFinish}
                            variant="solid"
                            className="px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium hover:shadow-lg transition-shadow"
                        >
                            Go to Dashboard
                        </Button>
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
            const loadWorkspaceSuggestion = async (): Promise<void> => {
                setIsLoadingWorkspaceSuggestion(true);

                try {
                    const name = await suggestWorkspace();
                    setSuggestedWorkspaceName(name);
                    // Auto-fill if empty
                    if (!workspaceName) {
                        setWorkspaceName(name);
                    }
                } catch (error) {
                    console.warn('Failed to load workspace suggestion', error);
                } finally {
                    setIsLoadingWorkspaceSuggestion(false);
                }
            };

            void loadWorkspaceSuggestion();
        }
    }, [currentStep, suggestedWorkspaceName, suggestWorkspace, workspaceName]);

    // Fetch AI suggestions when entering project step or changing type
    useEffect(() => {
        if (currentStep === 2) {
            const loadProjectSuggestion = async (): Promise<void> => {
                setIsLoadingProjectSuggestion(true);

                try {
                    const name = await suggestProject('temp-workspace', projectType as 'FULL_STACK' | 'BACKEND' | 'MOBILE' | 'UI' | 'DESKTOP');
                    setSuggestedProjectName(name);
                    // Auto-fill if empty
                    if (!projectName) {
                        setProjectName(name);
                    }
                } catch (error) {
                    console.warn('Failed to load project suggestion', error);
                } finally {
                    setIsLoadingProjectSuggestion(false);
                }
            };

            void loadProjectSuggestion();
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
    const { markComplete } = useOnboardingStatus();

    const handleCreate = useCallback(async () => {
        setCurrentStep(3);
        setIsCreating(true);
        setWorkspaceError(null);

        try {
            const workspace = await createWorkspace.mutateAsync({
                name: workspaceName,
                createDefaultProject: true,
                personaSelections: selectedPersonas,
                defaultProject: {
                    name: projectName,
                    type: mapProjectTypeToApiType(projectType),
                },
            });

            // Sync onboarding completion and persona preferences to server
            await markComplete({
                primary: selectedPersonas[0] || 'developer',
                active: selectedPersonas,
            });

            // Set the newly created workspace as current
            setCurrentWorkspaceId(workspace.id);

            setIsCreating(false);
        } catch (error: unknown) {
            console.error('Failed to create workspace:', error);

            // Check if it's a duplicate name error (P2002)
            const errorMessage = error instanceof Error ? error.message : '';
            if (errorMessage.includes('P2002') || errorMessage.includes('unique')) {
                // Go back to workspace step with inline error message
                setWorkspaceError(`A workspace named "${workspaceName}" already exists. Please try a different name.`);
                setCurrentStep(1);
                setIsCreating(false);
                return;
            }

            setWorkspaceError('We could not finish onboarding because workspace setup did not complete. No data was marked as complete.');
            setCurrentStep(1);
            setIsCreating(false);
        }
    }, [workspaceName, projectName, projectType, selectedPersonas, createWorkspace, setCurrentWorkspaceId, markComplete]);

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

                <div className="bg-white dark:bg-surface rounded-2xl shadow-xl p-8 min-h-[400px] flex flex-col justify-center">
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
