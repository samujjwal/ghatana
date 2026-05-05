import React from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { demoDashboard } from '../mockData';

export function RecordDetailPage(): React.ReactElement {
  const { recordId } = useParams();
  const record = demoDashboard.records.find((item) => item.id === recordId) ?? null;

  if (!record) {
    return (
      <Card>
        <CardHeader title="Record unavailable" subheader="FHIR resource rendering" />
        <CardContent>
          <p className="muted">No record payload is available for the requested identifier.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title={record.title} subheader="FHIR resource rendering" />
      <CardContent>
        <div className="stack gap-md">
          <div className="row gap-sm">
            <span className="pill">{record.resourceType}</span>
            <span className="pill ghost">{record.category}</span>
          </div>
          <pre className="code-block">{record.fhirJson}</pre>
        </div>
      </CardContent>
    </Card>
  );
}
