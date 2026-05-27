import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchImmunizations } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ImmunizationSummary } from '../types';

export function ImmunizationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [immunizations, setImmunizations] = useState<ImmunizationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchImmunizations(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setImmunizations)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load immunizations'))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">Loading immunizations...</div>;
  if (error) return <div className="error">Error: {error}</div>;
  if (!immunizations.length) return <div className="empty">No immunizations recorded</div>;

  // Group by status
  const complete = immunizations.filter((imm) => imm.status === 'complete');
  const due = immunizations.filter((imm) => imm.status === 'due');
  const other = immunizations.filter((imm) => imm.status !== 'complete' && imm.status !== 'due');

  // Calculate retention status (time since last dose)
  const getRetentionStatus = (imm: ImmunizationSummary): string => {
    if (!imm.occurrenceDate) return 'unknown';
    const date = new Date(imm.occurrenceDate);
    const now = new Date();
    const yearsSince = (now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24 * 365);
    
    if (yearsSince < 1) return 'current';
    if (yearsSince < 5) return 'valid';
    if (yearsSince < 10) return 'expiring';
    return 'expired';
  };

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title="Immunizations" subheader="Your vaccination history and status" />
        <CardContent>
          <div className="stack gap-md">
            {/* Complete immunizations */}
            {complete.length > 0 && (
              <>
                <h3>Complete</h3>
                <ul className="stack gap-sm">
                  {complete.map((imm) => {
                    const retention = getRetentionStatus(imm);
                    return (
                      <li key={imm.id} className="immunization-entry">
                        <strong>{imm.vaccine}</strong>
                        <span className={`badge badge--${retention}`}>
                          {retention === 'current' && 'Current'}
                          {retention === 'valid' && 'Valid'}
                          {retention === 'expiring' && 'Expiring Soon'}
                          {retention === 'expired' && 'Expired'}
                          {retention === 'unknown' && 'Unknown'}
                        </span>
                        <time dateTime={imm.occurrenceDate}>{new Date(imm.occurrenceDate).toLocaleDateString()}</time>
                        {imm.lotNumber != null && <span className="muted">Lot: {imm.lotNumber}</span>}
                        {imm.cvxCode != null && <span className="muted">CVX: {imm.cvxCode}</span>}
                      </li>
                    );
                  })}
                </ul>
              </>
            )}

            {/* Due immunizations */}
            {due.length > 0 && (
              <>
                <h3>Due</h3>
                <ul className="stack gap-sm">
                  {due.map((imm) => (
                    <li key={imm.id} className="immunization-entry">
                      <strong>{imm.vaccine}</strong>
                      <span className="badge badge--due">Due</span>
                      {imm.lotNumber != null && <span className="muted">Last Lot: {imm.lotNumber}</span>}
                      {imm.cvxCode != null && <span className="muted">CVX: {imm.cvxCode}</span>}
                    </li>
                  ))}
                </ul>
              </>
            )}

            {/* Other status */}
            {other.length > 0 && (
              <>
                <h3>Other</h3>
                <ul className="stack gap-sm">
                  {other.map((imm) => (
                    <li key={imm.id} className="immunization-entry">
                      <strong>{imm.vaccine}</strong>
                      {imm.status && <span className="badge badge--other">{imm.status}</span>}
                      <time dateTime={imm.occurrenceDate}>{new Date(imm.occurrenceDate).toLocaleDateString()}</time>
                      {imm.lotNumber != null && <span className="muted">Lot: {imm.lotNumber}</span>}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
