/**
 * Integration Example for New Features
 * Demonstrates how to use onboarding tours, accessibility monitoring, and contextual help
 */

import React, { useState } from 'react';
import {
  OnboardingTrigger,
  AccessibilityDashboard,
  HelpTrigger,
  ContextualHelp,
  useOnboardingTour,
  useAutoOnboarding,
} from '../libs/ui/src';

export const IntegrationExample: React.FC = () => {
  const [showAccessibilityDashboard, setShowAccessibilityDashboard] = useState(false);
  const { shouldShowOnboarding } = useOnboardingTour();
  
  // Auto-show onboarding for new users
  useAutoOnboarding({
    enabled: true,
    delay: 2000,
    tourId: 'getting-started',
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header with help and onboarding triggers */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <h1 className="text-xl font-semibold text-gray-900">
              Canvas Application
            </h1>
            
            <div className="flex items-center space-x-4">
              {/* Accessibility Dashboard Toggle */}
              <button
                onClick={() => setShowAccessibilityDashboard(!showAccessibilityDashboard)}
                className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                data-testid="accessibility-toggle"
              >
                🔍 Accessibility
              </button>
              
              {/* Onboarding Trigger */}
              <OnboardingTrigger
                variant="button"
                showBadge={shouldShowOnboarding()}
              >
                📚 Tours
              </OnboardingTrigger>
              
              {/* Help Trigger */}
              <HelpTrigger context="canvas-main" />
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
          {/* Main Canvas Area */}
          <div className="lg:col-span-3">
            <ContextualHelp context="canvas" trigger="hover">
              <div
                className="bg-white rounded-lg shadow border border-gray-200 h-96 flex items-center justify-center"
                data-testid="canvas-area"
              >
                <div className="text-center">
                  <div className="text-4xl mb-4">🎨</div>
                  <h2 className="text-xl font-semibold text-gray-900 mb-2">
                    Canvas Area
                  </h2>
                  <p className="text-gray-600">
                    Your main workspace for creating diagrams
                  </p>
                  <p className="text-sm text-gray-500 mt-2">
                    Hover over this area for contextual help
                  </p>
                </div>
              </div>
            </ContextualHelp>
          </div>

          {/* Sidebar with Component Palette */}
          <div className="lg:col-span-1">
            <ContextualHelp context="component-palette" trigger="hover">
              <div
                className="bg-white rounded-lg shadow border border-gray-200 p-4"
                data-testid="component-palette"
              >
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Component Palette
                </h3>
                
                <div className="grid grid-cols-2 gap-2">
                  {['Rectangle', 'Circle', 'Arrow', 'Text'].map((component) => (
                    <ContextualHelp
                      key={component}
                      context={`palette-${component.toLowerCase()}`}
                      trigger="hover"
                    >
                      <div
                        className="p-3 border border-gray-200 rounded-md hover:border-blue-300 transition-colors cursor-pointer text-center"
                        data-testid={`palette-item-${component.toLowerCase()}`}
                      >
                        <div className="text-2xl mb-1">
                          {component === 'Rectangle' && '⬜'}
                          {component === 'Circle' && '⭕'}
                          {component === 'Arrow' && '➡️'}
                          {component === 'Text' && '📝'}
                        </div>
                        <span className="text-xs text-gray-600">{component}</span>
                      </div>
                    </ContextualHelp>
                  ))}
                </div>
              </div>
            </ContextualHelp>
          </div>
        </div>

        {/* Toolbar */}
        <div className="mt-8">
          <ContextualHelp context="toolbar" trigger="hover">
            <div
              className="bg-white rounded-lg shadow border border-gray-200 p-4"
              data-testid="canvas-toolbar"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-4">
                  <button className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors">
                    💾 Save
                  </button>
                  <button className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors">
                    📤 Export
                  </button>
                  <button
                    className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                    data-testid="share-button"
                  >
                    🔗 Share
                  </button>
                </div>
                
                <div className="flex items-center space-x-2">
                  <span className="text-sm text-gray-500">Zoom:</span>
                  <button className="px-2 py-1 text-sm text-gray-600 hover:text-gray-800">100%</button>
                </div>
              </div>
            </div>
          </ContextualHelp>
        </div>

        {/* Accessibility Dashboard */}
        {showAccessibilityDashboard && (
          <div className="mt-8">
            <AccessibilityDashboard
              autoRun={true}
              showAutoFix={true}
              className="max-w-4xl"
            />
          </div>
        )}

        {/* Demo Content for Accessibility Testing */}
        <div className="mt-8 space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">
            Demo Content (for Accessibility Testing)
          </h2>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Good accessibility example */}
            <div className="bg-white p-4 rounded-lg shadow border border-gray-200">
              <h3 className="text-base font-medium text-gray-900 mb-2">
                ✅ Good Example
              </h3>
              <form>
                <label htmlFor="good-input" className="block text-sm font-medium text-gray-700 mb-1">
                  Name (required)
                </label>
                <input
                  id="good-input"
                  type="text"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  aria-describedby="good-input-help"
                />
                <p id="good-input-help" className="text-xs text-gray-500 mt-1">
                  Enter your full name
                </p>
                
                <button
                  type="submit"
                  className="mt-3 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  Submit
                </button>
              </form>
            </div>

            {/* Poor accessibility example */}
            <div className="bg-white p-4 rounded-lg shadow border border-gray-200">
              <h3 className="text-base font-medium text-gray-900 mb-2">
                ❌ Poor Example (for testing)
              </h3>
              <form>
                {/* Input without label */}
                <input
                  type="text"
                  placeholder="Enter name"
                  className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  style={{ outline: 'none' }} // Removes focus indicator
                />
                
                {/* Low contrast text */}
                <p className="text-gray-300 text-xs mt-1">
                  This text has poor contrast
                </p>
                
                {/* Clickable div instead of button */}
                <div
                  onClick={() => console.log('clicked')}
                  className="mt-3 px-4 py-2 bg-gray-200 text-gray-600 rounded-md cursor-pointer"
                  style={{ outline: 'none' }}
                >
                  Click me (not accessible)
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>

      {/* Fixed Help FAB */}
      <div className="fixed bottom-6 right-6 z-40">
        <OnboardingTrigger
          variant="fab"
          size="md"
          showBadge={shouldShowOnboarding()}
        />
      </div>
    </div>
  );
};

export default IntegrationExample;