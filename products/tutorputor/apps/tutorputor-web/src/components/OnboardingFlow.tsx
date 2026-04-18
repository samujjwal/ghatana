/**
 * Onboarding Flow Component
 *
 * Multi-step onboarding experience for new users including:
 * - Welcome screen
 * - Interest selection
 * - Starter module recommendations
 * - Progress tracking setup
 *
 * @doc.type component
 * @doc.purpose Guide new users through platform onboarding
 * @doc.layer product
 * @doc.pattern Wizard
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, ChevronLeft, Sparkles, BookOpen, Target, CheckCircle } from 'lucide-react';
import { Button, Card } from '@ghatana/design-system';

export interface InterestOption {
  id: string;
  label: string;
  emoji: string;
  category: string;
}

export interface StarterModule {
  assetId: string;
  title: string;
  description: string;
  difficulty: string;
  estimatedMinutes: number;
  matchScore: number;
  matchReasons: string[];
}

interface OnboardingFlowProps {
  interests: InterestOption[];
  suggestedModules: StarterModule[];
  onComplete: (selectedModules: string[]) => void;
  onSkip: () => void;
}

const INTEREST_OPTIONS: InterestOption[] = [
  { id: 'math', label: 'Mathematics', emoji: '🔢', category: 'STEM' },
  { id: 'science', label: 'Science', emoji: '🔬', category: 'STEM' },
  { id: 'coding', label: 'Programming', emoji: '💻', category: 'STEM' },
  { id: 'reading', label: 'Reading & Writing', emoji: '📚', category: 'Humanities' },
  { id: 'history', label: 'History', emoji: '🏛️', category: 'Humanities' },
  { id: 'art', label: 'Art & Design', emoji: '🎨', category: 'Creative' },
  { id: 'music', label: 'Music', emoji: '🎵', category: 'Creative' },
  { id: 'languages', label: 'Languages', emoji: '🗣️', category: 'Humanities' },
  { id: 'business', label: 'Business', emoji: '💼', category: 'Professional' },
  { id: 'health', label: 'Health & Wellness', emoji: '🏥', category: 'Professional' },
];

export function OnboardingFlow({ suggestedModules, onComplete, onSkip }: OnboardingFlowProps) {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [selectedInterests, setSelectedInterests] = useState<string[]>([]);
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [isAnimating, setIsAnimating] = useState(false);

  const totalSteps = 4;

  const handleNext = () => {
    if (step < totalSteps) {
      setIsAnimating(true);
      setTimeout(() => {
        setStep(step + 1);
        setIsAnimating(false);
      }, 300);
    } else {
      onComplete(selectedModules);
      navigate('/dashboard');
    }
  };

  const handleBack = () => {
    if (step > 1) {
      setStep(step - 1);
    }
  };

  const toggleInterest = (interestId: string) => {
    setSelectedInterests((prev) =>
      prev.includes(interestId)
        ? prev.filter((id) => id !== interestId)
        : [...prev, interestId]
    );
  };

  const toggleModule = (moduleId: string) => {
    setSelectedModules((prev) =>
      prev.includes(moduleId)
        ? prev.filter((id) => id !== moduleId)
        : [...prev, moduleId]
    );
  };

  const canProceed = () => {
    switch (step) {
      case 1:
        return true; // Welcome screen always allowed
      case 2:
        return selectedInterests.length > 0;
      case 3:
        return selectedModules.length > 0;
      case 4:
        return true;
      default:
        return false;
    }
  };

  const renderStep = () => {
    switch (step) {
      case 1:
        return <WelcomeStep />;
      case 2:
        return (
          <InterestSelectionStep
            selectedInterests={selectedInterests}
            onToggle={toggleInterest}
          />
        );
      case 3:
        return (
          <ModuleRecommendationStep
            modules={suggestedModules}
            selectedModules={selectedModules}
            onToggle={toggleModule}
          />
        );
      case 4:
        return <CompletionStep selectedModules={selectedModules.length} />;
      default:
        return null;
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4">
      <Card className="w-full max-w-2xl min-h-[500px] flex flex-col">
        {/* Progress Header */}
        <div className="p-6 border-b">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              {step === 1 && 'Welcome!'}
              {step === 2 && 'What interests you?'}
              {step === 3 && 'Recommended for you'}
              {step === 4 && "You're all set!"}
            </h2>
            <span className="text-sm text-gray-500">
              Step {step} of {totalSteps}
            </span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-600 h-2 rounded-full transition-all duration-300"
              style={{ width: `${(step / totalSteps) * 100}%` }}
            />
          </div>
        </div>

        {/* Step Content */}
        <div className={`flex-1 p-6 ${isAnimating ? 'opacity-0' : 'opacity-100'} transition-opacity duration-300`}>
          {renderStep()}
        </div>

        {/* Footer Navigation */}
        <div className="p-6 border-t flex justify-between">
          <Button
            variant="ghost"
            onClick={step === 1 ? onSkip : handleBack}
            disabled={step === 1}
          >
            {step === 1 ? 'Skip' : <><ChevronLeft className="w-4 h-4 mr-1" /> Back</>}
          </Button>
          <Button onClick={handleNext} disabled={!canProceed()}>
            {step === totalSteps ? (
              <><CheckCircle className="w-4 h-4 mr-1" /> Get Started</>
            ) : (
              <><>Next</> <ChevronRight className="w-4 h-4 ml-1" /></>
            )}
          </Button>
        </div>
      </Card>
    </div>
  );
}

