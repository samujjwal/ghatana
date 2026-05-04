/**
 * Campaigns Page - Campaign management UI.
 *
 * @doc.type component
 * @doc.purpose Campaign listing and management
 * @doc.layer frontend
 */
import React from 'react';

export function CampaignsPage(): React.ReactElement {
  return (
    <div data-testid="campaigns-page">
      <h1>Campaigns</h1>
      <p>Campaign management interface - coming soon</p>
      <p data-testid="feature-notice">
        This feature is currently in development. Use the API to manage campaigns.
      </p>
    </div>
  );
}
