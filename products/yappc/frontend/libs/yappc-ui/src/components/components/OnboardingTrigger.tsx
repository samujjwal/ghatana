/**
 * Onboarding Trigger Component
 * Button to launch onboarding tours from anywhere in the app
 */

import clsx from 'clsx';
import React, { useState } from 'react';

import { OnboardingTour } from './OnboardingTour';
import { TourSelection } from './TourSelection';
import { useOnboardingTour } from '../hooks/useOnboardingTour';

/**
 *
 */
export interface OnboardingTriggerProps {
  variant?: 'button' | 'fab' | 'menu-item';
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  children?: React.ReactNode;
  showBadge?: boolean;
}

export const OnboardingTrigger: React.FC<OnboardingTriggerProps> = ({
  variant = 'button',
  size = 'md',
  className,
  children,
  showBadge = true,
}) => {
  const [showTourSelection, setShowTourSelection] = useState(false);
  const {
    isActive,
    currentTour,
    currentStepIndex,
    nextStep,
    previousStep,
    skipTour,
    completeTour,
    stopTour,
    startTour,
    shouldShowOnboarding,
  } = useOnboardingTour();

  const handleTourStart = async (tourId: string) => {
    await startTour(tourId);
    setShowTourSelection(false);
  };

  const shouldShowNewUserBadge = showBadge && shouldShowOnboarding();

  const baseClasses = {
    button: 'inline-flex items-center justify-center font-medium rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500',
    fab: 'fixed bottom-6 right-6 rounded-full shadow-lg transition-all hover:shadow-xl focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500',
    'menu-item': 'flex items-center w-full px-3 py-2 text-sm text-left hover:bg-gray-100 transition-colors',
  };

  const sizeClasses = {
    button: {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2 text-sm',
      lg: 'px-6 py-3 text-base',
    },
    fab: {
      sm: 'w-12 h-12',
      md: 'w-14 h-14',
      lg: 'w-16 h-16',
    },
    'menu-item': {
      sm: 'px-2 py-1 text-xs',
      md: 'px-3 py-2 text-sm',
      lg: 'px-4 py-3 text-base',
    },
  };

  const colorClasses = {
    button: 'bg-blue-600 text-white hover:bg-blue-700',
    fab: 'bg-blue-600 text-white hover:bg-blue-700',
    'menu-item': 'text-gray-700',
  };

  const renderIcon = () => (
    <svg className="w-5 h-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.746 0 3.332.477 4.5 1.253v13C19.832 18.477 18.246 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
    </svg>
  );

  const renderFabIcon = () => (
    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );

  return (
    <>
      <button
        onClick={() => setShowTourSelection(true)}
        className={clsx(
          baseClasses[variant],
          sizeClasses[variant][size],
          colorClasses[variant],
          'relative',
          className
        )}
        data-testid="onboarding-trigger"
      >
        {variant === 'fab' ? renderFabIcon() : renderIcon()}
        {variant !== 'fab' && (children || 'Take a Tour')}
        
        {/* New user badge */}
        {shouldShowNewUserBadge && (
          <span className="absolute -top-1 -right-1 w-3 h-3 bg-red-500 rounded-full animate-pulse" />
        )}
      </button>

      {/* Tour Selection Modal */}
      <TourSelection
        isOpen={showTourSelection}
        onClose={() => setShowTourSelection(false)}
        onTourStart={handleTourStart}
      />

      {/* Active Tour */}
      <OnboardingTour
        isActive={isActive}
        currentTour={currentTour}
        currentStepIndex={currentStepIndex}
        onNext={nextStep}
        onPrevious={previousStep}
        onSkip={skipTour}
        onComplete={completeTour}
        onClose={stopTour}
      />
    </>
  );
};

export default OnboardingTrigger;