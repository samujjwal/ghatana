import React from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { demoDashboard } from '../mockData';

export function ConsentPage(): React.ReactElement {
  return (
    <Card>
      <CardHeader title="Consent management" subheader="Grant, review, and revoke data-sharing access" />
      <CardContent>
        <div className="stack gap-md">
          {demoDashboard.consents.map((consent) => (
            <section key={consent.id} className="data-card">
              <div>
                <strong>{consent.recipient}</strong>
                <p className="muted">{consent.purpose} · Expires {consent.expiresAt}</p>
              </div>
              <div className="row gap-sm">
                <span className={`pill ${consent.status === 'expiring' ? 'warning' : ''}`}>{consent.status}</span>
                <Button className="secondary-button">Update</Button>
              </div>
            </section>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}