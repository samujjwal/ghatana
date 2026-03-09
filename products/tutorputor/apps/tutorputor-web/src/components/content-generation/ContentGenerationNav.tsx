/**
 * Content Generation Navigation
 *
 * Quick navigation to all content generation visualization components
 */

import React from "react";
import { Link } from "react-router-dom";

export const ContentGenerationNav: React.FC = () => {
  return (
    <div className="bg-white rounded-lg shadow-md p-6 mb-6">
      <h2 className="text-xl font-bold text-gray-900 mb-4">
        Content Generation Visualizations
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Link
          to="/content-generation"
          className="block p-4 border-2 border-blue-500 rounded-lg hover:bg-blue-50 transition-colors"
        >
          <div className="text-2xl mb-2">🎯</div>
          <h3 className="font-semibold text-gray-900">Complete Demo</h3>
          <p className="text-sm text-gray-600 mt-1">
            All visualizations in one place
          </p>
        </Link>

        <Link
          to="/content-generation-dashboard"
          className="block p-4 border-2 border-green-500 rounded-lg hover:bg-green-50 transition-colors"
        >
          <div className="text-2xl mb-2">📊</div>
          <h3 className="font-semibold text-gray-900">Analytics Dashboard</h3>
          <p className="text-sm text-gray-600 mt-1">
            Real-time metrics and performance
          </p>
        </Link>

        <Link
          to="/simulation-preview"
          className="block p-4 border-2 border-purple-500 rounded-lg hover:bg-purple-50 transition-colors"
        >
          <div className="text-2xl mb-2">🎮</div>
          <h3 className="font-semibold text-gray-900">Simulation Preview</h3>
          <p className="text-sm text-gray-600 mt-1">
            Interactive content demos
          </p>
        </Link>

        <Link
          to="/analytics"
          className="block p-4 border-2 border-orange-500 rounded-lg hover:bg-orange-50 transition-colors"
        >
          <div className="text-2xl mb-2">📈</div>
          <h3 className="font-semibold text-gray-900">System Analytics</h3>
          <p className="text-sm text-gray-600 mt-1">Advanced system insights</p>
        </Link>
      </div>

      <div className="mt-6 p-4 bg-gray-50 rounded-lg">
        <h3 className="font-semibold text-gray-900 mb-2">Quick Start Guide</h3>
        <ol className="text-sm text-gray-600 space-y-1">
          <li>
            1. Start with the <strong>Complete Demo</strong> for an overview
          </li>
          <li>
            2. Visit the <strong>Analytics Dashboard</strong> to see real-time
            metrics
          </li>
          <li>
            3. Try the <strong>Simulation Preview</strong> to see generated
            content
          </li>
          <li>
            4. Check <strong>System Analytics</strong> for deep insights
          </li>
        </ol>
      </div>
    </div>
  );
};

export default ContentGenerationNav;
