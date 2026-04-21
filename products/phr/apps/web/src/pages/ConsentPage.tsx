import React, { useState, useEffect } from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import type { ConsentGrant } from '../types';

export function ConsentPage(): React.ReactElement {
  const [consents, setConsents] = useState<ConsentGrant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then(data => setConsents(data.consents))
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load consents'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">Loading consents...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  return (
    <Card>
      <CardHeader title="Consent management" subheader="Grant, review, and revoke data-sharing access" />
      <CardContent>
        <div className="stack gap-md">
          {consents.map((consent) => (
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