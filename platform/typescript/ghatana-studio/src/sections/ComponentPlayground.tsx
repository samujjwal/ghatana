/**
 * Component Playground
 *
 * Interactive component testing and story exploration.
 * Integrates with @ghatana/design-system contracts.
 *
 * @doc.type component
 * @doc.purpose Component testing and exploration
 * @doc.layer platform
 */

import React from 'react';
import { Button, Typography } from '@ghatana/design-system';

export default function ComponentPlayground() {
  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-4">
          <Typography variant="h2" className="text-2xl font-bold">
            Component Playground
          </Typography>
          <Button variant="primary">Select Component</Button>
        </div>
        
        <div className="studio-card">
          <Typography variant="body" className="text-gray-600 mb-4">
            Explore and test design system components with live prop editing.
          </Typography>
          
          <div className="space-y-4">
            <div className="border border-dashed border-gray-300 rounded-lg p-8 text-center">
              <Typography variant="body" className="text-gray-500">
                Select a component to explore its props and variants.
              </Typography>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