function WelcomeStep() {
  return (
    <div className="text-center space-y-6">
      <div className="w-20 h-20 bg-blue-100 rounded-full flex items-center justify-center mx-auto">
        <Sparkles className="w-10 h-10 text-blue-600" />
      </div>
      <h1 className="text-3xl font-bold text-gray-900">
        Welcome to Your Learning Journey
      </h1>
      <p className="text-lg text-gray-600 max-w-md mx-auto">
        Let's personalize your experience. We'll ask a few quick questions to recommend 
        the best content for you.
      </p>
      <div className="grid grid-cols-3 gap-4 max-w-md mx-auto mt-8">
        <div className="text-center p-4 bg-blue-50 rounded-lg">
          <Target className="w-6 h-6 mx-auto mb-2 text-blue-600" />
          <p className="text-sm text-gray-600">Set Goals</p>
        </div>
        <div className="text-center p-4 bg-green-50 rounded-lg">
          <BookOpen className="w-6 h-6 mx-auto mb-2 text-green-600" />
          <p className="text-sm text-gray-600">Learn</p>
        </div>
        <div className="text-center p-4 bg-purple-50 rounded-lg">
          <CheckCircle className="w-6 h-6 mx-auto mb-2 text-purple-600" />
          <p className="text-sm text-gray-600">Achieve</p>
        </div>
      </div>
    </div>
  );
}

interface InterestSelectionStepProps {
  selectedInterests: string[];
  onToggle: (id: string) => void;
}

function InterestSelectionStep({ selectedInterests, onToggle }: InterestSelectionStepProps) {
  return (
    <div className="space-y-4">
      <p className="text-gray-600 mb-6">
        Select topics you're interested in learning. This helps us recommend relevant content.
      </p>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        {INTEREST_OPTIONS.map((interest) => (
          <button
            key={interest.id}
            onClick={() => onToggle(interest.id)}
            className={`p-4 rounded-lg border-2 text-left transition-all ${
              selectedInterests.includes(interest.id)
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-200 hover:border-blue-300'
            }`}
          >
            <span className="text-2xl mb-2 block">{interest.emoji}</span>
            <span className="font-medium text-gray-900">{interest.label}</span>
            <span className="text-xs text-gray-500 block mt-1">{interest.category}</span>
          </button>
        ))}
      </div>
      <p className="text-sm text-gray-500 text-center">
        {selectedInterests.length} selected
      </p>
    </div>
  );
}

interface ModuleRecommendationStepProps {
  modules: StarterModule[];
  selectedModules: string[];
  onToggle: (id: string) => void;
}

function ModuleRecommendationStep({ modules, selectedModules, onToggle }: ModuleRecommendationStepProps) {
  if (modules.length === 0) {
    return (
      <div className="text-center py-8">
        <BookOpen className="w-12 h-12 mx-auto mb-4 text-gray-400" />
        <p className="text-gray-600">No recommendations available yet.</p>
        <p className="text-sm text-gray-500 mt-2">You can explore content from the dashboard.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-gray-600 mb-4">
        Based on your interests, here are some recommended modules to get you started.
      </p>
      <div className="space-y-3 max-h-80 overflow-y-auto">
        {modules.map((module) => (
          <div
            key={module.assetId}
            onClick={() => onToggle(module.assetId)}
            className={`p-4 rounded-lg border-2 cursor-pointer transition-all ${
              selectedModules.includes(module.assetId)
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-200 hover:border-blue-300'
            }`}
          >
            <div className="flex justify-between items-start">
              <div className="flex-1">
                <h3 className="font-semibold text-gray-900">{module.title}</h3>
                <p className="text-sm text-gray-600 mt-1 line-clamp-2">{module.description}</p>
                <div className="flex items-center gap-3 mt-2 text-xs text-gray-500">
                  <span>{module.difficulty}</span>
                  <span>•</span>
                  <span>{module.estimatedMinutes} min</span>
                  <span>•</span>
                  <span className="text-blue-600">
                    {Math.round(module.matchScore * 100)}% match
                  </span>
                </div>
                {module.matchReasons.length > 0 && (
                  <p className="text-xs text-gray-500 mt-1 italic">
                    {module.matchReasons[0]}
                  </p>
                )}
              </div>
              <div
                className={`w-6 h-6 rounded-full border-2 ml-3 flex items-center justify-center ${
                  selectedModules.includes(module.assetId)
                    ? 'bg-blue-500 border-blue-500'
                    : 'border-gray-300'
                }`}
              >
                {selectedModules.includes(module.assetId) && (
                  <CheckCircle className="w-4 h-4 text-white" />
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
      <p className="text-sm text-gray-500 text-center">
        {selectedModules.length} modules selected
      </p>
    </div>
  );
}

interface CompletionStepProps {
  selectedModules: number;
}

function CompletionStep({ selectedModules }: CompletionStepProps) {
  return (
    <div className="text-center space-y-6">
      <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto">
        <CheckCircle className="w-10 h-10 text-green-600" />
      </div>
      <h2 className="text-2xl font-bold text-gray-900">You're Ready to Learn!</h2>
      <p className="text-gray-600">
        You've selected {selectedModules} {selectedModules === 1 ? 'module' : 'modules'} to start with.
        You can always add more from the dashboard.
      </p>
      <div className="bg-blue-50 p-4 rounded-lg max-w-md mx-auto">
        <h3 className="font-semibold text-blue-900 mb-2">What's next?</h3>
        <ul className="text-sm text-blue-800 space-y-1 text-left">
          <li>• Complete your first module to build momentum</li>
          <li>• Track your progress on the dashboard</li>
          <li>• Use the AI tutor whenever you need help</li>
          <li>• Join study groups for collaborative learning</li>
        </ul>
      </div>
    </div>
  );
}
