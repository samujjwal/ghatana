/**
 * Interactive Onboarding Tour System
 * Provides guided tours for new users and feature discovery
 */

import clsx from 'clsx';
import React from 'react';

/**
 *
 */
export interface TourStep {
  id: string;
  title: string;
  content: string;
  target: string; // CSS selector or element ID
  position: 'top' | 'bottom' | 'left' | 'right' | 'center';
  action?: 'click' | 'hover' | 'focus' | 'none';
  actionTarget?: string;
  nextEnabled?: boolean;
  skipEnabled?: boolean;
  highlightPadding?: number;
  delay?: number;
  beforeStep?: () => Promise<void> | void;
  afterStep?: () => Promise<void> | void;
}

/**
 *
 */
export interface Tour {
  id: string;
  name: string;
  description: string;
  category: 'getting-started' | 'canvas-basics' | 'advanced-features' | 'collaboration' | 'shortcuts';
  icon: string;
  estimatedDuration: number; // in minutes
  prerequisites?: string[];
  steps: TourStep[];
  onComplete?: () => void;
  onSkip?: () => void;
}

/**
 *
 */
export interface OnboardingTourProps {
  isActive: boolean;
  currentTour: Tour | null;
  currentStepIndex: number;
  onNext: () => void;
  onPrevious: () => void;
  onSkip: () => void;
  onComplete: () => void;
  onClose: () => void;
  className?: string;
}

/**
 *
 */
export interface OnboardingState {
  isActive: boolean;
  currentTour: Tour | null;
  currentStepIndex: number;
  currentStep: TourStep | null;
  totalSteps: number;
  completedTours: string[];
}

/**
 *
 */
export class OnboardingTourManager {
  private tours: Map<string, Tour> = new Map();
  private currentTour: Tour | null = null;
  private currentStepIndex: number = 0;
  private isActive: boolean = false;
  private listeners: Set<(state: OnboardingState) => void> = new Set();
  private completedTours: Set<string> = new Set();

  /**
   *
   */
  constructor() {
    this.registerDefaultTours();
    this.loadCompletedTours();
  }

  /**
   * Register a tour
   */
  registerTour(tour: Tour): void {
    this.tours.set(tour.id, tour);
  }

  /**
   * Start a specific tour
   */
  async startTour(tourId: string): Promise<boolean> {
    const tour = this.tours.get(tourId);
    if (!tour) {
      console.warn(`Tour ${tourId} not found`);
      return false;
    }

    // Check prerequisites
    if (tour.prerequisites) {
      const unmetPrereqs = tour.prerequisites.filter(req => !this.completedTours.has(req));
      if (unmetPrereqs.length > 0) {
        console.warn(`Tour ${tourId} has unmet prerequisites:`, unmetPrereqs);
        return false;
      }
    }

    this.currentTour = tour;
    this.currentStepIndex = 0;
    this.isActive = true;

    this.notifyListeners();
    await this.executeCurrentStep();
    return true;
  }

  /**
   * Go to next step
   */
  async nextStep(): Promise<void> {
    if (!this.currentTour || !this.isActive) return;

    const currentStep = this.currentTour.steps[this.currentStepIndex];
    
    // Execute after-step action
    if (currentStep.afterStep) {
      await currentStep.afterStep();
    }

    this.currentStepIndex++;

    if (this.currentStepIndex >= this.currentTour.steps.length) {
      this.completeTour();
    } else {
      this.notifyListeners();
      await this.executeCurrentStep();
    }
  }

  /**
   * Go to previous step
   */
  async previousStep(): Promise<void> {
    if (!this.currentTour || !this.isActive || this.currentStepIndex <= 0) return;

    this.currentStepIndex--;
    this.notifyListeners();
    await this.executeCurrentStep();
  }

  /**
   * Skip current tour
   */
  skipTour(): void {
    if (!this.currentTour || !this.isActive) return;

    if (this.currentTour.onSkip) {
      this.currentTour.onSkip();
    }

    this.stopTour();
  }

