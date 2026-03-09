/**
 * Simple Test Component
 *
 * Basic component to test routing without complex logic
 */

import React from "react";

export const SimpleTestComponent: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-md p-8 max-w-md">
        <h1 className="text-2xl font-bold text-green-600 mb-4">
          Route Working! ✅
        </h1>
        <p className="text-gray-600 mb-4">
          This component loaded successfully without errors.
        </p>
        <div className="text-sm text-gray-500">
          If you can see this, the routing is working correctly.
        </div>
      </div>
    </div>
  );
};

export default SimpleTestComponent;
