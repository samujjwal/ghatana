import React from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';

export function EmergencyAccessPage(): React.ReactElement {
  return (
    <div className="two-column-layout">
      <Card>
        <CardHeader title="Break-glass workflow" subheader="High-friction emergency access with audit trail requirements" />
        <CardContent>
          <div className="stack gap-md">
            <p>Emergency mode grants a 30 minute scoped view to responders and triggers caregiver notification.</p>
            <ul className="stack gap-sm">
              <li>Reason capture is mandatory.</li>
              <li>Consent exceptions are recorded for retrospective review.</li>
              <li>All exports are redacted outside immediate treatment context.</li>
            </ul>
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title="Activate review" subheader="This page mirrors the emergency access backend workflow" />
        <CardContent>
          <div className="stack gap-md">
            <Button className="danger-button">Request emergency access</Button>
            <Button className="secondary-button">Notify caregiver</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}