  /**
   * Complete current tour
   */
  completeTour(): void {
    if (!this.currentTour || !this.isActive) return;

    const tourId = this.currentTour.id;
    this.completedTours.add(tourId);
    this.saveCompletedTours();

    if (this.currentTour.onComplete) {
      this.currentTour.onComplete();
    }

    this.stopTour();
  }

  /**
   * Stop current tour
   */
  stopTour(): void {
    this.currentTour = null;
    this.currentStepIndex = 0;
    this.isActive = false;
    this.notifyListeners();
  }

  /**
   * Get all available tours
   */
  getAvailableTours(): Tour[] {
    return Array.from(this.tours.values()).filter(tour => {
      // Filter out completed tours unless they're repeatable
      if (this.completedTours.has(tour.id)) return false;
      
      // Filter out tours with unmet prerequisites
      if (tour.prerequisites) {
        const unmetPrereqs = tour.prerequisites.filter(req => !this.completedTours.has(req));
        if (unmetPrereqs.length > 0) return false;
      }
      
      return true;
    });
  }

  /**
   * Get current tour state
   */
  getCurrentState(): OnboardingState {
    return {
      isActive: this.isActive,
      currentTour: this.currentTour,
      currentStepIndex: this.currentStepIndex,
      currentStep: this.currentTour?.steps[this.currentStepIndex] || null,
      totalSteps: this.currentTour?.steps.length || 0,
      completedTours: Array.from(this.completedTours),
    };
  }

  /**
   * Check if user should see onboarding
   */
  shouldShowOnboarding(): boolean {
    return this.completedTours.size === 0;
  }

