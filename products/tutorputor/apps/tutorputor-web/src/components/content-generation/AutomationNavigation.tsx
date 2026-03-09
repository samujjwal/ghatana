/**
 * Automation Navigation Component
 *
 * Provides navigation links for the automatic content creator system.
 * Reuses existing navigation patterns and extends them with automation features.
 *
 * @doc.type module
 * @doc.purpose Navigation for automation system
 * @doc.layer product
 * @doc.pattern Navigation
 */

import React from "react";
import { Link } from "react-router-dom";

/**
 * Automation Navigation Component
 */
export const AutomationNavigation: React.FC = () => {
  return (
    <nav className="bg-white shadow-sm border-b">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center py-4">
          <div className="flex items-center space-x-8">
            <div className="flex items-center">
              <span className="text-2xl mr-3">🤖</span>
              <h2 className="text-lg font-semibold text-gray-900">
                Automatic Content Creator
              </h2>
            </div>

            <div className="hidden md:flex space-x-6">
              <Link
                to="/automatic-content-creator"
                className="text-gray-700 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium transition-colors"
              >
                🏠 Automation Dashboard
              </Link>
              <Link
                to="/content-generation"
                className="text-gray-700 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium transition-colors"
              >
                🎯 Content Generation
              </Link>
              <Link
                to="/content-generation-dashboard"
                className="text-gray-700 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium transition-colors"
              >
                📊 Analytics
              </Link>
              <Link
                to="/simulation-preview"
                className="text-gray-700 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium transition-colors"
              >
                🎮 Simulations
              </Link>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2">
              <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
              <span className="text-sm text-gray-600">Automation Active</span>
            </div>
            <button className="bg-blue-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-blue-700 transition-colors">
              ⚡ Quick Create Rule
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default AutomationNavigation;
