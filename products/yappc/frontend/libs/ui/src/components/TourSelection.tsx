/**
 * Tour Selection Component
 * Allows users to browse and start available onboarding tours
 */

import clsx from 'clsx';
import React, { useState, useEffect } from 'react';

import { onboardingTourManager } from './OnboardingTour';

import type { Tour} from './OnboardingTour';

/**
 *
 */
export interface TourSelectionProps {
  isOpen: boolean;
  onClose: () => void;
  onTourStart: (tourId: string) => void;
  className?: string;
}

const categoryIcons: Record<string, string> = {
  'getting-started': '🚀',
  'canvas-basics': '🎨',
  'advanced-features': '⚡',
  'collaboration': '👥',
  'shortcuts': '⌨️',
};

const categoryNames: Record<string, string> = {
  'getting-started': 'Getting Started',
  'canvas-basics': 'Canvas Basics',
  'advanced-features': 'Advanced Features',
  'collaboration': 'Collaboration',
  'shortcuts': 'Keyboard Shortcuts',
};

export const TourSelection: React.FC<TourSelectionProps> = ({
  isOpen,
  onClose,
  onTourStart,
  className,
}) => {
  const [availableTours, setAvailableTours] = useState<Tour[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedTour, setSelectedTour] = useState<Tour | null>(null);

  useEffect(() => {
    if (isOpen) {
      const tours = onboardingTourManager.getAvailableTours();
      setAvailableTours(tours);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const categories = Array.from(new Set(availableTours.map(tour => tour.category)));
  const filteredTours = selectedCategory 
    ? availableTours.filter(tour => tour.category === selectedCategory)
    : availableTours;

  const handleTourStart = (tour: Tour) => {
    onTourStart(tour.id);
    onClose();
  };

  return (
    <div className={clsx('fixed inset-0 z-50 flex items-center justify-center', className)}>
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-black bg-opacity-50"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative bg-white rounded-lg shadow-xl max-w-4xl w-full mx-4 max-h-[80vh] overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex justify-between items-center">
            <div>
              <h2 className="text-xl font-semibold text-gray-900">Choose a Tour</h2>
              <p className="text-sm text-gray-600 mt-1">
                Learn how to use the platform with guided interactive tours
              </p>
            </div>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 transition-colors"
              data-testid="close-tour-selection"
            >
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        <div className="flex h-[calc(80vh-80px)]">
          {/* Sidebar - Categories */}
          <div className="w-64 bg-gray-50 border-r border-gray-200 p-4">
            <h3 className="text-sm font-medium text-gray-900 mb-3">Categories</h3>
            <div className="space-y-1">
              <button
                onClick={() => setSelectedCategory(null)}
                className={clsx(
                  'w-full text-left px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  selectedCategory === null
                    ? 'bg-blue-100 text-blue-900'
                    : 'text-gray-700 hover:bg-gray-100'
                )}
                data-testid="category-all"
              >
                All Tours ({availableTours.length})
              </button>
              
              {categories.map(category => {
                const count = availableTours.filter(tour => tour.category === category).length;
                return (
                  <button
                    key={category}
                    onClick={() => setSelectedCategory(category)}
                    className={clsx(
                      'w-full text-left px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center',
                      selectedCategory === category
                        ? 'bg-blue-100 text-blue-900'
                        : 'text-gray-700 hover:bg-gray-100'
                    )}
                    data-testid={`category-${category}`}
                  >
                    <span className="mr-2">{categoryIcons[category]}</span>
                    <span className="flex-1">{categoryNames[category]}</span>
                    <span className="text-xs bg-gray-200 px-2 py-1 rounded-full">{count}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Main content */}
          <div className="flex-1 overflow-y-auto">
            {selectedTour ? (
              /* Tour details */
              <div className="p-6">
                <button
                  onClick={() => setSelectedTour(null)}
                  className="flex items-center text-sm text-gray-600 hover:text-gray-900 mb-4"
                >
                  <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                  </svg>
                  Back to tours
                </button>

                <div className="mb-6">
                  <div className="flex items-center mb-4">
                    <span className="text-3xl mr-3">{selectedTour.icon}</span>
                    <div>
                      <h3 className="text-2xl font-bold text-gray-900">{selectedTour.name}</h3>
                      <div className="flex items-center text-sm text-gray-600 mt-1">
                        <span className="bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-xs font-medium mr-3">
                          {categoryNames[selectedTour.category]}
                        </span>
                        <span>~{selectedTour.estimatedDuration} minutes</span>
                      </div>
                    </div>
                  </div>
                  
                  <p className="text-gray-700 mb-6">{selectedTour.description}</p>

                  {selectedTour.prerequisites && selectedTour.prerequisites.length > 0 && (
                    <div className="mb-6">
                      <h4 className="text-sm font-medium text-gray-900 mb-2">Prerequisites:</h4>
                      <div className="flex flex-wrap gap-2">
                        {selectedTour.prerequisites.map(prereq => (
                          <span
                            key={prereq}
                            className="bg-gray-100 text-gray-700 px-2 py-1 rounded text-xs"
                          >
                            {prereq}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="mb-6">
                    <h4 className="text-sm font-medium text-gray-900 mb-3">
                      Tour Steps ({selectedTour.steps.length})
                    </h4>
                    <div className="space-y-3">
                      {selectedTour.steps.map((step, index) => (
                        <div
                          key={step.id}
                          className="flex items-start p-3 bg-gray-50 rounded-md"
                        >
                          <div className="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-800 rounded-full flex items-center justify-center text-xs font-medium mr-3 mt-0.5">
                            {index + 1}
                          </div>
                          <div>
                            <h5 className="text-sm font-medium text-gray-900">{step.title}</h5>
                            <p className="text-sm text-gray-600 mt-1">{step.content}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <button
                    onClick={() => handleTourStart(selectedTour)}
                    className="w-full bg-blue-600 text-white py-3 px-4 rounded-md font-medium hover:bg-blue-700 transition-colors"
                    data-testid="start-selected-tour"
                  >
                    Start Tour
                  </button>
                </div>
              </div>
            ) : (
              /* Tour list */
              <div className="p-6">
                <div className="grid gap-4 md:grid-cols-2">
                  {filteredTours.map(tour => (
                    <div
                      key={tour.id}
                      className="border border-gray-200 rounded-lg p-4 hover:border-gray-300 transition-colors cursor-pointer"
                      onClick={() => setSelectedTour(tour)}
                      data-testid={`tour-card-${tour.id}`}
                    >
                      <div className="flex items-start justify-between mb-3">
                        <div className="flex items-center">
                          <span className="text-2xl mr-3">{tour.icon}</span>
                          <div>
                            <h3 className="font-medium text-gray-900">{tour.name}</h3>
                            <div className="flex items-center text-sm text-gray-600 mt-1">
                              <span className="bg-gray-100 text-gray-700 px-2 py-1 rounded-full text-xs mr-2">
                                {categoryNames[tour.category]}
                              </span>
                              <span>~{tour.estimatedDuration} min</span>
                            </div>
                          </div>
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleTourStart(tour);
                          }}
                          className="bg-blue-600 text-white px-3 py-1.5 rounded text-sm font-medium hover:bg-blue-700 transition-colors"
                          data-testid={`start-tour-${tour.id}`}
                        >
                          Start
                        </button>
                      </div>
                      
                      <p className="text-gray-600 text-sm mb-3">{tour.description}</p>
                      
                      <div className="flex items-center justify-between text-xs text-gray-500">
                        <span>{tour.steps.length} steps</span>
                        {tour.prerequisites && tour.prerequisites.length > 0 && (
                          <span>Prerequisites required</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                {filteredTours.length === 0 && (
                  <div className="text-center py-12">
                    <div className="text-6xl mb-4">🎉</div>
                    <h3 className="text-lg font-medium text-gray-900 mb-2">All caught up!</h3>
                    <p className="text-gray-600">
                      You've completed all available tours in this category.
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default TourSelection;