  /**
   * Add state change listener
   */
  addListener(listener: (state: OnboardingState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   *
   */
  private async executeCurrentStep(): Promise<void> {
    if (!this.currentTour || !this.isActive) return;

    const step = this.currentTour.steps[this.currentStepIndex];
    
    // Execute before-step action
    if (step.beforeStep) {
      await step.beforeStep();
    }

    // Add delay if specified
    if (step.delay) {
      await new Promise(resolve => setTimeout(resolve, step.delay));
    }

    // Scroll target into view
    const targetElement = document.querySelector(step.target);
    if (targetElement) {
      targetElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  /**
   *
   */
  private registerDefaultTours(): void {
    const tours: Tour[] = [
      {
        id: 'getting-started',
        name: 'Getting Started',
        description: 'Learn the basics of using the canvas platform',
        category: 'getting-started',
        icon: '🚀',
        estimatedDuration: 5,
        steps: [
          {
            id: 'welcome',
            title: 'Welcome to Canvas',
            content: 'Welcome! This tour will help you get started with creating interactive diagrams and workflows.',
            target: 'body',
            position: 'center',
            nextEnabled: true,
          },
          {
            id: 'canvas-area',
            title: 'Canvas Area',
            content: 'This is your main canvas where you can add nodes, connections, and build your diagrams.',
            target: '[data-testid="react-flow-wrapper"], [data-testid="rf__wrapper"]',
            position: 'top',
            highlightPadding: 20,
            nextEnabled: true,
          },
          {
            id: 'component-palette',
            title: 'Component Palette',
            content: 'Use this palette to add different types of components to your canvas. Try dragging one onto the canvas!',
            target: '[data-testid="component-palette"]',
            position: 'right',
            action: 'hover',
            nextEnabled: true,
          },
          {
            id: 'toolbar',
            title: 'Toolbar',
            content: 'Access important tools and actions from the toolbar. You can save, export, and manage your canvas here.',
            target: '[data-testid="canvas-toolbar"]',
            position: 'bottom',
            nextEnabled: true,
          },
          {
            id: 'command-palette',
            title: 'Command Palette',
            content: 'Press Cmd+K (or Ctrl+K) to open the command palette for quick access to all features.',
            target: 'body',
            position: 'center',
            beforeStep: () => {
              // Simulate opening command palette
              document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', metaKey: true }));
            },
            delay: 1000,
            nextEnabled: true,
          },
        ],
      },
      
      {
        id: 'canvas-basics',
        name: 'Canvas Basics',
        description: 'Learn how to create and manipulate elements on the canvas',
        category: 'canvas-basics',
        icon: '🎨',
        estimatedDuration: 8,
        prerequisites: ['getting-started'],
        steps: [
          {
            id: 'add-node',
            title: 'Adding Nodes',
            content: 'Drag a component from the palette to the canvas to create a new node.',
            target: '[data-testid="palette-item-component"]',
            position: 'right',
            action: 'click',
            nextEnabled: true,
          },
          {
            id: 'select-node',
            title: 'Selecting Elements',
            content: 'Click on a node to select it. Selected nodes show handles for connections.',
            target: '.react-flow__node',
            position: 'top',
            action: 'click',
            nextEnabled: true,
          },
          {
            id: 'connect-nodes',
            title: 'Connecting Nodes',
            content: 'Drag from a node handle to another node to create connections.',
            target: '.react-flow__handle',
            position: 'top',
            nextEnabled: true,
          },
          {
            id: 'properties-panel',
            title: 'Properties Panel',
            content: 'Edit node properties and settings in the properties panel.',
            target: '[data-testid="properties-panel"]',
            position: 'left',
            nextEnabled: true,
          },
        ],
      },

      {
        id: 'collaboration',
        name: 'Collaboration Features',
        description: 'Learn how to collaborate with others in real-time',
        category: 'collaboration',
        icon: '👥',
        estimatedDuration: 6,
        prerequisites: ['canvas-basics'],
        steps: [
          {
            id: 'share-canvas',
            title: 'Sharing Canvas',
            content: 'Click the share button to invite others to collaborate on your canvas.',
            target: '[data-testid="share-button"]',
            position: 'bottom',
            nextEnabled: true,
          },
          {
            id: 'user-presence',
            title: 'User Presence',
            content: 'See who else is viewing and editing the canvas with real-time cursors.',
            target: '[data-testid="user-presence"]',
            position: 'bottom',
            nextEnabled: true,
          },
          {
            id: 'comments',
            title: 'Comments',
            content: 'Add comments to discuss specific parts of your canvas with collaborators.',
            target: '[data-testid="comments-panel"]',
            position: 'left',
            nextEnabled: true,
          },
        ],
      },

      {
        id: 'shortcuts',
        name: 'Keyboard Shortcuts',
        description: 'Master keyboard shortcuts for efficient workflow',
        category: 'shortcuts',
        icon: '⌨️',
        estimatedDuration: 4,
        steps: [
          {
            id: 'basic-shortcuts',
            title: 'Basic Shortcuts',
            content: 'Use Cmd+C/Cmd+V to copy/paste, Cmd+Z/Cmd+Shift+Z for undo/redo.',
            target: 'body',
            position: 'center',
            nextEnabled: true,
          },
          {
            id: 'selection-shortcuts',
            title: 'Selection Shortcuts',
            content: 'Use Cmd+A to select all elements, or hold Shift to multi-select.',
            target: 'body',
            position: 'center',
            nextEnabled: true,
          },
        ],
      },
    ];

    tours.forEach(tour => this.registerTour(tour));
  }

  /**
   *
   */
  private notifyListeners(): void {
    const state = this.getCurrentState();
    this.listeners.forEach(listener => {
      try {
        listener(state);
      } catch (error) {
        console.error('Tour listener error:', error);
      }
    });
  }

  /**
   *
   */
  private loadCompletedTours(): void {
    try {
      const saved = localStorage.getItem('completed-tours');
      if (saved) {
        const completed = JSON.parse(saved);
        this.completedTours = new Set(completed);
      }
    } catch (error) {
      console.error('Failed to load completed tours:', error);
    }
  }

  /**
   *
   */
  private saveCompletedTours(): void {
    try {
      localStorage.setItem('completed-tours', JSON.stringify(Array.from(this.completedTours)));
    } catch (error) {
      console.error('Failed to save completed tours:', error);
    }
  }
}

export const OnboardingTourUI: React.FC<OnboardingTourProps> = ({
  isActive,
  currentTour,
  currentStepIndex,
  onNext,
  onPrevious,
  onSkip,
  onComplete,
  onClose,
  className,
}) => {
  if (!isActive || !currentTour) {
    return null;
  }

  const currentStep = currentTour.steps[currentStepIndex];
  const totalSteps = currentTour.steps.length;
  const progressPercent = Math.min(100, ((currentStepIndex + 1) / totalSteps) * 100);
  const isDev = typeof import.meta !== 'undefined' && Boolean((import.meta as unknown).env?.DEV);

  return (
    <div
      className={clsx(
        'fixed inset-0 z-[2000] flex items-center justify-center bg-slate-900/60 px-4 py-10 backdrop-blur-sm',
        className,
      )}
      data-testid="onboarding-tour"
    >
      <div className="relative w-full max-w-xl rounded-2xl bg-white p-6 shadow-2xl ring-1 ring-black/10">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close onboarding tour"
          className="absolute right-4 top-4 inline-flex h-8 w-8 items-center justify-center rounded-full text-gray-500 transition hover:text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          data-testid="tour-close"
        >
          ×
        </button>

        <div className="mb-4 flex items-start justify-between gap-4 pr-8">
          <div>
            <p className="text-xs uppercase tracking-wide text-blue-600">{currentTour.name}</p>
            <h3 className="mt-1 text-xl font-semibold text-gray-900">{currentStep.title}</h3>
            <p className="mt-1 text-sm text-gray-500">
              Step {currentStepIndex + 1} of {totalSteps}
            </p>
          </div>
        </div>

        <div className="mb-5">
          <div className="h-2 w-full rounded-full bg-gray-200">
            <div
              className="h-2 rounded-full bg-blue-600 transition-all duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>

        <div className="mb-6 space-y-3">
          <p className="text-base text-gray-700">{currentStep.content}</p>
          {currentStep.action && currentStep.action !== 'none' && (
            <p className="text-sm font-medium text-blue-600">
              Suggested action: {currentStep.action}
              {currentStep.actionTarget ? ` on ${currentStep.actionTarget}` : ''}
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            {currentStepIndex > 0 && (
              <button
                type="button"
                onClick={onPrevious}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
                data-testid="tour-previous"
              >
                Previous
              </button>
            )}

            {currentStep.skipEnabled !== false && (
              <button
                type="button"
                onClick={onSkip}
                className="rounded-md px-4 py-2 text-sm font-medium text-gray-500 hover:text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                data-testid="tour-skip"
              >
                Skip tour
              </button>
            )}
          </div>

          <div>
            {currentStepIndex < totalSteps - 1 ? (
              <button
                type="button"
                onClick={onNext}
                disabled={currentStep.nextEnabled === false}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
                data-testid="tour-next"
              >
                Next
              </button>
            ) : (
              <button
                type="button"
                onClick={onComplete}
                className="rounded-md bg-green-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500"
                data-testid="tour-complete"
              >
                Complete
              </button>
            )}
          </div>
        </div>

        {isDev && (
          <div className="mt-6 rounded-lg border border-dashed border-gray-300 bg-gray-50 p-3 text-xs text-gray-600">
            <p className="mb-2 font-semibold text-gray-700">Developer helpers</p>
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                className="rounded-md border border-gray-300 bg-white px-3 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-100"
                onClick={() => {
                  try {
                    const stored = localStorage.getItem('completed-tours');
                    const completed = stored ? new Set<string>(JSON.parse(stored)) : new Set<string>();
                    completed.add('getting-started');
                    localStorage.setItem('completed-tours', JSON.stringify(Array.from(completed)));
                    window.dispatchEvent(
                      new CustomEvent('onboarding-tour:completed-dev', {
                        detail: { tourId: 'getting-started' },
                      }),
                    );
                  } catch (error) {
                    console.error('Failed to mark getting-started tour complete (dev helper)', error);
                  }
                }}
              >
                Mark "Getting Started" complete
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Backwards-compatible wrapper: some callers import { OnboardingTour }.
 * Re-export the memoized UI component under that name.
 */
export const OnboardingTour = React.memo(OnboardingTourUI);

export const onboardingTourManager = new OnboardingTourManager();
export default OnboardingTour;