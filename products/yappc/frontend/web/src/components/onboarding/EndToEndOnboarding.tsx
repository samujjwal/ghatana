/**
 * End-to-End Onboarding Journey Component
 *
 * Complete onboarding flow: account → workspace → project → first intent → guided suggestions → approval → deploy preview.
 *
 * @doc.type component
 * @doc.purpose End-to-end onboarding journey
 * @doc.layer product
 * @doc.pattern Onboarding Component
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  User as UserIcon,
  Building2 as WorkspaceIcon,
  Folder as ProjectIcon,
  Lightbulb as IntentIcon,
  Sparkles as AIIcon,
  CheckCircle as ApprovalIcon,
  Rocket as DeployIcon,
  ChevronRight,
  Check,
  X as CloseIcon,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import { Button } from '../ui/Button';
import { Textarea } from '../ui/Textarea';

interface OnboardingStep {
  id: string;
  title: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  status: 'pending' | 'in-progress' | 'completed' | 'skipped';
}

interface EndToEndOnboardingProps {
  className?: string;
}

export function EndToEndOnboarding({ className }: EndToEndOnboardingProps) {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(0);
  const [showCompletionDialog, setShowCompletionDialog] = useState(false);

  const steps: OnboardingStep[] = [
    {
      id: 'account',
      title: 'Create Account',
      description: 'Set up your account and profile',
      icon: UserIcon,
      status: 'completed',
    },
    {
      id: 'workspace',
      title: 'Create Workspace',
      description: 'Set up your first workspace',
      icon: WorkspaceIcon,
      status: 'completed',
    },
    {
      id: 'project',
      title: 'Create Project',
      description: 'Start your first product project',
      icon: ProjectIcon,
      status: 'completed',
    },
    {
      id: 'intent',
      title: 'Define Intent',
      description: 'Describe what you want to build',
      icon: IntentIcon,
      status: 'in-progress',
    },
    {
      id: 'ai-assist',
      title: 'Guided Suggestions',
      description: 'Use guided recommendations to shape your requirements',
      icon: AIIcon,
      status: 'pending',
    },
    {
      id: 'approval',
      title: 'Review & Approve',
      description: 'Review the generated plan',
      icon: ApprovalIcon,
      status: 'pending',
    },
    {
      id: 'deploy',
      title: 'Deploy Preview',
      description: 'Launch your first preview',
      icon: DeployIcon,
      status: 'pending',
    },
  ];

  const handleStepClick = (index: number) => {
    if (index <= currentStep) {
      setCurrentStep(index);
    }
  };

  const handleNext = () => {
    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1);
    } else {
      setShowCompletionDialog(true);
    }
  };

  const handleBack = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  const handleComplete = () => {
    setShowCompletionDialog(false);
    navigate('/projects');
  };

  const getStepStatusColor = (status: OnboardingStep['status']) => {
    switch (status) {
      case 'completed':
        return 'bg-emerald-500/10 border-emerald-500 text-emerald-400';
      case 'in-progress':
        return 'bg-info-bg/10 border-info-border text-info-color';
      case 'pending':
        return 'bg-surface border-border text-fg-muted';
      case 'skipped':
        return 'bg-surface border-border text-fg-muted';
      default:
        return 'bg-surface border-border text-fg-muted';
    }
  };

  const currentStepData = steps[currentStep];
  const CurrentIcon = currentStepData.icon;

  return (
    <div className={className}>
      <div className="p-6 space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold text-fg-muted mb-2">
            Welcome to YAPPC
          </h1>
          <p className="text-sm text-fg-muted">
            Let's get you started with your first project in just a few steps.
          </p>
        </div>

        {/* Progress Steps */}
        <div className="space-y-2">
          {steps.map((step, index) => {
            const Icon = step.icon;
            const isClickable = index <= currentStep;
            const isCurrent = index === currentStep;

            return (
              <Button
                key={step.id}
                variant="ghost"
                fullWidth
                onClick={() => handleStepClick(index)}
                disabled={!isClickable}
                className={cn(
                  'justify-start gap-4 p-4 rounded-xl border transition-all text-left [&>span]:flex [&>span]:w-full [&>span]:items-center [&>span]:gap-4',
                  getStepStatusColor(step.status),
                  !isClickable && 'opacity-50 cursor-not-allowed',
                  isClickable && 'hover:border-border'
                )}
              >
                <span className={cn(
                  'flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center',
                  step.status === 'completed' ? 'bg-emerald-500/20' :
                  step.status === 'in-progress' ? 'bg-info-bg/20' :
                  'bg-surface-muted'
                )}>
                  {step.status === 'completed' ? (
                    <Check className="w-5 h-5" />
                  ) : (
                    <Icon className="w-5 h-5" />
                  )}
                </span>
                <span className="flex-1 min-w-0">
                  <span className="flex items-center gap-2">
                    <span className="font-semibold">{step.title}</span>
                    {step.status === 'in-progress' && (
                      <span className="text-xs bg-info-bg/20 text-info-color px-2 py-0.5 rounded">
                        Current
                      </span>
                    )}
                  </span>
                  <span className="block text-sm opacity-80">{step.description}</span>
                </span>
                {isClickable && !isCurrent && (
                  <ChevronRight className="w-5 h-5 opacity-50" />
                )}
              </Button>
            );
          })}
        </div>

        {/* Current Step Content */}
        <div className="bg-surface border border-border rounded-xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-12 h-12 rounded-lg bg-info-bg/20 flex items-center justify-center text-info-color">
              <CurrentIcon className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-fg-muted">{currentStepData.title}</h2>
              <p className="text-sm text-fg-muted">{currentStepData.description}</p>
            </div>
          </div>

          {/* Step-specific content would go here */}
          <div className="bg-surface rounded-lg p-4 text-sm text-fg-muted">
            {currentStepData.id === 'intent' && (
              <div>
                <p className="mb-3">Describe what you want to build in a few sentences.</p>
                <Textarea
                  placeholder="e.g., I want to build a task management app with team collaboration features..."
                  fullWidth
                  resize="none"
                  className="w-full h-32 bg-surface border border-border rounded-lg p-3 text-fg-muted placeholder-zinc-500 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-info-border"
                />
              </div>
            )}
            {currentStepData.id === 'ai-assist' && (
              <div className="space-y-3">
                <p>YAPPC is analyzing your intent and generating requirements...</p>
                <div className="flex items-center gap-2 text-info-color">
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-info-border" />
                  <span>Processing</span>
                </div>
              </div>
            )}
            {currentStepData.id === 'approval' && (
              <div>
                <p className="mb-3">Review the generated requirements and architecture:</p>
                <div className="space-y-2">
                  <div className="p-3 bg-surface rounded border border-border">
                    <div className="font-medium text-fg-muted mb-1">Requirements</div>
                    <div className="text-xs text-fg-muted">User authentication, task CRUD, team management</div>
                  </div>
                  <div className="p-3 bg-surface rounded border border-border">
                    <div className="font-medium text-fg-muted mb-1">Architecture</div>
                    <div className="text-xs text-fg-muted">React + Node.js + PostgreSQL</div>
                  </div>
                </div>
              </div>
            )}
            {currentStepData.id === 'deploy' && (
              <div className="space-y-3">
                <p>Your preview environment is being prepared...</p>
                <div className="flex items-center gap-2 text-emerald-400">
                  <Check className="w-4 h-4" />
                  <span>Preview ready</span>
                </div>
              </div>
            )}
            {['account', 'workspace', 'project'].includes(currentStepData.id) && (
              <p>This step has been completed. You can proceed to the next step.</p>
            )}
          </div>
        </div>

        {/* Navigation */}
        <div className="flex items-center justify-between">
          <Button
            variant="soft"
            onClick={handleBack}
            disabled={currentStep === 0}
            className="px-4 py-2 bg-surface hover:bg-surface-muted text-fg-muted text-sm font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Back
          </Button>
          <div className="text-sm text-fg-muted">
            Step {currentStep + 1} of {steps.length}
          </div>
          <Button
            onClick={handleNext}
            className="px-4 py-2 bg-primary hover:bg-info-bg text-white text-sm font-medium rounded-lg transition-colors"
          >
            {currentStep === steps.length - 1 ? 'Complete' : 'Next'}
          </Button>
        </div>
      </div>

      {/* Completion Dialog */}
      {showCompletionDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-surface border border-border rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-border">
              <h2 className="text-lg font-semibold text-fg-muted">Onboarding Complete!</h2>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowCompletionDialog(false)}
                className="p-1 rounded hover:bg-surface text-fg-muted hover:text-fg-muted"
                aria-label="Close completion dialog"
              >
                <CloseIcon className="w-5 h-5" />
              </Button>
            </div>
            <div className="p-4 space-y-4">
              <div className="flex items-center justify-center py-8">
                <div className="w-16 h-16 rounded-full bg-emerald-500/20 flex items-center justify-center text-emerald-400">
                  <Check className="w-8 h-8" />
                </div>
              </div>
              <p className="text-center text-sm text-fg-muted">
                Congratulations! You've completed the onboarding journey. Your project is ready and you can now start using YAPPC.
              </p>
            </div>
            <div className="flex justify-end p-4 border-t border-border">
              <Button
                onClick={handleComplete}
                className="px-4 py-2 bg-primary hover:bg-info-bg text-white text-sm font-medium rounded-lg transition-colors"
              >
                Go to Dashboard
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default EndToEndOnboarding;
