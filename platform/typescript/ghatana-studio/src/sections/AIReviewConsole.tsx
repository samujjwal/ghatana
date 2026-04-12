/**
 * AI Review Console
 *
 * AI operation inspection, confidence analysis, and human-in-the-loop review.
 * Integrates with @ghatana/platform-events for AI telemetry.
 *
 * @doc.type component
 * @doc.purpose AI operation review and analysis
 * @doc.layer platform
 */

import React from 'react';
import { Button, Typography, ConfidenceBadge } from '@ghatana/design-system';

export default function AIReviewConsole() {
  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-4">
          <Typography variant="h2" className="text-2xl font-bold">
            AI Review Console
          </Typography>
          <Button variant="primary">Load Events</Button>
        </div>
        
        <div className="studio-card">
          <Typography variant="body" className="text-gray-600 mb-4">
            Review AI operations, confidence scores, and human-in-the-loop decisions.
          </Typography>
          
          <div className="space-y-4">
            <div className="flex items-center gap-2 mb-4">
              <ConfidenceBadge confidence={0.85} size="md" />
              <Typography variant="body" className="text-sm text-gray-600">
                Sample confidence display
              </Typography>
            </div>
            
            <div className="border border-dashed border-gray-300 rounded-lg p-8 text-center">
              <Typography variant="body" className="text-gray-500">
                No AI events loaded yet. Load telemetry data to begin review.
              </Typography>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
