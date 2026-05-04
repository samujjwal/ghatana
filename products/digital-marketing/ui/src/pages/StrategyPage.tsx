/**
 * Strategy Page - Strategy generation UI.
 *
 * @doc.type component
 * @doc.purpose Strategy generation and management
 * @doc.layer frontend
 */
import React from 'react';

export function StrategyPage(): React.ReactElement {
  return (
    <div data-testid="strategy-page">
      <h1>Strategy</h1>
      <p>Strategy generation interface - coming soon</p>
      <p data-testid="feature-notice">
        This feature is currently in development. Use the API to generate strategies.
      </p>
    </div>
  );
}
