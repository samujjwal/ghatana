import React from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { demoDashboard } from '../mockData';

export function MedicationsPage(): React.ReactElement {
  return (
    <Card>
      <CardHeader title="Medication management" subheader="Medication schedule, adherence, and refill planning" />
      <CardContent>
        <div className="stack gap-md">
          {demoDashboard.medications.map((medication) => (
            <div key={medication.id} className="data-card">
              <div>
                <strong>{medication.medication} {medication.dosage}</strong>
                <p className="muted">Schedule {medication.schedule}</p>
              </div>
              <span className="pill">{Math.round(medication.adherence * 100)}% adherence</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}