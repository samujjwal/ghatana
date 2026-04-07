import React from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { demoDashboard } from '../mockData';

export function LabsPage(): React.ReactElement {
  return (
    <Card>
      <CardHeader title="Lab results" subheader="HL7-ingested results rendered in a patient-readable view" />
      <CardContent>
        <div className="stack gap-md">
          {demoDashboard.labs.map((lab) => (
            <div key={lab.id} className="data-card">
              <div>
                <strong>{lab.name}</strong>
                <p className="muted">Collected {lab.collectedAt}</p>
              </div>
              <div className="row gap-sm align-center">
                <span className={`pill ${lab.status === 'attention' ? 'warning' : ''}`}>{lab.status}</span>
                <strong>{lab.value}</strong>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}